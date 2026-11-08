# OmicsMatic

OmicsMatic is a Java framework for biomedical and multiomics analysis, combining
reproducible statistical workflows with optional, investigator-supervised AI.

Visit the **[OmicsMatic website](https://robbyjo.github.io/OmicsMatic/)** for
capabilities, worked vignettes, validation results, and scientific citations.

## Choose your degree of AI collaboration

Begin with **[your first study.yaml](docs/vignettes/study-getting-started.md)**: minimum configuration, data placement, completed-analysis reuse, and running all or selected work. Then choose how to plan and review the analysis:

| Mode | Who plans and decides | Worked guide |
| --- | --- | --- |
| No AI | The investigator defines and approves a reproducible local workflow. | [Run without AI](docs/vignettes/ai-no-ai.md) |
| AI as an advisor | The built-in, opt-in adapter provides bounded advice through browser handoff or an explicitly selected API; the investigator reviews proposals. | [Collaborate with an advisor](docs/vignettes/ai-advisor.md) |
| AI as a driver | A separately managed agent coordinates existing CLI commands; scientific approval and data-access enforcement remain explicit. | [Coordinate an external agent](docs/vignettes/ai-driver.md) |

For configuration details, see the [study.yaml field guide](docs/vignettes/study-yaml-reference.md), [browser/API/OAuth setup](docs/vignettes/ai-connections.md), and [literature, tool choices and finding priorities](docs/vignettes/study-tools-literature.md).

Use the current source build's [first-login helper](docs/vignettes/ai-login.md) for OpenAI/Gemini browser authorization, guided Claude/Grok key setup, and SSH callbacks.

Saved AI credentials can use [password-encrypted files and supported automatic renewal](docs/vignettes/ai-credentials.md), with setup for OpenAI, Claude, Grok and Gemini.

The built-in advisor uses a bounded, policy-approved evidence preview and never
uploads patient files or grants shell or sample-matrix access. Aggregate findings
still require disclosure review; external agents need separate access controls.

## Build

Use JDK 17 or newer and the included Gradle wrapper. The first build downloads
dependencies. See [runtime setup](docs/runtime-requirements.md) for optional backends.

```shell
# Linux / macOS
./gradlew check executableJar
```

```powershell
# Windows
.\gradlew.bat check executableJar
```

The self-contained executable is `build/cli/omicsmatic-0.3.7.jar`.
[Published releases](https://github.com/robbyjo/OmicsMatic/releases) are also
available; source builds may include features beyond the latest release.

## Run an analysis

Fit a linear regression using the included synthetic phenotype table:

```shell
java -jar build/cli/omicsmatic-0.3.7.jar --pheno examples/quickstart/phenotype.tsv --id sample --formula "trait ~ age" --out build/quickstart/results.tsv
```

The result contains coefficient estimates, standard errors, and p-values,
with a companion run log. Choose a fresh output path for another run.

## Draw a plot

See the [command-line plotting vignette](https://robbyjo.github.io/OmicsMatic/plotting.html).

## Literature-driven bioinformatics toolbox

The [250-paper catalog](docs/literature/omics-2023-2026/README.md) records 390 named
tools and 2,450 paper-tool observations from Nature, Nature Genetics and AJHG.
There are 85 new scoped integrations (17 native Java methods and 68 upstream
adapters), or 88 including three existing adapters; 251 cataloged named tools still need
integration. The full implementation goal remains open.

The new [dndscv guide](docs/literature/DNDSCV_WORKFLOWS.md) covers native coding
annotation/opportunity methods, the tested original selection adapter, exact
runtime preparation and a runnable example. Use the current source build or the
separately validated development artifact. **SKIP3 skips dependent analysis when
the exact permitted runtime is unavailable; FAIL2 admits no result from an
atomic batch.** Read each tool's warnings and required actions before analysis.

The [SMR/HEIDI guide](docs/literature/SMR_WORKFLOWS.md) covers the tested original
SMR 1.3.1/1.4.3 adapter, native input and result audits, private runtime setup,
and a runnable synthetic example. Read its version, overlap and capacity limits.

The [LOFTEE workflow guide](docs/literature/LOFTEE_WORKFLOWS.md) covers the tested
original annotation adapter, exact private runtime preparation, complete native
audits and a compressed synthetic example. VEP cache execution remains skipped;
paper-specific transcript selection and variant masks remain separate.

The [biomaRt workflow guide](docs/literature/BIOMART_WORKFLOWS.md) covers original
annotation retrieval with native Java query planning, exact private R runtimes,
complete typed outputs and prominent skip/error recovery. Enrichment and
nearest-gene rules remain separate study decisions.

The [Hail workflow guide](docs/literature/HAIL_WORKFLOWS.md) covers original
QC/filtering, MatrixTable reuse, LD/PCA, liftover and LOCAL Batch DAGs, with a
separate exact private installer and prominent skip/failure recovery. The native
Java sample-QC component needs no Hail runtime. Cloud execution remains skipped.

The [LeafCutter workflow guide](docs/literature/LEAFCUTTER_WORKFLOWS.md) covers
original junction clustering, phenotype/PCA preparation and differential-splicing
fits, with native Java audits, a separate exact private installer and explicit
case-sensitive filesystem, skipped-analysis and atomic-failure recovery.

The [CellPhoneDB workflow guide](docs/literature/CELLPHONEDB_WORKFLOWS.md) covers
original5.0.1 simple, permutation and supplied-DEG methods, independent native
preparation/statistics audits, a separate permitted installer and prominent
entropy-subsampling, skipped-analysis and atomic-failure recovery. Five newly
cataloged software names increase the outstanding total; paper-wide review and
further named-software followups remain open.

## License

[GNU General Public License, version 3 or later](LICENSE) (`GPL-3.0-or-later`).
