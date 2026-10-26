# Beagle source review and validated development command

**The typed `beagle-tools` development command is validated.** The local candidate
is `build/literature-review/beagle-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`5ed54e44acc9d50bb5af240418a5497b9c235506ecc45f318bad9168bd867cac`.
It passed 176 focused tests with no failures or skips, plus 42 packaged workflows
and 24 failure controls on Java 21 and 25. The older canonical a104 executable
is unchanged and lacks this command; no new full-project gate or release is claimed.
The controlled catalog refresh adds these nine source-checked roles, reaching
510 reviewed roles and 88 supporting paragraph pointers across the same 250 papers.
See the [workflow guide](omics-2023-2026/BEAGLE_WORKFLOWS.md) for commands and recovery.

## What the nine papers actually used

The review used the catalog's hash-verified full texts and paragraph pointers.
Versions, reference panels and operations remain distinct:

| Paper | Beagle use | Integration boundary |
|---|---|---|
| [P024](https://doi.org/10.1038/s41586-025-09573-z) | Beagle 5.4 estimated MAPT-region haplotypes; the source reports one burn-in and 23 phasing iterations. | Reported convergence counts are not a complete command. PCA, clustering and later logistic regression are separate. |
| [P101](https://doi.org/10.1038/s41588-023-01571-z) | Beagle 4.1 imputed missing SNP calls before millet SNP GWAS. | This does not establish imputation of the separate PAV dataset or use of a reference panel. |
| [P102](https://doi.org/10.1038/s41588-024-01957-7) | Beagle 5.1 imputed and phased variants within two Brassica napus subpopulations. | Input ploidy, reference panel and detailed options are unstated; GEMMA association is separate. |
| [P149](https://doi.org/10.1038/s41588-023-01538-0) | Beagle 4.1 imputed FinnGen Release 6 array data with SISu v3. | Population-specific reference imputation requires the correct panel; an exact Beagle build is unstated. |
| [P171](https://doi.org/10.1038/s41588-026-02678-9) | Beagle 5.4 imputed filtered wheat VCFs using the reported defaults. | The organism name does not establish a tetraploid inference model or the VCF genotype encoding. |
| [P179](https://doi.org/10.1016/j.ajhg.2023.11.008) | Beagle 5.4 phased two family datasets with the 1000 Genomes panel before hap-ibd. | Reference-assisted phasing, reference-only marker imputation and IBD segment detection are distinct. |
| [P186](https://doi.org/10.1016/j.ajhg.2024.10.018) | Beagle 4.1 build 08Jun17.d8b imputed Eagle-prephased data with SISu 4.2. | Preserve the exact build and panel. The paper's INFO filter is not automatically a Beagle DR2 filter. |
| [P209](https://doi.org/10.1016/j.ajhg.2025.09.004) | The authors phased two UKBB subgroups with Beagle 5.4; other datasets reused earlier Beagle 5.2 phasing. | The catalog's primary pointer needs to move to the actual subgroup-phasing paragraph; the two reused-data paragraphs remain supporting evidence. |
| [P248](https://doi.org/10.1038/s41588-026-02682-z) | Build 21Jan17.6cc imputed missing cucumber SNP calls before GEMMA GWAS. | The official archived JAR identifies itself as Beagle 4.1. The source assigns a separate missingness exclusion to SVs. |

No missing genetic map, reference panel, genome assembly, ploidy or filtering
choice is filled in from a software default or organism name. A native diploid
test does not reproduce an entire plant or clinical workflow.

## Private runtime installation

The profile pins four official JARs: 5.4 build 29Oct24.c8e, 5.1 build 18May20.d20,
and the two explicitly reported 4.1 builds 08Jun17.d8b and 21Jan17.6cc. Original
JARs and the GPL text are installed privately; none is redistributed in this repo.
The modern source papers report version families; the admitted 5.4/5.1 archive
builds do not establish the exact historical build used by each paper. An
unspecified 4.1 build is likewise not inferred from the two explicit builds.
The author identifies bundled samtools portions as MIT-licensed in the original
source. See the official [5.4](https://faculty.washington.edu/browning/beagle/b5_4.html),
[5.1](https://faculty.washington.edu/browning/beagle/b5_1.html), and
[4.1](https://faculty.washington.edu/browning/beagle/b4_1.html) pages.

The hashes are locally recorded pins from the official HTTPS downloads. They
are not publisher-provided signatures or checksums. Java 8 or newer is required
by these JARs; actual setup probes completed on Java 21 and 25.

Where installation and downloads are permitted, run from the repository root:

```powershell
$beagleJava = (Get-Command java -CommandType Application).Source
python tools/literature/install_beagle.py --java $beagleJava --allow-install --allow-download
```

The two permission flags are independent. A verified home can be reused with
neither flag. Use `--home`, `--cache` and `--report` to choose separate directories.
Changed, linked or unmanaged homes/cached artifacts are preserved. No global
installation or persistent environment change is made.

**Read the reported `WARNINGS.md` and `dependency-status.json`.** Exit 3 means
setup was skipped. Where permitted, supply the matching cached official artifacts,
a working Java executable and a new private home. If installation or downloads
are forbidden, skip the dependent analysis until those requirements are supplied.
Do not treat a usage/version probe as scientific validation.

## Original reference checks

Synthetic references are in
`src/test/resources/org/jlinalg/genetics/beagle-reference/`. They contain 24 target
people, 32 reference people and structured diploid haplotypes. The scenarios
separate phasing without a panel, reference-assisted phasing without adding
markers, reference imputation and complete/incomplete prephased input.

Twenty-four actual original-program workflows independently checked 144,096
genotype cells and 576,000 dosage/allele/genotype-probability fields. Eight complete
prephased runs produced identical VCF data rows across Java 21/25. These are
synthetic contract checks, not biological accuracy or posterior-calibration claims.

Two findings must survive project integration:

- **Phase preservation depends on version and missingness.** The tested 5.4 and
  5.1 builds changed relative phase in some partially missing prephased targets.
  The complete prephased controls preserved known phase, as did the tested 4.1
  builds with partially missing targets. The explicit `preserve-provided` policy
  rejects incompatible retained input; `allow-rephase` permits and reports changes.
- **Keep original GT, DS, AP and GP separate.** The tested outputs round DS/AP/GP
  to hundredths, including values printed as `0` or `1`. Validation uses that
  rounding interval. Eight records had a reported GT whose unphased genotype
  probability was strictly below the largest printed GP; another 28 differed
  from a naive first-maximum rule because of ties. Reconstructing GT from rounded
  GP would alter valid original output.

With a reference panel, all tested builds excluded the deliberately introduced
target-only marker. The adapter requires an explicit error/drop-with-warning policy;
exclusions are retained in the ledger. GP/AP fields
were absent in the phasing-only outputs and present in the imputation outputs
under their version-specific flags. The project adapter preserves those
differences, source identifiers, reference provenance and marker partitions. The
42 packaged workflows separately checked 244,992 genotype cells and 950,400
posterior fields. Their independent Decimal checks agree with the native Java
integer-interval checks. Actual timeout termination, changed-runtime preservation,
opaque identifiers and Unicode data paths passed.
