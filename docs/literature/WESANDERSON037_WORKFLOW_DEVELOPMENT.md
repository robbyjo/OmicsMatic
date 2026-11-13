# wesanderson 0.3.7: original package and native Java palette engine

**Warnings and required actions:** native colors need no R installation. Original R functions and plots require a permitted Linux/WSL R 4.5.3 and the separately installed unchanged package. If a prerequisite is unavailable or installation is forbidden, keep **SKIP3** and skip that dependent operation. **FAIL2 admits no result**, including a partial output directory. Read the saved warning and raw logs, correct the named cause and retry NEW paths. Preserve older outputs and failures.

This development component supplies all 24 original palette vectors, scalar discrete/continuous generation in Java, a standalone command and complete saved-result replay. The original R package remains available through a private source-install provider. Its actual `print.palette` function generated all 24 retained PDF plots. R graphical devices, arbitrary R expressions and R-language vector/coercion rules are not Java ports.

The canonical `build/cli/omicsmatic-0.3.7.jar` is unchanged. Use the separate development executable below. Component qualification adds no catalog badge automatically; the full 250-paper curation and software implementation goal remains open.

## Build and start

Use Python 3.11 or later and a permitted matching JDK 21 or 25 compiler/runtime. The builder refuses unsupported compiler profiles. The produced classes target Java 17; qualification used Windows Java 21, Windows Java 25 and Linux Java 21. The native executable has no external library or R dependency.

```powershell
python tools/literature/wesanderson037/build.py `
  --java-bin "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin" `
  --out build/new-wesanderson-build

java -jar build/new-wesanderson-build/wesanderson037-native-development.jar --help
java -jar build/new-wesanderson-build/wesanderson037-native-development.jar palettes --out build/new-wesanderson-list
java -jar build/new-wesanderson-build/wesanderson037-native-development.jar generate --name Zissou1 --n 256 --type continuous --out build/new-wesanderson-colors
java -jar build/new-wesanderson-build/wesanderson037-native-development.jar verify --bundle build/new-wesanderson-colors --out build/new-wesanderson-replay
```

Replace the JDK path with your qualified installation. Use a different output path for each command and retry. Linux uses the same arguments with its JDK `bin` path. No download or package installation is needed for these native commands. The named palette and length are examples; they do not recover the paper's settings.

`palettes` writes every original palette entry, in source order, to `colors.tsv`. `generate` requires an explicit `--name`; omitting `--n` or `--type` preserves the corresponding original default. `--type` accepts the original unique prefixes, including `d`, `disc`, `c` and `cont`. `--maximum-colors` is an explicit complete-output limit from 1 to 1,000,000, defaulting to 1,000,000. Exceeding it refuses the request; no prefix is accepted as a complete result.

Successful result directories contain the full color table, returned name/class/length attributes, typed request, runtime record, exact source profile, implementation-source checksums, admission status, required actions and a complete-file manifest. Failure directories prominently state that no result is admitted. Existing output directories are retained and refused.

`verify` checks the complete file inventory and hashes, requires matching implementation/source provenance, recomputes every color and returned attribute from the saved request, and checks the saved admission contract and required-action text. It rejects tested resealed changes to colors, attributes, requests, admission metadata, source metadata, implementation metadata and warnings. It also refuses missing/extra files and output paths overlapping the saved input. Hashes establish integrity, not authenticity; replay is not an independent R refit or proof that the original author created the request.

## Use the Java API

Add the standalone JAR to your Java classpath:

```java
import org.jlinalg.plot.WesAnderson037;

var palette = WesAnderson037.palette("Zissou1", 256.0, "continuous");
System.out.println(palette.name());
System.out.println(palette.sourceClass());
System.out.println(palette.colors());
var allOriginalPalettes = WesAnderson037.palettes();
```

The returned colors and palette map are immutable. Java `null` for the number or type denotes the omitted original argument. The API accepts a scalar `Double`, not arbitrary R vectors or coercible R objects. Its four-argument overload accepts an explicit complete-output capacity. Source errors become Java exceptions; exact R diagnostic strings and condition objects are retained in the R reference packet rather than presented as identical Java exceptions.

Original edge behavior is intentional. Discrete `n=0` returns the first color because the source indexes with `1:n`. Some negative or near-integer counts therefore behave differently from a generic integer-length color generator. Nonfinite and invalid requests fail instead of being repaired. `Rushmore` and `Rushmore1` are separate source names with the same vectors. The lowercase entry in discrete `AsteroidCity2` is retained. Continuous colors use the source's uppercase RGB output.

For continuous `Zissou1`, the original function uses the 11-color `Zissou1Continuous` vector; its omitted continuous length is consequently 11, while discrete `Zissou1` has five entries. Interpolation follows the exact R 4.5.3 sequence-grid and arithmetic operation order, followed by its RGB truncation. Direct division, rounding or replacing the source palette changes some returned colors.

## Install and use the original R package when permitted

The qualified original reference profile is Linux/WSL R 4.5.3. Provide its actual `Rscript` path. The provider does not modify that R installation, the system package library or an earlier private provider. It installs only the original package into its new output's `library` directory. R source installation and the readiness check are not paper analysis.

```bash
python3 tools/literature/wesanderson037/provider.py \
  --rscript /path/to/R-4.5.3/bin/Rscript \
  --allow-download --allow-install \
  --out build/new-wesanderson-original
```

To work from a permitted cached archive, replace `--allow-download` with `--source-archive /path/to/wesanderson_0.3.7.tar.gz`. The helper verifies the exact archive and all 13 original files. Download permission does not imply installation permission; `--allow-install` must be explicit. Without the required permission, source or runtime, dependent original R work is skipped with SKIP3 and a recovery notice.

After `READY.json` reports readiness, the complete original namespace is usable in that R runtime. For example, save the following as your own R script and invoke it with the qualified Rscript. Replace `lib` and the new PDF path with your actual paths:

```r
lib <- "build/new-wesanderson-original/library"
library(wesanderson, lib.loc = lib)
cols <- wes_palette("Zissou1", 256, type = "continuous")
pdf("build/my-new-original-palette.pdf")
print(cols)  # The unchanged original S3 plotting method.
dev.off()
```

That explicit script performs the requested original plotting operation; provider setup alone does not. Use original package functions and its `heatmap` example dataset through R when needed. If R 4.5.3 cannot be installed or used, retain the skip for original R plotting and use the native color-table operations independently. Do not label a different graphics implementation as the original R print method.

## Paper role and edition boundary

P054, [The Microflora Danica atlas of Danish environmental microbiomes](https://doi.org/10.1038/s41586-025-09794-2), explicitly reports `wesanderson` 0.3.7 for the color scale of the ontology's MFOD1 levels. The same paragraph assigns colorblind-friendly continuous gradients to `viridisLite` 0.4.2, and describes plotting and figure assembly with other named tools. Those are distinct roles.

The paper reports R versions 4.2.3 through 4.4.0. The qualified original comparison here uses R 4.5.3. The actual Wes Anderson palette name, number of colors, discrete/continuous call and category-to-color mapping for MFOD1 are unreported in that paragraph. They remain unresolved. This component neither infers them nor claims exact author-runtime or figure reproduction. `PAPER_USE.json` retains the exact primary XML and paragraph hashes and the scoped individual review; the broader software census remains open.

## Qualification and independent evidence

The complete original package archive is 6,486 bytes, SHA-256 `c92e5604e1e149e00f49fd236c6ab8cd09d96106eb14479f7839e6996bf95e4e`. It is unchanged. The package's MIT notice/template, the project GPLv3 text and original R source/license notices accompany the development JARs. The retained R 4.5.3 sequence, interpolation and RGB sources identify the reference behavior.

The three final packaged artifacts each passed the complete 6,108-case API comparison: 224,445 returned colors, all returned attributes, all 24 palette vectors and 3,397 original failures. Each runtime also passed 72 command cases against complete original vectors: 62 successes, 10 source failures, 1,490 colors and 63 complete saved-result replays. Seven resealed computational/provenance mutations, two protected-output refusals, two missing/extra-file refusals, ten invalid requests and a missing-provenance skip were tested per runtime.

The public original provider generated another complete 6,108-case reference packet and all 24 actual original PDF plots. Its complete returned-object RDS files and TSV vectors match the independent original packet byte for byte. Setup controls passed two actual new private source installs, one official download and nine setup refusals. They did not perform paper analysis or alter the existing R runtime.

The initial 6,012-case Java comparison failed in 604 cases because its sequence-grid calculation used a different floating-point operation order. Its source, full mismatch logs and original outputs remain retained as a failure. The corrected operation order passed all 6,012 cases; the expanded 6,108-case original packet then passed on all three runtimes. Earlier packages and the later addition of the complete project license remain distinguishable in retained build snapshots. None of the earlier failures or provisional builds is silently relabelled as final qualification.

The self-contained evidence archive retains full original vectors, objects, error messages and classes, all original plots, exact source packages, complete process outputs, typed result bundles, refusals, source snapshots and historical failed attempts. The replay verifies every archived file before rebuilding the qualified current native sources and rerunning the full packaged comparisons. It performs no R installation and does not repeat original R analysis.

```powershell
python tools/literature/wesanderson037/verify.py `
  --java-bin "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin" `
  --allow-build --allow-analysis `
  --out build/new-wesanderson-offline-replay
```

Omitting either permission verifies retained evidence and then skips the dependent rebuild/analysis with SKIP3. A missing compiler also skips dependent work. A mismatch is FAIL2 and admits no qualification. Keep its complete diagnostics and retry a new directory after correcting the named cause.
