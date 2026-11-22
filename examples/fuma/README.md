# Native FUMA gene-set module example

All genes, intervals, memberships and trait names here are synthetic fixtures.
These are not MSigDB, GWAS Catalog or paper data. No database is downloaded.
Run from the repository root, replacing JAR with the development candidate in
`docs/literature/omics-2023-2026/fuma-native-validation.json`:

```text
java -jar JAR fuma-tools --method gene-set --annotations examples/fuma/genes.tsv --query examples/fuma/selected.txt --biotypes all --gmt-manifest examples/fuma/collections.tsv --annotation-id synthetic-20-gene-fixture-v1 --exclude-mhc false --correction fdr_tsbky --adjusted-cutoff 0.05 --min-overlap 2 --duplicates error --out build/fuma-example-NEW
```

Read `WARNINGS.md`, `id-mapping.tsv`, `all-tests.tsv` and `session.json`.
Each GMT is its own correction family; hidden/zero-hit rows remain in the audit.
Use `mixed-namespace.txt` to inspect the original global symbol priority: only
G001/G004 are selected; Ensembl/numeric tokens do not get converted independently.
Every output directory must be new. The command requires only Java.
The gene-set module does not implement the broader FUMA platform.
