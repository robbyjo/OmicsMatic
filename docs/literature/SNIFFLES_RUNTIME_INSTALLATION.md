# Install the tested private Sniffles runtime

**Runtime preparation is distinct from calling validation. Sniffles is still
excluded from the available-tool count.** The earlier execution development
checkpoint recorded unfinished provisioning. This new setup path works from a
minimal clean repository snapshot, without ignored reference directories or a
previous private installation.

## Requirements and actions

- Use the packaged tested Linux x86_64 host profile, including its exact Python
  3.12.3 binary and standard-library files. A version label alone is insufficient.
  Windows users must invoke the Linux commands through a permitted WSL environment.
- Downloads and private installation must be permitted. Both flags below are
  required. If permission, software, network access or the matching host is
  unavailable, the installer returns **SKIP3, exit 3**, writes a prominent
  `WARNINGS.md` and skips dependent calling. Correct the named error where
  permitted and retry into a NEW directory. Otherwise retain the skip.
- No system packages, shared Python libraries or existing runtime are installed,
  upgraded or replaced. The installer downloads four pinned official wheels and
  installs the three dependencies only into its new private `runtime/site`.
- **Do not change hashes to accept another host, source or dependency.** A new
  host needs a separately validated profile. The installer does not make that
  compatibility claim or replace global Python to force a match.
- Preserve errors and partial outputs. Existing output directories are refused.
  Request errors and detected changes to shared Python/standard-library files
  return **FAIL2, exit 2**. No installation receipt admits a scientific result.

## Run from a clean checkout

Choose a permitted matching Linux environment. Replace `/absolute/OmicsMatic`
with the canonical checkout path; all paths in this example must be absolute.
Create the parent directory first, then choose a NEW output:

```sh
mkdir -p /absolute/OmicsMatic/build/literature-review/sniffles-runtime

/usr/bin/python3.12 -I -B -S \
  -X pycache_prefix=/absolute/OmicsMatic/build/literature-review/sniffles-runtime/NEW_INSTALL/never-created-startup-cache \
  /absolute/OmicsMatic/tools/literature/install_sniffles_runtime_linux.py \
  --out /absolute/OmicsMatic/build/literature-review/sniffles-runtime/NEW_INSTALL \
  --allow-download --allow-install
```

`-I -B -S` and a fresh nonexistent cache prefix avoid ambient startup hooks and
cached Python bytecode. Missing flags are refused before downloads, runtime
copying or child-process execution. The setup uses complete original source
archives already retained in the repository's frozen native evidence, the
bundled exact legacy binary with original notices, and SHA-256/length-pinned
official PyPI wheels. It does not require a Hail bootstrap directory, a previous
Sniffles reference preparation, CMake, a compiler, system package installation or
preinstalled private dependencies.

Expected success is `PASS_INSTALLED_EXACT_PRIVATE_SNIFFLES_RUNTIME_ONLY`.
Read `WARNINGS.md` and `COMPLETED.json`. The resulting `runtime/` contains 1,721
exact files, including 573 private dependency files. Genuine pip 24.3.1 target
installation creates the recorded package metadata; it is not fabricated or
rewritten to match expected hashes. Original source and shared Python/standard-library
file checks run before and after preparation. Original sources and input data are retained.

Bind the resulting runtime with the corresponding development artifact:

```sh
java -cp /absolute/OmicsMatic/docs/literature/sniffles-execution-development/sniffles-execution-development.jar \
  org.jlinalg.cli.SnifflesRuntimeCli \
  --root /absolute/OmicsMatic/build/literature-review/sniffles-runtime/NEW_INSTALL/runtime \
  --out /absolute/OmicsMatic/build/literature-review/sniffles-runtime/NEW_BINDING
```

Binding checks original dependency imports, all five exact version probes and
native dependency/search bindings. Binding success also admits no SV analysis.
The [execution development guide](SNIFFLES_EXECUTION_DEVELOPMENT.md) describes
typed requests, full original/native outputs, whole-batch admission and limits.
Scientific calling and extended-format adapter validation remain separate work.

## Original programs and notices

The exact source editions are 1.0.7, 2.0.2, 2.0.7, 2.3.2 and 2.4 from the
[official Sniffles repository](https://github.com/fritzsedlazeck/Sniffles).
`sniffles-runtime-distribution/DISTRIBUTION.json` records their immutable commits,
complete source archive digests, wheel URLs/digests and binary provenance.
`assets.zip` contains the tested upstream 1.0.7 Linux binary and unchanged
Sniffles, BamTools, TCLAP, zlib, Google Test and JsonCpp notices. The original
scientific source was not patched. This is not an identified paper author's
binary; a fresh build in another absolute directory may produce different bytes.
Python, system C/C++ libraries and dependency wheels are not redistributed in
this asset archive.

**Retain observed source discrepancies.** Original 1.0.7 force-genotyping emitted
an `INV` record for a declared `DUP` in the constructed reference experiment,
including zero supporting reads and `0/0`. Do not relabel it to agree with the
input. Original 2.0.2 can print a fatal annotation error, exit zero and leave a
zero-byte VCF. Such a run is a failed analysis, never a valid empty callset.
Complete diagnostics, raw bytes and earlier unadmitted cases must be retained.
These observations do not establish general biological accuracy or paper
reproduction.

## What the installation validation proves

A clean source snapshot contains only 12 required repository files and starts
with no previous private runtime. It downloads the four exact official wheels,
installs into a new target, verifies all 1,721 resulting runtime files against
the closed profile and preserves its source files and shared Python/standard
library. Five additional public refusal checks cover each missing permission
combination, unsupported Windows execution and existing-output preservation.
The installer performs no SV calling and grants no catalog availability credit.

`sniffles-installation-validation/VALIDATION.json` pins the clean-install test,
the source experiments, all current helper sources and their evidence archive.
Check frozen integrity without installing or running Sniffles:

```sh
python tools/literature/sniffles_installation_evidence.py
python tools/literature/sniffles_installation_evidence.py --archive-only
```

Both modes require the pinned companion native checkpoint, distribution assets
and development JAR supplied in this repository. They are referenced by exact
hash rather than duplicated in the installation evidence ZIP. The default also
checks current source pins and preservation of the canonical executable;
archive-only checks the frozen evidence and companions. Neither mode executes
Java, Sniffles, pip or a network download, independently repeats calling,
authenticates execution or certifies biology.
