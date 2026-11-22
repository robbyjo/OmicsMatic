# FUMA numerical and locus-definition findings

**Use the native log probabilities and review strict threshold boundaries.**
Legacy numerical discrepancies are retained; they are not raw numeric matches.
All 70 packaged workflow decisions/counts and displayed gene/link fields agree
with the unchanged original backend on the tested synthetic protocols.

For N=2000 and selected=500, the exact singleton probability is 0.25. Legacy
SciPy 1.2.1 returned 0.24999999999945904; Java returned 0.2500000000000002.
The largest measured native-versus-original probability difference was
5.411782133535326e-13. The strict original
numeric-tolerance comparison remains `failed-comparisons-retained` in
`build/literature-review/fuma-runtime/native-matrix-2/observed.json`.
An independent integer-binomial/Fraction audit of all 1024 native rows passed,
with maximum relative raw-P error 3.910016149783362e-15
and maximum absolute log-P error 2.273736754432321e-13.
That derived proof is `native-matrix-exact-audit-1/observed.json`; it does not
overwrite the failed raw comparison. The tiny underflow fixture preserves a
finite native log probability even when displayed original/native P rounds to 0.

Legacy statsmodels 0.10.0 Sidak/Holm-Sidak uses subtractive arithmetic that can
round very small adjusted P to zero. Java uses stable log1p/expm1/log arithmetic.
Every tested native Sidak value also passed an independent 400-digit decimal
calculation at 2e-12 relative tolerance. Differences from legacy values must remain
within the recorded family-size floating-point cancellation bound; they are
explicit numerical extensions, not blanket tolerance relaxations.

An early Java BKY implementation compared a rounded adjusted-log value with the
first-stage alpha. The exact rank-boundary vector [.05/1.05/3,.2,.9] exposed an
incorrect first-stage count. The corrected candidate compares sorted raw-log P
with rank thresholds before adjustment rounding, returning approximately
[.0333333333333333,.21,.63]. Its regression test and all 13920 correction-field
comparisons pass. The earlier failure remains in `corrections-1/observed.json`.

Required action: retain the exact resources, correction method, first-stage alpha
and later display cutoff; inspect all-tests.tsv and the source numeric findings
before interpreting results near a strict threshold. Rounded zero is not an exact
probability. Synthetic arithmetic checks do not establish biological calibration.

## Supplied-reference locus findings

**Native loci use complete interval components; original backward-merge failures
remain retained.** The pinned original getLD functions were executed without code
changes on 43 synthetic protocols using real indexed BGZF frequency/LD files and
pytabix. They completed, but exit success did not prove valid locus membership.

Four protocols violate locus invariants. In `backward-bridge-multiple`, the source
leaves loci [100,150] and [50,3000] separate after a partial backward merge. They
overlap and should form one component. In `backward-bridge-followed-by-merge`, a
later merge loses the earlier rs111 membership even though its UID still maps to
that locus. Two additional generated LD-group examples also lose memberships.
Actual original outputs and logs remain in `locus-original-1`; the independent
all-pairs interval-graph audit is `locus-original-audit-1` under the private FUMA
research cache. The public synthetic example retains its exact defective source
partition in `examples/fuma/loci/retained-original-loci.tsv`.

The native sweep forms complete components and preserves the full candidate,
independent and lead unions. This is a deliberate correction, not a raw match to
defective original tables. Merge distance is explicitly strict or inclusive;
source code uses strict gap < distance, while parts of the tutorial label it <=.
Physical overlaps merge even when the distance is zero.

Two additional original protocols verify that independent-SNP table counts are
incremental. A block containing two candidates can report one new candidate after
sharing one with a previous block. A forced correlated seed can report zero new
candidates despite a full block of three. Native `independent.tsv` preserves both
full-block and source-new counts with distinct column names. Do not treat these
two counts as equivalent.

The original pandas 0.24.2 reader changes 64 parsed numerical inputs in the first
36 protocols. One changes a strict cutoff decision: the supplied decimal
0.049999999999999996 is rounded to 0.05, so the original excludes it at P < 0.05.
Native parsing retains the supplied double and original decimal, admitting it
under that rule and warning about the boundary. The raw comparison failure is
retained in `locus-core-comparison-1`; a derived input-aligned audit isolates
method behavior from parser behavior. Python 2's limited printed-P precision is
handled with exact 12-significant-digit half-unit intervals, rather than a generic
absolute epsilon.

With 50 equal-P variants in three perfect-LD groups, the original NumPy quicksort
selects rs300/rs328/rs329, while the declared native coordinate policy selects
rs300/rs301/rs302. Both original outputs and the independent perfect-clique
coordinate calculation are retained. `--ties error` refuses actual unclaimed
choices; `position` explicitly chooses the coordinate rule and warns. This is a
specified tie rule, not evidence for a uniquely causal index variant.

Predefined seeds bypass their own P and first-stage independence filters; MHC and
region filters still apply. MHC exclusion removes variants, while outer locus
bounds can span the excluded interval. Predefined GWAS regions can also admit
outside-region reference-only LD partners. These behaviors have run warnings and
separate input/filtered-role ledgers; they must not be interpreted as clipping
every base or gene within locus bounds.

The current locus candidate passed 224 focused tests in 20 suites, all 224 tests
against exact packaged classes on each of Java 21/25, and 129 actual typed native
workflows over the 43 protocols on Windows Java 21/25 and Linux Java 21. Those
workflows check 786 candidate rows; 30 additional controls/public/Unicode cases
and seven cross-profile identical table groups also passed. These synthetic tests
do not show that any catalogued paper's reported loci were affected or establish
biological LD calibration. Read FUMA_LOCUS_WORKFLOWS.md and the exact proof hashes
in fuma-locus-validation.json before using this development module.

## Positional/eQTL mapping-stage findings

The unmodified `scripts/genemap/geneMap.R` at pinned commit bdfbf06b was run under
private R4.2.3 with explicit synthetic resources in 32 protocols. Thirty-one
completed. A single selected binary eQTL track fails at `apply()` because the
one-column subset loses matrix dimensions; Java handles that case with an
independent scalar-filter oracle. The failed original run is retained in the
public reference fixture. It is not counted as successful original analysis.

The script reads eQTL significance/tissue parameters without applying them in
this stage; supplied `eqtl.txt` is already selected upstream. A fixture changing
those parameters leaves the mapping unchanged. The native command therefore
requires explicit extra `none` or inclusive P/FDR/tissue choices. It does not
claim discovery, harmonization or colocalization.

Gene MHC exclusion uses MOG start and COL11A2 **end after biotype filtering**;
endpoint inclusion leaves a gene spanning the whole interval. Native source
endpoint and full-overlap policies are explicit. Positional filters read CADD/
RegulomeDB from candidates, whereas eQTL filters and positional maximum CADD
read annotations; mismatches warn. Consequence regex search can match
`nonexonic` when given `exonic`, and different consequence rows can inflate
source columns labeled SNP counts. Native unique-variant and evidence-row
counts remain separate, missing maximum CADD remains NA, and formatted tissue/
independent-SNP/locus lists are deduplicated. These are declared summary choices,
not a claim of byte-identical legacy output. Read
[the mapping guide](FUMA_MAPPING_WORKFLOWS.md) and `fuma-mapping-validation.json`.

All 32 protocols have independent per-association checks and 96 current packaged
workflows across three JVM/OS profiles, with 160 identical table groups. The
checked-in portable replay retains31 original gene tables and one original
error, and passed 64 workflows on Windows/Linux. Earlier failed setup attempts,
the first replay's overly restrictive name check, and the initial incomplete
archive-change assertion are retained and superseded by the successful audits.
No upstream source was modified or embedded in production Java.

## Supplied chromatin-interaction stage

The unmodified original mapping stage ran 36 synthetic interaction protocols:
34 completed and 2 failed. The wrapper retained actual CI row/link/SNP tables
without patching geneMap.R. One selected binary annotation track causes dimension
drop before apply. A second fixture admits an interaction via a globally pooled
SNP, removes that supporting SNP from the final pool after promoter filtering,
then fails with “no rows to aggregate” during gene summarization. Both errors
remain in the public reference fixture; neither is counted as successful R analysis.

Source rows use a global union of listed SNP rsIDs. Cross-row pooling can create
links not listed in a particular row, and source pool flags can remain1 even
after every interaction is rejected. Native source-global mode retains those
flags and separately reports actual eligible links. Per-interaction membership
is an explicit alternative. Unsupported source declarations can remain visible
with zero actual edges and missing GWAS/LD/locus evidence; they are not observed
variant-to-gene associations.

Promoter keys match literal region2 strings, including leading zeros, and replace
original genes. Native source-closed versus BED conversion is explicit. Source
chromatin LD summaries can join compound labels with colons before splitting on
semicolons:one fixture produces `rs1:rs1;rs4:rs3:rs4`. Native atomic unique IDs
are `rs1;rs4;rs3`, checked against supplied LD memberships. Raw original strings
and ordering differences are retained. Duplicate interaction rows and tissue
provenance remain separate from unique gene-variant counts.

The 318-test candidate passed 152 packaged workflows across 80 independent policy
protocols, with 252 identical source-policy table groups across three JVM/OS
profiles. Ten public/Unicode/recovery cases and 160 portable chromatin replays
passed; 64 prior positional/eQTL native replay processes also passed. The first
portable CI verifier failed before completing a protocol because its new CI
resource paths were not rebound to the fixture directory; the corrected verifier
passed. An interrupted Linux attempt was confirmed terminal and retained before
rerunning only Linux; completed Windows and prior native replay evidence was reused.
The initial 318-test gate's 6 failures were test-reader errors: MAGMA's whitespace
reader could not parse true TSV spaces,blank cells and intentional empty results.
The corrected TSV test reader passes; production was unchanged by that test repair.
Read [the chromatin guide](FUMA_CHROMATIN_WORKFLOWS.md) and
`fuma-chromatin-validation.json`. Full FUMA and the complete implementation goal
remain open.

The final chromatin candidate separates session mapped_genes from retained_gene_rows and declared_only_gene_rows. Declaration-only outputs follow the explicit empty policy;their GWAS/LD fields remain missing. The enhanced regression passed all318 tests,318 exact packaged tests per Java21/25,152 scientific workflows,10 controls and224 portable replays on this final candidate. The previous candidate and its valid numerical/edge evidence remain retained. Only CLI admission/reporting classes changed;the native math classes and table schemas are byte-identical.

## Raw gene-score annotations and typed orchestration

Twenty unmodified full geneMap.R score-resource protocols completed19 runs and
retained one four-column error. Seventeen valid schemas match exact first-ID
selection/missing payloads. R accepts missing ensg and silently emits allNA;
native rejects that malformed schema and duplicate headers. A singleton payload
loses its source name in R. R type inference/printing rounds precise decimals
and subnormals. Native namespaced headers and raw text deliberately preserve the
supplied resource;no numerical score is calculated or validated.

The29-case independent score fixture passed87 packaged replays across Windows
Java21/25 andLinux21:17 valid joins and12 controlled failures perprofile,with153
identical table groups. Initial failure controls expected different missing-key
wording and bounded total columns instead of payload columns;only the fixtures
were corrected. Production behavior was correct and those attempts are retained.

The typed workflow passed57 packaged runs:17 native/control protocols perprofile
and six actual original MAGMA runs across mean/top/multi models on Windows21/25.
All201 published gene rows match frozen original references;202 annotated outcomes
retain one empty gene without inventing a test. A first verifier mistakenly
expected202 published tests;the reference,production output and gene-status
ledger correctly showed201. A Unicode argv run through the Windows Java25
launcher failed before application code after replacement with question marks.
All17 native/control protocols pass using an ASCII carrier and Unicode JSON
resource/output paths. Both original attempts remain failed,with recovery.

The first362-test gate retained one assertion failure:GENE2FUNC's statistical
eligibility constructor rejected a symbol-ambiguous singleton before the workflow
could publish its namespace diagnosis. A shared inspect preparation now retains
the same filter/namespace audit before statistical admission;the existing prepare
API still rejects fewer than two selected IDs. The final365-test gate and exact
packaged tests on both JVMs have no failures,errors or skips. No association test
is admitted with insufficient query/background coverage.

Read [FUMA_TYPED_WORKFLOW.md](FUMA_TYPED_WORKFLOW.md),
[FUMA_GENE_SCORE_ANNOTATIONS.md](FUMA_GENE_SCORE_ANNOTATIONS.md) and
`fuma-workflow-validation.json`. This is supplied-resource subset validation;
fullFUMA and the remaining271 integrations stay open.
