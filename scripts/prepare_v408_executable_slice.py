#!/usr/bin/env python3
"""Prepare the frozen MZmine v4.0.8 parser/centroid source slice for compilation.

Every upstream source is selected from the passing closure report and verified against its recorded
SHA-256. Every reached reviewed boundary must be represented by an exact checked-out adapter in the
frozen byte lock and semantic contract. Validation completes before any generated source is written.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
  sys.path.insert(0, str(SCRIPT_DIR))

from inventory_v408_adapters import (  # noqa: E402
    RootSpec,
    inventory as adapter_inventory,
    parse_root_spec,
    verify as verify_adapter_inventory,
)

ORACLE_COMMIT = "8029f930d28c0447f0acf2bcabef0a79865ad434"


class PreparationError(ValueError):
  """Raised when the executable source set cannot be prepared safely."""


@dataclass(frozen=True)
class PlannedSource:
  source: Path
  destination: Path
  destination_key: str
  origin: str
  source_ref: dict[str, str]
  sha256: str


def load_json(path: Path) -> dict[str, Any]:
  try:
    value = json.loads(path.read_text(encoding="utf-8"))
  except (FileNotFoundError, json.JSONDecodeError) as exc:
    raise PreparationError(f"cannot read JSON {path}: {exc}") from exc
  if not isinstance(value, dict):
    raise PreparationError(f"top-level JSON value must be an object: {path}")
  return value


def sha256(path: Path) -> str:
  digest = hashlib.sha256()
  with path.open("rb") as handle:
    while chunk := handle.read(1024 * 1024):
      digest.update(chunk)
  return digest.hexdigest()


def canonical_sha(value: dict[str, Any]) -> str:
  encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
  return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def fqcn_path(fqcn: str) -> Path:
  return Path(*fqcn.split(".")).with_suffix(".java")


def strip_source_prefix(relative: str) -> str:
  prefixes = (
      "mzmine-community/src/main/java/",
      "utils/src/main/java/",
      "taskcontroller/src/main/java/",
      "javafx-framework/src/main/java/",
      "open-offline-boundaries/src/main/java/",
  )
  for prefix in prefixes:
    if relative.startswith(prefix):
      return relative[len(prefix):]
  raise PreparationError(f"closure path is outside approved source roots: {relative}")


def validate_contract(
    closure: dict[str, Any],
    adapter_lock: dict[str, Any],
    contract: dict[str, Any],
    current_inventory: dict[str, Any],
) -> tuple[dict[str, dict[str, Any]], dict[str, dict[str, Any]], set[str]]:
  if contract.get("schema_version") != 1:
    raise PreparationError("adapter contract schema_version must be 1")
  contract_id = contract.get("contract_id")
  if not isinstance(contract_id, str) or not contract_id:
    raise PreparationError("adapter contract requires a non-empty contract_id")
  if contract.get("oracle_commit") != ORACLE_COMMIT:
    raise PreparationError("adapter contract targets the wrong oracle commit")
  if contract.get("source_closure_sha256") != closure.get("content_sha256"):
    raise PreparationError("adapter contract targets the wrong source closure")
  if contract.get("adapter_lock_sha256") != adapter_lock.get("content_sha256"):
    raise PreparationError("adapter contract targets the wrong adapter lock")

  reached_records = closure.get("reviewed_boundaries_reached")
  if not isinstance(reached_records, list):
    raise PreparationError("closure lacks reviewed_boundaries_reached")
  reached_classes = {
      item.get("class") for item in reached_records
      if isinstance(item, dict) and isinstance(item.get("class"), str)
  }
  if len(reached_classes) != len(reached_records):
    raise PreparationError("closure contains invalid or duplicate reached boundaries")

  locked_records = adapter_lock.get("adapters")
  if not isinstance(locked_records, list):
    raise PreparationError("adapter lock lacks an adapters array")
  locked_by_class = {
      item.get("class"): item for item in locked_records
      if isinstance(item, dict) and isinstance(item.get("class"), str)
  }
  if len(locked_by_class) != len(locked_records):
    raise PreparationError("adapter lock contains invalid or duplicate classes")

  current_records = current_inventory.get("adapters")
  if not isinstance(current_records, list):
    raise PreparationError("current adapter inventory lacks an adapters array")
  current_by_class = {item["class"]: item for item in current_records}

  contract_records = contract.get("adapters")
  if not isinstance(contract_records, list):
    raise PreparationError("adapter contract lacks an adapters array")
  contract_by_class: dict[str, dict[str, Any]] = {}
  for item in contract_records:
    if not isinstance(item, dict):
      raise PreparationError("adapter contract entries must be objects")
    fqcn = item.get("class")
    mode = item.get("execution_mode")
    members = item.get("preserved_members")
    excluded = item.get("excluded_behavior")
    if not isinstance(fqcn, str) or not fqcn:
      raise PreparationError("adapter contract entry lacks class")
    if fqcn in contract_by_class:
      raise PreparationError(f"duplicate adapter contract class: {fqcn}")
    if not isinstance(mode, str) or not mode:
      raise PreparationError(f"adapter contract lacks execution_mode: {fqcn}")
    if not isinstance(members, list) or not all(isinstance(value, str) for value in members):
      raise PreparationError(f"adapter contract has invalid preserved_members: {fqcn}")
    if not isinstance(excluded, str) or not excluded:
      raise PreparationError(f"adapter contract lacks excluded_behavior: {fqcn}")
    contract_by_class[fqcn] = item

  class_sets = {
      "reached boundaries": reached_classes,
      "adapter lock": set(locked_by_class),
      "current inventory": set(current_by_class),
      "adapter contract": set(contract_by_class),
  }
  reference = reached_classes
  differences = {
      name: {
          "missing": sorted(reference - values),
          "unexpected": sorted(values - reference),
      }
      for name, values in class_sets.items()
      if values != reference
  }
  if differences:
    raise PreparationError(f"reviewed boundary coverage mismatch: {differences}")

  return locked_by_class, contract_by_class, reached_classes


def prepare(
    checkout: Path,
    closure: dict[str, Any],
    adapter_roots: list[RootSpec],
    adapter_lock: dict[str, Any],
    adapter_contract: dict[str, Any],
    output: Path,
) -> dict[str, Any]:
  if closure.get("status") != "pass":
    raise PreparationError("source closure must have passing status")
  oracle = closure.get("oracle")
  if not isinstance(oracle, dict) or oracle.get("commit") != ORACLE_COMMIT:
    raise PreparationError("source closure does not target the frozen v4.0.8 commit")

  checkout = checkout.resolve()
  output = output.resolve()
  if not checkout.is_dir():
    raise PreparationError(f"missing upstream checkout: {checkout}")

  try:
    current_inventory = adapter_inventory(
        adapter_roots, expected_count=adapter_lock.get("adapter_count")
    )
    verify_adapter_inventory(current_inventory, adapter_lock)
  except ValueError as exc:
    raise PreparationError(f"adapter lock verification failed: {exc}") from exc

  locked_by_class, contract_by_class, reached_classes = validate_contract(
      closure, adapter_lock, adapter_contract, current_inventory
  )
  root_paths = {root.root_id: root.path.resolve() for root in adapter_roots}
  if len(root_paths) != len(adapter_roots):
    raise PreparationError("adapter root identifiers must be unique")

  nodes = closure.get("nodes")
  if not isinstance(nodes, list) or not nodes:
    raise PreparationError("closure contains no source nodes")
  node_classes = {
      node.get("class") for node in nodes
      if isinstance(node, dict) and isinstance(node.get("class"), str)
  }
  if not reached_classes.issubset(node_classes):
    raise PreparationError("reached boundary is absent from closure nodes")

  planned_by_destination: dict[str, PlannedSource] = {}
  class_records: list[dict[str, Any]] = []

  for node in sorted(nodes, key=lambda item: item["class"]):
    fqcn = node.get("class")
    relative = node.get("path")
    expected_sha = node.get("source_sha256")
    classification = node.get("classification")
    if not all(isinstance(value, str) and value for value in (
        fqcn, relative, expected_sha, classification
    )):
      raise PreparationError(f"invalid closure node: {node}")

    if fqcn in reached_classes:
      locked = locked_by_class[fqcn]
      root_id = locked["root_id"]
      root = root_paths.get(root_id)
      if root is None:
        raise PreparationError(f"adapter root is not configured: {root_id}")
      source = root / locked["relative_path"]
      destination_relative = fqcn_path(fqcn)
      origin = "open-offline-adapter"
      source_ref = {
          "root_id": root_id,
          "relative_path": locked["relative_path"],
      }
      expected_source_sha = locked["sha256"]
      execution_mode = contract_by_class[fqcn]["execution_mode"]
    else:
      source = checkout / relative
      destination_relative = Path(strip_source_prefix(relative))
      origin = "upstream-v4.0.8"
      source_ref = {"repository_relative_path": relative}
      expected_source_sha = expected_sha
      execution_mode = "upstream-public-source"

    if not source.is_file():
      raise PreparationError(f"missing source for {fqcn}: {source}")
    actual_sha = sha256(source)
    if actual_sha != expected_source_sha:
      raise PreparationError(
          f"source SHA mismatch for {fqcn}: expected {expected_source_sha}, got {actual_sha}"
      )

    destination_key = destination_relative.as_posix()
    plan = PlannedSource(
        source=source,
        destination=output / destination_relative,
        destination_key=destination_key,
        origin=origin,
        source_ref=source_ref,
        sha256=actual_sha,
    )
    existing = planned_by_destination.get(destination_key)
    if existing is None:
      planned_by_destination[destination_key] = plan
    elif (
        existing.sha256 != plan.sha256
        or existing.origin != plan.origin
        or existing.source_ref != plan.source_ref
    ):
      raise PreparationError(
          f"multiple closure classes map to conflicting source file {destination_key}"
      )

    class_records.append({
        "class": fqcn,
        "classification": classification,
        "compiled_path": destination_key,
        "origin": origin,
        "source_ref": source_ref,
        "source_sha256": actual_sha,
        "execution_mode": execution_mode,
    })

  # All provenance, source existence, SHA, lock, contract, and boundary coverage checks complete
  # before generated output is mutated.
  if output.exists():
    shutil.rmtree(output)
  output.mkdir(parents=True)
  for plan in sorted(planned_by_destination.values(), key=lambda item: item.destination_key):
    plan.destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(plan.source, plan.destination)

  source_files = [
      {
          "path": plan.destination_key,
          "origin": plan.origin,
          "source_ref": plan.source_ref,
          "sha256": plan.sha256,
      }
      for plan in sorted(planned_by_destination.values(), key=lambda item: item.destination_key)
  ]
  manifest = {
      "schema_version": 1,
      "oracle_commit": oracle["commit"],
      "closure_content_sha256": closure.get("content_sha256"),
      "adapter_lock_content_sha256": adapter_lock.get("content_sha256"),
      "adapter_contract_id": adapter_contract.get("contract_id"),
      "adapter_contract_sha256": canonical_sha(adapter_contract),
      "class_count": len(class_records),
      "source_file_count": len(source_files),
      "adapter_class_count": len(reached_classes),
      "classes": class_records,
      "source_files": source_files,
  }
  manifest["content_sha256"] = canonical_sha(manifest)
  return manifest


def build_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--checkout", type=Path, required=True)
  parser.add_argument("--closure", type=Path, required=True)
  parser.add_argument(
      "--adapter-root",
      action="append",
      type=parse_root_spec,
      default=None,
      help="Stable-id=path for a reviewed Java adapter source root. May be repeated.",
  )
  parser.add_argument(
      "--adapter-lock",
      type=Path,
      default=Path("oracle/v408-executable/adapter-lock.json"),
  )
  parser.add_argument(
      "--adapter-contract",
      type=Path,
      default=Path("oracle/v408-executable/adapter-contract.json"),
  )
  parser.add_argument("--output", type=Path, required=True)
  parser.add_argument("--manifest-output", type=Path, required=True)
  return parser


def main() -> int:
  args = build_parser().parse_args()
  adapter_roots = args.adapter_root or [
      RootSpec(
          "executable-adapters",
          Path("oracle/v408-executable/adapters/src/main/java"),
      ),
      RootSpec(
          "boundary-adapters",
          Path("oracle/v408-boundaries/src/main/java"),
      ),
  ]
  try:
    manifest = prepare(
        args.checkout,
        load_json(args.closure),
        adapter_roots,
        load_json(args.adapter_lock),
        load_json(args.adapter_contract),
        args.output,
    )
  except (PreparationError, OSError) as exc:
    print(f"Executable source preparation refused: {exc}", file=sys.stderr)
    return 2

  args.manifest_output.parent.mkdir(parents=True, exist_ok=True)
  args.manifest_output.write_text(
      json.dumps(manifest, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
      encoding="utf-8",
  )
  print(json.dumps({
      "class_count": manifest["class_count"],
      "source_file_count": manifest["source_file_count"],
      "adapter_class_count": manifest["adapter_class_count"],
      "content_sha256": manifest["content_sha256"],
  }, indent=2, sort_keys=True))
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
