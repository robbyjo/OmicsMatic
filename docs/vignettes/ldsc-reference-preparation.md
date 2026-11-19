# Native reference LD-score preparation

This command computes reference LD scores from a declared diploid hard-call
PLINK BED/BIM/FAM population and an explicit coordinate/annotation table. It
captures the complete inputs, filtering decisions, chunk graph, annotation
counts and optional binary overlaps. It uses the selected CBIIT 3.0.2 original
core's estimator and chunk behavior. It does not establish ancestry,
unrelatedness, assembly, map validity or biological suitability for a GWAS.

**Use a complete source-build JAR and Java 17+.** The earlier published 0.4.0
release predates this command. Native analysis needs no Python, R, bitarray or
external installation. Reference development uses a separate pinned upstream
runtime. Read the [qualification and warnings](../literature/ldsc-reference-workflow/README.md).

## Quick start with complete synthetic inputs

From the repository root:

```powershell
.\gradlew.bat :executableJar
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.LdscReferenceCli --request examples/ldsc-reference/unpartitioned-snps/request.json
```

The example creates `examples/ldsc-reference/unpartitioned-snps/result`.
**The output directory must not exist.** For another attempt, copy the complete
example folder to a new working directory or change the request's `out` to a
new name. Preserve earlier successful, failed and skipped attempts.

The [example collection](../../examples/ldsc-reference/README.md) also contains
binary and continuous annotation inputs. These are synthetic controls, not a
human reference panel. For real analysis, supply the intended panel, sample
selection, map, annotations and provenance; validate their compatibility with
the later GWAS/model separately.

## Request and literal tables

All 20 JSON keys are required; unknown fields and unsupported methods do not
silently select alternatives. Paths resolve relative to the request file.
The complete examples are copyable request templates.

| Field | Meaning |
|---|---|
| `schema_version`, `analysis` | `1` and `"ld-scores"`. |
| `bed_prefix` | Prefix of all three exact `.bed`, `.bim`, `.fam` files. |
| `reference_table` | Complete declared contributing variants, in selected BIM order. |
| `sample_table` | Exact FID/IID selection in caller order, or `null` for all FAM rows. |
| `print_variants`, `print_policy` | `null`/`"all-retained"`, or a print table with `"source-membership"` or `"require-retained"`. |
| `annotation_mode`, `annotations` | `"unpartitioned"` with `["base"]`, or `"binary"`/`"continuous"` with ordered labels. |
| `coordinate_units`, `window` | `"cm"`, `"bp"` or `"retained-snp-index"`, with an explicit finite nonnegative window. |
| `chunk_size`, `minimum_maf` | Positive source chunk size and strict MAF threshold in `[0,0.5]`. |
| `allow_whole_reference` | Explicit Boolean permitting an actual whole-reference pair graph after review. |
| `limits`, `out` | `"standard"` or complete declared limit maps, and a new output directory. |
| Four provenance fields | `data_provenance`, `sample_provenance`, `coordinate_provenance`, `annotation_provenance`; literal nonblank declarations. |

An unpartitioned reference table has the exact TSV header:

```text
variant	coordinate
rs1	0.1
rs2	0.2
```

Binary/continuous mode appends the declared annotation labels, in the exact
order, and a value for every variant/annotation. Binary values must be 0 or 1;
continuous columns retain signed values and zero columns. No rows are joined,
sorted, deduplicated, dropped or repaired. The table may explicitly select a
BIM subset, but must follow that subset's BIM order. All selected variants
must have one identical declared chromosome label.

Optional sample TSV: exact `FID` and `IID` header, with unique exact pairs.
At least three selected individuals are required. Optional print TSV: exact
one-column `variant` header with unique literal IDs. Tables are strict UTF-8;
an initial header BOM is recognized and the reference BOM is recorded.
Blank, incomplete and malformed rows are not silently removed.

For `bp`, coordinates must be exact nonnegative integers matching the selected
BIM positions, representable without precision loss. For `cm`, the supplied
nondecreasing finite map is authoritative; interpolation is not inferred.
For `retained-snp-index`, put the literal `retained-index` in **every** input
coordinate field; the command derives `0,1,...` after genotype filtering and
requires an integer window. It does not use raw BIM row numbers as those
coordinates. Native/core zero-width windows are supported; the unchanged
upstream root CLI uses option truthiness and rejects a zero-only window choice.

## Filtering, graph and count populations

Genotypes are hard calls only, not rounded dosages. The native PLINK reader's
A1 counts are complemented to the original core's stored A2 orientation.
Missing calls use the observed-call mean, followed by population-SD
normalization across all selected individuals. All-missing, constant and
all-heterozygous rows are audited and excluded by the explicit source filter.
MAF must be strictly greater than the requested threshold; the common-reference
population separately requires strict MAF `>0.05`. Source IEEE boundary
behavior is retained: A2 frequency `0.95` gives `1-0.95 > 0.05`, whereas an
exact stored `0.05` does not. Do not “repair” this by rounding frequencies.

Unbiased squared correlations use `r²-(1-r²)/(N-2)`, include self correlations,
and retain negative terms/scores. The nominal left edge includes the distance
boundary. Original chunk expansion and initial-clique fallback can accumulate
pairs outside the nominal coordinate window. A small window with a large
chunk can span the whole retained population. The command previews the actual
graph and requires `allow_whole_reference: true` for that case before requested
pair accumulation. This guard is stronger than the upstream root CLI's
nominal-left-edge-only check; the mathematical graph is unchanged.

The complete retained reference contributes to every score, annotation count
and binary overlap. A print table affects **printed score rows only**:

- `source-membership` prints the retained intersection in reference order,
  retaining warnings and a decision for each unavailable requested ID.
- `require-retained` refuses if any requested ID is unavailable.
- An empty retained print intersection always refuses completion.

`.M` contains annotation sums over the complete retained population;
`.M_5_50` contains sums over its strict common subset. Continuous sums are
weights, not binary SNP counts. Binary mode supplies full/common intersection
matrices and unique population sizes. Do not round continuous values into
binary enrichment inputs. Counts, overlaps and a later stratified model must
refer to the same declared reference universe.

## Outputs, admission and limits

`scores.tsv` holds literal IDs, ordered annotation columns and full-precision
scores. `reference.l2.ldscore.gz` uses standard CHR/SNP/BP and L2 column naming
and TSV quoting, with full precision. The original root CLI prints three
decimal places; comparisons account explicitly for that printing difference.
The bundle includes M files, count/overlap tables, every raw-row filter audit,
selected samples, original BIM ordinals, nominal lefts, chunk graph and preview,
captured source bytes, scalar model diagnostics, runtime/code pins, warnings
and an indexed manifest.

**Require exit 0, a checked `COMPLETED.json`, a valid manifest and
`analysis.json` with `result_admitted: true`.** Preflight is a component only.
After a late failure, completed-looking model files may remain unadmitted;
`unadmitted-analysis.json` preserves any earlier admission proposal.

**SKIP3:** dependent analysis was skipped. Supply the named permitted input,
complete Java backend or supported declared scope, or leave it skipped.
**FAIL2:** no complete result. Retain source bytes, partials and failure
evidence; read `FAILURE.txt`/`WARNINGS.md`, correct the stated cause and retry
with new output. Missing/forbidden prerequisites must be visible to users;
download/install authorization does not replace scientific input declarations.

Inspect copyable default limit maps with:

```powershell
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.LdscReferenceCli --default-limits
```

There are separate SDK numeric, BED metadata and I/O limits. Explicit maps must
contain every named key. Source BED bytes are captured and hashed by streaming;
they are not loaded as one array. SDK cache/work/pair and retained-byte limits
reject the complete problem without dropping pairs or annotations. The memory
estimate includes count-vector and binary-overlap working/result arrays; it is
an engine admission estimate, not a guarantee of total process heap/RSS.
Provide sufficient permitted JVM heap and storage. A manifest/completion pair
adds small administrative output beyond the bounded computed/admin payload
inventory. See [warnings and required actions](../literature/ldsc-reference-workflow/WARNINGS.md).

## Original-source limits

The selected CBIIT package identifies version 3.0.2; its root `ldsc.py` banner
literally reports 3.0.1. Both identities are retained. In the pinned reference
runtime, unchanged root-CLI `--extract` and `--keep` fail in `parse.py`'s
`np.nonzero(pd.Series)` join before estimation. Those two failures are retained,
with no original full-CLI subset result admitted. Native subset mathematics and
transport are checked separately against the original-core model fixtures.
No source function was patched or monkeypatched to turn the failures into
success. Broader LDSC methods, other editions, annotation construction,
munging, liability conversion and author-specific wrapper pipelines remain
separate work.
