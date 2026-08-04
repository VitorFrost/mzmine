# Published MZmine workflow compatibility

## Source and purpose

This benchmark uses the public file `MZmine processing settings.xml` from Zenodo record `14000687`,
licensed CC BY 4.0. The downloaded XML is not committed. Its frozen verification record is:

- byte size: `17,443`;
- repository MD5: `f8cf3ca41f2702c1781c1a55742e374f`;
- SHA-256: `87845675b0cda32e7ce4c70a2a03f1a8ad465d18d018ede1db57fe61adee074c`;
- XML root: `batch`;
- XML `mzmine_version`: `3.4.27`;
- ordered steps: `10`;
- parameter elements: `179`;
- unique parameter names: `122`.

The associated article describes MZmine 3.4.16, while the actual published XML identifies itself as
MZmine 3.4.27. For reproducibility, the XML metadata and the exact frozen file hash are authoritative
for this compatibility exercise.

This workflow is a real scientific benchmark and a public parameter source. It is not, by itself,
proof of MZmine 4.0 parity. Parity with the 4.0 line remains a behavior-by-behavior comparison against
public 4.0 source and the governed validation corpus.

## Compatibility terminology

- **Equivalent for validated scope** — the source exists and its relevant behavior already has a
  deterministic test in the open-offline branch.
- **Source present, validation pending** — the exact public module class exists, but this workflow has
  not yet been executed and frozen on the public real-data subset.
- **Adapted** — the analytical purpose is preserved through a documented open implementation or
  parameter/interface mapping.
- **Not implemented** — the public capability is relevant but absent.
- **Out of scope** — proprietary account/license/network infrastructure or functionality outside the
  analytical objective.

## Ordered module matrix

| # | Published module | Parameter version | Current state | Required next evidence |
|---:|---|---:|---|---|
| 1 | Mass detection | 1 | Equivalent for validated scope | Freeze real-data mass-list statistics |
| 2 | ADAP chromatogram builder | 1 | Equivalent for validated scope | Freeze real-data feature count and selected feature values |
| 3 | Smoothing | 1 | Source present, validation pending | Verify published smoothing algorithm and parameter mapping |
| 4 | Local-minimum feature resolver | 2 | Equivalent for validated scope | Freeze real-data resolved feature count |
| 5 | Isotopic peaks finder | 1 | Source present, validation pending | Verify isotope grouping count, charge and isotope relationships |
| 6 | Feature-list rows filter | 2 | Source present, validation pending | Map every enabled filter and freeze retained/removed row counts |
| 7 | Join aligner | 1 | Equivalent for validated scope | Validate blank plus two technical replicates |
| 8 | Peak finder, multithreaded | 1 | Source present, validation pending | Compare with validated single-thread gap filling and test determinism across thread counts |
| 9 | Duplicate peak filter | 1 | Source present, validation pending | Freeze duplicate groups and retained representative rows |
| 10 | Correlation grouping (metaCorrelate) | 2 | Source present, validation pending | Freeze correlation groups and verify deterministic grouping |

All ten module source classes are present in the open-offline MIT tree. Presence alone does not
promote a module to equivalent status.

## Already validated foundations

The open-offline branch already has deterministic tests for:

- centroid mass detection;
- ADAP chromatogram building;
- local-minimum resolving;
- join alignment;
- single-thread peak-finder gap filling;
- legacy CSV export;
- direct-module and XML-batch execution;
- equality with untouched MZmine 3.9.0 for the frozen synthetic baseline;
- import of the real public Zenodo mzML on Linux and Windows.

## Next public subset

Use the same public study to reduce instrument and matrix variability:

1. `blank_001.mzML`;
2. `Banane_30ngmL_001.mzML` — already frozen;
3. `Banane_30ngmL_002.mzML`.

The minimal subset is intended to validate module behavior, not reproduce the complete-study feature
count. The complete published workflow result may be used only after all required public input files
and metadata are frozen.

## Execution phases

### Phase A — configuration compatibility

- validate exact XML structure and hash;
- confirm every module source class;
- compare each published parameter name with the local parameter set;
- classify values as direct, renamed, transformed, deprecated, or unsupported;
- generate an adapted batch without executing unsupported settings silently.

### Phase B — single-sample processing

On `Banane_30ngmL_001.mzML`, freeze:

- mass-list point counts;
- ADAP chromatogram count;
- smoothed feature count;
- resolved feature count;
- isotope relationships;
- runtime and peak memory;
- selected feature m/z, RT, height and area.

### Phase C — blank and replicate processing

On blank plus two technical replicates, freeze:

- aligned row count;
- replicate matching rate;
- gap-filled values and statuses;
- blank/sample intensity relationships;
- duplicate filtering decisions;
- correlation-group count;
- deterministic export.

### Phase D — MZmine 4.0 parity classification

For each public 4.0 capability used by the workflow, record:

- public 4.0 source commit/tag;
- open-offline implementation source;
- parameter mapping;
- frozen public inputs;
- expected outputs and tolerances;
- result: equivalent, adapted, not implemented, or out of scope.

No proprietary `io.mzio` binary, authentication service, licensing behavior or decompiled source is
part of this matrix.
