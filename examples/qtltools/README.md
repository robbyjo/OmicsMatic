# Prepared-input QTLtools examples

Build the current source JAR first. The earlier release predates this command.
Read [warnings and the request reference](../../docs/literature/QTLTOOLS_WORKFLOWS.md).
These are artificial BED intervals; no person or study data are supplied.

From the repository root:

```powershell
java -jar build/cli/omicsmatic-0.4.0.jar qtltools-tools --request examples/qtltools/native-fdensity.json
java -jar build/cli/omicsmatic-0.4.0.jar qtltools-tools --verify examples/qtltools/build/native-fdensity
```

Native use needs Java only. Paths in JSON resolve against the JSON file's folder.
Output is NEW `examples/qtltools/build/native-fdensity`. Preserve it; change `out`
for another run. Its `outputs/result` is:

```text
-20 -11 0
-10 -1 2
0 9 2
10 19 0
```

The counts mean two QTL rows each have an overlap in each middle bin. The two
overlapping annotations at q1 count once per bin, not twice. This is not an
enrichment p-value. Window20 is a radius, coordinates are literal BED intervals,
and minus strand reverses relative direction.

For an original cis permutation run, copy `cis-permute.example.json` to a NEW
request file and provide your exact prepared inputs, indexes and selected runtime.
The placeholder file is deliberately incomplete. It must skip with a clear
missing-input/provider remedy until those files are supplied. Do not substitute
a different edition or guess the paper's unreported covariates/thresholds.

On Linux use absolute Linux runtime/library paths. On Windows use absolute drive
paths such as `D:/...` and add the explicit `wsl_distribution` inside `runtime`.
Use your installed distribution name. Inputs remain Windows paths; the adapter
owns transport to Linux. The exact1.3.1 build needs its matching scientific
libraries, including the BLAS directory. See the guide for identities and errors.

To replay, write a NEW JSON file with schema_version1, mode REPLAY, analysis set
to the existing capture, and out set to a NEW directory outside that capture.
For original cross-host replay, provide an explicit `runtime` object for that
host. Native replay has no runtime override. Parent bytes remain sealed.
