# Historical version1: superseded

Use [the current revision2 handover](../combat346-revision2-validation/HANDOVER.md). This earlier producer/qualification is retained unchanged in its exact source context; the notes below are historical.

---

# Native ComBat milestone and full-goal handover

The full 250-paper implementation goal remains active. The effective catalog is
**179 scoped families =36 native +143 upstream**, 453 named labels, 492 provider
definitions and 2,734 observations. There are **1,854 reviewed uses, 880 pending
reviews and 247 missing labels**. The frozen 2023-10-23..2026-10-23 cohort remains
71 Nature, 104 Nature Genetics and 75 AJHG papers. The effective registry is
`docs/literature/combat346-catalog-admission/REGISTRY.json`.

## Implemented and validated

The global `combat-native` command and `ComBat.adjustSva346` implement continuous
ComBat using source-specific sva 3.46.0 rules. This adds one native family and
zero source reviews. P108's ComBat and sva labels identify the same operation;
the provider label adds no extra family. Both source-review identities and
primary facts remain unchanged; their current implementation notes are updated.

The command supports parametric/nonparametric, mean-only, reference-batch,
singleton and explicit keep-design settings. It captures literal complete inputs,
preserves missing cells and unadjusted numeric values, distinguishes unestimated
posteriors, records exact defining code/profile and freshly recomputes every
result file. The legacy `ComBat.adjust`/`batch-adjust` conventions are preserved.
No R runtime is needed for the native command. See [input contract and commands](../../combat-native.md).

All 21 unit/contract regressions pass. Fourteen complete finite source-R cases
compare **25,703 finite adjusted/posterior values per host**, plus exact missing
and unestimated masks. Windows and Linux packaged Java21 runs each capture all
14 cases and freshly recompute every result file. A Linux recomputation of a
Windows capture also passes: 28 complete native captures and 29 fresh checks.
Each host also records three SKIP3 and eight FAIL2 outcomes. The 79 packaged
outcomes include repeated controls and do not represent independent biology.

Original reference calculations use unchanged ComBat/helper R files from the
sva 3.46.0 archive with R4.6.1, BiocParallel1.46.0 and matrixStats1.5.0. A read-only
exit observer retains the exact original statistical AST/formals and requires
bitwise-equal complete stock/observed returns. The full sva namespace is not
installed/qualified by that source-subset reference. Source and raw traces stay
private. Java/R numerical agreement is not bitwise R/libm identity or proof for
every conditioning boundary, combination, host or author stack.

## Prominent warnings and user actions

**Require exit0, admitted analysis status, WARNINGS.md and fresh --verify.**
SKIP3 skips missing/forbidden prerequisites and unsupported requested models.
Restore/select the named permitted prerequisite/profile and retry NEW paths.
FAIL2 admits no partial result: retain evidence, fix the named input/design,
numerical, convergence, integrity, cancellation or capacity cause and retry NEW
paths. Original likelihood underflow produces 28,800 undefined observed values
in the explicit failure control; the native command preserves and refuses them.
It never switches to the legacy stabilized calculation to conceal that outcome.

**ComBat_seq and longitudinal ComBat remain separate pending models.** P086's
function attribution/input-unit discrepancy needs the actual author input and
command. Do not round or invert transformed data or route it to continuous
ComBat. P128 needs subject-aware longitudinal harmonization. P185 does not specify
its SVA function/count estimator. The native generic SVA approximation is not
certified as the original R procedure. Exact author flags/data and whole-paper
reproduction remain unqualified. No release or literature Pages deployment is
part of this milestone; build current source.

## Retained evidence and next work

Private task root: `D:/projects/JLinAlg/build/fu-1/build/tool-audit-904`.
Final producing JAR: `project-build-Windows-3/project-build/cli/omicsmatic-0.4.1.jar`.
SHA256: `b1e773de2e5975c6191b375b8081955d62d3a99c7a7046949a0f533810ef17dc`.
The qualification binds 2,464 private scientific/source/test/failure files at
their existing paths. Original source inputs, failed references, earlier JARs,
snapshots, reports, runtime-library inventories and all previous task evidence
remain retained. The first failed assertion was replaced with a genuine targeted
signed-stopping control; the earlier evidence and fingerprints were preserved.
The used first build controller was recovered by reversing the recorded edit
and verifying its exact saved SHA256, without refreshing the old fingerprint.

The two documented example outputs are additionally retained at
`build/combat-native-example-NEW` and `build/combat-native-example-verified-NEW`.
The completion receipt records publication, final preservation and compiler
cleanup. Those result paths and all qualified evidence must survive cleanup.

Next, qualify original sva functions for `svaseq`/SVA/factor-count screening,
then count-specific ComBat_seq and the separate longitudinal model. Full SVA
was absent in the inspected runtime libraries; genefilter was also absent, and
the inspected Linux R stack additionally lacked mgcv. Installation remains
authorized; these observations are not an installation prohibition. Use a
private compatible runtime with pinned original functions/dependencies. Do not
substitute the older fixture generator's cumulative-monotone/KDE approximations
for a full original namespace, infer P185's entry point or execute/redistribute
P176's unlicensed author script. The linked author script was inspected read only.

Continue the 880 pending reviews and high-impact missing integrations. Retain
the source distinctions established in
`docs/literature/sva-combat-source-audit/HANDOVER.md`, including longCombat's
DESCRIPTION/MIT versus LICENSE.txt/Artistic-2.0 discrepancy. Keep all foreign
worktrees/edits, original scientific inputs, exact producers, runtimes and refs.
