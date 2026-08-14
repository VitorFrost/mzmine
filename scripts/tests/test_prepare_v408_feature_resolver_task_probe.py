#!/usr/bin/env python3
"""Regression tests for the exact-v4 FeatureResolverTask probe preparation gate."""

from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "prepare_v408_feature_resolver_task_probe.py"
SPEC = importlib.util.spec_from_file_location("prepare_v408_feature_resolver_task_probe", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PrepareV408FeatureResolverTaskProbeTest(unittest.TestCase):

  SOURCE = (
      "package io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution;\n"
      "public final class FeatureResolverTask {\n"
      "  public FeatureResolverTask() {}\n"
      "}\n"
  )

  def setUp(self) -> None:
    self.tmp = tempfile.TemporaryDirectory()
    root = Path(self.tmp.name)
    self.oracle = root / "oracle"
    self.candidate = root / "candidate"
    self.output = root / "generated" / "V408FeatureResolverTaskProbe.java"
    self.evidence = root / "evidence" / "probe.json"

    oracle_source = self.oracle / MODULE.ORACLE_PREFIX / MODULE.RELATIVE_SOURCE
    candidate_source = self.candidate / MODULE.CANDIDATE_PREFIX / MODULE.RELATIVE_SOURCE
    oracle_source.parent.mkdir(parents=True, exist_ok=True)
    candidate_source.parent.mkdir(parents=True, exist_ok=True)
    oracle_source.write_text(self.SOURCE, encoding="utf-8")
    candidate_source.write_text(self.SOURCE, encoding="utf-8")
    self.oracle_source = oracle_source
    self.candidate_source = candidate_source

  def tearDown(self) -> None:
    self.tmp.cleanup()

  def test_exact_bytes_generate_rename_only_probe_and_evidence(self) -> None:
    with mock.patch.object(MODULE, "git", return_value=MODULE.ORACLE_COMMIT):
      report = MODULE.prepare(self.oracle, self.candidate, self.output, self.evidence)

    generated = self.output.read_text(encoding="utf-8")
    self.assertNotIn("FeatureResolverTask", generated.replace("V408FeatureResolverTaskProbe", ""))
    self.assertIn("public final class V408FeatureResolverTaskProbe", generated)
    self.assertIn("public V408FeatureResolverTaskProbe()", generated)
    self.assertEqual(report["oracle_commit"], MODULE.ORACLE_COMMIT)
    self.assertEqual(report["source_git_blob_sha1"], report["candidate_git_blob_sha1"])
    self.assertEqual(report["renamed_identifier_occurrences"], 2)
    self.assertEqual(report["transformation"], "mechanical-java-identifier-class-rename-only")
    self.assertEqual(json.loads(self.evidence.read_text(encoding="utf-8")), report)

  def test_refuses_candidate_bytes_that_differ_from_frozen_oracle(self) -> None:
    self.candidate_source.write_text(self.SOURCE + "// candidate drift\n", encoding="utf-8")
    with mock.patch.object(MODULE, "git", return_value=MODULE.ORACLE_COMMIT):
      with self.assertRaisesRegex(ValueError, "not byte-identical"):
        MODULE.prepare(self.oracle, self.candidate, self.output, self.evidence)
    self.assertFalse(self.output.exists())
    self.assertFalse(self.evidence.exists())

  def test_refuses_wrong_oracle_commit_before_reading_source_contract(self) -> None:
    with mock.patch.object(MODULE, "git", return_value="0" * 40):
      with self.assertRaisesRegex(ValueError, "wrong oracle commit"):
        MODULE.prepare(self.oracle, self.candidate, self.output, self.evidence)
    self.assertFalse(self.output.exists())
    self.assertFalse(self.evidence.exists())


if __name__ == "__main__":
  unittest.main()
