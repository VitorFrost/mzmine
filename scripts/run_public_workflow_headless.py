#!/usr/bin/env python3
"""Run the frozen public MZmine workflow and always emit a diagnostic JSON report.

The Java fixture generator creates a 12-step batch: approved mzML import, the ten published
processing steps, and a deterministic CSV export. This script launches a separate MZmine process,
tracks progressive batch entry, records the last safely completed stage, validates the CSV on
success, and fails closed on authentication/licensing output.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = (ROOT / "build" / "public_workflow_execution").resolve()
BATCH_FILE = ARTIFACT_DIR / "public_workflow.mzbatch"
CSV_FILE = ARTIFACT_DIR / "public_workflow_features.csv"
FIXTURE_REPORT = ARTIFACT_DIR / "public_workflow_fixture.json"
EXECUTION_REPORT = ARTIFACT_DIR / "public_workflow_execution.json"
LOG_FILE = ARTIFACT_DIR / "public_workflow.log"
TEMP_DIR = ARTIFACT_DIR / "temp"

STEP_PATTERN = re.compile(r"Starting step #\s*(\d+)", re.IGNORECASE)
BATCH_START_PATTERN = re.compile(r"Starting a batch of\s+(\d+)\s+steps", re.IGNORECASE)
BATCH_FINISH_PATTERN = re.compile(r"Finished a batch of\s+(\d+)\s+steps", re.IGNORECASE)
ERROR_PATTERNS = (
    re.compile(r"Invalid parameter settings for module.*", re.IGNORECASE),
    re.compile(r"Could not start batch step.*", re.IGNORECASE),
    re.compile(r"Task .* error.*", re.IGNORECASE),
    re.compile(r"SEVERE:.*", re.IGNORECASE),
    re.compile(r"Exception.*", re.IGNORECASE),
)
FORBIDDEN_OUTPUT = (
    "auth.mzio.io",
    ".mzuser",
    "requires user login",
    "user login required",
    "license validation",
    "taskauthservice",
    "currentuserservice",
    "userauthstore",
    "google-analytics.com",
)


def gradle_wrapper() -> Path:
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise FileNotFoundError(f"Gradle wrapper not found: {wrapper}")
    return wrapper


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_fixture() -> dict[str, Any]:
    with FIXTURE_REPORT.open("r", encoding="utf-8") as stream:
        data = json.load(stream)
    steps = data.get("steps")
    if not isinstance(steps, list) or len(steps) != 12:
        raise AssertionError(f"Fixture does not describe twelve steps: {steps!r}")
    return data


def step_inventory(fixture: dict[str, Any]) -> dict[int, dict[str, Any]]:
    inventory: dict[int, dict[str, Any]] = {}
    for step in fixture["steps"]:
        number = int(step["batch_step"])
        inventory[number] = step
    if sorted(inventory) != list(range(1, 13)):
        raise AssertionError(f"Unexpected batch step inventory: {sorted(inventory)}")
    return inventory


def extract_error_lines(output: str) -> list[str]:
    findings: list[str] = []
    for raw_line in output.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if any(pattern.search(line) for pattern in ERROR_PATTERNS):
            findings.append(line[:1000])
    return findings[-50:]


def inspect_csv(path: Path) -> dict[str, Any]:
    result: dict[str, Any] = {
        "exists": path.is_file(),
        "size_bytes": None,
        "sha256": None,
        "row_count_including_header": 0,
        "data_row_count": 0,
        "header": None,
        "first_data_rows": [],
    }
    if not path.is_file():
        return result

    result["size_bytes"] = path.stat().st_size
    result["sha256"] = sha256(path)
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.reader(stream)
        for index, row in enumerate(reader):
            result["row_count_including_header"] += 1
            if index == 0:
                result["header"] = row
            elif len(result["first_data_rows"]) < 5:
                result["first_data_rows"].append(row)
    result["data_row_count"] = max(0, result["row_count_including_header"] - 1)
    return result


def write_report(report: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    with EXECUTION_REPORT.open("w", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, indent=2, sort_keys=True, ensure_ascii=False)
        stream.write("\n")
    print(f"PUBLIC_WORKFLOW_EXECUTION_REPORT={EXECUTION_REPORT}")
    print(json.dumps(report, indent=2, sort_keys=True, ensure_ascii=False))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--timeout-seconds", type=int, default=3600)
    parser.add_argument("--threads", type=int, default=2)
    args = parser.parse_args()

    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    TEMP_DIR.mkdir(parents=True, exist_ok=True)
    CSV_FILE.unlink(missing_ok=True)
    LOG_FILE.unlink(missing_ok=True)

    report: dict[str, Any] = {
        "schema_version": 1,
        "method": "single sequential 12-step headless batch with progressive log checkpoints",
        "batch_file": str(BATCH_FILE),
        "fixture_report": str(FIXTURE_REPORT),
        "expected_csv": str(CSV_FILE),
        "timeout_seconds": args.timeout_seconds,
        "threads": args.threads,
        "return_code": None,
        "timed_out": False,
        "elapsed_seconds": None,
        "declared_batch_steps": None,
        "entered_step_numbers": [],
        "completed_step_numbers_inferred": [],
        "last_entered_step": None,
        "last_completed_step_inferred": None,
        "last_entered_step_details": None,
        "batch_finished_marker": False,
        "forbidden_output_tokens": [],
        "error_lines": [],
        "csv": inspect_csv(CSV_FILE),
        "success": False,
        "fallback_recommended": False,
        "log_file": str(LOG_FILE),
        "log_tail": [],
    }

    try:
        for required in (BATCH_FILE, FIXTURE_REPORT):
            if not required.is_file():
                raise FileNotFoundError(
                    f"Missing generated artifact {required}; run PublicWorkflowHeadlessFixtureTest"
                )
        fixture = read_fixture()
        inventory = step_inventory(fixture)
        report["fixture"] = fixture

        cli_args = (
            f'-b "{BATCH_FILE}" -m all --threads {args.threads} -t "{TEMP_DIR}"'
        )
        command = [
            str(gradle_wrapper()),
            "run",
            f"--args={cli_args}",
            "--no-daemon",
            "--stacktrace",
        ]
        report["command"] = command
        print("Running published public workflow:", " ".join(command))

        started = time.monotonic()
        output = ""
        try:
            completed = subprocess.run(
                command,
                cwd=ROOT,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
                timeout=args.timeout_seconds,
            )
            output = completed.stdout or ""
            report["return_code"] = completed.returncode
        except subprocess.TimeoutExpired as exc:
            report["timed_out"] = True
            partial = exc.stdout or ""
            if isinstance(partial, bytes):
                partial = partial.decode("utf-8", errors="replace")
            output = partial
        finally:
            report["elapsed_seconds"] = round(time.monotonic() - started, 3)

        LOG_FILE.write_text(output, encoding="utf-8", newline="\n")
        print(output)

        start_matches = [int(value) for value in BATCH_START_PATTERN.findall(output)]
        report["declared_batch_steps"] = start_matches[-1] if start_matches else None
        entered = [int(value) for value in STEP_PATTERN.findall(output)]
        report["entered_step_numbers"] = entered
        report["last_entered_step"] = entered[-1] if entered else None
        finished_matches = [int(value) for value in BATCH_FINISH_PATTERN.findall(output)]
        finished = bool(finished_matches and finished_matches[-1] == 12)
        report["batch_finished_marker"] = finished

        if finished:
            completed_steps = list(range(1, 13))
        elif entered:
            completed_steps = list(range(1, max(entered)))
        else:
            completed_steps = []
        report["completed_step_numbers_inferred"] = completed_steps
        report["last_completed_step_inferred"] = completed_steps[-1] if completed_steps else None
        if entered:
            report["last_entered_step_details"] = inventory.get(entered[-1])

        normalized = output.lower()
        forbidden = [token for token in FORBIDDEN_OUTPUT if token in normalized]
        report["forbidden_output_tokens"] = forbidden
        report["error_lines"] = extract_error_lines(output)
        report["log_tail"] = output.splitlines()[-200:]
        report["csv"] = inspect_csv(CSV_FILE)

        csv_valid = (
            report["csv"]["exists"]
            and report["csv"]["size_bytes"] is not None
            and report["csv"]["size_bytes"] > 0
            and report["csv"]["data_row_count"] > 0
            and bool(report["csv"]["header"])
        )
        success = (
            report["return_code"] == 0
            and not report["timed_out"]
            and report["declared_batch_steps"] == 12
            and entered == list(range(1, 13))
            and finished
            and not forbidden
            and csv_valid
        )
        report["success"] = success
        report["fallback_recommended"] = not success and not entered

        if not success:
            write_report(report)
            return 1

        write_report(report)
        return 0
    except Exception as exc:  # report setup/generation failures too
        report["runner_exception"] = f"{type(exc).__name__}: {exc}"
        report["csv"] = inspect_csv(CSV_FILE)
        write_report(report)
        return 1


if __name__ == "__main__":
    sys.exit(main())
