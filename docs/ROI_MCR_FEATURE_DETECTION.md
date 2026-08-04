# ROI-MCR LC-MS feature detection — experimental

## Objective

Detect ion features directly from centroid scan mass lists without first running a conventional
chromatogram builder and chromatographic resolver.

## Processing model

```text
centroid mass lists
→ scan-to-scan m/z regions of interest
→ time × ROI matrix
→ constrained MCR-ALS
→ resolved component profiles and original-scale spectral loadings
→ one MZmine ion feature per accepted component/ROI pair
```

For each local matrix, the bilinear model is `X = C S^T + E`.

Implemented constraints and diagnostics:

- non-negative concentration profiles and spectra;
- chromatographic smoothing;
- flexible unimodality;
- spectral sparsity;
- repeated initializations;
- incremental rank selection;
- restart stability;
- minimum component ion count and energy;
- rejection of almost duplicate temporal profiles.

## Weighting

The ROI weighting exponent controls how strongly lower-intensity ROIs influence model discovery:

- `0`: original intensity scale;
- `0.5`: Pareto-style weighting, the initial default;
- `1`: unit-maximum weighting, more sensitive but more prone to amplify weak/noisy ROIs.

Spectral loadings are transformed back to the original intensity scale before feature creation and
fit reporting.

## Scientific limits

- A mathematical component is not automatically one unique molecule.
- Exactly proportional coelution cannot be separated from MS1 time/mass data alone.
- Signals sharing the same low-resolution m/z ROI remain inseparable without orthogonal evidence.
- Ion-specific retention offsets can cause false component splitting.
- This first integration processes one raw file and one connected matrix at a time.
- Rank selection and thresholds require validation with known mixtures and public mzML files.

## Current integration stage

The first branch deliberately does not register the module in the global menu or batch allowlist.
It first validates the scientific core and direct creation of a `ModularFeatureList` against the
real open-offline fork APIs. Registration follows only after Linux and Windows CI pass.
