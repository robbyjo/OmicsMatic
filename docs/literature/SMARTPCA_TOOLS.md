# smartpca tools: prepared PCA and population FST

`smartpca-tools` provides native Java PCA and FST profiles derived from EIG
**v7.2.1**, commit `4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1`, whose program
identifier is **16000**. The package edition and program identifier are distinct.
The profiles require a current complete project JAR and prepared EIGENSTRAT text
inputs. Analysis requires no external EIGENSOFT, R or Python installation.

**Build from current source.** Existing release assets can predate these
commands. The build targets Java17 bytecode; validation uses Windows Java21/25
and Linux Java21. See the repository build instructions, then run from the
project root:

```powershell
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --help
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --request examples/smartpca-fst/run.json
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --verify build/smartpca-fst-example
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --request examples/smartpca-fst/replay.json
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --verify build/smartpca-fst-replay-example
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --request examples/smartpca-fst/inbreed.json
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --verify build/smartpca-inbreed-example
```

Every output directory must be NEW, with an existing physical parent directory.
Paths in JSON resolve relative to the JSON file. Preserve old results and change
`out` before repeating a run. VERIFY reads the capture without changing it and
recomputes all scientific results and complete contribution tables. REPLAY
verifies its parent and recalculates into a disjoint new directory, retaining
portable parent evidence. Both require the exact producer JAR recorded by that
capture. Keep earlier producers when updating the software; do not refresh hashes
to make a different JAR appear to be the recorded producer.

## Choose the operation

| Operation | Source profile | Details |
| --- | --- | --- |
| `PCA` | `eig-7.2.1-smartpca-16000-native-pca-v1` | Normalization, ordinary/least-squares projection, iterative outlier removal, permanent marker exclusions and perturbation shrinkage. |
| `FST` | `eig-7.2.1-smartpca-16000-native-fst-v1` | Source `fstonly` estimators, block assignment, weighted block jackknife and optional inbreeding correction/coefficient estimates. |

The [PCA input/option guide](SMARTPCA_WORKFLOWS.md) and
[PCA examples](../../examples/smartpca-native/README.md) remain applicable through
the public `smartpca-tools` command. For example:

```powershell
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --request examples/smartpca-native/run.json
java -jar build/cli/omicsmatic-0.4.0.jar smartpca-tools --verify build/smartpca-native-example
```

Profiles and operations must agree. Unknown options are refused. The FST source
branch bypasses PCA normalization, `minallelecnt` and `maxmissing` filters;
those are not accepted as FST options. LD pruning, genotype harmonization,
X/haploid analysis, autoshrink/fastshrink, historical-edition equivalence and
whole-paper reproduction are outside these declared profiles.

## FST input and request

Provide complete UTF-8 `.geno`, `.ind` and `.snp` text files:

- `.geno`: one marker per row, with exactly one `0`, `1`, `2` or `9` character
  per individual. `9` is missing. Row count and width must match the metadata.
- `.ind`: unique literal individual ID, sex annotation and population, separated
  by whitespace. IDs such as `NA`, `NaN` and `Inf` remain identifiers. IDs and
  population names are limited to39 UTF-8 bytes without whitespace/control
  characters. The source-reserved `Ignore` population remains unsupported by
  this prepared profile and is refused explicitly.
- `.snp`: unique marker ID, numeric chromosome, genetic position, physical
  position, and optionally both allele annotations. Positions must be finite and
  nonnegative; FST physical positions must be literal decimal int32 integers.
  Chromosome codes are numeric0..99; only1..22 enter this human-autosomal
  calculation. Rows must already be ordered by numeric chromosome/physical
  position consistently with `.geno`. No implicit reordering, dosage conversion,
  `chr`-prefix conversion, allele harmonization or identifier repair is performed.

Declare `populations` explicitly as an ordered array of2..100 literal names, or
`null` to use all populations in first-individual appearance order. Every named
population must exist. Individuals outside a declared selection remain captured
and are explicitly unselected. Individuals with no calls at retained autosomal
markers are identified separately. Allele annotations and original position
tokens remain literal in the capture; no build or reference allele is inferred.

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "engine": "native",
  "profile": "eig-7.2.1-smartpca-16000-native-fst-v1",
  "operation": "FST",
  "out": "new-fst-output",
  "inputs": {
    "genotypes": "prepared.geno",
    "markers": "prepared.snp",
    "samples": "prepared.ind",
    "populations": ["POP_A", "POP_B", "POP_C"],
    "genome_build": "GRCh38"
  },
  "options": {
    "inbreed": false,
    "blgsize": 0.05
  }
}
```

`inbreed` defaults to `false`; set it explicitly for the source inbreeding
estimator. It is not inferred from population labels, sex or genotype appearance.
`blgsize` defaults to0.05 Morgans and must be positive and finite.

The source excludes nonautosomal markers and markers with at most one observed
selected individual before block assignment. Monomorphic retained markers still
contribute to global block weights, as do loci unavailable for a particular
population pair. Replacing these with pair-specific weights changes the method.
The source denominator term `1e-10` is preserved exactly; it does not identify a
zero-denominator estimate.

**Check genetic-position units.** The source examines all original marker rows,
including subsequently excluded chromosomes. If their maximum genetic position
is at most `1e-5`, it replaces genetic positions by physical position times
`1e-8`. Otherwise, if any genetic position exceeds100, it divides every genetic
position by100. Other positions are treated as Morgans. The native profile
retains those rules and records the selected convention and effective positions.
Review `markers.tsv`; prepare a consistent explicit map in a NEW request when
the source convention is inappropriate for your intended analysis.

## Results and admission

`native-result.json` stores typed estimates, complete population order, block
counts/digest, original options and admission flags. The streaming tables are:

| File | Contents |
| --- | --- |
| `pairs.tsv` | Every unordered population pair, FST estimate/standard error, source status, field eligibility, ratio diagnostics and usable-locus counts. |
| `inbreeding.tsv` | The source coefficient estimates and standard errors when `inbreed=true`; otherwise a header. |
| `locus-contributions.tsv` | Every evaluated marker/pair numerator, denominator and source ratio, plus inbreeding contributions when enabled. A source dash remains explicit where a ratio is not estimated. |
| `blocks.tsv` | Every block and its global retained-marker-count weight. |
| `markers.tsv` | Every original marker, literal annotations/position tokens, effective Morgans, source inclusion status and block. Excluded-marker observation counts are explicitly uncomputed. |
| `input-samples.tsv` | Every original individual, literal ID/population, selection and retained-autosomal contribution flags. |

The request, all three raw inputs, source/license resources, exact producer,
bindings, owned-worker receipt, warnings, status, seal and completion marker are
captured. SHA-256 establishes integrity, not authenticity. Keep raw inputs and
all failed, partial, undefined and superseded evidence.

Finite negative FST estimates remain unclamped. Zero source standard errors are
retained; this profile adds no P values or Z-score inference. Read the standard
error and source status with the point estimate.

**Undefined source outputs are not valid estimates.** With at most one block,
ordinary source mode returns zero diagnostics without estimating its jackknife;
inbreeding mode aborts. A nonpositive total denominator also leaves the estimate
unidentified even when the source prints zero. These states retain explicit
ineligible flags. Default policy withholds a complete capture when requested
estimate rows are unreportable. For diagnostic retention, an explicit
`"policies": {"allow_unreportable_fields": true}` can retain a completed capture
with unreportable fields marked false. It cannot override the source inbreeding
refusal or make any undefined field valid inference. Inspect data variability,
population coverage and block definition before a scientifically justified NEW
request; none is changed automatically.

## Resource limits and failure remedies

The shared `limits` object accepts `maximum_input_bytes` (default1 GiB per file),
`maximum_total_input_bytes` (2 GiB), `maximum_output_bytes` (512 MiB numerical
outputs), `maximum_samples` (10,000), `maximum_markers` (1,000,000),
`maximum_matrix_cells` (32,000,000), `maximum_line_bytes` (1 MiB),
`maximum_estimated_work` (2,000,000,000), `maximum_retained_bytes` (1 GiB estimate),
and `timeout_seconds` (1,800). Full declared populations and contributions must
fit. Limits refuse work instead of truncating or subsetting it. Storage/work
estimates are algorithm estimates, not operating-system heap quotas. The deadline
covers owned parsing/calculation and contribution production; input capture and
final table verification/sealing have file/byte bounds. Cancellation interrupts
only the owned worker and requires it to stop before admission.

**SKIP3: dependent analysis skipped.** Supply the exact permitted input, completed
capture or complete producer named by the diagnostic. If a required download or
installation is prohibited/unavailable, retain the skip. These native profiles
require no external smartpca runtime.

**FAIL2: no complete result admitted.** Preserve all evidence, fix the named
input, numerical, work/storage, deadline or integrity cause, and retry in a NEW
directory. Existing incomplete/changed captures are FAIL2. Read `WARNINGS.md`,
`analysis-status.json`, completion and every field flag, and require passing
VERIFY before use. Never replace NA/NaN/Inf, repair IDs, clamp source estimates,
silently drop populations or substitute a generic FST/PCA method.

The six source papers report different or unknown package/program editions.
Availability of these selected source profiles does not establish equivalence
to reported v6,7.2.0, literal program13050 or an unreported author environment.
The broader250-paper toolbox and source-review objective remains unfinished.
