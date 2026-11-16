# Original PoPS workflows: development checkpoint

**WARNING: this is unfinished development code. The original-source adapter has
not completed its full qualification and adds no catalog integration credit.**
Use the [qualified native prepared-input workflow](POPS_WORKFLOWS.md) for its
documented PoPS 0.2 ridge/OLS scope. The original adapter is an explicit separate
command, `pops-original-tools`; it never substitutes a native method after an
original-program failure. Neither command is in the released v0.4.0 executable.
Build current source to inspect this checkpoint.

The new code captures inputs, records the selected upstream edition and runtime,
runs unchanged original programs, retains their raw reports and warnings, checks
prediction values independently from captured features and source coefficients,
and supports genuine recomputation into a new output directory. Its proposed
profiles cover PoPS 0.1 selection and chromosome-held-out prediction, PoPS 0.2
ridge/OLS/LassoCV fitting, and original feature munging. Availability in source
does not establish that every profile or failure path has been qualified.

The separate original-program reference runs cover all 22 autosomes for PoPS
0.1, two synthetic LassoCV inputs and four explicit feature-munging missing-value
policies. These reference executions exercise the upstream programs directly;
they do not qualify the complete Java adapter. See the
[publication checkpoint](pops-original-development/STATUS.json) for the exact
checks run before this source snapshot was pushed. Runtime binaries, private
execution bundles and reference matrices are not included in that small record.

The publication checks passed 238 regression tests with no failures, errors or
skips, the executable build and the website check. One complete synthetic 0.2
LassoCV adapter run and its genuine replay passed with 600 target genes, 603
prediction genes and 149 selected features from 150 inputs. Its three scientific
reports were byte-identical to the direct original-program reference. Four
negative controls covered unsupported Windows execution, invalid edition,
duplicate request properties and preservation of an existing output directory.
This is a bounded checkpoint, not full adapter qualification.

## Warnings and required actions

- Original execution requires Java inside Linux x86-64, including permitted WSL,
  with `prlimit`, `setpriv` and a separately installed and adopted runtime.
  Running Java directly on Windows skips the dependent analysis with exit 3.
- Missing or forbidden prerequisites produce **SKIP3**. Install the named
  prerequisites only where permitted, then retry in a new output directory;
  otherwise leave the analysis skipped. Analysis commands do not download or
  install software.
- Other failures produce **FAIL2**, with no accepted complete result. Retain
  `FAILURE.txt`, `WARNINGS.md`, worker consoles, `runtime-logs` and raw outputs.
  Correct the reported cause before retrying into a new directory. Do not
  overwrite evidence or refresh saved fingerprints to force acceptance.
- An explicit source `ConvergenceWarning` rejects the fit even if coefficients
  are finite. Do not change iterations, random seeds, missing values, identifiers
  or covariance values silently to obtain a result.
- Original 0.1 has different covariance and regression semantics from 0.2. Its
  selected source fails with exactly one selected feature. Empty selection can
  produce a control-only prediction when control features are supplied; that is
  not evidence that a new predictive feature was selected.
- Feature munging requires an explicit `raise`, `ignore`, `mean` or `zero`
  missing-value policy. `mean` can retain an entirely missing feature as NaN.
  Munging and feature selection are preparation tasks, not fitted models.
- Only root `COMPLETED.properties` admits a complete requested task under the
  development contract. A worker receipt or finite partial output alone does
  not. The adapter's overall qualification remains pending even after a smoke
  case completes.

## Inspect and prepare the development runtime

From the repository root, build the current executable and read its full notice:

```powershell
.\gradlew.bat executableJar --no-daemon --max-workers=2 --console=plain
java -jar build/cli/omicsmatic-0.4.0.jar pops-original-tools --help
```

The setup scripts are in `tools/literature/pops_original`. Their `--help` output
describes the required paths. The installer requires a permitted Pixi executable
with SHA-256
`50571451e96f4a82cf675cd83baaf66fff9b718f0aaf153900b9faf4c29bd401`;
an arbitrary Pixi version is not accepted. Inside Linux, provide that executable
and use new directories, for example:

```bash
python3 -B tools/literature/pops_original/install_runtime.py \
  --pixi /absolute/path/to/verified/pixi \
  --out /absolute/path/to/new-pops-runtime \
  --cache-dir /absolute/path/to/pops-package-cache
python3 -B tools/literature/pops_original/adopt_runtime.py \
  --installed /absolute/path/to/new-pops-runtime \
  --out /absolute/path/to/new-pops-adoption
```

The selected dependencies are Python 3.8.20, NumPy 1.19.5, pandas 1.0.5, SciPy
1.5.2, scikit-learn 0.24.2 and statsmodels 0.12.2. Preserve the generated lock,
license files and adoption records. Setup readiness is not a scientific result.
Adoption records the environment, selected source and resolved shared-library
files; it is not a complete OS image or a claim of executable authenticity.

Remaining work includes complete adapter controls and replay coverage for both
editions, full original munging, resource and tamper failures, a real source
convergence failure, and paper-role catalog admission. The 250-paper project
remains in progress. Caller genome-build and feature-source labels do not prove
author settings, biological validity or whole-paper reproduction.
