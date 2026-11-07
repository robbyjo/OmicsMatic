# HTSEQ TOOLS: WARNINGS, ERRORS AND REQUIRED ACTION

# HTSeq tools development guide

The separate HTSeq development JAR provides `htseq-tools --request UTF8_JSON` for original SAM/BAM–GTF COUNT and portable Java-only VERIFY. It also contains two native Java APIs: [prepared-read classification/counting](HTSEQ_READ_COUNT_DEVELOPMENT.md) and [binary peak-by-cell read presence](HTSEQ_BINARY_ACCESSIBILITY_DEVELOPMENT.md). These scopes remain distinct, with exact source versions and caller-supplied preparation.

Use `build/literature-review/htseq-runtime/tools-development-final/omicsmatic-0.3.7-htseq-tools-development.jar`. The canonical/released executable is preserved and does not contain this route. Start with the [checked file-count example](../../examples/htseq-file-count/README.md), the [native binary example](../../examples/htseq-binary-accessibility/README.md), or the [native prepared-read example](../../examples/htseq-read-count/README.md).

```sh
java -jar build/literature-review/htseq-runtime/tools-development-final/omicsmatic-0.3.7-htseq-tools-development.jar htseq-tools --help
java -jar build/literature-review/htseq-runtime/tools-development-final/omicsmatic-0.3.7-htseq-tools-development.jar htseq-tools --source-report
java -jar build/literature-review/htseq-runtime/tools-development-final/omicsmatic-0.3.7-htseq-tools-development.jar htseq-tools --request /absolute/path/request.json
```

**SKIP3 — analysis skipped; required action:** a required permitted Linux Java/Python/package/helper is unavailable or forbidden. Read the exact cause and `WARNINGS.md`, skip the dependent analysis, and provision the exact declared private profile only where installation is allowed. Retry into new paths. If installation is forbidden, retain the skip. No COUNT operation installs software or substitutes a release. Native prepared-read/binary APIs need Java only; VERIFY also needs Java only and runs on Windows.

**FAIL2 — no result admitted; required action:** retain inputs, the complete attempt, `WARNINGS.md`, status/INCOMPLETE markers and original process logs. Correct the stated option, source/input identity, table, process, cancellation or capacity cause, then retry into new paths. Do not round fractions, repair identifiers, relax filters or reinterpret a source failure as negative biology. Uncommitted diagnostics may remain in the run root or `partial/`. Require `COMPLETED.json`, no `INCOMPLETE.json`, and successful VERIFY before admitting the complete result bundle.

COUNT supports exact original source profiles 0.5.3, 0.6.0, 0.11.1, 0.13.5 and 2.1.2 with their declared tested Python/NumPy/pysam environments. All scientific options are explicit: strand, overlap, nonunique allocation, flag policies, MAPQ, GTF feature/ID fields, input format/order and original automatic read/pair detection. No unstated paper flags or patch version are inferred. See the [file contract and permitted setup commands](HTSEQ_FILE_COUNT_DEVELOPMENT.md). Fresh stable package/interpreter identities include bytecode and compiled/generated files; exact installed scientific Python must match original sources. Base standard library, loader/system DLLs and whole-host/execution authenticity remain outside this guard.

The original gene counter retains its edition behavior. In particular, 0.5.3 uses unprefixed summary names and skips unknown chromosomes without counting them as no_feature; 0.5.3/0.6.0 score secondary/supplementary records and reject the tested header-only SAM and `=`/`X` CIGAR inputs. All accepted MAPQ values are explicit; **minimum 21 implements strict MAPQ>20**. FRACTION divides by assigned-feature count, not NH, and summary categories can overlap. RANDOM is not admitted. No proper-pair prefilter, normalization, FPKM, differential model or cell/barcode/UMI processing is supplied by this command.

VERIFY validates the complete committed file inventory, exact source profile, recorded options/arguments/commands/capacities, actual import probe, zero-exit/cleanup records, literal source table and exact derived tables. It detects missing/extra files, incomplete attempts and inconsistent metadata even if hashes were recomputed. It does not independently parse/pair SAM/BAM–GTF, execute HTSeq again, authenticate execution, recover original RNG/statistical fits or certify biological accuracy. Fully fabricated but internally coherent evidence is outside integrity-only verification. Zero counts/empty matrices are not negative biological findings.

The eight primary roles retain these paper-specific boundaries:

| Paper | Declared HTSeq role and boundary |
| --- | --- |
| P005 | HT-seq 0.6.0 RNA counts after STAR/mm10; effective annotation/flags are unstated |
| P020 | 0.5.3 gene counts after HISAT2/hg38; library description does not establish the HTSeq strand or read/pair choice |
| P044 | 0.13.5 GENCODE vM25 gene assignment; FPKM/log transforms are downstream |
| P099 | Custom peak-by-cell binary read presence; exact edition, ATAC script, cell assignment and filtering are unknown; native prepared presence implements the stated primitive |
| P113 | 0.11.1 RNA/exon counts; EU-region counts use an edition not separately stated; TSS windows/filtering/normalization remain separate |
| P121 | 0.13.5 interval counts with strict MAPQ>20/proper pairing; effective API and read/fragment unit remain unstated |
| P148 | Gene counts after STAR/hg19; edition/annotation/flags are unstated; FPKM/Wilcoxon are separate |
| P195 | Reported 0.13 is not an exact 0.13.5 patch declaration; HISAT2 RF does not determine HTSeq strand; limma-voom is separate |

The native binary API marks every supplied overlapping peak for its explicitly assigned cell, keeps exact supplied axes/order and zero rows/columns, and caps every coordinate at one. It does not apply gene-count ambiguity/NH allocation or recover custom author code. Callers supply read blocks, strand interpretation, filtering, peak construction/merging and cell identities. Native APIs return no partial result after invalid input, cancellation or logical-budget failure; callers retain failed attempts themselves.

All controls are bounded synthetic/source comparisons, not full biological datasets or whole-paper reproductions. Exact source/dependency versions do not establish whole historical environments. Development validation and catalog admission are separate milestones. Cell Ranger remains excluded. This guide does not claim a release, push, deployment, full project suite or whole-goal completion.


This runtime checkpoint does not itself increment catalog credit. Earlier candidates, genuine upstream failures, negative controls and forced commit failures are retained. Diagnostic fixes change only error naming and incomplete-report quarantine; all numerical/native parent entries remain unchanged.
