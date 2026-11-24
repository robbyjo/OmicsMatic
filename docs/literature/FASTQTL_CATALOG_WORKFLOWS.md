# FastQTL catalog workflows and paper uses

The guarded admission supplies **one scoped upstream family and five source
reviews**, preserving every existing observation and earlier review. Its target
state is **159 families (30 native, 129 upstream), 1,760 reviewed uses and 964
pending uses**, across 250 papers and 2,724 observations. Whole-tool, whole-paper
and full-goal completion are not claimed.

**Warnings and required actions:** use a current source build and the pinned
Ubuntu `2.184+v7+dfsg-4build3` runtime under Linux x86_64/WSL. Exit **3** skips a
dependent analysis whose exact edition, permitted runtime/input or qualified
capability is unavailable. Install/stage prerequisites only by a method your
environment permits; if installation is prohibited, leave the analysis skipped.
Exit **2** supplies **no complete result**: preserve partial/raw/nonfinite files,
read `FAILURE.md`, `WARNINGS.md`, `result-audit.tsv` and `scientific-audit.json`,
fix the named cause and retry a **new output directory**. Existing release assets
have not been replaced. Cell Ranger remains excluded.

## Available computation

[`fastqtl-tools`](../FASTQTL_WORKFLOW.md) runs prepared nominal and fixed/adaptive
permutation passes, with paired BGZF/TBI checks and exact sample-ID sets. Its
profile supports one-chromosome biallelic SNVs and optional finite numeric
covariates. DS-before-GT selection, mean imputation, float arithmetic, normal
transformation, covariate correction, rounded-dosage MAF and beta fitting remain
upstream. Beta-adjusted/empirical P values are **not FDR/q-values**. Java preserves
raw output; printed nonfinite statistics remain failed and unadmitted. No-cis
rows represent no result. Native Windows upstream fits are skipped.

The qualification records eight direct-original byte comparisons, two fresh
VERIFY/REPLAY fits, 240 independent R statistics, fifteen actual refusal controls
and thirty selected Java tests. This finite synthetic validation does not
establish all source variants, historical editions or biological calibration.

## Individually reviewed roles

| Paper | Use and boundary |
|---|---|
| [P019](https://doi.org/10.1038/s41586-026-10836-6) | PEER-factor-count selection over 5–50 factors using the significant-eQTL yield **closest to 95% of maximum**, with BH-adjusted two-sided P ≤0.05. Repeat within subcohorts and after combination. Ties, exact yield unit, edition and flags remain unresolved. No first-above-threshold replacement or complete PEER optimization is supplied. |
| [P157](https://doi.org/10.1038/s41588-024-01896-3) | GTEx-based eQTL mapping with 60 PEER factors, sex and five genotype PCs. Code availability declares **FastQTL 2.165**, repeated in Associated Data; its original nearby Methods version capture stays null. Disease-stratum fixed-effects meta-analysis is separate. **2.165 is unqualified: obtain/qualify that exact edition and prepared inputs, or keep the dependent fit skipped. Never substitute 2.184.** |
| [P170](https://doi.org/10.1038/s41588-025-02344-6) | Prepared donor-level PBMC profiles; cis ±1 Mb from TSS, 1,000 permutations/locus, mapping Q<0.05. Averaged NP/P profile prioritization uses a separate Q<0.10 comparison. Preserve the source's nominal-Q terminology and reported rank-normal/residualization preparation. Do not infer double correction or an unspecified Q-value method. Edition and original inputs remain unavailable. |
| [P187](https://doi.org/10.1016/j.ajhg.2025.11.009) | caQTL ±1 kb of peak centers; eQTL ±1 Mb of TSS; MAF≥0.1. caQTL uses sex, two genotype PCs and TSS enrichment, omitting ATAC PCs. eQTL uses sex, two genotype PCs and expression PCs by cell type (2/3/6/2/3/6). BH adjusts beta P separately at FDR<0.05. Preprocessing and LD clumping are separate. The paper describes biallelic variants; the available adapter's SNV scope is not assumed equivalent to all variant types. |
| [P212](https://doi.org/10.1016/j.ajhg.2025.09.003) | Reuse of public pancreatic-islet FastQTL results for colocalization; **no new local FastQTL run is established**. Lack of isolated islet signals motivates a one-signal-per-gene assumption. APEX conditional analyses concern adipose/liver, not conditional FastQTL mapping. |

All ten primary anchors and every original near-name capture remain intact.
The machine-readable [source roles](fastqtl-catalog-sources/ROLES.json) distinguish
reported settings, contextual versions, repeated declarations, prepared operation
types and remaining work. The earlier [pre-admission audit](FASTQTL_SOURCE_ROLES_2026-11-24.md)
and [workflow checkpoint](fastqtl-workflow-checkpoint/README.md) retain their
historical status. The current catalog proof separately records admission.

## Guarded admission and verification

`tools/literature/close_fastqtl_catalog.py` requires the exact FastQTL producer,
frozen primary XML cache, pre-admission workflow source context and historical
cytometry context/producer. Every output is new and under this checkout's build
directory. For a Windows-hosted linked checkout under WSL, map `GIT_DIR` and
`GIT_WORK_TREE` to their actual Linux paths before using Git-based catalog
commands; preserve the original Windows `.git` pointer. A separate Linux checkout
uses its ordinary Git paths. Example Linux/WSL paths, from the checkout root:

```sh
python3 -B tools/literature/close_fastqtl_catalog.py --plan \
  --out "$PWD/build/fastqtl-catalog-plan" \
  --cache /absolute/path/to/frozen-primary-XML-cache \
  --jar /absolute/path/to/exact-fastqtl-producer.jar \
  --workflow-source-root /absolute/path/to/pre-admission-fastqtl-source \
  --historical-root /absolute/path/to/unchanged-cytometry-catalog-source \
  --historical-jar /absolute/path/to/exact-cytometry-producer.jar
```

Repeat the same prerequisites with `--inspect --plan-dir ... --out NEW` before
`--apply --plan-dir ... --out NEW`. After admission, use `--verify --out NEW`.
Optional `--private-root /absolute/path/to/fastqtl-701` checks all retained
workflow evidence; otherwise that private-file count remains zero. These stages
validate saved scientific qualification and fresh primary sources, not new
paper-cohort fits. A changed plan, source, before-state or manifest refuses;
postcondition failure preserves concurrent edits and failed new metadata.

Use the exact preserved physical context when invoking an older checkpoint
reader. Its source/canonical hashes intentionally describe the state before
admission; running it against newly edited catalog files is not a valid historical
check. Never refresh those old hashes. Retain exact producer JARs and originals.
The development literature pages remain subject to the existing publication gate.
