# Original PLINK autosomal hard-call QC

**Development feature. Use the verified QC development candidate; released
0.3.7 and the earlier association candidate do not contain this workflow.**

**Read `WARNINGS.md` and the decision tables before using filtered genotypes.**
An unavailable, changed, unsupported or forbidden original runtime skips the
analysis with exit **3**. Where downloads and installation are permitted, run:

```powershell
python tools/literature/install_plink.py --home build/omics-plink-official --allow-install --allow-download
```

The admitted original profile is Windows x86_64. If installation or execution
is not permitted, skip this analysis. A changed or unmanaged runtime directory
is preserved; choose a new private home. Analysis commands never install
software. An existing verified installation can be reused without network
access or either permission flag.

## Synthetic example

The fixture has 128 synthetic samples and 96 variants. It includes repeated
IIDs across families, distinct `A_B/C` and `A/B_C` pairs, a Unicode sample,
recorded parents, an initially excluded sample, unresolved and all-missing
markers, and a chrX marker. There is no patient data. Coordinates are arbitrary.

```powershell
$qcJar = 'build/literature-review/plink-qc-candidate-4/jlinalg-0.3.7.jar'
java -jar $qcJar plink-tools --options-file examples/plink-tools/qc/plink1-options.json
java -jar $qcJar plink-tools --options-file examples/plink-tools/qc/plink2-options.json
```

Run from the repository root and choose a **new output directory** for every
attempt. The example's thresholds, control labels, order, all-sample reference
populations and PLINK2 k=0 are explicit choices for a small synthetic test.
**They are not recommendations for a research cohort or reconstructed choices
from a paper.** PLINK2 k and `keep-fewhet` must be declared when HWE is enabled.
Consult the [original PLINK2 HWE documentation](https://www.cog-genomics.org/plink/2.0/filter#hwe)
before choosing them for real data.

Options recipes are UTF-8 JSON objects with string values. Paths are relative
to the calling directory. For Windows Unicode paths, put the paths inside a
UTF-8 recipe whose filename is ASCII. Direct options override recipe values.

## Explicit input and population contracts

Supply `--task qc`, `--engine plink1|plink2`, a BED/BIM/FAM prefix and a QC
sample table. The table's exact tab-separated header is:

```text
fid	iid	include	group
```

Name every FAM pair exactly once. `include` is `yes` or `no`; `group` is
`control`, `case` or `unassigned`. Joining uses both identity fields. FAM
phenotypes are ignored for QC, staged as missing, and restored unchanged in
the final FAM. Recorded parents and sex are also restored.

Declare every scientific policy:

| Option | Meaning |
|---|---|
| `--chromosome-scope autosomes` | Initially exclude every chromosome outside human 1–22. Canonical 0–26/X/Y/XY/MT input codes and contiguous chromosome blocks are required. |
| `--unresolved-alleles error\|exclude\|retain-with-warning` | Decide how missing allele labels (`0`/`.`) are handled before missingness calculations. Retaining raw calls does not establish allele identity. |
| `--qc-order samples-then-variants\|variants-then-samples` | Choose when sample missingness is applied. The variant sequence is missingness, MAF/MAC, then optional HWE. |
| `--maximum-sample-missing`, `--maximum-variant-missing` | Explicit missing-call fractions in [0,1]. |
| `--minimum-maf`, `--minimum-mac` | Explicit minor allele frequency and copy-count bounds. Zero disables the corresponding constraint. `--maf` is omitted for zero because PLINK1 rejects it; `--mac 0` is passed explicitly. |
| `--frequency-population founders\|all` | Select the frequency reference from currently retained samples. Recorded founders have both FAM parent IDs equal to `0`. |
| `--hwe-mode off\|exact\|midp` | Disable HWE explicitly or choose the original exact/mid-p test. |
| `--hwe-population all\|controls`, `--hwe-founders founders\|all` | Independently select the HWE reference when enabled. Controls come only from the QC sample table. |
| `--hwe-p`, `--hwe-unreportable error\|exclude\|retain-with-warning` | Set an HWE threshold strictly between 0 and 1 and the zero-call/missing-P/unresolved-allele policy. |
| PLINK2 `--hwe-k`, `--hwe-keep-fewhet yes\|no` | Explicitly choose original sample-size scaling and whether to retain heterozygote deficiencies. PLINK1 does not accept these options. |
| `--final-check fail\|warn` | Choose whether successfully evaluated final-cohort threshold violations prohibit export or remain prominently flagged. |

Frequency and HWE reference subsets screen **variants**. Selecting controls or
founders for a reference does not by itself remove cases or nonfounders from
the final cohort. The original `--nonfounders` option includes all members of
each explicitly preselected reference subset. PLINK1's implicit binary-trait
HWE selection is overridden. Original PLINK1 and PLINK2 defaults differ;
see their [filtering documentation](https://www.cog-genomics.org/plink/1.9/filter#hwe).

Original filter decisions are authoritative. Rounded report fractions and
P-values are diagnostic and never used to reconstruct the retained masks.
The workflow supports biallelic diploid hard calls. Sex-chromosome/ploidy QC,
ancestry, relatedness, Mendelian errors, heterozygosity outliers, imputation
quality and genome-assembly validation require separate reviewed analyses.

## Final checks, outputs and action

A single ordered pass can retain samples or variants that fail thresholds
after the other dimension changes. Final checks reevaluate missingness,
MAF/MAC and enabled HWE on the final cohort without silently applying another
pass. `--final-check fail` prohibits publication if these checks find failures.
`warn` preserves their explicit flags. Any original-process nonzero exit,
including an original check that removes every sample or variant, fails the
workflow regardless of this policy. Empty reference populations also fail.

Both original programs may print HWE **P=1 with zero called genotypes**. Such
rows are marked `NO_CALLS`; they are not evidence of equilibrium. Monomorphic
and conditional-singleton reference sets are flagged `REPORTED_DEGENERATE`.
A printed HWE zero is unresolved numerical precision, with no invented floor.
`hwe_raw_p` preserves the original text; interpret it together with
`hwe_reportability` and the reference call count. The explicit invocation mode
also selects the original report schema: PLINK1 uses `P` for both tests, whereas
PLINK2 uses `P` for exact tests and `MIDP` for mid-p tests.

A retained missing allele may be spelled `0` or `.` by the original engine;
both remain unresolved. Resolved allele labels must match exactly, allowing
only the corresponding allele swap and genotype recoding.

| Output | Use |
|---|---|
| `genotypes.bed/bim/fam` | Original identities, coordinates, genetic distance, parents, sex and phenotype; upstream allele orientation retained. Every retained packed genotype is checked against the source, allowing only the matching allele swap. |
| `qc-samples-retained.tsv` | Retained QC identities and group labels. This is a QC table; prepare a separate reviewed association phenotype/covariate table. |
| `sample-decisions.tsv`, `variant-decisions.tsv` | Every source row's initial/filter decision, integer missingness denominators, available frequency/HWE diagnostics and final threshold flags. |
| `final-threshold-violations.tsv` | Final-cohort criteria requiring review or another explicitly justified pass. |
| `session.yaml`, `manifest.yaml`, `upstream/` | Effective policy, original versions/hashes/commands/exits, input hashes, reference-population stage sizes and unchanged raw reports. |

On exit **0**, review warnings, exclusions and final flags before downstream
use. On exit **2**, read the named input/scientific/process error and retained
logs, correct the cause and rerun to a new directory. Failed filtered files
are quarantined under `partial/`; use no quarantined output as a result.
On exit **3**, install/admit the runtime where permitted, or skip the analysis.
Do not increase a resource bound or relax a threshold without reviewing its
cause. The defaults bound samples, variants, total cell inspection, retained
metadata, line lengths, source bytes and report bytes; `--help` lists them.

This subset has a separate validation record. It does not establish coverage
of every PLINK operation, the complete QC performed by a paper, or the released
JAR. The paper catalog remains unchanged until the complete promotion gates
and scientific scope review have passed.
