# MZmine v4.0.8 ADAP Chromatogram Builder — source and parameter mapping

## Status

**Phase A source/parameter mapping in progress. Direct behavioral parity is not yet established.**

This record belongs to Milestone 4 issue #50 and freezes the source identities and known semantic differences that must be carried into the first ADAP differential execution.

It is deliberately separate from ROI-MCR. ROI-MCR is a fork-local experimental LC-MS method and is not evidence for MZmine v4.0.8 parity.

## Frozen references

| Role | Reference |
|---|---|
| Public 3.9 baseline | `mzmine/mzmine` tag `v3.9.0`, commit `2ac3ce3b25190430f3ae0e02c28ddbb94bc248ed` |
| Public v4.0.8 oracle | `mzmine/mzmine` tag `v4.0.8`, commit `8029f930d28c0447f0acf2bcabef0a79865ad434` |
| Open-offline integration base for this phase | `VitorFrost/mzmine` `open-offline-main`, merge commit `7840a8755e2924a3d73bb0f6a893fb53baf0b20f` |
| Governed dataset | `zenodo-14001110-banane-30ngml-001` |
| mzML SHA-256 | `2eb189e193925983ddf8348a13a4c96fa7382e4e790665a204251eb036aea77d` |
| Initial source-data level | explicit MS1 |

The import and centroid stages preceding ADAP already have direct v4.0.8 evidence. They must not be weakened or modified merely to make ADAP pass.

## Correct parameter class — F-021

The pre-Phase-A machine-readable inventory incorrectly named:

`io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderParameters`

No such parameter class is used by the inspected modules.

Both the public v4.0.8 module and the open-offline module return:

`ADAPChromatogramBuilderParameters.class`

The correct fully qualified parameter class is:

`io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ADAPChromatogramBuilderParameters`

F-021 was registered in issue #50 before correction.

## ADAP-local source identities

The open-offline ADAP implementation remains byte-identical to the public v3.9.0 source for the five ADAP-local files listed below. This means there are no fork-local scientific edits hidden inside these classes before the v4 comparison.

| Source | v3.9.0 / open-offline Git blob | v4.0.8 Git blob | Source-level result |
|---|---|---|---|
| `ADAPChromatogram.java` | `d827bcf2e0e7c9dd5a36cc0ed75a65cee92f3952` | `d827bcf2e0e7c9dd5a36cc0ed75a65cee92f3952` | byte-identical |
| `ExpandedDataPoint.java` | `2ec645dd90b678265f8e3044ed9e820a1f17142c` | `2ec645dd90b678265f8e3044ed9e820a1f17142c` | byte-identical |
| `ModularADAPChromatogramBuilderTask.java` | `6783194f47c32221a358010e84061b9a8474d5d6` | `ab49a0a13fa1a29707c560f24f15d47fb55db6f0` | differs |
| `ModularADAPChromatogramBuilderModule.java` | `f1782eafdaea381929bd80508760f690bd6ddfda` | `b5753ce0fdc993fe476d41469048fbbe9f56909a` | differs |
| `ADAPChromatogramBuilderParameters.java` | `6dd562fea83d932f97a527db5b2c3fbfc3f592b4` | `1293e4d698a472eb3590ae3c4f4aa94cc8cef460` | differs |

A matching source blob is evidence about source identity only. It is not, by itself, evidence of behavioral equivalence of the complete executed stage.

## ParameterSet version

`ADAPChromatogramBuilderParameters` does not override `getVersion()` in the inspected v4.0.8 source. The public v4.0.8 `ParameterSet` interface provides a default version of `1`.

Therefore:

- public v3.9.0 ADAP ParameterSet version: `1`;
- open-offline ADAP ParameterSet version: `1`;
- public v4.0.8 ADAP ParameterSet version: `1`.

## Scientific parameter surface

The inspected v3.9/open-offline and v4.0.8 parameter constructors expose the same scientific surface for the chromatography path:

1. raw data files;
2. scan selection, defaulting to MS1;
3. minimum consecutive scans;
4. minimum intensity for consecutive scans (`minGroupIntensity`);
5. minimum absolute height / highest point (`minHighestPoint`);
6. scan-to-scan m/z tolerance;
7. suffix;
8. hidden state controlling single-scan handling (`allowSingleScans`).

Legacy XML aliases remain present for the historically serialized names:

- `Min group size in # of scans`;
- `Group intensity threshold`;
- `Min highest intensity`;
- `Scans`;
- `Scan to scan accuracy (m/z)`.

The parameter source blobs differ because later source also contains GUI/help construction changes. No scientific parameter addition/removal has been identified in this inspected surface.

## Module execution path

For chromatography, the inspected v4.0.8 module still obtains `ADAPChromatogramBuilderParameters.dataFiles` and creates the task through:

`ModularADAPChromatogramBuilderTask.forChromatography(...)`

The open-offline module follows the same high-level chromatography task path.

Module source identity nevertheless differs, so execution parity must be demonstrated rather than inferred.

## Task-level semantic difference — F-022

The known non-imaging LC-MS difference is the final feature-list sorting/renumbering step.

Open-offline / public v3.9 behavior:

`FeatureListUtils.sortByDefaultRT(newFeatureList, true);`

Public v4.0.8 behavior:

`FeatureListUtils.sortByDefault(newFeatureList, true);`

Upstream commit `5b0704a6c7f110591dce8161a9384d8062c0572d` (`introduce method for default sorting in FeatureListUtils`, 2023-12-27) introduced the relevant behavior. In that commit, `sortByDefault` dispatches non-imaging raw data to m/z sorting with row-ID renumbering, whereas imaging data are sent to RT sorting.

The immediately preceding imaging-oriented change, commit `741e592e8113488da464aba114d5e340cc2e08ec`, still retained RT sorting for non-imaging chromatography.

For the LC-MS parity gate, the demonstrated source-level change is therefore:

> final feature-list order / row-ID renumbering changes from RT order in the 3.9 line to m/z order in v4.0.8.

F-022 was registered before any corrective implementation.

### Current decision

Do **not** port the sorting change before differential execution.

The normalized ADAP report must distinguish:

- scientific chromatogram identity/content; and
- final representation order / row ID.

A row ID cannot be the cross-version primary key.

## Non-imaging feature-shape behavior

One v4 task-source difference wraps `FeatureShapeType` assignment in an explicit `if (!isImaging)` block, whereas the 3.9-line task sets the boolean value to `!isImaging`.

For the declared non-imaging LC-MS gate, both paths set feature-shape availability to true. This source difference should still be retained in provenance, but it is not currently expected to change the governed non-imaging result.

## Differential record contract

The existing version-1 differential stage-report schema is scan-centric and fixes `record_key = scan_key`. ADAP must not be forced into that identity model.

The ADAP extension must define a deterministic `chromatogram_key` independently on oracle and candidate sides. The key must be based on stable scientific membership/content rather than the final row ID.

At minimum, each normalized ADAP record should retain:

- `chromatogram_key`;
- final `row_id`;
- final `row_order` / list ordinal;
- ordered source scan keys and scan numbers;
- first and last source scans;
- point count;
- representative/working m/z;
- RT start and end;
- apex RT when defined;
- maximum intensity/height when defined;
- area when already materialized at this stage;
- lossless point-series information sufficient to compare `(scan, RT, m/z, intensity)` membership and values;
- status / terminal state.

The comparison must separately report:

1. content/membership differences keyed by `chromatogram_key`;
2. representation/order differences (`row_order`, `row_id`).

F-022 must remain visible even if all chromatogram content is identical.

## Failure-first requirement

Any new runtime, source-closure, representation, or scientific difference discovered during ADAP execution receives a new `F-xxx` record before code is modified. The record must include original evidence, impact, root-cause hypothesis, risk, proposed correction, and predefined validation criteria.

Tolerance broadening is not an acceptable convenience repair.

## Remaining Phase A / Phase B work

Before direct execution can be accepted:

- freeze the executed ADAP dependency/source closure from the established oracle slice;
- freeze the canonical ADAP settings mapping and hash;
- correct the machine-readable parameter class/version/blob identities from F-021;
- extend the report schema for chromatogram-stage records without breaking version-1 import/mass-detection reports;
- add schema/comparator tests that prove row-ID changes do not create false missing/extra chromatograms while row-order differences remain visible;
- implement independent oracle and open-offline ADAP report producers.

ADAP remains `Adapted` with `direct_differential_complete = false` until the executable comparison passes.