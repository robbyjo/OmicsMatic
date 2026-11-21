# GCTA 1.94.1 mtCOJO prepared-data execution qualification

**The prepared-data mtCOJO execution paths now pass the additional controls
listed below. Global command registration, GCTA/COJO catalog admission, exact
older-edition scopes and the full 250-paper implementation/review goal remain
unfinished.** No catalog credit is added: totals stay **140 scoped integrations
(26 native +114 upstream), 1,548 reviewed uses and 1,166 pending observations**.
The 250-paper cohort and its 2023-10-23..2026-10-23 interval are unchanged.

## What this checkpoint establishes

The exact normal JAR from main `46611ae55e9b0778df6a117fd8be76ae468c27b5`
completes these actual requests on Windows Java21/25 and Linux Java21:

| Per JVM | Actual requests and outcome |
| --- | --- |
| 6 completed | Supplied zero; unequal prevalences; swapped prevalences; supplied effects despite a low-IV threshold; single-covariate GSMR with gzip traits/LD; multiple supplied effects with gzip traits/LD |
| 10 FAIL2 refusals | Single/multiple low-IV GSMR; supplied-100 sentinel; unsupported fractional LD-FDR; corrupt gzip CRC; unterminated gzip final record; overlong gzip record; duplicate LD ID; actual original timeout; actual output-cap exhaustion |
| 3 SKIP3 controls | Deliberately absent runtime, wrong runtime identity, and missing required LD input |

All **57 requests** return the required status. The 18 completed tables match
all original source table tokens, each with 10,000 rows, and pass separate-JVM
VERIFY. All 33 incomplete bundles refuse VERIFY; six pre-validation refusals
create no bundle and retain the external request/diagnostics. Available inputs,
partial/null/model files, original logs and NaN tables remain saved. Ten owned
PIDs, including observed descendants, are independently confirmed terminal
after the six timeout/output-cap controls. No unrelated process is stopped.

Six actual insufficient-instrument GSMR runs exit zero in the original tool,
write nonfinite conditional tables and are refused by the adapter. A successful
process exit does not establish a scientific result. Source warnings and the
failed rg-based fallback are retained, not replaced with a different estimator.

Actual Windows-to-Linux and Linux-to-Windows REPLAY completes both gzip models
with explicit `allow_runtime_flavor_change:true`, the corresponding exact
runtime, byte-identical captured inputs and separate-JVM VERIFY. Tables match
the target host's original-tool reference. Each host also refuses absent flavor
consent, same/nested/dot-dot destinations, alias destinations into the parent,
and RUN destinations nested through an alias; missing replay runtime returns
SKIP3. All **18 replay/control requests** pass. Every file in all four parents
has the same SHA-256 before and after; parents are never edited.

The new [runnable examples](../../examples/mtcojo/README.md) provide a native
prepared-parameter Java calculation and a portable original-runtime request
builder with synthetic inputs. Native execution passes on three JVMs against
12 independent analytic/scalar/`erfc` fields per host. The builder, actual
original analysis and VERIFY pass on Windows/Linux from extracted paths with
spaces. Both 10,000-row tables match original outputs; existing request/output
refusals retain the previous bytes. Compilation targets Java17 with
`-Xlint:all -Werror` using Windows JDK21. Independent Linux compilation remains
**SKIP3: Linux javac is absent**; transferred classes were executed there.

Production Java sources and the normal JAR are unchanged by this checkpoint.
The [previous development qualification](HANDOVER_2026-11-20_MTCOJO.md)'s 24
packaged tests, 300,000 native conditional-field comparisons per JVM and 56
earlier capture checks per JVM belong to that same exact producer and remain
retained. They were not reinterpreted as new independent model fits or rerun
merely for a new documentation commit.

## Warnings and required actions

**No liability conversion:** the source profile retains prevalence metadata
but never consumes it downstream in mtCOJO. Unequal/swapped declarations leave
the source tables unchanged. Use `effect_scale:"as_supplied"`. Resolve P090's
reported **1.92.4** edition before claiming its prevalence-dependent role.

**No silent option or estimator changes:** the genuine LD-FDR default 0.05
requires omission of the upstream flag; explicit fractions are integer-parsed.
Only that default or explicit 0/1 is supported. Supplied `abs(bxy)>=100` invokes
GSMR in the source and is refused here; supplied zero remains legitimate.
Preserve literal names, alleles, populations, signed LD and valid unconstrained
estimates. Do not clamp, impute or replace failed estimates.

**SKIP3 means dependent analysis skipped.** Supply the exact permitted runtime/
input named in the warning, or retain the skip where installation is forbidden.
The missing/wrong prerequisites above are deliberate controls; the exact
Windows/Linux 1.94.1 runtimes used by successful runs are available privately.
Unimplemented work is not a fabricated prerequisite skip.

**FAIL2 means NO COMPLETE RESULT.** Preserve the request, raw inputs,
`WARNINGS.md`, status, manifest, all available original model/null/partial
exports and logs. Fix the stated source/data/numeric/convergence/integrity/
capacity/process cause and retry into a **new directory**. Read warnings before
using results. Offline VERIFY checks saved integrity and declared invariants;
it does not independently refit the original model, authenticate coordinated
replacement or establish biological validity. Limits are monitored/logical
bounds, not OS memory/file quotas.

The native conditional API needs **already fitted** causal effects, overlap
intercepts, covariate genetic variances and genetic correlations. It does not
fit GSMR, LDSC or prevalence transformations. Its correlation matrix uses unit
diagonal; the source's printed `rg:` diagonal instead contains h2. The source
point-equation does not add causal-effect estimation uncertainty. See the
example before constructing parameters or interpreting a result.

## Remaining work in priority order

1. Register `gcta-tools` globally with exact dispatcher/help/metadata
   compatibility evidence. Preserve prior source-bound routes: the existing
   Broad GSEA dispatcher inverse only permits its own three additive lines, so
   a new GCTA transition must be explicitly composed, not accepted by refreshing
   historical hashes. Keep exact old reader owners available.
2. Complete source-role/cumulative GCTA/COJO admission, preserving all 1,548
   existing reviews. Address P112's missed full-name mtCOJO role and P127's
   comparison-only disposition with exact-before preservation. The existing
   17 GCTA and nine COJO observations cover 18 distinct papers, not 26 tools.
3. Resolve exact 1.92.4/1.93.2/1.93.0 editions, P140's 1.91.7beta/fastGWA
   statement, P171 wheat ploidy preparation and P248's formula/threshold.
   Do not replace a missing edition with a neighboring beta or 1.94.1.
   See the [source census](gcta-development/SOURCE-NOTES.md).
4. Continue high-impact missing scopes, including limma (16 observations),
   PEER (15) and TwoSampleMR (12), plus all 1,166 pending individual observations.
   Counts and the canonical 826-entry literature manifest remain unchanged here.

Two initial development-helper issues are preserved: a missing new-worktree
`build` parent prevented execution before any analysis ran; a packaging attempt
tried to stat a retained Linux alias on Windows before excluding it. New helpers
correct those harness issues while retaining their original code/receipts.
Six first-harness expected-substring mismatches are also retained: actual gzip
refusals reported the correct, more specific source causes. A separate readback
audits those exact causes; no original or adapter behavior was changed.

## Exact owners and transfer

Current checkout: `D:/projects/JLinAlg/build/gc-6`, branch
`codex/gcta-mtcojo-qualification-20261120`, based on main `46611ae5`.
Private stage: `build/mtcojo-qualification-1` beneath that checkout:
`execution-paths-1`, `execution-audit-1`, `replay-controls-1`, `examples-1` and
the preserved packaging failure. The new [saved-evidence reader](mtcojo-qualification/README.md)
checks the public archive without executing a model.

The exact normal producer remains in the **previous** checkout:
`D:/projects/JLinAlg/build/gc-5/build/gcta-mtcojo-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar`,
196,022,241 bytes, SHA-256
`3de49eb979a01116320a9ebc0d2c76174ae058cad8e45c68814dedce1c2044e2`.
Original source/runtime paths and EXE/ELF/DLL hashes are retained in the previous
handover and the example. Read the official v1.94.1 GPLv3 license; no runtime
executable or product JAR is redistributed with these examples/evidence.

**Before changing machines, separately transfer ignored exact runtimes,
producer JARs, complete private evidence/recovery stores, primary full text and
all 28 worktrees with checksums. GitHub is not the complete workstation backup.**
Preserve all dirty historical overlays and exact reader owners; do not reset,
clean or apply old overlays over main. Both literature reports remain withheld
from Pages. No release, original-input change, retirement, whole-tool/paper
qualification or full-goal completion is claimed.
