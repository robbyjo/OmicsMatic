# scrattch.mapping 0.2: native dense normalization

**Read the returned warnings and nonfinite-review flag.** This Java component
preserves the original normalization branches, including surprising zero-norm
behavior. It does not implement hierarchical mapping or establish a reproduced
paper. The scrattch.mapping family remains missing in the catalog until the
full workflow is separately qualified. This component adds zero family credits.

The implementation is `org.jlinalg.singlecell.scrattch.ScrattchMapping02`, derived
from `l2norm` in `R/HANN_utils.R` at the official
[v0.2 source commit](https://github.com/AllenInstitute/scrattch.mapping/tree/df8751129e47a140e2d3ceecfd71438fca1149ef).
The derived Java file is GPL-3.0-only; attribution is packaged at
`META-INF/licenses/scrattch-mapping02.txt`. The native operation calls no R,
Python, subprocess or external numerical library.

## Build and use

Build the matching source checkout (use `gradlew.bat` on Windows):

```sh
./gradlew :test --tests org.jlinalg.singlecell.scrattch.ScrattchMapping02Test jar sourcesJar
jshell --class-path build/libs/omicsmatic-0.3.7.jar
```

In JShell or Java:

```java
import org.jlinalg.singlecell.scrattch.ScrattchMapping02;
var input = new double[][] {{0.3, 0.4}, {0, 0}, {3, 4}};
var result = ScrattchMapping02.l2norm(input, 2, "row");
System.out.println(java.util.Arrays.deepToString(result.values()));
System.out.println(result.warnings());
System.out.println(result.requiresNonFiniteReview());
```

The first row remains `[0.3, 0.4]` because the original zero-row branch divides
every row by `max(norm, 1)`. The final row becomes `[0.6, 0.8]`; the zero row
remains zero. One original warning message is returned. Do not renormalize the
first row to make it resemble a different algorithm.

Exactly `"column"` selects column normalization. Other non-null strings select
rows, as in the original; a null selector is invalid in this Java API. If any
column norm is zero, the entire matrix is returned unchanged with the original
warning message. If a norm is missing/NaN without a zero norm overriding R's
`any` condition, the method throws `OriginalNormalizationException` and returns
no result. Squaring precedes summation, so original overflow/underflow outcomes
are preserved. No scaling repair, dropped cells or finite-value filtering occurs.

The explicit column count preserves zero-row matrix dimensions. An overload
accepts row/column names; absent names are null arrays, an R missing name is a
null element, and `"NA"` stays a literal string. Empty, duplicate and Unicode
names are retained. Inputs are not mutated; returned arrays belong to the caller.
The warning list contains original message text, not R condition objects.
`requiresNonFiniteReview()` flags nonfinite returned values. Inspect both.

This source component is not a root Java CLI command. The existing development
CLI and previously published release artifacts are not rebuilt by its isolated
validation. Build this source to obtain the new library class.

## Evidence and limits

The original function produced 318 controls: 303 returns, 15 original errors and
52 warning messages. The Java tests compare all 58,131 returned numbers, matrix
dimensions/names, original warning texts, errors and unchanged input values.
Finite comparisons use `1e-14 * max(abs(original), 1e-300)`; signed zero and
nonfinite classifications are checked separately. Java compensated summation
does not claim exact R extended-precision sum bits or R NA/NaN payload identity.

The fixture retains complete original RDS objects/conditions, supported R value
trees, binary exports, raw logs and all 134 files of the original source archive.
The unchanged original numeric function is also re-executed against every full
R object: inputs, values, attributes, visibility, errors and conditions must
match strictly. This R replay uses the separately locked R 4.5.3 / Matrix 1.7.6
reference profile, not the developing full mapping environment or author setup.

```sh
python3 -B tools/literature/scrattchmapping02/verify_native.py \
  --rscript /path/to/exact-reference-prefix/bin/Rscript \
  --out build/literature-review/monocle-runtime/NEW-native-reference-check
```

On Windows supply the Linux R path and `--wsl-distro NAME`; the explicit WSL
route supports a checkout on D:. Use Linux directly for other layouts.
The verifier installs nothing. Missing/forbidden R prerequisites **SKIP3** skip
dependent original-R verification; native Java use itself needs no R runtime.
**FAIL2 admits no complete verification**, including recording failure. Read
the console, `FAILURE.json`, `WARNINGS.md` and retained partial files. Resolve
the named source/runtime/filesystem cause and retry a **new** writable directory.
Never change saved hashes, repair missing values, suppress source warnings or
overwrite prior runs. A leftover RDS or successful portable hash check alone
is not a completed original-R replay.

The first reference attempt used case-only directory suffixes on the Windows
filesystem and was stopped after an output collision. Its partial records are
retained and unadmitted. The qualified reference uses ordinal case directories;
actual matrix names and selector strings remain unchanged. No output from that
failed attempt is included as a qualified reference.

At this native checkpoint, full mapping setup is still unqualified. The original
BIGCAT dependency failed to compile with Rcpp 1.1.2. A separate R 4.4.3 / Rcpp
1.0.14 profile installed unchanged BIGCAT and scrattch.mapping, but the mapping
package's load hook selected a different Python after conda discovery errors.
Its dependent mapping analysis is skipped until the declared environment is
resolved and full hierarchy controls pass. Keep all original installer/load
logs, pristine source archives and compiler partials. The first install attempt
wrote build outputs beside its extracted BIGCAT inputs; that tree is retained,
and later installation uses separate pristine and compiler-work directories.

Two full-runtime dependencies (`scrattch.io` and `scrattch.vis`) have Allen
Institute licenses restricting commercial redistribution. Their runtimes stay
private and are not bundled with this GPL source component. The full goal,
paper-specific settings, hierarchical mapping and remaining tool implementations
remain open; no whole-tool, biological-validity or convergence claim is made.
