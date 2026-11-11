# fastp complete-input and original-processing development

**This milestone adds complete FASTQ input validation, exact runtime binding and
controlled original processing. The public `fastp-tools` command, full scientific
output admission and catalog integration remain unfinished.** Process exit zero
does not produce an admitted result. The stage writes
`ORIGINAL_PROCESSING_FINISHED_OUTPUT_AUDIT_PENDING` and deliberately writes no
`COMPLETED.json` for an analysis. The catalog still has 60 new scoped integrations
and 260 missing tools, including fastp.

The native per-read Java methods and private runtime installer from
[FASTP_DEVELOPMENT.md](FASTP_DEVELOPMENT.md) remain available. This document records
the next development milestone; it does not describe a new public release.

## What is implemented

`FastpFastq` streams every unwrapped four-line record through physical EOF. It
checks the complete encoded-byte, decoded-character, read, base, per-read and
header populations without retaining whole input files in Java memory. The
largest admitted record must fit a conservative fraction of the current Java
heap. A prefix cannot be reported as a complete input.

Plain and gzip inputs are detected by magic bytes. The existing strict gzip
decoder checks every concatenated member, header/trailer, CRC, size and physical
EOF; trailing bytes are rejected. Raw input bytes are copied before decoding and
preserved. Compression is not inferred from the original filename. Captured
files receive the `.fastq` or `.fastq.gz` suffix needed by the original program.
LF and CRLF are admitted; a final unterminated quality line is explicitly counted.
Malformed UTF-8, bare carriage returns, truncated/wrapped records and out-of-policy
symbols fail admission.

The caller declares Phred+33 or Phred+64, uppercase A/C/G/T/N or literal printable
ASCII, and the plus-header policy. Empty-only, matching first-token and original
ignored-identity plus policies are distinct. Ignored mismatches remain counted.
Literal noncanonical bases are retained and counted rather than silently changed.
The informational N and GC counters count uppercase characters; no broader
original GC-report equivalence is claimed by those literal counters.

Mate identity policies are explicit: exact first token, terminal `/1` and `/2`,
CASAVA first token with correctly ordered read labels, or deliberately unverified
ordinal pairing. Every pair and both EOFs are checked. Duplicate read names are
not silently replaced with generated identities. Exact-token validation does
not also claim validation of arbitrary comment metadata or biological pairing.

`FastpRuntime` binds one of the four pinned static Linux x86_64 binaries to its
complete managed-home inventory and setup report. It checks the bundled lock,
license, binary bytes, ELF shape, completed report census, exact synthetic health
inputs/outputs and basic report counts. It also binds the current kernel and the
requested/real paths, bytes and modes of the process helpers. This is a declared
binding, not a source-to-binary compilation proof or an OS security sandbox.

`FastpParameters` constructs only typed original options. It supports the
preprocessing settings needed by the reviewed roles: ordinary single/paired
files, adapter choices and paired detection, global and quality cutting,
quality/N/length/complexity filters, final length caps, poly-G/X settings, paired
overlap correction, duplication and optional unpaired outputs. These fields have
not all received complete scientific output admission. Merging, UMI, adapter
FASTA lists, index filtering, streaming/splitting and other original capabilities
remain outside this development interface.

`FastpProcess` runs in a new owned directory with explicit wall, address-space,
CPU, file-size, file-count, workspace, process, thread and sampled RSS limits.
It preserves raw stdout/stderr, commands, process identities and cleanup records.
Cancellation, orphan, nonzero exit, integrity and capacity failures cannot admit
a result. Its control tests also check that unrelated processes survive cleanup.
Sampled RSS is not a hard aggregate RSS ceiling or a whole-host memory guarantee.

`FastpOriginalStage` ties these components together. It captures all original
bytes, validates complete inputs, preserves explicit options and native statistics,
checks original/snapshot/runtime identities around execution and retains original
outputs. Each request, capture, source binding and process receipt is recorded.
It supplies no analysis-time installer and no free-form command interface.

## Resource and failure actions

**SKIP3 means a required host, exact edition, runtime/setup, execution permission
or declared memory prerequisite is unavailable. Skip the dependent analysis.**
Supply a permitted exact-profile home and setup report using the existing
installer, Linux Java inside WSL when using Windows, and adequate explicit
capacities. If execution or installation is forbidden, keep that analysis skipped.
The unreported patch in P089's v0.23 is not resolved to another edition silently.

**Accuracy level 6 requires 24 GiB for its source Bloom buffer plus overhead.**
The parameter layer reserves additional minimum overhead and refuses smaller
declared AS/RSS limits before launching the original program. It does not lower
accuracy or replace Bloom deduplication with exact-set deduplication. The tested
insufficient-capacity case retains a skip receipt and never launches fastp.
The complete high-memory P054 configuration has still not been executed.

**FAIL2 means input, capture, integrity, process or final diagnostic failure.**
Inspect the retained `FAILURE.json`, `WARNINGS.md`, input snapshots and original
process directory. Preserve all partials, correct the named cause and use a new
output directory. Input-header policies are declared admission rules, not a claim
to reproduce the permissive original malformed-input parser.

**No scientific analysis completion is available at this stage.** Original
reports and transformed populations need the pending full output audit and
portable workflow verifier. Do not infer whole-paper reproduction or biological
accuracy from the controls. Author settings, barcode positions and unspecified
versions remain unresolved. Cell Ranger remains excluded.

## Evidence and retained validation corrections

`FASTP_ORIGINAL_STAGE_DEVELOPMENT_VALIDATION.json` records the actual development
JAR and source/control archive. There are 132 retained original processing runs:
124 independently recorded filter/cutting protocols repeated through the new
captured-input stage, four paired adapter/correction fixtures and four gzip
Phred+64 fixtures. The independent post-run audit checks every original/snapshot
byte, passing/failed golden population, expected paired fragment and corrected
quality, quality-encoding conversion and 1,056 basic before/after report fields.
It covers 31,844 input and 15,072 passing-output read records. All owned processes
are terminal. One additional accuracy-6 request is an expected prelaunch skip.

The source fixtures use persistent evidence directories. Their 2,392 raw files,
capture manifest and audit summary are archived. No original scientific processing
was repeated merely to correct validation metadata.

The first manual runner expected 326 tests instead of 325. Two version-probe test
assertions also assumed stderr; the newer editions use stdout. All 133 original
stage tests passed in that run, while the aggregate run correctly retained its
nonzero status. The corrected runtime/process/portable check run has 192 tests:
all 192 pass on Linux Java 21; 175 pass on Windows Java 21 and 25, with 17 Linux
controls explicitly aborted there. The 133 retained stage checks and these 192
checks cover 325 distinct tests; this is not a claim that a fresh single 325-test
invocation passed after the correction.

A separate Python auditor initially sliced a 22-base reference adapter as 21
bases. It was corrected to derive the length from the literal sequence. Its prior
source is retained, and the corrected audit passed on the unchanged raw outputs.
These were validation-code corrections; the scientific implementation did not
need corresponding changes. Earlier native 134-test and 124-original-protocol
evidence remains unchanged and separately retained.

The remaining work is complete original-output/report admission, broader
processing controls including the requested deduplication configuration, typed
public CLI/portable verification, and application of the seven paper-role drafts
with catalog warnings and browser checks.
