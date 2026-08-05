#!/usr/bin/env python3

from __future__ import annotations

import argparse
import importlib.util
import json
import shutil
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

  def _spec(self, root: Path | None = None, root_id: str = "test-adapters"):
    return INVENTORY.RootSpec(root_id, root or self.root)

  def _inventory(self, expected_count: int | None = None):
    return INVENTORY.inventory([self._spec()], expected_count)

  def _write(self, relative: str, content: str) -> None:
    path = self.root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")

  def test_inventory_is_sorted_and_deterministic(self) -> None:
    first = self._inventory(expected_count=2)
    second = self._inventory(expected_count=2)
    self.assertEqual(first, second)
    self.assertEqual(
        ["io.github.mzmine.example.Alpha", "io.github.mzmine.example.Beta"],
        [item["class"] for item in first["adapters"]],
    )
    self.assertEqual({"test-adapters"}, {item["root_id"] for item in first["adapters"]})
    self.assertTrue(all("root" not in item for item in first["adapters"]))
    self.assertEqual(64, len(first["content_sha256"]))
    self.assertTrue(all(len(item["sha256"]) == 64 for item in first["adapters"]))

  def test_identical_bytes_under_different_parent_paths_have_same_inventory(self) -> None:
    second_parent = Path(self.temp.name) / "different/workspace/src/main/java"
    shutil.copytree(self.root, second_parent)
    first = INVENTORY.inventory([self._spec(self.root)], expected_count=2)
    second = INVENTORY.inventory([self._spec(second_parent)], expected_count=2)
    self.assertEqual(first, second)

  def test_root_identifier_is_required_and_validated(self) -> None:
    parsed = INVENTORY.parse_root_spec(f"stable-root={self.root}")
    self.assertEqual("stable-root", parsed.root_id)
    self.assertEqual(self.root, parsed.path)
    for value in (str(self.root), f"Invalid={self.root}", f"={self.root}", "valid="):
      with self.assertRaises(argparse.ArgumentTypeError):
        INVENTORY.parse_root_spec(value)

  def test_duplicate_root_identifier_is_rejected(self) -> None:
    second = Path(self.temp.name) / "second"
    second.mkdir()
    with self.assertRaisesRegex(INVENTORY.InventoryError, "identifiers must be unique"):
      INVENTORY.inventory([self._spec(), self._spec(second)])

  def test_javadoc_keywords_do_not_replace_real_type(self) -> None:
    self._write(
        "io/github/mzmine/example/Alpha.java",
        """package io.github.mzmine.example;
/** This class and interface record enum wording is documentation only. */
public final class Alpha {
  private static class NestedBeforeEnd { }
  String text = "class FalseType";
}
""",
    )
    result = self._inventory(expected_count=2)
    self.assertIn(
        "io.github.mzmine.example.Alpha",
        {item["class"] for item in result["adapters"]},
    )
    self.assertNotIn(
        "io.github.mzmine.example.and",
        {item["class"] for item in result["adapters"]},
    )

  def test_nested_type_is_not_counted_as_second_top_level_type(self) -> None:
    self._write(
        "io/github/mzmine/example/Beta.java",
        """package io.github.mzmine.example;
public interface Beta {
  final class Nested { }
}
""",
    )
    result = self._inventory(expected_count=2)
    self.assertEqual(2, result["adapter_count"])

  def test_no_active_top_level_type_is_rejected(self) -> None:
    self._write(
        "io/github/mzmine/example/Empty.java",
        "package io.github.mzmine.example;\n// public class Empty {}\n",
    )
    with self.assertRaisesRegex(INVENTORY.InventoryError, "exactly one active top-level"):
      self._inventory()

  def test_path_and_package_mismatch_is_rejected(self) -> None:
    self._write(
        "io/github/mzmine/example/Wrong.java",
        "package io.github.mzmine.other;\npublic class Wrong {}\n",
    )
    with self.assertRaisesRegex(INVENTORY.InventoryError, "path/FQCN mismatch"):
      self._inventory()

  def test_expected_count_is_fail_closed(self) -> None:
    with self.assertRaisesRegex(INVENTORY.InventoryError, "count mismatch"):
      self._inventory(expected_count=3)

  def test_locked_manifest_accepts_exact_inventory(self) -> None:
    current = self._inventory(expected_count=2)
    INVENTORY.verify(current, json.loads(json.dumps(current)))

  def test_changed_byte_fails_lock_verification(self) -> None:
    locked = self._inventory(expected_count=2)
    path = self.root / "io/github/mzmine/example/Alpha.java"
    path.write_text(
        "package io.github.mzmine.example;\npublic final class Alpha { int changed; }\n",
        encoding="utf-8",
    )
    current = self._inventory(expected_count=2)
    with self.assertRaisesRegex(INVENTORY.InventoryError, "lock mismatch"):
      INVENTORY.verify(current, locked)

  def test_invalid_locked_content_hash_is_rejected(self) -> None:
    locked = self._inventory(expected_count=2)
    locked["content_sha256"] = "0" * 64
    with self.assertRaisesRegex(INVENTORY.InventoryError, "content SHA-256"):
      INVENTORY.verify(self._inventory(2), locked)


if __name__ == "__main__":
  unittest.main()
