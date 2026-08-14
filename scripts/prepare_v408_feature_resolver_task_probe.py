#!/usr/bin/env python3
"""Prepare the exact public v4.0.8 FeatureResolverTask for the governed minimum-search path.

The v4 task differs from the 3.9/open-offline framework in two branches that are explicitly outside
this narrow gate: legacy R-based FeatureResolver compatibility and optional group-MS2 processing.
The published minimum-search parameters select the modern Resolver path, and the side producer must
assert group-MS2 is disabled. This preparer therefore adapts only those compile-time incompatible,
non-executed branches while proving the executed dimension-independent resolver and feature-list
construction method bodies remain byte-for-byte unchanged after the class rename/adaptation.
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
ORACLE_TASK_BLOB = "039446715b253b4b7c8604fcc47dcbc48f6d0e38"
CANDIDATE_TASK_BLOB = "7414192bb58ca3f3acbe1e42f341624a5499b537"


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


def method_source(source: str, signature_fragment: str) -> str:
  start = source.find(signature_fragment)
  if start < 0:
    raise ValueError(f"required method signature not found: {signature_fragment}")
  brace = source.find("{", start)
  if brace < 0:
    raise ValueError(f"method opening brace not found: {signature_fragment}")
  depth = 0
  for index in range(brace, len(source)):
    char = source[index]
    if char == "{":
      depth += 1
    elif char == "}":
      depth -= 1
      if depth == 0:
        return source[start:index + 1]
  raise ValueError(f"method closing brace not found: {signature_fragment}")


def sha256_text(value: str) -> str:
  return hashlib.sha256(value.encode("utf-8")).hexdigest()


def replace_exact(source: str, old: str, new: str, label: str, expected_count: int = 1) -> tuple[str, dict]:
  count = source.count(old)
  if count != expected_count:
    raise ValueError(f"unexpected {label} occurrence count: {count}, expected {expected_count}")
  return source.replace(old, new), {
      "label": label,
      "occurrences": count,
      "old_sha256": sha256_text(old),
      "new_sha256": sha256_text(new),
  }


def adapt_v408_source(source: str) -> tuple[str, list[dict]]:
  transformations: list[dict] = []

  pattern = identifier_pattern(SOURCE_CLASS)
  count = len(pattern.findall(source))
  if count < 2:
    raise ValueError(f"unexpected FeatureResolverTask identifier count: {count}")
  if identifier_pattern(PROBE_CLASS).search(source):
    raise ValueError("probe class identifier already exists in frozen source")
  source, renamed = pattern.subn(PROBE_CLASS, source)
  if renamed != count or pattern.search(source):
    raise ValueError("mechanical FeatureResolverTask rename was not exact")
  transformations.append({
      "label": "mechanical-java-identifier-class-rename",
      "occurrences": renamed,
      "old_sha256": sha256_text(SOURCE_CLASS),
      "new_sha256": sha256_text(PROBE_CLASS),
  })

  replacements = [
      (
          "import io.github.mzmine.modules.dataprocessing.filter_groupms2.GroupMS2Processor;",
          "import io.github.mzmine.modules.dataprocessing.filter_groupms2.GroupMS2Task;\n"
          "import io.github.mzmine.util.R.RSessionWrapperException;",
          "compile-boundary-imports-for-nonexecuted-branches",
      ),
      (
          "private GroupMS2Processor groupMS2Task;",
          "private GroupMS2Task groupMS2Task;",
          "group-ms2-field-type-nonexecuted-branch",
      ),
      (
          "groupMS2Task = new GroupMS2Processor(this, newPeakList, ms2params);\n"
          "            // group all features with MS/MS\n"
          "            groupMS2Task.process();\n"
          "            groupMs2Param = null; // clear progress",
          "groupMS2Task = new GroupMS2Task(newPeakList, ms2params, moduleCallDate);\n"
          "            // group all features with MS/MS\n"
          "            groupMS2Task.processFeatureList(this);",
          "group-ms2-implementation-nonexecuted-branch",
      ),
      (
          "private void legacyResolve() {",
          "private void legacyResolve() throws RSessionWrapperException {",
          "legacy-r-throws-compatibility-nonexecuted-branch",
      ),
      (
          "newPeakList = resolvePeaks((ModularFeatureList) originalPeakList);",
          "newPeakList = resolvePeaks((ModularFeatureList) originalPeakList);",
          "legacy-resolve-call-preserved",
      ),
      (
          "private FeatureList resolvePeaks(final ModularFeatureList originalFeatureList) {",
          "private FeatureList resolvePeaks(final ModularFeatureList originalFeatureList)\n"
          "      throws RSessionWrapperException {",
          "legacy-helper-throws-compatibility-nonexecuted-branch",
      ),
      (
          "final ResolvedPeak[] peaks = resolver.resolvePeaks(originalFeature, parameters,\n"
          "          mzCenterFunction, msmsRange, RTRangeMSMS);",
          "final ResolvedPeak[] peaks = resolver.resolvePeaks(originalFeature, parameters, null,\n"
          "          mzCenterFunction, msmsRange, RTRangeMSMS);",
          "legacy-r-null-session-compatibility-nonexecuted-branch",
      ),
  ]
  for old, new, label in replacements:
    # The preserved call is an explicit assertion rather than a mutation.
    if old == new:
      if source.count(old) != 1:
        raise ValueError(f"unexpected {label} occurrence count: {source.count(old)}")
      transformations.append({
          "label": label,
          "occurrences": 1,
          "old_sha256": sha256_text(old),
          "new_sha256": sha256_text(new),
      })
      continue
    source, evidence = replace_exact(source, old, new, label)
    transformations.append(evidence)

  return source, transformations


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
  if oracle_blob != ORACLE_TASK_BLOB:
    raise ValueError(f"frozen oracle FeatureResolverTask blob drift: {oracle_blob}")
  if candidate_blob != CANDIDATE_TASK_BLOB:
    raise ValueError(f"candidate FeatureResolverTask blob drift: {candidate_blob}")

  oracle_text = oracle_source.read_text(encoding="utf-8")
  governed_signatures = [
      "private void dimensionIndependentResolve(ModularFeatureList originalFeatureList)",
      "private ModularFeatureList createNewFeatureList(ModularFeatureList originalFeatureList)",
  ]
  governed_before = {
      signature: sha256_text(method_source(oracle_text, signature)) for signature in governed_signatures
  }

  transformed, transformations = adapt_v408_source(oracle_text)
  governed_after = {
      signature: sha256_text(method_source(transformed, signature)) for signature in governed_signatures
  }
  if governed_before != governed_after:
    raise ValueError("governed dimension-independent resolver method body changed during adaptation")

  output.parent.mkdir(parents=True, exist_ok=True)
  output.write_text(transformed, encoding="utf-8")
  report = {
      "schema_version": 2,
      "probe_id": "mzmine-v4.0.8-feature-resolver-task-governed-path-probe-v2",
      "oracle_commit": ORACLE_COMMIT,
      "relative_source": RELATIVE_SOURCE,
      "source_git_blob_sha1": oracle_blob,
      "candidate_git_blob_sha1": candidate_blob,
      "source_class": SOURCE_CLASS,
      "probe_class": PROBE_CLASS,
      "transformation_policy": "class-rename-plus-compile-only-adaptation-of-asserted-nonexecuted-branches",
      "governed_path_requirements": {
          "modern_minimum_search_resolver_non_null": True,
          "legacy_feature_resolver_branch_executed": False,
          "group_ms2_enabled": False,
      },
      "governed_method_sha256_before": governed_before,
      "governed_method_sha256_after": governed_after,
      "governed_methods_unchanged": governed_before == governed_after,
      "transformations": transformations,
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
