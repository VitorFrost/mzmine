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


INVENTORY = load_module(
    "inventory_v408_adapters",
    SCRIPTS / "inventory_v408_adapters.py",
)


class InventoryV408AdaptersTest(unittest.TestCase):

  def setUp(self) -> None:
    self.temp = tempfile.TemporaryDirectory()
    self.root = Path(self.temp.name) / "src/main/java"
    self._write(
        "io/github/mzmine/example/Alpha.java",
        "package io.github.mzmine.example;\npublic final class Alpha {}\n",
    )
    self._write(
        "io/github/mzmine/example/Beta.java",
        "package io.github.mzmine.example;\npublic interface Beta {}\n",
    )

  def tearDown(self) -> None:
    self.temp.cleanup()

  def _write(self, relative: str, content: str) -> None:
    path = self.root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")

  def test_inventory_is_sorted_and_deterministic(self) -> None:
    first = INVENTORY.inventory([self.root], expected_count=2)
    second = INVENTORY.inventory([self.root], expected_count=2)
    self.assertEqual(first, second)
    self.assertEqual(
        ["io.github.mzmine.example.Alpha", "io.github.mzmine.example.Beta"],
        [item["class"] for item in first["adapters"]],
    )
    self.assertEqual(64, len(first["content_sha256"]))
    self.assertTrue(all(len(item["sha256"]) == 64 for item in first["adapters"]))

  def test_path_and_package_mismatch_is_rejected(self) -> None:
    self._write(
        "io/github/mzmine/example/Wrong.java",
        "package io.github.mzmine.other;\npublic class Wrong {}\n",
    )
    with self.assertRaisesRegex(INVENTORY.InventoryError, "path/FQCN mismatch"):
      INVENTORY.inventory([self.root])

  def test_expected_count_is_fail_closed(self) -> None:
    with self.assertRaisesRegex(INVENTORY.InventoryError, "count mismatch"):
      INVENTORY.inventory([self.root], expected_count=3)

  def test_locked_manifest_accepts_exact_inventory(self) -> None:
    current = INVENTORY.inventory([self.root], expected_count=2)
    INVENTORY.verify(current, json.loads(json.dumps(current)))

  def test_changed_byte_fails_lock_verification(self) -> None:
    locked = INVENTORY.inventory([self.root], expected_count=2)
    path = self.root / "io/github/mzmine/example/Alpha.java"
    path.write_text(
        "package io.github.mzmine.example;\npublic final class Alpha { int changed; }\n",
        encoding="utf-8",
    )
    current = INVENTORY.inventory([self.root], expected_count=2)
    with self.assertRaisesRegex(INVENTORY.InventoryError, "lock mismatch"):
      INVENTORY.verify(current, locked)

  def test_invalid_locked_content_hash_is_rejected(self) -> None:
    locked = INVENTORY.inventory([self.root], expected_count=2)
    locked["content_sha256"] = "0" * 64
    with self.assertRaisesRegex(INVENTORY.InventoryError, "content SHA-256"):
      INVENTORY.verify(INVENTORY.inventory([self.root], 2), locked)


if __name__ == "__main__":
  unittest.main()
