# Beagle: original phasing and imputation with Java validation

Validated development candidate:
`build/literature-review/beagle-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`5ed54e44acc9d50bb5af240418a5497b9c235506ecc45f318bad9168bd867cac`.
It passed 176 focused tests without failures/skips and 42 packaged workflows/24
failure controls on Java 21 and 25. The older canonical a104 full-gate build is
unchanged and lacks this command. No new full-project gate or release is claimed.
The commands below select the exact checked candidate. A fresh source build uses
`gradlew.bat executableJar --no-daemon --no-parallel` and writes `build/cli/`.

**Review warnings before using results.** The command retains the original GT,
dosage and probability fields, then checks them in Java. A successful run is marked
`passed-with-review-warnings`. It does not certify biological accuracy, panel
suitability, posterior calibration or an entire published workflow.

## Setup and first run

Use Java 17 or newer for OmicsMatic. The four admitted original Beagle JARs require
Java 8 or newer; actual development checks use Java 21 and 25. The original GPL
software is installed privately from the official HTTPS archives. No upstream
executable, real reference panel or patient data is redistributed.

Where the operating environment permits installation and downloads:

```powershell
$beagleJava = (Get-Command java -CommandType Application).Source
python tools/literature/install_beagle.py --java $beagleJava --allow-install --allow-download
java -jar build/literature-review/beagle-candidate-1/jlinalg-0.3.7.jar beagle-tools --options-file examples/beagle-tools/recipes/5.4-29Oct24-impute.json --home build/omics-beagle-official
```

Download and install permissions are independent. Verified offline reuse needs
neither permission flag. Choose separate `--home`, `--cache` and `--report`
directories. Changed, linked or unmanaged homes and cached files are preserved;
use a new private path instead of resealing changed binaries. Hashes are locally
recorded pins from official downloads, not publisher signatures.

Analysis never installs software. Supply `--home` or `JLINALG_BEAGLE_HOME`.
`--java` selects the upstream JVM; it defaults to the JVM running OmicsMatic. If
installation or downloads are forbidden, skip the dependent analysis until a
permitted, matching runtime is supplied. The installer and analysis report the
reason and exact required action in `WARNINGS.md`.

The [twenty synthetic recipes](../../../examples/beagle-tools/README.md) are
small contract tests. Their map, seed, minimum observed markers, iterations,
state counts, Ne and error rate are explicit test settings, not recommended
population parameters. All recipe values are JSON strings; direct flags override
them. Rerun with a new `--out` directory.

## Scientific inputs and version choices

| Choice | Required contract |
|---|---|
| `--method phase` | Phase supplied target markers and complete sporadically missing GT. A reference panel is optional. With a panel, output contains shared markers only. |
| `--method impute` | A reference panel is required. Output contains the reference marker family; missing typed calls and reference-only markers are completed. |
| `--profile` | Exactly `5.4-29Oct24`, `5.1-18May20`, `4.1-08Jun17` or `4.1-21Jan17`. No automatic substitution. These names identify pinned archived builds. |
| `--input` / `--reference` | Plain or gzip VCF 4.1–4.3, one chromosome, positive strictly increasing unique positions, resolved biallelic diploid GT. Reference calls must be complete and phased. Target calls may be fully missing; partial missing, haploid and polyploid GT require a separate workflow. |
| Alleles and QC | Exact chromosome/POS/REF/ALT agreement at shared loci. Non-PASS input filters require prior explicit QC. No allele flipping, complementing, coordinate repair, imputation-quality filter or ploidy conversion is inferred. Unused VCF INFO/FORMAT fields are not model inputs. |
| `--map` / `--assembly` | Explicit four-column PLINK cM map and declared assembly label. The map must bracket every target/reference position, with increasing bp and nondecreasing cM over a positive genetic span. The label is recorded, not independently validated against a genome reference. |
| `--phase-policy` | Explicit `preserve-provided` or `allow-rephase`. Modern 5.1/5.4 can rephase partially missing/unphased input. A preserve request with mixed retained phase/missingness is rejected; complete prephased controls and the tested 4.1 cases preserve provided phase. Every observed unordered GT is checked separately. |
| `--target-only-markers` | Reference workflows require `error` or `drop-with-warning`. Original Beagle excludes target-only markers with a panel; exclusions are explicit in the ledger. No-reference runs reject this reference-only option. |
| `--sample-overlap` | Reference workflows require `error` or `allow-with-warning` for overlapping sample names. Distinct names alone do not establish distinct people. |
| `--minimum-observed-markers` | Explicit positive per-target minimum, checked after target-only exclusions. A completely unobserved target cannot pass. |

The runner maps original chromosome, sample and marker identifiers to deterministic
ASCII staging names, then restores the exact identifiers in canonical outputs.
The sample and variant ledgers preserve the mapping and every exclusion. Target
genotypes stream to a bounded disk store and are checked row by row; the full
cohort-by-marker matrix is not retained in Java memory. Only GT is staged for
inference. GL/PL inference, IBD detection, pedigree models, automatic panel
retrieval and historical cohort reconstruction are outside this command.

All profiles require explicit `--seed`, `--iterations`, `--ne`, `--err`,
`--window-unit`, `--window-size` and `--overlap-size`. Ne must be finite and
positive; error probability is in `[0,1)`. Windows exceed overlap.

| Profile family | Version-specific options |
|---|---|
| 4.1 | Window/overlap are marker integers (`--window-unit markers`). `--iterations` maps to `niterations`; IBD is explicitly disabled. Modern burn-in/state/EM flags are rejected. Imputation requests original GP output. |
| 5.1 | Window/overlap use cM (`--window-unit cm`). Explicit `--burnin` and `--phase-states`; imputation also requires `--imp-states`. The 5.4 EM flag is rejected. |
| 5.4 | The 5.1 choices plus explicit `--em true|false`. Modern windows must be at least 1.1 times the overlap. |

Phasing-only output has GT. The tested 4.1 imputation output has GT/DS/GP;
modern imputation has GT/DS/AP1/AP2/GP. These are retained as distinct statistics.
**GT must not be reconstructed from the largest rounded GP.** Valid original
outputs included eight strictly nonmaximal-GP GTs and 28 first-maximum tie
differences across the 24 original reference workflows. Native checks use exact
integer intervals for the original hundredth precision, including values printed
as `0` or `1`; the independent verifier uses Decimal arithmetic. Neither
renormalizes or substitutes probabilities.

## Outputs, limits and recovery

| File/status | Interpretation and required action |
|---|---|
| `analysis-status.json` | Read first. Exit 0 means validated with review warnings; exit 3 means dependency unavailable/changed and analysis skipped; exit 2 means input, process or validation failure. Preflight option/output-path failures are printed to stderr before an output directory is created. |
| `WARNINGS.md` | Prominent warnings and exact recovery. Review assembly, map, panel provenance, phase changes, exclusions and the intended dosage/call statistic before downstream analysis. |
| `genotypes.vcf` | Canonical uncompressed VCF, restoring exact original identifiers. Original GT/DS/AP/GP strings are retained. No imputation-quality threshold is applied. `FILTER=PASS` from Beagle is not a new QC certification. |
| `variant-results.tsv` | Per-marker called/completed counts, phase-order changes, GP diagnostics and upstream DR2 as raw text. The paper's INFO filter is not automatically a DR2 rule. |
| `sample-ledger.tsv`, `variant-ledger.tsv` | Complete target/reference mapping, retained observed-marker counts, expected output family and explicit exclusions. |
| `session.yaml`, `manifest.yaml` | Requested options, exact runtime/JVM/inputs/outputs, commands, process IDs/exit codes, SHA-256 provenance and validation counts. Original logs and reports remain in `upstream/`. |
| `partial/` | A failure quarantines any canonical VCF/result table already being written. Do not use partial results. Correct the named issue and rerun into a new directory. |

Default independent ceilings are 100,000 samples per dataset, 2,000,000 markers,
100,000,000 combined input/output genotype cells, 512 MiB combined decompressed
input, 512 MiB estimated retained Java input storage, 1 MiB per line and 4,096 MiB
per raw/decompressed report. The output-cell ceiling is checked separately against
the same declared cell bound. Override with `--maximum-samples`,
`--maximum-variants`, `--maximum-cells`, `--maximum-input-mib`,
`--maximum-retained-mib`, `--maximum-line-bytes` and `--maximum-report-mib` after
reviewing the named limit. Retained storage must allow at least 16 line buffers.
These bounds and conservative canonical-size checks do not constitute a total
process RAM/disk guarantee.

Defaults for the upstream process are one thread, a 2,048 MiB heap, 5 MiB thread
stacks and a 3,600 second timeout. Set `--threads`, `--memory-mib`, `--stack-mib`
and `--timeout-seconds` explicitly when needed. Java application/native/OS overhead
is additional to the Beagle heap. A timeout terminates the owned process and
retains diagnostics; unrelated processes are not stopped.

See the [source review](../BEAGLE_SOURCE_REVIEW.md) for the nine paper roles,
exact build distinctions and official Beagle sources. Synthetic agreement checks
the implemented contracts; reference access, model justification and biological
validation remain responsibilities of the analysis using the command.
