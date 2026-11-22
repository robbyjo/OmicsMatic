# Synthetic FUMA expression and DEG workflow

This example supplies already computed values and DEG memberships. It does not
derive expression or differential expression from biological samples.

From the repository root, using the scoped development candidate:

```text
python examples/fuma-expression-workflow/prepare_inputs.py --out build/fuma-expression-example-NEW
java -jar build/literature-review/fuma-expression-candidate-1/jlinalg-0.3.7.jar fuma-tools --method expression --expression-options-file build/fuma-expression-example-NEW/options.json
```

The generated `workflow.json` also runs this as a journaled stage:

```text
java -jar build/literature-review/fuma-expression-candidate-1/jlinalg-0.3.7.jar fuma-tools --method workflow --workflow-options-file build/fuma-expression-example-NEW/workflow.json
```

Query genes E1/E2 are tested against four background genes. The supplied Liver.up
set has both genes: raw P is 1/6, adjusted P is 1/3 because the up family includes
an empty second row. The down and two-sided families are corrected separately.
The declared count 99 is audited, then recomputed as 2 from background memberships.
Expression rows retain resource order E2/E1 and unchanged values.

Read `WARNINGS.md`, `session.json`, `deg-tests.tsv` and both expression tables.
Exit 2 means failure: inspect `partial/` and correct the named cause before
rerunning into a new directory. Existing output is preserved. No software needs
installation beyond Java for analysis; Python is only used to prepare this example.

The complete reusable audit is:

```text
python tools/literature/verify_fuma_expression_workflow.py --jar build/literature-review/fuma-expression-candidate-1/jlinalg-0.3.7.jar --work build/fuma-expression-verification-NEW
```

See [the resource and warning guide](../../docs/literature/omics-2023-2026/FUMA_EXPRESSION_WORKFLOWS.md).
