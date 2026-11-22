# Synthetic supplied-resource FUMA workflow

Run from the repository root using the development candidate named in
`docs/literature/omics-2023-2026/fuma-workflow-validation.json`:

```text
java -jar JAR fuma-tools --method workflow --workflow-options-file examples/fuma/workflow/options.json --out build/fuma-workflow-example-NEW
```

Paths inside this recipe resolve relative to the recipe directory. An explicit
CLI `--out` resolves relative to the current directory. The output must be new.
This example uses synthetic IDs, coordinates, scores, LD and gene sets. Its score
values illustrate text preservation; they are not biological estimates.

Read the root and every stage's `WARNINGS.md`, `workflow-journal.json`,
`manifest.json`, `analysis-status.json`, resource and ID disposition tables.
Only actual mapped links enter the derived gene-set query. Supplied annotations
are matched to canonical locus UIDs and coordinates; no annotation is inferred.

Exit 0 means every selected stage completed with review warnings. Exit 3 means
one or more stages were skipped. Exit 2 means a failure occurred. Independent
completed stages remain retained, but a failed or partially skipped workflow is
not admitted as complete. Correct the named cause and rerun to a new directory.

MAGMA is optional and uses separately supplied genome-wide annotation, GWAS and
reference inputs plus the permitted original runtime. It does not receive the
locus-filtered candidate subset. See `FUMA_TYPED_WORKFLOW.md` in the catalog.
Cell Ranger remains excluded by the user.
