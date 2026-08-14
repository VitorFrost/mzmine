#!/usr/bin/env python3
"""Fail-closed source-contract audit for the v4.0.8 minimum-search resolver gate."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from pathlib import Path

ORACLE_COMMIT = "8029f930d28c0447f0acf2bcabef0a79865ad434"
INVENTORY_ID = "mzmine-v4.0.8-minimum-search-resolver-source-v2"
ACCEPTED_SMOOTHING_SHA = "1c8fb4b82356facdbff4b88174990dcdf307b665df5ddddd30363cde471ec971"
ALLOWED_RELATIONS = {"identical", "reviewed-different"}


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
  if inventory.get("schema_version") != 2:
    raise ValueError("unsupported resolver source inventory schema")
  if inventory.get("inventory_id") != INVENTORY_ID:
    raise ValueError("unexpected resolver source inventory id")
  if inventory["oracle"]["commit"] != ORACLE_COMMIT:
    raise ValueError("inventory does not pin exact v4.0.8 oracle commit")
  actual_commit = git(oracle_root, "rev-parse", "HEAD")
  if actual_commit != ORACLE_COMMIT:
    raise ValueError(f"wrong oracle checkout commit: {actual_commit}")

  oracle_prefix = Path(inventory["oracle"]["source_prefix"])
  candidate_prefix = Path(inventory["candidate"]["source_prefix"])
  frozen_files = inventory.get("files")
  if not isinstance(frozen_files, list) or not frozen_files:
    raise ValueError("resolver source inventory files are missing")

  records = []
  violations = []
  seen = set()
  for expected in frozen_files:
    relative = expected.get("path")
    relation = expected.get("expected_relation")
    if not isinstance(relative, str) or not relative or relative in seen:
      violations.append(f"invalid or duplicate governed path: {relative!r}")
      continue
    seen.add(relative)
    if relation not in ALLOWED_RELATIONS:
      violations.append(f"unsupported expected relation for {relative}: {relation!r}")
      continue

    oracle_path = oracle_root / oracle_prefix / relative
    candidate_path = fork_root / candidate_prefix / relative
    oracle_exists = oracle_path.is_file()
    candidate_exists = candidate_path.is_file()
    oracle_sha = blob_sha1(oracle_path) if oracle_exists else None
    candidate_sha = blob_sha1(candidate_path) if candidate_exists else None
    identical = oracle_sha is not None and oracle_sha == candidate_sha

    record = {
        "path": relative,
        "role": expected.get("role"),
        "expected_relation": relation,
        "oracle_exists": oracle_exists,
        "candidate_exists": candidate_exists,
        "oracle_git_blob_sha1": oracle_sha,
        "candidate_git_blob_sha1": candidate_sha,
        "identical": identical,
        "expectation_satisfied": False,
    }
    records.append(record)

    if not oracle_exists:
      violations.append(f"missing frozen v4.0.8 resolver source: {relative}")
      continue
    if not candidate_exists:
      violations.append(f"missing candidate resolver source: {relative}")
      continue

    frozen_oracle = expected.get("oracle_git_blob_sha1")
    frozen_candidate = expected.get("candidate_git_blob_sha1")
    if oracle_sha != frozen_oracle:
      violations.append(
          f"oracle blob drift for {relative}: expected={frozen_oracle} observed={oracle_sha}")
    if candidate_sha != frozen_candidate:
      violations.append(
          f"candidate blob drift for {relative}: expected={frozen_candidate} observed={candidate_sha}")
    if relation == "identical" and not identical:
      violations.append(
          f"governed executed resolver source is no longer identical for {relative}: "
          f"oracle={oracle_sha} candidate={candidate_sha}")
    if relation == "reviewed-different" and identical:
      violations.append(
          f"reviewed resolver framework difference unexpectedly disappeared for {relative}")

    record["expectation_satisfied"] = not any(
        relative in violation for violation in violations)

  governed_path = inventory.get("governed_path", {})
  if governed_path.get("legacy_feature_resolver_selected") is not False:
    violations.append("governed path must explicitly exclude legacy FeatureResolver selection")
  if governed_path.get("group_ms2_must_be_disabled_for_this_gate") is not True:
    violations.append("governed path must require disabled group-MS2 for this narrow resolver gate")
  if governed_path.get("pre_resolver_records_sha256") != ACCEPTED_SMOOTHING_SHA:
    violations.append("governed pre-resolver smoothing SHA is not the artifact-derived accepted state")

  report = {
      "schema_version": 2,
      "inventory_id": inventory["inventory_id"],
      "oracle_commit": ORACLE_COMMIT,
      "status": "PASS" if not violations else "FAIL",
      "governed_file_count": len(records),
      "identical_file_count": sum(1 for record in records if record["identical"]),
      "reviewed_different_file_count": sum(
          1 for record in records if record["expected_relation"] == "reviewed-different"),
      "direct_differential_required": True,
      "governed_path": governed_path,
      "violations": violations,
      "files": records,
  }
  if violations:
    raise ValueError("resolver source contract violations: " + "; ".join(violations))
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
    failure = {
        "schema_version": 2,
        "inventory_id": INVENTORY_ID,
        "oracle_commit": ORACLE_COMMIT,
        "status": "FAIL",
        "error": str(exc),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(failure, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Minimum-search resolver source audit refused: {exc}")
    return 2
  args.output.parent.mkdir(parents=True, exist_ok=True)
  args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
  print(json.dumps(report, indent=2, sort_keys=True))
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
