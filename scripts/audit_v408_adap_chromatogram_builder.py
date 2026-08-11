#!/usr/bin/env python3
"""Fail-closed source audit for the frozen v4.0.8 ADAP chromatogram-builder gate."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


ALLOWED_CLASSIFICATIONS = {
    "identical-scientific-core",
    "reviewed-ui-documentation-difference",
    "reviewed-display-name-difference",
    "requires-direct-differential",
}

REQUIRED_PARAMETER_SNIPPETS = (
    'new ScanSelection(1)',
    '"Minimum consecutive scans"',
    '5, true, 1, null',
    '"Minimum intensity for consecutive scans"',
    '0d)',
    'ToleranceType.SCAN_TO_SCAN, 0.002, 10',
    '"chromatograms"',
    '"Minimum absolute height"',
)


def git_blob_sha1(path: Path) -> str:
    data = path.read_bytes()
    header = f"blob {len(data)}\0".encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


def require_file(root: Path, prefix: str, package: str, name: str) -> Path:
    path = root / prefix / package / name
    if not path.is_file():
        raise ValueError(f"required source file is missing: {path}")
    return path


def audit(fork_root: Path, oracle_root: Path, inventory_path: Path) -> dict:
    inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
    if inventory.get("schema_version") != 1:
        raise ValueError("unsupported inventory schema_version")
    if inventory.get("inventory_id") != "mzmine-v4.0.8-adap-chromatogram-builder-source-v1":
        raise ValueError("unexpected inventory_id")
    oracle = inventory["oracle"]
    candidate = inventory["candidate"]
    if oracle.get("commit") != "8029f930d28c0447f0acf2bcabef0a79865ad434":
        raise ValueError("oracle commit is not the frozen v4.0.8 commit")

    package = inventory["package"]
    files = inventory.get("files", [])
    expected_names = {
        "ADAPChromatogram.java",
        "ExpandedDataPoint.java",
        "ADAPChromatogramBuilderParameters.java",
        "ModularADAPChromatogramBuilderModule.java",
        "ModularADAPChromatogramBuilderTask.java",
    }
    names = {item.get("name") for item in files}
    if names != expected_names or len(files) != len(expected_names):
        raise ValueError("inventory must govern exactly the five ADAP chromatogram-builder files")

    results = []
    for item in files:
        classification = item.get("classification")
        if classification not in ALLOWED_CLASSIFICATIONS:
            raise ValueError(f"unknown classification for {item['name']}: {classification}")
        oracle_path = require_file(
            oracle_root, oracle["source_prefix"], package, item["name"]
        )
        candidate_path = require_file(
            fork_root, candidate["source_prefix"], package, item["name"]
        )
        oracle_sha = git_blob_sha1(oracle_path)
        candidate_sha = git_blob_sha1(candidate_path)
        if oracle_sha != item["oracle_sha1"]:
            raise ValueError(
                f"oracle blob changed for {item['name']}: expected {item['oracle_sha1']}, got {oracle_sha}"
            )
        if candidate_sha != item["candidate_sha1"]:
            raise ValueError(
                f"candidate blob changed for {item['name']}: expected {item['candidate_sha1']}, got {candidate_sha}"
            )
        if classification == "identical-scientific-core" and oracle_sha != candidate_sha:
            raise ValueError(f"identical scientific core diverged for {item['name']}")
        results.append(
            {
                "name": item["name"],
                "classification": classification,
                "oracle_sha1": oracle_sha,
                "candidate_sha1": candidate_sha,
                "identical": oracle_sha == candidate_sha,
            }
        )

    parameter_name = "ADAPChromatogramBuilderParameters.java"
    oracle_parameters = require_file(
        oracle_root, oracle["source_prefix"], package, parameter_name
    ).read_text(encoding="utf-8")
    candidate_parameters = require_file(
        fork_root, candidate["source_prefix"], package, parameter_name
    ).read_text(encoding="utf-8")
    for snippet in REQUIRED_PARAMETER_SNIPPETS:
        if snippet not in oracle_parameters:
            raise ValueError(f"oracle parameter contract changed; missing: {snippet}")
        if snippet not in candidate_parameters:
            raise ValueError(f"candidate parameter contract changed; missing: {snippet}")

    task_item = next(item for item in files if item["name"] == "ModularADAPChromatogramBuilderTask.java")
    known_differences = task_item.get("known_reviewed_differences", [])
    if len(known_differences) != 2:
        raise ValueError("task must preserve exactly two reviewed source-level differences")

    return {
        "schema_version": 1,
        "inventory_id": inventory["inventory_id"],
        "oracle_commit": oracle["commit"],
        "status": "PASS",
        "governed_file_count": len(results),
        "identical_file_count": sum(1 for item in results if item["identical"]),
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
    result = audit(args.fork_root, args.oracle_root, args.inventory)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
