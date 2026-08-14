#!/usr/bin/env python3
"""Fail-closed source audit for the v4.0.8 minimum-search resolver gate."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from pathlib import Path

ORACLE_COMMIT = "8029f930d28c0447f0acf2bcabef0a79865ad434"


def git(checkout: Path, *args: str) -> str:
  result = subprocess.run(
      ["git", "-C", str(checkout), *args], check=True, text=True,
      stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  return result.stdout.strip()


def blob_sha1(path: Path) -> str:
  data = path.read_bytes()
  return hashlib.sha1(f"blob {len(data)}\0".encode("ascii") + data).hexdigest()


def audit(fork_root: Path, oracle_root: Path, inventory_path: Path) -> dict:
  inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
  if inventory.get("schema_version") != 1:
    raise ValueError("unsupported resolver source inventory schema")
  if inventory.get("inventory_id") != "mzmine-v4.0.8-minimum-search-resolver-source-v1":
    raise ValueError("unexpected resolver source inventory id")
  if inventory["oracle"]["commit"] != ORACLE_COMMIT:
    raise ValueError("inventory does not pin exact v4.0.8 oracle commit")
  actual_commit = git(oracle_root, "rev-parse", "HEAD")
  if actual_commit != ORACLE_COMMIT:
    raise ValueError(f"wrong oracle checkout commit: {actual_commit}")

  oracle_prefix = Path(inventory["oracle"]["source_prefix"])
  candidate_prefix = Path(inventory["candidate"]["source_prefix"])
  records = []
  for relative in inventory["scientific_paths"]:
    oracle_path = oracle_root / oracle_prefix / relative
    candidate_path = fork_root / candidate_prefix / relative
    if not oracle_path.is_file():
      raise ValueError(f"missing frozen v4.0.8 resolver source: {relative}")
    if not candidate_path.is_file():
      raise ValueError(f"missing candidate resolver source: {relative}")
    oracle_sha = blob_sha1(oracle_path)
    candidate_sha = blob_sha1(candidate_path)
    identical = oracle_sha == candidate_sha
    records.append({
        "path": relative,
        "oracle_git_blob_sha1": oracle_sha,
        "candidate_git_blob_sha1": candidate_sha,
        "identical": identical,
    })
    if not identical:
      raise ValueError(
          f"governed resolver source differs for {relative}: "
          f"oracle={oracle_sha} candidate={candidate_sha}")

  report = {
      "schema_version": 1,
      "inventory_id": inventory["inventory_id"],
      "oracle_commit": ORACLE_COMMIT,
      "status": "PASS",
      "governed_file_count": len(records),
      "identical_file_count": sum(1 for record in records if record["identical"]),
      "direct_differential_required": True,
      "files": records,
  }
  return report


def main() -> int:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--fork-root", type=Path, required=True)
  parser.add_argument("--oracle-root", type=Path, required=True)
  parser.add_argument("--inventory", type=Path, required=True)
  parser.add_argument("--output", type=Path, required=True)
  args = parser.parse_args()
  try:
    report = audit(args.fork_root, args.oracle_root, args.inventory)
  except (OSError, ValueError, json.JSONDecodeError, subprocess.CalledProcessError) as exc:
    print(f"Minimum-search resolver source audit refused: {exc}")
    return 2
  args.output.parent.mkdir(parents=True, exist_ok=True)
  args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
  print(json.dumps(report, indent=2, sort_keys=True))
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
