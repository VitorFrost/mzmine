#!/usr/bin/env python3
"""Resolve, download, and hash a public Zenodo fixture through HTTPS.

The script validates the record license, exact file key, byte size, and repository checksum from the
official Zenodo record API before calculating a local SHA-256. It writes metadata reports separately
from downloaded bytes so CI can delete the dataset and retain only provenance.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

RECORD_RE = re.compile(r"^[1-9][0-9]*$")
MD5_RE = re.compile(r"^[0-9a-f]{32}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


class DiscoveryError(RuntimeError):
  """Raised when a Zenodo fixture cannot be resolved or verified safely."""


def request_json(url: str, timeout: int) -> dict[str, Any]:
  request = urllib.request.Request(
      url, headers={"User-Agent": "mzmine-open-offline-zenodo-discovery/1"}
  )
  with urllib.request.urlopen(request, timeout=timeout) as response:
    raw = response.read()
  try:
    value = json.loads(raw.decode("utf-8"))
  except (UnicodeDecodeError, json.JSONDecodeError) as exc:
    raise DiscoveryError("Zenodo API returned invalid UTF-8 JSON") from exc
  if not isinstance(value, dict):
    raise DiscoveryError("Zenodo API response must be a JSON object")
  return value


def record_license(record: dict[str, Any]) -> tuple[str, str | None]:
  metadata = record.get("metadata")
  if not isinstance(metadata, dict):
    raise DiscoveryError("Zenodo record has no metadata object")
  rights = metadata.get("rights")
  if isinstance(rights, list) and rights:
    first = rights[0]
    if isinstance(first, dict):
      identifier = str(first.get("id") or first.get("title") or "").strip()
      link = first.get("link")
      if identifier:
        return identifier, str(link) if link else None
  license_info = metadata.get("license")
  if isinstance(license_info, dict):
    identifier = str(license_info.get("id") or license_info.get("title") or "").strip()
    link = license_info.get("link")
    if identifier:
      return identifier, str(link) if link else None
  raise DiscoveryError("Zenodo record has no explicit license metadata")


def find_file(record: dict[str, Any], file_key: str) -> dict[str, Any]:
  files = record.get("files")
  if not isinstance(files, list):
    raise DiscoveryError("Zenodo record has no files list")
  matches = [item for item in files if isinstance(item, dict) and item.get("key") == file_key]
  if len(matches) != 1:
    raise DiscoveryError(f"Expected exactly one Zenodo file named {file_key!r}, found {len(matches)}")
  return matches[0]


def parse_md5(file_info: dict[str, Any]) -> str:
  checksum = file_info.get("checksum")
  if not isinstance(checksum, str) or not checksum.startswith("md5:"):
    raise DiscoveryError(f"Zenodo file checksum is not an MD5 value: {checksum!r}")
  digest = checksum.split(":", 1)[1].lower()
  if not MD5_RE.fullmatch(digest):
    raise DiscoveryError(f"Invalid Zenodo MD5 digest: {digest!r}")
  return digest


def content_url(file_info: dict[str, Any], record_id: str, file_key: str) -> str:
  links = file_info.get("links")
  candidates: list[str] = []
  if isinstance(links, dict):
    for key in ("content", "self", "download"):
      value = links.get(key)
      if isinstance(value, str):
        candidates.append(value)
  candidates.append(
      f"https://zenodo.org/records/{record_id}/files/{urllib.parse.quote(file_key)}?download=1"
  )
  for candidate in candidates:
    parsed = urllib.parse.urlparse(candidate)
    if parsed.scheme == "https" and parsed.hostname and parsed.hostname.endswith("zenodo.org"):
      return candidate
  raise DiscoveryError("Zenodo file has no acceptable HTTPS content URL")


def file_digest(path: Path, algorithm: str) -> str:
  digest = hashlib.new(algorithm)
  with path.open("rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
      digest.update(chunk)
  return digest.hexdigest()


def write_json(path: Path, value: dict[str, Any]) -> None:
  path.parent.mkdir(parents=True, exist_ok=True)
  path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def download(
    url: str,
    destination: Path,
    expected_size: int,
    expected_md5: str,
    timeout: int,
) -> str:
  destination.parent.mkdir(parents=True, exist_ok=True)
  temporary: Path | None = None
  try:
    with tempfile.NamedTemporaryFile(
        prefix=destination.name + ".", suffix=".part", dir=destination.parent, delete=False
    ) as output:
      temporary = Path(output.name)
      request = urllib.request.Request(
          url, headers={"User-Agent": "mzmine-open-offline-zenodo-discovery/1"}
      )
      with urllib.request.urlopen(request, timeout=timeout) as response:
        final_url = urllib.parse.urlparse(response.geturl())
        if final_url.scheme != "https":
          raise DiscoveryError(f"Zenodo redirected to a non-HTTPS URL: {response.geturl()}")
        shutil.copyfileobj(response, output, length=1024 * 1024)

    actual_size = temporary.stat().st_size
    if actual_size != expected_size:
      raise DiscoveryError(
          f"Size mismatch: Zenodo metadata says {expected_size}, downloaded {actual_size} bytes"
      )
    actual_md5 = file_digest(temporary, "md5")
    if actual_md5 != expected_md5:
      raise DiscoveryError(
          f"MD5 mismatch: Zenodo metadata says {expected_md5}, downloaded {actual_md5}"
      )
    sha256 = file_digest(temporary, "sha256")
    if not SHA256_RE.fullmatch(sha256):
      raise DiscoveryError("Calculated invalid SHA-256")
    temporary.replace(destination)
    temporary = None
    return sha256
  finally:
    if temporary is not None:
      temporary.unlink(missing_ok=True)


def parse_args(argv: list[str]) -> argparse.Namespace:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--record", required=True)
  parser.add_argument("--file", required=True)
  parser.add_argument("--required-license", required=True)
  parser.add_argument("--output-directory", type=Path, required=True)
  parser.add_argument("--timeout", type=int, default=300)
  parser.add_argument("--resolve-only", action="store_true")
  return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
  args = parse_args(argv or sys.argv[1:])
  try:
    if not RECORD_RE.fullmatch(args.record):
      raise DiscoveryError(f"Invalid Zenodo record id: {args.record!r}")
    if not args.file or "/" in args.file or "\\" in args.file or args.file in {".", ".."}:
      raise DiscoveryError(f"Unsafe Zenodo file key: {args.file!r}")
    if args.timeout <= 0:
      raise DiscoveryError("Timeout must be positive")

    api_url = f"https://zenodo.org/api/records/{args.record}"
    record = request_json(api_url, args.timeout)
    returned_id = str(record.get("id", ""))
    if returned_id != args.record:
      raise DiscoveryError(f"Zenodo returned record {returned_id!r}, expected {args.record!r}")

    license_id, license_url = record_license(record)
    if license_id.lower() != args.required_license.lower():
      raise DiscoveryError(
          f"License mismatch: required {args.required_license!r}, Zenodo reports {license_id!r}"
      )

    file_info = find_file(record, args.file)
    size = file_info.get("size")
    if not isinstance(size, int) or size <= 0:
      raise DiscoveryError(f"Invalid Zenodo file size: {size!r}")
    md5 = parse_md5(file_info)
    url = content_url(file_info, args.record, args.file)
    output_directory = args.output_directory.resolve()

    resolution = {
        "schema_version": 1,
        "repository": "Zenodo",
        "record_id": args.record,
        "doi": record.get("pids", {}).get("doi", {}).get("identifier")
            if isinstance(record.get("pids"), dict) else None,
        "record_url": f"https://zenodo.org/records/{args.record}",
        "api_url": api_url,
        "title": record.get("metadata", {}).get("title")
            if isinstance(record.get("metadata"), dict) else None,
        "license": license_id,
        "license_url": license_url,
        "file_key": args.file,
        "content_url": url,
        "size_bytes": size,
        "repository_md5": md5,
    }
    write_json(output_directory / "fixture_resolution.json", resolution)
    print(json.dumps(resolution, indent=2, sort_keys=True), flush=True)

    if args.resolve_only:
      return 0

    local_file = output_directory / "download" / args.file
    sha256 = download(url, local_file, size, md5, args.timeout)
    report = {
        **resolution,
        "sha256": sha256,
        "local_file": str(local_file),
        "transfer_transport": "verified HTTPS from Zenodo",
    }
    write_json(output_directory / "fixture_report.json", report)
    print(json.dumps(report, indent=2, sort_keys=True), flush=True)
    return 0
  except (DiscoveryError, OSError, urllib.error.URLError) as exc:
    print(f"ERROR: {type(exc).__name__}: {exc}", file=sys.stderr)
    return 2


if __name__ == "__main__":
  raise SystemExit(main())
