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

The selected value configures the existing MZmine `ScanSelection` used by mass detection. Only scans
with that original MS level receive the selected mass-detection processing in that run.

The choice is recorded in:

- execution metadata;
- the normalized settings mapping id;
- the canonical settings SHA-256;
- the generated differential report.

The current mapping ids are:

- `mzml-import-centroid-source-ms1-v1`;
- `mzml-import-centroid-source-ms2-v1`.

Therefore, reports generated with different source selections cannot be compared as if they used the
same settings.

## What the selection does not do

The software does **not**:

- change the original `msLevel` stored in the mzML or imported scans;
- claim that an MS2-labeled scan is or is not a fragmented spectrum;
- infer instrument architecture or acquisition intent;
- combine MS1 and MS2 as one source stream in a single run;
- interpret the selected level as proof of analytical identity.

The analyst remains responsible for choosing the level that corresponds to the intended source data
for the acquisition under review.

## User interfaces

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

### GitHub Actions

When `Public parity stage report` is started manually, the user chooses `source_ms_level` from a
choice field containing `1` and `2`. Pull-request and integration runs remain frozen to MS1 unless a
separate governed workflow is explicitly configured.

## Scientific note shown to the user

> Select the MS level that should be used as the source-data stream. This option filters the original
> mzML levels for processing; it does not infer the instrument configuration, relabel scans, or
> determine whether MS2 spectra are fragmented.

This explanation is intentionally informative but non-prescriptive. The system records the user's
selection and does not substitute an automatic manufacturer-specific rule.
