# Native binary peak-by-cell read presence

This example takes already-filtered read blocks and exact caller-assigned cell identities. A peak–cell coordinate is one when any supplied read block overlaps that peak. Repeated reads and overlaps never increase its value. Zero-read peaks and cells remain on the supplied axes, in their supplied order.

Use Java 17+ and the separate `htseq-binary-development` JAR. From the repository root:

```sh
mkdir -p build/htseq-binary-example
javac --release 17 -cp build/literature-review/htseq-runtime/binary-development/omicsmatic-0.3.7-htseq-binary-development.jar \
  -d build/htseq-binary-example examples/htseq-binary-accessibility/BinaryReadPresence.java
java -cp build/literature-review/htseq-runtime/binary-development/omicsmatic-0.3.7-htseq-binary-development.jar:build/htseq-binary-example \
  BinaryReadPresence
```

Windows uses `;` between classpath entries. Create/use a fresh example output directory when retaining separate attempts. The expected output is:

```text
B	c2	1
B	c1	1
A	c1	1
reads=5 overlapping=4 nonzero=3; zero peak/cell retained
nonzero-capacity REFUSED
```

The first three lines contain literal tab separators. They contain three sparse coordinates; the declared matrix still has three peak rows and three cell columns. Dense zeros are not emitted as extra entries.

**Required action after an exception:** retain inputs and the error, correct the stated axis identity, coordinate/strand, capacity or cancellation cause, then retry. No partial matrix is returned. This Java library does not write a run/status directory or retain failed attempts automatically. Logical capacities do not guarantee Java heap usage.

This method uses current HTSeq interval-set geometry and the explicit read-presence definition described in P099. It does not recover that paper's original custom script, unstated HTSeq edition, barcodes, filtering or peak preparation. It does not parse SAM/BAM/GTF, pair reads, apply MAPQ/NH/flag rules, deduplicate UMIs, merge/call peaks, normalize counts or run differential tests. Read [the development guide](../../docs/literature/HTSEQ_BINARY_ACCESSIBILITY_DEVELOPMENT.md) and retain these boundaries.
