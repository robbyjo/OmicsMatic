# Native transcript import and gene summarization

`transcript-import` implements dense tximport point-estimate arithmetic in Java.
It reads existing Salmon `quant.sf`, kallisto `abundance.tsv`, RSEM
`isoforms.results`, or canonical TSV files. It does not run a quantifier.
Native execution needs Java only; R and the quantifiers are not dependencies.
**AVAILABLE IN THE VALIDATED LOCAL EXECUTABLE.** The full isolated gate passed
1,461 tests with zero failures/errors and five optional skips; all 55 required
suites ran without skips. The actual executable passed 44 transcript-import
workflow calls and all 23 prior executable verifier groups. The unreleased
local artifact is `build/literature-review/validated-cli/jlinalg-0.3.7.jar`,
SHA-256 `5680cbc4e457007f8903d1d4db3e74ecc890a150b350870f0b82d5650bdc0fec`. See [native validation](tximport-native-validation.json)
and [packaged checks](tximport-native-packaged-validation.json).

The original unmodified tximport 1.40.0 produced 204 frozen cases containing
15,615 scalar reference values. They cover transcript and gene outputs,
all four scaling choices, ID policies, RSEM short lengths and unannotated
transcript omission. Independent hand calculations check gene sums, median
isoform scaling and zero-expression length fallbacks. The original package is
used only to generate references in a private validation library.

## Inputs and a complete example

Create an unquoted, tab-separated manifest. Sample IDs remain strings; `1` and
`001` are distinct. Relative file paths resolve beside this manifest.

```text
sample	path
control1	control1/quant.sf
treated1	treated1/quant.sf
```

Create an annotation TSV from the exact quantification transcriptome release.
No annotation lookup, genome-build conversion or gene assignment is inferred.

```text
transcript	gene
ENST00000000001.4	ENSG00000000001.7
ENST00000000002.2	ENSG00000000001.7
```

```shell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar transcript-import \
  --samples samples.tsv --format salmon --level gene \
  --tx2gene tx2gene.tsv --unmapped error --id-policy exact \
  --counts-from-abundance lengthScaledTPM \
  --transcriptome-id GRCh38-GENCODE-release-used-by-quantifier \
  --inferential-replicates drop --out imported-genes --no-config
```

On PowerShell, put the command on one line or use PowerShell continuation
syntax. Use a new output directory on each run. All samples must contain the
same ordered transcript IDs. Every estimate must be finite and nonnegative;
effective lengths must be positive. Estimated counts can be fractional and
are retained without rounding. RSEM effective lengths below one are clamped
to one following the original transcript importer, with an explicit warning.
Plain text and `.gz` text are accepted. HDF5 is not accepted.

`--format tsv` uses exactly named numerical columns `transcript`, `counts`,
`abundance`, `length`. Abundance means TPM; length means effective transcript
length. The format-specific imports select the original columns, not genomic
gene lengths, FPKM or the sequence-length column.

## Scaling and output meaning

| Choice | Counts output | Required downstream action |
| --- | --- | --- |
| `no` | Sum supplied estimated counts within genes, or retain transcript counts | Use an appropriate effective-length offset/normalization workflow for gene DE; `length.tsv` is not itself a log offset |
| `scaledTPM` | Scale the output TPM column to the retained estimated-count library total | Do not also apply a length offset |
| `lengthScaledTPM` | Multiply output TPM by each feature's across-sample mean effective length, then scale to retained estimated library total | Do not also apply a length offset; fit on the intended sample cohort |
| `dtuScaledTPM` | Transcript TPM times the gene's median across-isoform mean length, scaled to transcript estimated library total | Requires transcript output and a complete annotation; intended for transcript-usage inputs |

For gene output, counts and TPM are summed first. Effective gene length is the
TPM-weighted mean isoform length in each sample. If a gene has no expression
in one sample, its length uses the geometric mean from its expressed samples.
If it is unexpressed in every sample, length uses the arithmetic mean across
all its isoforms and samples. Every fallback is recorded.

**Do not double-correct length.** Reconstructed scaled counts must not also
receive the effective-length offset used for unscaled estimated counts. TPM
itself is not a raw-count matrix. No differential test or DESeq2 constructor
runs here. Follow the downstream package's import contract.

The method follows the original
[tximport guide](https://bioconductor.org/packages/3.23/bioc/vignettes/tximport/inst/doc/tximport.html)
and [1.40.0 package](https://bioconductor.org/packages/3.23/bioc/html/tximport.html).
The method paper is
[Soneson, Love and Robinson](https://doi.org/10.12688/f1000research.7563.2).

## Identity, omission and uncertainty choices

`--level gene` requires `--tx2gene` and `--unmapped error|drop`. Choose `error`
to fail on an unmatched transcript. Explicit `drop` records every omitted ID,
and scaling then uses the retained library, matching tximport. It can change
the total library substantially. Mapping rows unused by these quantifications
are retained in a separate annotation audit.

`--level transcript` retains transcript/sample order. It takes a map only for
`dtuScaledTPM`; otherwise a supplied map is rejected as inapplicable. DTU
requires every transcript mapped. Gene rows use Java String order, which is
stable across locales; compare original R gene matrices by exact IDs.

`--id-policy exact` preserves matching IDs. `strip-version` removes the first
dot and everything after it for matching only. `strip-bar` removes the first
bar and everything after it for matching and transcript output IDs. Annotation
keys remain unchanged. Stripped collisions or conflicting transcript-to-gene
assignments fail explicitly. Identical annotation duplicates are deduplicated
with a warning. This is stricter than original tximport's first-row selection
for conflicting annotation entries.

**Point estimates only.** `--inferential-replicates drop` is required. Bootstrap
and Gibbs uncertainty is explicitly excluded; no sidecar uncertainty files are
read. Do not represent this output as uncertainty-aware import.

## Results and recovery

| File | Contents |
| --- | --- |
| `results.tsv` | Fractional estimated counts or the explicitly chosen reconstructed counts |
| `abundance.tsv` | TPM in the exact output feature/sample order |
| `length.tsv` | Effective lengths, including recorded gene fallback decisions |
| `transcript-status.tsv` | Every input transcript, matching ID, gene and retention status |
| `annotation-status.tsv` | Every mapping entry and whether it matched; when annotation is used |
| `length-fallbacks.tsv` | Every gene/sample fallback, method and replacement length |
| `library-totals.tsv` | Input, retained and output estimated-count totals per sample |
| `session.yaml`, `manifest.yaml` | Explicit choices, exact source hashes and output inventory |
| `WARNINGS.md`, `analysis-status.json` | Scientific warnings, terminal status and required actions |

**On failure, inspect `WARNINGS.md` and correct its named input or choice.** Exit
code 2 means analysis failed; no successful `results.tsv` is published. Any
partial files are retained under `partial/`. Rerun to a new directory after
fixing the annotation, sample/transcript order, numerical domain or resource
limit. A zero abundance denominator under scaling fails with the sample name;
choose unscaled import or resolve that sample explicitly. It is never silently
filled or dropped.

The default `--max-cells 10000000` limits transcript-by-sample cells, not the
total memory footprint: counts, TPM, lengths and audited copies occupy several
matrices. `--max-bytes 268435456` bounds each source and its expanded gzip
stream. Hard limits are 100 million cells, 1 million rows per quantification
or map, 1,000 samples and 2,147,483,647 bytes per source. Use an appropriate JVM
heap and declared cohort; increasing limits does not make memory unlimited.

## What the five papers actually used

| Paper | Reviewed tximport use and remaining distinctions |
| --- | --- |
| [P046](https://doi.org/10.1038/s41586-026-10987-6) | RSEM estimates for bulk glial CIBERSORTx composition analysis; source calls them raw counts. Exact input level and scaling are unspecified. RSEM gene-file import and CIBERSORTx remain outside this implementation |
| [P134](https://doi.org/10.1038/s41588-026-02714-8) | kallisto 0.44.0 plus tximport/biomaRt for PLATE-seq drug perturbations; mapping/scaling/version unspecified. Annotation retrieval and downstream z scores/metaVIPER are separate |
| [P151](https://doi.org/10.1038/s41588-026-02681-0) | Salmon transcript-to-gene import for human/mouse DESeq2 analyses; preserve species and contrast designs. DESeq2 filtering/testing is separate |
| [P212](https://doi.org/10.1016/j.ajhg.2025.09.003) | Source calls the output length-scaled TPM and then takes replicate medians for INHBB. Exact argument and prior quantifier are unspecified; do not equate this wording automatically with reconstructed count-scale `lengthScaledTPM` |
| [P227](https://doi.org/10.1016/j.ajhg.2024.06.017) | tximport 1.14.2 transcript/gene matrices after Salmon 1.1.0; older-version parity and AnnotationHub/BiocFileCache workflows remain open |

No historical version, full-paper pipeline, uncertainty-draw, HDF5, sparse,
alevin, piscem/oarfish, StringTie coverage conversion, RSEM gene-file input,
tximeta reference inference, annotation service, quantifier or DE parity is
claimed. The remaining integrations stay visible in the catalog.

## Reproduce the original reference oracle

Native users need no R installation. Developers regenerating references need
an allowed R 4.6 installation and tximport 1.40.0 in a private library. The
official source archive SHA-256 is
`72841a6b2ac1f64ccb05af2d9fba4eea781596771798cefcb3a9327f9dc929f9`.
If that optional validation environment cannot be installed, skip reference
regeneration and retain the frozen references; do not claim a fresh R run.

```shell
python tools/literature/generate_tximport_references.py \
  --rscript PATH_TO_RSCRIPT --library PRIVATE_R_LIBRARY \
  --archive PATH_TO_tximport_1.40.0.tar.gz
python tools/literature/verify_tximport_native.py \
  --jar build/literature-review/validated-cli/jlinalg-0.3.7.jar \
  --report build/literature-review/tximport-runtime/packaged-validation.json
```

Reference source hashes, generator hashes, original R log identity and the
reference digest are in `src/test/resources/tximport/reference-provenance.json`.
R emitted locale startup warnings on this Windows host; the full log is
retained and comparisons use gene identity rather than locale-specific order.
