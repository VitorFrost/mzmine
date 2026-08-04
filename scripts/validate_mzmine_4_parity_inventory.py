#!/usr/bin/env python3
"""Validate the frozen MZmine v4.0.8 LC-MS parity inventory.

The validator uses only the Python standard library so it can run in the independence-audit job
without resolving new packages. The committed JSON Schema documents the data shape; this script
adds project-specific, fail-closed invariants needed before differential evidence is accepted.
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
ALLOWED_MAPPING_STATES = {"verified", "partial", "pending", "not-applicable"}
ALLOWED_BUILD_STATES = {
    "blocked-by-independence-policy",
    "reproducible-public-slice",
    "complete",
}
SOURCE_ROLES = {"base_39", "oracle_408", "open_offline"}
SHA40 = re.compile(r"^[0-9a-f]{40}$")
JAVA_CLASS = re.compile(r"^io\.github\.mzmine(?:\.[A-Za-z_][A-Za-z0-9_]*)+$")
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


def _valid_sha(value: Any) -> bool:
  return isinstance(value, str) and SHA40.fullmatch(value) is not None


def _validate_target(inventory: dict[str, Any]) -> dict[str, Any]:
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
  resolved_at = tag_resolution.get("resolved_at")
  _require(isinstance(resolved_at, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", resolved_at),
           "target.tag_resolution.resolved_at must use YYYY-MM-DD")

  build = target.get("build")
  _require(isinstance(build, dict), "target.build must be an object")
  _require(build.get("system") == "Gradle Wrapper", "oracle build system must be Gradle Wrapper")
  _require(build.get("java_version") == "21", "v4.0.8 oracle must record Java 21")
  _require(build.get("enable_preview") is True, "v4.0.8 oracle must record preview features")
  _require(build.get("execution_status") in ALLOWED_BUILD_STATES,
           "invalid oracle build execution status")
  command = build.get("candidate_command")
  _require(isinstance(command, list) and command
           and all(isinstance(part, str) and part for part in command),
           "oracle build candidate command must be a non-empty string array")
  blockers = build.get("blockers")
  _require(isinstance(blockers, list) and all(isinstance(item, str) and item for item in blockers),
           "target.build.blockers must be a string array")
  if build["execution_status"] == "blocked-by-independence-policy":
    _require(blockers, "a blocked oracle build must record at least one blocker")
  _require(isinstance(build.get("policy"), str) and build["policy"],
           "target.build.policy must be recorded")
  return target


def _validate_source_roots(inventory: dict[str, Any], target: dict[str, Any]) -> None:
  roots = inventory.get("source_roots")
  _require(isinstance(roots, dict), "source_roots must be an object")
  _require(set(roots) == SOURCE_ROLES,
           "source_roots must contain exactly base_39, oracle_408, and open_offline")

  expectations = {
      "base_39": ("mzmine/mzmine", "v3.9.0", EXPECTED_BASE_COMMIT, "src/main/java/"),
      "oracle_408": ("mzmine/mzmine", "v4.0.8", target["commit"],
                     "mzmine-community/src/main/java/"),
      "open_offline": ("VitorFrost/mzmine", "open-offline-main", None, "src/main/java/"),
  }
  for role, (repository, ref, commit, prefix) in expectations.items():
    root = roots[role]
    _require(isinstance(root, dict), f"source_roots.{role} must be an object")
    _require(root.get("repository") == repository,
             f"source_roots.{role}.repository is unexpected")
    _require(root.get("ref") == ref, f"source_roots.{role}.ref is unexpected")
    _require(_valid_sha(root.get("commit")),
             f"source_roots.{role}.commit must be a lowercase Git commit SHA")
    if commit is not None:
      _require(root["commit"] == commit, f"source_roots.{role}.commit changed")
    _require(root.get("prefix") == prefix, f"source_roots.{role}.prefix is unexpected")


def _validate_dataset_groups(
    inventory: dict[str, Any], known_dataset_ids: set[str]
) -> dict[str, list[str]]:
  groups = inventory.get("dataset_groups")
  _require(isinstance(groups, dict) and groups, "dataset_groups must be a non-empty object")
  validated: dict[str, list[str]] = {}
  for group_name, dataset_ids in groups.items():
    _require(isinstance(group_name, str) and group_name, "dataset group names must be non-empty")
    _require(isinstance(dataset_ids, list) and dataset_ids,
             f"dataset group {group_name} must contain at least one dataset id")
    _require(all(isinstance(dataset_id, str) and dataset_id for dataset_id in dataset_ids),
             f"dataset group {group_name} must contain only non-empty strings")
    _require(len(dataset_ids) == len(set(dataset_ids)),
             f"dataset group {group_name} contains duplicate dataset ids")
    unknown = sorted(set(dataset_ids) - known_dataset_ids)
    _require(not unknown,
             f"dataset group {group_name} references unknown dataset ids: {', '.join(unknown)}")
    validated[group_name] = dataset_ids
  return validated


def _validate_tolerance_profiles(inventory: dict[str, Any]) -> set[str]:
  profiles = inventory.get("tolerance_profiles")
  _require(isinstance(profiles, dict) and profiles,
           "tolerance_profiles must be a non-empty object")
  for profile_name, fields in profiles.items():
    _require(isinstance(profile_name, str) and profile_name,
             "tolerance profile names must be non-empty")
    _require(isinstance(fields, dict) and fields,
             f"tolerance profile {profile_name} must contain fields")
    for field, tolerance in fields.items():
      _require(isinstance(field, str) and field,
               f"tolerance profile {profile_name} has an invalid field name")
      _require(isinstance(tolerance, dict),
               f"tolerance {profile_name}.{field} must be an object")
      _require(set(tolerance) == {"absolute", "relative"},
               f"tolerance {profile_name}.{field} must contain absolute and relative")
      for kind in ("absolute", "relative"):
        value = tolerance[kind]
        _require(isinstance(value, (int, float)) and not isinstance(value, bool) and value >= 0,
                 f"tolerance {profile_name}.{field}.{kind} must be non-negative")
  return set(profiles)


def _validate_blob_map(module_id: str, label: str, blobs: Any) -> None:
  _require(isinstance(blobs, dict) and blobs,
           f"{module_id}.blobs.{label} must be a non-empty object")
  _require(set(blobs).issubset(SOURCE_ROLES),
           f"{module_id}.blobs.{label} contains an unknown source role")
  _require({"oracle_408", "open_offline"}.issubset(blobs),
           f"{module_id}.blobs.{label} must include oracle_408 and open_offline")
  for role, sha in blobs.items():
    _require(_valid_sha(sha),
             f"{module_id}.blobs.{label}.{role} must be a lowercase Git blob SHA")


def validate(inventory: dict[str, Any], public_manifest: dict[str, Any]) -> dict[str, Any]:
  _require(inventory.get("schema_version") == 1, "schema_version must be 1")
  _require(isinstance(inventory.get("inventory_id"), str) and inventory["inventory_id"],
           "inventory_id must be recorded")

  target = _validate_target(inventory)

  fork = inventory.get("fork")
  _require(isinstance(fork, dict), "fork must be an object")
  _require(fork.get("repository") == "VitorFrost/mzmine", "unexpected fork repository")
  _require(fork.get("integration_branch") == "open-offline-main",
           "integration branch must be open-offline-main")
  _require(fork.get("scientific_base_commit") == EXPECTED_BASE_COMMIT,
           "scientific base commit changed without a new inventory version")
  _require(isinstance(fork.get("application_version"), str) and fork["application_version"],
           "fork.application_version must be recorded")

  policy = inventory.get("classification_policy")
  _require(isinstance(policy, dict), "classification_policy must be an object")
  _require(set(policy.get("allowed", [])) == ALLOWED_CLASSIFICATIONS,
           "classification policy must enumerate the four governed states")
  _require(policy.get("equivalent_requires_differential_evidence") is True,
           "Equivalent must require direct differential evidence")

  _validate_source_roots(inventory, target)
  groups = _validate_dataset_groups(inventory, _dataset_ids(public_manifest))
  profile_names = _validate_tolerance_profiles(inventory)

  finding_catalog = inventory.get("finding_catalog")
  _require(isinstance(finding_catalog, dict) and finding_catalog,
           "finding_catalog must be a non-empty object")
  for code, description in finding_catalog.items():
    _require(isinstance(code, str) and code, "finding codes must be non-empty")
    _require(isinstance(description, str) and description,
             f"finding {code} must have a description")
  finding_codes = set(finding_catalog)

  modules = inventory.get("modules")
  _require(isinstance(modules, list) and modules, "modules must be a non-empty array")
  ids: set[str] = set()
  completed = 0
  verified_sources = 0
  dataset_reference_count = 0

  for index, module in enumerate(modules):
    _require(isinstance(module, dict), f"module entry {index} must be an object")
    module_id = module.get("id")
    _require(isinstance(module_id, str) and module_id, f"module entry {index} has no id")
    _require(module_id not in ids, f"duplicate module id: {module_id}")
    ids.add(module_id)

    _require(isinstance(module.get("area"), str) and module["area"],
             f"{module_id}.area must be recorded")
    _require(isinstance(module.get("capability"), str) and module["capability"],
             f"{module_id}.capability must be recorded")

    classes = module.get("classes")
    _require(isinstance(classes, dict), f"{module_id}.classes must be an object")
    _require(set(classes) == {"module", "parameters"},
             f"{module_id}.classes must contain module and parameters")
    for class_role, class_name in classes.items():
      _require(isinstance(class_name, str) and JAVA_CLASS.fullmatch(class_name) is not None,
               f"{module_id}.classes.{class_role} must be an io.github.mzmine Java class")

    versions = module.get("parameter_versions")
    _require(isinstance(versions, dict), f"{module_id}.parameter_versions must be an object")
    _require(set(versions) == {"base_39", "oracle_408"},
             f"{module_id}.parameter_versions must contain base_39 and oracle_408")
    for role, version in versions.items():
      _require(version is None or (
          isinstance(version, int) and not isinstance(version, bool) and version >= 0
      ), f"{module_id}.parameter_versions.{role} must be null or non-negative")

    blobs = module.get("blobs")
    _require(isinstance(blobs, dict), f"{module_id}.blobs must be an object")
    _require("module" in blobs, f"{module_id}.blobs.module is required")
    _require(set(blobs).issubset({"module", "parameters"}),
             f"{module_id}.blobs contains an unsupported category")
    _validate_blob_map(module_id, "module", blobs["module"])
    if "parameters" in blobs:
      _validate_blob_map(module_id, "parameters", blobs["parameters"])

    mapping_status = module.get("parameter_mapping_status")
    _require(mapping_status in ALLOWED_MAPPING_STATES,
             f"{module_id} has an invalid parameter mapping status")

    classification = module.get("classification")
    _require(classification in ALLOWED_CLASSIFICATIONS,
             f"{module_id} has an invalid classification: {classification}")

    evidence = module.get("evidence")
    _require(isinstance(evidence, dict), f"{module_id}.evidence must be an object")
    _require(set(evidence) == {"source_presence_verified", "direct_differential_complete"},
             f"{module_id}.evidence has unexpected fields")
    _require(isinstance(evidence["source_presence_verified"], bool),
             f"{module_id}.evidence.source_presence_verified must be boolean")
    _require(isinstance(evidence["direct_differential_complete"], bool),
             f"{module_id}.evidence.direct_differential_complete must be boolean")
    source_verified = evidence["source_presence_verified"]
    direct_complete = evidence["direct_differential_complete"]
    verified_sources += int(source_verified)
    completed += int(direct_complete)
    if classification == "Equivalent":
      _require(direct_complete,
               f"{module_id} cannot be Equivalent without direct differential evidence")

    findings = module.get("findings")
    _require(isinstance(findings, list) and findings,
             f"{module_id}.findings must be a non-empty array")
    _require(all(isinstance(code, str) and code for code in findings),
             f"{module_id}.findings must contain non-empty strings")
    _require(len(findings) == len(set(findings)),
             f"{module_id}.findings contains duplicates")
    unknown_findings = sorted(set(findings) - finding_codes)
    _require(not unknown_findings,
             f"{module_id} references unknown finding codes: {', '.join(unknown_findings)}")

    gate = module.get("gate")
    _require(isinstance(gate, dict), f"{module_id}.gate must be an object")
    group_name = gate.get("dataset_group")
    _require(group_name in groups, f"{module_id} references unknown dataset group: {group_name}")
    dataset_reference_count += len(groups[group_name])
    _require(isinstance(gate.get("stage"), str) and gate["stage"],
             f"{module_id}.gate.stage must be recorded")
    exact_fields = gate.get("exact_fields")
    _require(isinstance(exact_fields, list)
             and all(isinstance(field, str) and field for field in exact_fields),
             f"{module_id}.gate.exact_fields must be a string array")
    tolerance_profile = gate.get("tolerance_profile")
    _require(tolerance_profile in profile_names,
             f"{module_id} references unknown tolerance profile: {tolerance_profile}")
    _require(gate.get("status") in ALLOWED_GATE_STATES,
             f"{module_id} has an invalid differential gate status")

  missing_modules = sorted(REQUIRED_MODULE_IDS - ids)
  _require(not missing_modules,
           "initial inventory is missing required modules: " + ", ".join(missing_modules))
  unexpected_modules = sorted(ids - REQUIRED_MODULE_IDS)
  _require(not unexpected_modules,
           "initial inventory contains undeclared modules: " + ", ".join(unexpected_modules))

  return {
      "inventory_id": inventory["inventory_id"],
      "oracle_commit": target["commit"],
      "oracle_execution_status": target["build"]["execution_status"],
      "module_count": len(modules),
      "source_presence_verified_count": verified_sources,
      "direct_differential_complete_count": completed,
      "dataset_group_count": len(groups),
      "dataset_reference_count": dataset_reference_count,
      "tolerance_profile_count": len(profile_names),
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
