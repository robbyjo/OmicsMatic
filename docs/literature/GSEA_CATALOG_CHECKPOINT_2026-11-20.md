# Native GSEA catalog checkpoint and next high-impact work

**Native Broad GSEA and 28 source-reviewed ranked-enrichment roles are now
admitted locally. The full 250-paper implementation/review goal is unfinished.**

This checkpoint follows main `b89c57376177b964205aa219fe5efd378dfa6a9e` and is
developed in `D:/projects/JLinAlg/build/gs-6`, branch
`codex/gsea-catalog-20261120`. Find its published commit in Git history and
`build/git-publish/20261120-gsea-catalog-66/PUBLISHED.json`. Start subsequent
work in an isolated checkout of latest main; retain this qualified stage.

## Current admitted state

The frozen collection remains **250 papers: 71 Nature, 104 Nature Genetics and
75 AJHG**, dated 2023-10-23 through 2026-10-23. No extension was needed. There
are **2,714 observations, 1,548 individually reviewed uses and 1,166 pending**.
All 1,520 preceding reviews remain exact. Nine missed observations are added:
P042/P069/P133/P151/P163/P165/P170/P182/P228. No observation is removed.

There are **140 scoped integrations: 26 native Java and 114 upstream**; including
three pre-existing integrations gives 143. The inventory still has 446 named
families/485 effective definitions, with 259 missing and 35 method overlaps.
The manifest has **826 clean entries**. Scope badges do not establish complete
tools, author workflows, paper-wide completeness or biological validity.

Read [the integration and provider crosswalk](GSEA_CATALOG_INTEGRATION.md),
[global command guide](../vignettes/broad-gsea-tools.md),
[source inputs/licensing](gsea-catalog-sources/README.md),
[admission controls](gsea-catalog-controls/README.md) and
[actual stage/CLI/UI validation](gsea-catalog-validation/README.md).

The already-qualified Java engine/global command is unchanged. It implements
prepared-data Broad 4.3.2/4.3.3/4.4.0 preranked, expression/gene-set and
expression/phenotype analyses with explicit settings, full null families and
closed RUN/VERIFY/REPLAY. It needs no external GSEA runtime. Global qualification
retains 668,739 original fields per JVM on Windows21/25/Linux21, eight focused
tests and actual predecessor-capture verification/replay. No new Java/model
refit is claimed by this catalog-only stage. The normal producer remains:

```text
D:/projects/JLinAlg/build/gs-5/build/gsea-admission-1/global-gradle-1/project-build/cli/omicsmatic-0.4.0.jar
195901211 bytes
SHA-256 bdc1e71bd7a53e0d727deb105c5c97baaaf8cc6627275bf14aee9584816c0dff
```

## Source findings and remaining author prerequisites

The expanded all-250 audit finds 56 candidates: 28 ranked-enrichment roles and
28 distinct, resource-only or unresolved contexts. It catches P182's explicit
fgsea/log2-fold-change operation, missed by the original analysis-suffixed
keyword matcher. Every candidate has an explicit source-hash-based disposition.
Providers, versions, ranks, universes and source discrepancies remain distinct.

Only P077/P220 name matching Broad source editions; all author workflows still
have unrecovered inputs/settings. Other rows name clusterProfiler, fgsea,
GSEApy, MAGeCK or an unresolved executor. Broad does not qualify those backends.
P040's 4.3.3 belongs to clusterProfiler. P098's ChEA/GSEApy hypergeometric role
does not identify its ranked RNA executor. P042 is an explicit result statement
with an unresolved invocation. Unknown P032/P089/P079/P094 operations receive
no positive GSEA observation by inference.

The exact P077 supplementary PDF is recovered and pages 14-15 visually
reviewed. Related MAGMA preparation/numerical clusters do not supply GSEA
options. It also reveals additional edgeR/voom roles, retained for a separate
source transition; earlier family observations/credits are not silently changed.
Its private path and integrity identity are:

```text
D:/projects/JLinAlg/build/gsea-author-followup-20261120-1/P077-EPMC-supplements/P077-supplement.pdf
4973589 bytes
SHA-256 b26a29c665422130fac210ef76b767ac9da739a2960f84f035f72c3c21188789
```

All 29 P096 author scripts are downloaded, published-MD5/SHA-256 checked and
retained unchanged under CC BY 4.0 with attribution. They are never executed.
Script 20 identifies classic preranked/meandiv, 1,000 permutations, size 15-200
and timestamp seed. The 2023.1.Hs collection label does not identify an executable
edition or historical seed. No explicit RNK export was identified in the
read-only script audit; Script 18 is a pseudotime workflow, not that exporter.
The actual ranking files and zero-adjusted-P boundary remain prerequisites.
The exact historical P096 analysis remains SKIP3 rather than selecting a native
edition or inventing a seed. Earlier download-open/source-review-open statements
are historical; these narrower unresolved author-execution gaps remain current.

## Exact validation and resume commands

The guarded owner passes plan/inspect/apply/fresh verify. It runs the unchanged
edgeR owner in the clean admitted checkout at
`0de612c6182b87d42a1d574af5f06a99630a0924`, without overlays. All 1,520 old review
objects, all unrelated observations and the other 445 inventory rows remain
exact. The qualified global dispatcher is unchanged; its prior three-line
relation and the new review append are the only metadata/source inverses.
Scientific source is excluded from compatibility inverses.

Twenty-eight source/model/transaction controls pass on Windows/Linux Python.
Six actual CLI refusals preserve canonical bytes/timestamps: double apply, stale
inspect, missing XML, missing exact PDF, incomplete source arguments and existing
output. Rollback controls retain failed new data and preserve concurrent foreign
edits. Desktop/mobile checks inspect 11 provider-sensitive roles and prominent
warnings. All nine preview files are byte identical to the admitted files.

From the qualified current checkout, choose NEW output:

```powershell
python -B tools/literature/close_gsea_catalog.py --verify --out build/NEW-saved-verify
python -B tools/literature/close_gsea_catalog.py --verify --primary-cache D:/projects/JLinAlg/build/literature-review/xml --p077-supplement D:/projects/JLinAlg/build/gsea-author-followup-20261120-1/P077-EPMC-supplements/P077-supplement.pdf --out build/NEW-fresh-verify
python -B tools/literature/verify_broad_gsea_global.py --out build/NEW-global-readback
```

The fresh PDF audit uses **pypdf 6.21.4** for exact page witnesses. Supply/install
that permitted dependency or retain the affected fresh audit as SKIP3. The saved
catalog mode rechecks retained evidence and needs historical Git objects; it does
not re-read XML/PDF or refit scientific models. The native global proof reader
does not invoke Git/Java/Gradle/models. Older owners must run only in their own
exact clean historical checkouts, not against this changed catalog.

Keep `build/gs-6/build/gsea-catalog-1`: `plan-1`, `inspect-1`, `apply-1`,
`fresh-verify-1`, `owner-controls-2`, `cli-controls-1`, `preview-ui-1`, source
audit/draft history and the P077 page audit/renders. The first source-audit
expectation rejected literal journal capitalization variants; raw labels were
preserved and the exact expected frozen variants documented. A subsequent
clarification binds P020's version only to clusterProfiler, not WebGestalt.
Earlier attempts remain retained; no old scientific fingerprint was refreshed.

## Next high-impact work

1. Start the next source/licensing/runtime audit with GCTA and its COJO roles,
   using the current inventory and all-250 primary sources. They occur in 17/9
   tagged papers respectively; deduplicate their paper sets before quoting
   combined coverage. Existing `ConditionalAssociation` is a related component,
   not proof of GCTA/COJO source behavior. Use native Java for tractable methods
   and exact tested upstream adapters where appropriate; do not substitute a
   generic conditional regression for the source algorithm.
2. Follow with high-impact gaps such as limma (16 tagged papers), PEER (15) and
   TwoSampleMR (12), after checking current components and exact source roles.
   Frequency alone does not establish a deployable replacement: higher-count
   Prism/FlowJo/instrument GUIs need supported scientific scope and permitted
   runtimes/terms. Do not count related statistical methods as whole products.
3. Continue the 1,166 pending reviews and paper-wide completeness audit. Add
   missed supplement-derived roles through new guarded transitions, including
   P077 edgeR/voom; do not rewrite prior reviewed objects silently. Keep resource,
   methods, result-only and ambiguous roles separate. GSEA admission is complete
   for this declared source scope, not for the whole toolbox objective.

## Prominent warnings and preservation

**SKIP3:** supply the exact named permitted prerequisite or keep its dependent
analysis skipped when unavailable/installation forbidden. **FAIL2: NO COMPLETE
RESULT:** preserve inputs, all model/null objects, conditions, partials and
diagnostics; resolve the named cause and retry NEW output. Never silently fix
identifiers, alleles, populations, thresholds, nonfinite values, editions or
algorithms. Cell Ranger stays excluded. Previous FIMO nonprofit/research and
GATK 3.8 academic noncommercial decisions remain in force within their scope.

**GitHub does not back up ignored producer JARs, runtimes, full-text caches or
complete private evidence. Transfer those separately before changing machines.**
Preserve all **22 registered worktrees**, especially unrelated dirty historical
work. Do not reset them, clean build trees or apply archived overlays over current
source. New author intakes are also under `build/gsea-author-followup-20261120-1`
and `build/gsea-p096-author-code-20261120-1`. Publication/preservation receipts
are under `build/git-publish/20261120-gsea-catalog-66`.

Two historical full marker-verifier checks remain SKIP3 for their exact old
164,898,617-byte JAR, SHA-256
`545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
The completed external cleanup also left one unlocated exact 193,411,520-byte
producer, SHA-256
`9955f022aa4a418a7d16ba248d2c40e3bf9588f2a3942728b48262065bbc4cb7`.
Supply exact permitted backups for dependent checks or retain their skips;
do not substitute a newer JAR or refresh old hashes. Earlier cleanup/recovery
records and retained exact copies remain linked from the
[preservation record](gsea-workflow-development/PRESERVATION.json).

**Both literature reports remain withheld from Pages.** Their source changes
trigger the existing deployment workflow; verify its actual successful run,
live unchanged home and both report URLs returning 404 after publication.
No release is created and the full goal remains active and unfinished.
