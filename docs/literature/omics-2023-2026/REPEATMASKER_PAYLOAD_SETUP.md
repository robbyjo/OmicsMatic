# RepeatMasker private payload setup

**Original `SEARCH` is still unavailable in the project command and returns
`SKIP3`.** This guide installs the exact source files and dependencies needed
for the next runtime-admission step. A successful installation reports
`PAYLOAD_INSTALLED_SEARCH_NOT_ADMITTED`. It does not certify a scientific run.
Native `CHECK`, `MASK` and `AUDIT` already work without this installation; start
with the [native example](../../../examples/repeat-native-workflow/README.md).

Use a JAR rebuilt from this checkout, or the private candidate identified in
[repeatmasker-native-validation.json](repeatmasker-native-validation.json).
The public release has not been updated. Export the helper to a new directory:

```powershell
.\gradlew.bat executableJar
java -jar build/cli/omicsmatic-0.3.7.jar repeat-tools --export-runtime-setup build/repeat-setup
```

The export contains `README.md`, `SHA256SUMS`, `setup.json`, the exact
`repeat-runtime-lock.json`, the installer, the isolated FamDB bootstrap and
OpenMP license notices. Exporting does not download or install anything.
Retain the JAR and export together. Verify every `SHA256SUMS` entry before using
the helper; checksums establish integrity, not authenticity.

Run setup with Linux x86_64 Python 3.11 or later and an existing `libzstd`.
On Windows, use a Linux shell inside WSL. This helper does not install OS
packages. The following example assumes this project is at
`D:\projects\JLinAlg`, which WSL exposes as `/mnt/d/projects/JLinAlg`:

```sh
cd /mnt/d/projects/JLinAlg/build/repeat-setup
sha256sum -c SHA256SUMS
python3 -I -S -B repeat-runtime-setup/install_repeat_payload.py --help

python3 -I -S -B repeat-runtime-setup/install_repeat_payload.py \
  --profile linux-x86_64-repeatmasker-4.2.6-custom-rmblast \
  --home /mnt/d/projects/JLinAlg/build/repeat-current-payload \
  --report /mnt/d/projects/JLinAlg/build/repeat-current-setup-report \
  --cache /mnt/d/projects/JLinAlg/build/repeat-archive-cache \
  --allow-install --allow-download
```

Change the paths to match your checkout. Their parents must already exist and
be real directories. Home, report and cache must be disjoint. Original runtime
paths use the ASCII subset `/[A-Za-z0-9_./+-]+`; spaces, shell metacharacters,
linked parents and existing home/report paths are rejected. Keep Unicode
source data intact; choose supported paths explicitly for the original runtime.

`--allow-install` authorizes only this explicit private setup. Downloads need
their separate `--allow-download` flag. Analysis commands do not install or
download software. To install offline, omit `--allow-download` and supply one
or more `--offline-archives /ABS/ARCHIVE_DIRECTORY` arguments. The helper checks
each selected archive's exact filename, size and SHA256 against the lock.
Changed cache files and retained partial downloads are preserved rather than
overwritten; correct the cause and choose a new cache when necessary.

The current profile uses the pinned original RepeatMasker 4.2.6 commit,
RMBlast 2.17.1, the checksum-pinned TRF release asset and private OpenMP library.
It supports the development custom-library fixture. It does not acquire a
current Dfam species database or RepBase.

For the historical profile, add
`--profile linux-x86_64-repeatmasker-4.1.5-dfam3.7-rmblast` and an explicit
`--python /ABS/exact-python3.12` matching the interpreter fingerprint in the
lock. Use new home/report paths. This profile retains the complete original
4.1.5 source, bundled Dfam 3.7 HDF5 library and metadata/licenses from the pinned
official h5py 3.16.0 and NumPy 2.5.4 wheels. The bootstrap uses `-I -S -B`,
does not load external site startup files and does not modify a global Python
environment. A different Python build is not silently substituted.

The default capacity is 16 GiB and 8,192 members. `--workspace-bytes` and
`--file-count` can increase explicit reviewed capacities. Preflight accounts
for the complete payload, selected archive cache and a 4 MiB/16-member report
reserve. Archive extraction checks complete selected files with streaming
SHA256 verification; it does not load the 5.86 GB HDF5 file into memory or
materialize archive links. The final inventory rejects missing, changed,
linked or extra payload members and extra empty directories.

**Read the result before doing anything with the payload:**

| Result | Meaning and required action |
| --- | --- |
| Exit `0`, `PAYLOAD_INSTALLED_SEARCH_NOT_ADMITTED` | The payload was installed and checked. Retain `installation.json`, `WARNINGS.md`, the lock and cache. Complete host/dependency closure and scientific runtime admission are still required before project `SEARCH` becomes available. |
| Exit `3`, `SKIP3_DEPENDENT_ANALYSIS_SKIPPED` | A dependency, supported profile or permission is unavailable. Leave the dependent analysis skipped. Read the named cause; use an allowed host, supply the exact permitted software/offline archives or authorize explicit setup/download where permitted. |
| Exit `2`, `FAIL2_NO_PAYLOAD_ADMITTED` | Input, integrity, extraction or capacity failed. Retain partial files and diagnostics, correct the named cause and use new home/report paths. Do not admit or reuse a partial payload. |

Errors before the report directory can be created are printed prominently on
stderr, including the required recovery. Export failures retain
`SETUP_EXPORT_FAILED.txt` when a partial export exists. Do not use a partial
export as a complete setup bundle.

The historical working-copy configuration requires one explicit change:
`ProcessRepeats` reads `LIBDIR` at compile time, before its environment override
is applied. The resource preparer therefore sets only that value in the fresh
owned working `RepeatMaskerConfig.pm`, records original/configured hashes and
the exact value in `resources.json`, and verifies the inverse recovers every
original byte. The sealed payload configuration and algorithm files remain
unchanged. Analysis caches belong to the fresh working copy; the full HDF5
logical size counts against capacity even when that resource is a hardlink.

Read the [current evidence and limits](REPEATMASKER_NATIVE_STATUS.md) and
[recipe contract](REPEATMASKER_RECIPES.md). Source papers do not report every
Dfam/dependency edition; these fixtures do not establish exact paper runtime
reconstruction, biological accuracy or biological absence. Restricted RepBase
resources were not acquired. Cell Ranger remains excluded.
