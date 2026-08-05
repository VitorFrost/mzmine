#!/usr/bin/env python3

from __future__ import annotations

import hashlib
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


PREPARE = load_module(
    "prepare_v408_executable_slice",
    SCRIPTS / "prepare_v408_executable_slice.py",
)


class PrepareV408ExecutableSliceTest(unittest.TestCase):

  def setUp(self) -> None:
    self.temp = tempfile.TemporaryDirectory()
    self.root = Path(self.temp.name)
    self.checkout = self.root / "checkout"
    self.adapters = self.root / "adapters"
    self.output = self.root / "generated"
    self.upstream = (
        self.checkout
        / "mzmine-community/src/main/java/io/github/mzmine/example/PublicType.java"
    )
    self.upstream.parent.mkdir(parents=True)
    self.upstream.write_text(
        "package io.github.mzmine.example;\npublic class PublicType {}\n",
        encoding="utf-8",
    )
    self.adapters.mkdir()

  def tearDown(self) -> None:
    self.temp.cleanup()

  def closure(self) -> dict:
    return {
        "status": "pass",
        "content_sha256": "c" * 64,
        "oracle": {
            "commit": "8029f930d28c0447f0acf2bcabef0a79865ad434",
        },
        "nodes": [{
            "class": "io.github.mzmine.example.PublicType",
            "path": (
                "mzmine-community/src/main/java/"
                "io/github/mzmine/example/PublicType.java"
            ),
            "source_sha256": PREPARE.sha256(self.upstream),
            "classification": "entry-point",
        }],
    }

  def test_copies_only_verified_upstream_source(self) -> None:
    manifest = PREPARE.prepare(
        self.checkout, self.closure(), [self.adapters], self.output
    )
    copied = self.output / "io/github/mzmine/example/PublicType.java"
    self.assertTrue(copied.is_file())
    self.assertEqual(self.upstream.read_bytes(), copied.read_bytes())
    self.assertEqual(1, manifest["class_count"])
    self.assertEqual(1, manifest["source_file_count"])
    self.assertEqual(0, manifest["adapter_class_count"])

  def test_refuses_changed_upstream_bytes(self) -> None:
    closure = self.closure()
    self.upstream.write_text(
        "package io.github.mzmine.example;\npublic class PublicType { int changed; }\n",
        encoding="utf-8",
    )
    with self.assertRaisesRegex(PREPARE.PreparationError, "SHA mismatch"):
      PREPARE.prepare(self.checkout, closure, [self.adapters], self.output)

  def test_uses_versioned_adapter_with_same_fqcn(self) -> None:
    adapter = self.adapters / "io/github/mzmine/example/PublicType.java"
    adapter.parent.mkdir(parents=True)
    adapter.write_text(
        "package io.github.mzmine.example;\npublic final class PublicType {}\n",
        encoding="utf-8",
    )
    manifest = PREPARE.prepare(
        self.checkout, self.closure(), [self.adapters], self.output
    )
    copied = self.output / "io/github/mzmine/example/PublicType.java"
    self.assertEqual(adapter.read_bytes(), copied.read_bytes())
    self.assertEqual(1, manifest["adapter_class_count"])
    self.assertEqual("open-offline-adapter", manifest["classes"][0]["origin"])

  def test_conflicting_adapters_fail_closed(self) -> None:
    second = self.root / "second-adapters"
    for root, marker in ((self.adapters, "one"), (second, "two")):
      adapter = root / "io/github/mzmine/example/PublicType.java"
      adapter.parent.mkdir(parents=True)
      adapter.write_text(
          f"package io.github.mzmine.example;\npublic class PublicType {{ String v = \"{marker}\"; }}\n",
          encoding="utf-8",
      )
    with self.assertRaisesRegex(PREPARE.PreparationError, "conflicting adapters"):
      PREPARE.prepare(
          self.checkout, self.closure(), [self.adapters, second], self.output
      )

  def test_refuses_nonpassing_or_wrong_commit_closure(self) -> None:
    closure = self.closure()
    closure["status"] = "fail"
    with self.assertRaisesRegex(PREPARE.PreparationError, "passing status"):
      PREPARE.prepare(self.checkout, closure, [self.adapters], self.output)

    closure = self.closure()
    closure["oracle"]["commit"] = "a" * 40
    with self.assertRaisesRegex(PREPARE.PreparationError, "frozen v4.0.8"):
      PREPARE.prepare(self.checkout, closure, [self.adapters], self.output)

  def test_manifest_hash_is_deterministic(self) -> None:
    first = PREPARE.prepare(
        self.checkout, self.closure(), [self.adapters], self.output
    )
    second = PREPARE.prepare(
        self.checkout, self.closure(), [self.adapters], self.output
    )
    self.assertEqual(first["content_sha256"], second["content_sha256"])
    expected = hashlib.sha256(
        __import__("json").dumps(
            {key: value for key, value in first.items() if key != "content_sha256"},
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
    ).hexdigest()
    self.assertEqual(expected, first["content_sha256"])


if __name__ == "__main__":
  unittest.main()
