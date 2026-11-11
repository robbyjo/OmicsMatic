# StringTie development: complete gene and Ballgown table audit

**This is a native Java audit foundation, not a completed StringTie integration.**
StringTie remains missing. The catalog is unchanged at 67 new scoped integrations
(70 including three existing adapters), 260 missing tools and 819 reviewed uses
from 250 papers. No runtime adapter, `stringtie-tools` command, release, push or
deployment is added by this checkpoint.

`src/main/java/org/jlinalg/sequence/StringTieTables.java` now parses every original
gene-abundance and Ballgown table and checks the complete reference-feature
topology. It builds on the unchanged native
[GTF audit](STRINGTIE_DEVELOPMENT.md). Exact artifact, source and test evidence
belong to `stringtie-tables-development/VALIDATION.json`. The canonical/released
JAR is unchanged and does not contain these new table-audit classes.

## Warnings and required actions

**Do not use structural validation as a scientific inference certificate.** This
audit checks identities, geometry, complete populations, printed-value domains
and source feature sharing. Edition-specific normalization, source readback,
independent alignment/count/coverage-moment checks and inference validation
remain separate work. Printed gene-reduction residuals are diagnostics, not
equality gates. Read them and the complete source tables before downstream use.

**Do not force gene coverage to summed transcript coverage or TPM to one million.**
The preserved synthetic 3.0.3 control has gene coverage `46.666667` for two
transcripts whose printed coverages sum to `51.732438`. Length weighting and the
gene's union exon length matter. A separate original 3.0.3 shared-feature control
reports TPM `1000000.007143`. That exact value is retained. Do not silently
renormalize, round away or replace original values with a desired invariant.

**Unknown strand is an original feature-sharing wildcard.** In the captured
controls, an unknown-strand transcript can share a retained minus-strand feature.
Another fixture retains the feature itself as unknown and links both known
strands to it. The retained feature strand and declared transcript strands remain
separate, unchanged values. Identical coordinates on different chromosomes stay
distinct. These rules are specific to the original source, not generic interval
equality or a claim that the alignments truly originate from an unknown strand.
Some original wildcard features have zero counts despite reads at those
coordinates; this audit preserves those source outputs and does not certify
their count/coverage inference.

**An omitted GTF transcript is not zero expression.** Use an explicit
`COMPLETE` or `RETAIN_OMISSIONS` GTF reference policy. Even with retained GTF
omissions, all Ballgown reference transcripts, genes, features and relations must
remain present and valid. A missing table row is never filled with zero. Gene
diagnostics report emitted and omitted GTF transcript counts so incomplete TPM
reductions cannot be mistaken for complete ones.

**Failures admit no audit result.** Missing/extra rows, conflicting parents,
changed geometry, invalid counts/decimals, exhausted capacities, I/O errors and
cancellation reject the complete family. Preserve raw tables and logs, read the
named table/line or stage, correct the cause and retry into NEW paths. Validator
failures retain `FAILURE.json`, `INCOMPLETE.json` and original process output;
require a complete successful validation receipt. No partial table family is an
admitted result. Capacities are complete-input bounds, not JVM heap guarantees.

**Skip dependent analysis when the exact required runtime cannot be provided.**
These Java audits require no StringTie installation. Running original StringTie
still needs the exact permitted edition and a compatible environment; private
Linux x86_64 copies were used for these controls. If installation/access is
forbidden or a required dependency is absent, report the named requirement and
skip the dependent analysis. Obtain that exact permitted requirement, inspect
its source/license/integrity and health, and retry into NEW paths. Never choose a
different edition silently. Cell Ranger remains excluded.

P171's literal reported `-rf` still fails in its reported StringTie 3.0.0; the
documented `--rf` controls remain distinct. Historical abundance differences and
all eight original flag failures remain in the preceding checkpoint. Hashes
establish integrity, not authenticity or biological correctness.

## Complete contract

Call `StringTieTables.auditEstimation` with six complete Readers, the exact
native `ANNOTATION` and `ESTIMATION` GTF documents, an omission policy, explicit
shared capacities and a cancellation hook. Caller code owns strict byte decoding,
input-byte budgets and Reader lifetimes. The table names are:

| Reader | Required original table |
| --- | --- |
| genes | `-A` gene-abundance output |
| transcripts | `t_data.ctab` |
| exons | `e_data.ctab` |
| introns | `i_data.ctab` |
| exonLinks | `e2t.ctab` |
| intronLinks | `i2t.ctab` |

All source headers, rows, cells, decimal tokens, line endings and terminal-newline
dispositions are retained. No source row is ignored. Blank/comment/extra rows and
wrong column populations reject these original tabular formats. LF, CRLF and an
unterminated final row are supported; bare CR, malformed Unicode and control
characters reject the input.

Every Ballgown transcript must match an exact guide identity, gene parent, gene
name, chromosome, strand, span, exon count and exonic length. Reference chromosome
groups cannot reappear. Numeric IDs are contiguous original row ordinals. Every
exon and intron and every ordered feature-to-transcript relation must match the
source-compatible feature insertion replay. That replay retains the original
match-cache behavior and non-transitive unknown-strand comparisons. No feature
is shared across chromosomes.

Counts retain their unsigned-32-bit domain. Unique counts cannot exceed raw
counts; printed weighted counts must be compatible with raw/unique counts at
their two-decimal reporting precision. All four exon coverage moments remain
nonnegative finite source values at four decimals. Transcript coverage/FPKM use
six decimals. Gene quantitative fields retain six decimals or the original
literal `0.0`. These domain checks do not recompute the counts or moments.

Gene identities, complete reference population, chromosome, strand, full span
and supplied name are checked. Missing source gene-name placeholders `-` and `.`
remain visible; they are not new gene identities. Gene rows have no ordinal IDs:
they are matched by scientific identity, and their supplied order is preserved.
Duplicate/missing/extra gene rows still reject the family. Adjacent guide exons
require separate original loader-normalization admission and produce an explicit
error here; they are not silently merged or interpreted as an invented intron.

The audit retains each gene's union exon length and exact decimal residuals
between the printed gene fields and printed transcript reductions. The weighted
coverage residual is `geneCoverage * unionExonLength` minus
`sum(transcriptCoverage * transcriptLength)`. It is not a confidence interval or
an exact unrounded gene-coverage calculation. FPKM and TPM residuals likewise
remain diagnostics. Inference/normalization checks must run separately before a
complete scientific workflow can claim those gates.

## Evidence and next implementation work

The fixtures cover 35 complete original expression/table families across 1.3.0,
1.3.6, 2.1.2, 2.1.7, 2.2.1, 3.0.0 and 3.0.3: 14 previous populated/empty
estimation controls and 21 fresh sharing/wildcard controls. Each family includes
all six tables, its GTF and guide, command, logs and process receipt. Exact
original source and edition-specific licenses are retained. No third-party
executable is bundled in the fixtures or evidence archive.

The Java suite checks every original family and tampered/malformed complete
populations, both wildcard representative directions, chromosome boundaries,
explicit omissions, original numerical discrepancies, read/cancellation errors,
immutability and exact resource limits. The first test attempt is retained: its
only failure was a test imposing an unsupported gene-row order. The corrected
test explicitly verifies identity matching and preservation of the supplied
order. Topology cancellation was also strengthened before the final run.

Remaining work includes edition-aware source readback and normalization,
independent alignment/count/coverage auditing, exact runtime setup and admission,
typed assembly/estimation/merge CLI workflows, complete portable output-bundle
verification, paper-use curation and catalog/browser gates. StringTie remains
missing until these required public integration gates are validated.

Official references: [StringTie manual](https://www.ccb.jhu.edu/software/stringtie/index.shtml?t=manual),
[original source releases](https://github.com/gpertea/stringtie/releases).
