# Native molecular geometry examples

Run from the project root with the validated development executable
`build/literature-review/molecular-geometry-candidate-1/jlinalg-0.3.7.jar`.
Its SHA256 is `798d3a08b64107eb7a7679359902efae3591f6ae5a89075f8e9a428698cc32b3`.

```
java -jar build/literature-review/molecular-geometry-candidate-1/jlinalg-0.3.7.jar molecular-geometry --options-file examples/molecular-geometry/measure.json
java -jar build/literature-review/molecular-geometry-candidate-1/jlinalg-0.3.7.jar molecular-geometry --options-file examples/molecular-geometry/fit.json
```

Choose a new `--out DIRECTORY` when repeating a run. Direct values override the recipe.
The measurement file uses literal tabs and empty unused trailing fields. Expected
values are5 Angstrom,90 degrees and+90 degrees. The five fit pairs encode a proper
quarter-turn and translation;RMSD is zero up to floating-point precision.

Read `WARNINGS.md`, `analysis-status.json`, `session.yaml` and the retained pair table.
This is native coordinate arithmetic; PyMOL installation is unnecessary. See
[the full guide](../../docs/literature/omics-2023-2026/MOLECULAR_GEOMETRY_WORKFLOWS.md)
for units,nonunique fits,resource limits and failure recovery.
