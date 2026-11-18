# Native reference LD-score API

`org.jlinalg.genetics.LdscLdScores` computes reference LD scores from ordered
hard calls, with optional binary or continuous annotations. It extends the
native LDSC preparation toolbox for the reviewed chromatin/eQTL and GWAS roles.
The implementation streams normalized variants through a bounded sliding
cache and supplies a bridge to the existing native PLINK BED reader.

Use a current source-build JAR and Java 17+. Analysis needs no Python, R,
bitarray or external LDSC installation. The
[complete SDK example](../../examples/ldsc-scores/README.md) is runnable from
source. The API returns a result or throws; it does not create a workflow
admission record. A captured workflow command remains a separate integration step.

## Declare the reference population

Open a `PlinkBedDataset` with explicit selected variant IDs. Its selected
metadata is in BIM order. Supply coordinates and annotation rows in exactly
that order. Supply unique FAM sample ordinals or pass `null` explicitly to use
every FAM sample. Keep the dataset open and source files unchanged throughout.
The bridge requires resolved BIM allele labels and complements stored A1 counts
to the selected original LDSC second-allele coding.

```java
try (var dataset = PlinkBedDataset.open(prefix, selectedVariantIds)) {
    var result = LdscLdScores.fromPlink(dataset, selectedFamOrdinals,
        orderedCoordinates, orderedAnnotations, true,
        window, requestedChunkSize, minimumMaf, LdscLdScores.Limits.defaults());
    for (String warning : result.warnings()) System.err.println(warning);
}
```

Coordinates must be finite and nondecreasing. Their units, genome assembly,
chromosome/reference scope and map are explicit caller choices. Analyze a
declared chromosome/reference scope; do not join chromosome resets into one
sorted numeric axis. Sample ancestry, relatedness, ploidy and assembly are not
inferred. `null` annotations explicitly requests an all-ones unpartitioned
column. Other matrices contain every input variant, including rows later
excluded by the declared genotype filter. Binary mode accepts only 0/1;
continuous mode preserves finite signed values without thresholding.

For another genotype backend, implement `Genotypes`: provide the declared
sample/variant counts and a variant-major vector of 0/1/2 calls, with NaN only
for missing calls. The method copies each returned vector, so a reader may
reuse its buffer afterward. Underlying data must remain fixed. Retained
vectors are fingerprinted again when read for normalization; whole-file
provenance and immutability checks remain the surrounding workflow's job.

## Preserve the selected source estimator

The numerical target is unchanged CBIIT LDSC 3.0.2 core commit
`6c673952cee74bd5c57aef1555a03b1c015399a0`.

- Filtering retains a variant only when its MAF is **strictly greater** than
  the declared threshold and it is not entirely heterozygous/missing.
  Every input row has a retained/excluded audit record; no filter is inferred.
- Missing calls are imputed to the observed-call mean, then normalized using
  population SD across all declared individuals. This declared source operation
  is separate from repairing malformed/nonfinite input or changing the population.
- Correlations use that full sample count N. The unbiased contribution is
  `r² - (1-r²)/(N-2)`. Self-correlations and negative contributions are retained.
  The API requires at least three samples; source development-only N <= 2
  fallback behavior is outside this profile.
- Nominal distance comparisons include the boundary. Requested chunk size is
  part of the estimator: source chunk expansion can include pairs beyond the
  nominal window. The result retains the exact chunk graph and counts those pairs.
- If rounding the initial clique exceeds the retained variant count, the
  selected original code resets effective chunk size to 1 and uses the whole
  retained population as that initial clique. The API preserves and warns about
  this source fallback. To request the source's exact-distance graph, explicitly
  request chunk size 1; do not infer it from an effective value after fallback.

MAF and **strict MAF > .05** summaries follow stored second-allele floating-point
behavior. For example, `1 - 0.95` is slightly above .05, whereas directly stored
`.05` is not. The reference tests retain this source distinction. Inspect
borderline rows and declare the intended target/profile; no rational-threshold
repair or allele-coding substitution is made silently.

## Read scores, counts and overlap populations

`retainedInputIndices()` maps result rows to the declared input variant order.
`variantAudit()` records every input row's called/missing counts, allele-2
frequency, MAF, normalization information when calculated, filter decision and
genotype fingerprint. Missing diagnostics remain null. Fingerprints establish
integrity relationships, not authenticity or ancestry.

`ldScores()` preserves full precision, rather than rounding to the upstream
CLI's three printed decimals. `annotationCounts()` and
`commonAnnotationCounts()` are separate sums over retained variants and their
strict MAF > .05 subset. `commonVariants()` is that subset's unique row count.
Binary mode also supplies full and common annotation-intersection matrices;
continuous mode leaves those binary overlap outputs absent.

Choose the intended count/reference population explicitly when preparing
heritability and overlap inputs. The sum of annotation counts double-counts
shared variants. Empty/signed annotation columns are preserved; they may require
a different model or cause a later joint fit to refuse. Do not silently remove
them to force inference. This API does not infer GWAS munging, trait Z/N columns,
allele alignment, annotation construction, liability conversion or wrapper behavior.

## Warnings and required actions

**Unavailable/forbidden input or prerequisite:** skip the dependent analysis
and identify the permitted resource the user must supply. The Java method does
not download or install dependencies. A surrounding command should report SKIP3.

**Invalid input, changed genotypes, interruption, rank/model, numerical or
capacity failure:** admit no complete result. Preserve exact inputs, provenance,
declared sample/variant selection and the failed attempt; correct the named
issue and retry as a new attempt. A surrounding command should report FAIL2.
Do not consume a partial array/console log as a completed workflow.

Read every returned warning. Do not clip negative contributions, force scores
positive, round dosages into hard calls, sort/repair IDs or coordinates, drop
failed variants/pairs, or substitute a different chunk graph to obtain success.
Keep original source artifacts and hashes; qualify changed estimators separately.

`Limits` declares whole-input size, output cells, cache cells, pair/work budget
and estimated retained bytes. Defaults permit up to one million input variants,
one million samples, 256 annotations, 100 million output cells, 128 million
cached normalized genotype cells, one billion unique correlations, two trillion
estimated multiply-adds and an 8 GiB retained-memory estimate. These are admission
bounds, not a promise of speed or sufficient JVM heap. Supply explicit suitable
limits/resources for the complete model; the method never truncates it.

The [qualification and warnings](../literature/ldsc-scores-development/README.md)
cover 37 original BED-based models and 75,226 reference fields, the same nine
tests on Windows/Linux, a 4,099-variant/512-sample case and full upstream L2 CLI
comparison. This method extension adds no new LDSC family count or whole-paper
reproduction claim.
