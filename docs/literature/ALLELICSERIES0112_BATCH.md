# Batch original allelic-series methods

**Warnings and required actions:** the selected original package declares `0.1.1.2`; P204 reports `1.1.2`, and exact build equivalence remains unproven. Missing/forbidden runtime or execution is **SKIP3**, which skips dependent analysis. **FAIL2 admits no complete batch**. Preserve all outputs, read the full batch index, original conditions and admission reasons, correct the named cause where permitted, and retry NEW paths. The original package is unchanged. This development workflow does not establish paper reproduction or quantitative/binary-trait calibration.

Use the [original provider setup](../../tools/literature/allelicseries0112/README.md) to obtain a ready private Linux/WSL R4.5.3 library. The batch adapter runs one original function export for every requested gene in a single R process. It verifies the provider once before and once after the full batch. It does not reinstall or restart R per gene.

## Create a typed batch request

The input is a named R list of complete named argument lists. Each identifier must be unique, nonmissing and nonempty. Whitespace, case, leading zeroes, punctuation and version suffixes are preserved. No alias mapping, variant alignment, gene grouping or category repair is performed. Establish matching variant/effect-allele order between effects, SEs, MAFs and LD before creating real requests.

This small synthetic example explicitly chooses three annotation categories and two genes:

```r
gene_a <- list(anno = c(1L, 2L, 3L), beta = c(.1, -.2, .3),
               se = c(.2, .2, .2), maf = c(.001, .002, .003),
               ld = matrix(c(1, .1, 0, .1, 1, .1, 0, .1, 1), 3, 3),
               weights = c(1, 2, 3))
gene_b <- list(anno = c(1L, 1L, 2L, 3L), beta = c(.2, .1, -.1, .3),
               se = rep(.2, 4), maf = c(.001, .003, .002, .004),
               ld = diag(4), weights = c(1, 2, 3))
requests <- list("ENSG000001.1" = gene_a, "ENSG000002.7" = gene_b)
saveRDS(requests, "my-new-gene-requests.rds", version = 3)
```

These are example inputs, not recovered author settings or biological results. COAST-SS is derived for quantitative phenotypes; the paper leaves binary/discrete-trait calibration as further work.

```bash
python3 tools/literature/allelicseries0112/batch.py \
  --provider build/my-ready-original-provider \
  --function COASTSS --arguments-rds my-new-gene-requests.rds \
  --seed 97744 --maximum-calls 25000 --allow-analysis \
  --out build/my-new-allelic-series-batch
```

Replace the provider path with your actual successful setup. Use a NEW result path. `--maximum-calls` is an explicit complete-output capacity, defaulting to100,000 and accepting1..1,000,000. Exceeding it refuses the entire batch before original calls; no prefix is accepted. The capacity is not a runtime/performance guarantee. The selected function can be any of the16 original exports listed in the workflow README; each entry must supply that function's original arguments.

An explicit seed initializes the original RNG stream once before the first call. The input order is retained, and each packet records before/after RNG state. Changing order or partitioning stochastic requests can change results. Omitting the seed preserves the original unseeded behavior.

## Inspect complete returns and conditions

`BATCH_INDEX.tsv` is a quoted TSV containing every literal identifier in input order, its ordinal directory, original return status, admission flag and condition counts. Use a delimited-text reader that supports quoted tabs, newlines and doubled quotes. The complete typed index is also retained as `BATCH_INDEX.rds`.

`entries/000000001`, `entries/000000002`, and so on contain the full original packet, exact argument values, original returned object or error condition, source warnings/messages, RNG state, status and admission reason. Identifiers are data, never directory names. The original whole-batch RDS is retained byte for byte. COAST tables and numeric matrices also receive quoted human-readable projections with17-digit numeric text and distinct `NA`/`NaN`/infinity spellings; RDS remains authoritative for types and attributes.

Original warnings are counted prominently in command output. `BATCH_WARNINGS.md` lists every affected entry and original condition, with the required actions. A source warning such as omitted LD preserves the original return while requiring the user to review and justify/correct that input/model choice.

Original errors, nonfinite returns, and confirmed or unproven low-level scalar-zero returns prevent complete-batch admission. Every completed entry remains retained, including successful individual returns. Do not relabel that failed batch as complete or silently remove failed genes. Read all recorded reasons, correct the actual causes and retry a new batch. A crash before the final batch record leaves explicitly incomplete artifacts and raw progress; they admit no complete batch.

The low-level original `BaselineSS` and `SumCountSS` return scalar zero before computing an association when no positive SEs remain. That raw zero is retained and explicitly withheld from association admission. The adapter resolves original abbreviated argument names without evaluating an expression twice. A zero probability actually computed with explicit positive SEs is preserved as an admissible original return. If a low-level zero's sentinel status cannot be established from typed arguments, it remains unadmitted; use explicit numeric SEs to establish the case.

The [earlier checkpoint](ALLELICSERIES0112_DEVELOPMENT.md) retains its original sources, failures and qualification. Native Java burden code, original package sources, the250-paper main catalog and the canonical executable are unchanged by this successor. Broader model/utility coverage, packaging, offline replay and catalog admission remain open; the full goal remains active.

## Saved qualification

The complete batch qualification compared1,026 calls with an independently installed unchanged original package. All1,022 returned objects matched the original R values, types and attributes; four original errors matched classes/messages, with full original conditions retained from both executions. Every literal identifier, input argument list, original condition and before/after RNG state was compared. This includes1,000 varied COASTSS requests, mixed failed/nonfinite cases, a shared-stream DGP sequence, and baseline/sum low-level edge cases. These are synthetic controls, not paper analyses.

The1,000-request batch ran in one original R process, retaining all1,000 entries and296 original warnings across280 entries. Eight invalid-schema/capacity/policy/overlap controls, five single-call scalar/real-zero controls, and lossless numeric-table/identifier controls passed. The common protocol also passed fresh full-object comparisons for all16 original single-call exports and the earlier seven refusal/skip plus warning-visibility controls. No whole-genome runtime or broad trait-calibration claim follows from these controls.

The initial fixture generator reused two filenames for different test structures. Validation rejected those malformed batch inputs before original calls; the attempt remains a failure. The generator was corrected, new reference/control directories were created, and the scientific inputs for the1,000-request, mixed and stochastic groups were confirmed byte-identical. The entire corrected suite was executed anew. The earlier source snapshots, wrong-shaped fixture files, actual results, refusal records and failed comparison are retained.

[Qualification record](allelicseries0112-batch-development/VALIDATION.json), [complete member inventory](allelicseries0112-batch-development/CONTROLS_LAYOUT.json) and [evidence archive](allelicseries0112-batch-development/controls.zip) retain current sources and all successful/failed attempts. The archive embeds the earlier checkpoint's complete evidence, including original package/dependency sources and native controls. It is not a redistributed R prefix or an offline original refit. The full paper/tool goal remains active; this successor adds no catalog badge.
