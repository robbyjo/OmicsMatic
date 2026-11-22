# Supplied-reference locus novelty example

Compare six synthetic lead variants with a supplied known-association table.
This Java-only workflow retains every nearby match, repeated catalog record and
source row. Empty-match windows with incomplete reference coverage remain
**UNASSESSABLE_REFERENCE_COVERAGE**. Covered absence means
**NOVEL_RELATIVE_TO_REFERENCE**, relative to the declared snapshot and trait policy.

**Read `WARNINGS.md` even after exit 0.** Coverage is your assertion about the
selected reference, not independently verified completeness of published studies.
The example includes no biological catalog, upstream software or LD estimation.

Use Python 3 and Java 17+. The local development artifact is
`build/literature-review/fuma-novelty-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`3549531160ee72e8688cb1df4f505a01eb366d1dfc0d62902415febc8bbea5fe`.
It is not the published v0.3.7 JAR. These supplied-reference recipes work on
Windows and Linux without MAGMA, R or an upstream FUMA installation.

From the repository root, choose new directories:

```powershell
python examples/fuma-novelty-workflow/prepare_inputs.py --out build/novelty-example-NEW
$jar = 'build/literature-review/fuma-novelty-candidate-1/jlinalg-0.3.7.jar'
java -jar $jar fuma-tools --novelty-options-file build/novelty-example-NEW/inclusive.json
```

Prepared recipes are `inclusive.json`, `strict.json`, `all-known.json`,
`unknown-coverage.json`, and independent typed `parent.json`:

```powershell
java -jar $jar fuma-tools --workflow-options-file build/novelty-example-NEW/parent.json
python tools/literature/verify_fuma_novelty_workflow.py --jar build/literature-review/fuma-novelty-candidate-1/jlinalg-0.3.7.jar --work build/novelty-verification-NEW
```

The verifier runs all five recipes through the packaged JAR and compares every
row and field in all four scientific tables with independent integer references.
It checks source/snapshot hashes and byte counts and writes `verification.json`.
Reference ZIP membership and SHA-256 are checked before preparation creates output.

The explicit example radius is 250,000 bases. `inclusive` uses distance ≤ radius;
`strict` uses distance < radius. `exact` compares literal trait IDs; `all-known`
selects every supplied known trait and requires coverage rows with trait `*`.
`unknown-coverage` declares no coverage and never labels an empty-match window novel.
These choices demonstrate behavior; the reviewed paper paragraph does not specify
its exact endpoint inequality, trait filter or reference snapshot.

**JAVA UNAVAILABLE OR INSTALLATION FORBIDDEN: SKIP THE ANALYSIS.** The public
verifier exits **3**, names the missing/unsupported Java cause and gives recovery.
Required action: provide Java 17+ on a permitted host, using `--java PATH` if
needed; if installation is forbidden, keep this dependent analysis skipped.
No analysis command installs software. Invalid data, identities or exceeded
capacities exit **2** with diagnostic logs and `partial/`. Correct the named
cause and rerun to a NEW directory. Existing outputs remain intact. Missing
upstream locus-discovery/catalog-generation software affects that upstream
analysis; this Java command can review valid permitted supplied inputs independently.

See the [input and interpretation guide](../../docs/literature/omics-2023-2026/FUMA_NOVELTY_WORKFLOWS.md)
and [validation record](../../docs/literature/omics-2023-2026/fuma-novelty-validation.json).
Broader FUMA remains unfinished; Cell Ranger remains excluded.
