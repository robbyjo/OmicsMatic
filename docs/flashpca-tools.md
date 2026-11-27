# Original FlashPCA genotype PCA with Java audits

`flashpca-tools` runs the original unchanged-source **v1.2.6** or **v2.0**
executable on the complete supplied PLINK BED/BIM/FAM population. Java checks
every genotype and the complete source PCA outputs. It does not substitute a
generic PCA implementation. Build from current source with `executableJar`;
older released binaries do not contain this command.

**Warnings and required actions:** inspect `WARNINGS.md`, `INPUT-AUDIT.json`,
`OUTPUT-AUDIT.json`, original stdout/stderr and any `FAILURE.json`. Require
`COMPLETED.json` and a successful fresh `--verify` before using results.
**SKIP3** skips dependent work for missing/forbidden prerequisites. Supply or
install the named prerequisite where permitted and retry **new output paths**.
**FAIL2** admits no partial result: fix the named input, source/runtime integrity,
numerical, capacity or cancellation failure and retry new paths. Preserve every
attempt, including failed installations, bindings, models and replays.

Original execution and replay require Linux x86_64 Java. Windows can use Linux
Java inside WSL. The native `FlashpcaPcaAudit` API and its numerical tests also run
on Windows. Commands install nothing.

```sh
java -jar omicsmatic-0.4.1.jar flashpca-tools --profiles
java -jar omicsmatic-0.4.1.jar flashpca-tools --example binding > binding.json
java -jar omicsmatic-0.4.1.jar flashpca-tools --bind binding.json --out new-binding
java -jar omicsmatic-0.4.1.jar flashpca-tools --example pca > pca.json
java -jar omicsmatic-0.4.1.jar flashpca-tools --request pca.json --out new-capture
java -jar omicsmatic-0.4.1.jar flashpca-tools --verify new-capture --scratch new-replay
```

Replace the example paths, population description and preparation with actual
values. Use `--example legacy` for v1.2.6; it has a separate runtime binding.
All schema keys are required. Source paths must be normalized absolute paths.
Complete file identities, effective libraries, helpers and the exact producing
JAR are bound; later changes invalidate the binding. Do not refresh historical
seals. Keep the producing JAR for replay. These consistency checks do not
authenticate a producer or a completely fabricated bundle.

## Runtime and source profiles

The profiles identify both the source tag and the exact tested source-built
binary. The selected source view contains unchanged compiled source/header files,
the Makefile, README and license; its file population must match `--profiles`.
The complete original archives and Git tree identities remain in private evidence.

| Profile | Source tag commit | Execution model |
| --- | --- | --- |
| `FlashPCA-1.2.6-35a6bbee` | `35a6bbee92b3dc025bd3f063d062ee567d88a15a` | Original randomized PCA, full genotype matrix, explicit Eigen/SVD and HIGH/LOW memory choices |
| `FlashPCA-2.0-b45400ea` | `b45400eabb46dc7942970b59ff95c08e3e5404c3` | Original Spectra online partial PCA with explicit SNP blocks |

Both official historical Linux static downloads crashed before displaying help
on the qualified workstation. Those failures are retained. The working runtimes
here were built from unchanged tags using private Eigen3.4, Boost1.83 and
Spectra0.4 dependencies; their build provenance and identities are recorded.
They are installed privately below `/opt/omicsmatic-flashpca-20261127-1`, in
`v1.2.6-source-built`, `v2.0-source-built` and `lib`. No upstream source, binary
or raw trace is published in this repository. See the [license notice](../LICENSES/FlashPCA-NOTICE.md).
Missing or forbidden installation means SKIP3, rather than a substitute solver.
A different build needs its own tested source profile; changing a historical
binary hash is not runtime validation.

## Source behavior and limits

| Behavior | v1.2.6 | v2.0 |
| --- | --- | --- |
| Profile standardization | `BINOM`, SD = sqrt(p(1-p)) | Explicit `BINOM2` (sqrt(2p(1-p))) or `BINOM` |
| Covariance divisor | N-1 | Explicit `P`, `N1` or `NONE` |
| Reported PC scores | Nontransposed U times eigenvalue; transposed U times unscaled singular value | U times sqrt(eigenvalue) |
| Transpose | Original automatic N>P, or explicit force | Original online solver |
| Monomorphic column | Source leaves its imputed constant values in place | Source's lookup stays zero |
| Output | Headerless numeric tables, six significant digits, positional FAM/BIM mapping | Explicit FID/IID and SNP/A1 headers, fifteen selected significant digits |

The original PC score scales are preserved. `RefAllele` in modern files denotes
BIM A1; it does not establish a reference-genome allele. Missing calls use the
original observed SNP mean. All-missing SNPs or nonfinite/inadequate-rank outputs
admit no result. Unique FID/IID pairs and variant IDs, exact BED dimensions and
unused padding bits are checked. No identifier, allele, dimension, missing value
or failed output is silently repaired.

The request declares components, seed, iteration tolerance/count, standardization,
divisor, source-specific options and a separate audit tolerance. Legacy extra
dimensions zero selects the original automatic rule; positive values are passed
explicitly. Legacy examples retain original Eigen/HIGH defaults and orthogonalized
iterations. Modern dimensions that the source would silently reduce are refused
before execution. The modern online source accepts the seed argument but
initializes its solver independently of that argument; seed passage is not proof
that it controls that initialization.

Java computes source SNP moments, full covariance/eigenvector residuals,
orthogonality, loading and PC identities, and PVE from the complete covariance
trace. It uses O(NPk+Nk²) work and O(Nk+P) storage, rather than a dense N×N GRM.
The complete retained genotype/input/output population must fit declared work,
memory, byte, file, time and process capacities. Legacy original execution still
materializes its genotype matrix. No biobank deployment is certified by small
controls. Native residual checks do not refit partial PCA or formally certify
global top-k order; independent complete-SVD controls test the original solver's
top components. Fresh replay reruns the original executable and every native
check, comparing all six complete numeric output files and audit values.

## Four selected paper roles

The [primary audit](literature/flashpca-source-audit/ROLES.json) preserves raw
automatic version fields separately from primary-text and citation evidence.

| Paper | Actual role | Remaining preparation/downstream work |
| --- | --- | --- |
| P023, Nature | Twenty rare-variant PCs, FlashPCA2 name and 2017 citation | Uniform random rare-panel selection (MAC>20, MAF<1%), phenotype/LoF preparation and burden tests; sixteen supplied common PCs are separate |
| P115, Nature Genetics | Twenty PCs for 337,129 white British participants; 2014 method citation | Exact executed edition/patch and supplementary QC are unreported; downstream GWAS and heterogeneity analysis are separate |
| P118, Nature Genetics | Twenty PCs, eigenvalues, loadings and variance explained for 337,126 unrelated white British participants; 2017 citation | Actual 68,619-SNP LD-pruned panel, sample selection, MAF/HWE/missingness and region exclusions require explicit provenance |
| P240, AJHG | Explicit FlashPCA2.0 for ATLAS genetic PCs | Preserve the source MAF<15% wording and HWE>0.001; separate lift-over, QC, 1000 Genomes/KNN ancestry labels and phenotype exclusions |

No MAF/HWE/LD filtering, region exclusion, random panel selection, relatedness
selection, ancestry classification or downstream association model is performed.
Citing a method family does not recover an unreported author patch. Full paper
and biological reproduction remain open.
