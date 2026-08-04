# v4.0.8 Oracle — frozen reference commit

This document freezes the exact upstream commit used as the immutable comparison oracle for Milestone 4 (functional parity with the public LC-MS scientific core of mzmine v4.0.8). See #14 and #27 (4B.1).

## Frozen identity

| Field | Value |
|---|---|
| Repository | `mzmine/mzmine` (GitHub) |
| Tag | `v4.0.8` |
| Exact commit SHA | `8029f930d28c0447f0acf2bcabef0a79865ad434` |
| Tag commit message | "Increment patch version" |
| Tag commit author | SteffenHeu (via github-actions bot) |
| Tag commit date | 2024-04-25T17:49:53Z |
| Full changelog range | `v4.0.3...v4.0.8` (9 PRs: user-tab/system-clock fix, remove Waters package, drag-drop install fix, calibrant file parsing fix, workshop fixes, Thermo parser extraction path fix, mzXML MS1/MS2 closing-tag fix, parallel-stream pairs helper) |
| License | MIT (SPDX `MIT`), confirmed via repository metadata |
| Repository visibility | Public |

## Verification method

The commit was resolved directly via the GitHub API (`GET /repos/mzmine/mzmine/git/refs/tags/v4.0.8`), which returned an annotated/lightweight tag object pointing to the commit SHA above. The commit was independently confirmed via `GET /repos/mzmine/mzmine/commits/{sha}`, showing a single 6-line diff to `mzmine-community/src/main/resources/mzmineversion.properties` (a version-bump commit, consistent with the release-note pattern used by this project's CI).

The release notes for `v4.0.8` (GitHub Releases) list the following merged PRs between `v4.0.3` and `v4.0.8`, none of which touch the core LC-MS scientific modules in scope for this milestone (mzML import, mass detection, ADAP, smoothing, resolver, isotope finder, rows filter, join aligner, gap filling, duplicate filter, correlation grouping, export):

- update more MZmine mentions to mzmine (#1793)
- Single users tab and fix for system clock offset (#1809)
- remove waters package (#1808)
- Directly select user after drag drop install (#1810)
- fix calibrant file parsing (#1812)
- Workshopfixes (#1798)
- move thermo parser extraction path, add to tmp file cleanup (#1816)
- Fix mzXML as ms1 scan closing tag is after MS2 scans (#1814)
- Easy parallel stream on pairs in a list (#1804)

The `mzXML MS1/MS2 closing tag` fix (#1814) and the `Thermo parser extraction path` fix (#1816) are the only two entries with plausible relevance to import/parsing behavior and must be recorded in the module inventory (#31 / 4B.3) as candidate parameter/algorithm changes to verify, even though neither targets mzML (the format used by this fork's governed public corpus).

## Build requirements (from the frozen commit's own repository metadata)

- Build system: Gradle (Kotlin DSL), consistent with this fork's own `build.gradle.kts` lineage.
- Language: Java (project language per repository metadata).
- The exact JDK/Gradle version pins must be read directly from the frozen commit's `gradle.properties`/`build.gradle.kts` at build time; this document intentionally does not restate version numbers that could drift from the frozen source — the build command is:

```
git clone https://github.com/mzmine/mzmine.git
cd mzmine
git checkout 8029f930d28c0447f0acf2bcabef0a79865ad434
./gradlew clean build
```

## Proprietary dependency check

The official `mzmine/mzmine` repository at this commit is published under the MIT license at the source level. This fork's existing independence-audit tooling (already used across Milestones 1–2) must be run against a local build of this frozen commit before it is used as a differential oracle, to confirm:

- no `io.mzio` proprietary binary is required to build or run the headless LC-MS core;
- any login/authentication/licensing behavior present in the built artifact is confined to GUI/account features outside the scientific core under test, and is not required for headless batch execution of the modules in scope.

This check is tracked as an explicit acceptance item and must be completed and recorded (pass/fail, with evidence) before this oracle is used in #32/#33/#34 (4B.4–4B.6).

## Non-goals

- This document does not merge upstream `master` into this fork.
- This document does not change this fork's own application version (remains 3.9.1 until Milestone 4 acceptance criteria are met).
- This document does not build the full GUI/installer — only the headless scientific core is in scope for the oracle.

## Status

Commit frozen. Independence/build verification (see "Proprietary dependency check" above) is the next required step before this oracle can be used in 4B.4–4B.6.
