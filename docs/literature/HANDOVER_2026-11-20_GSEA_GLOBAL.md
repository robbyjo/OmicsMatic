# Toolbox handover: global GSEA command and unfinished catalog work

**Start here for the next handoff. The full 250-paper implementation/review goal
is unfinished. Native GSEA is implemented; its catalog admission is still open.**

This checkpoint follows main `8d09589e78ff758cd7b648a92357b8cebcd7748e` and is
developed in `D:/projects/JLinAlg/build/gs-5`, branch
`codex/gsea-admission-20261120`. Find its published commit in Git history and
the local `build/git-publish/20261120-gsea-global-handover-65/PUBLISHED.json`.
Start subsequent development from latest main in an isolated checkout. Preserve
this qualified checkout, earlier worktrees and every failed/superseded attempt.

## Confirmed project status

The canonical catalog remains **250 papers, 2,705 tool-use observations, 1,520
reviewed uses, 1,185 pending and 139 scoped integrations: 25 native Java plus
114 upstream**. Including three pre-existing integrations gives 142. There are
446 named/485 effective definitions, 259 missing and 36 method overlaps; the
manifest still has 823 entries. GSEA has 19 existing observations and remains a
method overlap in that frozen catalog. **No GSEA integration/review credit is
added by this checkpoint.**

The frozen collection covers 2023-10-23 through 2026-10-23: 71 Nature, 104 Nature
Genetics and 75 AJHG papers. No time extension was needed. Continue this
collection rather than silently moving its dates to a new rolling window.

The native Broad GSEA engine and RUN/VERIFY/REPLAY file workflow were already
published and qualified. This checkpoint adds the normal global entry point:

```powershell
java -jar PATH/omicsmatic-0.4.0.jar broad-gsea-tools --help
java -jar PATH/omicsmatic-0.4.0.jar broad-gsea-tools --request NEW-WORK/run.json
```

Read [the global command guide](../vignettes/broad-gsea-tools.md), the
[frozen detailed workflow guide](../vignettes/gsea-native-workflow.md) and
[the prior scientific checkpoint](GSEA_WORKFLOW_CHECKPOINT_2026-11-20.md).
The latter two accurately describe their standalone-only historical producer;
the global route is new here. Native execution requires no external GSEA runtime.
Prepared inputs, literal identifiers, explicit source profile/settings, full
null families, owned-worker limits and strict no-result boundaries are unchanged.

Only three additive Java source lines change: command routing and its help
example. The normal build passes eight focused JUnit tests. The new JAR has
23,522 members; **only `org/jlinalg/cli/JLinAlgCli.class` changes** and all 23,521
other payloads are identical to the qualified predecessor. Seven strict inverse
controls refuse missing, duplicate, moved or unrelated dispatcher changes.
The [global proof packet](gsea-global-development/README.md) records actual global
RUN/VERIFY/REPLAY, 668,739 original counterpart fields on each of Windows Java
21/25 and Linux Java 21, plus VERIFY/REPLAY of the predecessor's saved captures.
The scientific model classes and old source guards have not been edited.

Exact new normal producer:

```text
D:/projects/JLinAlg/build/gs-5/build/gsea-admission-1/global-gradle-1/project-build/cli/omicsmatic-0.4.0.jar
195901211 bytes
SHA-256 bdc1e71bd7a53e0d727deb105c5c97baaaf8cc6627275bf14aee9584816c0dff
```

The first route harness expected SKIP3 for missing command syntax; the CLI
correctly returned FAIL2. That attempt is retained. The corrected harness uses
new output and changes no application code, numerical policy or fingerprints.
Do not count inherited refusal controls as newly rerun global controls: the
34-per-JVM refusal/cross-host suite belongs to the frozen preceding workflow;
this checkpoint separately re-reads its proof and exercises the new route.

## Exact next tasks, in priority order

1. **Finish the GSEA source-role crosswalk.** There are 19 existing observations
   and 31 additional lexical candidates. All 50 contexts have been read, with
   selected full-method provider follow-up; the latest findings are in
   [source-audit resume notes](gsea-global-development/SOURCE-AUDIT-RESUME.md).
   Those notes are provisional and do not constitute canonical review admission.
   Keep Broad, clusterProfiler, fgsea, GSEApy, MAGeCK, ssGSEA, ORA, MAGMA and
   resource-only uses distinct. A Broad/MSigDB URL or a package's capabilities
   does not identify the algorithm actually used. P032/P089 remain ambiguous;
   P094 needs Supplementary Note 4. Do not turn all 50 matches into Broad uses.
2. **Resolve the narrow author-source gaps.** P096 author Script 20 is recovered
   and checksum-verified from Zenodo 13321265. It establishes preranked classic
   scoring, meandiv, 1,000 permutations, size 15-200 and a timestamp seed, but
   no exact executable edition. The paper's `2023.1` label is not an executable
   version. Fetch/read Script 18 for the actual RNK-generation formula; Script
   19 is a different hypergeometric analysis. Keep the zero-adjusted-P boundary
   explicit. P077's exact supplementary PDF download failed DNS before intake;
   it is not saved or audited. Retry the exact permitted publisher/Europe PMC
   source into NEW output. Do not substitute a same-title preprint for P220's
   frozen final PMC12696503 article.
3. **Implement guarded GSEA catalog admission.** No `gsea_catalog` owner,
   source-role coverage, plan/apply transaction or canonical metadata edits have
   been created yet. Chain the current admitted edgeR owner in immutable
   `build/edg-3` at `0de612c6182b87d42a1d574af5f06a99630a0924`; use its exact
   after-catalog baseline, not its pre-edgeR baseline. Preserve all 1,520 earlier
   review objects, every unrelated tool use and all 15 canonical file identities
   until a reviewed new transition applies. Use
   `tools/literature/gsea_dispatch_transition.py` to prove this checkpoint's
   exact three-line Root-dispatch change. Never run an old source guard against
   changed metadata and then refresh old fingerprints to make it pass.
4. **Validate admission before credit.** Verify all 250 exact primary sources,
   source-role witnesses, old-review preservation, scope/provider distinctions,
   plan/inspect/apply/fresh verify, stale/double/concurrent-edit refusals and
   the manifest. Derive all totals from admitted roles; provisional candidate
   counts are not totals. If generated site files change, verify the actual
   successful Pages deployment and live home, with both literature reports 404.
5. **Continue higher-impact missing scopes and pending reviews.** Use current
   inventory and paper frequency to choose work after GSEA. Native Java is
   preferred for tractable methods; tested upstream adapters are authorized
   where appropriate. Native t-SNE already exists; inspect current capabilities
   before starting duplicates. The full goal has 1,185 pending reviews and
   substantial missing scopes; do not label this handoff complete.

## Resume paths and copyable checks

The new active evidence root is
`D:/projects/JLinAlg/build/gs-5/build/gsea-admission-1`:

- `global-gradle-1`: normal build, eight tests, exact new producer.
- `global-hosts-qualification-2`: final three-JVM global comparisons and
  predecessor-capture VERIFY/REPLAY. `global-hosts-qualification-1` is the
  preserved harness expectation failure, not an admitted pass.
- `global-transition-controls-2`: exact inverse and seven refusals.
- `source-contexts-1`: private full paragraphs for all 50 candidates.
- `provider-contexts-1`: selected full-method provider evidence and supplement
  links. Neither private full-text export is a redistributable public catalog.
- `author-sources-1`: Zenodo metadata, two author scripts, HTTP receipts,
  published MD5 checks and SHA-256 identities. Scripts were read, never executed.
- `dispatcher-edit-1`: exact before/after dispatcher bytes and three-line edit.

The original all-250 lexical intake is
`build/gs-1/build/gsea-high-impact-1/source-census-intake-1/CANDIDATES.json`;
exact raw primary XML remains `build/literature-review/xml`. The matcher misses
pure `fgsea`/`GSEApy` names: read full methods/provider sentences rather than
reusing only lexical anchors. Frozen public DOI/PMCID/paragraph witnesses are
in `docs/literature/gsea-handover-20261120/CANDIDATE-ANCHORS.json`.

Run from the new checkout, choosing output directories that do not exist:

```powershell
python -B tools/literature/verify_broad_gsea_global.py --out build/NEW-global-readback
python -B tools/literature/verify_broad_gsea_workflow.py --out build/NEW-workflow-readback
```

Run the unchanged current catalog owner in its immutable checkout:

```powershell
Set-Location D:/projects/JLinAlg/build/edg-3
python -B tools/literature/close_edger_catalog.py --verify --primary-cache D:/projects/JLinAlg/build/literature-review/xml --out build/NEW-catalog-readback
```

Use the current global reader with `--jar EXACT-NEW-JAR` for optional exact
producer validation; the older workflow reader's optional `--jar` expects its
own older producer. The global packet's source guard freezes this checkpoint.
Make future changes through explicit new compatibility transitions.

## Prominent warnings and transfer actions

**GitHub is not a backup of ignored files. Before switching machines, separately
transfer the producer JARs, private evidence roots, exact XML/source caches,
needed permitted third-party runtimes and publication/recovery records.** Public
source, handover and compact proof packets are committed; full ignored trees
are preserved locally. Check redistribution rights before copying third-party
archives. Keep all **21 current registered worktrees**; preserve unrelated dirty
work at the historical root. Do not reset it or apply old saved overlays over
current code. Publication snapshots verify it unchanged.

The completed external cleanup retired 12 older worktrees with six verified
recovery archives. Its index is `build/cleanup-20261120.json`; detailed recovery
and artifact transitions are in the prior
`build/git-publish/20261120-gsea-files-64` receipt. Do not recreate retired stale
trees. The old 193,411,520-byte producer with SHA-256
`9955f022aa4a418a7d16ba248d2c40e3bf9588f2a3942728b48262065bbc4cb7` remains
unlocated in the previous regular-workspace JAR search. **Any check specifically
requiring that producer remains SKIP3 until an exact permitted backup is
provided.** Do not substitute the current JAR. Other retained exact producer
copies are listed in
[the preservation record](gsea-workflow-development/PRESERVATION.json).

**SKIP3:** install/supply the exact named permitted prerequisite, or retain the
affected analysis as skipped. **FAIL2: NO COMPLETE RESULT:** retain all raw
inputs, partial outputs, model/null families and diagnostics; correct the
reported syntax/data/design/integrity/numerical/capacity/process cause and retry
NEW output. Do not silently repair identifiers, replace nonfinite values,
change editions or swap algorithms. A killed supervising JVM without final
completion status is not a complete result.

Cell Ranger stays excluded at the user's request. Prior FIMO nonprofit/research
and GATK 3.8 academic noncommercial decisions remain in force within their stated
scope. Downloads/private installs are authorized where the environment permits;
installation restrictions must produce a clear skipped-analysis warning and
recovery action. No new terms are accepted by this handoff.

**Both literature reports remain withheld from Pages.** No site files change,
no release is created and no new Pages deployment is claimed here. The full goal
remains active and unfinished; this handover records a reviewable checkpoint.
