# Contributing to mzmine Open Offline Fork

This repository is an independent fork with a strict scientific, legal, and reproducibility boundary.
Contributions are welcome when they preserve that boundary and add evidence, not only code.

## Core rules

1. **No proprietary `io.mzio` dependencies or binaries.**
2. **No authentication or license bypasses.** Do not emulate a successful login, fabricate an
   entitled user, patch an authorization result, or reconstruct proprietary behavior.
3. **No decompiled or binary-derived implementation.** Use public source with compatible licensing or
   write a new implementation from independently documented requirements.
4. **No silent scientific adaptation.** Renamed, transformed, unsupported, deprecated, or intentionally
   changed parameters must be classified and tested.
5. **No ungoverned dataset bytes.** Public files require explicit license, immutable source, exact size,
   SHA-256, and cleanup policy before CI download is enabled.
6. **Preserve MIT license headers and provenance.**
7. **Keep behavior and architecture changes separable.** Do not combine a broad source-tree refactor
   with an unreviewed scientific algorithm change.
8. **Record failures before correcting them.** Every newly discovered parity, regression, runtime,
   infrastructure, representation, or scientific discrepancy must receive an `F-xxx` record with
   observation, proposed correction, and predefined validation criteria before corrective code is
   implemented.

## Handling later upstream source

Before selectively adapting a later public upstream file or commit:

1. verify that the exact source state is publicly available;
2. verify the license header and repository license at that state;
3. record the upstream tag/commit SHA and original path;
4. identify all dependencies on proprietary or unavailable components;
5. decide whether to omit the integration, define an original local interface, or classify it out of
   scope;
6. document Java-version and monolithic-source-tree adaptations;
7. add tests that cover the scientific or infrastructure behavior being adopted;
8. preserve the frozen 3.9 regression unless an intentional, reviewed difference is recorded.

Do not copy implementation details from a proprietary JAR, decompiled class, stack trace reverse
engineering, or binary API experiment intended to reproduce closed behavior.

## Local open interfaces

A later public source file may refer to an unavailable service. The fork does **not** use a generic
“always authenticated” stub policy.

An acceptable local replacement must:

- have independently documented semantics;
- expose only behavior required by the open scientific workflow;
- avoid account, entitlement, and license concepts unless the fork has a legitimate independent use
  for them;
- make no mandatory network call;
- fail explicitly when unsupported;
- have unit and headless tests;
- be described as fork-local rather than upstream-compatible unless compatibility is demonstrated.

For example, the local `TaskService` is an open controller-access facade. It is not a reconstructed
`io.mzio` service and does not model user authorization.

## Branch workflow

```text
public mzmine 3.9.0 base
          │
          └── open-offline-main  (stable integration)
                    ▲
                    └── agent/*  (focused PR branches)

master = modern upstream history / minimum CI bridge, not fork feature development
```

Rules:

- create focused `agent/<description>` branches from `open-offline-main`;
- open pull requests against `open-offline-main`;
- do not merge modern `master` wholesale into the fork;
- keep commits and PR descriptions explicit about source provenance and validation;
- prefer a draft PR while scientific or cross-platform gates are still running;
- do not include unrelated changes in the same PR.

## Failure-first discrepancy process

The failure history is part of the validation evidence. Do not fix a failed check first and reconstruct
the explanation afterward.

When a new discrepancy is observed during parity, regression, CI, lifecycle, or scientific work:

1. allocate the next unique `F-xxx` identifier;
2. record the original observation and retain the relevant report/log/hash or reproducible input;
3. identify the affected gate and expected behavior;
4. record impact and whether the discrepancy is infrastructure, representation, integration, or
   potentially scientific;
5. record a root-cause hypothesis and confidence level; if unknown, state that it is unknown rather
   than guessing;
6. record scientific/provenance risk, including whether the discrepancy could hide membership,
   ordering, numerical, metadata, or algorithm changes;
7. write the proposed correction **before** implementing it;
8. define validation criteria **before** implementing it;
9. implement the smallest correction consistent with that record;
10. append the resolution and evidence while preserving the original observation.

For numerical differences, broadening a tolerance is not an ordinary bug fix. Before any tolerance
change, characterize the representation or measurement effect quantitatively and justify why the
new bound is narrow, field-specific, and incapable of masking unrelated scientific differences. The
strict pre-tolerance report must remain retained.

A transient external failure may be resolved by an unchanged retry only after the original failure is
recorded. Do not change dependency repositories, disable verification, or weaken scientific gates
merely to turn CI green.

## Required PR description

Every scientific or infrastructure PR should state:

- objective and scope;
- public source provenance or original fork-local design statement;
- files/modules affected;
- scientific behavior affected;
- parameter or output-model changes;
- known non-goals;
- tests added;
- Linux/Windows result;
- frozen dataset IDs/hashes when used;
- comparison baseline and tolerances;
- all new `F-xxx` failures discovered in the work, with the proposed correction and predefined
  validation criteria recorded before the corresponding fix;
- remaining uncertainty.

If no new failure was discovered, say so rather than inventing an `F-xxx` record.

## Standard validation gates

Every PR targeting `open-offline-main` must preserve the relevant subset of:

1. independence and prohibited-dependency audit;
2. public-data manifest validation and fail-closed policy tests;
3. Java 20 compilation and tests on Ubuntu and Windows;
4. no-login headless startup;
5. deterministic synthetic LC-MS processing;
6. deterministic XML batch processing;
7. comparison with untouched mzmine 3.9.0;
8. governed public-corpus workflow checks;
9. one-, two-, and N-thread checks for concurrency-sensitive changes;
10. memory/cleanup/error-path checks for lifecycle changes;
11. strict differential-report retention and failure-first records for parity changes.

Typical local commands:

```bash
python scripts/verify_open_offline.py
python scripts/fetch_public_test_data.py --validate
python -m unittest discover -s scripts/tests -p "test_*.py" -v
./gradlew clean test classes --no-daemon
python scripts/smoke_test_headless.py
python scripts/test_headless_batch.py
```

A clean source build may require network access to resolve third-party dependencies unless the
required Gradle cache is already present. Runtime scientific processing must not require an account or
mandatory network access.

## Scientific parity requirements

A class existing in both codebases is not parity evidence. A workflow ending successfully is not
sufficient if downstream filtering could hide upstream differences.

For a capability to be marked **Equivalent**, provide where practical:

- frozen identical inputs;
- reviewed parameter mapping;
- normalized intermediate records;
- final records;
- explicit numerical and categorical tolerances;
- repeated execution;
- platform/thread coverage;
- direct comparison with the declared oracle, currently public mzmine v4.0.8.

The evidence claim must state its tested scope narrowly. For example, the completed first direct gate
supports governed indexed-centroid mzML import and centroid mass detection at noise `0.0`; it does
not silently extend to profile peak picking, advanced import processors, ADAP, or downstream feature
processing.

Use the states defined in
[`docs/public_validation/MZMINE_4_PARITY.md`](docs/public_validation/MZMINE_4_PARITY.md):

- Equivalent;
- Adapted;
- Not implemented;
- Out of scope.

## Dataset contribution requirements

Do not commit large raw or mzML files unless a small fixture has a clear redistribution basis and the
repository explicitly approves vendoring it.

Before enabling an external file in a manifest, record:

- immutable record/commit;
- exact file path and official URL;
- explicit license/reuse evidence;
- byte size and SHA-256;
- instrument and acquisition metadata;
- expected assertions;
- attribution text;
- CI cost and cleanup policy.

Never disable TLS verification, weaken hash checking, or accept a partial download to make a test
pass.

## Porting record template

Include this information in the PR body or a dedicated provenance document:

| Field | Value |
|---|---|
| Upstream repository | `mzmine/mzmine` or other public source |
| Upstream tag/commit SHA | Exact immutable reference |
| Original path | Full source path |
| License/header | License and confirmation |
| Public availability date/state | Evidence that the source was public |
| Modifications | Java, architecture, interface, and behavior changes |
| Proprietary integrations removed | Exact imports/services and why |
| Tests added | Test classes/workflows |
| Scientific impact | None or explicit description |
| Parity classification | Equivalent / Adapted / Not implemented / Out of scope |

## Original fork-local implementations

Original functionality should have a short design record describing:

- problem being solved;
- API boundary;
- deterministic semantics;
- cancellation/error behavior;
- concurrency model;
- non-goals;
- acceptance tests.

The fork-local `GroupedTask` is the reference pattern: its semantics were documented, implemented,
then validated cross-platform without claiming upstream binary compatibility.

## Reporting issues

Open issues in this repository for fork-specific behavior. Do not report fork regressions as upstream
mzmine defects unless the same problem has been independently reproduced on an unmodified supported
upstream release.
