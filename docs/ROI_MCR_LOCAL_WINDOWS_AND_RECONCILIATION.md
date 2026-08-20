# ROI-MCR local windows and component reconciliation

Status: scientific core implemented and under CI validation; not yet wired into production feature
creation.

## Why the selected scan range must be divided

Fitting one MCR model across an entire LC-MS run makes the selected rank depend on unrelated regions,
increases computational cost, and allows broad background ions to couple chromatographically
independent peaks. Public validation also showed that different TIC regions behave very differently
under identical parameters.

The intended production path is therefore:

```text
full selected scan range
→ persistent m/z ROIs
→ bounded local windows
→ one constrained MCR-ALS model per window
→ perturbation stability per local component
→ reconciliation of components found in overlapping windows
→ feature creation from reconciled representatives
```

## Local-window generator

Implementation: `RoiMcrWindows`.

### ROI activity interval

For each ROI, the active interval is the first-to-last scan whose intensity is at least a configured
fraction of the ROI height. Using a relative interval instead of the full trace reduces artificial
connections caused by low-intensity tails and allowed missing scans.

### Anchor clustering

Intervals are sorted by start scan and joined when:

1. the next interval is within the maximum scan gap; and
2. the union does not exceed the maximum window width.

Each cluster is padded and expanded to a minimum scan count when necessary.

### Broad-ROI protection

An ROI whose active interval already exceeds the maximum window width is not used as a clustering
anchor. It may be assigned to every local window it overlaps, but cannot connect otherwise
independent regions.

This distinction is important for background, solvent-envelope, polymeric, or drifting signals that
span large parts of a chromatogram.

### Robust local apex

The window apex maximizes:

```text
sum over assigned ROIs of sqrt(intensity)
```

Square-root compression preserves positive-signal ordering but reduces domination by a single very
intense ion.

### Fallback behavior

If all surviving ROIs are broader than the maximum window, bounded apex-centered candidates are
created. The algorithm does not silently fall back to a whole-run matrix.

### Candidate-window deduplication

Two candidate windows are merged when both conditions are met:

- at least 80% overlap relative to the shorter scan range;
- at least 80% overlap relative to the smaller ROI set.

The merged window is rebound around the stronger aggregate apex and remains subject to the maximum
scan count.

## Cross-window component reconciliation

Implementation: `RoiMcrReconciliation`.

### Candidate evidence

Every fitted local component will provide:

- window and local-component IDs;
- global start, end, and apex scans;
- normalized chromatographic profile;
- original-scale m/z/loadings spectrum;
- perturbation stability and support;
- restart stability;
- explained signal on the original scale.

### Global-axis temporal comparison

The same chemical peak occurs at different relative positions when two overlapping windows start at
different scans. Therefore reconciliation does not compare relative profile positions directly.

Each normalized profile is interpolated back onto its candidate's global scan interval. Temporal
cosine is then calculated over the union of the two global intervals.

### Spectral comparison

Ions are aligned using the configured absolute/ppm m/z tolerance. Matched intensities contribute to
the dot product, while all original loadings remain in the norms. Unmatched ions therefore penalize
similarity.

### Combined reconciliation similarity

```text
45% global-axis chromatographic cosine
45% m/z-aligned spectral cosine
10% global-apex agreement
```

Candidates are eligible for comparison only when:

- they come from different windows;
- their scan intervals overlap; and
- their apex distance is within the configured maximum.

### Complete-link grouping

A new candidate joins an existing reconciliation group only when it meets the similarity threshold
against every member of that group. This prevents transitive over-merging.

Components generated within the same MCR window are never merged by reconciliation, even when their
signatures are similar. Such duplication must instead be controlled by MCR rank selection and the
maximum temporal-cosine constraint.

### Representative selection

The representative candidate maximizes:

```text
35% perturbation stability
25% perturbation support
20% restart stability
20% original-scale explained signal
```

The group retains all member references and the minimum similarity between the representative and
its members for auditability.

## Tests

`RoiMcrWindowsTest` covers:

- separated regions despite one broad background ROI;
- merging nearby coeluting intervals;
- bounded fallback with only broad ROIs;
- rejection of under-supported windows.

`RoiMcrReconciliationTest` covers:

- the same component recovered in overlapping windows;
- distinct spectra at nearly the same retention time;
- the same spectrum in separated chromatographic regions;
- prohibition against merging components from the same window.

## Integration boundary

The production `RoiMcrTask` still creates one model from the user-selected scan range. The local
window and reconciliation cores will be connected only after:

1. Linux and Windows unit tests pass;
2. public sample/blank reports expose component-level robustness correctly;
3. local windows are characterized on the frozen public files;
4. reconciliation is shown not to collapse genuinely distinct coeluting components.

Until then, these classes are testable scientific infrastructure, not hidden production behavior.
