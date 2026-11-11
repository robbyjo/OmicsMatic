# CellPhoneDB prepared numerical components

**Scope: a native Java numerical API, validated against published CellPhoneDB 5.0.1. CellPhoneDB remains missing from the implemented-tool count.** Database loading, gene mapping, full methods 1/2/3, scoring, CellSign, subsampling, an upstream execution adapter and paper reproduction remain open. The canonical executable and earlier evidence are unchanged.

**SKIP3: when a required original runtime cannot be installed, downloaded or used in the operating environment, skip its dependent analysis. FAIL2: invalid inputs, source/runtime drift, capacity failures or a failed comparison admit no partial batch.** Preserve inputs, failed directories, complete logs and warnings. Correct the named cause and retry into a **new** directory. Never change a frozen source hash, silently round input, patch the original package or widen a comparison tolerance to obtain a pass. Successful installation alone admits no scientific analysis.

## Use the Java API

`org.jlinalg.singlecell.CellPhoneDbStatistics` depends only on the Java standard library. Compile the source into a new directory with a JDK, then open JShell. Java 21 and 25 were exercised; the development artifact was compiled with `--release 17`, but Java 17 execution was not tested.

```powershell
javac -d build/cpdb-native-example-classes src/main/java/org/jlinalg/singlecell/CellPhoneDbStatistics.java
jshell --class-path build/cpdb-native-example-classes
```

Paste this toy **already prepared** matrix into JShell:

```java
import java.util.*;
import org.jlinalg.singlecell.CellPhoneDbStatistics;

var prepared = new CellPhoneDbStatistics.Prepared(
    List.of(101L, 102L), List.of("cell-1", "cell-2"), List.of("A", "B"),
    new double[][] {{1, 2}, {3, 4}}, List.of(),
    CellPhoneDbStatistics.Precision.FLOAT64);
var result = CellPhoneDbStatistics.analyze(
    prepared, List.of(new CellPhoneDbStatistics.Interaction("toy", 101, 102)),
    List.of(), 0.1, "|", List.of(), null, null,
    CellPhoneDbStatistics.Limits.defaults());
result.columns();
Arrays.deepToString(result.means());
result.warnings();
```

The pair columns are `[A|A, A|B, B|A, B|B]`; the means are `[[2.0, 2.5, 2.5, 3.0]]`. These numeric IDs and the interaction are toy declarations, **not mapped genes, a curated ligand–receptor assignment, a significance result or evidence of biological communication**. No permutations means `pvalues()` and `statistical()` are `null`. `nonStatistical()` retains expression-gated means and their ranks; it does not provide a statistical test.

`Prepared` requires unique protein and cell IDs, cell types in the exact cell order, a rectangular finite nonnegative protein-by-cell matrix and complete supplied complex memberships. No identifier, missing-value, normalization or count transformation is inferred. Caller-declared multidata IDs are numeric identities; raw gene-to-protein mapping belongs to a separate, unfinished stage. Arrays are copied on construction and access.

Choose precision explicitly. `FLOAT64` uses sequential double accumulation. `FLOAT32` retains original float accumulation, rounded cluster means and float interaction arithmetic. Its input values must **already** equal their actual source float32 values represented as doubles; otherwise the API rejects them. Preserve raw input and a separate explicit source-cast record if a prior preparation stage casts data. The API does not silently perform this input conversion. The five-argument `Prepared` constructor defaults to `FLOAT64`.

## Source behavior that affects interpretation

- Cell types sort by Unicode code point; literal `NA`, Unicode labels, spaces and quotes are data. Control characters and unpaired surrogates are rejected by this prepared profile. Original cell and feature order remain explicit.
- Cluster means average all cells of the type, including zeros. Fractions count values strictly greater than zero. A complex uses the **minimum** of subunit means and the minimum subunit fraction; repeated declared subunits remain present.
- The expression gate is **strictly greater than** `threshold` for both partners. A fraction exactly `0.1` fails a threshold of `0.1`.
- Interaction means use the source's literal masked arithmetic `(x>0)*(y>0)*(x+y)/2`. Finite inputs can overflow and produce `Inf` or `NaN`, including `0*Inf`. Those raw states and a warning are retained for audit. **They do not admit a downstream analysis.** Resolve the input precision/range problem in a separately recorded preparation or stop the dependent analysis; never replace the states with zero.
- Empty microenvironments request all directed pairs, including self pairs. Otherwise only pairs sharing a supplied categorical microenvironment are enumerated, retaining first-seen environment/row order and removing exact duplicate pairs. This is not a distance, contact or spatial-diffusion model. Pair identities remain separate when encoded headers collide; read `pairs()` alongside `columns()`.
- Supply complete permutation labels in their actual original order. Each must preserve the cell-type multiset. Java implements the supplied numerical kernels, **not NumPy's random generator**. Every permutation mean and MSB-first packed comparison byte is retained. The tail is **strictly greater than** the observed mean, divided by iterations, with no plus-one correction. Failed expression gates and observed zero means receive P=1. This differs from inclusive tail wording in the [official documentation](https://cellphonedb.readthedocs.io/en/latest/RESULTS-DOCUMENTATION.html) and P100; that discrepancy remains explicit.
- A nonzero P cutoff masks P values above the cutoff. The original zero-cutoff argument is falsy and instead masks cells whose supplied score equals zero. This surprising behavior is preserved with a warning. It is **not** an ordinary P≤0 filter.
- Ranks count non-NaN means divided by the number of pair columns, rounding to three decimal places with ties to even. Infinity counts as nonmissing; NaN does not.
- Supplied DEG flags do not fit differential expression. Complex DEG flags use the **maximum** across mapped subunits, with complex rows sorted by numeric ID. A relevant interaction must pass the expression gate and have a DEG-marked endpoint in its respective cell type. Duplicate marks remain in original inputs; out-of-axis marks follow the original update-intersection behavior and appear in an ignored-mark ledger.

**Two original errors are retained, not patched.** Under the bound pandas 2.2.3 runtime, `build_degs_matrix` fails with `TypeError` for an empty complex-composition table; Java rejects that DEG request and returns no result. Original single-thread `shuffled_analysis` with one iteration fails with `ZeroDivisionError` in its progress calculation. The full original-driver control uses 100 iterations. Java's supplied-permutation kernel does not invoke that progress driver and must not be described as a fix or a port of it.

The `Limits` record bounds cells, features, pair and interaction families, permutations and logical operations. Complete matrix/permutation capacity is checked before allocation; interruption also rejects the computation. Raising limits can increase retained-memory requirements considerably because results and accessors make defensive copies. No partial `Result` is returned on a capacity or validation failure.

## Evidence and reproducibility

Run the retained-evidence verifier without installing CellPhoneDB:

```powershell
python -B tools/literature/cellphonedb_statistics_evidence.py
python -B tools/literature/test_cellphonedb_statistics_comparison.py
```

The versioned evidence is `cellphonedb-statistics-development/VALIDATION.json` and its content-addressed `controls.zip`. It retains the complete published 5.0.1 wheel/source members and actual MIT notice, exact input/output matrices, every supplied permutation, original RNG replay proof, source profiles, owned-process receipts, unchanged complete runtime inventories, installation/compiler/import diagnostics and failed attempts. Hashes establish integrity, **not source authenticity or a digital signature**. Dependency archives and the large development JAR remain in local build storage; their full identities are retained rather than redistributing a complete installed environment.

All 13 complete cases pass exact comparisons on Windows Java 21, Windows Java 25 and Linux Java 21: **36,729 numeric values and 1,808 identity/state values per JVM**, with no numeric tolerance, plus 20 expected rejections and defensive-copy checks. The independent audit reconstructs 163 cluster cells, 881 interaction cells, 28,184 permutation cells, 3,560 packed bytes, 777 P-value cells, 259 rank rows and 90 DEG cells. It checks one encoded-header collision and seven observed nonfinite states. The unchanged original 100-iteration driver and a replay through unchanged original helper functions produce identical packed arrays and final NumPy RNG states. These are tiny numerical controls, not biological datasets or paper reconstructions.

The separate private Linux reference installed 43 exact cached Conda packages and an explicitly pinned Python wheel closure, including CellPhoneDB 5.0.1, NumPy 1.26.4, pandas 2.2.3, numpy-groupies 0.11.3 and numba 0.58.1. Original installed package members match the published wheel; complete before/after inventories contain 27,320 file/link and 2,410 directory records. The original module's global suppression of `UserWarning` is preserved; ignored messages cannot be claimed captured. Emitted runtime warnings and pip's root-user warning remain in full stderr. Setup and analysis use private caches, including the NumBa cache, and verified owned process groups. No shared runtime or canonical executable changed.

The additive development artifact is recorded in `VALIDATION.json`; every entry from the previously validated LeafCutter workflow artifact is byte-identical. Compilation alone carries no implementation credit. `generate_cellphonedb_statistics_fixtures.py`, `collect_cellphonedb_statistics_linux.py`, `build_cellphonedb_statistics.py` and `validate_cellphonedb_statistics.py` retain exact new-directory attempt boundaries. The reference installer is currently a **developer evidence workflow tied to the prior LeafCutter dependency cache**, not a portable public CellPhoneDB installer or analysis adapter.

## Paper roles and remaining implementation

Twelve primary-bound roles are saved as **unapplied drafts**, covering seven CellPhoneDB observations plus five newly identified tools/resources: OmniPath, CCplotR, COMMOT, iTALK and Spyrrow. Their source intake rechecked all 250 XML/DOI identities and preserved the 966 published reviews. The effective published catalog is unchanged: **84 newly implemented scoped tool integrations; 87 including three existing adapters; 247 still missing**. A native CellPhoneDB subcomponent does not increment those counts. The five draft registry additions are not yet effective catalog definitions.

The roles preserve distinctions between a standalone statistical/DEG workflow, Squidpy integration, a neural-GPCR resource module and a pooled interaction database used by another program. P008 reports software 5.1, which remains unresolved in the captured official distribution list; P100 reports 5.0.0; P070 reports 2.1.4; P167 reports a version-2.0 **database**, not a proven software edition. None is silently replaced by the validated 5.0.1 component. The captured 5.0.0/2.1.4 distributions declare MIT in metadata but lack a bundled license body; their locally retained source archives are not bundled into this public component evidence. A curated database's terms are separate from the software MIT license; no database has been admitted here.

Next work remains the full typed upstream adapter, database/version/license binding, original raw-input mapping and filtering audits, methods 1/2/3 and their complete outputs, meaningful failure/skip controls, and application of the source-role drafts through the frozen catalog chain. CellSign/scoring/subsampling and unresolved historic paper settings must keep their separate status. Cell Ranger remains explicitly excluded at the user's request. No release, website deployment, full-paper completion or whole-goal completion is claimed.
