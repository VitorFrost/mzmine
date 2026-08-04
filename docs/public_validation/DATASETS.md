# Public validation corpus

This document defines the governed public-data corpus used to validate the open-offline fork before
private laboratory files are introduced.

The machine-readable sources of truth are:

- [`datasets/public_validation_manifest.json`](../../datasets/public_validation_manifest.json);
- [`datasets/public_blank_replicate_manifest.json`](../../datasets/public_blank_replicate_manifest.json);
- [`datasets/public_workflow_manifest.json`](../../datasets/public_workflow_manifest.json).

## Governance principles

1. Public availability alone is insufficient; reuse terms must be explicit.
2. Every enabled download is pinned by immutable record or commit, exact path, URL, byte size, and
   SHA-256.
3. Candidate records fail closed and cannot be downloaded until all required fields are frozen.
4. Large mzML/vendor bytes are stored only in a local or CI cache and are never committed to Git.
5. CI verifies files before Java receives their paths and deletes downloaded bytes after use.
6. Scientific assertions must be reproducible on Linux and Windows.
7. Thread-count determinism must be tested separately where a module supports concurrency.
8. Transport, TLS, license, hash, or provenance checks must never be weakened to unblock a dataset.
9. Vendor conversion is acceptable when converter/version/command/input provenance/output hash are
   recorded.
10. A compatibility dataset is not a mass-accuracy reference unless its depositor qualifies it for
    that purpose.

## Corpus status

| Tier | Purpose | Source | Current state |
|---:|---|---|---|
| 0 | mzML syntax, indexing, arrays, units, and malformed metadata | HUPO-PSI examples and other explicitly licensed fixtures | Expanded conformance set pending |
| 1 | Official mzmine behavior references | Workshop LC-MS, MSe, GC-TOF, and spectral-library fixtures | Reference inventory pending provenance review |
| 2 | Real LC-MS import and workflow determinism | Zenodo records `14001110` and `14000687` | Sample, replicate, blank, and settings frozen; public workflow validated |
| 2 reserve | Additional small real LC-MS | MassIVE `MSV000101091` | Provenance verified; hosted-runner passive FTPS blocked |
| 3 | Multi-replicate statistics and correlation groups | Additional public replicates/QCs/blanks | Needed for correlation, DPR, and analytical blank evidence |
| 4 | Reproducible Waters conversion/import | MassIVE `MSV000091372` primary; `MSV000097125` reserve | Exact subset/converter/output hashes pending |
| 4 native | Native Waters RAW reader | Separate future milestone | Outside current 4.0 LC-MS core acceptance gate |

## Frozen Zenodo LC-MS subset

Source record:

- repository: Zenodo;
- record: `14001110`;
- DOI: `10.5281/zenodo.14001110`;
- creator: Markus Aigensberger, BOKU University;
- license: Creative Commons Attribution 4.0 International.

Required attribution for reports derived from these files:

> Aigensberger, Markus. Raw data and supporting files for “Modular comparison of untargeted
> metabolomics processing steps”. Zenodo. DOI: 10.5281/zenodo.14001110. CC BY 4.0.

### Technical replicate 1

- file: `Banane_30ngmL_001.mzML`;
- byte size: `87,090,777`;
- repository MD5: `488ed6c48c0db085de966e96e74902cc`;
- SHA-256: `2eb189e193925983ddf8348a13a4c96fa7382e4e790665a204251eb036aea77d`;
- total scans: `5,221`;
- MS1/MS2: `4,081 / 1,140`;
- polarity: negative;
- representation: centroided;
- RT range: `0.77684957–27.61178589 min`;
- m/z range: `50.00162125–749.99395752`;
- total data points: `6,477,349`;
- maximum points in one scan: `3,030`.

### Technical replicate 2

- file: `Banane_30ngmL_002.mzML`;
- byte size: `86,540,418`;
- repository MD5: `93140cfe8da68877d9945c841250a5e6`;
- SHA-256: `350292e2497d9df1c1139f67dba27f333331c1ff6902e25b36d5fcba8c713214`;
- total scans: `5,232`;
- MS1/MS2: `4,085 / 1,147`;
- polarity: negative;
- representation: centroided;
- RT range: `0.77683449–27.61018562 min`;
- m/z range: `50.00003433–749.99383545`;
- total data points: `6,410,718`;
- maximum points in one scan: `2,703`.

### Analytical blank

- file: `blank_001.mzML`;
- byte size: `86,111,130`;
- repository MD5: `d322d4a5744a6caac63cce5744e71b45`;
- SHA-256: `3bd67cbe55e6e1d7dbe5073985a263621045eb5f51a4809070e1ae0019764aab`;
- total scans: `5,207`;
- MS1/MS2: `4,112 / 1,095`;
- polarity: negative;
- representation: centroided;
- RT range: `0.77685571–27.61053658 min`;
- m/z range: `50.00000000–749.99389648`;
- total data points: `6,401,519`;
- maximum points in one scan: `3,513`.

All three frozen mzML files contain no empty scans and report no zero/negative intensities through the
imported file model. Import elapsed time is recorded but is not a deterministic scientific assertion.

## Frozen published settings

The same public study provides the processing settings through Zenodo record `14000687`:

- file: `MZmine processing settings.xml`;
- license: CC BY 4.0;
- byte size: `17,443`;
- repository MD5: `f8cf3ca41f2702c1781c1a55742e374f`;
- SHA-256: `87845675b0cda32e7ce4c70a2a03f1a8ad465d18d018ede1db57fe61adee074c`;
- XML version: MZmine `3.4.27`;
- processing modules: `10`.

The XML is parsed with DTD and external-entity protections and is never trusted merely because it was
downloaded from a public repository.

## Completed public workflow gate

The blank plus technical-replicate workflow completed on Java 20/Ubuntu and Windows with identical
checkpoints and a byte-identical CSV:

- 285/503 smoothed chromatograms;
- 131/391 resolved features;
- 101/309 isotope patterns;
- 27/130 gap-filled features;
- 13 duplicate rows removed;
- 176 final aligned/exported rows;
- output size `22,422` bytes;
- SHA-256 `139a02d3305280937783acc1ac730fbb4c719c4e80730c2bf864f68806e89c11`.

See [`PUBLISHED_WORKFLOW_COMPATIBILITY.md`](PUBLISHED_WORKFLOW_COMPATIBILITY.md).

## Gaps in the current corpus

The current frozen real-data subset is strong for centroided negative untargeted LC-MS execution, but
it does not cover all 4.0 LC-MS core requirements.

Still required:

- non-indexed mzML;
- profile spectra;
- positive and mixed polarities;
- zlib/uncompressed and 32-/64-bit binary arrays;
- alternate RT units;
- richer precursor, isolation, charge, and collision metadata;
- malformed/incomplete mzML behavior;
- a public multi-replicate/QC set that produces meaningful correlations and quantitative statistics;
- a public MS2/library workflow;
- a public MSe workflow;
- a reproducible Waters conversion/import subset;
- small, medium, and large performance fixtures.

## MassIVE transport investigation

MassIVE `MSV000101091` remains a useful small-file candidate. Its CC0 record, exact path, official FTP
root, TLS leaf SAN, AIA issuer, and certificate chain were resolved and verified. Hosted GitHub
runners could not establish the passive FTPS data channel, so the entry remains disabled.

Acceptable promotion routes are:

- an official HTTPS file endpoint;
- a self-hosted runner with the required passive ports;
- an authorized mirror with matching immutable hash and documented provenance.

Disabling certificate verification or accepting an incomplete transfer is prohibited.

## Waters compatibility target

MassIVE `MSV000091372` remains the preferred first Waters set because it is described as a
negative-mode Waters Synapt XS FastDDA example deposited for testing under CC0 1.0.

Before enabling a Waters conversion/import fixture, freeze:

- exact source vendor-file path;
- source byte size and SHA-256;
- converter name, version, build, and license;
- complete conversion command and filters;
- converted mzML byte size and SHA-256;
- scan count, polarity, RT range, MS levels, precursor metadata, and spectrum representation;
- expected compatibility and repeatability assertions.

The dataset warning about calibration/accuracy means it is appropriate for software compatibility,
not for asserting mass-accuracy performance.

Native Waters RAW support remains separate. The first LC-MS 4.0 parity release may use reproducible
mzML conversion and must not imply that a native vendor reader is included.

## Official mzmine references

Public upstream integration references may be used after each raw/settings/library input has its own
license and provenance record. Priority references are:

- workshop LC-MS batch and expected output;
- MSe batch and expected output;
- GC-TOF batch and expected output if GC-MS later enters scope;
- small MassBank/MoNA spectral-library fixtures.

These references are behavioral targets, not automatic permission to redistribute all associated raw
files.

## Downloader usage

List entries:

```bash
python scripts/fetch_public_test_data.py --list
```

Validate manifests without downloading dataset bytes:

```bash
python scripts/fetch_public_test_data.py --validate
```

Fetch an approved entry:

```bash
python scripts/fetch_public_test_data.py \
  --dataset <dataset-id> \
  --output-dir build/public_validation_data
```

The downloader writes an atomic partial file, verifies size and SHA-256 before renaming, refuses URLs
with credentials or path traversal, and does not replace an existing valid file unless explicitly
requested.

## Promotion checklist

A candidate may be changed to `download_enabled: true` only after:

- [ ] immutable repository commit or data-record identifier;
- [ ] exact relative source path and official source page;
- [ ] explicit reuse/license evidence;
- [ ] expected byte size and SHA-256;
- [ ] instrument and acquisition metadata;
- [ ] local destination without traversal;
- [ ] expected scientific assertions;
- [ ] attribution text where required;
- [ ] one completed manual verification;
- [ ] CI bandwidth/runtime cost reviewed;
- [ ] cleanup policy verified;
- [ ] no weaker fallback transport or verification path exists.
