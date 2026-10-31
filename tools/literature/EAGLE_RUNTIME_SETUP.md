# Eagle private runtime setup

The project can now install and admit the original Eagle 2.4.1 Linux executable in a private directory. Setup runs cohort and reference phasing with both missing-call policies, retains the four outputs, and checks every genotype call. Java independently repeats these scientific output audits when admitting the runtime.

**The documented Eagle 2.4.1 subset is validated in this development checkout.** The typed `eagle-tools` command provides native VCF/BCF2.2 input checks and original cohort/reference phasing through the validated runner. All eleven paper roles have [source-verified summaries and explicit historical boundaries](../../docs/literature/omics-2023-2026/EAGLE_SOURCE_REVIEW.md). See the [workflow guide](../../docs/literature/omics-2023-2026/EAGLE_WORKFLOWS.md) for copyable recipes, setup export, completion markers and required actions, and the [BCF guide](../../docs/literature/omics-2023-2026/EAGLE_BCF_STATUS.md) for protected binary/index provenance and format boundaries.

## Run setup on Linux

Use Linux x86_64 with Python 3.11 or later. On Windows, open the project's existing WSL distribution and run these commands from the Linux view of the repository. Analysis will also require Linux Java. Setup needs the existing `ldd`, `prlimit`, `setsid` and `libzstd` system components; it never installs system packages.

Choose three separate paths for the private runtime, downloaded archives and setup report. Every report must use a new directory. Keep failed homes, reports, corrupt archives and partial downloads for diagnosis; correct the cause and use new paths for the affected items.

```bash
cd /mnt/d/projects/JLinAlg
python3 tools/literature/install_eagle.py \
  --profile linux-x86_64-eagle-2.4.1 \
  --home "$PWD/build/eagle-private/home" \
  --cache "$PWD/build/eagle-private/archives" \
  --report "$PWD/build/eagle-private/setup-report-1" \
  --allow-install --allow-download
```

Supply `--allow-install` only when private installation is permitted and `--allow-download` only when network downloads are permitted. For offline setup, omit `--allow-download` and add `--archive-dir /path/to/verified/archives`; repeat that option for additional directories. The expected names are `Eagle_v2.4.1.tar.gz` and `libgomp-16.2.0-he0feb66_7.conda`. The [runtime lock](eagle-runtime-lock.json) supplies their official URLs, sizes and SHA-256 hashes. Extraction copies selected regular members and executes no archive installation scripts.

To check an admitted home again, omit the installation and download flags and choose another new report directory:

```bash
python3 tools/literature/install_eagle.py \
  --profile linux-x86_64-eagle-2.4.1 \
  --home "$PWD/build/eagle-private/home" \
  --cache "$PWD/build/eagle-private/archives" \
  --report "$PWD/build/eagle-private/reuse-report-1"
```

Reuse verifies the exact file inventory, hashes, host kernel, external libraries, helper utilities and all four retained scientific health outputs. A changed helper or lock requires a new admission; versions are never substituted for a requested historical edition.

## Read warnings before analysis

Each setup report contains `installation.json` and a prominent `WARNINGS.md` with the cause and required action. Process stdout, stderr and cleanup records remain in that report. Structured console output uses UTF-8 so paths with non-ASCII characters remain readable.

| Exit | Meaning | Required action |
| --- | --- | --- |
| 0 | The private runtime passed admission or reuse. | Review `WARNINGS.md` and the stated health scope, then use `eagle-tools` with a typed recipe and complete output audit. |
| 3 | **SKIP3**: a dependency, permission, supported host or requested edition is unavailable. | Skip the dependent analysis. Obtain the missing dependency where permitted, run on the supported host, or use an authorized offline archive. |
| 2 | **FAIL2**: setup, integrity, execution, capacity or scientific output checks failed. | Retain diagnostics and partial files. Correct the named cause and rerun with new affected paths. No scientific result is admitted. |

The setup health processes have hard per-process address-space, CPU-time, output-file-size and open-file limits, plus wall-time and workspace checks. Cleanup tracks the private process session, including ordinary descendants, and handles termination during launch and execution.

The internal Java runner brackets execution with runtime and caller-supplied prepared-input integrity checks. It applies the same kernel limits and monitors workspace size, file count, process count, thread count and sampled aggregate resident memory. Its shutdown hook records owned PID/start identities, cleanup state and required actions in `shutdown.json` and `SHUTDOWN_WARNINGS.md`. A process exit of zero still requires the complete scientific output audit. Sampled resident memory is an abort check, not a hard aggregate memory bound; CPU and address-space limits apply per process. Failed diagnostic records remain available even when a failed workspace exceeds its quota.

## Scientific and license scope

The health fixture has 32 variants and four target samples. Four actual original runs validate 512 genotype calls, including 80 checks of missing or partial source calls. Observed diploid dosage must remain unchanged and observed output calls must be phased. Cohort keep-missing mode preserves the original missing GT text; reference keep-missing mode clears the whole missing diploid call. Imputation mode requires a complete phased call without assuming the unknown true genotype or phase.

These checks establish synthetic diploid call, identity, order and completeness contracts. They do not establish biological phasing accuracy, X chromosome eligibility or equivalence to Eagle 2.3, 2.3.5, server workflows or a complete paper. Earlier archive-golden versus fresh-runtime phase discrepancies remain retained for review.

The private home retains Eagle's GPL3-or-later license, the full GPL3 text and GCC runtime exception for libgomp, and the HTSlib, Boost and OpenBLAS notices associated with the upstream executable. Runtime binaries remain private downloads. Cell Ranger remains excluded by the user's instruction.

The historical VCF checkpoint includes [152 strict Java tests per JVM](../../build/literature-review/eagle-runtime/typed-cli-native-tests-2/observed.json), [Python scientific and process tests](../../build/literature-review/eagle-runtime/typed-cli-installer-tests-1/observed.json), [18 live Java process protocols](../../build/literature-review/eagle-runtime/process-linux-protocols-3/observed.json), [unrelated sibling isolation](../../build/literature-review/eagle-runtime/process-sibling-isolation-1/observed.json), [40 packaged original VCF phasing protocols](../../build/literature-review/eagle-runtime/typed-cli-packaged-linux-1/observed.json), [packaged Windows and Linux checks](../../build/literature-review/eagle-runtime/typed-cli-packaged-portable-1/observed.json) and [a fresh runtime with VCF examples](../../build/literature-review/eagle-runtime/typed-cli-final-public-1/observed.json). Its final package changed only installer warning wording relative to that 40-case candidate; its compiled classes were byte-for-byte identical. The current BCF bridge separately passes [169 strict tests on each of three JVMs](../../build/literature-review/eagle-runtime/bcf-bridge-native-tests-2/observed.json), [54 exact-JAR portable protocols](../../build/literature-review/eagle-runtime/bcf-bridge-packaged-portable-1/observed.json), [40 original-matched BCF phasing protocols covering 692,743 calls](../../build/literature-review/eagle-runtime/bcf-bridge-packaged-linux-1/observed.json) and [six native checks plus four original-matched BCF examples](../../build/literature-review/eagle-runtime/bcf-bridge-public-examples-1/observed.json). The BCF bridge uses the same admitted runtime and does not require a BCFtools installation during analysis.
