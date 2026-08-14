# Repository-global failure ID index

## Purpose

`F-xxx` identifiers are repository-global. They are not local to an issue, pull request, workflow, or `agent/*` branch. This index is the canonical allocation table used to prevent parallel work from assigning the same identifier to unrelated failures.

Detailed original evidence remains in the append-only failure records, issue #27, issue #50/#55, or the relevant pull request/workflow artifacts. This index records identity and current resolution state; it does not replace those records.

## Allocation rule

Before assigning a new identifier:

1. inspect this index;
2. inspect `docs/development/failures/`, the detailed failure register/resolution log, and active milestone issues;
3. inspect commits and active `agent/*` branches that may be progressing in parallel;
4. search the repository issues/commits for the candidate identifier;
5. allocate the next unused repository-global integer;
6. add the allocation to this index in the same focused branch/PR whenever practical.

If a collision is found later, preserve the original evidence, reassign the colliding record to the next unused canonical identifier, and record the alias/correction history.

As of the 2026-08-14 smoothing parity work, identifiers through **F-034** are allocated. The next candidate is **F-035**, only after a fresh repository-wide check confirms that no parallel work has allocated it.

## Canonical allocation table

| ID | Short description | Current state / evidence boundary |
|---|---|---|
| F-001 | Repository Gradle wrapper cannot run the Java 21 oracle build | Validated; isolated oracle Gradle used |
| F-002 | Java 21 preview source compiled without preview enabled | Validated; preview enabled |
| F-003 | Reviewed boundaries copied as full application classes | Validated via locked adapters |
| F-004 | Boundary member contract initially incomplete/misaligned | Validated for declared executable contract |
| F-005 | Adapter provenance depended on external retrieval path | Validated; checked-out bytes are authoritative |
| F-006 | Java source inventory parser could misread comments/literals/nested types | Validated with parser regression tests |
| F-007 | Adapter inventory hash depended on physical paths | Validated with portable root identities |
| F-008 | Preparation manifest leaked physical workspace paths | Validated with portable canonical identities |
| F-009 | Adapter contract bound to superseded closure hash | Validated against corrected closure |
| F-010 | README creation attempted through wrong existing-file operation | Validated with correct create operation |
| F-011 | Existing-file update used stale blob SHA | Validated after fresh lookup |
| F-012 | Invalid/no-op repository file update attempt | Historical tool-routing failure |
| F-013 | Placeholder file update used instead of PR metadata update | Historical tool-routing failure |
| F-014 | Repeated file-routing error while updating PR body | Historical tool-routing failure |
| F-015 | Oracle mzML physical path resolved under standalone Gradle project | Validated; physical path separated from portable identity |
| F-016 | Cached legacy MSDK reread returned caller-supplied zero arrays | Validated in report producer; production parser unchanged |
| F-017 | Legacy binary32 RT representation produced deterministic strict differences | Validated with narrow RT-only contract; strict evidence retained |
| F-018 | Transient Windows Maven HTTP 403 | Validated by unchanged retry; dependency policy not weakened |
| F-019 | Obsolete release workflow produced invalid/jobless failure | Corrected with read-only release guard |
| F-020 | ADAP source lookup assumed historical/nonexistent class identity | Historical source-inventory failure; corrected by exact tree/source inspection |
| F-021 | Parallel ADAP source lookup used another incorrect class assumption | Historical source-inventory failure; corrected before accepted inventory |
| F-022 | ADAP source-closure summary requested wrong evidence key | Corrected without weakening auditor |
| F-023 | ADAP closure expanded into application graph through `ModularFeatureList` | Bounded with reviewed application/UI edges and focused task probe |
| F-024 | Frozen v4 ADAP task required default feature-list sorting API absent from 3.9 line | Compatibility boundary retained; exact v4 non-imaging behavior resolves to RT sorting |
| F-025 | Legacy JAXP provider rejected defense-in-depth XML access-control attributes | Corrected while retaining mandatory anti-XXE features |
| F-026 | Parity-policy unit tests encoded obsolete pre-PR #49 inventory state | **Validated** by PR #51 run `31810269951`; validator/tolerances unchanged |
| F-027 | Push/PR CI events shared a concurrency group and could leave no successful run | **Validated** by PR #51: event type added to concurrency key; exact-head PR CI green |
| F-028 | ADAP inventory referenced nonexistent `ModularADAPChromatogramBuilderParameters` | **Resolved**; canonical class is `ADAPChromatogramBuilderParameters` |
| F-029 | Apparent ADAP row-order difference inferred from `sortByDefault` call-site change | **Resolved as source-API false positive**: exact v4.0.8 `sortByDefault` dispatches non-imaging LC-MS to `sortByDefaultRT`; no scientific correction required |
| F-030 | Global failure-index placeholder accidentally created directly on `open-offline-main` | **Validated as corrected** after failure/proposal record |
| F-031 | Accidental `docs/development/NOOP` file created directly on `open-offline-main` | **Validated as corrected** by commit `89f1d36f118998fd10b7d8918e1c08a770ddb723`; only the accidental file was removed |
| F-032 | Superseded duplicate ADAP harness test introduced undeclared Python `jsonschema` dependency | **Resolved on superseded branch; not merged**. Test made dependency-neutral and duplicate PR #52 closed |
| F-033 | Governed ADAP differential exhausted heap when candidate and v4.0.8 oracle ran sequentially in one JVM | **Validated as resolved** by independent candidate/oracle JVM producers; direct run `31812225494` completed with 517/517 exact normalized records and identical record SHA-256 |
| F-034 | Smoothing source closure escaped into unrelated import/task-controller graph and hit unresolved `AllSpectralDataImportMainTask -> ThreadPoolTask` | **Open**. Run `31814946367` retained; correction must identify and bound the non-executed application/import path rather than adding importer/thread-pool behavior blindly |

## ADAP parity consequence

F-033 was a differential-harness lifecycle problem, not a scientific mismatch. The accepted isolated-producer run retained the same governed input/settings and executed the open-offline and frozen v4.0.8 ADAP paths independently. The final normalized records were exactly equal:

- candidate feature count: `517`;
- oracle feature count: `517`;
- candidate record SHA-256: `a6275eb15ce967e999374818803cc8b41c77a3e916d9f5196572caaf6e7d57fa`;
- oracle record SHA-256: identical;
- `records_equal: true`;
- `first_mismatch: null`.

The same-JVM OOM remains relevant evidence for the later memory/lifecycle release gate; process isolation does not constitute a claim of equal memory efficiency.

## Smoothing parity note

F-034 is a source-closure/provenance failure, not evidence of a smoothing algorithm difference. The independent source inventory shows the governed smoothing task, parameter set, module, algorithm interface, Savitzky-Golay implementation/parameters, LOESS implementation/parameters, and zero-handling type are byte-identical between candidate and frozen v4.0.8. The closure must still be narrowed/reviewed before that source identity can support the direct differential.

## Governance lessons

Parallel issue/branch work caused several historical ID collisions. The canonical reconciliations are retained rather than erased. Future work must consult this file and active branch/issue history before allocating F-035 or later IDs. A machine-enforced uniqueness gate remains recommended before the 4.0 release candidate.
