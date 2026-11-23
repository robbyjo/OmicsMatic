# Original SCENIC+ cisTarget, DEM and cistrome preparation

`org.jlinalg.cli.ScenicPlusMotifsCli` calls unchanged SCENIC+ v1.0a2 helpers
`_run_cistarget_single_region_set` and `_run_dem_single_region_set`, followed by
original `get_and_merge_cistromes`. These are the scientific helpers used by the
original command layer. This adapter runs them with explicit local resources in
one fresh worker. It does not claim to reproduce the complete original command
dispatcher or historical paper editions.

The ranking-based cisTarget method and score-based DEM method accept prepared
region sets. The merged direct/extended Boolean cistromes, motif annotation and
TF list feed the separately tested network adapter. Prepared multiome data supply
the assayed region and gene universes; this stage does not fit a topic model,
normalize expression or infer cell annotations.

## Warnings and required actions

- **Keep licensed runtime, captures, fitted objects and source-bearing traces
  private.** The user accepted unmodified internal academic use of SCENIC+ v1.0a2.
  Copying/transfer restrictions remain applicable. Do not export the runtime or
  transfer it to a new destination/recipient without permission.
- **ClusterBuster scanning remains SKIP3.** An explicit usage license for its
  exact runtime has not been established from the
  [official repository](https://github.com/weng-lab/cluster-buster); linked official
  download/about pages timed out. Supply permission covering an exact runtime or
  retain the skipped scanning analysis. This profile uses explicit prepared
  motif databases. The artificial validation databases are declared artificial
  scores/rankings and are not represented as genome-scan results.
- **Original DEM background-cap bug:** with promoter balancing off, a requested
  `max_bg_regions` is ignored by the pinned original source. The actual retained
  foreground/background sizes appear in `results.json`. A requested cap smaller
  than the actual background produces a console warning and a
  `source-warnings.json` entry. No silent subsampling or vendor repair occurs.
  Review the actual background; use promoter balancing with explicit annotation
  only if scientifically appropriate, or retain the limitation/skip the analysis.
- **Empty or undefined results:** read the per-analysis outcome, private worker
  warnings and numeric field ledger. No motif hits or HDF5 enrichment result are
  invented when the original method returns no enriched motifs. The original
  merger raises `TypeError` if either direct or extended cistrome categories are
  entirely empty, including an all-empty run; this stays FAIL2 with the original
  traceback and a clear remedy. Retain enrichment evidence and skip the dependent
  network, or choose justified inputs/settings in a NEW run. A mix of empty and
  positive analyses can complete when both categories remain nonempty.
  NaN/Inf are recorded with typed values and finite-field flags,
  not converted to zero or declared reportable.
- **SKIP3 / exit 3:** restore the exact named permitted runtime/input, or retain
  the skipped dependent analysis. **FAIL2 / exit 2:** no complete result; preserve
  raw, partial and failed evidence, read `analysis-status.json`, `FAILURE.md` and
  private `worker/report.json`, resolve the cause and retry a NEW directory.
- The original annotation reader applies its normal Pandas parsing. Inputs whose
  literal IDs or descriptions change under that parser (for example `NA` as a TF
  name) are refused. No identifier repair, default-reference download or genome
  assembly choice is made.

## Run with explicit inputs

Start from [the complete JSON example](../../examples/scenicplus-motifs/request.json).
Paths resolve relative to the request; the output must be new.

```bash
java -cp /absolute/path/omicsmatic-0.4.0.jar \
  org.jlinalg.cli.ScenicPlusMotifsCli --request request.json
```

The profile is `scenicplus-1.0a2-motifs-v1`, operation `MOTIFS`, schema 1. Required
inputs are `prepared` MuData (`scRNA`, `scATAC`), `region_sets` TSV and
`motif_annotations` original motif2TF table. A cisTarget analysis requires
`rankings`; a DEM analysis requires `scores`. Both databases use original
Feather-v2 region-by-motif column layout, with a `motifs` index column.
Ranking values must be zero-based integers within the database region range;
scores must be finite floats. The adapter checks types/ranges and does not
authenticate how a database was generated or prove full ranking permutations.
Optional `genome_annotation` is required for promoter-balanced DEM and includes
literal `Chromosome` and integer `Start` (TSS) columns. No remote annotation is
queried. HDF5 soft/external/virtual references are refused.

The region-set table has exact columns `set`, `Chromosome`, `Start`, `End`, with
literal set/chromosome names and canonical zero-based half-open int64 intervals.
Repeated membership rows are refused; a region may occur in different sets.
Original DEM resolves overlap between foreground and background by its own rule.

`options` requires `species`, `annotation_version` and a list `analyses`. Species
is `homo_sapiens`, `mus_musculus` or `drosophila_melanogaster`. An annotation
edition label does not authenticate the supplied table; its actual bytes are
captured. Defaults are fraction overlap 0.4, motif-similarity FDR 0.001 and
orthologous identity threshold 0. Annotation groups are chosen from
`Direct_annot`, `Orthology_annot`, `Motif_similarity_annot` and
`Motif_similarity_and_Orthology_annot`. `annotations_to_use` defaults to direct
and orthology. Merging defaults to direct annotation for direct cistromes and
orthology annotation for extended cistromes. All normalized options are recorded.

Each analysis has a unique name matching `[A-Za-z0-9_-]{1,128}`:

- `method: CISTARGET`: exactly one named `foreground` set and optional `ctx`
  settings `auc_threshold` (0.005), `nes_threshold` (3), `rank_threshold` (0.05).
- `method: DEM`: explicit lists `foreground` and `background`; `dem` requires an
  explicit uint32 `seed` and Boolean `balance_number_of_promoters`. Optional
  settings are `max_bg_regions` (null), `promoter_space` (1000), `adjpval_thr`
  (0.05), `log2fc_thr` (1), `mean_fg_thr` (0), `motif_hit_thr` (null). A null motif
  threshold invokes the original ROC selection; the original returned table
  does not expose the selected per-motif threshold, so none is fabricated.

The pinned DEM source applies `p_adjust_bh`; descriptive documentation from a
different edition is not substituted for the executed implementation. Explicit
background sampling/promoter choices and original source failures are retained.

Optional `export_network_inputs` defaults to false. When explicitly true, the
adapter also exports `network-expression.tsv` and `network-accessibility.tsv`
with the network protocol's `cell` header, original ordered cell/feature names,
and exact numeric promotion to float64. It performs no normalization, sorting,
intersection or inferred pairing. Both modalities must have identical ordered
cells. Supported source values are finite Boolean/integer/IEEE float16/32/64;
integers must be within the guaranteed exact float64 range ±2^53. Unsupported,
nonfinite or potentially lossy conversions fail explicitly. Matrix dimensions
are checked before dense export. The full matrices join the typed scientific
comparison, and `network-export-bridge.json` records source and export dtypes.
This bridge resolves the original preparation TSVs' unnamed index header without
altering those retained originals or shortening float32 values through a lossy
decimal intermediate.

## Outputs and fresh verification

`worker/out/<analysis>/` contains complete enrichment/mapping TSVs. Nonempty
analyses also retain original motif-hit and cistrome maps and the original HDF5
result. `direct-cistromes.h5ad`, `extended-cistromes.h5ad`, corresponding complete
TSVs/metadata and `tfs.txt` are original merged outputs. TF selection preserves
the original direct/extended union intersected with RNA gene names.
`scientific.jsonl` records complete typed in-memory values; `field-ledger.tsv`
marks finite numerical fields. `results.json` records actual per-analysis counts
and empty outcomes, while `SUMMARY.json` reports totals and remaining exclusions.

```json
{
  "schema_version": 1,
  "mode": "VERIFY",
  "analysis": "/absolute/path/motif-analysis",
  "out": "/absolute/path/NEW-motif-verification"
}
```

Invoke the same Java class with this request. REPLAY uses the same fields and
also performs fresh original computation. The read-only parent and new witness
must be disjoint. Fresh comparison covers complete typed exports, all numerical
tables, maps, invocation, warnings/counts and runtime closure. It also reads the
actual serialized HDF5 arrays and attributes with h5py, using complete streaming
typed-array digests. Saved PyTables tables/attributes are not unpickled. These
logical HDF5 views include scientific arrays and metadata rather than relying
on whole-container byte hashes. Only the capture-specific absolute motif
annotation path is normalized to the independently pinned annotation-input
identity; raw paths remain in the inventoried original containers.

`--inspect CAPTURE` checks saved byte closure and exact producer only, without
fresh science. Hashes and updated manifests do not authenticate execution.
Captures are private development evidence; successful calls establish neither
biological validity nor full SCENIC+ family/catalog admission.

## Runtime and capacity

Use the unchanged permitted Linux x86_64 Python 3.11.8 runtime recorded in the
[runtime handover](HANDOVER_SCENICPLUS_2026-11-23.md). Original 41 SCENIC+ Python
members, 73 members in four pinned Git dependencies, 228 installed distributions
and 231 loaded metadata-origin bindings are checked. Recorded interpreter,
stdlib and distribution locations are fingerprinted before and after execution.
The original source and installed dependencies are not modified.

Default limits are input/member 2 GiB, output 8 GiB, 50,000 files, 1,800 seconds,
one million cells/genes, ten million regions/pairs and 100 million matrix values.
`maximum_genes` also bounds motif rows; `maximum_pairs` bounds region-set
memberships. Database dimensions and serialized-container logical values are
bounded before materialization. Explicit ceilings are input 64 GiB, member
32 GiB, output 256 GiB, one million files, 86,400 seconds, ten million cells/genes,
100 million regions, one billion pairs and ten billion matrix values. Large real
databases may require explicitly raised limits and sufficient memory. These are
capture/processing checks, not a hard RAM quota. Runtime fingerprinting is
bounded at 16 GiB / 100,000 files excluding bytecode caches. Observed-process
start identities and deadlines do not establish OS containment or loader closure.
