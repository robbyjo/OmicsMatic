# Toolbox handover: original PEER workflows, 21 November 2026

**Resume here. The 250-paper collection is complete; the implementation and
individual source-review goal remains unfinished. The new `peer-tools` command
is implemented and tested, but PEER has not yet been admitted to the catalog.**

## Current state

| Admitted catalog at this handover | Count |
| --- | ---: |
| Papers | 250: 71 Nature, 104 Nature Genetics, 75 AJHG |
| Frozen collection window | 2023-10-23 through 2026-10-23; no extension needed |
| Named labels / registry definitions | 446 / 485 |
| Scoped implementation families | 142: 26 native Java + 116 upstream |
| Including three existing adapters | 145 |
| Validated aliases with zero extra family credit | 2: COJO/GCTA and voom/limma |
| Paper/tool observations | 2,717 |
| Individually reviewed uses / pending | 1,598 / 1,119 |
| Method-overlap / missing labels | 31 / 259 |
| Clean manifest entries / canonical files | 832 / 24 |

These are scoped implementations, not complete reproductions of every named
tool or paper. PEER adds **zero catalog credit at this checkpoint**. The previous
admitted main commit is `c54d9186f74b92fc28c1250ce4b364771f8c708f`.
No release was requested. Both literature HTML reports remain excluded from
GitHub Pages. Lower entries in [HANDOVER_CURRENT.md](HANDOVER_CURRENT.md) retain
historical states; this document supersedes their next-work lists.

## Work completed and saved

The source-built normal JAR registers `peer-tools` RUN, runtime-free VERIFY and
REPLAY. Five new Java classes orchestrate the unchanged dense PMBio PEER 1.3
core at commit `40bc4b2cd92459ce42f44dfe279717436395f3f6`, using its FP32
matrices, bundled Eigen/alglib and an owned observer. A private builder verifies
the pinned archive and all 2,526 upstream files. It needs CMake and g++; Windows
WSL transport also needs Python 3 in an explicitly named distribution.

**The legacy Java `Peer` implementation is unchanged and has no stock-output
qualification.** Its FP64/Java random stream and initial second moment differ
from the original core. Do not count it as a newly qualified native PEER port,
silently use it as fallback, or refresh historical source hashes to bless it.

Read [PEER_WORKFLOWS.md](PEER_WORKFLOWS.md), the
[copyable build and example instructions](../../examples/peer-workflows/README.md),
the [18-role source proposal](peer-source-review-proposal/README.md) and the
[portable evidence reader](peer-workflow-checkpoint/README.md).

Recorded checks are complete:

- Normal Gradle build: all 26 configured tests pass, with no skips; strict Java
  17 source compilation. Packaged tests pass separately on Windows Java 21,
  Windows Java 25 and Linux Java 21, 26 per JVM, with no skips and JAR-origin checks.
- Twenty-four actual original controls include six converged models and 18
  explicitly incomplete fixed-iteration captures. Adding the double-bound
  observer preserved all 120 prior original files byte for byte. Original
  reported iteration/history counters and observed updates remain separate.
- Six documented global examples plus separate verification match all 42
  selected original raw files. Three real global replays include both
  Windows/Linux directions, preserving every parent file and original outputs.
- Fresh source controls on Windows and Linux verify all 250 XML/DOI identities,
  18 positive paper roles, 181 case-insensitive candidates and 163 exclusions;
  16 semantic refusals per host and seven exact dispatcher-inverse refusals pass.
- Only the three PEER route/help lines change the preceding dispatcher. The exact
  inverse restores its qualified bytes. All preceding JAR payloads except the
  dispatcher remain identical; the legacy native PEER class remains identical.
- The separate unchanged preceding limma owner executes fresh source/catalog
  verification and its saved reader successfully, without source/hash overlays.
- Saved PEER evidence readers pass on Windows and Linux: 8,404 logical evidence
  files in 1,287 unique blobs. These readers check recorded bytes and scope;
  they do not execute Java, C++, Git, primary XML parsing or fitting.

Failed compiler/test harness attempts and the first unpublished packet-filter
attempts remain retained with exact source snapshots. None is substituted for
a successful result. The final packet excludes installed runtimes, producer
JARs, full upstream source/archive, primary XML and full paper text.

## Next work, in priority order

1. **Complete the guarded PEER catalog admission.** No PEER catalog controller
   has been created. Start a new model from `limma_catalog.model.registry()`;
   use the existing limma/GCTA catalog code as architecture, preserving every
   earlier source owner, proof, before-state and all 1,598 existing reviews.
   Keep original paper identities/order and all unrelated inventory rows exact.
2. Add only the three missed expanded-name observations, **P074, P107 and P169**,
   and append the 18 proposed PEER source-role reviews. Update the PEER registry
   matcher to recognize the uppercase acronym and case-insensitive expanded
   name, including estimation/residual plurals, without matching generic peer
   review or the reviewer surname Peer. Promote one upstream family; the legacy
   native implementation remains unqualified. If this exact proposal passes,
   expected totals are 143 families (26 native +117 upstream), 146 including
   existing adapters, 2,720 observations, 1,616 reviewed and 1,104 pending.
   Derive and verify these totals rather than writing them as completed facts.
3. Preserve all source distinctions in `peer_source_roles.py` and CENSUS.json.
   Supplied GTEx factors and sensitivity-only use are not new fitting runs;
   only P212 explicitly reports PEER 1.3. Retain P213's four-versus-six-PC
   discrepancy, P219's BMI/residual-definition difference, P203's higher-BIC
   wording and P249's unresolved “logistically corrected” wording. Do not infer
   missing editions, author seeds, normalization, population or count rounding.
4. Bind the new admission to base `c54d9186...`, the PEER dispatcher inverse and
   the saved workflow qualification. Preserve the eight preceding metadata
   files exactly until transactional apply. Use closed plan/inspect/apply/fresh
   verify, source/model/transaction refusal controls on Windows and Linux,
   independent counts and review/source inverses, and preview/installed
   desktop/mobile checks. Extend the manifest and canonical inventory only for
   actual admitted proof files. Both literature reports must remain off Pages.
5. **Then prioritize TwoSampleMR**, using native Java for tractable methods and
   tested adapters for appropriate upstream scope. Continue the remaining
   individual source reviews and higher-impact missing families. The code-only
   preprocessCore finding still needs a separate provider/registry audit.

The current PEER source audit is reusable; do not repeat completed numerical
qualification without a relevant source change or unresolved failure. Future
catalog metadata/source changes must have their own evidence. Never weaken an
old strict reader to accept a changed dispatcher or review ledger.

## Resume paths and commands

The active checkout is `D:/projects/JLinAlg/build/pr-1`, branch
`codex/peer-20261121`. Private current evidence is
`D:/projects/JLinAlg/build/pr-1/build/peer-1`.
There are **37 worktrees**; preserve all historical dirty states.

Use Python 3.11+ without `-O`, and a NEW output directory for each attempt:

```powershell
Set-Location D:/projects/JLinAlg/build/pr-1
python -B tools/literature/verify_peer_workflow_checkpoint.py `
  --out build/NEW-peer-saved-check

python -B tools/literature/check_peer_sources.py `
  --primary-cache D:/projects/JLinAlg/build/literature-review/xml `
  --out build/NEW-peer-source-check

python -B tools/literature/peer_global_context.py `
  --historical-checkout D:/projects/JLinAlg/build/lm-6 `
  --primary-cache D:/projects/JLinAlg/build/literature-review/xml `
  --author-code-cache D:/projects/JLinAlg/build/lm-5/build/limma-global-1/author-code-intake-1 `
  --out build/NEW-peer-preceding-context
```

The unchanged preceding limma reader belongs to `build/lm-6` at `c54d9186...`.
Running that strict old reader directly against the new PEER dispatcher is
expected to refuse it. Use the exact inverse/current metadata check and the
separate unchanged owner above; do not overlay old source pins or hashes.

Useful current private evidence under `build/peer-1`:

| Path | Purpose |
| --- | --- |
| `intake-1` | Pinned official archive/source, all-250 intake and private contexts |
| `public-setup-1` | Successful tested builder, exact runtime.json and original binary |
| `original-controls-2` | Final observer original controls and 120-file equivalence |
| `normal-gradle-4` | Successful normal JAR, test reports and exact source snapshots |
| `packaged-global-1` | Three-JVM results and JAR payload comparison |
| `global-examples-1`, `global-controls-1` | Six global examples, three replays and inverses |
| `source-controls-windows-1`, `source-controls-linux-1` | Fresh source audits/refusals |
| `preceding-context-1` | Exact inverse and unchanged preceding owner execution |
| `saved-reader-windows-1`, `saved-reader-linux-1` | Successful final portable packet checks |

Normal JAR:
`build/peer-1/normal-gradle-4/project-build/cli/omicsmatic-0.4.0.jar`.
Private runtime binding:
`build/peer-1/public-setup-1/runtime.json`.
Its executable SHA-256 is
`133e2193ae92b05f4a598bbed258019a248190857c21963451423549be753a40`.
The tested WSL distribution is `JLinAlg-Omics-PyMOL-20261026`; transport is
explicit and cannot be silently substituted after moving machines.
Inherited P80 publication receipts are under
`D:/projects/JLinAlg/build/git-publish/20261121-limma-catalog-80`;
this handover's publication receipts are under
`D:/projects/JLinAlg/build/git-publish/20261121-peer-handover-81`.

## Warnings and required action

**Full website check has a pre-existing failure.** `checkWebsite` stops at
`Markdown vignette lacks scientific citations: broad-gsea-tools.md` on both
this checkout and the unchanged published `c54d9186...` owner. Resolve that
documentation/checker mismatch with its own provenance review before claiming
a green full website gate. The changed PEER page has separate local-link,
anchor and desktop/mobile checks; those do not replace the failed full gate.
The original failure logs and unchanged-baseline comparisons are retained in
`build/peer-1/handover-checks-1` through `handover-checks-3`. The final scoped
page checks pass after allowing the long source hashes to wrap on mobile;
the earlier overflow diagnosis and screenshots remain retained.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Read WARNINGS.md and analysis-status.json.
Supply the named exact permitted source, input, runtime/package or environment
and retry NEW output, or retain the skip if unavailable. Download/private
installation is authorized where the environment permits. If installation is
prohibited, prominently report the affected analysis and exact prerequisite,
and skip it. Missing software alone does not establish an installation ban.

**FAIL2: NO COMPLETE RESULT.** Preserve inputs, original initial/fitted/null/
partial objects, warnings, histories and failed attempts. Resolve the named
data/design, numerical/convergence, source/integrity, capacity or process cause
and retry NEW output. Never silently repair identifiers, populations, versions,
thresholds, estimators or nonfinite values. Read original worker logs. If cleanup
fails, retain the exact ownership ledger/token and inspect only that owned
process group. An interrupted supervisor without final status is incomplete.

**Before changing machines, separately transfer ignored exact runtimes, producer
JARs, primary XML/full text, linked author source and complete private evidence
with checksums. GitHub is not a complete machine backup.** Retain all 37 worktrees
and their Git metadata; do not delete, clean or reset historical dirty checkouts.
Use the inherited publication preservation inventories and new handover receipt
to identify retained files. Public packets contain owned code, permitted license
text, hashes, locators, synthetic captures and receipts, not all private inputs.

Cell Ranger remains excluded by the user. FIMO research/nonprofit use and the
GATK 3.8 academic noncommercial terms were accepted. No authorization to message
others, release software or publish the withheld literature reports is implied.
The full goal remains active and unfinished.
