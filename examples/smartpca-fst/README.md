# Native source FST examples

These12-individual,48-marker synthetic files are exact inputs from the unchanged
original program's `diploid-complete` reference case. Three populations are
declared in the individual file. They are not cohort data. Read
[the complete guide and warning remedies](../../docs/literature/SMARTPCA_TOOLS.md).

From the project root with a freshly built complete JAR:

```powershell
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --request examples/smartpca-fst/run.json
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --verify build/smartpca-fst-example
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --request examples/smartpca-fst/replay.json
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --verify build/smartpca-fst-replay-example
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --request examples/smartpca-fst/inbreed.json
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --verify build/smartpca-inbreed-example
```

The outputs must be NEW; preserve old captures and change `out` before repeating
a run. `inbreed.json` explicitly selects the source inbreeding estimator. Read
`WARNINGS.md`, status and each field's admission flag. Negative finite source FST
is retained. Source zero diagnostics with too few blocks or no denominator are
ineligible; they must not be reported as identified estimates. SKIP3 skips
dependent work; FAIL2 admits no complete result. No external EIGENSOFT runtime is
required. Keep the exact producer for future VERIFY/REPLAY.
