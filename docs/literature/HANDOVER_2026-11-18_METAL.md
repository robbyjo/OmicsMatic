# Literature toolbox handover — 18 November 2026

**Start here. The overall goal remains unfinished.** This handover records the
latest completed milestones and preserves all current native meta-analysis
development work. It supersedes the current-state instructions in
[the earlier LD-score handover](HANDOVER_2026-11-18_LD_SCORES.md). Older
checkpoints, failed attempts and original hashes remain historical evidence.
No release or additional tool-family admission is made by this handover.

## Current catalog and completed work

The frozen cohort is **250 papers** from **2023-10-23 through 2026-10-23**:
71 Nature, 104 Nature Genetics and 75 AJHG. It contains 446 named families,
485 effective definitions and 2,695 paper/tool observations. There are
**129 new scoped integrations: 24 native Java and 105 upstream**, or 132
including three pre-existing adapters. **1,266 positive uses are individually
reviewed; 1,429 observations still need review.** There are 259 missing and 46
method-overlap definitions. These counts do not establish whole-tool parity or
paper reproduction.

Recent qualified commits already on GitHub main before this handover:

| Commit | Result and evidence |
|---|---|
| `586f758f0835bb2f40fb6792bc1bb2da6353808f` | Captured native reference LD-score preparation, SDK graph preflight/resource accounting, source comparisons and Windows/Linux qualification. Read [the checkpoint](LD_SCORE_REFERENCE_CHECKPOINT_2026-11-18.md) and [guide](../vignettes/ldsc-reference-preparation.md). |
| `91549561599bba8b1d5f069033a2273bb1c5d8ad` | LDAK catalog admission after source-specific role review and retained/current-reader audits. Read [the checkpoint](LDAK_CATALOG_CHECKPOINT_2026-11-18.md) and [integration guide](LDAK_CATALOG_INTEGRATION.md). |

The earlier LD-score draft has now been implemented and qualified by
`586f758f`; do not restart that old draft's to-do list. Its archive stays
unchanged. Reference qualification retains 37 original models and 75,226
reference fields at each boundary, 20 focused tests on each platform, 31 actual
native launches and two retained original-CLI subset failures. Those upstream
failures are not complete original subset results. LDAK's positive roles are
P090, P109, P120, P171 and P238; P245 is retained prior-work context rather than
positive tool use. Do not reapply its obsolete six-positive-role preview.

## Current unfinished work: METAL and native weighted Z

METAL appears in 24 current observations and remains **method-overlap**. A
dedicated captured METAL workflow is not implemented or admitted. Selection of
this next high-impact family follows the user's request to prioritize impact.
Existing inverse-variance Java methods already exist; the missing independent
sample-size-weighted signed-Z component was started instead.

All four exact development files, an applicable Git patch, independent R
fixtures and 35 indexed development-evidence payloads are committed in
[metal-weighted-z-draft](metal-weighted-z-draft/README.md). The draft is outside
executable source so the handover retains qualified production source. It
compiled and passed **7 Windows focused tests**: **69 finite R reference models
and 1,844 fields**, plus **one expected unrepresentable model with 20 retained
raw diagnostics**. It is independently written mathematics, not a METAL source
translation. Linux/package/source-specific validation remains unfinished.

The unmodified original METAL release **2020-05-05**, commit
`e2253cc3901df8403a331bd725d4d9fe1edfb19f`, was privately built under Linux x86_64
and passed its official CTest. All 136 original source files remained unchanged.
A verified CMake 3.31.10 wheel was unpacked into the workspace; no system-wide
installation or source patch was made. **Original source headers prohibit
redistribution. Do not publish the original source, archive, executable or
bundled example data.** Runtime-build success is not project adapter or
scientific qualification. Saved build metadata is included; the runtime itself
is local only. See the [official repository](https://github.com/statgen/METAL)
and [command reference](https://genome.sph.umich.edu/wiki/METAL_Command_Reference).

The 24-paper primary-source intake has been captured and its XML identities
checked. **It is intake only, not 24 completed semantic reviews.** IDs:
P076, P077, P088, P090, P105, P107, P112, P114, P120, P123, P125, P126, P130,
P135, P136, P140, P150, P162, P191, P199, P200, P203, P212 and P243.
No catalog role, count or integration credit was changed by this work.

## Next work, in priority order

1. Apply the preserved weighted-Z patch on a clean `codex/` branch and rerun
   the seven focused tests. Run the same suite on Linux and through the actual
   packaged JAR; compare complete models/diagnostics across platforms. Check
   all pre-existing JAR members against the frozen qualified baseline, allowing
   only the intended new class prefix. Keep the expected numerical refusal.
2. Use the unchanged private METAL runtime for synthetic source comparisons.
   Cover ordinary sample-size weighting, inverse-variance reuse, effect-allele
   orientation, single-cohort and tiny-Q behavior, P underflow and precision.
   Preserve source-specific differences explicitly: `LOGPVALUE ON` is output
   control, not a log-P input declaration; source output log P uses base 10.
   The draft SDK accepts natural-log inputs. The original's tiny-Q/single-cohort
   heterogeneity conventions differ from the independent SDK. Confirm literal
   behavior with actual controls rather than forcing agreement.
3. Implement and test a typed captured **original METAL workflow adapter**:
   freeze inputs/runtime, declare the exact scheme and allele/effect columns,
   capture commands and raw consoles, enforce result admission, and qualify
   genomic-control, overlap, heterogeneity and pooled-frequency scopes that
   the reviewed papers actually require. Handle upstream errors even with exit
   zero. Do not infer effective sample size, author settings or missing alleles.
4. Individually review all 24 METAL paper roles and source paragraphs before
   family admission. P140 explicitly uses sample-size weighting; P212 includes
   pooled allele-frequency use. P076 and P243 name a 2011-03-25 edition; a 2020
   private runtime does not establish those historical author runs. Distinguish
   execution, benchmark, reference reuse and under-specified settings. Add
   beginner examples, a new source-bound verifier and closed qualification.
5. Continue remaining high-impact gaps using
   [IMPLEMENTATION_PRIORITY.md](IMPLEMENTATION_PRIORITY.md) and the current
   inventory. LDSC munging/allele QC, liability conversion and distinct wrapper
   methods remain separate work. Do not count existing LDSC components as full
   Genomic SEM, gsMap, CELLECT or Covariate-LDSC implementations. Relatedness
   methods (`KingRobustKinship`, `KingKinshipThreshold`, `SnprelateKingRobust`)
   and inverse-variance `MetaAnalysis` already exist: inspect before duplicating.
6. Finish the 1,429 unreviewed observations and remaining missing integrations.
   Preserve literal editions, source pointers and negative/context decisions.
   Prioritize broadly reused analytical methods over low-impact catalog-only
   additions. There is no defensible completion ETA for this remaining scope.

## Fresh checks at this handover

The current catalog verifier rechecked all **250 primary XML hashes**, all
**797 manifest entries** and the LDAK admission packet with 2,424 indexed
payloads; there were no manifest mismatches, missing entries or extra entries.
The current reference LD-score verifier rechecked its source bindings, 1,383
qualification payloads and inherited historical evidence. Both passed.
These are source/evidence audits; historical Linux/Windows upstream analysis
and JUnit suites were not re-executed by those audits. The separate draft
Windows build/test pass is retained with its exact source snapshot.

The 3 GB private LDAK store was deeply audited in the previous qualified
milestone and was not fully rehashed again here. All 21 worktrees were checked
against the previous publication snapshot; the other 20 were unchanged.
Machine-readable results are in
[HANDOVER_METAL_CHECKS_2026-11-18.json](HANDOVER_METAL_CHECKS_2026-11-18.json).

## Prominent warnings and required actions

**For qualified existing analysis, use matching qualified source and the
frozen source-build JAR below.** The latest public `0.4.0` release predates
these additions, and the version string alone identifies neither source nor
qualification. The private weighted-Z development JAR is unqualified and
includes the draft classes; do not substitute it or stale Gradle classes.

**SKIP3 means dependent analysis was skipped.** Supply the named permitted
runtime/reference/platform, or leave that analysis skipped. Download/install
permission is already granted where the operating environment permits it.
If installation is unavailable or forbidden, report the affected analysis,
reason and exact recovery action prominently.

**FAIL2 means no complete result.** Retain inputs, partials and failure evidence,
correct the stated cause and retry into a new output directory. For captured
workflows require exit 0, the workflow's completion marker and successful
verification/admission; an upstream zero exit or saved model alone is not
completion. Never silently change IDs, drop failed rows/pairs, replace NaN/Inf,
force positivity or use a pseudoinverse to manufacture success.

Cell Ranger remains excluded by the user. FIMO educational/research/nonprofit
use and the specific GATK 3.8 academic noncommercial terms were accepted.
Legacy iDEP.96 licensing remains unresolved; newer iDEP 2.4.4 work is separate.
LDAK tutorial replacements of 18 zero statistics and four tagging floors need
explicit recorded permission or refusal; they are not silent native repairs.
Do not repeat settled permission questions or redistribute restricted runtimes.

Keep both literature HTML pages withheld by `.github/workflows/pages.yml`.
The previous browser check did not qualify the complete warnings page's mobile
layout. This handover publishes no release and makes no biological or
whole-paper completion claim. Preserve historical, skipped and failed evidence.

## Workspace and handover inventory

The authoritative checkout is **`D:/projects/JLinAlg/build/high-impact-stats-1`**,
branch **`codex/metal-workflows-20261118`**. The target is
[`robbyjo/OmicsMatic` main](https://github.com/robbyjo/OmicsMatic/tree/main).
The original `D:/projects/JLinAlg` checkout and other worktrees retain older
dirty overlays. They were unchanged from the previous published snapshot.
**Do not broadly stage, reset or clean them, or replace newer integrated source
with older files.** Their unchanged historical state is preserved locally.

Important absolute locations for a machine handover:

- Qualified frozen JAR:
  `D:/projects/JLinAlg/build/high-impact-stats-1/build/ldsc-reference-cli-1/build-2/jar/omicsmatic-0.4.0.jar`,
  194,091,631 bytes, SHA-256
  `eb0212234d8e14193063bbd9f42009b20f43f4ec312d83e2be890e7761f1c7de`.
- Current private work:
  `D:/projects/JLinAlg/build/high-impact-stats-1/build/metal-1/`.
  `upstream-1/` holds the private original source intake;
  `build-original-1/native-build/bin/metal` is the private binary;
  `PRIMARY-ROLES.private.json` holds exact primary paragraphs and pointers;
  `weighted-z-reference-{1,2}/` and `weighted-z-build-1/` hold both reference
  attempts and the completed draft build. The draft JAR is pinned in DRAFT.json.
- Private METAL binary: 242,792 bytes, SHA-256
  `dc07f5b3718845c0e177a5bcd7c0f46f94b9a39b3f082e9f458fb2a8b7695a98`.
  Linux x86_64 runtime was built in WSL distribution
  `JLinAlg-Omics-SAIGE-20261026`. No project installer is implemented.
- Full private LDAK evidence:
  `D:/projects/JLinAlg/build/snakemake-workflow-1/build/ldak/qualification-evidence-1/`.
  Keep its INDEX, all objects and matching frozen reader. The public catalog
  archive is a selected portable audit packet, not this complete store.
- Primary XML cache: `D:/projects/JLinAlg/build/literature-review/xml/`.
- Fresh handover audits and patch replay:
  `D:/projects/JLinAlg/build/high-impact-stats-1/build/handover-20261118-meta-1/`.
- Publication and preservation receipts:
  `D:/projects/JLinAlg/build/git-publish/20261118-handover-meta-19/`.

Git contains source, documentation, closed public evidence and the exact
unfinished draft. Ignored build caches, large private evidence stores and
licensed original runtimes are not uploaded. Retain the named workspace for
a machine handover. No tool analysis was left intentionally running.
