#!/usr/bin/env python3
"""Validate a frozen MZmine settings inventory against committed expectations.

The validator compares structural facts and confirms that every referenced public module has source
in the open-offline tree. It never imports Java classes or executes the settings XML.
"""

from __future__ import annotations

import argparse
import collections
import json
import re
import sys
from pathlib import Path
from typing import Any

JAVA_CLASS_RE = re.compile(r"^(?P<package>(?:[a-zA-Z_$][\w$]*\.)+)(?P<class>[A-Za-z_$][\w$]*)$")
ALLOWED_COMPATIBILITY = {
    "equivalent-for-validated-scope",
    "source-present-not-yet-real-data-validated",
    "adapted",
    "not-implemented",
    "out-of-scope",
}


class ValidationError(ValueError):
  pass


def load_json(path: Path) -> dict[str, Any]:
  try:
    value = json.loads(path.read_text(encoding="utf-8"))
  except FileNotFoundError as exc:
    raise ValidationError(f"Missing JSON file: {path}") from exc
  except json.JSONDecodeError as exc:
    raise ValidationError(f"Invalid JSON in {path}: {exc}") from exc
  if not isinstance(value, dict):
    raise ValidationError(f"JSON root must be an object: {path}")
  return value


def java_source_path(repo_root: Path, class_name: str) -> Path:
  match = JAVA_CLASS_RE.fullmatch(class_name)
  if not match:
    raise ValidationError(f"Invalid Java class name: {class_name!r}")
  relative = Path(*class_name.split(".")).with_suffix(".java")
  return repo_root / "src" / "main" / "java" / relative


def verify_java_source(path: Path, class_name: str) -> None:
  if not path.is_file():
    raise ValidationError(f"Referenced module source does not exist: {class_name} -> {path}")
  text = path.read_text(encoding="utf-8")
  package, simple_name = class_name.rsplit(".", 1)
  if not re.search(rf"\bpackage\s+{re.escape(package)}\s*;", text):
    raise ValidationError(f"Package declaration mismatch in {path}")
  if not re.search(rf"\bclass\s+{re.escape(simple_name)}\b", text):
    raise ValidationError(f"Class declaration mismatch in {path}")


def validate(inventory: dict[str, Any], expected: dict[str, Any], repo_root: Path) -> dict[str, Any]:
  exact_fields = {
      "size_bytes": "source_size_bytes",
      "sha256": "source_sha256",
      "xml_root_tag": "xml_root_tag",
      "element_count": "element_count",
      "parameter_name_count": "unique_parameter_name_count",
  }
  for inventory_key, expected_key in exact_fields.items():
    actual = inventory.get(inventory_key)
    wanted = expected.get(expected_key)
    if actual != wanted:
      raise ValidationError(
          f"Mismatch for {inventory_key}: expected {wanted!r}, observed {actual!r}"
      )

  root_attributes = inventory.get("root_attributes")
  if not isinstance(root_attributes, dict):
    raise ValidationError("Inventory root_attributes must be an object")
  if root_attributes.get("mzmine_version") != expected.get("mzmine_version"):
    raise ValidationError(
        "MZmine version mismatch: expected "
        f"{expected.get('mzmine_version')!r}, observed {root_attributes.get('mzmine_version')!r}"
    )

  tag_counts = inventory.get("tag_counts")
  if not isinstance(tag_counts, dict):
    raise ValidationError("Inventory tag_counts must be an object")
  if tag_counts.get("parameter") != expected.get("parameter_element_count"):
    raise ValidationError(
        "Parameter element count mismatch: expected "
        f"{expected.get('parameter_element_count')!r}, observed {tag_counts.get('parameter')!r}"
    )

  actual_steps = inventory.get("steps")
  expected_steps = expected.get("steps")
  if not isinstance(actual_steps, list) or not isinstance(expected_steps, list):
    raise ValidationError("Both inventory and expectation must contain step arrays")
  if len(actual_steps) != len(expected_steps):
    raise ValidationError(
        f"Step count mismatch: expected {len(expected_steps)}, observed {len(actual_steps)}"
    )
  if inventory.get("step_count") != len(expected_steps):
    raise ValidationError("Inventory step_count does not match its step array")

  compatibility_counts: collections.Counter[str] = collections.Counter()
  source_entries: list[dict[str, Any]] = []
  for index, (actual, wanted) in enumerate(zip(actual_steps, expected_steps), start=1):
    if not isinstance(actual, dict) or not isinstance(wanted, dict):
      raise ValidationError(f"Step {index} must be an object")
    if wanted.get("order") != index:
      raise ValidationError(f"Expected step order is not sequential at entry {index}")

    class_name = wanted.get("class")
    parameter_version = wanted.get("parameter_version")
    compatibility = wanted.get("compatibility")
    if actual.get("class") != class_name:
      raise ValidationError(
          f"Step {index} class mismatch: expected {class_name!r}, observed {actual.get('class')!r}"
      )
    attributes = actual.get("attributes")
    if not isinstance(attributes, dict) or attributes.get("parameter_version") != parameter_version:
      raise ValidationError(
          f"Step {index} parameter version mismatch: expected {parameter_version!r}, "
          f"observed {attributes.get('parameter_version') if isinstance(attributes, dict) else None!r}"
      )
    if compatibility not in ALLOWED_COMPATIBILITY:
      raise ValidationError(f"Unsupported compatibility state at step {index}: {compatibility!r}")

    source_path = java_source_path(repo_root, str(class_name))
    verify_java_source(source_path, str(class_name))
    compatibility_counts[str(compatibility)] += 1
    source_entries.append({
        "order": index,
        "class": class_name,
        "parameter_version": parameter_version,
        "compatibility": compatibility,
        "source_path": str(source_path.relative_to(repo_root)),
        "source_present": True,
    })

  inventory_classes = inventory.get("java_classes")
  expected_classes = [step["class"] for step in expected_steps]
  if inventory_classes != sorted(expected_classes):
    raise ValidationError("Inventory Java-class set differs from the expected workflow classes")
  if inventory.get("java_class_count") != len(expected_classes):
    raise ValidationError("Inventory java_class_count is inconsistent")

  return {
      "schema_version": 1,
      "dataset_id": expected.get("dataset_id"),
      "source_sha256": expected.get("source_sha256"),
      "mzmine_version": expected.get("mzmine_version"),
      "step_count": len(source_entries),
      "all_module_sources_present": True,
      "compatibility_counts": dict(sorted(compatibility_counts.items())),
      "steps": source_entries,
  }


def parse_args(argv: list[str]) -> argparse.Namespace:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--inventory", type=Path, required=True)
  parser.add_argument("--expected", type=Path, required=True)
  parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[1])
  parser.add_argument("--output", type=Path, required=True)
  return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
  args = parse_args(argv or sys.argv[1:])
  try:
    report = validate(
        load_json(args.inventory.resolve()),
        load_json(args.expected.resolve()),
        args.repo_root.resolve(),
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0
  except (ValidationError, OSError, UnicodeError) as exc:
    print(f"ERROR: {exc}", file=sys.stderr)
    return 2


if __name__ == "__main__":
  raise SystemExit(main())
