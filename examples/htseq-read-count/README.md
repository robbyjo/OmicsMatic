# Native counting of prepared HTSeq read units

This synthetic Java example counts already-parsed, already-paired alignments. It does not read SAM/BAM/GTF or infer pairs. Native execution needs Java 17+ and no Python installation.

From the repository root:

```powershell
.\gradlew.bat --no-daemon -I tools/literature/htseq-read-count-development.init.gradle executableJar
$htseqJar = 'build/literature-review/htseq-runtime/read-count-development/omicsmatic-0.3.7-htseq-read-count-development.jar'
javac --release 17 -cp $htseqJar -d build/htseq-read-example-NEW examples/htseq-read-count/CountPreparedReads.java
java -cp "$htseqJar;build/htseq-read-example-NEW" CountPreparedReads
```

On Linux, use `:` rather than `;` in the classpath. Choose a NEW example class directory. The separate development build preserves the canonical executable.

Expected output:

```text
NONE A=1.0 B=0.0
FRACTION A=1.5 B=0.5
FRACTION summary no_feature=1 ambiguous=1 too_low_aQual=1 not_aligned=1 alignment_not_unique=1
PAIRED missing-first-NH FIRST_MATE_MISSING_TAG_ABORTED ASSIGNED
unit-capacity REFUSED
```

The example preserves source behavior. An ambiguous overlap is counted fractionally by the number of assigned features, not the NH value. A nonunique low-quality unit can increment both source summary categories. A missing first-mate NH tag stops checking the second mate's NH in the original implementation; the native counter reports that condition explicitly.

**Errors and required action:** invalid prepared fields, a layout/strand mismatch, exceeded bounds or cancellation admit no count result. Retain the original inputs and exception, correct the named cause, then retry. Do not discard an invalid mate or silently relax a filter. Empty counts are not a biological absence finding. Summary categories are not always a disjoint partition of read units.

`Alignment.blocks` must contain original nonempty M/=/X matched blocks with original strand. Keep absent mates as `null` and present unaligned mates as `Alignment(false, …)`; they behave differently. Preserve MAPQ 255 as its literal value and use minimum 21 for a strict MAPQ>20 rule. Declare strand, layout, overlap and nonunique policies explicitly. Random allocation remains upstream.

Read [the development guide](../../docs/literature/HTSEQ_READ_COUNT_DEVELOPMENT.md). The public HTSeq counting command and file-intake pipeline remain unfinished; this example does not admit a paper analysis.
