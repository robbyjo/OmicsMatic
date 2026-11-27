# Native rMATS result selection

Generate a synthetic post-fit fixture, then run the packaged Java command:

```text
python examples/rmats-results/create_example.py NEW-input.tsv
java -jar OMICSMATIC.jar rmats-results --input NEW-input.tsv --event SE --mode JC --group1 2 --group2 2 --max-p 0.05 --min-abs-dpsi 0.05 --out NEW-result
java -jar OMICSMATIC.jar rmats-results --verify NEW-result
```

Expected: eight rows; three SELECTED, three EXCLUDED and two UNASSESSABLE.
Both original ID columns and all source tokens remain intact. The fixture
exercises selection boundaries and missing values; its post-fit fields are
synthetic contract cases, not outputs of a fitted biological model.

Read WARNINGS.md, summary.json and all unassessable.tsv rows. The command
selects on raw P < maximum and absolute source delta PSI >= minimum; it never
uses FDR in place of raw P. Replicate counts must describe the actual groups.
No R, Python or rMATS installation is needed for the Java selection command.
Read the [guide](../../docs/rmats-results.md) for scope, limits and failures.
