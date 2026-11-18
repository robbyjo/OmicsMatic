# Upstream references and licenses

Alexis Dinno's original [CRAN dunn.test](https://cran.r-project.org/package=dunn.test)
1.4.2 source archive is retained unchanged, including its DESCRIPTION,
copyright and GPL-2 declaration. A separate complete GPL-2 license accompanies
the evidence. The archive identity and every extracted source file hash are
recorded. Its unchanged functions execute as development references under
R 4.6.1 with the installed rlang edition recorded in RUNTIME.txt. Original
errors and differing adjusted values are retained; no function is patched.
The literal 1.4.1 header in dunn.test.R is preserved beside the 1.4.2 package
declaration. This is not a historical author-runtime reconstruction.

The [GraphPad Dunn methods guide](https://www.graphpad.com/guides/prism/latest/statistics/stat_how_the_dunn_method_for_nonpar.htm)
documents pooled ranks, ties, normal-reference probabilities and Bonferroni
adjustment. It is a method reference, rather than a software dependency or
permission to redistribute Prism. No Prism executable, source or author data
was downloaded or installed for this native implementation. Licensed Prism
runtime equivalence is not established.

Independent base-R references use `rank`, `var`, `kruskal.test`, `pnorm` and
[`p.adjust`](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/p.adjust.html).
The original R/rlang runtime is not redistributed in the controls bundle.
Installed runtime edition, paths and original source identities are recorded
for the performed checks. The separate reference worker and native Java
implementation are project GPL-3.0-or-later code. No original dunn.test R
function body is incorporated into production Java; no R/rlang runtime is
required for native analysis.

Native Bonferroni/Holm/BH/BY log adjustments reuse the existing project
adjustment engine. The Dunn profile has an explicit stable Šidák route to keep
subnormal log tails without rounding through an intermediate probability.
Its original-package comparison intentionally preserves cancellation and
stepwise-method differences rather than using the native output as an oracle.

Primary paper identifiers and hashes in SOURCE_REVIEW.json refer to the frozen
250-paper cohort. Public use summaries are paraphrases. Complete original
paper XML remains in the separately retained source cache; no long article
excerpts are redistributed in this development bundle.
