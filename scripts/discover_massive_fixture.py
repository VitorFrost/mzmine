#!/usr/bin/env python3
"""Resolve and hash one public MassIVE fixture without modifying the manifest.

This is a maintainer discovery tool, not the normal dataset downloader. It asks the official
MassiveServlet for the dataset FTP root, derives the exact file URL from a path displayed by the
MassIVE files page, writes a resolution report, and optionally downloads the file to verify size and
calculate SHA-256. Dataset bytes are never added to Git by this script.
"""

from __future__ import annotations

import argparse
import ftplib
import hashlib
import json
import os
import re
import shutil
import ssl
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, BinaryIO

ACCESSION_RE = re.compile(r"^MSV\d{9}$")
TASK_RE = re.compile(r"^[0-9a-f]{32}$")
ALLOWED_SCHEMES = {"ftp", "https"}


class DiscoveryError(RuntimeError):
  """Raised when public fixture discovery cannot be safely completed."""


HANDLED_ERRORS = (DiscoveryError, OSError, urllib.error.URLError) + ftplib.all_errors


def fetch_json(url: str, timeout: int) -> dict[str, Any]:
  request = urllib.request.Request(
      url, headers={"User-Agent": "mzmine-open-offline-fixture-discovery/1"}
  )
  with urllib.request.urlopen(request, timeout=timeout) as response:
    raw = response.read()
  try:
    value = json.loads(raw.decode("utf-8"))
  except (UnicodeDecodeError, json.JSONDecodeError) as exc:
    preview = raw[:200].decode("utf-8", errors="replace")
    raise DiscoveryError(f"MassiveServlet did not return valid UTF-8 JSON: {preview!r}") from exc
  if not isinstance(value, dict):
    raise DiscoveryError("MassiveServlet JSON response must be an object")
  return value


def validate_public_ftp_root(value: Any, accession: str) -> str:
  if not isinstance(value, str) or not value.strip():
    raise DiscoveryError("MassiveServlet response has no FTP root")
  parsed = urllib.parse.urlparse(value.strip())
  if parsed.scheme.lower() not in ALLOWED_SCHEMES:
    raise DiscoveryError(f"Unexpected dataset URL scheme: {parsed.scheme!r}")
  if parsed.username or parsed.password:
    raise DiscoveryError("Public fixture discovery refuses credential-bearing URLs")
  if not parsed.hostname:
    raise DiscoveryError("Dataset FTP root has no hostname")
  if accession.lower() not in parsed.path.lower():
    raise DiscoveryError(
        f"Dataset FTP root does not contain expected accession {accession}: {value}"
    )
  return value.strip()


def derive_file_url(ftp_root: str, accession: str, listed_path: str) -> str:
  prefix = f"f.{accession}/"
  if not listed_path.startswith(prefix):
    raise DiscoveryError(f"Listed path must start with {prefix!r}: {listed_path!r}")
  suffix = listed_path[len(prefix):]
  if not suffix or ".." in Path(suffix).parts:
    raise DiscoveryError(f"Unsafe or empty dataset path: {listed_path!r}")

  root = ftp_root.rstrip("/") + "/"
  encoded_suffix = urllib.parse.quote(suffix, safe="/._-~")
  resolved = urllib.parse.urljoin(root, encoded_suffix)
  parsed = urllib.parse.urlparse(resolved)
  if parsed.scheme.lower() not in ALLOWED_SCHEMES or not parsed.hostname:
    raise DiscoveryError(f"Derived an invalid file URL: {resolved}")
  return resolved


def sha256_file(path: Path) -> str:
  digest = hashlib.sha256()
  with path.open("rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
      digest.update(chunk)
  return digest.hexdigest()


def write_json(path: Path, value: dict[str, Any]) -> None:
  path.parent.mkdir(parents=True, exist_ok=True)
  path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def copy_https(url: str, output: BinaryIO, timeout: int) -> None:
  request = urllib.request.Request(
      url, headers={"User-Agent": "mzmine-open-offline-fixture-discovery/1"}
  )
  with urllib.request.urlopen(request, timeout=timeout) as response:
    shutil.copyfileobj(response, output, length=1024 * 1024)


def copy_explicit_ftps(url: str, output: BinaryIO, timeout: int) -> None:
  """Download an ftp:// URL through explicit TLS because MassIVE rejects clear FTP."""
  parsed = urllib.parse.urlparse(url)
  if parsed.scheme.lower() != "ftp" or not parsed.hostname:
    raise DiscoveryError(f"Not a valid public FTP URL: {url}")
  if parsed.username or parsed.password:
    raise DiscoveryError("Credential-bearing FTP URLs are forbidden")

  remote_path = urllib.parse.unquote(parsed.path)
  if not remote_path.startswith("/") or ".." in Path(remote_path).parts:
    raise DiscoveryError(f"Unsafe FTP path: {remote_path!r}")

  context = ssl.create_default_context()
  ftp = ftplib.FTP_TLS(context=context, timeout=timeout)
  try:
    ftp.connect(parsed.hostname, parsed.port or 21, timeout=timeout)
    ftp.login(user="anonymous", passwd="mzmine-open-offline@example.invalid")
    ftp.prot_p()
    ftp.set_pasv(True)
    ftp.retrbinary(f"RETR {remote_path}", output.write, blocksize=1024 * 1024)
  finally:
    try:
      ftp.quit()
    except ftplib.all_errors:
      ftp.close()


def copy_public_url(url: str, output: BinaryIO, timeout: int) -> str:
  parsed = urllib.parse.urlparse(url)
  scheme = parsed.scheme.lower()
  if scheme == "https":
    copy_https(url, output, timeout)
    return "https"
  if scheme == "ftp":
    copy_explicit_ftps(url, output, timeout)
    return "explicit-ftps"
  raise DiscoveryError(f"Unsupported transfer scheme: {scheme!r}")


def download_and_hash(
    url: str, expected_size: int, destination: Path, timeout: int
) -> tuple[str, str]:
  destination.parent.mkdir(parents=True, exist_ok=True)
  temp_path: Path | None = None
  transport = "unknown"
  try:
    with tempfile.NamedTemporaryFile(
        prefix=destination.name + ".", suffix=".part", dir=destination.parent, delete=False
    ) as temp_handle:
      temp_path = Path(temp_handle.name)
      transport = copy_public_url(url, temp_handle, timeout)

    actual_size = temp_path.stat().st_size
    if actual_size != expected_size:
      raise DiscoveryError(
          f"Size mismatch: expected {expected_size} bytes, downloaded {actual_size} bytes"
      )
    digest = sha256_file(temp_path)
    os.replace(temp_path, destination)
    temp_path = None
    return digest, transport
  finally:
    if temp_path is not None:
      temp_path.unlink(missing_ok=True)


def parse_args(argv: list[str]) -> argparse.Namespace:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--task", required=True, help="32-character MassIVE task id")
  parser.add_argument("--accession", required=True, help="MassIVE accession, e.g. MSV000101091")
  parser.add_argument("--listed-path", required=True, help="Exact f.MSV... path from dataset_files.jsp")
  parser.add_argument("--expected-size", required=True, type=int)
  parser.add_argument("--output-directory", type=Path, required=True)
  parser.add_argument("--timeout", type=int, default=180)
  parser.add_argument(
      "--resolve-only",
      action="store_true",
      help="Write fixture_resolution.json and exit before downloading dataset bytes",
  )
  return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
  args = parse_args(argv or sys.argv[1:])
  try:
    accession = args.accession.upper()
    task = args.task.lower()
    if not ACCESSION_RE.fullmatch(accession):
      raise DiscoveryError(f"Invalid MassIVE accession: {args.accession!r}")
    if not TASK_RE.fullmatch(task):
      raise DiscoveryError(f"Invalid MassIVE task id: {args.task!r}")
    if args.expected_size <= 0 or args.timeout <= 0:
      raise DiscoveryError("Expected size and timeout must be positive")

    endpoint = (
        "https://massive.ucsd.edu/ProteoSAFe/MassiveServlet?"
        + urllib.parse.urlencode({"task": task, "function": "massiveinformation"})
    )
    info = fetch_json(endpoint, args.timeout)
    returned_accession = str(info.get("dataset_id", "")).upper()
    if returned_accession != accession:
      raise DiscoveryError(
          f"MassiveServlet returned dataset {returned_accession!r}, expected {accession!r}"
      )
    if str(info.get("private", "false")).lower() == "true":
      raise DiscoveryError("Fixture discovery refuses private MassIVE datasets")

    ftp_root = validate_public_ftp_root(info.get("ftp"), accession)
    file_url = derive_file_url(ftp_root, accession, args.listed_path)
    output_directory = args.output_directory.resolve()

    resolution = {
        "schema_version": 1,
        "accession": accession,
        "task": task,
        "dataset_title": info.get("title"),
        "dataset_private": info.get("private"),
        "dataset_license": info.get("license"),
        "dataset_page": f"https://massive.ucsd.edu/ProteoSAFe/dataset.jsp?accession={accession}",
        "files_page": f"https://massive.ucsd.edu/ProteoSAFe/dataset_files.jsp?task={task}",
        "listed_path": args.listed_path,
        "ftp_root": ftp_root,
        "resolved_file_url": file_url,
        "expected_size_bytes": args.expected_size,
        "required_transport": "explicit FTPS for ftp:// MassIVE URLs",
    }
    write_json(output_directory / "fixture_resolution.json", resolution)
    print(json.dumps(resolution, indent=2, sort_keys=True), flush=True)

    if args.resolve_only:
      return 0

    downloaded_path = output_directory / "download" / Path(args.listed_path).name
    digest, transport = download_and_hash(file_url, args.expected_size, downloaded_path, args.timeout)
    report = {
        **resolution,
        "size_bytes": args.expected_size,
        "sha256": digest,
        "transfer_transport": transport,
        "local_file": str(downloaded_path),
    }
    write_json(output_directory / "fixture_report.json", report)
    print(json.dumps(report, indent=2, sort_keys=True), flush=True)
    return 0
  except HANDLED_ERRORS as exc:
    print(f"ERROR: {exc}", file=sys.stderr, flush=True)
    return 2


if __name__ == "__main__":
  raise SystemExit(main())
