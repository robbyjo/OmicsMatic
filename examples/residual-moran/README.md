# Native residual Moran example

From the repository root, build current source and prepare a new work directory:

```bash
./gradlew executableJar
python3 -B examples/residual-moran/prepare.py --out build/moran-example
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar monocle-tools MORAN-LM \
  --request build/moran-example/request.json
```

Windows users use `./gradlew.bat` and their permitted Python command. No R,
Monocle, download or installation is required for this native example. Existing
work/result directories are preserved and refused.

On Windows, keep the **request pathname ASCII** if the Java launcher loses
non-ASCII command-line characters. The helper supports `--request NEW_ASCII.json`
to place the request separately from a Unicode `--out` work directory. Exact
Unicode input/output paths are then declared inside UTF-8 JSON; no files or
identifiers are renamed. See the worked guide for the complete example.

The checked synthetic inputs contain 60 observations, six explicit design
columns and three prepared gene responses. `intercept`, BatchB, BatchC, SexM,
PMI and log_n_genes define the declared model. The graph has reciprocal unit
weights on a ring; the method symmetrizes supplied directed weights and does
not row-normalize them. Factor contrasts and response preparation are explicit
example choices, not inferred biological defaults.

Read `result/WARNINGS.md`, require exit 0 and `COMPLETED.json`, and check
`analysis.json` with `result_admitted: true`. `tests.json` and quoted `tests.tsv`
preserve gene order. `PROVENANCE.json` records the exact input identities,
original-spdep fixture and expected Moran statistics, probabilities and BH
values for comparison. These are software controls, not paper data.

**SKIP3:** provide permitted accessible input/output paths. **FAIL2:** no
complete family is admitted; preserve inputs/partials, correct the named
cause and retry NEW output. Do not remove genes or cells to force a pass.
When probabilities reach numeric zero, retain log probabilities and read
`NUMERIC-WARNING.txt` rather than treating zero as an exact null probability.

The [worked guide](../../docs/vignettes/covariate-aware-moran.md) explains the
model, input formats, isolated-observation behavior, capacities and independent
validation. Released 0.4.0 availability and complete Monocle/paper integration
remain separate.
