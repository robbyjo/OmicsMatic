# PLINK component evidence and exact validation fixtures

**Current component checks passed with explicit frozen fixtures. Cumulative
catalog admission is still unfinished; this packet awards no family credit.**
Read the [checkpoint](../PLINK_COMPONENT_CHECKPOINT_2026-11-19.md) and
[qualification](QUALIFICATION.json). The current tested JAR is 194,474,943
bytes, SHA-256
`9faafae72255787ffc836207ae422a230ea63c8a1473d2b7e56908e409deb1d9`.
Its source/workflow baseline is main commit
`2f4aafb008e7b1a4928446bec7b0acc0df250c48`; the additional fixture/audit tools
are pinned separately. No production scientific Java code was changed.

## Warnings and required actions

**The four old GLM/pruning/PCA/CI verifier mains fail against the current
working-tree fixtures.** Forty byte-pinned text files have checkout line
endings different from their original provenance; the GLM README also changed.
Their first failed checks and error consoles are retained. None is relabeled
as a successful working-tree verification, and no old expected hash or verifier
source is refreshed.

Use the explicit fixture project below. Its 125 files include the unchanged
producers, public synthetic inputs/references and their original provenance.
Forty restorations recover only CRLF bytes and one restores the exact earlier
validated README, identified by its old SHA-256. [FIXTURES.json](FIXTURES.json)
records every before/after pin and origin. Current working files are untouched;
no scientific data fields are changed. This is a validation input project,
not a checkout or an overlay of production/catalog sources.

**SKIP3:** missing exact fixture/source/runtime prerequisites skip the dependent
audit or analysis. Restore the named permitted bytes, or leave the skip visible.
Original execution here uses the pinned Windows x86_64 PLINK profile. Native
scoring needs Java only; original-reference checks additionally require both
exact PLINK programs. Downloads/installations remain subject to the environment.

**FAIL2:** changed/corrupt evidence, an invalid input/model, nonfinite or
unreportable results, or resource/process failures admit no complete result.
Keep inputs, partials and errors, correct the cause and retry NEW outputs.
Do not alter IDs, alleles, source inequalities, statistics or algorithms to
make a check pass. Missing fixtures return 3; invalid fixtures or existing
output return 2. The new restorer preserves an existing output directory.

Standalone KING 1.4, legacy PLINK 1.07 CNV operations, dosage/X/nonhuman and
other unsupported paper roles remain gaps. The known original KING 2.0
joint-call discrepancy remains a required scientific rejection when triggered.
Two unrelated historical full Seurat marker-verifier mains remain SKIP3 for
their missing exact historical canonical JAR; follow the recovery instructions
in the [Seurat checkpoint](../SEURAT_CATALOG_CHECKPOINT_2026-11-19.md).

## Checks actually performed

| Component | Fresh check on Windows Java 21 |
|---|---|
| Typed original additive GLM | 20 workflows, 1,920 rows, 960 frozen original-report rows, 956 independent OLS comparisons, five controls; ordinary/fallback/always-Firth separated cases included |
| Original autosomal QC | 25 workflows, six controls, 263,085 genotype cells and 1,908 independently enumerated HWE values; exact and mid-P HWE |
| Original LD pruning | Seven workflows and 12 controls with original masks and independent BED call checks |
| Original genetic PCA | 11 workflows and 12 controls; identities, GRM/eigen/weight/residual checks, exact/randomized profiles |
| Original reported CI/SE import | 30 typed workflows plus 2,880 API rows and 2,880 typed rows; seven controls; six suppressed intervals in each reader |
| Native supplied-weight scoring | Six profiles, 12 fresh original PLINK calls, 2,007 original field comparisons, 670 native rows, 28 Java calls and 22 controls |
| Native KING evidence | Unchanged source/archive/count/binary audit passed |
| Standalone KING 2.3.2 saved results | 26 Java saved-bundle verifications passed; original KING was not rerun |
| Historical KING saved results/cutoffs | 58 Java checks passed, including deliberate promotion attempts rejected by the scientific guard; original KING/R were not rerun |

GLM/pruning/PCA/CI used the declared frozen fixture project. QC and native
scoring ran directly from the active checkout. The new relatedness workflow's
separate [qualification](../plink-relatedness-development/README.md) remains
unchanged. Original PLINK execution in this component campaign was Windows
only. GLM/Firth checks validate original report fidelity and reportability;
there is no new independent Firth estimator. Nothing here proves historical
author-runtime equivalence, whole-tool parity or complete paper reproduction.

`evidence.zip` retains 6,615 files: actual producer records/consoles, synthetic
inputs, raw original/canonical output, qualified source snapshots and the
initial failures. It excludes original executable/runtime copies. A packaging
attempt refused a copied CI runtime before producing its evidence archive;
that failed packaging record and partial fixture packet remain local.

## Read back or reproduce

Python 3.11+ is sufficient for frozen packet checking and restoration. These
commands do not execute PLINK, KING, R or the scientific JAR:

```powershell
python -B tools/literature/plink_catalog/verify_components.py
python -B tools/literature/plink_catalog/fixture_project.py --out build/plink-component-fixtures-NEW
python -B tools/literature/plink_catalog/test_fixture_project.py
python -B tools/literature/plink_catalog/test_components.py
```

The readback checks 100 current source pins, 20 retained predecessor artifacts,
every evidence member, actual producer/reader identities, component populations,
all initial failures, the frozen-fixture successes and the known KING rejection
scope. It does not rerun numerical inference. Sixteen fixture/evidence controls
and frozen readback/restoration were checked separately on Windows and Linux;
see [READBACK.json](READBACK.json). Full original statistical checks require
the permitted Windows runtime and validation dependencies below.

To rerun, first supply the exact saved JAR or follow the
[source rebuild instructions](../plink-relatedness-development/README.md).
An independently rebuilt container with a different hash is a new candidate:
verify its required payload identity and retain separate fresh reports. Do not
rewrite this saved qualification to call it the same file. With NumPy and
SciPy available in a permitted private Python environment, for example:

```powershell
$fixtureProject = (Resolve-Path 'build/plink-component-fixtures-NEW/view/project').Path
$plinkJar = (Resolve-Path 'build/plink-relatedness-development-1/build-6/omicsmatic-0.4.0.jar').Path
$plinkHome = (Resolve-Path 'build/plink-relatedness-development-1/runtime-2').Path
$java = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
python -B "$fixtureProject/tools/literature/verify_plink_upstream.py" `
  --jar $plinkJar --home $plinkHome --java $java --work build/plink-glm-rerun-NEW
python -B "$fixtureProject/tools/literature/verify_plink_prune.py" `
  --jar $plinkJar --home $plinkHome --java $java --work build/plink-prune-rerun-NEW
python -B "$fixtureProject/tools/literature/verify_plink_pca.py" `
  --jar $plinkJar --home $plinkHome --java $java --out build/plink-pca-rerun-NEW
python -B "$fixtureProject/tools/literature/verify_plink_ci_upstream.py" `
  --jar $plinkJar --home $plinkHome --java $java `
  --javac 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe' `
  --work build/plink-ci-rerun-NEW
```

Use absolute existing JAR/runtime/JVM paths and new output paths. Paths above
are this machine's private retained locations; GitHub does not contain the
large scientific JAR or original runtimes. Missing SciPy was installed into a
new private workspace target for this campaign; it did not modify the host
Python installation. NumPy 2.3.5 and SciPy 1.18.1 were used for the GLM check.
Retain the original installation report and private run directories when
transferring machines. Both literature HTML pages remain withheld from Pages;
this packet creates no release and does not apply the catalog proposal.
