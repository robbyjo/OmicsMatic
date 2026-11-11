# Synthetic sample-level expression preparation

Follow [FUMA_EXPRESSION_PREPARATION.md](../../docs/literature/FUMA_EXPRESSION_PREPARATION.md).
The Java helper requires a NEW work directory. Copy `workflow.json` into that
directory; its paths resolve relative to the JSON file.

Expected preparation: five input samples, four explicitly selected samples,
two labels in B/A order with1/3 samples, four audited genes and two retained genes.
Only one selected value is capped. The large excluded-sample values do not affect
eligibility or transformation. ENSG2 has raw mean exactly1 and is excluded by
the strict boundary; ENSG4 is unexpressed. Gene-version suffixes are retained.
MAGMA `Average` differs from the sample-weighted centering value.

The DEG memberships are independently supplied synthetic input. This example
does not generate DEGs. It exercises the handoff into the existing expression/
DEG enrichment command, including its separate populations and corrections.
Read every warning; these data establish no biological result or historical
GTEx/FUMA resource equivalence. No external runtime is needed for this example.
