# WGCNA checkpoint — 2026-11-27

The full 250-paper goal remains **open**. This checkpoint admits **one scoped
WGCNA family and five primary-source usage reviews**: **182 families
(36 native, 146 upstream), 1,869 reviewed uses, 865 pending uses and 247 missing
labels**. The frozen cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG
papers, with 453 named labels, 492 provider definitions and 2,734 observations.
Native components do not earn duplicate family credit.

Use [wgcna-tools](../../wgcna-tools.md), the
[native example](../../../examples/wgcna-tools/request.json),
[qualification](QUALIFICATION.json), [primary roles](../wgcna-source-audit/ROLES.json)
and [current registry](../wgcna-admission/REGISTRY.json).

Native Java now implements scoped WGCNA1.72-1 `rankPvalue` p-values and
`intramodularConnectivity`, preserving deterministic tie/NA/weight rules,
the separate rank/scaled-score extreme tails, column sums, small modules,
zero maxima and original missing results. Native rank tests explicitly use
`calculateQvalue=FALSE`. They never substitute BH for original qvalue.

The typed original-R adapter runs WGCNA1.72-1 on an explicit private R4.2.3
Linux profile: complete single-block network fitting, original TOMs/objects/RNG,
module labels/eigengenes, adjacency, Pearson/bicor signedKME, connectivity and
rankPvalue with original qvalue calls. It checks 16 package versions, 214 files
in five core packages and eight source-matched bodies/formals before and after.
It does not attest every dependency, host or BLAS file. The legacy `network`
adapter remains available.

Validation includes 23 passing unit/contract tests, 22 complete direct-original
component comparisons on Windows and 23 on Linux, and an original network control
using **all 135 supplied mouse-liver samples and all 3,600 supplied probes**.
No sample or probe was removed to meet capacity; the tutorial's optional outlier
removal was not applied. All four network export families exactly match the
direct original control. Native full-node connectivity differs by at most
4.69e-13; full Pearson and bicor membership controls differ by at most1.11e-15.
There are 12 R-free saved verifications across Windows/Linux and eight explicit
prerequisite, source-integrity, time/capacity and original-qvalue refusals.
All 21,392 old classes other than the global command router remain byte-identical.

**Warnings and required actions:** require exit0, `results_admitted:true` and
fresh `--verify`. SKIP3 skips dependent analysis: restore the named exact
permitted prerequisite or retain the skip. FAIL2 admits no complete result:
retain original inputs/objects/partials and logs, correct the named cause and
retry **new** output. Read `WARNINGS.md`, `analysis-status.json`, worker reports
and original console/warnings before downstream use. The original qvalue
`pi0 <= 0` error remains an error. Changing the p-value family, estimator or
disabling q-values creates a different analysis and is never an automatic fix.

Keep these source roles distinct:

- P059: 534 TF markers, subclass-average expression, power6/signed TOM/
  detectCutHeight0.998, followed by filtering and manual module edits.
- P062: the actual named host is **PyWGCNA2.2.1**. This R integration does not
  qualify its Python workflow.
- P089: **rankPvalue1.72-1**, three rankings over 19,676 gene pairs; network
  fitting, pair construction and mutation annotation are different operations.
- P108: sample-connectivity QC below -3; the full construction/normalization
  recipe remains unreported here and is not inferred from gene connectivity.
- P182: a referenced **robust rWGCNA** recipe with power7 plus named signedKME
  and intramodularConnectivity. Ordinary bicor fitting does not establish that
  complete robust workflow.

The expanded 250-source census also retains distinct hdWGCNA/PyWGCNA contexts,
including P085 and P157; the latter's literal PyWGCNA1.20.3 version is not
silently normalized. No complete author recipe or paper replication is claimed.

Retain failed trials. Initial runtime solving failed on an unquoted TOML key;
archived annotation downloads returned403/404. The final isolated runtime reused
byte-verified compatible annotation packages from retained private runtimes and
compiled official WGCNA source. One initial unit check caught the warning-heading
format. Two development captures failed with `IOException: No such file or
directory` during monitoring while guard temporary files were created/deleted
inside the capture; no exact stack was available for those historical failures.
The final worker places guard temporary files in R's temporary directory and
retains full Java failure stacks/execution diagnostics. Fresh complete controls
pass; the failed roots remain failed and are not retroactively admitted.

Private evidence: `build/fu-1/build/tool-audit-911`. Keep all four producing
development JARs, direct originals, full inputs, source archives, raw traces,
failed captures, source/function guards and private runtime
`/opt/omicsmatic-wgcna1721-r423-20261127-2`. Keep prior B907/B909/B910 producer
JARs and their scientific evidence. Source/data/runtimes/raw traces remain
private; only own code, synthetic fixtures and provenance are published.
Compiler/cache cleanup and its exact recovery/preservation receipts are recorded
after feature publication; scientific/runtime directories are retained.

Resume the whole goal by ranking actual `tool-inventory.json`
`implementation_status` and reviewed source roles. Several legacy registry
`current_coverage` fields remain stale, so they must not drive duplicate work.
Prioritize genuine gaps such as remaining VEP/ANNOVAR workflows or sufficiently
specified high-impact GraphPad analyses. Generic overlap does not qualify a
vendor suite. Cell Ranger stays excluded; MaxQuant runtime stays skipped/pending.
Retain source-unreported settings, exact-version gaps, author recipes and all
remaining semantic reviews. No release or literature Pages deployment is claimed.
