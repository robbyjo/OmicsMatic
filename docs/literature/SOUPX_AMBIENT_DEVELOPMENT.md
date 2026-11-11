# SoupX ambient workflow development

**The execution adapter and count-correction validation are unfinished.** This checkpoint adds native Java ambient-profile arithmetic, non-expression masks, an analytical global Poisson estimate and a closed internal request contract. It does not provide a user-facing ambient RNA analysis command. SoupX remains missing in the credited catalog; totals remain76 new scoped integrations and255 missing tools. The full250-paper goal remains active.

## What is available

`org.jlinalg.singlecell.SoupXAmbient` accepts complete literal count axes and unique sparse integer coordinates. It can verify an explicitly declared raw-subset relationship, select droplets with strict UMI-total endpoints, compute the ambient profile, calculate Poisson/BH non-expression masks at the cell or supplied-cluster level, and calculate the analytical global Poisson intercept MLE from explicit gene sets and a mask. It does not infer biologically non-expressing genes or cells.

**The analytical estimate is not R's iterative fit.** The captured original `calculateContaminationFraction` fits a Poisson GLM with default epsilon1e-8. All six captured global fits report convergence, but the original rho differs from the analytical ratio by7.198298485056043e-10 for the two estimated-mask cases and2.589336106639806e-10 for the supplied-mask case in each edition. Saved original objects show a small nonzero score after iteration. The Java method matches the analytical limit computed independently from the original response and log-exposure vectors. Both original and analytical estimates remain retained. Future upstream execution must preserve its original GLM estimate; the native ratio must not silently replace it. Confidence intervals, GLM iteration and cell-specific STAN are not implemented by this Java arithmetic.

The [executed example](../../examples/soupx-ambient-core/Example.java) selects two synthetic empty droplets, returns fractions0.5/0.5 and analytical rho0.1. It performs no count correction. With a JAR built from current source containing this API:

```powershell
javac --release 17 -cp /path/to/current-source-build.jar -d /new/example-classes examples/soupx-ambient-core/Example.java
java -cp "/new/example-classes;/path/to/current-source-build.jar" Example
```

On Linux/macOS use `:` between classpath entries. Released0.3.7 JARs lack these new classes. In the existing development workspace, `build_soupx_ambient_core.py` produces a new additive development JAR using the previously validated marker artifact; it is not a fresh-clone runtime installer. Native arithmetic needs Java and installs nothing.

## Original controls and API differences

The original R references executed55 ambient-control cases:28 in SoupX1.6.2 and27 in the recovered1.3.0 source candidate. Both use identical unmodified1.6.2 toyData counts and labels under R4.6.1/Matrix1.7-5. These226-gene/62-cell toy data are package test data, not a biological validation cohort or a reconstruction of any paper. Original source bodies/formals, input identity, warnings and RDS returns remain retained; no original function was patched. Before/after inventories show unchanged preexisting private/shared libraries.

Controls cover strict droplet ranges, fixed rho, each original correction method (`subtraction`, `soupOnly`, `multinomial`), cell-level and supplied-cluster correction, integer rounding with declared seeds, manual estimated/supplied masks, automatic estimators, high-rho behavior, no accepted cells, gene-order mismatch and an empty droplet selection. Running these upstream controls does not mean the Java execution adapter is complete.

**Version1.3.0 autoEstCont returns a data frame.** Its density estimate is not an updated SoupChannel. Version1.6.2 returns a channel with a gamma-based estimate. The internal contract uses distinct estimator names and parameters and requires explicit `apply_density_mode=true` before applying the historical density estimate to correction. Current `forceAccept` has no historical counterpart. The references preserve1.6.2's rejection of rho0.9 without forceAccept and the historical acceptance of that value.

**Literal first cluster `FALSE` changes the original correction branch.** Both editions' original `adjustCounts` test the first cluster against FALSE. The controls retain the resulting cell-level behavior, rather than renaming labels. A future adapter must compare the requested clustering scope with the branch actually executed and refuse a mismatch with clear recovery instructions.

**Empty droplet selections return nonfinite profiles in the source.** Native profile arithmetic likewise retains zero counts and NaN fractions as diagnostics. A nonfinite/zero-total profile cannot be used for native non-expression or Poisson estimation. Never convert these NaNs to zero or silently change the droplet range. Current source rejects raw/filtered gene-order mismatch; the historical constructor accepts it. The declared raw-subset check independently refuses mismatched axes/counts without reordering or repairing them.

The closed internal `SoupXWorkflowRequest` contract separates fixed, manual global Poisson, current automatic gamma and historical automatic density modes. It requires raw integer units, explicit matrix/cluster/count-relationship declarations, ranges, seeds, optional correction versus estimation-only, exact runtime/source paths and resource bounds. Manual marker masks remain caller supplied or explicitly estimated; no ALB/Csf1r cell-type assumptions are inferred. P145's reported automatic overrides fit the current contract; P104's validation role must remain distinct from applying corrected counts. See the [seven source-role drafts and marker guide](TFIDF_MARKERS_DEVELOPMENT.md).

## Warnings, failures and remaining work

**Do not use the internal request parser as an execution command.** Native/source controls are development evidence only. Java input capture, source/runtime admission, owned process supervision, complete output auditing and atomic user-result admission remain to be implemented and tested. No ambient result is currently admitted by a Java CLI.

If an exact upstream runtime cannot be installed or executed in the environment, preserve a prominent SKIP3 report, state the missing dependency and required action, and skip dependent analysis. Do not silently substitute a release, patch an upstream function, reuse an incompatible API or replace failed output. Native arithmetic remains independent of that runtime. The original captured1.3.0 GitHub commit is a source candidate, not proof of a paper commit or historical R environment. Original source/license provenance is retained in the earlier marker evidence.

The [ambient-core evidence](soupx-ambient-core-development/VALIDATION.json) records28 checks on each of Windows Java21/25 and Linux Java21, including all1,808 profile-gene comparisons,372 manual mask decisions, analytical-versus-original Poisson evidence, input/capacity refusals and10 version-specific request-contract tests. Failed preparation, serialization, compile and invalid estimator-equivalence assumptions remain retained. The final tests distinguish analytical and iterative estimates with tighter arithmetic comparisons; they do not widen a tolerance to claim source-fit identity.

```powershell
python tools/literature/soupx_ambient_core_evidence.py
```

The portable checker reads saved evidence without installing or running R. It does not authenticate execution or establish biological validity. The canonical executable, parent JAR entries, prior proofs, existing libraries and unrelated local changes remain preserved. Cell Ranger remains excluded.
