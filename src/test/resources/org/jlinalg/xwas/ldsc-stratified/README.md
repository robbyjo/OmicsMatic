# Stratified LDSC reference fixtures

`reference-fixtures.zip` contains 48 synthetic prepared models, complete original
reference outputs and 22,596 individually checked scalar fields. The source is
unchanged CBIIT LDSC core commit `6c673952cee74bd5c57aef1555a03b1c015399a0`
(package edition 3.0.2), using its partitioned `old_weights=True` path and
`Hsq._overlap_output`. Original module hashes and exact Python/NumPy/SciPy/pandas
versions are inside `provenance.json`.

`generate_reference.py` is the exact generator used for the final reference.
It loads the unchanged numerical modules through a namespace package, bypassing
the unrelated web initializer. It replaces no original functions. Its fixed
seed creates prepared synthetic data, rather than author GWAS inputs. The saved
qualification includes commands, full reference JSON and prior failed tests.

Cases include 2..9 joint annotations, disjoint/overlapping binary resources,
variable N, free/fixed intercepts, signed LD estimates and coefficients, negative
heritability, above-one heritability and initial weight clamping. All-variants
baseline enrichment P values remain NA. Transformed baseline uncertainty can
reflect cancellation roundoff; the full underlying covariance matrices are
checked separately, and the test records an explicit roundoff bound without
rewriting either runtime's values.

The original source is GPL-3.0; the native implementation and generator use
GPL-3.0-or-later. NumPy, SciPy, pandas and Python are reference-development
dependencies. End-user analysis uses Java.
