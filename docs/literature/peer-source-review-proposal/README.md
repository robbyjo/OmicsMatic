# Reviewed PEER source roles awaiting admission

CENSUS.json records a fresh audit of all 250 XML hashes/DOIs and all case-insensitive
PEER/expanded-name matches. It identifies 18 positive software-use papers among
181 case-insensitive candidates. The other 163 papers contain generic peer-review
language or the published reviewer surname Peer. Positive use includes supplied
factors and sensitivity analyses; it does not mean 18 new PEER fitting pipelines.

The preceding acronym-only inventory has 15 observations. P074, P107 and P169
are three proposed expanded-name additions. No canonical metadata, review count
or family credit changes at this source-review checkpoint.

| Paper | Distinct source role |
| --- | --- |
| P019 | Subcohort/combined-cohort inference and residualization; closest-to-95%-maximum eQTL search |
| P074 | Sixty expression-residual factors before PredictDB training |
| P104 | Inference and eQTL search over 10-98 factors by four |
| P107 | Separate APOE-stratum inference: 45 versus 15 factors |
| P108 | Supplied tissue-specific GTEx covariates reused in isoTWAS |
| P157 | Sixty expression/protein factors, fifteen splicing factors, interaction-QTL covariates |
| P169 | Reported adjusted residuals and quarter-sample-size hidden covariates |
| P170 | Fifteen factors before bulk QTL and response mixed models |
| P183 | Joint lncRNA/PCG inference with 15/30/45/60 sample-size bands |
| P193 | Fifteen supplied GTEx factors in sensitivity analysis; primary model has no covariates |
| P201 | Twenty-nine splicing-QTL factors, reported as one quarter of sample size |
| P203 | Inference plus factor-count search selecting twenty; retain higher-BIC wording |
| P212 | Explicit v1.3; 0-100 grid, 40 GTEx/20 FUSION; zero-factor baseline is separate |
| P213 | Two factors from the original analysis; preserve four-versus-six-PC discrepancy |
| P219 | Fifty estimated, thirty selected; retain distinct BMI/LMM residual definitions |
| P221 | Seventy JHS factors from the prior pipeline for MESA-model validation |
| P234 | Sixty supplied GTEx factors for residualization/co-expression |
| P249 | Reported TPM correction; logistically-corrected wording remains unresolved |

The machine-readable roles retain exact paper identities, paragraph hashes and
name/version offsets, reported counts, provenance distinctions and cautions.
Only P212 explicitly reports PEER v1.3 in these selected source paragraphs;
QTLtools v1.2 in P219 is a separate provider. Version-label agreement does not
establish the paper-executed source commit. No author runtime, full preparation
or whole-paper reproduction is claimed. Primary XML and full paragraphs remain
private; public records contain locators, hashes and paraphrased roles.

Run `python -B tools/literature/check_peer_sources.py --primary-cache PATH --out
build/NEW-peer-source-check` with Python 3.11+ without -O for a fresh audit and
16 semantic refusal controls. Supply the exact permitted cache for SKIP3; for
FAIL2 retain the attempt, resolve the named source/integrity/role cause and retry
NEW output. Read the [workflow scope](../PEER_WORKFLOWS.md). Both literature HTML
reports remain withheld from Pages, and the full goal remains unfinished.
