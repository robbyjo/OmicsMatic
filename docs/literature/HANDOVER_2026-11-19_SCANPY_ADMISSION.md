# Toolbox handover, 19 November 2026

**Resume from GitHub main. The latest scientific baseline is
`d70b229489a7b16206060ca8827539011b6a896a`. This handover records remaining
work; it does not admit Scanpy or complete the 250-paper goal.**

This supersedes the remaining-work lists in the earlier Scanpy handovers.
Global dispatch, the first 22-paper crosswalk and native dispersion HVGs are
now implemented and published. Do not restart those completed milestones.

## Current position

The frozen collection contains **250 papers: 71 Nature, 104 Nature Genetics
and 75 AJHG**, dated **2023-10-23 through 2026-10-23**. No extension was needed.
Keep those identities and dates when resuming. The latest admitted cumulative
catalog remains [Enrichr](ENRICHR_CATALOG_CHECKPOINT_2026-11-19.md).

| Item | Admitted total |
| --- | ---: |
| New scoped integrations | **136** |
| Native Java / upstream | 25 / 111 |
| Including three existing adapters | 139 |
| Named families / effective definitions | 446 / 485 |
| Paper/tool observations | 2,703 |
| Reviewed / pending individual uses | **1,462 / 1,241** |
| Missing / method-overlap definitions | 259 / 39 |
| Cumulative manifest entries | 812 |

These are bounded scopes, not entire replicated packages or papers. Scanpy
components are qualified but have **no admitted family credit yet**. Canonical
metadata and all 1,462 review objects remain unchanged at this handoff.

## Completed work to reuse

All checks below cover Windows Java 21/25 and Linux Java 21. Original inputs,
source, failures, corrections and compact evidence remain retained.

| Milestone | Qualified scope | Published commit |
| --- | --- | --- |
| [Native preprocessing and source census](SCANPY_PREPROCESSING_CHECKPOINT_2026-11-19.md) | Float64 dense/CSR filtering, normalization, log1p and scaling; explicit 1.9.3/1.13.0 profiles; 426 checks/JVM including 418 original cases; 250 XML identities, 22 roles and 53 named paragraphs | `e3211aa370993c7d10d73b32c44325d297469934` |
| [Ordered upstream workflow](SCANPY_WORKFLOW_CHECKPOINT_2026-11-19.md) | Original Scanpy 1.13.0 CPU RUN/VERIFY/REPLAY; 13-step MatrixMarket and four-step supplied-H5AD programs, 15 distinct operations; 58 original array/annotation comparisons; 20 Java checks/JVM and 21 worker controls | `33d94e818d3e03c7785563145980780c3e1860e4` |
| [Native Pearson normalization](SCANPY_PEARSON_CHECKPOINT_2026-11-19.md) | Float64 dense/CSR analytic residuals, explicit 1.10.4/1.13.0 zero conventions; 735 checks/JVM including 720 original cases; standalone Java library/examples | `a638b0ae3610001332c46022aeeff5f47bdb706d` |
| [Global command and source crosswalk](SCANPY_DISPATCH_CHECKPOINT_2026-11-19.md) | Main `scanpy-tools` entry, actual RUN/VERIFY/REPLAY, 52 original-array comparisons and 62 existing checks/JVM; exact three-line dispatcher inverse preserves older Enrichr evidence; all 22 literal source roles mapped | `cc6f6823a0cf6dba95fd8567b1bef3f6d18eda95` |
| [Native dispersion HVGs](SCANPY_HVG_CHECKPOINT_2026-11-19.md) | Explicit Seurat/Cell-Ranger flavors, 1.9.3/1.13.0, float64 dense/CSR, batching, ties/missing/export rules; normal JAR and isolated library each pass 2,574 checks/JVM, including 2,560 original cases and 928 original errors; examples pass without Python | `d70b229489a7b16206060ca8827539011b6a896a` |

The dispersion-HVG addition preserves all 20,210 preceding class payloads.
The three native APIs are explicit Java paths; they do not silently invoke
upstream software. Native Pearson normalization does not select Pearson HVGs
or fit SCT. Dispersion HVGs do not implement Seurat-v3 LOESS. The workflow's
29 development operation names exceed its 15 qualified operations.

## Immediate next work: guarded Scanpy catalog admission

1. Verify the saved packets below and read the frozen
   [census](scanpy-preprocessing-checks/CENSUS.json),
   [crosswalk](scanpy-source-coverage/COVERAGE.json),
   `tools/literature/scanpy_source_roles.py` and `scanpy_coverage.py`.
   There are 22 existing Scanpy observations, **21 unadmitted review
   candidates**, and one already reviewed P061 use. Preserve P061's original
   review and the entire 1,462-object review prefix. Do not add observations
   for the same 22 uses or invent missing historical settings.
2. Recover historical-owner execution as described under the checkout warning
   below. The original unchanged Enrichr owner must run in its own exact
   historical checkout, with its existing exact COLOC predecessor. The new
   dispatcher deliberately makes the old Enrichr current-source guard refuse
   the active source; do not refresh hashes or overlay old dispatcher bytes.
3. Create a **new** `tools/literature/scanpy_catalog/` owner, following
   [the Enrichr admission driver](../../tools/literature/enrichr_catalog/ADMISSION.md).
   No Scanpy admission modules, proposal, plan, apply or verify have been
   implemented at handoff. Use scientific baseline `d70b229...` for the actual
   before state. Keep `enrichr_catalog/` and older proof readers unchanged.
   Extend the coverage in the new namespace; the old crosswalk is frozen and
   predates native dispersion HVGs.
4. Admit only the qualified current CPU workflow and explicit native component
   scopes. All 22 roles must retain separate historical-edition, author-setting,
   host, unqualified-branch and whole-author-pipeline gaps. A bounded family
   admission can proceed with those gaps explicit; do not spend the entire
   next tranche on uncommon branches before other high-impact families.
5. Add deterministic proposal/conservation checks, source/predecessor and
   qualification evidence, exact before ZIP and review inverse, concurrent
   edit guards, manifest and both embedded HTML report payloads. Run **plan,
   inspect, apply, then fresh verify**. Preserve all prior evidence and original
   successful/failed attempts; use NEW output directories for every operation.
   Do not reapply Enrichr.
6. Derive counts from the actual proposal. If all 21 candidates qualify as
   source reviews and one bounded upstream Scanpy family is admitted, the
   expected result is **137 integrations (25 native +112 upstream), 1,483
   reviewed and 1,220 pending**, with 2,703 observations and 259 missing
   definitions unchanged, and method-overlap falling to 38. These are
   **prospective counts, not the current catalog**. Do not count the native
   component APIs again as additional families.
7. Validate and push the resulting admission to main without force. Changing
   `site/**` reports requires following the actual Pages workflow and checking
   the live home page and both deliberately excluded report URLs. Keep their
   exclusions. No release is authorized by this handover.

Important crosswalk constraints: P021 explicitly reports the `cell_ranger`
HVG flavor, but its older/custom wrapper remains a historical gap. P093 uses
`seurat_v3`; P062 uses experimental Pearson HVGs, so neither acquires
dispersion-HVG equivalence. P031 says log10 and *most highly expressed* genes,
not HVGs. P055's t-SNE belongs to OpenTSNE. Keep P062's square-root plots,
P070/P085/P123 spellings, P100's literal conjunction/threshold and all other
source ambiguities. P024/P083/P104 do not identify Scanpy functions;
P182 reuses a supplied integrated object without establishing a de novo fit.
Current-function counterparts are not proof of author-version execution.

After this admission, continue **higher-impact missing scopes** and the
remaining individual reviews. SuSiE and GSEA each occur in 19 retained catalog
papers, GCTA in 17 and edgeR in 16; recheck current implemented scope and
primary methods before selecting work. Frequencies do not establish an
unimplemented whole tool. No reliable ETA is established for the whole goal.

## Prominent warnings and required recovery

**Historical checkout preparation failed; no Scanpy admission ran.**
`build/scanpy-catalog-admission-1/` under the active checkout retains the first
attempt. Windows Git reported `Filename too long` for deep PRS-CS fixture paths
and `Could not reset index file to revision 'HEAD'`. Git did not retain a new
registered checkout. The existing **22 worktrees remain unchanged**. The
[preparation packet](handover-scanpy-20261119/PREPARATION.json) preserves the
original helper/logs and records recovery. Do not rerun that helper into its
existing output or treat this failure as a scientific result.

Use a NEW shorter path and command-local Git options, for example from the
active repository:

```powershell
git -c core.longpaths=true -c core.autocrlf=false worktree add --detach D:/projects/JLinAlg/build/sc-enrichr-NEW bd26330459c35c5ec6e5be904d349280bb353f6e
git -C D:/projects/JLinAlg/build/sc-enrichr-NEW rev-parse HEAD
git -C D:/projects/JLinAlg/build/sc-enrichr-NEW status --porcelain
```

Require the exact HEAD above and a clean checkout before running its unchanged
owner. Recreate its ignored `build/literature-review` junction to
`D:/projects/JLinAlg/build/literature-review` where permitted. Use a NEW output
under that checkout, its own `tools/literature` on `PYTHONPATH`, and these
original-owner arguments (all paths absolute):

```text
python -B -m enrichr_catalog.catalog --verify
  --primary-cache D:/projects/JLinAlg/build/literature-review/xml
  --supplement-pdf D:/projects/JLinAlg/build/high-impact-stats-1/build/seurat-catalog-1/supplement-P160-2/SUPPLEMENT.pdf
  --author-archive D:/projects/JLinAlg/build/high-impact-stats-1/build/plink-high-impact-1/P114-author-code-1/EAS_SCZ_rCNV_analysis.zip
  --historical-checkout D:/projects/JLinAlg/build/high-impact-stats-1/build/enrichr-catalog-admission-1/coloc-predecessor-checkout
  --out <NEW-output-under-the-Enrichr-checkout>
```

The multiline block lists arguments; combine them into one command for the
chosen shell. The existing COLOC checkout must remain clean at
`29486e3f7bf83f534324f21c2d2bd917ad35b134`. Expected original Enrichr success is
`PASS_ENRICHR_CUMULATIVE_ADMISSION_25_ROLES_CURRENT_SERVICE_UPSTREAM`, with
136 integrations, 1,462 reviews and 812 manifest entries. Its nested historical
marker skip remains a skip. The new Scanpy driver will need distinct Enrichr
and COLOC checkout paths, passing the latter through to the original owner.

**SKIP3:** an exact permitted dependency, runtime, source or input is absent,
unreadable, or cannot be installed/executed. Skip the dependent analysis,
identify the missing item and restore it where permitted; retry NEW output.
Native Java components do not need Python. Saved proof readers need Python
3.11+; original upstream execution needs the specifically qualified stack.

**FAIL2/no complete result:** changed evidence, invalid schema/data, undefined
or nonfinite arithmetic, lost positive model terms, capacity, interruption,
timeout or process failure. Preserve inputs, warnings, diagnostics and partials;
correct the named cause and retry NEW output. Do not silently repair IDs,
dtype, alleles, P values, thresholds, populations, NaN/Inf or algorithms.
Check native `usable()` before using output; diagnostic selections are not
admitted results when it is false.

**Two historical marker-verifier mains remain SKIP3** until exact original
`build/cli/omicsmatic-0.3.7.jar` is restored: **164,898,617 bytes**, SHA-256
`545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
A different published JAR is not a substitute. Keep original verifier hashes.

Downloads/installs are already authorized where allowed. Research/nonprofit
FIMO use and academic noncommercial GATK 3.8 terms were accepted. **Cell Ranger
remains excluded** at the user's request; Scanpy's `cell_ranger` HVG flavor is
a separate open-source method. Do not ask again about established decisions.
Read each workflow's warnings/status/logs before using results.

## Saved-proof checks

From the active checkout, use Python 3.11+ and NEW paths:

```powershell
python -B docs/literature/handover-20261119/verify.py
python -B tools/literature/verify_scanpy_preprocessing.py --out build/handoff-preprocessing-NEW --restore-component
python -B tools/literature/verify_scanpy_workflow.py --out build/handoff-workflow-NEW --restore-capture
python -B tools/literature/verify_scanpy_pearson.py --out build/handoff-pearson-NEW --restore-component
python -B tools/literature/verify_scanpy_dispatch.py --out build/handoff-dispatch-NEW --restore-capture
python -B tools/literature/verify_scanpy_hvg.py --out build/handoff-hvg-NEW --restore-component
```

The preprocessing reader can repeat the 250-paper census with
`--rescan --primary-cache D:/projects/JLinAlg/build/literature-review/xml`.
These readers check saved evidence and restore explicit components/captures;
they do not perform fresh scientific fits, reinstall upstream runtimes or
authenticate coordinated replacement. Qualification counts above describe
the retained actual runs. SHA-256 establishes integrity, not a signature.

## Machine transfer and preservation

Active checkout: **`D:/projects/JLinAlg/build/high-impact-stats-1`**, branch
`codex/plink-relatedness-20261119`. Original root
`D:/projects/JLinAlg` remains on `codex/analysis-pipeline` with historical dirty
overlays. Do not reset/clean it or bulk merge old variants over current main.
All 22 registered worktree HEADs are ancestors of main. All 2,248 nonignored
dirty file variants remain represented by Git plus the verified public
[historical overlay archive](handover-20261119/README.md). The four remaining
old release commits are already in public tag v0.3.1; do not merge their
obsolete release metadata merely to make old branches appear merged.

**GitHub is not a backup of ignored full evidence, installed runtimes or JARs.**
Transfer separately, where permitted:

- Entire active `build/scanpy-high-impact-1/`, preserving every successful and
  failed attempt, source/runtime intakes, frozen public copies and all three
  HVG reference captures. Qualified normal JAR:
  `hvg-gradle-2/project-build/cli/omicsmatic-0.4.0.jar`; standalone library:
  `hvg-package-2/omicsmatic-scanpy-hvg-development.jar`. Public 0.4.0 predates
  these Scanpy APIs; use those candidates, restored components or current source.
- Active `build/scanpy-catalog-admission-1/` failed preparation and existing
  `build/enrichr-catalog-admission-1/` including the exact COLOC checkout.
- Original `D:/projects/JLinAlg/build/literature-review/` XML/cache/source
  evidence; the P160 PDF and P114 author ZIP named above; all prior evidence
  bundles, before archives, input files and runtime provenance.
- WSL distribution `JLinAlg-Omics-SAIGE-20261026`, especially exact prefixes
  `/var/tmp/omicsmatic-scanpy-current-20261119-1/environment`,
  `/var/tmp/omicsmatic-scdrs-reference-2/environment`, and
  `/var/tmp/omicsmatic-scanpy-1104-pearson-20261119-1/environment`.
  Never install into existing reference prefixes. Preserve their inventories.
- Original-root `build/*20261119*.py` NEW-output helpers and
  `build/git-publish/20261119-scanpy-hvg-50/` plus
  `build/git-publish/20261119-handover-51/` publication/preservation receipts.
  Do not publish setup receipts containing full environment dictionaries.

The five saved Scanpy packets, frozen census, 812-entry manifest and historical
overlay archive are rechecked for this handover. The private preparation
failure is retained separately from those passing checks. Both literature
HTML reports remain withheld from Pages; no release is created. The complete
implementation/review goal remains active and unfinished.
