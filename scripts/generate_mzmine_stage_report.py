#!/usr/bin/env python3
"""Generate a normalized open-offline import/mass-detection report from governed mzML bytes.

The command validates the selected public dataset record and local bytes before invoking the opt-in
Java acceptance test. The produced JSON is then validated with the same independent contract used by
the differential comparator.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT / "scripts") not in sys.path:
  sys.path.insert(0, str(ROOT / "scripts"))

from compare_mzmine_stage_reports import ReportError, load_report  # noqa: E402

DEFAULT_DATASET_ID = "zenodo-14001110-banane-30ngml-001"
DEFAULT_TEST = "MZmineDifferentialReportAcceptanceTest.generateConfiguredReport"


class GenerationError(ValueError):
  """Raised when generation cannot proceed without violating provenance rules."""


def _load_json(path: Path) -> dict[str, Any]:
  try:
    value = json.loads(path.read_text(encoding="utf-8"))
  except FileNotFoundError as exc:
    raise GenerationError(f"missing JSON file: {path}") from exc
  except json.JSONDecodeError as exc:
    raise GenerationError(f"invalid JSON in {path}: {exc}") from exc
  if not isinstance(value, dict):
    raise GenerationError(f"top-level JSON value must be an object: {path}")
  return value


def _dataset(manifest: dict[str, Any], dataset_id: str) -> dict[str, Any]:
  datasets = manifest.get("datasets")
  if not isinstance(datasets, list):
    raise GenerationError("public manifest must contain a datasets array")
  matches = [dataset for dataset in datasets
             if isinstance(dataset, dict) and dataset.get("id") == dataset_id]
  if len(matches) != 1:
    raise GenerationError(
        f"dataset id must resolve to exactly one manifest entry: {dataset_id}"
    )
  dataset = matches[0]
  if dataset.get("download_enabled") is not True:
    raise GenerationError(f"dataset is not approved for governed download/use: {dataset_id}")

  files = dataset.get("files")
  if not isinstance(files, list) or len(files) != 1 or not isinstance(files[0], dict):
    raise GenerationError(
        f"this first-stage producer requires exactly one governed file: {dataset_id}"
    )
  file_record = files[0]
  expected_size = file_record.get("expected_size_bytes")
  expected_sha = file_record.get("sha256")
  relative_path = file_record.get("relative_path")
  if not isinstance(expected_size, int) or expected_size < 1:
    raise GenerationError(f"dataset file has no frozen positive byte size: {dataset_id}")
  if (not isinstance(expected_sha, str) or len(expected_sha) != 64
      or any(character not in "0123456789abcdef" for character in expected_sha)):
    raise GenerationError(f"dataset file has no frozen lowercase SHA-256: {dataset_id}")
  if not isinstance(relative_path, str) or not relative_path:
    raise GenerationError(f"dataset file has no relative_path: {dataset_id}")

  return {
      "dataset_id": dataset_id,
      "relative_path": relative_path,
      "expected_size_bytes": expected_size,
      "sha256": expected_sha,
  }


def _sha256(path: Path) -> str:
  digest = hashlib.sha256()
  with path.open("rb") as handle:
    while chunk := handle.read(1024 * 1024):
      digest.update(chunk)
  return digest.hexdigest()


def validate_input(path: Path, dataset: dict[str, Any]) -> tuple[int, str]:
  if not path.is_file():
    raise GenerationError(f"input is not a regular file: {path}")
  size = path.stat().st_size
  expected_size = dataset["expected_size_bytes"]
  if size != expected_size:
    raise GenerationError(
        f"input byte size mismatch: expected {expected_size}, got {size}"
    )
  sha = _sha256(path)
  if sha != dataset["sha256"]:
    raise GenerationError(
        f"input SHA-256 mismatch: expected {dataset['sha256']}, got {sha}"
    )
  return size, sha


def gradle_command(repository: Path, test_name: str) -> list[str]:
  wrapper = repository / ("gradlew.bat" if os.name == "nt" else "gradlew")
  if not wrapper.is_file():
    raise GenerationError(f"Gradle wrapper is missing: {wrapper}")
  command = [str(wrapper)]
  if os.name != "nt" and not os.access(wrapper, os.X_OK):
    command = ["bash", str(wrapper)]
  return command + [
      "test",
      "--tests",
      test_name,
      "--no-daemon",
      "--rerun-tasks",
      "--stacktrace",
  ]


def build_environment(
    args: argparse.Namespace,
    dataset: dict[str, Any],
    input_sha: str,
) -> dict[str, str]:
  environment = os.environ.copy()
  environment.update({
      "MZMINE_PARITY_INPUT": str(args.input.resolve()),
      "MZMINE_PARITY_OUTPUT": str(args.output.resolve()),
      "MZMINE_PARITY_DATASET_ID": dataset["dataset_id"],
      "MZMINE_PARITY_RELATIVE_PATH": dataset["relative_path"],
      "MZMINE_PARITY_EXPECTED_SHA256": input_sha,
      "MZMINE_PARITY_REPOSITORY": args.producer_repository,
      "MZMINE_PARITY_REF": args.producer_ref,
      "MZMINE_PARITY_COMMIT": args.producer_commit,
      "MZMINE_PARITY_APPLICATION_VERSION": args.application_version,
      "MZMINE_PARITY_THREADS": str(args.threads),
  })
  return environment


def build_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--input", type=Path, required=True)
  parser.add_argument("--output", type=Path, required=True)
  parser.add_argument("--dataset-id", default=DEFAULT_DATASET_ID)
  parser.add_argument(
      "--manifest",
      type=Path,
      default=ROOT / "datasets/public_validation_manifest.json",
  )
  parser.add_argument("--repository", type=Path, default=ROOT)
  parser.add_argument("--producer-repository", default="VitorFrost/mzmine")
  parser.add_argument("--producer-ref", required=True)
  parser.add_argument("--producer-commit", required=True)
  parser.add_argument("--application-version", default="3.9.1")
  parser.add_argument("--threads", type=int, default=1)
  parser.add_argument("--test-name", default=DEFAULT_TEST)
  parser.add_argument(
      "--dry-run",
      action="store_true",
      help="Validate provenance and print the command without executing Gradle.",
  )
  return parser


def main() -> int:
  args = build_parser().parse_args()
  try:
    if args.threads < 1:
      raise GenerationError("--threads must be positive")
    if len(args.producer_commit) != 40 or any(
        character not in "0123456789abcdef" for character in args.producer_commit
    ):
      raise GenerationError("--producer-commit must be a lowercase 40-character SHA")
    dataset = _dataset(_load_json(args.manifest), args.dataset_id)
    _, input_sha = validate_input(args.input, dataset)
    command = gradle_command(args.repository.resolve(), args.test_name)
    environment = build_environment(args, dataset, input_sha)
  except GenerationError as exc:
    print(f"Stage report generation refused: {exc}", file=sys.stderr)
    return 2

  print(json.dumps({
      "command": command,
      "dataset_id": dataset["dataset_id"],
      "input": str(args.input.resolve()),
      "input_sha256": input_sha,
      "output": str(args.output.resolve()),
      "producer_commit": args.producer_commit,
      "producer_ref": args.producer_ref,
      "relative_path": dataset["relative_path"],
  }, indent=2, sort_keys=True))
  if args.dry_run:
    return 0

  args.output.parent.mkdir(parents=True, exist_ok=True)
  if args.output.exists():
    args.output.unlink()
  completed = subprocess.run(
      command,
      cwd=args.repository.resolve(),
      env=environment,
      check=False,
  )
  if completed.returncode != 0:
    print(
        f"Gradle report producer failed with exit code {completed.returncode}",
        file=sys.stderr,
    )
    return completed.returncode

  try:
    report = load_report(args.output)
  except ReportError as exc:
    print(f"Generated report is invalid: {exc}", file=sys.stderr)
    return 2
  if report["input"]["dataset_id"] != dataset["dataset_id"]:
    print("Generated report contains the wrong dataset id", file=sys.stderr)
    return 2
  if report["input"]["relative_path"] != dataset["relative_path"]:
    print("Generated report contains the wrong relative input path", file=sys.stderr)
    return 2
  if report["input"]["sha256"] != input_sha:
    print("Generated report contains the wrong input SHA-256", file=sys.stderr)
    return 2
  if report["producer"]["commit"] != args.producer_commit:
    print("Generated report contains the wrong producer commit", file=sys.stderr)
    return 2

  print(f"Validated differential stage report: {args.output}")
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
