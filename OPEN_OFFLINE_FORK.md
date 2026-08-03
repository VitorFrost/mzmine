# Open Offline Fork

This branch starts from the exact MZmine 3.9.0 commit:

- Upstream tag: `v3.9.0`
- Base commit: `2ac3ce3b25190430f3ae0e02c28ddbb94bc248ed`

## Objective

Build an independently maintainable, offline-capable MZmine fork using only source code released under compatible open-source licenses.

This project does **not** patch, modify, decompile for redistribution, or bypass proprietary authentication or licensing components. Later functionality must be ported from publicly licensed source history or independently implemented.

## Non-goals

- Circumventing MZIO account, authentication, feature, or license checks.
- Redistributing proprietary `io.mzio` binaries or reconstructed proprietary implementations.
- Using MZmine or MZIO trademarks in a way that implies official affiliation.

## Prohibited dependencies

The independent build must not depend on these artifacts:

- `io.mzio:user-client`
- `io.mzio:user-management`
- `io.mzio:user-management-fx`
- `io.mzio:global-events`
- proprietary `io.mzio:taskcontroller`
- proprietary `io.mzio:memory-management`
- proprietary `io.mzio:mzmine-core`

## Recovery strategy

1. Establish that MZmine 3.9.0 builds and runs offline.
2. Add reproducible build checks for Windows and Linux.
3. Recover later MIT-licensed task controller changes from public Git history.
4. Preserve or modernize the MIT-licensed `MemoryMapStorage` implementation.
5. Port scientific modules selectively, recording the source commit and license for every imported file.
6. Replace account-related UI and service calls with no-account local services where an interface is required.
7. Add CI checks that reject imports and dependencies matching `io.mzio.users`, `AuthRequiredEvent`, and prohibited Maven coordinates.

## Branch strategy

- `master` follows the modern upstream history and only contains the CI bridge required by GitHub.
- `open-offline-main` is the stable integration branch for the independent offline fork.
- `agent/open-offline-base` contains the current changes under validation.

## Current status

Completed on the initial branch:

- independent branch created from the exact MZmine 3.9.0 commit;
- source/JAR audit added for prohibited MZIO authentication and licensing components;
- Java 20 CI added for Linux and Windows;
- Google Analytics telemetry replaced with an inert compatibility facade;
- automatic network update checks disabled;
- draft pull request opened against `open-offline-main`;
- CI bridge added to the repository default branch so GitHub can validate the independent branch.

Still pending validation:

- successful Gradle dependency resolution and compilation;
- GUI startup without network access;
- headless batch execution;
- mzML import and deterministic reference processing.

## First milestone

A successful first milestone must:

- compile without any `io.mzio` dependency;
- start GUI without network access;
- execute a headless XML batch without an account or user file;
- import mzML;
- run mass detection, chromatogram building, resolving, alignment, gap filling, and export;
- produce deterministic output from a fixed test dataset;
- contain no bundled proprietary JARs.

## Validation commands

Initial validation should include:

```bash
./gradlew clean build
./gradlew test
```

On Windows:

```bat
gradlew.bat clean build
gradlew.bat test
```

Before each release, search the source and dependency graph for prohibited components:

```bash
git grep -n -E "io\.mzio|AuthRequiredEvent|CurrentUserService|UserAuthStore|LicenseUtils"
./gradlew dependencies
```

## Porting record

Every ported file or commit should be recorded with:

- upstream commit SHA;
- original path;
- license header;
- modifications made;
- tests added;
- whether the implementation existed publicly before binary extraction.
