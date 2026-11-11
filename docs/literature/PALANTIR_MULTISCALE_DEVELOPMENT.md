# Palantir native multiscale coordinates and original-workflow controls

**The Java coordinate method is implemented and tested. The complete Palantir
execution adapter and catalog application remain unfinished.** This development
component adds no new tool credit. The current catalog still records 85 new
scoped integrations, 88 including three existing adapters, and 251 tools marked
missing. The five further named-tool candidates and their eight source-role
drafts have not yet been applied.

The original source is the bioinformatics project
[dpeerlab/Palantir](https://github.com/dpeerlab/Palantir). Exact official 1.4.4 and
1.4.6 wheels, immutable tag revisions, source, packaging metadata and MIT notices
are retained. Both versions have the same scientific function body for
`utils.determine_multiscale_space`. Later versions changed random-generator
behavior elsewhere in the package; keep the requested version explicit.
[Upstream release notes](https://github.com/dpeerlab/Palantir/releases)

P094 uses Palantir for multiscale diffusion-space construction before scFates
tree fitting. P024 uses Palantir as a microglial pseudotime comparator with a
200-repeat selected-root procedure. Neither paper paragraph reports the
Palantir edition. These source roles do not establish the tested versions as
historical paper versions, and the coordinate component does not reproduce
either complete analysis.

## Native Java API

Use `org.jlinalg.embedding.PalantirMultiscale.scale` with explicit ordered
float64 eigenvalues, matching eigenvector positions and the original cell labels.
It computes `vectors[:,1:n_eigs] * (lambda[1:n_eigs] / (1-lambda[1:n_eigs]))`.
The leading component is excluded. An explicit `n_eigs=4` returns three columns,
newly numbered 0,1,2 as in the original DataFrame. Cell labels and order remain
unchanged, including duplicates, Unicode and empty strings within this API.

A null count follows the original largest-gap, second-largest-gap and
minimum-three rules. If tied gaps admit different component counts under
different NumPy sort orders, the method returns no result and requires the
unchanged upstream implementation or a scientifically declared explicit count.
It accepts ties whose possible orders all give the same original bound.
It does not guess a tie break or infer an unreported paper setting.

The API preserves literal signed zeros, NaNs, infinities, near-one amplification
and overflow. Read `warnings()`, `nanOutputs()` and `infiniteOutputs()` before any
downstream work. These states are not imputed or clipped. Explicit zero/negative
counts retain the original empty-column result; that result is not a trajectory.
Arbitrary pandas Series label alignment and float32 eigenvalue arithmetic are
outside this float64 positional API. Their original outcomes remain in the
reference evidence. Complete cell/component/value/operation limits and thread
cancellation prevent a partial result from being returned.

Build the additive development artifact using the locally validated parent JAR:

```powershell
python -B tools/literature/build_palantir_multiscale.py --out build/literature-review/palantir-runtime/multiscale-build-NEW
```

Read that directory's `COMPLETED.json` for the exact artifact path and SHA-256.
The build must say `PASS_COMPILED_NATIVE_PALANTIR_MULTISCALE_COMPONENT_ONLY`.
Compilation alone does not validate a changed method or admit an analysis.
Missing parent artifacts must be supplied exactly; never refresh saved hashes.
The canonical published executable and all 21,083 parent JAR entries are preserved.

Start JShell with its default execution engine and the artifact on its class path,
then paste:

```sh
jshell --class-path /absolute/path/omicsmatic-0.3.7-palantir-multiscale-development.jar
```

**Use the default JShell engine.** An additional `--execution local` control failed
with ClassNotFoundException for this development artifact. That failed attempt is
retained. Default JShell and ordinary Java execution on all three tested JVMs pass;
local-engine compatibility is not claimed.

```java
import java.util.Arrays;
import org.jlinalg.embedding.PalantirMultiscale;
var result = PalantirMultiscale.scale(
    new String[]{"cell-A","cell-B"},
    new double[][]{{1,2,3,3},{1,4,5,6}},
    new double[]{1,.75,.5,.25}, 4,
    PalantirMultiscale.Limits.defaults());
System.out.println(Arrays.deepToString(result.coordinates()));
System.out.println(Arrays.toString(result.selectedComponents()));
```

Expected output is `[[6.0, 3.0, 1.0], [12.0, 5.0, 2.0]]` and `[1, 2, 3]`.
This native call needs Java 17+ and no Python package.

## Original software and recovery

Complete original 1.4.4 and 1.4.6 imports were prepared in separate new edition
directories with a 48-wheel resolved closure including 1.4.6, plus the separately
captured 1.4.4 wheel, using the existing bound
Python 3.11.17 interpreter read-only. Every original scientific source, license
and non-RECORD wheel member matches its official wheel. Pip's regenerated RECORD
metadata is retained separately with both hashes. No namespace function is
patched. The first setup's RECORD mismatch, original diagnostics and complete
failed prefix remain preserved; a new setup reused only verified downloads.
The shared interpreter tree stayed unchanged.

**SKIP3: skip dependent upstream analysis** if installation, downloads, the exact
runtime or required Java are unavailable or forbidden. Obtain permitted software
and retry new paths, or retain the skipped state. The four actual permission and
connected-skip controls execute no original analysis. **FAIL2: no result is
admitted** after a source/hash, numeric, capacity, original-method or audit failure.
Preserve every original/provisional output and diagnostic, resolve the named cause
and retry new paths. Never edit old source profiles or binding hashes. Runtime
preparation is separate from analysis; the portable execution/install kit is
unfinished. The current preparation scripts are workspace reference harnesses.

The data-only full-workflow discovery controls retain original diffusion,
pseudotime, Markov, fate and entropy outputs. They also retain these source details
that the execution adapter must handle explicitly:

- Missing requested terminal cells remain in the original mapping and can raise
  a later KeyError. Do not drop or repair them; reject the complete requested batch.
- `PResults` sets branch probabilities below 0.01 to zero after the entropy is
  computed. It does not renormalize the clipped rows. Retain the observed
  pre-clipping probabilities and entropy as well as returned probabilities.
- Terminal annotation mappings use cell IDs as keys/index and annotations as
  values in the actual validation code. Preserve both identities and labels.
- Boundary-root selection, explicit early roots, automatic terminals, alternate
  kernel backends and the newer multiple-root interface are distinct choices.
  Do not substitute the exact sklearn kernel for the source Scanpy kernel.
- Automatic terminal detection can return an empty fate-probability family. A
  successful original function return alone does not admit a fitted fate model;
  the execution adapter needs an explicit empty-fate policy and a prominent
  warning, with no inferred terminal cells.

Full-workflow controls preserve original warning filters and record explicit
fixture caller/method seeds and NumPy states. The separate coordinate collector
temporarily records all warnings for its arithmetic controls; it does not claim
original warning-suppression parity. No original function or random draw is
replaced. Pickle data inputs are never loaded, and no biological or paper analysis
is admitted by these software controls.

The source-observation audit retains 15 complete original executions and six
expected errors. It checks all 2688 observed probability states, including 503
states changed by the original clipping and 185 returned rows below unit mass.
These are software/source observations, not independent trajectory refitting or
biological calibration.

## Validation and remaining work

Each version has 52 original coordinate cases: 42 complete supported numerical
families, six original errors, two ambiguous-tie deferrals and two retained
source-only dtype/alignment profiles. Java 21/25 on Windows and Java 21 on Linux
each pass 17,627 exact numerical-state checks per version, ten additional native
rejections and defensive-copy checks. Seven deliberate comparison corruptions
are rejected. The documented JShell example is executed from the exact artifact.

See `palantir-multiscale-development/VALIDATION.json` and its self-contained
`controls.zip`. Kernel/eigensolver fitting, root selection, pseudotime, fate and
entropy admission, portable runtime setup, the complete execution adapter,
catalog application and paper-wide semantic completeness remain required work.
