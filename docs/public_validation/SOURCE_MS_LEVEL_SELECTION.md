# Manual source MS-level selection

## Purpose

The differential and public-validation workflows require the user to select which mzML MS level is
treated as the **source-data stream** for mass detection and downstream feature construction.

The available choices are:

- **MS1** — use scans whose original mzML `ms level` is `1`;
- **MS2** — use scans whose original mzML `ms level` is `2`.

This is a manual processing choice. The software does not infer the selection from the manufacturer,
instrument model, number of quadrupoles, scan definition, precursor metadata, isolation window, or
collision-energy metadata.

## What the selection does

The selected value configures the existing MZmine `ScanSelection` used by mass detection and by
scientific stages that explicitly consume the selected source stream. Only scans with that original
MS level receive the selected processing in that run.

The choice is recorded in:

- execution metadata;
- the normalized settings mapping id;
- the canonical settings SHA-256;
- the generated differential report.

The current import/centroid mapping ids are:

- `mzml-import-centroid-source-ms1-v1`;
- `mzml-import-centroid-source-ms2-v1`.

Therefore, reports generated with different source selections cannot be compared as if they used the
same settings.

## What the selection does not do

The software does **not**:

- change the original `msLevel` stored in the mzML or imported scans;
- claim that an MS2-labeled scan is or is not a fragmented spectrum;
- infer instrument architecture or acquisition intent;
- combine MS1 and MS2 as one source stream in a single producer execution;
- interpret the selected level as proof of analytical identity.

The analyst remains responsible for choosing the level that corresponds to the intended source data
for the acquisition under review.

## User interfaces and CI behavior

### Command line

```bash
python scripts/generate_mzmine_stage_report.py \
  --input data/file.mzML \
  --output parity-artifacts/report.json \
  --producer-ref open-offline-main \
  --producer-commit <40-character-commit-sha> \
  --source-ms-level 1
```

Use `--source-ms-level 2` to select MS2 instead. The older `--survey-ms-level` spelling remains an
accepted compatibility alias, but new commands and documentation use `--source-ms-level`.

### Public parity stage-report workflow

When `Public parity stage report` is started manually, the user chooses `source_ms_level` from a
choice field containing `1` and `2`. One producer execution processes one explicitly selected source
level.

### Direct v4.0.8 oracle differential workflow

The completed v4.0.8 import/centroid gate validates **both** user-selectable paths. Push and
pull-request CI use two explicit jobs/matrix entries, one for MS1 and one for MS2. Each path executes
the oracle and candidate independently and retains its own settings identity, strict comparison, and
governed comparison.

This distinction is important: the software still never auto-selects or combines the source levels.
CI simply runs both explicit choices as separate governed validation cases.

The first completed result is documented in
`V408_IMPORT_MASS_DETECTION_PARITY.md`.

## Current downstream use

The first ADAP Chromatogram Builder parity gate is intentionally starting with explicit **MS1** source
data so that chromatogram-construction differences are isolated from MS2 association semantics.
This does not remove MS2 support or reinterpret MS2. The validated explicit MS2 import/centroid path
remains retained for later feature-to-MS2 association and other workflows where it is scientifically
appropriate.

## Scientific note shown to the user

> Select the MS level that should be used as the source-data stream. This option filters the original
> mzML levels for processing; it does not infer the instrument configuration, relabel scans, or
> determine whether MS2 spectra are fragmented.

This explanation is intentionally informative but non-prescriptive. The system records the user's
selection and does not substitute an automatic manufacturer-specific rule.
