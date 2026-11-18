# Native cytometry compensation and gating checkpoint

The [command guide](../../vignettes/cytometry-gating.md) provides a copyable
request, gate example, explicit units/policies and recovery instructions.
`cytometry-tools GATE` is available from a current source build. It needs no
external scientific runtime or installation. Released artifacts remain
unchanged. This checkpoint adds reusable methods; it does not complete the
FlowJo/FACSDiva families or the 250-paper implementation goal.

The source census found FlowJo in 27 and FACSDiva in 17 papers, across 33
distinct papers. That census has verified primary-source hashes and identifies
promising shared work, rather than proving 44 computational workflows or
paper-specific gating equivalence. Acquisition/sorting mentions, proprietary
workspaces, plugins, cell-cycle models and particular analysis settings remain
separate review and implementation work.

The implementation includes pure Java FP64 square compensation, explicitly
selected rectangular OLS spectral unmixing, linear/log/arcsinh/logicle/hyperlog
transforms and ratios, rectangle/polygon/ellipsoid/quadrant gates, explicit
parent populations and Boolean gates. The raw-reader bridge preserves original
metadata and requires explicit formatting and legacy interpretations. Model
validation precedes event evaluation. Captured inputs, whole-population tables,
warnings, a manifest and an admission marker make the command reviewable.

`VALIDATION.json` records the tested artifact, completed checks and exact scope.
The same 26 focused tests pass on Windows and Linux; the counts are not independent
test cases added together. They include 25 original Python-struct storage
controls, 88 NumPy compensation/inverse references, 12 independently encoded
bridge files, geometric boundaries, transform domains, XML schema/security,
named/inverted/rectangular compensation, quadrant/Boolean/parent rules and CLI
failure/admission controls. Refer to the receipts for the exact test counts.

The packaged command reads the **unchanged original set-2 FCS file**, all 10,000
events and all 30 original gates, on Windows and Linux. Every one of the
300,000 memberships and the complete count table is byte-identical between
platforms and matches the retained prepared-input component. This is complete
raw-file execution, rather than a prefix or a prepared-values-only claim.

**The historical 80-gate ISAC suite does not pass as a whole.** The read-only
audit verifies all selected standard files against the pinned original ZIP and
records the following differences without modifying any source or expected
bitmap:

- Set 1 contains a nonsymmetric `Ellipsoid3D` covariance (17.5 versus 15.5).
  Normative section 5.3.3(j) requires symmetric positive-definite covariance.
  Full-file model admission is refused. Its legacy FCS file also has the
  separately retained ambiguous TEXT issue; no complete native set-1 claim is
  made and no gate is removed to obtain one.
- Set 2 has 24 complete original bitmap matches, five complete transformed
  polygon disagreements and a missing `Results_Poly1ul.txt`. The unbound
  `Results_Poly1l.txt` is retained and is never silently renamed or used as an
  oracle. `Summary.csv` separately calls `RectMix1` by `RectMix1.txt`.
- Independent FlowUtils 1.2.2 agrees with all 60,000 memberships for the six
  transformed polygons, including the missing-original-name case. It also
  matches the complete raw compensated polygon. On the remaining raw polygon,
  three exact boundary points differ: native Java and the old bitmap include
  them, while FlowUtils excludes them. Exact rational orientation/segment
  checks establish the boundary locations; the standard requires inclusion.

These results support a bounded shared-method implementation, rather than a
claim of full historical-suite, vendor-software or biological equivalence.
The failed comparisons remain failed, with their exact mismatches retained;
the later bounded audit explains them and does not relabel those attempts.

`controls.zip` contains an indexed, read-back-verified collection of source
snapshots, complete independent fixtures, original reference archive/source,
reference memberships, packaged-command captures/tables, JUnit receipts and
retained unsuccessful attempts. It omits the large executable JAR, Java/Python
installations and NumPy runtime. `VALIDATION.json` supplies the artifact identity
and bundle checksum. The ordinary ZIP is evidence, not a runtime installer.

The reusable read-only audit is:

```sh
python3 tools/literature/cytometry/audit_gating_reference.py \
  --archive ORIGINAL_GatingML20_specification.zip \
  --standard "UNCHANGED_EXTRACT/Gating-ML 2.0 Full 20130122" \
  --native-run COMPLETE_NATIVE_SET2_RESULT \
  --flowutils-polygons RETAINED_INDEPENDENT_FLOWUTILS_BITMAP_DIRECTORY \
  --prepared-scale RETAINED_INDEPENDENT_scaled-2.tsv \
  --out NEW_AUDIT_DIRECTORY
```

Its archive hash is fixed and it checks complete populations, source bytes,
missing/orphan names and exact boundary proofs. It consumes retained reference
artifacts; it does not itself regenerate a FlowUtils comparison. The evidence
includes the original reference producer, package identities and raw output.
Source regeneration scripts create new directories and retain failed attempts.
See [warnings](WARNINGS.md) and [upstream credits](UPSTREAM_CREDITS.md).
