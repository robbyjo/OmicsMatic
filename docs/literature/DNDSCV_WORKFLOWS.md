# dndscv selection workflows and native Java coding methods

This development adapter runs the unchanged original [dndscv](https://github.com/im3sanger/dndscv) selection model, then audits its inputs and complete tabular results in Java. It supports explicit 192-, 12- and 2-rate substitution models, global/local/covariate selection output, optional original `genesetdnds` comparisons, supplied coding references, and declared coverage inputs. `SomaticCoding` also provides Java-only standard-code translation, coding annotation and 192-channel opportunity arithmetic.

**REQUIRE EXIT 0 AND `result_admitted=true`.** Read the attempt's `WARNINGS.md`, `analysis-status.json`, `provenance.json`, every `source-status.json`, original logs and `native-event-ledger.tsv` before using a result. The canonical executable and released package have not been replaced. Use a current source build or the separately hash-bound development candidate in [the validation record](dndscv-workflow-development/VALIDATION.json).

**SKIP3 — DEPENDENT ANALYSIS SKIPPED.** Upstream execution requires the exact permitted Windows R 4.6.1 / `x86_64-w64-mingw32` source and dependency profile below. Missing, forbidden or different software means skip dependent analysis. Linux upstream execution explicitly skips without Windows interoperability or another model fallback. Native Java methods and portable table audits need no R. Analysis installs nothing. Preparation requires explicit `--allow-install`; without it, preparation writes actionable SKIP3 and starts no installer.

**FAIL2 — NO RESULT FROM THE ATOMIC BATCH IS ADMITTED.** This includes earlier cases that finished before a later failure. Preserve original objects, warnings, failed attempts and cleanup receipts. Fix the named input, reference, model, source, capacity or timeout cause and retry with NEW output paths. Never drop observations, change an event cutoff to hide a failure, repair IDs/alleles, or replace `NA` / `NaN` / `Inf` with plausible values. The independently captured sample-event limit of 10 excludes the complete supplied population and fails upstream; that failure remains retained alongside a separately declared median-cutoff example.

## Prepare the original source and private runtime

The exact official commit is `43c5e2f1beaaf3ac26df9c046b281ceb0edbfff7`, whose DESCRIPTION labels the package **0.0.1.0**. P056 reports **0.1.0**; these labels are not interchangeable and no paper commit has been inferred. The GPL-3 original package remains a separate R program outside the Java JAR. Keep its `LICENSE.txt` and original notices with exported source.

From the repository root, export the committed source evidence without network access or installation:

```powershell
python tools/literature/export_dndscv_source.py `
  --out build/literature-review/dndscv-runtime/my-source
```

The exporter checks the saved official archive, all 60 members and their exact hashes before writing a new tree. It does not resolve the current upstream branch. Hashes establish captured-byte integrity; they are not signatures or proof of authenticity.

Provision these versions in permitted private dependency libraries. Repeat `--dependency-library` in the order R should resolve them. An existing installation with these packages can be used read-only; the helper installs the original dndscv source into a NEW library.

| Package | R `packageVersion()` |
| --- | --- |
| dndscv | 0.0.1.0 |
| seqinr | 4.2.44 |
| MASS | 7.3.65 |
| GenomicRanges | 1.64.0 |
| Biostrings | 2.80.2 |
| IRanges | 2.46.0 |
| Rsamtools | 2.28.0 |
| poilog | 0.4.2.1 |
| plyr | 1.8.9 |
| rtracklayer | 1.72.0 |
| jsonlite | 2.0.0 |

CRAN DESCRIPTION versions such as `seqinr 4.2-44` and `MASS 7.3-65` are displayed with dots by R's `packageVersion()`. This notation does not authorize a different release. Preparation checks every selected version, original function body/formals and packaged reference data. It does not prove identical implementations of every transitive dependency.

```powershell
python tools/literature/setup_dndscv_runtime.py `
  --source-home build/literature-review/dndscv-runtime/my-source `
  --rscript 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' `
  --dependency-library 'D:/permitted-private-R-library' `
  --out build/literature-review/dndscv-runtime/my-runtime `
  --allow-install
```

Replace the dependency path with an actual permitted library. This fresh-checkout helper downloads nothing, installs no dependencies, and modifies no existing library. If exact dependencies cannot be supplied or installation is forbidden, read its `WARNINGS.md`, retain the attempt and skip dependent analysis. The development intake/setup scripts retain the earlier permitted CRAN binary downloads and full inventories; their machine-specific library chain is not a fresh-checkout setup instruction.

## Run the supplied demonstration

Build the current sources with the repository's normal `executableJar` Gradle task, or use the separately validated candidate named in the validation record. The additive development builder depends on ignored validated parent artifacts and is intended for this development workspace.

```powershell
python tools/literature/prepare_dndscv_example.py `
  --reference-home build/literature-review/dndscv-runtime/my-runtime `
  --out build/literature-review/dndscv-runtime/my-example

java -Xmx2g -cp 'PATH-TO-CURRENT-SOURCE-BUILD-OR-DEVELOPMENT.jar' `
  org.jlinalg.cli.DndscvWorkflowCli `
  --request build/literature-review/dndscv-runtime/my-example/request.json
```

Replace the JAR placeholder with the actual artifact. The direct class is not dispatched by an old canonical JAR. `--help` prints its scope and required actions. The demonstration retains the complete original `dataset_normalskin` population: 3,408 events and 74 requested genes expanding to 75 source isoforms. Its declared `2r_3w` settings and infinite event limits are a reproducible demonstration, not inferred settings from a catalog paper. Output is a NEW sibling `result` directory.

## Supply your own mutation events

The request is closed JSON, version 1, `mode: "SELECTION"`, with profile `source-43c5e2f1beaaf3ac26df9c046b281ceb0edbfff7`, `genetic_code: 1` and `independent_events: "caller-asserted"`. The generated demonstration is a complete editable request. [DndscvWorkflowRequest.java](../../src/main/java/org/jlinalg/cli/DndscvWorkflowRequest.java) specifies every field, enum and capacity limit. Unknown fields and arbitrary R scripts/formulas are refused.

Mutation input is literal UTF-8 TSV:

```text
sample_id	chromosome	position	reference	alternate
clone-01	1	123456	A	T
```

The example above shows separators as `\t`; the actual file must contain tab characters. Positions are exact positive 1-based integers in the original 32-bit domain; nearly integral values are refused. Alleles are uppercase A/C/G/T sequences or a single `-`. Chromosome, sample and gene labels are literal: `NA`, `TRUE` and Unicode IDs remain strings. No chromosome-prefix conversion, liftover, allele orientation repair, sample matching or clone inference occurs.

Declare `duplicate_policy: "error"` or `"source-collapse-with-ledger"`. The latter retains every input row and records the original exact-duplicate collapse; different sample/clone IDs remain independent rows. Original identical-reference/alternate omissions, no-reference overlap, wrong reference bases, sample exclusions, within-gene exclusions with average rank ties, complex events and stop-loss changes all receive explicit ledger dispositions. Stop-loss annotations remain outside the four-class N matrix as in this source. Read/spot counts do not establish independent mutation events; the caller must supply the correct event unit.

Choose built-in `hg19` / `hg38` or an explicit `supplied-refcds` RDA with exactly `RefCDS` and `gr_genes`. Built-in unweighted opportunities are checked natively. Custom references require an `assembly_label` and an explicit `verify-unweighted-standard-code` or `as-supplied` opportunity choice. The latter captures and uses the complete supplied weighted L matrix; it does not infer its panel, blacklist or coverage construction. Canonical coding/splice bases are required for the native opportunity domain; unresolved flanks are counted explicitly. Nonstandard genetic codes are outside this profile.

Select all genes or supply a `gene_id` TSV. `CDKN2A` expansion requires explicit `source-expand-isoforms` for a built-in reference; all other labels remain exact. Custom numeric covariates (`gene_id` plus numeric columns) and per-gene coverage (`gene_id`, `coverage`) must match the complete selected source gene order. Built-in hg38 requests require an explicitly compatible covariate database; no hidden switch from hg19 is assumed. Cancer-gene lists are declared as `cgc81`, none or a supplied `gene_id` file.

Coverage is none, per-gene positive weights, or a supplied per-site BigWig. Duplex coverage requires both event limits explicitly `"Inf"`; no source overwrite is silently accepted. The adapter copies the unchanged coverage bytes to an owned relative path, avoiding UCSC's interpretation of Windows `D:` as a URL scheme. The separately retained original absolute-path failure and successful relative-path result are both evidence. **Original site-depth integer coercion is retained; fractional depths can truncate and overflow can yield NA. Review the original warnings and supply appropriate depth units. Site extraction and coverage-weighted L are not independently recomputed in Java.** A per-gene scalar is not a substitute for P036's sample/tissue/cohort channel-specific median-coverage construction.

Each of 1–16 cases declares its ID, seed, model, both event limits, indel settings, covariate limits, truncation constraint, output depth, one-sided choice and gene-set comparison. One-sided selection requires an explicitly constrained truncation choice. Optional `gene_sets` TSV has `gene_set` and `gene_id` columns and is present exactly when at least one case requests comparison. The original selected-gene and remaining-gene comparison tables are retained; no pathway membership is inferred.

## What Java checks and what remains upstream

Java checks complete input identity, selected reference order, coding coordinates on both strands, codon/amino-acid consequences, 192-channel unweighted opportunities, event dispositions, N counts, stated L identities, gene totals/expectations, Poisson response/offset/inverse-link identities, ordinary BH families, and every emitted JSON/TSV cell. One-dimensional original R array columns retain their class, dimensions and dimension names alongside the complete RDS. Floating-point tokens use 17 digits; arrays use lossless big-endian IEEE-754 doubles. Missing/nonfinite values have separate kind flags.

Original optimizers, selection likelihoods, confidence intervals, negative-binomial fitting, gene-set fitting and site coverage extraction remain upstream. Source numerical warnings and nonfinite metadata stay visible. This audit does not independently refit those models or establish biological calibration. Runtime limits bound logical events, tables, bytes, files and elapsed time; they are not OS RAM/disk quotas. Timeout/growth failures kill owned workers and retain cleanup receipts.

[SomaticCoding.java](../../src/main/java/org/jlinalg/genetics/SomaticCoding.java) exposes Java-only `translate`, `consequence`, `channel`, `channelLabel`, `Gene.annotate`, `Gene.opportunities`, and explicit `weighted` opportunities. It uses standard genetic code 1 and four impacts (synonymous, missense, nonsense, splice); stop loss and unresolved context have separate counters/dispositions. Arrays are copied defensively. Translation of unresolved IUPAC codons returns `X`; complete unambiguous coding/splice sequences are required for opportunity enumeration. These methods do not fit selection models.

## Paper-specific limits and evidence

Seven primary roles and eleven supporting paragraphs are audited separately. P042's IntOGen 2023 wrapper version is not a dndscv release; seven-method consensus, weighting and driver-role classification are separate. P056's mouse reference, P165's GRCm39 clone-level events and P036's coverage-adjusted panel must be supplied from actual provenance. No human-reference fallback or read/spot-count substitution is allowed. P111's postfilter text gives both “eight or fewer” and “at least eight”; the boundary is unresolved and no threshold is silently chosen. P146's figure describes pentanucleotide pathway models; the 192-channel trinucleotide interface does not establish equivalence. P148's any-method FDR, hotspot and role-classification logic is downstream of this standalone adapter.

The portable verifier checks sealed source, complete typed results, failure protocols, original comparisons and candidate-byte bindings without R. Opaque complete model/return/reference RDS objects are retained unchanged in the development workspace; their file hashes and omission receipts are public where the evidence archive omits these large opaque objects. Complete scientific tables, N/L arrays and model diagnostic transports remain public. See [VALIDATION.json](dndscv-workflow-development/VALIDATION.json) for the exact admitted scope and artifact and run `python tools/literature/dndscv_workflow_evidence.py` from a checkout. Source hashes and toy/reference comparisons do not establish whole-paper reproduction, every feature of the original package, or completion of the 250-paper implementation goal.
