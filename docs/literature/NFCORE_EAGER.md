# nf-core/eager: native row inspection and source audit

**Full eager analyses remain unvalidated and receive no additional tool credit.**
The native Java component below is available now. It checks already parsed
sample-table rows against the original eager 2.3.4 and 2.4.4 row rules. Complete
workflow execution, MALT/HOPS screening, capture genotyping and ancestry analysis
still require their own complete inputs and scientific checks.

**Required action when an analysis cannot run:** if required installation or
download is forbidden, or the exact runtime/data are unavailable, skip that
dependent analysis. State the missing prerequisite prominently, retain diagnostics,
and supply the exact prerequisite or use an allowed environment before retrying.
An empty, failed or unassessable analysis must not become a negative finding.

## What the paper actually reports

[Barquera and colleagues, Nature, DOI 10.1038/s41586-024-08515-5](https://pmc.ncbi.nlm.nih.gov/articles/PMC11964931/)
describes three eager uses. The fixed 250-paper XML audit finds all three eager
paragraphs in P002; they must remain distinct.

| Use | Source requirements | Outstanding requirements |
| --- | --- | --- |
| Pathogen screening | Main-text methods: eager 2.4.4, Nextflow 21.04.1, human-read removal, MAPQ filtering at 37, MALT/HOPS and the dated 2017 database composition | Complete historical database and HOPS taxonomy/taxon files; complete workflow and scientific validation |
| Treponemal capture processing | Main text leaves the version unstated. Supplementary section 6.1 explicitly selects eager 2.4.4 with **Conda**, adapter combinations, quality filtering, end trimming and UG genotyping; a separate low-stringency mapping changes BWA parameters | Exact author environment, sample TSV, adapter list, references and read data; provider discrepancy resolution and complete output contracts |
| Human ancestry | Main-text methods: eager 2.3.4, GRCh37/hg19, BWA seed length 32, damage and contamination assessment; engine edition unreported for this role | Intended exact providers, SNP panel and data; separately described mpileup/custom pileupCaller and downstream population analyses |

The [original supplement](https://media.springernature.com/full/springer-static/esm/art%3A10.1038%2Fs41586-024-08515-5/MediaObjects/41586_2024_8515_MOESM1_ESM.docx)
is SHA-256 `cefa7be7b2bb5313cce9ddb23d5369020c2ef74a90b99daf78a889270db23451`.
Its capture command is preserved literally as evidence. Typographic dashes,
quotes, formatting and an unmatched FASTA quote make it unsuitable for direct
copying into a shell. No executable replacement command is admitted here.

## Version warnings that must accompany a dependent analysis

| Observation | Meaning and required action |
| --- | --- |
| Main-text DeDup **0.21.8**; supplementary table S3 and original images **0.12.8** | Retain both statements. Declare the intended exact provider before a paper-specific analysis. |
| Capture UG **3.8** in section 6.3; **vPicked** in table S3; image executable **3.5-0-g36282e4** | A 3.5 software control does not establish a 3.8 analysis. Obtain/declare the required permitted provider. |
| Ancestry BWA **0.7.12** and ANGSD **0.935**; eager 2.3.4 image executables **0.7.17-r1188** and **0.933** | Preserve the source/provider differences; a pipeline tag alone cannot reproduce this role. |
| Separately described custom pileupCaller **8.2.2**; eager 2.3.4 package label **1.4.0.6**, executable **1.4.0.5** | These do not establish the same custom program or step. Supply the intended tool separately. |
| eager 2.4.4 Preseq package label **3.1.2**, executable **3.1.1** | The literal executable agrees with table S3. Retain package and executable versions separately. |
| BWA/Picard help or version commands exit 1; Qualimap rejects `--version`; locale warnings occur | Full diagnostic text and literal exits are retained. A rejected version option does not establish an executable version. |

Two current original Docker images were selected by immutable OCI digest and
prepared in private storage. Their package inventories, literal version/help
diagnostics and SIF integrity hashes are retained. The authors' historical image
digests were unreported. These are separate software controls; they do not
establish the authors' Conda environment, scientific results or image history.

## Use the native Java component

Use Java 17 or newer and the separate
`docs/literature/nfcore-eager-development/nfcore-eager-samples-javac21.jar`
(or the javac25 build). This library has no external runtime dependency.
Save this example as `EagerRows.java`:

```java
import java.nio.file.*;
import java.util.*;
import org.jlinalg.workflow.NfcoreEagerSamples;

public class EagerRows {
    public static void main(String[] args) throws Exception {
        Path base = Path.of(args[0]).toAbsolutePath();
        String[] values = {"sample1", "library1", "001", "4", "SE", "NA",
                           "double", "none", "reads.fastq.gz", "NA", "NA"};
        var row = new LinkedHashMap<String, String>();
        for (int i = 0; i < values.length; i++)
            row.put(NfcoreEagerSamples.COLUMNS.get(i), values[i]);
        var checked = NfcoreEagerSamples.inspect(
            NfcoreEagerSamples.Version.EAGER_244, List.of(row),
            path -> Files.exists(base.resolve(path)));
        System.out.println(checked.get(0).cells());
    }
}
```

From the repository root in PowerShell, compile and inspect an existing read
path with:

```powershell
javac -cp docs/literature/nfcore-eager-development/nfcore-eager-samples-javac21.jar EagerRows.java
java -cp ".;docs/literature/nfcore-eager-development/nfcore-eager-samples-javac21.jar" EagerRows D:/your-input-directory
```

The example expects `reads.fastq.gz` in that directory. Change the literal row
values to your actual metadata. On Linux, use `:` instead of `;` in the runtime
classpath. The API accepts **already parsed maps**: it does not implement
Nextflow's TSV quoting/parser behavior, capture files, validate FASTQ contents,
deduplicate libraries, merge lanes or execute an analysis. `EAGER_234` selects
the other original row contract. The native diagnostic explains null rejection;
upstream 2.3.4 itself calls `isEmpty` on null and refuses it by an exception.

Literal source behavior is retained, including case-sensitive `NA`, suffix checks
that do not require a dot, arbitrary lane/organism/strand cells, repeated IDs,
SE rows containing both BAM and FASTQ, and some incomplete mate combinations.
**Passing row inspection does not validate a library graph or sequencing data.**
The existence predicate must use the same path scope as the intended original
invocation. Results preserve every raw cell and row order in immutable snapshots.

## Verification and retained evidence

The original complete row closures and helper methods ran on the exact
Nextflow 21.04.1/Groovy runtime, with real Nextflow file/Path handling and a
controlled caught rejection in place of terminating the diagnostic harness.
Each version had 49 complete cases: 13 accepted and 36 refused. Both Java
compiler builds matched both source versions on Windows Java 21/25 and Linux
Java 21: **588 case comparisons passed**. This verifies parsed-row semantics,
including deliberately permissive cases; it does not verify TSV parsing or
channel scheduling.

Verify the distributed component and replay all 98 source-version cases with
each build on your current Java runtime:

```powershell
python tools/literature/verify_nfcore_eager_checkpoint.py
```

The verifier uses the already published, unchanged ChIP-seq 2.0.0 development
JAR only for its saved diagnostic JSON reader. The eager library itself has no
such dependency. Missing Java produces a prominent verification skip, not an
analysis result. See the checkpoint README and `VALIDATION.json` for exact scope,
source pins, provenance, retained failures and artifact hashes.

The initial image setup failed when a scientific staged-link checker encountered
an absolute link inside an unpacked container root filesystem. That failed
receipt is retained with an empty owned-live-process list. Separate private
setup then succeeded without weakening scientific input containment. The first
control-helper compilation failure and unsuccessful supplement retrievals also
remain retained. No released executable, global catalog entry or nf-core tool
completion credit changes at this checkpoint. The broader 250-paper goal remains
active.
