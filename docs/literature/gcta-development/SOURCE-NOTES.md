# GCTA/COJO source intake: implementation and admission remain open

**Later checkpoint:** the [current GCTA handover](../HANDOVER_2026-11-20_GCTA.md)
adds a prepared-hard-call Java GRM prototype and original synthetic exploration.
Five initial native GRM comparisons pass on three JVMs; full workflow,
normal-producer/edge-corpus qualification and catalog admission remain open.
Both advanced fastGWA attempts fail at the 2,000-SNP tuning prerequisite.
The launch-only/no-prototype statements below describe the earlier intake
checkpoint. No GCTA/COJO catalog credit or canonical review has been added.

**Research notes only. No GCTA/COJO implementation credit or canonical review
has been added.** The current admitted baseline is main
`bdf1c3e8067896131d31cbca3e7de9191aa93a0d`: 140 scoped integrations, 1,548 reviewed
uses, 1,166 pending and 826 clean manifest entries. GSEA is already admitted;
do not restart that implementation or apply its older admission-open handover.

This stage is `D:/projects/JLinAlg/build/gc-1`, branch
`codex/gcta-high-impact-20261120`. All 22 predecessor worktrees are preserved;
there are now 23. Full source/runtime/evidence intakes remain private under
`build/gcta-high-impact-1`; separately transfer ignored artifacts before moving
machines. The full 250-paper implementation/review goal remains unfinished.

## All-250 source census

Citation-cleaned and raw paragraph contexts are both retained. There are 22
candidates, with 17 original GCTA observations and nine original COJO
observations across **18 distinct papers**, not 26 independent studies. Primary
XML identities and exact paragraph witnesses are recorded in the private
`source-intake-1/CANDIDATES.json` and per-paper text exports.

The 18 execution candidates are P090/P105/P112/P126/P135/P140/P149/P150/P159/
P171/P193/P207/P212/P216/P219/P238/P245/P248. Full methods have been read;
these remain source-role findings rather than admitted reviews.

| Paper | Paragraphs | Reported role and source constraint |
| --- | --- | --- |
| P090 | 33 | GCTA 1.92.4 mtCOJO conditions DCM on AF/CAD/SBP; European 1000G LD and distinct binary prevalence inputs. Do not substitute ordinary COJO or map the edition to 1.94.1. |
| P105 | 49/60 | GCTA-COJO 1.93.2, EAS-specific PUD/subtypes, 20,000 unrelated BBJ best-guess genotypes, stepwise selection then joint fit, MAF >0.01 and P <5e-8. Effect-size comparisons are separate. |
| P112 | 31 | Missed package-role candidate: the full GCTA name accompanies mtCOJO on CanUD conditioned separately on smoking initiation/cigarettes per day; downstream MR is separate. Edition unstated. |
| P126 | 41 | cojo-GCTA 1.94.1 on meta-GWAS, 45,918 ancestry-matched UKB/MGBB LD samples, stated imputation/dosage filters and literal MAF >=0.0001%; variants >500 kb are treated uncorrelated. Keep percent units. |
| P135 | 31/33 | Separate COJO selection and mtCOJO conditioning on eBMD; stated flags include literal uppercase `--MAF`, `--cojo-p 5e-8`, `--cojo-wind 10000`, `--cojo-collinear 0.9`. Do not silently repair flag case or confuse 10,000-kb modeling with +/-500-kb locus definitions. mtCOJO uses 5,000 European UKB LD samples; edition unstated. |
| P140 | 55/59 | MoBa fastGWA-GLMM binary analyses with a sparse relatedness matrix, stated GCTA 1.91.7beta and `--fastGWA-mlm-binary`; FinnGen REGENIE 2.2.4 is separate. Confirm the exact edition/command combination instead of assigning 1.94.1. Additional covariate/sex analyses remain distinct. |
| P149 | 47 | GCTA approximate conditional analysis is attributed only to Finnish summary data; other cohorts use individual-level genotypes. Class-specific thresholds and 500-kb secondary-signal search remain literal; exact command/edition unresolved. |
| P150 | 50 | GCTA-COJO 1.94.1 stepwise conditional/joint lead-SNP analysis, P <5e-8, 10-Mb window, collinearity <0.9; FUMA clumping/annotation is separate. Do not infer the COJO LD panel from FUMA context. |
| P159 | 29 | GCTA 1.93.0 computes 20 reference PCs from 1KG/HGDP; projection via SNP loadings and later UMAP are separate roles requiring exact code/settings. Do not equate PLINK PCA/projection with GCTA. |
| P171 | 53 | GCTA 1.94.1 PCA in tetraploid wheat. The paragraph's 100,000-variant sample is specifically for phylogeny; do not silently transfer it to PCA. Recover genotype encoding/species/chromosome conventions rather than assuming human diploidy. |
| P193 | 14 | GCTA-GREML estimates narrow-sense irQTL heritability from intron PI and GTEx genotypes; edition, GRM, covariate and phenotype preparation/settings unresolved. |
| P207 | 21 | GCTA-COJO joint/independent common-signal analysis with p <=1e-7 and 10,000 UKB TOPMed LD samples before rare-variant burden tests. Exact selection/joint command and edition are unstated; REGENIE/SBAT/SKAT-O/ACAT-V are separate. |
| P212 | 16 | Explicit cojo-slct and cojo-cond on 26 traits using 40,000 unrelated European UKB LD samples; use European GWAS subsets when available, without assuming every input is European. Edition unstated. |
| P216 | 30 | GCTA-COJO independent signals, 5e-8 threshold, >1-Mb independence and collinearity <0.9; edition/reference-panel details unresolved. Window units and LD clumping are not interchangeable. |
| P219 | 21/32 | cojo-slct is used separately for skin meQTL and eQTL conditional analysis; preserve the two outcome families and LD/cis/trans interpretation. Edition and complete settings unresolved. |
| P238 | 10 | GCTA REML on covariate-residualized splicing S values with 1/10/100-kb windows. **The paper explicitly permits heritability outside 0-1. Do not clamp or reject valid converged unconstrained estimates merely for crossing those bounds.** Prediction models and cross-validation are separate. |
| P245 | 25 | GCTA computes in-sample LD scores for MAF/LD annotations; the paper's GxE estimator and tissue annotations are separate. LD-score algorithm/window/edition are unresolved, not automatically LDSC settings. |
| P248 | 52/55 | GCTA 1.94.1 PCA uses 18,600 4DTv sites from 422 cucumber accessions; separate MAF-stratified multi-GRM REML reports only 1.94. Preserve literal rare/common cutoffs 0<MAF<0.034 and MAF>0.034; equality is not assigned by that prose. The raw XML formula has no division/fraction node; recover the publisher formula rather than silently repairing it from the numerical cutoff. |

Four candidates do not establish an execution:

- P127 paragraph 60 describes a covariance approximation as similar to COJO.
  Its existing **unreviewed COJO observation is a proposed false-positive
  removal**, with exact-before retention required in a future guarded
  transition. Do not count this as running GCTA/COJO or remove it silently.
- P177 paragraph 38 lists GCTA-GREML as a possible future improvement; this
  paper uses a Z-score correlation method.
- P199 paragraph 49 discusses GREML-LDMS/BOLT-REML as related future directions.
- P204 paragraph 45 lists GCTA among possible upstream GWAS tools without
  establishing its use; actual LD/MAF/annotation work names Hail/Python/VEP.

Citation-cleaned GSEA follow-up finds P053/P161 mentions missed by the closed
raw-text candidate pattern. Both explicitly use GSEApy Enrichr/EnrichR ORA,
not new ranked GSEA roles. Preserve the follow-up contexts separately; do not
rewrite old GSEA census fingerprints or add ranked-GSEA credit.

## Exact upstream intake and launch health

The upstream [repository/README](https://github.com/jianyangqt/gcta) identifies
GPLv3 with file-specific LGPL portions. The immutable v1.94.1 source commit is
`6da64e9d8be838d01c34efb974e69c72e861f4c2`; its source archive and 121 regular
members are inventoried. Primary GitHub API metadata and release assets are
saved. The official website returned HTTP 403 and the search tool temporarily
returned 503; no website/license content is invented from those failures.

The original [v1.94.1 release](https://github.com/jianyangqt/gcta/releases/tag/v1.94.1)
supplies both runtimes. Windows extraction contains the original EXE and
libomp.dll; no global installation or system-library change was made.

```text
Windows EXE: 34016768 bytes
SHA-256 2529bccb2f685332f115726c3cb2bf8cab91f12ae3344a991e3a52d751bdb07e
Linux ELF: 38686056 bytes
SHA-256 dbb2b5e5d51e278328ff297a403d3dd8ee5f5280f0b6d3d8db74b3e7fead7b49
```

Both original executables launch and report v1.94.1. The no-argument probe
intentionally returns **1**, exactly as `src/main.cpp` specifies, with the
no-analysis diagnostic. **This is launch health, not an analysis or scientific
validation.** No GCTA result has yet been admitted and no Java adapter is
implemented at this source-intake checkpoint. Older, beta or incomplete
reported editions remain unresolved; never silently substitute 1.94.1.

## Implementation direction and warnings

Read `src/main.cpp` before invoking flags: it requires `--out` for an analysis
and routes legacy modes through `main/option.cpp`. There is no generic
`--version`/`--help` shortcut in the inspected entry point. Preserve raw output
line endings (Windows launch stdout contains CR/CR/LF) and source-specific
diagnostics. Source filenames/headers are not sufficient proof of successful
convergence or a complete model result.

Existing `ConditionalAssociationModel` assumes a known common residual variance;
it does not re-estimate the GCTA summary-model variance or account for all sample
size/LD behavior. The existing GRM builder mean-imputes and uses a common marker
denominator; GCTA's own GRM/N/diagonal options require independent source
comparison. Do not relabel these related components as GCTA. Native Java remains
appropriate for tractable source-qualified operations; original upstream
adapters are authorized for the more complex mixed/multitrait models.

Next: generate independent synthetic PLINK/phenotype/summary fixtures; execute
actual original GRM/PCA, COJO, constrained/unconstrained/multi-GRM REML, LD-score
and binary fastGWA branches before defining the Java workflow contracts. Audit
mtCOJO's extra LD-score/prevalence prerequisites separately. Capture all inputs,
outputs, failures and diagnostics, and inspect the original convergence rules.

**SKIP3:** a dependent exact-edition analysis is skipped until its named permitted
runtime/input is available, or stays skipped if installation is forbidden.
**FAIL2: NO COMPLETE RESULT:** retain raw inputs, model/null artifacts, conditions,
partials and diagnostics; correct the named cause and retry NEW output. Never
silently repair identifiers/alleles, change populations/thresholds/editions,
replace nonfinite values or substitute algorithms. Keep source-produced valid
unconstrained estimates distinct from numerical or convergence failures.
