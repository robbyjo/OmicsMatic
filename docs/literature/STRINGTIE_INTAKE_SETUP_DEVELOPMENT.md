# StringTie development: complete BAM intake and exact runtime setup

**The public StringTie fitting workflow is still unfinished.** This checkpoint
adds complete native BAM intake and a usable Java exporter for a standalone
installer. It does not yet expose typed assembly/estimation/merge fits or admit a
scientific runtime. StringTie stays missing: catalog totals remain 67 new scoped
integrations (70 including three existing adapters), 260 missing tools and 819
reviewed uses from 250 papers. Cell Ranger remains excluded.

The exact development artifact is recorded in
`stringtie-intake-setup-development/VALIDATION.json`. The canonical/released JAR
is unchanged. No changes in this checkpoint are released, pushed or deployed.
The earlier [GTF](STRINGTIE_DEVELOPMENT.md),
[table](STRINGTIE_TABLES_DEVELOPMENT.md) and
[normalization](STRINGTIE_NORMALIZATION_DEVELOPMENT.md) sources remain unchanged.

## Warnings and required actions

**Installer success means exact payload setup only.** It verifies the original
executable and edition-specific license bytes. It does not execute StringTie,
check the current ELF/library closure, admit a scientific runtime, run a user fit
or certify biological accuracy. Keep those statuses separate. Do not use an
installer `COMPLETED.json` as a scientific-result completion marker.

**SKIP3 means skip dependent setup/analysis while a requirement is unavailable.**
The installer names the stage and missing/forbidden installation, download,
access, platform or filesystem capability. Read `WARNINGS.md`, `FAILURE.json`
when present, and console stderr if the report directory could not be written.
Provide the exact requirement only in a permitted environment, or leave the
dependent analysis skipped. Use Linux x86_64 Python, including Linux Python inside
WSL on Windows, and private directories on a native Linux filesystem. Do not
silently choose another StringTie edition.

**FAIL2 means no setup or analysis is admitted.** Corrupt hashes, invalid
profiles, unsafe archives, conflicting paths, unmanaged/mutated homes and
finalization failures retain explicit diagnostics. Preserve every partial home,
staging directory, archive and report; correct the named cause and retry into NEW
home/report paths. Existing directories are not overwritten. A complete managed
home may be rechecked into a NEW report without install/download permission.
Require exit0, `COMPLETED.json`, its exact manifest/files, and no `INCOMPLETE.json`.

**BAM intake preserves raw data; it does not select StringTie-usable reads.**
Secondary, supplementary, duplicate, QC-fail, paired and unmapped flags remain
visible. Every original record and raw input byte remains protected. Mate
reciprocity, read eligibility, gene abundance inference and biological accuracy
are not certified. Empty input is explicit and is not a finding of biological
absence. Chromosome names/lengths are checked exactly; build authentication,
aliases, sorting, clipping and filtering are not inferred.

**NH and strand choices must be explicit.** The inspected original sources map
missing or zero `NH` to one. Default native intake rejects that ambiguity;
`ORIGINAL_ONE` retains the reported missing/zero state and records each fallback
instead of relabeling it as an observed unique alignment. Invalid NH types and
negative/out-of-domain multiplicities reject intake. Missing/unknown spliced
`XS` rejects by default; `KEEP_UNRESOLVED` retains it explicitly. Alignment
orientation and `ts` never silently fill `XS`. Non-character `XS` values, such as
alignment scores, are distinct from A-type RNA strand evidence. Exact
edition-specific interpretation of `ts` belongs to runtime fitting admission.

**Incomplete/corrupt BAM or exhausted capacities admits no intake result.** Read
the named framing, record, dictionary, tag, ordering or capacity error. Supply
complete authentic inputs or explicit supported policies, preserve partial
ledgers, and retry into NEW output paths. This transport currently requires
ordinary complete BGZF BAM with one canonical terminal EOF. SAM, CRAM,
nonterminal empty BGZF blocks and `CG` long-CIGAR transport require separate
admission. The helper does not convert them automatically. Complete byte/record
limits are not JVM heap guarantees.

The prior literal P171 `-rf` failure, distinct documented `--rf` controls,
edition-dependent values, unknown paper settings and source normalization
uncertainty remain retained. Hashes establish integrity, not authenticity or
biological correctness.

## Export a standalone setup kit

From this checkout's root, with Java available, use the exact development JAR:

```text
java -cp build/literature-review/stringtie-runtime/native-intake-setup-matrix-1/omicsmatic-0.3.7-stringtie-intake-setup-development.jar org.jlinalg.cli.StringTieSetupExport --out build/stringtie-kit-NEW
```

Choose a NEW directory with an existing canonical parent and no whitespace in
its path. This command copies the pinned installer, seven licenses, exact runtime
lock and synthetic health inputs. It performs no download, installation or fit.
Read `RUN-SETUP.txt`; require `export.json`, all 14 recorded file hashes, and no
`INCOMPLETE.txt` or `FAILURE.txt`. Older released executables lack this exporter.

The kit is standalone. Copy it to a suitable computer if needed. On permitted
Linux x86_64 Python, run **from the exported kit directory**:

```bash
python3 -I -B ./install_stringtie.py \
  --profile linux-x86_64-stringtie-3.0.3 \
  --home /var/tmp/stringtie-home-NEW \
  --report /var/tmp/stringtie-setup-NEW \
  --cache /var/tmp/stringtie-cache \
  --allow-install --allow-download
```

Select the exact profile needed: 1.3.0, 1.3.6, 2.1.2, 2.1.7, 2.2.1, 3.0.0 or
3.0.3. These are captured editions, not an automatic recommendation to replace
paper settings. Installation and network acquisition have independent permission
flags. Use them only where permitted. With the exact two official archives
already available, add `--archive-dir /absolute/archive/directory` and omit
`--allow-download`. The directory must contain both profile archive filenames
listed in `stringtie-runtime-lock.json`.

The installer verifies complete archive hashes before reading bounded safe
members. It copies only `bin/stringtie` and `LICENSE-StringTie.txt`, and commits a
managed marker last. Private home/bin directories have mode0700; the original
executable and license retain their declared modes. No system paths are changed.
Historical archives without publisher digests remain identified as such; a
captured download SHA256 is not a publisher signature.

For an unchanged managed home, use that home with a NEW report and omit both
permission flags. Every managed file, mode, namespace and marker must match the
exact profile. Corrupt/unmanaged homes are preserved rather than repaired in
place. Read all setup warnings after success. Native current-host health and the
typed project fitting workflow remain separate development gates.

## Complete native BAM contract

`StringTieBam` first checks physical BGZF/BAM framing with bounded allocations:
every block-size field, deflate end, expanded size, CRC32, canonical EOF, header,
reference dictionary, record body and complete auxiliary-tag population. Duplicate
tags, invalid arrays/text/hex and oversized fields reject before lazy HTSJDK
decoding can accept a prefix or allocate beyond those declared limits.

Strict HTSJDK decoding then visits every record. Every reference name and length
matches the supplied complete dictionary; binary and supplied text dictionaries
cannot disagree. The BAM's reference order remains visible and controls record
sorting. `REQUIRE_COORDINATE` requires the header declaration;
`VERIFY_RECORDS` explicitly permits an unspecified/different declaration while
still checking every record's actual coordinate order. Neither policy sorts or
rewrites the input. Unplaced records must remain at the tail.

Every mapped record needs complete sequence and bounded original CIGAR geometry.
Read consumption, reference span, M/=/X bases, deletions and N-splice intervals are
checked without filling introns as aligned signal. Inclusive junction boundaries
and every original flag/mate coordinate/template length remain in the row census.
M operations can contain mismatches; the counter is named
`CIGAR_M_EQ_X_reference_bases`, not sequence-confirmed nucleotide matches.
No secondary/duplicate/QC or strand filter is performed. Paired records need not
be adjacent, and this intake does not certify their reciprocal mate population.

Complete encoded/decoded bytes, record count, metadata, record size, reference
count, read length, CIGAR operations, tag count and array items have explicit
capacities. Source identity is checked before intake, after the full census and
on later verification. Cancellation and ledger-write errors propagate without
returning an admitted report. The typed fitting workflow will capture private
unchanged inputs and own its overall completion/partial-result guards.

## Evidence and next implementation

The Java suite has 1,307 checks: 47 new BAM controls, four setup-export checks and
the unchanged 1,256 prior checks. The protected original synthetic BAM contains
840 mapped records, 120 spliced records, 42,000 M/=/X reference bases and 26,000
skipped reference bases. Tests cover physical corruption, complete tag parsing,
exact resource boundaries, explicit NH/XS policies, source changes, cancellation
and retention of raw flags. These counts are raw geometry, not inferred usable
StringTie reads or a biological benchmark.

Actual setup controls install all seven exact profiles offline, recheck all seven
without changing their payloads, acquire two official archives through the
permitted download path, and test forbidden install/download, corrupt archives,
corrupt kits/payloads and existing-path preservation. A real UID65534 permission
control returns named SKIP3 diagnostics. These deliberate failure/skip controls
do not mean a user dataset was analyzed or skipped. Private Linux homes and
original reports remain retained for subsequent runtime admission.

The exact candidate also exports a kit through its actual Java main on Windows
Java21/25 and Linux Java21. An exported installer is exercised from an isolated
Linux working directory using only the copied kit and previously downloaded
archives, without repository code imports. Setup still admits no scientific
runtime or user fit. The initial BAM compile failure is retained; XML copied
from a preceding run in that attempt is explicitly labeled stale, not test
evidence for the failed compilation.

Remaining work: exact current-host ELF/library and fresh native health admission,
typed assembly/estimation/merge fits, independent source-population/coverage and
gene checks where supportable, complete portable fit-bundle verification,
paper-use curation and catalog/browser gates. StringTie remains missing until
the public fitting workflow is validated.
