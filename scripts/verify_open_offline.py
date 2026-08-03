#!/usr/bin/env python3
"""Fail when proprietary MZIO authentication components enter the open fork.

The check intentionally scans build/config/source files and JAR contents. Documentation
and this script are not scanned as source, so they may describe forbidden components.
"""

from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

TEXT_SUFFIXES = {
    ".gradle",
    ".java",
    ".kt",
    ".kts",
    ".properties",
    ".toml",
    ".xml",
    ".yaml",
    ".yml",
}

IGNORED_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    "build",
    "out",
    "target",
}

FORBIDDEN_PATTERNS = {
    "MZIO package": re.compile(r"\bio\.mzio(?:\.|:)"),
    "MZIO authentication host": re.compile(r"auth\.mzio\.io", re.IGNORECASE),
    "MZmine user file": re.compile(r"\.mzuser\b", re.IGNORECASE),
    "Keycloak authentication": re.compile(r"\b(?:org\.)?keycloak\b", re.IGNORECASE),
    "authentication event": re.compile(r"\bAuthRequiredEvent\b"),
    "task authorization service": re.compile(r"\bTaskAuthService\b"),
    "user authentication store": re.compile(r"\bUserAuthStore\b"),
    "user login service": re.compile(r"\bUserLoginService\b"),
    "current-user service": re.compile(r"\bCurrentUserService\b"),
    "license utility": re.compile(r"\bLicenseUtils\b"),
}


def is_ignored(path: Path) -> bool:
    return any(part in IGNORED_DIRS for part in path.relative_to(ROOT).parts)


def scan_text_files() -> list[str]:
    findings: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or is_ignored(path) or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            findings.append(f"Unreadable UTF-8 source/config file: {path.relative_to(ROOT)}")
            continue

        for line_number, line in enumerate(text.splitlines(), start=1):
            for label, pattern in FORBIDDEN_PATTERNS.items():
                if pattern.search(line):
                    findings.append(
                        f"{path.relative_to(ROOT)}:{line_number}: {label}: {line.strip()}"
                    )
    return findings


def scan_paths() -> list[str]:
    findings: list[str] = []
    forbidden_repo = ROOT / "local-repo" / "io" / "mzio"
    if forbidden_repo.exists():
        findings.append(f"Forbidden proprietary repository directory: {forbidden_repo.relative_to(ROOT)}")
    return findings


def scan_jars() -> list[str]:
    findings: list[str] = []
    for jar_path in ROOT.rglob("*.jar"):
        if is_ignored(jar_path):
            continue
        try:
            with zipfile.ZipFile(jar_path) as archive:
                offending = next(
                    (name for name in archive.namelist() if name.startswith("io/mzio/")),
                    None,
                )
                if offending:
                    findings.append(
                        f"{jar_path.relative_to(ROOT)} contains forbidden entry {offending}"
                    )
        except zipfile.BadZipFile:
            findings.append(f"Invalid JAR/ZIP file: {jar_path.relative_to(ROOT)}")
    return findings


def main() -> int:
    findings = scan_paths() + scan_text_files() + scan_jars()
    if findings:
        print("Open-offline audit FAILED:\n")
        for finding in findings:
            print(f" - {finding}")
        return 1

    print("Open-offline audit passed: no MZIO authentication/licensing components found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
