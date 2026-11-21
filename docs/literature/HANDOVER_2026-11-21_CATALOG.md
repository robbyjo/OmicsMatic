# Toolbox handover: 21 November 2026

**Resume here. The 250-paper collection is complete; implementation and individual
usage review remain unfinished.** The latest TwoSampleMR admission brings the
catalog to **144 scoped implementation families: 26 native Java and 118 upstream
adapters**, or 147 including three existing integrations. These counts describe
validated, bounded operations, not complete replacements for entire packages.
There are 446 named labels, 485 registry definitions, 2,722 paper/tool observations,
**1,630 individually reviewed uses and 1,092 pending uses**. The two validated
aliases, COJO/GCTA and voom/limma, receive no extra family credit. Inventory gaps
remain **259 missing labels and 29 method-overlap labels**; neither category is
qualified implementation credit.

The frozen collection is 71 Nature, 104 Nature Genetics and 75 AJHG papers from
2023-10-23 through 2026-10-23. No date extension was needed. This is the originally
selected three-year window, not a newly refreshed rolling window at handover.

## What was just completed

The [TwoSampleMR integration](TWOSAMPLEMR_CATALOG_INTEGRATION.md) admits one upstream
family and fourteen source-role reviews. Only P157/P177 observations are added;
all 1,616 earlier review records, 445 unrelated inventory rows, 250 paper identities
and unrelated observations remain exact. P157's archived bidirectional calls are
static attribution. P177's IVW provider is separately named
MendelianRandomization; its median/mode attribution is an inference, and its
edition/Egger gaps remain explicit. The retained proposal and author-code audit
must not be presented as executed author pipelines.

The source-built `twosamplemr-tools` command supplies seven exact profiles:
modern 0.7.14 prepared data and local clumping; historical 0.4.9/0.5.6/0.5.7/0.6.8
formatting/prepared data; and historical 0.6.8 local clumping. The prior normal
producer has 858 focused tests per Windows Java21/25 and Linux Java21, seven
examples, seven real replays, fifty full original-return/class/RNG comparisons
and ten CLI refusals. This admission changes no Java/R scientific source or
numerical producer. Current catalog tests and the saved evidence reader check
metadata and retained qualification; they do not re-execute scientific analyses.

The admission uses separate plan, inspection, transactional application and fresh
verification. The current manifest has 838 clean members. Thirty model/transaction
tests, sixteen source-semantic refusal controls and a fresh 250-paper audit pass
per Windows/Linux host. Nine pinned author files and the paper-linked P157 archive
are checked. Six actual CLI refusals preserve canonical bytes. Independent
registry/ledger/source-inverse checks and preview/installed desktop/mobile report
checks pass. See the [saved checkpoint](twosamplemr-catalog-checkpoint/README.md).

## Next work, in priority order

1. Continue the remaining **1,092 individual use reviews**, grouping papers by
   high-impact software families. Read actual Methods, supplements and linked
   author code; distinguish analysis from citations, resources, acquisition,
   plotting and generic method names. Preserve source wording, versions, provider
   distinctions and unresolved discrepancies. New discoveries may change the
   observation denominator; record an explicit before/after transition.
2. Prefer high-impact open workflows that can be completed and validated within
   a bounded scope. **Monocle (9 papers)** already has native prepared-graph and
   original Monocle2/3 development work: audit existing evidence, reported editions
   and paper roles before adding credit. **QTLtools (9), SKAT-O (8), ACAT (7),
   FUSION (7), MR-PRESSO (6), SKAT (6), fastQTL (5), GenomicSEM (5) and PRSice (5)**
   have related methods or gaps; inspect the current project before implementing
   duplicates. Prioritize by actual reviewed omics/GWAS use, applicability and
   testability, not mention count alone. These are candidates, not newly qualified
   equivalents or promises that every package is immediately installable.
3. For simple, well-specified numerical methods, implement native Java and compare
   source behavior against the selected original edition. For larger workflows,
   use explicit upstream adapters with tested RUN/VERIFY/REPLAY and clear external
   inputs, dependencies, capacities, output admission and provenance. Do not
   silently substitute an approximately related method.
4. Audit high-frequency proprietary/instrument-associated labels separately:
   Prism (47), FlowJo (27), FACSDiva (17), bcl2fastq (10), Space Ranger (10).
   Frequency does not establish available source, execution permission or a
   reproducible workflow. bcl2fastq retains unresolved licensing/input requirements.
   **Cell Ranger is explicitly excluded by the user.** Current iDEP was suggested
   instead of legacy iDEP.96; do not infer that current terms license the old source.
5. Keep TwoSampleMR's remaining scopes explicit: legacy API clumping for older
   editions, remote reference/authentication, optional providers, full author input
   preparation and dependency-era reconstruction remain unqualified. Unreported
   editions stay unknown. P112 trait counts, P150 MAF wording, P211 Steiger/R-version
   distinctions and P224 separate MVMR/conditional F remain visible.
6. Admit additional work only through a source-bound before/after catalog change,
   fresh source audit, independent review/registry readback and manifest/UI checks.
   Credit one implementation family once. Do not mark the overall goal complete
   until the requested review and implementation scope is actually handled.

The current [tool inventory](omics-2023-2026/tool-inventory.json),
[review ledger](../../tools/literature/reviewed-uses.json) and
[paper catalog](omics-2023-2026/papers.json) are authoritative at this checkpoint.
The saved packet retains `current/independent-readback-1/PRIORITY-CANDIDATES.json`
with the top 35 current gap candidates and paper IDs.

## Exact local resume points

The workspace is `D:/projects/JLinAlg`. The clean publication owner after commit is
`D:/projects/JLinAlg/build/mr-9`, branch `codex/twosamplemr-catalog-20261121`.
All **47 worktrees** must be preserved. The root remains an older dirty working
branch; do not reset, clean, overwrite or check out main there. Prior uncommitted
variants remain historically archived under
[`handover-2026-11-21-worktrees`](handover-2026-11-21-worktrees/README.md), not promoted
as current validated source. Main contains the current integrated implementation.
Use the clean publication owner or a fresh clone of main for new work.

Current complete private evidence is
`D:/projects/JLinAlg/build/mr-9/build/twosamplemr-catalog-1`.
The plan, inspection, application, Windows verification, Linux retry, UI results,
negative controls and failed attempts are retained in separate output directories.
Publication/preservation receipts are under
`D:/projects/JLinAlg/build/git-publish/20261121-twosamplemr-catalog-91`.

The exact unchanged qualified global owner is
`D:/projects/JLinAlg/build/mr-8`, commit
`59be97062b0773fb9aad75038651cebdc0ea52c1`.
Its preceding PEER catalog owner is sibling `pr-2`, commit
`ac75e6958d7cf4cf6ec6372ec0548e1dcb1dcee1`.
Keep both clean. Full fresh admission verification runs the old saved reader on
that real old checkout; it must not overlay old metadata onto the new catalog or
refresh old hashes to make an old verifier accept changed current files.

From the current checkout, with Python3.11+ and a NEW output directory:

```powershell
python -B tools/literature/verify_twosamplemr_catalog_checkpoint.py --out build/NEW-saved-check
```

Add `--restore` to extract the public saved evidence. This portable check reads
exact retained bytes and recomputes the pure catalog transition; it requires no
Git, R, Java, full-text cache or historical checkout. It is not fresh scientific
execution. The recorded old-owner execution is provenance, not a claim that the
portable reader runs that owner again.

For a full fresh source/owner check on the original machine:

```powershell
python -B tools/literature/close_twosamplemr_catalog.py --verify --out build/NEW-fresh-check --primary-cache D:/projects/JLinAlg/build/literature-review/xml --author-code-cache D:/projects/JLinAlg/build/twosamplemr-author-code-intake-20261121-1 --historical-checkout D:/projects/JLinAlg/build/mr-8
```

Do not reapply the already installed plan. WSL native Git cannot read the Windows
absolute gitdir of linked worktrees. The successful Linux retry uses an explicit
Windows-Git executable and a PATH transport wrapper for inherited helpers that
call `git` directly. See retained `current/linux-git-transport/git` and the recorded
verification command. This changes command transport only; no source, fixture,
metadata or hash overlay is used. A native Linux clone normally uses native Git.

## Warnings and required actions

**GitHub is not a complete machine backup. Transfer ignored private artifacts
separately with SHA256 verification before retiring this machine.** Preserve
original/raw/null/failed/superseded evidence. Public saved packets omit licensed or
restricted source text, binaries, original full text and heavyweight build outputs.
Use the preceding
[private-transfer manifest](twosamplemr-historical-handover/PRIVATE-TRANSFER.json)
and [global omitted-artifact record](twosamplemr-global-checkpoint/OMITTED.json),
plus the new checkpoint's private-transfer record. Key local roots are:

- `build/twosamplemr-runtime-intake-20261121-1/library` (modern runtime).
- `build/twosamplemr-historical-runtime-20261121-1` and
  `build/twosamplemr-historical-intake-20261121-1` (historical runtime/source).
- `build/twosamplemr-author-code-intake-20261121-1` (pinned author source/archive).
- `build/literature-review/xml` and retained author full-text intake.
- Every predecessor worktree's ignored evidence and the current private evidence.
- `build/omics-plink-official/plink1/plink.exe` and the selected system R/JDKs.

The unchanged normal producer is
`build/mr-8/build/twosamplemr-global-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar`,
197,091,867 bytes, SHA256
`d462a2c7bb31f16a21448852b0acb4b622de84f4e1af5fa963ba3716cd1e9689`.
The published 0.4.0 release predates the new command; build current source or
transfer this exact qualified producer. No release is created by this handover.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Restore the exact named permitted runtime,
profile, executable, input or clean historical checkout. Downloads/private installs
are authorized when allowed. If installation/execution is actually disallowed,
skip the dependent analysis and clearly state what the user must supply; absence
alone is not an installation prohibition.

**FAIL2: NO COMPLETE RESULT.** Retain original errors, logs and partial outputs.
Correct the named data, numerical, integrity, capacity or process failure and retry
in NEW output. Do not silently repair identifiers, populations, allele/trait order,
thresholds, versions, providers or nonfinite values. Read `WARNINGS.md`,
`analysis-status.json`, `worker/report.json` and original logs.

Original TwoSampleMR R execution is qualified on Windows R4.6.1 with the declared
present-day dependency stack. Linux checks saved captures without R. Historical
dependency-era reproduction is unproven; 0.4.9 omits only broken `man/` documentation.
Legacy startup source contains upstream application configuration that stays
private: never publish the complete old startup or dump global R options.

Both literature reports remain off GitHub Pages. The inherited full website gate
fails for missing scientific citations in `broad-gsea-tools.md`; scoped report
checks pass. Six inherited descriptive registry/inventory differences remain
explicitly retained; implementation status, pattern and source fields agree.
No new GPU hardware qualification is claimed. Current failed attempts are retained:
an initial manual unit invocation, the first Linux control transport attempt and
the first full Linux verification. Later successful attempts supersede their
execution status without deleting their evidence.

The user authorizes pushing current work to GitHub main. Preserve unrelated dirty
owners, validate exact staged bytes, integrate an advanced remote without force,
then verify the remote commit and published file hashes. The full goal stays active.
