# Supplied-reference FUMA locus example

These are synthetic variants, frequencies and LD pairs, not a biological reference
panel or paper data. Run from the repository root, using the exact development
candidate in `docs/literature/omics-2023-2026/fuma-locus-validation.json`:

```text
java -jar JAR fuma-tools --locus-options-file examples/fuma/loci/options.json --out build/fuma-loci-example-NEW
```

Only Java is needed. Every output directory must be new. The JSON contains string
options, explicit assembly/population label, LD floor, MHC choice, thresholds,
tie policy, reference inclusion and merge boundary. Read every WARNINGS.md and
the candidate/LD/disposition/independent/lead/locus tables.

Expected native result: eight candidates, four independent variants, four leads
and one complete locus spanning positions 50–3000. The independent LD groups
have long, crossing physical spans. `retained-original-loci.tsv` is the actual
unchanged pinned original function output for these inputs: it leaves two
overlapping loci after a partial backward merge. It is retained evidence of the
upstream defect and must not be used as a valid locus partition. Native interval
components intentionally correct that defect and preserve all memberships.

The full FUMA platform and variant annotation remain unfinished. This example
does not estimate LD, download resources, lift genome builds, run ANNOVAR/MAGMA
or contact a FUMA service. Supplied P, MAF and LD are explicit synthetic inputs.
