# Native Spyrrow communication component

The Java API implements the entire published `spyrrow.commu.basic.cal_cell_communication` calculation on explicit floating expression, coordinates and ligand/receptor lists. It preserves neighbor selection, both interaction directions, four ligand/receptor sum families and complete source axes. **Spyrrow remains a missing integration in the catalog.** The reported `commu.cn.call` entrypoint and both paper-used plotting functions are not admitted by this component; the full project goal remains unfinished.

Read [the exact development validation receipt](spyrrow-communication-development/VALIDATION.json) for the JAR path/SHA256, source bindings and controls. This additive development JAR preserves every parent entry and the canonical executable. Native execution requires Java17+ and no Python installation. Python is used only for the separately captured original-component reference.

From the project root, start JShell from a JDK17+ installation using the JAR path recorded in the receipt:

```sh
jshell --class-path build/literature-review/spyrrow-runtime/communication-build-1/omicsmatic-0.3.7-spyrrow-communication-development.jar
```

Then paste:

```java
import java.util.List;
import org.jlinalg.singlecell.SpyrrowCommunication;

var counts = new SpyrrowCommunication.Counts(
    List.of("sender", "receiver"), List.of("L", "R"),
    new double[][] {{1, 4}, {9, 16}});
var database = new SpyrrowCommunication.Database(List.of("L"), List.of("R"));
var result = SpyrrowCommunication.analyze(
    counts, new double[] {0, 0.5}, new double[] {0, 0},
    database, 1.0, SpyrrowCommunication.Limits.defaults());
System.out.println(result.columns());
System.out.println(result.values()[0][0]); // sender L to receiver R: sqrt(1*16) = 4
```

The example uses arbitrary software-control coordinates and an explicitly chosen distance threshold; it does not infer a unit, normalization or paper parameter. Coordinates are positional vectors aligned to the supplied cell rows. Inputs represent floating DataFrame values: no count normalization, int-arithmetic substitution, species conversion, gene alias repair or database acquisition occurs. Original gene/cell order remains explicit. Data matrices and returned matrices are defensively copied.

The source is the paper-linked [liuyunho/Spyrrow repository](https://github.com/liuyunho/Spyrrow), commit `18cf78611c22bfb5ea7a6373a9e1c0bb5bec9b77`, whose package declares0.1 and carries Apache2.0. Its25 captured source/license files are pinned by Git blob and SHA256. CPython3.11.17 norm/hash/set behavior is bound to immutable commit `e6392eb68a39cef49c9fe6431253050caa17a426`, with original PSF/SipHash notices retained.

**Do not install the unrelated PyPI package just because its name is `spyrrow`.** The inspected PyPI project points to PaulDL-RS/spyrrow, a strip-packing solver, rather than the spatial-bioinformatics source linked by this paper. No PyPI spyrrow package was installed here.

**The original `cn.py` entrypoint is unresolved.** Its body contains unguarded study-specific code and references an undefined `nam` during import. An actual diagnostic loads the complete unchanged cn module body after its basic dependency and observes that NameError before any pickle file can open. The diagnostic uses namespace parents and skips the unrelated root-package initializer; it is not a full-package import test. The repository exposes one cn-history entry, no tags, no releases and only its main branch in the recorded discovery. `cn.call` also reads a pickle database and ignores `group`/`subset`. Neither the default database nor any pickle is loaded, supplied or converted by this work.

To execute the reported full entrypoint, obtain a working, version-identified source/export from its authors and a reviewed data-only database route, then validate the complete original workflow. The P167 role also names `plot_cell_patch` and `plot_RNA_on_cell`; those require their own complete geometry/output controls. The expanded primary-source review is drafted but not applied to the frozen980-role catalog. The reported Python3.10.5 environment and exact paper commit remain unproved.

## Retained source behavior

Pairs retain input row order with `i<j` and strict CPython `math.dist<threshold`. Distinct cells at identical coordinates can pair; self-pairs are excluded. An all-empty pair family raises a clear error matching the original failure rather than fabricating success. The literal source distance norm is ported, including subnormal scaling, compensation and subtraction overflow; a generic `hypot` approximation is not substituted at cutoff boundaries.

Available interaction keys retain first insertion position; later duplicate keys overwrite their value. Unmapped genes are skipped with a database-row ledger. Duplicate expression gene names remain present, and original `list.index` uses the first matching column. Unequal ligand/receptor lists follow source zip truncation, with trailing counts and original input lists retained. No-such-interaction cases preserve a nonempty pair index with zero value columns. Ambiguous `:` gene keys and `|` cell keys, duplicate cell IDs, ragged axes, negative/nonfinite inputs and exceeded complete-family limits are rejected without repair.

Both directional scores and all four aggregate families use original floating multiplication, square root and sequential sums. Finite-input product/sum overflow remains Inf with a warning and no downstream-analysis admission; underflow remains literal zero. The matrix columns use the exact declared Linux little-endian64-bit CPython3.11.17/SipHash13/seed0 set order. The Java port includes native Unicode storage and source probing/resizing, rather than sorting or Java hash order. This explicit interoperation profile does not infer the paper's unknown hash seed or establish Python3.10.5 parity.

The original pair search is quadratic. Explicit cell/gene/database/pair/matrix/operation limits apply to whole families; cancellation and exceeded limits return no partial result. Default capacities are not a cohort-scale performance or OS memory-isolation guarantee.

## Validation and recovery

Twenty-one complete unchanged-source communication cases match all331480 source table cells and every identity/axis on Windows Java21, Windows Java25 and Linux Java21. Each JVM also matches5008 original string hashes, nine complete set groups up to85000 inputs and2006 distance cases. The total per JVM is333478 numeric comparisons and100040 exact identity/state comparisons, without widened tolerances. Fourteen native rejection controls and defensive-copy checks pass. Nine comparison-corruption controls reject changed values, coherent axis swaps, false empty output, repaired Inf, hash/set changes, a one-ULP distance change and signed-zero conflation.

Original no-pair, duplicate-cell-index and colon-gene failures remain preserved. The source body is loaded unchanged with its original imports; no function or code object is replaced. Two missing imports were installed from exact checksummed wheels into a new private overlay, without dependency resolution or shared installation. Complete shared-runtime and overlay inventories match before/after reference execution. Full install/import diagnostics and the failed comparison-test path attempt remain retained.

**SKIP3:** when permitted component dependencies/installation are unavailable or forbidden, skip dependent original-reference validation. **FAIL2:** any source, runtime, input, capacity, precision or comparison failure admits no component reference or partial family. Preserve all originals and diagnostics, correct the named cause and retry NEW paths. A passing component receipt does not admit the broken full entrypoint, database, plots, biological signaling or a paper replay.

**Also read the [confirmed Python hash-seed issue](PYTHON_HASH_SEED_KNOWN_ISSUE.md).** The first-generation CellPhoneDB runner's `python_hash_seed` option is ineffective under its `-I` launch; work requiring that option must wait for a validated correction. The Spyrrow reference uses effective seed0 startup flags and checks them explicitly. This defect is recorded as active follow-up, without altering any earlier source, runtime or output receipt.
