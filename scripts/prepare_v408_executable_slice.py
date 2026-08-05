#!/usr/bin/env python3
"""Prepare the frozen MZmine v4.0.8 parser/centroid source slice for compilation.

Every upstream source is selected from the passing closure report and verified against its recorded
SHA-256. Reviewed boundary classes are replaced only when a versioned adapter with the identical
fully qualified class name exists under one of the declared adapter source roots.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
from pathlib import Path
from typing import Any, Iterable


class PreparationError(ValueError):
  pass


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


def fqcn_path(fqcn: str) -> Path:
  return Path(*fqcn.split(".")).with_suffix(".java")


def find_adapter(fqcn: str, adapter_roots: Iterable[Path]) -> Path | None:
  relative = fqcn_path(fqcn)
  matches = [root / relative for root in adapter_roots if (root / relative).is_file()]
  if len(matches) > 1:
    digests = {sha256(path) for path in matches}
    if len(digests) > 1:
      raise PreparationError(
          f"conflicting adapters for {fqcn}: {', '.join(map(str, matches))}"
      )
  return matches[0] if matches else None


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


def prepare(
    checkout: Path,
    closure: dict[str, Any],
    adapter_roots: list[Path],
    output: Path,
) -> dict[str, Any]:
  if closure.get("status") != "pass":
    raise PreparationError("source closure must have passing status")
  oracle = closure.get("oracle")
  if not isinstance(oracle, dict) or oracle.get("commit") != (
      "8029f930d28c0447f0acf2bcabef0a79865ad434"
  ):
    raise PreparationError("source closure does not target the frozen v4.0.8 commit")

  checkout = checkout.resolve()
  output = output.resolve()
  adapter_roots = [root.resolve() for root in adapter_roots]
  if not checkout.is_dir():
    raise PreparationError(f"missing upstream checkout: {checkout}")
  for root in adapter_roots:
    if not root.is_dir():
      raise PreparationError(f"missing adapter source root: {root}")

  if output.exists():
    shutil.rmtree(output)
  output.mkdir(parents=True)

  copied_paths: dict[str, dict[str, Any]] = {}
  class_records: list[dict[str, Any]] = []
  nodes = closure.get("nodes")
  if not isinstance(nodes, list) or not nodes:
    raise PreparationError("closure contains no source nodes")

  for node in sorted(nodes, key=lambda item: item["class"]):
    fqcn = node.get("class")
    relative = node.get("path")
    expected_sha = node.get("source_sha256")
    classification = node.get("classification")
    if not all(isinstance(value, str) and value for value in (
        fqcn, relative, expected_sha, classification
    )):
      raise PreparationError(f"invalid closure node: {node}")

    adapter = find_adapter(fqcn, adapter_roots)
    if adapter is not None:
      source = adapter
      destination = output / fqcn_path(fqcn)
      origin = "open-offline-adapter"
    else:
      source = checkout / relative
      destination = output / strip_source_prefix(relative)
      origin = "upstream-v4.0.8"
      if not source.is_file():
        raise PreparationError(f"missing closure source for {fqcn}: {source}")
      actual_sha = sha256(source)
      if actual_sha != expected_sha:
        raise PreparationError(
            f"source SHA mismatch for {fqcn}: expected {expected_sha}, got {actual_sha}"
        )

    destination.parent.mkdir(parents=True, exist_ok=True)
    destination_key = destination.relative_to(output).as_posix()
    existing = copied_paths.get(destination_key)
    source_digest = sha256(source)
    if existing is None:
      shutil.copyfile(source, destination)
      copied_paths[destination_key] = {
          "path": destination_key,
          "origin": origin,
          "sha256": source_digest,
          "source": source.as_posix(),
      }
    elif existing["sha256"] != source_digest:
      raise PreparationError(
          f"multiple closure classes map to conflicting source file {destination_key}"
      )

    class_records.append({
        "class": fqcn,
        "classification": classification,
        "compiled_path": destination_key,
        "origin": origin,
        "source_sha256": source_digest,
    })

  manifest = {
      "schema_version": 1,
      "oracle_commit": oracle["commit"],
      "closure_content_sha256": closure.get("content_sha256"),
      "class_count": len(class_records),
      "source_file_count": len(copied_paths),
      "adapter_class_count": sum(
          1 for record in class_records if record["origin"] == "open-offline-adapter"
      ),
      "adapter_roots": [root.as_posix() for root in adapter_roots],
      "classes": class_records,
      "source_files": sorted(copied_paths.values(), key=lambda item: item["path"]),
  }
  manifest["content_sha256"] = hashlib.sha256(
      json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode("utf-8")
  ).hexdigest()
  return manifest


def build_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--checkout", type=Path, required=True)
  parser.add_argument("--closure", type=Path, required=True)
  parser.add_argument(
      "--adapter-root",
      action="append",
      type=Path,
      default=None,
      help="Reviewed Java adapter source root. May be repeated.",
  )
  parser.add_argument("--output", type=Path, required=True)
  parser.add_argument("--manifest-output", type=Path, required=True)
  return parser


def main() -> int:
  args = build_parser().parse_args()
  adapter_roots = args.adapter_root or [
      Path("oracle/v408-executable/adapters/src/main/java"),
      Path("oracle/v408-boundaries/src/main/java"),
  ]
  try:
    manifest = prepare(
        args.checkout,
        load_json(args.closure),
        adapter_roots,
        args.output,
    )
  except (PreparationError, OSError) as exc:
    print(f"Executable source preparation refused: {exc}", file=sys.stderr)
    return 2

  args.manifest_output.parent.mkdir(parents=True, exist_ok=True)
  args.manifest_output.write_text(
      json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
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
