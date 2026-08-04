#!/usr/bin/env python3
"""Validate the frozen MZmine v4.0.8 LC-MS parity inventory.

The validator intentionally uses only the Python standard library so it can run in the
independence-audit job without resolving additional packages. The committed JSON Schema remains the
machine-readable contract; this script enforces the project-specific invariants that matter before a
differential run is accepted.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

EXPECTED_ORACLE_COMMIT = "8029f930d28c0447f0acf2bcabef0a79865ad434"
EXPECTED_BASE_COMMIT = "2ac3ce3b25190430f3ae0e02c28ddbb94bc248ed"
ALLOWED_CLASSIFICATIONS = {"Equivalent", "Adapted", "Not implemented", "Out of scope"}
ALLOWED_GATE_STATES = {
    "contract-defined",
    "exporter-pending",
    "oracle-run-pending",
    "comparison-pending",
    "complete",
}
SHA40 = re.compile(r"^[0-9a-f]{40}$")
REQUIRED_MODULE_IDS = {
    "mzml-import",
    "mass-detection",
    "adap-chromatogram-builder",
    "smoothing",
    "minimum-search-resolver",
    "isotope-finder",
    "rows-filter",
    "join-aligner",
    "multithread-peak-finder",
    "duplicate-filter",
    "correlation-grouping",
    "legacy-csv-export",
}


class ValidationError(ValueError):
  """Raised when the parity inventory violates a fail-closed rule."""


def _require(condition: bool, message: str) -> None:
  if not condition:
    raise ValidationError(message)


def _load_json(path: Path) -> dict[str, Any]:
  try:
    value = json.loads(path.read_text(encoding="utf-8"))
  except FileNotFoundError as exc:
    raise ValidationError(f"missing JSON file: {path}") from exc
  except json.JSONDecodeError as exc:
    raise ValidationError(f"invalid JSON in {path}: {exc}") from exc
  _require(isinstance(value, dict), f"top-level JSON value must be an object: {path}")
  return value


def _dataset_ids(manifest: dict[str, Any]) -> set[str]:
  datasets = manifest.get("datasets")
  _require(isinstance(datasets, list), "public dataset manifest must contain a datasets array")
  ids: set[str] = set()
  for index, dataset in enumerate(datasets):
    _require(isinstance(dataset, dict), f"dataset entry {index} must be an object")
    dataset_id = dataset.get("id")
    _require(isinstance(dataset_id, str) and dataset_id, f"dataset entry {index} has no id")
    _require(dataset_id not in ids, f"duplicate public dataset id: {dataset_id}")
    ids.add(dataset_id)
  return ids


def _validate_source(module_id: str, role: str, source: Any, target_commit: str) -> None:
  _require(isinstance(source, dict), f"{module_id}.{role} must be an object")
  required = {
      "repository",
      "ref",
      "commit",
      "module_path",
      "parameter_path",
      "module_blob_sha",
      "parameter_blob_sha",
  }
  missing = sorted(required - set(source))
  _require(not missing, f"{module_id}.{role} missing fields: {', '.join(missing)}")

  commit = source["commit"]
  _require(isinstance(commit, str) and SHA40.fullmatch(commit) is not None,
           f"{module_id}.{role}.commit must be a lowercase 40-character SHA")
  if role == "oracle_408":
    _require(commit == target_commit,
             f"{module_id}.oracle_408.commit does not match the frozen target commit")
    _require(source["ref"] == "v4.0.8", f"{module_id}.oracle_408.ref must be v4.0.8")

  for blob_name in ("module_blob_sha", "parameter_blob_sha"):
    blob = source[blob_name]
    _require(blob is None or (isinstance(blob, str) and SHA40.fullmatch(blob) is not None),
             f"{module_id}.{role}.{blob_name} must be null or a lowercase Git blob SHA")

  module_path = source["module_path"]
  _require(isinstance(module_path, str) and module_path.endswith(".java"),
           f"{module_id}.{role}.module_path must point to a Java source file")


def validate(inventory: dict[str, Any], public_manifest: dict[str, Any]) -> dict[str, Any]:
  _require(inventory.get("schema_version") == 1, "schema_version must be 1")

  target = inventory.get("target")
  _require(isinstance(target, dict), "target must be an object")
  _require(target.get("repository") == "mzmine/mzmine", "target repository must be mzmine/mzmine")
  _require(target.get("tag") == "v4.0.8", "target tag must be v4.0.8")
  _require(target.get("commit") == EXPECTED_ORACLE_COMMIT,
           "v4.0.8 must resolve to the frozen oracle commit")
  _require(target.get("license") == "MIT", "target source license must be recorded as MIT")
  _require(target.get("role") == "behavioral-oracle", "target role must be behavioral-oracle")
  _require(target.get("source_tree") == "mzmine-community",
           "target source tree must be mzmine-community")
  tag_resolution = target.get("tag_resolution")
  _require(isinstance(tag_resolution, dict), "target.tag_resolution must be an object")
  _require(tag_resolution.get("object_type") == "commit",
           "v4.0.8 tag must resolve directly to a commit")
  build = target.get("build")
  _require(isinstance(build, dict), "target.build must be an object")
  _require(build.get("system") == "Gradle Wrapper", "oracle build system must be Gradle Wrapper")
  _require(build.get("java_version") == "21", "v4.0.8 oracle must record Java 21")
  _require(build.get("enable_preview") is True, "v4.0.8 oracle must record preview features")
  _require(build.get("execution_status") in {
      "blocked-by-independence-policy", "reproducible-public-slice", "complete"
  }, "invalid oracle build execution status")
  _require(isinstance(build.get("candidate_command"), list) and build["candidate_command"],
           "oracle build candidate command must be recorded")

  fork = inventory.get("fork")
  _require(isinstance(fork, dict), "fork must be an object")
  _require(fork.get("repository") == "VitorFrost/mzmine", "unexpected fork repository")
  _require(fork.get("integration_branch") == "open-offline-main",
           "integration branch must be open-offline-main")
  _require(fork.get("scientific_base_commit") == EXPECTED_BASE_COMMIT,
           "scientific base commit changed without a new inventory version")

  policy = inventory.get("classification_policy")
  _require(isinstance(policy, dict), "classification_policy must be an object")
  _require(set(policy.get("allowed", [])) == ALLOWED_CLASSIFICATIONS,
           "classification policy must enumerate the four governed states")
  _require(policy.get("equivalent_requires_differential_evidence") is True,
           "Equivalent must require direct differential evidence")

  known_dataset_ids = _dataset_ids(public_manifest)
  modules = inventory.get("modules")
  _require(isinstance(modules, list) and modules, "modules must be a non-empty array")

  ids: set[str] = set()
  completed = 0
  verified_sources = 0
  for index, module in enumerate(modules):
    _require(isinstance(module, dict), f"module entry {index} must be an object")
    module_id = module.get("id")
    _require(isinstance(module_id, str) and module_id, f"module entry {index} has no id")
    _require(module_id not in ids, f"duplicate module id: {module_id}")
    ids.add(module_id)

    classification = module.get("classification")
    _require(classification in ALLOWED_CLASSIFICATIONS,
             f"{module_id} has an invalid classification: {classification}")

    versions = module.get("versions")
    _require(isinstance(versions, dict), f"{module_id}.versions must be an object")
    for role in ("base_39", "oracle_408", "open_offline"):
      _validate_source(module_id, role, versions.get(role), target["commit"])

    mapping = module.get("parameter_mapping")
    _require(isinstance(mapping, dict), f"{module_id}.parameter_mapping must be an object")
    _require(mapping.get("status") in {"verified", "partial", "pending", "not-applicable"},
             f"{module_id} has an invalid parameter mapping status")
    changes = mapping.get("changes")
    _require(isinstance(changes, list), f"{module_id}.parameter_mapping.changes must be an array")

    evidence = module.get("evidence")
    _require(isinstance(evidence, dict), f"{module_id}.evidence must be an object")
    source_verified = evidence.get("source_presence_verified") is True
    direct_complete = evidence.get("direct_differential_complete") is True
    verified_sources += int(source_verified)
    completed += int(direct_complete)
    if classification == "Equivalent":
      _require(direct_complete,
               f"{module_id} cannot be Equivalent without direct differential evidence")

    gate = module.get("differential_gate")
    _require(isinstance(gate, dict), f"{module_id}.differential_gate must be an object")
    _require(gate.get("status") in ALLOWED_GATE_STATES,
             f"{module_id} has an invalid differential gate status")
    dataset_ids = gate.get("dataset_ids")
    _require(isinstance(dataset_ids, list) and dataset_ids,
             f"{module_id} must declare at least one governed dataset")
    unknown = sorted(set(dataset_ids) - known_dataset_ids)
    _require(not unknown, f"{module_id} references unknown dataset ids: {', '.join(unknown)}")

    stages = gate.get("stages")
    _require(isinstance(stages, list) and stages, f"{module_id} must declare stages")
    tolerances = gate.get("numeric_tolerances")
    _require(isinstance(tolerances, dict) and tolerances,
             f"{module_id} must declare numeric tolerances")
    for field, tolerance in tolerances.items():
      _require(isinstance(tolerance, dict), f"{module_id} tolerance {field} must be an object")
      for kind in ("absolute", "relative"):
        value = tolerance.get(kind)
        _require(isinstance(value, (int, float)) and value >= 0,
                 f"{module_id} tolerance {field}.{kind} must be non-negative")

  missing_modules = sorted(REQUIRED_MODULE_IDS - ids)
  _require(
      not missing_modules,
      "initial inventory is missing required modules: " + ", ".join(missing_modules),
  )
  unexpected_modules = sorted(ids - REQUIRED_MODULE_IDS)
  _require(
      not unexpected_modules,
      "initial inventory contains undeclared modules: " + ", ".join(unexpected_modules),
  )

  return {
      "inventory_id": inventory.get("inventory_id"),
      "oracle_commit": target["commit"],
      "module_count": len(modules),
      "source_presence_verified_count": verified_sources,
      "direct_differential_complete_count": completed,
      "dataset_reference_count": sum(
          len(module["differential_gate"]["dataset_ids"]) for module in modules
      ),
      "valid": True,
  }


def build_parser() -> argparse.ArgumentParser:
  root = Path(__file__).resolve().parents[1]
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument(
      "--inventory",
      type=Path,
      default=root / "datasets/parity/mzmine_v408_lcms_core_inventory.json",
  )
  parser.add_argument(
      "--public-manifest",
      type=Path,
      default=root / "datasets/public_validation_manifest.json",
  )
  parser.add_argument("--output", type=Path, help="Optional path for the validation report")
  return parser


def main() -> int:
  args = build_parser().parse_args()
  try:
    report = validate(_load_json(args.inventory), _load_json(args.public_manifest))
  except ValidationError as exc:
    print(f"Parity inventory validation failed: {exc}")
    return 2

  rendered = json.dumps(report, indent=2, sort_keys=True)
  print(rendered)
  if args.output:
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(rendered + "\n", encoding="utf-8")
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
