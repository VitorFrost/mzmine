#!/usr/bin/env python3
"""Validate and fetch approved public mass-spectrometry test fixtures.

Downloads are deliberately fail-closed. A manifest entry is fetchable only when it explicitly
records verified reuse terms, an expected byte size, a SHA-256 digest, and an HTTPS or anonymous
FTP URL. Candidate entries may be listed and validated but cannot be downloaded.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "datasets" / "public_validation_manifest.json"
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
ALLOWED_SCHEMES = {"https", "ftp"}
VERIFIED_LICENSE_STATES = {"verified", "verified-source-code-reference"}


class ManifestError(ValueError):
  """Raised when the dataset manifest violates the fail-closed policy."""


@dataclass(frozen=True)
class DownloadItem:
  dataset_id: str
  url: str
  destination: Path
  expected_size: int
  expected_sha256: str


def load_manifest(path: Path) -> dict[str, Any]:
  try:
    data = json.loads(path.read_text(encoding="utf-8"))
  except FileNotFoundError as exc:
    raise ManifestError(f"Manifest does not exist: {path}") from exc
  except json.JSONDecodeError as exc:
    raise ManifestError(f"Invalid JSON in {path}: {exc}") from exc
  validate_manifest(data)
  return data


def validate_manifest(data: dict[str, Any]) -> None:
  if data.get("schema_version") != 1:
    raise ManifestError("schema_version must be exactly 1")

  policy = data.get("policy")
  if not isinstance(policy, dict) or policy.get("allow_unverified_downloads") is not False:
    raise ManifestError("policy.allow_unverified_downloads must be false")
  if policy.get("commit_large_test_data") is not False:
    raise ManifestError("policy.commit_large_test_data must be false")

  datasets = data.get("datasets")
  if not isinstance(datasets, list) or not datasets:
    raise ManifestError("datasets must be a non-empty list")

  seen_ids: set[str] = set()
  for index, dataset in enumerate(datasets):
    prefix = f"datasets[{index}]"
    if not isinstance(dataset, dict):
      raise ManifestError(f"{prefix} must be an object")

    dataset_id = dataset.get("id")
    if not isinstance(dataset_id, str) or not re.fullmatch(r"[a-z0-9][a-z0-9._-]*", dataset_id):
      raise ManifestError(f"{prefix}.id is invalid: {dataset_id!r}")
    if dataset_id in seen_ids:
      raise ManifestError(f"Duplicate dataset id: {dataset_id}")
    seen_ids.add(dataset_id)

    if not isinstance(dataset.get("tier"), int) or dataset["tier"] < 0:
      raise ManifestError(f"{dataset_id}.tier must be a non-negative integer")
    if not isinstance(dataset.get("download_enabled"), bool):
      raise ManifestError(f"{dataset_id}.download_enabled must be boolean")

    source = dataset.get("source")
    license_info = dataset.get("license")
    files = dataset.get("files")
    if not isinstance(source, dict):
      raise ManifestError(f"{dataset_id}.source must be an object")
    if not isinstance(license_info, dict):
      raise ManifestError(f"{dataset_id}.license must be an object")
    if not isinstance(files, list):
      raise ManifestError(f"{dataset_id}.files must be a list")

    for file_index, file_info in enumerate(files):
      if not isinstance(file_info, dict):
        raise ManifestError(f"{dataset_id}.files[{file_index}] must be an object")
      _validate_relative_path(dataset_id, file_info.get("relative_path"))

    if dataset["download_enabled"]:
      _validate_enabled_dataset(dataset)


def _validate_relative_path(dataset_id: str, value: Any) -> Path:
  if not isinstance(value, str) or not value.strip():
    raise ManifestError(f"{dataset_id}: file relative_path must be a non-empty string")
  path = Path(value)
  if path.is_absolute() or ".." in path.parts:
    raise ManifestError(f"{dataset_id}: unsafe relative_path: {value}")
  return path


def _validate_download_url(dataset_id: str, url: Any) -> str:
  if not isinstance(url, str) or not url:
    raise ManifestError(f"{dataset_id}: an enabled file requires download_url")
  parsed = urllib.parse.urlparse(url)
  if parsed.scheme.lower() not in ALLOWED_SCHEMES:
    raise ManifestError(f"{dataset_id}: URL scheme must be HTTPS or anonymous FTP")
  if parsed.username or parsed.password:
    raise ManifestError(f"{dataset_id}: credentials are forbidden in public fixture URLs")
  if not parsed.hostname:
    raise ManifestError(f"{dataset_id}: download URL has no hostname")
  return url


def _validate_enabled_dataset(dataset: dict[str, Any]) -> None:
  dataset_id = dataset["id"]
  license_info = dataset["license"]
  if license_info.get("status") not in VERIFIED_LICENSE_STATES:
    raise ManifestError(f"{dataset_id}: download enabled without verified reuse terms")
  if not license_info.get("identifier") or not license_info.get("evidence_url"):
    raise ManifestError(f"{dataset_id}: verified license identifier and evidence URL are required")

  files = dataset["files"]
  if not files:
    raise ManifestError(f"{dataset_id}: download enabled but no files are frozen")

  source_url = dataset["source"].get("download_url")
  for file_info in files:
    size = file_info.get("expected_size_bytes")
    digest = file_info.get("sha256")
    if not isinstance(size, int) or size <= 0:
      raise ManifestError(f"{dataset_id}: enabled file requires positive expected_size_bytes")
    if not isinstance(digest, str) or not SHA256_RE.fullmatch(digest.lower()):
      raise ManifestError(f"{dataset_id}: enabled file requires a lowercase SHA-256 digest")
    _validate_download_url(dataset_id, file_info.get("download_url") or source_url)


def iter_download_items(
    manifest: dict[str, Any], selected_ids: set[str], output_dir: Path
) -> Iterable[DownloadItem]:
  found: set[str] = set()
  for dataset in manifest["datasets"]:
    dataset_id = dataset["id"]
    if selected_ids and dataset_id not in selected_ids:
      continue
    found.add(dataset_id)
    if not dataset["download_enabled"]:
      raise ManifestError(
          f"{dataset_id}: download is disabled until provenance, reuse terms, size, and hash are frozen"
      )
    source_url = dataset["source"].get("download_url")
    for file_info in dataset["files"]:
      yield DownloadItem(
          dataset_id=dataset_id,
          url=file_info.get("download_url") or source_url,
          destination=output_dir / _validate_relative_path(dataset_id, file_info["relative_path"]),
          expected_size=file_info["expected_size_bytes"],
          expected_sha256=file_info["sha256"].lower(),
      )

  missing = selected_ids - found
  if missing:
    raise ManifestError(f"Unknown dataset id(s): {', '.join(sorted(missing))}")


def sha256_file(path: Path) -> str:
  digest = hashlib.sha256()
  with path.open("rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
      digest.update(chunk)
  return digest.hexdigest()


def verify_file(item: DownloadItem) -> None:
  if not item.destination.is_file():
    raise ManifestError(f"Missing downloaded file: {item.destination}")
  actual_size = item.destination.stat().st_size
  if actual_size != item.expected_size:
    raise ManifestError(
        f"Size mismatch for {item.destination}: expected {item.expected_size}, got {actual_size}"
    )
  actual_hash = sha256_file(item.destination)
  if actual_hash != item.expected_sha256:
    raise ManifestError(
        f"SHA-256 mismatch for {item.destination}: expected {item.expected_sha256}, got {actual_hash}"
    )


def download_item(item: DownloadItem, timeout: int, retries: int, force: bool) -> None:
  item.destination.parent.mkdir(parents=True, exist_ok=True)
  if item.destination.exists() and not force:
    verify_file(item)
    print(f"Verified existing file: {item.destination}")
    return

  last_error: Exception | None = None
  for attempt in range(1, retries + 1):
    temp_path: Path | None = None
    try:
      with tempfile.NamedTemporaryFile(
          prefix=item.destination.name + ".", suffix=".part", dir=item.destination.parent, delete=False
      ) as temp_handle:
        temp_path = Path(temp_handle.name)
        request = urllib.request.Request(item.url, headers={"User-Agent": "mzmine-open-offline-validator/1"})
        with urllib.request.urlopen(request, timeout=timeout) as response:
          shutil.copyfileobj(response, temp_handle, length=1024 * 1024)

      if temp_path.stat().st_size != item.expected_size:
        raise ManifestError(
            f"Downloaded size mismatch for {item.dataset_id}: expected {item.expected_size}, "
            f"got {temp_path.stat().st_size}"
        )
      actual_hash = sha256_file(temp_path)
      if actual_hash != item.expected_sha256:
        raise ManifestError(
            f"Downloaded SHA-256 mismatch for {item.dataset_id}: expected {item.expected_sha256}, "
            f"got {actual_hash}"
        )
      os.replace(temp_path, item.destination)
      print(f"Downloaded and verified: {item.destination}")
      return
    except Exception as exc:  # retry network and verification failures; never keep partial bytes
      last_error = exc
      if temp_path is not None:
        temp_path.unlink(missing_ok=True)
      if attempt < retries:
        time.sleep(min(2 ** (attempt - 1), 8))

  raise ManifestError(f"Failed to download {item.dataset_id} after {retries} attempt(s): {last_error}")


def print_datasets(manifest: dict[str, Any]) -> None:
  print("ID\tTier\tStatus\tDownload\tLicense\tPurpose")
  for dataset in manifest["datasets"]:
    print(
        f"{dataset['id']}\t{dataset['tier']}\t{dataset['status']}\t"
        f"{'enabled' if dataset['download_enabled'] else 'disabled'}\t"
        f"{dataset['license'].get('identifier') or dataset['license'].get('status')}\t"
        f"{dataset['purpose']}"
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
  parser.add_argument("--output-dir", type=Path)
  parser.add_argument("--dataset", action="append", default=[], help="Dataset id; may be repeated")
  parser.add_argument("--list", action="store_true", help="List entries without downloading")
  parser.add_argument("--validate", action="store_true", help="Validate the manifest and exit")
  parser.add_argument("--verify-existing", action="store_true", help="Verify files without downloading")
  parser.add_argument("--force", action="store_true", help="Replace an existing verified file")
  parser.add_argument("--timeout", type=int, default=120)
  parser.add_argument("--retries", type=int, default=3)
  return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
  args = parse_args(argv or sys.argv[1:])
  try:
    manifest = load_manifest(args.manifest.resolve())
    if args.list:
      print_datasets(manifest)
    if args.validate:
      print(f"Manifest valid: {args.manifest}")
    if args.list or args.validate:
      return 0
    if not args.dataset:
      raise ManifestError("Specify at least one --dataset, or use --list/--validate")
    if args.timeout <= 0 or args.retries <= 0:
      raise ManifestError("--timeout and --retries must be positive")

    output_dir = (args.output_dir or ROOT / manifest["default_output_directory"]).resolve()
    items = list(iter_download_items(manifest, set(args.dataset), output_dir))
    for item in items:
      if args.verify_existing:
        verify_file(item)
        print(f"Verified: {item.destination}")
      else:
        download_item(item, timeout=args.timeout, retries=args.retries, force=args.force)
    return 0
  except ManifestError as exc:
    print(f"ERROR: {exc}", file=sys.stderr)
    return 2


if __name__ == "__main__":
  raise SystemExit(main())
