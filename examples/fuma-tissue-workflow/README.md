# Supplied tissue gene-property example

Run original MAGMA 1.10 positive tissue-property models while conditioning on a
supplied `Average` column. The archive includes a sealed synthetic 201-gene
analysis, synthetic 54- and 30-tissue matrices, long labels, missingness examples,
and complete original coefficient tables. It includes no biological GTEx data,
upstream program, upstream source or manual.

**DEPENDENCY UNAVAILABLE OR INSTALLATION FORBIDDEN: SKIP THESE FITS.** Fitting
requires Windows x86_64, Java 17+ and a privately admitted unmodified MAGMA 1.10
home. The current adapter has no Linux fitting profile. Exit **3** means skipped;
the warning names the cause and required recovery. Install only where permitted.
Analysis never installs software. Invalid inputs or failed models exit **2**;
retain `partial/` and logs as diagnostics, correct the named cause and use a new
output directory. Read `WARNINGS.md` even after exit 0.

The local development JAR is
`build/literature-review/fuma-tissue-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`b69bdbc6465b425b72055c1ece562e3554985b8e33a289269f02317a5fd5ce8a`.
This functionality is not claimed for the published v0.3.7 JAR.

From the repository root, use Python 3 and new directories:

```powershell
# Optional setup, only where installation and upstream terms permit it.
python tools/literature/install_magma.py --allow-download --environment build/omics-magma
python examples/fuma-tissue-workflow/prepare_inputs.py --out build/tissue-example-NEW --home build/omics-magma
$jar = 'build/literature-review/fuma-tissue-candidate-1/jlinalg-0.3.7.jar'
java -jar $jar magma-tools --method properties --build GRCh38 --home build/omics-magma --gene-bundle build/tissue-example-NEW/gene-bundle --properties build/tissue-example-NEW/properties/detailed54.tsv --property-direction positive --condition-hide Average --missing-values error --max-missing 0 --seed 42 --out build/tissue-example-NEW/detailed-result
```

Reuse an already admitted home without downloading. If setup cannot be permitted,
keep dependent fitting skipped and run on a permitted supported host. Do not
replace the binary inside an admitted home. Use ASCII example paths; other path
encodings are not certified by this example.

To execute and audit all seven recipes, choose another new directory:

```powershell
python tools/literature/verify_fuma_tissue_workflow.py --jar build/literature-review/fuma-tissue-candidate-1/jlinalg-0.3.7.jar --home build/omics-magma --work build/tissue-verification-NEW
```

The verifier uses the packaged JAR, checks every reference coefficient, effective
`NGENES`, exact property bytes, complete populations and retained original call
identities. It writes `verification.json` and preserves logs on failure or skip.
`recipes.json` describes direct `magma-tools` arguments for the verifier; it is
not a `magma-tools` options-file interface. `mean` and `drop` explicitly use a
0.2 missingness limit. The original mean example has effective counts 200/201;
the drop example has 198. Those denominators are preserved.

The prepared `parent.json` runs the 54-tissue model through the typed FUMA parent:

```powershell
java -jar $jar fuma-tools --workflow-options-file build/tissue-example-NEW/parent.json
```

The optional prepared `marginal.json` uses the existing supplied-resource engine
with conditional stages disabled, plus native Java BH adjustment:

```powershell
java -jar $jar fuma-tools --celltype-options-file build/tissue-example-NEW/marginal.json
```

Here the `Cell_type` output column contains supplied synthetic tissue labels.
`step1.tsv` retains raw `P`, per-dataset `P.adj.pds` (families 54 and 30) and pooled
`P.adj` (family 84). All rows are retained. These are explicit example choices;
choose a scientifically justified correction family for real work. No single-cell
preprocessing is performed.

**Average is supplied, never computed by these commands.** Hidden conditioning
keeps it in upstream models and omits its coefficients upstream. Ordinary
`--condition` coefficients may still appear per model. Raw direct results are
not FDR adjusted. `--correct` selects technical covariates, not multiple-testing
correction. See the [behavior and input guide](../../docs/literature/omics-2023-2026/FUMA_TISSUE_WORKFLOWS.md)
and [validation record](../../docs/literature/omics-2023-2026/fuma-tissue-validation.json).
Broader FUMA remains unfinished; Cell Ranger remains excluded.
