# Native GSEApy local enrichment statistics

`org.jlinalg.enrichment.GseapyStatistics` implements the original local
over-representation arithmetic for explicitly prepared gene sets. It supports
the four paper-reported editions **1.0.4, 1.1.2, 1.1.3 and 1.1.8**. Select an
edition explicitly; the code does not guess an unreported paper version.

This is a Java statistics component. GSEApy remains missing in the catalog until
its execution workflows are implemented and validated. Current catalog totals
remain **87 new scoped integrations**, **90 including existing adapters**, and
**254 missing tools**. The canonical executable is unchanged. Native use needs
Java 17 or newer and the separately built development artifact; it needs no
Python, network access or installation of GSEApy.

Use the validated development JAR and run the complete example from the project
root in PowerShell:

```powershell
$gseapyJar = 'build/literature-review/gseapy-runtime/statistics-build-3/omicsmatic-0.3.7-gseapy-statistics-development.jar'
java -cp $gseapyJar examples/gseapy-statistics/GseapyOraExample.java
```

The example keeps a six-gene explicit background, removes duplicate query/set
members using literal set semantics, and returns two overlapping pathways.
Their one-sided P values are `0.6` and `1/15`. The zero-overlap pathway is omitted
before correction, as in original `stats.calc_pvalues`. Its omission changes
the correction family: do not add excluded terms back just to apply a generic
whole-library correction. This is a source behavior, not a recommendation about
the statistical family that a particular study should choose.

The public API accepts a query collection, a map of term identifiers to gene
collections, an explicit `Universe` or `Population`, an edition and an alpha.
A universe intersects both query and term sets before testing. A numeric
population cannot filter identifiers and can be incompatible with the supplied
margins. There is no default population. Identifiers retain case, whitespace,
Unicode and empty-string identity; no aliases or species mapping are inferred.
Term/hit presentation follows Unicode code-point order. Hit membership matches
the original sets; Python hash-dependent semicolon order is not reproduced.

**Warnings and required actions:** display every `Row.warnings()` message to the
user. An undefined original test remains `NaN`; preserve all returned terms,
correct the explicit background on a new analysis, or skip dependent inference.
Do not remove undefined rows or fabricate P values. A floating-point zero tail
and any infinite combined score are retained; do not insert an epsilon, clip
the score or call the tail an exact zero probability. Invalid Java input throws
before the API returns a result list; callers must not present earlier work in
a failed batch as an admitted complete analysis.

The original **1.0.4 odds formula differs** from the formula in the other three
editions. Its local high-level result also lacks a combined-score column; the
native row exposes `combinedScoreAvailable=false` and an unavailable `NaN`
placeholder. Later editions use the original `-log(P) * odds` combined score.
These odds are source-specific corrected expressions, not an unconditional
claim of an uncorrected Fisher odds estimate or an Enrichr service Z score.

`correct` implements the source Benjamini–Hochberg and Bonferroni helpers on an
explicit supplied family. NaNs remain NaN in both adjusted values and the
numeric rejection mask. BH uses inclusive rejection at the threshold;
Bonferroni uses strict rejection and retains adjusted values above one. This
differs from helpers that always cap Bonferroni values at one. Returned arrays,
sets and row membership cannot be modified through caller-owned references.

The development controls execute the four unchanged original `stats.py`
modules with the bound Python 3.11.17, NumPy 1.26.4 and SciPy 1.11.4 runtime.
They cover 432 set-enrichment cases, 27,608 probability states and 648
correction families, including invalid numeric margins, empty and duplicate
sets, literal Unicode identifiers, rare/underflow tails and NaNs. Independent
exact rational combinatorics checks all 27,600 small-population tails.
The complete runtime inventory is checked against its original bound baseline
before and after the reference run. Native numerical comparisons use a declared
relative bound of `5e-12` for tails and adjusted P values, `1e-15` for direct
correction arithmetic, exact odds expressions, and exact zero/nonfinite kinds.
Combined scores use the measured tail difference propagated through the source
logarithm, plus 16 ULPs for NumPy-versus-Java log rounding. This distinction
matters near P=1, where a small tail rounding difference can have a larger
relative effect on `-log(P)`. It is not a universal relative-error certificate. This is
arithmetic validation, not independent biological calibration.

The six primary-source role drafts are retained with the evidence. Three
papers name Enrichr with particular resources/backgrounds. P062 does not identify
its exact GSEA mode. P079 reports gene set variation analysis and a separate
enrichment analysis; neither paragraph establishes a specific `gsva` or `ssgsea`
call. P098 reports a **two-tailed hypergeometric test**, while the original local
GSEApy function uses a **one-sided upper tail**. That discrepancy remains
unresolved; it must not be silently relabeled as a supported paper method.
The draft does not change existing catalog reviews or paper identities.

High-level case conversion, organism mapping, remote Enrichr requests, historical
library recovery, preranked/phenotype GSEA, GSVA, ssGSEA, permutation inference
and report workflows remain separate implementation work. Downloading a wheel
does not establish those capabilities. If the original runtime or permitted
installation is unavailable, original-dependent analysis must report **SKIP3**
and the exact recovery action. A failed or incomplete requested execution must
report **FAIL2**, preserve its diagnostics and admit no partial batch.

Original Python sources, metadata and MIT licenses are bundled unchanged under
`org/jlinalg/omics/gseapy-statistics/original/`. New Java code is GPL-3.0-or-later.
The exact official PyPI wheels, hashes and source relationships are retained in
the source profile. No full GSEApy package or paper reproduction is claimed.
