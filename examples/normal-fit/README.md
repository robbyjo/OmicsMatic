# Analytical normal fit and explicit background tests

Use [the guide](../../docs/literature/NORMAL_FIT_WORKFLOWS.md) to build the separate development JAR and run this request from the repository root. The request resolves input paths relative to this directory and refuses an existing output. Java alone runs the analysis; nothing is installed.

These are synthetic values, not paper data or recommended scientific settings. Expected fit: mean0, SD1, population variance1. All five test rows remain in input order. Only far-upper meets the declared ordinary BH q<0.001 rule. Its linear p/q underflow to zero; finite log tails remain available, and WARNINGS.md prominently explains the precision limit.

Admission does not validate normality, the supplied background, BH calibration, biological significance or a retention rule. Original numeric strings and literal IDs remain captured. Missing/nonfinite values and constant backgrounds fail without omission or rescaling.
