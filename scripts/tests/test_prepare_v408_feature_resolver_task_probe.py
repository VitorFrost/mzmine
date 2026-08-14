#!/usr/bin/env python3
"""Regression tests for the governed-path v4 FeatureResolverTask probe preparation gate."""

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

  SOURCE = """package io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution;
import io.github.mzmine.modules.dataprocessing.filter_groupms2.GroupMS2Processor;
public class FeatureResolverTask {
  private static final Class<?> SELF = FeatureResolverTask.class;
  private GroupMS2Processor groupMS2Task;
  private void runGroup() {
    groupMS2Task = new GroupMS2Processor(this, newPeakList, ms2params);
            // group all features with MS/MS
            groupMS2Task.process();
            groupMs2Param = null; // clear progress
  }
  private void legacyResolve() {
    newPeakList = resolvePeaks((ModularFeatureList) originalPeakList);
  }
  private void dimensionIndependentResolve(ModularFeatureList originalFeatureList) {
    final Resolver resolver = ((GeneralResolverParameters) parameters).getResolver(parameters,
        originalFeatureList);
    newPeakList = originalFeatureList;
  }
  private FeatureList resolvePeaks(final ModularFeatureList originalFeatureList) {
    final ResolvedPeak[] peaks = resolver.resolvePeaks(originalFeature, parameters,
          mzCenterFunction, msmsRange, RTRangeMSMS);
    return originalFeatureList;
  }
  private ModularFeatureList createNewFeatureList(ModularFeatureList originalFeatureList) {
    return originalFeatureList;
  }
}
"""

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

  def tearDown(self) -> None:
    self.tmp.cleanup()

  def test_frozen_blobs_generate_probe_without_changing_governed_methods(self) -> None:
    with mock.patch.object(MODULE, "git", return_value=MODULE.ORACLE_COMMIT), \
        mock.patch.object(MODULE, "git_blob_sha1",
                          side_effect=[MODULE.ORACLE_TASK_BLOB, MODULE.CANDIDATE_TASK_BLOB]):
      report = MODULE.prepare(self.oracle, self.candidate, self.output, self.evidence)

    generated = self.output.read_text(encoding="utf-8")
    self.assertIn("class V408FeatureResolverTaskProbe", generated)
    self.assertNotIn("GroupMS2Processor", generated)
    self.assertIn("GroupMS2Task", generated)
    self.assertIn("parameters, null,", generated)
    self.assertTrue(report["governed_methods_unchanged"])
    self.assertEqual(report["governed_method_sha256_before"],
                     report["governed_method_sha256_after"])
    self.assertFalse(report["governed_path_requirements"]["legacy_feature_resolver_branch_executed"])
    self.assertFalse(report["governed_path_requirements"]["group_ms2_enabled"])
    self.assertEqual(json.loads(self.evidence.read_text(encoding="utf-8")), report)

  def test_refuses_candidate_task_blob_drift(self) -> None:
    with mock.patch.object(MODULE, "git", return_value=MODULE.ORACLE_COMMIT), \
        mock.patch.object(MODULE, "git_blob_sha1",
                          side_effect=[MODULE.ORACLE_TASK_BLOB, "0" * 40]):
      with self.assertRaisesRegex(ValueError, "candidate FeatureResolverTask blob drift"):
        MODULE.prepare(self.oracle, self.candidate, self.output, self.evidence)
    self.assertFalse(self.output.exists())

  def test_refuses_wrong_oracle_commit_before_adaptation(self) -> None:
    with mock.patch.object(MODULE, "git", return_value="0" * 40):
      with self.assertRaisesRegex(ValueError, "wrong oracle commit"):
        MODULE.prepare(self.oracle, self.candidate, self.output, self.evidence)
    self.assertFalse(self.output.exists())

  def test_adaptation_fails_closed_if_expected_nonexecuted_group_block_drifts(self) -> None:
    changed = self.SOURCE.replace("groupMs2Param = null; // clear progress", "// changed upstream")
    with self.assertRaisesRegex(ValueError, "group-ms2-implementation"):
      MODULE.adapt_v408_source(changed)


if __name__ == "__main__":
  unittest.main()
