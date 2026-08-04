#!/usr/bin/env python3
"""Compare normalized MZmine import and mass-detection stage reports.

The comparator is deliberately independent of either MZmine code line. Each producer must emit the
same versioned report contract. This tool then validates provenance, compares every record before
returning failure, and writes a deterministic machine-readable difference report.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
from pathlib import Path
from typing import Any

SHA40 = re.compile(r"^[0-9a-f]{40}$")
SHA64 = re.compile(r"^[0-9a-f]{64}$")
SUPPORTED_STAGES = ("import", "mass_detection")

DEFAULT_TOLERANCES: dict[str, dict[str, float]] = {
    "retention_time_minutes": {"absolute": 1e-7, "relative": 0.0},
    "mz_min": {"absolute": 1e-7, "relative": 1e-9},
    "mz_max": {"absolute": 1e-7, "relative": 1e-9},
    "intensity_sum": {"absolute": 1e-6, "relative": 1e-9},
    "sampled_points.mz": {"absolute": 1e-7, "relative": 1e-9},
    "sampled_points.intensity": {"absolute": 1e-6, "relative": 1e-9},
}
EXACT_FIELDS = (
    "scan_key",
    "scan_number",
    "ms_level",
    "point_count",
    "polarity",
    "spectrum_type",
    "precursor",
)


class ReportError(ValueError):
  """Raised when an input report is malformed or cannot be compared safely."""


def _require(condition: bool, message: str) -> None:
  if not condition:
    raise ReportError(message)


def load_report(path: Path) -> dict[str, Any]:
  try:
    value = json.loads(path.read_text(encoding="utf-8"))
  except FileNotFoundError as exc:
    raise ReportError(f"missing report: {path}") from exc
  except json.JSONDecodeError as exc:
    raise ReportError(f"invalid JSON in {path}: {exc}") from exc
  _require(isinstance(value, dict), f"report must be a JSON object: {path}")
  validate_report(value, str(path))
  return value


def _nonempty_string(value: Any) -> bool:
  return isinstance(value, str) and bool(value.strip())


def _finite_or_none(value: Any) -> bool:
  return value is None or (
      isinstance(value, (int, float))
      and not isinstance(value, bool)
      and math.isfinite(float(value))
  )


def validate_report(report: dict[str, Any], label: str = "report") -> None:
  _require(report.get("schema_version") == 1, f"{label}: schema_version must be 1")

  producer = report.get("producer")
  _require(isinstance(producer, dict), f"{label}: producer must be an object")
  for field in (
      "repository",
      "ref",
      "commit",
      "application_version",
      "java_version",
      "operating_system",
  ):
    _require(_nonempty_string(producer.get(field)), f"{label}: producer.{field} is required")
  _require(SHA40.fullmatch(producer["commit"]) is not None,
           f"{label}: producer.commit must be a lowercase 40-character SHA")
  _require(isinstance(producer.get("thread_count"), int) and producer["thread_count"] >= 1,
           f"{label}: producer.thread_count must be a positive integer")
  command = producer.get("command")
  _require(isinstance(command, list) and command and all(_nonempty_string(v) for v in command),
           f"{label}: producer.command must be a non-empty string array")

  input_record = report.get("input")
  _require(isinstance(input_record, dict), f"{label}: input must be an object")
  for field in ("dataset_id", "relative_path"):
    _require(_nonempty_string(input_record.get(field)), f"{label}: input.{field} is required")
  _require(isinstance(input_record.get("size_bytes"), int) and input_record["size_bytes"] > 0,
           f"{label}: input.size_bytes must be positive")
  _require(isinstance(input_record.get("sha256"), str)
           and SHA64.fullmatch(input_record["sha256"]) is not None,
           f"{label}: input.sha256 must be a lowercase SHA-256")

  settings = report.get("settings")
  _require(isinstance(settings, dict), f"{label}: settings must be an object")
  _require(_nonempty_string(settings.get("mapping_id")),
           f"{label}: settings.mapping_id is required")
  _require(isinstance(settings.get("sha256"), str)
           and SHA64.fullmatch(settings["sha256"]) is not None,
           f"{label}: settings.sha256 must be a lowercase SHA-256")

  stages = report.get("stages")
  _require(isinstance(stages, dict) and stages, f"{label}: stages must be a non-empty object")
  unknown_stages = sorted(set(stages) - set(SUPPORTED_STAGES))
  _require(not unknown_stages, f"{label}: unsupported stages: {', '.join(unknown_stages)}")

  for stage_name, stage in stages.items():
    _require(isinstance(stage, dict), f"{label}: stage {stage_name} must be an object")
    _require(stage.get("record_key") == "scan_key",
             f"{label}: stage {stage_name} record_key must be scan_key")
    records = stage.get("records")
    _require(isinstance(records, list), f"{label}: stage {stage_name}.records must be an array")
    _require(isinstance(stage.get("summary"), dict),
             f"{label}: stage {stage_name}.summary must be an object")

    seen: set[str] = set()
    previous_key: str | None = None
    for index, record in enumerate(records):
      prefix = f"{label}: stage {stage_name} record {index}"
      _require(isinstance(record, dict), f"{prefix} must be an object")
      key = record.get("scan_key")
      _require(_nonempty_string(key), f"{prefix}.scan_key is required")
      _require(key not in seen, f"{label}: stage {stage_name} duplicate scan_key {key}")
      if previous_key is not None:
        _require(previous_key < key,
                 f"{label}: stage {stage_name} records must be sorted by scan_key")
      previous_key = key
      seen.add(key)

      _require(isinstance(record.get("scan_number"), int) and record["scan_number"] >= 0,
               f"{prefix}.scan_number must be non-negative")
      _require(isinstance(record.get("ms_level"), int) and record["ms_level"] >= 1,
               f"{prefix}.ms_level must be positive")
      _require(isinstance(record.get("point_count"), int) and record["point_count"] >= 0,
               f"{prefix}.point_count must be non-negative")

      if "polarity" in record:
        _require(record["polarity"] in {"POSITIVE", "NEGATIVE", "UNKNOWN"},
                 f"{prefix}.polarity is invalid")
      if "spectrum_type" in record:
        _require(record["spectrum_type"] in {"CENTROIDED", "PROFILE", "THRESHOLDED", "UNKNOWN"},
                 f"{prefix}.spectrum_type is invalid")
      for numeric_field in ("retention_time_minutes", "mz_min", "mz_max", "intensity_sum"):
        if numeric_field in record:
          _require(_finite_or_none(record[numeric_field]),
                   f"{prefix}.{numeric_field} must be finite or null")

      sampled_points = record.get("sampled_points", [])
      _require(isinstance(sampled_points, list), f"{prefix}.sampled_points must be an array")
      previous_index = -1
      for point_index, point in enumerate(sampled_points):
        _require(isinstance(point, dict), f"{prefix}.sampled_points[{point_index}] must be an object")
        index_value = point.get("index")
        _require(isinstance(index_value, int) and index_value >= 0,
                 f"{prefix}.sampled_points[{point_index}].index is invalid")
        _require(index_value > previous_index,
                 f"{prefix}.sampled_points must be sorted by index")
        previous_index = index_value
        _require(_finite_or_none(point.get("mz")) and point.get("mz") is not None,
                 f"{prefix}.sampled_points[{point_index}].mz must be finite")
        _require(_finite_or_none(point.get("intensity")) and point.get("intensity") is not None,
                 f"{prefix}.sampled_points[{point_index}].intensity must be finite")


def _numeric_equal(left: float, right: float, tolerance: dict[str, float]) -> tuple[bool, float, float]:
  delta = abs(left - right)
  scale = max(abs(left), abs(right))
  allowed = max(tolerance["absolute"], tolerance["relative"] * scale)
  return delta <= allowed, delta, allowed


def _difference(
    stage: str,
    scan_key: str | None,
    field: str,
    kind: str,
    reference: Any,
    candidate: Any,
    **extra: Any,
) -> dict[str, Any]:
  value = {
      "stage": stage,
      "scan_key": scan_key,
      "field": field,
      "kind": kind,
      "reference": reference,
      "candidate": candidate,
  }
  value.update(extra)
  return value


def _index_records(stage: dict[str, Any]) -> dict[str, dict[str, Any]]:
  return {record["scan_key"]: record for record in stage["records"]}


def _compare_sampled_points(
    stage_name: str,
    scan_key: str,
    reference: list[dict[str, Any]],
    candidate: list[dict[str, Any]],
    tolerances: dict[str, dict[str, float]],
    differences: list[dict[str, Any]],
) -> None:
  reference_by_index = {point["index"]: point for point in reference}
  candidate_by_index = {point["index"]: point for point in candidate}
  for point_index in sorted(set(reference_by_index) | set(candidate_by_index)):
    left = reference_by_index.get(point_index)
    right = candidate_by_index.get(point_index)
    field_prefix = f"sampled_points[{point_index}]"
    if left is None or right is None:
      differences.append(_difference(
          stage_name, scan_key, field_prefix, "missing-sampled-point", left, right
      ))
      continue
    for value_name in ("mz", "intensity"):
      tolerance_key = f"sampled_points.{value_name}"
      equal, delta, allowed = _numeric_equal(
          float(left[value_name]), float(right[value_name]), tolerances[tolerance_key]
      )
      if not equal:
        differences.append(_difference(
            stage_name,
            scan_key,
            f"{field_prefix}.{value_name}",
            "numeric-mismatch",
            left[value_name],
            right[value_name],
            absolute_difference=delta,
            allowed_difference=allowed,
        ))


def compare_reports(
    reference: dict[str, Any],
    candidate: dict[str, Any],
    tolerances: dict[str, dict[str, float]] | None = None,
) -> dict[str, Any]:
  validate_report(reference, "reference")
  validate_report(candidate, "candidate")
  effective_tolerances = {key: dict(value) for key, value in DEFAULT_TOLERANCES.items()}
  if tolerances:
    for key, value in tolerances.items():
      _require(key in effective_tolerances, f"unsupported tolerance field: {key}")
      _require(isinstance(value, dict), f"tolerance {key} must be an object")
      absolute = value.get("absolute")
      relative = value.get("relative")
      _require(isinstance(absolute, (int, float)) and absolute >= 0,
               f"tolerance {key}.absolute must be non-negative")
      _require(isinstance(relative, (int, float)) and relative >= 0,
               f"tolerance {key}.relative must be non-negative")
      effective_tolerances[key] = {"absolute": float(absolute), "relative": float(relative)}

  differences: list[dict[str, Any]] = []
  for field in ("dataset_id", "relative_path", "size_bytes", "sha256"):
    if reference["input"][field] != candidate["input"][field]:
      differences.append(_difference(
          "provenance", None, f"input.{field}", "provenance-mismatch",
          reference["input"][field], candidate["input"][field]
      ))
  for field in ("mapping_id", "sha256"):
    if reference["settings"][field] != candidate["settings"][field]:
      differences.append(_difference(
          "provenance", None, f"settings.{field}", "settings-mismatch",
          reference["settings"][field], candidate["settings"][field]
      ))

  for stage_name in SUPPORTED_STAGES:
    left_stage = reference["stages"].get(stage_name)
    right_stage = candidate["stages"].get(stage_name)
    if left_stage is None or right_stage is None:
      if left_stage is not None or right_stage is not None:
        differences.append(_difference(
            stage_name, None, "stage", "missing-stage",
            left_stage is not None, right_stage is not None
        ))
      continue

    left_records = _index_records(left_stage)
    right_records = _index_records(right_stage)
    for scan_key in sorted(set(left_records) | set(right_records)):
      left = left_records.get(scan_key)
      right = right_records.get(scan_key)
      if left is None or right is None:
        differences.append(_difference(
            stage_name, scan_key, "record", "missing-record",
            left is not None, right is not None
        ))
        continue

      for field in EXACT_FIELDS:
        left_value = left.get(field)
        right_value = right.get(field)
        if left_value != right_value:
          differences.append(_difference(
              stage_name, scan_key, field, "exact-mismatch", left_value, right_value
          ))

      for field in ("retention_time_minutes", "mz_min", "mz_max", "intensity_sum"):
        left_value = left.get(field)
        right_value = right.get(field)
        if left_value is None or right_value is None:
          if left_value != right_value:
            differences.append(_difference(
                stage_name, scan_key, field, "null-mismatch", left_value, right_value
            ))
          continue
        equal, delta, allowed = _numeric_equal(
            float(left_value), float(right_value), effective_tolerances[field]
        )
        if not equal:
          differences.append(_difference(
              stage_name,
              scan_key,
              field,
              "numeric-mismatch",
              left_value,
              right_value,
              absolute_difference=delta,
              allowed_difference=allowed,
          ))

      _compare_sampled_points(
          stage_name,
          scan_key,
          left.get("sampled_points", []),
          right.get("sampled_points", []),
          effective_tolerances,
          differences,
      )

  stage_counts = {
      stage_name: {
          "reference": len(reference["stages"].get(stage_name, {}).get("records", [])),
          "candidate": len(candidate["stages"].get(stage_name, {}).get("records", [])),
      }
      for stage_name in SUPPORTED_STAGES
      if stage_name in reference["stages"] or stage_name in candidate["stages"]
  }
  report = {
      "schema_version": 1,
      "verdict": "equivalent-within-tolerance" if not differences else "different",
      "reference": {
          "repository": reference["producer"]["repository"],
          "ref": reference["producer"]["ref"],
          "commit": reference["producer"]["commit"],
      },
      "candidate": {
          "repository": candidate["producer"]["repository"],
          "ref": candidate["producer"]["ref"],
          "commit": candidate["producer"]["commit"],
      },
      "input_sha256": reference["input"]["sha256"],
      "settings_sha256": reference["settings"]["sha256"],
      "stage_record_counts": stage_counts,
      "tolerances": effective_tolerances,
      "difference_count": len(differences),
      "differences": differences,
  }
  canonical = json.dumps(report, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
  report["comparison_sha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
  return report


def _load_tolerances(path: Path | None) -> dict[str, dict[str, float]] | None:
  if path is None:
    return None
  try:
    value = json.loads(path.read_text(encoding="utf-8"))
  except (OSError, json.JSONDecodeError) as exc:
    raise ReportError(f"cannot load tolerances from {path}: {exc}") from exc
  _require(isinstance(value, dict), "tolerance file must contain an object")
  return value


def build_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("reference", type=Path)
  parser.add_argument("candidate", type=Path)
  parser.add_argument("--tolerances", type=Path)
  parser.add_argument("--output", type=Path, required=True)
  return parser


def main() -> int:
  args = build_parser().parse_args()
  try:
    result = compare_reports(
        load_report(args.reference),
        load_report(args.candidate),
        _load_tolerances(args.tolerances),
    )
  except ReportError as exc:
    print(f"Differential comparison failed before comparison: {exc}")
    return 2

  args.output.parent.mkdir(parents=True, exist_ok=True)
  args.output.write_text(
      json.dumps(result, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
      encoding="utf-8",
  )
  print(
      f"{result['verdict']}: {result['difference_count']} differences; "
      f"report={args.output}; sha256={result['comparison_sha256']}"
  )
  return 0 if result["difference_count"] == 0 else 1


if __name__ == "__main__":
  raise SystemExit(main())
