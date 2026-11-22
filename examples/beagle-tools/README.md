# Beagle phasing and reference imputation

Run from the repository root with Java 17 or newer. These commands select the
validated development candidate, SHA-256
`5ed54e44acc9d50bb5af240418a5497b9c235506ecc45f318bad9168bd867cac`.
The older canonical a104 executable is unchanged and lacks this command; no new
full-project gate or release is claimed. Fresh source builds write `build/cli/`.
First prepare the private,
verified upstream runtime where installation/downloads are permitted:

```powershell
$beagleJava = (Get-Command java -CommandType Application).Source
python tools/literature/install_beagle.py --java $beagleJava --allow-install --allow-download
java -jar build/literature-review/beagle-candidate-1/jlinalg-0.3.7.jar beagle-tools --options-file examples/beagle-tools/recipes/5.4-29Oct24-impute.json --home build/omics-beagle-official
```

The installer supports offline reuse. Installation and download permissions are
separate flags. Analysis never installs. Missing, changed or forbidden dependencies
skip with exit 3 and a prominent `WARNINGS.md` containing the required action.
Input, process or validation failures return 2; read the retained logs and rerun
into a new output directory. Existing directories and changed runtimes are preserved.

These twenty recipes cover four exact profiles and five tasks per profile:
phasing without a panel, reference-assisted phasing, reference imputation,
imputation from partially phased targets, and complete prephased targets. Override
`--out` when repeating a recipe. Direct flags override the UTF-8 JSON recipe.

All data are synthetic: 24 targets, 32 reference samples, 300 reference markers,
and one deliberate target-only marker. The map, Ne, iterations, error probability,
state counts, seed and minimum observed markers are **test settings**. Choose and
justify those values, reference provenance, genome assembly and ploidy for real data.

**Read `analysis-status.json`, `WARNINGS.md`, `session.yaml`, `variant-ledger.tsv`
and `sample-ledger.tsv` before using the output.** With a panel, the deliberate
target-only marker is excluded under an explicit warning policy. Modern Beagle
can rephase partially missing prephased input; those two recipes explicitly allow
rephasing. The other recipes require preservation of provided phase. Do not derive
GT from the largest rounded GP.

The detailed [workflow guide](../../docs/literature/omics-2023-2026/BEAGLE_WORKFLOWS.md)
explains inputs, version-specific options, outputs, bounds and recovery. The
[paper review](../../docs/literature/BEAGLE_SOURCE_REVIEW.md) separates the nine
observed roles from the supported analysis scope.

To check every recipe against the saved original outputs, without downloading:

```powershell
python tools/literature/verify_beagle_workflows.py --jar build/literature-review/beagle-candidate-1/jlinalg-0.3.7.jar --home build/omics-beagle-official --java $beagleJava --out build/literature-review/beagle-example-verification
```

The verifier also runs Unicode identifier/path checks and twelve failure controls
per supplied `--java`. It checks observed genotypes, exact original GT/DS/AP/GP,
Decimal probability identities and every sample/marker decision. Synthetic
agreement does not establish biological accuracy or reproduce an entire paper.
