#!/usr/bin/env python3
"""Prepare a compile-time probe from the exact public MZmine v4.0.8 SmoothingTask.

The only permitted transformation is a mechanical Java identifier rename of SmoothingTask so the
frozen oracle task can coexist with the candidate task in the same test classpath. No scientific
statement, method body, parameter, constant, or dependency is rewritten.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

ORACLE_COMMIT = "8029f930d28c0447f0acf2bcabef0a79865ad434"
TASK_RELATIVE_PATH = (
    "mzmine-community/src/main/java/io/github/mzmine/modules/dataprocessing/"
    "featdet_smoothing/SmoothingTask.java"
)
TASK_BLOB_SHA1 = "324870813e7f0d58f8c77073bf667738195bce6f"
SOURCE_CLASS = "SmoothingTask"
PROBE_CLASS = "V408SmoothingTaskProbe"


def git(checkout: Path, *args: str) -> str:
  result = subprocess.run(
      ["git", "-C", str(checkout), *args],
      check=True,
      text=True,
      stdout=subprocess.PIPE,
      stderr=subprocess.PIPE,
  )
  return result.stdout.strip()


def identifier_pattern(identifier: str) -> re.Pattern[str]:
  return re.compile(rf"(?<![A-Za-z0-9_$]){re.escape(identifier)}(?![A-Za-z0-9_$])")


def identifier_count(source: str, identifier: str) -> int:
  return len(identifier_pattern(identifier).findall(source))


def rename_identifier(source: str, old: str, new: str) -> tuple[str, int]:
  return identifier_pattern(old).subn(new, source)


def canonical_sha(value: dict) -> str:
  encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
  return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def prepare(checkout: Path, output: Path, evidence: Path) -> dict:
  checkout = checkout.resolve()
  task = checkout / TASK_RELATIVE_PATH
  if not task.is_file():
    raise ValueError(f"missing frozen task source: {task}")

  actual_commit = git(checkout, "rev-parse", "HEAD")
  if actual_commit != ORACLE_COMMIT:
    raise ValueError(f"wrong oracle commit: {actual_commit}")
  actual_blob = git(checkout, "hash-object", TASK_RELATIVE_PATH)
  if actual_blob != TASK_BLOB_SHA1:
    raise ValueError(f"upstream smoothing task blob changed: {actual_blob}")

  source = task.read_text(encoding="utf-8")
  count = identifier_count(source, SOURCE_CLASS)
  if count < 2:
    raise ValueError(f"unexpected SmoothingTask identifier occurrence count: {count}")
  if identifier_count(source, PROBE_CLASS) != 0:
    raise ValueError("probe class identifier already exists in frozen source")

  transformed, renamed = rename_identifier(source, SOURCE_CLASS, PROBE_CLASS)
  if renamed != count:
    raise ValueError(f"mechanical rename count changed: expected {count}, got {renamed}")
  if identifier_count(transformed, SOURCE_CLASS) != 0:
    raise ValueError("source task identifier remains after rename")
  if identifier_count(transformed, PROBE_CLASS) != count:
    raise ValueError("probe identifier count does not match source count")

  output.parent.mkdir(parents=True, exist_ok=True)
  output.write_text(transformed, encoding="utf-8")
  record = {
      "schema_version": 1,
      "probe_id": "mzmine-v4.0.8-smoothing-task-source-probe-v1",
      "oracle_commit": ORACLE_COMMIT,
      "source_path": TASK_RELATIVE_PATH,
      "source_git_blob_sha1": TASK_BLOB_SHA1,
      "source_class": SOURCE_CLASS,
      "probe_class": PROBE_CLASS,
      "transformations": [{
          "kind": "mechanical-java-identifier-class-rename",
          "from": SOURCE_CLASS,
          "to": PROBE_CLASS,
          "occurrences": count,
      }],
      "output_sha256": hashlib.sha256(transformed.encode("utf-8")).hexdigest(),
  }
  record["content_sha256"] = canonical_sha(record)
  evidence.parent.mkdir(parents=True, exist_ok=True)
  evidence.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
  return record


def main() -> int:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--checkout", type=Path, required=True)
  parser.add_argument("--output", type=Path, required=True)
  parser.add_argument("--evidence", type=Path, required=True)
  args = parser.parse_args()
  try:
    record = prepare(args.checkout, args.output, args.evidence)
  except (OSError, ValueError, subprocess.CalledProcessError) as exc:
    print(f"smoothing task probe preparation refused: {exc}")
    return 2
  print(json.dumps(record, indent=2, sort_keys=True))
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
