# DEVELOPMENT: NATIVE NORMALIZATION ONLY

This milestone implements the raw-integer median-location subset of DESeq2 1.52.0 size-factor estimation. It does not implement differential inference, dispersion fitting, VST, rlog, shrinkage, quantifier offsets, pseudobulk assembly or a whole-paper analysis. No new catalog credit, public release, push, deployment or main-dispatcher route is claimed.

## Undefined factors: action required before a later fit

NA, NaN, zero and infinite factors are retained, not repaired. A completed normalization can have usable_for_differential_fit=false. Review counts, zeros, explicit means and control selection; preserve the attempt and choose a new output directory after correction. The flag checks numeric prerequisites only, not biological design adequacy.

## FAIL2: no result admitted

Read the named error, WARNINGS.md, analysis-status.json and manifest. Partial tables in a failed attempt are not results. Correct malformed/fractional/negative counts, duplicate or mismatched identities, invalid means/controls, or capacity/permission errors. Never silently round, rename or drop observations. Existing output directories are refused and preserved.

## External inference requirements and SKIP3

This Java normalization command needs no R runtime. A separate validated upstream workflow remains necessary for DESeq2 inference. If a needed runtime cannot be installed or installation is forbidden, skip that dependent analysis and name the required software and recovery steps. The forbidden-install control created SKIP3 evidence without analysis or library mutation. It is a policy control, not a claim that installation was forbidden for this permitted reference setup.

## Preserved attempts and validation limits

The first official-host archive download returned HTTP403; the listed Posit mirror succeeded. The first Java comparison failed two cases involving all-NaN supplied means; the guard was corrected and all cases passed. The first platform matrix overcounted actual command protocols by three; its metadata correction and old validator are retained. A new matrix ran all checks plus an actual help command on each platform, supporting the final count of 15 protocols.

The reference runtime used current R4.6.1 dependencies, not complete historical environments for the 47 papers. The loaded original function matched source body/formals. Finite numerical comparisons use relative tolerance 2e-12; missing/nonfinite kinds are checked separately. No biological validity, exact historical package behavior, cryptographic authenticity or full project-suite validation is claimed. Original-package setup includes a small synthetic smoke fit; it does not admit a user inference workflow.

Build and run instructions are in the development guide and examples/deseq2-normalization/README.md. The frozen development JAR is local; its path and SHA-256 are recorded in VALIDATION.json.
