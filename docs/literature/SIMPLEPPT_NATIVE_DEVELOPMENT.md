# SimplePPT native arithmetic and original scFates controls

**Native arithmetic is implemented and validated. The complete scFates execution
adapter is unfinished, and this component adds no tool credit.** The current
250-paper catalog still has 86 new scoped integrations (17 native subsets and
69 upstream adapters), 89 including three existing adapters, and 255 missing
named tools. scFates remains missing. These counts are from the current
[catalog checkpoint](omics-2023-2026/palantir-catalog-validation.json).

**Read the warnings before downstream work.** Underflow can produce all-zero
assignment rows; the original code maps their division NaNs to zero. Literal
convergence can be zero despite coordinate changes. Original diagnostic scores
can contain NaN or infinity. None of these states certifies a valid trajectory.
The saved original 200-node negative control raises IndexError after computing
provisional pseudotime: its partial output is not an admitted analysis. See
the recovery table below.

The source is [SimplePPT 1.1.7](https://pypi.org/project/simpleppt/1.1.7/) and
[scFates 1.2.5](https://pypi.org/project/scFates/1.2.5/), with their unchanged
BSD-3-Clause licenses. Exact official wheels, immutable Git tag revisions,
complete package source and source comparisons accompany the evidence.
Of 58 Python modules, 57 are byte-identical between wheel and tag. Only generated
SimplePPT version constants differ. Both files are retained; scientific function
bodies match. The original wheel is executed without rewriting its version file.

P094 uses scFates after Palantir diffusion-space construction: its reported
200-node UMAP setting uses sigma 0.5 and lambda 100, and its individual diffusion
setting uses 3,000 nodes, sigma 0.0005 and lambda 200. The source paragraph does
not specify a package version. The controls here use supplied synthetic
coordinates, declared iteration budgets and the exact editions above. They do
not reproduce the paper's UMAP, convergence, biological findings or complete
analysis. The paper roles are linked from the
[catalog guide](PALANTIR_CATALOG.md).

## Run native Java arithmetic

The API is `org.jlinalg.embedding.SimplePptMath`. It needs Java 17+ and no Python
or external numerical package. Matrices use rows for cells/nodes and columns for
coordinates. Its five operations are:

| Operation | Literal source behavior |
| --- | --- |
| `assignments` | `exp(-distance/sigma)`, row sums, division, then NaN-to-zero mapping; preserve zero-mass rows. |
| `convergenceError` | Maximum over nodes of `sqrt(sum(new-old)^2)/sqrt(sum(new^2))`; the numerator squares the signed coordinate sum. |
| `conditionalSystem` | `lambda*(diag(columnDegree(B))-B)+diag(columnSum(R))`, including complete forests. |
| `conditionalRightHandSide` | Node-by-coordinate transpose of `(X.T*W.T) @ R`; null weights mean literal ones. |
| `diagnostics` | Correlation across coordinates, literal `R*log(R)`, and the pre-update node-distance term. |

This component does not choose initial nodes, consume random numbers, select an
MST, solve the system, fit a tree, choose a root or project pseudotime. Source
outputs can use lagged assignments and distances from before the final node
update; those distinct states must not be silently interchanged.

Build against the exact locally validated Palantir development parent:

```powershell
python -I -B tools/literature/build_simpleppt_math.py --out build/literature-review/scfates-runtime/math-build-NEW
```

Use the artifact path in the new directory's `COMPLETED.json`. Its status must
be `PASS_COMPILED_NATIVE_SIMPLEPPT_CPU_ARITHMETIC_ONLY`. Compilation does not
validate changed numerical code. Missing parent artifacts must be supplied
exactly; do not refresh their saved hashes. All 21,164 parent entries are
preserved. The canonical published executable is unchanged.

Start JShell with its default execution engine, then paste the example:

```sh
jshell --class-path /absolute/path/omicsmatic-0.3.7-simpleppt-math-development.jar
```

```java
import java.util.Arrays;
import org.jlinalg.embedding.SimplePptMath;
var limits = SimplePptMath.Limits.defaults();
var r = SimplePptMath.assignments(new double[][]{{0,0},{1000,1000}}, .5, limits);
System.out.println(Arrays.deepToString(r.weights()));
System.out.println("zero rows="+r.zeroMassRows());
System.out.println(r.warnings());
System.out.println("literal error="+SimplePptMath.convergenceError(new double[][]{{1,3}}, new double[][]{{2,2}}, limits));
```

Output includes `[[0.5, 0.5], [0.0, 0.0]]`, `zero rows=1`, an explicit
`SOURCE ZERO-MASS ASSIGNMENTS` warning, and `literal error=0.0`. In the last call
both coordinates changed but their signed changes cancel. Do not substitute a
Euclidean change norm and claim source equivalence.

Check `warnings()`, `zeroMassRows()` and `sourceNaNsMappedToZero()` on assignments;
check `warnings()` and `nonfiniteValues()` on matrix results. Diagnostic warnings
explain nonfinite states and the scope of the score. `convergenceError` returns
a scalar: explicitly check its finiteness and interpret its literal signed-sum
definition. No epsilon, imputation, clipping or probability renormalization is
added. Arrays returned by results are defensive copies.

The default limits permit 100,000 rows, 4,096 columns, 25,000,000 values per
complete matrix and 1,000,000,000 charged operations. Dense node systems require
quadratic storage. Limits also apply to output matrix rows; interruption and
invalid/ragged inputs return no partial result. Calls require finite positive
assignment sigma, finite nonnegative input distances/assignments, matching
shapes and source-compatible binary symmetric adjacency with a zero diagonal.
Other functions retain documented nonfinite arithmetic; finite signed weights
are permitted in the conditional right-hand side.

## Evidence and its limits

The [validation manifest](simpleppt-core-development/VALIDATION.json) binds
source snapshots and lossless content-addressed evidence archives. Large arrays
are data-only, shape-checked and SHA-256-bound little-endian files, never user
pickle. The archives retain all original outputs, tracebacks, logs, warnings,
random states, setup inventories, failed development attempts and the successful
reruns. Runtime installations and development JARs remain local; their exact
sources, build records and dependency hashes are retained.

The original full namespace was installed into a new private prefix using 50
official, hash-verified wheels. The existing Python 3.11.17 interpreter stayed
read-only. Both complete runtime trees match before and after execution.
Original namespace functions, warning filters and scientific files were not
patched. Installer-generated RECORD changes are captured separately.

Validation includes 30 calls to the actual compiled Numba assignment functions,
five NumPy replays of inline source convergence expressions, five NumPy replays
of conditional/score expressions, and final fitting-state witnesses from ten
actual original PPT executions. Nine complete original scFates tree/root/
pseudotime workflows pass; three expected original errors are retained. The
ten witnesses include the PPT stage preceding the palette failure. The witness
is not an admitted pseudotime result. Read-only tracing captures original locals
without changing functions, consuming random values or editing fitting state.

Positive workflows cover weighted coordinates, supplied initialization,
automatic tip-root selection, likelihood projection, probabilistic projection
with one and two jobs, a two-node tree, a separate 200-node curved-coordinate
control with ten iterations, and a 3,000-node control with one iteration.
Not-converged messages and NaN scores remain visible. These are software
controls, not convergence or biological certification.

Each of Windows Java 21, Windows Java 25 and Linux Java 21 compares 40 arithmetic
cases and ten actual fitting witnesses: 18,203,693 numeric states, including six
nonfinite states, 30 zero-mass rows and 7,150 normalization NaNs mapped to zero.
Matrix/assignment checks use `5e-12*abs(expected)+16*ulp(expected)` for finite
nonzero values; signed zeros and nonfinite classes remain explicit. Fitted-node
linear-system residuals use a `1e-10` sum-of-absolute-terms scale.

The first diagnostic score subtracts correlations near one and needs a distinct
cancellation comparison. The 3,000-node original value
`7.249340140175516e-8` and Java value `7.249340140233295e-8` differed by about
`5.8e-19`, exceeding the former purely relative check. The final reports retain
every finite first-score pair and its difference. Only this diagnostic uses an
additional absolute envelope `(8*coordinateCount+16)*ulp(1.0)*sum(abs(R))/cells`.
It is a declared software comparison tolerance, not a universal rounding-error
bound. No calculation or original value is repaired. Five comparator controls
and nine full-family corruption runs reject altered values, signed zeros,
nonfinite classes, equations, observations and incomplete/duplicate families.
Four actual setup/dependent-run controls verify prominent SKIP3 outcomes.

## Warnings, errors and recovery

| Status or warning | What the user must do |
| --- | --- |
| **SKIP3: dependencies or permission unavailable** | Skip dependent scFates analysis. Read the exact cause and `WARNINGS.md`; obtain compatible software only where installation/download is permitted, then retry in a new directory. Native Java arithmetic itself needs no install. |
| **FAIL2: source, hash, import, timeout or comparison failure** | Admit no requested batch and keep earlier cases provisional. Preserve original inputs, failed directories and full logs; correct the named cause and rerun on new paths. Never refresh historical hashes to make changed scientific code pass. |
| **Original pseudotime IndexError** | The 200-node branched synthetic control reaches provisional pseudotime but fails in original milestone-color indexing. Keep the traceback and reject that whole requested batch. Investigate the upstream source/data cause; do not invent colors or treat partial observations as completed output. |
| **Too many nodes / zero iterations** | Original controls retain ValueError and UnboundLocalError. Use scientifically declared compatible settings on a new run; do not auto-adjust the request. |
| **Zero-mass assignments** | Inspect distance scale, sigma and affected rows. Do not fabricate a fallback assignment distribution. |
| **NaN/Inf diagnostics or literal zero convergence** | Inspect saved values and the source equations. Do not replace nonfinite values or infer convergence from this scalar alone. |
| **Not-converged original log** | Review the declared iteration budget and actual error before further analysis. Retain the original message even if other source messages appear inconsistent. |
| **Capacity/cancellation rejection** | Adjust explicit resource limits only if the environment can support the entire matrix; rerun the complete call. No partial result is available. |

The developer reference-preparation scripts are not a public execution adapter.
RUN must not install. Optional R/GAM, GPU, unrelated scFates analyses and a full
native Java fitter remain outside this validated component. The 250-paper
implementation goal remains active.
