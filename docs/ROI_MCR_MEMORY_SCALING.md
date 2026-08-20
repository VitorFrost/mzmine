# ROI-MCR full-range memory scaling

Status: **bounded primitive builder validated on the frozen public sample/blank pair in Ubuntu and Windows; not yet production-active**.

## Incident

The first full-common-range public characterization attempted to track all negative-mode centroid
signals with the prototype `RoiMcrCore.RoiBuilder`.

The test worker failed with:

```text
java.lang.OutOfMemoryError: Java heap space
at java.lang.Integer.valueOf
at RoiMcrCore$RoiBuilder$MutableRoi.add
```

The failure occurred during ROI construction before local-window generation. It was not a failure of
MCR-ALS, rank selection, or the window-clustering algorithm.

The prototype stores every ROI point in three boxed lists:

- `List<Integer>` scan indices;
- `List<Double>` m/z values;
- `List<Double>` intensities.

This representation remains acceptable for short selected regions but multiplies memory overhead
when a full LC-MS run contains millions of accepted centroid points.

## Corrective design

Implementation: `RoiMcrMemorySafeBuilder`.

### Primitive point storage

Every active trace uses dynamically growing primitive arrays:

```text
int[] scan indices
double[] m/z values
double[] intensities
```

Only the occupied prefix is copied when the ROI is finalized. Consecutive-scan persistence is
updated incrementally, avoiding a second traversal of boxed scan values.

### Primitive scan-point ordering

Accepted points are ordered by descending intensity using a primitive `int[]` index array and an
in-place quicksort. The original intensity-priority matching rule is preserved without allocating an
`Integer` object for every spectral point.

### Bounded completed-ROI retention

Completed valid traces are kept in a bounded priority queue. When the configured limit is reached,
the builder retains the higher-quality trace according to:

```text
quality = ROI height × sqrt(maximum consecutive scans)
```

Rationale:

- ROI height preserves strong minor species;
- persistence rewards coherent chromatographic evidence;
- square-root persistence prevents broad background traces from dominating linearly by duration.

The first public characterization used a visible limit of 8,000 retained ROIs. This is a safety
boundary, not a universal analytical optimum.

## Public full-range result

Frozen pair:

- sample: `Banane_30ngmL_002.mzML`;
- blank: `blank_001.mzML`.

| Metric | Sample | Blank |
|---|---:|---:|
| Input centroid points | 6,332,682 | 6,365,258 |
| Points above ROI noise | 3,523,379 | 3,583,060 |
| ROIs started | 135,085 | 129,001 |
| Valid completed ROIs | 6,203 | 4,446 |
| Retained ROIs | 6,203 | 4,446 |
| Discarded by limit | 0 | 0 |
| Maximum active ROIs | 1,047 | 984 |
| Maximum points in one ROI | 4,042 | 3,983 |

The retention boundary did not alter this public pair because all valid completed ROIs fit below the
8,000-ROI limit.

### Runtime

| Platform | Sample | Blank |
|---|---:|---:|
| Ubuntu | 4.063 s | 3.854 s |
| Windows | 5.631 s | 5.490 s |

The scientific JSON output was identical across operating systems after excluding paths and elapsed
times.

## Audit statistics

Every full-range characterization records:

- total input centroid points;
- points above ROI noise;
- ROIs started;
- ROIs completing all persistence/height criteria;
- ROIs retained;
- ROIs discarded by the retention limit;
- maximum simultaneously active ROIs;
- maximum number of points in one ROI;
- elapsed time for sample and blank.

A retention limit is therefore never silent. If valid candidates exceed the boundary in another
dataset, the report exposes that truncation and production integration remains blocked.

## Tests

`RoiMcrMemorySafeBuilderTest` verifies:

1. trace-for-trace equivalence with the prototype builder when the limit is not reached;
2. deterministic retention of the strongest persistent ROIs when the limit is reached;
3. storage of a 10,000-point ROI without boxed point lists.

The standard CI, headless startup, deterministic batch regression, MZmine 3.9.0 comparison, and
dedicated public mzML jobs passed on Ubuntu and Windows.

## Migration boundary

The existing production task continues using the previously validated prototype builder for short
selected ranges. Migration to the bounded builder requires:

1. an explicit production parameter for the retention boundary;
2. provenance reporting whenever completed candidates exceed that boundary;
3. public local-window MCR fitting and reconciliation;
4. acceptable memory and runtime when the largest public windows contain more than 1,400 ROIs;
5. deterministic output on Ubuntu and Windows.
