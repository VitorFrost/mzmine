# ROI-MCR bounded local-window fitting

Status: scientific core implemented and under CI validation; not production-active.

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

The first public fitting gate will characterize several limits rather than asserting that one value
is universally correct. A production limit must be exposed as a parameter and stored in provenance.

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

## Current safeguards

- deterministic variable selection;
- explicit matrix-width bound;
- non-negative MCR-ALS constraints inherited from the validated core;
- cancellation propagation;
- original-scale reconstruction metrics;
- focused tests for independent windows, deterministic retention and cancellation.

## Deliberate limitations

The first local fitter does not yet:

- run parameter perturbations for every window;
- create output features;
- reconcile duplicate components between overlapping windows;
- compare paired sample and blank components;
- register the module globally.

Those operations follow only after public fitting demonstrates acceptable runtime, rank behavior,
failure count and sensitivity to the ROI-variable limit.

## Acceptance gate for production integration

1. all synthetic local-fitting tests pass;
2. public sample and blank windows fit on Ubuntu and Windows;
3. scientific outputs are deterministic across operating systems;
4. failed-window count and variable truncation are explicitly reported;
5. runtime remains bounded for the complete chromatogram;
6. rank and component signatures remain acceptably stable over neighboring ROI limits;
7. reconciliation does not merge distinct coeluting components;
8. the full standard CI and untouched-MZmine regression remain green.
