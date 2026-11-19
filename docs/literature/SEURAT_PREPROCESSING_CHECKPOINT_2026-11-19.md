# Native Seurat preprocessing checkpoint — 19 November 2026

The preceding goal turn admitted METAL and 24 reviewed uses in main commit
`eafc967aa72adb7943914fca5e9014cda81226a6`. This turn adds a qualified native Java
matrix component for LogNormalize, RC, both CLR margins and unsplit,
no-regression sparse ScaleData, plus copyable examples and original references.
**The full 250-paper implementation/review goal remains active and unfinished.**

Read the [API guide](../vignettes/seurat-native-preprocessing.md),
[example](../../examples/seurat-preprocessing/README.md),
[qualification](seurat-preprocessing-development-v2/README.md), and
[machine-readable validation](seurat-preprocessing-development-v2/VALIDATION.json).
The source API is `org.jlinalg.sequence.SeuratPreprocessing`. It needs Java 17+
and no R/external package to run. This is a source-build component; the old
public 0.4.0 release does not contain it.

## What was verified

The pinned official Seurat 5.6.0 source is commit
`75dfa8603befe31505e6287d32dc86c607732bd6`. Six installed R function
bodies/formals matched it before original calls. The independent Windows R
controls comprise 13 datasets and 298 requests. Three source CLR shape errors
are retained. All 295 returned matrix views match the Java calculations,
including source diagnostic values: 15,081 primary fields plus 920 pipeline
fields. The native admission boundary yields 247 usable matrices and 48
diagnostic-only numerical refusals.

Thirteen tests passed against the actual development JAR on each of Windows
and Linux Java 21. Four standalone example launches passed their declared
outcomes: two normal exits and two expected FAIL2 exits with no admitted
matrix. Source-only compilation yields the same ten method-class payloads as
the packaged candidate. Every one of the 23,053 prior JAR members is unchanged.
No full-project suite, original Linux R run or biological validation is claimed.

Source details retained include integer coercion of LogNormalize's scale,
real RC scaling, CLR's two distinct populations, explicit-zero/empty-column
differences, upper-only clipping even with both flags off, zero-sigma source
rows, and unusable undefined/overflowed statistics. Unknown/duplicate selected
IDs are refused rather than silently intersected away. No input values,
identities or requested populations are silently repaired or truncated.

The original-reference collector reproduced both JSON and RDS bytes. Four
genuine SKIP3 controls launched no reference process. No installation was
needed or attempted. All 24,841 private R-library files, the previous 33 frozen
artifacts and the other 20 worktrees remained unchanged. The current protected
set also retains the new candidate, original RDS and runtime manifest.

The source/evidence audit checked all 220 public qualification payloads, the
seven pinned upstream source payloads, all 23,053 prior JAR members and the
24,841 private R-library files. An initial audit-counter error for scalar JSON
strings was retained as a failed attempt in the separate historical folder;
the corrected version passed without changing calculations or old hashes.

## What remains

**Seurat still has method-overlap status; no new family credit is claimed.**
Canonical totals remain 250 papers, 446 named families, 485 definitions, 2,695
observations, 130 new scoped integrations (24 native + 106 upstream), 1,290
reviewed positive uses and 1,405 observations awaiting review. There are 259
missing and 45 method-overlap definitions. All nine catalog/manifest/HTML files
checked at this checkpoint remain byte-identical to the METAL predecessor.

The source intake scanned all 250 identities and found 197 named Seurat
paragraphs in 51 papers; 49 have current observations. This is intake, not 49
completed reviews. Existing reviewed P061 is methodology reimplementation,
not established direct package execution. Extra candidate P083 is background
about Azimuth/Seurat v4, and P160's mention is a supplementary-table label;
neither becomes a new positive observation automatically.

Next, finish individual Seurat roles and their literal editions/settings, then
implement the remaining high-impact workflow stages. Inspect existing marker
testing, SCTransform, PCA/embedding and graph components before duplicating
them. Variable-feature selection, regression/split-aware scaling, full assay
construction, integration and clustering remain separate work. The new
5.6.0-profile arithmetic does not establish historical v3/v4/v5 author runs.
The existing standalone Seurat marker-testing adapter and six-edition native
marker summaries remain separately qualified; do not restart those drafts.

Continue the full missing-tool and remaining-review backlog after the Seurat
workflow work. Cell Ranger remains excluded. Keep both literature pages
withheld from Pages and preserve historical failures/skips/partial evidence.
No release or website deployment is part of this source-only component.

## Warnings and handover locations

**FAIL2 means no complete result.** Print native warnings and required actions;
`matrix()` refuses invalid arithmetic while diagnostic getters retain it.
Retain inputs and diagnostics, correct the named cause and retry. A finite
source zero or a saved diagnostic table is not sufficient for admission.
**SKIP3 skips optional original-reference capture/audit** when an exact permitted
prerequisite is absent or forbidden. Supply it or leave that work skipped.
Native Java usage does not require those R prerequisites. Never install into
or alter the frozen/shared runtime libraries to make a request pass.

Use `D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/seurat-preprocessing-20261119`. The old root and other worktrees retain
unchanged historical overlays; do not broadly stage, reset or clean them.

- Current private work and retained attempts:
  `D:/projects/JLinAlg/build/high-impact-stats-1/build/seurat-high-impact-1/`.
- Current JAR: `build-1/jar/omicsmatic-0.4.0.jar` within that directory,
  194,168,202 bytes, SHA-256
  `e5af8fb0f3c2fb39ef3c3d399380dbe6cd66b8323aabd6cfab0118aec4fb0bc5`.
- Original final controls: `reference-3/`; JVM checks: `qualification-1/`;
  examples: `example-1/`; collector controls: `collector-controls-2/`;
  complete source/physical audit: `audit-2/`.
- Immutable private runtime manifest: `probe-2/R-LIBRARIES-BEFORE.json`.
- Full primary source intake:
  `primary-intake-1/PRIMARY-CANDIDATES.private.json`. It contains full article
  paragraphs for private review; do not upload that file as public evidence.
- Publication/preservation receipts:
  `D:/projects/JLinAlg/build/git-publish/20261119-seurat-preprocessing-24/`.

The public qualification packet contains selected source, synthetic results,
method bytecode and receipts. It omits the full candidate/parent JARs, private
runtime manifest and installed libraries. Preserve the named local workspace
for a machine handover. No scientific analysis is intentionally left running.
