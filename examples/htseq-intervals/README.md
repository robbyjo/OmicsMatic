# Native HTSeq interval assignment example

This Java example assigns synthetic, already-aligned genomic blocks to feature sets. It does not parse SAM/GTF, count reads, or reproduce a paper pipeline. Java 17+ is sufficient; Python and HTSeq are unnecessary for native execution.

From the repository root:

```powershell
.\gradlew.bat --no-daemon -I tools/literature/htseq-core-development.init.gradle executableJar
$htseqJar = 'build/literature-review/htseq-runtime/core-development/omicsmatic-0.3.7-htseq-core-development.jar'
javac --release 17 -cp $htseqJar -d build/htseq-example-NEW examples/htseq-intervals/AssignFeatures.java
java -cp "$htseqJar;build/htseq-example-NEW" AssignFeatures
```

On Linux, replace the classpath separator `;` with `:`. Choose a NEW example class directory. The older released/canonical executable lacks this API. The separate development build preserves the canonical JAR.

Expected output:

```text
overlap UNION AMBIGUOUS [gene-A, gene-B]
overlap INTERSECTION_STRICT UNIQUE [gene-A]
overlap INTERSECTION_NONEMPTY UNIQUE [gene-A]
gap UNION UNIQUE [gene-A]
gap INTERSECTION_STRICT NO_FEATURE []
gap INTERSECTION_NONEMPTY UNIQUE [gene-A]
zero-length query REFUSED
whole-assignment capacity REFUSED
```

UNION collects every overlapping feature. INTERSECTION_STRICT requires a feature at every queried position, including positions in gaps. INTERSECTION_NONEMPTY intersects only positions having at least one feature. The result remains a set: duplicate exon intervals and repeated gene IDs do not create extra features.

**Errors and required action:** zero-length set-array intervals, malformed coordinates/strands, capacity excess and cancellation return no assignment. Preserve the original inputs, inspect the exception, correct the stated problem, and retry; do not silently drop invalid blocks or relax a scientific threshold. Negative positions are not accepted. Capacity limits bound logical populations rather than JVM memory.

An empty assignment is an interval result, not a biological absence claim. An ambiguous set is not automatically counted, split, discarded or sampled. A caller must declare read-versus-fragment units, pairing, strand transformation, flags, MAPQ/NH filters and nonunique counting policy separately.

Read [the development guide](../../docs/literature/HTSEQ_INTERVALS_DEVELOPMENT.md) for exact validation and remaining integration work.
