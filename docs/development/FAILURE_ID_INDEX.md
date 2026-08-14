# Repository-global failure ID index

## Purpose

`F-xxx` identifiers are **repository-global**, not local to an issue, pull request, workflow, or
`agent/*` branch. This index exists to prevent parallel work from assigning the same identifier to
different failures.

The detailed original evidence remains where it was first recorded: `FAILURE_REGISTER.md`,
`FAILURE_RESOLUTION_LOG.md`, files under `docs/development/failures/`, the Milestone 4 issue #27,
issue #50, or the relevant pull request. This file is the canonical allocation index; it does not
replace those append-only records.

The older `FAILURE_RESOLUTION_LOG.md` is a historical detailed log through the early executable-oracle
work. Any stale "next unresolved gate" language in that older snapshot is superseded by this index,
`MZMINE_4_PARITY.md`, and the active milestone documentation.

## Allocation rule

Before creating a new failure record:

1. inspect this index;
2. inspect `docs/development/failures/` and the main failure register/resolution log;
3. inspect the active milestone/feature issue threads;
4. inspect commit messages on active `agent/*` branches that may be progressing in parallel;
5. allocate the next unused repository-global integer;
6. add the allocation to this index in the same documentation change as the new record whenever
   practical.

If a collision is found later, **do not delete or silently rewrite the original evidence**. Preserve
the initial timestamp/content, assign the colliding record the next free canonical identifier, and
record the alias/correction history in both locations.

As of the reconciliation on **2026-08-14**, identifiers through `F-030` are allocated. The next
identifier is `F-031` **only if a fresh repository-wide check confirms that no parallel branch has
allocated it in the meantime**.

## Canonical allocation table

| ID | Short description | Current state / evidence boundary |
|---|---|---|
| F-001 | Repository Gradle wrapper cannot run the Java 21 oracle build | Validated; isolated Gradle 8.10.2 used for oracle build |
| F-002 | Java 21 preview source compiled without preview enabled | Validated; preview enabled for compile/test/JavaExec |
| F-003 | Reviewed boundaries copied as full application classes | Validated for source preparation/compilation via locked adapters |
| F-004 | Boundary member contract initially incomplete/misaligned | Validated for declared executable member contract |
| F-005 | Adapter provenance depended on external retrieval path | Validated; checked-out bytes are the source of truth |
| F-006 | Java source inventory parser could misread comments/literals/nested types | Validated with parser/inventory regression tests |
| F-007 | Adapter inventory hash depended on physical parent paths | Validated with portable logical root IDs |
| F-008 | Preparation manifest leaked physical workspace paths | Validated with portable canonical source identities |
| F-009 | Adapter contract bound to a superseded closure hash | Validated against corrected closure hash |
| F-010 | New README was attempted through the wrong existing-file operation | Validated; created with the correct new-file operation |
| F-011 | Existing-file update used a stale blob SHA | Validated after fresh blob lookup |
| F-012 | Invalid/no-op repository file update attempt | Historical tool-routing failure; documented under `docs/development/failures/` |
| F-013 | Placeholder file update used instead of PR metadata update | Historical tool-routing failure; documented under `docs/development/failures/` |
| F-014 | Repeated file-routing error while updating PR body | Historical tool-routing failure; documented under `docs/development/failures/` |
| F-015 | Oracle mzML physical path resolved under the standalone Gradle project | Validated; physical workspace paths separated from portable dataset identity |
| F-016 | Cached legacy MSDK scan reread returned caller-supplied zero arrays | Validated by requesting parser-owned arrays and copying declared point count |
| F-017 | Legacy binary32 RT representation produced deterministic strict differences | Validated as a narrow RT-only representation contract; strict evidence retained |
| F-018 | Transient Windows Maven HTTP 403 | Validated by unchanged retry; dependency/repository policy not weakened |
| F-019 | Obsolete release workflow produced an invalid/jobless failure | Corrected with a read-only release guard; packaging remains a later release gate |
| F-020 | ADAP source lookup assumed a historical/nonexistent class identity | Historical ADAP source-inventory failure on #27; corrected by tree/source inspection |
| F-021 | Parallel ADAP source lookup used another incorrect class assumption | Historical ADAP source-inventory failure on #27; corrected before accepting source inventory |
| F-022 | ADAP source-closure summary requested the wrong evidence key | Historical ADAP workflow-evidence failure on #27; corrected without weakening the auditor |
| F-023 | ADAP closure expanded into the application graph through `ModularFeatureList` | ADAP branch bounded application-only paths and prepared a focused task probe; no scientific-parity claim follows from closure alone |
| F-024 | Frozen v4 ADAP task required default feature-list sorting absent from the 3.9 line | ADAP task-probe compatibility work; scientific effect remains governed by direct differential evidence |
| F-025 | Legacy JAXP provider rejected defense-in-depth XML access-control attributes | Implemented on ADAP branch; mandatory anti-XXE features retained. Its dedicated task-probe run later failed at direct differential execution, so F-025 alone does not validate ADAP parity |
| F-026 | Parity-policy unit tests encoded the obsolete pre-PR #49 inventory state | **Validated** by PR #51 run `31810269951`; validator/scientific tolerances unchanged |
| F-027 | Push/PR CI events can enter the same concurrency group and leave no successful run | Governance defect recorded during PR #51; any concurrency correction must be validated independently |
| F-028 | ADAP inventory referenced nonexistent `ModularADAPChromatogramBuilderParameters` | Source-provenance record on #50; canonical class is `ADAPChromatogramBuilderParameters`; no parity promotion |
| F-029 | ADAP final feature-list ordering/renumbering differs between 3.9-line candidate and v4.0.8 task | Observed/source-classified; row identity/order must remain visible in the direct differential and must not be normalized away |
| F-030 | Global failure-index placeholder was accidentally created directly on `open-offline-main` | **Validated as corrected**: original placeholder commit `85be47cdbab026422639683ff1ad8165a6d2a1c3`; correction replaced it in place with this complete index after the failure/proposal was recorded on #27 |

## Active ADAP note

The branch `agent/v408-chromatogram-feature-parity` is active and may move independently of merged
documentation work. At the 2026-08-14 documentation check it had progressed beyond F-025 and
contained:

- a frozen ADAP source inventory;
- fail-closed source-closure work;
- a compiled frozen v4.0.8 ADAP task probe;
- a governed public real-data differential acceptance test;
- explicit failure exposure in CI;
- a subsequent worker-heap change after the F-025 run reached the direct differential and failed.

The F-025 head `603332159e00cdd96afc3a2f5dadba13d7b53335` passed the general Open Offline
CI, but dedicated ADAP task-probe run `31810440392` failed specifically at `Execute direct ADAP
differential on public LC-MS` after source preparation, compilation, governed mzML download/hash
verification, settings download/hash verification, and pre-execution checks had passed. A later branch
head changed worker heap and started a new task-probe execution. This is **work in progress**, not
integrated evidence.

ADAP remains `Adapted` with `direct_differential_complete = false` until the strict/governed
scientific comparison is accepted, all subsequent failures are globally registered, regressions are
green, and the result is merged into `open-offline-main`.

## Governance lesson from the 2026-08-14 reconciliation

Issue #27, issue #50, PR #51, and the independent ADAP branch were progressing concurrently. That
allowed F-IDs to be reused for unrelated observations before the collision was noticed. The colliding
issue records were reassigned while retaining their original text/history:

- the stale parity-policy test record is canonical `F-026`;
- the CI concurrency record is canonical `F-027`;
- the issue #50 parameter-class record is canonical `F-028`;
- the issue #50 ordering/renumbering record is canonical `F-029`.

F-030 then demonstrated a second governance lesson: even documentation-only writes must obey the
focused-branch workflow. Its correction was recorded before replacing the placeholder, and no
scientific or parity state was changed.

Future work must treat this file as a repository-global allocation lock in documentation form. A
machine-enforced uniqueness check is recommended before the 4.0 release candidate.
