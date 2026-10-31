# Supplied chromatin-interaction mapping

**Checkpoint scope:** this guide retains its earlier candidate and validation. The current combined candidate adds optional raw gene-score annotations and typed orchestration; read [FUMA_TYPED_WORKFLOW.md](FUMA_TYPED_WORKFLOW.md), [FUMA_GENE_SCORE_ANNOTATIONS.md](FUMA_GENE_SCORE_ANNOTATIONS.md) and `fuma-workflow-validation.json`. Broader FUMA remains missing.

The native `fuma-tools --method map-genes` command now combines positional,
supplied eQTL and supplied chromatin evidence. It needs Java and local resources.
It does not discover interactions, test their significance, acquire annotations,
infer promoter genes, align alleles, run gene scores/MAGMA/expression, or reproduce
the full hosted FUMA pipeline. **Broader FUMA remains missing.**

Use the exact development candidate in
[fuma-chromatin-validation.json](fuma-chromatin-validation.json):
`build/literature-review/fuma-chromatin-candidate-3/jlinalg-0.3.7.jar`, SHA-256
`62e0e95f61b1ac2c61c67143897b97388244ae965af239f9b3802f17696764d3`.
The canonical executable and public releases are unchanged.

From the repository root:

```text
java -jar build/literature-review/fuma-chromatin-candidate-3/jlinalg-0.3.7.jar fuma-tools --mapping-options-file examples/fuma/chromatin/options.json --out build/fuma-chromatin-example-NEW
```

The example is synthetic. It uses explicit per-interaction membership,
source-closed coordinates, supplied enhancer UIDs and exact promoter keys. It
produces four declared genes, five interaction/variant/gene evidence rows and
four actual mapped candidates. Shared gene/candidate/annotation resources are
under `examples/fuma/mapping/`. Use a new output directory on every run. Review
all warnings and dispositions before interpreting the result.

## Required inputs and choices

The [positional/eQTL guide](FUMA_MAPPING_WORKFLOWS.md) defines common gene,
candidate, annotation, biotype, MHC and resource-budget inputs. Its earlier
candidate did not include chromatin mapping; this guide describes the current
extension. Existing positional/eQTL carriers work without modification and keep
their original five result-table schemas when chromatin is disabled.

Select `--chromatin supplied` and provide these plain UTF-8 TSVs:

| Option | Required columns | Meaning |
| --- | --- | --- |
| `--interactions FILE` | `interaction_id`, `region1`, `region2`, `genes`, `rsIDs`, `tissue` | One uniquely identified supplied interaction per row |
| `--ci-enhancers none` or a file | `uid` | Explicit candidate UID subset; `none` disables this filter |
| `--ci-promoters none` or a file | `region2`, `genes` | Exact region2 key-to-gene assignments; `none` retains the interaction's original genes |

Use numeric `CHR:START-END` region strings, with chromosome 1–23 and
0≤start≤end within signed integer bounds. X must be explicitly represented as23
in these region keys. Gene lists use colons; listed rsIDs use semicolons; `NA`
means no supplied genes/rsIDs. Duplicate interaction IDs or duplicate IDs within
one list fail. Different interaction rows can share coordinates, genes or SNPs;
their provenance remains separate. Tissue labels can contain ordinary spaces,
but no control characters. Unknown genes remain visible in the interaction
ledger and yield no eligible gene links. Unavailable listed rsIDs warn and are
never synthesized. No rsID alias, allele, assembly or coordinate convention is
guessed; retain the original resource releases and preselection procedure.

Explicitly select both policies:

- `--ci-membership source-global` reproduces the original global union of
  listed SNP rsIDs. Candidates can support an interaction through another row's
  listed rsID. The final pool is restricted using retained rows' listed SNPs.
- `--ci-membership per-interaction` additionally requires each supporting
  candidate's rsID to appear in that interaction's own list. This is a declared
  native alternative; it can differ from original FUMA on inconsistent lists.
- `--ci-coordinates source-closed` uses the original supplied closed bounds.
  A zero start is allowed; candidates still have positive one-based positions.
- `--ci-coordinates bed` converts zero-based half-open bounds to one-based
  closed [start+1,end] before comparing candidates. Zero-width intervals fail.
  This is an explicit extension, not automatic detection of an input's origin.

Region2 promoter matching always uses the **literal supplied string**, including
leading zeros. It is not an overlap search and is not normalized by the coordinate
policy. All genes attached to an exact matching key are unioned, replacing the
interaction's original gene list. Region2 is not independently mapped to genes.
The enhancer filter likewise uses supplied UIDs; the command does not annotate
enhancers from BED files. Chromosome or distance between a supplied gene and
region1 does not exclude a supplied interaction; interchromosomal assignments
are possible when explicitly supplied.

Declare `--ci-cadd N --ci-rdb none|N --ci-states none|S1:S2
--ci-binary none|B1:B2`. These filters reuse the existing mapping implementation
and read **annotation-table scores**, unlike positional score filters. Positive
CADD minima and numeric RegulomeDB maxima are inclusive; CADD zero disables
that filter. Missing scores fail enabled filters. Numeric preprocessing of
RegulomeDB labels is required; labels such as `1a` are not converted implicitly.
Selected states need `--ci-state-method any|mode|all --ci-state-max N`; selected
binary tracks need `--ci-binary-method any|majority|all`. State mode ignores
missing values and resolves ties to the smallest numeric state; min/max propagate
missingness. Binary majority is strictly more than half. One binary track works
in Java; the original R branch fails on that shape.

These files must already have the intended interaction/eQTL significance and
tissue selections. This stage performs no interaction P-value/FDR filtering,
FDR estimation, colocalization or causality testing.

## Declarations, flags and actual links are different

Keep all common tables plus `chromatin-evidence.tsv` and
`chromatin-interactions.tsv`. Every admitted eligible edge names its interaction,
candidate UID, gene, tissue and `listed_in_interaction` boolean. Duplicate rows
remain separate evidence; gene variant counts use unique UIDs.

The interaction ledger preserves original/effective/eligible gene lists, literal
regions, listed rsIDs, initial admitting candidates, final overlapping candidates,
retention status and exclusion reason. The candidate ledger adds:

| Field | Interpretation |
| --- | --- |
| `chromatin_filtered_pool` | Candidate passes the initial listed-SNP/enhancer/annotation filters |
| `chromatin_pool_flag` | Source-like retained pool flag; this does not establish a gene link |
| `chromatin_mapped` | Candidate has an actual eligible edge in `chromatin-evidence.tsv` |

**Two retained source failures require review.** A single binary annotation track
causes an R dimension-drop error. Another fixture admits a row using a globally
pooled candidate, removes that candidate from the final pool, then fails with
“no rows to aggregate” during gene summarization. Java handles the scalar filter
and preserves the latter row as `retained-without-final-overlap`. A declared gene
can consequently have zero chromatin variants: GWAS P, LD memberships and loci
remain missing/empty when there are no actual edges. Do not report that declaration
as an observed variant-to-gene association. The session counts actual supported genes in `mapped_genes` and separately reports `retained_gene_rows` and `declared_only_gene_rows`. A declaration-only run requires `--empty retain` and receives `completed-empty-with-review-warnings`; `--empty error` fails with quarantined interaction audits.

**Source-global pool flags can survive rejection of every interaction.** Java
retains those flags for audit and separately reports `chromatin_mapped=false`.
Per-interaction mode clears retained-pool flags when no row is admitted. Inspect
the explicit warnings instead of treating a pool flag as mapped evidence.

Original chromatin summaries can join compound independent-SNP labels using
colons and then split them on semicolons. One fixture produces
`rs1:rs1;rs4:rs3:rs4`; Java reports the separate unique IDs `rs1;rs4;rs3`.
The independent oracle checks those atomic IDs against actual supplied LD
memberships. The original strings and ordering differences remain retained.
Combined gene summaries union actual positional/eQTL/chromatin evidence; no
SNP–gene membership or LD statistic is invented for an unsupported declaration.

## Failures and recovery

**Required action:** review `WARNINGS.md`, `session.json`, `analysis-status.json`,
`manifest.yaml`, both chromatin ledgers and common gene/candidate/exclusion
tables. Confirm assembly/release, original upstream selections, coordinate policy,
row membership, promoter keys, score sources, biotypes and MHC rules.

Exit0 means completed with required review warnings. An empty analysis requires
`--empty retain`; it is not a negative biological conclusion. Invalid inputs,
policy conflicts, changed inputs and resource-budget failures return exit2,
quarantine scientific partials and admit no completed gene table. Correct the
named cause and rerun to a new directory; existing outputs are preserved.

`--ci-max-overlaps` defaults to 1,000,000 and bounds stored region hits before
allocating an oversized result. It applies to the initial pooled interval search,
including hits later excluded by per-interaction membership. `--max-evidence`
bounds combined positional/eQTL/chromatin evidence, and separately bounds retained
chromatin gene-declaration expansion using the remaining budget. Common input
byte/row bounds still apply. Review candidate coverage and row multiplicity
before explicitly increasing a limit. Interval search reuses `GeneWindowMapping`.

No R or original FUMA installation is needed for this native analysis. **SKIP
broader analyses whose required software/data cannot be supplied or installed**;
name the affected stage, cause and permitted recovery. Annotation acquisition,
interaction/eQTL discovery, gene scores, full MAGMA orchestration, expression,
DEG and cell-type workflows remain open. Cell Ranger remains excluded by user
request; no Cell Ranger installation is required.

## Reference validation and portable replay

The current candidate passed 318 tests in 27 suites with no skips, strict Java 17
compilation, Javadoc and executable packaging. All 318 tests also passed against
the exact packaged production classes on Java 21 and25, including original MAGMA
regressions for the shared mapper. The original unmodified R mapping stage ran
36 synthetic interaction protocols:34 completed and 2 failed as described above.
The wrapper retained actual intermediate R tables and errors without patching
the original script. Original source SHA-256:
`3977d60ca891d09576d0125b6c3407d82f2a7312987519b0cd3f28c2a0e41e80`,
commit `bdfbf06b5dd24037ec3c7497d773fad3c2656150` in
[FUMA-webapp](https://github.com/vufuma/FUMA-webapp).

Eighty distinct policy protocols produced 152 packaged workflows:36 source-policy
protocols on Windows 21/25 and Linux 21,36 per-interaction alternatives and8 BED
alternatives. An independent brute-force oracle checks exact edges, flags,
declarations, gene counts/tissues, GWAS minima and atomic LD memberships. The source
protocols have 252 identical cross-profile table groups. A failed original run is
not counted as a successful original reference.

```text
python tools/literature/verify_fuma_chromatin.py --jar build/literature-review/fuma-chromatin-candidate-3/jlinalg-0.3.7.jar --java JAVA_EXECUTABLE --out build/fuma-chromatin-replay-NEW
```

The checked-in replay needs Python3 and Java, uses local synthetic fixtures and
compares all seven tables for 80 protocols. Missing Java skips with exit3, zero
completed protocols and a prominent recovery action. No software is installed by
the replay. The earlier positional/eQTL replay remains available separately:
`tools/literature/verify_fuma_mapping.py`. These checks validate the stated native
subset, not full FUMA, historical paper resources or the full implementation goal.
