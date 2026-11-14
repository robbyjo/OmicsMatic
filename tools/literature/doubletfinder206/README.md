# Original DoubletFinder 2.0.6 workflow

**Review required:** the SCT control emits 3,706 original R warnings, including
12 iteration-limit warnings. The original source averages paired counts to form
artificial doublets; this control emits fractional-count warnings during SCT.
The selected Seurat profile also reports that optional glmGamPoi is absent and
uses its original native estimator. These outputs do not qualify convergence.
Read `WARNINGS.md`, `original/condition-summary.json` and the full original
condition RDS before interpreting results. Do not round the artificial counts,
suppress warnings or overwrite the qualified environment to obtain a pass.
A changed estimator/dependency profile needs independent original controls and
a new runtime/output. If a requested protocol requires unavailable or forbidden
software, skip that dependent analysis.

The workflow invokes the unchanged original public functions `doubletFinder`,
`paramSweep`, `summarizeSweep`, `find.pK` and `modelHomotypic`. The selected source
is [commit 1b244d8](https://github.com/chris-mcginnis-ucsf/DoubletFinder/tree/1b244d8f0d54b4b1cb4365639931bbb16f01e1cd),
whose DESCRIPTION declares version 2.0.6 and CC0. The locked development profile
uses Linux x86-64/WSL, R 4.5.3, Seurat 5.5.1 and SeuratObject 5.4.0. It is not
an inferred author environment.

The [three native Java operations](../../../docs/literature/DOUBLET_FINDER206_NATIVE_COMPONENTS.md)
work without R. Full original execution needs permitted external prerequisites.

Install the locked dependencies and exact original source into a NEW private
directory. Supply a trusted permitted Pixi executable:

```sh
python3 -B tools/literature/doubletfinder206/install_runtime.py \
  --manager /path/to/pixi \
  --out /path/to/NEW-install \
  --allow-install --allow-download
```

Without `--allow-install`, installation returns SKIP3 and dependent analysis is
skipped. Without `--allow-download`, supply the exact original local
`--source-archive` and a complete permitted `--cache-dir`; the manager runs
offline and missing cache entries return SKIP3. Partial new installations and
complete raw logs remain available. No historical installation is removed.
The original installer may clean its own failed new package.

The installer initializes the original Cairo/fontconfig cache before recording
the complete runtime file inventory. Earlier uninitialized providers and their
failed guards remain retained. No source/library file is omitted from checking
and no prior provider hash is refreshed. If you provision an exact environment
yourself, initialize graphics before creating a NEW provider with
`adopt_runtime.py`; its `--prefix` is the directory containing `pixi.toml`,
`pixi.lock`, `.pixi/envs/default` and the private `library`, and `--source` is the
complete unchanged extracted original source tree. Its `--out` must be separate.

If a saved runtime inventory differs, inspect the exact added, removed and changed
files with the read-only diagnostic command. It grants no admission and never
refreshes a provider:

```sh
python3 -B tools/literature/doubletfinder206/inspect_runtime.py \
  --runtime /path/to/OLD-provider --out /path/to/NEW-runtime-differences
```

Prepare the self-contained [synthetic examples](../../../examples/doubletfinder206/README.md).
Run one exact request:

```sh
python3 -B tools/literature/doubletfinder206/run.py \
  --runtime /path/to/NEW-install/provider \
  --request /path/to/NEW-examples/requests/lognorm-default-pN.rds \
  --out /path/to/NEW-fit \
  --wall-seconds 1800 \
  --maximum-files 50000 \
  --maximum-workspace-bytes 2147483648
```

Analysis never downloads or installs software. Native Windows execution returns
SKIP3 with a prominent recovery message; use the declared Linux/WSL runtime.
Missing/forbidden prerequisites skip dependent analysis. FAIL2 admits no complete
result. The resource limits cover the analysis child and retained working files;
terminal error/recovery records remain best effort if recording itself fails.
Only the workflow's own process group is stopped after its explicit wall/file/
byte limit. All available partial files remain.

Read `analysis-status.json` and `WARNINGS.md` first. R handler totals are explicit;
unknown raw/native console totals remain unavailable, never zero. The original
result is `original/complete-result.rds`; the full conditions, RNG state and
original plots remain alongside it. A completed original object is a source
execution result, not a converged SCT, validated biological or reproduced paper
result. Recording failures revoke admission even if the original calculation
finished; inspect `FINALIZATION_ERROR.json` and the console if required status
files could not be written, resolve the named filesystem fault and retry NEW paths.

Requests are trusted RDS lists with `operation`, a complete original `arguments`
list, and `rng=list(kind=RNGkind(), seed=.Random.seed)`. Nothing is coerced or
inferred from a paper. Serialized R objects can contain closures and environments;
they execute with the account's permissions. This workflow is not an OS sandbox.
Complete-object verification requires the selected original R runtime and
`CompareOriginal.R`; unsupported Seurat closures are not flattened into portable
numeric summaries. The independent controls compare all returned slots,
attributes, closures, numeric bits, NA/NaN payloads and RNG objects, plus complete
PNG files. Full original conditions are retained per execution; elapsed-time
messages can differ. No condition-identity or opaque RDS-file byte claim is made.

The five primary paper uses are still awaiting catalog admission. P059 names a
modified scrattch.hicat 1.0.9 implementation, a separate source audit. P096's
deposited script calls legacy `_v3` functions with `reuse.pANN=FALSE`; the literal
paper version `3.0` remains unresolved. This current API does not silently
translate those calls or claim to run that deposited script verbatim. All five
papers, the catalog and the full 250-paper objective remain open beyond these
bounded integration checks.
