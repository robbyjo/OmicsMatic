# Native MR-MEGA ancestry development

**MR-MEGA's complete GWAS workflow remains unfinished and uncredited.** The
catalog remains at 91 new scoped integrations, 94 including existing tools,
and 250 missing tools. This checkpoint extends the independently authored
[regression component](MRMEGA_REGRESSION_DEVELOPMENT.md) with ancestry-marker
selection and classical multidimensional scaling (MDS) on prepared data.

`org.jlinalg.meta.MrMegaAncestry.select(variants, cohortCount)` accepts variants
in their original first-encounter order. Frequencies must already be aligned to
the same alleles and cohort order. Use frequency `-1` for an absent cohort.
`infer(selection, axisCount)` computes the explicitly requested axes; its
coordinates can be passed to `MrMegaRegression.fitWeighted` with matching
effects and weights. Public input/output arrays are copied defensively.

**Required preparation:** merge variant identifiers, align alleles, preserve
cohort order and explicitly supply the axis count. This component supplies no
Java GWAS file reader, allele harmonizer, genomic control or complete analysis
CLI. Do not treat its test probe as an analysis application. The thin development
jars additionally need the recorded project runtime on their classpath; the
canonical executable has not been rebuilt.

## Source behavior and warnings

The selector preserves observed MR-MEGA0.2 behavior: float32 frequency storage,
complete-cohort frequency bounds, last eligible marker per chromosome/megabase
slot, omission of an unoverwritten source ordinal zero, and traversal of
chromosomes 1 through 23 and bins 0 through 298. Every supplied variant has a
selection disposition on success. Frequencies exactly `.01` or `.99` round
outside the original bounds when stored as float32 and are excluded.

**Position bin 300 is an upstream out-of-bounds array access.** The tested
original build aliases the next chromosome's bin zero. The native implementation
models that observed contiguous-slot result explicitly and warns for each such
marker. It retains the marker's original chromosome and position. This is an
observed-build compatibility behavior, not a portable guarantee about undefined
C++ behavior. Bin 299 is stored but never selected; larger bins are ignored.

Distances are mean squared frequency differences. Native classical MDS uses a
symmetric eigensolver with explicit convergence, positive-rank and capacity
checks. Signs put each axis's largest absolute loading on its positive side.
Original SVD signs remain separate. Test comparisons apply recorded sign maps
only during comparison; stored original and native results remain unchanged.
Signed Gram eigenvalues are retained, including unselected rounding noise.
No equivalence between signed eigenvalues and the original nonnegative SVD
spectrum is claimed.

**A cutoff through a numerically tied eigenspace is refused.** Supply explicit
coordinates or select the complete eigenspace. A complete included tied space
has an explicit nonunique-direction warning. Reference parity for tied original
SVD bases is not claimed. Invalid, inconsistent, nonfinite, unresolved or empty
selected populations fail with an explanatory exception and no admitted result.
Correct the named input or settings; do not invent axes or silently drop cohorts.

## Validation and evidence

Twelve synthetic families cover 7–11 cohorts, 1–4 axes, missingness, float32
boundaries, source-order overwrites, source ordinal zero, X/Y/XY, ignored bins
and observed bin-300 aliasing. Each was run through the unchanged original
MR-MEGA0.2 program's ancestry-only mode. Selected IDs, ordinals, frequencies and
cohort order match exactly. Separate probes of unchanged original public
distance, MDS and regression functions retain full-precision results.

Both JDK21 and JDK25 builds target Java17 and pass all twelve families on
Windows JVM21, Windows JVM25 and Linux JVM21: 72 complete ancestry and coupled
regression comparisons, plus six analytic/immutability/refusal contract suites.
No JVM17 execution is claimed. These tests do not execute original full GWAS
regression file output or complete Java ingestion. Every inherited regression
class remains byte-identical to the earlier sealed component.

Frozen evidence is in `mrmega-ancestry-development/`. Run
`python tools/literature/mrmega_ancestry_evidence.py` from the repository root to
check source pins, archived marker populations, geometry and regression values.
`--archive-only` skips current development-source and canonical-runtime checks.
Both modes check the frozen parent bundle. Neither mode reruns Java/C++,
authenticates historical execution, independently refits current user data or
certifies a whole-paper reproduction.

Original reference source, archive and binaries stay private; the evidence
contains our implementation/probes, input controls, provenance pins and results.
Reference regeneration requires permitted Linux compilation and installation.
If those are unavailable or forbidden, skip the dependent reference analysis,
keep its SKIP3/FAIL2 receipt, and follow its stated action. Native prepared-data
calculations need no original MR-MEGA installation. Preserve failed attempts and
retry into a new output path after correcting the named problem.
