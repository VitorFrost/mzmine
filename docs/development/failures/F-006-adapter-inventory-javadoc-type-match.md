# F-006 — Adapter inventory parsed a Javadoc phrase as a Java type declaration

- **Status:** proposal-ready
- **First observed:** 2026-08-05 11:58 BRT
- **Scope:** deterministic adapter SHA-256 inventory
- **Input revision:** `79744c5c11bddd7266570139e9c1a76aa2261602`
- **Evidence:** workflow run `31017855245`, job `92346565416`, step
  `Record exact checked-out adapter hashes`; error:
  `adapter path/FQCN mismatch: io/github/mzmine/datamodel/MetadataOnlyScan.java != io/github/mzmine/datamodel/and.java`.
  All 24 unit tests completed successfully before the real repository inventory step.
- **Observed behavior:** the inventory parser searched raw Java text with an unanchored type-declaration
  regex. In `MetadataOnlyScan.java`, Javadoc text containing the words `class and` was selected before
  the actual `public abstract class MetadataOnlyScan` declaration. The parser therefore inferred the
  false type name `and` and correctly refused the path/FQCN mismatch.
- **Impact:** no adapter hash inventory was emitted, provenance recording and all later oracle steps
  were skipped, and the workflow uploaded only the unit-test evidence. No source preparation or
  compilation occurred in this run.
- **Root-cause hypothesis:** comment and literal removal/top-level declaration parsing used by the
  source-closure auditor was not reused in the adapter inventory script. The synthetic tests did not
  include Javadoc containing Java declaration keywords. Confidence: high.
- **Scientific/provenance risk:** low. The failure is in provenance parsing and was fail-closed. Risk
  would become medium if corrected by accepting path-derived FQCN without verifying the source
  declaration.
- **Proposed correction — revision 1 (not implemented):**
  1. Strip line comments, block/Javadoc comments, and string/character literals before parsing the
     package and type declaration.
  2. Detect only type declarations at Java brace depth zero, using the same tested semantics as the
     source-closure auditor, while retaining an independent small implementation or shared tested
     helper.
  3. Continue to verify that the declared package/type maps exactly to the file path; do not infer
     identity from path alone.
  4. Add regression tests with:
     - Javadoc containing `class and`, `interface`, `record`, and `enum` phrases;
     - nested classes before/after the top-level declaration;
     - package/type path mismatch;
     - files with no active top-level type.
- **Validation criteria — defined before implementation:**
  - the unchanged `MetadataOnlyScan.java` is identified as
    `io.github.mzmine.datamodel.MetadataOnlyScan`;
  - Javadoc/comment/string tokens never become declarations;
  - nested types are ignored;
  - all prior inventory tests remain green;
  - the real checked-out inventory discovers exactly 12 adapters and emits their SHA-256 values;
  - source preparation and compilation remain blocked until the inventory step succeeds;
  - no network access or path-only identity shortcut is introduced;
  - any subsequent failure is registered before correction.
- **Implemented change:** not implemented.
- **Validation result:** not run.
- **Decision and lessons:** provenance parsers require the same lexical defenses as source-graph
  parsers. Synthetic tests must include misleading natural-language Java keywords, not only valid
  declarations.
