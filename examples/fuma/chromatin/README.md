# Synthetic chromatin-interaction mapping example

From the repository root, use the exact development candidate in
`docs/literature/omics-2023-2026/fuma-chromatin-validation.json`:

```text
java -jar JAR fuma-tools --mapping-options-file examples/fuma/chromatin/options.json --out build/fuma-chromatin-example-NEW
```

Only Java and local files are required. Every resource is synthetic. Shared
genes/candidates/annotations come from `examples/fuma/mapping/`; the interaction,
enhancer and promoter tables are supplied here. The example explicitly selects
per-interaction rsID membership and source-closed coordinates, replaces original
gene lists using exact promoter keys, and restricts candidates by enhancer UIDs.
It produces four declared genes, five interaction/variant/gene evidence rows and
four actual mapped candidates. Read WARNINGS.md and all interaction dispositions.
This is not chromatin-interaction discovery, annotation acquisition, FDR testing,
causal inference or full SNP2GENE. Use a new output directory for every attempt.
