#!/usr/bin/env python3

from __future__ import annotations

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


PREPARE = load_module(
    "prepare_v408_executable_slice",
    SCRIPTS / "prepare_v408_executable_slice.py",
)


class PrepareV408ExecutableSliceTest(unittest.TestCase):

  FQCN = "io.github.mzmine.example.PublicType"
  UPSTREAM_RELATIVE = (
      "mzmine-community/src/main/java/io/github/mzmine/example/PublicType.java"
  )

  def setUp(self) -> None:
    self.temp = tempfile.TemporaryDirectory()
    self.root = Path(self.temp.name)
    self.checkout = self.root / "checkout"
    self.adapter_root = self.root / "adapters"
    self.output = self.root / "generated"
    self.upstream = self.checkout / self.UPSTREAM_RELATIVE
    self.upstream.parent.mkdir(parents=True)
    self.upstream.write_text(
        "package io.github.mzmine.example;\npublic class PublicType {}\n",
        encoding="utf-8",
    )
    self.adapter_root.mkdir()

  def tearDown(self) -> None:
    self.temp.cleanup()

  def root_spec(self, path: Path | None = None):
    return PREPARE.RootSpec("test-adapters", path or self.adapter_root)

  def closure(self, boundary: bool = False) -> dict:
    return {
        "status": "pass",
        "content_sha256": "c" * 64,
        "oracle": {"commit": PREPARE.ORACLE_COMMIT},
        "reviewed_boundaries_reached": (
            [{"class": self.FQCN, "classification": "synthetic-boundary"}]
            if boundary else []
        ),
        "nodes": [{
            "class": self.FQCN,
            "path": self.UPSTREAM_RELATIVE,
            "source_sha256": PREPARE.sha256(self.upstream),
            "classification": "synthetic-boundary" if boundary else "entry-point",
        }],
    }

  def lock(self, root: Path | None = None, expected_count: int = 0) -> dict:
    return PREPARE.adapter_inventory(
        [self.root_spec(root)], expected_count=expected_count
    )

  def contract(self, closure: dict, lock: dict, boundary: bool = False) -> dict:
    adapters = []
    if boundary:
      adapters.append({
          "class": self.FQCN,
          "execution_mode": "synthetic-executed",
          "preserved_members": ["constructor"],
          "excluded_behavior": "synthetic excluded behavior",
      })
    return {
        "schema_version": 1,
        "contract_id": "synthetic-contract",
        "oracle_commit": PREPARE.ORACLE_COMMIT,
        "source_closure_sha256": closure["content_sha256"],
        "adapter_lock_sha256": lock["content_sha256"],
        "adapters": adapters,
    }

  def write_adapter(self, root: Path | None = None, marker: str = "adapter") -> Path:
    root = root or self.adapter_root
    path = root / "io/github/mzmine/example/PublicType.java"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "package io.github.mzmine.example;\n"
        f"public final class PublicType {{ String marker = \"{marker}\"; }}\n",
        encoding="utf-8",
    )
    return path

  def prepare(self, closure: dict, lock: dict, contract: dict,
      checkout: Path | None = None, adapter_root: Path | None = None,
      output: Path | None = None) -> dict:
    return PREPARE.prepare(
        checkout or self.checkout,
        closure,
        [self.root_spec(adapter_root)],
        lock,
        contract,
        output or self.output,
    )

  def test_copies_only_verified_upstream_source(self) -> None:
    closure = self.closure()
    lock = self.lock()
    manifest = self.prepare(closure, lock, self.contract(closure, lock))
    copied = self.output / "io/github/mzmine/example/PublicType.java"
    self.assertEqual(self.upstream.read_bytes(), copied.read_bytes())
    self.assertEqual(1, manifest["class_count"])
    self.assertEqual(1, manifest["source_file_count"])
    self.assertEqual(0, manifest["adapter_class_count"])
    self.assertEqual(
        {"repository_relative_path": self.UPSTREAM_RELATIVE},
        manifest["source_files"][0]["source_ref"],
    )

  def test_reached_boundary_uses_exact_locked_adapter(self) -> None:
    adapter = self.write_adapter()
    closure = self.closure(boundary=True)
    lock = self.lock(expected_count=1)
    contract = self.contract(closure, lock, boundary=True)
    manifest = self.prepare(closure, lock, contract)
    copied = self.output / "io/github/mzmine/example/PublicType.java"
    self.assertEqual(adapter.read_bytes(), copied.read_bytes())
    self.assertEqual(1, manifest["adapter_class_count"])
    self.assertEqual("open-offline-adapter", manifest["classes"][0]["origin"])
    self.assertEqual(
        {
            "root_id": "test-adapters",
            "relative_path": "io/github/mzmine/example/PublicType.java",
        },
        manifest["classes"][0]["source_ref"],
    )

  def test_refuses_changed_upstream_bytes_before_mutating_output(self) -> None:
    closure = self.closure()
    lock = self.lock()
    contract = self.contract(closure, lock)
    self.output.mkdir()
    sentinel = self.output / "sentinel.txt"
    sentinel.write_text("preserve", encoding="utf-8")
    self.upstream.write_text(
        "package io.github.mzmine.example;\npublic class PublicType { int changed; }\n",
        encoding="utf-8",
    )
    with self.assertRaisesRegex(PREPARE.PreparationError, "source SHA mismatch"):
      self.prepare(closure, lock, contract)
    self.assertEqual("preserve", sentinel.read_text(encoding="utf-8"))

  def test_changed_adapter_byte_fails_lock_before_copying(self) -> None:
    adapter = self.write_adapter()
    closure = self.closure(boundary=True)
    lock = self.lock(expected_count=1)
    contract = self.contract(closure, lock, boundary=True)
    adapter.write_text(adapter.read_text(encoding="utf-8") + "// changed\n", encoding="utf-8")
    with self.assertRaisesRegex(PREPARE.PreparationError, "adapter lock verification failed"):
      self.prepare(closure, lock, contract)
    self.assertFalse(self.output.exists())

  def test_missing_boundary_adapter_fails_closed(self) -> None:
    closure = self.closure(boundary=True)
    lock = self.lock()
    contract = self.contract(closure, lock)
    with self.assertRaisesRegex(PREPARE.PreparationError, "coverage mismatch"):
      self.prepare(closure, lock, contract)
    self.assertFalse(self.output.exists())

  def test_unreviewed_adapter_fails_closed(self) -> None:
    self.write_adapter()
    closure = self.closure(boundary=False)
    lock = self.lock(expected_count=1)
    contract = self.contract(closure, lock, boundary=True)
    with self.assertRaisesRegex(PREPARE.PreparationError, "coverage mismatch"):
      self.prepare(closure, lock, contract)

  def test_wrong_contract_lock_or_closure_is_rejected(self) -> None:
    self.write_adapter()
    closure = self.closure(boundary=True)
    lock = self.lock(expected_count=1)
    contract = self.contract(closure, lock, boundary=True)

    wrong = json.loads(json.dumps(contract))
    wrong["adapter_lock_sha256"] = "0" * 64
    with self.assertRaisesRegex(PREPARE.PreparationError, "wrong adapter lock"):
      self.prepare(closure, lock, wrong)

    wrong = json.loads(json.dumps(contract))
    wrong["source_closure_sha256"] = "0" * 64
    with self.assertRaisesRegex(PREPARE.PreparationError, "wrong source closure"):
      self.prepare(closure, lock, wrong)

  def test_manifest_is_portable_across_parent_directories(self) -> None:
    self.write_adapter()
    closure = self.closure(boundary=True)
    lock = self.lock(expected_count=1)
    contract = self.contract(closure, lock, boundary=True)
    first = self.prepare(closure, lock, contract)

    second_root = self.root / "other-workspace"
    second_checkout = second_root / "checkout"
    second_adapter_root = second_root / "adapters"
    second_output = second_root / "generated"
    shutil.copytree(self.checkout, second_checkout)
    shutil.copytree(self.adapter_root, second_adapter_root)
    second = self.prepare(
        closure, lock, contract,
        checkout=second_checkout,
        adapter_root=second_adapter_root,
        output=second_output,
    )
    self.assertEqual(first, second)
    serialized = json.dumps(first, sort_keys=True)
    self.assertNotIn(str(self.root), serialized)
    self.assertNotIn("/home/runner", serialized)

  def test_refuses_nonpassing_or_wrong_commit_closure(self) -> None:
    closure = self.closure()
    lock = self.lock()
    contract = self.contract(closure, lock)
    closure["status"] = "fail"
    with self.assertRaisesRegex(PREPARE.PreparationError, "passing status"):
      self.prepare(closure, lock, contract)

    closure = self.closure()
    contract = self.contract(closure, lock)
    closure["oracle"]["commit"] = "a" * 40
    with self.assertRaisesRegex(PREPARE.PreparationError, "frozen v4.0.8"):
      self.prepare(closure, lock, contract)

  def test_manifest_hash_is_deterministic(self) -> None:
    closure = self.closure()
    lock = self.lock()
    contract = self.contract(closure, lock)
    first = self.prepare(closure, lock, contract)
    second = self.prepare(closure, lock, contract)
    self.assertEqual(first, second)
    core = {key: value for key, value in first.items() if key != "content_sha256"}
    self.assertEqual(PREPARE.canonical_sha(core), first["content_sha256"])


if __name__ == "__main__":
  unittest.main()
