# Native independent-sample Dunn checkpoint

The [command guide](../../vignettes/dunn-comparisons.md) supplies a copyable
request and input, model assumptions, explicit rank/comparison families and
required recovery actions. Native execution requires Java only. This is a
source-build feature; released artifacts and vendor-family admission remain
unchanged. The full 250-paper implementation goal remains open.

All 250 frozen primary XML identities are checked by
`tools/literature/common_statistics/review_sources.py`. Nine papers contain the
name Dunn: eight describe statistical uses and P035 names an author. The source
crosswalk distinguishes the P006 ambiguous method menu, ordinary Kruskal–Wallis
uses, P122 Bonferroni, P167 Holm, additional outer correction in P196 and
unresolved settings. No author design or default is inferred. The review also
separates nine Dunnett papers, including repeated-measures and unequal-variance
contexts; this Dunn implementation does not supply those different tests.

The same review found a real catalog omission: P215 repeatedly names bare
Prism, including two-sided unpaired t-tests on biological-replicate means. The
existing family expression omitted it. `SOURCE_REVIEW.json` retains five exact
source pointers and marks canonical curation pending. There are 47 name-matching
Prism papers versus 46 in the admitted inventory. Main catalog observations and
badges have not been silently regenerated or promoted.

`VALIDATION.json` records the tested executable, focused tests, complete
reference checks, bundle identities and scope. The oracle contains 4,557
comparisons across 36 independent models and seven adjustments, using all-pair,
first/last-control and selected/reversed families. It covers unequal sizes,
ties, singletons, constant individual groups, equal mean ranks, signed zeros,
extreme finite data, nearly-all-tied populations, positive subnormal and
underflowed probabilities. Base R 4.6.1 supplies ranks, variance, Kruskal–Wallis,
normal/log tails and standard `p.adjust` calculations. Original dunn.test 1.4.2
functions supply raw z/P comparisons and separately retained adjustment output.

**Original reference differences are not repaired or concealed.** The original
CRAN archive is GPL-2, size 16,790 bytes, SHA-256
`ca0f057c7f048713ae9ac75e5ebf5f3ec0c521d11d3585e1223bbdaeb3612cfc`.
Its DESCRIPTION says 1.4.2 while `dunn.test.R` literally says 1.4.1. All original
files remain unchanged. The selected comparison audit retains:

- 423 adjusted-value differences from the original noncumulative stepwise
  calculations. Native standard Holm/BH/BY values are checked against base R;
  Holm–Šidák enforces its stepwise cumulative rule.
- 15 original Šidák/Holm–Šidák cancellation-to-zero results where the stable
  calculation remains positive. Log-space tails remain explicit.
- 20 original two-group stepwise method errors from dropping a one-column
  matrix dimension. They are retained as original errors with no original
  result; the package is not patched to make them pass.

The API's ordinary Dunn model and standard corrections are therefore the
qualified scope. Whole dunn.test-package, historical Prism-edition and
paper-specific numerical equivalence are not claimed. Original failures and
the independent standard reference have distinct records. The ordinary
post-hoc normal approximation remains an approximation at small sample sizes.

`controls.zip` holds indexed source/fixture snapshots, unchanged original CRAN
source, full original and independent reference tables, primary-source
crosswalks, test/command receipts and unsuccessful attempts. Every member is
read back against its size/SHA-256 index. Executable JARs, R/Java installations
and installed rlang binaries are omitted; the tested artifact identity is
recorded separately. The bundle is evidence, not a runtime installer.

Independent oracle generation uses the unchanged source directory from the
pinned archive and an available permitted Rscript:

```sh
Rscript --vanilla tools/literature/common_statistics/dunn_reference.R \
  ORIGINAL_EXTRACT/dunn.test NEW_REFERENCE_DIRECTORY
```

Use a C locale for the recorded reference profile. R/rlang are optional
development requirements, rather than native-analysis dependencies. If a
needed installation or source retrieval is unavailable/forbidden, skip its
dependent comparison and preserve an actionable warning. Do not substitute a
different model or relabel an unavailable reference as passed. The complete
source/function identities and raw output remain in the evidence bundle.
