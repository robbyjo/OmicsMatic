# Native genetic PCA and projection

**Development API; the released 0.3.7 JAR does not contain this class.**
`org.jlinalg.genetics.GeneticPrincipalComponents` fits an exact genetic
relationship matrix (GRM), computes its eigenvectors using the existing numerical
backend, and projects explicitly oriented diploid dosages. The default backend
is portable Java; this API requires no PLINK installation or network access.

Frequency-reference samples and fitted-basis samples are separate explicit
index arrays. Choose the panel, QC, LD pruning and unrelated basis beforehand.
Recorded FAM founders alone do not establish unrelatedness. The API accepts
fractional dosages in [0,2] and NaN missing values; the saved PLINK comparisons
cover synthetic biallelic hard calls.

## Runnable synthetic example

Use the PCA development JAR identified in the retained validation report. From
the repository root, compile the example with Java 17 or newer:

```powershell
$pcaJar = 'build/literature-review/genetic-pca-candidate-1/jlinalg-0.3.7.jar'
javac --release 17 -Xlint:all -Werror -cp $pcaJar -d build/genetic-pca-example examples/genetic-pca/GeneticPcaExample.java
java -cp "build/genetic-pca-example;$pcaJar" GeneticPcaExample src/test/resources/org/jlinalg/genetics/genetic-pca founders mean build/genetic-pca-example-founders-mean
```

The input is synthetic: 125 fitted samples, 87 declared markers and either all
125 people or 123 recorded founders in the frequency reference. The two selected
nonfounders remain fitted samples in the founder-frequency example. The reader
is deliberately restricted to this small fixture; use the library API for real
data with your own bounded input reader. Matrix rows match `markers.tsv` and
columns match exact FID/IID pairs in `samples.tsv`, in order. `all`/`founders`
and `mean`/`pairwise` are explicit example arguments. Always use a new output path.

Read **STATUS.txt and WARNINGS.md before using output**. Input/model errors stop
with a named exception before output is created. If output publication fails,
the example retains diagnostic files and marks them `FAILED_DO_NOT_USE`; fix the
named cause and rerun in a new directory. `coordinates.tsv` labels fitted and projected rows
separately. `weights.tsv` retains counted/other alleles, frequencies and reference
call counts. `relationship.tsv` and `spectrum.tsv` expose the actual GRM and its
full descending spectrum, including negative eigenvalues.

## Transform and interpretation

For marker dosage `g` and its supplied counted-allele frequency `p`,
`Z=(g-2p)/sqrt(2p(1-p))`. A missing centered value contributes zero.

| Fit setting | GRM denominator | Projection of fitted samples |
|---|---|---|
| `MEAN_IMPUTE` | Total submitted marker count M | Matches the fitted eigenvectors to numerical precision |
| `PAIRWISE_COMPLETE` | Jointly observed markers for each sample pair, including the diagonal | A separate linear projection; generally differs from the fitted eigenvectors |

Pairwise GRMs can have meaningful negative eigenvalues. The implementation uses
direct symmetric eigenanalysis and retains those values; it does not convert
the matrix to a positive-semidefinite matrix or apply SVD to an indefinite GRM.
Only positive components above the declared rank tolerance are reportable.
The requested boundary cannot split a numerically tied eigenspace. Signs are
set by the largest absolute basis coordinate; signs and rotations within fully
retained tied spaces remain mathematically arbitrary.

Native projection coefficients are `W=Z*v/(M*lambda)`. Projected coordinates
sum standardized target dosages times W. They are **not original PLINK allele
weight files**: PLINK1 uses a different scale, and PLINK2 uses two rows per marker
with an additional orientation convention. Do not feed these native coefficients
to a PLINK recipe or import original weights into this API without a reviewed
conversion. This API does not import original weights.

`project()` requires the fitted marker identity, order, counted allele and other
allele to match exactly. It reuses fitted frequencies without estimating target
frequencies, dropping variants or guessing allele flips. Entirely missing targets
return NaN coordinates with `NO_OBSERVED_DOSAGES`; partial missingness remains
visible as `PARTIALLY_IMPUTED`. Review the observed marker count before interpreting
a target; no minimum coverage threshold is inferred.

## Bounds and errors

Default limits admit at most 100,000 markers, 100,000 input samples, 1,000 basis
samples, 25 million input cells, 500 million marker-by-basis-pair products,
one billion cubic eigen-work units and 256 MiB estimated API working storage.
These are independent ceilings, so their simultaneous maxima are not admissible.
Caller input arrays, result accessor copies and backend-specific native workspace
need additional memory. Projection has separate target-batch admission checks.
Supply explicit `Limits` when a reviewed workload requires different ceilings.
There is no silent approximate PCA fallback.

Invalid dosages, duplicate identities, empty/repeated reference selections,
unresolved alleles, frequencies outside (0,1), no-call reference markers, no-call
basis markers/samples, pairs with no joint calls, insufficient positive rank,
split tied eigenspaces and work/storage excesses raise an exception identifying
the cause. Correct the panel, reference, requested components or explicit limits
before rerunning. No invalid marker is silently excluded.

For BED inputs, `PlinkBedDataset.readAllele1Dosages(variant)` counts BIM A1;
construct each `Marker` with `variant.allele1()` as its counted allele and
`variant.allele2()` as its other allele. Preserve exact sample and variant order.
The generic `PrincipalComponentConfounders` API uses different default centering
and scaling; those defaults are not this genetic-frequency transform.

## Evidence and scope

Six pinned original PLINK cases cover two explicit frequency populations and
pairwise/mean missingness, with independently decoded calls. Tests compare
93,750 GRM entries, 30 eigenvalues and 750 sample/eigenvector rows using original
print precision and sign-aware comparisons. Additional controls cover projection,
orientation, missing calls, exact identities, rank/ties, bounds and defensive copies.
Provenance and hashes accompany the test fixture. This is a native mathematical
subset, not whole-PLINK equivalence, an ancestry classifier or a reproduction of
every paper's panel and selection policies.

The reviewed upstream descriptions are the official
[PLINK PCA documentation](https://www.cog-genomics.org/plink/2.0/strat) and
[relationship-matrix documentation](https://www.cog-genomics.org/plink/2.0/distance#make_rel).
The stored fixed-binary reports and independently decoded genotypes provide the
runtime reference evidence.
