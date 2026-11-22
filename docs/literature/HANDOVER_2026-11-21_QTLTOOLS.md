# Toolbox handover: QTLtools workflow checkpoint, 21 November 2026

**Start here. Collection is complete at 250 papers; implementation and individual
paper-use review remain unfinished.** Canonical main has **145 scoped families
(26 native Java + 119 upstream adapters)**, or 148 including three existing
adapters. There are 2,722 observations, 446 labels, 485 registry definitions,
**1,630 reviewed and 1,092 pending uses**, 258 missing labels and 29 method-overlap
labels. These are scoped implementations, not complete replacements for every
operation in a software package. This handover adds **zero family credit**.

The corpus stays frozen at 2023-10-23 through 2026-10-23: 71 Nature, 104 Nature
Genetics and 75 AJHG papers. Do not shift dates or replace papers on resume.

## Warnings and required actions

**The full QTLtools adapter is unfinished and must not be used for analysis.**
Six exact draft files are saved under
[`tools/literature/qtltools_workflow_development`](../../tools/literature/qtltools_workflow_development/README.md),
outside active production sources and resources. Three Java classes and the
synthetic-input generator compile against the qualified P103 JAR with Java21,
targeting Java17. The Python worker parses and its profile hash matches. These
are compilation and syntax diagnostics only. No workflow or generated-input
scientific operation has run; no output auditor, RUN/VERIFY/REPLAY CLI, global
route, qualification suite or QTLtools catalog admission has been completed.
Restore deliberately into a NEW worktree before development; do not run a draft
worker directly or copy these files over an unrelated checkout.

**GitHub is not a backup of ignored private artifacts. Transfer those separately
before retiring this machine.** Preserve all existing worktrees and ignored
build directories, runtime archives and extracted dependencies, JARs, full-text
cache, author-source cache, and all successful, failed, null and invalidated
scientific captures. The [private transfer record](handover-2026-11-21-qtltools/PRIVATE-TRANSFER.json)
gives exact current roots and earlier transfer inventories. Runtime binaries,
third-party example genomes and private full text are excluded from this public
checkpoint. No private transfer destination was supplied, so no transfer is
claimed. Compare SHA256 manifests after any authorized private transfer.

**Dependency failure must produce a prominent skip with a remedy.** Downloads
and private installation are authorized when the operating environment permits
them. If a needed installation is prohibited, a runtime is unavailable, or exact
edition/library identity cannot be supplied, skip that dependent analysis
(`SKIP3`). Name the affected operation, exact missing executable/library/input or
permission, and the steps needed to retry. Never silently substitute an edition,
method, engine or sample set. Native fdensity needs Java only. Original controls
need the selected Linux executable and libraries; Windows transport uses the
explicit WSL distribution below.

**Errors admit no result.** Invalid data, numerical failure, capacity exhaustion,
timeout, changed source/runtime or incomplete process cleanup must retain raw
inputs, logs and partial outputs and report `FAIL2`. Correct the named cause and
retry into NEW output. Do not overwrite a failed capture, silently repair NaN/Inf,
change thresholds, or count a retained null/error output as a successful result.
The drafts do not yet establish these guarantees: qualification remains required.

**Preserve the source-order warning in fdensity.** The original small annotation
tree can skip a chromosome's matches depending on its first input row. The native
API preserves this source behavior. Calling workflows must prominently expose
`sourceOrderGuardedQtlRows()`; do not sort input silently. See the
[qualified API guide](QTLTOOLS_FDENSITY.md).

**License and user decisions persist.** Use native Java for straightforward,
well-specified methods and tested upstream adapters where appropriate. Educational,
research or nonprofit use was established for MEME/FIMO; GATK3.8's academic
noncommercial terms were separately accepted. Cell Ranger is explicitly excluded.
The user suggested newest iDEP instead of legacy iDEP.96: inspect current terms
and source rather than assuming they license the old version. Do not request
these same decisions again. New concrete restrictions still need to be resolved
without inventing permissions.

## Qualified baseline

Main baseline before this handover is
[`ec11adbaf549b18ffd3d01fcc9d2584c2859e1d6`](https://github.com/robbyjo/OmicsMatic/commit/ec11adbaf549b18ffd3d01fcc9d2584c2859e1d6).
The original owner is `D:/projects/JLinAlg/build/qt-1`, branch
`codex/qtltools-high-impact-20261121`. Its native
`org.jlinalg.genetics.QtltoolsFunctionalDensity` is a library API only.

P103 retains 576 actual synthetic original-reference cases: 569 completed
outputs, seven original errors and 2,838 complete bins. All 588 focused tests
passed per normal build and packaged Windows Java21/25 and Linux Java21 JVM.
All 22,425 preceding JAR payloads stayed byte-identical; four native classes were
added. The initial 313-failure build is retained and unadmitted. These are prior
qualified results, not fresh executions by this handover. Its frozen saved reader
must run on that actual unchanged owner, because it pins then-current files.

The qualified normal JAR is:

```text
D:/projects/JLinAlg/build/qt-1/build/qtltools-fdensity-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar
bytes: 197267583
SHA256: 4adcd89262b3f5cf748c5eb1060491bcf83aa8b957554349b9be08278633bc8e
```

The published release predates this API; build current qualified source or use
the exact development JAR above. No release or Pages deployment is requested.
The preceding Monocle admission is complete at
`3408fdcf476fb9c39b83eb30d00bda97262d873b`, owner `build/mo-9`; it accounts for
the current 145-family catalog. Do not reapply the older unapplied Monocle plan.

## Exact resume points and saved changes

Workspace: `D:/projects/JLinAlg`. Current unfinished owner:
`D:/projects/JLinAlg/build/qt-2`, branch `codex/qtltools-workflow-20261121`,
based on `ec11adba...`. It retains all six original untracked files unchanged.
The publication owner is `D:/projects/JLinAlg/build/handover-105`, branch
`codex/handover-20261121-qtltools`. **All 61 preceding worktrees are preserved**;
the publication owner is number62. Every preceding HEAD is reachable from the
qualified main baseline. The old root branch remains dirty: do not reset, clean,
check out main, or overlay a historic catalog there.

The [saved packet](handover-2026-11-21-qtltools/README.md) records every existing
worktree's Git-visible file variants, deletions and staged patch. Already-public
historical variants reuse pinned Git blobs or the earlier handover archive;
new draft bytes are saved in the new delta archive. This preserves all visible
changes as development history without applying incompatible old drafts to main.
`RESTORE.json` maps each of the six QTLtools drafts to its intended path, exact
hash, and baseline target. Do not restore automatically over current source.

Current publication/diagnostic receipts:
`D:/projects/JLinAlg/build/git-publish/20261121-handover-qtltools-105`.
Earlier unpublished workflow intake receipts:
`D:/projects/JLinAlg/build/git-publish/20261121-qtltools-workflow-104`.
P103 full evidence: `D:/projects/JLinAlg/build/qt-1/build/qtltools-fdensity-1`.
Primary paper context: `D:/projects/JLinAlg/build/mo-9/build/qtltools-discovery-1/private-context`
(private; never publish full paragraphs).

To verify saved bytes and optionally restore COPIES, use Python3.11+ without -O,
full main Git history, and a NEW output directory:

```powershell
python -B tools/literature/verify_qtltools_handover.py --out build/NEW-handover-check --restore
```

On Linux/WSL, if native Git is unavailable, pass an explicit `--git` executable.
The reader executes no model, installs nothing, applies no patch and never
restores over original worktrees. It checks integrity and declared scope;
SHA256 is not authentication or independent scientific verification.

## Next work in priority order

1. **Finish the high-impact QTLtools workflow before claiming full-tool credit.**
   Inspect the saved contracts, files, runtime, worker and profiles, deliberately
   restore into a NEW baseline worktree, then review supervision first. Handle
   malformed cleanup receipts conservatively; wait for launcher termination;
   sanitize loader variables before Python launch; reject unsupported Python,
   platform or missing `ldd` as unavailable; and kill/reap a fresh stopped child
   if handshake fails before the owned ledger is durable. Reprofile NEW worker
   bytes, preserving the initial draft and all old evidence.
2. **Execute the synthetic generator and direct originals.** Compilation passed;
   generation was not run. Inspect BEDCodec indexing of QTL phenotype BED's
   nonstandard columns, BAM index naming and exact header/index compatibility.
   Use only artificial data. Run exact editions1.1/1.2/1.3.1 across nominal cis,
   fixed permutations, conditional mapping, all grouping choices, trans nominal,
   permutation/sample/adjust, MBV, quan and fdensity. Inspect actual original
   source/output schemas first. Preserve all errors and null cases. Do not use
   bundled upstream human example genomes as public fixtures.
3. **Implement output admission and RUN/VERIFY/REPLAY.** An exit code of zero is
   insufficient: require completion, complete outputs, version/group-dependent
   schemas, numeric domains and source/runtime identity. A valid no-cis-variant
   permutation row has zero variants and an NA tail; retain it as null, not p=1.
   Nonfinite fitted results with variants must fail admission. Handle all trans
   exports, MBV's 11 columns and versioned quan schemas. Preserve original raw
   filenames, chromosome/coordinate behavior and numerical values. Native VERIFY
   must recompute all bins and metadata. Original VERIFY is an offline provenance
   and schema check; REPLAY reruns models into NEW output with parent seals intact.
4. **Qualify the actual workflow and normal JAR.** Include real missing-runtime,
   wrong-edition, malformed input, capacity, timeout, owned-group cleanup, source/
   library change, archive/fixture/transcript tamper and resealed-result refusals.
   Check exact argument ordering across JVMs, input/index capture, no silent
   preprocessing, fresh direct-original comparisons, Windows Java21/25 and Linux
   Java21 packaged executions and replays. Only then register the global CLI and
   qualify shared dispatcher/source compatibility without refreshing old hashes.
5. **Complete paper-bound QTLtools review/admission.** Nine positive papers and
   19 source pointers were found by an all250 scan; see the
   [source assessment](QTLTOOLS_SOURCE_ASSESSMENT.md) and public census in the
   fdensity packet. Versions1.1/1.2/1.3.1 are distinct. P190's executable binding
   remains unresolved. P073's literal `--grp` is not automatically replaced by
   `--grp-best`; test ambiguity and retain the discrepancy. P201 reports 10,000
   adaptive permutations, while inspected cis accepts a fixed integer; do not
   invent an adaptive argument. P203's "match" wording does not establish the
   renamed MBV edition. P219's separate quan edition is unreported; P227 citation27
   is not version1.2.27. Threshold files for conditional mapping must be explicit
   nominal thresholds, not silently substituted permutation/FDR outputs.
6. **Continue the full toolbox goal.** Review the remaining1,092 uses against
   Methods, supplements and author code. Prioritize high-impact GWAS/omics gaps
   such as SKAT-O, ACAT, FUSION, MR-PRESSO, SKAT, fastQTL, GenomicSEM and PRSice,
   checking existing native methods/adapters before implementing duplicates.
   Admit each family once through a source-bound catalog transition, fresh source
   audit, independent ledger/registry readback and manifest/UI verification.
   Preserve the distinction between package citations, resources, acquisition,
   preparation, plotting and executed analyses. The original goal remains active
   and unfinished; no ETA or completion percentage is invented.

## Exact upstream intake available locally

WSL distribution: `JLinAlg-Omics-PyMOL-20261026` (Linux x86_64).
Runtime cache: `D:/projects/JLinAlg/build/literature-review/qtltools-runtime`.

| Edition | Exact private executable relative to cache | SHA256 |
| --- | --- | --- |
| 1.1 | `historical-intake-1/1.1/binary/QTLtools_1.1_Ubuntu16.04_x86_64` | `0c5e469b42753ffe9ec2d9690aa21f626a9e5294cdf9ecbe3c024f78c72a7024` |
| 1.2 | `historical-intake-1/1.2/binary/QTLtools_1.2_Ubuntu16.04_x86_64/QTLtools_1.2_Ubuntu16.04_x86_64` | `1350229c46349fae4337edbc7088b2a65a3f38d8e3cfc8d1daf7910faaaad0f0` |
| 1.3.1 | `ubuntu-private-1/root/usr/bin/QTLtools` | `246108e394f04961741c0393667e5d0b0a2f965aca2aea59ddd50c779f14f2c0` |

Official1.1/1.2 archives have GPL notices and are selectively extracted.
The1.3.1 build is Ubuntu noble `1.3.1+dfsg-4build3`; its source tag peels to
`a9606ee736d8489e3ec54c8b8c92c32fecad3bd0`. Private extracted library paths need
`root/usr/lib`, `root/usr/lib/x86_64-linux-gnu` and its `blas` subdirectory.
The first BLAS load failure is retained; the corrected path passes load/help.
System QTLtools was not installed. All three executables load and their top-level,
cis/trans/MBV/quan/fdensity help exits successfully in `edition-probes-1`.
**Load/help success establishes no scientific workflow execution.** Quan1.1 uses
plural `--samples`, supports multiple BAMs, and has no TPM/hash flags;1.2/1.3.1 use
singular `--sample`. Do not make a uniform command by guessing across editions.

This checkpoint publishes source drafts, safe metadata and public help logs only.
It preserves canonical scientific source, catalog, build configuration and site.
Historical handovers below the current pointer keep their original then-current
counts and unfinished states; read this handover first.
