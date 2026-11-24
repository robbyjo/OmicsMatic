# OmicsMatic v0.4.1

## Quality-aware MR and expanded scoped workflows

- Add `mr-linked`: exposure-only cis instrument selection, exact-first outcome matching, and LD proxy fallback using the proxy's own exposure and outcome effects. Retain reliable null outcomes, explicit untestable results, full-family Bonferroni adjustment, quality/LD sensitivity, captured inputs and verifiable output inventories. Optional reviewed regional SuSiE sidecars run colocalization across declared priors.
- Include the process-ownership correction that checks worker creation identities during cleanup, plus atomic worker readiness publication. Users of earlier external-tool builds should use this release and read the [incident and validation limits](https://github.com/robbyjo/OmicsMatic/blob/v0.4.1/docs/literature/PROCESS_OWNERSHIP_SAFETY_2026-11-22.md).
- Include scoped additions since v0.4.0 for edgeR, GSEA, GCTA, LDSC, TwoSampleMR, QTLtools, SKAT/ACAT, GenomicRanges, g:Profiler, FUSION, Squidpy, MR-PRESSO, smartpca, SCENIC/SCENIC+, IMPUTE2, FlowJo/cytometry, FastQTL, Genomic SEM and native PRSice hard-call scoring. Each operation retains its documented version, inputs, runtime requirements, warnings and evidence.
- Freeze the current 446-family literature inventory: 30 validated native subsets and 130 validated upstream workflow families. Three alias labels do not add independent family credit. Missing, excluded and related-method entries retain their separate status; these counts do not describe complete bundled packages.
- Reconcile all inventoried branch histories, preserve current qualified implementations over historical recovery snapshots, and retain the transform-pipeline parser regression improvement from the obsolete v0.3.1 branch.
- Restore scientific citation blocks for newer tutorials and update current download links.

## Scope and validation

Java 17 or newer is required. The release workflow runs `clean check assemble` on Linux/Java 17 before publishing executable, library, source and Javadoc JARs, the frozen inventory and SHA-256 checksums. See the retained [release validation record](https://github.com/robbyjo/OmicsMatic/blob/main/docs/releases/0.4.1/validation.md) for observed results.

Deterministic software and synthetic-data checks do not establish instrument validity, causal mediation, live-provider behavior or cohort scientific validity. Optional upstream runtimes are installed separately. Historical producer fingerprints and catalog predecessor/source-baseline restrictions remain applicable; this release makes no new whole-catalog qualification claim. Historical evidence and previous releases remain immutable.

