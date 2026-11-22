# OmicsMatic

Java software for biomedical and multiomics analysis: statistical models,
genetic and molecular workflows, bioinformatics tools, and reproducible studies
with optional, investigator-supervised AI.

**Process cleanup safety:** builds made before the November 22, 2026 ownership
fix can mistake an unrelated process for a worker after Windows reuses a PID.
Use the corrected source build before running external-tool workflows. Existing
release assets have not been replaced by this source change. Read the
[incident, validation limits and required actions](docs/literature/PROCESS_OWNERSHIP_SAFETY_2026-11-22.md).

**[Website and guides](https://robbyjo.github.io/OmicsMatic/)** ·
**[Download v0.4.0](https://github.com/robbyjo/OmicsMatic/releases/tag/v0.4.0)** ·
**[What's new](https://robbyjo.github.io/OmicsMatic/release-0.4.0.html)**

## Start an analysis

Use Java 17 or newer. Download the executable JAR from the release, then run:

```shell
java -jar omicsmatic-0.4.0.jar --help
```

Start with [your first study](https://robbyjo.github.io/OmicsMatic/vignettes/study-getting-started.html),
the [command-line guide](https://robbyjo.github.io/OmicsMatic/vignettes/command-line.html),
or the [bioinformatics toolbox](https://robbyjo.github.io/OmicsMatic/toolbox.html).
Optional upstream tools have their own runtime and input requirements.

## Build from source

```shell
./gradlew check executableJar
```

On Windows, use `gradlew.bat check executableJar`. The executable is
`build/cli/omicsmatic-0.4.0.jar`. See [runtime setup](docs/runtime-requirements.md)
and the [validation record](docs/release-0.4.0-freeze.md) for dependencies,
test coverage, and the remaining catalog qualification limitation.

## License

[GNU General Public License, version 3 or later](LICENSE).
