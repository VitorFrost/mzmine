#!/usr/bin/env python3
"""Audit the public source dependency closure for the frozen MZmine v4.0.8 oracle slice.

This is a conservative source-level inventory, not a Java compiler. It follows explicit internal
imports, fully qualified internal references, internal wildcard imports, and same-package top-level
type references. Reviewed bootstrap boundaries are recorded but are not traversed. Their source is
still inspected for direct prohibited references so the report explains why the boundary exists.

The command is fail-closed for missing entry points, unresolved explicit internal imports, unexpected
prohibited references, and a checkout whose Git commit differs from the frozen oracle commit.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from collections import defaultdict, deque
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

PACKAGE_RE = re.compile(r"\bpackage\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;")
IMPORT_RE = re.compile(
    r"\bimport\s+(static\s+)?([A-Za-z_$][\w$]*(?:\.[A-Za-z_$*][\w$*]*)*)\s*;"
)
TYPE_RE = re.compile(
    r"(?m)^\s*(?:public\s+|protected\s+|private\s+|abstract\s+|final\s+|sealed\s+|"
    r"non-sealed\s+|static\s+|strictfp\s+)*(?:class|interface|enum|record|@interface)\s+"
    r"([A-Za-z_$][\w$]*)\b"
)
CAPITALIZED_RE = re.compile(r"\b([A-Z_$][A-Za-z0-9_$]*)\b")
FQCN_RE = re.compile(r"\b(?:io\.github\.mzmine|io\.mzio)(?:\.[A-Za-z_$][\w$]*)+\b")
HEX40_RE = re.compile(r"^[0-9a-f]{40}$")


class AuditError(ValueError):
  """Raised when the manifest or checkout cannot be audited safely."""


@dataclass(frozen=True)
class SourceUnit:
  path: Path
  relative_path: str
  package: str
  top_level_types: tuple[str, ...]
  text: str
  code: str
  sha256: str


@dataclass(frozen=True, order=True)
class Edge:
  source: str
  target: str
  relation: str


@dataclass(frozen=True, order=True)
class Reference:
  source: str
  reference: str
  context: str


def load_json(path: Path) -> dict[str, Any]:
  try:
    value = json.loads(path.read_text(encoding="utf-8"))
  except FileNotFoundError as exc:
    raise AuditError(f"missing JSON file: {path}") from exc
  except json.JSONDecodeError as exc:
    raise AuditError(f"invalid JSON in {path}: {exc}") from exc
  if not isinstance(value, dict):
    raise AuditError(f"top-level JSON value must be an object: {path}")
  return value


def require_text(value: Any, field: str) -> str:
  if not isinstance(value, str) or not value.strip():
    raise AuditError(f"{field} must be a non-empty string")
  return value


def require_string_list(value: Any, field: str) -> list[str]:
  if not isinstance(value, list) or not value:
    raise AuditError(f"{field} must be a non-empty array")
  result: list[str] = []
  for index, item in enumerate(value):
    result.append(require_text(item, f"{field}[{index}]") )
  if len(result) != len(set(result)):
    raise AuditError(f"{field} must not contain duplicates")
  return result


def validate_manifest(manifest: dict[str, Any]) -> None:
  if manifest.get("schema_version") != 1:
    raise AuditError("manifest schema_version must be 1")
  require_text(manifest.get("audit_id"), "audit_id")
  oracle = manifest.get("oracle")
  if not isinstance(oracle, dict):
    raise AuditError("oracle must be an object")
  require_text(oracle.get("repository"), "oracle.repository")
  require_text(oracle.get("tag"), "oracle.tag")
  commit = require_text(oracle.get("commit"), "oracle.commit")
  if not HEX40_RE.fullmatch(commit):
    raise AuditError("oracle.commit must be a lowercase 40-character Git SHA")
  require_text(oracle.get("license"), "oracle.license")
  require_string_list(manifest.get("source_roots"), "source_roots")
  require_string_list(manifest.get("entry_points"), "entry_points")
  require_string_list(manifest.get("internal_prefixes"), "internal_prefixes")
  require_string_list(manifest.get("prohibited_prefixes"), "prohibited_prefixes")
  boundaries = manifest.get("reviewed_boundaries")
  if not isinstance(boundaries, dict):
    raise AuditError("reviewed_boundaries must be an object")
  for name, boundary in boundaries.items():
    require_text(name, "reviewed_boundaries key")
    if not isinstance(boundary, dict):
      raise AuditError(f"reviewed boundary {name} must be an object")
    require_text(boundary.get("classification"), f"{name}.classification")
    require_text(boundary.get("reason"), f"{name}.reason")
  policy = manifest.get("policy")
  if not isinstance(policy, dict):
    raise AuditError("policy must be an object")
  for key in (
      "follow_reviewed_boundaries",
      "fail_on_missing_entry_point",
      "fail_on_unresolved_internal_import",
      "fail_on_unreviewed_prohibited_reference",
      "require_at_least_one_reviewed_boundary",
  ):
    if not isinstance(policy.get(key), bool):
      raise AuditError(f"policy.{key} must be boolean")


def strip_comments_and_literals(text: str) -> str:
  """Replace comments and string/character literals with spaces while preserving newlines."""
  result = list(text)
  index = 0
  length = len(text)
  state = "code"
  quote = ""
  while index < length:
    char = text[index]
    next_char = text[index + 1] if index + 1 < length else ""
    if state == "code":
      if char == "/" and next_char == "/":
        result[index] = result[index + 1] = " "
        index += 2
        state = "line_comment"
        continue
      if char == "/" and next_char == "*":
        result[index] = result[index + 1] = " "
        index += 2
        state = "block_comment"
        continue
      if char in ('"', "'"):
        quote = char
        result[index] = " "
        index += 1
        state = "literal"
        continue
      index += 1
      continue
    if state == "line_comment":
      if char == "\n":
        state = "code"
      else:
        result[index] = " "
      index += 1
      continue
    if state == "block_comment":
      if char == "*" and next_char == "/":
        result[index] = result[index + 1] = " "
        index += 2
        state = "code"
      else:
        if char != "\n":
          result[index] = " "
        index += 1
      continue
    if state == "literal":
      if char == "\\":
        result[index] = " "
        if index + 1 < length:
          if text[index + 1] != "\n":
            result[index + 1] = " "
          index += 2
        else:
          index += 1
        continue
      if char == quote:
        result[index] = " "
        index += 1
        state = "code"
      else:
        if char != "\n":
          result[index] = " "
        index += 1
  return "".join(result)


def parse_source(path: Path, checkout: Path) -> SourceUnit:
  text = path.read_text(encoding="utf-8")
  code = strip_comments_and_literals(text)
  package_match = PACKAGE_RE.search(code)
  package = package_match.group(1) if package_match else ""
  types = tuple(dict.fromkeys(TYPE_RE.findall(code)))
  return SourceUnit(
      path=path,
      relative_path=path.relative_to(checkout).as_posix(),
      package=package,
      top_level_types=types,
      text=text,
      code=code,
      sha256=hashlib.sha256(text.encode("utf-8")).hexdigest(),
  )


def build_index(checkout: Path, source_roots: Iterable[str]) -> tuple[
    dict[str, SourceUnit], dict[str, list[str]], list[str]
]:
  index: dict[str, SourceUnit] = {}
  packages: dict[str, list[str]] = defaultdict(list)
  roots_used: list[str] = []
  for root_value in source_roots:
    root = (checkout / root_value).resolve()
    try:
      root.relative_to(checkout.resolve())
    except ValueError as exc:
      raise AuditError(f"source root escapes checkout: {root_value}") from exc
    if not root.is_dir():
      raise AuditError(f"source root is missing: {root_value}")
    roots_used.append(root_value)
    for path in sorted(root.rglob("*.java")):
      unit = parse_source(path, checkout)
      for type_name in unit.top_level_types:
        fqcn = f"{unit.package}.{type_name}" if unit.package else type_name
        if fqcn in index and index[fqcn].path != path:
          raise AuditError(
              f"duplicate top-level type {fqcn}: {index[fqcn].relative_path}, "
              f"{unit.relative_path}"
          )
        index[fqcn] = unit
        packages[unit.package].append(fqcn)
  for values in packages.values():
    values.sort()
  return index, dict(packages), roots_used


def resolve_internal_type(reference: str, index: dict[str, SourceUnit]) -> str | None:
  candidate = reference.removesuffix(".*")
  while candidate:
    if candidate in index:
      return candidate
    if "." not in candidate:
      break
    candidate = candidate.rsplit(".", 1)[0]
  return None


def is_prefixed(value: str, prefixes: Iterable[str]) -> bool:
  return any(value.startswith(prefix) for prefix in prefixes)


def imports_for(unit: SourceUnit) -> list[tuple[bool, str]]:
  return [(bool(match.group(1)), match.group(2)) for match in IMPORT_RE.finditer(unit.code)]


def direct_prohibited_references(
    fqcn: str, unit: SourceUnit, prohibited_prefixes: list[str]
) -> set[Reference]:
  references: set[Reference] = set()
  for is_static, imported in imports_for(unit):
    if is_prefixed(imported, prohibited_prefixes):
      references.add(Reference(fqcn, imported, "static-import" if is_static else "import"))
  for reference in FQCN_RE.findall(unit.code):
    if is_prefixed(reference, prohibited_prefixes):
      references.add(Reference(fqcn, reference, "qualified-reference"))
  return references


def dependencies_for(
    fqcn: str,
    unit: SourceUnit,
    index: dict[str, SourceUnit],
    packages: dict[str, list[str]],
    internal_prefixes: list[str],
) -> tuple[set[Edge], set[Reference], set[Reference]]:
  edges: set[Edge] = set()
  unresolved: set[Reference] = set()
  wildcard_imports: set[Reference] = set()
  explicit_simple_names: set[str] = set()

  for is_static, imported in imports_for(unit):
    if not is_prefixed(imported, internal_prefixes):
      continue
    relation = "static-import" if is_static else "import"
    if imported.endswith(".*") and not is_static:
      package = imported[:-2]
      targets = packages.get(package, [])
      wildcard_imports.add(Reference(fqcn, imported, "package-wildcard"))
      if not targets:
        unresolved.add(Reference(fqcn, imported, "unresolved-package-wildcard"))
      for target in targets:
        if target != fqcn:
          edges.add(Edge(fqcn, target, "wildcard-import"))
      continue

    resolved = resolve_internal_type(imported, index)
    if resolved is None and is_static:
      resolved = resolve_internal_type(imported.rsplit(".", 1)[0], index)
    if resolved is None:
      unresolved.add(Reference(fqcn, imported, f"unresolved-{relation}"))
      continue
    explicit_simple_names.add(resolved.rsplit(".", 1)[-1])
    if resolved != fqcn:
      edges.add(Edge(fqcn, resolved, relation))

  for reference in FQCN_RE.findall(unit.code):
    if not is_prefixed(reference, internal_prefixes):
      continue
    resolved = resolve_internal_type(reference, index)
    if resolved is None:
      unresolved.add(Reference(fqcn, reference, "unresolved-qualified-reference"))
    elif resolved != fqcn:
      edges.add(Edge(fqcn, resolved, "qualified-reference"))

  package_types = packages.get(unit.package, [])
  if package_types:
    identifiers = set(CAPITALIZED_RE.findall(unit.code))
    for target in package_types:
      if target == fqcn:
        continue
      simple_name = target.rsplit(".", 1)[-1]
      if simple_name in identifiers and simple_name not in explicit_simple_names:
        edges.add(Edge(fqcn, target, "same-package-reference"))

  return edges, unresolved, wildcard_imports


def git_head(checkout: Path) -> str | None:
  completed = subprocess.run(
      ["git", "-C", str(checkout), "rev-parse", "HEAD"],
      text=True,
      stdout=subprocess.PIPE,
      stderr=subprocess.PIPE,
      check=False,
  )
  if completed.returncode != 0:
    return None
  value = completed.stdout.strip()
  return value if HEX40_RE.fullmatch(value) else None


def canonical_sha(value: dict[str, Any]) -> str:
  encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
  return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def audit(checkout: Path, manifest: dict[str, Any], require_commit: bool) -> tuple[dict[str, Any], list[str]]:
  validate_manifest(manifest)
  checkout = checkout.resolve()
  if not checkout.is_dir():
    raise AuditError(f"checkout directory is missing: {checkout}")

  oracle = manifest["oracle"]
  actual_commit = git_head(checkout)
  violations: list[str] = []
  if require_commit:
    if actual_commit is None:
      violations.append("checkout is not a readable Git worktree")
    elif actual_commit != oracle["commit"]:
      violations.append(
          f"checkout commit mismatch: expected {oracle['commit']}, got {actual_commit}"
      )

  index, packages, roots_used = build_index(checkout, manifest["source_roots"])
  entry_points: list[str] = manifest["entry_points"]
  internal_prefixes: list[str] = manifest["internal_prefixes"]
  prohibited_prefixes: list[str] = manifest["prohibited_prefixes"]
  boundaries: dict[str, dict[str, str]] = manifest["reviewed_boundaries"]
  policy: dict[str, bool] = manifest["policy"]

  missing_entries = sorted(entry for entry in entry_points if entry not in index)
  if missing_entries and policy["fail_on_missing_entry_point"]:
    violations.extend(f"missing entry point: {entry}" for entry in missing_entries)

  queue = deque(sorted(entry for entry in entry_points if entry in index))
  visited: set[str] = set()
  queued = set(queue)
  edges: set[Edge] = set()
  unresolved: set[Reference] = set()
  wildcard_imports: set[Reference] = set()
  prohibited: set[Reference] = set()
  reached_boundaries: set[str] = set()

  while queue:
    fqcn = queue.popleft()
    if fqcn in visited:
      continue
    visited.add(fqcn)
    unit = index[fqcn]
    prohibited.update(direct_prohibited_references(fqcn, unit, prohibited_prefixes))

    is_boundary = fqcn in boundaries
    if is_boundary:
      reached_boundaries.add(fqcn)
      if not policy["follow_reviewed_boundaries"]:
        continue

    unit_edges, unit_unresolved, unit_wildcards = dependencies_for(
        fqcn, unit, index, packages, internal_prefixes
    )
    edges.update(unit_edges)
    unresolved.update(unit_unresolved)
    wildcard_imports.update(unit_wildcards)
    for edge in sorted(unit_edges):
      if edge.target not in visited and edge.target not in queued:
        queue.append(edge.target)
        queued.add(edge.target)

  unreviewed_prohibited = sorted(
      reference for reference in prohibited if reference.source not in reached_boundaries
  )
  if unreviewed_prohibited and policy["fail_on_unreviewed_prohibited_reference"]:
    violations.extend(
        f"unreviewed prohibited reference: {ref.source} -> {ref.reference}"
        for ref in unreviewed_prohibited
    )
  if unresolved and policy["fail_on_unresolved_internal_import"]:
    violations.extend(
        f"unresolved internal reference: {ref.source} -> {ref.reference} ({ref.context})"
        for ref in sorted(unresolved)
    )
  if policy["require_at_least_one_reviewed_boundary"] and not reached_boundaries:
    violations.append("no reviewed source boundary was reached from the entry points")

  nodes: list[dict[str, Any]] = []
  for fqcn in sorted(visited):
    unit = index[fqcn]
    if fqcn in entry_points:
      classification = "entry-point"
    elif fqcn in reached_boundaries:
      classification = boundaries[fqcn]["classification"]
    else:
      classification = "source-dependency"
    node: dict[str, Any] = {
        "class": fqcn,
        "path": unit.relative_path,
        "source_sha256": unit.sha256,
        "classification": classification,
    }
    if fqcn in reached_boundaries:
      node["boundary_reason"] = boundaries[fqcn]["reason"]
    nodes.append(node)

  report: dict[str, Any] = {
      "schema_version": 1,
      "audit_id": manifest["audit_id"],
      "oracle": oracle,
      "checkout": {
          "path_recorded": checkout.name,
          "actual_commit": actual_commit,
          "commit_required": require_commit,
      },
      "source_roots": roots_used,
      "entry_points": entry_points,
      "nodes": nodes,
      "edges": [edge.__dict__ for edge in sorted(edges)],
      "reviewed_boundaries_reached": [
          {
              "class": fqcn,
              "classification": boundaries[fqcn]["classification"],
              "reason": boundaries[fqcn]["reason"],
          }
          for fqcn in sorted(reached_boundaries)
      ],
      "prohibited_references": [reference.__dict__ for reference in sorted(prohibited)],
      "unresolved_internal_references": [
          reference.__dict__ for reference in sorted(unresolved)
      ],
      "internal_wildcard_imports": [
          reference.__dict__ for reference in sorted(wildcard_imports)
      ],
      "summary": {
          "indexed_top_level_types": len(index),
          "reachable_source_types": len(visited),
          "dependency_edges": len(edges),
          "reviewed_boundaries_reached": len(reached_boundaries),
          "prohibited_references": len(prohibited),
          "unresolved_internal_references": len(unresolved),
          "internal_wildcard_imports": len(wildcard_imports),
          "violations": len(violations),
      },
      "status": "pass" if not violations else "fail",
      "violations": sorted(set(violations)),
      "interpretation": (
          "A passing audit proves only that the configured source-level closure and reviewed "
          "boundaries are reproducible. It does not prove that the v4.0.8 oracle compiles or that "
          "scientific behavior is equivalent."
      ),
  }
  report["content_sha256"] = canonical_sha(report)
  return report, sorted(set(violations))


def build_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--checkout", type=Path, required=True)
  parser.add_argument(
      "--manifest",
      type=Path,
      default=Path("datasets/parity/v408_source_closure_manifest.json"),
  )
  parser.add_argument("--output", type=Path, required=True)
  parser.add_argument(
      "--require-commit",
      action="store_true",
      help="Require the checkout HEAD to equal the frozen oracle commit.",
  )
  return parser


def main() -> int:
  args = build_parser().parse_args()
  try:
    manifest = load_json(args.manifest)
    report, violations = audit(args.checkout, manifest, args.require_commit)
  except (AuditError, OSError) as exc:
    print(f"Source closure audit refused: {exc}", file=sys.stderr)
    return 2

  args.output.parent.mkdir(parents=True, exist_ok=True)
  args.output.write_text(
      json.dumps(report, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
      encoding="utf-8",
  )
  print(json.dumps(report["summary"], indent=2, sort_keys=True))
  print(f"content_sha256={report['content_sha256']}")
  if violations:
    for violation in violations:
      print(f"VIOLATION: {violation}", file=sys.stderr)
    return 2
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
