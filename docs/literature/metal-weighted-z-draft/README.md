# Sample-size-weighted signed-Z development snapshot

**Unfinished development work. Do not use this snapshot as an admitted METAL
workflow or a qualified analytical release.** The exact independently written
Java API, its tests and R fixtures are saved here for continuation. They are
outside executable source at this handover; qualified source remains at
`91549561599bba8b1d5f069033a2273bb1c5d8ad`.

| File | Purpose |
|---|---|
| `exact-draft-sources.zip` | All four original development files, byte for byte: API, test, fixture ZIP and fixture attributes. |
| `implementation.patch` | Git binary patch adding those files to the baseline above. |
| `development-evidence.zip` | 35 indexed payloads: both R reference attempts, raw results, generator/build/intake scripts, Windows test/build receipts and private-runtime metadata. |
| `DRAFT.json` | Source/archive hashes, verification scope, unperformed checks and next work. |

All archive payloads were read back. The patch was applied in an isolated Git
repository and all four results matched after Git line-ending normalization;
the source ZIP preserves the exact original bytes. The evidence ZIP's
`INDEX.json` pins every other payload. No original METAL source, executable,
archive or bundled example data is included.

## What the draft implements

`org.jlinalg.meta.SampleSizeWeightedMetaAnalysis` provides explicit signed-Z,
two-sided P with explicit direction, and finite **natural-log** P inputs. The
caller supplies positive effective sample sizes and already aligned statistics
for independent cohorts. The combined statistic is
`sum(sqrt(N_i) / sqrt(sum(N)) * Z_i)`. It also retains individual contributions,
a common-standardized-effect heterogeneity Q, degrees of freedom, P/log P and
ordinary nonnegative I-squared. Single-cohort heterogeneity inference is null.

Inputs are not silently omitted, harmonized, imputed or repaired. Exact input
P=0 is refused; supply its finite natural log if known. A result P that
underflows to zero retains its finite log P and an explicit underflow flag.
Nonfinite/unrepresentable inputs, intermediate statistics or results refuse
the complete model. Resource limits refuse the whole cohort population.

This independently written mathematical component does not implement the
METAL interpreter, allele harmonization, overlap estimation, genomic control,
effective-N estimation or a typed original-runtime workflow. The project's
existing `org.jlinalg.meta.MetaAnalysis` already supplies inverse-variance
methods; do not duplicate those while continuing this work.

## Checks actually completed

The exact source compiled with JDK 21 targeting Java 17. The Windows Gradle run
passed all **7 focused tests**, with no failures, errors or skips, and built a
development executable JAR. Independent base R 4.6.1 references cover **70
models**: 69 finite models with **1,844 compared fields**, plus one deliberately
unrepresentable model with **20 raw diagnostic fields retained** and an expected
Java refusal. Cases include cancellation, single cohort, unequal/extreme
weights, natural-log probabilities and numerical underflow. Other tests check
invalid inputs, capacity refusal and ownership of input/result arrays.

The first reference generator refused a model whose R heterogeneity Q was Inf;
its inputs and raw R diagnostics remain in `reference-attempts`. The second
attempt explicitly classifies that same model as an expected refusal. It does
not remove it or replace Inf to force a finite answer. See the retained test
source for exact tolerances, including two specifically named near-zero-Q
cases. These are numerical method controls, not biological validation.

**Not yet performed:** Linux tests, packaged-JAR numerical controls, unchanged
original METAL numerical comparisons, complete paper-role review, typed adapter
qualification or closed scientific-workflow admission. Passing the original
METAL build's own CTest is a separate runtime-build check, not project-adapter
qualification. No additional family is credited.

## Continuing the draft

Start in a clean checkout containing the baseline commit. On a new `codex/`
branch, from the repository root:

```powershell
git switch -c codex/continue-weighted-z
git apply --check docs/literature/metal-weighted-z-draft/implementation.patch
git apply docs/literature/metal-weighted-z-draft/implementation.patch
.\gradlew.bat --no-daemon --no-parallel :test --tests org.jlinalg.meta.SampleSizeWeightedMetaAnalysisTest :executableJar
```

Check the branch name is available first. Preserve every new attempt in a new
output directory. The saved historical Python scripts contain absolute paths
to the original workspaces; adapt their task-root/output declarations before
using them on another machine. Do not overwrite retained attempts or older
source fingerprints. Continue the ordered work in the
[current handover](../HANDOVER_2026-11-18_METAL.md).

The private development JAR is pinned in `DRAFT.json` and the evidence archive.
It includes unqualified draft classes. **Use the qualified frozen reference
workflow JAR named in the handover for qualified existing analysis**, and build
fresh after changing source. Do not infer qualification from a stale Gradle
classes directory or the shared version string `0.4.0`.
