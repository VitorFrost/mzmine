#!/usr/bin/env python3
"""Prepare a compile-time probe from the frozen public v4.0.8 ADAP task.

The source transformation is intentionally narrow and fail-closed:
1. verify the frozen oracle commit and Git blob of the upstream task;
2. rename only exact Java identifier occurrences of the task class so it can coexist with the
   candidate implementation;
3. for the explicitly non-imaging governed probe, map v4.0.8 sortByDefault(...) to
   sortByDefaultRT(...). Frozen v4.0.8 FeatureListUtils defines these as equivalent for every
   non-ImagingRawDataFile.

No other source substitutions are permitted.
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
    "featdet_adapchromatogrambuilder/ModularADAPChromatogramBuilderTask.java"
)
TASK_BLOB_SHA1 = "ab49a0a13fa1a29707c560f24f15d47fb55db6f0"
SOURCE_CLASS = "ModularADAPChromatogramBuilderTask"
PROBE_CLASS = "V408ModularADAPChromatogramBuilderTaskProbe"
SORT_SOURCE = "FeatureListUtils.sortByDefault(newFeatureList, true);"
SORT_PROBE = "FeatureListUtils.sortByDefaultRT(newFeatureList, true);"


def git(checkout: Path, *args: str) -> str:
  result = subprocess.run(
      ["git", "-C", str(checkout), *args],
      check=True,
      text=True,
      stdout=subprocess.PIPE,
      stderr=subprocess.PIPE,
  )
  return result.stdout.strip()


def canonical_sha(value: dict) -> str:
  encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
  return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def identifier_pattern(identifier: str) -> re.Pattern[str]:
  return re.compile(rf"(?<![A-Za-z0-9_$]){re.escape(identifier)}(?![A-Za-z0-9_$])")


def identifier_count(source: str, identifier: str) -> int:
  return len(identifier_pattern(identifier).findall(source))


def rename_identifier(source: str, old: str, new: str) -> tuple[str, int]:
  pattern = identifier_pattern(old)
  return pattern.subn(new, source)


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
    raise ValueError(f"upstream ADAP task blob changed: {actual_blob}")

  source = task.read_text(encoding="utf-8")
  class_count = identifier_count(source, SOURCE_CLASS)
  sort_count = source.count(SORT_SOURCE)
  if class_count < 3:
    raise ValueError(f"unexpected source class identifier occurrence count: {class_count}")
  if sort_count != 1:
    raise ValueError(f"expected exactly one governed sort call, got {sort_count}")
  if identifier_count(source, PROBE_CLASS) != 0:
    raise ValueError("probe class identifier already exists in frozen source")

  transformed, renamed_count = rename_identifier(source, SOURCE_CLASS, PROBE_CLASS)
  if renamed_count != class_count:
    raise ValueError(
        f"mechanical class rename count changed: expected {class_count}, got {renamed_count}")
  transformed = transformed.replace(SORT_SOURCE, SORT_PROBE)
  if identifier_count(transformed, SOURCE_CLASS) != 0:
    raise ValueError("source class identifier remains after mechanical rename")
  if identifier_count(transformed, PROBE_CLASS) != class_count:
    raise ValueError("probe class identifier count does not match the governed rename count")
  if SORT_SOURCE in transformed:
    raise ValueError("v4 default-sort call remains after governed non-imaging mapping")
  if transformed.count(SORT_PROBE) != 1:
    raise ValueError("governed non-imaging sort mapping is ambiguous")

  output.parent.mkdir(parents=True, exist_ok=True)
  output.write_text(transformed, encoding="utf-8")

  record = {
      "schema_version": 1,
      "probe_id": "mzmine-v4.0.8-adap-task-non-imaging-source-probe-v1",
      "oracle_commit": ORACLE_COMMIT,
      "source_path": TASK_RELATIVE_PATH,
      "source_git_blob_sha1": TASK_BLOB_SHA1,
      "source_class": SOURCE_CLASS,
      "probe_class": PROBE_CLASS,
      "transformations": [
          {
              "kind": "mechanical-java-identifier-class-rename",
              "from": SOURCE_CLASS,
              "to": PROBE_CLASS,
              "occurrences": class_count,
          },
          {
              "kind": "governed-non-imaging-equivalent-sort-mapping",
              "from": SORT_SOURCE,
              "to": SORT_PROBE,
              "occurrences": 1,
              "precondition": "RawDataFile is not ImagingRawDataFile",
              "basis": "frozen v4.0.8 FeatureListUtils.sortByDefault dispatches non-imaging data to sortByDefaultRT",
          },
      ],
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
    print(f"ADAP task source probe preparation refused: {exc}")
    return 2
  print(json.dumps(record, indent=2, sort_keys=True))
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
