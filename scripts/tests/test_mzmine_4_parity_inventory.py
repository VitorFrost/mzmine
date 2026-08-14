#!/usr/bin/env python3

from __future__ import annotations

import copy
import importlib.util
import json
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = Path(__file__).resolve().parents[2]


def load_module(name: str, path: Path):
  spec = importlib.util.spec_from_file_location(name, path)
  assert spec is not None and spec.loader is not None
  module = importlib.util.module_from_spec(spec)
  sys.modules[spec.name] = module
  spec.loader.exec_module(module)
  return module


VALIDATOR = load_module(
    "validate_mzmine_4_parity_inventory",
    SCRIPTS / "validate_mzmine_4_parity_inventory.py",
)


class MZmine4ParityInventoryTest(unittest.TestCase):

  @classmethod
  def setUpClass(cls) -> None:
    cls.inventory_path = (
        REPOSITORY / "datasets/parity/mzmine_v408_lcms_core_inventory.json"
    )
    cls.manifest_path = REPOSITORY / "datasets/public_validation_manifest.json"
    cls.inventory = json.loads(cls.inventory_path.read_text(encoding="utf-8"))
    cls.manifest = json.loads(cls.manifest_path.read_text(encoding="utf-8"))

  @staticmethod
  def module_by_id(inventory: dict, module_id: str) -> dict:
    return next(module for module in inventory["modules"] if module["id"] == module_id)

  def test_committed_inventory_is_valid_and_pins_v408(self) -> None:
    report = VALIDATOR.validate(copy.deepcopy(self.inventory), self.manifest)
    self.assertTrue(report["valid"])
    self.assertEqual(
        "8029f930d28c0447f0acf2bcabef0a79865ad434",
        report["oracle_commit"],
    )
    self.assertEqual("complete", report["oracle_execution_status"])
    self.assertEqual(12, report["module_count"])
    self.assertEqual(12, report["source_presence_verified_count"])
    self.assertEqual(4, report["direct_differential_complete_count"])
    self.assertEqual(2, report["dataset_group_count"])
    self.assertEqual(3, report["tolerance_profile_count"])

    equivalent_modules = {
        module["id"] for module in self.inventory["modules"]
        if module["classification"] == "Equivalent"
    }
    self.assertEqual(
        {"mzml-import", "mass-detection", "adap-chromatogram-builder", "smoothing"},
        equivalent_modules,
    )
    for module_id in equivalent_modules:
      module = self.module_by_id(self.inventory, module_id)
      self.assertTrue(module["evidence"]["direct_differential_complete"])
      self.assertEqual("complete", module["gate"]["status"])

    smoothing = self.module_by_id(self.inventory, "smoothing")
    self.assertEqual(1, smoothing["parameter_versions"]["oracle_408"])
    self.assertEqual("verified", smoothing["parameter_mapping_status"])
    self.assertIn("governed-differential-complete", smoothing["findings"])

  def test_equivalent_requires_direct_differential_evidence(self) -> None:
    inventory = copy.deepcopy(self.inventory)
    resolver = self.module_by_id(inventory, "minimum-search-resolver")
    self.assertFalse(resolver["evidence"]["direct_differential_complete"])
    resolver["classification"] = "Equivalent"
    with self.assertRaisesRegex(
        VALIDATOR.ValidationError, "cannot be Equivalent"
    ):
      VALIDATOR.validate(inventory, self.manifest)

  def test_unknown_dataset_is_rejected(self) -> None:
    inventory = copy.deepcopy(self.inventory)
    inventory["dataset_groups"]["single"] = ["missing-dataset"]
    with self.assertRaisesRegex(VALIDATOR.ValidationError, "unknown dataset"):
      VALIDATOR.validate(inventory, self.manifest)

  def test_unknown_dataset_group_is_rejected(self) -> None:
    inventory = copy.deepcopy(self.inventory)
    inventory["modules"][0]["gate"]["dataset_group"] = "missing-group"
    with self.assertRaisesRegex(VALIDATOR.ValidationError, "unknown dataset group"):
      VALIDATOR.validate(inventory, self.manifest)

  def test_unknown_finding_code_is_rejected(self) -> None:
    inventory = copy.deepcopy(self.inventory)
    inventory["modules"][0]["findings"].append("missing-finding")
    with self.assertRaisesRegex(VALIDATOR.ValidationError, "unknown finding codes"):
      VALIDATOR.validate(inventory, self.manifest)

  def test_unknown_tolerance_profile_is_rejected(self) -> None:
    inventory = copy.deepcopy(self.inventory)
    inventory["modules"][0]["gate"]["tolerance_profile"] = "missing-profile"
    with self.assertRaisesRegex(VALIDATOR.ValidationError, "unknown tolerance profile"):
      VALIDATOR.validate(inventory, self.manifest)

  def test_oracle_commit_is_fail_closed(self) -> None:
    inventory = copy.deepcopy(self.inventory)
    inventory["target"]["commit"] = "0" * 40
    with self.assertRaisesRegex(VALIDATOR.ValidationError, "frozen oracle commit"):
      VALIDATOR.validate(inventory, self.manifest)

  def test_initial_modules_cannot_be_silently_removed(self) -> None:
    inventory = copy.deepcopy(self.inventory)
    inventory["modules"] = [
        module for module in inventory["modules"] if module["id"] != "mass-detection"
    ]
    with self.assertRaisesRegex(
        VALIDATOR.ValidationError, "missing required modules: mass-detection"
    ):
      VALIDATOR.validate(inventory, self.manifest)


if __name__ == "__main__":
  unittest.main()
