# Toolbox handover: unfinished Monocle neighbor integration

**Start here. The 250-paper corpus is complete; the overall implementation and
paper-use review goal is unfinished.** Current catalog totals are **144 scoped
families (26 native Java +118 upstream adapters), 1,630 reviewed uses and 1,092
pending uses**. There are 147 integrations including three existing families,
446 named labels, 485 registry definitions, 2,722 observations, 259 missing labels
and 29 method-overlap labels. Aliases receive no extra family credit. The frozen
window is 2023-10-23 through 2026-10-23: 71 Nature, 104 Nature Genetics and 75 AJHG
papers. This handover does not refresh that window or alter the catalog.

**WARNING: THE NEW NEIGHBOR ADAPTER IS UNFINISHED AND FAILED ITS LATEST PILOT.**
It admitted no graph or statistical result. Its source is saved outside the
compiled tree, so it is absent from the normal executable and root dispatcher.
Use the qualified native weight component described in the
[preceding checkpoint](MONOCLE100_CHECKPOINT_2026-11-21.md) within its stated scope.

## Validated baseline and current development

Baseline main is `54e0130cfbfcd061ff1f76d16240898a9f8c2210`. It includes the native
`org.jlinalg.singlecell.Monocle100Weights` API and standalone
`org.jlinalg.cli.Monocle100WeightsCli`. That qualification has 33 original controls
(25 successful graphs and eight source failures), 636 focused tests per normal
build and each Windows Java21/25 and Linux Java21 packaged JVM, plus three public
bridges preserving 74 exact weights and matching 21 original-spdep inference
fields. These are preceding recorded results, not tests rerun by this handover.
All compiled source, build configuration, site files and 30 canonical catalog files
remain unchanged. No new family or review credit is added.

The new draft comprises five Java classes, an R worker, a Linux Python supervisor,
six compatible original reference files, a runtime profile and notices. See
[`development`](../../tools/literature/monocle100_neighbors/development/README.md).
It proposes explicit RUN/VERIFY/REPLAY with two engines: original `calculateLW`,
or original RANN rankings followed by the published native Java weights. There
is no automatic fallback. Both require a complete prepared original1.0.0 CDS,
literal embedding slot and explicit graph/settings. Neither learns a graph,
transforms expression or supplies a statistical test. Source-selected self-links
must remain; the current residual-Moran primitive refuses them.

The current five classes passed quick strict Java17 compilation. The earlier
direct-worker attempt `worker-smoke-1` failed in the adapter's sparse adjacency
projection, before neighbor calculation. `worker-smoke-2` succeeded after that
projection correction, with 27 cells and three genes, but predates the final
fingerprint/replay/report changes. The actual quick-compiled Java pilot
`pilot-original-1` then failed at the runtime guard:

```text
Changed canonical original function: slam rowapply_simple_triplet_matrix
FAIL2_NO_COMPLETE_WEIGHT_GRAPH_ADMITTED
```

The R worker exited2; the supervisor recorded no owned live processes after
cleanup, and Java recorded its supervisor no longer alive. `analysis.json` says
`weights_admitted=false` and `scientific_test_admitted=false`; `COMPLETED.json` is
absent. This is a retained guard failure, not proof the installed original source
changed. The fingerprint producer verified parsed-original versus loaded
body/formals, then serialized the parsed objects; the runtime guard serializes
loaded objects. Inspect serialization attributes/bytecode and package-loading
context before deciding on a canonical comparison. Do not weaken the guard,
change original source or refresh expected hashes merely to pass.

## Ordered next actions

1. Reproduce and diagnose the SLAM guard failure against the captured pilot and
   private exact runtime. Preserve the current hashes and failed attempts. Add
   independent source-change refusal controls for any revised canonicalization.
2. In a new worktree, restore the draft's Java and resources to their original
   `src/main` paths using the recorded byte-exact relocation map. Check for existing
   files before copying. The intake/smoke scripts retain those original paths.
   Keep engine selection explicit and the complete graph atomic.
3. Fix/verify the known admission gaps: JSON exports must fit declared read caps
   before completion; forced cleanup must wait and verify all owned descendants;
   test Java-parent death and cancellation. Current address-space limits are hard
   per process; sampled aggregate RSS/output checks are not hard aggregate quotas.
4. Exercise both engines on all33 source controls (25 prior successes/eight prior
   failures). Compare complete original RANN/listw/RNG returns with fresh original
   calls using identical caller expressions, including names/classes/attributes.
   Test VERIFY on both hosts, real REPLAY with parent preservation, missing
   runtimes, changed source, resealed capture changes, resource bounds, timeouts
   and process cleanup. R-free verification cannot establish nearest-neighbor
   optimality or authenticate the producer; describe that boundary accurately.
5. Add meaningful Java admission/contract tests, build the normal executable, and
   run the relevant focused suite and Windows21/25/Linux21 packaged checks.
   Compare all preceding JAR payloads. Quick compilation and the old direct-worker
   success are insufficient. Run the normal-JAR neighbor-to-weights-to-MORAN-LM
   synthetic bridge and independent original comparisons.
6. Audit original1.0.0 graph learning/preparation and P021's modified covariate Moran
   scope. **P021 reports UMAT: never silently substitute UMAP.** Do not guess roots,
   assay/size factors, covariate coding, author executed commit or historical
   dependencies. Current author code has unresolved license/commit attribution;
   it was inspected privately, not executed or redistributed.
7. Reassess all nine Monocle roles against their actual editions and uses before
   bounded family admission. Existing Monocle2/3 adapters and native primitives
   already cover parts of the scope; inspect them before duplicating work. Fresh
   source audits and guarded catalog transitions must preserve prior records.
8. Continue 1,092 pending individual-use reviews, grouped by high-impact families.
   Next candidates include QTLtools(9 papers), SKAT-O(8), ACAT(7), FUSION(7),
   MR-PRESSO(6), SKAT(6), fastQTL(5), GenomicSEM(5) and PRSice(5). Verify existing
   implementations, actual source roles and editions first. Prefer simple native
   Java methods; use tested upstream adapters for larger workflows. The
   [catalog handover](HANDOVER_2026-11-21_CATALOG.md) records the wider backlog,
   attribution boundaries and fresh-audit commands.

## Exact local resume and transfer paths

Workspace: `D:/projects/JLinAlg`. Handover owner: `build/mo-2`, branch
`codex/monocle100-neighbors-20261121`, based on the baseline above. Preserve all
**49 worktrees**, including the older dirty root branch. Use the clean handover
owner or a new clone/worktree of GitHub main; do not reset or clean the root.
Publication receipts are `build/git-publish/20261121-handover-93`.

Current private evidence is
`D:/projects/JLinAlg/build/mo-2/build/monocle100-neighbors-1`. Its important outputs:

- `pilot-original-1`: complete failed Java capture, original error, source,
  warnings, process receipts and unadmitted status; no completion marker.
- `worker-smoke-1` and `worker-smoke-2`: earlier failed/successful direct-worker
  attempts with their own source snapshots; neither qualifies the latest draft.
- `worker-smoke-2/reference-inputs`:33 complete synthetic original CDS objects.
- `function-fingerprints-1`: original body/formals comparison and saved hashes.
- `source-intake-1`, `source-intake-2`, `resource-draft-1`, `compile-1`, `compile-2`
  and `pilot-inputs-1`: retained intake, earlier drafts, classes and requests.

The exact private original library is
`D:/projects/JLinAlg/build/mo-1/build/monocle100-1/install-1/R-lib`.
Base R4.3.3 is under
`D:/projects/JLinAlg/build/literature-review/monocle-runtime/monocle133-reference-setup-1/.pixi/envs/default`.
Rscript is `bin/Rscript`, R home `lib/R`, native libraries `lib`, and base R library
`lib/R/library`, expressed as `/mnt/d/...` inside Linux. The selected profile pins
Monocle3 1.0.0, RANN2.6.2, spdep1.4-1, slam0.1-55, Matrix1.6-5, igraph2.0.3,
jsonlite2.0.0 and digest0.6.37 on `x86_64-conda-linux-gnu`. These present-day
dependencies do not establish a reconstructed paper-era environment. RDS files
are accepted only as complete explicitly prepared original-edition objects.

Original runtime installation/source protection receipts and the qualified normal
JAR remain in `build/mo-1/build/monocle100-1`. Producer:
`normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar`; its exact hash is in that
directory's `RESULT.json` and the saved preceding checkpoint. WSL profiles
`JLinAlg-Omics-SAIGE-20261026` and `JLinAlg-Omics-PyMOL-20261026` supply the original
R control environment and Linux Java/Python execution respectively. Windows
R4.6.1 is not the selected R4.3.3 runtime.

The [new saved packet](handover-2026-11-21-neighbors/README.md) preserves the
Git-visible dirty state of all49 worktrees as historical variants, plus the new
synthetic attempts and errors. This retains competing source variants without
promoting old catalogs or unfinished methods into the main build. Git objects in
the baseline supply exact matching files; the packet supplies changed byte blobs
and index patches. The reader verifies bytes, not scientific correctness.

```powershell
python -B tools/literature/monocle100_neighbors/verify_handover.py --out build/NEW-neighbor-handover-check
```

Use Python3.11+ and Git. Add `--restore` to extract historical variants and retained
evidence under NEW output; this never overwrites original worktrees or applies
staged patches. In a Linux clone use native Git. Under WSL on this linked Windows
checkout, set `--git /mnt/c/Program\ Files/Git/cmd/git.exe` for Windows gitdir
transport. No R, Java, installation or scientific calculation is run by the reader.

The preceding P92 saved reader also pins the old `HANDOVER_CURRENT.md`. Running
it directly on this newer checkout correctly refuses that changed document. Its
Windows/Linux retries use the untouched actual `build/mo-1` owner and pass; no
old hash, fixture or metadata was refreshed. Keep those two initial refusal logs
as provenance. To rerun that old check, execute its script in `build/mo-1`, or
check out its exact baseline commit in another clean location.

## Warnings and required actions

**GITHUB IS NOT A COMPLETE MACHINE BACKUP. Before retiring this machine, transfer
ignored runtimes, compiled JARs, primary full text, author source and complete
private evidence separately with SHA256 verification.** Preserve every raw,
null, failed, invalidated and superseded result. The new packet's
`PRIVATE-TRANSFER.json` identifies current private roots and exact existing JAR
and receipt pins; earlier transfer manifests remain linked from the
[catalog handover](HANDOVER_2026-11-21_CATALOG.md). Git-visible snapshots exclude
ignored files. SLAM GPL-2-only source/archive and the first resource draft remain
private; the public draft retains only fingerprints for that function. Compatible
Monocle/RANN/spdep reference source and licenses remain identified separately.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the exact named permitted runtime,
executable, original-edition input or accessible path. General download/private
installation authorization persists where the environment permits it. If actual
installation/execution is prohibited, retain the skip and clearly tell the user
what must be supplied. The analysis command itself performs no installation.

**FAIL2: NO COMPLETE RESULT.** Preserve original errors, logs and partial output;
correct the named source, input, numerical, capacity or process cause and retry
NEW output. The latest draft is currently in this category. Do not remove guards,
rename identifiers, drop cells/genes, remove self-links or substitute editions to
force success. Require exit0, `COMPLETED.json` and an explicit admitted analysis
before accepting a graph. A weight graph still is not an admitted statistical test.

The user permits educational/research/nonprofit use and accepted the separate
GATK3.8 academic noncommercial terms. Cell Ranger remains explicitly excluded.
Current iDEP was suggested instead of legacy iDEP.96; current terms do not license
that older source. Legacy TwoSampleMR startup configuration stays private.

Both literature reports stay off Pages. The inherited full website citation gate
still fails for `broad-gsea-tools.md`; no full-site pass or release is claimed.
Six inherited descriptive registry/inventory differences remain retained. This
handover adds no scientific qualification, GPU qualification or catalog credit.
The requested main-branch push is authorized; no force push is needed. The full
goal remains unfinished for the next owner.
