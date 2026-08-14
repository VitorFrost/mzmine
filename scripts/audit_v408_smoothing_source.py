#!/usr/bin/env python3
"""Fail-closed source audit for the governed v4.0.8 feature-smoothing gate."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def git_blob_sha1(path: Path) -> str:
  data = path.read_bytes()
  return hashlib.sha1(f"blob {len(data)}\0".encode("ascii") + data).hexdigest()


def audit(fork_root: Path, oracle_root: Path, inventory_path: Path) -> dict:
  inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
  if inventory.get("schema_version") != 1:
    raise ValueError("unsupported smoothing inventory schema")
  if inventory.get("inventory_id") != "mzmine-v4.0.8-feature-smoothing-source-v1":
    raise ValueError("unexpected smoothing inventory id")
  oracle = inventory["oracle"]
  candidate = inventory["candidate"]
  if oracle["commit"] != "8029f930d28c0447f0acf2bcabef0a79865ad434":
    raise ValueError("smoothing inventory does not pin the frozen v4.0.8 commit")

  package = inventory["package"]
  results = []
  for item in inventory["files"]:
    rel = Path(package) / item["path"]
    oracle_path = oracle_root / oracle["source_prefix"] / rel
    candidate_path = fork_root / candidate["source_prefix"] / rel
    if not oracle_path.is_file() or not candidate_path.is_file():
      raise ValueError(f"missing governed smoothing source: {item['path']}")
    oracle_sha = git_blob_sha1(oracle_path)
    candidate_sha = git_blob_sha1(candidate_path)
    if oracle_sha != item["oracle_sha1"]:
      raise ValueError(f"oracle smoothing blob changed for {item['path']}: {oracle_sha}")
    if candidate_sha != item["candidate_sha1"]:
      raise ValueError(f"candidate smoothing blob changed for {item['path']}: {candidate_sha}")
    if item["classification"] == "identical-scientific-core" and oracle_sha != candidate_sha:
      raise ValueError(f"scientific smoothing source diverged for {item['path']}")
    results.append({"path": item["path"], "sha1": oracle_sha, "identical": oracle_sha == candidate_sha})

  if inventory.get("parameter_set_version") != 1:
    raise ValueError("governed smoothing ParameterSet version must remain 1")

  return {
      "schema_version": 1,
      "inventory_id": inventory["inventory_id"],
      "oracle_commit": oracle["commit"],
      "status": "PASS",
      "governed_scientific_file_count": len(results),
      "identical_scientific_file_count": sum(1 for item in results if item["identical"]),
      "direct_differential_required": True,
      "files": results,
  }


def main() -> int:
  parser = argparse.ArgumentParser()
  parser.add_argument("--fork-root", type=Path, required=True)
  parser.add_argument("--oracle-root", type=Path, required=True)
  parser.add_argument("--inventory", type=Path, required=True)
  parser.add_argument("--output", type=Path, required=True)
  args = parser.parse_args()
  try:
    report = audit(args.fork_root, args.oracle_root, args.inventory)
  except (OSError, ValueError, json.JSONDecodeError) as exc:
    print(f"Smoothing source audit refused: {exc}")
    return 2
  args.output.parent.mkdir(parents=True, exist_ok=True)
  args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
  print(json.dumps(report, indent=2, sort_keys=True))
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
