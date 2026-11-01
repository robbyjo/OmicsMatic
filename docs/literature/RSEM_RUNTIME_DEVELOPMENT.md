# RSEM original runtime and source-build development

Five pinned original-source builds and their staged wrappers pass fixed reference/fitting controls on the declared Linux x86_64 host. **The public RSEM fitting workflow remains unfinished. RSEM stays missing in the toolbox inventory.** Native arithmetic and ordinary reference/count/model/result/alignment audits remain separately available; reading a result does not run a fit.

The current [source-build validation record](RSEM_SOURCE_SETUP_VALIDATION.json) pins the candidate, source, tests and [metadata/synthetic-result bundle](omics-2023-2026/rsem-source-build-controls.zip). The earlier [runtime record](RSEM_RUNTIME_VALIDATION.json) and its complete 57-file source snapshot are retained as historical evidence. The new record does not apply old results to changed source without fresh controls.

## Exact source and build provenance

`RsemBuild` verifies the exact original archive and commit for 1.2.28, 1.3.0, 1.3.1, 1.3.2 or 1.3.3. Extraction refuses unsafe paths, links, special members and precompiled ELF input. It preserves regular-file modes and original archive modification times: preserving those times retains the distributed dependency metadata.

Setup captures the complete declared compiler prefix, including transitive filesystem aliases, headers, library search locations and missing states. It discovers actual compiler frontends, assembler, linker, LTO helpers and plugin; captures their declared host/ELF dependencies; and checks the complete compiler inputs before and after compilation. The build receipt retains actual commands, zero exits, terminal owned process groups, source graphs and evidence hashes. Only the three explicit legacy generated configuration files may differ from original source bytes; before-build and final changes are both recorded. Newer profiles retain all original source bytes.

The initially reported missing `liblto_plugin.so` was a discovery-code error on this host: the plugin was already installed. GCC's [program-search query](https://gcc.gnu.org/onlinedocs/gcc/Developer-Options.html) finds this toolchain's executable-prefix plugin. The earlier failed query is retained. GNU make 4.4.1 uses [the explicit anonymous-pipe jobserver option](https://www.gnu.org/software/make/manual/make.html), preserving the refusal of filesystem FIFOs.

The configure script deliberately probes symlinks, including a temporary self-reference. RSEM also invokes configuration within its normal make recipe. Only the explicitly declared canonical samtools source subtrees permit opaque relative ASCII basename symlink probes during configure/make. Each link node and its target-name bytes count toward resource limits; its target is never followed. Absolute/traversing targets, links elsewhere, FIFOs, sockets and other special files still fail. Normal RSEM execution remains strict, and final source-build inventory still requires regular files only. The original source and configure script are unchanged.

Earlier attempts completed three source profiles and reverified their observed build receipts/source graphs for partial runtime batches. Those failures and successful substeps remain retained. The final batch freshly builds all five profiles and runs their controls with the current candidate, inside one Linux session and precreated parent namespaces.

## Payload and host binding

`RsemPayload.stageBuilt` consumes a verified live local build context and binds its receipt to every copied program. The independent bundle verifier links all 14 staged files to their complete compiled-source graph. Source/build integrity is not a signature or OS sandbox. A JSON record alone is not a public admission ticket.

Original 1.3.2 and 1.3.3 report `RSEM v1.3.1`, as does 1.3.1. Exact archive/commit/script identities select the release; the version diagnostic alone cannot select a runtime. The source fix for no-alignable reads is not substituted for older versions.

`RsemHost` retains declared Perl module populations, helpers, configuration/absence states, platform identity, ELF interpreters and dependencies, RPATH/RUNPATH searches, SONAME candidate states and accepted `ldd` bindings. All declared file states and copied payloads are rechecked before and after execution. Earlier-library shadows and altered payload/build evidence refuse before launch. Unsupported provider layouts require explicit review.

For these controls, all profile parent directories were created before host capture and the batch ran inside one Linux session. An earlier parallel top-level directory creation changed `/opt` during a census. Separate WSL inspection entries also changed resolver-symlink metadata while its target bytes stayed identical; that observation is retained. [WSL automatically generates resolver configuration](https://learn.microsoft.com/windows/wsl/wsl-config). The successful batch avoided new WSL entries during capture. No resolver/system configuration was changed and no dependency check was suppressed.

## Execution, warnings and required actions

`RsemProcess` retains actual arguments, stdout/stderr, exits, process identities, cleanup state and warnings. Hard per-process address-space, CPU, file-size, open-file and zero-core limits combine with wall/workspace/member/process/thread checks and sampled RSS. Sampled RSS is not a hard aggregate memory limit. Whole-operation deadlines include source/provider checks.

**If required installation or execution is unavailable or forbidden, SKIP dependent fitting.** Name the missing compiler/runtime/provider and preserve the failure report. Supply a permitted exact source archive and declared working compiler, headers and library roots, or wait for a supported environment. Correct integrity, host-change or resource errors and retry in NEW paths. Never substitute a provider or release, discard the failed evidence, or admit output from a failed process. Current source setup is internal; there is no public fitting command to recommend yet.

## Current validation and remaining implementation

The exact candidate passes 171 focused tests on Linux Java 21 with zero skips. Windows Java 21/25 each pass 154 with 17 explicit Linux-only skips. All 111 existing native science tests pass on each profile. Strict Java 17 compilation and private Javadoc pass. Five new controls cover configuration-probe scope, opaque self/dangling links, escape/traversal refusal, FIFO refusal and strict ordinary execution. Extraction also checks preservation of distributed timestamps.

Five observed exact source builds and fresh staged-wrapper controls execute 35 principal original commands. The 22 completed fits match every one of 364 scientific files and 30 reference files; three historical zero-alignment failures remain failures. Paired-end, reverse-stranded, single-end and posterior/credibility/read-start settings are covered. Six dependency/build-evidence controls per release pass, and all positive contexts are reverified. The independent bundle check validates complete original populations, source receipt links, compiler graph hashes and staged-file/source-graph hashes. Compiled programs and source archives remain private; complete metadata and synthetic results are retained in the bundle.

Complete protected scientific reference/alignment/input admission, typed fitting requests and output/completion verification. Then provide permitted public setup, CLI and runnable fitting examples and validate those public workflows against the originals. Genome/GTF preparation, aligner invocation, BAM generation, allele-specific and pRSEM workflows require their own complete controls. The broader 250-paper implementation and semantic review remains active.
