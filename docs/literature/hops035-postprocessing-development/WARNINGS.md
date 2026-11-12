# HOPS postprocessing warnings and required actions

**Whole HOPS/MALT qualification, biological paper reproduction, manual candidate
assessment and the250-paper implementation remain incomplete. This stage earns0
additional whole-tool catalog credit.** Use the separate development JAR and
[guide](../HOPS_POSTPROCESSING.md); the released/canonical executable is unchanged.

**SKIP3: if an exact required runtime, permitted installation or Linux execution
is unavailable, skip the dependent analysis.** Retain all diagnostics. Install
the declared public R provider and exact external1.7 reader in NEW directories
only where allowed, then rerun in a NEW output directory. Never substitute
versions or claim an analysis from installation/version probes.

**FAIL2: no result is admitted after an input, source, capacity, process, workspace
or output failure.** Read `FAILURE.json`, `WARNINGS_FAILURE.md`, and the named
process's `process.stderr`. Original processing errors are in
`post-process/process.stderr`; workspace export errors are in
`export-process/process.stderr` or a saved verification's
`reexport-process/process.stderr`. Keep the entire failed attempt.

**FAIL2 before original plotting: the output prefix must contain only ASCII
letters/digits, slash, underscore, dot, colon or hyphen.** The unchanged R script
uses an unquoted shell `mkdir`; spaces and shell punctuation are refused before
it runs. Keep the failed attempt and choose a NEW safe prefix. Paths are never
renamed. Saved verification only loads RData and does not run that shell command.

The unchanged original `default` script fails with `undefined columns selected`.
If default-only processing is required, skip this R stage and review retained
MaltExtract tables manually. Do not switch to `def_anc` merely to make a check
pass. A deliberate method change requires matching extraction and scientific
justification. The original empty reduced matrix also fails during plotting;
do not convert that failure into a validated absence result.

Literal target names currently require qualified ASCII letters/digits/spaces/
underscore/dot/hyphen because the upstream script builds an unquoted shell mkdir.
Other identities are refused without renaming. Use a separately qualified
upstream version supporting them, or skip affected original postprocessing.
Complete populations exceeding capacity are refused, never sampled/truncated.
Increase the named capacity only if resources permit, then retry a NEW directory.

Classes1..4 are source screening criteria, not read counts or authentication.
Missing values remain unassessable. R's original mixed numeric/character precision
loss, strict/nonstrict thresholds, table-name formatting and NA/NaN/Inf distinctions
are retained. Original PDFs and RData are unchanged. PDF checks cover the complete
expected population, identities and headers/trailers; arbitrary graphics are
produced by the unchanged upstream script. Arbitrary MaltExtract damage/coverage
cells and biological truth are not independently reconstructed from RMA reads.

Saved verification freshly decodes RData and checks complete scientific fields;
it does not rerun original fitting/plotting, authenticate a producer, establish
biological validity or recover unreported author settings. Process RSS checks
are sampled, not hard aggregate memory limits. Review all original R warnings.

The exact public GCC9 package retains an unused dangling compatibility symlink
with the newer locked C++ runtime. Its literal target is preserved and checked
against the pinned archive; it is not repaired. This provider qualifies R
postprocessing, not GCC9 compilation. HOPS distribution0.35 contains a launcher
reporting0.33; program hashes identify the tested script. Original Linux processing
is qualified on JDK21; Linux JDK25 execution is untested at this checkpoint.
