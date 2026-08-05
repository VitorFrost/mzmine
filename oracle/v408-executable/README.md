# MZmine v4.0.8 executable parser/centroid oracle

## Scope

This standalone Java 21 build compiles the focused public MZmine v4.0.8 source slice defined by the
passing source closure. It is a source-compatibility and future behavioral-oracle harness, not an
application distribution.

Included scope:

- non-IMS LC-MS mzML parser records;
- scan and precursor metadata exposed by the public parser layer;
- primitive m/z and intensity arrays;
- the exact public centroid primitive-array overload;
- explicit no-filter/no-advanced-import processing;
- manual source MS1/MS2 selection outside the parser.

Excluded scope:

- project registration, feature lists, and application raw-data objects;
- JavaFX, GUI, desktop lifecycle, and scheduler integration;
- account, cloud, event, licensing, or `io.mzio` services;
- IMS frame construction;
- automatic manufacturer, instrument, or source-MS-level inference;
- unavailable memory-map behavior.

## Frozen provenance

| Item | Value |
|---|---|
| Public repository | `mzmine/mzmine` |
| Public tag | `v4.0.8` |
| Exact commit | `8029f930d28c0447f0acf2bcabef0a79865ad434` |
| Current source closure | `6662c196b9487154cf7efb4aafb1574c42a6c90a554c8805c6585b5787a0ffd6` |
| Adapter byte lock | `1f3fa33550bfd1742c1af0b809ebb700794979553489d6278731f43cfdfb2e43` |
| Adapter contract ID | `mzmine-v4.0.8-parser-centroid-adapters-v1` |
| Validated adapter contract SHA-256 | `ee6a6b2db93bd7923b81686854f9cc649c161cafb33b7a0baea8a1d3ad116d86` |
| Validated portable source-set SHA-256 | `b1cbac55fbe28a32dda9fea0043bb9b97879b2cb0bece89ca12a0ddc494df23a` |

## Preparation rules

`scripts/prepare_v408_executable_slice.py` refuses preparation unless:

- the source closure passes and targets the exact public commit;
- every reached reviewed boundary appears exactly once in the adapter lock;
- every locked adapter exists with the exact byte length and SHA-256;
- the checked-out adapter inventory exactly matches the lock;
- the semantic contract contains the same boundary class set;
- the contract binds to the exact closure and adapter-lock hashes;
- every non-boundary public source matches its closure SHA-256;
- no extra or uncontracted adapter exists.

All validation completes before the generated source directory is deleted or written.

Canonical preparation evidence contains only upstream repository-relative paths or stable adapter
`root_id` plus root-relative path. Physical checkout roots are excluded.

## Reviewed adapters

The build uses 12 adapters for the 12 reached boundaries. They are not generic stubs. Each has a
specific execution mode and excluded behavior recorded in `adapter-contract.json`.

Notable behavior:

- import filtering always accepts and never infers manufacturer/instrument/source level;
- import processing is identity and import-time mass detection is disabled;
- centroid processing is a separate governed stage;
- null storage uses an in-memory read-only buffer; non-null storage is rejected;
- application precursor-object conversion throws explicitly if called;
- `MemoryMapStorage` is non-instantiable and supplies type identity only;
- no JavaFX, project, feature-list, IMS-frame, account, or cloud graph is introduced.

## Building

The generated source directory is produced from an exact upstream checkout and is not committed.
The CI build uses Java 21 and Gradle 8.10.2:

```bash
python scripts/audit_v408_source_closure.py \
  --checkout build/oracle/mzmine-v4.0.8 \
  --manifest datasets/parity/v408_source_closure_manifest.json \
  --output oracle/v408-executable/evidence/v408-source-closure.json \
  --require-commit

python scripts/prepare_v408_executable_slice.py \
  --checkout build/oracle/mzmine-v4.0.8 \
  --closure oracle/v408-executable/evidence/v408-source-closure.json \
  --adapter-lock oracle/v408-executable/adapter-lock.json \
  --adapter-contract oracle/v408-executable/adapter-contract.json \
  --output oracle/v408-executable/generated-src/main/java \
  --manifest-output oracle/v408-executable/evidence/v408-executable-source-manifest.json

gradle -p oracle/v408-executable clean compileJava --no-daemon
```

The Gradle build enables Java 21 preview for compilation and for future test/Java execution tasks.

## Validated result

Workflow run `31019779360` validated:

- 34 policy/provenance/preparation tests;
- exact 12-file adapter lock;
- exact public source closure;
- 48 classes represented by 47 Java source files;
- 12 adapters and 36 upstream public classes;
- no physical paths in canonical preparation evidence;
- successful Java 21 preview compilation.

This does not prove parser or centroid behavioral equivalence.

## Failure-first history

All failures and correction proposals are recorded before implementation:

- `docs/development/FAILURE_REGISTER.md` — original observations/proposals;
- `docs/development/failures/` — detailed individual failure records;
- `docs/development/FAILURE_RESOLUTION_LOG.md` — authoritative current resolution status.

The next gate executes the compiled parser against the governed public mzML and compares the
normalized import and centroid report with open-offline. Any failure or scientific difference must be
registered before correction.
