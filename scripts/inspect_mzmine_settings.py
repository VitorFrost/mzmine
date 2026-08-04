#!/usr/bin/env python3
"""Inspect a public MZmine XML settings/batch file without executing it.

The parser rejects DTD/entity declarations, records structural metadata, inventories Java class
references, batch steps, and parameter names, and emits deterministic JSON. It deliberately does not
instantiate classes or evaluate configuration values.
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

JAVA_CLASS_RE = re.compile(r"^(?:[a-zA-Z_$][\w$]*\.){2,}[A-Za-z_$][\w$]*$")
FORBIDDEN_XML_RE = re.compile(r"<!\s*(?:DOCTYPE|ENTITY)\b", re.IGNORECASE)
CLASS_ATTRIBUTE_NAMES = {"class", "method", "module", "module_class", "parameter_class"}
PARAMETER_TAGS = {"parameter", "param"}
STEP_TAGS = {"batchstep", "step", "module"}


class InspectionError(ValueError):
  pass


def local_name(tag: str) -> str:
  return tag.rsplit("}", 1)[-1] if "}" in tag else tag


def sha256_file(path: Path) -> str:
  digest = hashlib.sha256()
  with path.open("rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
      digest.update(chunk)
  return digest.hexdigest()


def inspect(path: Path) -> dict[str, Any]:
  if not path.is_file():
    raise InspectionError(f"XML file does not exist: {path}")
  raw = path.read_bytes()
  if not raw:
    raise InspectionError("XML file is empty")
  text = raw.decode("utf-8-sig")
  if FORBIDDEN_XML_RE.search(text):
    raise InspectionError("DTD and ENTITY declarations are forbidden")

  try:
    root = ET.fromstring(text)
  except ET.ParseError as exc:
    raise InspectionError(f"Invalid XML: {exc}") from exc

  tag_counts: collections.Counter[str] = collections.Counter()
  java_classes: set[str] = set()
  parameter_names: set[str] = set()
  steps: list[dict[str, Any]] = []

  for index, element in enumerate(root.iter()):
    tag = local_name(element.tag)
    tag_counts[tag] += 1

    attributes = {local_name(key): value.strip() for key, value in element.attrib.items()}
    for name, value in attributes.items():
      if name.lower() in CLASS_ATTRIBUTE_NAMES and JAVA_CLASS_RE.fullmatch(value):
        java_classes.add(value)
      elif JAVA_CLASS_RE.fullmatch(value) and value.startswith("io.github.mzmine."):
        java_classes.add(value)

    if tag.lower() in PARAMETER_TAGS:
      candidate = attributes.get("name") or attributes.get("id")
      if candidate:
        parameter_names.add(candidate)

    if tag.lower() in STEP_TAGS:
      class_value = next(
          (attributes.get(name) for name in CLASS_ATTRIBUTE_NAMES if attributes.get(name)), None
      )
      if class_value or tag.lower() == "batchstep":
        steps.append({
            "document_index": index,
            "tag": tag,
            "class": class_value,
            "attributes": dict(sorted(attributes.items())),
        })

  return {
      "schema_version": 1,
      "source_file": str(path.resolve()),
      "size_bytes": len(raw),
      "sha256": sha256_file(path),
      "xml_root_tag": local_name(root.tag),
      "root_attributes": dict(sorted((local_name(k), v) for k, v in root.attrib.items())),
      "element_count": sum(tag_counts.values()),
      "tag_counts": dict(sorted(tag_counts.items())),
      "java_class_count": len(java_classes),
      "java_classes": sorted(java_classes),
      "parameter_name_count": len(parameter_names),
      "parameter_names": sorted(parameter_names),
      "step_count": len(steps),
      "steps": steps,
  }


def parse_args(argv: list[str]) -> argparse.Namespace:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("xml", type=Path)
  parser.add_argument("--output", type=Path, required=True)
  return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
  args = parse_args(argv or sys.argv[1:])
  try:
    report = inspect(args.xml.resolve())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0
  except (InspectionError, OSError, UnicodeError) as exc:
    print(f"ERROR: {exc}", file=sys.stderr)
    return 2


if __name__ == "__main__":
  raise SystemExit(main())
