# Original ATAC-seq 1.2.1 source-build checkpoint

The [command guide](../NFCORE_ATACSEQ121.md) provides requirements, setup,
complete-input preparation, execution, portable verification and recovery.
`nfcore-tools ATACSEQ-1.2.1` runs the unchanged upstream pipeline. Java validates
the exact source, typed request, complete input bindings, runtime/image and
selected task/output contracts. Native sample mapping matches 33 executions of
the original checker. Upstream tools perform the numerical analyses.

The qualified public yeast example uses paired-end reads, broad peaks, two
groups and two biological replicates each. It completed 88 tasks across 39
process names and captured all 356 published files (306,292,466 bytes). The
complete typed result and exported portable result passed Linux verification;
the portable result also passed Windows verification. The final public reader
accepts both saved forms. This example does not reproduce either paper's
biological experiment or qualify every supported parameter combination.

The initial focused suite passed 194 of 198 tests; four POSIX-specific tests
were skipped on Windows. The final Linux suite passed all 12 selected tests,
including those Windows-skipped controls and all six new ATAC tests. These
suites overlap. All 32 typed request controls passed. Final packaged controls
check dispatch, failed/component-only result refusal, changed manifests,
existing export targets and missing runtime/image SKIP3 without analysis.
The preparation defaults match the explicit limits of the successful run.

Two independent pinned OCI conversions produced the same SIF, and each passed
the original container namespace health check. The successful analysis kept
the adapter, managed Nextflow/Java runtime, work and prepared inputs on native
Linux storage. Earlier failed runs remain failed, including two engines that
completed all source tasks before their initial outer adapters rejected
incorrect output expectations. See `RETAINED_ATTEMPTS.json` and `WARNINGS.md`.

`controls.zip` is an ordinary ZIP with a size/SHA-256 `INDEX.json` for every
payload. Every member is read back and checked. It contains the complete
original workflow archive, native mapping fixtures/reference records, current
source and helpers, source-role census, test/command receipts, complete selected
published text tables and the complete published-output index. Its explicit
selection includes every published file with the listed table/text extensions;
no selected table is row-truncated. It also contains the failed-attempt records
and inventories/checksums for the retained full native failure archives.

**This control ZIP is not the complete portable scientific result or a runtime
distribution.** Complete captured example inputs, immutable result objects,
producer JAR, installed runtimes/images, native work trees and failure archives
remain separately retained in the development workspace. Their identities and
omissions are explicit. Use the guide's pinned intake/setup to provision source
data and runtimes. Expand the control ZIP into a NEW directory with ordinary
ZIP tools. Read `UPSTREAM_CREDITS.md` before reusing original material.

The final public JAR differs from the qualified producer only in one unused OCI
provenance resource: an expiring signed redirect query was omitted. Every
executable class and all other JAR entries are byte-identical. Original metadata
and producer remain private and unchanged; the transformation record is saved.
The final reader reverified the original complete results on both platforms.

This is local source-build availability. The released 0.4.0 JAR is unchanged.
P169 explicitly reports atacseq 1.2.1; P050's edition and both papers' engine
versions remain unreported. Neither an author engine nor a whole-paper
reproduction is inferred. Other nf-core pipeline roles and the full 250-paper
implementation goal remain open; this checkpoint adds no catalog family credit.
