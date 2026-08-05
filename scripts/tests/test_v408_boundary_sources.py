#!/usr/bin/env python3

from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BOUNDARY = (
    ROOT
    / "oracle/v408-boundaries/src/main/java/io/github/mzmine/util/MemoryMapStorage.java"
)
MANIFEST = ROOT / "datasets/parity/v408_source_closure_manifest.json"


class V408BoundarySourceTest(unittest.TestCase):

  def test_memory_map_boundary_is_explicitly_behavior_free(self) -> None:
    text = BOUNDARY.read_text(encoding="utf-8")
    self.assertIn("package io.github.mzmine.util;", text)
    self.assertIn("public final class MemoryMapStorage", text)
    self.assertIn("private MemoryMapStorage()", text)
    self.assertIn("UnsupportedOperationException", text)
    self.assertIn("null MemoryMapStorage reference", text)
    self.assertNotIn("io.mzio", text)

    # No state, factories, buffers, lifecycle, persistence, or public methods may be added.
    self.assertIsNone(
        re.search(
            r"(?m)^\s*(?:public|protected)\s+(?!final\s+class\b)(?:static\s+)?"
            r"[A-Za-z_$][\w$<>?, .\[\]]*\s+[A-Za-z_$][\w$]*\s*\(",
            text,
        )
    )
    self.assertIsNone(
        re.search(
            r"(?m)^\s*(?:public|protected|private)\s+(?:static\s+)?(?:final\s+)?"
            r"[A-Za-z_$][\w$<>?, .\[\]]*\s+[A-Za-z_$][\w$]*\s*(?:=|;)",
            text,
        )
    )

  def test_manifest_records_boundary_origin_and_null_only_scope(self) -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    boundary = manifest["reviewed_boundaries"]["io.github.mzmine.util.MemoryMapStorage"]
    self.assertEqual("null-storage-type-boundary", boundary["classification"])
    self.assertEqual(
        "oracle/v408-boundaries/src/main/java/io/github/mzmine/util/MemoryMapStorage.java",
        boundary["source_origin"],
    )
    self.assertEqual(
        ["type identity for nullable arguments only"],
        boundary["preserved_members"],
    )
    self.assertIn("no constructor", boundary["reason"])
    self.assertIn("open-offline-boundaries/src/main/java", manifest["source_roots"])


if __name__ == "__main__":
  unittest.main()
