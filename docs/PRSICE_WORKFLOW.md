# PRSice: original execution and native score audit

`prsice-tools` runs pinned original PRSice 2.3.5 for clumping, score construction
and single-trait association. Java captures inputs, validates the declared
choices and independently reconstructs exported hard-call scores. The original
program supplies its haplotype LD clumping and fitted statistics.

**Warnings and required actions:** use results only after exit 0 and a valid
`COMPLETED.json`, manifest and `results_admitted=true`. Read `WARNINGS.md`,
`scientific-audit.json`, both ledgers and the original logs. Exit **3 means the
dependent analysis was skipped**; install or stage the exact prerequisite only
where allowed, then retry a **new** output path. If installation or execution is
forbidden, retain the skip. Exit **2 means no complete result**; preserve partial
files, resolve the named data, numerical, policy or capacity problem and retry a
new path. Existing output is never overwritten.

This adapter requires a current source-built executable JAR. The published
0.4.1 source precedes this adapter. The earlier native
[hard-call API](literature/PRSICE_HARD_CALL_SCORES.md) remains separately useful
without a PRSice runtime. This workflow checkpoint has no catalog family/review
credit until the five paper-use records are separately admitted.

## Install a private runtime

Build the complete JAR using the project's JDK/Gradle setup:

```powershell
.\gradlew.bat executableJar
java -jar build/cli/omicsmatic-0.4.1.jar prsice-tools --help
java -jar build/cli/omicsmatic-0.4.1.jar prsice-tools --export-setup D:/private/prsice-setup
python D:/private/prsice-setup/setup.py --platform windows --download --out D:/private/prsice-2.3.5
```

Create `D:/private` first. Both destinations must be new, with physical parents
without junction/symlink aliases. The exported standard-library Python helper
downloads only with `--download`. For an already downloaded official archive,
replace it with `--archive D:/downloads/PRSice_win64.zip`. Linux uses
`--platform linux` and `PRSice_linux.zip`; run the helper on that Linux x86_64
host. `--stage-only` checks and stages bytes without executing them. Setup
verifies the complete archive and executable SHA-256, retains the GPL license,
and checks `--version` on a matching host. It runs no analysis and needs no
administrator installation. Keep runtime files and raw traces private.

The [official release](https://github.com/choishingwan/PRSice/releases/tag/2.3.5)
is GPL-3.0-or-later and corresponds to source revision
`9dbf55e3f7f3ede2c1b1b6a334c676163e0cbe34`. Source and runtime pins are packaged
in `SOURCE.json`. The upstream release flags Windows Eigen modifications,
BGEN limitations and changed R-squared reporting. This profile uses diploid,
autosomal, SNP-major BED inputs on Windows/Linux x86_64.

## Run an artificial example

```powershell
python tools/literature/prsice_workflow/make_example.py --runtime-home D:/private/prsice-2.3.5 --out D:/private/prsice-example
java -jar build/cli/omicsmatic-0.4.1.jar prsice-tools --request D:/private/prsice-example/target-clump-score.json
java -jar build/cli/omicsmatic-0.4.1.jar prsice-tools --inspect D:/private/prsice-example/target-clump-score
```

The generator writes artificial genotype, phenotype and covariate files and five
complete requests. It does not execute them. `linear.json` and `binary.json`
fit single-trait associations with age and a categorical batch covariate;
`reference-frequency.json` explicitly selects an independent reference panel;
`prepared.json` applies previously prepared effect weights without reclumping.
Inspect and adapt all choices for your study. Relative bundle, runtime and
output paths resolve against the request file's parent. Input role paths resolve
inside the declared bundle and retain their original bytes.

Read `worker/original.all_score` for original scores. `results.tsv` supplies the
literal fitted coefficient, SE, P and R2 with the declared phenotype beside the
original phenotype label (which is `-` for single-trait original output).
`worker/original.prsice`, `.summary` and `.best` retain original fit products.
`sample-ledger.tsv` distinguishes selection, founder status, model membership
and standardization; `variant-ledger.tsv` records original selection and allele
orientation. Unselected rows are not assigned an invented QC/clumping cause.

## Declare the scientific request

RUN requests have exactly these root fields: `schema_version:1`, `mode:"RUN"`,
`profile:"prsice-2.3.5-9dbf55e"`, `operation`, `runtime_home`, `bundle`, `inputs`,
`parameters`, `policies`, `limits` and `out`. Generated requests show the entire
schema; unknown or omitted scientific fields fail.

| Choice | Required declaration |
| --- | --- |
| Operation | `CLUMP`, `SCORE`, or `PREDICT` (single-trait association) |
| Inputs | Named base columns; target BED/BIM/FAM; reference trio exactly when used; optional keep/remove, extract/exclude, phenotype and covariate files exactly when used |
| Identity and provenance | Assembly and population labels, `ignore_fid`, delimiter, `include_nonfounders`, seed, threads and original memory setting |
| Base | `GWAS` with SNP, CHR, BP, A1, A2, effect and P header names; `BETA` or `OR`; explicit optional INFO/MAF minimum filters |
| Clumping | `NONE`, `TARGET`, or `REFERENCE`; active clumping requires r2, window in kb and P cutoff; unused values are null |
| Scoring | `add`, `dom`, `rec`, `het`; `MEAN_IMPUTE`, `SET_ZERO`, `CENTER`; `sum`, `avg`, `std`, `con-std`; cumulative Boolean; `TARGET`/`REFERENCE` frequencies; increasing `LEVELS` or explicit `GRID` |
| Phenotype | `QUANTITATIVE` or `BINARY`; `FILE` with exact column or `FAM` column 6; explicit prevalence or null; no unused outcomes |
| Regression | Exact covariate and categorical column lists; permutation count; logistic-permutation Boolean; `IN_SAMPLE_TUNING` or `FIXED_SCORE_ASSOCIATION` (one threshold) |
| Alleles | Palindromic `DROP` or `KEEP_ORIGINAL_WITH_WARNING`; strand `EXACT` or `ALLOW_ORIGINAL_COMPLEMENT_WITH_WARNING` |

Headers and identifiers are literal UTF-8; case, BOM, duplicates and sample keys
are never silently repaired. The selected analysis is autosomal; unused X/Y
rows in an otherwise valid BIM do not make the entire input invalid. BED must
match the full original FAM/BIM dimensions, including samples/variants later
removed. Compressed base tables are recognized by gzip magic. Selection lists
use FID/IID (or IID with explicit `ignore_fid`) without a header. Base/target/
reference coordinates must match for selected variants. Population labels are
user declarations, not measured ancestry checks.

`PREPARED_WEIGHTS` uses SNP/A1/A2/effect headers, `pvalue:null`, no clumping, a
single inclusion marker threshold 1 and cumulative scoring. Supply both weight
coordinate headers or set both null to request explicit mapping by unique
target BIM ID. The generated base table labels P=1 as an inclusion marker,
not association evidence; the original weight file remains unchanged. Headerless
weight files need an explicit preparation step; column positions are not guessed.
These settings can apply posterior weights from another tool, without claiming
that the weight estimation or historical paper analysis was reproduced.

## Source conventions and refusal policies

**REFERENCE frequency cache:** original PRSice skips filling reference counts
when reference `maf=0` and `missing=1`; `--use-ref-maf` can then use target counts.
This adapter returns SKIP3 for that unfulfilled request. Retain the skip, choose
TARGET explicitly, or supply scientifically justified active reference QC.
No threshold is changed automatically. Separate reference frequencies cannot
be combined with target clumping through this original CLI, so that combination
is refused.

**Missing outcomes and standardization:** quantitative `-9` is observed; binary
`-9` and literal NA/NAN are missing. Binary codes must follow the original
integer 0/1 or 1/2 convention. Original standardization uses its source flags
and raw-versus-compact keep/remove indexing, rather than fitted complete cases.
Control standardization excludes known cases in the final model map; unknown
outcomes/covariate-excluded rows can remain in its reference set. Warnings and
sample ledgers expose those rows. Choose
`standardization:"FAIL_ON_INCOMPLETE_FIT_ROWS"` to refuse such inclusion, or
`"ORIGINAL_MASK_WITH_WARNING"` to preserve it explicitly.

**Fit interpretation:** original quantitative P uses Student t with df=N;
SE uses the source residual-variance convention. Threshold-table R2 is
full-minus-null, whereas summary PRS.R2 is
`1-(1-Full.R2)/(1-Null.R2)`. Negative/unavailable R2 is retained. A printed
zero P is a numerical/export boundary. The source does not expose its GLM
convergence flag. `solver_convergence:"REQUIRE_REPORTED_CONVERGENCE"` skips
fitting before launch; `"SOURCE_UNREPORTED_WITH_WARNING"` preserves the explicit
limitation. Finite fields do not prove convergence. Threshold tuning and
permutation output do not establish held-out predictive accuracy.

`unmatched_variants`, `incomplete_samples` and `original_warnings` each require
`ERROR` or `RETAIN_WITH_WARNING`. No nonfinite primary fitted coefficient, SE,
P or score is admitted. Allele complements follow the original single A/T/C/G
rules; multi-base strings are not reverse-complemented. Palindromic keep uses
original literal dosage orientation. Nonadditive missing/centering formulas
retain source behavior. Average divides by contributed allele count (two per
diploid variant), and standardization uses average scores with sample SD.

`limits:{}` selects documented resource defaults in the typed source contract:
1 hour, 100,000 files, 8 GiB/member, 32 GiB captured inputs, 64 GiB output,
10 million table rows, 2 million samples, 20 million variants, 10,000 thresholds,
200 million native scoring cells, 16 GiB scratch and 16 MiB transpose blocks.
Raise named limits explicitly only when resources and policy permit. The native
audit streams BED rows and a temporary score spool; it allocates no dense
genotype matrix and removes its owned spool before returning. Process limits
are monitored periodically; the original `--memory` setting and observed-child
cleanup are not OS containment or a hard aggregate RAM quota. An original GRID
overflow that changes to SNP-by-SNP evaluation is SKIP3 here; use justified
explicit LEVELS or a separately qualified fallback implementation.

## Inspect and execute a fresh verification

`--inspect` requires the exact retained complete producer JAR. It checks closed
file/directory inventories, source/profile pins, input bindings, reconstructed
ledgers and native score comparisons. It needs no installed original runtime
and does not refit original regression. SHA-256 detects byte changes; it is not
a signature or proof of authenticity.

For a fresh original execution, save this JSON beside a new output parent:

```json
{"schema_version":1,"mode":"VERIFY","analysis":"target-clump-score","out":"verified-score","runtime_home":"D:/private/prsice-2.3.5"}
```

Run it with `--request`. `REPLAY` has the same fields and also executes a fresh
original computation. Both recheck the unchanged parent, exact producer and
runtime identity and compare scientific products; paths, timing and logs are
retained separately. Use the same pinned platform runtime for fresh byte
comparison. Saved inspection is portable across Java hosts. Preserve the
original producer and captured inputs; never refresh old hashes to accept a
changed binary or result.

## Qualification

Run `tools/literature/prsice_workflow/check.py --help` for reproducible artificial
qualification. It retains private inputs, raw traces and captures in NEW output.
The public [checkpoint](literature/prsice-workflow-checkpoint/STATUS.json) and
counts-only validation distinguish actual Windows/Linux executions, independent
score comparisons, fresh operations and independent R reference checks. BGEN,
PRSet/gene sets, R plotting, multiple traits, historical author editions, full
paper pipelines and original cohort reanalysis require separate qualification.
