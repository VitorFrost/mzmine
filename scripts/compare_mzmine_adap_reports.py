#!/usr/bin/env python3
"""Compare normalized MZmine ADAP Chromatogram Builder reports.

This comparator is independent of both MZmine code lines. Scientific chromatogram content is keyed
by a stable source-membership key, while final row order and row IDs are compared separately as
representation fields. This prevents the known v3.9 -> v4.0.8 sorting change from creating false
missing/extra chromatograms while still preserving that difference explicitly.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import struct
from pathlib import Path
from typing import Any

SHA40 = re.compile(r"^[0-9a-f]{40}$")
SHA64 = re.compile(r"^[0-9a-f]{64}$")
CHROMATOGRAM_KEY = re.compile(r"^adap:[0-9a-f]{64}$")
KEY_CONTRACT = "source-scan-key-and-source-mz-sha256-v1"

DEFAULT_TOLERANCES: dict[str, dict[str, float]] = {
    "representative_mz": {"absolute": 1e-7, "relative": 1e-9},
    "rt_start_minutes": {"absolute": 1e-7, "relative": 6e-8},
    "rt_end_minutes": {"absolute": 1e-7, "relative": 6e-8},
    "apex_rt_minutes": {"absolute": 1e-7, "relative": 6e-8},
    "height": {"absolute": 1e-6, "relative": 1e-9},
    "area": {"absolute": 1e-6, "relative": 1e-9},
    "point_series.rt_minutes": {"absolute": 1e-7, "relative": 6e-8},
    "point_series.mz": {"absolute": 1e-7, "relative": 1e-9},
    "point_series.intensity": {"absolute": 1e-6, "relative": 1e-9},
}

CONTENT_EXACT_FIELDS = (
    "source_scan_keys",
    "source_scan_numbers",
    "point_count",
    "status",
)
REPRESENTATION_EXACT_FIELDS = ("row_order", "row_id")
NUMERIC_FIELDS = (
    "representative_mz",
    "rt_start_minutes",
    "rt_end_minutes",
    "apex_rt_minutes",
    "height",
    "area",
)


class ReportError(ValueError):
  """Raised when a report or tolerance contract cannot be compared safely."""


def _require(condition: bool, message: str) -> None:
  if not condition:
    raise ReportError(message)


def _nonempty_string(value: Any) -> bool:
  return isinstance(value, str) and bool(value.strip())


def _finite(value: Any) -> bool:
  return (
      isinstance(value, (int, float))
      and not isinstance(value, bool)
      and math.isfinite(float(value))
  )


def _finite_or_none(value: Any) -> bool:
  return value is None or _finite(value)


def canonical_chromatogram_key(point_series: list[dict[str, Any]]) -> str:
  """Return a language-independent key from ordered source scan identity and source m/z bits.

  Canonical payload v1 is one UTF-8 line per point:

      <scan_key> TAB <16 lowercase hex chars of IEEE-754 binary64 m/z> LF

  The key is ``adap:`` plus the SHA-256 of those bytes. Java producers can reproduce the m/z token
  with ``Double.doubleToLongBits`` formatted as unsigned lowercase hexadecimal padded to 16 chars.
  """

  digest = hashlib.sha256()
  for point in point_series:
    scan_key = point["scan_key"]
    mz_bits = struct.pack(">d", float(point["mz"])).hex()
    digest.update(scan_key.encode("utf-8"))
    digest.update(b"\t")
    digest.update(mz_bits.encode("ascii"))
    digest.update(b"\n")
  return f"adap:{digest.hexdigest()}"


def _canonical_sha256(value: Any) -> str:
  payload = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
  return hashlib.sha256(payload.encode("utf-8")).hexdigest()


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


def load_tolerances(path: Path | None) -> dict[str, dict[str, float]]:
  tolerances = {key: dict(value) for key, value in DEFAULT_TOLERANCES.items()}
  if path is None:
    return tolerances

  try:
    value = json.loads(path.read_text(encoding="utf-8"))
  except FileNotFoundError as exc:
    raise ReportError(f"missing tolerance contract: {path}") from exc
  except json.JSONDecodeError as exc:
    raise ReportError(f"invalid tolerance JSON in {path}: {exc}") from exc
  _require(isinstance(value, dict), "tolerance contract must be an object")
  unknown = sorted(set(value) - set(DEFAULT_TOLERANCES))
  _require(not unknown, f"unsupported tolerance fields: {', '.join(unknown)}")
  missing = sorted(set(DEFAULT_TOLERANCES) - set(value))
  _require(not missing, f"missing tolerance fields: {', '.join(missing)}")

  for field, entry in value.items():
    _require(isinstance(entry, dict), f"tolerance {field} must be an object")
    _require(set(entry) == {"absolute", "relative"},
             f"tolerance {field} must contain only absolute and relative")
    absolute = entry["absolute"]
    relative = entry["relative"]
    _require(_finite(absolute) and float(absolute) >= 0,
             f"tolerance {field}.absolute must be finite and non-negative")
    _require(_finite(relative) and float(relative) >= 0,
             f"tolerance {field}.relative must be finite and non-negative")
    tolerances[field] = {"absolute": float(absolute), "relative": float(relative)}
  return tolerances


def validate_report(report: dict[str, Any], label: str = "report") -> None:
  _require(report.get("schema_version") == 1, f"{label}: schema_version must be 1")

  producer = report.get("producer")
  _require(isinstance(producer, dict), f"{label}: producer must be an object")
  for field in (
      "repository", "ref", "commit", "application_version", "java_version", "operating_system"
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

  stage = report.get("stage")
  _require(isinstance(stage, dict), f"{label}: stage must be an object")
  _require(stage.get("stage_name") == "adap_chromatogram_builder",
           f"{label}: stage.stage_name must be adap_chromatogram_builder")
  _require(stage.get("record_key") == "chromatogram_key",
           f"{label}: stage.record_key must be chromatogram_key")
  _require(stage.get("key_contract") == KEY_CONTRACT,
           f"{label}: stage.key_contract must be {KEY_CONTRACT}")
  records = stage.get("records")
  summary = stage.get("summary")
  _require(isinstance(records, list), f"{label}: stage.records must be an array")
  _require(isinstance(summary, dict), f"{label}: stage.summary must be an object")

  seen: set[str] = set()
  previous_key: str | None = None
  total_points = 0
  for record_index, record in enumerate(records):
    prefix = f"{label}: record {record_index}"
    _require(isinstance(record, dict), f"{prefix} must be an object")
    key = record.get("chromatogram_key")
    _require(isinstance(key, str) and CHROMATOGRAM_KEY.fullmatch(key) is not None,
             f"{prefix}.chromatogram_key is invalid")
    _require(key not in seen, f"{label}: duplicate chromatogram_key {key}")
    if previous_key is not None:
      _require(previous_key < key, f"{label}: records must be sorted by chromatogram_key")
    previous_key = key
    seen.add(key)

    _require(isinstance(record.get("row_id"), int) and record["row_id"] >= 1,
             f"{prefix}.row_id must be positive")
    _require(isinstance(record.get("row_order"), int) and record["row_order"] >= 0,
             f"{prefix}.row_order must be non-negative")
    _require(isinstance(record.get("point_count"), int) and record["point_count"] >= 1,
             f"{prefix}.point_count must be positive")
    _require(_nonempty_string(record.get("status")), f"{prefix}.status is required")

    for field in ("representative_mz", "rt_start_minutes", "rt_end_minutes"):
      _require(_finite(record.get(field)), f"{prefix}.{field} must be finite")
    for field in ("apex_rt_minutes", "height", "area"):
      _require(_finite_or_none(record.get(field)), f"{prefix}.{field} must be finite or null")
    _require(record["rt_start_minutes"] <= record["rt_end_minutes"],
             f"{prefix}: rt_start_minutes must not exceed rt_end_minutes")
    if record["apex_rt_minutes"] is not None:
      _require(record["rt_start_minutes"] <= record["apex_rt_minutes"] <= record["rt_end_minutes"],
               f"{prefix}: apex_rt_minutes must be within the RT range")

    scan_keys = record.get("source_scan_keys")
    scan_numbers = record.get("source_scan_numbers")
    points = record.get("point_series")
    _require(isinstance(scan_keys, list) and scan_keys
             and all(_nonempty_string(v) for v in scan_keys),
             f"{prefix}.source_scan_keys must be a non-empty string array")
    _require(isinstance(scan_numbers, list) and scan_numbers
             and all(isinstance(v, int) and v >= 0 for v in scan_numbers),
             f"{prefix}.source_scan_numbers must be a non-empty non-negative integer array")
    _require(isinstance(points, list) and points,
             f"{prefix}.point_series must be a non-empty array")
    _require(len(scan_keys) == len(scan_numbers) == len(points) == record["point_count"],
             f"{prefix}: source membership, point_series and point_count lengths must match")

    point_scan_keys: list[str] = []
    point_scan_numbers: list[int] = []
    for point_index, point in enumerate(points):
      point_prefix = f"{prefix}.point_series[{point_index}]"
      _require(isinstance(point, dict), f"{point_prefix} must be an object")
      _require(point.get("index") == point_index,
               f"{point_prefix}.index must equal its zero-based position")
      _require(_nonempty_string(point.get("scan_key")), f"{point_prefix}.scan_key is required")
      _require(isinstance(point.get("scan_number"), int) and point["scan_number"] >= 0,
               f"{point_prefix}.scan_number must be non-negative")
      for field in ("rt_minutes", "mz", "intensity"):
        _require(_finite(point.get(field)), f"{point_prefix}.{field} must be finite")
      point_scan_keys.append(point["scan_key"])
      point_scan_numbers.append(point["scan_number"])

    _require(point_scan_keys == scan_keys,
             f"{prefix}: source_scan_keys must equal ordered point_series scan keys")
    _require(point_scan_numbers == scan_numbers,
             f"{prefix}: source_scan_numbers must equal ordered point_series scan numbers")
    expected_key = canonical_chromatogram_key(points)
    _require(key == expected_key,
             f"{prefix}: chromatogram_key does not match {KEY_CONTRACT}: expected {expected_key}")
    total_points += len(points)

  _require(summary.get("record_count") == len(records),
           f"{label}: summary.record_count must equal record count")
  _require(summary.get("total_point_count") == total_points,
           f"{label}: summary.total_point_count must equal total point count")


def _numeric_equal(left: float, right: float, tolerance: dict[str, float]) -> tuple[bool, float, float]:
  delta = abs(left - right)
  scale = max(abs(left), abs(right))
  allowed = max(tolerance["absolute"], tolerance["relative"] * scale)
  return delta <= allowed, delta, allowed


def _difference(
    domain: str,
    chromatogram_key: str | None,
    field: str,
    kind: str,
    reference: Any,
    candidate: Any,
    **extra: Any,
) -> dict[str, Any]:
  value: dict[str, Any] = {
      "domain": domain,
      "stage": "adap_chromatogram_builder" if domain != "provenance" else "provenance",
      "chromatogram_key": chromatogram_key,
      "field": field,
      "kind": kind,
      "reference": reference,
      "candidate": candidate,
  }
  value.update(extra)
  return value


def _compare_numeric(
    key: str,
    field: str,
    left: Any,
    right: Any,
    tolerances: dict[str, dict[str, float]],
    differences: list[dict[str, Any]],
) -> None:
  if left is None or right is None:
    if left != right:
      differences.append(_difference(
          "scientific", key, field, "null-mismatch", left, right
      ))
    return
  equal, delta, allowed = _numeric_equal(float(left), float(right), tolerances[field])
  if not equal:
    differences.append(_difference(
        "scientific", key, field, "numeric-mismatch", left, right,
        absolute_difference=delta, allowed_difference=allowed,
    ))


def compare_reports(
    reference: dict[str, Any],
    candidate: dict[str, Any],
    tolerances: dict[str, dict[str, float]] | None = None,
) -> dict[str, Any]:
  validate_report(reference, "reference")
  validate_report(candidate, "candidate")
  effective_tolerances = (
      {key: dict(value) for key, value in DEFAULT_TOLERANCES.items()}
      if tolerances is None else {key: dict(value) for key, value in tolerances.items()}
  )
  _require(set(effective_tolerances) == set(DEFAULT_TOLERANCES),
           "effective tolerance fields must exactly match the ADAP contract")

  differences: list[dict[str, Any]] = []

  for field in ("dataset_id", "relative_path", "size_bytes", "sha256"):
    left = reference["input"][field]
    right = candidate["input"][field]
    if left != right:
      differences.append(_difference(
          "provenance", None, f"input.{field}", "provenance-mismatch", left, right
      ))
  for field in ("mapping_id", "sha256"):
    left = reference["settings"][field]
    right = candidate["settings"][field]
    if left != right:
      differences.append(_difference(
          "provenance", None, f"settings.{field}", "settings-mismatch", left, right
      ))

  left_records = {record["chromatogram_key"]: record for record in reference["stage"]["records"]}
  right_records = {record["chromatogram_key"]: record for record in candidate["stage"]["records"]}

  for key in sorted(set(left_records) | set(right_records)):
    left = left_records.get(key)
    right = right_records.get(key)
    if left is None or right is None:
      differences.append(_difference(
          "scientific", key, "record", "missing-record", left is not None, right is not None
      ))
      continue

    for field in CONTENT_EXACT_FIELDS:
      if left[field] != right[field]:
        differences.append(_difference(
            "scientific", key, field, "exact-mismatch", left[field], right[field]
        ))

    for field in REPRESENTATION_EXACT_FIELDS:
      if left[field] != right[field]:
        differences.append(_difference(
            "representation", key, field, "exact-mismatch", left[field], right[field]
        ))

    for field in NUMERIC_FIELDS:
      _compare_numeric(key, field, left[field], right[field], effective_tolerances, differences)

    left_points = left["point_series"]
    right_points = right["point_series"]
    for index in range(max(len(left_points), len(right_points))):
      left_point = left_points[index] if index < len(left_points) else None
      right_point = right_points[index] if index < len(right_points) else None
      prefix = f"point_series[{index}]"
      if left_point is None or right_point is None:
        differences.append(_difference(
            "scientific", key, prefix, "missing-point", left_point, right_point
        ))
        continue
      for field in ("index", "scan_key", "scan_number"):
        if left_point[field] != right_point[field]:
          differences.append(_difference(
              "scientific", key, f"{prefix}.{field}", "exact-mismatch",
              left_point[field], right_point[field]
          ))
      for field in ("rt_minutes", "mz", "intensity"):
        tolerance_field = f"point_series.{field}"
        equal, delta, allowed = _numeric_equal(
            float(left_point[field]), float(right_point[field]), effective_tolerances[tolerance_field]
        )
        if not equal:
          differences.append(_difference(
              "scientific", key, f"{prefix}.{field}", "numeric-mismatch",
              left_point[field], right_point[field],
              absolute_difference=delta, allowed_difference=allowed,
          ))

  for field in ("record_count", "total_point_count"):
    left = reference["stage"]["summary"][field]
    right = candidate["stage"]["summary"][field]
    if left != right:
      differences.append(_difference(
          "scientific", None, f"summary.{field}", "exact-mismatch", left, right
      ))

  counts = {
      domain: sum(1 for difference in differences if difference["domain"] == domain)
      for domain in ("provenance", "scientific", "representation")
  }
  if counts["provenance"] or counts["scientific"]:
    verdict = "different"
  elif counts["representation"]:
    verdict = "scientific-content-equivalent-representation-different"
  else:
    verdict = "equivalent-within-tolerance"

  result = {
      "schema_version": 1,
      "verdict": verdict,
      "reference": {
        "producer_commit": reference["producer"]["commit"],
        "report_sha256": _canonical_sha256(reference),
      },
      "candidate": {
        "producer_commit": candidate["producer"]["commit"],
        "report_sha256": _canonical_sha256(candidate),
      },
      "tolerances": effective_tolerances,
      "difference_count": len(differences),
      "provenance_difference_count": counts["provenance"],
      "scientific_difference_count": counts["scientific"],
      "representation_difference_count": counts["representation"],
      "reference_record_count": len(reference["stage"]["records"]),
      "candidate_record_count": len(candidate["stage"]["records"]),
      "differences": differences,
  }
  result["comparison_sha256"] = _canonical_sha256(result)
  return result


def parse_args() -> argparse.Namespace:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--reference", type=Path, required=True)
  parser.add_argument("--candidate", type=Path, required=True)
  parser.add_argument("--tolerances", type=Path)
  parser.add_argument("--output", type=Path, required=True)
  return parser.parse_args()


def main() -> int:
  args = parse_args()
  try:
    reference = load_report(args.reference)
    candidate = load_report(args.candidate)
    tolerances = load_tolerances(args.tolerances)
    result = compare_reports(reference, candidate, tolerances)
  except ReportError as exc:
    print(f"Invalid ADAP differential input: {exc}")
    return 2

  args.output.parent.mkdir(parents=True, exist_ok=True)
  args.output.write_text(
      json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
  )
  print(json.dumps({
      "verdict": result["verdict"],
      "difference_count": result["difference_count"],
      "scientific_difference_count": result["scientific_difference_count"],
      "representation_difference_count": result["representation_difference_count"],
      "comparison_sha256": result["comparison_sha256"],
  }, indent=2, sort_keys=True))
  return 0 if result["difference_count"] == 0 else 1


if __name__ == "__main__":
  raise SystemExit(main())
