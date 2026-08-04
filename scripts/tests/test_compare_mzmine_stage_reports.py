#!/usr/bin/env python3

from __future__ import annotations

import copy
import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]


def load_module(name: str, path: Path):
  spec = importlib.util.spec_from_file_location(name, path)
  assert spec is not None and spec.loader is not None
  module = importlib.util.module_from_spec(spec)
  sys.modules[spec.name] = module
  spec.loader.exec_module(module)
  return module


COMPARATOR = load_module(
    "compare_mzmine_stage_reports",
    SCRIPTS / "compare_mzmine_stage_reports.py",
)


def make_report(repository: str, ref: str, commit: str) -> dict:
  return {
      "schema_version": 1,
      "producer": {
          "repository": repository,
          "ref": ref,
          "commit": commit,
          "application_version": "test",
          "java_version": "20",
          "operating_system": "test-os",
          "thread_count": 1,
          "command": ["java", "-jar", "mzmine.jar"],
      },
      "input": {
          "dataset_id": "zenodo-14001110-banane-30ngml-001",
          "relative_path": "zenodo/14001110/Banane_30ngmL_001.mzML",
          "size_bytes": 87090777,
          "sha256": "2eb189e193925983ddf8348a13a4c96fa7382e4e790665a204251eb036aea77d",
      },
      "settings": {
          "mapping_id": "import-centroid-v1",
          "sha256": "1" * 64,
      },
      "stages": {
          "import": {
              "record_key": "scan_key",
              "records": [
                  {
                      "scan_key": "00000001",
                      "scan_number": 1,
                      "ms_level": 1,
                      "polarity": "NEGATIVE",
                      "spectrum_type": "CENTROIDED",
                      "point_count": 2,
                      "retention_time_minutes": 0.77684957,
                      "mz_min": 100.0,
                      "mz_max": 200.0,
                      "intensity_sum": 30.0,
                      "sampled_points": [
                          {"index": 0, "mz": 100.0, "intensity": 10.0},
                          {"index": 1, "mz": 200.0, "intensity": 20.0},
                      ],
                      "precursor": None,
                  },
                  {
                      "scan_key": "00000002",
                      "scan_number": 2,
                      "ms_level": 2,
                      "polarity": "NEGATIVE",
                      "spectrum_type": "CENTROIDED",
                      "point_count": 1,
                      "retention_time_minutes": 0.8,
                      "mz_min": 150.0,
                      "mz_max": 150.0,
                      "intensity_sum": 5.0,
                      "sampled_points": [
                          {"index": 0, "mz": 150.0, "intensity": 5.0}
                      ],
                      "precursor": {
                          "mz": 300.0,
                          "charge": None,
                          "isolation_window_lower": None,
                          "isolation_window_upper": None,
                      },
                  },
              ],
              "summary": {"scan_count": 2, "total_point_count": 3},
          },
          "mass_detection": {
              "record_key": "scan_key",
              "records": [
                  {
                      "scan_key": "00000001",
                      "scan_number": 1,
                      "ms_level": 1,
                      "point_count": 2,
                      "mz_min": 100.0,
                      "mz_max": 200.0,
                      "intensity_sum": 30.0,
                      "sampled_points": [
                          {"index": 0, "mz": 100.0, "intensity": 10.0},
                          {"index": 1, "mz": 200.0, "intensity": 20.0},
                      ],
                  },
                  {
                      "scan_key": "00000002",
                      "scan_number": 2,
                      "ms_level": 2,
                      "point_count": 1,
                      "mz_min": 150.0,
                      "mz_max": 150.0,
                      "intensity_sum": 5.0,
                      "sampled_points": [
                          {"index": 0, "mz": 150.0, "intensity": 5.0}
                      ],
                  },
              ],
              "summary": {"scan_count": 2, "total_point_count": 3},
          },
      },
  }


class DifferentialStageReportTest(unittest.TestCase):

  def setUp(self) -> None:
    self.reference = make_report(
        "mzmine/mzmine",
        "v4.0.8",
        "8029f930d28c0447f0acf2bcabef0a79865ad434",
    )
    self.candidate = make_report(
        "VitorFrost/mzmine",
        "open-offline-main",
        "ecdbe340d48b091556ab99a9ce5870a86a1a1dca",
    )

  def test_equal_reports_are_deterministic(self) -> None:
    first = COMPARATOR.compare_reports(self.reference, self.candidate)
    second = COMPARATOR.compare_reports(self.reference, self.candidate)
    self.assertEqual("equivalent-within-tolerance", first["verdict"])
    self.assertEqual(0, first["difference_count"])
    self.assertEqual(first["comparison_sha256"], second["comparison_sha256"])

  def test_small_numeric_difference_within_tolerance_passes(self) -> None:
    self.candidate["stages"]["import"]["records"][0]["mz_min"] += 5e-8
    result = COMPARATOR.compare_reports(self.reference, self.candidate)
    self.assertEqual("equivalent-within-tolerance", result["verdict"])

  def test_all_differences_are_reported(self) -> None:
    self.candidate["input"]["sha256"] = "f" * 64
    self.candidate["stages"]["import"]["records"][0]["point_count"] = 3
    self.candidate["stages"]["import"]["records"][0]["mz_min"] = 101.0
    self.candidate["stages"]["mass_detection"]["records"].pop()
    result = COMPARATOR.compare_reports(self.reference, self.candidate)
    self.assertEqual("different", result["verdict"])
    kinds = [difference["kind"] for difference in result["differences"]]
    self.assertIn("provenance-mismatch", kinds)
    self.assertIn("exact-mismatch", kinds)
    self.assertIn("numeric-mismatch", kinds)
    self.assertIn("missing-record", kinds)
    self.assertGreaterEqual(result["difference_count"], 4)

  def test_unsorted_records_are_rejected(self) -> None:
    self.candidate["stages"]["import"]["records"].reverse()
    with self.assertRaisesRegex(COMPARATOR.ReportError, "sorted by scan_key"):
      COMPARATOR.compare_reports(self.reference, self.candidate)

  def test_duplicate_sample_index_is_rejected(self) -> None:
    points = self.candidate["stages"]["import"]["records"][0]["sampled_points"]
    points[1]["index"] = 0
    with self.assertRaisesRegex(COMPARATOR.ReportError, "sorted by index"):
      COMPARATOR.compare_reports(self.reference, self.candidate)


if __name__ == "__main__":
  unittest.main()
