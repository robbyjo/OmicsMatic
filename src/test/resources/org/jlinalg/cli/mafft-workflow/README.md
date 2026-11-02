# Complete historical MAFFT workflow controls

`initial-controls.zip` preserves three complete synthetic analysis bundles from
the development checkpoint published in commit
`0d59f88b3f24d866a61a02abc9de878d5a480cfa`. Its SHA-256 is recorded in
`initial-controls.sha256` and independently fixed in MafftWorkflowBundleTest.
The archive has 7,559 members and contains no upstream executable distribution.

The cases are nucleotide auto alignment (7.526), addition to a single protein
backbone with keep-length and a complete map (7.526), and expanding protein
addition with global-pair alignment and a 100-iteration maximum (7.505).
They retain original/prepared inputs, full raw outputs/maps, headers/case,
residue/taxon ledgers, recipes, process/host probe receipts and complete bundle
inventories. Repeated cases use tiny synthetic families, not paper datasets.

Current tests verify every complete bundle without an aligner or its original
host, and reject rehashed inconsistencies in inputs, recipes, commands, limits,
restored cells, maps, ledgers and recorded payload metadata. The earlier process
ledger and slightly later return receipt have distinct elapsed-time observations.
Portable verification checks their order and preserves both raw values.

These checks establish declared provenance and source/output consistency. They
do not authenticate a file supplier, establish a hermetic host, measure biological
alignment accuracy or reconstruct any paper. Runtime setup is a separate gate.
Keep the historical bytes; do not replace this archive with a later run.
