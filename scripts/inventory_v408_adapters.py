#!/usr/bin/env python3
"""Generate or verify a deterministic SHA-256 inventory of v4.0.8 oracle adapters.

The command reads only checked-out local files. It never downloads source and never rewrites a lock
file. Generation emits evidence; verification compares that evidence with an explicitly supplied
locked manifest and fails closed on any difference.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
  sys.path.insert(0, str(SCRIPT_DIR))

from audit_v408_source_closure import (  # noqa: E402
    PACKAGE_RE,
    strip_comments_and_literals,
    top_level_types,
)

HEX64_RE = re.compile(r"^[0-9a-f]{64}$")
ROOT_ID_RE = re.compile(r"^[a-z][a-z0-9-]*$")


class InventoryError(ValueError):
  """Raised when adapter provenance cannot be established safely."""


@dataclass(frozen=True)
class RootSpec:
  root_id: str
  path: Path


def parse_root_spec(value: str) -> RootSpec:
  root_id, separator, path_value = value.partition("=")
  if separator != "=" or not ROOT_ID_RE.fullmatch(root_id) or not path_value:
    raise argparse.ArgumentTypeError(
        "--root must use stable-id=path with a lowercase hyphenated identifier"
    )
  return RootSpec(root_id, Path(path_value))


def sha256_bytes(value: bytes) -> str:
  return hashlib.sha256(value).hexdigest()


def canonical_sha(value: dict[str, Any]) -> str:
  encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
  return sha256_bytes(encoded.encode("utf-8"))


def source_identity(path: Path, root: Path) -> tuple[str, str, bytes]:
  data = path.read_bytes()
  try:
    text = data.decode("utf-8")
  except UnicodeDecodeError as exc:
    raise InventoryError(f"adapter is not UTF-8: {path}") from exc

  try:
    code = strip_comments_and_literals(text)
    declared_types = top_level_types(code)
  except ValueError as exc:
    raise InventoryError(f"cannot parse Java adapter {path}: {exc}") from exc
  package_match = PACKAGE_RE.search(code)
  if package_match is None or len(declared_types) != 1:
    raise InventoryError(
        f"adapter requires exactly one active top-level type and one package: {path}; "
        f"types={list(declared_types)}"
    )

  fqcn = f"{package_match.group(1)}.{declared_types[0]}"
  expected_relative = Path(*fqcn.split(".")).with_suffix(".java")
  actual_relative = path.relative_to(root)
  if actual_relative != expected_relative:
    raise InventoryError(
        f"adapter path/FQCN mismatch: {actual_relative.as_posix()} != "
        f"{expected_relative.as_posix()}"
    )
  return fqcn, actual_relative.as_posix(), data


def inventory(roots: list[RootSpec], expected_count: int | None = None) -> dict[str, Any]:
  if not roots:
    raise InventoryError("at least one named adapter root is required")
  root_ids = [root.root_id for root in roots]
  if len(root_ids) != len(set(root_ids)):
    raise InventoryError("adapter root identifiers must be unique")

  resolved_roots = [RootSpec(root.root_id, root.path.resolve()) for root in roots]
  for root in resolved_roots:
    if not root.path.is_dir():
      raise InventoryError(f"missing adapter source root {root.root_id}: {root.path}")

  by_fqcn: dict[str, dict[str, Any]] = {}
  by_origin: set[str] = set()
  for root in resolved_roots:
    for path in sorted(root.path.rglob("*.java")):
      fqcn, relative, data = source_identity(path, root.path)
      record = {
          "class": fqcn,
          "root_id": root.root_id,
          "relative_path": relative,
          "byte_length": len(data),
          "sha256": sha256_bytes(data),
      }
      existing = by_fqcn.get(fqcn)
      if existing is not None:
        raise InventoryError(
            f"duplicate adapter class {fqcn}: {existing['root_id']}, {root.root_id}"
        )
      origin_key = f"{root.root_id}::{relative}"
      if origin_key in by_origin:
        raise InventoryError(f"duplicate adapter origin: {origin_key}")
      by_origin.add(origin_key)
      by_fqcn[fqcn] = record

  records = [by_fqcn[key] for key in sorted(by_fqcn)]
  if expected_count is not None and len(records) != expected_count:
    raise InventoryError(
        f"adapter count mismatch: expected {expected_count}, found {len(records)}"
    )
  result: dict[str, Any] = {
      "schema_version": 1,
      "adapter_count": len(records),
      "adapters": records,
  }
  result["content_sha256"] = canonical_sha(result)
  return result


def load_json(path: Path) -> dict[str, Any]:
  try:
    value = json.loads(path.read_text(encoding="utf-8"))
  except FileNotFoundError as exc:
    raise InventoryError(f"missing JSON file: {path}") from exc
  except json.JSONDecodeError as exc:
    raise InventoryError(f"invalid JSON in {path}: {exc}") from exc
  if not isinstance(value, dict):
    raise InventoryError(f"top-level JSON value must be an object: {path}")
  return value


def verify(current: dict[str, Any], locked: dict[str, Any]) -> None:
  if locked.get("schema_version") != 1:
    raise InventoryError("locked adapter manifest schema_version must be 1")
  locked_records = locked.get("adapters")
  if not isinstance(locked_records, list):
    raise InventoryError("locked adapter manifest must contain an adapters array")
  for record in locked_records:
    if not isinstance(record, dict) or not HEX64_RE.fullmatch(str(record.get("sha256", ""))):
      raise InventoryError("locked adapter entries require lowercase SHA-256 values")
    if not ROOT_ID_RE.fullmatch(str(record.get("root_id", ""))):
      raise InventoryError("locked adapter entries require stable root_id values")
  locked_core = {
      "schema_version": locked["schema_version"],
      "adapter_count": locked.get("adapter_count"),
      "adapters": locked_records,
  }
  expected_content_sha = locked.get("content_sha256")
  if expected_content_sha != canonical_sha(locked_core):
    raise InventoryError("locked adapter manifest content SHA-256 is invalid")
  if current != {**locked_core, "content_sha256": expected_content_sha}:
    current_by_class = {item["class"]: item for item in current["adapters"]}
    locked_by_class = {item["class"]: item for item in locked_records}
    missing = sorted(set(locked_by_class) - set(current_by_class))
    unexpected = sorted(set(current_by_class) - set(locked_by_class))
    changed = sorted(
        fqcn for fqcn in set(current_by_class) & set(locked_by_class)
        if current_by_class[fqcn] != locked_by_class[fqcn]
    )
    raise InventoryError(
        f"adapter lock mismatch: missing={missing}, unexpected={unexpected}, changed={changed}"
    )


def build_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--root", type=parse_root_spec, action="append", required=True)
  parser.add_argument("--output", type=Path, required=True)
  parser.add_argument("--expected-count", type=int)
  parser.add_argument("--lock", type=Path)
  return parser


def main() -> int:
  args = build_parser().parse_args()
  try:
    result = inventory(args.root, args.expected_count)
    if args.lock is not None:
      verify(result, load_json(args.lock))
  except (InventoryError, OSError) as exc:
    print(f"Adapter inventory refused: {exc}", file=sys.stderr)
    return 2

  args.output.parent.mkdir(parents=True, exist_ok=True)
  args.output.write_text(
      json.dumps(result, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
      encoding="utf-8",
  )
  print(json.dumps({
      "adapter_count": result["adapter_count"],
      "content_sha256": result["content_sha256"],
      "verified_against_lock": args.lock is not None,
  }, indent=2, sort_keys=True))
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
