# ROI-MCR bounded local-window fitting

Status: scientific core implemented, technically validated, and validated on the frozen public
sample/blank pair; not production-active.

## Purpose

The public full-range characterization found local windows containing up to 1,509 persistent m/z
ROIs. Fitting every variable with the reference model and all perturbation variants would create
large, poorly bounded matrices and unnecessary runtime. `RoiMcrLocalFitter` introduces a separate,
auditable gate between window generation and production feature creation.

## Processing path

```text
persistent ROIs
→ local chromatographic windows
→ deterministic ROI quality ranking
→ bounded time × ROI matrix
→ weighted constrained MCR-ALS
→ original-scale spectra
→ component signatures
```

## ROI retention inside a window

When a window exceeds the configured variable limit, ROIs are ranked by:

```text
quality = ROI height × sqrt(maximum consecutive scans)
```

This is the same scientific priority used by the memory-safe whole-run builder:

- height retains analytically meaningful minor signals;
- persistence rewards chromatographic coherence;
- square-root duration prevents broad background traces from dominating linearly.

After selecting the highest-quality ROIs, variables are reordered deterministically by mean m/z and
ROI ID before matrix construction. The selected global ROI indices are retained in every fit result.

A production limit must be exposed as a parameter and stored in provenance.

## Scale handling

The local matrix preserves original intensities. Optional weighting is used only during factor
discovery:

```text
X_weighted[:,j] = X_original[:,j] / max(X_original[:,j])^weight
```

After fitting, spectral loadings are restored to the original intensity scale before calculating:

- explained signal on the original matrix;
- component height and area;
- spectral signatures;
- later sample/blank evidence.

## Audit output

Each `WindowFit` retains:

- global window ID and scan bounds;
- number of input and selected ROIs;
- selected global ROI indices;
- accepted rank;
- restart stability;
- weighted-model result;
- restored spectra;
- original-scale explained signal;
- component signatures;
- elapsed fitting time.

The aggregate summary records:

- successful and failed windows;
- total input and selected ROI assignments;
- maximum input and selected matrix widths;
- total elapsed time.

A failed local fit remains countable and cannot be silently interpreted as absence of analytes.

## Public bounded-width benchmark

Frozen public pair:

- sample: `Banane_30ngmL_002.mzML`;
- blank: `blank_001.mzML`.

Common negative-MS1 range:

- sample: 4,084 scans, 6,203 retained ROIs, 56 windows;
- blank: 4,111 scans, 4,446 retained ROIs, 52 windows;
- maximum input width: 1,509 sample ROIs and 1,432 blank ROIs;
- no ROI was discarded by the 8,000-ROI whole-run retention limit.

The first benchmark used Pareto weighting, maximum rank 4, two restarts and 120 ALS iterations. It
characterized 64, 128 and 256 selected ROIs per window without parameter perturbations.

### Ubuntu results

| Limit | Sample components | Blank components | Sample explained | Blank explained | Sample time | Blank time |
|---:|---:|---:|---:|---:|---:|---:|
| 64 | 74 | 56 | 0.750823 | 0.854950 | 1.084 s | 1.018 s |
| 128 | 76 | 56 | 0.751182 | 0.855225 | 1.643 s | 1.490 s |
| 256 | 75 | 56 | 0.751455 | 0.855273 | 2.972 s | 3.163 s |

All 56 sample and 52 blank windows fitted successfully at every limit. Mean restart stability was
at least 0.9896 in the sample and 0.9944 in the blank.

Windows and Ubuntu produced identical scientific outputs after excluding paths and elapsed times:

- identical ROI/window counts;
- identical rank distributions;
- identical component totals;
- identical original-scale explained signal;
- identical restart stability;
- zero failed windows.

### Limit decision

Increasing the sample limit from 64 to 256:

- increased selected ROI assignments from 3,584 to 14,153;
- increased Ubuntu fitting time from 1.084 s to 2.972 s;
- changed the component total only from 74 to 75;
- improved mean original-scale explained signal by only 0.000631.

The blank component total remained 56 at all limits. The increase from 64 to 256 improved blank
explained signal by only 0.000323.

Decision:

- **64 ROIs/window is the current production candidate** for the first integrated local path;
- 128 and 256 remain diagnostic options, not default choices;
- 64 is not claimed to be universally optimal;
- the selected limit must remain explicit in provenance;
- a later known-mixture benchmark must test whether the quality ranking drops diagnostically weak but
  chemically important minor ions.

## Current safeguards

- deterministic variable selection;
- explicit matrix-width bound;
- non-negative MCR-ALS constraints inherited from the validated core;
- cancellation propagation;
- original-scale reconstruction metrics;
- focused tests for independent windows, deterministic retention and cancellation;
- public whole-run fitting on Ubuntu and Windows;
- fail-closed reporting when more than 10% of local windows fail.

## Deliberate limitations

The local fitter does not yet:

- run parameter perturbations for every window;
- create output features in the production task;
- reconcile duplicate components between overlapping windows in public data;
- compare paired sample and blank components;
- register the module globally.

The public benchmark used only two restarts and 120 iterations to characterize matrix width. These
settings are not automatically the final production settings.

## Acceptance gate for production integration

1. all synthetic local-fitting tests pass — **passed**;
2. public sample and blank windows fit on Ubuntu and Windows — **passed**;
3. scientific outputs are deterministic across operating systems — **passed**;
4. failed-window count and variable truncation are explicitly reported — **passed**;
5. runtime remains bounded for the complete chromatogram — **passed for the reference pair**;
6. rank remains acceptably stable over neighboring ROI limits — **passed for component totals and
   rank distributions on the reference pair**;
7. reconciliation does not merge distinct coeluting components — pending public characterization;
8. the full standard CI and untouched-MZmine regression remain green — **passed**.
