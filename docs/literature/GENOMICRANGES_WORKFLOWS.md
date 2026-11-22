**HANDOVER DRAFT: this branch is unqualified. Its guard uses the shared process
ownership correction, but a fresh complete packaged original-R matrix and
admission are still required. No family or review credit. See
[the current handover](HANDOVER_2026-11-22_PROCESS_SAFETY.md).**

# Original GenomicRanges prepared-object workflows

**Implementation checkpoint: qualification is still in progress. No new catalog
family or paper-use credit has been added.** The complete 250-paper objective
remains unfinished. Current canonical totals are 148 scoped families and
1,661 reviewed/1,062 pending uses. Use this guide with the source-built JAR;
it does not describe a published release asset.

`genomicranges-tools` calls original GenomicRanges on explicitly prepared
GRanges objects. It provides `find-overlaps`, `subset-overlaps`, `nearest`,
`distance-to-nearest`, `promoters`, `out-of-bounds`, `reduce`, and `disjoin`.
RUN captures the inputs, request, source references, runtime identity, conditions,
and full original return value. VERIFY checks saved integrity and core export
relationships. REPLAY performs a fresh operation with the captured parent input.

**SKIP3 — dependent analysis skipped.** Supply the named exact permitted Rscript,
private package profile or input. Download/install privately where permitted.
If the environment prohibits installation, keep the skip and identify the
unavailable analysis and prerequisite. A nearby version is not substituted.

**FAIL2 — no complete admitted result.** Preserve this attempt, including raw,
partial, failed, null, superseded and invalidated evidence. Fix the stated input,
capacity, process or integrity cause and retry into a NEW output directory.
Files under `worker/raw-result` or `failed-exports-*` are not admitted exports.

**Exit 0 — completed with warnings.** Read `WARNINGS.md` and the original
conditions before using `admitted/result.rds`. The manifest must also say
`complete: true` and `results_admitted: true`, and VERIFY must succeed.

## Runtime profiles

| Profile | R engine | Original package | Important limits |
| --- | --- | --- | --- |
| `windows-x86_64-r4.1.0-genomicranges1.44.0` | 4.1.0 | 1.44.0 | S4Vectors and GenomeInfoDb retain their recorded build-under-R-4.1.1 warnings. |
| `windows-x86_64-r4.6.1-genomicranges1.58.0` | 4.6.1 | 1.58.0 | Declared profile uses IRanges 2.40.1; P094 reports 2.40.0. This is not its exact environment. |
| `windows-x86_64-r4.6.1-genomicranges1.60.0` | 4.6.1 | 1.60.0 | Paper R engine was not established. This package requires IRanges >=2.41.1; the qualified dependency closure uses 2.42.0. |

The complete dependency versions and official source URLs are in the packaged
`org/jlinalg/omics/genomicranges-workflow/SOURCE.json` descriptor. These are
Windows x86_64 runtime profiles. Java compilation with `--release 17` is not a
Java 17 runtime or Linux-original-R qualification claim.

The package versions reported by the papers are supported as explicit providers;
this does not reconstruct the papers' annotations, reference genomes, parameters
or biological results. P094 jointly lists GenomicRanges 1.58.0/1.60.0 and IRanges
2.40.0 without mapping them to individual steps. Do not invent a compatible
whole-paper environment from that list, or reinterpret its literal
`v2025.09.0 + 387` R label as a verified R engine version.

Runtimes are supplied separately from GitHub source. They are not included in
the JAR. Keep each profile in its own private library. The request's ordered
`r_libraries` are used before the selected R engine's base library. Missing or
wrong editions skip the dependent analysis before an operation call. The worker
also compares 160 direct R function definitions with the exact official-source
references before its runtime fingerprint handshake. This does not authenticate
every S4 dispatch entry, compiled native source or the host.

## Artificial quick start

Choose a fresh working directory. Copy
[`overlaps.request.json`](../../examples/genomicranges-workflow/overlaps.request.json)
there, replace its Rscript/library paths with an installed exact profile, and
create the matching input with
[`make-input.R`](../../examples/genomicranges-workflow/make-input.R).
The input file must be NEW; the script uses artificial coordinates and no human
or other biological data.

```powershell
$omicsmaticRscript = 'C:/path/to/R-4.1.0/bin/x64/Rscript.exe'
$omicsmaticLibrary = 'D:/path/to/private-genomicranges-1.44.0-library'
& $omicsmaticRscript --vanilla D:/path/to/OmicsMatic/examples/genomicranges-workflow/make-input.R $omicsmaticLibrary objects.rds
java -jar D:/path/to/omicsmatic-0.4.0.jar genomicranges-tools RUN --request overlaps.request.json --out overlap-run
java -jar D:/path/to/omicsmatic-0.4.0.jar genomicranges-tools VERIFY --run overlap-run
java -jar D:/path/to/omicsmatic-0.4.0.jar genomicranges-tools REPLAY --run overlap-run --out overlap-replay
```

The original result contains two hits: query 1 with subject 1 and query 2 with
subject 2. To inspect the full return value, load the same package profile and
read `overlap-run/admitted/result.rds` in R. Inputs remain available as the
ordered `query`/`subject` list in `overlap-run/inputs/objects.rds`.

## Input and option contract

Coordinates and sequence metadata are those of original GRanges: one-based
closed endpoints, source-supported zero-width ranges, `+/-/*` strands, literal
sequence labels and explicit Seqinfo. The caller supplies any coordinate
conversion, genome harmonization, annotation or promoter construction needed by
the analysis. There is no chromosome-alias guessing, liftover, automatic matching,
trimming, wrapping or identifier repair.

`input_rds` contains exactly `list(query=GRanges, subject=GRanges)` for
`subject_mode: "provided"`. For `self` or `unused`, it contains exactly
`list(query=GRanges)`. Binary operations require explicit `provided` or `self`;
unary operations require `unused`. Self nearest queries use the original
missing-subject dispatch, including its self-exclusion behavior. GRangesList,
GPos and implicit container coercions are outside this prepared-GRanges scope.

Every option below is required; names retain the original R spelling.

| Operation | Explicit options |
| --- | --- |
| `find-overlaps` | `maxgap`, `minoverlap`, `type`, `select`, `ignore.strand` |
| `subset-overlaps` | `maxgap`, `minoverlap`, `type`, `ignore.strand`, `invert` |
| `nearest`, `distance-to-nearest` | `select`, `ignore.strand` |
| `promoters` | `upstream`, `downstream`, `use.names` |
| `out-of-bounds` | Empty object `{}` |
| `reduce` | `drop.empty.ranges`, `min.gapwidth`, `with.revmap`, `with.inframe.attrib`, `ignore.strand` |
| `disjoin` | `with.revmap`, `ignore.strand` |

Overlap types are `any`, `start`, `end`, `within`, `equal`. Overlap selections are
`all`, `first`, `last`, `arbitrary`; nearest selections are `arbitrary` or `all`.
Other options use explicit JSON booleans or R-range integers. Promoter distances
accept finite numeric scalars/vectors, including empty vectors. They are passed
to original R numeric conversion; original `recycleIntegerArg` controls integer
coercion and any resulting warnings/errors. Raw requested values are retained.
`use.names` controls names explicitly; metadata follows the original method.

All capacity limits in the example are required. Input and result row limits,
candidate-pair bounds, member/workspace bytes, file counts and wall time can
refuse an analysis. They do not promise an operating-system RAM or job quota.
Runtime identity is fingerprinted before the first operation and after exit;
captured input metadata is checked periodically and its full bytes before/after.
This is not continuous monitoring or host/binary attestation.

## Results and source behavior

`admitted/result.rds` is the complete original return value. Its class, names,
metadata, seqinfo, or intentional metadata loss follow the original method;
the wrapper does not add the input's metadata to Hits or restore metadata dropped
by reduce/disjoin. The original input objects remain captured separately.
`rows.tsv` is a core-field view. Text fields use UTF-8 hexadecimal transport;
NULL names, missing names and literal empty names remain distinct. Original
integer selections and logical-missing selections also remain distinct in RDS
and the outcome's `result_kind`.

* Nearest tie behavior follows the exact upstream edition. In the tested point
  tie at 100 versus 90/110, arbitrary selection returns subject 1, while `all`
  returns subject 2 alone. It does not combine the opposite-side ties.
* An empty subject can produce logical missing selections. They are preserved,
  rather than converted into integer missing values.
* Adjacent ranges have distance zero. Distance-to-nearest drops unmatched queries;
  nearest selection vectors retain missing entries.
* Out-of-bounds returns one-based row indices from the original internal helper.
  Known noncircular bounds apply even when circularity is unknown; unknown
  lengths and circular TRUE are treated as in source. It does not filter rows.
  If applying the indices yourself, handle an empty index vector explicitly.
* A source error remains a failure. Raw files after a failed call are retained
  without admission. Negative or overflowing promoter arguments are not repaired.

Offline VERIFY checks the producer/source bytes, seal, controls, status, runtime
records and core export relationships. It does not parse RDS, rerun the original
operation, authenticate a resealed capture, or independently certify a biological
result. Use the exact producing JAR for saved captures and preserve old producers.
REPLAY must use a NEW physical directory separate from its parent. Its parent
must pass verification before and after the fresh operation.

## Java-only geometry component

`org.jlinalg.genetics.GenomicRangeGeometry` provides scalar promoter geometry and
one-based out-of-bounds indices without an R dependency. It supports nonmissing
R-range integers and explicitly represented sequence bounds. It does not supply
S4 objects, metadata, overlaps, nearest queries or the complete package. Its
17 original-1.64.0 fixture cases cover 4,096 artificial ranges; that reference
edition is distinct from the three upstream workflow editions above. It adds
no separate family credit.

## Paper-specific limits

P013 names promoters and internal boundary-index filtering; its GenomicRanges
edition is unstated. Sequence extraction, motif scanning and the paper's literal
`seqalong()` spelling are not resolved by this adapter. P032 names 1.44.0 nearest
TSS assignment, with R 4.1.0 but unspecified tie/strand/TSS preparation choices.
P051 names 1.58.0 common-peak creation/quantification with Signac without naming
the GenomicRanges function. Do not attribute reduce/disjoin to that paper.
P137/P158 name distance-to-nearest without an edition; P158's promoter definition
does not establish a call to promoters. P242 names interval mapping without an
edition or exact function. A broader scan also found P039's unattributed
subsetByOverlaps mention; the provider is not inferred from a generic name.
The 250-paper semantic-completeness and missed-tool review remains open.
