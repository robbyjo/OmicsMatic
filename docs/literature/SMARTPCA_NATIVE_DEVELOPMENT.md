# Native smartpca preparation and projection: development checkpoint

`SmartpcaPrepared` adds native Java allele-frequency normalization, exact sample
covariance PCA, ordinary projection and missing-call least-squares projection for
explicitly prepared autosomal hard calls. This is a library component. A complete
user-facing RUN/VERIFY/REPLAY workflow and catalog admission remain required.
Current admitted totals stay **153 families; 1,691 reviewed/1,031 pending uses**.

## Warnings and required actions

**Do not treat this component as the complete smartpca program.** Outlier removal,
shrinkmode/autoshrink, FST and block-jackknife uncertainty, LD filtering, file
conversion, chromosome/ploidy handling and ancestry assignment are separate work.
Prepare and record those choices explicitly before calling this API. Do not
silently omit a paper's requested shrinkage or substitute ordinary PCA for it.
No new family, paper review, biological result or whole-paper reproduction is
admitted by this checkpoint. Existing release JARs may predate this component.

**SKIP3: dependent reference analysis skipped.** Supply the named permitted Linux
compiler/installer/source/runtime prerequisite and retry NEW output; if it is
unavailable or installation is forbidden, retain the skip. The reference installer
requires explicit `--allow-install` and uses private prefixes. Native calculation
needs Java and the project dependencies, without R, GSL or an installed smartpca.

**FAIL2: no complete workflow result is admitted.** Keep original, partial, failed,
undefined and superseded evidence. Fix the named input, numerical, resource or
integrity problem and retry NEW output. Never coerce dosages into hard calls,
repair identifiers, replace missing/undefined values by zero or reduce the input
population to fit a limit. The library throws on invalid calls, zero covariance,
singular/unreportable projection, explicit capacity exhaustion or interruption.
It does not yet create a saved workflow capture or return CLI status codes.

An LSQ-projected sample with no more observed markers than requested components
has `INSUFFICIENT_NONMISSING_MARKERS` status and NaN coordinates. The original
program omits that sample from its eigenvector output. Read every status before
using coordinates. A computed coordinate does not establish an ancestry label.

## Source and paper roles

The selected reference is original EIG tag `v7.2.1`, commit
`4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1`, from
[DReichLab/EIG](https://github.com/DReichLab/EIG/tree/4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1).
Its executable reports **smartpca 16000**; these are distinct package/program
identifiers. Source archive, all 159 member identities and original BSD license
are retained under `src/main/resources/org/jlinalg/omics/smartpca-4f7e9f0/`.
Original compiler warnings and the first failed header-path build are retained.
No scientific original source was patched. The private reference links GSL and
OpenBLAS; retain their installed metadata/licenses when transferring that runtime.
The legacy POPGEN README footer is retained alongside the selected root license;
reference use remains within the declared nonprofit/research purpose.

| Paper | Actual source role | Required distinction |
| --- | --- | --- |
| P019 | MVP PCs with EIGENSOFT v6; separately ROSMAP-to-1000G projection | Do not assign the MVP version to the separate unversioned ROSMAP call |
| P041 | Least-squares PCA during validation of ancestry masking, EIGENSOFT 7.2.1 | Mask construction, local ancestry and F4/ADMIXTURE analyses are separate |
| P045 | Unprojected/projected PCA with shrinkmode YES and r2thresh 0.7 | Shrinkage remains required; killr2 and the complete LD rule are unreported |
| P071 | Present-day-reference PCA with ancient projection, LSQ, no outlier iterations and shrinkage | Reference populations and shrinkage must remain explicit |
| P180 | PCA using reported EIGENSOFT 7.2.0; separately fstonly YES | Do not conflate PCA and FST calls or infer the separate FST edition; preserve the 100,000-SNP cutoff |
| P229 | PCA with literal SmartPCA v.13050 | This is not the selected 16000 executable or an inferred package version |

The [source packet](smartpca-native-development/ROLES.json) checks all 250 frozen
XML/DOI identities and eight bound paragraphs without redistributing full primary
text. Paper-level source roles are drafted, not applied to canonical reviews.
Selected-source validation does not recover unreported settings or establish
historical release equivalence.

## Use the prepared Java API

Calls are marker-by-sample bytes: **0, 1, 2 copies of the chosen reference allele;
9 means missing**. Alleles, marker/sample identities, autosomal diploid coding,
reference-population selection and upstream filters must already be checked and
retained by the caller. Fitting sample indices are ordered and unique. Projection
targets never contribute fitting allele frequencies. No RNG is used here.

```java
var options = SmartpcaPrepared.Options.prepared(2,
    SmartpcaPrepared.Projection.LEAST_SQUARES);
var result = SmartpcaPrepared.fit(markerBySample, fittingSampleIndices, options);
double[][] coordinates = result.scores();
// Check result.scoreStatus() for each sample before using its coordinates.
```

The complete synthetic [example](../../examples/smartpca-prepared/PreparedExample.java)
can be compiled against a newly built project JAR containing this component.
From the project root, substitute the exact new JAR path, and use NEW output:

```powershell
javac --release 17 -cp PATH_TO_NEW_PROJECT_JAR -d build/NEW-smartpca-example examples/smartpca-prepared/PreparedExample.java
java -cp "build/NEW-smartpca-example;PATH_TO_NEW_PROJECT_JAR" PreparedExample
```

On Linux use `:` instead of `;` in the runtime classpath. The example uses six
synthetic markers, four fitting samples and one target. It is not biological
validation. The private normal-build JAR path/hash and exact example commands are
recorded in the checkpoint receipts; existing release assets are unchanged.

`usenorm` and `altnormstyle` follow the executable C formula. With altnormstyle YES,
the denominator uses p=observed_mean/2. With NO, it uses
p=(observed_reference_alleles+1)/(2*observed_calls+2); centering still uses the
unmodified observed mean. A nearby original C comment reverses these descriptions;
the executable and original-run comparisons determine the implementation. Missing
basis calls contribute centered zero. Original covariance trace is scaled to
basis_sample_count-1. Returned eigenvalues retain numerical signs without clamping.

Requested axes must have positive finite eigenvalues. Components are limited to
less than the fitting sample count and no more than the supplied marker count;
unreportable/singular cases fail rather than returning a repaired inverse.
Explicit work/storage bounds apply to the complete matrix. Eigenvector signs may
differ, and repeated eigenvalues allow different bases of the same eigenspace;
coordinate identity is not guaranteed across eigensolvers.

## Evidence and next work

The [saved checkpoint](smartpca-native-development/README.md) retains nine actual
unchanged-program eigenvalue/GRM/coordinate comparisons across Windows Java 21/25
and Linux Java 21. Seven component test methods cover source normalization,
reference-only allele frequencies, input rejection, capacities, interruption and
defensive copies. Three additional original calls retain shrinkage, FST and a
zero-trace error as separate witnesses, without a native equivalence claim.
Original files print six decimal places for eigenvalues/GRM and four for ordinary
coordinates; comparisons use only the corresponding half-unit printing envelope.
This is not full internal double-precision equivalence.

Continue by integrating source outlier iterations, required shrinkage/FST branches,
typed EIGENSTRAT input, original/effective option binding, sealed RUN/VERIFY/REPLAY,
actual deadlines and contradictory-capture controls. Qualify the final producer
and exact prior-payload compatibility before catalog admission. Preserve unknown
versions/settings and original failures. The full 250-paper objective remains open.
