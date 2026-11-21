# mtCOJO prepared-data examples

These examples have two different scopes. The native Java API calculates
conditional GWAS results from **already fitted parameters**. The standalone
original-runtime workflow can estimate GSMR and LDSC quantities with the exact
qualified GCTA 1.94.1 executable. Neither prepares a study's reference population
or infers its identifiers, alleles, ploidy or effect scale. Full GCTA catalog
admission and global command registration remain unfinished.

## Native conditional calculation

Build the normal executable JAR with `gradlew.bat executableJar` (Windows) or
`./gradlew executableJar` on a host with a permitted JDK. Use the actual JAR path
reported by the build. These PowerShell commands compile the synthetic example
and run it; choose a new classes directory if the named one already exists:

```powershell
$jar = 'C:/absolute/path/to/omicsmatic-0.4.0.jar'
javac --release 17 -cp $jar -d build/mtcojo-example-new examples/mtcojo/MtcojoConditionalExample.java
java -cp "$jar;build/mtcojo-example-new" MtcojoConditionalExample
```

On Linux, replace the runtime classpath separator `;` with `:`. If `javac` is
unavailable, compilation is **SKIP3** until a permitted JDK is supplied. Running
transferred compiled classes is a runtime check, not independent compilation.

The example prints conditional beta, SE and P for two synthetic variants under
single- and multiple-covariate conditioning. It then prints the multiple-model
joint causal effects. The single model's conditional betas are 0.08 and -0.24.
No participants or scientific claims are involved.

You must supply the causal effects, GWAS beta/SE arrays, overlap intercepts and,
for multiple covariates, positive genetic variances and genetic correlations.
Genetic variance is **h2 multiplied by phenotypic variance**, not h2 alone.
The API correlation matrix has unit diagonal; the original source's printed
`rg:` matrix instead puts h2 on the diagonal. Matrix order must follow the
declared target/covariate order. The source equation conditions on point causal
effects; it does not add causal-effect estimation SE to each variant's variance.
Undefined variance, singular source-profile factorization, nonfinite values,
capacity exhaustion or cancellation refuse the whole result. Preserve inputs
and report **FAIL2: NO COMPLETE RESULT**; do not emit a partial result table.

## Original GCTA request

`synthetic-inputs.zip` contains the frozen 512-subject, 10,000-marker,
22-chromosome reference, three synthetic GWAS summary tables, matching empirical
LD scores and construction/provenance receipt. It contains no executable or
participant data. Extract it into a **new directory**, for example
`build/mtcojo-input-new`, keeping the `ref-ld` and `weights-ld` subdirectories.
Python 3 and the exact original GCTA runtime are required for the next example.
The request builder itself performs no installation or scientific analysis.

From the repository root, use PowerShell (backticks continue each command):

```powershell
python -B examples/mtcojo/make_request.py `
  --target Y=build/mtcojo-input-new/Y.ma `
  --covariate X1=build/mtcojo-input-new/X1.ma `
  --covariate X2=build/mtcojo-input-new/X2.ma `
  --genotype-prefix build/mtcojo-input-new/reference `
  --reference-ld-prefix build/mtcojo-input-new/ref-ld/ `
  --weight-ld-prefix build/mtcojo-input-new/weights-ld/ `
  --causal-effect-mode supplied --causal-effect X1=0.35 --causal-effect X2=-0.2 `
  --effect-scale as_supplied `
  --runtime C:/absolute/path/to/gcta-win-1.94.1.exe `
  --preparation-note 'Synthetic Gaussian GWAS and matching empirical LD; supplied scale; no participant data.' `
  --analysis-out build/mtcojo-original-new `
  --request-out build/mtcojo-request-new.json

$jar = 'C:/absolute/path/to/omicsmatic-0.4.0.jar'
java -cp $jar org.jlinalg.cli.GctaWorkflowCli --request build/mtcojo-request-new.json
java -cp $jar org.jlinalg.cli.GctaWorkflowCli --verify build/mtcojo-original-new
```

On Linux use the equivalent shell continuation `\`, and supply the exact Linux
runtime path. For GSMR, use `--causal-effect-mode GSMR` and omit the causal-effect
arguments. To use explicitly gzip-compressed trait/LD inputs, set the corresponding
`--trait-compression gzip` and/or `--ld-compression gzip`, supply trait paths ending
in `.gz`, and keep `.l2.M_5_50` files uncompressed. No automatic compression or
fallback occurs. All 22 LD shards must be declared, including empty chromosomes.

The builder uses literal names and writes absolute paths. Read its generated
JSON before running. Advanced resource limits and the exact closed request
contract are documented in the Java source; arbitrary upstream flags are not
accepted. A request JSON file, complete bundle and capture seal must be retained
together with raw inputs and all original logs/exports.

**Required warnings and recovery:**

- Exact Windows runtime SHA-256:
  `2529bccb2f685332f115726c3cb2bf8cab91f12ae3344a991e3a52d751bdb07e`;
  adjacent `libomp.dll`:
  `ee9ce7570217bbccf87d569eb06e71fe9ec246899d2b2fe4fd918215e29e1c79`.
  Linux runtime:
  `dbb2b5e5d51e278328ff297a403d3dd8ee5f5280f0b6d3d8db74b3e7fead7b49`.
  Obtain the exact permitted GCTA v1.94.1 release, read its GPLv3 license and use
  it only where execution/installation is allowed. This example does not ship it.
- **SKIP3:** a necessary permitted runtime/input is unavailable. Supply the exact
  prerequisite named in the warning, or retain the skip if installation is not
  allowed. Never substitute editions or saved hashes to make a check pass.
- **FAIL2: NO COMPLETE RESULT:** preserve `WARNINGS.md`, status, raw inputs and
  all available worker logs/exports; correct the named cause and retry in a new
  directory. Low-instrument GSMR can exit zero and still write NaN tables.
- GCTA 1.94.1 mtCOJO retains prevalence metadata but performs no liability
  conversion. Adding `--prevalence Y=0.25,0.01` does not change that fact.
- The genuine LD-FDR default is obtained by omitting its upstream flag. This
  source integer-parses explicit fractions; only its default 0.05 or explicit
  integer 0/1 are supported. Absolute supplied causal effects at least 100 are
  source sentinels invoking GSMR and are refused. Zero remains legitimate.
- Preserve literal IDs, allele orientation and signed estimates. Gzip must pass
  CRC checks, end in LF and stay within the source's 1,023-byte record bound.
- Offline VERIFY checks integrity and saved input/export invariants. It does not
  refit the original model, establish authenticity or validate a biological claim.

For REPLAY use a NEW output outside the retained parent. A cross-Windows/Linux
replay additionally requires the corresponding exact runtime and explicit
`allow_runtime_flavor_change:true` in its replay JSON. Keep the parent unchanged;
different source runtime flavors can differ numerically even at the same seed.
See the [current handover](../../docs/literature/HANDOVER_CURRENT.md) for the full
qualification status, source profile owners and remaining work.
