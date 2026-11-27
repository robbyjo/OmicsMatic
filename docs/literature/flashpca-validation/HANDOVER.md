# FlashPCA milestone and full-goal handover

The original 250-paper implementation goal remains active. The frozen cohort is
2023-10-23 through 2026-10-23: 71 Nature, 104 Nature Genetics and 75 AJHG papers.
Current effective catalog: **178 scoped families = 35 native +143 upstream**,
453 named labels, 492 provider definitions and 2,734 observations. There are
1,848 reviewed uses and **886 pending reviews**. **247 labels remain missing**;
replacing FlashPCA's method-overlap status did not reduce that count.

## Completed implementation

Feature commit `b98587ac0f2b0d63c5ca9a0b41a6127f06ae4e64` is on GitHub main. All 39 feature files
were read from the hosted immutable commit and matched byte for byte.
`flashpca-tools` runs unchanged-source FlashPCA1.2.6 and2.0 with complete Java
BED/BIM/FAM, eigenvector covariance residual, orthogonality, PC scale, SNP/A1
loading, moment and trace/PVE checks. Original score/scaling and six/fifteen-digit
precision differences remain explicit. Original PCA is not replaced by generic
Java PCA. Native residual checks are not a formal global top-k certificate.

Fourteen component/contract tests passed. Seven complete captures and fresh
original replays cover both editions, wide/tall matrices, unequal block tails,
BINOM/BINOM2, covariance divisors, legacy Eigen/HIGH and SVD/LOW modes and forced
transpose. Every case includes all 15,360 synthetic genotypes and all six output
tables; repeated cases are not independent biological populations. Twenty
independent complete R-SVD quantities compare top20 eigenvalues/vectors,
loadings, PC scores and PVE in four default cases. Ten packaged refusal controls
give three SKIP3 and seven FAIL2 outcomes with no result admitted. Whole-paper,
author-patch, ancestry, caller filtering and biobank deployment remain unqualified.

**Warnings/actions:** build current source; no released binary or website
deployment is qualified here. Original runtime execution/replay needs Linux
x86_64 Java; Windows can use WSL, while the native audit also runs on Windows.
Require COMPLETED.json and successful fresh --verify, and read WARNINGS.md.
SKIP3 skips missing/forbidden prerequisites; FAIL2 admits no partial batch. Fix
the named cause and retry NEW paths. Do not refresh old producer/runtime seals.

Both official static Linux downloads crashed before help. The working private
runtimes were built from unchanged tagged C++ with private Eigen3.4, Boost1.83
and Spectra0.4 dependencies. Installed runtimes and libraries remain below
`/opt/omicsmatic-flashpca-20261127-1`, in `v1.2.6-source-built`,
`v2.0-source-built` and `lib`; broken original installations are retained as
failure evidence. Source views are below the private root's `source-views`.
Full Git blob checks matched all 53 legacy and 88 modern source/archive entries.

## Primary paper roles

P023 computes20 rare-variant PCs, separate from16 common PCs supplied by the
data release; random panel membership and MAC>20/MAF<1% preparation are not
inferred. P115 uses20 PCs on337,129 individuals and cites the2014 method;
the exact executed edition/patch remains unknown. P118 uses20 PCs, loadings,
eigenvalues and PVE on337,126 individuals and68,619 LD-pruned common SNPs,
citing the2017 FlashPCA2 method. P240 explicitly reports2.0; its MAF<15%
wording remains unchanged, and KNN/1000Genomes ancestry assignment is separate.
Only P240 establishes a precise version. All four raw automatic version fields
remain null, separate from the manually reviewed primary evidence.

## Evidence and cleanup

Private root: `D:/projects/JLinAlg/build/fu-1/build/tool-audit-902`.
Producing JAR: `project-build-Windows-1/project-build/cli/omicsmatic-0.4.1.jar`.
SHA256: `5d8809f98dff9e1334afa896c83c23f5f259dcc2bef46a8240d4e7cd47a1cf36`. All 966 sealed scientific evidence files remain at
their existing paths, along with original inputs, source archives, dependency
packages, runtime bindings, failures and producer artifacts.

Windows cleanup removed 12,418 compiler/cache files,
426,543,353 logical bytes; observed D free change over
its archive/deletion interval was 109,330,432
bytes (0.102GiB), overlapping Linux archive writes.
The separate Linux compiler SDK cleanup removed 16,324
regular files and8 links, 276,585,623 logical bytes;
Linux filesystem free change was 320,491,520 bytes
(0.298GiB). No VHDX compaction occurred.
These are different, nonadditive filesystem measurements and can include
concurrent disk activity. Working runtime inventory nodes were excluded from
removal. The final JAR, source views, original inputs, scientific outputs,
development producers and failures remain intact.

Exact recovery archives:

- `cleanup-plan-Windows-1/compiler-recovery.zip`, SHA256 `498281289e864b8d87331a9e5cd22cd99992a52f21bced88f23a06a8b05b72a3`.
- `cleanup-plan-Linux-2/compiler-recovery.zip`, SHA256 `b3284550269a6941837b0b4f23c8114abc467654d9adf1be61bb5e4e148ff7cb`.

The initial Linux cleanup preflight stopped before removal because native Linux
Git could not resolve the Windows linked-worktree pointer. Its failure is kept;
the corrected check uses Windows Git without altering worktree metadata.
Complete private/public qualification, all 250 source/canonical catalog checks,
and a fresh original uneven-block PCA replay passed after both compiler cleanups.
All 18 foreign worktrees and 51 tracked foreign edits match the initial snapshot.
No worktrees, refs or unrelated processes were removed or stopped.

## Next work

First correct the confirmed SVA false positive in P206 using an auditable exact
catalog transition. The full 250 candidate census in `triage-1/sva-private.json`
shows actual software uses in P108 (ComBat/sva3.46.0), P176 (software paragraph28,
separate from retrotransposon mentions) and P185 (sva3.46.0; no significant SVs).
P206 is a retrotransposon annotation. P003/P033 are also repeat-element contexts,
and P234 mentions SVA as background while actually using PEER. Preserve raw
extraction history and avoid inventing additional software use. Then qualify
the remaining actual package functions; existing generic SVA overlaps alone
do not prove source equivalence.

Continue high-impact open omics/GWAS families. Candidate tensorQTL has four
papers and14 primary contexts (`triage-1/tensorqtl-private.json`), including
trans/interaction roles, grouped cis/permutation/independent signals, SuSiE,
and nominal statistics. Preserve mode/version distinctions and examine author
code before interpreting a claimed likelihood-ratio test as an upstream t test.
Other four-paper candidates include Azimuth, Juicer, HiC-Pro and pbmm2; decompose
nf-core into actual pipelines. Do not substitute vendor frequency for feasible
source qualification. Cell Ranger remains excluded; MaxQuant/Andromeda/MaxDIA
remain pending with runtime analyses skipped. Retain previous academic-license
approvals and private-runtime restrictions.

Use `docs/literature/flashpca-catalog-admission/REGISTRY.json` as the effective
registry; prior checkpoints are historical. Keep this own checkout/branch and
all persistent refs. Main pushes remain authorized; no release/tag is requested.
Validate, publish, check hosted bytes and remove verified task-owned stale
compiler files before finishing each next feature. The whole goal remains open.
