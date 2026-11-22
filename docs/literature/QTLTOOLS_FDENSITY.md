# Native Java QTLtools 1.3.1 fdensity component

`org.jlinalg.genetics.QtltoolsFunctionalDensity.calculate` implements the selected
fdensity operation on explicitly supplied valid BED intervals. It runs entirely
in Java and uses no C++, R, network or external process. This is a **library API**;
the complete QTLtools execution adapter and global CLI remain pending. It adds
zero catalog-family credit. The catalog stays at 145 scoped families with 1,092
paper-use reviews pending.

Build current source; the previously released JAR predates this class. Start with
the [runnable example](../../examples/qtltools-fdensity/README.md). It declares
two QTL rows and three annotations and returns four original-matching rows:

```text
-20 -11 0
-10 -1 0
0 9 2
10 19 0
```

The third column counts **QTL rows with any annotation overlap in the bin**.
Two annotations at one QTL count once in that bin. Duplicate QTL rows remain
separate rows and can increase the count. This component does not compute
enrichment significance or discover QTLs.

## Warnings and required actions

**Annotation order can change original results.** The upstream interval-tree
constructor keeps input order for chromosome groups with fewer than 64 annotations.
Its query guard can skip the entire group when the window's right endpoint
precedes the first input annotation's start. The Java component preserves this
behavior. Read `result.sourceOrderGuardedQtlRows()` and report it prominently in
any calling workflow. Do not silently sort or repair inputs to change a result.
Sorting is a different declared input and can change the original analysis.
The four explicit 2/63/64-row controls and 100 larger-tree cases verify this rule.

**Supply the exact coordinates, assembly and strand.** Inputs use
`GenomicIntervals.Interval` with nonempty zero-based half-open BED coordinates.
The original QTL anchor is start plus one; QTL end and ID do not select a different
anchor. Only minus strand reverses relative coordinates. Dot follows original
positive-strand handling; it does not establish biological orientation. Chromosome
labels remain literal. No assembly conversion, chromosome-name repair, lead-QTL
selection, OCR preparation, rooting or phenotype inference occurs.

**Window is the radius.** Both window and bin width are positive explicit integers.
Candidate selection uses the original closed window. If the bin width does not
divide twice the radius, the final bin extends past the radius, while candidate
selection is unchanged. Do not enlarge the candidate window to match that bin.
The maximum permitted allocation is explicit and at most 1,000,000 bins. Original
signed-int coordinate/loop overflow is refused. Refusal never changes bin widths,
coordinates or requested capacity. Inputs lacking any shared chromosome are
refused; a valid shared chromosome with no nearby features produces zero counts.

**Failure admits no result.** The API throws `IllegalArgumentException` for invalid
parameters, coordinates, empty inputs, missing chromosome correspondence or
capacity/overflow. The caller must retain diagnostics, report the affected analysis
and correct the named cause before retrying. Malformed/permissively parsed C++
input, verbose variance/RNG summaries, file parsing and original process-error
text are outside this typed valid-BED component. No NaN/Inf or identifier repair
is provided. No cis/trans mapping, permutation calibration, conditional inference,
MBV, quan, FDR or whole-paper reproduction is supplied.

**Upstream reference dependencies are separate.** Native use needs Java only.
Rerunning C++ controls needs the exact permitted executable, libraries and profile.
If a necessary installation is prohibited or unavailable, label that dependent
reference analysis SKIP3, state the reason and exact missing item/path, and retain
the published controls as retained evidence rather than fresh execution. Downloads
and private installation remain authorized where the environment permits them.

## Evidence and retained differences

The selected official source is tag 1.3.1, commit
`a9606ee736d8489e3ec54c8b8c92c32fecad3bd0`. The
[source packet](qtltools-fdensity-development/SOURCE.json) retains the mode,
interval-tree code, GPL license and unchanged hashes. The measured reference is
the exact Ubuntu rebuild `1.3.1+dfsg-4build3`, with its executable, package and
loaded-library hashes recorded. It is not described as an official upstream
binary build. No shared system package/runtime was changed; dependencies were
privately downloaded and extracted. An initial missing BLAS search path and the
first source-download capacity refusal remain retained.

The original controls comprise **576 cases: 569 completed and seven original
errors, with 2,838 complete output bins**. They cover strand/origin/end behavior,
duplicates, literal chromosomes, empty/disjoint inputs, final-bin overshoot,
integer boundaries, annotation ordering, the 64-row transition and larger trees.
All initial 472 scientific records remain exact after extending the controls.

The first Java build passed compilation but failed 313 of 483 tests because it
used a generally sorted interval index without the upstream small-tree guard.
Its source, diagnostics, failed XML and unadmitted JAR remain retained. Expected
original results were not changed. The corrected source passes **588 focused tests
in a normal build and per actual packaged Windows Java 21/25 and Linux Java 21
JVM**. Production classes are checked to load from that normal JAR. All 22,425
preceding JAR payloads remain byte-identical; only four new native class payloads
are added. The public example matches the original four rows on all three JVMs,
and its documented source-file launch passes on Windows Java 21.

Read the [nine-paper assessment](QTLTOOLS_SOURCE_ASSESSMENT.md) before assigning
this operation to a paper. P104 reports fdensity, but its complete prepared inputs
and invocation are not recovered. Other reported editions and operations remain
separate; P190's executable binding needs supplementary/code review.

## Recheck and continue

The metadata reader checks saved source, fixture, original captures and validation
bytes without executing Java or C++. It does not become a fresh model comparison:

```powershell
python -B tools/literature/qtltools_fdensity/verify.py --out build/NEW-fdensity-check
python -B tools/literature/qtltools_fdensity/verify.py --out build/NEW-fdensity-restore --restore
```

For actual Java comparisons, use the checked-in `build.py` with --out NEW and
--jdistlib-cache pointing to the repository-pinned 0.10.2 dependency; then run
`packaged.py --build BUILD --out NEW`. These qualification drivers retain this
machine's JDK/Gradle/WSL locations. Review paths in a NEW copy when moving machines;
do not refresh older evidence hashes. Ordinary library users can build current
source with the repository Gradle workflow and call the public Java API directly.

Current owner: `D:/projects/JLinAlg/build/qt-1`, branch
`codex/qtltools-high-impact-20261121`. Local evidence is under
`build/qtltools-fdensity-1`; source/private runtime intake is under
`D:/projects/JLinAlg/build/literature-review/qtltools-runtime`.
**Before machine retirement, separately transfer exact private JARs/runtimes,
full text, author source and complete private/failed outputs, and verify destination
checksums.** See the packet's PRIVATE-TRANSFER.json. SHA256 establishes integrity,
not producer authentication. Preserve all 59 preceding owners. Both unfinished
reports remain off Pages, Cell Ranger remains excluded, and no release or whole
goal completion is claimed. Next work is the exact-edition QTLtools adapter and
the remaining high-impact GWAS/omics implementations and use reviews.
