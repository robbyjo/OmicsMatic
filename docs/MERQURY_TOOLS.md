# Merqury counting and assembly assessment

`merqury-tools` runs explicitly selected Merqury/Meryl programs and checks their complete results with Java methods. It supports ordinary k-mer counting from complete FASTA/FASTQ inputs, assembly QV/completeness/copy-number spectra and error-position tracks, optional source false duplication, and parental hap-mer database construction. Verkko assembly, Yak phasing error analysis, Merqury trio phase blocks and homopolymer-compressed k-mers require separate workflows.

Build the current project source with its `executableJar` Gradle task. Earlier 0.4.1 release downloads predate this command. Keep the exact producing JAR at the recorded path, together with its bound runtime; saved binary replay uses those recorded locations.

**A successful original shell exit is insufficient.** Require `COMPLETED.json`, inspect `WARNINGS.md` and `OUTPUT-AUDIT.json`, and run saved verification with the exact producing JAR. A failed attempt remains retained and admits no complete result.

| Exit | Meaning | Required action |
| --- | --- | --- |
| 0 | The requested scoped workflow and complete checks passed. Runtime binding alone performs no scientific analysis. | Inspect the outputs, source profile, warnings, nonfinite values and preparation provenance. Verify the saved capture. |
| 3 / SKIP3 | A required permitted prerequisite is missing, unavailable or forbidden. Dependent analysis was skipped. | Read the named missing path/program/library and binding diagnostics. Supply or install the exact dependency only where permitted, then create a new binding and analysis directory. If installation is forbidden, retain the skip and obtain an administrator-provided runtime. |
| 2 / FAIL2 | Invalid/changed data or runtime, resource/cancellation failure, upstream failure, undefined requested rate, incomplete output or numerical inconsistency. | Retain the failed directory. Inspect `FAILURE.json`, `WORKER-FAILURE.json` or `BINDING-FAILURE.json` and the original process logs. Correct the named cause and retry to new paths. Do not refresh old hashes or accept partial output. |

## Select an exact source profile

| Merqury source | Meryl binary | Source mode |
| --- | --- | --- |
| 1.1, commit `27a15fc2f42e6eab0889c36681af831f7f8209b2` | 1.0 Linux x86_64 | `original` |
| 1.3, commit `6b5405e68a763f6c68927b17db1e874251aea3e0` | 1.3 Linux x86_64 | `original` |
| 1.4.1, commit `88f37c0f4dbb073001d194bcc5d5989a0b0527df` | 1.4.1 Linux x86_64 | `original` or `native-boundary-palette-v1` |

The separately selected native mode changes two captured source files: the `filt.sh` call to the histogram helper and the copy-spectrum plotting palette/legend. Java computes the original first strict slope-sign crossing from the **complete** histogram. Sparse bins retain the original current-depth-minus-one rule; no crossing leaves zero. The output is a separately named `.hist.boundary.tsv`, not a full ploidy table. The original full-table formatter can fail on unpaired arrays. The palette covers the union of data and assembly-only overlay categories while retaining the actual data legend labels. No read, count, scientific parameter or threshold is silently changed.

This source modification is explicit. It is never an automatic fallback and must not be described as an inferred paper edition. Upstream source, the historical negative cases and their diagnostic outputs remain separate.

## Prepare a permitted Linux runtime

Execution and full binary verification require Linux x86_64 Java. On Windows, run Linux Java inside WSL; a Windows Java launch returns SKIP3 for these operations. The Java numerical classes are platform independent.

Supply the exact selected unmodified [Merqury source](https://github.com/marbl/merqury) and [Meryl release](https://github.com/marbl/meryl/releases), IGVtools 2.19.8 with its complete `lib` directory, Java 21, Python, SAMtools, bedtools, and an R environment. The bound R profile checks R 4.5.3, ggplot2 4.0.4, scales 1.4.0, argparse 2.2.5 and findpython 1.0.9. Retain each component's license and notices. Obtain dependencies only through downloads/installations permitted by your environment. Binding itself installs nothing, changes no shared runtime library and performs no network download.

In a Linux shell, replace `/absolute/omicsmatic.jar` with the complete executable project JAR:

```sh
java -jar /absolute/omicsmatic.jar merqury-tools --example binding > binding-request.json
```

Edit every placeholder before running it. Select the source mode deliberately. `source` is the complete unmodified selected source directory. `python` must select the same isolated Python resolved as `python3` by the declared PATH. Put the selected Meryl binary directory first; also include IGVtools, SAMtools, the R environment, bedtools, Java and the required shell utilities. `R_LIBS_USER`, `JAVA_HOME`, PATH and library search directories are explicit. Declare existing complete permitted runtime roots; include the R environment, IGV, private tools, Python standard library, fonts/font configuration and applicable font cache. Remove unused nonexistent library-search entries in a **new** request rather than inventing a dependency. The binder also discovers full R library populations, selected interpreters and actual dynamic-library dependencies.

```sh
java -Xmx2g -jar /absolute/omicsmatic.jar merqury-tools \
  --bind /absolute/binding-request.json --out /absolute/new-runtime-binding
```

Keep the binding directory and producing JAR. The full census includes empty directories, original bytes, modes, selected PATH programs, links and separately resolved targets. An unused dangling metadata link, such as Debian's optional JDK `src.zip`, is recorded unresolved and must remain so; required executable/library resolution still has to pass. Binding warnings and health logs are retained. Changed runtimes require investigation and a new permitted binding, never resealing historical evidence.

## Count reads, assess assemblies or construct hap-mers

Generate the request for the desired mode:

```sh
java -jar /absolute/omicsmatic.jar merqury-tools --example count-kmers > count-request.json
java -jar /absolute/omicsmatic.jar merqury-tools --example evaluate > evaluate-request.json
java -jar /absolute/omicsmatic.jar merqury-tools --example build-hapmers > hapmer-request.json
```

Edit the selected request. Supply actual preparation/reference provenance, the completed runtime binding, the full ordered input population, scientifically chosen `k` and mode settings, and explicit capacities. The examples are editable templates, not paper-specific defaults.

Use canonical absolute database directories. If an upstream output name is a symbolic alias, resolve it with `readlink -f` before declaring that directory; the adapter does not silently change input paths or roles.

- **count-kmers:** every input has role `reads`, kind `fastx`, and a supported FASTA/FASTQ filename suffix. Multiple files, including deliberately repeated inputs, are summed without deduplication. Set `count_memory_gib`; other modes use null. Original `union_sum.sh` requires an open-file limit of at least 32000 for multiple inputs.
- **evaluate:** supply `reads` as a complete Meryl database followed by `assembly1` and optionally `assembly2` as complete FASTAs. The supplied read database must use the declared k and ordinary, uncompressed k-mer domain. Set `false_duplications` only when requesting the source rate. `filter_parents` and count memory are null.
- **build-hapmers:** supply complete `mat`, `pat` and optional `child` Meryl databases. Select `filter_parents`; Merqury 1.1 requires true because that edition has no `-no-filt` flag. Child inheritance filtering remains distinct from the parent-filter option. The resulting parental databases are inputs for a separately configured assembler.

```sh
java -Xmx4g -Djava.awt.headless=true -jar /absolute/omicsmatic.jar merqury-tools \
  --request /absolute/evaluate-request.json --out /absolute/new-assessment

java -Xmx4g -Djava.awt.headless=true -jar /absolute/omicsmatic.jar merqury-tools \
  --verify /absolute/new-assessment --scratch /absolute/new-verification
```

The new output and verification directories must not exist. Keep complete captures and all failed attempts. Full verification needs the exact producing JAR, original bound runtime and a new scratch directory; original absolute input paths are provenance, while their complete captured bytes are replayed. Verification freshly decodes every opaque Meryl database with the original binary and repeats all Java equations. It does not recount new reads or fit a model. Legacy TDF verification also reconstructs deterministic coverage from saved, independently validated BED intervals and the full FASTA-checked FAI dictionary, comparing every decoded dataset/tile/coordinate/name/value with original IGV.

## Read the numerical results carefully

Global QV weights missing k-mer **occurrences**; completeness counts **distinct solid read k-mers** above the source automatic cutoff. Copy-number spectra retain every read multiplicity bin. Meryl canonical words use encoded A,C,T,G order rather than ASCII A,C,G,T order. U and ambiguous IUPAC bases break windows. Repeated record identifiers remain separate records; no sorting, identifier repair, input intersection or imputation is performed. Assembly assessment requires FASTA.

Source false duplication sums distinct copy classes below 1.5 times the first copy-one histogram mode, using source rounding. An absent copy class leaves an empty shell field and can shift the original report columns. A requested undefined rate fails rather than receiving invented zero counts. Inspect the complete spectrum before selecting a scientifically appropriate new request.

Source QV/completeness arithmetic can produce Inf/NaN, including zero missing windows or no solid read keys. These remain labelled values and do not demonstrate perfect biological accuracy. Legacy IGV summary bins can contain NaN or zero-width coordinates; complete decoded source values are preserved. Merqury 1.1 hard-codes 21-base BED intervals. Original Meryl 1.3 lookup can fail with a nonsensical enormous-memory message while the top-level shell still exits zero; the adapter checks complete per-sequence results and tracks instead of accepting that exit. Original plotting can also fail on sparse copy categories. Inspect all original logs.

Native complete count tables and each complete sequence must fit the declared capacities and Java heap. Full genome audits can require substantial memory; increase explicit limits and use suitable permitted hardware, or retain the failure. No input is sampled to fit. External processes have hard per-process AS/CPU/file/nofile limits and owned wall/workspace/process/thread checks with sampled aggregate RSS. Sampled RSS is not a hard aggregate bound; native checks use cooperative cancellation/wall limits.

These checks qualify the requested component workflow. They do not establish whole-package, whole-paper or biological reproduction. Hashes establish integrity rather than authenticity or scientific validity.
