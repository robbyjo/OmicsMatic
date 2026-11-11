# CellChat PPI projection in native Java

This source checkpoint adds a native Java implementation of the prepared-expression PPI projection used by original **CellChat 1.1.3 `projectData`**. It solves the complete network system, preserves expression row/cell order, and leaves genes outside the network unchanged. The separate private installation of original CellChat 1.1.3 is also tested. **This does not complete the six CellChat paper roles or add catalog credit:** totals remain 93 new scoped integrations, 96 including three existing adapters, and 248 missing tools.

**Read `WARNINGS.md` before using any result.** Projection does not automatically change communication inference. An upstream CellChat workflow must explicitly use `raw.use=FALSE` to consume projected expression. P085 reports projection but its effective `raw.use` choice remains unresolved here; this implementation does not guess it. The [earlier original-package RNA workflow](CELLCHAT_WORKFLOW_DEVELOPMENT.md) remains a separate, pinned CellChat 2.2.0 workflow and does not consume this output automatically.

## Run the complete synthetic example

Use Java 17 or newer. The [Java 21 compiler build](cellchat-projection-development/cellchat-projection-javac21.jar) and [Java 25 compiler build](cellchat-projection-development/cellchat-projection-javac25.jar) both target Java 17. They are separate development executables; the canonical released executable is unchanged.

From the repository root in PowerShell:

```powershell
New-Item -ItemType Directory -Force build | Out-Null
java -jar docs/literature/cellchat-projection-development/cellchat-projection-javac21.jar --request docs/literature/cellchat-projection-development/example/request.json
java -jar docs/literature/cellchat-projection-development/cellchat-projection-javac21.jar --verify build/cellchat-projection-example-1
```

The example's three-gene directed network and expression values are synthetic. Gene B is unmeasured but remains in the solve, connecting A and C. `outside` is unchanged. The example is not a biological PPI or a recommended analysis design. Its request writes to `build/cellchat-projection-example-1`; that directory must be new. To repeat the example, edit the request's `out` to a different new directory and retain earlier results.

For your data, copy [request.json](cellchat-projection-development/example/request.json) and change `expression`, `ppi`, `out`, `alpha`, `normalization` and capacities explicitly. Paths resolve relative to the request file. Expression must already be externally log normalized and nonnegative; the command does not normalize counts or perform CellChat database/signaling-gene selection. Supply the prepared signaling expression intended for projection.

Both matrices are complete, plain UTF-8 TSV files with first column `gene_id`. Expression has one named row per gene and one named column per cell. PPI has identical gene names in identical row/column order. Names are literal, unique, untrimmed and cannot contain control characters. Numeric cells must be finite and nonnegative. The command refuses malformed UTF-8, negative/nonfinite values, ragged rows, duplicate names and implicit trimming. PPI row and column sums must all be positive. Negative edge weights and ambiguous duplicate/misaligned axes are outside this adapter's admitted domain, even if an upstream R helper would accept them.

Choose `normalization: "rows"` or `"columns"` explicitly and `0 < alpha < 1`. The original `projectData` rejects both alpha endpoints. In complete PPI gene space, missing expression rows become zero; the command solves `(I - alpha * normalized_PPI) X = (1 - alpha) E`, then replaces only overlapping expression rows. Removing unmeasured network genes before solving changes the model and is not performed.

The implementation uses dense partial-pivot elimination with all cell columns sharing the same factors. It has cubic network-size cost and quadratic network storage, plus complete expression and right-hand-side storage. Increase capacities only after planning the required resources. `working_bytes` and `arithmetic_updates` bound logical estimates; they are not hard OS memory/time quotas. The default API network capacity is 1,024 genes. No prefix, sampled cells or truncated network is admitted when any declared capacity is exceeded.

## Results, warnings and recovery

Require all three: exit 0, `COMPLETED.json` with `result_admitted=true`, and a successful `--verify`. The output retains the exact request and both raw inputs, complete `projected.tsv`, numerical diagnostics, file hashes and prominent warnings. Input files are checked again before admission. Failed attempts retain `FAILED.json` and `WARNINGS.md` when an output directory could be created; completed or provisional outputs are never admitted after a detected failure. A failure before output-directory creation reports its cause and required action on stderr.

`--verify` runs no R code and installs nothing. It checks the complete file population, hashes and capacities, then repeats the entire native numerical projection and diagnostics from the captured inputs. This is portable replay using the current executable, not an independent original-R refit or an authenticity certificate. It does not need the original input paths recorded in the request. SHA-256 establishes byte integrity, not authorship.

On **FAIL2**, skip dependent use, preserve the attempt, correct the named cause and retry a new directory. For a missing Java installation, use a permitted Java 17+ installation and retry; if installation is forbidden, leave analysis skipped. For heap exhaustion or resource termination, preserve partial output, plan a permitted heap and capacities appropriate to the *complete* matrices, and retry a new path. Never fill missing values, repair identifiers or trim the network to force admission. Alpha close to one produces an explicit sensitivity warning: a small backward residual does not guarantee small forward error.

## Use the native API

```java
var result = org.jlinalg.omics.CellChatProjection.project(
    expressionGeneNames, preparedExpression, ppiGeneNames, ppiWeights,
    0.5, org.jlinalg.omics.CellChatProjection.Normalization.ROWS,
    org.jlinalg.omics.CellChatProjection.Limits.defaults());
double[][] projected = result.expression();
```

Arrays are genes by cells; PPI is genes by genes. Caller matrices are not mutated and returned expression arrays are copied. Explicit capacities, residual diagnostics, overlap counts and warnings are available through the API. Cancellation through the Java thread's interrupt flag refuses a complete result. Floating-point decisions near downstream thresholds still require review against the original workflow.

## Original reference and validation

The [validation receipt](cellchat-projection-development/VALIDATION.json) and [complete evidence archive](cellchat-projection-development/controls.zip) retain original-package reference outputs, source snapshots, process logs, complete native attempts and refusals. Seven installed original function bodies/formals are checked against unmodified source at commit `fea1c8ecdea0f393d32513cd00b866f8b4457839` from [sqjin/CellChat](https://github.com/sqjin/CellChat/tree/fea1c8ecdea0f393d32513cd00b866f8b4457839). This identifies the tested source candidate, not a paper's historical commit. Reference execution uses Windows R 4.6.1, not a reconstructed historical R environment.

There are 50 deterministic synthetic complete-matrix families: asymmetric directed networks, both normalizations, one/multiple genes or cells, partial/all/no overlap, zeros, unmeasured intermediate genes and alpha from `1e-8` to `0.999999`. The same families are checked using two compiler builds on Windows Java 21/25 and Linux Java 21. This creates 300 numerical comparisons, not 300 independent datasets. Each compiler/JVM combination also checks the native API and complete-input refusal/rehashed-bundle regressions. Maximum scaled difference from original R is about `1.25e-10`; the declared tolerance is `2e-12` through alpha `0.999` and `5e-9` above it. These are source-consistency controls, not biological validation or a large-PPI performance benchmark.

The original reference preserves four endpoint/zero-row/zero-column refusals. Nineteen invalid-input/schema/capacity protocols and six offline tamper protocols are refused per compiler/JVM combination. The first unsuccessful isolated compile and the earlier reference/encoding attempts remain in the archive with their original outcomes. In the R test harness, UTF-8 marking and raw-byte writing preserve Unicode identifiers under its `C` locale; original CellChat functions are unchanged.

Verify the published evidence without R:

```powershell
python tools/literature/verify_cellchat_projection.py --check-current-source
```

## Private original-package setup

Native Java projection requires no R installation. For further original-package development, `setup_cellchat_editions.py` installs an unchanged source candidate into a **new private** library. This checkpoint actually validates edition **1.1.3**; other accepted edition labels are not claimed installed or workflow validated. The setup requires the earlier pinned source intake, private compiler health, modern dependency setup and permitted Windows R 4.6.1 installation. It records all new downloads, package versions, compilation warnings and pre-existing library inventories. Constructor/native-DLL health does not admit a scientific analysis.

```powershell
python tools/literature/setup_cellchat_editions.py --allow-install --source-label 1.1.3 --out build/literature-review/cellchat-runtime/edition-1.1.3-user-setup-1
```

If an exact prerequisite is missing, unavailable or installation is forbidden, require **SKIP3**, read the setup's `WARNINGS.md`, and skip dependent original-R analysis. Supply the named permitted prerequisite in new private paths and retry new paths; do not patch CellChat, replace its edition or alter shared libraries. The explicit no-install control verifies this disposition. Download permission for source packages remains subject to the operating environment; the script does not install globally.

Original CellChat declares GPL-3. See the [upstream notice](CELLCHAT_UPSTREAM_NOTICE.txt), immutable source and license retained in the archive. The new Java implementation is project code following the documented source behavior. Legacy merge/similarity/rankNet workflows, other source editions, paper-specific comparisons and the remaining CellChat role reviews still require work before catalog promotion. The full 250-paper implementation goal remains active.
