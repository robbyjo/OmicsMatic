# Read stored flow-cytometry event channels in Java

The new `org.jlinalg.cytometry.FcsReader` API supplies a reusable input component
for cytometry analysis. It reads a complete single list-mode data set from FCS
2.0, 3.0 or 3.1 using explicit capacities. It supports unsigned byte-aligned
8/16/24/32-bit integers and IEEE 32/64-bit floating values, including mixed
integer widths. Both standard byte orders are supported. It is available from
a current source build; the released 0.4.0 JAR is unchanged.

The [development evidence](../literature/fcs-reader-development/README.md)
records independent storage controls and complete upstream-file comparisons.
The frozen paper census found FlowJo in 27 papers and FACSDiva in 17, across
33 distinct papers. This API is the shared event-input component. A separate
[native command now adds explicit compensation and Gating-ML gates](cytometry-gating.md).
Cell-cycle models, plugins, acquisition and instrument control remain separate
work; no vendor-family implementation credit is awarded here.

Build using the project's normal Gradle/JDK instructions. This API has no
external scientific runtime, Python, R or installation requirement. For an
already captured, unchanged input:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.jlinalg.cytometry.FcsReader;

try (FcsReader reader = FcsReader.open(
        Path.of("experiment.fcs"),
        FcsReader.Limits.defaults(),
        StandardCharsets.UTF_8)) {
    var info = reader.info();
    double[] event = new double[info.parameters().size()];
    while (reader.readEvent(event)) {
        // Consume this complete event in parameter order; copy the array if retaining it.
    }
    var summary = reader.summary();  // Requires every declared event.
    System.out.println(summary.eventsRead());
    for (String warning : info.warnings()) System.err.println(warning);
}
```

**These are stored channel values.** The mandatory FCS integer unused-bit mask
is applied; its changed-value count is returned in the summary. Floating values
are never clipped to `$PnR`. Gain, antilog, time scaling, compensation,
calibration, display transforms and gates are not inferred. Parameter order,
names and decoded metadata values retain their spelling, including `$` and
escaped delimiters. Keyword lookup is case insensitive. Absent optional values
remain absent. Nonfinite values remain visible and are counted separately.

Defaults allow a 64 GiB file, 100 million events, 4,096 parameters and 16 MiB per
TEXT segment. `new FcsReader.Limits(fileBytes, events, parameters, textBytes)`
changes those explicit capacities. Exceeding a capacity refuses the whole input.
It never samples events or returns a completed prefix. A wrongly sized event
buffer is refused. `summary()` refuses incomplete or closed streams and changed
file lengths.

FCS 3.1 values always use strict UTF-8. The charset argument explicitly selects
legacy FCS 2.0/3.0 decoding; choose `StandardCharsets.ISO_8859_1` only when that
matches the original file. There is no encoding fallback or identifier repair.
One original FCS2.0 fixture requires this explicit legacy encoding.

Some vendor exports include ASCII spaces after the last TEXT delimiter within
the declared segment. Exact decoding is the default. When that particular
format is intended, select it explicitly:

```java
FcsReader reader = FcsReader.open(
    Path.of("space-padded.fcs"), FcsReader.Limits.defaults(),
    StandardCharsets.UTF_8,
    FcsReader.TextPolicy.ALLOW_TRAILING_SPACE_PADDING);
```

The warning reports the number of excluded padding bytes. The original bytes
and segment offsets remain unchanged; values ending in spaces before their
closing delimiter are preserved. No delimiter or empty-value rule is guessed.
An original fixture whose supplemental TEXT points to its own primary TEXT is
decoded once with that alias and its warning retained.

Numeric metadata padding is a separate explicit policy. Existing overloads
continue to use `NumericPolicy.EXACT`. A fifth argument
`FcsReader.NumericPolicy.ALLOW_ASCII_SPACE_PADDING` permits outer ASCII spaces
in recognized numeric tokens and records a warning. Original keyword/parameter
values and all names remain unchanged. Tabs/other malformed tokens still fail.
The original FACSDiva Gating-ML reference file requires this choice for its
padded event count/data offset. The [new gating guide](cytometry-gating.md)
records its complete raw-file execution and the retained strict refusal.

**Required action on refusal:** preserve the complete original file and error.
`UnsupportedFormatException` means skip the dependent analysis for a capability
outside this profile: packed integer bits, integers wider than 32 bits, ASCII
or histogram storage, mixed byte orders, multiple data sets, legacy `$UNICODE`
semantics or unsupported editions. Obtain a separately retained supported export
from the original software or use a separately qualified reader. Do not select
the first data set, guess offsets or drop metadata to force success.

Other `IOException`s mean invalid/inconsistent input, wrong declared decoding,
exceeded capacity or I/O failure; no complete stream is available. The original
parser accepts one legacy fixture with an ambiguous trailing metadata token;
this native profile skips it explicitly. Both parsers reject the two original
offset-discrepancy fixtures. Those differences and failed controls are retained.

This low-level API does not create a captured/portable analysis result, hash
the original file, validate a CRC, interpret the ANALYSIS segment's models or
claim complete FCS conformance. The caller must capture and fingerprint the
whole original file, keep it unchanged, preserve warnings, consume every event
and publish completion only after all downstream work succeeds. A callback or
table written before an exception is a partial candidate, not an admitted result.

Storage interpretation follows the [ISAC FCS data standard](https://isac-net.org/data-standards/)
and its [normative FCS 3.1 reference](https://flowcytometry.gwu.edu/sites/g/files/zaskib311/files/2021-11/Data%20File%20Standard%20for%20Flow%20Cytometry%281%29.pdf).
The comparison uses pinned [FlowIO](https://github.com/whitews/FlowIO) 1.4.0
stored values; it does not establish FlowJo/FACSDiva workspace equivalence.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [International Society for Advancement of Cytometry (2026) — Cytometry data standards: FCS and Gating-ML](../CITATIONS.md#isac-data-standards)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
