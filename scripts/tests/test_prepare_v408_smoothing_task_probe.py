from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "prepare_v408_smoothing_task_probe.py"
SPEC = importlib.util.spec_from_file_location("prepare_v408_smoothing_task_probe", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PrepareV408SmoothingTaskProbeTest(unittest.TestCase):

  def test_identifier_pattern_matches_only_exact_java_identifier(self) -> None:
    text = "SmoothingTask V408SmoothingTaskProbe SmoothingTaskSuffix"
    self.assertEqual(1, len(MODULE.pattern(MODULE.SOURCE_CLASS).findall(text)))
    self.assertEqual(1, len(MODULE.pattern(MODULE.PROBE_CLASS).findall(text)))

  def test_mechanical_rename_preserves_nonmatching_identifiers(self) -> None:
    text = "public class SmoothingTask { SmoothingTask(){} Class<?> c=SmoothingTask.class; SmoothingTaskSuffix x; }"
    transformed, count = MODULE.pattern(MODULE.SOURCE_CLASS).subn(MODULE.PROBE_CLASS, text)
    self.assertEqual(3, count)
    self.assertNotIn("class SmoothingTask ", transformed)
    self.assertIn("SmoothingTaskSuffix", transformed)

  def test_frozen_constants(self) -> None:
    self.assertEqual("8029f930d28c0447f0acf2bcabef0a79865ad434", MODULE.ORACLE_COMMIT)
    self.assertEqual("324870813e7f0d58f8c77073bf667738195bce6f", MODULE.SOURCE_BLOB)


if __name__ == "__main__":
  unittest.main()
