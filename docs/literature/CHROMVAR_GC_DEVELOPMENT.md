# Native chromVAR GC bias development

`org.jlinalg.sequence.ChromVarGcBias` implements the retained chromVAR 1.16.0 and 1.34.1 `addGCBias` sequence formula in Java:

```text
bias = (C + G) / (A + C + G + T)
```

Ambiguous IUPAC residues, gaps and mask characters are excluded from the denominator. Input case does not change base identity. An empty or all-ambiguous sequence returns source `NaN`. Signac `RegionStats` uses the full sequence width for its GC percentage, so dividing that percentage by 100 gives a different value when ambiguity is present.

The [checked example](../../examples/chromvar-gc-bias/README.md) includes all inputs, a complete Java caller and copyable compile/run commands. Java is sufficient for the implementation and example. The validated development JAR is `build/literature-review/signac-runtime/background-gc-controls-1/omicsmatic-0.3.7-chromvar-gc-development.jar`:

```text
bytes:  164876516
SHA256: 78ed853c8e2f7417291809700e518ed513cde6276c5e823a9ab9af7171ad00e1
```

For a sequence already in memory, call `ChromVarGcBias.count(byte[], BooleanSupplier)`. The immutable result includes canonical-base, C/G and ambiguous-base counts alongside the fraction. Supported sequence bytes are A/C/G/T, R/Y/S/W/K/M/B/D/H/V/N, `-` and `+`, in either case where applicable. Unsupported bytes fail explicitly.

For a reference genome, call:

```java
var result = ChromVarGcBias.calculate(
    fastaPath, optionalFaiPath, orderedPeaks,
    SignacReferenceStats.Limits.defaults(), () -> false);
```

`orderedPeaks` is a nonempty `List<SignacFragments.Region>` with one-based closed coordinates. For BED intervals, perform and record the explicit zero-based to one-based conversion before calling this API. The method validates the complete uncompressed FASTA, builds a byte-offset index in memory and checks a supplied `.fai` against the actual reference. It leaves the reference and its index unwritten. Missing contigs, duplicate peak identities, out-of-bounds intervals, unsupported residues, malformed references and exceeded capacities fail; rows are not renamed, imputed or filtered. Results follow the exact supplied order. `Limits` bounds reference bytes/residues, sequence and peak counts, line and peak widths and total queried bases. The caller can supply a cancellation predicate; thread interruption is also checked.

**WARNINGS / REQUIRED ACTIONS**

- **Undefined bias:** inspect `result.warnings()` and every `NaN`. Review the source reference and ranges. Background covariance/sampling can fail on undefined or constant bias; preserve the failed attempt and explicitly document any scientifically justified population change before retrying.
- **Reference/input errors:** retain the error, correct the named assembly, contig, range, index or capacity problem and use new output paths. No automatic aliasing, filtering or repair occurs. Compressed FASTA requires a separate validated workflow; provide a preserved uncompressed copy here.
- **Java 25 command-line paths:** this environment's Windows Java 25 rejected a Unicode path argument before the example reached the native library. That failed example is retained. The documented commands use ASCII paths; use ASCII helper arguments here or a separately tested caller that reads paths from UTF-8 configuration. This limitation concerns command-line path decoding, not biological identifiers; do not rename biological inputs to bypass an error.
- **Upstream availability:** native GC calculation and [prepared-background deviation scoring](CHROMVAR_DEVIATIONS_DEVELOPMENT.md) need Java. A separately required original background sampler needs its declared R/packages. If installation is forbidden or software is unavailable, mark that dependent analysis skipped, retain a prominent warning, and explain which permitted runtime is needed to resume. Do not admit a substitute result.
- **Scope:** this API reproduces the sequence formula and indexed reference extraction. It does not construct a `BSgenome` object, implement the complete `addGCBias` container method, sample backgrounds or provide the entire Signac workflow. Sequence biases and numerical agreement do not establish biological accuracy or regulatory causality.

Validation compared the exact two original formula lines from both retained source editions with native results for 22 sequence populations, including ambiguity, masks, gaps, case, empty sequences and long sequences. Real indexed FASTA extraction covered 21 nonempty ranges through Rsamtools, independently of Java's HTSJDK extraction. The original controls used R 4.6.1, Biostrings 2.80.2 and Rsamtools 2.28.0. Each of Windows Java 21, Windows Java 25 and Linux Java 21 passed 49 checks without skips. These source expressions ran on current recorded dependencies; historical whole packages and a full `BSgenome` workflow were not executed.

The [frozen evidence](chromvar-gc-development/VALIDATION.json) preserves original controls, exact source snapshots, actual-JAR test logs, checked example captures and original sampler intake controls. The sampler intake compares two unchanged R core functions and the current installed public Matrix interface on the same recorded current dependencies. Matching seeded outputs and RNG states are source replay evidence; they are not an independent Java RNG implementation or an available end-user background adapter. The full `utils.cpp` sampler source is byte-identical between the two retained editions; source equality does not authenticate the installed DLL.

This is local development. The tested JAR extends the separately pinned native deviation artifact without changing its existing entries. It has no new root CLI route, catalog credit, release or deployment. The shared canonical JAR is not the artifact tested here. The catalog remains 68 new scoped integrations, 71 including existing tools, and 259 missing tools.
