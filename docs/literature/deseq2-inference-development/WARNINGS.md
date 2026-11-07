# DEVELOPMENT: ORIGINAL DESEQ2 WORKFLOW; CATALOG PROMOTION PENDING

This is a tested standalone adapter to original DESeq2 1.52.0 on R4.6.1, not whole-paper or historical-environment reproduction. The full 250-paper implementation goal remains incomplete. Main-dispatcher integration, semantic review of 47 DESeq2 paper uses and catalog promotion remain pending. There is no new release, GitHub push or deployment.

## SKIP3: dependent analysis skipped

Supply the named Rscript, exact R4.6.1/DESeq2 1.52.0 edition, declared private libraries and requested optional packages only where installation/execution is permitted. If it is forbidden or unavailable, skip the dependent request. Read runtime versions, source-guard diagnostics and logs. Preserve each attempt and choose NEW output paths. The user-facing private installer refuses existing library/report paths and reports forbidden installs without creating a library.

## FAIL2: no result admitted

Any partial table in a failed attempt is evidence only. Read WARNINGS.md, analysis-status.json, worker/worker-report.json and stdout/stderr. Correct named count/unit/identity/covariate/rank/contrast/capacity/process errors and retry into NEW outputs. Existing results are preserved. Timeout cleanup stops only the owned worker tree. Neither an incompatible source function nor a failing statistical method is silently substituted.

## Scientific behavior requiring review

Raw counts are not TPM/CPM. Fractional estimates require explicit estimated_counts and R_round; no unit conversion is inferred. Technical replicate collapse is explicit, requires consistent modeled covariates and does not infer biological replication. Size factors use the complete supplied input universe before explicit keep_genes selection. Dataset poscounts centering and its override of supplied means are retained, not replaced by the low-level Java function's defaults.

Original NA/NaN/Inf and Cook/independent-filtering effects remain visible. A missing adjusted p-value is not repaired or treated as a successful null test. Review original result metadata and gene estimates. Original normal shrinkage rejects interaction designs. The tested glmGamPoi LRT factor contrast failed in the original source package; explicit coefficient selection worked. Inspect worker/coefficients.tsv, make a deliberate valid selection and rerun rather than expecting an automatic fallback. LRT p-values describe the model comparison, not merely the displayed fold change.

## Verification and runtime limits

VERIFY checks capture integrity, source/parameter correspondence, identities, prepared counts, normalization and published/worker consistency without R. It does not rerun negative-binomial inference, iterative factor optimization, transforms or shrinkage. Coordinated fabricated tables plus rehashing cannot be authenticated by this capture. Hashes are not signatures, biological validation or complete native-DLL source authentication. R/Java memory is not a hard aggregate bound; review explicit capacities and available resources.

The source guard checks 16 loaded R function bodies/formals against retained original sources. Current dependency versions are recorded; historical paper editions are not assumed equivalent. Quantifier import/length offsets, pseudobulk assembly and wrapper-specific pipelines remain separate work. Linux Java-only verification passed; Linux original-runtime fitting and the full project test suite were not run for this milestone.

## Retained development attempts

Initial generated reference count strings contained formatting padding, which strict admission rejected. A subsequent successful original fit was refused by the coefficient index audit for the same generated padding; the formatter was corrected without changing numeric values. An R test-harness partial match of extra$sf to extra$sf_type caused reference setup failures; exact indexing fixed the harness. The original glmGamPoi factor-contrast failure remains preserved rather than relabeled as success. All those attempts and later independent comparisons are retained.

Use docs/literature/DESEQ2_INFERENCE_DEVELOPMENT.md and examples/deseq2-inference/README.md for exact setup, requests, output interpretation and recovery.
