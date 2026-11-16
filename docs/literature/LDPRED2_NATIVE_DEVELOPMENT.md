# Native Java LDpred2 numerical methods

**Development API; build current source.** Published v0.4.0 assets do not include
these additions. Native Java implements LDpred2-inf and one LDpred2-grid model,
using prepared sparse LD and matched GWAS effects. LDpred2-auto, the workflow
CLI, complete grid orchestration and validation-set model selection remain open.
No additional catalog family is credited by this component checkpoint. The
250-paper implementation objective remains active.

The five source papers use LDpred2. P074 reports the grid method through
bigsnpr 1.12.2; P109 reports version 1.12.4 and refers to auto; P125 explicitly
uses auto. P128 and P205 do not establish the exact settings in the reviewed
paragraphs. Native infinitesimal estimation must not be substituted for an
auto or grid analysis. Exact author binaries and complete environments remain
unestablished. The source intake retains all 250 XML checksums and every
deduplicated LDpred mention.

## Java API

Build with `./gradlew executableJar` or `gradlew.bat executableJar`. Use
`org.jlinalg.genetics.LdCorrelation`, `LdPred2` and `LdPred2Random` from Java.
The executable JAR includes these classes and the existing numerical dependency;
the top-level command dispatcher does not yet provide an LDpred2 command.

```java
import org.jlinalg.genetics.LdCorrelation;
import org.jlinalg.genetics.LdPred2;

// Two variants; both triangles, ascending row indices within each column.
var ld = LdCorrelation.ofCsc(2,
    new int[]{0, 2, 4}, new int[]{0, 1, 0, 1},
    new double[]{1.0, 0.25, 0.25, 1.0});
var fit = LdPred2.infinitesimal(ld,
    new double[]{0.2, 0.1},       // effects on the allele scale
    new double[]{0.01, 0.015},    // standard errors
    new double[]{10000, 18000},   // per-variant effective sample sizes
    0.3, LdPred2.SolveOptions.defaults());
double[] weights = fit.alleleEffects();
```

`LdPred2.gibbs` takes `GridParameters(h2, p, sparse, burnIn, iterations)`,
an explicit random stream and an optional sampling callback. Returned weights
are averaged posterior allele effects. Callback vectors are standardized
sampled effects: they are different outputs. Multiply each sampled component
by `sqrt(n_eff * beta_se^2 + beta^2)` when allele-scale samples are required.
Callback values remain provisional until the whole call succeeds.

`LdPred2Random.RStream` takes the seven integers of an R `.Random.seed` for
L'Ecuyer-CMRG with Inversion normals (kind 407 or 10407). Its `state()` method
returns a defensive copy for continuation. Allocate a distinct stream per
model; do not share a mutable stream across threads. This component does not
implement R's `set.seed(integer)` initialization or foreach/doRNG's ordering
and allocation of streams. It does not promise that an arbitrary Java seed
reproduces a complete R grid call.

LD matrices are immutable full CSC arrays in memory, with finite, exactly
symmetric correlations, sorted unique row indices and a unit diagonal. Both
triangles are mandatory. The native solver uses a diagonal preconditioner,
checks the actual residual and rejects nonconvergence or nonpositive curvature.
No dense matrix is allocated. Memory requirements grow with the number of
stored LD entries and variants; this is not an on-disk SFBM implementation.

## Warnings and required actions

**Prepared inputs must agree.** Verify variant order, effect alleles, genome
build, reference population and effective sample-size definitions before calling
the API. No allele matching/flipping, reference lookup, LD thresholding,
symmetrization, sample-size derivation or phenotype tuning is performed.
Use the appropriate effective sample sizes for the actual GWAS model; ordinary
sample counts are not interchangeable across all study designs.

**Divergence admits no model.** The original grid core returns missing effects
when its squared sampled-effect norm exceeds its source-defined threshold.
Java raises `LdPred2.DivergenceException`. Retain the error and input identities;
check LD/summary agreement and model settings before a new analysis. Do not
replace missing effects, disable the threshold or silently alter the inputs.
A later error invalidates earlier callback samples. All 19 original divergences
in the initial synthetic controls are retained; none becomes an accepted fit.

**Other failures admit no effects.** Invalid dimensions, LD entries, numeric
inputs, random draws, overflow and solver nonconvergence fail explicitly.
Do not use partial results. The caller must retain a prominent warning/error
and commit outputs only after the complete call succeeds.

**Runtime prerequisites.** Native computation requires the built Java library,
not R. Regenerating original references requires a permitted original bigsnpr
1.12.2 R environment and the exact CRAN source archive. If needed installation
is unavailable or forbidden, skip that regeneration and retain the cause/action;
the supplied fixtures support offline Java tests. `prepare_reference.py` reports
SKIP3 for missing reference executables/files and FAIL2 for other failures.
Use NEW output and fixture paths after correcting the named cause.

## Validation and source provenance

The reference package is the unmodified bigsnpr 1.12.2 conda-forge build
`r43h08d816e_0`, running R 4.3.3 with bigstatsr 1.6.1 and bigsparser 0.7.3.
Its build recipe names the exact
[CRAN archive](https://cran.r-project.org/src/contrib/Archive/bigsnpr/bigsnpr_1.12.2.tar.gz)
with SHA-256 `443a5529ae2596eb3cf8f19760893b96beba4f12a4decbf6bea645db4d2187f6`.
All three original R function bodies are checked against that archive. Reference
answers execute the original installed R/C++ functions; their bodies and native
code are not replaced. The reference binary was not independently rebuilt.

There are 49 original small controls: four LD structures, three causal fractions,
both sparsity choices, unequal effective sample sizes, the initial synthetic
set and added coherent correlated signals. Thirty grid fits are finite and
19 diverge in the original. Java matches all final original random states and
the finite posterior means; independent sampled-effect comparisons use the
original public sampling entry. All 49 infinitesimal results are also compared.
The separate R-generator fixture checks uniform, normal and mixed draws and
the complete states after each phase.

The large control uses 1,100,000 synthetic variants and 2,200,000 stored LD
entries in paired blocks. Every native infinitesimal and grid effect is compared
with the actual original output, with a 256-MiB Java heap. This establishes the
tested sparse computation and dimension; arbitrary LD panels of that dimension
may need substantially more memory. Large raw binary inputs and outputs remain
local, with saved identities. No biological or complete paper validation is
claimed.

Run the focused tests with:

```text
./gradlew test --tests org.jlinalg.genetics.LdPred2Test --tests org.jlinalg.genetics.LdPred2OriginalTest
```

`tools/literature/ldpred2_native/prepare_reference.py --help` describes original
reference regeneration, including the optional explicit WSL bridge.
`large_reference.R` and `LargeControl.java` provide the large original/native
comparison. The [upstream reference](https://privefl.github.io/bigsnpr/reference/LDpred2.html)
documents the separate inf, grid and auto methods and effective sample-size
contracts. Source/license notices accompany the development evidence.

Read the saved [qualification](ldpred2-native-development/QUALIFICATION.json)
and [source/license notices](../../tools/literature/ldpred2_native/SOURCE-NOTICES.md).
Use `python -B tools/literature/ldpred2_native/records.py --require-current-source`
to read the saved evidence. Add `--rescan-sources` for all 250 retained primary
XML files, or `--private-base build/ldpred2` to check the named complete local
raw records. These are saved-record checks; they do not rerun the methods.
