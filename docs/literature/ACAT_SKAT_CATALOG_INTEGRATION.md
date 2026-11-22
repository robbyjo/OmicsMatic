# ACAT component and ACAT/SKAT source crosswalk

This admission credits **one bounded native ACAT family** and reviews 22
ACAT/SKAT/SKAT-O observations: 21 pending uses plus the omitted P127 `SKATO`
spelling. It does not credit a standalone SKAT adapter. The resulting totals are
**147 scoped families (27 native + 120 upstream), 1,661 reviewed uses and 1,062
pending**, with 250 papers, 446 labels, 485 definitions and 2,723 observations.
All 1,639 preceding review records and 2,701 unrelated observations are preserved.
Two existing aliases gain no duplicate credit. Full semantic completeness and
the full implementation goal remain unfinished.

The [native ACAT guide](ACAT091_NATIVE.md) and
[working example](../../examples/acat091/README.md) describe the checked ACAT
0.91 matrix combination and `acat-tools` RUN/VERIFY/REPLAY command. It has 500
focused tests per normal build and packaged Windows Java 21, Windows Java 25 and
Linux Java 21 JVM. This admission binds the actual immutable `build/sa-1` owner,
its saved original-source controls and exact normal JAR; it does not refit R
models. Current native scientific/build source is unchanged from `f66c8e5e`.

Native ACAT0.91 accepts declared prepared p-value columns. It does not implement
ACAT-V genotype/null-model fitting, ACAT-O, REMETA, REGENIE, isoTWAS or the whole
paper workflows. Existing stable ACAT/ACAT-V/ACAT-O and rare-meta SKAT-O profiles
remain separate. SKAT and SKAT-O retain **method-overlap** status. The new
registry provider is `acat_skat_catalog.model.registry`; three spelling patterns
are updated without changing other 482 definitions. `SKATO` belongs to SKAT-O,
and ACATV/ACATO remain spellings within the existing ACAT family.

## Individually reviewed uses

The [frozen source census](acat-skat-catalog-sources/CENSUS.json) binds all 250
XML/DOI identities, 19 candidate papers, 95 candidate paragraphs, one table hash
and three complete P190 supplement pages. Candidate matches do not all establish
executed software. Each role has its original primary pointer, inspected support,
provider witness, explicit or unknown edition, and execution boundaries. Full
XML/PDF text, rendered pages and the real aggregate P220 table stay private.

| Paper | Tool label | How it is used and what remains distinct |
|---|---|---|
| [P090](https://doi.org/10.1038/s41588-024-01952-y) | SKAT; SKAT-O | Comparators motivating REGENIE 3.2.4 burden testing; the paragraph does not establish executed SKAT/SKAT-O runs. |
| [P106](https://doi.org/10.1038/s41588-025-02403-y) | SKAT; SKAT-O | Meta-SAIGE score/LD tests with provider-specific SPA and superset construction, followed by separate annotation/MAF Cauchy combinations. |
| [P108](https://doi.org/10.1038/s41588-023-01560-2) | ACAT | isoTWAS isoform-to-gene omnibus before BH and within-gene sequential testing; ACAT edition unknown. |
| [P127](https://doi.org/10.1038/s41588-025-02390-0) | ACAT; recovered SKAT-O | REMETA combines variant, mask, rho-grid and test evidence. Its rho-grid ACAT differs from the original minimum-p SKAT-O procedure. No standalone SKAT provider is inferred. |
| [P131](https://doi.org/10.1038/s41588-026-02735-3) | ACAT | Explicit ACAT R 0.91 for T-SEM/FUSION tissue p-values. Preserve source tissue/gene/factor wording and separate QGENE filtering/gsMAP. |
| [P132](https://doi.org/10.1038/s41588-023-01555-z) | SKAT-O | Somatic gene testing with occurrence/VAF filters and covariates; provider, edition and calibration unknown. |
| [P141](https://doi.org/10.1038/s41588-026-02705-9) | SKAT-O | Gene-level p-values attributed to SAIGE-GENE+ in RareEffect examples; a standalone R call and fresh-fit versus externally obtained status are not established. |
| [P152](https://doi.org/10.1038/s41588-024-01930-4) | ACAT; SKAT | REGENIE Step 2 unified gene analysis across consequence/AAF masks; these are not standalone R package calls. |
| [P154](https://doi.org/10.1038/s41588-024-01919-z) | SKAT | SEAK 0.4.3 baseline tests, separate REGENIE comparison, DeepRVAT seed selection and prediction. Preserve collapsing, marker-capacity and correction differences. |
| [P176](https://doi.org/10.1016/j.ajhg.2025.12.002) | SKAT-O | Candidate/exome rare-variant repeat-instability testing; provider unknown. The expanded SKAT acronym inside SKAT-O is not another independent test. Preserve the exome-wide null finding. |
| [P190](https://doi.org/10.1016/j.ajhg.2025.10.012) | ACAT | Uniform-weight junction p-value aggregation in a simulated sGene method comparison. Supplement S3.3 gives the Cauchy scale as the weight sum; S5.1 describes simulation. Edition unknown. |
| [P204](https://doi.org/10.1016/j.ajhg.2025.09.012) | SKAT | SKAT-type component in COAST/COAST-SS reconstructed from summary statistics; not an established standalone SKAT R call. |
| [P207](https://doi.org/10.1016/j.ajhg.2024.08.021) | ACAT; SKAT-O | REGENIE 3.0 simulations/real analyses and four-test GENE_P. Preserve source MAC below 10 wording; this omnibus is not canonical six-component ACAT-O. |
| [P220](https://doi.org/10.1016/j.ajhg.2025.08.013) | SKAT-O | Fresh SAIGE container 1.1.9 testing, conditional tests and selection, plus separately downloaded AoU/Genebass results. Bibliography45 is not part of version 1.1.9. |
| [P221](https://doi.org/10.1016/j.ajhg.2026.03.008) | ACAT | FIVTWAS omnibus only for genes with at least two available models; single-model reuse remains separate. Unsigned p-values do not identify signed Z direction. |
| [P246](https://doi.org/10.1016/j.ajhg.2025.06.013) | SKAT; SKAT-O | Explicit SKAT R 2.2.5 and SKAT() for CASR PheWAS; main-text SKAT-O method/rho options are missing. Package edition is separate from the SKAT-O method label. |

P123 gsMAP, P147 LTPI/LTFH and P210 annotation-mask CCT explicitly use Cauchy
combinations but do not establish a named ACAT package. Their checked negative
decisions prevent invented package observations. P210 writes a Cauchy **CDF**
for the combined p-value; retain this tail-wording discrepancy rather than
silently declaring its formula equivalent to the native survival-tail profile.
P106's background MetaSKAT mention does not establish an executed MetaSKAT run.

## Warnings and required actions

**SKIP3: the dependent analysis or source audit did not run.** Supply the named
exact permitted executable, Java/R library, input/index, primary XML/PDF or audit
dependency. Downloads/private installation are authorized where allowed. If
installation is prohibited, retain the skip and clearly name the affected
analysis and prerequisite. The maintainer PDF audit needs pypdf 6.21.4; ordinary
native ACAT use requires no R or PDF reader. Cell Ranger remains excluded.

**FAIL2: no complete result is admitted.** Preserve raw, null, failed, partial
and superseded evidence and logs. Resolve the named input, numerical, capacity,
source/integrity or process cause and retry NEW output. Never silently repair
NaN/Inf, endpoints, weights or identifiers, invent paper settings, refresh old
hashes or overwrite evidence. Read `analysis-status.json` and `WARNINGS.md`.
One unadmitted ACAT column withholds the batch export. VERIFY can confirm a failed
capture's consistency while its result remains unadmitted. Numeric zero is not
infinitely precise biological significance, and completed null results are not
positive associations.

Transfer ignored JARs, runtimes, primary text and complete private/failed evidence
separately before machine retirement. Preserve all old worktrees and test links;
do not follow deliberate junctions blindly. No private transfer is claimed.
Both unfinished reports remain excluded from Pages; no release or explicit
Pages publication is associated with this admission.

## Reproduce or resume

Read [the admission protocol](../../tools/literature/acat_skat_catalog/ADMISSION.md).
Use `close_acat_skat_catalog.py --verify --out build/NEW-readback` on the exact
admission owner. Add primary XML/PDF paths for a fresh audit and `--checks-root`
for actual immutable-owner rechecks. Frozen older readers belong on their actual
old owners, rather than refreshed source hashes.

Linux Python checks on this Windows-owned worktree use a recorded Windows Git
bridge solely for metadata access. Source extraction and scientific behavior
are not replaced.

Next implement the standalone SKAT adapter with explicit prepared inputs, null
model, method, filters, imputation, adjustment, RNG and numerical-fallback
admission. Eight existing direct synthetic probes are not that adapter. Recover
P246 supplementary options, then qualify original calls and actual packaged
RUN/VERIFY/REPLAY. Continue FUSION, GenomicRanges, g:Profiler and other high-impact
gaps, and the remaining 1,062 use reviews. The full goal remains unfinished.
