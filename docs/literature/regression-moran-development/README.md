# Native regression-residual Moran checkpoint

Use the [worked guide](../../vignettes/covariate-aware-moran.md) for the source-build
command, complete input contracts, sample-size choices, explicit capacities,
API and recovery. The native method supplies fitted-design residual Moran
inference on prepared weights/responses; it does not calculate the paper's
historical Monocle graph or infer biological preprocessing choices.

`VALIDATION.json` records an original-spdep reference whose installed function
body/formals match official spdep 1.4-1 source. There are 108 gene comparisons
across 36 graph/design/alternative cases, including isolated observations,
explicit factor models and a 10000-observation sparse graph with finite log
tails after ordinary probabilities underflow. All 108 comparisons and BH values
were checked through the actual final packaged command. Linux controls use
an empty executable search path. The documented 60-observation example matches
its original expected values and preserves Unicode data/output paths.

The initial integration ran 559 focused tests. After strengthening fixture
identity/independent-refusal tests and adding the Windows recovery guidance,
all 12 native-core/CLI tests passed on the selected current source. They overlap
the initial 559 tests; these are not 571 distinct tests. The final package keeps
all earlier scientific entries byte-identical; only the Monocle root dispatch
changes, with new native classes added. Existing numerical-class verification
also passed through the final package.

**Windows:** the tested Cp1252 launcher changes beta in a command-line request
pathname before main receives it. Put the request at an ASCII pathname; use
`prepare.py --request NEW_ASCII.json` to keep exact Unicode data/output paths
inside UTF-8 JSON. Files and identifiers are not renamed. Earlier skips and
launcher code-point diagnostics remain retained. See `WARNINGS.md`.

`controls.zip` is an ordinary ZIP with an `INDEX.json` size/SHA-256 record for
every payload. Every member is read back and matched before acceptance. It
contains complete new scientific inputs/outputs, original reference/source
records, current code/fixtures and retained failed/skipped attempts. Expand
into a NEW directory with ordinary ZIP tools. Large compiled readers and
installed runtimes remain separately provisioned dependencies; this archive
is not a complete runtime distribution. See `UPSTREAM_CREDITS.md`.

This is local source-build availability. The released 0.4.0 JAR is unchanged.
No whole-Monocle family badge or new catalog credit is inferred. The remaining
Monocle roles and full 250-paper implementation goal remain open.
