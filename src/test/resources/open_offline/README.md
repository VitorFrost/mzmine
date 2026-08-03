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

## `synthetic_lcms_profile.csv`

Purpose: human-readable source for a deterministic multi-scan LC-MS fixture. The integration test
converts this profile to mzML at runtime and then executes import, centroid mass detection, and the
MZmine 3.9 ADAP chromatogram builder.

The four fixed m/z channels are:

| m/z | Role | Expected behavior |
|---:|---|---|
| 75 | low background, intensity 25 | removed by centroid mass detection at noise level 100 |
| 150 | chromatographic signal A | retained and connected into a feature with apex at 0.6 min and height 9000 |
| 300 | chromatographic signal B | retained and connected into a feature with apex at 1.0 min and height 7000 |
| 500 | constant control ion, intensity 150 | retained by mass detection but rejected as a chromatogram because the ADAP start height is 500 |

Processing parameters fixed by the test:

- centroid mass-detection noise level: `100`;
- ADAP minimum consecutive scans: `3`;
- ADAP minimum intensity for consecutive scans: `500`;
- ADAP minimum absolute height: `500`;
- scan-to-scan tolerance: `0.005 m/z` or `10 ppm`;
- scan range: 15 MS1 scans from `0.1` to `1.5 min`.

Expected feature result:

| Feature | Average m/z | Apex RT | Height |
|---|---:|---:|---:|
| A | 150.0000 | 0.6 min | 9000 |
| B | 300.0000 | 1.0 min | 7000 |

The generated mzML is written to a JUnit temporary directory. No download, vendor SDK, network
lookup, login, or proprietary component is required.
