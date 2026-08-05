#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
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


AUDITOR = load_module(
    "audit_v408_source_closure_empty_java",
    SCRIPTS / "audit_v408_source_closure.py",
)


class EmptyJavaSourceTest(unittest.TestCase):

  def test_file_without_active_top_level_type_is_ignored(self) -> None:
    with tempfile.TemporaryDirectory() as directory:
      checkout = Path(directory)
      root = checkout / "src/main/java/io/github/mzmine"
      root.mkdir(parents=True)
      (root / "Inactive.java").write_text(
          "package io.github.mzmine;\n// public class Inactive {}\n",
          encoding="utf-8",
      )
      (root / "Active.java").write_text(
          "package io.github.mzmine;\npublic class Active {}\n",
          encoding="utf-8",
      )
      index, packages, roots = AUDITOR.build_index(checkout, ["src/main/java"])
      self.assertEqual(["src/main/java"], roots)
      self.assertIn("io.github.mzmine.Active", index)
      self.assertNotIn("io.github.mzmine.Inactive", index)
      self.assertEqual(["io.github.mzmine.Active"], packages["io.github.mzmine"])


if __name__ == "__main__":
  unittest.main()
