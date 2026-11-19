# Scoped PLINK-relatedness workflow checkpoint

The subsequent [96-role source census](PLINK_SOURCE_CENSUS_2026-11-19.md)
supersedes the pending 92-observation intake task below. Its source-only
proposal is tested; cumulative catalog admission still remains unfinished.

**Original PLINK-hosted relatedness workflows and their declared Java checks
are qualified on synthetic inputs. Full tool/paper parity, paper-role review
and catalog admission are not complete. New family credit: zero.** Canonical
totals remain 250 papers, 2,696 observations, 131 new scoped integrations
(24 native + 107 upstream), 1,339 reviewed positive uses and 1,357 pending.

Start with the [guide](../vignettes/plink-relatedness-workflows.md),
[example](../../examples/plink-relatedness/README.md),
[proof](plink-relatedness-development/QUALIFICATION.json) and
[readback/build instructions](plink-relatedness-development/README.md).
The [earlier handover](HANDOVER_2026-11-19_PLINK_RELATEDNESS.md) retains the
source-intake notes, local paths, user decisions and old development evidence.
Its pilot-only qualification warning is superseded for the scope below.

| Qualified scope | Explicit limits |
|---|---|
| PLINK 2 KING count/proportion tables; same-FID or coefficient filtering | Native between-family estimator for all returned pairs; no pedigree estimator or standalone KING equivalence |
| Triangular float64/float32 KING matrices | Exact input axes, diagonal excluded, native numerical bits; nonfinite output rejected |
| PLINK 2 cutoff selection | Complete original keep/exclude partition, optional native audit of all retained pairs; no maximum-set optimality claim |
| PLINK 1.9 full GENOME reports, bounded/unbounded/nudge/filter variants | Exact source runtime; native IBS/DST/PPC arithmetic and rounded-field relations, without independent frequency/Z/pedigree/thinning refit |
| RUN, portable VERIFY, genuine REPLAY | Exact Windows x86-64 original runtime for execution; Windows/Linux Java verification; no source-paper recipe inferred |

Two production changes followed the draft: the already selected process's
shared lifecycle is now exercised directly with owned parent/child fixtures;
and combined input byte capacity is checked before large input hashing/copying.
The exact sealed runtime admission remains outside that lifecycle helper.
No old native method, profile, installer, scientific guard or registry changed.

Observed validation for the final build-6 candidate:

- All 16 original-program pilot expectations, including three retained FAIL2
  attempts; all 13 successful captures passed actual Java VERIFY.
- 54 packaged controls covering replay, batch sizes 1/7/31, missing/changed
  dependencies, malformed inputs, six capacity limits, legitimate empty output,
  all-unreportable tables, and resealed count/coefficient/identity/order/genome/
  matrix/partition/process/warning/summary corruption. Linux verified the 16
  pilot outcomes and returned SKIP3 for RUN before copying genotype inputs.
- Four original-program profiles with 257 participants, 1,025 SNPs and 32,896
  pairs: table counts at one/four threads and float64/float32 matrices; all
  passed Java VERIFY and table results were byte-identical across thread counts.
  This fixture is not a production GWAS throughput benchmark.
- Six JUnit tests on each of Windows and Linux Java, without skips/failures:
  closed JSON/operation contracts, numerical/UTF-8 bounds, actual timeout and
  interruption cleanup of an owned parent/child. The timeout left an unrelated
  live process untouched; interruption was restored after the retained process
  record was written. Test fixtures and process/assertion records are retained.
- Java 17-target isolated build; all 23,095 parent payloads unchanged and 27
  class/resource additions. The public rebuild requires bit-identical payloads
  with `-Xlint:all -Werror`. Full project Gradle checks were not run for this scope.
- Public-packet readback of all 57 captures on both Windows and Linux Python/
  Java, with all 1,613 extracted evidence files unchanged. The public rebuild
  reproduced every payload; all four generated example RUNs and VERIFY calls
  passed on that rebuilt JAR. See the retained
  [readback](plink-relatedness-development/READBACK.json),
  [rebuild](plink-relatedness-development/REBUILD.json) and
  [example](plink-relatedness-development/EXAMPLE.json) records.

The source/XML intake is still only preliminary: 52 PLINK, 25 PLINK 2 and
15 KING observations, with 22 pre-existing reviews. Finish the full 92-role
source-bound census, preserving cohort, edition, command host, threshold and
other-package attribution. Only then prepare cumulative admission with the
old review prefix, source guards, predecessor proof, scoped compatibility
delta and unchanged unrelated catalog content. Do not credit regex hints.

**SKIP3 / recovery:** exact supported runtime or platform unavailable means
skip the dependent analysis. Export setup explicitly, restore the sealed
profile and retry fresh paths where permitted, or leave the skip visible.
RUN never installs. The profile remains the existing dated Windows release,
not the newest upstream release. See the guide for the full setup command.

**FAIL2 / recovery:** no complete result is admitted after input, numerical,
integrity, original-process, capacity or cleanup failure. Retain raw/partial
outputs and read `analysis-status.json`, `diagnostics.json`, `WARNINGS.md`
and worker logs. Fix the cause and retry new paths. Do not repair identifiers,
NaN/Inf/NA, unbounded values or thresholds silently. Logical limits and owned
cleanup are not hard OS containment; hashes do not authenticate a producer.

Both literature pages remain withheld from Pages; no release is created.
Private parent/candidate JARs, exact upstream runtime/archives and full paper
cache remain local. Preserve all worktrees and old artifacts. The overall
implementation/source-review goal remains active and unfinished.
