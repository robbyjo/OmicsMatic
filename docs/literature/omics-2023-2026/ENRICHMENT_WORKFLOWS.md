# Native enrichment scores and original R workflows

**AVAILABLE IN THE VALIDATED LOCAL EXECUTABLE.** The isolated build passed
1,461 tests with zero failures/errors and five optional skips; all 55 required
suites ran without skips. The current gate includes 16 enrichment API/CLI tests
and 897 independent original-R arithmetic cases. This executable also passed
37 packaged enrichment calls/11 check groups and all 23 prior executable
regression groups. The enrichment calls comprise 29 original inferential APIs,
six native score runs and two explicit empty/dependency cases.

The unreleased local artifact is
`build/literature-review/validated-cli/jlinalg-0.3.7.jar`, SHA-256
`5680cbc4e457007f8903d1d4db3e74ecc890a150b350870f0b82d5650bdc0fec`.
See [current adapter evidence](enrichment-adapter-validation.json),
[native arithmetic evidence](enrichment-native-validation.json), and
[actual executable checks](enrichment-packaged-validation.json).


`enrichment-tools` provides native Java running scores, original clusterProfiler
overrepresentation/GSEA, original fgsea GSEA, and an explicitly separate legacy
DOSE generic API. Supply local gene sets and exact gene identifiers. Each engine
keeps its original statistics and raw R object; the adapter independently checks
identities, counts, score arithmetic and the reported testing family.

**Dependency unavailable or installation forbidden: skip the affected analysis.**
Exit **3** and `WARNINGS.md` identify the dependency and recovery command. An
analysis command never installs packages. Input, execution and audit failures
return **2**, retain diagnostics and move original partial outputs under
`partial/`; there is no successful `results.tsv` after failure. Inspect every
warning before using results. Native `--method score` requires only Java.

## Choose the method and engine explicitly

| Mode | Engine/convention | Validated operation |
|---|---|---|
| `score` | `fgsea` | Indexed running score using fgsea integer-weight preparation; balanced absolute extrema select zero. |
| `score` | `enrichit-multilevel` | Prepared integer weights with the current enrichit positive balanced-extremum convention. |
| `score` | `enrichit-running` | Dense raw-power running helper with separate hit/miss accumulation and the original negative tie convention. |
| `ora` | `clusterprofiler` | Original clusterProfiler 4.20.3 `enricher` on supplied memberships and universe. |
| `gsea` | `clusterprofiler` | Original 4.20.3 `GSEA`, using enrichit 0.2.6; choose `multilevel`, `permute` or `sample`. |
| `gsea` | `fgsea` | Original fgsea 1.38.0 `fgseaMultilevel` or `fgseaSimple`. |
| `ora`/`gsea` | `dose` | Separate original DOSE 4.5.0 legacy `enricher_internal`/`GSEA_internal`; GSEA algorithms `multilevel`, `simple` or `dose`. |

Native running scores provide **no NES, null distribution, P-value or inferential
significance**. The native `GseaStatistics` API also supplies one-sided log
hypergeometric tails and BH/BY/Holm/Bonferroni adjustments. It does not implement
the complete fgsea, clusterProfiler, DOSE, VIPER or aREA software suites.

The native API can also be used directly from Java:

```java
import org.jlinalg.enrichment.GseaStatistics;

double[] ranked = {3, 1, -1, -3}; // Already in descending rank order.
var prepared = GseaStatistics.prepareWeights(
    GseaStatistics.integerWeights(ranked, 1));
var walk = prepared.walk(new int[] {0, 1},
    GseaStatistics.Tail.STANDARD, GseaStatistics.ExtremumTie.ZERO);
double score = walk.score(); // 1.0; selected positions are zero-based.
double logOraP = GseaStatistics.logHypergeometric(100, 3, 10, 3);
```

Reuse `prepared` for additional sets on the same ranked weights. `logOraP` is a
separate ORA tail for its explicitly supplied population/query/set/overlap
counts; it is not a P-value for the running score above.

The main dependency profile contains original **DOSE 4.6.0**, whose generic
interfaces have changed. The legacy 4.5.0 profile is a separate, named library;
the main package is never silently downgraded. These profiles do not reproduce
the papers’ historical 3.x/4.x versions or full analyses.

## Set up permitted private runtimes

The managed original-engine profile requires **Windows x86_64 and R 4.6.1**.
The installers write only the requested new private library and report folders.
They retain original archives, repository checksums, package versions, file
SHA-256 seals and installation logs. Existing unmanaged, changed, incomplete or
unsealed libraries are preserved; fix the cause and choose a new library path.

From the project directory in PowerShell:

```powershell
$taskRscript = 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe'
& $taskRscript --vanilla tools/literature/install_enrichment_dependencies.R `
  build/omics-enrichment-r build/literature-review/enrichment-dependency allow
# Only for --engine dose, after the main profile succeeds:
& $taskRscript --vanilla tools/literature/install_dose_legacy.R `
  build/omics-dose-legacy-r build/omics-enrichment-r `
  build/literature-review/dose-legacy-dependency allow
```

Replace `allow` with `forbid` to prohibit installation while permitting an intact
sealed profile to be checked and reused. If setup returns 3, read the report’s
`WARNINGS.md`, `dependency-status.yaml` and named attempt log. Supply a permitted
matching profile before retrying; do not interpret an installation/version probe
as scientific validation. The tested closure contains 127 main-library packages.
The installers resolve the other required dependencies from their configured
repositories and seal that local closure. Published scientific evidence names
the exact tested marker and package versions; validate a newly resolved closure
before interpreting its results.

Pass `--rscript`, `--r-library` and, for DOSE, `--dose-library`, or set
`JLINALG_ENRICHMENT_RSCRIPT`, `JLINALG_ENRICHMENT_R_LIBRARY` and
`JLINALG_DOSE_LEGACY_R_LIBRARY`. The adapter checks exact package pins, all sealed
files and the complete library inventory. Original execution uses serial
BiocParallel and explicit RNG/seed settings. Character handling is explicitly
UTF-8 while numeric and collation rules remain C; literal `NA`, leading-zero
identifiers and Unicode descriptions are preserved.

## Input contracts

`--memberships` is either a two-column TSV with header `term<TAB>gene`, or GMT
with one term, description and its member genes per line (`--set-format gmt`).
For TSV, an optional two-column `--term-names` file has header `term<TAB>name`.
Missing descriptions default to the term identifier. Duplicate memberships require
`--duplicate-membership error|collapse`; duplicated ranking/query/universe gene
IDs fail. Identifiers are literal and case-sensitive. A `/` in a gene ID must
first be replaced through an explicitly retained bijective alias map because
the original generic APIs use `/` to delimit core-enrichment genes.

Ranked input is `gene<TAB>statistic` TSV, or headerless two-column RNK using
`--ranking-format rnk`. Statistics must be finite; the adapter sorts descending.
Choose `--ties error|input|gene-id`. Tied ranks can change scores, leading edges
and inference; changing the tie order is a scientific protocol choice.

ORA query and universe files each have one column headed `gene`; selected genes
must be a subset of the supplied universe. Set `--annotation-id` to the exact
local resource/edition identity. The adapter performs no online annotation lookup,
GO traversal, KEGG/MSigDB download, orthology mapping, gene selection or rank
construction. Those upstream resources and preprocessing choices must be supplied.

Always choose `--min-size`, `--max-size`, `--unmapped-members error|drop` and
`--empty error|retain`. Audits retain membership omissions and all term-size
dispositions. Explicit empty retention produces a header-only table and
`completed-no-tested-terms`; the original inferential API is not called when
there are no eligible hypotheses. Runtime availability is still checked.

## Native running scores

Use the validated executable built from these sources; `--help` lists all options.
Every output directory must be new. A native command needs no R flags or seed:

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar enrichment-tools `
  --method score --ranking ranking.tsv --ranking-format tsv `
  --memberships memberships.tsv --set-format tsv --term-names names.tsv `
  --annotation-id my-gene-sets-v1 --min-size 10 --max-size 500 `
  --duplicate-membership error --unmapped-members drop --empty error `
  --ties error --exponent 1 --tail std --zero-weight error `
  --score-convention fgsea --out output/native-scores --no-config
```

`--zero-weight error` rejects a set whose selected power weights sum to zero.
With explicit `original`, fgsea uses uniform selected-hit weights, whereas the
raw enrichit helper adds no hit mass. Both outcomes are retained with prominent
warnings. They are different conventions, not interchangeable repairs.

The indexed native helper validates prepared weights once and reuses the sorted
gene index across sets. Dense `enrichit-running` uses the original separate
accumulators to preserve near-balanced floating-point behavior. Bound dense work
with `--max-score-cells`; changing the bound does not change the statistic.

## Original ORA

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar enrichment-tools `
  --method ora --engine clusterprofiler --rscript $taskRscript `
  --r-library build/omics-enrichment-r `
  --selected selected.tsv --universe universe.tsv --universe-policy supplied `
  --query-unmapped error --memberships memberships.tsv --set-format tsv `
  --term-names names.tsv --annotation-id my-gene-sets-v1 `
  --min-size 10 --max-size 500 --duplicate-membership error `
  --unmapped-members drop --empty error --seed 77 --p-adjust BH `
  --p-threshold 0.05 --undefined error --out output/ora --no-config
```

`--universe-policy supplied` keeps the declared background; `annotated` restricts
it to annotation members. `--query-unmapped error|drop|original` controls selected
genes lacking effective annotation. Under `original`, **legacy DOSE counts only
annotated query genes, while current clusterProfiler counts query genes in its
effective universe**. These denominators can yield different P-values from the
same supplied list. Review `query-audit.tsv`, `membership-audit.tsv`, `term-audit.tsv`
and the reported GeneRatio/BgRatio counts before interpretation.

Original zero-overlap terms are excluded from that engine’s adjustment family.
Every excluded term is recorded. The Java audit independently checks the
hypergeometric log tail and recomputes adjustment for the complete reported
family. It does not invent tests for omitted terms.

## Original GSEA

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar enrichment-tools `
  --method gsea --engine fgsea --rscript $taskRscript `
  --r-library build/omics-enrichment-r `
  --ranking ranking.tsv --ranking-format tsv --ties error `
  --memberships memberships.tsv --set-format tsv --term-names names.tsv `
  --annotation-id my-gene-sets-v1 --min-size 10 --max-size 500 `
  --duplicate-membership error --unmapped-members drop --empty error `
  --exponent 1 --tail std --zero-weight error --seed 77 --p-adjust BH `
  --p-threshold 0.05 --undefined error --algorithm multilevel `
  --eps 0.00001 --sample-size 101 --simple-permutations 500 `
  --out output/gsea --no-config
```

Multilevel requires explicit `--eps`, odd `--sample-size` and
`--simple-permutations`. Other algorithms require `--permutations` instead.
`--tail std|pos|neg` is available for fgsea and current clusterProfiler multilevel;
clusterProfiler fixed permutation/sampling and the legacy DOSE permutation
algorithm use the standard score only. These are gene-set/rank null models,
**not phenotype-label permutations**.

Current clusterProfiler uses enrichit, so its balanced-extremum convention and
inference differ from fgsea. Legacy DOSE recomputes core-enrichment genes with
its own raw running helper even when its fgsea backend reports ES zero. That
helper can return a nonempty negative core; its negative first-hit boundary can
also exclude the first hit. `legacy-leading-audit.tsv` and the retained upstream
hit-prefix table expose this exact original behavior. Do not infer a different
leading edge from the sign of the reported ES or silently replace the core.
For a declared positive or negative tail, current clusterProfiler and fgsea can
also retain directional core genes when the reported ES is zero. The audit
checks that tail’s original boundary and warns explicitly; a nonempty core is
not positive evidence of enrichment or significance.

## Undefined results, adjustment and recovery

`--p-adjust BH|BY|holm|bonferroni|none` applies to the declared complete family.
Original package-adjusted P-values are retained in their own column. Storey
q-values are a **different quantity**; optional `--q-threshold` is available only
for the generic clusterProfiler/DOSE APIs. Missing Storey q-values are retained
as NA and never replaced by BH values. If a q threshold was requested, those
rows have no significance flag under explicit retention.

Undefined NES/P, zero-resolution P or an omitted eligible hypothesis causes
`--undefined error` to fail with the original object retained. With explicit
`--undefined retain`, these rows remain unreportable, and native adjusted
P-values/significance flags are suppressed for **the entire incomplete family**.
Zero original P is not proof of zero probability; finite ORA log tails are kept
separately without repairing the original P. A multilevel P at `eps` is flagged
as an original floor, not a more precise probability.
The actual current-clusterProfiler negative-tail fixture omitted two size-eligible
sets after original invalid-P warnings. Strict mode refused it; explicit
retention keeps the omissions visible and suppresses adjustment/significance for
the incomplete family. A successful engine process alone does not establish a
complete, reportable analysis.

Read `WARNINGS.md`, `session.json`, `manifest.yaml`, `reported-family.tsv` and
`upstream/metadata.json`. Raw `upstream/original-object.rds`, results, rankings,
actual prepared weights and leading edges retain the original engine evidence.
Revise the named universe, rank/null model or sampling resource issue, then rerun
to a **new** output directory. The byte/gene/link/term limits and `--timeout` bound
this workflow; they are not a guarantee of all internal R-package memory use.

**VIPER/aREA validation is pending license eligibility.** The catalog records the
explicit P134 package t-test, separate metaVIPER transformation and aREA method.
No restricted VIPER runtime was installed or run. Its
[official license](https://bioconductor.org/packages/3.23/bioc/licenses/viper/LICENSE)
restricts eligible use; an applicable eligibility response is required before
original-engine validation. These missing tools are not covered by native GSEA.

The source audit records 36 individually reviewed uses across 33 papers. Many
papers use `enrichGO`, historical packages, custom ranks or separate downstream
analyses; the local generic interfaces cover only the operations described here.
Paper-wide semantic completeness and implementation of other tools remain open.

## Reproduce the executable fixture checks

The five small synthetic input files are retained under
`tools/literature/fixtures/enrichment/`, with source and normalized SHA-256 hashes.
On a fresh checkout, prepare them before running the executable verifier:

```powershell
python tools/literature/prepare_enrichment_workflow_inputs.py
python tools/literature/verify_enrichment_workflows.py `
  --jar build/literature-review/validated-cli/jlinalg-0.3.7.jar `
  --rscript $taskRscript --library build/omics-enrichment-r `
  --dose-library build/omics-dose-legacy-r `
  --report build/literature-review/enrichment-runtime/local-packaged-validation.json
```

Preparation needs no R, download or analysis. It creates new fixture files or
reuses matching LF/CRLF originals without changing any existing byte. Changed or
incomplete existing inputs fail with recovery instructions. Preserve them and
select a new destination for diagnosis; the workflow verifier uses the default
fixture location shown by the preparation command.
