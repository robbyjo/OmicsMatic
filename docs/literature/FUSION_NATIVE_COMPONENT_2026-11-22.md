# Native FUSION component and next workflow work

Native Java now provides FUSION allele-QC flags and association for a prepared,
matched SNP set with one selected prediction model. The normal build passes
**45 focused tests**. Original-source references cover **6,561 allele cases and
136 prepared associations: 118 complete and 18 no-results**. The actual executable
JAR passes the same cases on Windows Java 21, Windows Java 25 and Linux Java 21.
All **22,832 preceding JAR payloads remain byte-identical**, with eight new class
and license payloads. No existing scientific implementation was replaced.

**This is a library component; the full FUSION workflow adapter is unfinished.**
It adds **zero catalog family credit or canonical reviews**. Counts remain 150
scoped families (27 native +123 upstream), 1,673 reviewed/1,050 pending uses. The
full 250-paper objective remains unfinished. The source census and pending
P212 cohort-homonym/P108 baseline corrections remain separate work.

## Use the prepared Java API

Build with JDK 21 targeting Java 17. Compile your application against the
source-built JAR and import `org.jlinalg.fusion.FusionAssociation`:

```java
var result = FusionAssociation.testPrepared(
    new double[] {2.0, Double.NaN, 1.0},
    new double[] {0.5, 0.2, -0.1},
    new double[] {
        1.0, 0.9, 0.5,
        0.9, 1.0, 0.4,
        0.5, 0.4, 1.0
    },
    FusionAssociation.Options.defaults());
System.out.println(result.status());
for (String warning : result.warnings()) System.err.println(warning);
if (result.complete()) {
    System.out.println("Z=" + result.z() + ", P=" + result.pValue());
}
```

Inputs are GWAS Z-scores, weights for **standardized genotypes**, and reference
correlation LD flattened in row-major order, all in the same matched SNP order.
They are not raw-dosage weights requiring genotype-SD multiplication. NaN Z values
represent missing statistics. The component applies the original fixed 0.1-ridge
imputation, maximum missing fraction (default 0.5), and mean imputation-R2 filter
at nonzero-weight SNPs (default 0.7). That mean is not weighted by effect magnitude.

`new FusionAssociation.Options(maximumMissingFraction, minimumMeanImputationR2,
top1)` makes settings explicit. With `top1=true`, only the first largest squared
weight is retained, matching the source tie rule. This does not select among
trained models. NaN selected weights become zero as in the original script, with
every original zero-based position reported by `missingWeightsZeroed()` and an
explicit warning. No source IDs are matched/repaired by this prepared API.

The separate `alleleQc(a1,a2,reference1,reference2)` helper uppercases inputs and
returns the source `keep` and `flip` flags, including R's NA logical behavior
(`null` input means R NA). **The original keep flag does not reject every
incompatible reference pair or identical allele pair.** It is not a guarantee
of harmonization. The full upstream loader/matching behavior remains pending.

## Evidence and limits

The exact selected source is
[gusevlab/fusion_twas commit 9346c122](https://github.com/gusevlab/fusion_twas/tree/9346c1222bffbb34499fa7a8e23c1b701b55cb05),
under its retained GPL-3.0 license. The reference generator parses the original
`FUSION.assoc_test.R` and evaluates selected **unchanged AST expressions**, not
a manually copied reference algorithm. The complete source/license, selected
expressions, artificial inputs, full numeric arrays, source errors/no-results,
session and generator are retained in the checked-in test ZIP. Java comparisons
use absolute tolerance 1e-11 plus relative tolerance 1e-10. This is a component
comparison, not execution of the complete script for those 136 cases.

The [saved status](fusion-native-development/STATUS.json) binds source, fixture,
producer and complete evidence inventory. The actual development producer is
198,029,624 bytes, SHA-256
`bb25c3a41b2d2464f69deea9218e1742d8a5d1ce0817281deaf6a9d27668d54c`.
The optional producer check also verifies every JAR payload against the retained
complete payload maps.

```powershell
python -B tools/literature/verify_fusion_native_checkpoint.py
python -B tools/literature/verify_fusion_native_checkpoint.py --jar PATH_TO_EXACT_DEVELOPMENT_JAR
```

These are read-only checks, without R, installation or network execution. A future
changed producer/source needs its own qualification; do not refresh old hashes.

Separately, five direct executions of the **complete original R association
script** ran on synthetic PLINK/RDat inputs: complete data, missing-statistic
imputation, forced top1, and two internal COLOC configurations (GWAS BETA/SE and
Z/sample-size input). Each produced three original result rows; the six COLOC
rows report finite probabilities. These are runtime intake controls, **not a
completed execution adapter or whole-paper/biological validation**. Original
tables retain their three-significant-digit formatting. R packages were installed
only in a private project library; no global installation was requested. Linux
qualification covers the Java component, not Linux R execution.

## Prominent warnings and required actions

**A no-result status must not be interpreted as a finite association.** Read
`status()`, `complete()` and `warnings()`. The component preserves diagnostic
NaNs and original imputed arrays rather than dropping problematic SNPs. Even a
zero-weight missing SNP may propagate NaN as in the source. Original P=0 is
retained without flooring; the additional finite log-tail is available separately.
All returned arrays are defensive copies. Invalid/infinite inputs, non-correlation
LD and loci above the explicit **4,096-variant dense capacity** throw a named
exception. Fix the named cause or use a subsequently qualified upstream path;
do not silently shrink the locus or modify LD/settings to force a result.

**Full RUN/VERIFY/REPLAY, RDat/BED loading, model selection/training, permutation,
post-processing/plotting and the upstream execution adapter remain pending.**
The current component must not be advertised as the complete FUSION tool or a
replacement for a paper's unreported edition/options. Current master is not
substituted for historical paper editions. There is no global `fusion-tools`
command in this checkpoint.

When a required runtime cannot be installed where allowed, the future workflow
must prominently emit **SKIP3: dependent analysis skipped**, naming the missing
prerequisite and remedy. **FAIL2** means no complete workflow result: retain logs
and partial outputs, fix the cause and retry NEW output. Do not treat source
exit 0 alone as proof of a completed result. Native Java API failures are exposed
as explicit statuses/exceptions, not CLI exit codes.

Reference R startup emitted four C.UTF-8 locale warnings on Windows; the raw
warnings and complete passing outputs are retained. The first AST walker failed
on an R missing argument; its source/logs are retained separately, and the
corrected generator ran in a NEW directory. No scientific source was patched.

## Resume and private transfer

Finish the original association/COLOC adapter with typed source profiles, staged
inputs, owned-process/time/resource bounds, closed result admission and offline
VERIFY/REPLAY. Install/qualify required private runtimes for original model
training and post-processing; then test the full supported paths and inspect
linked author code for P108's baseline. Correct P212/P108 in a guarded catalog
transition preserving extraction provenance. Continue other high-impact tools,
all 1,050 pending use reviews and full-corpus missed-provider screening.

Preserve all 74 worktrees, shared dirty files, original inputs, and failed/null/
superseded evidence. Current private evidence is
`D:/projects/JLinAlg/build/gp-1/build/fusion-1/`. Transfer these before retiring the
machine: `normal-build-1/` (actual JAR/source context), `native-reference-1/` and
`native-reference-2/` (failed/current references), `packaged-reference-1/`,
`original-association-1/`, `upstream-intake-1/` and `runtime-install-1/library/`
plus downloaded dependency archives/receipts. The existing source audit remains
under `intake-1/` and `source-{windows,linux}-{1,2}/`. GitHub does not include the
installed runtime or actual JAR.

Use current process-safe source for external workers; pre-fix worker JARs remain
unsafe, and the older possibly terminated process's application/recovery remain
unknown. Existing release assets were not replaced. Previously blocked historical
exports remain local and require specific approval. Cell Ranger stays excluded.
Both unfinished reports stay off Pages; the 33 existing citation-panel omissions
remain open. No release or full-goal completion is claimed.
