# MR with linked variants and imputed outcomes

`mr-linked` tests whether a genetically predicted molecular trait is associated
with an outcome using **one exposure-selected cis signal per transcript–trait
pair**. It accepts high-quality imputed outcomes, preserves reliable null
results, and can replace an unusable outcome measurement with a high-LD SNP
having its **own measured exposure and outcome effects**. This is a bounded
single-signal workflow, not a new multi-instrument estimator or an automatic
claim of causal mediation.

```powershell
java -jar omicsmatic-<version>.jar mr-linked --request examples/mr-linked/request.yaml --out results/linked-mr-run1
java -jar omicsmatic-<version>.jar mr-linked --verify results/linked-mr-run1
```

The committed example is synthetic and includes a retained exact null, an
imputation-quality fallback, and an untestable rare-variant example. No live
provider, individual-level data, or cohort-specific scientific validation is
implied. All input files are local; the command never transmits variant lists.
Existing `mr-estimate`, `mr-xwas` and automatic study MR policies are unchanged.
The new command is also allowed in an explicitly configured study custom step.

## Selection and estimation

1. Freeze the hypothesis family, populations, builds, phenotype definitions,
   additive per-allele scales, independent-sample rationale, cis boundaries,
   imputation metric and thresholds before inspecting outcome associations.
2. Within each declared cis interval, retain exposure rows passing source QC,
   measurement quality, the exposure P threshold (at most `5e-8`) and F threshold
   (at least 10). Select the lowest exposure P, then largest F, then variant ID.
   F breaks ties when published P values underflow to zero. Palindromic A/T and
   C/G SNPs are conservatively excluded, including those with favorable P values.
3. Keep the selected SNP if its outcome measurement is usable. Outcome beta,
   standard error and P do **not** rank variants. A reliable beta of zero or
   P of one is retained. Missing metadata and poor measurement are not nulls.
4. Only if that outcome is absent, fails source/measurement QC, or cannot be
   aligned, consider explicitly supplied same-chromosome LD pairs. Require
   `r_squared >= minimum_proxy_r2` (at least 0.8), a strong eligible exposure
   association at the proxy, and a reliable allele/coordinate-matched outcome.
   Rank by descending LD r-squared, descending outcome measurement quality,
   then exposure evidence and ID. No outcome significance criterion exists.
5. Calculate the existing first-order Wald ratio using the selected variant's
   own two effects. Allele reversal or strand complementation is recorded.
   Never divide the outcome effect at B by the exposure effect at A, and never
   rescale beta/SE by INFO or LD. Because both effects are at B, signed A–B
   phase is unnecessary for this bounded implementation; same-SNP allele
   alignment is still essential. If B has no measured strong exposure effect,
   this implementation reports the test unavailable rather than approximating it.
6. Adjust primary P values by `min(1, P * family_size)`. The denominator includes
   untestable and additional declared tests. `family_size` cannot be smaller
   than the submitted test list. Declare all transcripts **and outcomes** in the
   actual search, not only favorable pairs. The software cannot verify that an
   investigator's declared family captures an earlier exploratory search.
7. Repeat the same deterministic selection with both quality and LD thresholds
   raised to at least 0.9. This is a labeled sensitivity analysis; it cannot
   overwrite the primary result or become a separate selected discovery.

The first-order Wald estimate is `beta_Y / beta_X`, with
`SE = SE_Y / abs(beta_X)`. Its two-sided P value equals the outcome **Wald**
P computed from beta/SE. A published outcome score-test P may differ. Stronger
irQTL evidence does not manufacture stronger outcome evidence. Confidence
intervals are per-test intervals, not simultaneous family-adjusted intervals.

## Inputs and quality requirements

The YAML request uses the exact schema shown in the example. Unknown fields
are rejected to prevent misspelled filters being silently ignored. Each test
declares an exact feature ID, trait/source IDs and units. Exposure, outcome and
LD must have identical explicit build and population labels. Labels are
investigator declarations, not independent ancestry inference. This workflow
requires `sample_overlap: none` with a rationale; it does not implement overlap
correction. Isoform ratios remain relative transcript usage, not absolute
transcript expression. Gene-level effects cannot silently replace isoform effects.

Exposure and outcome files are UTF-8 **TSV** files, regardless of extension:

```text
variant_id chromosome position effect_allele other_allele beta se p eaf n qc_pass measurement quality_metric quality
```

Use actual tab separators. Required fields are signed additive per-allele beta,
positive SE, P in [0,1], effect-allele frequency in (0,1), SNP-specific N >= 2,
one-based position, and `qc_pass` exactly `true` or `false`. SNP IDs must be unique
within each source. Only biallelic A/C/G/T SNPs are supported. Omnibus genotypic
P values, odds ratios not converted to log odds, burden tests and indels are not
compatible input. Convert/normalize upstream and preserve the original source.

`measurement` is `sequenced`, `genotyped`, `imputed`, or `unknown`.
For directly measured rows use `NA` for both quality fields. Imputed rows need
the exact declared metric label and a score in [0,1]; `NA` quality is retained
as an unavailable measurement. Different metric labels are not silently pooled.
Set quality thresholds according to the provider and variant frequency; 0.8/0.9
are explicit workflow choices, not universal equivalences between INFO and Rsq.
The quality score is **not** the target–proxy LD r-squared. `qc_pass` records
source QC; the workflow does not infer QC or introduce an HWE filter.

LD files contain `target_id`, `proxy_id`, `r_squared`. Each directed pair must be
unique and both IDs must appear in the exposure file. They must refer to the
same normalized variants/build as the associations. Record the LD reference
source, population, sample size and suitability rationale. Reference-based
LD estimates, especially for rare variants, require scientific review. No proxy
is invented from distance or assumed from high D-prime. A rare WGS variant may
remain untestable in an imputed GWAS.

Header-only exposure/outcome files represent an explicitly exhausted source;
missing files, malformed numbers, duplicate IDs and invalid declarations fail
visibly. Files are limited to 64 MiB each and association tables to 100,000 rows;
extract regional data upstream. Do not feed an entire GWAS into this reader.

## Optional regional colocalization

Use the existing [SuSiE](cli-susie-tutorial.md) and
[colocalization](cli-colocalization-tutorial.md) workflows to fit **dense regional
statistics, including nonsignificant variants**, separately for each trait.
Review imputation quality, sample size variation, allele-aligned LD, convergence,
coverage, trait identity and the relationship of the MR instrument to the fitted
signals. An instrument-only or significance-filtered table is not a regional fit.

An optional per-test `colocalization` mapping runs native SuSiE colocalization
from the resulting sidecars at each declared shared prior (primary first):

```yaml
colocalization:
  trait1_effects: regional/exposure.effects.tsv
  trait2_effects: regional/outcome.effects.tsv
  trait1_log: regional/exposure.log
  trait2_log: regional/outcome.log
  exposure_source_id: FHS-v48-reviewed
  outcome_source_id: chosen-GWAS-release
  exposure_trait_id: ENST_EXACT_VERSION
  outcome_trait_id: chosen-trait
  genome_build: GRCh38
  ancestry: declared-population
  complete_region: true
  regional_inputs_reviewed: true
  p1: 0.0001
  p2: 0.0001
  shared_priors: [0.000005, 0.000001]
  minimum_overlap: 0.5
```

Source and trait IDs must equal the MR declarations. Each supplied SuSiE log
must contain exactly one `converged=true` metadata line. Completeness and input
suitability remain source-declared; hashes and log checks do not independently
certify the upstream fit. Every signal pair, H0–H4, shared-variant posterior,
overlap diagnostic and prior-specific log is retained. No largest-H4 selection,
combined MR/coloc P value, or automatic “causal” label is generated. Empty or
trimmed signal sets require review and are not evidence against colocalization.

## Outputs and interpretation

- `results.tsv`: one primary result or explicit untestable disposition per test.
- `<test>/selection.tsv`: all exposure eligibility decisions, exact-outcome
  disposition and any evaluated proxies, with reasons.
- `<test>/strict-selection.tsv` and `result.yaml`: sensitivity results and
  whether selection changed; primary and sensitivity are always separate.
- `<test>/inputs/`: byte-identical input snapshots; optional regional sidecars
  and convergence logs are retained here too.
- `<test>/coloc-*.tsv*`: all optional prior-specific regional outputs and logs.
- `report.yaml`, `request.yaml`, `command.txt`, `manifest.yaml`, `COMPLETED`:
  policy, family denominator, source identities, input hashes, runtime and
  complete output inventory. Failed runs retain `FAILED.txt` without completion.

`--verify` checks the complete file inventory and hashes; it is not independent
scientific reproduction, authentication or a human signature. Output directories
are create-only. Preserve input copies according to their data-use restrictions;
this command does not make a bundle publicly redistributable.

`association_supported` means the primary Wald P passed the declared Bonferroni
threshold. `causal_mediation` remains `not_established` even when colocalization
supports sharing. There is no automatic classification of an imprecise null as
absence: interpret the interval against prespecified scientifically meaningful
effect sizes. For stronger evidence, validate the molecular association in
independent QTL samples, compare better-measured outcomes, and seek independent
outcome GWAS replication. Reusing one GWAS across QTL cohorts is not independent
outcome replication. Colocalization and MR reuse evidence and cannot be counted
as independent replications either.

## Method references

- [TwoSampleMR outcome/proxy workflow](https://mrcieu.github.io/TwoSampleMR/articles/outcome.html).
- [Imputed-genotype uncertainty methods](https://pmc.ncbi.nlm.nih.gov/articles/PMC3143715/).
- [IMPUTE2 quality metric guidance](https://mathgen.stats.ox.ac.uk/impute/impute_v2.html).
- [SMR interpretation and molecular testing families](https://doi.org/10.1186/s13073-016-0338-4).
- [Multi-signal coloc method](https://doi.org/10.1371/journal.pgen.1009440).
- [STROBE-MR reporting guidance](https://doi.org/10.1136/bmj.n2233).

## Developer validation

```text
gradlew test --tests org.jlinalg.mr.LinkedVariantMrTest --tests org.jlinalg.cli.LinkedMrCliTest
```

Tests cover independent analytic Wald values, null preservation, outcome-blind
proxy ranking, allele reversal/complementation, separate quality/LD requirements,
unknown quality, underflow ties, full-family correction, optional prior sweeps,
input tampering, source incompatibility and failed-run retention. Synthetic
software tests do not establish instrument validity or validate live GWAS data.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Stephen Burgess, Neil M. Davies, and Simon G. Thompson (2016) — Bias due to participant overlap in two-sample Mendelian randomization](../CITATIONS.md#burgess-overlap-2016) — [PMID: 27625185](https://pubmed.ncbi.nlm.nih.gov/27625185/) · [PMCID: PMC5082560](https://pmc.ncbi.nlm.nih.gov/articles/PMC5082560/)
- [Gibran Hemani et al. (2018) — The MR-Base platform supports systematic causal inference across the human phenome](../CITATIONS.md#hemani-mrbase-2018) — [PMID: 29846171](https://pubmed.ncbi.nlm.nih.gov/29846171/) · [PMCID: PMC5976434](https://pmc.ncbi.nlm.nih.gov/articles/PMC5976434/)
- [Chris Wallace (2021) — A more accurate method for colocalisation analysis allowing for multiple causal variants](../CITATIONS.md#wallace-coloc-2021) — [PMID: 34587156](https://pubmed.ncbi.nlm.nih.gov/34587156/) · [PMCID: PMC8504726](https://pmc.ncbi.nlm.nih.gov/articles/PMC8504726/)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
