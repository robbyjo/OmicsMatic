# Original SMR and HEIDI workflows

The development adapter executes the captured original [SMR](https://github.com/JianYang-Lab/SMR) 1.3.1 or 1.4.3 program as a separate process. It supports single-SNP SMR, multi-SNP SMR, the original and newer HEIDI procedures, explicit sample-overlap estimation, sparse/dense QTL input and a PLINK BED reference. Java checks the input population, source exclusions, complete returned probe family and single-instrument SMR arithmetic. HEIDI and multi-SNP tail fitting remain in the original program.

**Check the [workflow validation record](smr-workflow-development/VALIDATION.json) and [current catalog](omics-2023-2026/README.md) for the admitted scope and availability.** The earlier [native statistics checkpoint](SMR_STATISTICS_DEVELOPMENT.md) remains a separate retained component checkpoint. Compilation or a successful example alone does not establish a paper reproduction or completion of the 250-paper goal. The canonical executable has not been replaced.

**REQUIRE EXIT 0 AND `analysis-status.json` → `result_admitted=true`.** Inspect `WARNINGS.md`, `provenance.json`, every native ledger, `native-audit.json`, process receipt and original stdout/stderr before using a result. Output is a NEW directory; an existing directory is never overwritten.

**SKIP3 — DEPENDENT ANALYSIS SKIPPED.** The original workflow needs the exact permitted Linux x86_64 source, program and ELF loader/library closure. Windows Java explicitly skips; launch Linux Java inside a permitted Linux environment instead. If preparation, execution or an exact dependency is unavailable or forbidden, retain the warning bundle and skip the dependent analysis. The analysis command downloads and installs nothing, and substitutes no binary, source version, operating environment or estimator.

**FAIL2 — NO RESULT FROM THE ATOMIC BATCH IS ADMITTED.** This includes earlier cases that finished before a later failure. Correct the named input, source, permission, capacity or timeout cause and retry a NEW output path. Preserve original inputs, failed attempts, partial worker outputs, logs and cleanup receipts. Never repair identifiers/alleles, omit probes/variants to hide failures, replace missing/nonfinite fields or substitute a generic heterogeneity P value.

## Prepare a private runtime

The captured source commits are `21587e8091b37183e0d7bda418723fafb36c28d0` (1.3.1) and `eedb1a62ba4c66ab0b09acd0205876955a803f18` (1.4.3). Source labels do not identify historical paper binaries. In particular, P150's reported **1.03** is not silently interpreted as **1.3.1**.

Run from the repository root with Python 3.11 or newer. The helper exports the original source/program from the committed native-component evidence and captures matching local libraries into a NEW private directory. It needs no network access and performs no global installation. The original source program remains separately licensed under GPL-2 outside the Java JAR; its full corresponding source, build/dependency records and notices remain in the evidence bundle. The helper does not publicly redistribute host GNU libraries.

```powershell
python tools/literature/setup_smr_workflow.py --version 1.4.3 --out build/literature-review/smr-runtime/my-runtime --allow-prepare
```

On Windows the default permitted WSL distribution is `JLinAlg-Omics-SAIGE-20261026`; select an actual permitted distribution with `--wsl-distribution NAME`. On Linux the helper uses the local host. The exact profile was captured on Ubuntu 24.04 x86_64 with GCC 13.3.0; another host or updated library may not match. The helper writes actionable SKIP3 instead of treating a different host library as equivalent. Follow the saved `COMPLETED.json` `source` and `runtime` paths. `--allow-prepare` authorizes only this private preparation.

The original program's **`--help` exits 1** while printing its version and usage. The helper checks that exact behavior. It is a metadata health check, not a successful model fit. Actual model commands must exit 0 and pass complete output checks.

## Generate a runnable synthetic example

After private preparation succeeds, export the complete retained demonstration:

```powershell
python tools/literature/prepare_smr_workflow_example.py --runtime-home build/literature-review/smr-runtime/my-runtime --out build/literature-review/smr-runtime/my-example
```

The same helper runs on Linux. It writes a complete Linux request,512 synthetic reference samples,32 variants,5 probes and90 QTL observations, and declares separate single/multi-SNP cases. It prints the actual request path. Run that request with Linux Java as shown below. The example fits no human data, supplies no population/assembly inference and does not reproduce a catalog paper. Its NEW sibling `result` directory must report exit0 and `result_admitted=true`; read its warnings and ledgers.

## Launch an analysis

Use a current source build from a fresh checkout, or the separate development candidate recorded by the local `build_smr_workflow.py` receipt. An older canonical JAR does not contain this direct class. The additive development builder depends on ignored validated parent JARs in this workspace; it is not a fresh-checkout bootstrap script.

Inside Linux, run:

```sh
java -Xmx2g -cp /absolute/path/to/CURRENT-SOURCE-BUILD.jar org.jlinalg.cli.SmrWorkflowCli --request /absolute/path/to/request.json
```

Replace both paths with real files. `--help` prints the supported scope and required actions. All paths in a Linux request are Linux paths, including mounted Windows paths such as `/mnt/d/project/data/gwas.ma`. Do not run Windows Java with a Linux request.

The request is closed JSON with `schema_version: 1`, `mode: "ANALYZE"`, `profile: "original-SMR-Linux-x86_64-1.3.1-1.4.3"` and an explicit `version`. [SmrWorkflowRequest.java](../../src/main/java/org/jlinalg/cli/SmrWorkflowRequest.java) defines every field and numeric range. Unknown fields, arbitrary CLI options and scripts are refused. The following is a complete editable 1.4.3 request; replace paths, labels and biological units with your actual choices:

```json
{
  "schema_version": 1,
  "mode": "ANALYZE",
  "profile": "original-SMR-Linux-x86_64-1.3.1-1.4.3",
  "version": "1.4.3",
  "gwas": "/data/gwas.ma",
  "qtl": {"kind": "qfile", "file": "/data/exposure.qfile", "storage": "sparse", "precision": "source-binary32", "preparation_policy": "source-version-with-ledger"},
  "reference": {
    "kind": "plink-bed", "prefix": "/data/reference",
    "assembly_label": "GRCh38", "population_label": "describe the supplied LD population",
    "coordinate_policy": "require-equal", "missing_genotypes": "source-mean-impute-LD"
  },
  "probe_selection": {"kind": "all"},
  "summary_assembly_label": "GRCh38", "exposure_units": "describe QTL effect units", "outcome_units": "describe GWAS effect units",
  "allele_policy": "source-exact-or-swap",
  "source": "/absolute/path/to/my-runtime/source", "runtime": "/absolute/path/to/my-runtime/runtime", "out": "/data/NEW-SMR-result",
  "analyses": [{
    "id": "single-new", "kind": "single", "qtl_p": 5e-8, "window_kb": 2000, "minimum_maf": 0.01,
    "frequency_check": {"kind": "source-check", "maximum_difference": 0.2, "maximum_excluded_fraction": 0.5},
    "sample_overlap": {"kind": "none"},
    "heidi": {"method": "top20", "qtl_p": 0.00157, "ld_upper": 0.9, "ld_lower": 0.05, "minimum_snps": 3, "maximum_snps": 20, "skip_smr_p": 1, "maximum_ld_candidates": 500}
  }],
  "threads": 1, "timeout_millis": 900000
}
```

These demonstration cutoffs are declared settings, not inferred settings for a catalog paper. There is no automated decision that HEIDI non-rejection proves colocalization or causality.

## Input and source behavior

GWAS input has eight whitespace-delimited columns: `SNP A1 A2 freq b se p N` (`n` is also accepted as the last header). Qfile input has fourteen: `SNP Chr BP A1 A2 Freq Probe Probe_Chr Probe_bp Gene Orientation b SE p` (`se` is also accepted in that position). Positions are positive original 32-bit integers; chromosomes are literal integer codes 1–26. Identifiers and allele labels remain exact printable ASCII tokens; lowercase alleles, duplicated observations and inferred chromosome recoding are refused. Orientation is a literal single character `+`, `-` or `N`; this profile refuses longer labels such as `NA` instead of silently shortening them as the upstream character parser does. Alleles are matched exactly or swapped, with the corresponding sign/frequency reversal. There is no inferred strand complementation.

GWAS effects and positive SEs must be finite. Qfile effects and positive supplied/effective SEs must remain representable as finite, non-sentinel binary32 values, because original BESD storage quantizes them. Declare `preparation_policy: "source-version-with-ledger"`. Every supplied qfile row receives a quantization/preparation record. **Original 1.3.1 derives SE from beta and nonmissing P in [1e-150, 1), even when a positive SE is supplied.** P0 is changed to the minimum normal double and then keeps supplied SE; P1 and more extreme P keep supplied SE. **Original 1.4.3 uses supplied SE.** Java checks the actual declared version's preparation and records every raw SE/P, expected stored SE and change flag. It never rewrites the input to make the versions agree. Selection subsequently uses beta/SE after binary32 storage. Original ESI frequency output and ordinary result fields use six significant digits; P fields use seven. Java compares against those actual print precisions.

Frequency `NA` or `-9` remains unknown. The original program can skip pairwise comparisons involving zero/unknown frequencies; it does not infer a missing frequency. Source GWAS `p=NA` is converted to 0 by its original `atof`; the raw input is retained and this behavior is explicitly warned, not relabeled as a newly estimated P. Multi-SNP output instead recomputes the displayed GWAS P from beta/SE. Review input P provenance before interpretation.

For **legacy P-derived SE only**, the independent Java inverse-CDF calculation and original DCDFlib solver can round to adjacent binary32 numbers. The original `cdfchi` declares a relative root tolerance of `1e-8`. The audit admits at most one float ULP in that specific derivation branch and records every discrepancy with both exact stored values; all supplied-SE branches and beta values require exact binary32 identity. The original stored SE remains the analysis input. This is an explicit preparation-audit precision limit, not byte-identical inverse-CDF reproduction or permission to change a supplied SE. Larger discrepancies fail the batch.

An existing QTL dataset can use `{"kind":"besd","prefix":"/data/qtl","precision":"source-binary32","missing_policy":"source-sentinel-ledger"}`. The reader checks the complete EPI/ESI axes, paired row identities, column offsets, dimensions, byte length, duplicate observations and every value. Supported indicators are dense 0/5 and sparse `0x40400000`/3. Source missing sentinels are recorded, not replaced. Unsupported, truncated or trailing data fail the batch. Dense capacities include missing cells.

BED/FAM/BIM inputs retain all original calls and sample order. Native ledgers record called/missing counts and raw dosage variance; original mean imputation is used only for derived LD. Each reference allele's frequency is counted directly from the calls in the original operation order, preserving strict MAF boundaries after an allele swap. Reference X-chromosome frequencies use the original sex weighting; a selected X variant with no called genotype of FAM sex1/2 fails with an explicit metadata warning instead of inventing a weighted frequency. Assembly/population/unit labels are caller assertions. `require-equal` refuses reference/QTL coordinate disagreement. The explicit `source-ID-and-alleles-with-ledger` choice preserves original ID-based matching and records coordinate disagreement; it performs no liftover.

The source MAF filter is strict: frequencies equal to either cutoff are excluded. Frequency-difference exclusions and the maximum excluded proportion are separate settings; exceeding the proportion stops the original analysis. `frequency_check: {"kind":"disabled"}` maps to the original `--disable-freq-ck` option. No threshold is relaxed after an error.

The original GWAS allele swap complements a frequency only when it is strictly positive; zero and unknown tokens keep their source treatment. No native recoding to an inferred missing frequency occurs.

For `probe_selection`, choose all or `{"kind":"supplied","file":"/data/probes.list"}` with one exact probe ID per line. Unknown/duplicate IDs fail. Every source probe and QTL observation receives a disposition, including unselected probes, missing dense cells, absent/mismatched SNPs, source QC exclusions, outside-window observations, weak top QTLs and insufficient null SNPs.

## Analysis choices

Each of 1–16 cases has a unique portable ID, kind, QTL threshold, cis window, MAF, frequency policy, overlap policy and HEIDI settings. The original top-SNP selector replaces a previous candidate when absolute Z values differ by less than `1e-6`; this near-tie convention is retained. Dense BESD with an applied LD reference follows the original BIM order after source alignment/filtering. Sparse observations retain their file row sequence; summary-only alignment retains QTL/ESI order. Original BIM/BED and GWAS rows are captured unchanged, including when their order differs.

`heidi.method: "original"` declares `qtl_p`, `ld_upper`, `minimum_snps` and `skip_smr_p`; lower LD and maximum-SNP arguments are absent because that original path does not apply them. `top20` declares both LD bounds and minimum/maximum SNP counts. Only 1.4.3 declares `maximum_ld_candidates` (500–10,000). For 1.3.1 omit that field. The adapter maps the HEIDI skip threshold to the version's actual option (`--psmr` in 1.3.1, `--heidi-skip-thresh` in 1.4.3).

`heidi: {"method":"off"}` keeps original HEIDI NA output. In a single-SNP case, `minimum_maf` must be 0 because the source does not apply BED/MAF filtering. **The 1.3.1 source has an original null-LD-file close failure when no `--bfile` argument is supplied, even with HEIDI off.** A declared BED reference selects its BED dispatch path but supplies no applied LD/MAF filter for that case. A literal no-reference 1.3.1 attempt fails and admits no output; the original code is not patched and no dummy reference is invented. The 1.4.3 no-reference path guards that close.

For `kind: "multi"`, add `multi: {"window_kb":500,"ld_r2":0.1}`. This maps to `--set-wind` and `--ld-multi-snp`; `--smr-wind` would change a different source variable. The source extends the cis window if the set window is larger, then selects instruments around the top SNP. Multi-SNP mode requires an LD reference and uses the newer HEIDI path, so `heidi.method: "original"` is refused. Original selected-instrument and probe-region lists remain available.

For **single-SNP cases**, sample overlap is none or `{"kind":"estimate-original","null_p":0.01,"minimum_null_snps":2}`. The active original single-SNP path uses SNPs with both absolute Z values below its null threshold, skips probes with too few such SNPs and estimates their error correlation. Single-SNP ratio SEs use that correlation.

For **multi-SNP cases**, choose none or `{"kind":"source-flag-without-estimation"}`. **Both captured active multi-SNP entry paths leave correlation at 0 and do not apply null-SNP estimation/filtering, even when their overlap flag is present.** They print ratio SEs without overlap correction and pass the literal flag with correlation 0 to HEIDI. The separately defined correlation-estimating helper is not called by those active paths. The explicit flag mode preserves this source behavior and issues a prominent warning. `estimate-original`, null-P and minimum-null-SNP settings are refused for multi-SNP cases so they cannot promise an unapplied correction. No native correction or dummy correlation estimate is substituted.

## Capacity, outputs and interpretation

Input/table/variant/probe/sample/QTL/native-operation/output-file/output-byte limits are explicit request fields. The timeout applies to each original process; owned process descendants receive cleanup. These logical bounds do not impose operating-system RAM or disk quotas. A capacity, timeout or native-domain failure admits no atomic-batch result. Review the named cause and increase capacity only if permitted; never reduce the scientific population to make a failure disappear.

**Current ingestion limit: at most 512 MiB per complete text table.** Rows are retained in Java memory; the 2 GiB heap in the example is a demonstration setting, not a guarantee for every admitted logical size. Supply permitted heap/capacity for your actual dataset. Tables above the hard bound require a scalable-reader extension before this adapter can admit them. Input rows are never subsampled to fit a limit.

`inputs/` retains supplied bytes. `worker/prepare/` retains original QTL preparation. `worker/cases/CASE/` retains the complete original files/logs/process receipt plus native variant, observation and probe ledgers. `native-sample-order.tsv` records exact FID/IID order. `provenance.json` binds the request, input pins, source/runtime profile, reference sample count and audited cases. Only a completely admitted batch receives `results/`; unadmitted worker artifacts remain evidence.

An original HEIDI `NA`, nonfinite outcome or skipped test remains unavailable; it is not replaced by a generic chi-square test. The native `SmrStatistics` API separately exposes ratio arithmetic and a supplied-instrument covariance/deviation kernel. It does not implement a complete native HEIDI P value, reference construction, LD pruning, multi-SNP fit, historical paper environment or biological calibration.
