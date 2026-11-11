# Salmon native runtime and index development

The source-built **`salmon-tools`** integration provides typed original
index/quantification commands, complete captured replay and controlled original
fits for five exact editions. The frozen checkpoint is
[`salmon-bulk-workflow/VALIDATION.json`](salmon-bulk-workflow/VALIDATION.json);
catalog and browser closure is recorded separately in
[`omics-2023-2026/salmon-workflow-catalog-validation.json`](omics-2023-2026/salmon-workflow-catalog-validation.json).
These changes have not been released or substituted for the canonical executable.
The proofs below cover synthetic controls, not a user's biological study.

## Current-host runtime checks

Internal `SalmonRuntime.admit` supports the five exact original Linux x86_64
editions **1.0.0, 1.1.0, 1.5.2, 1.8.0 and 1.9.0**. It performs no installation
or download. First complete the explicit setup described in
[SALMON_SETUP_EVIDENCE.md](SALMON_SETUP_EVIDENCE.md).

Native admission rechecks complete payload names, bytes, types and modes; the
managed marker and setup evidence; required helper paths; ELF images, interpreters
and library bindings; declared loader configuration; complete ELF search-root
trees or absences; and host CPU/kernel/process identity. It uses clean owned
process environments and retained process-group/resource/cleanup receipts.
RSEM's unrelated Perl/provider requirements are not required for Salmon.

It then builds a fresh original index and runs two original 40-target synthetic
fits: EM with 50 bootstraps and VB with 100 bootstraps, with sequence/GC/position
bias. Every resulting table row and bootstrap value passes native checks.
`Admitted.verify` rechecks the complete host and completed health evidence before
later use. Copied evidence is not a current-host admission ticket.

All five native admission controls passed. They retain 400 health-table rows and
30,000 bootstrap values from ten health fits. A separate controlled late
finalization failure follows two completed health fits and revokes completion;
those extra 80 rows and 6,000 values remain unadmitted. Missing-runtime,
wrong-profile, capacity and cancellation controls have their expected SKIP3/FAIL2
outcomes. The 162-case actual-JAR audit matrix passes on Windows Java 21/25 and
Linux Java 21 without skips.

Earlier terminal attempts remain intact. One rejected a changing WSL resolver
file; a separate WSL inspection had refreshed that host file. A later attempt
found an inactive original ELF search directory outside the home. The native
gate now captures complete declared search-root trees and absences. No host-change
or payload-integrity waiver was introduced. During live host validation, use
Windows-side logs: starting another WSL command can refresh resolver state.

These controls reuse one small population below original burn-in thresholds.
Retain upstream fallback warnings and the unresolved separate four-target
positional-bias crashes. Runtime health is not model calibration, production-scale
evidence, biological accuracy or whole-paper reproduction.

## Complete reference-table decoder

Public Java `org.jlinalg.sequence.SalmonIndexReferences.read` consumes complete
original `ctable.bin`, `complete_ref_lens.bin` and `reflengths.bin` streams. Its
fixed admitted ABI is original Linux x86_64 little-endian Cereal. It reads every
name, reference-ID extension, original/normalized length and contig-position
entry through exact EOF, with capacities and cancellation checks. Every file
receives a complete byte/hash receipt.

**Full reference identity and contig identity differ.** An extension map projects
each contig reference ID into the complete reference population. A retained short
reference can have no contig ID (`-1`) and still remain a full reference. Original
complete length, normalized length and fitted effective length are distinct.
Do not remove retained short records, confuse them with decoys or infer biological
zero expression from index eligibility.

All 41 decoder tests pass in an actual JAR on three JVMs, alongside the prior 162
cases. The five original reference-table fixtures retain 205 full-reference rows
and 220 serialized positions; they reuse one 41-reference synthetic population.
Expected identities and lengths come from separately pinned input FASTA, not the
quantifier's output. Synthetic serialization tests additionally cover reference
extensions, UTF-8, unsigned values and both orientations.

The decoder checks starts within normalized reference bounds. It does not prove
contig ends, graph topology, the remaining index structures, raw FASTA equivalence
or original process completion. Those require the separate full-index and workflow
gates. **Stage position callbacks until the complete receipt succeeds.** A hidden
tail, truncated family or later error invalidates every earlier callback.

## Observed source boundary: short nondecoys can be omitted

**A completed original process can omit a requested nondecoy transcript.** In
three original 1.8.0 controls, `shortAtK` or `ENST3.3` remained in the full index
but was classified as a decoy by the quantification runtime and omitted from
`quant.sf`. The requested-population audits failed and remain unadmitted.

The original selective-alignment loader computes the decoy flag using
`i - numShort` **before** incrementing the count for the current short reference.
A short nondecoy immediately before the decoy boundary can therefore receive a
source decoy flag. The same classifier order is present in all five captured
source editions. The completed wider audit retains seven original index attempts
per edition (35 total) and three follow-up quantifications per edition (15 total).
All 15 requested-population audits retain the predicted omission. These repeat
controlled populations across versions; they are not independent biological
datasets. Their source-as-run tables pass separately, without requested-population
admission.

`SalmonDecoyAssignment` preserves the protected declared assignment and the exact
source-runtime assignment for every full reference. It reports every disagreement
and exposes both nondecoy populations separately. The complete original tables
match the source-as-run population and printed arithmetic; their requested
nondecoy coverage still fails. **Do not relabel an omitted transcript as a
requested decoy, fill in a zero row or present the smaller output as complete
requested coverage.**

Required action: inspect the exact affected names, original index/reference
ledger, `quant.sf`, `meta_info.json`, commands and raw logs. Explicitly review and
revise the reference/indexing request when appropriate, preserve the failed
evidence and rerun into new paths. Any source-as-run interpretation must retain
its population exclusions and separate scope. Unknown paper settings remain
unknown; source behavior does not authenticate a historical paper pipeline.

## Exact unsigned metadata

An original no-decoy index can write `first_decoy_index` as
**18446744073709551615**, beyond signed Java `long`. This field is inactive when
`num_decoys` is zero. `SalmonMetadata` retains exact numeric tokens, including
uint64 values and decimal precision, and applies typed bounds only when a field
is consumed. It never silently casts this sentinel to `-1`, zero or a rounded
double. The earlier generic JSON reader remains unchanged.

The original Pufferfish dependency ZIPs for all five editions match the hashes
declared in the original Salmon fetch scripts. Exact sources also show clipping,
ambiguous-base replacement, duplicate handling, header processing and short-
reference behavior. Source locations alone are not full protocol validation;
complete original edge observations and declared/source ledgers remain necessary.

## Captured inputs and typed index construction

`SalmonReferences` reads the complete unchanged FASTA and declared decoy file,
including every gzip member and trailer. It retains original case, versioned
identities, sequence hashes, raw header tokens and declared GENCODE splitting.
Decoys bind the raw token before GENCODE splitting. Unknown, repeated, misplaced
or malformed decoys fail before original indexing, including older editions that
can exit zero with an unmatched decoy name.

`SalmonReads` validates complete unchanged four-line FASTQ files, synchronized
paired populations and explicit identity/alphabet policies. It never converts
quality encodings, uppercases sequence, drops reads or treats read length as a
fragment-length prior. An explicit ordinal-only pairing policy remains visibly
unverified. The input helpers have 51 tests.

`SalmonIndexRecipe` and `SalmonIndexWorkflow` add an internal typed original
dense-index build. The request fixes the exact edition, k, headers, decoys,
duplicate policy, poly-A clipping choice, fixed-FASTA retention, threads and
capacities. Unsupported original options are reported as unavailable rather than
omitted: `--keepFixedFasta` is absent in 1.0.0; `--no-clip` is available in the
admitted 1.8.0/1.9.0 editions. Health capacities and user-index capacities are
separate. No arbitrary command or extra flags are accepted.

The new directory preserves the complete raw input and recipe bytes, fresh native
admission, original supervised process, complete index census, `INDEX-AUDIT.json`
and one `REFERENCE-LEDGER.jsonl` row per raw FASTA record. This includes records
not retained by the original index. Reasons for removal remain separate; a
duplicate-cluster entry alone does not prove exclusion. Every retained row keeps
original length, normalized length, contig identity and both declared/source
decoy assignments. Index-only completion does not admit a quantification.

Portable verification replays the complete captured reference/metadata ledger,
original options, process completion and full namespace/byte census. Original
Linux input/runtime paths remain text; verification opens captured members only.
Recorded producer modes remain evidence when copied to Windows, rather than being
claimed as current Windows permissions. Complete live producer integrity checks
still compare actual modes. Hashes establish integrity, not authenticity.

The current actual-JAR regression matrix passes **344 tests per JVM**, without
skips, on Windows Java 21/25 and Linux Java 21. It includes 25 complete original
input/index-table families across the five editions. The original fixture archive
has SHA-256 `0d840bf8e3ac246bdc6de6331a8664ea366374397a2c5a1a6d121b763fdf9a7e`.
Supervised typed-index controls and portable negative controls are retained under
`build/literature-review/salmon-runtime/index-workflow-development-1/`.
All five fresh original index builds passed native completion and Windows Java
21/25 portable verification. Each retains its one declared/source disagreement
as index-only evidence. Six negative controls reject late table junk, a wrong
audit, an erased ledger disagreement, a live-worker receipt, a failed-attempt
marker and an extra empty directory. The file/manifest hashes are deliberately
refreshed in the inconsistent negative copies, so semantic replay is exercised
separately from byte-integrity checks. Every negative copy is retained.

## Quantification input work in progress

`SalmonQuantRecipe` records separate paired FASTQ, single FASTQ and transcriptome
SAM/BAM roles, exact library code, optimizer, sequence/GC/position bias,
bootstrap count, explicit fragment prior and complete capacities. A stated
orientation phrase is not guessed into an original library code. Requested
declared-population admission and an explicitly selected source-runtime view are
separate policies. This request object alone does not execute a fit.

`SalmonAlignments` validates complete query-grouped transcriptome SAM/BAM and its
exact full reference dictionary. It checks physical UTF-8/framing or all gzip
members/CRCs and bounded BAM framing before HTSJDK allocates records. It preserves
the source, counts complete query groups separately from physical mate/hit rows,
and rejects incomplete mates, noncontiguous query groups, dictionary changes and
genomic spliced CIGARs. Its declared contract requires complete sequence fields
and adjacent first-then-second records for paired hits. It does not reorder or
project genomic alignments. The retained original SAM has 24,240 physical records,
8,520 query groups and 40 transcript references. A test BAM conversion is not an
additional original Salmon quantification.
The complete input/output admission matrix now passes **425 actual-JAR tests per
JVM** on Windows Java 21/25 and Linux Java 21, without skips. Its additions include
20 request tests, 17 SAM/BAM tests, 38 output-family tests and six complete input
capture/replay tests. `SalmonQuantInputs` protects the original sources, captures
unchanged complete FASTA/FASTQ/SAM/BAM and full index bundles, and opens only
captured members during portable replay. Byte and character capacities have
separate names; combined input/reference/identity capacities are checked.

`SalmonQuantAudit` checks all 25 retained original mapping/alignment families,
including 1,000 complete quantification rows and 31,600 bootstrap values. It binds
the exact original option metadata, full protected target order/lengths, input
fragment counts, original assigned counters and index links. Alignment mode uses
its separate original mapped-fragment metadata counter; mapping uses the original
`lib_format_counts.json` assigned counter. Counts are never inferred by summing
rounded table values. Original explicit `--numBootstraps 0` and omission of that
option remain distinct complete commands with zero requested replicates. The
reader alone does not establish process completion or admit a user fit.

`SalmonQuantWorkflow` connects these components to supervised original execution
and portable finalization. Its complete candidate passes 446 actual-JAR checks
per JVM on Windows Java 21/25 and Linux Java 21. The standalone command and exact
setup exporter have 16 tests; a fifth workflow boundary test additionally covers
unexpected guard errors. A strict declared-
retained request is skipped before fitting when the source index cannot supply
that population. A separately declared source-runtime policy must retain false
declared-retained coverage and the full exclusion ledger. Later failures revoke
completion and preserve the pre-failure warnings and all partial evidence.

Fresh 1.9.0 controls now pass a complete typed index, paired EM/50-bootstrap and
VB/100-bootstrap fits with all three requested bias flags, single EM/5-bootstrap,
paired VB without replicates, transcriptome SAM/EM and BAM/VB fits, and an
explicitly selected source-runtime view. One additional original alignment fit
passes the standalone public CLI. All eight complete families pass Windows Java
21/25 portable replay. Strict declared-population mismatch skips before fitting;
original insufficient-assigned-mass failure, bootstrap capacity, late cancellation
and an unexpected post-completion error retain their expected SKIP3/FAIL2 status.
Both post-completion controls revoke completion and preserve the successful raw
prefix without admitting it. The same complete workflow sequence also passes in
the four other exact editions.

The first 1.9.0 workflow attempt is retained under
`build/literature-review/salmon-runtime/quant-workflow-development-2/control-1/`.
Its completed original EM fit had 40 numerically checked rows and 2,000 complete
bootstrap values, but final replay rejected its saved decimal audit totals. The
verifier had reread `8520.000` and `1000000.000000` through a generic double reader.
The corrected verifier uses the existing separate lossless Salmon metadata
reader. Raw original inputs/results were not repaired or replaced, and that
earlier fit remains unadmitted with its completion revoked. Current controls and
their source snapshots use new paths.

All five exact editions have now passed the complete fresh workflow sequence.
Together they retain 36 admitted controlled fits, 1,275 quantification rows and
32,720 complete bootstrap values, including the additional original standalone
CLI fit. Strict mismatches and original insufficient-mass failures have the
expected outcomes in every edition. These figures exclude runtime health and all
rejected prefixes.

The source dispatcher adds only `salmon-tools` routing and one help line. An exact
inverse reconstructs every prior dispatcher byte, and the packaged comparison
changes only `JLinAlgCli.class`; every scientific class and other resource remains
byte-identical to the original-tested candidate. The packaged root command now
passes **449 tests per JVM** on Windows Java 21/25 and Linux Java 21, without
skips, plus executable help/profiles, eight complete saved-result replays per
platform and failed-prefix rejection.

The copyable [bulk example](../../examples/salmon-bulk/README.md) also passes
through the packaged root command: setup export, permitted offline installation,
typed index creation, paired VB/100-bootstrap and single EM/5-bootstrap fits,
and complete saved-result verification. Its two fits retain 80 complete rows and
4,200 bootstrap values, separately from the 36 workflow controls and runtime
health fits. Every kit/setup/index/analysis member is captured; runtime binary
homes and archive caches stay in their separately named owned Linux location.
The six frozen archives retain complete original result families, source bytes,
validation receipts and earlier failures without redistributing runtime binaries.

## Required recovery and remaining work

**SKIP3 — dependent analysis skipped:** an exact runtime, permitted helper,
resource or capability is unavailable or forbidden. Name the dependency, complete
only permitted explicit setup or skip that analysis. **FAIL2 — no result
admitted:** invalid input, changed evidence, failed original process, numerical/
capacity/cancellation or finalization failure. Preserve partials, inspect all
warnings and process receipts, correct the named cause and use new paths.

The input/output audit documents describe their own component checks; the final
`COMPLETED.json` governs admission of the selected complete population. TPM remains
relative abundance, not raw integer counts. Cell Ranger
remains excluded. Hashes prove integrity, not authenticity. No release, deployment
or whole-goal completion is claimed.
