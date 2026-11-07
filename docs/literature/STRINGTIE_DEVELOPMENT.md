# StringTie development: native GTF audit foundation

**StringTie is not yet an implemented public integration.** The catalog stays at
67 new scoped integrations (70 including three existing adapters), with StringTie
among the 260 missing tools. This checkpoint adds a Java structure/reference
audit and preserves original controls. It does not add a `stringtie-tools` CLI,
run a complete paper pipeline or certify biological accuracy.

The native API is
`src/main/java/org/jlinalg/sequence/StringTieGtf.java`. Its exact development
artifact and evidence are recorded in
`stringtie-gtf-development/VALIDATION.json`. Older canonical/released JARs do not
contain this API. The canonical executable is preserved. Nothing in this
checkpoint is released, pushed or deployed.

## Warnings and required actions

**P171's literal `-rf` option fails in its reported StringTie 3.0.0.** All seven
captured editions reject that literal spelling. The distinct documented `--rf`
control succeeds in six editions and is unavailable in 1.3.0. Do not silently
replace a reported command and call it a reproduction. Retain both commands and
their original logs; resolve the intended settings from the study's actual code
before attempting a study reproduction.

**Historical editions produce different abundance values.** In this synthetic
fixture, transcript `ta` has printed TPM `0.000000` in 1.3.0 and
`216849.625203` in 3.0.3. This is a direct control observation, not a biological
finding. Keep the exact edition, input, options, raw values and uncertainty.
Later-edition results cannot stand in for an unspecified or older paper edition.

**Missing transcripts are not zero expression.** Native `auditEstimation` checks
every reference identity, gene parent, contig, strand and exon interval.
`COMPLETE` rejects an omitted transcript. `RETAIN_OMISSIONS` reports an explicit
`OMITTED` disposition with no output ordinal or invented quantitative values.
An original printed zero remains an original zero. Choose the policy explicitly
and inspect the complete reference ledger; an omission-tolerant audit does not
establish complete quantified reference coverage.

**Unavailable dependencies mean skip the dependent analysis.** Original
StringTie controls require an allowed Linux x86_64 runtime and the exact captured
binary. No system installation was needed here: archives were extracted only
under the workspace. If that environment, executable or required access is
unavailable, retain the named skip/error and original logs, install or provide
the exact permitted requirement, and retry into NEW paths. Do not substitute an
edition. The Java GTF API itself needs no StringTie runtime or installation.

**Syntax, identity, geometry, capacity or read failures admit no document.** The
API throws an exception; unchecked cancellation also propagates. The validator
retains `FAILURE.json`, `INCOMPLETE.json`, warnings and process logs on failure.
Preserve the failed attempt, correct its named cause, and retry into a NEW output
directory. Require a successful complete validation receipt before using that
candidate as evidence. Resource limits bound complete input, not JVM heap needs.
Caller code must separately enforce input-byte limits and strict decoding.

**Structure is not inference validation.** This API does not check the complete
alignment population, recompute assembly or abundance inference, validate gene
abundance/Ballgown tables, or prove normalization. Hashes establish integrity, not
authenticity or biological correctness. GffCompare, HISAT2, STAR, RNA-SeQC,
EVidenceModeler, Ballgown and downstream differential expression remain separate
scientific operations. Cell Ranger remains excluded by the user.

## Source roles retained for the next integration

The source census verifies all 250 cached primary XML hashes and finds eight
papers with 13 named StringTie paragraphs, including supporting/result context.
The full texts and exact anchors are preserved in the checkpoint evidence. The
existing catalog objects are not rewritten or promoted by this development step.

| Paper | Reported edition and use | Boundary to preserve |
| --- | --- | --- |
| P101 | 2.1.7, default transcript assembly from HISAT2 alignments | HISAT2 intron settings and MAKER2 annotation are separate. |
| P102 | 1.3.6, gene/transcript TPM with default settings | The complete sentence gives the edition even though the current selected catalog field is null. Exact quantification flags/resources are unreported. |
| P109 | 1.3.0, reported extraction of “mapping states” in genome annotation | Preserve the wording and uncertainty; do not attribute alignment or the entire annotation pipeline to StringTie. |
| P110 | TPM for supplied gene models, edition unspecified; separately 2.1.2 transcript-model prediction | Do not assign 2.1.2 to the separate TPM run without evidence. Trinity/Mikado/PASA integration is distinct. |
| P171 | 3.0.0 assembly with `-m 150 -t -f 0.3 -rf`, plus transcript merging | Literal strand option fails; exact merge commands are not printed. Retain unknowns and separate documented-option controls. |
| P183 | Guide-assisted `-G` assembly and explicit `--merge` | GffCompare and RNA-SeQC quantification are separate. Reported hg19 and GENCODE v38 require exact resource/build reconciliation, not automatic substitution. |
| P223 | HISAT2/StringTie/Ballgown workflow, edition unspecified | StringTie is not the aligner; Salmon counts and edgeR differential expression are separately reported. |
| P248 | 2.2.1 transcript assembly from HISAT2 alignments | BRAKER2, EVidenceModeler selection and repeat filtering are separate. |

## Native API contract

`StringTieGtf.read` accepts a complete `Reader`, an exact local chromosome-size
map, an explicit `ANNOTATION`, `ASSEMBLY`, `ESTIMATION` or `MERGE` profile, complete
resource limits, and a cancellation checkpoint. Use one-based inclusive
coordinates within the positive signed-32-bit domain; chromosome aliases and
assembly identities are never inferred. Build labels alone do not authenticate
reference resources.

The reader retains every source line and its LF/CRLF or missing final terminator,
all attribute order and original decimal tokens. Repeated auxiliary attributes
such as GENCODE `tag` are preserved. Reserved parent, ordinal and quantitative
attributes must be unique. GFF3, backslash-escaped attribute values, malformed
Unicode and bare-CR line endings require separate contracts.

Every transcript needs complete, nonoverlapping exons with consistent gene,
chromosome and strand. A supplied transcript span must equal its full exon span.
Annotation exons may appear out of order and omit a transcript header; their
derived span is explicitly separate from a declared transcript feature.
Original output requires a transcript header, transcript/exon features only,
and exon numbers in genomic order, including minus-strand transcripts. The API
does not reorder or rewrite the retained source lines.

Assembly/estimation output requires complete nonnegative finite `cov`, `FPKM`
and `TPM` attributes. Exact decimal text, precision and signed zero are retained.
Merge output lacks those quantities and is not assigned zeros. Annotation may
carry selected quantitative attributes without completing the missing triple;
the raw attributes remain accessible. These checks do not recompute abundance.

Example for a supplied reference annotation and a completed original estimation:

```java
var limits = new StringTieGtf.Limits(
    1_000_000, 1_000_000, 100_000, 10_000, 1_000, 65_536, 134_217_728);
// Dictionary and strict UTF-8 Readers come from independently captured local inputs.
var reference = StringTieGtf.read(referenceReader, chromosomeSizes,
    StringTieGtf.Profile.ANNOTATION, limits, cancellationCheckpoint);
var result = StringTieGtf.read(outputReader, chromosomeSizes,
    StringTieGtf.Profile.ESTIMATION, limits, cancellationCheckpoint);
var coverage = StringTieGtf.auditEstimation(result, reference,
    StringTieGtf.PopulationPolicy.COMPLETE);
// Inspect every coverage row and preserve every source line and original value.
```

## Evidence and remaining work

Private official source/binary intakes cover 1.3.0, 1.3.6, 2.1.2, 2.1.7, 2.2.1,
3.0.0 and 3.0.3. Each original version check passed in the recorded Linux host.
Licenses are retained per edition: old source licenses must not be relabeled as
the current MIT license. Publisher SHA-256 digests are checked where available;
otherwise the captured download hashes are identified as integrity records.
Third-party executables are not redistributed in the test/evidence archives.

The synthetic fixture has 840 primary alignment rows, 120 spliced rows, four
reference transcripts, an uncovered reference transcript and one supported
unannotated locus. It tests geometry, not reference-sequence or aligner truth.
Of 49 original protocols, 41 succeed and eight flag failures remain retained.
Native tests consume every successful GTF, preserve complete lines/values and
check every estimation transcript against the reference. Malformed/tampered
identity, exon, numeric, mode, capacity, read and cancellation controls are also
included. The exact final matrix and class counts belong to `VALIDATION.json`.

Remaining implementation: exact original runtime setup/admission, complete
alignment contracts, gene and Ballgown audits, inference/normalization checks
where independently supportable, typed assembly/estimation/merge workflows,
portable bundle verification, paper-use curation and catalog/browser gates.
StringTie stays missing until that work is validated.

Official upstream references: [manual](https://www.ccb.jhu.edu/software/stringtie/index.shtml?t=manual),
[release history](https://github.com/gpertea/stringtie/wiki/History), and
[source releases](https://github.com/gpertea/stringtie/releases).
