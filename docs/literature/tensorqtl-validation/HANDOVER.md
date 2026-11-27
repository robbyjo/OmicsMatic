# Original tensorQTL checkpoint (2026-11-27)

The full 250-paper goal remains active. Current totals: **181 scoped families
(36 native, 145 upstream), 1,858 reviewed uses, 876 pending uses and 247 missing
labels**. The frozen 2023-10-23..2026-10-23 cohort remains 71 Nature, 104 Nature
Genetics and 75 AJHG papers, with 453 labels, 492 provider definitions and
2,734 observations. This checkpoint adds one upstream tensorQTL family and
four primary-source role reviews. Its auxiliary 2022 paper adds zero cohort
papers. These counts do not mean all tools or whole-paper recipes are complete.

Use [tensorqtl-tools](../../tensorqtl-tools.md), the
[prepared example](../../../examples/tensorqtl-tools/request.json),
[paper roles](../tensorqtl-source-audit/ROLES.json),
[qualification](QUALIFICATION.json) and current
[registry](../tensorqtl-admission/REGISTRY.json).

Original 1.0.5/1.0.9 workflows cover prepared cis nominal/permutation mapping,
grouped conditional discovery, supplied-table independent mapping, qvalues,
trans/interactions and modern tensorQTL-hosted SuSiE. Explicit CPU and actual
CUDA runs retain original float32 mapping, original source functions, critical
runtime-file guards, original objects, optimizer/SuSiE returns, R objects,
RNG states, logs and diagnostics. Saved VERIFY runs without Python/R and
does not unpickle objects, refit models or authenticate coordinated replacements.

Validation: 12 Java contract tests; 25 complete direct-original comparisons,
with all 102,217 numeric fields in the typed returns matching exactly and
480 expected undefined beta fields preserved. Numeric fields include shapes
and indexes as well as statistical values. Four complete cases execute on
CUDA; the other 21 execute on CPU. All 25 verify under Windows and Linux.
Six original numerical/error cases admit no result: two failed-minimizer cases,
one nonconverged SuSiE case, two no-discovery conditional errors and the modern
dense trans-interaction error. Missing dependencies/device, source changes,
input order, timeout/output capacity, corrupted/resealed output and existing
output paths are explicitly checked, with replay on both retained producer
contexts and additional current-verifier count/frequency-bound refusals.

**Warnings and required actions:** inspect WARNINGS.md, analysis-status.json
and original worker diagnostics. Require exit 0, results_admitted=true and
fresh --verify. SKIP3 / exit 3 skips the dependent analysis: restore the named
exact permitted prerequisite or retain the skip. FAIL2 / exit 2 admits no
complete result: preserve the capture, fix the named data/design/numerical,
convergence, integrity, process or capacity cause and retry NEW output.

The source 1.0.9 dense trans-interaction branch has a reproduced matrix-shape
error; it is not silently patched or converted to sparse output. The legacy
source names two count series maf: current verification keeps the names and
applies count bounds only to their exact tuple slots. The initial project
audit rejection is retained alongside its correction and a new successful run.
The producer change affects only TensorQtlWorkflowAudit; original worker and
profile bytes are identical. Both producer/source contexts remain retained.

P157's likelihood-ratio wording is unresolved; original coefficient t-tests
are never relabeled. Its cited earlier script uses nominal interaction/eigenMT,
but does not establish the later executed recipe. HLA MHC-center coordinates,
lead selection, imputation QC and multiple-testing denominators remain explicit
external steps. P181 grouping/conditional mapping and tensorQTL SuSiE hosting
remain distinct. P183's named custom programs are missing; its window half-width
and adjustment recipe remain unresolved. P234's current linked CPU environment
is not an established executed revision. Unlicensed author records stay read
only and private. No exact author recipe or whole-paper reproduction is claimed.

Current private producer: fu-1/build/tool-audit-906/project-build-Windows-3/
project-build/cli/omicsmatic-0.4.1.jar. Earlier producer 2, all raw/source/model/
failure evidence, runtimes and worktrees remain retained. QUALIFICATION.json
binds exact bytes and explains the saved-audit correction. Publication, hosted
checks and mandatory cleanup closure are recorded separately in COMPLETED.json.
No release/tag or deployed literature report is authorized by this checkpoint;
the existing Pages exclusion remains in force.

Next: continue the 876 pending source-use reviews. Prioritize feasible high-impact
open integrations such as nf-core (six paper memberships), Azimuth (four),
Hi-C workflows and CellTypist; verify their actual primary-paper roles first.
Keep proprietary instruments/GUI packages distinct from feasible native methods.
Cell Ranger remains excluded by the user. MaxQuant/Andromeda/MaxDIA remain
pending with runtime analyses skipped. Preserve all prior accepted private
academic-runtime terms and do not ask again for the same authorization.
