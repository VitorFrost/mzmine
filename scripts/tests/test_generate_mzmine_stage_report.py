#!/usr/bin/env python3

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import sys
import tempfile
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


GENERATOR = load_module(
    "generate_mzmine_stage_report",
    SCRIPTS / "generate_mzmine_stage_report.py",
)


class GenerateMZmineStageReportTest(unittest.TestCase):

  def test_committed_governed_dataset_is_normalized(self) -> None:
    manifest = json.loads(
        (REPOSITORY / "datasets/public_validation_manifest.json").read_text(encoding="utf-8")
    )
    dataset = GENERATOR._dataset(
        manifest, "zenodo-14001110-banane-30ngml-001"
    )
    self.assertEqual(
        "zenodo/14001110/Banane_30ngmL_001.mzML",
        dataset["relative_path"],
    )
    self.assertEqual(87090777, dataset["expected_size_bytes"])
    self.assertEqual(
        "2eb189e193925983ddf8348a13a4c96fa7382e4e790665a204251eb036aea77d",
        dataset["sha256"],
    )

  def test_disabled_dataset_is_rejected(self) -> None:
    manifest = {
        "datasets": [{
            "id": "disabled",
            "download_enabled": False,
            "files": [],
        }]
    }
    with self.assertRaisesRegex(GENERATOR.GenerationError, "not approved"):
      GENERATOR._dataset(manifest, "disabled")

  def test_multiple_files_are_rejected_for_first_stage(self) -> None:
    file_record = {
        "relative_path": "a.mzML",
        "expected_size_bytes": 1,
        "sha256": "0" * 64,
    }
    manifest = {
        "datasets": [{
            "id": "multi",
            "download_enabled": True,
            "files": [file_record, dict(file_record)],
        }]
    }
    with self.assertRaisesRegex(GENERATOR.GenerationError, "exactly one"):
      GENERATOR._dataset(manifest, "multi")

  def test_input_size_and_hash_are_fail_closed(self) -> None:
    payload = b"governed-mzml-fixture"
    with tempfile.TemporaryDirectory() as directory:
      path = Path(directory) / "fixture.mzML"
      path.write_bytes(payload)
      dataset = {
          "expected_size_bytes": len(payload),
          "sha256": hashlib.sha256(payload).hexdigest(),
      }
      size, sha = GENERATOR.validate_input(path, dataset)
      self.assertEqual(len(payload), size)
      self.assertEqual(dataset["sha256"], sha)

      wrong_size = dict(dataset, expected_size_bytes=len(payload) + 1)
      with self.assertRaisesRegex(GENERATOR.GenerationError, "byte size mismatch"):
        GENERATOR.validate_input(path, wrong_size)

      wrong_sha = dict(dataset, sha256="f" * 64)
      with self.assertRaisesRegex(GENERATOR.GenerationError, "SHA-256 mismatch"):
        GENERATOR.validate_input(path, wrong_sha)

  def test_gradle_command_is_explicit_and_test_scoped(self) -> None:
    with tempfile.TemporaryDirectory() as directory:
      repository = Path(directory)
      wrapper = repository / ("gradlew.bat" if os.name == "nt" else "gradlew")
      wrapper.write_text("", encoding="utf-8")
      command = GENERATOR.gradle_command(repository, "Acceptance.generate")
      self.assertIn("test", command)
      self.assertIn("--tests", command)
      self.assertIn("Acceptance.generate", command)
      self.assertIn("--no-daemon", command)
      self.assertIn("--rerun-tasks", command)

  def test_source_ms_level_has_distinct_governed_mapping(self) -> None:
    self.assertEqual(
        "mzml-import-centroid-source-ms1-v1",
        GENERATOR.settings_mapping_id(1),
    )
    self.assertEqual(
        "mzml-import-centroid-source-ms2-v1",
        GENERATOR.settings_mapping_id(2),
    )
    with self.assertRaisesRegex(GENERATOR.GenerationError, "exactly 1 or 2"):
      GENERATOR.settings_mapping_id(3)

  def test_environment_passes_single_manual_source_level(self) -> None:
    with tempfile.TemporaryDirectory() as directory:
      root = Path(directory)
      args = argparse.Namespace(
          input=root / "input.mzML",
          output=root / "report.json",
          producer_repository="VitorFrost/mzmine",
          producer_ref="agent/test",
          producer_commit="a" * 40,
          application_version="3.9.1",
          threads=1,
          source_ms_level=2,
      )
      dataset = {
          "dataset_id": "dataset",
          "relative_path": "dataset/input.mzML",
      }
      environment = GENERATOR.build_environment(args, dataset, "b" * 64)
      self.assertEqual("2", environment["MZMINE_PARITY_SOURCE_MS_LEVEL"])
      self.assertNotIn("MZMINE_PARITY_SURVEY_MS_LEVEL", environment)
      self.assertEqual("1", environment["MZMINE_PARITY_THREADS"])

  def test_new_and_legacy_cli_names_resolve_to_same_selection(self) -> None:
    common = [
        "--input", "input.mzML",
        "--output", "report.json",
        "--producer-ref", "agent/test",
        "--producer-commit", "a" * 40,
    ]
    new_args = GENERATOR.build_parser().parse_args(
        common + ["--source-ms-level", "2"]
    )
    legacy_args = GENERATOR.build_parser().parse_args(
        common + ["--survey-ms-level", "2"]
    )
    self.assertEqual(2, new_args.source_ms_level)
    self.assertEqual(new_args.source_ms_level, legacy_args.source_ms_level)


if __name__ == "__main__":
  unittest.main()
