# ROI-MCR full-range memory scaling

Status: bounded primitive builder implemented and under public-data validation.

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

This representation is acceptable for short selected regions but multiplies memory overhead when a
full LC-MS run contains millions of accepted centroid points.

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
updated incrementally, avoiding a second full traversal of boxed scan values.

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

The first public characterization uses a visible limit of 8,000 retained ROIs. This is not presented
as a universal analytical optimum. It is a safety boundary to measure the real candidate pressure
before selecting a production default.

## Audit statistics

Every full-range characterization records:

- total input centroid points;
- points above ROI noise;
- ROIs started;
- ROIs completing all scientific persistence/height criteria;
- ROIs retained;
- ROIs discarded by the retention limit;
- maximum simultaneously active ROIs;
- maximum number of points in one ROI;
- elapsed time for sample and blank.

A retention limit is therefore never silent. If valid candidates exceed the boundary, the report
will show the truncation explicitly and the production integration remains blocked.

## Diagnostic-first public test

The public test writes its JSON report before scientific assertions. Segmentation failures such as
no windows, low coverage, excessive fragmentation, under-supported windows, or truncation pressure
remain inspectable in CI artifacts instead of being lost behind a failed assertion.

## Tests

`RoiMcrMemorySafeBuilderTest` verifies:

1. trace-for-trace equivalence with the prototype builder when the limit is not reached;
2. deterministic retention of the strongest persistent ROIs when the limit is reached;
3. storage of a 10,000-point ROI without boxed point lists.

## Migration boundary

The existing production task continues using the previously validated prototype builder for short
selected ranges. Migration to the bounded builder requires:

1. unit-test equivalence;
2. successful full-range public characterization on Ubuntu and Windows;
3. reproducible retained/discarded counts;
4. acceptable window count and scan coverage;
5. explicit production parameters and provenance for the retention boundary.
