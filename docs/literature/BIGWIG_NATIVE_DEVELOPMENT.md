# Native bigWig writer development

`org.jlinalg.genetics.BigWigWriter` implements native Java bigWig v4 output from
complete finite bedGraph intervals and an explicit chromosome-size dictionary.
It writes chromosome B+tree and raw-data R-tree indexes, compressed or uncompressed
bedGraph sections, total summaries and the ending signature. Its complete verifier
checks every structural field and raw interval against the supplied inputs;
compressed payloads are independently inflated, not compared by zlib encoding.

**This is an API checkpoint. The public converter command, captured workflow,
precision/reference ledgers, examples and catalog promotion remain open.** The
catalog remains at 66 new scoped integrations and 261 missing tools. Use the
development candidate in the [frozen proof](bigwig-native-development/VALIDATION.json),
not the unchanged canonical/released JAR. The 250-paper goal remains active.

```java
var reference = Map.of("chr1", 1000L);
var intervals = List.of(new BedGraphSignals.Segment("chr1", 10, 20, 0.5));
var options = new BigWigWriter.Options(
    true,       // zlib compression
    1024,       // records per raw section
    256,        // tree fanout
    100000000L, // maximum complete output bytes
    true,       // reject float64-to-float32 precision loss
    false       // store used chromosomes only
);
var report = BigWigWriter.write(newFile, reference, intervals, options, () -> {});
var checked = BigWigWriter.verify(newFile, reference, intervals, options, () -> {});
```

Both methods use the exact complete supplied population. Coordinates are
zero-based, half-open and unsigned32. Chromosomes may appear in any input order,
each in one contiguous block; intervals within a block must already be sorted
and nonoverlapping. Literal chromosome identities remain unchanged. The binary
dictionary/data use unsigned UTF-8 byte order. No coordinate aliasing, clipping,
normalization, overlap summing or automatic within-chromosome sorting occurs.
The declared dictionary policy either stores used chromosomes or all reference
chromosomes. An empty track requires explicit all-reference policy and remains
distinct from measured zero signal. The API is bounded to 100,000 reference
chromosomes and ten million input intervals; these bounds are not a JVM heap
guarantee or production-scale certificate.

**PRECISION: stored signal is float32.** The API first interprets the supplied
Java double and then casts it to float, matching the reviewed original raw-data
conversion. `requireExactFloat32=true` refuses any change to that supplied double.
When rounding is explicitly allowed, inspect `roundedValues` and
`underflowedValues`; nonzero signal can become signed zero. This exactness policy
does not certify equality to an original decimal token that was already rounded
while constructing the double. Float32 overflow is rejected before file creation.
Negative zero is preserved in raw data. No nonfinite raw signal is admitted.

**NATIVE TOTAL SUMMARIES DIFFER FROM UCSC ARITHMETIC.** The native implementation
uses stored float32 values with double products and compensated double sums.
The pinned original converter uses float arithmetic in parts of its summary
calculation. Exact raw intervals match across every successful shared protocol,
while the full original/native total-summary differences remain in the proof.
The original can store infinite sums of squares for finite raw data; the native
controls retain finite double summaries. Do not claim original summary-bit,
zoom-statistic, binary-layout or downstream numerical identity.

**NO ZOOM APPROXIMATIONS ARE WRITTEN.** Full-resolution indexes and complete data
are present. Consumers may need more raw I/O when viewing large ranges; this
checkpoint does not certify browser rendering performance or reproduce the
original converter's zoom selection and approximate statistics.

**OBSERVED UPSTREAM CONSUMER LIMITATIONS:** the pinned Cygwin pyBigWig 0.3.26
build segfaulted when its `header()` read the original float-boundary file with
an infinite stored summary. That direct crash is retained. Further nonfinite
original headers were guarded; their exact raw IEEE summaries and complete raw
intervals were still read and retained. The same reader returns signed positions
above 2^31 even though its dictionary reports the unsigned length. The exact
signed values and their explicit unsigned format interpretation are retained;
the original UCSC decoder independently confirmed every unsigned coordinate.
The pinned original converter rejected an empty track and timed out on the
unsigned-coordinate probe. These are separate original failures, not native
successful conversions. Do not pass such inputs to an unverified downstream
consumer or infer broad compatibility from one successful reader.

The final [frozen proof](bigwig-native-development/VALIDATION.json) records 28
actual-JAR tests on each of Windows Java 21, Windows Java 25 and Linux Java 21,
without skips. Nine new classes match strict fresh Java 17 compilation; all
19,505 parent archive entries remain identical. The unchanged reader's regression
fixtures cover 351,836 original intervals. Gradle tests and Javadoc pass.

Twenty-two complete native protocols retain 79,948 intervals. Original UCSC
`bigWigToBedGraph` accepts every native file and recovers every coordinate.
Pinned original pyBigWig verifies every float32 value, with the signed-coordinate
view explicitly recorded. Twenty successful original converter outputs match
all their raw float32 intervals. Empty and unsigned-coordinate original failures,
nonfinite summaries and exact precision differences are retained. Three failed
validation attempts and the earlier passing receipt are also retained. Inputs
include compression choices, multiple tree levels,
many raw sections, unused chromosomes, empty and zero tracks, negative/subnormal/
maximum values, signed zero, UTF-8 identities and seeded mixed-scale values.

The source review binds UCSC Kent commit
`c00932ec61a581a351713b7ff301f9f1532c5a23`, especially
[bedGraphToBigWig.c](https://github.com/ucscGenomeBrowser/kent/blob/c00932ec61a581a351713b7ff301f9f1532c5a23/src/utils/bedGraphToBigWig/bedGraphToBigWig.c),
[bwgInternal.h](https://github.com/ucscGenomeBrowser/kent/blob/c00932ec61a581a351713b7ff301f9f1532c5a23/src/inc/bwgInternal.h),
and the B+tree/R-tree writer declarations. Original tools are development-only
references. They are not bundled with the Java implementation and are not needed
to write or verify native output. Existing pyBigWig was reused; no new reader
package was installed. The complete primary-source cohort scan found exactly
five named converter roles. Their pending reviews preserve P110's literal
reported “v4” ambiguity, P050's sort command and upstream normalization/peak
calling boundaries; no unknown assembly or tool edition is inferred.

**FAILURE RECOVERY:** `write` requires a new output file. Invalid input, precision
policy, output capacity, I/O or cancellation throws and admits no completed
workflow. Any created partial file is preserved. Correct the stated cause, retain
the partial and use a new path; do not treat a file's existence as success.
The API report/verifier alone does not create a public study completion marker.
No installation is attempted. A future public workflow must prominently name
forbidden requirements as SKIP3 and integrity/capacity/execution failures as FAIL2.
Cell Ranger remains excluded. No release, push, deployment, integration-count
increase or whole-goal completion is claimed at this checkpoint.
