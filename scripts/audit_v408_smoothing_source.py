#!/usr/bin/env python3
"""Fail-closed source audit for the frozen MZmine v4.0.8 smoothing gate."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

ORACLE_COMMIT = "8029f930d28c0447f0acf2bcabef0a79865ad434"
ALLOWED_CLASSIFICATIONS = {"identical-scientific-core", "reviewed-gui-only-difference"}


def git_blob_sha1(path: Path) -> str:
  data = path.read_bytes()
  return hashlib.sha1(f"blob {len(data)}\0".encode("ascii") + data).hexdigest()


def audit(fork_root: Path, oracle_root: Path, inventory_path: Path) -> dict:
  inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
  if inventory.get("schema_version") != 1:
    raise ValueError("unsupported inventory schema_version")
  if inventory.get("inventory_id") != "mzmine-v4.0.8-smoothing-source-v1":
    raise ValueError("unexpected inventory_id")
  oracle = inventory["oracle"]
  candidate = inventory["candidate"]
  if oracle.get("commit") != ORACLE_COMMIT:
    raise ValueError("oracle commit is not frozen v4.0.8")

  files = inventory.get("files", [])
  if len(files) != 11:
    raise ValueError("smoothing inventory must govern exactly 11 files")

  seen = set()
  results = []
  for item in files:
    rel = item["path"]
    if rel in seen:
      raise ValueError(f"duplicate governed path: {rel}")
    seen.add(rel)
    classification = item.get("classification")
    if classification not in ALLOWED_CLASSIFICATIONS:
      raise ValueError(f"unknown classification for {rel}: {classification}")

    oracle_path = oracle_root / oracle["source_prefix"] / rel
    candidate_path = fork_root / candidate["source_prefix"] / rel
    if not oracle_path.is_file():
      raise ValueError(f"missing oracle source: {oracle_path}")
    if not candidate_path.is_file():
      raise ValueError(f"missing candidate source: {candidate_path}")

    oracle_sha = git_blob_sha1(oracle_path)
    candidate_sha = git_blob_sha1(candidate_path)
    if oracle_sha != item["oracle_sha1"]:
      raise ValueError(f"oracle blob changed for {rel}: {oracle_sha}")
    if candidate_sha != item["candidate_sha1"]:
      raise ValueError(f"candidate blob changed for {rel}: {candidate_sha}")
    if classification == "identical-scientific-core" and oracle_sha != candidate_sha:
      raise ValueError(f"scientific core diverged for {rel}")
    if classification == "reviewed-gui-only-difference" and oracle_sha == candidate_sha:
      raise ValueError(f"GUI difference unexpectedly disappeared for {rel}; re-review inventory")

    results.append({
        "path": rel,
        "classification": classification,
        "oracle_sha1": oracle_sha,
        "candidate_sha1": candidate_sha,
        "identical": oracle_sha == candidate_sha,
    })

  parameter_set = inventory["parameter_set"]
  if parameter_set.get("class") != "io.github.mzmine.modules.dataprocessing.featdet_smoothing.SmoothingParameters":
    raise ValueError("unexpected smoothing parameter class")
  if parameter_set.get("version") != 1:
    raise ValueError("unexpected smoothing ParameterSet version")
  if parameter_set.get("oracle_sha1") != parameter_set.get("candidate_sha1"):
    raise ValueError("smoothing parameter set must remain byte-identical in this inventory")

  scope = inventory["governed_scope"]
  if scope.get("direct_differential_required") is not True:
    raise ValueError("source identity must not waive the direct differential")

  return {
      "schema_version": 1,
      "inventory_id": inventory["inventory_id"],
      "oracle_commit": oracle["commit"],
      "status": "PASS",
      "governed_file_count": len(results),
      "identical_scientific_file_count": sum(
          1 for item in results if item["classification"] == "identical-scientific-core" and item["identical"]
      ),
      "reviewed_gui_difference_count": sum(
          1 for item in results if item["classification"] == "reviewed-gui-only-difference" and not item["identical"]
      ),
      "parameter_set_version": parameter_set["version"],
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
  result = audit(args.fork_root.resolve(), args.oracle_root.resolve(), args.inventory.resolve())
  args.output.parent.mkdir(parents=True, exist_ok=True)
  args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
  print(json.dumps(result, indent=2, sort_keys=True))
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
