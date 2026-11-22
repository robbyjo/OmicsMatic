# Synthetic FUMA positional/eQTL mapping example

Run from the repository root using the exact development candidate in
`docs/literature/omics-2023-2026/fuma-mapping-validation.json`:

```text
java -jar JAR fuma-tools --mapping-options-file examples/fuma/mapping/options.json --out build/fuma-mapping-example-NEW
```

Only Java is required. All coordinates, genes, candidates, annotation scores and
associations are synthetic. The supplied eQTL file deliberately includes nonsignificant
rows: this stage receives upstream-selected evidence; `eqtl-p/fdr=none` applies
no additional significance filter. Inspect every input and `WARNINGS.md`.
The baseline produces six genes and retains both positional and eQTL evidence.
Candidate and annotation CADD/RegulomeDB values deliberately differ, and missing
CADD remains NA. Unknown genes in supplied evidence are excluded by the gene resource.
Use a new output directory for each attempt. This is not full SNP2GENE, gene scoring,
chromatin mapping or historical paper data. The original source's single-eQTL-track
failure and MHC endpoint behavior are retained in the public reference fixture.
