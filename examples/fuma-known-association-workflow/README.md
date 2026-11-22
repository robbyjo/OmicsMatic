# Known association review example

Adjust complete declared probability families, form a significant baseline union,
and review every target association with native Java. This synthetic example has
two baseline sources, one target source, 150 families and 2,355 test rows. Every
input test, family member, union contributor, target decision and baseline match
remains available in nine evidence tables.

**Read `WARNINGS.md` even after exit 0.** Family membership and source namespaces
are investigator assertions. Missing baseline evidence remains
**UNASSESSABLE_BASELINE**. A resolved absence is **NOVEL_RELATIVE_TO_BASELINES**,
relative to the declared resources and matching policy.

Use Python 3 and Java 17+. The verified local development artifact is
`build/literature-review/fuma-known-association-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`45f43df4caf5903bf37ae287c86663f333f08ed4926e636072872ae3a5fad12d`.
It is a development build distinct from the published v0.3.7 JAR. The recipes
need no R, MAGMA or upstream FUMA installation and work on Windows and Linux.

From the repository root, choose new directories:

```powershell
python examples/fuma-known-association-workflow/prepare_inputs.py --out build/association-example-NEW
$jar = 'build/literature-review/fuma-known-association-candidate-1/jlinalg-0.3.7.jar'
java -jar $jar fuma-tools --known-options-file build/association-example-NEW/exact.json
```

The preparer checks the ZIP checksum, every member, its type and size before
creating output. It creates these string-valued JSON recipes:

| Recipe | Meaning |
| --- | --- |
| `exact.json` | Match exact gene and trait IDs; require available matching tests in every baseline source before calling an absent key novel. |
| `selected-reference.json` | Permit absent keys as relative absence; matching missing tests and baseline sources with no available tests still prevent an absence claim. |
| `across-traits.json` | Pool baseline genes across traits for matching; keep every correction family unchanged. |
| `supplied-adjusted.json` | Import the independently adjusted reference values unchanged; retain `computed_adjustment_size=NA` because import does not certify the original denominator. |
| `parent.json` | Run the independent `known_associations` stage in the typed workflow. |

Run the parent or verify all five recipes:

```powershell
java -jar $jar fuma-tools --workflow-options-file build/association-example-NEW/parent.json
python tools/literature/verify_fuma_known_association_workflow.py --jar build/literature-review/fuma-known-association-candidate-1/jlinalg-0.3.7.jar --work build/association-verification-NEW
```

The verifier checks all nine tables against independent R adjustment and Python
union, coverage and link references. Nonzero finite numeric fields use relative
error at most `2e-14`, with no absolute tolerance. Zeros, infinities, missing values,
selection flags, integer counts and text must agree exactly. It verifies source
and snapshot hashes, byte counts, complete row populations and final status, then
writes `verification.json`. Original R inputs, reference values, the regeneration
script and provenance are included; R is needed only to regenerate that oracle.

**JAVA UNAVAILABLE OR INSTALLATION FORBIDDEN: SKIP THE ANALYSIS.** The verifier
exits **3**, names the dependency cause and gives recovery instructions. Required
action: provide Java 17+ on a permitted host, using `--java PATH` if needed. If
installation is forbidden, retain the dependent analysis as skipped. No analysis
command installs software. Unavailable upstream fitting software affects that
upstream analysis; this Java module can review valid permitted supplied results.

**INVALID INPUT OR EXCEEDED CAPACITY: FAILURE 2.** Inspect `WARNINGS.md`,
`session.json`, `analysis-status.json` and `partial/`; correct the named cause and
rerun to a NEW directory. Partial tables are diagnostics. An independent stage
can complete while another parent stage fails or skips; retain the parent status.

See the [input and interpretation guide](../../docs/literature/omics-2023-2026/FUMA_KNOWN_ASSOCIATION_WORKFLOWS.md)
and [validation record](../../docs/literature/omics-2023-2026/fuma-known-association-validation.json).
Fisher enrichment is available through the separate [Fisher workflow](../fuma-fisher-workflow/README.md).
Broader FUMA remains unfinished. Cell Ranger remains excluded.
