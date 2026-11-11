# scDRS numerical components: development checkpoint

**scDRS is still missing from the admitted toolbox.** This checkpoint adds the
native Java `org.jlinalg.genetics.ScdrsScores` API. The full typed adapter,
runtime admission, saved workflow verification, root command, downstream graph
workflow, and five paper-use reviews remain unfinished. There is no
`scdrs-tools` command in the released executable. The catalog remains at **98
new scoped integrations, 101 including three existing adapters, and 245 named
tools missing**. These counts describe the catalog, rather than completion of
the 250-paper implementation goal.

## Warnings and required actions

* **Missing or forbidden dependencies: SKIP3.** Skip dependent scDRS analysis.
  Resolve the named permission, network, Python, package or source problem
  where permitted, then repeat setup in **new private paths**. Setup does not
  change shared Python packages. Do not treat successful installation as a
  validated analysis or use a different scDRS version silently.
* **Invalid, nonfinite or unassessable results: FAIL2; NO RESULT ADMITTED.**
  Preserve the failed attempt and inputs. Resolve the stated data, numerical
  or logical-capacity cause and retry with a new output. Zero control SD,
  constant correlation annotations, a zero raw-score weight sum and invalid
  variance ratios are refused. The API adds no jitter, SD floor or data repair.
* **Historical categorical covariates differ.** In the retained controls,
  original 1.0.0 and 1.0.1 preprocessing fail on a text-valued sex covariate;
  1.0.2 converts it to a dummy column. The first failed reference attempt is
  retained. For the numerical reference fixtures, sex was explicitly generated
  as numeric 0/1. A user must define and document any categorical encoding of
  their own inputs. Do not infer an encoding or claim 1.0.2 reproduces a paper
  that reports 1.0.0 or 1.0.1.
* **The Java API receives prepared inputs.** Expression must already have the
  normalization and covariate correction appropriate to the analysis. It does
  not fit LOESS technical variances, create mean/variance bins, sample matched
  control gene sets, perform H5AD or species conversion, select genes using
  MAGMA, remove the MHC/APOE region, or build a neighbor graph. Do not substitute
  arbitrary controls or uncorrected counts for those inputs.
* **P-value families and precision matter.** Cell P values use pooled
  normalized controls; cell Monte Carlo P values use controls for that cell.
  Group association uses the **95th percentile in the original code**, despite
  an upstream CLI description saying 5%. The correlation test uses signed
  Pearson correlation. `benjaminiHochberg` adjusts exactly the supplied family;
  do not pool datasets or cell and group tests automatically.
* Logical capacities cover component work arrays, **not an OS memory quota**.
  This finite component supports at least two cells and two controls and
  refuses singleton groups. Java normal quantiles come from the separately
  pinned JDistlib dependency. Evidence hashes check integrity, not authenticity,
  biological validity or independent statistical refitting.

## What is implemented

`rawScore` implements uniform, variance-stabilized (`vs`) and inverse-standard-
deviation (`inv_std`) weighted scores. The latter two preserve scDRS's explicit
`+0.01` variance term. Signed gene weights and repeated supplied gene slots are
retained; the caller owns gene intersection, ordering and duplicate handling.
It returns the raw scores, normalized weights and independent gene variance.

`correctBackground` performs the original first gene-set alignment, cell-wise
population-SD standardization and second gene-set alignment. Exact zero raw
trait scores receive the final minimum minus 0.001; exact zero raw controls
receive the final minimum. `score` computes both empirical P values,
`-log10(P)` before float32 conversion, the source's explicit Z clipping to
[-10,10], and all normalized control columns. Cell identities retain their
literal input order, and result arrays are defensive copies.

`empiricalP`, `groupAssociation`, `correlation` and `benjaminiHochberg` are also
available. Group heterogeneity/Geary's C, overdispersion weighting and the
complete downstream workflow are not part of this native component.

## Use the separate development API

The canonical `build/cli/omicsmatic-0.3.7.jar` is unchanged. Use the API artifact
in `docs/literature/scdrs-native-development/` with the existing, separately
pinned `build/dependencies/jdistlib-all-0.10.2.jar`. The latter has SHA-256
`8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81`.

This complete Java example consumes **explicitly supplied synthetic raw
scores**, controls and variance ratios. It does not analyze an H5AD file:

```java
import java.util.List;
import org.jlinalg.genetics.ScdrsScores;

public final class ScdrsExample {
    public static void main(String[] args) {
        var result = ScdrsScores.score(
            List.of("cell-A", "cell-B", "cell-C", "cell-D"),
            new double[]{0, 1, 4, -2},
            new double[][]{{0, 4, 2}, {2, 0, 1}, {3, 1, 7}, {-1, 2, 0}},
            new double[]{1, 2, 0.5},
            new ScdrsScores.Limits(4, 3, 1_000_000));
        System.out.println(result.columns());
        for (float[] row : result.values())
            System.out.println(java.util.Arrays.toString(row));
    }
}
```

Save it as `ScdrsExample.java` in a new working directory. From the repository
root, with Java 17 or newer available on PATH, compile and run in PowerShell
(use the actual path to the saved example):

```powershell
$scdrsCp = 'docs/literature/scdrs-native-development/scdrs-api-javac21.jar;build/dependencies/jdistlib-all-0.10.2.jar'
javac -cp $scdrsCp ScdrsExample.java
java -cp ".;$scdrsCp" ScdrsExample
```

On Linux, use `:` as the classpath separator. The separate consumer artifact is
a reference-test harness and is not a production analysis CLI.

## Evidence and reproducible setup

The numerical controls use the unchanged, MIT-licensed historical sources:

| Version | Immutable upstream commit |
| --- | --- |
| 1.0.0 | `79986ff5415537248e84eb8a4a34befacedf699c` |
| 1.0.1 | `075527009a20f4f13287e2625a2828dc88785481` |
| 1.0.2 | `7f8f8d5754e1e3057916cd3fbe720b96f90c1c1b` |

The private reference uses Python 3.10.16, NumPy 1.23.5, SciPy 1.10.1,
pandas 1.5.3, Scanpy 1.9.3, AnnData 0.8.0, scikit-misc 0.3.1 and statsmodels
0.13.5. The full installed dependency freeze and installer logs are retained.
This is a tested dependency profile, not a claim to recover each paper's
unreported environment. NumPy's BSD-licensed pairwise float64 reduction order
is preserved in the Java background component; both upstream notices are
included in the development artifacts.

There are seven original-source numerical controls per version, including
three actual `score_cell` workflows per version with 4,200 genes, 96 cells,
20-by-20 requested mean/variance bins, numeric covariates where selected, and
200 or 1,000 matched control sets. Comparisons cover every output coordinate.
Windows Java 21, Windows Java 25 and Linux Java 21 each passed 2,522,943 value
comparisons and eight unit tests. All tested float32 table outputs agree
exactly; the maximum scaled double-intermediate error is below 4e-16. These
are synthetic controls, not reproductions of the papers' biological results.

Verify the complete saved evidence without installing Python packages or
rerunning the original analysis:

```powershell
python tools/literature/verify_scdrs_native_evidence.py
python tools/literature/verify_scdrs_native_evidence.py --replay --java java --out build/literature-review/scdrs-runtime/saved-replay-user-1
```

The second command needs Java 17 or newer and the pinned JDistlib file; its
output path must be new. It replays the Java numerical controls against saved
references. It does not refit the original preprocessing or certify an
upstream runtime. Keep every prior attempt.

For development, source intake, private setup, isolated builds and live
reference validation are provided by the `*_scdrs_*.py` helpers in
`tools/literature/`. Setup requires both `--allow-download` and
`--allow-install`, an existing micromamba binary, the named WSL distribution,
and a new `/var/tmp/omicsmatic-scdrs-...` private root. This environment can
remove temporary directories; preflight the recorded runtime and recreate it
explicitly if absent. Read each attempt's `COMPLETED.json`, process logs and
`WARNINGS.md` before proceeding.

Primary upstream references:
[scDRS source and version history](https://github.com/martinjzhang/scDRS),
[historical 1.0.2 scoring source](https://github.com/martinjzhang/scDRS/blob/7f8f8d5754e1e3057916cd3fbe720b96f90c1c1b/scdrs/method.py),
[NumPy 1.23.5 reduction source](https://github.com/numpy/numpy/blob/v1.23.5/numpy/core/src/umath/loops_utils.h.src).
