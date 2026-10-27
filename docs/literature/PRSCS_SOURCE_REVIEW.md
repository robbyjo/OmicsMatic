# PRS-CS source review and implementation boundary

**The typed `prs-cs-tools` development command is validated.** The local candidate
is `build/literature-review/prscs-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`33b26e76b989ce3387d3965f4b625a6e4a02e3e2b55d8bccd90bb1e48981a764`.
It passed 218 focused tests with zero failures/errors/skips,plus 12 packaged
workflows and 28 failure controls on Java 21/25. Thirty native and eight live
command tests passed on each JVM. The canonical a104 executable is unchanged and
lacks this command. A new full-project release gate is not claimed.
The controlled catalog refresh adds 11 reviewed roles, for 521 reviews and 92
supporting pointers across the same 250 papers. See the
[workflow guide](omics-2023-2026/PRSCS_WORKFLOWS.md) for commands and recovery.

**Historical runtimes need special care.** The explicitly selected 1.0.0 and
1.1.0 profiles use Python 3.10.11, whose support ended on 2026-10-01. Use them
for offline historical reproduction. Choose the separately pinned current source
and compatible Python 3.12 environment for new work. See the
[official Python release notice](https://www.python.org/downloads/release/python-31011/)
and [original PRS-CS repository](https://github.com/getian107/PRScs).

## What the papers used

All 12 observations were checked against the catalog's immutable, hashed full
texts. Eleven additional role reviews and four supporting pointers are published;
the existing P205 review is preserved. This source check is separate from software
validation and paper-wide completeness review.

| Paper | PRS-CS operation | Choices and separate downstream operations |
|---|---|---|
| [P024](https://doi.org/10.1038/s41586-025-09573-z) | Alzheimer disease weights with PRS-CS-auto and a developers' 1000 Genomes panel; a=1, b=0.5, 1000 iterations, 500 burn-in, thinning 5. | Panel population and exact source build are unstated. PLINK2 scoring and APOE sensitivity analyses follow inference. |
| [P074](https://doi.org/10.1038/s41588-025-02417-6) | Ankylosing-spondylitis weights using population-matched 1000 Genomes panels; European LD for the multi-ancestry GWAS. | Phi grid 1e-6, 1e-4, 1e-2, 1; independent MGBB tuning. The reported 2021-Jun-4 date does not identify an unprovided checksum. LDpred2 has its own hyperparameter grid. |
| [P076](https://doi.org/10.1038/s41588-023-01583-9) | PRS-CS-mult benchmark: separate population-specific PRS-CS fits, then learned score combinations. | Version 1.0.0 is reported. Training/evaluation partitions and combination-weight estimation are distinct from each constituent fit and from joint PRS-CSx inference. |
| [P090](https://doi.org/10.1038/s41588-024-01952-y) | Dilated-cardiomyopathy weights with PRS-CS-auto, European 1000 Genomes phase3 LD and reported version 1.0. | UKB-excluded GWAS/MTAG, variant QC, PLINK1.9 scoring and clinical evaluation have separate methods. |
| [P105](https://doi.org/10.1038/s41588-023-01569-7) | PRS-CS-auto with East Asian 1000 Genomes HapMap3 LD; TMM-50K training and BioBank Japan application. | The paper reports 2021-Jun-4 and Python 3.8.8. The admitted historical tag/runtime is an explicit reproducibility profile; it does not reconstruct that environment. |
| [P114](https://doi.org/10.1038/s41588-026-02732-6) | East Asian schizophrenia weights with East Asian 1000 Genomes phase3 LD and phi=0.01. | MHC/ambiguous-marker exclusions, PLINK2 chromosome scoring, summation and standardization are separate choices. |
| [P120](https://doi.org/10.1038/s41588-025-02410-z) | Hypothyroidism weights with a UK Biobank LD reference. | The cited paragraph leaves version, phi and panel population unstated. UKB/GESUS regression and pROC evaluation follow scoring. |
| [P126](https://doi.org/10.1038/s41588-024-01947-9) | Type2-diabetes common-variant weights with PRS-CS-auto, UKB-excluded European GWAS and European UK Biobank LD. | Rare-gene carrier definitions and later stratification have separate methods. |
| [P128](https://doi.org/10.1038/s41588-026-02722-8) | Separate population-specific Alzheimer disease fits with ancestry-matched 1000 Genomes HapMap3 LD. | PRSice2 scoring without clumping, ADGC combination weights and ADSP normalization/evaluation follow inference. The exact source build and phi are unstated. |
| [P141](https://doi.org/10.1038/s41588-026-02705-9) | Alternative common-variant PRS component for comparison with SBayesRC and combination with rare-variant scores. | Rare-effect estimation and composite-score evaluation are separate; panel and sampling details remain unspecified here. |
| [P150](https://doi.org/10.1038/s41588-024-01933-1) | Metabolic-syndrome components and latent factors with UKB-excluded GWAS, European 1000 Genomes phase3 LD, phi=0.01 and effective N for binary traits. | Reported a=1, b=0.5, 1000/500/5 sampling settings precede PLINK2 scoring. Genomic SEM and clinical evaluation are separate. |
| [P205](https://doi.org/10.1016/j.ajhg.2026.05.006) | Simulated-GWAS comparator weights. | The real-GWAS comparator uses LDpred2. Hyperparameter tuning and linear score combination are distinct steps. The existing reviewed record is retained. |

The curator validates XML identity, primary paragraph hashes, named-tool matches
and supporting paragraph pointers before it can update `reviewed-uses.json`:

```powershell
python tools/literature/curate_prscs_uses.py --out build/prscs-role-check
```

Add `--apply` only when publishing the validated role update and regenerating the
controlled catalog. The command archives the previous review file and preserves
existing reviews.

## Admitted sources and native methods

| Explicit profile | Source commit | Input/output support |
|---|---|---|
| `source-fbc9ec0` | `fbc9ec017652f0b40a6ab47f63747d97fa7d033b` | BETA/OR with SE or P; posterior means, retained draws and psi. Separate Python 3.12, NumPy 1.26.4, SciPy 1.11.4 and h5py 3.11.0. |
| `1.1.0` | `60219e3a5284212eca1b57a8e783e5c5253630c5` | BETA/OR with SE or P; means and psi. Historical Python 3.10.11, NumPy 1.23.5, SciPy 1.8.1 and h5py 3.8.0. |
| `1.0.0` | `36827146c32e1b45972a290b965e621d990142c3` | BETA/OR with P; means. Same declared historical dependency family. |

Original scientific source is downloaded privately from these commits and kept
unchanged. The tag 1.0.0 commit is June5 UTC/June4 US; a paper's date label alone
does not supply its exact historical checksum. PRS-CS has an MIT license.

`PrsCsMath` provides native allele orientation, marginal-effect transformations,
reference-frequency inversion, effect-unit conversion and retained-iteration
checks. Its tests include 1728 ordered DNA allele triples, 592 marginal values
returned by the three original parsers, and 1040 original posterior-unit
relationships. Palindromic SNPs require an explicit policy: original summary and
reference branch orders differ, so naive sign reversal is insufficient.

The original 1.0.0/1.1.0 boolean flags are case-sensitive. Their normalization
denominator is the unrounded sampling quotient; current source floors it and can
overrun draw storage for an inconsistent schedule. Actual upstream edge controls
confirmed these behaviors. The adapter supplies the correct boolean case and
requires an integral quotient equal to the actual retained count.

## Input, numerical and warning contracts

The command requires an explicit autosomal chromosome family, target BIM,
summary format, uniform sample size, sample-size interpretation, trait kind,
panel kind, assembly/population labels and model/sampling choices. BETA/OR plus
SE uses SE(beta) or SE(logOR). P formats use effect direction; extreme P values
need an explicit rejection or original-floor policy. The original panel's
positions and effect frequencies remain traceable through input and variant
ledgers.

Tables are streamed, including compressed summary/BIM inputs. The supplied
reference SNP-info/HDF5 files remain in place and are hashed before and after
processing. The HDF5 bridge rejects external/virtual storage, unsupported filters,
duplicate IDs and malformed dimensions. Java checks exact SNP order, finite raw
LD, correlation range, diagonal and symmetry. Original allele-sign multiplication
and SVD adjustment remain upstream operations; these checks do not certify a
general positive-semidefinite matrix.

Every model row is compared with the unmodified original parser before fitting.
Native posterior checks require complete identities, alleles, positions, widths,
finite effects and psi in (0,1]. Original seven-significant-digit strings are
retained. `--posterior both` runs the original mean and draw modes separately
with the same seed and checks their rounding intervals and psi agreement.
The original source restarts the supplied seed on each chromosome.

**Warnings remain visible.** Known current NumPy array-to-scalar deprecations
and the ten exact reviewed historical SciPy alias deprecations are counted and
retained in original stderr, session metadata and `WARNINGS.md`. Unknown or
numerical warnings fail validation. The historical EOL notice is prominent on
each selected historical analysis. Failed canonical files are quarantined under
`partial/`; the published bundle retains diagnostics and required recovery steps.

**Unavailable or forbidden software skips the analysis with exit 3.** Installation
requires explicit setup flags and the host's permission. The analysis command
does not install or download dependencies or panels. A changed or unmanaged home
is preserved; recovery uses a new private path. Scientific/input/process failures
return exit 2 and explain the correction in `WARNINGS.md`.

Whole-runtime source, interpreter, package and external Python-base inventories
are checked around child execution. Cache-bypass flags prevent stale bytecode
from replacing pinned source. Small synthetic runs can spend substantial time
on these integrity checks. Resource ceilings count full requested LD matrices,
including unused markers, and provide conservative work/storage guards; total
process RAM, including native-library and variable-length HDF5 overhead, is not
guaranteed by those ceilings.

The 56 unmodified synthetic reference runs produced 208 posterior/shrinkage
files, all reproduced byte-for-byte from the revised sealed installation.
Independent checks covered original model families, effect units and mean/draw
relationships. Sampling settings 40/20/5 belong to those tests. Chain stability,
independent prediction evaluation, training-target overlap and study-specific
reference compatibility require separate scientific assessment.
