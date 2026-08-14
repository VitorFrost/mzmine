# MZmine v4.0.8 governed feature-smoothing parity

## Purpose

This record documents direct behavioral equivalence between open-offline and the exact public MZmine v4.0.8 feature-smoothing task for the published untargeted LC-MS workflow path.

The governed chain is:

`mzML import -> MS1 centroid mass detection -> ADAP Chromatogram Builder -> smoothing`

The three upstream stages already have direct accepted v4.0.8 evidence. Smoothing is evaluated independently in fresh candidate/oracle JVMs so downstream evidence cannot depend on shared mutable object state.

## Frozen provenance

### Public oracle

- repository: `mzmine/mzmine`;
- tag: `v4.0.8`;
- commit: `8029f930d28c0447f0acf2bcabef0a79865ad434`.

### Governed input

- dataset: `zenodo-14001110-banane-30ngml-001`;
- file: `Banane_30ngmL_001.mzML`;
- SHA-256: `2eb189e193925983ddf8348a13a4c96fa7382e4e790665a204251eb036aea77d`;
- explicit source level: MS1;
- MS1 scan count: `4,081`;
- centroid detector noise: `0.0`.

### Published processing settings

- settings SHA-256: `87845675b0cda32e7ce4c70a2a03f1a8ad465d18d018ede1db57fe61adee074c`;
- smoothing algorithm: `io.github.mzmine.modules.dataprocessing.featdet_smoothing.savitzkygolay.SavitzkyGolaySmoothing`;
- algorithm ParameterSet: `io.github.mzmine.modules.dataprocessing.featdet_smoothing.savitzkygolay.SavitzkyGolayParameters`;
- original feature list handling: `KEEP`;
- suffix: `sm`.

The upstream ADAP input is reconstructed independently on both sides from the exact same bytes/settings and is re-hashed before smoothing.

## Scientific source audit

The complete governed headless/scientific smoothing implementation is byte-identical between current open-offline and the exact public v4.0.8 tree:

| Source | Git blob SHA-1 |
|---|---|
| `SmoothingAlgorithm.java` | `c26a032cb6c6ed0ef0fd35b1e2700b2c6b42328e` |
| `SmoothingModule.java` | `9d297cd70688b379fd691da87e88663a6f13b343` |
| `SmoothingParameters.java` | `e1519c426ac0f545c39442700b859b9d9d804cd7` |
| `SmoothingTask.java` | `324870813e7f0d58f8c77073bf667738195bce6f` |
| `ZeroHandlingType.java` | `76ae9b214b5c7cc78f53af6bd8075dc18a0110eb` |
| `loess/LoessSmoothing.java` | `4ded5a40677050840c0c407a941d55387b7c0d7c` |
| `loess/LoessSmoothingParameters.java` | `99ba5f3e9d7f4b6962d6b4665023c48595b1fe4b` |
| `savitzkygolay/SavitzkyGolayFilter.java` | `b3d3d722bd236b1b6deea812376133a14144d71d` |
| `savitzkygolay/SavitzkyGolayParameters.java` | `983c31afbefeb9a75f2153f64f649c979158e7a0` |
| `savitzkygolay/SavitzkyGolaySmoothing.java` | `ab5fb8e8d8cab874b49c168c67546736bf74de48` |

`SmoothingSetupDialog.java` is excluded from this headless scientific gate because it is interactive setup/preview UI. Its source differs, but it is not executed by the governed task path.

Source identity by itself was not used to claim equivalence. The task was still executed directly against the frozen oracle.

## Exact v4.0.8 task probe

The oracle producer uses the exact public v4.0.8 `SmoothingTask.java` after verifying:

- oracle commit;
- exact task Git blob;
- absence of an existing probe-class identifier.

The only transformation is a fail-closed mechanical Java identifier rename:

`SmoothingTask -> V408SmoothingTaskProbe`

No method body, parameter, algorithm, numeric operation, data-model behavior, or source dependency is rewritten.

## Independent execution design

Candidate and oracle run in separate Gradle test-worker JVMs.

Each side independently:

1. verifies the governed mzML and settings hashes;
2. imports the same raw data;
3. applies explicit MS1 centroid mass detection at noise `0.0`;
4. executes the published ADAP settings;
5. snapshots and hashes the complete normalized 517-feature ADAP input;
6. loads the exact published smoothing step;
7. runs either open-offline `SmoothingTask` or the mechanically renamed exact-v4 task;
8. snapshots the complete post-smoothing feature list;
9. exits before the other implementation executes.

This follows the lifecycle isolation established by the ADAP gate and prevents a parity verdict from depending on keeping both large scientific object graphs alive in one JVM.

## Normalized comparison contract

Each post-smoothing record contains:

- row index;
- row ID;
- m/z;
- RT;
- height;
- area;
- representative scan number;
- RT range;
- m/z range;
- intensity range;
- scan count;
- SHA-256 over the complete ordered feature series;
- deterministic first/middle/last sampled points.

The complete ordered record array is serialized deterministically and independently hashed.

No numerical tolerance is applied in this smoothing gate: the normalized candidate and oracle records must be exactly equal.

## Governed result

Dedicated workflow run:

`31814860815`

Artifact:

- artifact ID: `9224679187`;
- ZIP SHA-256: `6763461911a7a22d04754f29a85601e99a0ecc19ed721e888734cf9f569d7b2a`.

### Upstream-state equality

- candidate ADAP input feature count: `517`;
- oracle ADAP input feature count: `517`;
- candidate ADAP-input record SHA-256: `a6275eb15ce967e999374818803cc8b41c77a3e916d9f5196572caaf6e7d57fa`;
- oracle ADAP-input record SHA-256: identical.

### Smoothing output equality

- candidate output feature count: `517`;
- oracle output feature count: `517`;
- candidate normalized output SHA-256: `fcf45b949b1adca37bf3418366296525218c3117b692e363526c5adb9cdcb6c2`;
- oracle normalized output SHA-256: `fcf45b949b1adca37bf3418366296525218c3117b692e363526c5adb9cdcb6c2`;
- `records_equal: true`;
- `first_mismatch: null`.

The result therefore demonstrates exact equality of the governed normalized post-smoothing scientific state, including the complete feature-series hashes.

## Failure history

The first scientific differential run itself passed. No F-ID was allocated for scientific or runtime behavior.

After the successful run, the first repository update intended to freeze the evidence used a stale file SHA and GitHub rejected it with HTTP 409. That infrastructure/governance event is F-034. It changed no repository content and did not affect the accepted scientific artifact. The correction fetched fresh repository content/SHA before replaying the metadata promotion.

## What this proves

For the frozen public input, explicit MS1 centroid input, governed equivalent ADAP state, and exact published Savitzky-Golay smoothing settings, open-offline is behaviorally equivalent to the public MZmine v4.0.8 smoothing task for the normalized:

- feature membership/order;
- m/z and RT;
- height and area;
- representative scan;
- RT/m/z/intensity ranges;
- scan count;
- complete ordered feature-series content;
- deterministic sampled points.

The machine-readable LC-MS inventory may classify this governed smoothing scope as `Equivalent`.

## What this does not prove

This result does not establish parity for:

- Loess settings not exercised by the published workflow;
- other Savitzky-Golay parameter combinations;
- interactive smoothing preview/setup UI;
- mobility/IMS smoothing paths not exercised by this file;
- local-minimum feature resolving;
- isotope grouping or multi-file stages;
- smoothing performance/memory equivalence.

## Next scientific gate

The next direct stage is the **local-minimum feature resolver**.

It must start from the now-governed exact post-smoothing state, freeze the exact v4.0.8 resolver task/parameter mapping, compare resolved feature boundaries and complete series, and retain all differences before any repair.
