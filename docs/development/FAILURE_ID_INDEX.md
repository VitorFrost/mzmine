# Repository-global failure ID index

## Purpose

`F-xxx` identifiers are repository-global. They are not local to an issue, pull request, workflow, or `agent/*` branch. This index is the canonical allocation table used to prevent parallel work from assigning the same identifier to unrelated failures.

Detailed original evidence remains in the append-only failure records, issue #27, issue #50, issue #55, issue #59, or the relevant pull request/workflow artifacts. This index records identity and current resolution state; it does not replace those records.

## Allocation rule

Before assigning a new identifier:

1. inspect this index;
2. inspect `docs/development/failures/`, the detailed failure register/resolution log, and active milestone issues;
3. inspect commits and active `agent/*` branches that may be progressing in parallel;
4. search repository issues/commits/branches for the candidate identifier;
5. allocate the next unused repository-global integer;
6. add the allocation to this index in the same focused branch/PR whenever practical.

If a collision is found later, preserve the original evidence, reassign the colliding record to the next unused canonical identifier, and record the alias/correction history.

As of the 2026-08-14 smoothing-evidence governance repair, identifiers through **F-046** are allocated. The next candidate is **F-047**, only after a fresh repository-wide check confirms that no parallel work has allocated it.

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
| F-034 | Smoothing source closure escaped through documentation-only importer references | **Resolved** by bounding Javadoc-only import edges; issue #55 records commit `3b09a4de86005738391cc7e21f357eee2fc8ff21`. A later stale-SHA metadata-write incident was also historically labelled F-034 in issue #55; that duplicate label is preserved as historical evidence rather than treated as a second canonical allocation |
| F-035 | GitHub connector action called with the wrong argument-name convention during resolver governance inspection | **Resolved operationally** by using each action's discovered schema; no repository/scientific mutation occurred |
| F-036 | Resolver differential workflow referenced a missing probe-preparation unittest | **Correction committed** as `d7613140314584c3f031cd2d7a6b7cc49a1aff59`; probe test subsequently passed in resolver CI before later precondition failures |
| F-037 | Accepted smoothing differential was merged without synchronizing the global machine-readable parity promotion | **Correction implemented by the F-046 governance-repair branch**: smoothing is promoted using artifact-derived evidence, not the later mis-transcribed hashes; exact-head CI/merge remains the validation boundary |
| F-038 | Session container could not resolve GitHub for a read-only local clone | **Resolved operationally** by using the authenticated GitHub connector as repository source of truth; no network workaround or scientific change |
| F-039 | Initial resolver inventory incorrectly required the legacy generic `FeatureResolver` framework API to be byte-identical | **Source-contract correction validated** by run `31825118473`: v2 audit PASS with 9 governed files, 6 identical and 3 exact reviewed differences; workflow assertion drift handled separately as F-044 |
| F-040 | Resolver side producer attempted nonexistent no-argument construction of `MinimumSearchFeatureResolver` | **Correction committed** as `9bfc77120f4c8116606a05414886f2b9439722f0`; no scientific resolver verdict yet because later runs stopped at the pre-resolver smoothing SHA guard |
| F-041 | Resolver source inventory referenced nonexistent `FeatureResolverParameters.java` | **Validated as corrected** by v2 inventory and run `31825118473`; actual framework dependencies `GeneralResolverParameters`, `Resolver`, and `AbstractResolver` are governed |
| F-042 | Stale optimistic blob SHA rejected resolver probe-unittest update | **Resolved** after fresh fetch/replay; accepted update commit `62621a8b30b8945632f72aa8c8821281400fe87b` |
| F-043 | Exploratory smoothing producer lookup guessed a nonexistent filename on the resolver branch | **Resolved operationally** through repository/PR file discovery; no repository/scientific mutation |
| F-044 | Source-gate workflow retained obsolete `7/7 identical` assertions after source-contract v2 correction | **Validated**: the corrected v2 source gate subsequently passed with 9 governed files, 6 identical and 3 reviewed differences |
| F-045 | Resolver producer rejected the reconstructed 517-feature smoothing state and initially attributed the mismatch to normalization-schema drift | **Original observation preserved; root-cause interpretation superseded by F-046.** The observed SHA `1c8fb4b8…ec971` is exactly the retained accepted smoothing artifact state, so the resolver had not executed and the accepted state itself was being rejected by a wrong expected SHA |
| F-046 | Accepted smoothing artifact values were transcribed incorrectly into later governance/prose and then copied into the resolver precondition | **Correction implemented on `agent/smoothing-evidence-governance-repair`** from retained run `31814860815` / artifact `9224679187`: machine evidence registry, inventories, docs, policy tests and failure history now use output SHA `1c8fb4b8…ec971` and artifact ZIP SHA `676191a2…fe98c9`; exact-head CI and merge remain required before resolver restart |

## Accepted direct parity consequences

### ADAP

F-033 was a differential-harness lifecycle problem, not a scientific mismatch. The accepted isolated-producer run retained the same governed input/settings and executed the open-offline and frozen v4.0.8 ADAP paths independently:

- candidate feature count: `517`;
- oracle feature count: `517`;
- candidate record SHA-256: `a6275eb15ce967e999374818803cc8b41c77a3e916d9f5196572caaf6e7d57fa`;
- oracle record SHA-256: identical;
- `records_equal: true`;
- `first_mismatch: null`.

The same-JVM OOM remains relevant evidence for the later memory/lifecycle release gate; process isolation does not constitute a claim of equal memory efficiency.

### Smoothing

The retained Savitzky-Golay smoothing artifact from workflow run `31814860815` is the primary evidence source. Its machine-readable reports show:

- candidate output records: `517`;
- oracle output records: `517`;
- normalized output SHA-256: `1c8fb4b82356facdbff4b88174990dcdf307b665df5ddddd30363cde471ec971` on both sides;
- artifact ID: `9224679187`;
- retained artifact ZIP SHA-256: `676191a22491899832d7426eef2ed2ca1b4ae29fd03c88c38a8a7d7b05fe98c9`;
- `records_equal: true`;
- `first_mismatch: null`;
- no numerical tolerance was used for the normalized record comparison.

The earlier `fcf45b949b1adca37bf3418366296525218c3117b692e363526c5adb9cdcb6c2` and `6763461911a7a22d04754f29a85601e99a0ecc19ed721e888734cf9f569d7b2a` values are retained only in historical issue/prose evidence explaining F-046; they are not accepted scientific identifiers.

F-037 was the missing promotion; F-046 identifies and corrects the incorrect evidence identifiers that had been proposed for that promotion.

### Minimum-search resolver source contract

The source contract is intentionally narrower than whole-framework byte identity. Exact v4.0.8 and candidate are byte-identical for the executed modern resolver interface, `AbstractResolver`, task factory, concrete local-minimum algorithm, concrete module, and concrete parameter class. Three generic framework files are frozen as exact reviewed differences: the legacy `FeatureResolver` API, `GeneralResolverParameters` legacy R declaration, and `FeatureResolverTask` branches for legacy R/group-MS2 integration.

No direct resolver behavioral verdict exists yet. Runs performed before F-046 stopped at the pre-resolver smoothing-state assertion. A valid resolver differential must first reproduce 517 records with SHA `1c8fb4b82356facdbff4b88174990dcdf307b665df5ddddd30363cde471ec971` on each side and then actually execute both resolver tasks.

## Governance lessons

Parallel issue/branch work caused several historical ID collisions. The canonical reconciliations are retained rather than erased. In particular:

- stale parity-policy tests are F-026;
- CI concurrency is F-027;
- ADAP parameter-class provenance is F-028;
- the disproven ADAP ordering hypothesis is F-029;
- the accidental direct-main documentation writes are F-030/F-031;
- the superseded duplicate Python dependency failure is F-032;
- the real ADAP same-JVM memory failure is F-033;
- the canonical smoothing closure overreach is F-034, while the duplicate F-034 wording for a later stale-SHA write remains historical issue evidence;
- resolver continuation failures F-035 through F-045 remain preserved even when later evidence superseded an early root-cause hypothesis;
- F-046 establishes the evidence hierarchy: retained machine-readable artifact > committed machine-readable registry/inventory > validated prose > human summaries.

Future work must consult this file and active branch/issue history before allocating F-047 or later IDs. A machine-enforced uniqueness gate remains recommended before the 4.0 release candidate.
