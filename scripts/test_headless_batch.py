#!/usr/bin/env python3
"""Execute the generated MZmine 3.9 batch through the real headless CLI.

The Java test suite first writes a synthetic mzML file and a batch XML assembled from real
ParameterSet objects. This script launches a separate MZmine process, rejects authentication or
license output, and compares the exported CSV with the direct-module deterministic reference.
"""

from __future__ import annotations

import csv
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = (ROOT / "build" / "open_offline_headless").resolve()
BATCH_FILE = ARTIFACT_DIR / "synthetic_batch.mzbatch"
MZML_FILE = ARTIFACT_DIR / "synthetic_batch.mzML"
CSV_FILE = ARTIFACT_DIR / "synthetic_batch_features.csv"
TEMP_DIR = ARTIFACT_DIR / "temp"

FORBIDDEN_OUTPUT = (
    "auth.mzio.io",
    ".mzuser",
    "requires user login",
    "user login required",
    "license validation",
    "taskauthservice",
)


def fail(message: str, code: int = 1) -> int:
    print(message, file=sys.stderr)
    return code


def gradle_wrapper() -> Path:
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise FileNotFoundError(f"Gradle wrapper not found: {wrapper}")
    return wrapper


def parse_float(value: str, label: str) -> float:
    try:
        return float(value)
    except ValueError as exc:
        raise AssertionError(f"Invalid numeric value for {label}: {value!r}") from exc


def assert_close(actual: str, expected: float, label: str, tolerance: float = 1e-4) -> None:
    parsed = parse_float(actual, label)
    if abs(parsed - expected) > tolerance:
        raise AssertionError(
            f"Unexpected {label}: expected {expected}, got {parsed} (raw {actual!r})"
        )


def verify_csv() -> None:
    with CSV_FILE.open("r", encoding="utf-8", newline="") as stream:
        rows = list(csv.reader(stream))

    if len(rows) != 3:
        raise AssertionError(f"Expected header plus two rows, found {len(rows)} rows: {rows}")

    expected_header = [
        "row ID",
        "row m/z",
        "row retention time",
        "synthetic_batch.mzML Feature status",
        "synthetic_batch.mzML Feature m/z",
        "synthetic_batch.mzML Feature RT",
        "synthetic_batch.mzML Peak height",
        "",
    ]
    if rows[0] != expected_header:
        raise AssertionError(f"Unexpected CSV header:\nexpected={expected_header}\nactual={rows[0]}")

    data_rows = sorted(rows[1:], key=lambda row: parse_float(row[1], "row m/z"))
    if any(len(row) != 8 for row in data_rows):
        raise AssertionError(f"Expected eight CSV fields including trailing empty field: {data_rows}")

    first, second = data_rows
    if first[3] != "DETECTED" or second[3] != "DETECTED":
        raise AssertionError(f"Unexpected feature statuses: {first[3]!r}, {second[3]!r}")

    assert_close(first[1], 150.0, "first row average m/z")
    assert_close(first[2], 0.6, "first row average RT")
    assert_close(first[4], 150.0, "first feature m/z")
    assert_close(first[5], 0.6, "first feature RT")
    assert_close(first[6], 9000.0, "first feature height")

    assert_close(second[1], 300.0, "second row average m/z")
    assert_close(second[2], 1.0, "second row average RT")
    assert_close(second[4], 300.0, "second feature m/z")
    assert_close(second[5], 1.0, "second feature RT")
    assert_close(second[6], 7000.0, "second feature height")

    if first[7] != "" or second[7] != "":
        raise AssertionError("Legacy trailing CSV separator was not preserved")


def main() -> int:
    for required in (BATCH_FILE, MZML_FILE):
        if not required.is_file():
            return fail(
                f"Missing generated batch artifact: {required}. Run ./gradlew test before this script."
            )

    CSV_FILE.unlink(missing_ok=True)
    TEMP_DIR.mkdir(parents=True, exist_ok=True)

    args = (
        f'-b "{BATCH_FILE}" -m all --threads 2 -t "{TEMP_DIR}"'
    )
    command = [
        str(gradle_wrapper()),
        "run",
        f"--args={args}",
        "--no-daemon",
        "--stacktrace",
    ]
    print("Running deterministic headless batch:", " ".join(command))
    completed = subprocess.run(
        command,
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
        timeout=300,
    )

    output = completed.stdout or ""
    print(output)
    if completed.returncode != 0:
        return fail(f"Headless batch failed with exit code {completed.returncode}")

    normalized = output.lower()
    forbidden = [token for token in FORBIDDEN_OUTPUT if token in normalized]
    if forbidden:
        return fail(
            "Headless batch emitted forbidden authentication/licensing output: "
            + ", ".join(forbidden)
        )

    if not CSV_FILE.is_file():
        return fail(f"Headless batch did not create expected CSV: {CSV_FILE}")

    try:
        verify_csv()
    except AssertionError as exc:
        return fail(f"Deterministic CSV comparison failed: {exc}")

    print(
        "Headless batch reference passed: import, mass detection, ADAP, resolver, and CSV export "
        "match the direct-module reference without login or license validation."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
