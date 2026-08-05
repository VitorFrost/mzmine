#!/usr/bin/env python3

from __future__ import annotations

import json
import math
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TOLERANCES = ROOT / "datasets/parity/v408_import_mass_detection_tolerances_v1.json"


class V408ImportMassDetectionToleranceTest(unittest.TestCase):

  def test_contract_is_narrow_and_rt_only(self) -> None:
    value = json.loads(TOLERANCES.read_text(encoding="utf-8"))
    self.assertEqual({"retention_time_minutes"}, set(value))
    rt = value["retention_time_minutes"]
    self.assertEqual({"absolute", "relative"}, set(rt))
    self.assertEqual(1e-7, rt["absolute"])
    self.assertEqual(6e-8, rt["relative"])

  def test_relative_bound_tracks_one_half_binary32_spacing(self) -> None:
    value = json.loads(TOLERANCES.read_text(encoding="utf-8"))
    relative = value["retention_time_minutes"]["relative"]
    half_binary32_spacing = 2.0 ** -24

    # The contract allows only a sub-percent margin above the theoretical half-ULP bound.
    self.assertGreaterEqual(relative, half_binary32_spacing)
    self.assertLessEqual(relative, half_binary32_spacing * 1.007)
    self.assertTrue(math.isclose(relative, 6e-8, rel_tol=0.0, abs_tol=0.0))

  def test_contract_does_not_relax_mz_or_intensity_fields(self) -> None:
    value = json.loads(TOLERANCES.read_text(encoding="utf-8"))
    forbidden = {
        "mz_min",
        "mz_max",
        "intensity_sum",
        "sampled_points.mz",
        "sampled_points.intensity",
    }
    self.assertTrue(forbidden.isdisjoint(value))


if __name__ == "__main__":
  unittest.main()
