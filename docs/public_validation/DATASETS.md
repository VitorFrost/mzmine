# Public validation corpus

This directory defines the public-data gate that must be completed before private laboratory files
are used with the open-offline fork.

## Principles

1. Public availability alone is not sufficient. Reuse terms must be recorded.
2. Every downloadable file must be pinned by source, exact path, expected byte size, and SHA-256.
3. Candidate entries are fail-closed and cannot be downloaded by the repository script.
4. Large mzML and vendor files are stored in the local/CI cache, never in Git.
5. A public Waters subset must pass before any private Waters project is introduced.
6. Scientific results must remain deterministic on Linux and Windows and across supported thread
   counts.

The machine-readable source of truth is
[`datasets/public_validation_manifest.json`](../../datasets/public_validation_manifest.json).

## Corpus tiers

| Tier | Purpose | Initial source | State |
|---:|---|---|---|
| 0 | mzML syntax, indexing, arrays, and metadata | HUPO-PSI `tiny.pwiz.1.1.mzML` | License terms pending |
| 1 | Official behavioral references | Public MZmine workshop, MSe, and GC-TOF integration fixtures | Reference only |
| 2 | Small real LC-MS performance and determinism | MassIVE `MSV000101091` | Exact file selected; dataset license/hash pending |
| 3 | Blank and technical-replicate behavior | MassIVE `MSV000089848` | Minimal subset pending |
| 4 | Waters compatibility | MassIVE `MSV000091372` | CC0 verified; exact file list/hash pending |
| 4 reserve | Additional Waters lipidomics | MassIVE `MSV000097125` | Subset pending |

## First Waters target

MassIVE `MSV000091372` is the preferred first Waters set because its dataset description states that
it is a negative-mode Waters Synapt XS FastDDA example deposited for testing and records CC0 1.0.
Before enabling it, freeze:

- the exact vendor-file path or converted mzML path;
- byte size;
- SHA-256;
- whether conversion was produced by MassIVE or a pinned ProteoWizard version;
- scan count, polarity, RT range, MS levels, precursor metadata, and profile/centroid state.

The dataset warning about calibration/accuracy means it is suitable for software compatibility and
repeatability tests, not for asserting mass-accuracy performance.

## Official MZmine references

The public MZmine integration suite provides valuable behavioral targets even when the corresponding
raw data are not directly embedded in Git. Public references include:

- the two-file workshop LC-MS batch and expected CSV;
- the MSe batch and expected CSV;
- the GC-TOF batch and expected CSV;
- small MassBank/MoNA spectral-library fixtures.

These files are used to inventory public modules and output semantics. No raw file is assumed
redistributable until its own provenance is established.

## Downloader usage

List all entries:

```bash
python scripts/fetch_public_test_data.py --list
```

Validate the manifest without network access:

```bash
python scripts/fetch_public_test_data.py --validate
```

Fetch an approved entry after its manifest record is frozen:

```bash
python scripts/fetch_public_test_data.py \
  --dataset <dataset-id> \
  --output-dir build/public_validation_data
```

The downloader uses an atomic partial file, verifies size and SHA-256 before renaming, refuses
credentials in URLs, accepts only HTTPS or anonymous FTP, and does not replace an existing valid file
unless explicitly requested.

## Promotion checklist

A candidate may be changed to `download_enabled: true` only after all items below are complete:

- [ ] immutable repository commit or MassIVE accession;
- [ ] exact relative source path;
- [ ] public source page;
- [ ] explicit reuse/license evidence;
- [ ] expected byte size;
- [ ] SHA-256 calculated from a completed download;
- [ ] instrument and acquisition metadata;
- [ ] local destination path without traversal;
- [ ] expected scientific assertions;
- [ ] one manual verification of the downloaded file;
- [ ] CI cost reviewed before enabling automatic download.
