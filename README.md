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

## License

[GNU General Public License, version 3 or later](LICENSE) (`GPL-3.0-or-later`).
