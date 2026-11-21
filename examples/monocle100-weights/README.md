# Monocle1.0.0 weights and residual Moran example

This complete synthetic example uses all **27 cells, 74 directed links and three
genes**. It runs a new Java weight-selection stage followed by the existing native
residual-Moran test. It performs no R installation or execution. The supplied
rankings come from an actual original Monocle3 1.0.0/RANN control; this Java stage
does not calculate nearest neighbors or learn a graph.

Build current source, then choose a NEW ASCII-path working directory:

```bash
./gradlew executableJar
python3 -B examples/monocle100-weights/prepare.py --out build/monocle100-example
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.Monocle100WeightsCli --request build/monocle100-example/weights-request.json
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar monocle-tools MORAN-LM --request build/monocle100-example/moran-request.json
```

On Windows use `gradlew.bat` and your permitted Python command. The released 0.4.0
JAR predates this component; use a current source build. The first command is a
standalone class in that executable JAR. It is not a new `monocle-tools` action.

Require exit 0 and `weights/COMPLETED.json` plus `weights/analysis.json` with
`weights_admitted: true`. That stage always reports `scientific_test_admitted:
false`. The second command separately requires `statistics/COMPLETED.json` and
`statistics/analysis.json` with `result_admitted: true`. Read both `WARNINGS.md`
files. A weight table alone is not a statistical result.

The complete numeric design includes an explicit intercept, position and a
nonlinear covariate. Responses are explicitly prepared synthetic linear-model
responses. No factor coding, count normalization, log transformation or missing
value removal is inferred. The original reference uses `stats::lm` followed by
the official-source-checked spdep1.4-1 `lm.morantest`, with `zero.policy=TRUE`, a
greater alternative and BH over all three genes.

`PROVENANCE.json` pins every input. `original-input.json` retains the synthetic
coordinates and principal mapping behind the original rankings;
`original-reference.json` records 17-digit original inference values. Software
controls match every weight as a binary64 value and all21 inference fields within
an absolute/relative tolerance of 2e-12. These are software controls, not paper
results or biological findings.

**SKIP3:** supply the named accessible permitted input/output paths and retry NEW
output. This Java example needs no R runtime. **FAIL2:** preserve inputs, errors
and partial outputs, correct the named source-profile, axis, numerical, capacity
or integrity cause and retry NEW output. Never remove cells, change ties or drop
genes to force success. Source-selected self-links are retained; the existing
MORAN-LM method refuses self-links. This example has none.

Read the [scope and API guide](../../docs/literature/MONOCLE100_WEIGHTS.md).
P021's full graph learning, response preparation, unreported author settings and
historical environment remain separate. No whole-Monocle credit follows from
this component alone.
