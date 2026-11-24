# Unbinned cytometry intensity summaries

`flowjo-tools SUMMARIZE` computes complete, unweighted intensity summaries from an
explicitly selected population in a completed [native FlowJo gate run](FLOWJO_WORKSPACES.md).
It freshly evaluates **every event and every gate**, checks them against the captured
parent memberships and counts, then summarizes the selected measurements. No vendor
installation is required. This adds a native measurement analysis; it does not establish
that a paper's vendor statistics have been reproduced.
Saved-count agreement and discrepancy annotations are also checked against the
captured workspace and the population counts rather than trusted as parent metadata.

**Read `WARNINGS.md` and `analysis.json` before using results.** FlowJo documents
transform-dependent binning for most statistics and special off-scale handling for
Robust SD. This profile uses unbinned measurements and explicit R type-7 quantiles.
Its central 68.26% half-width must not be relabeled as vendor Robust SD. A vendor
comparison needs its original binning, transform, off-scale and population settings.
See the [official statistic definitions](https://docs.flowjo.com/flowjo/workspaces-and-samples/ws-statistics/ws-statdefinitions/).

## Prepare and run

Build with `.\gradlew.bat executableJar` on Windows or `./gradlew executableJar` on
Linux. Preserve `build/cli/omicsmatic-0.4.0.jar` as the exact producer for later replay.
First complete a `flowjo-tools GATE` run using the linked guide. Keep its entire result
directory, including original captures, membership table, manifest and completion marker.

Create `definitions.json` as a UTF-8 JSON array. Replace `FSC-A` and the gate path with
exact entries from that parent's `analysis.json`; channel names are case sensitive.
An empty `gate_path` selects all original events. A nonempty path names every ancestor
and the selected gate in order. The following example selects all events:

```json
[
  {
    "id": "all-fsc",
    "gate_path": [],
    "parameter": "FSC-A",
    "domain": "AMPLIFIER_MEASUREMENT",
    "statistics": ["MEAN", "MEDIAN", "SAMPLE_SD", "CV_PERCENT", "MAD", "MAD_PERCENT"]
  }
]
```

Use Python 3.11+ (standard library only) to capture the parent manifest fingerprint:

```powershell
python tools/cytometry/prepare_intensity_request.py --parent gate-result --definitions definitions.json --measurement-units "FSC-A amplifier measurements with the parent FCS gain/scale policies" --statistics-provenance "Complete declared population; unweighted measurements; no vendor binning" --export-measurements --request intensity-request.json --out intensity-result
java -jar build/cli/omicsmatic-0.4.0.jar flowjo-tools SUMMARIZE --request intensity-request.json
java -jar build/cli/omicsmatic-0.4.0.jar flowjo-tools SUMMARIZE VERIFY --run intensity-result
java -jar build/cli/omicsmatic-0.4.0.jar flowjo-tools SUMMARIZE REPLAY --run intensity-result --out intensity-replay
```

Request and result paths must be new. Keep the request outside both sealed results.
The default parent policy, `REQUIRE_SAVED_COUNTS_AGREE`, refuses native populations
that disagree with saved FlowJo counts. Investigating those populations requires the
explicit preparer option `--parent-count-policy ALLOW_DECLARED_NATIVE_POPULATION`;
all disagreements remain in the report. That option does not establish vendor agreement.

Choose the measurement domain explicitly:

- `AMPLIFIER_MEASUREMENT`: the parent's declared FCS amplifier/gain/time preprocessing,
  before compensation or display transforms. This is not necessarily stored integer data.
- `WSP_COMPENSATED_MEASUREMENT`: the same preprocessing followed by the saved sample-local
  WSP spectrum. The exact selected channel must be present in that matrix. Negative
  compensated values remain in the population. No FCS matrix is applied a second time.

The native compensation uses the gate profile's declared SVD inverse. Floating-point
roundoff close to zero can change a sign compared with another solver; no values are
snapped to zero. In qualification, 513 selected comparisons differed in nonpositive
classification between the native calculation and FlowKit, all within `4.59e-13` of
zero. Both sets of values and counts are retained. Interpret strict-positive geometric
mean admission using the reported native measurements and counts, particularly for
near-zero signals.

No display transformation or histogram binning is applied to summary values. Display
transforms can still be needed for the parent's gates; any required biex calibration
tables must already be present in the completed parent.

## Exact definitions

Let `n` be the complete selected count, `m` the arithmetic mean, `d` the median, and
`Q(p)` the R type-7 quantile. For sorted observations, its one-based index is
`1 + (n-1)*p`, with linear interpolation between the adjacent observations.

| Request statistic | Definition |
| --- | --- |
| `MEAN` | Arithmetic mean |
| `MEDIAN` | `Q(0.5)` |
| `SAMPLE_SD` | Square root of the centered sum of squares divided by `n-1` |
| `CV_PERCENT` | `100 * SAMPLE_SD / m` |
| `CENTRAL_68_26_HALF_WIDTH` | `Q(0.8413)/2 - Q(0.1587)/2` |
| `CENTRAL_68_26_CV_PERCENT` | `100 * CENTRAL_68_26_HALF_WIDTH / d` |
| `MAD` | Median of `abs(x-d)`, without a normal-consistency multiplier |
| `MAD_PERCENT` | `100 * MAD / d` |
| `GEOMETRIC_MEAN` | `exp(mean(log(x)))`, defined only when every value is strictly positive |

Percent ratios preserve the sign of the declared denominator. Define “MFI” explicitly
as mean, median or geometric mean and specify the measurement domain. The Java library
also exposes `CytometryIntensitySummary.quantileType7(values, p)` for finite complete
vectors and `p` in `[0,1]`.

Each undefined value is JSON `null` / TSV `UNDEFINED` with a reason: `EMPTY_POPULATION`,
`INSUFFICIENT_COUNT`, `ZERO_DENOMINATOR`, `NONPOSITIVE_VALUES` or `NUMERIC_OVERFLOW`.
The report retains total and nonpositive counts. It never drops a nonpositive event
to manufacture a geometric mean. A nonfinite source measurement refuses the analysis.
The SD calculation uses scaling to avoid avoidable underflow and overflow. At the
artificial scale near `1e-200`, raw R 4.6.1 `sd` underflowed to zero; Java retained the
nonzero value confirmed by a separately scaled R calculation. Raw R outputs and this
discrepancy are retained in the qualification evidence.

## Outputs, limits and failure actions

Success requires exit `0`, `COMPLETED.json` and `result_admitted: true`. Outputs include
`statistics.tsv`, `analysis.json`, the complete sealed `parent/`, request captures,
producer identity, manifest and `WARNINGS.md`. With `--export-measurements`, the command
also writes every selected event index and value to `selected-measurements.tsv`.
`VERIFY` checks recorded bytes only. `REPLAY` requires the exact producer JAR and
freshly recomputes memberships and statistics. Replay uses its captured parent rather
than the parent's obsolete external absolute input paths, so a complete intensity
capture can be relocated with all its members.

The JSON request requires `schema: 1`, profile `native-cytometry-intensity-unbinned-v1`,
`parent`, `parent_manifest_pin`, `out`, `binning: "NONE"`, `quantile_method: "R_TYPE_7"`,
`parent_count_policy`, `definitions`, `measurement_units`, `statistics_provenance` and
Boolean `export_measurements`. Optional capacities reject oversize complete inputs;
they never authorize sampling. Defaults are 2 GiB captured parent, 10 million events,
128 MiB selected value buffers, 512 MiB new output and 250 million event/gate pairs.
The corresponding fields are `maximum_parent_bytes`, `maximum_events`,
`maximum_value_bytes`, `maximum_output_bytes` and `maximum_event_gate_pairs`.
The parent has a separate byte limit from new statistics output. Measurement export
uses a conservative size precheck. Java heap must also accommodate the complete
vectors and numerical working copies.

**SKIP3 (exit 3): the complete dependent analysis was skipped.** Supply the named
permitted input, saved calibration or supported explicit method, then retry with new
output. Unknown vendor binning and alternative quantile conventions are not inferred.
**FAIL2 (exit 2): no complete result is admitted.** Preserve inputs and partial output;
fix the named integrity, membership, channel, numerical or capacity problem and retry
with new output. An undefined individual statistic can occur in an otherwise complete
run; inspect its status and counts before interpretation.

Clustering plugins, cell-cycle fitting, acquisition/sorting, vendor plots and binned
vendor statistics remain separate capabilities. This operation provides no substitute
for missing cohort FCS files, gate workspaces, donor definitions or vendor settings.
