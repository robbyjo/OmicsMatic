# Six nf-core source reviews

The catalog contains 250 frozen papers, 1,864 reviewed uses and 870 pending
uses. The implementation total remains **181 scoped families: 36 native and
145 upstream**. This checkpoint adds six source reviews and zero families.
The full implementation goal remains active.

All 250 saved primary sources were checked against their retained hashes and
DOIs. Eleven nf-core method paragraphs cover six papers and ten workflow
branches. The review preserves the extractor's original near-name version
fields and separates reported workflow steps from subsequent statistics and
visualization. Earlier reviews and provider definitions are unchanged.

| Paper | Reviewed use and remaining execution boundary |
| --- | --- |
| P002 | Distinct eager capture mapping, pathogen screening and ancestry workflows; reported editions and references differ. Historical databases, the eukaryotic selection and custom ancestry steps remain unresolved. Unassessable pathogen reads remain unassessable. |
| P027 | rnasplice 1.0.4 and downstream splicing analyses. The paper reports STAR 2.7.9a; this pipeline source selects 2.7.10a. Its stated event threshold uses raw P < 0.05 and absolute delta PSI >= 0.05. The discrepancy is retained. |
| P032 | chipseq 2.0.0 alignment/filtering, with separately described spike-in scaling, background selection and peak calling. The complete author recipe is unresolved. |
| P050 | Unversioned ChIP and literal ATAQ-SEQ workflows. Mapping the latter to atacseq is an explicit inference. Reported bwa2 and Picard duplicate-merging descriptions are retained. |
| P169 | chipseq 1.2.1 for six targets across six cell lines and atacseq 1.2.1 for eight cell lines. ChromHMM is a separate downstream step. |
| P228 | hic 2.1.0 with reported digestion, TAD and MAPQ settings; downstream resolutions, normalization and differential analyses remain distinct. The source's Unicode Bowtie option requires validated interpretation before execution. |

The complete structured roles and source anchors are in [ROLES.json](ROLES.json).
[CENSUS.json](CENSUS.json) records the complete search population;
[qualification](QUALIFICATION.json) pins this checkpoint. The current effective
492-definition registry is [here](../nfcore-source-review-admission/REGISTRY.json).
Earlier qualification files retain their historical counts.

**Execution warnings and required actions:** source review admits no runtime
result. Require each adapter's complete result and fresh verification before
downstream use. SKIP3 (exit 3) skips analysis for a named missing or forbidden
prerequisite: complete its permitted setup or retain the skip. FAIL2 (exit 2)
admits no complete result: preserve the attempt, correct the named cause and
use a new session when its immutable request must change. Do not substitute
unreported versions, databases, identifier repairs or statistical thresholds.
Cell Ranger remains excluded; MaxQuant runtime analyses remain skipped and
the tool remains pending.

The separate full original rnasplice test completed all 128 tasks, but its
capture was refused because our output contract incorrectly required a
top-level `rmats` directory. The original pipeline publishes
`star_salmon/rmats`. The failed capture and all original outputs were retained.
A fresh run with the corrected output contract is tracked at
`/opt/omicsmatic-nfcore-rnasplice104-20261127-2/engine`; follow its saved state
and preserve it while active. Neither trial reproduces P027: they use the
original four-sample human chromosome-X test, and the STAR discrepancy remains.
No whole nf-core family, whole-paper replication, release or literature Pages
publication is admitted here. The development reports remain withheld from
Pages. Publication, preservation and cleanup closure will be recorded in
COMPLETED.json after those checks finish.
