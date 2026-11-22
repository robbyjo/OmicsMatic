# Fisher enrichment workflow example

Fit two-sided Fisher tests for declared novel-versus-known count tables, or build
those tables from a completed known-association review. The Java implementation
retains every original count, pseudocount, planned comparison, correction family,
conditional odds estimate, confidence interval and root diagnostic. Grouped
analyses also retain every contributing association and exclusion.

**Read each result `WARNINGS.md`, even after exit 0.** Empty original groups,
missing tables, unassessable baseline evidence and overlapping keys require
explicit choices. A pseudocount does not create observed associations.

Use Python 3 and Java 17+. The verified local development JAR is
`build/literature-review/fuma-fisher-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`844e4cf3d9aad0834b692cdfac9e3af992fe5e772faae8dd2c6649f3fd6eae24`.
The published v0.3.7 JAR predates this implementation. These recipes require no
R, MAGMA or upstream FUMA installation. They use synthetic inputs throughout.

From the repository root, choose a new directory with an ASCII path:

```powershell
python examples/fuma-fisher-workflow/prepare_inputs.py --out build/fisher-example-NEW
$jar = 'build/literature-review/fuma-fisher-candidate-1/jlinalg-0.3.7.jar'
java -jar $jar fuma-tools --fisher-options-file build/fisher-example-NEW/counts.json
```

The preparer verifies the archive checksum and the complete member, type, size
and checksum allowlist before creating the directory. `counts.json` fits 244
planned tables in 22 explicit families. It includes an empty group, a missing
table and a finite log probability whose displayed probability underflows to
zero. Inspect `results/counts/fisher-tables.tsv`, `family-audit.tsv`,
`root-audit.tsv` and `fit-warnings.tsv`.

For grouped counts, run the matching upstream review first:

```powershell
java -jar $jar fuma-tools --known-options-file build/fisher-example-NEW/upstream-exact.json
java -jar $jar fuma-tools --fisher-options-file build/fisher-example-NEW/exact.json
```

Each grouped recipe fits 240 comparisons in 20 families: all five supported
corrections, both probability representations and explicit pseudocounts 0 and 1.
Every family includes all 12 planned comparisons, including empty groups.

| Fisher recipe | Run first | Meaning |
| --- | --- | --- |
| `exact.json` | `upstream-exact.json` | Match gene and trait IDs; require available matching tests in every baseline source before calling an absent key novel. |
| `selected.json` | `upstream-selected.json` | Permit relative absence from the supplied selected references; matching missing tests still prevent an absence claim. |
| `pooled-strict.json` | `upstream-pooled-strict.json` | Recompute original classifications using genes pooled across traits, while requiring matching baseline tests. |
| `pooled.json` | `upstream-pooled.json` | Recompute pooled-gene classifications with selected-reference absence. |
| `parent.json` | None | Run a fresh known-association stage and its explicitly dependent Fisher stage in the typed parent workflow. |

```powershell
java -jar $jar fuma-tools --workflow-options-file build/fisher-example-NEW/parent.json
python tools/literature/verify_fuma_fisher_workflow.py --jar build/literature-review/fuma-fisher-candidate-1/jlinalg-0.3.7.jar --work build/fisher-verification-NEW
```

The verifier runs all ten commands: six Fisher result populations and four
separate upstream reviews. It compares complete count, classification, group,
contributor, overlap and exclusion records with independent Python definitions
and original R 4.2.3 statistical references. It verifies result manifests, source
and snapshot hashes, complete row populations and final status, then writes
`verification.json`. R is needed only to regenerate the included references.

**JAVA UNAVAILABLE OR INSTALLATION FORBIDDEN: SKIP 3.** Required action: provide
Java 17+ on a permitted host, using verifier option `--java PATH` if necessary.
If installation is forbidden, retain the affected analysis as skipped. No
analysis command installs software. Unavailable upstream fitting software
affects that upstream analysis; the supplied-result Fisher module can use valid
permitted completed results.

**INVALID INPUT OR EXCEEDED CAPACITY: FAILURE 2.** Required action: inspect the
named cause, `WARNINGS.md`, `session.json`, `analysis-status.json` and `partial/`;
correct the cause and rerun to a NEW directory. Partial tables are diagnostics.
An independent Fisher stage can finish while a parent retains another stage's
failure or skip. A connected Fisher stage skips when its known-association
dependency fails.

**WINDOWS JAVA 21 PATH ENCODING.** The local launcher damaged non-ASCII paths
passed directly on its command line. Required action: keep the full path to the
JSON carrier ASCII and put Unicode input/output paths inside its UTF-8 JSON.
That route was verified on Windows Java 21/25 and Linux Java 21.

See the [input and interpretation guide](../../docs/literature/omics-2023-2026/FUMA_FISHER_WORKFLOWS.md).
Broader FUMA remains unfinished. Cell Ranger remains excluded.
