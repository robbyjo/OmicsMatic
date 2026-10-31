# Supplied SNP annotation example

This Java command combines supplied gene, consequence, score, LD, reference-count
and optional BED tables using the pinned FUMA SNP annotation postprocessing
semantics. It does not generate ANNOVAR consequences, estimate LD or download
biological resources. The small data are synthetic; captured original R result
tables provide numerical references. No upstream program or biological database
is redistributed.

From the repository root, with Python 3 and Java 17 or newer:

```powershell
python examples/fuma-variant-annotation-workflow/prepare_inputs.py --out build/snp-demo-inputs
java -jar build/literature-review/fuma-variant-annotation-candidate-1/jlinalg-0.3.7.jar fuma-tools --annotation-options-file build/snp-demo-inputs/minimal.json
java -jar build/literature-review/fuma-variant-annotation-candidate-1/jlinalg-0.3.7.jar fuma-tools --annotation-options-file build/snp-demo-inputs/bed.json
java -jar build/literature-review/fuma-variant-annotation-candidate-1/jlinalg-0.3.7.jar fuma-tools --workflow-options-file build/snp-demo-inputs/parent.json
```

Use new input/output directories for each attempt. The preparer verifies the
archive SHA-256, every member hash and exact member population before creating
the output. It writes UTF-8 JSON recipes with relative input paths. The first
recipe uses no BED tracks; the second and parent recipe use two tracks with
explicit plain/gzip formats. Resource names and assembly are synthetic labels.

Run the complete numerical check against the actual JAR:

```powershell
python tools/literature/verify_fuma_variant_annotation_workflow.py --jar build/literature-review/fuma-variant-annotation-candidate-1/jlinalg-0.3.7.jar --work build/snp-demo-verification
```

Use `--java PATH_TO_JAVA` if necessary. This verifier prepares fresh inputs and
runs all three recipes. It compares every synthetic source result row/field,
nearest-gene query/subject pair, BED result, raw input snapshot/hash and retained
scientific byte count. Java 21/25 on Windows and Java 21 on Linux were tested.
The exact development JAR hash and evidence are in
[the validation record](../../docs/literature/omics-2023-2026/fuma-variant-annotation-validation.json).
Build the current executable development JAR in the validated checkout if the
local candidate is absent; the version label alone does not identify its bytes.

**Warnings require review even after exit 0.** Read each result's `WARNINGS.md`,
`session.json`, `analysis-status.json`, `resource-ledger.tsv`,
`annotation-disposition.tsv` and `consequence-ledger.tsv`. The synthetic example
intentionally exercises undefined/infinite enrichment. No multiple-test
correction is inferred. Raw RegulomeDB labels such as `1a` are preserved and
are not automatically converted for other mapping commands.

**Unavailable or forbidden Java: the verifier skips dependent analysis, exit
3, with the cause and Required action.** Supply Java 17+ on a permitted host
and rerun to a new directory. No R or ANNOVAR installation is needed for these
supplied inputs. Resource-generation software has separate requirements; skip
that affected generation analysis when installation is unavailable/forbidden.

**Invalid input, capacity, source population or runtime failures exit 2.**
Inspect the named cause and logs. Child output tables and snapshots from a
failed command are retained under `partial/` as diagnostics. Correct the cause
and rerun to a new directory; existing outputs are preserved. The verifier
retains `verification.json` and command logs. A successful parent stage does
not turn another failed or skipped stage into a successful analysis.

Read [the input and scientific reference](../../docs/literature/omics-2023-2026/FUMA_VARIANT_ANNOTATION_WORKFLOWS.md)
before replacing the synthetic data. Broader FUMA and the overall literature
implementation goal remain unfinished. Cell Ranger remains excluded.
