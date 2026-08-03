# Open-offline test fixtures

## `sciex_two_scan.mzML`

Purpose: minimal deterministic mzML import test containing one centroided MS1 scan and one
centroided MS2 scan.

Provenance:

- upstream repository: `mzmine/mzmine`;
- upstream commit inspected: `01c8cf3db35d83e8c652c0c3255a67f1189221de`;
- upstream path: `mzmine-community/src/test/resources/rawdatafiles/additional/sciex_no_mz_range_cv.mzML`;
- repository license: MIT;
- local change: renamed for the open-offline test suite and corrected the MS2 intensity-array unit
  annotation from `m/z` to `number of detector counts`; spectral binary arrays were not changed.

Expected contents:

| Scan | MS level | m/z values | Intensities |
|---|---:|---|---|
| 1 | 1 | 100, 200, 300 | 1000, 5000, 2000 |
| 2 | 2 | 105, 150 | 800, 3000 |

The fixture is intentionally tiny. It validates the parser and project import path only; it is not
sufficient to evaluate chromatogram building, feature resolving, alignment, or gap filling.
