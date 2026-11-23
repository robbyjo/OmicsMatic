# SCENIC+ workflows and paper-specific uses

This integration provides one bounded upstream SCENIC+ family through
`scenicplus-tools`, reviews six paper uses and corrects a missed P153 positive
observation. Source-role review describes what the papers did. It does not
reproduce their environments or biological outcomes.

Use the [main-command guide](SCENICPLUS_GLOBAL_WORKFLOWS.md) for explicit inputs,
RUN, inspection, fresh VERIFY and REPLAY. A current complete source-built JAR is
required. The qualified private Linux runtime is original SCENIC+ v1.0a2,
unmodified, under the accepted internal academic terms.

| Paper | How SCENIC+ is used | Current boundary |
| --- | --- | --- |
| P008 | Same-label random metacells combine unpaired RNA/ATAC; enhancer/TF/gene network inference within a reported 150 kb window | Original unpaired preparation and prepared network components are tested; exact edition, seed, resources and sampling membership remain unknown |
| P021 | Compare 44 prioritized TFs with 582 published developing-neocortex eRegulons | External-resource comparison; no local SCENIC+ fit established |
| P047 | TF annotations for gene categories; motif-cluster resources and Cluster-Buster DMR scanning for a separate PCC-based network | Resource use reviewed; scanning stays SKIP3. The PCC graph is distinct from a SCENIC+ eGRN fit |
| P061 | Motif-cluster resource and Cluster-Buster scanning; cisTarget enrichment of positive/negative DMR sets with NES > 3 and leading-edge assignment | Prepared cisTarget enrichment available; scanning and required scores stay SKIP3 pending permission |
| P094 | SEACells-derived metacells, cisTopic candidates, human motif references and eGRNs with 2 Mbp upstream/downstream search space | Supply externally prepared matrices/candidates. SEACells targets 75 cells/metacell with 15 initial eigenvalues; these are distinct from random metacells |
| P153 | Benchmark enhancer-gene predictions; pinned author code records paired preparation, search-space and GBM/Spearman R2G calls plus formatting | Prepared R2G tested. Paired preparation, annotation/search-space generation and benchmark formatting are separate. Both 1 Mbp/150 kb author branches exist; final Supplementary Note 2 remains unavailable for independent resolution |

Every SCENIC+ edition remains unreported. P094's SEACells 0.3.3, pycistopic
2.0a0 and Scanpy 1.8.2 are dependency versions. P153's author analysis v1.2 is
not a SCENIC+ edition. Its corrected observation uses a literal Results mention
and pinned author code. Its automated extraction score is null because no
automated Methods-extractor score is claimed.

[Frozen roles](scenicplus-catalog-sources/ROLES.json) and the
[author-source manifest](scenicplus-catalog-sources/AUTHOR-SOURCES.json) retain
summaries, paragraph hashes and identities without redistributing primary texts
or raw notebooks. The manifest pins 42 original Git blobs from
[scE2G_analysis](https://github.com/anderssonlab/scE2G_analysis/tree/8096f6548b112f97f0f2eb739f25c6c9dbdba8b4).
Saved author outputs are source evidence, not project-executed results.

## Warnings and required actions

- **SKIP3: dependent analysis skipped.** Supply/install the named permitted
  exact runtime/input and retry NEW output, or retain the skip. Exact author
  reproduction stays skipped where edition, settings or inputs are unavailable.
- **ClusterBuster scanning stays SKIP3.** Supply permission covering the exact
  runtime before installation or execution. Prepared motif enrichment is a
  separate supported operation. SCENIC+ permission does not establish these
  terms. Unconfirmed permission does not establish that permission cannot exist.
- **FAIL2: no complete result.** Preserve raw, partial and failed evidence; fix
  the named cause and retry NEW output. Original masked-array and empty-category
  cistrome failures remain failures. Read reports, warnings and field ledgers.
- **Keep undefined values.** Preserve NaN/Inf and eligibility flags. P153 author
  formatting can assign a zero sign where correlation is undefined; that does
  not define correlation. Original unbalanced DEM may ignore a cap; inspect
  actual selected counts and source warnings.
- **Inspection checks identities only.** Require fresh VERIFY for scientific
  comparison. Retain exact whole producer JARs; restore them rather than
  refreshing historical hashes. No execution authentication is implied.
- **Keep licensed material private and unmodified.** Keep source, caches,
  trusted fitted objects and source-bearing traces out of public Git. Check
  permission before transfers or full WSL exports. Never load untrusted cisTopic
  pickles. Cell Ranger remains excluded.

## Maintainer transaction

Run `python -B tools/literature/close_scenicplus_catalog.py --help` from this
checkout. Every command needs NEW `--out` under `build/` and these prerequisites:

| Argument | Exact retained content |
| --- | --- |
| `--cache` | Frozen 250-paper XML cache |
| `--jar` | Global producer SHA-256 `50e126903c8787e24742c5e710220600581de13cb6eeb98a6fd08a4fab84c222` |
| `--private-root` | `scenicplus-dispatch-497` saved/scientific evidence |
| `--predecessor-root` | Its physical `predecessor-source` context |
| `--predecessor-jar` | RNA SCENIC producer SHA-256 `ae5f81ed6a520a1a9f4be32f6558ecb2120b20ffbaa2248b701f8aafcecf3a2a` |
| `--author-root` | Original author tree from `p153-code-1/original` |

Use `--plan` to create a preview and eight-file predecessor archive. Inspect
using `--inspect --plan-dir SAVED-PLAN` and another NEW output. Apply using
`--apply --plan-dir SAVED-PLAN`, again with NEW output. Verify afterward with
`--verify`, the same prerequisites and NEW output. The installer refuses changed
predecessors, sources, previews or qualification; it preserves concurrent edits,
rolls back its own failed writes and retains failed metadata.

The unchanged historical RNA SCENIC reader executes in its archived physical
source context with Git work-tree overrides. The current global reader checks
the precise source extension and exact producer separately. Every command
rechecks all 250 primary XML and 42 author blobs. No historical verifier/hash is
refreshed and no scientific model is rerun by catalog verification. Unrelated
observations and 1,701 unrelated previous reviews are unchanged. The existing
P061 review is expanded with its original record retained in the before archive;
five new reviews are added. Collapsed historical
notices retain the counts and warnings of their original checkpoints.

No release asset or deployment is claimed. The full 250-paper implementation
and semantic-completeness goal remains open.
