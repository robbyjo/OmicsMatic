# Native sample-level FUMA expression preparation

**Development candidate; broader FUMA remains partial.** Java now prepares
expression heatmap resources and gene covariates from sample-level TPM/RPKM.
The [published FUMA method](https://fuma.ctglab.nl/tutorial) caps values above50,
applies log2(x+1), averages within labels, and supplies gene-centered averages.
The [MAGMA source tutorial](https://github.com/Kyoko-wtnb/FUMA-webapp/blob/master/resources/views/tutorial/snp2gene/magma.blade.php)
also defines an unweighted average across tissue means for conditioning.

This command does not quantify TPM/RPKM, infer tissue labels, convert identifiers,
generate DEG sets, run t-tests, cluster a heatmap or reconstruct historical data.
The existing expression command continues to require separate supplied DEG
memberships and complete query/background/annotation choices.

## Runnable synthetic workflow

From the repository root with a Java21 JDK on PATH, in PowerShell:

```powershell
$proof = Get-Content docs/literature/fuma-expression-preparation-2/VALIDATION.json -Raw | ConvertFrom-Json
$jar = $proof.artifact.path
javac --release 17 -Xlint:all -Werror -d build/fuma-expression-preparation-example examples/fuma-expression-preparation/CreateInputs.java
java -cp build/fuma-expression-preparation-example CreateInputs build/fuma-expression-preparation-run-1
Copy-Item examples/fuma-expression-preparation/workflow.json build/fuma-expression-preparation-run-1/workflow.json
java -Xmx128m -jar $jar fuma-tools --method workflow --workflow-options-file build/fuma-expression-preparation-run-1/workflow.json --out build/fuma-expression-preparation-run-1/workflow-1
```

Check `$LASTEXITCODE` after each command. Every destination must be new.
[Expected output](../../examples/fuma-expression-preparation/README.md) explains
the selections and strict boundary. Read root/stage `WARNINGS.md` and audits.
The proof identifies the tested candidate; the canonical release JAR is preserved.

Standalone preparation of your explicitly declared data:

```powershell
java -Xmx1g -jar $jar fuma-tools --method prepare-expression --matrix data/expression.gct --format gct12 --samples data/samples.tsv --labels data/labels.tsv --extra-samples error --build GRCh37 --annotation-id DECLARED_RELEASE --expression-id DECLARED_DATASET --gene-namespace exact-ensembl --units tpm --empty error --out output/prepared-expression-1
```

`--format tsv` requires header `gene<TAB>sample1<TAB>sample2...`. GCT1.2 requires
`#1.2`, exact gene/sample dimensions, then `Name<TAB>Description<TAB>samples`.
Files are plain UTF-8; decompress a `.gz` input separately where permitted.
Descriptions, exact gene IDs, versions and case are preserved in the audit.
Repeated IDs, negative/nonfinite/missing values and malformed rows fail.
Values in excluded sample columns are also validated; no imputation occurs.

Samples are an explicit TSV selection with header `sample<TAB>label`. Labels are
a TSV with header `label`, one per row in intended output order. Every label
needs at least one selected sample. Unknown/duplicate IDs or labels fail.
Unselected matrix columns require `--extra-samples exclude`; matrix order is
preserved for selected samples. Labels cannot contain whitespace/control
characters or reserved output names such as `Average`. Supply explicit intended
aliases in metadata; no tissue/subject mapping is inferred.

## Output contract

Gene eligibility uses **raw mean >1 in at least one selected label**, before
capping/logging. `gene-audit.tsv` includes every gene and raw label mean;
`sample-selection.tsv` and `label-samples.tsv` retain selection and sample counts.
`log2.tsv` holds label means after capping/logging. `norm.tsv` centers each gene
across **selected samples**, then averages by label. These exact-ID resources
feed the existing `fuma-tools --method expression` command.

`gene-covariates.tsv` has `GENE`, ordered label columns and `Average`, where
`Average` is the **unweighted mean of label means**. It differs from the
sample-weighted center when label sizes differ. For MAGMA fitting, gene IDs
must match the separately fitted gene bundle; Ensembl/Entrez conversion and
annotation release matching remain explicit preparation responsibilities.

The typed `expression_prepare` stage can run alone or hand its two matrices
into `expression`. Omit downstream `log2`/`norm`; annotation/expression identities
must agree and the bridge requires declared `exact-ensembl` IDs. Symbols, query,
background and DEG sets remain explicit independent inputs. A failed preparation
causes dependent expression to SKIP3 and the overall workflow to FAIL2.

## Numerical evidence and required review

Controls execute an **independent base-R implementation of the documented
method**, not original FUMA preprocessing code. Sixteen complete/subset cases
cover192 gene rows, uneven label counts, strict eligibility, cap boundaries,
tiny/constant/extreme values and sample/label ordering. The fixture retains
actual R output, runtime/version receipts and producer code.

**Known departure:** some base-R means overflow at constant maximum finite
double inputs. Java scaled raw means stay finite; the exact discrepancy remains
in the tests/proof. Such values are stress controls, not plausible TPM values.
Actual inputs with raw means>=1e300 trigger a prominent units-review warning.
Raw means within1e-12 of1 also trigger review because floating-point differences
can affect a strict eligibility decision. Inspect the raw audit against the
intended exact resource before claiming historical membership parity.
The explicit `x+1` operation can round very tiny positives to1 before logging.

## Limits, failures and remaining scope

Rows stream rather than retaining a full genes-by-samples matrix. Defaults:
100000 genes,25000 samples,1000 labels,2000000000 input values,16GiB matrix,
1GiB total payload,256MiB estimated owned storage and8MiB per input line.
Complete row/value/output budgets apply; there is no successful prefix.
Line/metadata/storage limits also respect smaller heap sublimits. Library
callers must bound retained rows and sink storage independently.

**Exit2: failed; no partial resource is admitted.** Read `WARNINGS.md` and
`session.json`; provisional tables move to `partial/` (empty for early admission
failure). Correct the named format/selection/value/identity/numerical/budget cause
and rerun the complete request to a new directory. Existing outputs are preserved.
Explicit `--empty retain` writes empty resources with a warning; it cannot support
a fitted MAGMA property analysis. Native preparation needs no external runtime.
The MAGMA adapter separately requires its permitted original runtime and reports
SKIP3 plus installation/recovery instructions when unavailable. Cell Ranger is
excluded at the user's request. No release, hosted deployment, complete FUMA or
whole-paper reproduction follows from this scoped implementation.
