#!/usr/bin/env python3
"""Prepare the exact public v4.0.8 FeatureResolverTask as a coexisting test probe.

The transformation is limited to an exact Java identifier rename. Before generating the probe the
script verifies the exact v4.0.8 checkout and requires the candidate FeatureResolverTask bytes to be
identical to the frozen oracle source. The concrete MinimumSearchFeatureResolver is separately
covered by the resolver source gate.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

ORACLE_COMMIT = "8029f930d28c0447f0acf2bcabef0a79865ad434"
RELATIVE_SOURCE = (
    "io/github/mzmine/modules/dataprocessing/featdet_chromatogramdeconvolution/"
    "FeatureResolverTask.java"
)
ORACLE_PREFIX = Path("mzmine-community/src/main/java")
CANDIDATE_PREFIX = Path("src/main/java")
SOURCE_CLASS = "FeatureResolverTask"
PROBE_CLASS = "V408FeatureResolverTaskProbe"


def git(checkout: Path, *args: str) -> str:
  result = subprocess.run(
      ["git", "-C", str(checkout), *args], check=True, text=True,
      stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  return result.stdout.strip()


def identifier_pattern(identifier: str) -> re.Pattern[str]:
  return re.compile(rf"(?<![A-Za-z0-9_$]){re.escape(identifier)}(?![A-Za-z0-9_$])")


def git_blob_sha1(path: Path) -> str:
  data = path.read_bytes()
  return hashlib.sha1(f"blob {len(data)}\0".encode("ascii") + data).hexdigest()


def prepare(oracle_checkout: Path, candidate_root: Path, output: Path, evidence: Path) -> dict:
  oracle_checkout = oracle_checkout.resolve()
  candidate_root = candidate_root.resolve()
  actual_commit = git(oracle_checkout, "rev-parse", "HEAD")
  if actual_commit != ORACLE_COMMIT:
    raise ValueError(f"wrong oracle commit: {actual_commit}")

  oracle_source = oracle_checkout / ORACLE_PREFIX / RELATIVE_SOURCE
  candidate_source = candidate_root / CANDIDATE_PREFIX / RELATIVE_SOURCE
  if not oracle_source.is_file() or not candidate_source.is_file():
    raise ValueError("FeatureResolverTask source is missing on oracle or candidate side")
  oracle_blob = git_blob_sha1(oracle_source)
  candidate_blob = git_blob_sha1(candidate_source)
  if oracle_blob != candidate_blob:
    raise ValueError(
        f"FeatureResolverTask is not byte-identical: oracle={oracle_blob} candidate={candidate_blob}")

  source = oracle_source.read_text(encoding="utf-8")
  pattern = identifier_pattern(SOURCE_CLASS)
  count = len(pattern.findall(source))
  if count < 2:
    raise ValueError(f"unexpected FeatureResolverTask identifier count: {count}")
  if identifier_pattern(PROBE_CLASS).search(source):
    raise ValueError("probe class identifier already exists in frozen source")
  transformed, renamed = pattern.subn(PROBE_CLASS, source)
  if renamed != count or pattern.search(transformed):
    raise ValueError("mechanical FeatureResolverTask rename was not exact")

  output.parent.mkdir(parents=True, exist_ok=True)
  output.write_text(transformed, encoding="utf-8")
  report = {
      "schema_version": 1,
      "probe_id": "mzmine-v4.0.8-feature-resolver-task-source-probe-v1",
      "oracle_commit": ORACLE_COMMIT,
      "relative_source": RELATIVE_SOURCE,
      "source_git_blob_sha1": oracle_blob,
      "candidate_git_blob_sha1": candidate_blob,
      "source_class": SOURCE_CLASS,
      "probe_class": PROBE_CLASS,
      "transformation": "mechanical-java-identifier-class-rename-only",
      "renamed_identifier_occurrences": renamed,
      "output_sha256": hashlib.sha256(transformed.encode("utf-8")).hexdigest(),
  }
  evidence.parent.mkdir(parents=True, exist_ok=True)
  evidence.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
  return report


def main() -> int:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--oracle-checkout", type=Path, required=True)
  parser.add_argument("--candidate-root", type=Path, required=True)
  parser.add_argument("--output", type=Path, required=True)
  parser.add_argument("--evidence", type=Path, required=True)
  args = parser.parse_args()
  try:
    report = prepare(args.oracle_checkout, args.candidate_root, args.output, args.evidence)
  except (OSError, ValueError, subprocess.CalledProcessError) as exc:
    print(f"FeatureResolverTask probe preparation refused: {exc}")
    return 2
  print(json.dumps(report, indent=2, sort_keys=True))
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
