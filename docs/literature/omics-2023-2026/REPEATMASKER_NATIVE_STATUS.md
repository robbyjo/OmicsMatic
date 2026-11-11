# RepeatMasker native foundation

**Current scoped public workflow is validated (2026-10-31).** Native CHECK/MASK/AUDIT and original SEARCH are available in current source for the two declared profiles. Both public runtime admissions, all 13 planned search controls, complete independent output/tree audits, mapped inputs, the large control and the copyable example pass. Read [the public workflow and required actions](REPEATMASKER_PUBLIC_WORKFLOW.md). Counts are 54 new integrations, 57 including existing adapters and 265 missing. Cell Ranger remains excluded.

## Historical component evidence

The records below retain their original candidate scopes, counts and pre-admission wording. They are historical evidence; current availability and recovery are defined above and in the public guide.

**Current source snapshot (2026-10-31): experimental public integration.** The admission CLI, typed original SEARCH path and completion verifier now compile, but their public end-to-end validation is unfinished. The validation records below describe earlier component gates. Do not treat the new SEARCH path as validated or use its output for scientific conclusions yet. SEARCH without an admitted runtime still returns SKIP3. RepeatMasker remains outside the completed tool count.

**RepeatMasker remains missing from the completed integration count.** The
native Java annotation methods and private original search foundation are
available in development, including the public native command described below.
Original-search workflow admission, complete host/provider closure, historical/species
resource admission and final paper-role/catalog gates remain open. The explicit
[private payload installer](REPEATMASKER_PAYLOAD_SETUP.md) is available.
The catalog therefore remains at 53 new validated integrations, 56 including
three existing adapters, and 266 missing.

The [runnable Java example](../../../examples/native-repeat-annotations/README.md)
needs only a JAR rebuilt from this checkout and Java 17 or later. The API is
`org.jlinalg.genetics.RepeatAnnotations`. It reads explicit OUT layouts with or
without IDs, or 16/17-column UCSC `rmsk` tables without/with bin. Bounded reading
checks complete EOF, row/line/text capacities and an estimated storage bound.
It preserves repeat names, classes, families, IDs, overlap markers, original
columns and source line numbers. It does not infer an assembly or chromosome
aliases.

Genomic intervals are normalized to zero-based half-open coordinates. OUT
consensus fields are interpreted by strand when ordinary coordinates are
available. Extended/negative OUT coordinates and UCSC signed consensus slots
remain preserved; they are not silently converted into ordinary consensus
spans. Source query-length hints do not replace an independent FASTA audit.

The Java methods compute exact interval-union coverage, ordinary consensus
coverage of an explicitly supplied compatible insertion group, strict or
inclusive rational thresholds, and soft/N/X masking. Overlaps are counted once
within each supplied population. Covered bases and characters actually changed
are separate counts. Masking preserves bases and case outside the selected
union. Callers explicitly select classes and marked-overlap rows; the API does
not invent a biological repeat classification or group unrelated IDs.

**Source-order warning:** the original `maskFile.pl` utility assumes query/start
order. An unsorted control skips an earlier interval after a later accepted
hit. Java union coverage is explicitly order independent. Keep the original
data and specify an order policy in any integrated workflow; the unsorted
control is retained rather than claimed as an upstream match. See the
[complete original comparison](../../../build/literature-review/repeatmasker-runtime/original-annotation-corpus-1/observed.json).

The [strict native gate](../../../build/literature-review/repeatmasker-runtime/native-tests-1/observed.json)
passes 14 tests on Windows Java 21/25 and Linux Java 21, including 200 random
mask/union populations in three masking modes. Twenty original parser and
forty sorted masking runs match all parsed fields and complete sequences
through 120 native JVM runs: 1,900 row comparisons per JVM and 39,060 paired
sequence bases across all profiles. The private
[current original search health](../../../build/literature-review/repeatmasker-runtime/current-scientific-health-1/observed.json)
also passes three searches with nine known repeat hits and 8,160 paired query
bases, including a complete negative-query population. These establish the
stated synthetic source contracts, not biological accuracy or paper-wide
reconstruction.

The [packaged native gate](../../../build/literature-review/repeatmasker-runtime/packaged-native-gate-1/observed.json)
passes the same 14 tests on each JVM against the exact private JAR, with no
production-class shadowing, three runs of the copyable Java example and nine
complete original-search replays covering 24,480 paired query bases. The
[validation record](repeatmasker-native-validation.json) identifies that JAR
and keeps the integration count unchanged.

**Current search scope:** those original searches use RepeatMasker 4.2.6 with
a controlled custom interspersed-repeat library and `-s -norna -nolow`.
They do not admit species/Dfam searches or establish simple-repeat/TRF health.
The historical 4.1.5 runtime is now configured in a separate private copy
with bundled Dfam 3.7. All 19,768 nucleotide and 18,011 protein database titles
and complete sequences match their source FASTA, including preserved trailing
spaces in 159 protein titles. The immutable original distribution is unchanged.
The [historical scientific gate](../../../build/literature-review/repeatmasker-runtime/historical-scientific-health-1/observed.json)
passes eight original custom-library, mouse-species and simple-only searches,
including N/SOFT/X masks, a repeated species search and complete negative queries.
Every parsed field and every complete masked sequence matches the exact-JAR
Java audit. Generated caches remain explicit private runtime resources. The
custom-library path also creates an unversioned `Libraries/general` cache;
admission must verify its complete contents and resource identity.

**Historical scope warning:** the paper does not identify the exact Dfam
release or every search dependency edition. The fixtures use Dfam 3.7,
RMBlast 2.17.1 and the checksum-pinned TRF release asset; they do not establish
the paper's exact runtime/library reconstruction. Simple-only health uses
explicit `-noint`; that flag was not reported by the paper. Public scientific runtime
admission and the typed original-search CLI are unfinished. Eleven
source-checked paper-role drafts remain unapplied to the catalog.

The internal streaming FASTA reader and complete masking auditor now pass
38 focused tests per JVM (24 new tests plus the earlier 14), with the same
tests passing against the exact private JAR. Plain UTF-8 and gzip are explicit
choices. Gzip member headers, optional header checksums, CRC32/size trailers,
concatenated members and physical EOF are checked; trailing garbage fails.
No prefix is admitted when a source/decoded/line/sequence/storage limit is
exceeded. Exact identifiers, full headers, sequence case and no-hit records
are retained, with no inferred alias or base normalization.

The [packaged streaming gate](../../../build/literature-review/repeatmasker-runtime/streaming-packaged-gate-1/observed.json)
replays all eleven original-search outputs in plain and gzip form on all three
JVMs: 66 complete replays and 233,580 paired query bases. Results match the
previous independent native masks and original outputs. A separate 64 MiB
sequence passes on each JVM with a 32 MiB Java heap; this is a computational
streaming control, not a new original search or a biological accuracy claim.

**Streaming scope:** these are internal workflow components. Protected input
staging and the public native CLI are described below; original runtime
admission and the installer remain open.
The audit retains three bounded FASTA censuses, a separately bounded interval
index and caller-owned annotations; configure the combined memory budget.
Every annotation must join an exact FASTA query with valid coordinates and a
consistent length hint, including rows excluded by class/name/overlap filters.
Original source and full masked output populations must match through EOF.

The [typed recipe and protected preparation contract](REPEATMASKER_RECIPES.md)
now passes 62 focused tests per JVM, including the previous 38, and the same
tests against the exact private JAR. The requests distinguish native annotation
reuse from exact upstream profiles. Original files and prepared members are
hashed, source snapshots are retained, unexpected changes fail, and existing
outputs are never overwritten. Workspace capacity reserves space for prominent
failure diagnostics. Native input excess is never admitted as a truncated result.

The [packaged preparation/original gate](../../../build/literature-review/repeatmasker-runtime/preparation-packaged-original-3/observed.json)
passes 18 protected search preparations, six fresh original searches and 102
complete native audit preparations over 331,500 query bases. Explicit ASCII
ordinal staging preserves the original Unicode filenames, full headers, base
case and query mappings while giving original programs declared uppercase
inputs. These fixtures include names and headers that original FastaDB would
otherwise reinterpret. Plain and gzip native imports agree on complete fields
and masks. An additional gate matches 108 complete fresh original row fields
and refuses six actual Linux link boundaries. Runtime/provider hashes are
unchanged before and after the searches.

**Preparation scope:** this implements internal recipes and staging. Public
runtime admission, the installer and original-search CLI remain unfinished. The process
primitive described below now enforces requested recipe budgets in controlled
original searches; public runtime/resource binding remains open. Unsupported historical profiles remain unavailable and no
paper's unreported exact library/dependency edition is inferred. The first
private fixture's declared line limit and a private Windows stdout encoding
issue were corrected with their failed attempts retained; production snapshots
and UTF-8 preparation ledgers preserved the original data throughout.

The internal Linux process supervisor now passes 74 focused tests on each of
the three JVMs, including 12 new tests, with the same 74 tests against the exact
private JAR. Eighteen [controlled process protocols](../../../build/literature-review/repeatmasker-runtime/owned-process-corpus-1/observed.json)
exercise hard per-process address-space, CPU, file-size and descriptor limits;
wall time, workspace, member, process and thread monitoring; sampled aggregate
RSS; cancellation; integrity changes; and JVM SIGTERM cleanup. Process identity
includes PID start time. A surviving owned worker after a zero exit fails, and
an unowned sentinel remains alive. RSS monitoring is sampled and does not
provide a hard aggregate memory bound. Prepared and run files share the
declared logical-byte and member budgets. Canonical ledgers reject non-finite
numbers and ambiguous key types.

The [exact-JAR supervised original gate](../../../build/literature-review/repeatmasker-runtime/process-packaged-original-1/observed.json)
runs eight original searches across 4.2.6 and 4.1.5, using the requested typed
recipe limits, protected preparations and complete runtime/provider hash fences.
Six searches pass complete native mask audits over 16,320 query bases. These
development bindings do not implement public runtime/resource/cache admission,
and process success does not create a scientific completion marker.

**NO-HIT OUTPUT WARNING:** both editions' all-negative search writes a no-hit
`.out` message but produces no `.masked` FASTA or `.tbl`. The original
ProcessRepeats zero-annotation branch and exact artifact absence are
[retained explicitly](../../../build/literature-review/repeatmasker-runtime/process-progress-1/original-no-hit-artifact-observation.json).
The two cases remain outside scientific result admission. Retain the original
files and logs; do not certify a search from exit zero or fabricate an upstream
masked output. The public result contract must handle this case explicitly
before it can admit such a result. Native unchanged-mask derivations now
carry separate provenance, as described below; they do not certify an original
no-hit search.

**Process scope and recovery:** this original-process supervisor remains
internal; the public native command does not invoke it or install a runtime. Missing or forbidden Linux execution helpers are
unavailable dependencies. Execution, integrity, capacity and cancellation
failures retain `WARNINGS.md`, process ledgers, stdout/stderr and applicable
shutdown diagnostics. Correct the named cause and rerun in a new directory.
No failed or process-only run is a scientific result. The 18 stress protocols
use source-compiled production classes; the separate packaged gate tests the
exact private JAR with all 74 unit tests and eight original searches.

The internal native `MASK` executor now writes a complete `native-masked.fa`,
per-query `native-coverage.tsv`, `native-mask.json` and prominent `WARNINGS.md`.
It applies the explicitly selected interval union, preserves exact headers and
case outside the mask, rereads the entire generated output and verifies input
and output hashes before returning a result. Coverage and changed characters
are separate counts. Zero rows and zero selected rows preserve every query.
The ledger explicitly certifies neither an original search nor supplied
annotation completeness or biological absence. Native file names and provenance
distinguish these outputs from files produced by original RepeatMasker.

The [strict native execution gate](../../../build/literature-review/repeatmasker-runtime/native-mask-tests-2/observed.json)
passes 90 tests per JVM, including 16 new tests. The
[exact-JAR gate](../../../build/literature-review/repeatmasker-runtime/native-mask-packaged-2/observed.json)
passes the same tests and 24 complete native execution replays across all three
JVMs. Eighteen outputs match retained original positive-search masked sequences
over 48,960 paired query bases. Six native unchanged-mask derivations cover
7,200 bases from retained all-negative annotations; the absent original masks
remain absent, and those searches remain outside original-result admission.
The writer also processes a single 64 MiB sequence on each JVM with a 32 MiB
Java heap. That large-record check exercises the streaming writer and complete
comparison, not the full protected preparation workflow or biological accuracy.

**Native execution recovery:** preparations and native result files share the
declared logical-byte and member capacities. The executor reserves 64 KiB and
one member for `NATIVE_MASK_FAILED.txt`. Capacity, integrity and cancellation
failures retain partial evidence and return no result. Read the named cause,
correct inputs or reviewed capacities, and rerun to a new output directory.
Existing outputs and protected inputs are never overwritten. The first
packaged replay exposed non-string JSON hash-map keys; those keys are now
explicit path strings, a serialization regression check passes, and the failed
attempt, source and JAR remain retained. Public native CLI wiring is now
complete; original runtime/resource/cache admission remains unfinished. RepeatMasker is still
excluded from the completed integration count.

The public development command `repeat-tools` now runs native `CHECK`, `MASK`
and `AUDIT`. The [copyable workflow](../../../examples/repeat-native-workflow/README.md)
contains synthetic FASTA/OUT inputs and all three recipes. Build the JAR from
this checkout; older released JARs do not acquire this command automatically.
Native operations require Java 17 or later and no upstream installation.
Original `SEARCH` requests remain `SKIP3` with retained inputs and explicit
recovery guidance; this command does not yet admit or install original runtimes.

**Completion and capacity:** each native operation has separate completion
flags and scope. Original-search completion, external annotation completeness
and biological absence always remain uncertified. Require `COMPLETED.json`,
matching manifest/file hashes and `repeat-tools --verify DIRECTORY`. Verification
is read-only and checks integrity, not authenticity. Complete bundle budgets
include root metadata, protected preparation and native results. Component
budgets subtract existing root usage and reserve 256 KiB/eight members for
finalization and diagnostics. Failure or interruption retains warnings and
partials; correct the named cause and rerun to a new output directory.

The [packaged public CLI gate](../../../build/literature-review/repeatmasker-runtime/native-cli-packaged-2/observed.json)
passes all 111 focused tests per JVM and 45 complete public native bundles.
Nine runs execute the copyable examples. Twenty-four replay retained original
annotations, including 18 original-mask matches over 48,960 paired bases and
six separately identified native no-hit derivations over 7,200 bases. Nine
additional runs cover OUT without IDs and both UCSC layouts. Three Unicode
gzip/options runs preserve source headers, paths and case. Six unavailable
search/edition requests retain `SKIP3`; three bad gzip requests retain `FAIL2`;
three deliberately changed bundles fail read-only verification.

The [actual Linux boundary gate](../../../build/literature-review/repeatmasker-runtime/native-cli-linux-boundaries-2/observed.json)
completes the full public 64 MiB native workflow with a 32 MiB Java heap,
including snapshots, masking, coverage, ledgers and verification. A separate JVM
terminated during output retains `INTERRUPTED.md` and fails verification. Three
real filesystem-link boundaries are refused. The first packaged Windows
Unicode verification exposed launcher argument conversion; analysis bytes
were intact. `--verify-options-file` now reads an immutable UTF-8 object with
one `directory` string, and all three JVMs pass that executable path. The
failed attempt, source and private JAR remain retained.

The current payload, resource, fingerprint and setup-export components pass
137 focused tests per JVM and the same tests against the exact private JAR.
The standalone Python installer passes 21 tests on each Windows/Linux host.
Three public setup exports are byte-identical. Actual offline installation,
a real pinned TRF download, independent Java payload checks and explicit helper
refusals are retained in the validation record. Read the
[copyable setup guide](REPEATMASKER_PAYLOAD_SETUP.md) before permitted setup.

The sealed payload retains every original source/configuration byte. Historical
`ProcessRepeats` reads `LIBDIR` at compile time, before its environment override.
The fresh owned working configuration therefore receives exactly that value;
the source/configured hashes and byte-exact inverse are recorded in
`resources.json`. Algorithm files remain unchanged. The earlier parent exit0,
postprocessor failure and outer harness deadline are retained as a failed run;
exec session77911 is terminal and must not be treated as live.

Fresh completed controls are frozen in the
[completed-control receipt](../../../build/literature-review/repeatmasker-runtime/historical-fix-setup-progress-1/completed-original-controls.json):
5 searches, 14 independently parsed annotation rows and 15740
paired query bases, including the repaired historical custom-library case and
current N/SOFT/X masks. Only completed cases receive credit. The remaining
13-case pipeline state is recorded in the
[execution locator](../../../build/literature-review/repeatmasker-runtime/historical-fix-setup-progress-1/execution-locator.json).
A frozen locator is not live-process evidence; inspect the same session or
exact actual processes before deciding whether work is still running.

The historical HDF5 resource is a regular hardlink or bounded streaming copy;
its full logical size counts against capacity. Each before/after integrity pass
hashes complete bytes afresh. Within one pass, physical hardlink aliases can
share a hash only while file identity, size, modification/creation and Unix
change time remain stable. Without change time, bytes are always reread.
New caches remain inside owned Libraries and receive complete fingerprints.

Historical packages come from isolated pinned official wheels; the bootstrap
uses `-I -S -B` and the explicitly pinned new CPython build. Existing global
Python environments remain unchanged. The installer retains source, metadata
and license files, and makes no OS-package changes. Its success status remains
`PAYLOAD_INSTALLED_SEARCH_NOT_ADMITTED`.

The internal original-stage runner and raw-census reader now pass 160 focused
tests per JVM and 160 tests against the exact latest private JAR on Windows
Java21/25 and Linux Java21. The full two-stage original gate runs 14 controls:
28 separately supervised original processes, 12 complete original mask matches,
29 independently parsed annotation rows and 10,041,651 paired query bases.
It covers both editions, historical species/simple-only and all mask modes,
repeated species processing and both genuine no-hit mask/table absences.

A retained large-query attempt exposed a3071-byte upstream batch-boundary
line being checked against a1024-character annotation-row limit. The corrected
reader streams irrelevant alignment/batch-boundary lines without retaining
them, while independently limiting retained census metadata. Every byte and
UTF8 code unit still reaches complete EOF/CRC validation. No source row or raw
file is truncated or discarded. The final large32MiB control passes with the
original limits.

The latest JAR adds a required raw-census check before postprocessing. Forty-two
complete original raw-result replays and six real rejection replays pass on
32MiB heaps across all three JVMs. Five fresh integrated original controls
cover current/historical positives, both no-hit cases and the actual upstream
compression boundary. The latter processes more than10million query bases
with a32MiB Java heap, preserving every original masked base. The other14
original controls identify their earlier exact candidate explicitly; their
source/configuration and output evidence remain retained.

Raw headers must match the protected complete query population, original
edition, speed and RMBlast version. The reader reaches every line, every gzip
member/CRC/trailer and physical EOF, including historical headers appended
after all alignments. It preserves the library display label after the single
upstream separator; the label does not establish resource identity. Empty,
missing, duplicate, malformed, changed-census or truncated raw data fail before
postprocessing and cannot become an admitted no-hit result.

Both original stages have independent exit/cleanup records. A real controlled
original postprocessor failure is caught by the same production process
supervisor, with original stderr and recovery retained in the workspace.
Complete engine/output inventories are fenced before and after native audits;
changed, extra, missing or linked files and extra empty directories are
rejected. Protected copies and full logical library bytes count against the
shared workspace/member capacities. Per-process time/memory/file limits apply
to each stage; hashing and native audits are outside watched child time.

Validation uses separate private payloads/workspaces on the permitted native
Linux filesystem. Complete original scientific outputs, metadata, raw files,
configured-source provenance and the controlled failure logs are mirrored into
the project evidence bundle. The temporary runtime/source/cache directories
are development locations; the pinned original archives and installation
receipts remain in the workspace for reconstruction. Their presence never
substitutes for current host/dependency admission.

A read-only host intake captured4686 bounded module-tree traversal entries,
83,776,096 logical bytes and29 external library bindings, including declared
Perl include roots, Python stdlib/provider files and loader configuration.
This is intake evidence; production host capture and independent bounded
admission replay remain required.

The eleven source-role drafts were rechecked against their pinned original
XML. They remain unapplied: the candidate would add10 reviews and explicitly
revise one earlier role, yielding703 reviews/203 support pointers. The current
catalog still has693 reviewed uses/202 support pointers,250 papers,379 names
and2,428 observations. Three annotation-reuse roles remain distinct from eight
search/masking roles. Wider paper semantics and unsupported historical or
restricted resource editions remain explicit.

The newer [host/provider development status](REPEATMASKER_HOST_STATUS.md)
records complete declared inventories, second fully re-derived captures and
independent byte/permission/membership and ELF checks for both profiles.
The current inventory covers 3,227 member occurrences and 379 ELF images;
the historical inventory covers 4,702 member occurrences and 485 ELF images.
The 278-test family passes against the exact latest private JAR on Windows
Java21/25 and Linux Java21, with explicit platform-only skips. The process
supervisor also retains a fresh original replay of the fixed procfs exit race.
The [fixed original health component](REPEATMASKER_HEALTH_STATUS.md) also
passes all13 fresh controls:26 original processes,26 independently checked
annotation rows and two genuine no-hit mask/table absences. Five fresh access
traces close all selected paths. These are internal components. The public admission/SEARCH and catalog gates
remain unfinished.

The [internal original runtime API](REPEATMASKER_RUNTIME_STATUS.md) now binds
the verified payload,host and fixed scientific controls. Both profiles pass
fresh admission and separate-JVM reopening;278 focused tests and independent
complete Linux-tree/original-output checks pass. Public admission/SEARCH and
completed-original verification remain unfinished;the tool count is unchanged.

**Remaining scope:** public runtime-admission integration, the original
`SEARCH` workflow and final paper-role/catalog gates are unfinished. Original
SEARCH still returns `SKIP3`. RepeatMasker remains outside the completed count.
Do not infer biological absence or original-search completion from a native
mask, prepared payload, parent exit0 or partial health receipt.

**Required action:** retain source files and explicit build/library/coordinate
choices. A structural `Table` read alone does not certify that an original
analysis completed or establish biological absence. The native methods need
no RepeatMasker runtime. For original searches, provide the requested exact
runtime and permitted local resources; unavailable/forbidden dependencies or
capabilities must skip the dependent analysis with a prominent cause and
recovery. Invalid data, mutation, execution or capacity must fail with retained
diagnostics. Never substitute the current 4.2.6 fixture for historical 4.0.9,
4.1.1, 4.1.2 or 4.1.2-p1 analyses. RepBase-dependent work requires a local
appropriately licensed resource; no restricted library was obtained for the
synthetic tests. Cell Ranger remains excluded.
