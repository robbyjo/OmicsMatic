# Eagle cohort and reference phasing

The `eagle-tools` command provides native VCF/BCF2.2 input checks and original Eagle 2.4.1 cohort or reference phasing. It protects source snapshots, builds validated BGZF and CSI stages, runs the admitted executable with resource limits, and audits the complete output population under the selected genotype and matching policies.

**The documented Eagle 2.4.1 subset is validated in this development checkout.** All eleven paper roles have [source-verified summaries and explicit boundaries](EAGLE_SOURCE_REVIEW.md); [validation](eagle-validation.json) identifies the exact private candidate and evidence. The commands below use the JAR built from this checkout. Existing INFO and FORMAT annotations require review: phase is re-estimated, supplied heterozygous phase can change, and stale phase-set, likelihood or dosage metadata is not recalculated by input preparation.

## Check the synthetic inputs

Build this checkout and confirm the command:

```powershell
.\gradlew.bat executableJar
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --help
```

The [copyable example](../../../examples/eagle-phasing-workflow/README.md) includes four VCF recipes, four BCF recipes and separate synthetic input archives. For the VCF examples, extract files into `examples/eagle-phasing-workflow/inputs` using the instructions there, then run:

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --recipe examples/eagle-phasing-workflow/cohort-check.json --out build/eagle-cohort-check-1
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --recipe examples/eagle-phasing-workflow/reference-check.json --out build/eagle-reference-check-1
```

These operations run on Windows or Linux and launch no original phasing process. Each validates the entire supplied VCF or decoded BCF, including rows outside the selected region. BCF conversion also validates complete wire framing and retains its original binary. Reference checks additionally retain matching decisions, explicit exclusions and the expected output plan. Every run needs a new output directory; failed and provisional directories remain available for diagnosis.

## Set up the original runtime

Original phasing requires Linux x86_64 Java and a private runtime admitted on that host. Windows users should run setup and phasing inside WSL. Export the self-contained setup helper from the JAR:

```bash
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --export-runtime-setup build/eagle-setup
python3 build/eagle-setup/install_eagle.py \
  --profile linux-x86_64-eagle-2.4.1 \
  --home "$PWD/build/eagle-home" --cache "$PWD/build/eagle-archives" \
  --report "$PWD/build/eagle-setup-report-1" --allow-install --allow-download
```

Use installation and download flags only where each action is permitted. For offline setup, omit `--allow-download` and supply `--archive-dir /path/to/verified/archives`. The [setup guide](../../../tools/literature/EAGLE_RUNTIME_SETUP.md) lists the pinned archives, existing system dependencies, scientific health checks and retained licenses. Setup changes no system packages. Analysis never installs dependencies or substitutes a historical edition.

After reviewing setup warnings, phase to separate new directories:

```bash
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools \
  --recipe examples/eagle-phasing-workflow/cohort-phase.json \
  --home "$PWD/build/eagle-home" --out build/eagle-cohort-phase-1

java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools \
  --recipe examples/eagle-phasing-workflow/reference-phase.json \
  --home "$PWD/build/eagle-home" --out build/eagle-reference-phase-1
```

The output VCF is `prepared/engine/result.vcf`. Its complete audit and raw SHA-256 are in `results.json`. Merely producing that file or returning a process exit code of zero does not admit a result.

## Read status and required actions

| Exit | Status | Required action |
| --- | --- | --- |
| 0 | Input check or phasing completed with review warnings. | Read `WARNINGS.md`; verify `COMPLETED.json` against `manifest.json` and its listed hashes. A check admits no phased result. |
| 3 | **SKIP3** unavailable runtime, host, requested edition or capability. | Skip the dependent analysis. Correct the named dependency where permitted or supply a verified supported input, then use a new output directory. |
| 2 | **FAIL2** invalid input, integrity, execution, resource or finalization failure. | Keep all files and diagnostics. Correct the cause and rerun to a new output. No partial result is admitted. |

`analysis-status.json` and `session.json` retain the cause and effective choices. A successful bundle requires `COMPLETED.json`; its `manifest_sha256` must match the raw manifest file, and every file named in the manifest must match its recorded SHA-256. For a native check, the completion marker says `input_check_completed: true` and `scientific_result_admitted: false`. Phasing sets the latter only after complete output checks.

An interrupted process retains `prepared/engine/shutdown.json` and `SHUTDOWN_WARNINGS.md` when shutdown cleanup runs. A finalization failure retains `FINALIZATION_FAILED.md` where writable. Interrupted or incomplete bundles have no completion marker. Preserve them and inspect any reported live PID/start identity before rerunning.

## Specify scientific choices

Recipes use schema version 1. Paths resolve relative to the recipe's `resource_root`; that directory resolves relative to the recipe file. Inputs must stay inside the declared root and use canonical files without linked ancestors. `target`, `reference` and optional `exclusion` declare `path` and `format`. Plain, gzip and BGZF VCF share `format: "vcf"`; plain or ordinary BGZF BCF2.2 uses `format: "bcf"` with legacy VCF4.0–4.3 header semantics. BCF2.1 and newer VCF header semantics produce SKIP3. Mixed roles are supported. See the [BCF workflow](EAGLE_BCF_STATUS.md) for exact scope and the separate generic codec's preservation limits.

A BCF role may explicitly declare `index` with `index_policy: "snapshot-only-regenerate"`. The index is protected and hashed as provenance only: it is never queried or certified against the source, and no sidecar is discovered implicitly. A fresh CSI is generated and validated for the prepared input. Keep the original binary, optional index, decoded text and conversion ledger under `source-intake/<role>/`; these remain fenced by hashes and included in the completion manifest.

Use `operation: "check"` for native admission and `operation: "phase"` for execution. `mode` is `COHORT` or `REFERENCE`, with the explicit profile `linux-x86_64-eagle-2.4.1`. Supply meaningful `assembly` and `input_review` declarations; these record the investigator's review and do not certify biological eligibility. The region states its literal `contig`, numeric `chromosome` and `chromosome_x`, inclusive `start` and `end`, and `selection: "literal-pos-input-subset"`. This selects input rows by POS and is not certification of every original indexed overlap or flank option.

Common policies are `missing` (`KEEP` or `IMPUTE`), `partial_missing` (`REFUSE` or `ALLOW_ORIGINAL_WHOLE_CALL_MISSING`), `keep_missing_ploidy_x` (boolean), and `outside_map` (`REQUIRE_BRACKET` or `SOURCE_BOUNDARIES`). A partial call such as `0/.` is treated as wholly missing during original inference; its supplied allele is ignored. Cohort KEEP preserves its original GT string, while reference KEEP clears a missing diploid call to `./.`. IMPUTE may replace the entire missing call. All-haploid X reference rows retain the original pass-through behavior, so imputation does not imply that every X missing call becomes complete.

Reference mode additionally requires `allow_reference_alt_swap` (boolean), `unmatched` (`REFUSE`, `DROP_WITH_WARNING`, `KEEP_UNPHASED`), `expansion` (`REFUSE`, `ALLOW_ALT_EXPANSION`, `ALLOW_SOURCE_REF_REPLACEMENT`), `reference_fallback` (`REFUSE`, `ALLOW_ORIGINAL_WITH_WARNING`), and `sample_overlap` (`REFUSE`, `ALLOW_WITH_WARNING`). Review every resulting warning and matching ledger. REF replacement explicitly changes the allele representation; it is not a claim of unchanged nucleotide dosage. An exclusion may be an eight-column site-only VCF or a site-only BCF. If its selected population is empty, the retained header-only stage is not passed to Eagle as an empty exclusion argument. Declared exclusions obey the explicit `unmatched` policy; choose the intended drop or keep-unphased policy rather than expecting `REFUSE` to discard rows silently.

Parameters require `threads`, `conditioning_haplotypes` (upstream Kpbwt), `pbwt_iterations`, `seed`, and `algorithm`. The admitted bounds are 1–1024 threads, 1–1000000 conditioning haplotypes, 0–10 PBWT iterations (0 selects upstream automatic behavior), and an unsigned 32-bit seed. Cohort algorithms are `COHORT_AUTO` and `COHORT_PBWT_ONLY`; reference mode requires `REFERENCE_PBWT`. Eagle 2.4.1 ignores the seed in reference mode, which the result warnings retain. Arbitrary engine arguments are refused.

## Size the resource limits

Use `limits: {"preset": "bounded-vcf-v1"}` and `process_limits: {"preset": "bounded-process-v1"}` for the versioned defaults. Optional numeric overrides belong in the corresponding object. Capacities refuse excess input rather than truncating it. Raise them only after reviewing the study's requirements and available resources.

| VCF limit override | Default |
| --- | ---: |
| `maximum_samples` | 100000 |
| `maximum_variants` | 1000000 |
| `maximum_cells` | 100000000 |
| `maximum_input_bytes` | 536870912 |
| `maximum_decoded_characters` | 8589934592 |
| `maximum_line_characters` | 1048576 |
| `maximum_header_characters` | 4194304 |
| `maximum_map_rows` | 50000000 |
| `maximum_selected_map_rows` | 2000000 |
| `maximum_map_characters` | 8589934592 |
| `maximum_join_records` | 10000 |
| `maximum_join_characters` | 4194304 |
| `maximum_join_comparisons` | 10000000 |
| `retained_bytes` | 536870912 |
| `ledger_bytes` | 1073741824 |
| `workspace_bytes` | 17179869184 |

The shared VCF byte limit also applies to BCF source/index copies, decoded VCF and output auditing. Conversion, source copies and native preparation share the overall workspace bound. `retained_bytes` bounds the admitted native storage estimate; it is not a whole JVM RSS limit. The preset also fixes header/FORMAT/allele and chromosome capacities, listed in [the typed recipe implementation](../../../src/main/java/org/jlinalg/cli/EagleRecipe.java).

| Process limit override | Default |
| --- | ---: |
| `wall_millis` | 3600000 |
| `address_space_bytes` | 8589934592 |
| `cpu_seconds` | 3600 |
| `file_bytes` | 4294967296 |
| `open_files` | 256 |
| `workspace_bytes` | Recipe workspace limit |
| `file_count` | 4096 |
| `processes` | 8 |
| `threads` | At least 64 and the requested engine threads |
| `sampled_rss_bytes` | 4294967296 |
| `monitor_millis` | 100 |

Address-space, CPU, output-file and open-file limits apply per process. Wall time, workspace, file/process/thread counts and aggregate RSS are monitored; sampled RSS is not a hard aggregate memory bound. PID/start identities and the newly owned process directory control cleanup. Source, snapshot, stage, recipe and runtime hashes are checked around execution and the complete output audit. Failed diagnostics remain available even if a failed workspace exceeds its quota.

Scientific validation covers declared call, dosage, ploidy, matching, missingness, identity/order and completeness contracts. It does not measure biological phasing accuracy or establish historical Eagle 2.3/2.3.5, server or whole-paper equivalence. Previously observed archive-golden differences remain retained. Cell Ranger remains excluded.
