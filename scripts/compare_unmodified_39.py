#!/usr/bin/env python3
"""Compare the open-offline batch output with untouched MZmine 3.9.0.

The current checkout must already have generated and executed the deterministic headless batch.
A second checkout, pinned to the exact public MZmine 3.9.0 commit, executes the same mzML and XML
batch without modifying any tracked baseline source file. The two CSV outputs are then compared
structurally and numerically.
"""

from __future__ import annotations

import csv
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

BASELINE_COMMIT = "2ac3ce3b25190430f3ae0e02c28ddbb94bc248ed"
ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = (ROOT / "build" / "open_offline_headless").resolve()
BATCH_FILE = ARTIFACT_DIR / "synthetic_batch.mzbatch"
MZML_FILE = ARTIFACT_DIR / "synthetic_batch.mzML"
BATCH_OUTPUT = ARTIFACT_DIR / "synthetic_batch_features.csv"
FORK_OUTPUT = ARTIFACT_DIR / "open_offline_features.csv"
BASELINE_OUTPUT = ARTIFACT_DIR / "unmodified_3_9_features.csv"
BASELINE_LOG = ARTIFACT_DIR / "unmodified_3_9_batch.log"
BASELINE_TEMP = ARTIFACT_DIR / "unmodified_3_9_temp"
NUMERIC_TOLERANCE = 1e-4

FORBIDDEN_AUTH_OUTPUT = (
    "auth.mzio.io",
    ".mzuser",
    "requires user login",
    "user login required",
    "license validation",
    "taskauthservice",
)


@dataclass(frozen=True)
class ParsedCsv:
    header: tuple[str, ...]
    rows: tuple[tuple[str, ...], ...]


def fail(message: str, code: int = 1) -> int:
    print(message, file=sys.stderr)
    return code


def run(command: list[str], cwd: Path, timeout: int = 420) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
        timeout=timeout,
    )


def git_output(baseline_root: Path, *args: str) -> str:
    completed = run(["git", *args], baseline_root, timeout=60)
    if completed.returncode != 0:
        raise AssertionError(
            f"Git command failed in baseline checkout: git {' '.join(args)}\n{completed.stdout}"
        )
    return (completed.stdout or "").strip()


def assert_untouched_baseline(baseline_root: Path) -> None:
    actual_head = git_output(baseline_root, "rev-parse", "HEAD")
    if actual_head != BASELINE_COMMIT:
        raise AssertionError(
            f"Baseline checkout is not exact MZmine 3.9.0: expected {BASELINE_COMMIT}, "
            f"found {actual_head}"
        )

    tracked_diff = git_output(baseline_root, "status", "--porcelain", "--untracked-files=no")
    if tracked_diff:
        raise AssertionError(
            "Untouched baseline has tracked modifications before or after execution:\n"
            + tracked_diff
        )


def gradle_wrapper(project_root: Path) -> Path:
    wrapper = project_root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise AssertionError(f"Gradle wrapper not found in baseline checkout: {wrapper}")
    return wrapper


def parse_csv(path: Path) -> ParsedCsv:
    with path.open("r", encoding="utf-8", newline="") as stream:
        raw_rows = list(csv.reader(stream))
    if len(raw_rows) < 2:
        raise AssertionError(f"CSV contains no feature rows: {path}: {raw_rows}")

    header = tuple(raw_rows[0])
    rows = tuple(sorted((tuple(row) for row in raw_rows[1:]), key=lambda row: float(row[1])))
    return ParsedCsv(header=header, rows=rows)


def numeric(value: str, label: str) -> float:
    try:
        return float(value)
    except ValueError as exc:
        raise AssertionError(f"Invalid numeric value for {label}: {value!r}") from exc


def compare_csvs(fork_path: Path, baseline_path: Path) -> None:
    fork = parse_csv(fork_path)
    baseline = parse_csv(baseline_path)

    if fork.header != baseline.header:
        raise AssertionError(
            "CSV headers differ between open-offline and untouched 3.9:\n"
            f"open-offline={fork.header}\nuntouched-3.9={baseline.header}"
        )
    if len(fork.rows) != len(baseline.rows):
        raise AssertionError(
            f"Feature-row count differs: open-offline={len(fork.rows)}, "
            f"untouched-3.9={len(baseline.rows)}"
        )

    # Batch export columns: row ID, row m/z, row RT, status, feature m/z, feature RT,
    # feature height, trailing empty legacy field.
    numeric_columns = {1, 2, 4, 5, 6}
    exact_columns = {0, 3, 7}

    for row_index, (fork_row, baseline_row) in enumerate(zip(fork.rows, baseline.rows), start=1):
        if len(fork_row) != len(baseline_row):
            raise AssertionError(
                f"Column count differs in row {row_index}: "
                f"open-offline={fork_row}, untouched-3.9={baseline_row}"
            )
        if len(fork_row) != 8:
            raise AssertionError(f"Unexpected deterministic CSV width in row {row_index}: {fork_row}")

        for column in exact_columns:
            if fork_row[column] != baseline_row[column]:
                raise AssertionError(
                    f"Exact field differs at row {row_index}, column {column}: "
                    f"open-offline={fork_row[column]!r}, "
                    f"untouched-3.9={baseline_row[column]!r}"
                )

        for column in numeric_columns:
            fork_value = numeric(fork_row[column], f"open-offline row {row_index} col {column}")
            baseline_value = numeric(
                baseline_row[column], f"untouched-3.9 row {row_index} col {column}"
            )
            if abs(fork_value - baseline_value) > NUMERIC_TOLERANCE:
                raise AssertionError(
                    f"Numeric field differs at row {row_index}, column {column}: "
                    f"open-offline={fork_value}, untouched-3.9={baseline_value}, "
                    f"tolerance={NUMERIC_TOLERANCE}"
                )


def execute_baseline(baseline_root: Path) -> str:
    BATCH_OUTPUT.unlink(missing_ok=True)
    BASELINE_TEMP.mkdir(parents=True, exist_ok=True)

    args = f'-b "{BATCH_FILE}" -m all --threads 2 -t "{BASELINE_TEMP}"'
    command = [
        str(gradle_wrapper(baseline_root)),
        "run",
        f"--args={args}",
        "--no-daemon",
        "--stacktrace",
    ]
    print("Running untouched MZmine 3.9.0 batch:", " ".join(command))
    completed = run(command, baseline_root)
    output = completed.stdout or ""
    BASELINE_LOG.write_text(output, encoding="utf-8")
    print(output)

    if completed.returncode != 0:
        raise AssertionError(
            f"Untouched MZmine 3.9.0 batch failed with exit code {completed.returncode}"
        )

    normalized = output.lower()
    forbidden = [token for token in FORBIDDEN_AUTH_OUTPUT if token in normalized]
    if forbidden:
        raise AssertionError(
            "Untouched 3.9 unexpectedly emitted later MZIO authentication/licensing output: "
            + ", ".join(forbidden)
        )

    if not BATCH_OUTPUT.is_file():
        raise AssertionError(f"Untouched 3.9 did not create expected CSV: {BATCH_OUTPUT}")
    return output


def main() -> int:
    if len(sys.argv) != 2:
        return fail("Usage: compare_unmodified_39.py <path-to-untouched-3.9-checkout>", 2)

    baseline_root = Path(sys.argv[1]).resolve()
    if not baseline_root.is_dir():
        return fail(f"Baseline checkout does not exist: {baseline_root}")

    for required in (BATCH_FILE, MZML_FILE, BATCH_OUTPUT):
        if not required.is_file():
            return fail(
                f"Missing current reference artifact: {required}. "
                "Run the Java tests and test_headless_batch.py first."
            )

    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    shutil.copy2(BATCH_OUTPUT, FORK_OUTPUT)
    BASELINE_OUTPUT.unlink(missing_ok=True)

    try:
        assert_untouched_baseline(baseline_root)
        execute_baseline(baseline_root)
        shutil.copy2(BATCH_OUTPUT, BASELINE_OUTPUT)
        compare_csvs(FORK_OUTPUT, BASELINE_OUTPUT)
        assert_untouched_baseline(baseline_root)
    except (AssertionError, OSError, subprocess.TimeoutExpired) as exc:
        return fail(f"Untouched MZmine 3.9 comparison failed: {exc}")

    print(
        "Untouched MZmine 3.9.0 comparison passed: exact commit verified, tracked source "
        "remained unchanged, and normalized CSV structure/status/numeric values match the "
        "open-offline fork within 1e-4."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
