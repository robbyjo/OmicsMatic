# Synthetic hardcall reference workflow

Use the candidate and commands in [FUMA_REFERENCE_WORKFLOW.md](../../docs/literature/FUMA_REFERENCE_WORKFLOW.md).
`CreateInputs.java` requires a NEW work directory and Java17+. No download or
external analysis runtime is needed. The JSON workflow resolves paths relative
to its own location, so copy `workflow.json` into the created work directory.

Expected result: four selected founders, six captured variants, three retained
variants, two assessed/stored LD pairs, and three explicit marginal exclusions.
The all-heterozygous row has MAF0.5 but constant calls. The 100–1101 pair is outside
the 1000bp window and remains uncomputed despite the identical synthetic calls.
Two selected GWAS variants therefore remain protocol-separated; this is **not
evidence of biological independence**. Review every warning before using output.

The work directory is disposable synthetic data, but failed run folders should
be retained when diagnosing a failure. Commands preserve existing destinations.
