# Native FUMA positional and eQTL mapping

**Checkpoint scope:** this guide retains its earlier candidate and validation. The current combined candidate adds optional raw gene-score annotations and typed orchestration; read [FUMA_TYPED_WORKFLOW.md](FUMA_TYPED_WORKFLOW.md), [FUMA_GENE_SCORE_ANNOTATIONS.md](FUMA_GENE_SCORE_ANNOTATIONS.md) and `fuma-workflow-validation.json`. Broader FUMA remains missing.

**Checkpoint scope:** this guide retains its earlier validated candidate and formats. The current combined command also supports supplied chromatin interactions; read [FUMA_CHROMATIN_WORKFLOWS.md](FUMA_CHROMATIN_WORKFLOWS.md) and `fuma-chromatin-validation.json`. Broader FUMA remains missing.

`fuma-tools --method map-genes` maps supplied candidates to supplied genes using
gene windows or consequence labels, and retains supplied eQTL associations after
explicit filters. It needs only Java. The wider FUMA integration remains missing:
annotation acquisition, eQTL discovery and harmonization, chromatin interactions,
gene scores, full MAGMA orchestration, expression and cell-type workflows are open.
This command does not reconstruct historical paper data or the hosted service.

Use the exact development JAR in [fuma-mapping-validation.json](fuma-mapping-validation.json):
`build/literature-review/fuma-genemap-candidate-2/jlinalg-0.3.7.jar`, SHA-256
`3ea1375311d930cd73416527ac50be64a50016b968faa9309643eff70a1161ea`.
The canonical executable and public releases are unchanged.

```text
java -jar JAR fuma-tools --mapping-options-file examples/fuma/mapping/options.json --out build/fuma-mapping-example-NEW
java -jar JAR fuma-tools --method map-genes --help
```

Run the example from the repository root. Its resources are synthetic, with
deliberately different candidate/annotation scores and nonsignificant supplied
eQTL rows. It produces six genes. The UTF-8 JSON carrier contains string values;
direct command options override it, and both the carrier hash and effective
options are recorded. This also permits Unicode input/output paths inside the
carrier. Use a new output directory for every attempt.

## Required resources and choices

All inputs are plain UTF-8 headered TSVs. IDs must be unique in gene, candidate
and annotation resources. Annotation IDs must match candidate IDs exactly;
represent missing values explicitly with `NA`. Numbers must otherwise be finite.
No build conversion, prefix stripping or annotation resource download is inferred.

| Resource | Required columns |
| --- | --- |
| `--genes` | `ensembl_gene_id`, `external_gene_name`, `chromosome_name`, `start_position`, `end_position`, `strand`, `gene_biotype` |
| `--candidates` | `uid`, `rsID`, `chr`, `pos`, `gwasP`, `CADD`, `RDB`, `locus`, `independent_rsIDs` |
| `--annotations` | `uid`, `CADD`, `RDB`; selected state tracks use `state.NAME`, binary tracks `binary.NAME` |
| `--consequences` | `uid`, `gene`, `annot`; needed for consequence mapping |
| `--eqtls` | `uid`, `gene`, `db`, `tissue`, `p`, `FDR`, `alignedDirection`; needed for eQTL mapping |

Declare `--annotation-id` as a whitespace-free assembly/release identity, and
`--biotypes all` or an explicit colon-separated set. Chromosomes are integers
1–23, with `X` accepted as 23. Y/other contigs are explicitly unsupported by this
bounded subset. Gene positions are positive one-based closed intervals; strand
is `+`, `-` or `.`. Candidate positions are positive. Loci are positive integers.
Independent rsIDs use semicolons, or `NA` for no supplied membership. Retain the
original LD membership resource and provenance; the command does not estimate LD.
Unknown genes in consequence/eQTL evidence are excluded by the eligible gene
resource. Unknown candidate IDs fail. P and FDR values must be in [0,1] or `NA`.
Aligned directions are `+`, `-` or `NA`; their allele alignment must already be
established upstream.

Choose `--positional window --window-bp N`,
`--positional consequence --consequences FILE --consequence-regex REGEX`, or
`--positional none`. Symmetric windows include both endpoints and ignore strand.
They reuse `GeneWindowMapping`; clipping an expanded start at zero does not change
membership for positive candidate positions. Coordinates are not BED starts.
Consequence matching uses regex search, as in the source, so `exonic` also matches
`nonexonic`. Use an anchored regex when exact labels are intended. Exact duplicate
consequence triples are removed; different labels for one pair remain evidence.

Choose `--eqtl supplied` with `--eqtls`, `--eqtl-p none|P`,
`--eqtl-fdr none|Q` and `--eqtl-tissues all|TISSUE:TISSUE`, or `--eqtl none`.
**The original geneMap.R reads significance/tissue settings but does not apply
them at this stage.** It receives a previously selected `eqtl.txt`. Native `none`
preserves supplied selection; numeric P/FDR cuts are additional inclusive filters
that reject missing values, and named tissues match input tissue tokens exactly.
Nonsignificant supplied rows remain when no extra cut is requested. This is not
eQTL testing, FDR estimation, colocalization, causal inference or allele alignment.

## Annotation and MHC filters require review

For each enabled module explicitly supply the `pos-` or `eqtl-` options:
`cadd`, `rdb`, `states` and `binary`. CADD zero disables the filter; positive
minimum CADD and numeric maximum RegulomeDB are inclusive. `rdb=none` disables
that filter. Inputs must contain numeric preprocessed RegulomeDB scores; ordinal
labels such as `1a` are not converted automatically. Missing scores fail an
enabled score filter. **Positional filters read candidate scores; eQTL filters
read annotation scores; positional maximum CADD also reads annotation scores.**
Differences trigger a warning, matching the source's distinct score sources.

Select state columns explicitly (`none` or `s1:s2`) and specify
`--pos-state-method any|mode|all --pos-state-max N` (or the `eqtl-` counterparts).
States must be integer 1–15 or `NA`. `any` uses the minimum, `all` the maximum.
`mode` implements the source's “majority” state rule: most frequent state,
smallest numeric state on a tie; this is not a majority of passing tissues.
Mode omits missing states; minimum/maximum propagate missingness, and an all-missing
selection does not pass. Explicit columns avoid the source's substring `all`
and positional-column inference.

Binary tracks must be 0,1 or `NA`; select `none` or explicit names and choose
`--pos-binary-method any|majority|all` (or `eqtl-`). Any requires sum>0, majority
strictly more than half, and all the full sum. Missing values do not pass.
Single-track selections work in Java. The original eQTL script fails on that
shape; the original positional script succeeds. The failed original run is
retained in the reference fixture, not treated as a successful reference.

Select `--mhc keep|source-endpoints|full-overlap` and
`--mhc-extension none|START:END`. Exclusion finds a unique chromosome-6 MOG start
and COL11A2 **end after biotype filtering**. The custom interval can expand but
cannot shrink these anchors. Missing/ambiguous anchors fail. The source endpoint
rule removes genes whose start or end lies in the closed interval and retains
genes spanning the entire interval. `full-overlap` deliberately removes those
spanning genes too. This gene rule differs from both candidate-MHC exclusion and
GENE2FUNC's COL11A2-start rule. Review `excluded-genes.tsv` and the chosen assembly.

## Results, failures and recovery

Retain `genes.tsv`, `positional-evidence.tsv`, `eqtl-evidence.tsv`,
`candidate-disposition.tsv`, `excluded-genes.tsv`, `session.json`,
`analysis-status.json`, `manifest.yaml` and **WARNINGS.md**. Input hashes are
checked again before admission. Original inputs are not overwritten.

Gene summaries count unique variants separately from positional evidence rows.
Missing maximum CADD remains `NA`; the source substitutes zero. eQTL direction is
the mode of all retained association rows, with deterministic `+` before `-` ties;
it is not a vote of unique variants. Tissue labels, independent rsIDs and locus
lists are deduplicated. These explicit summary choices prevent legacy repeated
consequences/compound LD strings from being misreported as unique SNP counts.
Gene ordering is chromosome/start, retaining input order for equal coordinates.

Exit0 means completed with required review warnings; an empty result needs
`--empty retain`. Exit2 means failed: scientific partials are quarantined, the
error and required action are prominent, and no completed gene table is admitted.
Correct the named resource/choice/budget and rerun to a new directory. Defaults
bound each input to 268,435,456 bytes/1,000,000 rows and emitted evidence to
1,000,000 rows; explicit limits are available. Shared window mapping enforces its
membership bound while accumulating hits. Coordinate overflow fails explicitly.

**SKIP unavailable dependent analyses.** This native command requires no R or
upstream FUMA installation. If a requested broader stage needs unavailable or
forbidden software/data, skip that stage, name the cause and the precise resource
or permitted installation needed, and do not substitute this subset for it.
Cell Ranger remains excluded by user request; no Cell Ranger installation is needed.

## Validation and portable replay

The candidate passed 278 tests in 25 suites with no skips, including original
MAGMA regression tests for the reused mapper, strict Java17 compilation, Javadoc
and executable packaging. All 278 tests also passed against the exact packaged
production classes on Java21 and25. Thirty-two reference protocols produced
96 packaged workflows across Windows21/25 and Linux21; 160 table groups agreed
across all three. An independent per-association Python oracle checked all 32
protocols. Thirteen extra controls/public/Unicode cases checked explicit P/FDR/
tissue cuts, MHC extension, input failures and a genuine missing-Java skip.

```text
python tools/literature/verify_fuma_mapping.py --jar JAR --java JAVA_EXECUTABLE --out build/fuma-mapping-replay-NEW
```

The checked-in replay needs Python3 and Java, uses synthetic fixtures, creates new
outputs and compares all five tables. A missing Java runtime skips with exit3,
zero completed protocols and an explicit recovery action. The fixture preserves
31 original full R mapping-stage outputs and one original error. It also retains
source versus native summary differences; it does not certify complete FUMA.

Original reference source: [FUMA-webapp](https://github.com/vufuma/FUMA-webapp),
commit `bdfbf06b5dd24037ec3c7497d773fad3c2656150`, unmodified geneMap.R SHA-256
`3977d60ca891d09576d0125b6c3407d82f2a7312987519b0cd3f28c2a0e41e80`.
Private R4.2.3 follows its Dockerfile; actual dependencies were separately pinned
and recorded, not claimed as historical paper versions. The legacy reference
environment is not shipped or needed for native analyses. The data dependency
was recovered from GWDG, an [official Bioconductor mirror](https://bioconductor.org/about/mirrors/),
with its recorded upstream MD5 and computed SHA-256 checked. No sealed analysis
environment or global Windows package was modified. Original-source terms remain
distinct from the independent Java implementation.
