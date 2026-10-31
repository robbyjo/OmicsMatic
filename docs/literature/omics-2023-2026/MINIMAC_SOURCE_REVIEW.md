# Minimac paper roles and original-runtime findings

**The project imputation adapter is still missing.** Thirteen source roles and
six supporting pointers have been reviewed. Original software and a native
Java dosage-moment API have been tested separately. These checks do not provide
a `minimac-tools` command, an admitted production runtime, imputation-server
access or complete historical/paper reproduction. The implementation goal
remains active; Cell Ranger remains excluded.

The frozen catalog contains 250 papers, 379 tools and 2,428 observations. This
review brings individually reviewed roles to 671 and supporting pointers to 197.
Paper identities, raw XML hashes, primary locators and all 658 prior review
objects remain preserved.

| Paper | Reported Minimac release or family | Reviewed role |
| --- | --- | --- |
| P019 | Minimac4; patch unstated | MVP core-supplied imputation with TOPMed; SHAPEIT4 4.1.3 is a separate version |
| P072 | Minimac3; patch unstated | 23andMe phasing/imputation with combined 1000 Genomes Phase III/UK10K resources |
| P084 | Minimac4; patch unstated | Local Michigan server 1.5.7 for screening, separate server batch processing; UKBB paragraph instead describes IMPUTE2 while labeling a QC metric “Minimac r2” |
| P097 | Minimac3; patch unstated | Receipt of previously imputed Tarkin VCFs, followed by BCFtools/PLINK QC; no new Minimac run established |
| P105 | Minimac4 1.0.2 | 1000 Genomes panels, separate male/female chromosome-X analysis and R² < 0.3 exclusion |
| P108 | Minimac4; patch unstated | Developmental TOPMed Freeze5 imputation and separate adult array-batch server workflow; strict R² > 0.8 |
| P112 | Minimac4 for MVP; iPSYCH release unstated | Distinct AGR/1000 Genomes panel processing and iPSYCH Eagle/Minimac/HRC workflow |
| P123 | Minimac4; patch unstated | SHAPEIT4 followed by imputation against an in-house Japanese panel of 11,754 individuals |
| P130 | Minimac4; patch unstated | TOPMed server 1.7.3 plus received pre-imputed ROSMAP data; server version is not the executable release |
| P136 | Minimac4 1.0.0 | Separately phased BBJ/NCGG cohorts with historical Minimac4 |
| P192 | Minimac4; patch unstated | Distinct HRC r1.1 and TOPMed r2 server pipelines, with allele/strand handling and R² QC |
| P213 | Minimac4; patch unstated | Previously described OneK1K imputation plus separate X PAR/non-PAR analyses; Y haplogroup assignment uses yhaplo |
| P243 | Minimac4; patch unstated | Three Michigan-server cohort runs with HRC/GRCh37, Eagle2.4, EUR selection and an rsq 0.3 filter |

**Required action:** read each reviewed summary and scope note in `papers.json`
or the catalog. Do not transfer server, cohort, reference-panel or phasing
versions to the Minimac executable. Keep historical Minimac3/4 releases distinct.
P084's IMPUTE2/Minimac-metric attribution and P213's sex-chromosome wording remain
explicit; this review does not silently repair the papers. The boundary of
P243's server filter is unstated, so a strict/inclusive comparison must not be
invented. Reference access, phasing, allele harmonization, build conversion, QC,
batch merging and association are separate operations.

## Original software and compatibility evidence

The following unmodified original binaries were downloaded from the developers'
official releases or University of Michigan archive and retained by hash:

| Runtime | Local outcome |
| --- | --- |
| Minimac4 4.1.6, commit `0c4a8d894338809eb257be2a9f4bf37ced0e2a6c` | Static Linux binary runs natively |
| Minimac4 1.0.2 | Native help probes crashed; the same binary runs through the explicit private QEMU 8.2.2 profile |
| Minimac4 1.0.0 | Initially missing `libgomp.so.1`; runs with an official Ubuntu OpenMP library extracted into a private directory |
| Minimac3 2.0.1 | Original binary runs natively on this Linux host |

No global package installation or kernel changes were made. The QEMU binary was
reused read-only from the already validated private ADMIXTURE runtime. Original
licenses/notices are retained in the private source intake; production admission
must package the required notices and file identities. Legacy fitting commands
explicitly disabled the upstream phone-home option. Help may return 255 and is
not a scientific success criterion.

Five actual imputation workflows completed: Minimac3, both historical Minimac4
versions, and current Minimac4 with directly compressed and Minimac3-converted
references. Three additional stages prepared or converted references. The
independent synthetic fixture has 24 reference samples, six distinct target IDs,
120 variants and 31 observed target variants. All five outputs contain 720
sample/variant cells and match their known phased hard calls, including 534
held-out cells per workflow. These small synthetic checks are not a real-data
accuracy benchmark. Historical 1.0.0/1.0.2 calls, dosages and R² agree for this
fixture; that does not prove general equivalence or absence of known old bugs.

## Findings that the adapter must handle

**Line endings and indexes:** the original 4.1.6 reader rejected the Windows
CRLF fixture as having the wrong number of sample columns. Derived LF files
with identical decoded fields compressed successfully. The original CRLF files
and failed attempt remain retained. HTSJDK-generated BGZF/TBI files were
successfully queried by original BCFtools 1.24, but Minimac/Savvy failed loading
the target through TBI. Adding a derived CSI index allowed the imputation run.
The future adapter must validate and stage its exact input/index combination;
an existing file or index suffix alone is not proof that it is usable.

**Reference preparation changes results:** current directly compressed VCF and
Minimac3-estimated/M3VCF-converted references produce different dosages and R²
at 70 of 120 variants, despite identical phased hard calls here. All 120 output
IDs also differ because the original legacy preparation generated coordinate
IDs. Preserve the preparation route, model/recombination/error parameters,
sample/haplotype identity and a complete variant-key/ID ledger. A new MSAV is
not silently interchangeable with an existing M3VCF or server panel.

**Keep R² definitions and precision separate:** the current tool summarizes
haplotype dosages using float32 sums and squared sums. Independent calculations
and a direct reproduction of that accumulation explain 20 rows whose stable
double-precision R² differs by more than 5e-6; the largest difference is about
1.10e-5. The source values remain retained. Do not silently replace them, widen
an accuracy claim or treat variance-derived R² as empirical correlation with
true genotypes. Review both values near a downstream QC threshold. Genotype
probabilities, dosages, posterior variances, AF and per-copy calls were checked
independently; no known invalid probability was admitted in these fixtures.

## Native Java helper

`org.jlinalg.genetics.HaplotypeDosage` calculates independent-copy genotype
probabilities, expected ALT copies, posterior variance, explicit per-copy calls
and ploidy-weighted haplotype summaries from supplied probabilities. It rejects
missing, nonfinite, out-of-range and unsupported-ploidy inputs. Half probability
is called ALT; the two copy calls can differ from the most probable unphased
genotype. It does not infer phase or run the imputation HMM.

The combined development candidate passes 433 tests in 34 suites and all 433
tests against exact packaged classes on Java 21 and 25. Six packaged native
audits on Windows Java21/25 and Linux Java21 check 4,320 original sample/variant
cells from the two current outputs, with identical native tables across hosts.
The public example ran on all three profiles. Read
[`examples/native-haplotype-dosage`](../../../examples/native-haplotype-dosage/README.md)
and `minimac-source-validation.json` for the exact candidate and evidence hashes.

## Remaining implementation and required recovery

**SKIP project Minimac imputation until its typed adapter is validated.** A
private reference run, native helper or imported dosage does not establish an
available project imputation command. Runtime admission/installer policy,
reference inspection/conversion, explicit phase/ploidy/allele/panel contracts,
output identity/posterior audits, owned-process limits and cleanup, quarantine,
public recipes and packaged end-to-end tests remain required. Mixed-ploidy
mathematics is tested in the helper; mixed-ploidy upstream fitting and the
paper-specific X PAR/non-PAR workflows remain to be validated.

Where software cannot be installed or run in the operating environment, skip the
affected analysis and name the cause, affected method and permitted recovery.
Use a new private environment and supported host only where installation is
allowed. Preserve source data and failed attempts; do not automatically fall
back to another version, engine, ploidy, panel or parameter set. No participant
upload, server registration or server submission is performed by this work.

Official references: [Minimac4 source and usage](https://github.com/statgen/Minimac4),
[4.1.6 release](https://github.com/statgen/Minimac4/releases/tag/v4.1.6),
[Minimac3 archive](https://share.sph.umich.edu/minimac3/),
[CSI format](https://samtools.github.io/hts-specs/CSIv1.pdf).
