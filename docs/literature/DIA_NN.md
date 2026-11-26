# DIA-NN captured workflow

**Scoped validation passed for original DIA-NN 1.8, 1.8.1 and 2.0.2 local library-free workflows.** Build the current source for this command. This checkpoint adds one bounded upstream integration; it does not reproduce a whole package, author pipeline or source paper.

`diann-tools` runs a separately installed original DIA-NN runtime for library-free protein quantification. Java captures the complete input population, builds explicit commands, monitors the owned processes, retains their original outputs and checks supported input/output consistency. Prediction and raw-spectrum fitting use the original engine.

The supported runtime profiles identify exact Linux x86_64 releases 1.8, 1.8.1 and 2.0.2. Each profile pins the complete original runtime directory. No runtime, identity registration, licence acceptance or automatic installer is bundled. Obtain permission and install the selected original separately using its official distribution:

- [DIA-NN 1.8 release](https://github.com/vdemichev/DiaNN/releases/tag/1.8).
- [DIA-NN 1.8.1 release](https://github.com/vdemichev/DiaNN/releases/tag/1.8.1).
- [DIA-NN 2.0 release and assets](https://github.com/vdemichev/DiaNN/releases/tag/2.0), selecting the 2.0.2 academic Linux archive, rather than inferring a 2.0.2 source tag.

Use the applicable upstream terms. The captured workflow requires the request's explicit `private_academic_local: true` declaration; that declaration cannot grant a licence. Keep restricted runtimes and their archives private. Place only the original files listed by `--profiles` in the runtime directory; keep archives, readme copies and other versions outside it. Preserve its filenames and contents and provide execute permission for the original program. Linux also needs Java17 or newer, readable `/proc`, `/usr/bin/setsid`, `/usr/bin/prlimit` and the original executable's shared-library dependencies.

## Validated execution and limits

The [qualification](diann-validation/QUALIFICATION.json) identifies the exact producing JAR and the tested scope. Each edition completed fresh original prediction and quantification of two complete, distinct Bruker TDF runs from public control PXD065380, with original match-between-runs enabled. The reference contains 1,704 records and is a limited control reference, not a whole-human proteome or an author's historical database.

| Edition | Full original execution control |
| --- | --- |
| 1.8 | Robust LC, fixed 10 ppm MS1/MS2 accuracy and scan radius 7 |
| 1.8.1 | Original default quantification, fixed 10 ppm MS1/MS2 accuracy and scan radius 7 |
| 2.0.2 | Original default QuantUMS, fixed 10 ppm MS1/MS2 accuracy and scan radius 6 |

These are validation settings, not acquisition recommendations. The versions need not produce identical libraries, fits, populations or quantities. Other documented request combinations still require the user's explicit settings and the same complete execution/output audit.

Each operating system passed 74 focused JUnit tests with no skips, three full saved-capture readbacks and 18 corruption/refusal controls. Original raw execution was on Linux x86_64; Windows validation covers native Java checks and saved readback. Separate direct original controls exercised all three editions' fresh two-run MBR. The packaged comparison preserved 23,494 earlier members and added 35 DIA-NN members; the existing main dispatch class is the only changed earlier member. Private probes and original runtime binaries are not packaged.

The [five source-role records](omics-2023-2026/diann-source-role-coverage.json) retain historical versions and unresolved settings. Exact author references/raw cohorts, per-run optimisation, custom/PAV libraries, nanoparticle aggregation, private cloud orchestration and downstream analyses remain separate. P029's literal `maxLGQ` and P097's `PG.Normalised` are preserved. These controls do not certify neural-network optimisation, empirical FDR or biological truth. No release is created by this checkpoint; preserve each capture's own producing JAR for readback.

## Start with an explicit request

Build the current source from the project directory:

```shell
./gradlew check executableJar
```

Use `gradlew.bat` on Windows. An older downloaded release does not acquire a new source command. Perform original execution with Linux Java; a Linux shell inside WSL is suitable. Use the full source-built JAR, which includes the existing DuckDB dependency used to read original Parquet reports.

In a Linux shell, choose absolute paths for your actual JAR and work directory:

```shell
JAR=/absolute/project/build/cli/omicsmatic-0.4.1.jar
WORK=/absolute/existing/work
java -jar "$JAR" diann-tools --profiles
java -jar "$JAR" diann-tools --example > "$WORK/request.json"
```

Edit `request.json` before execution. Every example value is an explicit starting value, not a claim about your acquisition or a paper's parameters. Replace both placeholder raw paths and the FASTA path. State the reference organism, database/release and any preparation in `reference.provenance`; explicitly state when its release is unreported. Review every digestion, modification, mass, charge, normalization, quantification, FDR and capacity setting. Supply the complete experiment's run population and original files.

The current protein request supports K/R digestion excluding cleavage before proline, fixed C/UniMod4 and variable M/UniMod35. It exposes peptide/fragment/precursor intervals, missed cleavages, N-terminal methionine excision and the number of variable modifications. Unsupported modification or enzyme profiles require separate implementation and validation. Peptide oxidation candidates do not establish PTM-site localisation.

The quantification block requires these explicit choices:

| Request field | Meaning |
| --- | --- |
| `threads` | Original engine's requested compute threads; process aggregate thread capacity is separate |
| `match_between_runs` | Requires at least two distinct run names and paths; enables the original MBR workflow |
| `normalization` | `source-default`, `global` or `none`; raw original outputs remain retained |
| `strategy` | `source-default` or `robust-lc`; Robust LC requests the original peak-height mode |
| `report_qvalue` | Main-report threshold from0.01 through0.05; changing it can affect protein inference |
| `ms1_mass_accuracy_ppm`, `ms2_mass_accuracy_ppm` | Explicit accuracies;0 requests the original automatic setting |
| `scan_window_radius` | Explicit positive radius, or0 for the original automatic setting |

This command begins prediction and quantification fresh. It does not reuse existing `.quant` caches. For legacy MBR it requests the documented smart-profiling option. Version2.0.2 uses its original default library-profiling behavior. These are version-specific commands, not a promise that the versions generate identical libraries, fits or quantities.

Inputs use canonical absolute Linux paths. Raw run basenames are preserved, including spaces. Distinct source paths with the same run name are rejected rather than renamed. The formats exposed by this request are:

| Format declaration | Required source |
| --- | --- |
| `bruker-tdf-d` | Complete original `.d` directory including `analysis.tdf` and `analysis.tdf_bin`; all additional files and empty directories are captured |
| `centroided-mzml` | Original `.mzML` file and explicit `centroided_spectra: true`; chromatogram mzML is unsuitable |
| `diann-dia` | Original DIA-NN `.dia` file |

Current full integration controls use Bruker TDF. The mzML and `.dia` branches follow the documented Linux interfaces and remain pending direct format-specific execution controls. Linux cannot directly read Thermo `.raw` or Sciex `.wiff` with these profiles. Any separately permitted conversion must preserve the experiment and its provenance; this command does not fabricate a conversion or substitute a file.

The FASTA reader preserves full UniProt `sp|accession|entry` or `tr|accession|entry` identifiers, descriptions, sequences and explicit gene names. Missing genes remain missing. Duplicate sequences with distinct identifiers remain separate; duplicate accessions and unresolved header syntax fail. Non-UniProt identifiers require their own supported mapping and validation.

## Execute and verify

Use new output and scratch paths whose parents already exist. Keep them separate from every original input and runtime, including their ancestor directories:

```shell
java -Xmx4g -jar "$JAR" diann-tools \
  --request "$WORK/request.json" \
  --home /absolute/private/original-diann-runtime \
  --out "$WORK/analysis-new" \
  --scratch "$WORK/audit-scratch-new"

java -Xmx4g -jar "$JAR" diann-tools \
  --verify "$WORK/analysis-new" \
  --scratch "$WORK/readback-scratch-new"
```

Choose the JVM heap and request capacities for the complete experiment. The sample `-Xmx4g` is not a universal resource recommendation. The Java reference and join checks consume caller-JVM memory; child-process address-space/RSS limits govern separate processes. Byte, file, row, residue, join and overall-wall bounds are explicit. Sampled aggregate RSS/process/thread monitoring complements per-process Linux limits; it is not a hard aggregate memory guarantee. Capacity or cancellation failures retain the attempt and admit no complete result.

Do not begin downstream analysis until the command returns0, `COMPLETED.json` exists, and `--verify` succeeds using the **exact producing JAR**. Keep that JAR with its saved evidence; a different build is not silently accepted. Saved verification also runs with Windows Java and does not need the original Linux runtime or original host input paths:

```powershell
java -Xmx4g -jar D:/retained/exact-producing.jar diann-tools --verify D:/retained/analysis-new --scratch D:/retained/readback-new
```

## Warnings, failures and retained results

Read `WARNINGS.md` and `OUTPUT-AUDIT.json`, then the original process logs and reports. A successful exit alone is insufficient: some original invocations can exit0 without processing any raw files. The capture checks edition banners, requested run population, terminal owned processes and the original predicted-library population.

| Exit | User-visible state | Required action |
| --- | --- | --- |
| 0 | Complete captured workflow or successful complete saved readback | Review retained original recommendations, annotation dispositions and scientific output before downstream use |
| 3 | **SKIP3: dependent analysis skipped** | Supply the named permitted runtime/platform/input/install/access prerequisite separately, or retain the skip |
| 2 | **FAIL2: no complete result admitted** | Read `FAILURE.json` when present, the named stage and original stdout/stderr; correct the specific input, process, numerical, resource or consistency cause and use a new attempt path |

Every created attempt starts with `INCOMPLETE.json`. A failure removes any completion marker and preserves captured inputs, partial output and process diagnostics. A failure before creation of the output directory is reported on stderr. No fallback analysis, identifier repair, missing-value imputation, version replacement or altered FDR is performed. Unknown original warnings fail qualification until their meaning is resolved; retained known recommendations still appear prominently in the final warnings file.

Two version2.0.2 dispositions need particular attention. During MBR the original engine may exclude candidates with no annotated fragments from its empirical library; its logged count can include targets and decoys. The warning and full first-pass/library outputs remain retained, and Java does not invent replacement fragments. QuantUMS may also warn that fewer than six runs provide less suitable hyperparameter optimization. Such a capture retains that limitation and does not certify optimal tuning. If the experiment needs that optimization, use its complete suitable run population in a new request. Do not duplicate runs to satisfy a count. `WARNINGS.md` identifies both dispositions prominently with required actions.

A complete capture contains:

- `request.json`, the exact declared scientific options and capacities.
- `inputs/` and `source-inputs.json`, all captured original files and empty directories, source declarations, byte hashes and totals.
- `engine/`, the original predicted library and fresh quantification cache.
- `outputs/`, the original main report, matrices, statistics and any additional original generated outputs.
- `processes/`, the original commands, logs, resource records and terminal owned-process state.
- `runtime/provenance.json`, original runtime and Linux helper identities; runtime binaries stay in their private installation.
- `ATTRIBUTION.txt`, `OUTPUT-AUDIT.json`, `WARNINGS.md`, `manifest.json` and `COMPLETED.json`.

Saved verification checks the complete captured namespace and hashes twice, rechecks supported reference/report/matrix/statistics consistency and compares saved warnings and scientific commands. It does not rerun the original model, prove binary authenticity or establish biological truth. Finite output alone cannot certify neural-network convergence or empirical FDR.

## Scientific field and scope distinctions

Original `Protein.Ids` lists peptide matches; `Protein.Group` identifies the inferred group. Gene/name checks follow that inferred group. Empty original genes are not filled from another matching protein. Supported low-confidence unassigned precursor rows retain their empty group and zero group quantities and are counted separately from quantified proteins.

A protein-matrix `Protein.Ids` cell can name its inferred group while the main report lists additional peptide-matched proteins. The audit permits that exact inferred-group annotation, counts those rows and preserves both original cells. It does not accept arbitrary subsets or relabel precursor-matrix annotations.

`PG.MaxLFQ` is a legacy MaxLFQ quantity in1.8/1.8.1 and can contain QuantUMS quantities in2.0.2. The same column label does not establish the same method. Main reports, matrices and statistics have distinct filters and counting units. In particular, the statistics protein count need not equal the number of protein-matrix rows. Original text precision and typed Parquet values remain unchanged; bounded comparison accounts for the original printed FLOAT32 precision.

Empirical/PAV library construction and custom decoy interpretation, other modification profiles, multiplexing, PTM-site localisation, Proteograph cloud orchestration, PepCal and downstream iq/Perseus/limma analyses remain separate scopes. This workflow does not establish full-paper reproduction. The native [iq command](IQ_MAXLFQ_WORKFLOWS.md) and [spectral annotation preparation](SPECTRAL_LIBRARY_ANNOTATIONS.md) have separate inputs and validation boundaries.

Consult the original [1.8.1 manual](https://github.com/vdemichev/DiaNN/tree/1.8.1) and [2.0 manual](https://github.com/vdemichev/DiaNN/tree/2.0) for the upstream modes and field definitions. Their settings and filters vary by edition.
