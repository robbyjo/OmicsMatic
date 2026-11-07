# Native bigWig conversion workflow

The development `bigwig-tools` command converts complete local plain/gzip bedGraph
and chromosome sizes to an indexed bigWig v4 track entirely in Java. It captures
the unchanged raw inputs, records every numeric/reference disposition and verifies
the complete output before committing the run. No UCSC executable or Python
installation is needed for conversion or copied-bundle verification. The
[copyable examples](../../examples/bigwig/README.md) cover exact and explicitly
rounded signal.

**At this frozen workflow checkpoint, catalog promotion is still pending.** Its
baseline has 66 new scoped integrations and 261 missing tools; consult the
[current catalog summary](omics-2023-2026/summary.json) for a later promotion.
The canonical/released JAR is unchanged. Use the development artifact recorded in
the [workflow proof](bigwig-typed-workflow/VALIDATION.json). The complete 250-paper
implementation goal remains active.

```text
java -jar DEVELOPMENT_JAR bigwig-tools --input SIGNAL.bedGraph --sizes REFERENCE.chrom.sizes --genome-build DECLARED_BUILD --units DECLARED_UNITS --float32 exact|round --dictionary used|all --compression zlib|none --out NEW_DIRECTORY
java -jar DEVELOPMENT_JAR bigwig-tools --verify COMPLETE_BUNDLE
```

The output parent must already exist and be canonical. Paths may contain Unicode
and spaces. Required build/units are literal investigator declarations; the
converter does not guess or authenticate an assembly, run coverage/normalization,
clip intervals, sort within a chromosome, alias identities, average overlaps or
fill missing signal with zeros. Input chromosomes may appear in any order, each
in a single block with sorted, nonoverlapping, zero-based half-open intervals.
Binary dictionary/data order is unsigned UTF-8 byte order. Initial track/browser
metadata is counted and retained raw; coordinate-bearing records cannot disappear
as display metadata. A real chromosome named `track` remains valid when declared.

**Precision is explicit.** `exact` requires the original decimal to equal the
stored float32 value, including information that would already be lost in
float64 parsing. `round` permits the change and reports it prominently.
`VALUE-LEDGER.tsv` retains every original numeric/coordinate token, source line,
float64/float32 hexadecimal value, float32 bits, literal-equality checks and
underflow stage. Signed zero remains intact. Overflow/nonfinite raw data are
refused. `REFERENCE-LEDGER.tsv` retains every declared chromosome, size token,
used/omitted disposition, binary ID and input count. Explicit `used` and `all`
policies differ; an empty track requires `all` and is not measured zero signal.

Plain text and gzip use bounded strict UTF-8 decoding. Concatenated gzip members,
optional headers, payload/header CRCs, sizes and compressed EOF are checked;
trailing non-gzip bytes are refused. Complete decoded-byte limits include both
signal and reference streams. Line and record capacities are explicit. Ledger
I/O is buffered and bounded. The requested maximum output plus a 16 MiB final
metadata reserve must fit alongside all complete prepared evidence, or the
conversion fails; requested limits are never silently lowered. These limits are
not JVM heap guarantees or production-scale certification.

**Native summaries and consumer boundaries remain visible.** The track stores
complete raw data/indexes and no zoom approximations. Total summaries use stored
float32 data with double products and compensated double sums, differing from
UCSC 2.10 float arithmetic. Original finite raw data can yield nonfinite original
summaries; the prior controls retain those values and an original-header crash
in the pinned Cygwin pyBigWig reader. That reader also returned a signed view for
unsigned coordinates above 2^31, whereas UCSC independently recovered the native
unsigned coordinates. The original converter timed out on that boundary probe.
No original summary-bit, zoom-statistic, binary-layout, downstream consumer or
historical-edition equivalence is claimed. Review the
[unchanged native reference checkpoint](BIGWIG_NATIVE_DEVELOPMENT.md).

`INCOMPLETE.json` guards the entire attempt, including final verification. The
overall manifest/completion are verified while this guard is present; only then
is it removed as the last commit action. A failure during finalization retains
the guard and revokes any new overall markers into `UNADMITTED-*` files. Portable
verification checks the exact whole namespace, all input/output bytes, regenerated
complete ledgers, every raw interval/index field, audits and exact warnings. It
never opens recorded producer paths. Copied filesystem modes are not producer
permission certificates. Hashes provide integrity, not authenticity or biology.

The frozen proof records **74 actual-JAR tests per JVM** on Windows Java 21,
Windows Java 25 and Linux Java 21, with no skips. All 25 component/dispatcher
classes match fresh strict Java 17 compilation. The native writer's nine classes
and all unrelated parent entries remain unchanged. An exact inverse of the two
route/help insertions reconstructs the complete parent dispatcher bytecode.
Gradle tests and Javadoc pass.

**132 packaged public workflows** reuse all 22 independently read native datasets
in plain and gzip form on each JVM: **479,688 interval instances** across the
three platform runs. Every produced track is byte-identical to its independently
read native prototype; this links the workflow to the earlier **79,948 interval
instances across 22 reference protocols**, rather than claiming new UCSC/pyBigWig
runs or independent biology. Nine producer/consumer combinations perform **396
complete portable bundle verifications**. Six actual executable-main example
fits and six main verifications cover the two documented inputs on all JVMs.
Two actual lower-privilege Linux controls produce named input/output access
SKIP3 results. Two executable-main adversarial controls reject malformed
coordinate-bearing `track`/`browser` rows with no completed track.

The first candidate's complete matrix, the interrupted corrected matrix, and a
diagnostic malformed-row admission are retained. That diagnostic admission is
explicitly invalidated, with its original raw files and marker bytes preserved.
The corrected candidate rejects the same malformed row; a record is never
silently discarded as metadata. The interrupted matrix was not duplicated while
its process was live: Windows and WSL ownership checks confirmed it stopped
before a fresh full validation began.

**SKIP3 — CONVERSION SKIPPED:** required input/output access is forbidden. Read
the named resource and stderr/retained `WARNINGS.md` when writable; supply a
permitted resource or leave that conversion skipped. No installation is attempted.

**FAIL2 — NO RESULT ADMITTED:** input, precision, integrity, capacity, I/O,
cancellation or finalization failed. Read `FAILURE.json` and its named stage,
`request.json`, both audits and complete ledgers. Preserve the attempt/partial
track, correct the cause and use new paths. Require overall `COMPLETED.json`,
absence of `INCOMPLETE.json`/`FAILURE.json`, and successful `--verify` before use.
Cell Ranger remains excluded. Whole-paper reconstruction, unknown original
converter editions/chromosome resources, and the other missing integrations
remain open. No release, push, deployment or whole-goal completion is claimed.
