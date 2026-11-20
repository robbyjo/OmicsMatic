# Native Scanpy Pearson component checkpoint

**A new native Java complete-matrix Pearson-normalization component is
qualified for explicit Scanpy 1.10.4 and 1.13.0 float64 dense/CSR profiles.**
It runs without Python, subprocesses or network. This is a library component,
separate from the current upstream file workflow. Scanpy's broader HVG/
historical workflow qualification, global dispatch and cumulative admission
remain open. Catalog totals remain **136 scoped integrations (25 native Java
+111 upstream), 1,462 reviewed uses and 1,241 pending**. No family credit is
added by this component; the full goal is unfinished.

Read the [guide](../vignettes/scanpy-pearson-native.md),
[example](../../examples/scanpy-pearson/README.md) and
[saved proof/restorer](scanpy-pearson-checks/README.md).
The [ordered file-workflow checkpoint](SCANPY_WORKFLOW_CHECKPOINT_2026-11-19.md)
and earlier preprocessing/source-census checkpoint remain valid historical
scopes. Their sources and saved hashes are unchanged.

## Implementation and evidence

`ScanpyPearsonResiduals` derives the analytic offset-model expected count from
cell/gene/grand totals, applies the source NB variance with shared theta, and
clips residuals symmetrically. Defaults are theta=100 and sqrt(number of cells)
clipping. It does not fit SCT, add a variance floor, select HVGs or run PCA.
Counts and expected means are evaluated at every coordinate: implicit sparse
zeros generally need negative residuals, not an implicit zero output.

The 1.10.4 source divides directly for zero expected counts and can return
NaNs. Those remain unreportable diagnostics. The 1.13.0 source explicitly
assigns zero residuals at zero expected counts, including all-zero input.
That convention is retained and flagged. Overflow, nonfinite model terms,
undefined unclipped residuals and positive expected-count underflow refuse a
complete matrix even when clipping leaves finite-looking values. The existing
scalar NB helper has different supplied-mean/floor semantics and is not used
as a substitute.

The strict normal Gradle build passed. Its **eight new class payloads** form
the small library together with unchanged shared matrix classes and GPL/BSD
notices; all **20,202 preceding class payloads remain byte-identical**.
`jdeps` reports only `java.base`. All **735 checks pass on Windows Java 21/25
and Linux Java 21**, including **720 original-package cases** and 15 boundary/
admission/cancellation/identity controls. Native production classes come only
from the small component during those tests; only a JDK-only JSON parser is
copied as a test helper. All three Java example runs passed.

References execute actual full isolated Scanpy 1.10.4 and the existing 1.13.0
package with declared dependency versions. Both original normalization source
files match the exact PyPI wheels. Each edition has 312 base cases plus 48
additional implicit-zero cases. Raw original captures remain unchanged; a
combined test view is explicitly derived and links those parts. Across both
reference phases, all **37,676 runtime entries remain unchanged**. A first
capture helper had an unmatched parenthesis before import/analysis; its source,
stderr, baselines and failure receipt remain retained. The fixed helper ran
into NEW outputs; no original scientific function, input/result or hash changed.

Cases cover zero cells/genes, all-zero counts, one-cell/gene matrices,
fractional counts, implicit and explicitly stored sparse zeros, wide reductions,
theta/clip boundaries, overflowing totals/products/variance, positive
underflow and finite clipping that hides invalid arithmetic. Finite reference
values use the stated 64-ULP/8e-12 tolerance; NaN/Inf classes and result admission
are separate assertions. No arbitrary ill-conditioned, whole-package,
author-runtime, whole-paper or biological validation claim is made.

## Continue in this order

1. Verify/restore the saved component into NEW output and run the example.
   Reuse this kernel; do not substitute the scalar NB helper or silently
   change a reported edition's zero convention.
2. Continue the actual 22 Scanpy source roles. P062 reports 1.10.4 Pearson
   normalization/HVGs before SysVI and a separate square-root count branch
   for plots/DE. This native normalization does not establish its entire
   experimental-HVG, SysVI, denoising or marker workflow. Preserve that distinction.
3. Prioritize remaining HVG flavors/batching, including source-defined missing
   ranks, Pearson HVGs and regression. Extend useful current/historical
   original workflows with exact dependency/source guards and failure controls.
   Keep mathematical failures distinct from specifically audited source
   conventions; never introduce a general NaN-to-zero admission rule.
4. Add global CLI dispatch with the guarded shared-source compatibility
   transition; finish the 22-role coverage crosswalk and cumulative admission
   through Enrichr and the 812-entry manifest. Preserve all 1,462 reviews and
   P061; the 21 Scanpy review candidates remain unadmitted.
5. Continue other higher-impact missing tools and the 1,241 pending reviews.
   The frozen 250-paper window remains 2023-10-23 to 2026-10-23 (71 Nature,
   104 Nature Genetics, 75 AJHG). Do not silently shift it or redefine completion.

## Warnings and required actions

**Check `usable()` and `warnings()` before `matrix()`.** Legacy zero-count
NaNs, nonfinite model arithmetic, lost positive expectations and invalid input/
capacity/interruption admit no complete matrix. Retain raw counts, declared
profile/dtype/storage/options, diagnostics and failures. Correct the named
cause only within the scientific protocol; retry NEW analyses. Do not replace
NaN/Inf, round counts, silently filter identities, add a floor, switch editions
or refresh saved hashes to manufacture success.

The native API needs Java 17+ and its packaged shared matrix classes; original
Python is unnecessary for native computation. Fresh original-package capture
needs the exact permitted reference environment. If unavailable or installation
is forbidden, skip that dependent capture with prominent SKIP3 and restore the
named dependency. Saved-reader missing evidence is SKIP3; changed/invalid
evidence is FAIL2. Library exceptions/warnings carry equivalent no-result
states, not CLI exit codes.

The two historical marker-verifier mains still need the exact original
`build/cli/omicsmatic-0.3.7.jar`: 164,898,617 bytes, SHA-256
`545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
Leave them skipped unless restored; the different published release is not a
replacement. Cell Ranger remains excluded. Both literature reports remain
withheld from Pages. No release is created; published 0.4.0 predates this API.

## Transfer and resume

The active checkout remains `D:/projects/JLinAlg/build/high-impact-stats-1`,
branch `codex/plink-relatedness-20261119`. Preserve all 22 worktrees, historical
dirty overlays and prior evidence; do not reset/clean the original root.
GitHub is not a full machine backup.

Retain the entire active `build/scanpy-high-impact-1/`, especially
`pearson-source-1/`, `runtime-pearson1104-1/`, both base reference attempts,
`pearson-extra-inputs-1/`, `pearson-extra-reference-1/`, `pearson-fixture-1/`,
`pearson-build-1/`, `pearson-gradle-1/` and `pearson-package-1/`. The normal
candidate is `pearson-gradle-1/project-build/cli/omicsmatic-0.4.0.jar`; the small
qualified library is in `pearson-package-1/`. Restore only the pinned library
for offline native use; full JARs/archives/wheels need separate permitted transfer.

The new original 1.10.4 reference prefix is
`/var/tmp/omicsmatic-scanpy-1104-pearson-20261119-1/environment` in WSL
`JLinAlg-Omics-SAIGE-20261026`. Preserve it and the unchanged 1.13.0 and older
scDRS reference prefixes. Exact wheels, locks, package sources, original
results and before/after runtime inventories are retained; avoid publishing
setup receipts containing full environment dictionaries. Keep the primary XML
cache and all earlier transfer paths.

Private new-output helpers are under `D:/projects/JLinAlg/build/`, including
`setup_scanpy_1104_pearson_linux_20261119.py`,
`drive_scanpy_pearson_reference_v2_20261119.py`,
`drive_scanpy_pearson_extra_reference_20261119.py`,
`package_scanpy_pearson_fixtures_20261119.py`,
`gradle_scanpy_pearson_20261119.py` and
`package_qualify_scanpy_pearson_20261119.py`. Never rerun into existing outputs.
Publication/preservation receipts belong under
`D:/projects/JLinAlg/build/git-publish/20261119-scanpy-pearson-46/`.
