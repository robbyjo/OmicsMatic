# PLINK release identities and GWAS source review

The frozen cohort still contains 250 papers. The source correction brings the
catalog to 2,422 paper-tool observations. The later Beagle refresh brings the
current catalog to 510 individually reviewed roles and 88 supporting paragraph
pointers. Exact source XML hashes and paper identities
are unchanged. Broader semantic review and implementation remain active.

**Typed original PLINK/PLINK2 subsets are available in the validated development
candidate; the broader adapter remains in progress.** The candidate
`build/literature-review/beagle-candidate-1/jlinalg-0.3.7.jar` contains the checked
autosomal hard-call GLM/QC, LD pruning and exact/randomized PCA commands. See
`examples/plink-tools/` for their separate immutable proofs, explicit reference,
sample, allele, model and missingness choices. The older canonical a104 executable
is unchanged and lacks `plink-tools`; no new full-project gate or release is claimed.
The
separate [native genotype-score helper](GENOTYPE_SCORE_WORKFLOWS.md) now applies
supplied weights to resolved diploid human autosomal BED hard calls in Java.
Its exact allele/frequency/missingness contracts, original numeric comparisons,
full gate, warnings and recovery are validated separately from source curation.
The broader tool families retain method-overlap status; this does not establish
complete original PRS training, every QC/PCA/GLM option or historical paper replay.

## Source corrections

PLINK (v.2.0), PLINK v.2 and PLINK version 2 now resolve to the explicit release-2
family. Alpha build suffixes retain their spelling; bibliographic reference labels
are excluded from the version while the raw evidence paragraph remains unchanged.
Genuine release-1 and release-2 uses survive even within one sentence. A PLINK
website URL or PLINK/PGEN file-format statement alone is not execution evidence.
Appendix and result headings containing GWAS or GWASs are eligible for direct
analysis evidence; availability lists and previous-study statements remain excluded.

The correction adds 32 source-role reviews. Examples of the distinctions are:

| Operation | Source-specific boundary |
|---|---|
| Genotype QC | P024 uses PLINK2; P084 names both families for shared processing; P176 uses separate exome PLINK2 and array PLINK1.9.0 workflows. Thresholds and cohorts must remain separate. |
| GWAS | P097 names PLINK1.90b7.1 --linear; P076 uses quantitative --glm; P118 uses linear regression for imputed phenotypes; P150 explicitly requests binary firth-fallback. GEMMA association in P176 is a separate program. |
| PCs and PRS | P024 and P249 use release 2. PRS-CS posterior-weight estimation differs from PLINK score application. Unstated PCA/allele/normalization options remain unstated. |
| LD clumping | P023 separates a 10-Mb clump window from later 100-kb lead merging. P200 pairs --clump-kb with a one-million-bp description: resolve the units before constructing a numeric command. |
| Kinship | P084 uses the KING-robust estimator inside PLINK2; P224 names KING v.2.0 separately. Neither coefficient is automatically IBD pi_hat. |
| Annotation | P192 reads SIFT annotations through VEP and reports an unusually labeled CADD-SV value. P190 uses SpliceAI annotations without specifying local model execution. Lookup and executable inference are distinct. |

P242's generic PLINK --hwe wording is retained alongside the PLINK2-named QC
workflow; it does not establish a separate PLINK1 run. Its Fisher-exact wording is
not a contract to replace a genotype HWE procedure with an ordinary 2x2 Fisher test.
P112's source calls ten covariates positive controls; the catalog preserves that
wording instead of silently selecting different covariates.

## Validation and current implementation work

The source-correction run passed 215 extraction/installer policy tests without skips, including
18 new release-name, citation, context, URL/format and appendix-heading cases.
The full frozen cohort was re-extracted offline from hash-verified source XML,
with explicit source review of the changed roles. Earlier previews and the first
failed plural-heading test remain in the private research cache.

Official Windows original programs were downloaded privately from links on the
[PLINK 1.9](https://www.cog-genomics.org/plink/1.9/) and
[PLINK 2](https://www.cog-genomics.org/plink/2.0/) sites. Actual six-process reference
work on known diploid genotypes checked 38 allele-frequency values, 38 independent
exact conditional HWE tests, 291 score sums and 97 score averages. The programs
reported 1.9.0 (27 Sep 2026) and 2.0.0-b.1.2 (24 Oct 2026). This is upstream
reference work; it is not Java adapter validation or historical paper replay.
The all-missing marker was not credited as an interpretable HWE result.

**Original scoring and resource contracts differ.** Tested PLINK1.9 rejects
`sum` combined with `no-mean-imputation`; the valid missing-omission average uses
its observed-allele denominator. Tested PLINK2 permits the checked sum modes and
requires at least 640 MiB for its `--memory` argument. Never drop an unsupported
option silently or change the requested statistic to make a run pass. Preserve
the error and report the required correction. Missing/forbidden required software
must skip the dependent analysis and give exact setup/recovery instructions.

Reference evidence is retained in
`build/literature-review/plink-runtime/reference-math-5/verification.json`.
The original input, version, binary/archive hashes, command lines, raw reports and
failed attempts are separate from the project adapter work that follows.

## Native scoring implementation and next-operation references

The canonical native helper passed 41 API/BED/CLI tests, six actual original
comparison workflows with 2,007 reported values, 670 native sample rows and 23
packaged controls. A separate Java 17 API consumer ran on actual Java 21/25.
Read GENOTYPE_SCORE_WORKFLOWS.md for supported inputs and required actions.

Another private original-only pilot completed 13 QC/pruning/LD/PCA/quantitative,
ordinary-logistic and logistic-fallback processes on a separate 128-sample,
96-variant synthetic design. Independent checks covered 1,728 regression values,
9,120 unphased LD pairs, ten eigenvalues and exact sample identities. The fallback
cases used ordinary logistic (`FIRTH?=N`); they do not validate Firth inference.
This prepares adapter contracts and is not a project upstream-adapter claim.

**Regression effects must follow the actual output allele pair.** In that tested
PLINK2 GLM profile, 40 of 96 effects used BIM A2. Preserve each row's A1/OMITTED
alleles; assuming all coefficients describe BIM A1 reverses their interpretation.
This observation does not prescribe defaults for every release or command.
The first mistaken orientation assumption and a verifier-only missing Python
dependency are retained, with corrected reference checks kept separately under
`build/literature-review/plink-runtime/original-operation-math-3/verification.json`.

A separate quasi-separated-data probe distinguishes ordinary logistic
`SEPARATION,ALT1` errors from fallback/forced Firth. Both tested Firth modes
printed finite provisional OR/P values with `ERRCODE=UNFINISHED` and exit 0.
Those rows remain unreportable; a successful process exit is not completed
inference. Preserve the error, review separation, event counts, covariates and
convergence, and make any model/engine change explicitly. Never drop the error
flag or substitute a zero. Raw contracts are retained under
`build/literature-review/plink-runtime/original-firth-contracts-2/verification.json`.
