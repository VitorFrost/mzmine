# ROI-MCR LC-MS feature detection — experimental

## Objective

Detect ion features directly from centroid scan mass lists without first running a conventional
chromatogram builder and chromatographic resolver.

The module is intended for difficult low-resolution LC-MS screening where several ions can form
partially overlapping chromatographic envelopes. It is not a compound-identification module and is
not analytically validated.

## Processing model

```text
centroid mass lists
→ scan-to-scan m/z regions of interest
→ time × ROI matrix
→ constrained MCR-ALS
→ resolved component profiles and original-scale spectral loadings
→ parameter-perturbation assessment
→ one MZmine ion feature per accepted component/ROI pair
```

For each modeled matrix, the bilinear model is `X = C S^T + E`.

Implemented constraints and diagnostics:

- non-negative concentration profiles and spectra;
- chromatographic smoothing;
- flexible unimodality;
- spectral sparsity;
- repeated initializations;
- incremental rank selection;
- restart stability;
- minimum component ion count and energy;
- rejection of almost duplicate temporal profiles;
- component stability under neighboring ROI-noise and weighting configurations.

## Two distinct stability concepts

### Restart stability

Restart stability compares repeated MCR-ALS initializations while all analytical parameters remain
fixed. It detects rotational ambiguity and dependence on the initial spectra.

A high restart-stability value does **not** prove that the rank or component is robust to changes in
noise threshold, ROI composition, or weighting.

### Parameter-perturbation stability

Each reference analysis is now compared with neighboring models:

1. ROI noise, seed intensity, and minimum reconstructed height divided by the configured noise
   factor;
2. the same values multiplied by the configured noise factor;
3. ROI-weighting exponent reduced by the configured step, when possible;
4. ROI-weighting exponent increased by the configured step, when possible.

The default neighborhood is:

```text
noise factor: 1.5
weighting step: 0.1
```

For Pareto weighting (`0.5`), this corresponds to noise conditions of approximately `0.667×` and
`1.5×`, plus weighting exponents `0.4` and `0.6`.

A component signature contains:

- a chromatographic profile resampled to 101 normalized points;
- relative apex position;
- original-scale spectral loadings;
- the m/z value of every contributing ROI;
- reconstructed component height and area.

Components are matched one-to-one between models using a combined score:

```text
45% chromatographic-shape cosine
45% m/z-aligned spectral cosine
10% relative-apex agreement
```

Spectral cosine is calculated after aligning ions within the configured absolute/ppm m/z tolerance.
Unmatched ions remain in the spectral norms and therefore penalize similarity instead of being
silently discarded.

## Robustness output

Every generated feature row stores the following provenance in `CommentType`:

- `component` and selected `rank`;
- source ROI identifier;
- component-assignment fraction;
- `restart_stability`;
- `perturbation_stability`;
- `perturbation_support`;
- number of attempted perturbation variants;
- `rank_agreement`;
- `confidence`;
- explained signal on the original intensity scale.

Current confidence classes are:

| Class | Meaning |
|---|---|
| `stable` | Component similarity and support satisfy the configured limits. |
| `moderate` | Component is reproduced by part of the neighborhood but does not meet the stable criterion. |
| `parameter-sensitive` | Component disappears, changes substantially, or depends strongly on the selected parameters. |

These classes are annotations only. The current experimental version does not remove
parameter-sensitive components, because the rejection thresholds still require validation with
known mixtures and independent public datasets.

## Weighting

The ROI-weighting exponent controls how strongly lower-intensity ROIs influence model discovery:

- `0`: original intensity scale;
- `0.5`: Pareto-style weighting, the current default;
- `1`: unit-maximum weighting, more sensitive but more prone to amplify weak/noisy ROIs.

Spectral loadings are transformed back to the original intensity scale before feature creation,
component signatures, and fit reporting.

## Blank-evidence foundation

The component-signature implementation also provides a continuous blank-evidence calculation for
a future paired sample/blank workflow. It combines:

- chromatographic and spectral component similarity;
- blank/sample reconstructed-height ratio;
- blank/sample reconstructed-area ratio.

Initial classifications are:

- `blank-associated`;
- `sample-enriched`;
- `ambiguous`;
- `unmatched-needs-review`.

An unmatched component is deliberately **not** classified automatically as a confirmed
sample-specific analyte. Public validation showed that m/z/RT non-matching can coexist with almost
identical total sample and blank areas. Module-level blank pairing will be implemented only after
component reconciliation across local windows is available.

## Public-data findings recorded on PR #26

The frozen, SHA-verified pair `Banane_30ngmL_002.mzML` / `blank_001.mzML` was processed on Ubuntu and
Windows.

Three independent negative-mode regions near 2.005, 15.834, and 18.900 min produced:

- 233 ROI-MCR ion-feature rows;
- 3,039 ADAP-builder/minimum-search-resolver rows;
- 169 ROI-MCR rows matching a conventional feature (`72.5%` overall);
- cross-method match fractions of `90.6%`, `76.2%`, and `17.0%` by region.

A separate 3 × 3 weighting/noise sweep demonstrated that selected rank and ion sets were materially
parameter-sensitive. This result is the reason perturbation stability is now an explicit output
rather than an undocumented tuning decision.

## Scientific limits

- A mathematical component is not automatically one unique molecule.
- Exactly proportional coelution cannot be separated from MS1 time/mass data alone.
- Signals sharing the same low-resolution m/z ROI remain inseparable without orthogonal evidence.
- Ion-specific retention offsets can cause false component splitting.
- The current task still models one selected connected scan matrix at a time.
- Local-window generation and reconciliation are not yet integrated into the production task.
- Blank scoring is implemented as a reusable scientific core but not yet exposed as paired-file
  processing.
- Parameter confidence is not equivalent to analytical validation, identification confidence, or a
  regulatory acceptance criterion.

## Current integration stage

The module remains isolated from the global menu and batch allowlist. The required sequence is:

1. compile and unit-test the scientific core;
2. execute public sample/blank validation on Linux and Windows;
3. validate perturbation-stability behavior;
4. add automatic local chromatographic windows;
5. reconcile duplicate components between overlapping windows;
6. integrate paired blank evidence;
7. register the module only after the preceding gates pass.
