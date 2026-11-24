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

[GNU General Public License, version 3 or later](LICENSE).
