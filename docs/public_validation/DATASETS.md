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
| 2 | Small real LC-MS performance and determinism | Zenodo record `14001110` | First fixture frozen and validated |
| 2 reserve | Additional small real LC-MS | MassIVE `MSV000101091` | Provenance verified; hosted-runner FTPS transport blocked |
| 3 | Blank and technical-replicate behavior | Zenodo `14001110` blanks/replicates, then MassIVE `MSV000089848` | Minimal subset pending |
| 4 | Waters compatibility | MassIVE `MSV000091372` | CC0 verified; exact file list/hash pending |
| 4 reserve | Additional Waters lipidomics | MassIVE `MSV000097125` | Subset pending |

## First frozen real LC-MS fixture

The first approved public real-data file is:

- repository: Zenodo;
- record: `14001110`;
- DOI: `10.5281/zenodo.14001110`;
- creator: Markus Aigensberger, BOKU University;
- file: `Banane_30ngmL_001.mzML`;
- license: Creative Commons Attribution 4.0 International;
- byte size: `87,090,777`;
- repository MD5: `488ed6c48c0db085de966e96e74902cc`;
- frozen SHA-256: `2eb189e193925983ddf8348a13a4c96fa7382e4e790665a204251eb036aea77d`.

Required attribution for reports derived from this fixture:

> Aigensberger, Markus. Raw data and supporting files for “Modular comparison of untargeted
> metabolomics processing steps”. Zenodo. DOI: 10.5281/zenodo.14001110. CC BY 4.0.

The mzML is downloaded only through the approved manifest. CI verifies the exact size and SHA-256,
imports it, deletes the bytes, and retains only summary/test reports.

### Frozen import reference

| Property | Expected value |
|---|---:|
| Total scans | 5,221 |
| MS1 scans | 4,081 |
| MS2 scans | 1,140 |
| Polarity | Negative |
| Spectrum representation | Centroided |
| RT range | 0.77684957–27.61178589 min |
| m/z range | 50.00162125–749.99395752 |
| Total data points | 6,477,349 |
| Maximum points in one scan | 3,030 |
| Empty scans | None |
| Zero/negative intensities | None reported by the imported file model |

Import elapsed time is reported but is not a deterministic scientific assertion because it depends
on runner hardware and cache state.

## MassIVE transport investigation

MassIVE `MSV000101091` remains a useful small-file candidate. Its CC0 record, exact path, official FTP
root, TLS leaf SAN, AIA issuer, and certificate chain were resolved and verified. However, hosted
GitHub runners could not establish the passive FTPS data channel. The entry remains disabled rather
than bypassing certificate checks or silently accepting incomplete downloads.

It may be promoted later through:

- an official HTTPS file route;
- a self-hosted runner with permitted FTPS passive ports; or
- a separately mirrored copy with documented authorization and matching hash.

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

- [ ] immutable repository commit or data-repository record;
- [ ] exact relative source path;
- [ ] public source page;
- [ ] explicit reuse/license evidence;
- [ ] expected byte size;
- [ ] SHA-256 calculated from a completed download;
- [ ] instrument and acquisition metadata;
- [ ] local destination path without traversal;
- [ ] expected scientific assertions;
- [ ] attribution text where the license requires it;
- [ ] one manual verification of the downloaded file;
- [ ] CI cost reviewed before enabling automatic download.
