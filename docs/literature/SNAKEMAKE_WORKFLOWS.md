# Original Snakemake workflows and portable evidence

**Available from current source:** selected original Snakemake 9.27.1 local
execution, independent directory/ZIP verification and genuine replay, with
preinstalled, Conda, Apptainer and combined deployment. The public commands have
original-engine, portable-reader and failure controls described below. This is
one scoped tool family; it does not establish author versions, downstream
biological correctness or whole-paper reproduction.

The current source exposes these commands:

```text
snakemake-tools run --request RECIPE.json --runtime ADOPTION --out NEW
snakemake-tools verify --bundle DIRECTORY_OR_ZIP --out NEW
snakemake-tools replay --bundle DIRECTORY_OR_ZIP --runtime ADOPTION --out NEW
snakemake-tools benchmark --help
```

Run `java -jar PATH_TO_CURRENT_BUILD.jar snakemake-tools --help` for the current
requirements and warnings. The published v0.4.0 release does not contain the new
workflow commands. The native benchmark component has its own validated scope
in [SNAKEMAKE_BENCHMARKS.md](SNAKEMAKE_BENCHMARKS.md).

Execution and replay require the selected Snakemake 9.27.1 runtime adoption,
Linux x86-64, Java 21 or later, a native POSIX output filesystem, and a complete
explicit recipe. Managed recipes additionally require the selected qualified
Conda/Apptainer providers and explicit cache declarations. Installation follows
the recipe's `allow_install` setting. The command does not substitute software
found on PATH or relocate an installed Conda prefix. Use trusted workflow code;
these commands are not a security sandbox.

**SKIP3 means the dependent analysis was skipped.** Read stderr and any retained
`FAILURE.json` and `FAILURE_WARNINGS.md`. Restore the named permitted runtime,
provider or cache, or use a permitted setup procedure, then retry with a new
output directory. An unsupported output filesystem requires a native Linux
location. If installation is forbidden, leave the analysis skipped.

**FAIL2 admits no completed result for the failed command.** Preserve inputs,
partial outputs, original diagnostics, invalidated completion markers and failed
attempts. Correct the named discrepancy and retry a new output directory.
Argument and overlap failures can occur before an output directory is created;
their explanation is on stderr. A successful nested parent does not replace a
failed replay. Verification checks captured evidence, not biological accuracy,
authorship or reproduction of a paper.

The earlier private bundle and replay qualifications remain documented in
[SNAKEMAKE_ORIGINAL_DEVELOPMENT.md](SNAKEMAKE_ORIGINAL_DEVELOPMENT.md) and
[SNAKEMAKE_DEPLOYMENT_DEVELOPMENT.md](SNAKEMAKE_DEPLOYMENT_DEVELOPMENT.md).
Those receipts apply to their frozen code editions. Use their matching reader;
never update historical hashes to admit changed code. The current public-command
edition has its own separate controls and matching reader.

## Start with a complete synthetic request

Build this checkout with Java 21 and retain its JAR alongside your results:

```bash
./gradlew executableJar --no-daemon --max-workers=2 --console=plain
SNK_JAR="$(pwd)/build/cli/omicsmatic-0.4.0.jar"
java -jar "$SNK_JAR" snakemake-tools --help
```

On Windows use `gradlew.bat` to build. Run original workflows inside permitted
Linux, using Linux paths for the JAR, recipe, runtime and output. For WSL, the
checkout/JAR may be under `/mnt/d/`; execution output must use a native Linux
location such as a new directory under the Linux user's home. Portable
verification and the native benchmark operation also work on Windows.

If a selected runtime adoption is already available, start with the
[runnable example](../../examples/snakemake-workflow/README.md). The generator
creates the entire workflow, data, explicit recipe and warnings. Its expected
summary is exactly `5`, `9007199254740993` and `1e-300`; it additionally records a
real original-engine benchmark. These invented counts establish software
behavior, not a biological result.

## Install and adopt a selected runtime

Run setup only where downloads, installation and execution are permitted. It
uses a private prefix and the selected lock/package bytes. It records original
source controls before creating an adoption. Do not point `--runtime` at an
arbitrary Python environment or an executable discovered on PATH.

```bash
mkdir -p "$HOME/.local/share/omicsmatic" build/snakemake-original
SNK_SETUP="$HOME/.local/share/omicsmatic/snakemake-runtime-NEW"
python3 -I -B tools/literature/snakemake_original/setup_runtime.py \
  --out "$SNK_SETUP" --records-out build/snakemake-original/NEW-runtime-setup \
  --allow-install
SNK_RUNTIME="$SNK_SETUP/adoption"
```

Omitting `--allow-install` causes setup to record **SKIP3** and launch no
installation. Preserve that attempt and skip dependent analysis. If setup fails,
read its retained diagnostics before attempting new paths; assigning a variable
does not establish that an adoption was created successfully.

For managed workflow software, prepare selected providers separately:

```bash
SNK_PROVIDER_SETUP="$HOME/.local/share/omicsmatic/snakemake-providers-NEW"
python3 -I -B tools/literature/snakemake_original/setup_deployment.py \
  --out "$SNK_PROVIDER_SETUP" \
  --records-out build/snakemake-original/NEW-provider-setup --allow-install

SNK_PROVIDER_CONTROLS="$HOME/.local/share/omicsmatic/snakemake-provider-controls-NEW"
python3 -I -B tools/literature/snakemake_original/qualify_deployment.py \
  --engine-python "$SNK_SETUP/environment/.pixi/envs/default/bin/python" \
  --managers "$SNK_PROVIDER_SETUP" \
  --container-source examples/snakemake-workflow/container-source.json \
  --native-out "$SNK_PROVIDER_CONTROLS" \
  --out build/snakemake-original/NEW-provider-controls

SNK_PROVIDERS="$HOME/.local/share/omicsmatic/snakemake-provider-adoption-NEW"
python3 -I -B tools/literature/snakemake_original/adopt_deployment.py \
  --conda-setup "$SNK_PROVIDER_SETUP" --apptainer-setup "$SNK_PROVIDER_SETUP" \
  --source-controls build/snakemake-original/NEW-provider-controls \
  --out "$SNK_PROVIDERS"
```

Each command must succeed before the next. Selected provider setup requires
Linux x86-64, glibc 2.38 or later, `dpkg-deb`, suitable native filesystem semantics
and sufficient permitted storage. The executed qualification host was Ubuntu
24.04 under WSL, glibc 2.39, effective UID 0. Its adoption does not qualify a
different host or UID. Fresh setup/adoption must pass its own checks; otherwise
skip dependent analysis. The deployment development guide explains source
controls, separate provider prefixes and retained historical failures.

## Choose installation permission explicitly

| Recipe mode | Required selection | Existing immutable caches when installation is false |
| --- | --- | --- |
| `preinstalled` | schema 1, explicitly declared executables | No managed providers |
| `conda` | schema 2, selected provider adoption | Exact original Conda cache prefix |
| `apptainer` | schema 2, selected provider adoption | Complete matching image cache |
| `conda-apptainer` | schema 2, selected provider adoption | Both matching caches |

For example, create a new request that explicitly permits Conda deployment:

```bash
python3 -I -B examples/snakemake-workflow/prepare.py \
  --runtime "$SNK_RUNTIME" --providers "$SNK_PROVIDERS" \
  --deployment conda --allow-install \
  --out "$HOME/snakemake-conda-example-NEW"
```

The generator installs nothing. The subsequent `run` follows the recipe's
`execution.allow_install` boolean. When true, both cache declarations must be
null so original deployers use new owned paths. When false, missing/incomplete
deployments produce **SKIP3 before deployment**, with no silent installation or
fallback. Use `--conda-environments` and/or `--container-images` on the generator
to declare existing matching caches. Preserve their complete state at their
original prefixes. Copying an installed Conda environment to another prefix is
not a supported replay mechanism. Conda hashes include definition text, prefix
and container context, so matching Python versions alone do not establish reuse.

## Declare the complete analysis contract

Inspect the generated `recipe.json` before execution. It explicitly declares
the workflow root/Snakefile, config files, target rules or relative paths,
resource roots, parameter bindings, external executables, output family,
deployment policy and limits. Declare every required input root and intended
output; a zero engine exit alone admits no result. Existing output paths are
rejected. The output parent must already exist, and output must be separate from
all input, runtime, provider and borrowed-cache paths.

`{"$input":"data","path":"counts.tsv"}` binds to captured data;
`{"$output":"summary.tsv"}` binds to the owned publication directory.
Configuration numbers retain their original JSON numeric spelling. Config files
and workflow profiles remain captured; explicit request bindings take declared
precedence through the original parser. Partial-DAG/dry-run and conflicting
deployment profiles are rejected. Shadow staging is explicitly assigned to the
owned execution directory, including when a captured profile requests another
shadow prefix. Legacy schema-1 managed requests are skipped; use an explicit
schema-2 request rather than assuming installation permission or rewriting an
old result.

Legacy `use-conda` profile flags are currently rejected even if the requested
deployment name matches. Preserve the author profile and original failure;
changing a profile requires a deliberate new workflow edition. The adapter does
not silently repair it or claim an unqualified historical engine version.

Limits are explicit caller choices, not permission to drop data or jobs.
Increase declared process/capture capacities for larger DAGs before a new run.
File limits also apply to downloaded images; an image larger than
`output_file_bytes` cannot be installed within that contract. Workspace/RSS
sampling is not an OS disk quota or aggregate JVM memory guarantee. An exceeded
limit rejects the complete operation; retain diagnostics and retry a suitable
new contract/output.

`wall_seconds` limits the supervised upstream process. Input copying, hashing,
Java verification and final evidence sealing run outside that process timer.
The process resource limits do not cap the entire Java caller or turn portable
verification into an OS sandbox. Large complete software captures can take
substantial time to hash and archive; poll the actual running process rather
than launching a replacement because an observation timed out.

## Verify and replay

`verify` reads either a complete bundle directory or a standalone ZIP without
launching an engine or consulting producer paths. Literal software symlink and
device metadata are data, never host paths opened by the portable reader. Keep
the exact matching reader JAR; later code editions can have different frozen
fingerprints. ZIP membership and bytes are checked before and after its scoped
use. A failed outer replay cannot fall back to a nested earlier success.

`replay` performs fresh original-engine execution from captured workflow/data
inputs and retained declared settings. Exact outputs must match bytes and member
families. Benchmark reports retain fresh timing/RSS measurements and compare
their selected schema/repeat family. No favorable repeat is selected or pooled
automatically. Fresh permitted installation may resolve different dependency
bytes; use pinned package/archive inputs for version-specific comparisons and
inspect both captured software trees.

An exact output containing an installation prefix can correctly fail comparison
after fresh installation. Preserve both results. If the desired scientific
contract is path-independent, author a new explicit contract/parent run; never
normalize, repair or replace an existing parent. The synthetic example declares
its scientific summary and benchmark report, while retaining separate software
provenance in the captured working state.

## Paper-specific roles and boundaries

The primary-source audit scans all 250 frozen XML files and checks exact section,
paragraph index and paragraph hash for these six roles. No engine version is
established by these selected paragraphs. The project selects 9.27.1 explicitly;
that selection does not identify the authors' runtime or execute their data.

| Paper | What the source establishes | Separate work |
| --- | --- | --- |
| [P027](https://pmc.ncbi.nlm.nih.gov/articles/PMC12779559/) | A modified CLAPAnalysis RIP-seq workflow orchestrates trimming/QC, repeat/structural-RNA filtering, genome alignment and duplicate marking. | Exact author workflow revision, input reads, references, aligner options and downstream RIP-seq biology. |
| [P061](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719113/) | Snakemake constructs pipelines for large-scale computation alongside ALLCools, Xarray and dask. Tensor storage uses Zarr/Google Cloud; SkyPilot sets up cloud environments. | Cloud provisioning, author deployment and methylation/3D-genome algorithms. |
| [P181](https://pmc.ncbi.nlm.nih.gov/articles/PMC13361010/) | LaDDR/Pantry resource comparison on 100 Geuvadis samples sums recorded `cpu_time` and takes maximum `max_rss` per pipeline. | The 100-sample experiment and repeat assignment. The reported maximum is not concurrent whole-pipeline peak memory. |
| [P182](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639091/) | A pipeline orchestrates cartilage RNA-seq alignment, gene counting, transcriptome projection, gene-level aggregation and QC. | STAR/featureCounts/RSEM/Picard/dupRadar/RSeQC semantics, references and study filtering decisions. |
| [P190](https://pmc.ncbi.nlm.nih.gov/articles/PMC12626415/) | MAJIQTL code availability includes a Snakemake pipeline; contributions identify pipeline development. | Availability does not establish that the reported analyses ran through it. No execution claim is added. |
| [P210](https://pmc.ncbi.nlm.nih.gov/articles/PMC13277689/) | A SHAPEIT5 wrapper phases combined exome/array genotypes and uses phased common variants to scaffold rarer variants. | Harmonization, references, SHAPEIT5 details, cohort inputs and recessive burden analysis. |

One scoped workflow-engine family is distinct from its downstream tools. Native
benchmark aggregation is part of the same family and earns no second integration
credit. Neither the six reviewed roles nor a completed synthetic workflow proves
whole-paper biological reproduction or semantic completeness of the full cohort.

## Qualification evidence

The [catalog qualification record](snakemake-catalog-integration/QUALIFICATION.json)
binds the exact packaged JAR, completed controls and source boundaries. The
[catalog source audit](snakemake-catalog-integration/CENSUS.json) retains the
250-file census and six primary-source role pointers. Scientific executions use
synthetic inputs. Original warnings and expected failures remain retained.

| Controls | Result and scope |
| --- | --- |
| 12 preinstalled workflows | Includes configuration bindings, scripts, checkpoints, retries, pipes, benchmark repeats/extended reports, profiles and expected skip/failure. |
| 21 managed workflows | Nine successes, six expected skips and six expected failures across all three managed modes; includes fresh installation, immutable reuse and explicit owned shadow staging. |
| 14 genuine replay generations | Two preinstalled plus twelve managed generations; both directory and ZIP parents run with original caller inputs unavailable, then restore the callers. |
| 3 negative managed replays | Two fresh-prefix exact-output mismatches correctly fail; missing provider adoption skips before execution. Failed outer roots cannot use nested success. |
| 25 Windows portable controls | Three valid representations accepted; twenty-two resealed semantic contradictions rejected, with no producer runtime or PATH available. |
| 6 Windows standalone bundle verifications | Every managed mode/permission combination verifies independently. |
| Copied matching reader | A second-generation combined managed lineage verifies from a different working directory on Windows; 17,777 source members remain sealed. |
| 15 public failure controls | Output overlap is rejected before mutation; failed success delivery and a ZIP changed during live replay invalidate completion and retain scientific outputs. |
| 4 generated examples | Actual run/verify in every deployment mode, exact synthetic summary, original benchmark report and unchanged caller inputs. |

The first example checker used an incorrect hard-coded execution directory after
its scientific run had completed. That failed check is retained; the corrected
checker reads `invocation.json` and independently verifies the completed run.
It does not rerun science to repair a post-run lookup. A later warning-only text
change is separately checked to leave workflow/data/recipe generation unchanged;
historical fingerprints are retained.

The current Java sources match the packaged build that passed 331 Java tests,
22 Linux Python tests and the website check. New catalog metadata does not change
those scientific Java sources. Vendor-wide plugin support, remote executors,
cloud provisioning, arbitrary external workflow dependencies and author
environments are outside this selected qualification. Full project implementation
and whole-cohort semantic review remain open.
