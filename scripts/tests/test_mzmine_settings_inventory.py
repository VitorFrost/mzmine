#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
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


INSPECTOR = load_module("inspect_mzmine_settings", SCRIPTS / "inspect_mzmine_settings.py")
VALIDATOR = load_module(
    "validate_mzmine_settings_inventory", SCRIPTS / "validate_mzmine_settings_inventory.py"
)


class MZmineSettingsInventoryTest(unittest.TestCase):

  def test_inspector_preserves_batch_step_order_and_parameters(self) -> None:
    xml = """<?xml version="1.0" encoding="UTF-8"?>
    <batch mzmine_version="3.4.27">
      <batchstep method="io.github.mzmine.example.FirstModule" parameter_version="1">
        <parameter name="Noise level">100.0</parameter>
      </batchstep>
      <batchstep method="io.github.mzmine.example.SecondModule" parameter_version="2">
        <parameter name="Suffix">test</parameter>
      </batchstep>
    </batch>
    """
    with tempfile.TemporaryDirectory() as temp_dir:
      path = Path(temp_dir) / "settings.xml"
      path.write_text(xml, encoding="utf-8")
      report = INSPECTOR.inspect(path)

    self.assertEqual("batch", report["xml_root_tag"])
    self.assertEqual("3.4.27", report["root_attributes"]["mzmine_version"])
    self.assertEqual(2, report["step_count"])
    self.assertEqual(
        [
            "io.github.mzmine.example.FirstModule",
            "io.github.mzmine.example.SecondModule",
        ],
        [step["class"] for step in report["steps"]],
    )
    self.assertEqual(["Noise level", "Suffix"], report["parameter_names"])

  def test_inspector_rejects_doctype_and_entities(self) -> None:
    xml = """<!DOCTYPE batch [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
    <batch mzmine_version="3.4.27"><parameter name="x">&secret;</parameter></batch>
    """
    with tempfile.TemporaryDirectory() as temp_dir:
      path = Path(temp_dir) / "unsafe.xml"
      path.write_text(xml, encoding="utf-8")
      with self.assertRaisesRegex(INSPECTOR.InspectionError, "DTD and ENTITY"):
        INSPECTOR.inspect(path)

  def test_validator_requires_real_source_class_and_exact_order(self) -> None:
    class_name = "io.github.mzmine.example.FirstModule"
    with tempfile.TemporaryDirectory() as temp_dir:
      root = Path(temp_dir)
      source = root / "src/main/java/io/github/mzmine/example/FirstModule.java"
      source.parent.mkdir(parents=True)
      source.write_text(
          "package io.github.mzmine.example; public class FirstModule {}\n", encoding="utf-8"
      )
      inventory = {
          "size_bytes": 10,
          "sha256": "0" * 64,
          "xml_root_tag": "batch",
          "element_count": 2,
          "parameter_name_count": 1,
          "root_attributes": {"mzmine_version": "3.4.27"},
          "tag_counts": {"parameter": 1},
          "step_count": 1,
          "java_class_count": 1,
          "java_classes": [class_name],
          "steps": [{
              "class": class_name,
              "attributes": {"parameter_version": "1"},
          }],
      }
      expected = {
          "dataset_id": "synthetic",
          "source_size_bytes": 10,
          "source_sha256": "0" * 64,
          "xml_root_tag": "batch",
          "mzmine_version": "3.4.27",
          "element_count": 2,
          "parameter_element_count": 1,
          "unique_parameter_name_count": 1,
          "steps": [{
              "order": 1,
              "class": class_name,
              "parameter_version": "1",
              "compatibility": "source-present-not-yet-real-data-validated",
          }],
      }
      report = VALIDATOR.validate(inventory, expected, root)
      self.assertTrue(report["all_module_sources_present"])
      self.assertEqual(1, report["step_count"])

      expected["steps"][0]["class"] = "io.github.mzmine.example.MissingModule"
      with self.assertRaisesRegex(VALIDATOR.ValidationError, "class mismatch"):
        VALIDATOR.validate(inventory, expected, root)

  def test_committed_expected_inventory_is_valid_json(self) -> None:
    expected_path = (
        Path(__file__).resolve().parents[2]
        / "datasets/expected/zenodo_14000687_mzmine_settings_inventory.json"
    )
    expected = json.loads(expected_path.read_text(encoding="utf-8"))
    self.assertEqual(10, len(expected["steps"]))
    self.assertEqual(list(range(1, 11)), [step["order"] for step in expected["steps"]])


if __name__ == "__main__":
  unittest.main()
