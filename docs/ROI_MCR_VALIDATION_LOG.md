# ROI-MCR validation and decision log

This document records scientific and engineering gates for the experimental direct LC-MS ROI-MCR
feature detector. It is intentionally separate from the user-facing method description.

## Status vocabulary

- **implemented**: code exists and is covered by focused tests;
- **validated technically**: Linux/Windows compilation and regression gates passed;
- **validated on public data**: executed on the frozen SHA-verified mzML fixtures;
- **production-active**: used by `RoiMcrTask` to create output features;
- **analytically validated**: demonstrated with defined mixtures, acceptance limits and independent
  datasets. No part of this module has this status yet.

## Public fixtures

Frozen public pair from Zenodo record 14001110:

- sample: `Banane_30ngmL_002.mzML`;
- blank: `blank_001.mzML`.

Dataset bytes are downloaded and SHA-verified by CI and are not committed.

## Gate 1 — direct scan-level ROI-MCR

Status: **implemented**, **validated technically**, **validated on public data**,
**production-active within the isolated experimental module**.

Processing:

```text
centroid mass lists → persistent m/z ROIs → weighted constrained MCR-ALS
→ reconstructed original-scale ion features
```

Public dominant-window result:

| Metric | Sample | Blank |
|---|---:|---:|
| Ion-feature rows | 56 | 68 |
| Unique MCR components | 3 | 2 |
| Maximum reconstructed height | 3.54117024e8 | 2.0150114e7 |
| Summed area | 1.11195124e8 | 4.13548855e7 |

Using m/z ±0.02 and RT ±0.12 min:

- matched sample rows: 35;
- unmatched sample rows: 21;
- sample/blank maximum-height ratio: approximately 17.57;
- sample/blank summed-area ratio: approximately 2.69.

Decision: direct feature creation is technically operational, but feature count and binary
sample/blank matching are not sufficient evidence of chemical specificity.

## Gate 2 — comparison with conventional feature detection

Status: **validated on public data**.

Three negative-mode TIC regions near 2.005, 15.834 and 18.900 min were compared with:

```text
auto mass detection → ADAP chromatogram builder → minimum-search resolver
```

Totals:

| Metric | ROI-MCR | Conventional |
|---|---:|---:|
| Sample ion-feature rows | 233 | 3,039 |
| Rows matched across methods | 169 | — |
| Overall ROI-MCR match fraction | 72.5% | — |

Match fractions by region:

- 90.6%;
- 76.2%;
- 17.0%.

Decision: ROI-MCR is not a drop-in equivalent to conventional peak finding. Agreement is strongly
region-dependent and must be interpreted with component evidence, not total row counts.

## Gate 3 — weighting and noise sensitivity

Status: **validated on public data**.

Sweep:

- weighting exponents: 0, 0.5 and 1;
- ROI-noise multipliers: 0.5×, 1× and 2×;
- nine sample/blank pairs completed on Ubuntu and Windows.

Findings:

- original intensity was stable but collapsed the sample to one represented component and eight
  ions;
- Pareto produced three to four represented components and 51–74 ions;
- unit-maximum produced 34–35 ions at lower/default noise but collapsed to 13 ions and one component
  when noise doubled;
- larger apparent sample-only fractions under unit-maximum coincided with rank/feature instability.

Decision: Pareto remains the least extreme default, but no single setting is treated as proof of the
correct rank. Parameter perturbation must be reported.

## Gate 4 — parameter-perturbation robustness

Status: **implemented**, **validated technically**, **validated on public data**,
**production-active as an annotation layer**.

Reference models are compared with neighboring noise/seed/height and weighting configurations.
Component matching uses:

```text
45% chromatographic cosine + 45% m/z-aligned spectral cosine + 10% apex agreement
```

Dominant-window public classification:

| Confidence | Sample components | Blank components |
|---|---:|---:|
| stable | 2 | 2 |
| moderate | 0 | 0 |
| parameter-sensitive | 1 | 0 |

Mean metrics:

| Metric | Sample | Blank |
|---|---:|---:|
| Perturbation similarity | 0.846773 | 0.940148 |
| Perturbation support | 0.666667 | 0.875000 |
| Rank agreement | 1.000000 | 1.000000 |
| Restart stability | 1.000000 | 1.000000 |

Decision: stability is a component-quality dimension, not sample specificity. Stable components can
also be strong blank components. Parameter-sensitive components remain visible and explicitly
flagged rather than being silently filtered.

## Gate 5 — structured provenance

Status: **implemented**, **validated technically**, **validated on public data**.

The report schema was raised to version 2. ROI-MCR `CommentType` fields are parsed centrally and
statistics are deduplicated by component ID instead of ion-feature row.

Validation fails when:

- no component provenance is parsed;
- confidence counts do not reconcile with unique component count;
- fewer than two perturbation variants are represented;
- stability/support metrics fall outside [0,1].

Decision: component-level reporting is required before local-window integration.

## Gate 6 — automatic local windows

Status: **implemented as scientific core**, **validated technically in synthetic tests**,
**public full-range characterization in progress**, **not production-active**.

Implemented protections:

- relative ROI activity intervals;
- bounded scan gaps and window widths;
- minimum ROI support;
- broad ROI exclusion from clustering anchors;
- broad ROI reassignment to overlapping windows;
- square-root-compressed aggregate apex;
- bounded fallback when all ROIs are broad;
- candidate-window deduplication.

Public characterization records, separately for sample and blank:

- persistent ROI count;
- local-window count;
- scan coverage;
- overlapping scan assignments;
- minimum/maximum window width;
- maximum and mean ROI support;
- every window's scan and retention-time bounds.

Decision gate: do not activate local windows if segmentation is excessively fragmented, leaves major
regions uncovered, produces unbounded matrices, or differs unexpectedly across operating systems.

## Gate 7 — cross-window component reconciliation

Status: **implemented as scientific core**, **validated technically in synthetic tests**,
**not production-active**.

The same component from overlapping windows is compared on the global scan axis. Grouping uses a
complete-link rule to prevent transitive over-merging. Components from the same MCR window are never
merged.

Representative score:

```text
35% perturbation stability + 25% perturbation support
+ 20% restart stability + 20% original-scale explained signal
```

Decision gate: public local-window characterization must pass before fitting and reconciling all
public windows.

## Gate 8 — paired component-level blank evidence

Status: **scoring core implemented**, **not exposed as a paired-file module**,
**not production-active**.

Evidence combines component similarity with blank/sample height and area ratios. Initial labels:

- `blank-associated`;
- `sample-enriched`;
- `ambiguous`;
- `unmatched-needs-review`.

Decision: an unmatched sample component must not be called sample-specific automatically. Component
reconciliation across local windows is required before paired blank classification.

## Registration gate

The module remains absent from the global GUI and batch allowlist. Registration requires:

1. bounded public local-window characterization on Ubuntu and Windows;
2. public local-window fitting and reconciliation;
3. paired component-level blank evidence;
4. acceptable performance and deterministic output;
5. preserved standard CI and MZmine 3.9.0 baseline comparison;
6. explicit experimental labeling and documented analytical limitations.
