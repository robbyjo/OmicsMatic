# FlowJo 10 workspace gate memberships and counts

`flowjo-tools` imports the saved sample-specific gates from a FlowJo 10 workspace
and evaluates every event in an explicitly supplied FCS file. It reports complete
memberships, counts, percentages, source captures and integrity checks.

Build the current source with `./gradlew executableJar` (PowerShell:
`.\gradlew.bat executableJar`) and use `build/cli/omicsmatic-0.4.0.jar` in the
examples below. Preserve that exact JAR alongside your run evidence.

**Scope:** the profile is `native-flowjo10-wsp20-flowkit132-v1`: FlowJo 10.x,
workspace format 20.0, using the explicitly declared FlowKit 1.3.2 interpretation.
It does not claim that an arbitrary vendor workspace or a paper has been reproduced.
FlowJo itself is not installed or required. FACSDiva acquisition is a separate task.

**Read `WARNINGS.md` and `analysis.json` before using a run.** Saved FlowJo counts can
disagree with this interpretation. The default `REQUIRE_MATCH_WHEN_RECORDED` refuses
the result when a recorded nonnegative count disagrees. `REPORT_DIFFERENCES` is an
explicit alternative for investigating native-profile results; it preserves and
prominently reports every disagreement and does not establish vendor agreement.
Saved count `-1` means uncomputed and is not used as a numerical target.

## Run a saved gate tree

Use the exact `sampleID` from the workspace's `SampleList/Sample/SampleNode`.
Supply the FCS explicitly. The importer records the saved URI as metadata and
never opens its path or downloads a file from it. Sample names and filenames
do not select or repair bindings. The selected operation is `MEMBERSHIP_COUNTS`.

For a sample without time scaling or biex transforms, prepare the request with
Python 3.11+ (standard library only). Replace the example paths and declarations
with your actual inputs; preserve the original gates and FCS file.

```powershell
python tools/cytometry/prepare_flowjo_request.py --workspace experiment.wsp --fcs sample.fcs --sample-id 1 --time-policy NONE --population-declaration "All events in the supplied original sample, in acquisition order" --gating-provenance "Saved sample-local gates from experiment.wsp; no gate changes" --request request.json --out result
java -jar omicsmatic-0.4.0.jar flowjo-tools GATE --request request.json
java -jar omicsmatic-0.4.0.jar flowjo-tools VERIFY --run result
java -jar omicsmatic-0.4.0.jar flowjo-tools REPLAY --run result --out replay-result
```

Every request, result and replay destination must be new. Paths in JSON resolve
against the request's directory. Unicode input paths belong inside UTF-8 JSON;
an ASCII request pathname avoids Windows launcher argument conversion problems.
Keep the exact producer JAR: replay requires its original whole-file SHA-256.
`VERIFY` checks captured bytes and a completed manifest; it does not rerun gating.
Replay uses the parent's captured input paths. Keep the completed parent at its
recorded location, or restore its complete archived tree there before replaying.

Declare input policies explicitly:

- `--time-policy APPLY_FCS_TIMESTEP_IGNORE_TIME_GAIN --time-channel Time` selects
  the exact original `Time` channel, multiplies its stored values by the actual
  FCS `$TIMESTEP`, and does not apply that channel's PnG. This is the declared
  FlowIO-compatible interpretation; no missing/empty time-step fallback is used.
- `--label-policy FLOWJO_SLASH_TO_UNDERSCORE` explicitly maps `/` to `_` in source
  channel labels for workspace matching. Collisions refuse the analysis. `EXACT`
  is the default. Original names remain in captured FCS metadata.
- `--allow-text-padding` and `--allow-numeric-padding` permit only the reader's
  declared vendor ASCII-space policies. Names, metadata bytes and offsets remain
  unchanged. These options do not authorize general whitespace or ID repair.
- `--legacy-zero-log-minimum-as-one` explicitly selects the supported legacy FCS
  zero-log-minimum interpretation. It cannot repair incompatible modern FCS data.
- `--saved-count-policy REPORT_DIFFERENCES` admits only the declared native
  computation when saved counts disagree. It leaves the discrepancies visible.

## Biex calibration when needed

Biex and logicle are different transforms. The Java engine does not replace one
with the other. For a saved biex transform it needs an explicit table containing
exactly 4,097 finite, strictly increasing input knots paired with output integers
0 through 4,096. It applies linear interpolation and the table's intrinsic endpoint
saturation. Raw measurements and saved parameters are retained unchanged.

No upstream biex generator is copied into the Java implementation. You may supply
an independently exported matching table, or use the optional helper with a
separately installed **FlowKit 1.3.2** in an isolated Python environment:

```powershell
python -m venv flowjo-reference-env
flowjo-reference-env/Scripts/python.exe -m pip install "flowkit==1.3.2"
flowjo-reference-env/Scripts/python.exe tools/cytometry/export_flowjo_biex_calibrations.py --workspace experiment.wsp --sample-id 1 --out calibration
python tools/cytometry/prepare_flowjo_request.py --workspace experiment.wsp --fcs sample.fcs --sample-id 1 --time-policy NONE --population-declaration "All original sample events in acquisition order" --gating-provenance "Unmodified sample-local workspace gates" --calibrations calibration/CALIBRATIONS.json --request request-with-calibration.json --out result-with-calibration
java -jar omicsmatic-0.4.0.jar flowjo-tools GATE --request request-with-calibration.json
```

Only install where permitted. If installation is unavailable, supply an existing
matching table or skip the dependent biex analysis. The Java command returns
**SKIP3** when required calibration is absent; it never installs software itself.
Exporting calibration data does not run gating or a vendor runtime.

Each calibration request entry contains `parameter`, `attributes`, `table`, `pin`
(`bytes` and `sha256`) and `provenance`. The attributes must exactly match the saved
biex element, including XML namespace keys such as
`{http://www.isac-net.org/std/Gating-ML/v2.0/transformations}width`.
The table is UTF-8 TSV, header `input<TAB>output`, followed by all 4,097 pairs.

## Supported computation and limits

The complete selected sample tree is used, including customized gates. Group
templates are not substituted. Square sample-local WSP compensation is applied
once, with explicit SVD cutoff `1e-12` in generated requests. FCS compensation
keywords are retained and are not applied in addition to the workspace spectrum.
Spectral WSP matrices, multiple sample matrices and unresolved numerical rank
require a separately qualified export or method.

Rectangles use `[minimum, maximum)`, polygons include their boundary, and
Boolean And/Or/Not gates intersect their parent population. Saved quadrant
rectangles and reused leaf names retain their complete literal paths. Explicit
cycles, missing references and ambiguous slash-serialized paths refuse the tree.
Ellipse gates use the declared 128-vertex display-space approximation; unresolved
foci/edges fail rather than receiving a numerical repair.

Linear, log, logicle and fasinh mappings follow the declared FlowKit 1.3.2
interpretation. In particular, WSP linear `maxRange/minRange` map to Gating-ML
`T/A`; the saved display `gain` is recorded and not separately applied. This is
distinct from actual FCS PnG preprocessing. Fasinh uses `T/M/A`; other saved
attributes are retained. Log intrinsically floors inputs below its positive
offset for display transformation. These choices are reported in run warnings.

Saved `Statistic` nodes are retained in the original workspace and explicitly
reported as **NOT COMPUTED**. This operation does not compute means, medians,
geometric means, MFI, t-SNE, gates learned from data, plots, instrument calibration
or acquisition. Use a separately qualified procedure on an explicitly declared
measurement/compensation scale for intensity statistics.

No events are subsampled or dropped. Nonfinite measurements, invalid metadata,
unsupported storage and capacity violations refuse a complete population.
Default capacities are 1 GiB per FCS, 10 million events, 250 million event-gate
pairs and 512 MiB membership output. Optional request fields are
`maximum_input_bytes` (up to 64 GiB), `maximum_events` (up to 100 million),
`maximum_event_gate_pairs` (up to two billion) and `maximum_membership_bytes`
(up to 2 GiB). Workspace limits are 16 MiB, 4,096 samples/gates, depth 128 and
100,000 polygon vertices; FCS has at most 512 channels and 16 MiB TEXT.
These are whole-population admission limits, not OS resource guarantees.

Successful output includes `memberships.tsv` (one-based event order; stable gate
IDs), `counts.tsv`, full gate paths in `analysis.json`, original input/request
captures, `WARNINGS.md`, `producer.json`, `manifest.json` and `COMPLETED.json`.
An empty parent gives `UNDEFINED_EMPTY_POPULATION`, not a fabricated percentage.

**SKIP3:** supply the named permitted runtime data, exact calibration or supported
export, then retry a new output directory. **FAIL2:** no result is admitted;
preserve inputs and partial outputs, read `FAILURE.txt`, correct the named issue
and retry a new directory. A completion marker, exit zero and
`analysis.json`'s `result_admitted=true` are all required before use.

The [pinned FlowKit source](https://github.com/whitews/FlowKit/tree/1.3.2) and
[official FlowJo workspace XML documentation](https://www.flowjo.com/docs/flowjo10/workspaces-and-samples/ws-ribbons-and-tabs/ws-ribbon-band-debug/workspace-xml)
describe the reference interpretation and file format. The bundled FlowKit
BSD-3-Clause notice credits the workspace/ellipse interpretation; no vendor
software or license is redistributed.
