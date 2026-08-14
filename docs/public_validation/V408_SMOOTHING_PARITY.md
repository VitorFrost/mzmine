# MZmine v4.0.8 governed feature-smoothing parity

## Purpose

This record documents direct behavioral equivalence between open-offline and the exact public MZmine v4.0.8 feature-smoothing task for the published untargeted LC-MS workflow path.

The governed chain is:

`mzML import -> MS1 centroid mass detection -> ADAP Chromatogram Builder -> smoothing`

The three upstream stages already have direct accepted v4.0.8 evidence. Smoothing is evaluated independently in fresh candidate/oracle JVMs so downstream evidence cannot depend on shared mutable object state.

## Evidence source-of-truth rule

The primary evidence for this gate is the retained machine-readable GitHub Actions artifact from run `31814860815`, artifact ID `9224679187`. Its extracted immutable values are frozen in:

`datasets/parity/v408_smoothing_accepted_evidence.json`

Human-maintained prose is secondary to that machine-readable record. F-046 documents why this precedence is necessary: an earlier version of this document transcribed two hashes incorrectly after the successful run even though the candidate/oracle scientific reports were exactly equal.

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
- ParameterSet version: `1`;
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

Accepted candidate commit:

`56393a7571681bd1fcf5397eb48a1634398ff9c7`

Artifact:

- artifact ID: `9224679187`;
- artifact name: `v408-smoothing-direct-differential-31814860815`;
- ZIP SHA-256 re-derived from the retained downloaded artifact: `676191a22491899832d7426eef2ed2ca1b4ae29fd03c88c38a8a7d7b05fe98c9`.

### Upstream-state equality

- candidate ADAP input feature count: `517`;
- oracle ADAP input feature count: `517`;
- candidate ADAP-input record SHA-256: `a6275eb15ce967e999374818803cc8b41c77a3e916d9f5196572caaf6e7d57fa`;
- oracle ADAP-input record SHA-256: identical.

### Smoothing output equality

- candidate output feature count: `517`;
- oracle output feature count: `517`;
- candidate normalized output SHA-256: `1c8fb4b82356facdbff4b88174990dcdf307b665df5ddddd30363cde471ec971`;
- oracle normalized output SHA-256: `1c8fb4b82356facdbff4b88174990dcdf307b665df5ddddd30363cde471ec971`;
- `records_equal: true`;
- `first_mismatch: null`;
- numerical tolerance applied: `false`.

The result therefore demonstrates exact equality of the governed normalized post-smoothing scientific state, including the complete feature-series hashes.

## Failure history and F-046 erratum

The scientific differential itself passed. No scientific mismatch was observed.

F-034 records the historical smoothing source-closure/governance work, including a duplicate historical label for a stale optimistic-lock write. Neither changed the scientific output.

F-046 records a separate and later governance defect discovered while constructing the local-minimum resolver gate. The retained run artifact was re-inspected and showed that the earlier post-run prose had transcribed:

- the output-record SHA as `fcf45b949b1adca37bf3418366296525218c3117b692e363526c5adb9cdcb6c2`, although both artifact side reports contain `1c8fb4b82356facdbff4b88174990dcdf307b665df5ddddd30363cde471ec971`;
- the artifact ZIP SHA as `6763461911a7a22d04754f29a85601e99a0ecc19ed721e888734cf9f569d7b2a`, although the retained ZIP hashes to `676191a22491899832d7426eef2ed2ca1b4ae29fd03c88c38a8a7d7b05fe98c9`.

No scientific file changed between accepted-run commit `56393a757...` and the later smoothing head that introduced the prose record. The correction therefore changes governance only; it does not rerun, repair, or reinterpret the smoothing algorithm.

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

It must start from the corrected artifact-derived post-smoothing state: 517 records with normalized SHA-256 `1c8fb4b82356facdbff4b88174990dcdf307b665df5ddddd30363cde471ec971`. Resolver candidate/oracle execution is not considered to have started until both sides independently reproduce that precondition.
