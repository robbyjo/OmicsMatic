# OmicsMatic

Java software for biomedical and multiomics analysis, statistical models,
bioinformatics workflows and reproducible studies with optional supervised AI.

**[Download v0.4.1](https://github.com/robbyjo/OmicsMatic/releases/tag/v0.4.1)** ·
**[Website and guides](https://robbyjo.github.io/OmicsMatic/)** ·
**[What's new](docs/release-0.4.1.md)**

Use Java 17 or newer:

```shell
java -jar omicsmatic-0.4.1.jar --help
```

Start with [your first study](https://robbyjo.github.io/OmicsMatic/vignettes/study-getting-started.html)
or the [command-line guide](https://robbyjo.github.io/OmicsMatic/vignettes/command-line.html).
The [linked-variant MR tutorial](docs/vignettes/linked-variant-mr.md) explains
exact-first matching and quality-aware LD proxy fallback.

v0.4.1 includes the [worker ownership safety correction](docs/literature/PROCESS_OWNERSHIP_SAFETY_2026-11-22.md)
and subsequent scoped workflow additions. Optional upstream tools require their
documented runtimes; supported operations and versions have explicit limits.
Read the [release scope](docs/release-0.4.1.md) and
[validation record](docs/releases/0.4.1/validation.md).

Build from source with `./gradlew check executableJar` (`gradlew.bat` on Windows).
The executable is `build/cli/omicsmatic-0.4.1.jar`.
See [runtime setup](docs/runtime-requirements.md).
The source-built [Popcorn workflows](docs/popcorn-tools.md) add original
cross-population GWAS covariance scores and correlation fitting, native Java
regression checks, explicit runtime setup and complete saved-input replay.
The source-built [velocyto counting workflows](docs/velocyto-tools.md) add original
spliced/unspliced counts from supplied BAMs, native Java preparation and exact
S+U matrices, explicit integer-overflow checks and complete fresh replay.
The current source build provides the exact-edition
[nf-core ChIP-seq commands](docs/literature/NFCORE_CHIPSEQ_COMMANDS.md).
It also provides original-edition [CrossMap BED, VCF and BigWig conversion](docs/literature/CROSSMAP_WORKFLOWS.md), with separate runtime setup and portable result verification.
The source-built [GEMMA workflows](docs/literature/GEMMA_WORKFLOWS.md) add Java
kinship and original univariate association fitting, with explicit sample/QC
contracts, separate private runtime setup and portable result verification.

The source includes [native MetaXcan summary workflows](docs/literature/METAXCAN_WORKFLOWS.md)
from PredictDB, GWAS and covariance inputs, with complete captured-result
recomputation, plus the [association arithmetic API](docs/literature/METAXCAN_ARITHMETIC.md).
Individual-level integration and model training remain unqualified.

The source-built [native iq MaxLFQ workflow](docs/literature/IQ_MAXLFQ_WORKFLOWS.md)
aggregates prepared normalized-log2 ion measurements into protein quantities,
with captured-input numerical replay and no R dependency during analysis.
Raw mass-spectrometry search and Proteograph/PepCal preparation remain separate.

The [DIA-NN command](docs/literature/DIA_NN.md) is available from source for bounded
local library-free protein quantification with original Linux 1.8, 1.8.1 and 2.0.2
runtimes. Java captures and audits inputs and outputs; original prediction and
raw fitting remain upstream. Complete two-run controls and Windows/Linux saved
readback are validated. Supply a separately permitted runtime and read the
scope limits and warning actions before analysis.

The source-built [spectral-library annotation command](docs/literature/SPECTRAL_LIBRARY_ANNOTATIONS.md)
extracts explicitly stated b/y fragment charges and numbers into separate columns,
preserving every original field and record with complete captured-input replay.
Raw-MS analysis and upstream runtime qualification remain separate.

The source-built [native contact-map insulation workflow](docs/literature/CONTACT_INSULATION.md)
calculates weighted cis insulation and fixed numeric boundary strengths from
complete prepared sparse tables, with captured-input numerical replay.
Raw Cooler preparation, eigenvectors, dots and ALLCools remain separate.
The [arithmetic validation supplement](docs/literature/contact-insulation-arithmetic/README.md)
documents the explicit median-overflow refusal and unchanged reference-map results.

[GNU General Public License, version 3 or later](LICENSE).
