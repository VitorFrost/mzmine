#!/usr/bin/env python3
"""Prepare a compile-time probe from the exact public v4.0.8 SmoothingTask source."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

ORACLE_COMMIT = "8029f930d28c0447f0acf2bcabef0a79865ad434"
SOURCE_PATH = "mzmine-community/src/main/java/io/github/mzmine/modules/dataprocessing/featdet_smoothing/SmoothingTask.java"
SOURCE_BLOB = "324870813e7f0d58f8c77073bf667738195bce6f"
SOURCE_CLASS = "SmoothingTask"
PROBE_CLASS = "V408SmoothingTaskProbe"


def git(checkout: Path, *args: str) -> str:
  result = subprocess.run(["git", "-C", str(checkout), *args], check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  return result.stdout.strip()


def pattern(identifier: str) -> re.Pattern[str]:
  return re.compile(rf"(?<![A-Za-z0-9_$]){re.escape(identifier)}(?![A-Za-z0-9_$])")


def prepare(checkout: Path, output: Path, evidence: Path) -> dict:
  checkout = checkout.resolve()
  if git(checkout, "rev-parse", "HEAD") != ORACLE_COMMIT:
    raise ValueError("wrong v4.0.8 oracle commit")
  source = checkout / SOURCE_PATH
  if not source.is_file():
    raise ValueError(f"missing frozen smoothing task: {source}")
  actual_blob = git(checkout, "hash-object", SOURCE_PATH)
  if actual_blob != SOURCE_BLOB:
    raise ValueError(f"frozen smoothing task blob changed: {actual_blob}")

  text = source.read_text(encoding="utf-8")
  source_matches = len(pattern(SOURCE_CLASS).findall(text))
  if source_matches < 3:
    raise ValueError(f"unexpected SmoothingTask identifier count: {source_matches}")
  if pattern(PROBE_CLASS).search(text):
    raise ValueError("probe class already exists in frozen source")
  transformed, count = pattern(SOURCE_CLASS).subn(PROBE_CLASS, text)
  if count != source_matches or pattern(SOURCE_CLASS).search(transformed):
    raise ValueError("mechanical smoothing task rename was not exact")

  output.parent.mkdir(parents=True, exist_ok=True)
  output.write_text(transformed, encoding="utf-8")
  report = {
      "schema_version": 1,
      "probe_id": "mzmine-v4.0.8-smoothing-task-source-probe-v1",
      "oracle_commit": ORACLE_COMMIT,
      "source_path": SOURCE_PATH,
      "source_git_blob_sha1": SOURCE_BLOB,
      "source_class": SOURCE_CLASS,
      "probe_class": PROBE_CLASS,
      "transformation": "mechanical-java-identifier-class-rename-only",
      "renamed_identifier_occurrences": count,
      "output_sha256": hashlib.sha256(transformed.encode("utf-8")).hexdigest(),
  }
  evidence.parent.mkdir(parents=True, exist_ok=True)
  evidence.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
  return report


def main() -> int:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--checkout", type=Path, required=True)
  parser.add_argument("--output", type=Path, required=True)
  parser.add_argument("--evidence", type=Path, required=True)
  args = parser.parse_args()
  try:
    report = prepare(args.checkout, args.output, args.evidence)
  except (OSError, ValueError, subprocess.CalledProcessError) as exc:
    print(f"Smoothing task probe preparation refused: {exc}")
    return 2
  print(json.dumps(report, indent=2, sort_keys=True))
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
