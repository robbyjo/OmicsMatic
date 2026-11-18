# Gate a complete FCS population in native Java

`cytometry-tools GATE` reads a complete single-dataset FCS file, applies an
explicit channel-to-scale policy and evaluates an explicit Gating-ML 2.0
definition. It writes event membership and population counts. This source-build
feature needs Java only; Python, R, FlowJo and FACSDiva are not analysis
dependencies. The released 0.4.0 JAR has not been replaced.

**Read before using results:** this implements shared cytometry methods, with
the bounded [development qualification](../literature/cytometry-methods-development/README.md).
It does not import a proprietary workspace, learn gates, operate an instrument,
infer paper-specific settings or reproduce a complete FlowJo/FACSDiva workflow.
The historical ISAC reference archive contains contradictory controls; full
standard-suite compliance is not claimed. Original inputs and failures remain
preserved. Consult the detailed [warnings and actions](../literature/cytometry-methods-development/WARNINGS.md).

Build with the repository's normal Gradle/JDK instructions and use the resulting
executable JAR. The actual JAR filename may vary with the project version:

```sh
java -jar omicsmatic-0.4.0.jar cytometry-tools --help
java -jar omicsmatic-0.4.0.jar cytometry-tools GATE --request request.json
```

Save this UTF-8 request next to your input files. Paths resolve relative to the
request. `out` must be new. Choose the encoding, units, gate definition and
compensation policy for your actual experiment; these example choices are not
paper settings:

```json
{
  "schema_version": 1,
  "fcs": "experiment.fcs",
  "gating_ml": "gates.xml",
  "out": "new-gating-result",
  "legacy_charset": "UTF-8",
  "text_policy": "EXACT",
  "numeric_policy": "EXACT",
  "compensation": "AUTO",
  "legacy_zero_log_minimum_as_one": false,
  "svd_relative_cutoff": 1e-12,
  "population_declaration": "All original events and parameters, in acquisition order",
  "gating_provenance": "State who defined these gates, the source export and the intended units"
}
```

A minimal example `gates.xml` selects `FSC-A` values in `[100,1000)` on
uncompensated amplifier scale. Replace this with your separately justified
complete gate definition. Names match the source `$PnN` values exactly.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<g:Gating-ML xmlns:g="http://www.isac-net.org/std/Gating-ML/v2.0/gating"
             xmlns:d="http://www.isac-net.org/std/Gating-ML/v2.0/datatypes">
  <g:RectangleGate g:id="ExampleRange">
    <g:dimension g:compensation-ref="uncompensated" g:min="100" g:max="1000">
      <d:fcs-dimension d:name="FSC-A"/>
    </g:dimension>
  </g:RectangleGate>
</g:Gating-ML>
```

**Only use results when the process exits 0, `COMPLETED.json` exists and
`analysis.json` contains `"result_admitted": true`.** Read `WARNINGS.md` even
after success. A partial table, copied input or unadmitted report is not a
completed population. If you rerun, select a new output directory.

The output includes:

- `inputs/source.fcs`, `inputs/gates.xml` and `source-request.json`: complete
  captured originals. Source and captured hashes are checked before admission.
- `memberships.tsv`: one row for every source event, with a one-based index and
  one binary column for every output gate in definition order. Text headers use
  TSV quoting. Parent and Boolean dependencies are evaluated before use.
- `counts.tsv`: gate and parent counts and percentages. A zero denominator
  produces an empty percentage cell. Parentless gates use the full population.
- `analysis.json`: exact parameter order, explicit policies, compensation rank
  and singular values for used matrices, provenance, capacities and warnings.
- `manifest.json`: complete size/SHA-256 identities of the captured inputs,
  tables, request, report and warnings. `COMPLETED.json` pins that manifest.
  These hashes check integrity; they are not digital signatures or evidence
  that a chosen biological gate is appropriate.

The file capture does not remove the need to retain the qualified executable
and its independently recorded checksum. The development evidence records the
tested JAR; a different JAR needs its own validation.

The channel-to-scale bridge uses `$PnE`/`$PnR` antilog scaling or divides linear
channels by `$PnG` (unit gain when absent). It does not convert time to seconds
or apply calibration, display transforms or compensation by itself. Gating-ML
then selects compensation, ratios and scalar transforms explicitly. Nonfinite
stored values or undefined evaluated dimensions fail the complete population;
events are not removed or assigned an invented membership. An event outside a
parent gate needs no child transform evaluation.

| Choice | Behavior and required decision |
|---|---|
| `legacy_charset` | Explicit FCS2.0/3.0 decoding. FCS3.1 remains strict UTF-8. No fallback. |
| `text_policy=EXACT` | Exact TEXT segment. `ALLOW_TRAILING_SPACE_PADDING` explicitly permits ASCII spaces after its last delimiter, with a warning. |
| `numeric_policy=EXACT` | Exact numeric tokens. `ALLOW_ASCII_SPACE_PADDING` explicitly permits outer ASCII spaces in recognized numeric tokens, while retaining original metadata and every name. Tabs and other malformed tokens remain errors. The original FACSDiva 6.1.3 reference file needs this explicit policy for its padded count/offset. |
| `compensation=AUTO` | Selects one unambiguous `$SPILLOVER` or `SPILL` description, or establishes absence. Multiple/legacy descriptions remain unresolved. Uncompensated gates may run with an unresolved description; a dependent `FCS` gate skips the requested analysis. |
| `compensation=$SPILLOVER` or `SPILL` | Selects that exact available description. Names retain their order; no automatic normalization, transposition or first-description choice. Standard `$SPILLOVER` requires a unit diagonal. |
| `compensation=NONE` | Establishes absent metadata; conflicts with a present recognized description. To use uncompensated channels in a file containing compensation, select an appropriate file policy and use `compensation-ref="uncompensated"` in the gates. |
| `legacy_zero_log_minimum_as_one` | `false` refuses a zero logarithmic minimum. `true` explicitly interprets it as one for legacy FCS2.0/3.0, with a warning and original bytes preserved. It does not permit incompatible FCS3.1 metadata. |
| `svd_relative_cutoff` | Explicit FP64 Java SVD threshold. Singular square compensation fails. Rectangular spectra use the declared Moore–Penrose OLS pseudoinverse; rank deficiency is reported prominently as nonidentifiable individual signals. Already-inverted matrices are used literally with original rank unknown. |

Default command limits are 1 GiB input, ten million events, 512 MiB membership
upper bound and 250 million event/gate pairs. Optional request fields
`maximum_input_bytes`, `maximum_events`, `maximum_membership_bytes` and
`maximum_event_gate_pairs` permit respectively up to 64 GiB, 100 million,
2 GiB and two billion. Fixed bounds include 512 FCS channels, 16 MiB TEXT/XML,
4,096 top-level definitions, 128 gate/named-XML-matrix dimensions and 100,000 polygon
vertices. These are whole-input refusal bounds, not memory/CPU quotas or a
promise of practical run time. The command never silently samples a prefix.

The native model APIs are `CytometryCompensation`, `CytometryTransforms`,
`CytometryGates`, `GatingMl20` and `FcsGatingInput`. Scalar functions include
linear, logarithmic, arcsinh, logicle and hyperlog plus ratios. Rectangle upper
bounds are exclusive; polygon boundaries and ellipsoid surfaces are inclusive.
Self-crossing polygons use the even/odd rule with exact orientation fallback at
ambiguous floating-point edges. Ellipsoid covariance must be symmetric positive
definite. No clipping, averaging, covariance jitter or square-pseudoinverse
fallback repairs invalid definitions. Quadrants, explicit parents and Boolean
AND/OR/NOT/complements are supported. The original offline XSDs and semantic
checks validate the complete definition before event evaluation; external XML
entities and network schema resolution are disabled.

The low-level [stored-channel reader](fcs-stored-channels.md) remains separately
usable. Its strict defaults are unchanged. This command adds capture and
downstream gating around that API; it does not implement descriptive intensity
statistics, robust SD, cell-cycle fitting, acquisition, FlowAI, UMAP or FlowSOM.

**If execution is skipped or fails:** preserve the source files and output.
`SKIP3` means a needed capability/export or permitted file access is unavailable;
obtain the named complete supported export or correct access, then retry new
output. `FAIL2` means the complete analysis was not admitted; read `FAILURE.txt`
and `WARNINGS.md` and correct the specific definition, numerical domain,
metadata, integrity or capacity cause. Do not alter original gates, drop events
or replace values to force success. No software installation is needed for this
native profile. Optional development reference comparisons need their named
upstream packages; if installation/retrieval is unavailable or forbidden, skip
those dependent comparisons and retain the warning rather than claim a pass.
