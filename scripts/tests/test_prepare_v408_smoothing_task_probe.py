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

  def test_exact_identifier_rename(self) -> None:
    source = (
        "public class SmoothingTask {\n"
        "  public SmoothingTask() {}\n"
        "  Class<?> type = SmoothingTask.class;\n"
        "}\n"
    )
    transformed, count = MODULE.rename_identifier(
        source, MODULE.SOURCE_CLASS, MODULE.PROBE_CLASS)
    self.assertEqual(3, count)
    self.assertEqual(0, MODULE.identifier_count(transformed, MODULE.SOURCE_CLASS))
    self.assertEqual(3, MODULE.identifier_count(transformed, MODULE.PROBE_CLASS))

  def test_identifier_boundary_is_exact(self) -> None:
    source = "SmoothingTask V408SmoothingTaskProbe SmoothingTaskSuffix"
    self.assertEqual(1, MODULE.identifier_count(source, MODULE.SOURCE_CLASS))
    self.assertEqual(1, MODULE.identifier_count(source, MODULE.PROBE_CLASS))

  def test_probe_has_no_scientific_substitution_contract(self) -> None:
    self.assertEqual("SmoothingTask", MODULE.SOURCE_CLASS)
    self.assertEqual("V408SmoothingTaskProbe", MODULE.PROBE_CLASS)
    self.assertEqual(
        "324870813e7f0d58f8c77073bf667738195bce6f", MODULE.TASK_BLOB_SHA1)


if __name__ == "__main__":
  unittest.main()
