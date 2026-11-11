# MAFFT development: complete inputs, alignment audits and original controls

**The scoped MAFFT alignment/addition adapter is validated and integrated into the catalog.** Current source includes native Java FASTA/alignment/map audits, six exact private Linux runtime editions, declared host-file admission and the typed `mafft-tools` command with owned execution, restored headers/case, a complete residue ledger and portable bundle verification. All 57 representative packaged protocols match their expected outcomes, and all eight paper-role reviews are applied. This source-build capability is separate from older released 0.3.7 executables, which lack the command. Unavailable or forbidden exact dependencies require a prominent warning and skipped dependent analysis; no biological alignment-accuracy or whole-paper reconstruction certificate is claimed.

The eight paper uses report six exact editions: **7.313, 7.475, 7.487, 7.490, 7.505 and 7.526**. P052 reports only major edition **7**, for an add-to-alignment tool. Do not silently replace that unresolved edition with one of the six releases.

## Native Java APIs

**org.jlinalg.sequence.MafftSequences** reads complete plain UTF-8 FASTA with explicit nucleotide/protein alphabet, symbol policy, header interpretation and aligned/unaligned layout. It retains row order, original headers, literal residue characters, case and gap characters. Sequence spaces and tabs are formatting: they are removed and counted. Complete sequence, cell, decoded-character, line, header and estimated-retention capacities are explicit. Limits reject the entire input; no prefix, shortened sequence or sampled population is returned.

Supplied readers are owned. EOF and successful close are required before a Data result is returned. Malformed UTF-8, duplicate identities, bad later rows, cancellation and read/close failures return no data. FIRST_FIELD preserves the original description but uses only the first ASCII space/tab-delimited field as the taxon identity; collisions fail. WHOLE_HEADER uses the exact whole header, including Unicode. Name mappings are copied before exports and audits, so writer/mapping callbacks cannot rebind later taxa.

Symbols.BIOLOGICAL is the standard MAFFT input set admitted by this integration; it is not a statement that other symbols are biologically invalid. Original controls show that protein U needs the explicit source --anysymbol choice. ANY_PRINTABLE retains non-whitespace printable ASCII symbols literally. The aligner and its scoring interpretation still require a separate explicit contract.

~~~java
var input = MafftSequences.readFasta(
    Path.of("sequences.fasta"),
    MafftSequences.Alphabet.NUCLEOTIDE,
    MafftSequences.Symbols.BIOLOGICAL,
    MafftSequences.Headers.WHOLE_HEADER,
    MafftSequences.Layout.SEQUENCES,
    MafftSequences.Gaps.ALLOW,
    MafftSequences.Limits.defaults());
var originalToOpaque = input.originalToOpaque();
try (var writer = Files.newBufferedWriter(
        Path.of("prepared.fasta"), StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW)) {
    // Explicitly remove existing input gaps for a fresh alignment.
    // The input object's original rows remain unchanged.
    input.writeFasta(writer, originalToOpaque, true, 80);
}
~~~

These methods parse and prepare data; the example does not execute MAFFT. Keep the original source file and its raw-byte hash separately. These APIs do not seal external files or runtimes before/after execution. Estimated retained bytes are a capacity estimate, not a hard process-RAM guarantee.

**MafftSequences.audit()** checks the exact complete output taxon set, explicit input/source row-order policy, every original non-gap residue, every output cell and all-gap columns. ASCII case may change and changes are counted; U and T remain distinct. Omitted, inserted or changed residues fail. A one-to-many alias cannot hide an extra output row. The audit does not score alignment optimality or biological accuracy.

## Existing alignments and deliberate deletion

**auditBackbone(..., Backbone.EXPAND)** checks every informative original backbone column while permitting insertion of backbone-gap columns and source removal of all-gap columns. It reports original and output backbone-gap counts separately. **It does not infer unique original-to-output identities for all-gap columns.** Do not derive an exact deletion/ownership map from the difference between those counts.

Backbone.KEEP_LENGTH requires the complete original backbone width and every original character/gap at its indexed column. An ordinary residue-preserving audit is insufficient for --keeplength, because the original program deliberately deletes insertions from added sequences.

**org.jlinalg.sequence.MafftAddMap** reads every original --mapout row. It verifies complete added-taxon families, every original ungapped residue position, the raw input character column, every mapped output position and every deleted residue. Positions are one-based; output column -1 means a source-deleted insertion. Raw and source-printed residue characters remain separate, with case changes counted.

**Callback rows remain provisional until EOF and successful close return a complete audit.** A later truncation, extra residue, duplicate taxon/coordinate, unaccounted output residue, changed symbol, capacity failure or close failure invalidates the map. Preserve partial ledgers but never admit them as complete. Combine the complete taxon/order check, backbone audit and add-map audit before accepting a keep-length result.

The maintainer documents that map output activates keep-length behavior and that inserted residues can be removed. These are explicit analysis choices, not harmless formatting conversions. See the [original add/keep-length documentation](https://mafft.cbrc.jp/alignment/software/addsequences.html).

## Original runtime and test evidence

The maintainer's unmodified [Linux portable distributions](https://mafft.cbrc.jp/alignment/software/linuxportable.html) were downloaded for all six exact editions and unpacked privately. No compiler or global installation was needed. Original programs remain outside the repository. The retained [BSD notice](../../tools/literature/mafft-runtime/BSD-NOTICE.txt), [download/member lock](../../tools/literature/mafft-runtime-lock.json) and four synthetic health inputs support the private setup described below. Setup evidence and full scientific execution admission have separate gates.

**WARNING — original 7.475 memory behavior.** The maintainer reports an FFT-NS-i memory-allocation bug in 7.463–7.486 that can also affect --auto. Preserve the exact requested release and any failed outputs; never silently upgrade a paper's edition. Successful tiny single-thread controls do not establish large-input or multithread resource behavior. See the [official changelog](https://mafft.cbrc.jp/alignment/software/changelog.html).

The [complete original-control archive](../../src/test/resources/org/jlinalg/sequence/mafft-original/original-controls.zip) retains 60 actual protocols across six editions: **48 alignment fits, six explicit standard-protein alphabet refusals and six version probes**. Eight successful protocol types reuse six small synthetic input families; these are not 48 independent biological datasets. The controls cover nucleotide auto/global-pair alignment, protein L-INS-i, add/keep-length, unusual symbols and existing gaps. All owned process groups were confirmed terminal. The six keep-length controls retain complete maps for 72 added residues, including **30 deliberate deletions**.

Current native/replay tests verify all 60 controls without an upstream installation. Earlier native test and archive snapshots are retained in the development evidence rather than relabelled or overwritten. These source-consistency and input-integrity checks do not reconstruct a paper's alignments, manual curation or downstream phylogeny.

~~~powershell
.\gradlew.bat test --tests "org.jlinalg.sequence.Mafft*" --tests "org.jlinalg.cli.MafftOriginalReplayTest"
~~~

## Prepare an exact private runtime

The [standalone installer](../../tools/literature/install_mafft.py) requires Linux x86_64 Python 3.11+, the declared existing shell/coreutils/Perl helpers, and permitted prlimit/setsid execution. On Windows, use Linux Python inside WSL. Native Windows setup returns SKIP3. No compiler, package manager, global installation or system configuration change is used.

Choose persistent private Linux paths. **Managed home and report paths must be canonical ASCII paths without whitespace or shell metacharacters.** Older original launchers have their own path handling. This restriction applies to these runtime/setup paths; the Java FASTA API retains Unicode taxon headers. Home, cache and report trees must be separate, and their parents must already exist.

The following example assumes an ASCII Linux home path and starts in the repository. Select the exact requested edition from 7.313, 7.475, 7.487, 7.490, 7.505 or 7.526. Neither a major-only edition nor an unsupported edition selects a fallback.

~~~bash
umask 077
base="$HOME/.local/share/omicsmatic/mafft-private"
mkdir -p "$base/homes" "$base/reports" "$base/cache"
python3 tools/literature/install_mafft.py \
  --profile linux-x86_64-mafft-7.526 \
  --home "$base/homes/7.526" \
  --cache "$base/cache" \
  --report "$base/reports/7.526-install-1" \
  --allow-install --allow-download
~~~

Installation and download permissions are independent. For complete existing original archives, omit --allow-download and supply --archive-dir /path/to/original-archives. Missing or forbidden prerequisites/downloads require SKIP3 and skipped dependent analysis. Existing corrupt archives and partial downloads are preserved; a corrupt archive returns FAIL2 rather than being replaced.

**Require a successful exit and report/COMPLETED.json with matching full hashes.** Review installation.json and WARNINGS.md. The completion status is **completed-runtime-setup-only** and scientific_analysis_admitted is false. A marker in the home alone is provisional. A failed finalization retains the home, raw outputs, FAILURE.json and a prominent FAILURE.md when writable; it cannot admit the runtime. Earlier warning/install files remain provisional. Preserve those files, correct the cause and use new home/report paths.

A complete home can be rechecked without either permission flag, using a new report:

~~~bash
python3 tools/literature/install_mafft.py \
  --profile linux-x86_64-mafft-7.526 \
  --home "$base/homes/7.526" \
  --cache "$base/cache" \
  --report "$base/reports/7.526-recheck-1"
~~~

The marker binds the exact home and original setup-report locations, lock, installer bytes, every original/bundled file and its private mode, declared host helper paths/bytes/modes/aliases, and kernel. Keep those locations intact. Copying a marker to another home does not create a valid installation. Private runtime files cannot be symlinks or hardlink aliases. Existing system helper hardlinks, such as Perl's version alias, are recorded explicitly.

Each successful setup runs one version probe and three complete tiny synthetic alignments: nucleotide auto, protein local-pair with a 1000-iteration maximum, and protein add/keep-length with a complete residue map. Stdout and the map must match the retained original controls; raw stderr, timings, inputs and owned-process receipts remain in the report. The last control deliberately deletes five inserted residues. These three synthetic families are reused across editions; repeated setup/reuse checks are not independent biological datasets or alignment-accuracy benchmarks.

Owned setup processes run in a clean environment with private temporary paths. The controller retains the leader PID until its process group is cleaned, checks complete output capacities, and applies explicit per-process address-space, CPU, file-size, descriptor and wall-time limits. A nonzero exit, timeout, live leftover descendant, integrity mismatch or finalization failure admits no completed setup. Successful tiny single-thread controls do not establish large-input or multithread resource behavior.

The internal Java MafftRuntime class checks these installed payloads and complete setup receipts without launching an aligner. It independently parses every health alignment and keep-length map with the native APIs. Changed programs, modes, extra empty directories, altered commands, rehashed output changes and provisional failed-finalization markers are refused. Runtime setup and these read-only checks provide payload/setup evidence. The separate MafftHost analysis gate captures and rechecks the declared launcher/helper, ELF/library, Perl/configuration and host bindings before owned user-data execution. Its complete files and probe receipts remain in each analysis bundle. These declared bindings do not establish a hermetic whole-host environment.

Installer tests cover permission/download separation, immutable caches and reports, complete metadata, owned cleanup and the exact installed editions. Focused Java tests include the portable original controls, actual six-edition setup admission and changed/rehashed evidence. Read the current validation receipts for precise platform counts; Linux runtime cases require the explicitly prepared private setup matrix. Native Windows can use the FASTA/alignment/map APIs, portable completed-bundle verification and `mafft-tools --export-runtime-setup`. Original alignment execution requires Linux Java and an admitted exact Linux runtime.

The historical [runtime/setup validation summary](MAFFT_RUNTIME_SETUP_VALIDATION.json) records the setup-only source snapshot preserved in its archive, before the typed workflow additions below. Its source hashes identify that historical snapshot, including the earlier documentation. It records 123 focused cases against those classes and the actual executable JAR: Linux Java21 passes 122 with one Windows-only abort; Windows Java21/25 each pass 102 with 21 Linux-only aborts, using a 128 MiB heap. All 26 Linux installer tests pass. The repository Python suite has 518 passes and 46 environment skips. Compilation of all Java sources/tests, Javadoc and website checks pass. All 18,938 prior JAR members are byte-identical. Those counts describe the historical setup checkpoint. The current push-validation receipt records the subsequent full repository test/build gate separately; the earlier 6,553-case gate remains historical evidence.

## Typed alignment command in development

Use a JAR built from this source snapshot. The older published 0.3.7 executable does not contain this command. Generate an explicit recipe and edit its input paths, exact profile and policies before running it under Linux Java:

~~~bash
java -jar /path/to/current-source-build.jar mafft-tools --example > recipe.json
# For an existing protein backbone, use --example-add instead.
java -jar /path/to/current-source-build.jar mafft-tools \
  --recipe recipe.json --home "$base/homes/7.526" \
  --out "$base/reports/alignment-1"
java -jar /path/to/current-source-build.jar mafft-tools \
  --verify "$base/reports/alignment-1"
~~~

The output directory must be NEW with an existing canonical ASCII Linux parent. Source FASTA paths and complete headers can contain Unicode. Recipes require explicit alphabet, symbol/header/gap policies, strategy, scoring and thread/order choices. Iterative strategies require an explicit maximum; a maximum does not assert that every iteration ran. ADD requires an aligned backbone and an explicit ungapping policy for added rows. Keep-length deliberately deletes insertions and writes every retained/deleted original residue to `residue-map.tsv`. Iterative add/keep-length and iterative addition on 7.313 are unavailable contracts; they return SKIP3 rather than silently changing settings.

Review `WARNINGS.md`, `session.json`, `output-audit.json`, `taxon-map.json`, `residue-map.tsv`, all raw process files and host-admission receipts. A provisional session or successful process alone admits no result. `--verify` checks every declared file/directory, original and aligned cells, restored header/case mappings, the complete keep-length map and every residue-ledger row. It runs no aligner and certifies no biological accuracy. SHA-256 proves byte integrity, not authenticity. Preserve incomplete output trees and retry into NEW paths after correcting the named cause.

The historical [first workflow development receipt](MAFFT_WORKFLOW_DEVELOPMENT_VALIDATION.json) records the source snapshot published at 0d59f88: its full repository build/tests, packaged native tests on three JVMs, initial portable controls and one additional actual-JAR alignment. Its source hashes refer to that earlier checkpoint, before the stronger recipe/process bindings and current matrix below. Preserve the earlier receipt rather than treating it as evidence for later source edits.

## Current six-edition workflow evidence

The [matrix validation checkpoint](MAFFT_WORKFLOW_MATRIX_VALIDATION.json) records 57 actual executable-JAR protocols: **35 completed alignments, 13 expected SKIP3 outcomes and nine expected FAIL2 outcomes**. The failures include a controlled finalization failure after a completed original fit; no valid result marker is admitted. All owned source process groups are terminal. In total, 36 original process fits completed, with 35 finalized analysis results. The 35 results reuse nine byte-distinct tiny input populations, including case/gap variants; they are not 35 independent biological datasets.

All six editions execute nucleotide auto, protein local-pair/1000 maximum and protein add/keep-length/map workflows. Their 24 alignment/map outputs match the frozen original bytes. Additional controls exercise all eight declared strategy choices, BLOSUM 30/45/62/80 with explicit gap settings, one/two threads, refinement-thread zero, source/input ordering, whole/first-field headers, unusual literal symbols, a single reference backbone, nucleotide/protein addition, expansion and intentional deletion. This is a representative matrix, not every parameter cross-product or an accuracy/performance benchmark.

Portable verification now binds the complete original recipe request to its replay, original inputs to every opaque prepared row, the recorded command/limits to the requested operation, and the declared payload bytes to the exact release lock. It checks restored headers/case, every residue/map row, actual source-case-change counts, session/result agreement and host snapshot/profile bindings. Fixed recipe JSON is capped at 2 MiB, fixed report metadata at 16 MiB and host snapshot metadata at 64 MiB before whole JSON decoding. The process ledger precedes its return receipt: both elapsed values are retained, and their order is checked instead of assuming millisecond equality.

There are 26 new portable bundle regressions. All 180 focused cases run against the actual executable JAR on three JVMs with a 128 MiB heap: Windows Java21 and Java25 each pass 159 with 21 Linux-only aborts; Linux Java21 passes 179 with one Windows-only abort. The Windows class-based run has the same 159/21 result. The current full Java suite has 6,637 cases: 6,362 passes, 275 environment skips and no failures/errors. All nine prior evidence validators and the MAFFT verifier pass. The rendered catalog passes all 299 general browser checks and five dedicated MAFFT checks, including mobile dark-mode warnings and paper-specific unknown settings. The full Python and final documentation gate counts are recorded in the catalog closure record; the earlier 0d59f88 full gate remains historical evidence.

The lossless development archive preserves all 77,687 logical files from the 57 protocols, including failed/skipped attempts and raw host/probe receipts. Content addressing reuses identical byte objects without merging observations or removing file families. Independent checks verify every object and bundle census, all 1,678 original-residue ledger cells and 37 deliberate deletions across finalized results, and the 24 original byte comparisons. Rehydrated unusual-protein and nucleotide keep-length bundles verify on Windows; the rehydrated controlled finalization failure is correctly rejected. No upstream executable distribution or biological-accuracy certificate is included.

## Integrated current-source capability

Read the [active workflow evidence record](omics-2023-2026/mafft-workflow-validation.json), [eight reviewed paper roles](omics-2023-2026/mafft-role-coverage.json) and [catalog closure record](omics-2023-2026/mafft-catalog-validation.json). The losslessly repacked 13 MB XZ/ZIP evidence retains every file from the earlier 70.6 MB container. Its 77,687 logical files and source/output checks remain intact; storage deduplication does not merge scientific observations. The original container, all failed attempts and earlier source snapshots are retained in development evidence.

The final executable changes only the generated setup README's first sentence. An exact whole-source inverse and a class-file UTF8-constant comparison prove every scientific instruction and all other 18,977 packaged members are unchanged. The final JAR passes all 180 focused cases per JVM (159 passes/21 Linux-only aborts on Windows Java21/25;179 passes/one Windows-only abort on Linux Java21), exports the corrected kit, and completes an additional 48-residue synthetic alignment. Both the retained matrix and that additional complete bundle are checked offline.

The installer, download/member lock and original setup receipts remain byte-pinned runtime/setup artifacts. Their setup-only flags describe those frozen checkpoints; they do not replace the current adapter's separate analysis admission. Setup completion never admits user-data alignment. Require the analysis command's valid COMPLETED.json, full native replay and warning review.

## Required remaining work and recovery

The complete retained workflow evidence is integrated through `mafft_workflow_validation.py`, scoped catalog metadata and prominent recovery sections in both catalog and warning pages. All 378 unrelated tool objects remain unchanged. The eight paper-specific reviews preserve all 751 unrelated reviews; the catalog has 759 reviewed usage summaries and 218 supporting paragraph pointers. P052 still has only major version 7; P101 still lacks an explicit alphabet/algorithm. Obtain effective author settings before paper-exact execution; no version, alphabet or algorithm is guessed. Manual/downstream paper steps remain separate. The earlier seven initial CLI controls and matrix checkpoint remain historical evidence.

The required public policy is **SKIP3 for an unavailable/forbidden exact runtime or unsupported capability**, and **FAIL2 with no admitted result for invalid input, integrity, process, capacity or finalization failures**. The installer and typed analysis command implement these outcomes with prominent warnings and required actions; native audit APIs report exceptions. Only a valid analysis COMPLETED.json with complete matching hashes and native replay admits an analysis result. Runtime setup-only completion cannot admit one. Preserve raw/partial files, correct the named cause and use new output paths. Native exceptions, version probes and one valid alignment row are not public analysis completion receipts.

RNA structural extensions, DASH/network-backed alignment, arbitrary score files, orientation inference, codon back-translation and full paper pipelines retain separate unfinished contracts. Cell Ranger remains excluded. The full 250-paper goal remains active, with 59 new scoped integrations (12 native and 47 upstream adapters), 62 including three pre-existing adapters, and 260 currently registered named tools still missing. Paper-wide semantic completeness review remains open.
