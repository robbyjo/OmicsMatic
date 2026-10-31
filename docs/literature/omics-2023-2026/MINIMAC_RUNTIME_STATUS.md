# Minimac runtime setup, checks and recovery

**Runtime setup and the Java execution backend are validated. The project
imputation command remains unfinished.** The installer prepares exact original
tools and checks real synthetic imputation. It does not validate a user's panel,
assembly, phased target data, sample identities or analysis settings. Minimac
therefore remains a missing integration in the 250-paper catalog.

Read the [runtime evidence](minimac-runtime-validation.json),
[paper roles](MINIMAC_SOURCE_REVIEW.md) and
[reference warnings](MINIMAC_REFERENCE_AUDITS.md). Cell Ranger remains excluded.

## Supported profiles

| Explicit profile | Original engine | Private additions |
| --- | --- | --- |
| `linux-x86_64-minimac4-4.1.6` | Minimac4 4.1.6 | Original Savvy 2.1.0 exporter and original notices/manuals |
| `linux-x86_64-minimac4-1.0.2-qemu8.2.2` | Minimac4 1.0.2 | Pinned QEMU 8.2.2, explicit `qemu64` CPU and copyright notice |
| `linux-x86_64-minimac4-1.0.0-private-gomp14` | Minimac4 1.0.0 | Original Ubuntu OpenMP library, matching GCC base copyright notice, historical README and current upstream project license notice |
| `linux-x86_64-minimac3-2.0.1` | Minimac3 2.0.1 | Original Minimac3, libStatGen, Twister and SAMtools notices |

These Linux x86_64 profiles were tested on Ubuntu 24.04 in WSL. Setup requires
Python 3.11 or later; analysis requires Linux Java 17 or later. Java 21 was used
for actual Linux backend execution; Java 17 compatibility is checked by strict
compilation. Current Minimac/Savvy and the legacy QEMU profile use static
executables. Dynamic profiles also bind their complete host library inventory,
resolved paths, sizes and hashes. The host kernel is recorded. A different or
changed host requires new admission and scientific health checks.

QEMU is explicit because the original 1.0.2 executable failed natively on this
host. OpenMP is extracted privately for 1.0.0; no system package is installed.
The original OpenMP package contains a documentation symlink, so its matching
official `gcc-14-base` package supplies the retained notice. The installer does
not create or follow that package symlink. The historical 1.0.0 source tag has
no standalone LICENSE file; its historical README and the current upstream
project GPL3 notice are retained distinctly. The installer does not claim that
the current notice was bundled with the historical tag. Original executables
are downloaded by the user-side installer and are not redistributed in this
repository. Legacy execution explicitly disables phone-home.

## Install where permitted

Run from the repository root in Linux, including a Linux shell inside WSL on
Windows. Select new, separate home and report directories:

```sh
python3 tools/literature/install_minimac.py \
  --profile linux-x86_64-minimac4-4.1.6 \
  --home "$PWD/build/omics-minimac4-4.1.6" \
  --cache "$PWD/build/minimac-archive-cache" \
  --report "$PWD/build/minimac-setup-report" \
  --allow-install --allow-download
```

Use another listed profile when the original version is required. For historical
Debian packages, an existing `/usr/bin/dpkg-deb` is needed for private data
extraction. The script never runs the upstream shell installer, changes the
kernel, installs a system package, registers with an imputation server or
uploads participant data.

For offline installation, omit `--allow-download` and supply the exact archived
files through one or more directories:

```sh
python3 tools/literature/install_minimac.py \
  --profile linux-x86_64-minimac4-4.1.6 \
  --home "$PWD/build/omics-minimac4-offline" \
  --cache "$PWD/build/minimac-offline-cache" \
  --report "$PWD/build/minimac-offline-report" \
  --archive-dir /path/to/verified/archives --allow-install
```

Names, official URLs, byte counts, archive payload boundaries and member hashes
are pinned in `tools/literature/minimac-runtime-lock.json`. All required archives
must be present or explicitly downloadable. A corrupt or linked cache entry is
preserved and refused. Existing unmanaged, changed or partially created homes
are preserved; use a new home after correcting the cause. Never edit an
admission marker to make a failed or changed installation appear valid.

An existing admitted home can be checked without granting installation or
download permission. Reuse its profile and home, give a new report path, and
omit both permission flags. A valid existing home is checked directly; an
unrelated poisoned cache does not trigger downloads or replace the runtime.

## Warnings and required recovery

**If software cannot be installed or executed, SKIP the dependent analysis.**
Read the console diagnostic and the report's `WARNINGS.md` and
`installation.json`. The cause, affected setup and required action are retained.

| Result | Meaning and required action |
| --- | --- |
| Exit 0 | Exact files, host closure and actual scientific health are admitted, or an existing admitted home was reverified. Review the report; this is not completed project imputation. |
| Exit 3 | Missing installation permission, absent offline archive without download permission, unavailable download/library/extractor, unsupported Windows host, old Python, or changed/unmanaged runtime. Preserve the cause; obtain permitted dependencies or use a supported host and a new home/report. |
| Exit 2 | Invalid paths, corrupt/linked/partial archives, failed integrity contract or another setup error. Preserve all files, inspect the named error and correct it before retrying to new paths. |

Python older than 3.11 reports the minimum version and exit 3 on the console
before creating paths. Windows setup reports exit 3 with Linux/WSL recovery.
Installation and download permissions are separate: granting downloads alone
does not authorize installation. Denied installation creates no runtime or
cache. Home, cache and report must be distinct, non-nested paths. Existing
reports are never overwritten. Preserve partial downloads and failed homes;
the installer does not silently repair or resume them.

The admitted home contains `.jlinalg-minimac.json`. The separate report retains
original stdout/stderr, owned process IDs/groups, exit status, cleanup state,
health inputs/outputs and hashes. Initial unavailable runtime checks require
an analysis skip. A runtime change during execution, exceeded output bound,
invalid work directory or conflicting diagnostic path prevents result admission.
The Java backend owns and terminates its subprocesses and records cleanup.

**Reference errors still require action.** Original mixed-ploidy X compression
wrote `AN=48` for 40 actual haplotypes; the native audit rejects that reference.
Preserve it and skip its use until an explicitly documented, independently
validated preparation is available. Do not silently replace AN or change panel
membership. Converted Minimac3 haplotype names require an explicit identity/copy
ledger. Use complete traceable reference exports with `--update-info never`;
original Savvy coordinate-query omissions affect both native and HTSlib CSI.
Runtime admission does not repair those issues. See the reference guide above.

## Verified scope and remaining work

Four new offline and four new permitted-download installations each completed
actual imputation of 120 variants in six target samples, matched their frozen
same-version records and recovered 534 known held-out genotype calls. Current
profiles also compressed and exported the original reference, independently
matching all 5,760 phased reference alleles and AC/AN. Twenty-four live Linux
setup controls cover permissions, preservation, all four valid reuses, corrupt
and linked caches, changed members, incomplete host closure and missing health.
The minimum-Python startup branch has a separate simulated-version unit check;
it is not a claim that an older Python executable was run.

The development JAR passes 471 focused tests in 36 suites, strict Java 17
compilation, Javadoc and executable packaging, plus 471 independently compiled
tests against its exact production classes on each Java 21 and Java 25. Actual
Linux packaged backend runs reproduce all four original imputation record sets
and current reference compression/export. Seventeen packaged controls cover
Windows skips, changed/incomplete runtime admission, marker changes, protected
diagnostics, linked work paths, unavailable historical exporters and output-bound
cleanup. All 15,080 prior class entries are unchanged.

The remaining integration needs a typed user command, complete legacy M3VCF
admission, explicit build/panel/phase/ploidy/allele/identity/QC contracts, native
output audits, failure quarantine and public analysis recipes. No project
imputation command, historical/server/full-paper reproduction, mixed-ploidy
production support or real-data accuracy is claimed. The catalog and broader
goal remain active with 270 missing integrations.
