#!/usr/bin/env python3

from __future__ import annotations

import copy
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve().parents[1] / "fetch_public_test_data.py"
SPEC = importlib.util.spec_from_file_location("fetch_public_test_data", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class PublicDatasetManifestTest(unittest.TestCase):

  @classmethod
  def setUpClass(cls) -> None:
    cls.manifest_path = Path(__file__).resolve().parents[2] / "datasets" / "public_validation_manifest.json"
    cls.manifest = MODULE.load_manifest(cls.manifest_path)

  def test_repository_manifest_is_valid_and_ids_are_unique(self) -> None:
    MODULE.validate_manifest(self.manifest)
    ids = [dataset["id"] for dataset in self.manifest["datasets"]]
    self.assertEqual(len(ids), len(set(ids)))

  def test_no_candidate_is_downloadable_before_hash_is_frozen(self) -> None:
    for dataset in self.manifest["datasets"]:
      if dataset["status"] in {"candidate", "reserve-candidate", "reference-only"}:
        self.assertFalse(dataset["download_enabled"], dataset["id"])

  def test_disabled_dataset_refuses_download(self) -> None:
    selected = {self.manifest["datasets"][0]["id"]}
    with tempfile.TemporaryDirectory() as temp_dir:
      with self.assertRaisesRegex(MODULE.ManifestError, "download is disabled"):
        list(MODULE.iter_download_items(self.manifest, selected, Path(temp_dir)))

  def test_unknown_dataset_refuses_download(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      with self.assertRaisesRegex(MODULE.ManifestError, "Unknown dataset"):
        list(MODULE.iter_download_items(self.manifest, {"not-a-real-dataset"}, Path(temp_dir)))

  def test_duplicate_dataset_id_is_rejected(self) -> None:
    invalid = copy.deepcopy(self.manifest)
    invalid["datasets"].append(copy.deepcopy(invalid["datasets"][0]))
    with self.assertRaisesRegex(MODULE.ManifestError, "Duplicate dataset id"):
      MODULE.validate_manifest(invalid)

  def test_path_traversal_is_rejected(self) -> None:
    invalid = copy.deepcopy(self.manifest)
    invalid["datasets"][0]["files"][0]["relative_path"] = "../private.raw"
    with self.assertRaisesRegex(MODULE.ManifestError, "unsafe relative_path"):
      MODULE.validate_manifest(invalid)

  def test_enabled_dataset_requires_verified_license_size_hash_and_url(self) -> None:
    invalid = copy.deepcopy(self.manifest)
    dataset = invalid["datasets"][0]
    dataset["download_enabled"] = True
    with self.assertRaisesRegex(MODULE.ManifestError, "verified reuse terms"):
      MODULE.validate_manifest(invalid)

    dataset["license"] = {
        "status": "verified",
        "identifier": "CC0-1.0",
        "evidence_url": "https://example.test/license"
    }
    with self.assertRaisesRegex(MODULE.ManifestError, "positive expected_size_bytes"):
      MODULE.validate_manifest(invalid)

    dataset["files"][0]["expected_size_bytes"] = 123
    with self.assertRaisesRegex(MODULE.ManifestError, "SHA-256"):
      MODULE.validate_manifest(invalid)

    dataset["files"][0]["sha256"] = "0" * 64
    MODULE.validate_manifest(invalid)

  def test_credentials_and_insecure_http_are_rejected(self) -> None:
    invalid = copy.deepcopy(self.manifest)
    dataset = invalid["datasets"][0]
    dataset["download_enabled"] = True
    dataset["license"] = {
        "status": "verified",
        "identifier": "CC0-1.0",
        "evidence_url": "https://example.test/license"
    }
    dataset["files"][0]["expected_size_bytes"] = 123
    dataset["files"][0]["sha256"] = "0" * 64
    dataset["source"]["download_url"] = "http://example.test/file.mzML"
    with self.assertRaisesRegex(MODULE.ManifestError, "HTTPS or anonymous FTP"):
      MODULE.validate_manifest(invalid)

    dataset["source"]["download_url"] = "ftp://user:password@example.test/file.mzML"
    with self.assertRaisesRegex(MODULE.ManifestError, "credentials are forbidden"):
      MODULE.validate_manifest(invalid)

  def test_manifest_round_trip_remains_utf8_json(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      path = Path(temp_dir) / "manifest.json"
      path.write_text(json.dumps(self.manifest, ensure_ascii=False, indent=2), encoding="utf-8")
      MODULE.load_manifest(path)


if __name__ == "__main__":
  unittest.main()
