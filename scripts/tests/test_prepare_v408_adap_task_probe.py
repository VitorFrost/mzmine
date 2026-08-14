from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "prepare_v408_adap_task_probe.py"
SPEC = importlib.util.spec_from_file_location("prepare_v408_adap_task_probe", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PrepareV408AdapTaskProbeTest(unittest.TestCase):

  def test_exact_java_identifier_rename_does_not_match_probe_substring(self) -> None:
    source = (
        "public class ModularADAPChromatogramBuilderTask {\n"
        "  ModularADAPChromatogramBuilderTask() {}\n"
        "  Class<?> c = ModularADAPChromatogramBuilderTask.class;\n"
        "}\n"
    )
    transformed, count = MODULE.rename_identifier(
        source, MODULE.SOURCE_CLASS, MODULE.PROBE_CLASS)

    self.assertEqual(3, count)
    self.assertEqual(0, MODULE.identifier_count(transformed, MODULE.SOURCE_CLASS))
    self.assertEqual(3, MODULE.identifier_count(transformed, MODULE.PROBE_CLASS))
    self.assertIn(MODULE.PROBE_CLASS, transformed)

  def test_identifier_boundary_rejects_only_exact_java_identifier(self) -> None:
    source = (
        "ModularADAPChromatogramBuilderTask "
        "V408ModularADAPChromatogramBuilderTaskProbe "
        "ModularADAPChromatogramBuilderTaskSuffix"
    )
    self.assertEqual(1, MODULE.identifier_count(source, MODULE.SOURCE_CLASS))
    self.assertEqual(1, MODULE.identifier_count(source, MODULE.PROBE_CLASS))

  def test_sort_mapping_literals_remain_distinct(self) -> None:
    self.assertNotEqual(MODULE.SORT_SOURCE, MODULE.SORT_PROBE)
    self.assertIn("sortByDefault(", MODULE.SORT_SOURCE)
    self.assertIn("sortByDefaultRT(", MODULE.SORT_PROBE)


if __name__ == "__main__":
  unittest.main()
