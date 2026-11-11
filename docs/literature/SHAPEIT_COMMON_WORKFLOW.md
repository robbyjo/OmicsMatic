# SHAPEIT5 common-stage workflow

Current source and the locally built JAR provide `shapeit-tools` for the exact controlled original SHAPEIT5 source commit `c34d4db3e99a2f7e23deb727671ae260901a5886`, banner 5.1.1. It supports cohort, reference-assisted and scaffold-assisted common-stage fitting with ordinary diploid GT data. These are local development capabilities; a previously downloaded release JAR may not contain the command. Build the current source with `./gradlew executableJar` and use `build/cli/omicsmatic-0.3.7.jar` below. Original fitting requires Linux x86_64 Java with AVX2 and FMA; on Windows, run Linux Java inside WSL.

**WARNING — reference-assisted AN:** the original runtime writes target genotypes and target AC but includes target and reference haplotypes in AN. Ten targets plus forty reference samples produce AN=100 with only twenty target allele slots. Preserve `analysis/phased.bcf`. For target-specific allele frequencies, use `analysis/output-audit/target-allele-counts.tsv`, which independently reports target AC/AN alongside original AC/AN. Do not silently retag the original file or treat its AN as the target denominator.

**WARNING — installation permissions and exact runtime:** the source checkout now includes `tools/literature/build_shapeit_runtime.py` for permitted, pinned downloads and compilation. It reconstructs the same 32 program, loader, library and notice members accepted by the existing exact-build adopter. Setup alone does not admit any scientific analysis. Missing or forbidden permissions, host capabilities or prerequisites require **SKIP3 and a skipped dependent analysis**. Build, integrity or capacity failures require **FAIL2 with retained files and no setup success**. Preserve the report, correct its named cause and retry with fresh build, home and report paths. The JAR's exported setup remains an adopter for already existing exact members.

## Prepare the runtime

### Build from pinned source downloads

Use a current source checkout on Linux x86_64 with Python 3.11 or later, AVX2/FMA on every processor, and permitted executable `/usr/bin/dpkg-deb`, `/usr/bin/setsid` and `/usr/bin/prlimit`. The source checkout and setup paths must use only ASCII letters, digits, `/`, `.`, `_`, `+` and `-`. Provide at least **8 GiB free on the build filesystem**, at least **0.5 GiB for the download cache**, and enough memory for two compiler jobs. Compilation and dependency provisioning have explicit process limits; installation may need more available host memory than the 128 MiB Java validation controls.

```sh
mkdir -p runtime-build runtime-homes download-cache runtime-reports
python3 tools/literature/build_shapeit_runtime.py \
  --work "$PWD/runtime-build/shapeit-common-1" \
  --home "$PWD/runtime-homes/shapeit-common-1" \
  --cache "$PWD/download-cache/shapeit" \
  --report "$PWD/runtime-reports/shapeit-common-1.json" \
  --allow-download --allow-source-build --allow-install
```

The build and runtime destinations must be new. Keep the build workspace, runtime home and download cache in separate trees, with the report outside all three. Parent directories must already exist and be canonical. Only a verified download cache is reusable; partial or changed cached files are retained and refused. If installation is not permitted or a prerequisite is unavailable, **skip the dependent analysis** and follow the report's required action. The installer does not change system packages.

The pinned downloads include the original **435 MB SHAPEIT source archive**, Pixi 0.83.0, Ubuntu's libc6 2.39-0ubuntu8.9 package and a separately locked GCC 14.4/Boost 1.85/HTSlib 1.18 compiler environment. Only 140 selected upstream source files are extracted for the common-stage build; original source bytes and links are checked again after compilation. The two programs and every runtime library must match the existing lock exactly. Original encoded build paths are preserved to reproduce those bytes; **run analyses through `shapeit-tools`**, whose private loader verifies dependency bindings.

Read `status`, `error`, `required_action`, `compiler_warning_lines` and the retained `*.stdout`, `*.stderr` and `*.process.json` logs. The original makefile emits static-library discovery warnings for this explicitly shared-library build. Those messages are retained; they do not authorize substitution of another library. A successful report says `installed-exact-source-built-runtime-only` and `scientific_analysis_admitted: false`. Every analysis still requires its own native admission and complete output audit. Preserve any failed or provisional home and use fresh destinations after correcting a failure.

### Adopt existing permitted exact members

Export the setup helper from the current local JAR:

```sh
java -jar build/cli/omicsmatic-0.3.7.jar shapeit-tools \
  --export-runtime-setup shapeit-setup
```

The export contains `install_shapeit.py`, `shapeit-runtime-lock.json` and instructions; it contains no runtime executables. The source manifest is a JSON object mapping **every** locked member, such as `bin/phase_common` or `lib/libhts.so.3`, to an existing permitted file with exactly the locked SHA-256. To adopt an existing exact profile already arranged as `bin/`, `lib/` and `share/`, generate the complete manifest rather than manually omitting libraries:

```sh
python3 - <<'PY'
import json
from pathlib import Path
lock = json.loads(Path('shapeit-setup/shapeit-runtime-lock.json').read_text())
existing = Path('/absolute/path/to/existing/exact-profile')
manifest = {name: str(existing / name) for name in lock['files']}
Path('shapeit-source-members.json').write_text(json.dumps(manifest, indent=2) + '\n')
PY

python3 shapeit-setup/install_shapeit.py \
  --home /absolute/path/to/new-private-shapeit \
  --source-manifest shapeit-source-members.json \
  --allow-install \
  --report shapeit-install-report.json
```

Replace the two absolute paths with permitted locations. The private home, its parent and output paths must be canonical without linked ancestors. The home cannot contain whitespace, colons or control characters, and must be separate from analysis output. Existing homes and reports are preserved; choose fresh destinations. A different SHAPEIT executable, compiler build, library version or historical edition is not substituted when hashes differ. The current controlled installation used a private development environment; it did not change system packages.

## Run a common-stage analysis

Supply sorted **BGZF BCF2.2** inputs and their matching `FILE.bcf.csi` files. CSI is consumed by the original synchronized reader, and the native linker independently closes its physical occurrence census against the complete raw input. Arbitrary supplied indices are not accepted merely because a file exists. Bare BCF and VCF transport/conversion are not available through this command yet. Target plus reference samples must total at least fifty; scaffold samples do not count toward that source requirement.

Save this as `shapeit-common.json`, replacing input paths, contig and region for the study. Relative input paths resolve against the recipe's directory:

```json
{
  "schema_version": 1,
  "profile": "shapeit5-c34d-common-linux-x86_64",
  "target": "input/cohort.bcf",
  "reference": "input/reference.bcf",
  "scaffold": "input/scaffold.bcf",
  "map": "input/genetic.map.tsv",
  "contig": "20",
  "region": "20",
  "parameters": {
    "snps_only": false,
    "minimum_maf": 0.001,
    "seed": 15052011,
    "threads": 1,
    "count_policy": "REQUIRE_SAMPLE_COUNTS",
    "missing_policy": "SOURCE_WHOLE_GENOTYPE_IMPUTATION"
  }
}
```

Remove `reference`, `scaffold` or `map` entirely when unused; do not set them to a guessed file or `null`. Without a map, the original uniform 1 cM/Mb default is retained and prominently reported. A supplied map must cover the selected positions without reaching the known unresolved original extrapolation boundary. Genome build, map suitability and study design remain investigator responsibilities. Read the [map and source-behavior notes](SHAPEIT_DEVELOPMENT.md).

```sh
mkdir -p output
java -Xmx4g -jar build/cli/omicsmatic-0.3.7.jar shapeit-tools \
  --recipe shapeit-common.json \
  --home /absolute/path/to/new-private-shapeit \
  --out output/shapeit-common-1
```

The parent of `--out` must already exist and be canonical. The output directory itself must be new. The example heap is a starting configuration, not a study capacity guarantee. Complete-population sample, record, cell, byte, coordinate-bucket, ledger, table, process and whole-workspace limits are explicit and configurable. A failure never authorizes sampling or truncating inputs. For example, add a `limits` object to the recipe to raise the input sample and byte limits while retaining the remaining defaults:

```json
"limits": {
  "raw": {"samples": 200000, "inputBytes": 8589934592},
  "process": {"wallMillis": 86400000, "addressSpaceBytes": 68719476736},
  "wholeWorkspaceBytes": 274877906944
}
```

Raise every affected complete-population limit and provision sufficient Java heap, disk and process resources. The schema rejects unknown fields and arbitrary engine flags. Nested limit names correspond to the explicit records in `ShapeitCommonPhasing.Limits`, `ShapeitBcf.Limits`, `ShapeitRawIndex.Limits`, `ShapeitCommonVariants.Limits`, `ShapeitGeneticMap.Limits`, `ShapeitCommonCalls.Limits`, `ShapeitJoinedRecords.Limits` and `ShapeitProcess.Limits`. Hard per-process address-space, CPU, file-size and file-descriptor limits are distinct from sampled aggregate RSS/process/thread checks and monitored wall/workspace limits. Sampled RSS is not a hard aggregate memory limit.

## Choose the count and missing-call policies

`REQUIRE_SAMPLE_COUNTS` checks declared AC/AN against called genotypes for every selected ordinary-GT panel record and refuses mismatches before fitting. `USE_DECLARED_COUNTS` preserves the source scanner's original declared AC/AN for filtering, even when those counts describe a different population; every selected record is retained in `analysis/input-count-review.tsv`, mismatches are counted and a prominent warning requires scientific review. Neither policy retags the original inputs.

`REQUIRE_COMPLETE` refuses any selected missing target GT before fitting. `SOURCE_WHOLE_GENOTYPE_IMPUTATION` retains original whole-call imputation: **a partially missing target call can lose its observed allele**. The audit reports that exposure; it does not claim that the discarded allele was preserved. Phase-marked missing alleles and vector-end/haploid storage are refused by this admitted workflow. Selected reference and scaffold calls must be complete phased diploid calls. Duplicate sample names are refused. A configured scaffold applying zero constraints is refused before fitting, even though the original runtime can otherwise exit successfully without using it.

## Read the result or recover from failure

**Only a public bundle with `COMPLETED.json`, its matching `manifest.json` SHA-256 and every listed file hash is complete.** `FINALIZATION_FAILED.md` or any missing, changed or malformed completion/manifest member invalidates public completion. Original process exit zero and an inner `status.txt` are insufficient. Inspect:

- `WARNINGS.md` and `session.json` for status, scientific warnings and recovery actions.
- `analysis/phased.bcf` for unchanged original fitting output.
- `analysis/output-audit/target-allele-counts.tsv` for independently verified target AC/AN.
- `analysis/input-count-review.tsv` for declared versus called selected-panel counts.
- `analysis/analysis.json`, `analysis/staged/`, the complete raw spools, selection ledger, original join and process records for input/runtime/output provenance.

**SKIP3:** dependency, host, source profile or capability is unavailable or forbidden. Skip the dependent analysis; provision the permitted exact profile or a supported environment before retrying. **FAIL2:** invalid input, integrity, execution, capacity or finalization failure. No public result is admitted. Preserve original inputs, failed output and logs, correct the named cause and rerun to a fresh directory. Never reuse a partial output to claim success.

The current packaged command passed nine actual fits, six pre-fitting input/policy refusals, four unsupported-transport skips and one malformed-compression failure. Full output audits include all applied scaffold orientations and expose the reference-assisted AN departure. Controlled process tests cover resource failure, cancellation, orphan cleanup and JVM termination, preserving unrelated processes. The [versioned validation record](omics-2023-2026/shapeit-managed-common-validation.json) retains complete evidence; nine portable Java replays recheck every genotype and both count tables without launching SHAPEIT. All 380 native/replay tests pass on three tested JVM profiles. These are synthetic source-behavior controls, not biological phase-accuracy, convergence or paper-data benchmarks. Binary XCF, VCF/bare-BCF staging, SHAPEIT2/4, pedigree, haploid/phase-set options, rare-stage phasing, ligation and the historical 5.0.0/local-contig singleton-posterior workflow remain separate implementation requirements. Cell Ranger remains excluded.
