# What's new in OmicsMatic v0.4.0

OmicsMatic v0.4.0 brings a larger bioinformatics toolbox together with more
complete study execution, association analysis and evidence-linked reporting.
The release keeps the frozen tool boundary while ongoing development continues
on main.

[Download v0.4.0](https://github.com/robbyjo/OmicsMatic/releases/tag/v0.4.0) ·
[Start your first study](vignettes/study-getting-started.md) ·
[Browse the toolbox](https://robbyjo.github.io/OmicsMatic/toolbox.html)

## Bioinformatics workflows

The frozen literature catalog contains **122 newly integrated tool families**:
21 validated native subsets and 101 validated upstream workflows. These counts
describe recorded, operation-specific qualifications, not complete packages or
installed runtimes. Existing adapters, lookups, exports and related native
methods remain separate categories. Of 446 cataloged families, 265 remain
missing and one is excluded.

The searchable [tool directory](https://robbyjo.github.io/OmicsMatic/toolbox.html)
lists every family, its supported scope, limitations and source or workflow
guide. Examples include:

- **Sequence and file processing:** native QC, adapter trimming, SAM/BAM
  operations and HTSeq counting; original-engine routes for STAR, HISAT2,
  featureCounts, Salmon, kallisto and other sequencing tools.
- **Genetic and regulatory interpretation:** FUMA mapping and enrichment
  subsets, MAGMA, SMR/HEIDI, LOFTEE, HOMER, MEME and Tomtom workflows, alongside
  native fine-mapping and colocalization methods.
- **Single-cell and spatial follow-up:** SingleR and celldex reference-based
  annotation, SPOTlight deconvolution, HICAT doublet workflows, scDRS, CellChat
  and CellPhoneDB within their documented profiles.
- **Additional completed integrations:** BIGCAT workflows, MELODI service
  operations and native TPMCalculator. iDEP development remains outside the
  admitted toolset.

Use the [native replacement guide](native-tool-replacements.md) for Java-only
operations. Each original-engine route documents its separate installation,
resources, supported versions and recovery steps. A catalog entry does not
automatically connect every operation to the study planner.

## Association and cross-omics adjustment

Mapped omics adjustment can select feature-specific covariates from multiple
sources, apply registered transforms and refit on the complete cases for each
target feature. Supported routes include OLS, GLM, LMM and GLMM; mapped
adjustment is not supported for Cox or genotype scans. The
[worked example](../examples/mapped-omics-association/README.md) shows the mapping
files, model setup, result metadata and independent comparisons.

The study guides also cover genotype QC and projected PCs, Firth and exact
conditional binary tests, rare-set analyses, registered subgroup interactions,
native Cox association and frozen genetic scores. Choose the model supported
by the design rather than treating these routes as interchangeable. See the
[association tutorial](vignettes/cli-association-tutorial.md) and the
[study pipeline guide](vignettes/study-pipeline-tutorial.md).

## Reuse completed analyses and run missing work

Supply validated primary results, meta-analysis outputs or supported follow-up
artifacts, then run the work still needed. Reuse preserves result identities,
testing families and provenance. Partial supplied stages retain explicit
coverage and unresolved items; they do not silently become complete results.

Newer study capabilities include cohort overlap and heterogeneity diagnostics,
covariance-aware pooling, native ranked GSEA and LD-aware category enrichment,
network modules and preservation, held-out score development, and more complete
MR and colocalization records. Read the
[study tutorial](vignettes/study-pipeline-tutorial.md) for the exact inputs and
remaining implementation boundaries.

Study cleanup now provides an auditable plan and explicit execution controls.
Inspect `study help` and the [pipeline guide](vignettes/auditable-omics-pipeline.md)
before removing derived results.

## Useful follow-ups and manuscript evidence

The [analysis specification](vignettes/analysis-pipeline-specification.md)
now describes conditional SNP analysis starting from the strongest SNP or a
prespecified file, followed by forward selection or a configured advanced
method. It also describes selecting useful bioinformatics operations and linking
their results to the corresponding variants, loci, genes, tissues and findings.
These are workflow requirements; they do not imply new automatic adapters for
every tool.

Manuscript composition and revision carry finding identities, source-linked
claims, uncertainty and incomplete analyses into reports. Recent template
improvements cover connected introductions and discussions, structured
abstracts, local Table 1, mathematical typography and appropriate decimal
precision. See the [manuscript template](manuscript-writing-template.md),
[tools and literature guide](vignettes/study-tools-literature.md), and
[implementation status](analysis-pipeline-implementation.md).

## Optional AI collaboration

Run [without AI](vignettes/ai-no-ai.md), use an
[AI advisor](vignettes/ai-advisor.md), or coordinate approved commands with an
[external agent](vignettes/ai-driver.md). Browser/API setup, OAuth login,
encrypted credential storage and supported renewal have dedicated
[connection guides](vignettes/ai-connections.md).

The built-in advisor uses a bounded evidence preview under the configured
disclosure policy. Aggregate findings still require review; external agents
need their own data-access controls. These mechanisms do not constitute a
whole-system privacy guarantee.

## Downloads, verification and known limitations

The release provides an executable JAR, library JAR, sources, Javadoc, the
frozen tool inventory and SHA-256 checksums. Java 17 is the application target;
optional upstream runtimes have separate requirements.

The frozen Windows/JDK 21 run recorded **25,078 passed tests, 441 skipped tests,
and no failures or errors across 878 suites**. The executable's synthetic OLS
results matched an independent NumPy calculation, and the build, website links
and JAR archive checks passed. Skips include optional runtime, platform and live
checks; software tests do not certify all scientific applications.

**Known catalog limitation:** the catalog-wide verifier refuses changes relative
to its historical predecessor source baseline. The original tool-specific
qualifications and the failed verification record are preserved. This release
does not claim newly verified whole-catalog equivalence or a pass of that check.
See the [freeze and validation record](release-0.4.0-freeze.md) for full details.
