# Handover: admitted toolbox and unfinished GSEA work

**Start here. The full 250-paper implementation and source-review goal remains
unfinished. GSEA has no new admitted integration or review credit.** The last
scientific/catalog checkpoint is main commit
`0de612c6182b87d42a1d574af5f06a99630a0924` (edgeR admission). This handover adds
research/build records and resume instructions. It does not qualify GSEA
statistical execution. Older edgeR registration/admission-open task lists are
superseded by the [admitted edgeR checkpoint](EDGER_CATALOG_CHECKPOINT_2026-11-20.md).

## Current admitted state

| Item | Current value |
| --- | --- |
| Frozen cohort | 250 papers: 71 Nature, 104 Nature Genetics, 75 AJHG |
| Original date window | 2023-10-23 through 2026-10-23; no extension needed |
| Named tool families / effective definitions | 446 / 485 |
| Paper-tool observations | 2,705 |
| Reviewed / awaiting individual review | 1,520 / 1,185 |
| New scoped integrations | 139: 25 native Java + 114 upstream |
| Including three existing adapters | 142 |
| Missing / method-overlap definitions | 259 / 36 |
| Clean catalog manifest entries | 823 |
| edgeR | Admitted; `edger-tools` and `edger-normalize` are registered |
| GSEA | 19 existing observations, zero reviewed GSEA uses; method-overlap |

These are bounded capabilities, not whole-tool, whole-paper or biological
validation. Current source contains commands absent from the released 0.4.0
binary. No release is requested.

## Warnings and required actions

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** If an exact required input, source,
producer, runtime/package or permission is unavailable, report the affected
analysis, exact missing prerequisite and recovery action. Download/private
installation is authorized where the operating environment permits. If it is
forbidden or cannot be installed, retain the skip. Do not substitute another
edition, provider, model or gene-set collection automatically. Cell Ranger
remains excluded by the user's instruction.

**FAIL2: NO COMPLETE RESULT.** Preserve original inputs, model/reference
objects, conditions, diagnostics, partial outputs and all failed/superseded
attempts. Correct the named data/schema/design, numerical/convergence,
integrity/source, capacity or process issue, then use a NEW output. Do not
silently repair identifiers, alleles, population, source wording, versions,
thresholds, nonfinite values or algorithms to obtain a pass.

The two original old-edition edgeR null fits that exhaust 251 iterations
remain refused despite `failed=FALSE` and finite stock tables. Full old
3.32.1/3.36.0 inference providers remain skipped; qualified selected native
normalization kernels do not qualify whole packages. Exact 3.20.1/3.22.3/3.38.4
model profiles remain unsupported. Two historical full marker-verifier mains
still require their exact old producer; see the edgeR checkpoint for its hash.

**GSEA build success is not analysis success.** The only two GSEA CLI probes
used the unsupported `-help` option and exited 1 with an unrecognized-option
exception. They were help probes, not failed scientific analyses. No synthetic
expression/phenotype or preranked statistical run has yet been completed.
The first handover catalog readback also returned FAIL2 because its requested
output was outside the exact owner's build directory. Its logs are retained;
the rerun uses a NEW output inside that directory and passes. No scientific
request or qualified source was changed to obtain the rerun.

**GitHub is not a complete machine backup.** All intended source changes,
compact evidence, resume helpers and preserved historical draft variants are
versioned. Ignored runtimes, complete research evidence, XML/PDF/source caches
and large builds remain local. Transfer these separately if changing machines;
keep the original paths or explicitly adapt new private attempts. Keep all
29 worktrees. Do not reset/clean them or extract historical overlays over main.
SHA-256 establishes integrity, not authentication. Both literature reports
remain withheld from Pages.

## GSEA work completed, with explicit limits

The [handover packet](gsea-handover-20261120/README.md) preserves compact
records and the six original one-shot helper scripts. Full primary paragraphs
remain private. `CANDIDATE-ANCHORS.json` supplies paper identities, paragraph
indices, hashes and literal matching spans without the full paragraphs.

- All 250 frozen XML hashes and DOI/cohort identities were checked during
  intake. Fifty papers have lexical matches: 19 existing GSEA observations and
  31 possible additions requiring semantic review. A match can mean a
  different provider, ssGSEA, ORA, a resource link or a result label. It is not
  evidence that Broad's software was executed.
- Official immutable source tags and archives were retrieved. GSEA 4.4.0 is
  commit `2cfcbd9856cd8e47b39994063ff717708631cfcf`, with 698 source/bundled
  dependency files. Tags 4.3.2 and 4.3.3 both point to
  `e1ae61cd83ddb14878ece6388b9c1da92594a4c1`; the retrieved 4.3.2 archive has
  711 files. A shared source commit does not prove identical vendor binaries,
  bundled JVMs or author execution environments.
- The unchanged original 4.4.0 Gradle build script successfully produced a
  local source JAR using Windows JDK 21 and cached Gradle 9.7.1, offline. The
  explicit release/build properties and external isolated build directory are
  recorded. This is a local source build, not a vendor distribution. All 698
  source files remained exact before/after. The 2,105,155-byte JAR has SHA-256
  `79b374949ceef737fe8a0c5af5fe24049ec36adc021167da1d82bb70e6b23a03`.
- A private module runtime contains that JAR, the original dependency JARs,
  logging configuration and license files. Its profile explicitly says
  `qualified_statistical_execution=false`. No project adapter, global GSEA
  dispatcher or native Broad statistics class has been added.
- No MSigDB collection was downloaded and no registration details were
  supplied. GSEA's source has BSD-style terms; preserve its notices and inspect
  bundled components' own terms. Public source retrieval does not authorize
  bypassing registration/access controls for other downloads.

The fresh handover readback rehashes the 250 XMLs, both source archives,
1,409 source/dependency files, produced JAR and runtime files. It also reads the
saved edgeR global proof, verifies the guarded admitted catalog against fresh
primary sources, and verifies the historical overlay archive. It does not
refit R or run GSEA statistics. See `READBACK.json` and the attached reports.

## Next owner, in priority order

1. **Finish provider-aware GSEA source review.** Start with the 50 candidates
   and [provisional notes](gsea-handover-20261120/SOURCE-NOTES.md). Resolve the
   remaining lexical matches before adding canonical observations/reviews.
   Existing GSEA-labelled roles often use clusterProfiler, fgsea or GSEApy;
   reuse those provider-specific integrations. P077 explicitly names Broad
   GSEA 4.3.2. P220 explicitly names GSEA 4.3.3 on DESeq2 rlog expression
   matrices and always 10,000 permutations. P096's literal `2023.1` does not
   establish a GSEA executable version; inspect its script 20 and supplements.
2. **Run unchanged original software on owned synthetic inputs.** Support
   expression/phenotype and preranked modes. A preranked-only implementation
   does not cover P220. Read the small relevant portions of
   `GseaWrapper.java` and `GseaPrerankedWrapper.java` before launching: the
   wrapper can clean its working directory. Use a dedicated owned cwd/home
   with preserved raw inputs outside it. Use direct headless Java, not the
   Windows vendor batch launcher, which uses asynchronous `start java`.
   Start with the built 4.4 source profile for diagnostics, then qualify the
   requested source editions explicitly. Fixed seed, input units, rank/tie
   policy, class orientation, scoring metric, null type, collection/species,
   size limits and permutation count must all be explicit. Do not silently
   map/collapse/remap symbols or fetch annotations/collections. A vendor
   default is not evidence for an unspecified paper option.
3. **Build exact 4.3.2/4.3.3 source profiles without source patches.** The
   unchanged old build uses Gradle 4.10.2 and configurations incompatible with
   modern Gradle. Use an exact permitted Gradle distribution plus its official
   checksum and a compatible original JDK. A real cached Linux JDK 11 exists
   at the path below; most `unqualified-JDK` search results are test fixtures.
   Keep source-tag aliases, explicit build properties, original dependency
   notices and local-source-build status clear. Do not relabel 4.4 as 4.3.3.
4. **Implement and qualify the missing high-impact workflow.** Prefer native
   Java for manageable primitives and a tested adapter to the unchanged
   upstream Java runtime for substantial GSEA orchestration. Preserve original
   float-backed ranking/null scores and mixed float/double accumulation.
   Independently check source-defined ES, NES, nominal tails, pooled empirical
   FDR and FWER against original API/exported objects; generic BH/BY is not
   Broad's pooled-NES FDR. Validate phenotype and gene-set nulls separately,
   both signs, ties, zero weights, missing null tails, nonfinite inputs and
   class orientation. Do not clamp/floor or introduce pseudocounts silently.
   Include the 10,000-permutation workflow form for P220 without claiming an
   author-data reproduction. Save original conditions, RNG, null vectors,
   source/runtime identity and outputs, and implement strict RUN/VERIFY/REPLAY,
   prerequisite skips, numerical failures and owned process/resource controls.
5. **Only then register and admit.** Qualify a normal source-built project JAR
   on Windows Java 21/25 and Linux Java 21, actual execution and cross-host
   replay, independent arithmetic/source comparisons and refusal controls.
   Add global discovery with an explicit qualified dispatcher transition.
   Preserve the current edgeR checkout as the exact predecessor. Build the
   per-paper scope crosswalk and guarded cumulative plan/inspect/apply/fresh
   verify owner. Do not patch old verifiers, refresh their hashes, or pretend
   an older owner accepts newer metadata. Leave counts unchanged until the
   admission passes. Retain unsupported paper modes/editions as explicit gaps.
6. **Continue broader work after that milestone.** Other frequent analytical
   gaps include GCTA (17 observations), limma (16), PEER (15), TwoSampleMR (12),
   GCTA-COJO and QTLtools (9 each). Counts overlap and are not a complete impact
   ranking. Inspect existing capabilities before implementing. Reuse iDEP
   2.4.4, existing native/common-statistics and provider-specific enrichment
   components. Continue the other 1,185 pending individual uses and paper-wide
   semantic completeness review. Also audit held-open output-size monitoring
   in other long-running adapters; edgeR's separately qualified live-EOF fix
   does not establish which other adapters are affected. Practical native
   edgeR filtering/`sumTechReps` and historical inference gaps remain open.

## Exact resume paths and checks

Active worktree: `D:/projects/JLinAlg/build/gs-1`, branch
`codex/gsea-high-impact-20261120`. Its base is the admitted commit above.
The handover commit is on GitHub main after publication; verify the actual
HEAD with `git ls-remote origin refs/heads/main`. Keep
`D:/projects/JLinAlg/build/edg-3` at the exact admitted edgeR predecessor.
Keep `build/edg-2` at model checkpoint
`33db0648621a359e03fb6da3e048c409b45588c4` and `build/edge-1` at the earlier
native checkpoint. Preserve the dirty root worktree and the other historical
worktrees. Their already-published draft variants are indexed in
[the historical overlay archive](handover-20261119/README.md).

From the GSEA checkout, Python 3.11+ without `-O`:

```text
python -B docs/literature/gsea-handover-20261120/verify.py
python -B docs/literature/gsea-handover-20261120/verify.py --primary-cache D:/projects/JLinAlg/build/literature-review/xml --local-intake build/gsea-high-impact-1
python -B tools/literature/verify_edger_dispatch.py --out build/edger-saved-proof-NEW
```

The packet reader checks saved bytes and optional exact local prerequisites;
it does not execute GSEA, install software or establish computational validity.
Use NEW output names for workflow checks. The guarded cumulative catalog owner
must run in the exact retained edgeR checkout, with its output inside that
checkout's own `build` directory:

```text
cd D:/projects/JLinAlg/build/edg-3
python -B tools/literature/close_edger_catalog.py --verify --primary-cache D:/projects/JLinAlg/build/literature-review/xml --out build/edger-catalog-verify-NEW
```

No reapply of the stale original admission plan is required. Its post-admission
refusal is intentional. Saved native/model/global readers and their exact
producer identities are linked from the admitted edgeR checkpoint.

Private GSEA artifacts under
`D:/projects/JLinAlg/build/gs-1/build/gsea-high-impact-1/`:

| Directory | Purpose |
| --- | --- |
| `source-census-intake-1` | All-250 identity audit and full private candidate paragraphs |
| `upstream-metadata-1` | Original official tag/API responses |
| `source-intake-1/4.4.0` and `/4.3.2` | Unchanged archives, extracted source/dependencies, notices and hashes |
| `original-build-4.4.0-1` | Original build command/logs, source before/after pins, produced JAR/member pins |
| `runtime-source-4.4.0-1` | Private module runtime; not yet statistically qualified |
| `preflight-4.4.0-1` | Both retained unsupported-help probes |
| `handover-readback-1` | Saved proof readbacks and first refused catalog-output attempt |

Successful catalog rerun:
`D:/projects/JLinAlg/build/edg-3/build/gsea-handover-20261120-catalog-2`.
The cached real Linux JDK 11 root is
`D:/projects/JLinAlg/build/literature-review/hail-runtime/reference-setup-130-2/mamba-root/pkgs/https/conda.anaconda.org/conda-forge/linux-64/openjdk-11.0.30-ha668962_0/lib/jvm`.
Use its `/mnt/d/...` path in WSL distro `JLinAlg-Omics-SAIGE-20261026`;
do not modify that protected cache. Recheck it on the receiving host.
Windows JDK 21 is `C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot`.

Local publication receipts, worktree snapshots and artifact hash/stat manifests
are under `D:/projects/JLinAlg/build/git-publish/`. The preceding receipt is
`20261120-edger-admission-60/PUBLISHED.json`; this handover uses
`20261120-gsea-handover-61/`. Transfer the previous receipts/manifests with the
evidence. Inherited SHA evidence is retained with fresh size/mtime checks;
only new artifacts are fully rehashed for the publication preservation pass.
Do not describe that as a fresh full inherited hash audit.

The six saved original helpers are one-shot historical scripts with fixed
absolute paths and `exist_ok=False`. **Read them first; do not run them over
existing outputs.** For a new attempt, copy/adapt paths into a new private
script, preserve the originals, and record all changed commands/properties.
The source census depends on the frozen collector and fixed-cohort verifier
in this repository. A new machine also needs the exact raw XML cache or a
permitted retrieval; hashes alone cannot restore primary text.

If moving machines, transfer all required ignored worktree evidence/builds,
`build/literature-review`, source/runtime caches and publication manifests
separately. Do not redistribute restricted runtimes contrary to their terms.
Verify file identities and prerequisites on receipt before using results.

Official upstream references: [source repository](https://github.com/GSEA-MSigDB/gsea-desktop),
[GSEA license](https://www.gsea-msigdb.org/gsea/gsea_license_terms.jsp),
[GSEA documentation/download site](https://www.gsea-msigdb.org/gsea/).
