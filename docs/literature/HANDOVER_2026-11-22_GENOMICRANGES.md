# Toolbox handover — 2026-11-22

**The 250-paper collection is complete; implementation and semantic review are
unfinished.** Canonical counts are **148 scoped families: 27 native + 121 upstream**
(151 including three pre-existing adapters), **1,661 reviewed uses and 1,062 pending**.
There are 446 named labels, 485 provider definitions and 2,723 observations.
The frozen collection window is 2023-10-23 through 2026-10-23: 71 Nature,
104 Nature Genetics and 75 AJHG papers. Do not shift this window silently.
This checkpoint adds **zero family credit and zero paper-use reviews**.

## Resume here

Use the newest GitHub `main`, then read this document and
[STATUS.json](genomicranges-handover-20261122/STATUS.json).
The development owner is `D:/projects/JLinAlg/build/gr-1`, branch
`codex/genomicranges-workflows-20261122`, based on qualified SKAT/catalog commit
`9cc492314af013860818c2151d126425bf81c352`. Publication receipts are in
`D:/projects/JLinAlg/build/git-publish/20261122-genomicranges-114`.
The shared root remains an older, dirty checkout; do not reset it or substitute
its catalog for current main. Preserve all **70 worktrees**, their indexes and
all ignored scientific evidence. Prior handover entries retain historical counts.

The public development packet contains original-reference fixture tables, the
reference generator, driver snapshots, source/runtime intake receipts, build
results and an integrity manifest. See
[EVIDENCE.json](genomicranges-handover-20261122/EVIDENCE.json) and
[PRIVATE-TRANSFER.json](genomicranges-handover-20261122/PRIVATE-TRANSFER.json).
Driver snapshots record their actual historical paths; inspect and adapt paths
on another machine, use NEW attempt directories, and never blindly rerun a
driver that creates or publishes Git branches.

## Latest completed baseline

Original SKAT 2.2.5 prepared workflows are qualified and admitted as **one upstream
family** on main. SKAT-O is an operation/alias, not extra family credit.
[The qualification record](skat225-qualification/QUALIFICATION.json) and
[integration scope](skat225-qualification/INTEGRATION.md) supersede the older SKAT
draft handovers. Qualification includes 722 focused tests, 89 public original-R
matrix cases plus six additional original cases, actual replay/dependency/capacity
controls, 33 resealed verifier controls, and catalog validation. This is Windows
x86_64 Java 21/R 4.6.1 qualification, not a cross-platform original-R claim.
Keep recorded package build-version warnings prominent.

The qualified SKAT producer stays at:

`D:/projects/JLinAlg/build/sk-2/build/skat-runtime-1/normal-gradle-8/project-build/cli/omicsmatic-0.4.0.jar`

SHA-256 `d87eb1597fcdd48214a4ee6fb72b7b2e4cb4c7b0ce547d1782bcd4303ae902cc`.
The independent native ACAT producer stays at:

`D:/projects/JLinAlg/build/sa-1/build/skat-acat-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar`

SHA-256 `9965571a5f24f6d701b4da693d20b8c44e58c4359da54dd932476dc1ee19fd7b`.
Never replace either with the newer development JAR. Only complete admitted
exports with successful verification are results. Old invalidated constant-trait
SKAT captures remain retained and unadmitted. Offline VERIFY checks integrity
and relationships; it does not refit a model or certify scientific convergence.

## GenomicRanges progress and limits

The new Java library component `org.jlinalg.genetics.GenomicRangeGeometry`
implements **scalar promoter geometry and one-based out-of-bounds row indices**.
It supports literal chromosome names, `+/-/*` strands, zero-width ranges,
negative coordinates and known/unknown/circular sequence bounds. It refuses
unsupported integer overflow instead of repairing missing endpoints.
It does not implement S4 containers, metadata coercion, vector-valued distances,
overlaps, nearest-neighbor queries, sequence extraction, liftover or a global CLI.
It is **not a replacement for the GenomicRanges package or a reproduced paper**.

Actual GenomicRanges 1.64.0/R 4.6.1 generated 17 cases containing 4,096 artificial
ranges. Three selected installed helper bodies/formals matched the official
1.64.0 source. Original promoters preserved input metadata in those captures.
The Java tests compare all saved coordinates and row indices, including literal
UTF-8/whitespace labels, unknown/circular sequence metadata and the empty-input
early return. The normal build passes **747 focused tests (zero failures/skips)**, including
21 new component tests. The saved result pins the development JAR; it does not qualify any reported historical package edition.

Official source archives **1.44.0, 1.58.0, 1.60.0 and 1.64.0** were privately
downloaded and checked against their DESCRIPTION versions; all declare
Artistic-2.0. A private portable R **4.1.0** was extracted and its exact version
checked, without executing its installer, changing registry/defaults, or modifying
old libraries. **The historical GenomicRanges dependency profiles are not yet
installed or qualified.** The default R 4.6.1 libraries lack GenomicRanges; a
separate existing read-only private library contains 1.64.0. Do not relabel that
modern runtime as 1.44.0/1.58.0/1.60.0 execution.

Source screening found seven actual paper uses:

| Paper | Reported role | Edition and unresolved details |
| --- | --- | --- |
| P013 | Extend array midpoints with `promoters()`; identify out-of-chromosome ranges with internal `get_out_of_bound_index()` | Edition unstated; sequence extraction belongs to other packages. Preserve the paper's literal command spelling. |
| P032 | `nearest()` assigns genes by nearest TSS to CUT&RUN regions | 1.44.0 and R 4.1.0; TSS construction and tie/strand options are unstated. |
| P051 | Common peak-set creation/quantification before merging single-cell objects, with Signac | 1.58.0; exact GenomicRanges function unstated. Do not assume `reduce()`. |
| P094 | `subsetByOverlaps()`/`findOverlaps()` intersects accessible or linked peaks with SNPs/regulatory sets after separate liftover | Both 1.58.0 and 1.60.0 reported. Literal R label is `v2025.09.0 + 387`; do not reinterpret it as an R engine edition. Already reviewed. |
| P137 | `distanceToNearest()` links predicted TF binding sites and recurrent loss peaks under the stated distance threshold | Edition, query/subject orientation and ties unstated; all-within-distance is not equivalent. |
| P158 | `distanceToNearest()` links ChIP-seq peaks to nearest promoters, defined as 2 kb upstream/200 bp downstream of TSS | Edition unstated; promoter definition does not establish a call to `promoters()`. |
| P242 | Map SNPs to DHS intervals after separate CrossMap coordinate conversion | Edition/function unstated. Already reviewed. |

Exact primary paragraph indices and decompressed XML hashes are retained in the
development role-screening record. P013/P094 long paragraph tails still need
careful curation; inspect full source, not the truncated console previews.
Five of these uses remain pending; preserve the two existing reviews and avoid
duplicating their credit. GenomicRanges remains **method-overlap** in the catalog.

## Remaining work, in order

1. Verify the packet and build receipts. Retain any failure and create a NEW
   attempt after fixing its cause. The native component's source-matched modern
   references do not establish S4 or historical-edition equivalence.
2. Privately provision exact **GenomicRanges 1.44.0, 1.58.0 and 1.60.0** package
   dependency closures using permitted official archives/binaries. Use the saved
   portable R 4.1.0 for the 1.44.0 paper profile. Keep modern/legacy runtime
   libraries separate and record actual R versions/build warnings. No Rtools,
   make or C/C++ compiler was found during screening; a private install may be
   attempted where allowed, but a missing/prohibited dependency must skip its
   dependent analysis with the remedy below.
3. Implement a typed original-R adapter for the **actual paper operations**:
   overlap/subset, nearest/distance, promoters/bounds, and common-peak handling
   only after establishing the paper's operation. Preserve full RDS objects,
   metadata, seqinfo, warnings, RNG where applicable and original raw failures.
   Do not substitute the old positive-width, half-open Java interval API for
   general one-based GenomicRanges/S4 behavior.
4. Preserve source nearest/tie behavior. A direct 1.64.0 probe with a point at100
   and equidistant points at90/110 returned subject1 for arbitrary selection,
   but subject2 **alone** for `select="all"`. That branch chooses the preceding
   side rather than combining opposite-side ties. Confirm exact editions; do
   not silently "fix" it. Adjacent ranges have distance0; distanceToNearest
   drops unmatched queries whereas nearest returns missing indices. Bounds
   ignore circular TRUE/unknown lengths but apply to known length with unknown
   circularity. Promoters do not trim; integer intermediate overflow matters.
5. Finish RUN/VERIFY/REPLAY and normal-JAR/global-command qualification with
   direct original comparisons, ties/strand/zero-width/metadata/seqinfo cases,
   missing/wrong dependency skips, capacity/process failures, replay ownership,
   and public integrity/refusal tests. Changing global dispatch affects saved
   SKAT source fingerprints: preserve original producers and old hashes; create
   a new declared qualification context instead of refreshing old evidence.
6. Re-audit all 250 sources for missed GenomicRanges roles, curate the five
   pending uses and preserve the two existing reviews. Only after all gates
   pass consider one upstream family admission, regenerate/validate the catalog
   and derive counts from actual output. Do not pre-credit helpers or aliases.
7. Continue **high-impact missing families**, including FUSION and g:Profiler;
   screening signals also include Squidpy, smartpca and MR-PRESSO. Reassess
   impact, paper frequency, existing overlap, licensing and feasibility before
   choosing the next family. Implement feasible methods natively in Java and
   use tested exact upstream adapters for substantial software. Complete the
   remaining **1,062 paper/tool use reviews** and semantic missed-tool audit.
   The full objective must not be narrowed to this checkpoint.

## Warnings, user decisions and transfer

**SKIP3 means the dependent analysis was skipped.** Read the named prerequisite
and exact allowed version in WARNINGS/logs. Supply it or download/install into a
new private profile where permitted. If installation is prohibited, retain the
skip and explain which analysis is unavailable and exactly what the user must
supply. Never claim a result or silently substitute a nearby package edition.

**FAIL2 means no complete admitted result.** Preserve raw/null/failed/partial,
superseded and invalidated evidence; fix the stated input, numerical, capacity,
process or integrity cause, then retry into NEW output. Never silently repair
identifiers, NaN/Inf, endpoints, weights or unreported scientific choices.
Warnings/errors and required actions must be prominent to end users.

Standing decisions: downloads/private installs are authorized where the operating
environment allows them; MEME/FIMO educational/research/nonprofit use confirmed;
GATK 3.8 academic noncommercial terms accepted; **Cell Ranger excluded**; evaluate
newest iDEP instead of relying on uncertain legacy iDEP.96 terms. Unfinished
`literature-review.html` and `literature-warnings.html` remain excluded from Pages.
This checkpoint is not a release, a whole-tool implementation or goal completion.

**A GitHub clone is insufficient for a full handover.** Before machine retirement,
transfer all 70 worktrees and ignored build/evidence/runtime trees through an
appropriate private channel, preserving links/junctions explicitly. This includes
primary XML/PDF/XLSX, private author source, full RDS captures, original archives,
portable/private runtimes, old JARs, failed attempts and publication receipts.
The public packet lists concrete current paths and hashes; the older private
trees must remain intact too. No private transfer is claimed here.

**Historical export remains blocked:** automatic approval review rejected
publishing nine broad unpublished snapshot branches and `codex/release-v0.3.1`
because they may contain sensitive material. Specific approval for that export
remains pending. The snapshots and private incremental Git bundle remain local;
do not upload the bundle or merge those payloads as a workaround. Private shared
root manuscript/rendering outputs remain local. This current-task push includes
reviewed development source, artificial references and handover material only.
