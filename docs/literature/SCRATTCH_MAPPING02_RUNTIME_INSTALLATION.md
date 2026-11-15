# Installing the original scrattch.mapping 0.2 runtime

The project provides `tools/literature/scrattchmapping02/install_runtime.py` for
a new private Linux x86-64 runtime. Its permitted-download installation has been
executed from scratch: two locked environments, two original annotation packages,
nine unchanged source packages and the complete namespace audit all completed.
The cache-only branch has also completed all fourteen steps in a second new
private runtime, using the retained exact archives and package cache.
Installation does not qualify an analysis or add a catalog family credit.

**If installation or downloads are forbidden or unavailable, skip the dependent
analysis.** The installer prints `SKIP3`, writes `WARNINGS.md` when the output is
writable and names the missing requirement. A `FAIL2` recording or integrity
failure admits no completed setup. Retain the attempt and retry NEW paths after
resolving the named cause. Analysis commands never install software themselves.

Static source/runtime inventories fail on unreadable or disappearing paths.
During a running original child, the workspace size monitor permits its temporary
directories to disappear; permission errors remain fatal. Final inventories stay
strict. Actual permission-denied and directory-disappearance controls exercise
both behaviors. Earlier interrupted controls remain retained as failures.

## Install when permitted

Use Python 3 and a permitted executable Pixi manager inside Linux/WSL. Pixi 0.83.0
was used for the actual installation. Pixi supports downloading a standalone
executable from its official releases; see the
[official installation guide](https://pixi.prefix.dev/latest/installation/#download-from-github-releases).
Supply the executable using `--manager`. This project installer does not run a
shell bootstrap script or change your shell startup files.

Choose a new absolute output outside distributable project directories. The
installer retains original archives, pristine source trees, separate compiler
work trees, complete raw logs and private installed libraries. Source compilation
needs a permitted C/C++ build toolchain. If one is missing, inspect the named
package's compiler log, install the permitted prerequisite and retry a new output.

```sh
python3 -B /path/to/OmicsMatic/tools/literature/scrattchmapping02/install_runtime.py \
  --manager /path/to/pixi \
  --out /work/private-mapping-install-001 \
  --allow-install --allow-download
```

`--allow-install` and `--allow-download` express the permissions for this run.
The installer requires permission before starting downloads or child installers.
Each archive is checked against its pinned size and SHA-256; the annotation
archives also match the locked publisher MD5 metadata. All original regular
source files are checked before compilation and again afterwards. Compilers work
on separate copies. The two environment installs use `pixi install --locked`;
Pixi documents that this rejects a manifest/lock mismatch in its
[install command reference](https://pixi.prefix.dev/latest/reference/cli/pixi/install/#update-options).

The default installation wall guard is 7,200 seconds. Use `--wall-seconds` for
an adequate declared duration. Keep all partial files after a failure or timeout;
do not reuse its output directory or replace historical checksums.

The two selected environments supply R 4.2.3 and a separate real Conda 24.3.0
manager. The installer configures the old reticulate/Pixi layout, with the R/Python
prefix's `bin/conda` pointing to that real manager. The audit checks the actual
Python 3.10.22 interpreter, NumPy 1.24.4, Python anndata 0.9.2, declared R package
versions and all 102 source-defined mapping functions. R anndata is the selected
original archived 0.7.5.6 wrapper. This is a selected comparison environment,
not a recovered author environment.

**Keep the runtime private.** The original scrattch.io and scrattch.vis licenses
restrict commercial redistribution. Their source and installed code are not
bundled into this project's distributable evidence. The installer downloads them
directly to the permitted private setup. Installing HICAT or BIGCAT as dependencies
does not qualify their separate paper workflows or add tool-family credits.

## Using local source archives without downloads

Without `--allow-download`, provide both a complete read-only `--source-cache`
and a permitted read/write `--cache-dir` containing the locked Pixi packages.
The source-cache directory must contain all eleven exact archive filenames from
`tools/literature/scrattchmapping02/install-source-profile.json`. Local archive
bytes must match the same pinned identities as downloads. Missing files skip
setup; they do not trigger a network fallback. The Pixi steps use `--offline`,
whose cache-only behavior is described in its
[official command reference](https://pixi.prefix.dev/latest/reference/cli/pixi/install/#configuration-options).

```sh
python3 -B /path/to/OmicsMatic/tools/literature/scrattchmapping02/install_runtime.py \
  --manager /path/to/pixi \
  --source-cache /work/permitted-original-archives \
  --cache-dir /work/permitted-pixi-cache \
  --out /work/private-mapping-install-002 \
  --allow-install
```

## Checking the result and starting an analysis

Require exit 0 and the top-level `COMPLETED.json` status
`PASS_INSTALLED_AND_AUDITED_ORIGINAL_PRIVATE_RUNTIME_NOT_ANALYSIS`. The installer
also creates `provider/READY.json` and its matching complete audit receipt.
A successful R compiler exit or a `READY.json` alone is insufficient.

Use the provider with the
[prepared-template request interface](SCRATTCH_MAPPING02_UPSTREAM_DEVELOPMENT.md):

```sh
python3 -B /path/to/OmicsMatic/tools/literature/scrattchmapping02/run.py \
  --runtime /work/private-mapping-install-001/provider \
  --request /data/request.rds \
  --assets /data/reference-assets \
  --out /work/mapping-analysis-001
```

The request, prepared taxonomy, original marker indexes and every external Annoy
file must already be complete. Use trusted RDS inputs and preserve serialized
index paths. Read every warning and complete original condition record. An
original return with nonfinite values remains subject to review; installation
and recording success do not establish biological validity.
