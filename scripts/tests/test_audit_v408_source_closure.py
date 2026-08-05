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


AUDITOR = load_module(
    "audit_v408_source_closure",
    SCRIPTS / "audit_v408_source_closure.py",
)


class SourceClosureAuditTest(unittest.TestCase):

  def setUp(self) -> None:
    self.temp = tempfile.TemporaryDirectory()
    self.checkout = Path(self.temp.name)
    self.source = self.checkout / "src/main/java"
    self._write(
        "io/github/mzmine/Root.java",
        """
        package io.github.mzmine;
        import io.github.mzmine.dep.Dep;
        import io.github.mzmine.main.MZmineCore;
        public class Root { Dep dep; MZmineCore core; }
        """,
    )
    self._write(
        "io/github/mzmine/dep/Dep.java",
        """
        package io.github.mzmine.dep;
        public class Dep { Helper helper; }
        """,
    )
    self._write(
        "io/github/mzmine/dep/Helper.java",
        """
        package io.github.mzmine.dep;
        public class Helper { }
        """,
    )
    self._write(
        "io/github/mzmine/main/MZmineCore.java",
        """
        package io.github.mzmine.main;
        import io.mzio.users.UserService;
        public class MZmineCore { UserService service; }
        """,
    )

  def tearDown(self) -> None:
    self.temp.cleanup()

  def _write(self, relative: str, content: str) -> None:
    path = self.source / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.strip() + "\n", encoding="utf-8")

  def manifest(self) -> dict:
    return {
        "schema_version": 1,
        "audit_id": "synthetic-source-closure",
        "oracle": {
            "repository": "example/repo",
            "tag": "v1",
            "commit": "a" * 40,
            "license": "MIT",
        },
        "source_roots": ["src/main/java"],
        "entry_points": ["io.github.mzmine.Root"],
        "internal_prefixes": ["io.github.mzmine."],
        "prohibited_prefixes": ["io.mzio."],
        "reviewed_boundaries": {
            "io.github.mzmine.main.MZmineCore": {
                "classification": "bootstrap-boundary",
                "reason": "synthetic reviewed boundary",
            }
        },
        "expected_prohibited_prefixes": ["io.mzio."],
        "policy": {
            "follow_reviewed_boundaries": False,
            "fail_on_missing_entry_point": True,
            "fail_on_unresolved_internal_import": True,
            "fail_on_unreviewed_prohibited_reference": True,
            "require_at_least_one_reviewed_boundary": True,
            "require_at_least_one_prohibited_reference_behind_boundary": False,
        },
    }

  def test_reaches_dependency_and_stops_at_reviewed_boundary(self) -> None:
    report, violations = AUDITOR.audit(self.checkout, self.manifest(), False)
    self.assertEqual([], violations)
    self.assertEqual("pass", report["status"])
    classes = {node["class"] for node in report["nodes"]}
    self.assertEqual(
        {
            "io.github.mzmine.Root",
            "io.github.mzmine.dep.Dep",
            "io.github.mzmine.dep.Helper",
            "io.github.mzmine.main.MZmineCore",
        },
        classes,
    )
    self.assertEqual(1, report["summary"]["reviewed_boundaries_reached"])
    self.assertEqual(1, report["summary"]["prohibited_references"])
    self.assertEqual(
        "io.mzio.users.UserService",
        report["prohibited_references"][0]["reference"],
    )

  def test_duplicate_nested_names_are_not_indexed_as_top_level_types(self) -> None:
    self._write(
        "io/github/mzmine/dep/Dep.java",
        """
        package io.github.mzmine.dep;
        public class Dep {
          static class DataPointIterator { }
          Helper helper;
        }
        """,
    )
    self._write(
        "io/github/mzmine/dep/Helper.java",
        """
        package io.github.mzmine.dep;
        public class Helper {
          private class DataPointIterator { }
        }
        """,
    )
    index, _, _ = AUDITOR.build_index(self.checkout, ["src/main/java"])
    self.assertIn("io.github.mzmine.dep.Dep", index)
    self.assertIn("io.github.mzmine.dep.Helper", index)
    self.assertNotIn("io.github.mzmine.dep.DataPointIterator", index)
    report, violations = AUDITOR.audit(self.checkout, self.manifest(), False)
    self.assertEqual([], violations)
    self.assertEqual("pass", report["status"])

  def test_multiple_real_top_level_types_are_indexed(self) -> None:
    self._write(
        "io/github/mzmine/dep/Dep.java",
        """
        package io.github.mzmine.dep;
        public class Dep { Helper helper; }
        final class PackageHelper { }
        """,
    )
    index, _, _ = AUDITOR.build_index(self.checkout, ["src/main/java"])
    self.assertIn("io.github.mzmine.dep.Dep", index)
    self.assertIn("io.github.mzmine.dep.PackageHelper", index)

  def test_unreviewed_prohibited_reference_fails(self) -> None:
    self._write(
        "io/github/mzmine/Root.java",
        """
        package io.github.mzmine;
        import io.github.mzmine.main.MZmineCore;
        import io.mzio.events.EventService;
        public class Root { MZmineCore core; EventService events; }
        """,
    )
    report, violations = AUDITOR.audit(self.checkout, self.manifest(), False)
    self.assertEqual("fail", report["status"])
    self.assertTrue(any("unreviewed prohibited reference" in value for value in violations))

  def test_unresolved_internal_import_fails(self) -> None:
    self._write(
        "io/github/mzmine/Root.java",
        """
        package io.github.mzmine;
        import io.github.mzmine.main.MZmineCore;
        import io.github.mzmine.missing.MissingType;
        public class Root { MZmineCore core; MissingType missing; }
        """,
    )
    report, violations = AUDITOR.audit(self.checkout, self.manifest(), False)
    self.assertEqual("fail", report["status"])
    self.assertTrue(any("unresolved internal reference" in value for value in violations))

  def test_missing_entry_point_fails(self) -> None:
    manifest = self.manifest()
    manifest["entry_points"] = ["io.github.mzmine.DoesNotExist"]
    report, violations = AUDITOR.audit(self.checkout, manifest, False)
    self.assertEqual("fail", report["status"])
    self.assertIn("missing entry point: io.github.mzmine.DoesNotExist", violations)

  def test_comments_and_literals_do_not_create_dependencies(self) -> None:
    self._write(
        "io/github/mzmine/dep/Dep.java",
        """
        package io.github.mzmine.dep;
        public class Dep {
          // io.github.mzmine.fake.CommentType
          String text = "io.mzio.fake.StringType";
          Helper helper;
        }
        """,
    )
    report, violations = AUDITOR.audit(self.checkout, self.manifest(), False)
    self.assertEqual([], violations)
    references = {item["reference"] for item in report["prohibited_references"]}
    self.assertNotIn("io.mzio.fake.StringType", references)

  def test_report_hash_is_deterministic(self) -> None:
    first, first_violations = AUDITOR.audit(self.checkout, self.manifest(), False)
    second, second_violations = AUDITOR.audit(self.checkout, self.manifest(), False)
    self.assertEqual(first_violations, second_violations)
    self.assertEqual(first["content_sha256"], second["content_sha256"])
    self.assertEqual(
        json.dumps(first, sort_keys=True),
        json.dumps(second, sort_keys=True),
    )

  def test_manifest_rejects_invalid_commit(self) -> None:
    manifest = self.manifest()
    manifest["oracle"]["commit"] = "not-a-sha"
    with self.assertRaisesRegex(AUDITOR.AuditError, "40-character"):
      AUDITOR.audit(self.checkout, manifest, False)


if __name__ == "__main__":
  unittest.main()
