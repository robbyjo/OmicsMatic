# Native MetaXcan summary workflows

The source-built `metaxcan-tools` command reads PredictDB SQLite models, GWAS
statistics and covariance files, then performs native Java **S-PrediXcan** and
**S-MulTiXcan** calculations. It captures the complete input files and can
recompute every saved result on Windows or Linux using the exact producing JAR.
It makes no Python installation or network request during analysis.

**Warnings and required action:** choose calibration explicitly; confirm that
models, covariance, model products, ancestry and genome build belong together.
Read `WARNINGS.md`, `OUTPUT-AUDIT.json` and every exclusion ledger before using
results. A successful capture can include genes with no eligible association;
their reasons remain in `GENES.jsonl`. This command does not establish biological
replication or implement model training or individual-level PrediXcan.

This command is a source-build addition. Previously published v0.4.1 release
JARs do not acquire it automatically. Build with Java17 or newer:

```shell
./gradlew executableJar
java -jar build/cli/omicsmatic-0.4.1.jar metaxcan-tools --help
java -jar build/cli/omicsmatic-0.4.1.jar metaxcan-tools --profiles
```

On Windows use `gradlew.bat`. SQLite JDBC3.54.1.0 supplies the read-only database
driver and its platform native library. Its extraction directory must permit
writing and loading that library; set `-Djava.io.tmpdir=/permitted/existing/tmp`
before `-jar` when necessary. Missing support skips the dependent analysis with
`SKIP3` and a required action. No runtime is installed by an analysis command.

## First run

Prepare closed, standalone PredictDB databases and the matching provider's
covariance file. A live `-wal`, `-shm` or `-journal` sidecar causes refusal:
checkpoint/export a **separate** closed database first. The original is never
opened for writing or repaired. Weight identifiers must be stored as text.
The generated template uses request schema2. Existing schema1 requests keep
their original `rsid`-only, exact-four-column behavior and saved profile.

Schema2 requires both settings below explicitly:

```json
"model_key": {"column": null, "identifier_handling": "unchanged"},
"additional_output": false
```

`column: null` uses `rsid` unchanged, just as omitting upstream
`--model_db_snp_key` does. To select an existing alternate column, use for example:

```json
"model_key": {"column": "varID", "identifier_handling": "source-b38-first-row-rule"},
"additional_output": true
```

The column must be a literal existing identifier, not an SQL expression. Matching
uses SQLite's ASCII case-insensitive column semantics. An absent column fails;
`SNP_ID` is never silently mapped to `rsid` or `varID`.

**Source identifier rule:** with any explicitly selected column (including
`"rsid"`), the original inspects its first row. If that identifier contains
`_b38` and does not contain case-sensitive `chr`, it adds literal `chr` to
**every** identifier in that model. A later identifier already beginning with
`chr` therefore becomes `chrchr...`. If the first row already contains `chr`,
no row is prefixed. This reproduces the source; it does not infer coordinates
or repair individual identifiers. Review `OUTPUT-AUDIT.json` model-key decisions
and both `source_variant` and effective `variant` in `MODEL-SNPS.jsonl`.

Create a request template, then edit its absolute paths, column names, assembly,
source edition, calibration and capacities. All illustrated values, including
the joint condition-number cutoff30, are examples rather than recovered author
settings. Author-unreported choices remain unreported.

POSIX shell:

```shell
java -jar build/cli/omicsmatic-0.4.1.jar metaxcan-tools --example summary-joint > request.json
```

PowerShell (explicit UTF-8; the request reader accepts a UTF-8 BOM):

```powershell
java -jar build/cli/omicsmatic-0.4.1.jar metaxcan-tools --example summary-joint |
    Set-Content -LiteralPath request.json -Encoding utf8
```

Use `--example summary` to omit the joint test and model-product input.
Provide `compression: "none"` for plain text or `"gzip"` for gzip explicitly.
GWAS `separator` is `"tab"`, `"comma"` or `"whitespace"`. Table headers remain
literal: punctuation is allowed and duplicate headers are refused.
Named tissues use unique Python-compatible names such as `Liver` or `Tissue_A`;
names that collide on Windows are refused. No label is silently repaired.
Entirely numeric GWAS identifier columns require separate dtype qualification
and are refused; this workflow does not infer numeric-to-text identifier conversion.

Run with enough heap for the complete GWAS/model/matrix populations. For example,
after adjusting the request's conservative memory/work limits to your data:

```shell
java -Xmx8g -jar build/cli/omicsmatic-0.4.1.jar metaxcan-tools --request /absolute/request.json --out /absolute/NEW_result
java -Xmx8g -jar build/cli/omicsmatic-0.4.1.jar metaxcan-tools --verify /absolute/NEW_result
```

On Windows replace paths with canonical absolute Windows paths. The parent of
`NEW_result` must already exist; the result directory must be new. Keep the exact
producing JAR alongside your private evidence. This command does not overwrite a
previous attempt. No original host input path is accessed during saved-result
verification, so a complete capture can be transferred to another supported host.

## Statistics and calibration

The `columns` object names all nine roles; unused statistics must be explicit
JSON `null`. Always provide `snp`, `effect_allele` and `non_effect_allele`.

| Available statistics | Source selection |
| --- | --- |
| `zscore` | Declared Z takes precedence over P or BETA/SE |
| `pvalue` plus `beta`, `odds_ratio` or `beta_sign` | Signed normal quantile from two-sided P |
| `se` plus `beta` or `odds_ratio` | BETA/SE, using log(OR) when OR is mapped |

When `odds_ratio` is declared it **overwrites BETA**, including when Z is also
supplied. The original rounds every ingested Z through float32 before alignment;
the native workflow does the same. GWAS alleles are uppercased. Summary matching
uses exact allele pairs or swaps, with sign changes to Z and BETA. It does not
perform strand complementation; palindromic pairs follow that same contract.

`keep_non_rsid: false` retains identifiers containing the case-sensitive string
`rs`, as upstream does. Set it to `true` explicitly for other compatible model
identifiers. This flag does not remap coordinates or establish a genome build.

`pvalue_policy: "source-default-divergent-floor"` reproduces the original default
for infinite derived Z values: use the smaller of1e-50 and the smallest nonzero P
with a finite derived Z. Finite tiny-P values are not clamped. All-zero P inputs
have no eligible minimum and fail; supply valid Z or BETA/SE instead.
`"discard-divergent"` corresponds to the original `--input_pvalue_fix 0` behaviour.
Every nonfinite-Z exclusion is recorded. Literal `+` or `-` is required for
`beta_sign`; multi-cohort direction strings are refused. Finite Z with a missing
mapped effect is refused rather than yielding an admitted missing effect size.

The template explicitly selects uncalibrated values:

```json
"calibration": {"mode": "source-uncalibrated"}
```

**Uncalibrated values can be inflated.** For the source behaviour of release tag
v0.8.1, compatible models with complete `extra.phi`, and user-supplied study
parameters, select:

```json
"calibration": {
  "mode": "source-phi",
  "gwas_samples": 10000,
  "gwas_heritability": 0.3
}
```

Those numbers are illustrative. Missing phi for a reportable gene fails instead
of silently dropping the gene through the original inner join. Negative phi is
replaced by zero as upstream does; raw phi, applied phi and uncalibrated values
remain visible. No correction is retroactively applied to historical editions.

## Matrices and outputs

Covariance input uses the original header:

```text
GENE RSID1 RSID2 VALUE
```

The optional joint `model_product` input uses:

```text
gene model1 model2 value
```

Whitespace separates the columns. Schema2 accepts these required names in any
order and retains every unused column literally as `source_metadata` in
`MATRICES.jsonl`; for example, the deposited MESA covariance includes a fifth
`chr` column. Such metadata is captured but does not establish allele or assembly
identity. Schema1 still requires the exact four-column headers above.
Entries for each gene must be contiguous.
Supply one triangle with all required pairs, including the diagonal; reverse or
duplicate pairs are refused. Missing numeric entries are recorded as ignored.
Used axes reproduce the source's first-ID rule. A missing required pair is never
filled with zero. Matrices must be positive semidefinite and used model variance
must be finite and positive. Covariance is in raw dosage units and is assumed to
use the supplied model effect alleles; the files contain no independent allele
or assembly proof. Conflicting allele orientation across models is refused.

Summary variance is calculated from the **used SNP intersection**. Metadata
`n.snps.in.model` is retained separately from the actual number of model weights.
Gene identifiers retain their version suffixes. Joint tests use the supplied
model-product matrix, not a guessed substitute. All eligible tissue labels must
be present. Select one explicit cutoff kind: `condition-number`, `eigen-ratio`,
`trace-ratio` or `variance-threshold`. Optional nonnegative `regularization` is
applied after deriving the cutoff, as upstream does. The first singular component
is always retained. The Java CPU backend is selected explicitly.

When `additional_output: true`, `outputs/summary.tsv` also contains source
`best_gwas_p` (the two-sided normal P for the largest absolute used, rounded,
aligned GWAS Z) and `largest_weight` (largest absolute used model weight).
These use the SNP intersection before phi calibration; an ingested GWAS P
column does not replace the source's Z-derived calculation.

| Saved file | What to inspect |
| --- | --- |
| `outputs/summary.tsv` | Each reportable model/gene association, raw and calibrated statistics, actual/annotated SNP counts |
| `outputs/joint.tsv` | Joint statistics, retained components, tissue extremes and source status |
| `GWAS.jsonl` | Every input GWAS row and filter disposition |
| `MATRICES.jsonl` | Every input covariance/model-product entry |
| `MODEL-SNPS.jsonl` | Every model weight and its alignment/use disposition |
| `GENES.jsonl` | Every model/gene, including genes with no association |
| `OUTPUT-AUDIT.json` | Complete populations, exclusions, calibration changes, underflows and capacity estimates |

Log P values accompany tail P values. A P value underflow to zero is counted;
joint source status-4 is retained. `COMPLETED.json` is created only after complete
capture checks and numerical replay. `--verify` checks the full file/directory
namespace and bytes, then recomputes all dispositions and results; a resealed
changed statistic is not accepted merely because its new hash matches.

Capacities bound full populations, input/output/decoded bytes, numerical memory
estimates and work; exceeding a bound fails without truncation. `decoded_bytes`
includes the extra scan needed for the default P floor. Table records are limited
to1Mi characters and1024 columns. Memory estimates are conservative rather than a
JVM heap guarantee; wall time and cancellation checks are cooperative. Requests
for the native engine require `process: null`.

## Source scope and remaining work

The reference sources are unmodified [v0.7.3](https://github.com/hakyimlab/MetaXcan/tree/72e8c94edf31a4b261d29a7acf9a659804510e46),
[commit b133eb3, internal0.7.4](https://github.com/hakyimlab/MetaXcan/tree/b133eb36039a557d1476ead50fc6147734c998de),
and [release tag v0.8.1](https://github.com/hakyimlab/MetaXcan/tree/964f1fdb5bf9585585690e85bb0eca7b67663ddb).
The last tag declares internal package version0.7.5; this mismatch is retained.
The0.7.4 commit is not proof of the exact source commit used by an author who
reported only0.7.4. Original MIT notice is included in the source and executable.
See also the [arithmetic API and earlier validation](METAXCAN_ARITHMETIC.md) and
the [SQLite JDBC release](https://github.com/xerial/sqlite-jdbc/releases/tag/3.54.1.0).

Synthetic original-source controls cover GWAS ingestion, single-/multi-tissue
summary association and phi correction. Original individual prediction and
association controls are development evidence; their project execution adapter
remains unfinished. Observed upstream issues include suppressed prediction
exceptions, old text readers treating FID/IID as genes, the selected0.7.4
MulTiXcan entrypoint missing a phenotype argument, and HDF5 byte-string gene
labels under the tested environment. No original source was patched to hide them.
The [paper-role audit](METAXCAN_PAPER_ROLES.md) classifies all25 broad text matches
across the250-paper corpus. It separates eight MetaXcan-family observations
from two previously uncataloged PredictDB training observations. Catalog credit
is one **scoped native summary family** with two alias labels; it does not
certify a complete MetaXcan package, training pipeline or historical paper run.
PredictDB training remains a separately named missing integration. The wider
project goal remains active. The unfinished literature pages remain withheld
from the website.

**Deposited MESA example needs correction by its provider:** in the
[author's CC-BY4.0 deposit](https://zenodo.org/records/18644222), `TWAS.sh` selects
`SNP_ID`, but `EN-FM_model.db` has `rsid` and `varID` and no `SNP_ID` column.
The literal wrapper request fails in all three pinned original editions and
in this native command. Use an explicitly verified existing key with matching
GWAS/covariance identifiers, or obtain corrected author inputs; do not rename
columns by guesswork. The unchanged public database and covariance, combined
with synthetic GWAS statistics, were checked with all three original editions
and complete native captures on Windows/Linux. No cohort analysis was run.

The same deposit's `EN-FM_model.R` fails an R parse-only check at line9,
`library(doMC))`. Training was skipped without evaluating or patching it.
Obtain a corrected, licensed trainer and validate its complete workflow before
training. The selected older PredictDB tutorial's license page is an unfilled
template, which does not establish permission to install or execute its code.
Neither issue blocks the independently validated summary-association command.

**SKIP3:** dependent implementation/platform/SQLite support unavailable; supply
the named permitted prerequisite or retain the skip. **FAIL2:** invalid/changed
input, numerical, capacity, cancellation or finalization failure. Read the
prominent terminal diagnostic and retained `FAILURE.json`/`WARNINGS.md`; preserve
the attempt, correct the cause, and use new paths. Neither status admits a
complete result.
