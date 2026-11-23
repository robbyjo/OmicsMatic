# Handover: SCENIC+ development and the remaining 250-paper objective

The public catalog contains **155 scoped families: 29 native Java and 126
upstream workflows**, or 158 including three existing adapters. All 250 papers
are collected; **1,702 paper uses are reviewed and 1,020 remain pending**.
There are 2,722 positive observations, 446 labels and 485 provider definitions.
The full implementation and semantic-review objective remains unfinished.
SCENIC+ earns **zero new families and zero reviews** at this checkpoint.

The frozen cohort is 2023-10-23 through 2026-10-23: Nature 71, Nature Genetics
104 and AJHG 75. No extension was needed. Keep that cohort when resuming.
The [SCENIC admission](SCENIC_CATALOG_CHECKPOINT_2026-11-23.md) is already on
GitHub main in `6a8713927618c9cdb5f1b8db99dc2d649b9cad71`, followed by publication
and cleanup receipt `acc48c54746f7b3af0b5ab4378e454131b07d3d5`. SCENIC and SCENIC+
are separate providers. This handover records subsequent private development;
it introduces no project command or released binary.

## Warnings and required actions

| Warning or unfinished work | Required action |
| --- | --- |
| **SCENIC+ is a private, licensed runtime.** The user accepted the v1.0a2 terms for an unmodified internal academic runtime. | Keep the vendor source, installed runtime, wheels, caches and source-bearing traces private. Follow the accepted copying, transfer, modification, citation and indemnification terms. Do not port vendor code into Java, modify it, or publish it in evidence bundles. See the [official license](https://github.com/aertslab/scenicplus/blob/v1.0a2/LICENCE.txt). Do not repeat the already answered acceptance question. |
| **Older blanket WSL-transfer instructions need qualification.** This distribution now contains that licensed software. | Before exporting/copying WSL, establish that the destination and recipient are permitted by the accepted license. Otherwise retain this runtime on the current authorized machine and arrange an independently authorized installation. GitHub publication does not transfer the private runtime or its permission. No machine transfer is claimed here. |
| **SCENIC+ Java integration and full eGRN qualification are unfinished.** | Implement and qualify the project adapter before catalog admission. Import checks and the original region-to-gene smoke test do not establish a project implementation or an author-environment reproduction. |
| **Undefined correlations were observed.** | Preserve all warnings, NaN/Inf and field-specific eligibility. Four of 16 artificial region-to-gene rows have undefined rho. Do not replace undefined correlations with zero or report a successful fit as evidence that every field is reportable. |
| **SKIP3: required software, references or permitted installation unavailable.** | Name the affected analysis, dependency and exact recovery steps, skip that dependent analysis, and continue independent work. Keep Cell Ranger excluded as requested. Do not substitute another edition silently. |
| **FAIL2: invalid, incomplete, numerical, deadline or integrity result.** | Preserve raw/partial evidence. Fix the stated cause and retry fresh output paths. Inspect warnings, task ledgers and reportability flags. Never refresh historical hashes to bypass a failure. |
| **Full website validation still has 33 existing citation-panel failures.** | Add appropriate mapped scientific citations and rerun full `checkWebsite`. The first saved failure is `broad-gsea-tools.md`. Keep both literature reports excluded from Pages until their release build is ready. The last successful Pages deployment does not establish a passing full website check. |
| **Some historical exports were rejected by automatic permission review for possible sensitive content.** | Preserve their refs locally; do not merge or export them indirectly. The nine `codex/handover-20261122-ownerNN` refs (01, 04, 29, 30, 31, 41, 43, 45, 59) and `codex/release-v0.3.1` remain outside main. Resolve the specific review before any export. Preserve unrelated dirty worktrees. |

## What is ready

The working private runtime is in WSL distribution
`JLinAlg-Omics-SAIGE-20261026`, with interpreter:

```text
/var/tmp/omicsmatic-scenicplus-runtime-20261123-384/environment/bin/python
```

It uses Python 3.11.8, SCENIC+ v1.0a2 at commit
`86fa25e6819919e2a824c14f7fd0d6e01e257ae9`, and 228 installed distributions.
Full dependency checks, selected imports and original console startup passed.
The handover rechecked interpreter identity, package count and the full
dependency check. This is an API-development environment, not an independently
reconstructed paper environment. The virtual environment uses symlinks into
its managed interpreter; copying only `environment/` is insufficient.

The original registry pins are preserved. Four mutable Git requirements were
pinned to the last declared-branch commits at the upstream requirements-file
commit date, 2024-04-23T13:13:37Z (`970ed3073ad5d512ff4385f3131b3ec81b09125d`):

| Dependency | Selected original commit |
| --- | --- |
| LoomXpy | `61995ff10940968eac2cee8fe48300ab477a15d0` |
| pycistarget | `8cd78f110f4eeeb38d8f7006a1100a13d636d984` |
| pycisTopic | `dca4bb6bc03ae1fba651075334bb19324331e4e8` |
| pySCENIC | `d2309fed83759a0e66e0a995ef3d2f40bf4d6c1a` |

These selections resolve incompatible current Git heads without patching vendor
source or upgrading the original registry pins. They do not identify the paper
authors' package editions. Keep the original ConfigArgParse 1.7 yanked-release
warning; do not conceal it by silently upgrading. Standard build constraints
were setuptools 68.2.2, wheel 0.43.0, Cython 0.29.37 and NumPy 1.26.4.

The original region-to-gene API and CLI produced exactly matching frames,
including dtypes and all 16 rows/values, on wholly artificial data: 64 cells,
four genes, 12 regions and 16 candidate pairs. The fixture includes a constant
accessibility region and literal `NA`, leading-zero and Unicode cell labels.
Nine API warnings and four undefined rho rows are retained. Vendor source
hashes matched before and after. This checks the original API/CLI operation;
no Java SCENIC+ workflow or full eGRN pipeline is qualified.

## Resume implementation in this order

1. Work in `D:/projects/JLinAlg/build/fu-1`; the source branch at this handover
   is `codex/scenic-catalog-20261123`. Preserve the root checkout's independent
   edits and all 19 worktrees. Read this note before older transfer instructions.
2. Implement our own Java controller and Python API caller against the unchanged
   licensed runtime. Reuse the project's process ownership, deadline, staging,
   runtime/source fingerprint and capture-audit contracts where appropriate.
   Start with region-to-gene, then cover the actual full eGRN, unpaired/metacell
   and resource roles below. A region-to-gene-only milestone must stay scoped.
3. Preserve original interface behavior. Expression and accessibility matrices
   are both cells by features; the original CLI uses MuData keys `scRNA` and
   `scATAC`. Search space has `Name`, `Gene` and `Distance`; retain explicit
   search-space/reference bytes. Preserve source defaults, labels and undefined
   fields. Do not assume the existing RNA-only AUCell profile is equivalent
   under this different dependency/runtime profile.
4. Qualify actual project RUN, fresh scientific recomputation VERIFY, REPLAY,
   warning/skip behavior, independent original comparisons and packaged execution.
   Test process ownership/deadlines and altered numerical-result refusals. Bind
   captures to the exact producer, runtime and references. Full VERIFY must rerun
   the science. Use synthetic data before any large biological input.
5. Admit only source-supported capabilities and paper roles through a guarded
   catalog transaction. Keep unknown paper editions unknown. Add the missed P153
   observation with exact primary/code provenance separately from family credit.
   Preserve all previous reviews and unrelated catalog bytes. Global dispatcher
   changes require a precise predecessor bridge, not relaxed old verifier hashes.
6. Continue the full cohort and prioritize impact. Review the 47 GraphPad Prism
   observations for bounded statistical operations already present or practical
   in Java; this is not a claim to reproduce that entire product. Inspect existing
   nf-core/newest iDEP development before restarting them. Current missing counts
   include nf-core 6, IMPUTE 6, MaxQuant 6 and DIA-NN 5 papers; SCENIC+ has five
   catalogued roles plus the missed P153 candidate. Frequency is a triage input;
   establish actual roles, terms, reusable components and scientific impact first.
   Continue all 1,020 pending uses and missed-provider screening, including
   supplements and linked code. Current iDEP terms do not establish legacy
   iDEP.96 rights. No completion ETA is established by this checkpoint.

## SCENIC+ source-role draft: not admitted

The private fresh census checked all 250 XML identities/DOIs and retained 27
matching paragraphs plus six additional structures. Six role drafts exist;
none has been applied to the canonical review ledger.

| Paper | Actual role to preserve |
| --- | --- |
| P008 | Full eGRNs from unpaired RNA/ATAC, same-cell-type random pseudo-multiome metacells, GBM/correlation links and reported 150 kb context. Do not replace metacells with an assumed shared-barcode intersection. |
| P021 | Comparison with an external SCENIC+ eRegulon resource. This does not establish a local SCENIC+ fit; STRING and decoupleR steps remain separate. |
| P047, P061 | Ensemble motif resources and Cluster-Buster sequence scanning on DMRs. Resource-author TOMTOM clustering does not establish a new local clustering run or eGRN fit. Establish Cluster-Buster's applicable license before compiling/porting it; that review remains open. |
| P094 | Full eGRNs with SEACells metacells, pycisTopic processing, explicit motif/ranking references and a reported 2 Mb upstream/downstream search context. Dependency editions are not SCENIC+ editions. |
| P153 | Direct original region-to-gene CLI calls in pinned author notebooks, rather than only reuse of published predictions. Keep 1 Mb and 150 kb branches and undefined correlations distinct. Author package edition/seed and final benchmark-field selection remain unestablished. |

For P153, the article is [DOI 10.1038/s41588-026-02695-8](https://doi.org/10.1038/s41588-026-02695-8),
PMCID PMC13447104. The [author analysis release v1.2](https://github.com/anderssonlab/scE2G_analysis/releases/tag/v1.2)
is pinned to `8096f6548b112f97f0f2eb739f25c6c9dbdba8b4`; this is an analysis
repository edition, not a SCENIC+ edition. Forty SCENIC+ notebooks plus README
and repository license were retrieved with exact Git blob checks. Ten
region-to-gene notebooks contain selected calls; nine source cells retain
adjacency-save logs. There are 192 textual ConstantInputWarning occurrences,
not 192 independent genes or experiments. No author notebook was executed.
Some author postprocessing initializes signs to zero: an undefined correlation
can consequently yield a zero signed score. Do not interpret that as defined rho.

Supplementary Note 2 remains unread. Three retained attempts failed (Springer
DNS, Europe PMC timeout, NCBI OA endpoint 404); no PDF was saved or rendered.
Retrieve and inspect the supplement before asserting its final score definitions.
The XML and pinned author code already support the direct-call role; retain
the remaining supplement limitation when correcting the observation.

## Local recovery map

The following paths are **local and absent from GitHub**. Preserve them at their
current paths until authorized machine transfer or an audited replacement exists.

| Location | Contents and recovery use |
| --- | --- |
| Owned checkout `build/scenicplus-370/` | License decision, primary-role drafts, pinned author notebooks, private original source, five install attempts, working runtime receipts and original API/CLI smoke evidence. Keep its private-source markers. |
| `build/scenicplus-370/runtime-install-5/` | Working runtime installation policy, lock, package/source pins, dependency/import/startup results. The separate Linux prefix ends in `-384`. |
| `build/scenicplus-370/original-r2g-1/` | Artificial input TSV/H5MU, source hashes, original API/CLI outputs, warnings and completion record. The API pickle remains private. |
| Root `build/prepare_scenicplus_handover_20261123_386.py` and `build/git-publish/20261123-scenicplus-handover-386/` | Ownership/index snapshots, fresh catalog/JAR/XML readback, live runtime checks, publication/readback and cleanup receipts. |
| Root `build/*scenicplus*20261123_37*.py` through `*385.py` | Original local intake/install/probe helpers. Inspect exact names and saved attempt receipts before reuse; do not rerun scripts that create immutable output directories. |
| Root `build/literature-review/xml/` | Frozen primary XML cache. Keep hashes and article identities. |

Four failed install prefixes `...-378`, `...-379`, `...-380` and `...-381`
are retained alongside their failures: Windows Git-pointer cwd, copied-interpreter
loader failure, legacy build metadata, then incompatible current Git dependencies.
Attempt 5 (`...-384`) supersedes them for use; no failed prefix is claimed usable.
Do not delete raw failures, original inputs or a runtime merely because it is
under `build/` or `/var/tmp/`. No blanket cleanup or worktree retirement is safe.

Keep the separate qualified RNA-only runtime at
`/var/tmp/omicsmatic-scenic-runtime-20261123-274` and its exact producer JAR in
the owned checkout:

```text
build/scenic-3/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar
199085384 bytes
SHA-256 ae5f81ed6a520a1a9f4be32f6558ecb2120b20ffbaa2248b701f8aafcecf3a2a
```

Retain preceding JARs for their own captures. A newly built JAR does not replace
their source-bound identity. Recheck the current catalog from the owned checkout
with the retained JAR and XML cache:

```powershell
python -X utf8 -B tools/literature/verify_scenic_catalog_checkpoint.py `
  --jar build/scenic-3/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar `
  --cache D:/projects/JLinAlg/build/literature-review/xml
```

Use the current catalog reader: it supplies exactly the archived predecessor
metadata to the older workflow reader while checking live scientific source.
Do not change old saved verifier hashes. This handover's public
[status receipt](scenicplus-handover-20261123/STATUS.json) contains summaries and
source identities only; it deliberately contains no licensed original code,
runtime, cache, original notebooks or source-bearing vendor traces.
