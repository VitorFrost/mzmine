#!/usr/bin/env python3
"""Run a minimal MZmine startup without opening the GUI.

The smoke test launches the Gradle application with ``--version``. This exercises class
initialization, command-line parsing, and normal process termination without requiring an mzML
fixture. It also rejects authentication/login messages that must not exist in the independent fork.
"""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

FORBIDDEN_OUTPUT = (
    "auth.mzio.io",
    ".mzuser",
    "requires user login",
    "user login required",
    "license validation",
    "taskauthservice",
)


def gradle_wrapper() -> Path:
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise FileNotFoundError(f"Gradle wrapper not found: {wrapper}")
    return wrapper


def main() -> int:
    command = [
        str(gradle_wrapper()),
        "run",
        "--args=--version",
        "--no-daemon",
        "--stacktrace",
    ]

    print("Running headless startup smoke test:", " ".join(command))
    completed = subprocess.run(
        command,
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )

    output = completed.stdout or ""
    print(output)

    if completed.returncode != 0:
        print(f"Headless startup failed with exit code {completed.returncode}.", file=sys.stderr)
        return completed.returncode or 1

    normalized = output.lower()
    found = [token for token in FORBIDDEN_OUTPUT if token in normalized]
    if found:
        print(
            "Headless startup emitted forbidden authentication/licensing output: "
            + ", ".join(found),
            file=sys.stderr,
        )
        return 1

    print("Headless startup smoke test passed without login or license validation output.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
