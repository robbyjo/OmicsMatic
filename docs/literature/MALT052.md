# Original MALT 0.5.2 alignment and native classification

This standalone development command runs pinned original **MALT 0.5.2, built 28 January 2021**, with its bundled Linux Java 14 runtime. Java 21/25 controls execution and verifies saved output. It captures complete FASTQ/index inputs, owns the upstream process, checks every SAM alignment, compares aligned FASTA and raw RMA sequences with the complete input, and recomputes naive/readCount taxonomic assignments in Java.

**The original accession-map indexing path remains unqualified.** Both official 0.5.2 package builds contain the same MALT/MEGAN/jloda jars. The tested builder can exit zero while leaving all reference taxonomy IDs zero. This alignment command refuses zero, unknown or inconsistent reference taxa before fitting. Supply a validated, completely classified index. The [native index mapping stage](MALT_NATIVE_INDEX_TAXONOMY.md) is an explicitly different method and requires an existing complete, unclassified seed index. An owned, source-tested seed-index builder command is still separate work.

**This does not complete the whole MALT/HOPS/MaltExtract family or reproduce the paper.** The custom paper screen requires its complete dated 91-genome database and query population. Software controls use three complete mitochondrial reference genomes and cannot substitute for that database. No biological negative, pathogen authentication or whole-tool catalog credit follows from these checks.

## Install prerequisites where permitted

From a Linux x86_64 shell, with util-linux `prlimit` and `setsid` available:

```bash
python3 tools/literature/malt052/setup.py \
  --out /var/tmp/my-malt052-provider-1
```

Setup downloads [the exact public package](https://conda.anaconda.org/bioconda/noarch/malt-0.5.2-hdfd78af_1.tar.bz2), verifies its full size/SHA-256, and extracts only the original JRE, jars and licenses. It executes no package installation scripts and changes no system Java. Use a native Linux filesystem to preserve original modes and internal legal-document symlinks. For an offline installation, add `--archive /path/to/malt-0.5.2-hdfd78af_1.tar.bz2`.

The package is 141,110,092 bytes with SHA-256 `3cdf55e01405164cfa9c5f1435cbb5d66f305028cf5b9fec8a2bed66665d0dd2`. The full provider has 370 selected files/links; its byte/mode/link inventory digest is `0549790002c48ffb828ea4958fdf3d7e2cbf050873833672bcb7bfe771222ac2`. Keep its licenses and `RUNTIME_INVENTORY.json`. READY establishes prerequisite availability and help checks, not scientific equivalence.

**If download, installation or execution is unavailable or forbidden, skip the dependent analysis.** Setup/build prints **SKIP3**, the affected prerequisite and a required action. An owned failure directory contains **`FAILURE.json`** and **`WARNINGS_FAILURE.md`**. Retain those files and resolve the named issue before retrying a **new** directory. An existing output directory is left untouched. Never interpret a skipped or empty result as biological absence.

## Build the Java controller

Use a permitted complete JDK 21 or 25, from the repository root:

```powershell
python tools/literature/malt052/build.py `
  --java-bin 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' `
  --download-htsjdk --out build/malt052-controller-1
```

On Linux, use `python3`, your JDK `bin` directory and shell line continuations. Offline builds accept `--htsjdk-jar /path/to/htsjdk-5.0.0.jar` instead of downloading. The complete dependency is 2,087,306 bytes with SHA-256 `59d862b74ec3e847ec84bc87bed34cecd1a80095495d965b9403f863e374e99c`. Keep that exact jar beside `malt052-alignment-development.jar`, together with the project license. The controller targets Java 17 bytecode; qualification uses Java 21/25. This build does not replace the released executable.

## Run alignment

On Linux, using a complete classified index and unique-name FASTQ:

```bash
java -Xmx3g -jar build/malt052-controller-1/malt052-alignment-development.jar \
  --runtime /var/tmp/my-malt052-provider-1 \
  --database /path/to/complete-classified-index \
  --query /path/to/complete.fastq.gz \
  --index-provenance native-alternative \
  --out /path/to/new-malt052-result-1
```

Use `native-alternative` only when declaring that method difference. Use `--index-provenance supplied` for another supplied, validated index. This option records your declaration; it does not authenticate index construction. Verify a native derivation with its own command before using it. Source index, runtime and output paths must not overlap.

`CUSTOM052` is the default profile: BlastN, SemiGlobal, identity 90%, up to 25 alignments per query and 100,000 per reference, top 1%, support 0.01%, naive/unpaired/readCount assignment, LCA identity 0 and effective coverage 80%. The original help's coverage 100% belongs to the weighted-mode option and is not the naive constructor default. BlastN gaps are 7/3. The distinct `STANDALONE` profile selects Local, identity 0%, one alignment per reference, top 10% and support 0.001%.

Default resources are two alignment workers, a 4 GiB upstream heap and one parallel GC thread. The paper's reported resource request is representable with `--threads 16 --heap-mib 133120 --gc-threads 1` (130 GiB). Provision permitted resources before selecting it. Software validation uses the smaller request and makes no paper-scale performance claim. `--help` lists explicit complete-input, output, wall-time and heap limits. Exceeding a limit refuses the analysis; no prefix is substituted.

The leading `java -Xmx3g` controls the application heap; `--heap-mib` controls the separate upstream process. Larger complete-input limits can also require a larger application heap. If the necessary resources cannot be provided where permitted, skip that dependent analysis and retain its diagnostics. A JVM exit without `COMPONENT_COMPLETED.json` is not an admitted result.

RMA, SAM and aligned FASTA have separate output directories under `native/`, as required by this original version. Read **`WARNINGS.md`** and **`native/process/process.stderr`**. `audit.json` reports complete populations, original consumption counters, native classifications and support selection. The process ledger records original arguments, resource observations and cleanup. RSS is sampled; it is not a hard aggregate memory limit. Internal Nextflow labels refer to the shared process supervisor.

## Verify saved output on Windows or Linux

Install only the permitted three-jar reader provider when original fitting is unnecessary:

```powershell
python tools/literature/malt052/setup.py --reader-only `
  --archive path/to/malt-0.5.2-hdfd78af_1.tar.bz2 `
  --out build/malt052-reader-1

java -Xmx3g -jar build/malt052-controller-1/malt052-alignment-development.jar `
  --verify path/to/complete-saved-result `
  --runtime build/malt052-reader-1
```

The reader uses isolated, exact `malt.jar`, `megan.jar` and `jloda.jar` bytes. It does not substitute the older 0.4.1 reader. Native taxonomy includes all four original synthetic root leaves: `-1` No hits, `-2` Not assigned, `-3` Low complexity and `-6` Contaminants. Saved replay checks the complete file inventory, recomputes geometry/classification/export checks, and verifies inputs/outputs again. It does not refit MALT or authenticate an unsigned producer. A mismatch returns **FAIL2 NO RESULT ADMITTED**, with diagnostics and required recovery steps.

The four complete-input controls consumed 16,862 queries and produced 122 alignments, including a six-read support control where the original and native selected minimum is three. Complete ordered-taxonomy comparison covers all 2,259,893 nodes. Nine focused tests and twelve saved replays passed. All 12,275 source assignment cases now match, including the version-specific treatment of unknown, negative and NaN hit identities. Raw fields remain unchanged; 0.5.2 has its own native filtering rules.

These fitting controls cover `CUSTOM052`; additional selection of `STANDALONE`/Local still requires separate qualification. Source projection or population discrepancies cause refusal. Portable evidence packaging and the owned seed-index builder remain separate work; see the current [validation record](malt052-development/VALIDATION.json).

**Current environment warning:** after the completed Linux fitting controls, WSL creation returned `Wsl/Service/CreateInstance/CreateVm/ERROR_TIMEOUT` for an additional source-oracle run. That Linux run was not started and is recorded as skipped. Restore a working, permitted Linux/WSL session before further original fitting. The equivalent Windows source comparison completed with explicitly pinned OpenJFX 14 base/graphics dependencies; production saved replay needs only the three reader jars. Full family/catalog implementation remains in progress.
