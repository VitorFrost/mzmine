#!/usr/bin/env python3

from __future__ import annotations

import copy
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

import jsonschema

SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = Path(__file__).resolve().parents[2]


def load_module(name: str, path: Path):
  spec = importlib.util.spec_from_file_location(name, path)
  assert spec is not None and spec.loader is not None
  module = importlib.util.module_from_spec(spec)
  sys.modules[spec.name] = module
  spec.loader.exec_module(module)
  return module


COMPARATOR = load_module(
    "compare_mzmine_adap_reports",
    SCRIPTS / "compare_mzmine_adap_reports.py",
)


def base_report() -> dict:
  points = [
      {
          "index": 0,
          "scan_key": "scan:000010",
          "scan_number": 10,
          "rt_minutes": 1.0,
          "mz": 100.123456,
          "intensity": 0.0,
      },
      {
          "index": 1,
          "scan_key": "scan:000011",
          "scan_number": 11,
          "rt_minutes": 1.1,
          "mz": 100.123457,
          "intensity": 5000.0,
      },
      {
          "index": 2,
          "scan_key": "scan:000012",
          "scan_number": 12,
          "rt_minutes": 1.2,
          "mz": 100.123455,
          "intensity": 0.0,
      },
  ]
  key = COMPARATOR.canonical_chromatogram_key(points)
  return {
      "schema_version": 1,
      "producer": {
          "repository": "mzmine/mzmine",
          "ref": "v4.0.8",
          "commit": "8" * 40,
          "application_version": "4.0.8",
          "java_version": "21",
          "operating_system": "Linux",
          "thread_count": 1,
          "command": ["oracle", "--adap"],
      },
      "input": {
          "dataset_id": "fixture",
          "relative_path": "fixture.mzML",
          "size_bytes": 123,
          "sha256": "a" * 64,
      },
      "settings": {
          "mapping_id": "adap-ms1-v1",
          "sha256": "b" * 64,
      },
      "stage": {
          "stage_name": "adap_chromatogram_builder",
          "record_key": "chromatogram_key",
          "key_contract": COMPARATOR.KEY_CONTRACT,
          "records": [
              {
                  "chromatogram_key": key,
                  "row_id": 1,
                  "row_order": 0,
                  "source_scan_keys": [p["scan_key"] for p in points],
                  "source_scan_numbers": [p["scan_number"] for p in points],
                  "point_count": 3,
                  "representative_mz": 100.123456,
                  "rt_start_minutes": 1.0,
                  "rt_end_minutes": 1.2,
                  "apex_rt_minutes": 1.1,
                  "height": 5000.0,
                  "area": 500.0,
                  "status": "DETECTED",
                  "point_series": points,
              }
          ],
          "summary": {"record_count": 1, "total_point_count": 3},
      },
  }


class CompareMZmineAdapReportsTest(unittest.TestCase):

  def test_example_satisfies_json_schema_and_runtime_validator(self) -> None:
    report = base_report()
    schema = json.loads((
        REPOSITORY / "datasets/parity/adap_chromatogram_stage_report.schema.json"
    ).read_text(encoding="utf-8"))
    jsonschema.Draft202012Validator(schema).validate(report)
    COMPARATOR.validate_report(report)

  def test_equal_reports_have_zero_differences(self) -> None:
    reference = base_report()
    candidate = copy.deepcopy(reference)
    candidate["producer"]["repository"] = "VitorFrost/mzmine"
    candidate["producer"]["commit"] = "7" * 40
    candidate["producer"]["java_version"] = "20"
    result = COMPARATOR.compare_reports(reference, candidate)
    self.assertEqual("equivalent-within-tolerance", result["verdict"])
    self.assertEqual(0, result["difference_count"])

  def test_row_order_and_id_are_representation_only(self) -> None:
    reference = base_report()
    candidate = copy.deepcopy(reference)
    candidate["stage"]["records"][0]["row_id"] = 42
    candidate["stage"]["records"][0]["row_order"] = 9
    result = COMPARATOR.compare_reports(reference, candidate)
    self.assertEqual(
        "scientific-content-equivalent-representation-different", result["verdict"]
    )
    self.assertEqual(0, result["scientific_difference_count"])
    self.assertEqual(2, result["representation_difference_count"])
    self.assertEqual(
        {"row_id", "row_order"},
        {difference["field"] for difference in result["differences"]},
    )

  def test_scientific_numeric_difference_is_not_hidden(self) -> None:
    reference = base_report()
    candidate = copy.deepcopy(reference)
    candidate["stage"]["records"][0]["height"] = 5100.0
    result = COMPARATOR.compare_reports(reference, candidate)
    self.assertEqual("different", result["verdict"])
    self.assertEqual(1, result["scientific_difference_count"])
    self.assertEqual("height", result["differences"][0]["field"])

  def test_known_binary32_scale_rt_difference_can_pass_frozen_contract(self) -> None:
    reference = base_report()
    candidate = copy.deepcopy(reference)
    record = candidate["stage"]["records"][0]
    record["rt_start_minutes"] += 5e-8
    record["rt_end_minutes"] += 5e-8
    record["apex_rt_minutes"] += 5e-8
    for point in record["point_series"]:
      point["rt_minutes"] += 5e-8
    result = COMPARATOR.compare_reports(reference, candidate)
    self.assertEqual("equivalent-within-tolerance", result["verdict"])

  def test_point_intensity_difference_is_scientific(self) -> None:
    reference = base_report()
    candidate = copy.deepcopy(reference)
    candidate["stage"]["records"][0]["point_series"][1]["intensity"] += 1.0
    result = COMPARATOR.compare_reports(reference, candidate)
    self.assertEqual("different", result["verdict"])
    self.assertEqual(1, result["scientific_difference_count"])
    self.assertEqual("point_series[1].intensity", result["differences"][0]["field"])

  def test_key_is_fail_closed_against_membership_or_source_mz_change(self) -> None:
    report = base_report()
    report["stage"]["records"][0]["point_series"][1]["mz"] += 0.001
    with self.assertRaisesRegex(COMPARATOR.ReportError, "chromatogram_key does not match"):
      COMPARATOR.validate_report(report)

  def test_membership_arrays_must_match_point_series(self) -> None:
    report = base_report()
    report["stage"]["records"][0]["source_scan_keys"][1] = "scan:999999"
    with self.assertRaisesRegex(COMPARATOR.ReportError, "source_scan_keys must equal"):
      COMPARATOR.validate_report(report)

  def test_summary_must_match_actual_records(self) -> None:
    report = base_report()
    report["stage"]["summary"]["total_point_count"] = 4
    with self.assertRaisesRegex(COMPARATOR.ReportError, "summary.total_point_count"):
      COMPARATOR.validate_report(report)

  def test_tolerance_contract_rejects_missing_or_unknown_fields(self) -> None:
    contract = copy.deepcopy(COMPARATOR.DEFAULT_TOLERANCES)
    contract.pop("area")
    contract["unexpected"] = {"absolute": 0.0, "relative": 0.0}
    with tempfile.TemporaryDirectory() as directory:
      path = Path(directory) / "tolerances.json"
      path.write_text(json.dumps(contract), encoding="utf-8")
      with self.assertRaises(COMPARATOR.ReportError):
        COMPARATOR.load_tolerances(path)

  def test_committed_tolerance_contract_loads_exactly(self) -> None:
    path = REPOSITORY / "datasets/parity/v408_adap_chromatogram_tolerances_v1.json"
    loaded = COMPARATOR.load_tolerances(path)
    self.assertEqual(set(COMPARATOR.DEFAULT_TOLERANCES), set(loaded))
    self.assertEqual(6e-8, loaded["point_series.rt_minutes"]["relative"])
    self.assertEqual(1e-9, loaded["point_series.mz"]["relative"])


if __name__ == "__main__":
  unittest.main()
