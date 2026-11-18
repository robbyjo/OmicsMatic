# Native reference LD scores: warnings and required actions

**This qualification covers a Java API and synthetic SDK example.** It does
not supply a captured workflow command or infer author inputs, genome assembly,
ancestry/unrelatedness, maps, annotations, trait munging, liability conversion
or complete paper/software parity. Read the [API guide](../../vignettes/ldsc-ld-scores.md).

**Unavailable or forbidden prerequisite:** skip dependent analysis and identify
the permitted input/resource the user must supply. Native analysis uses the
complete current source-build JAR and Java 17+; Python/bitarray are reference
development dependencies. The API downloads/installs nothing.

**Invalid input, changed calls, interruption, numerical or capacity failure:**
admit no complete result. Retain exact inputs, sample/SNP selection, provenance,
settings and the failed attempt. Correct the named issue before a new attempt.
Workflow callers should distinguish unavailable prerequisites (SKIP3) from
invalid analyses (FAIL2), and must not publish a partial array or console log.

**Source chunk graph:** requested chunk size can enlarge the pair set beyond
the nominal distance. The small-panel initial-clique fallback can cover the
entire retained population even when effective chunk size becomes one.
Inspect the returned graph, pair count and warnings. Do not silently substitute
an exact-distance estimator. Explicitly request chunk size one when that is the
intended source graph; do not infer this from an effective value after fallback.

**Filtering and floating-point boundaries:** retain every audit decision.
Strict MAF > threshold, the heterozygous/missing exclusion and strict MAF > .05
summaries follow selected original second-allele behavior. `1-.95` is slightly
above .05; directly stored .05 is not. Inspect borderline rows and declare the
intended target. Do not round/fix allele frequencies to force a count or result.

**Missing calls and negative scores:** observed-mean imputation and population
SD are declared source operations. Malformed, infinite and fractional hard
calls are refused. Preserve negative unbiased-r2 contributions/scores, signed
annotations and empty columns. Never clip scores, round dosages, sort/repair
IDs/coordinates, drop failed variants/pairs or alter sample N to obtain success.

**Count populations:** choose full versus strict-MAF>.05 counts/intersections
explicitly for the next model. Shared annotations double-count variants when
their counts are summed. Binary overlaps are absent for continuous mode. A
zero/signed annotation sum can be valid preparation output while a later
heritability fit refuses that model; do not silently drop its column.

**Resource bounds:** cache and whole-input/work estimates are admission limits,
not speed or heap guarantees. Supply sufficient resources and explicit limits
for the complete model, rather than truncating it. Input-file immutability and
complete provenance remain the surrounding workflow's responsibility.
