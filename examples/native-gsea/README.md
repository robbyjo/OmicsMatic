# Synthetic native GSEA example

Follow the [workflow guide](../../docs/vignettes/gsea-native-workflow.md). Copy this
directory to NEW working space before running, so generated captures stay separate
from source examples. `run.json` runs nine synthetic 10,000-permutation analyses;
`verify.json` and `replay.json` consume `run-out` and write new sibling captures.

The inputs are source-counterpart validation data, not a biological experiment or
author dataset. No GSEA installation or network download is needed. The exact
native profile, complete ranking/randomization settings, class order, literal
identifier universe, gene sets, provenance and resource limits are declared.

Inspect **`WARNINGS.md`** and `analysis-status.json` first. SKIP3 means a named
permitted prerequisite must be supplied or the analysis kept skipped. FAIL2 means
NO COMPLETE RESULT; retain inputs/partials/diagnostics and retry NEW output after
correcting the named cause. Do not change identifiers, editions or algorithms to
make a failed computation appear complete.
