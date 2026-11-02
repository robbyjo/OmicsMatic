# Public composite proxy example

Follow [the command guide](../../docs/literature/MASHR_PROXY_COMMAND.md) to create
the synthetic native A/B bundle, copy the four request TSV files, run the proxy
analysis and verify it. Copy only `*.tsv` into the request directory; its exact
file graph is checked, so do not copy this README or `.gitattributes` there.

The example declares five patterns, including the source's empty ALL/ANY
identities. This synthetic two-condition demonstration does not reproduce a
paper's biological data or its eight disease contrasts. Resolve real groups and
contrasts explicitly. Always read the emitted warnings and numeric diagnostics.
