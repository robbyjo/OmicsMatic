# Additional software audit: current draft checkpoint

**This is an incomplete source-audit checkpoint. It changes no main catalog entries or implementation badges.** The full 250-paper review and missing-tool implementation goal remain open. Treat candidate matches as prompts for individual review, not as evidence of software execution, package identity or an available analysis.

The new scanner reads the exact frozen cohort of250 primary XML sources and verifies each DOI and source hash. Its current88 name definitions include one alias for an existing family. They produce227 candidate paper-name pairs and350 named primary anchors. There are178 manually authored anchor reviews, with172 still awaiting individual curation. In115 candidate paper-name groups, every named anchor has a review. These units differ from the main catalog's paper-tool use count.

All125 additional paragraphs in the recorded reading batches have been read across the goal turns, alongside the earlier95-paragraph plugin intake. Candidate expansion exposes further source paragraphs, so completed reading batches do not imply that the expanded queue is exhausted. The reading ledger records exact private batch hashes. Public records contain paraphrased roles and source anchors; raw primary full texts remain in the ignored cache.

The main catalog remains at250 papers,430 families,2,669 observations and1,130 individually reviewed uses, with105 admitted new scoped integrations. The separately qualified [wesanderson component](WESANDERSON037_WORKFLOW_DEVELOPMENT.md) is pending catalog admission. Current draft findings and pending names are not silently added to those totals.

## Findings that affect tool selection

- Six cell-counter matches name instruments; five genuine plugin anchors occur in P005, P018 and P165. Hardware counts cannot establish an ImageJ Cell Counter workflow.
- Two `patchwork` occurrences describe biological mosaics; the P054 plotting-package use is distinct.
- PyTorch Geometric, Harmony-PyTorch and PyTorch-Lightning have separate identities and version contexts. Harmony-PyTorch0.1.7 is not a base PyTorch edition or an R Harmony release.
- The paper writes `Dye Assistant` with a space. The earlier unadmitted detector failure is retained; the corrected pattern finds its acquisition-module role.
- P121 reports NumPy `1.12.5`, including in the text with citation labels removed. That literal is retained. Its edition availability remains unverified; do not silently select a different release. Resolve the author environment or explicitly document a different qualified profile before attempting an exact-edition analysis.
- Visiopharm2022.03 and Nuclei detect AI App2023.01.2.13695 are separately versioned. Their pretrained-app configuration is not recovered by naming the parent platform.
- P184 names Theano as a historical implementation replaced by PyTorch. That anchor does not establish current Theano execution.
- Source roles distinguish the Cellpose segmentation model, separate custom measurement/colocalization code, Bin2cell output files, schard import, NumPy operations, BioRender illustrations and acquisition systems.

The audit also records analytical gaps such as AllelicSeries/COAST-SS, MACHINA, Captum and directed mediation with semopy. Exact author revisions, inputs, weights and settings remain unresolved where the source does not provide them. A new name or an existing generic method is insufficient to claim original-tool compatibility.

## Inspect and reproduce the checkpoint

- [Source census and all reviewed/pending anchors](software-frontier-audit/CENSUS.json).
- [Sixteen observed-source regression checks](software-frontier-audit/REGRESSIONS.json).
- [Additional environment names awaiting wider source assessment](software-frontier-audit/PENDING_ENVIRONMENT_NAMES.json).
- [Current source/baseline validation](software-frontier-audit/VALIDATION.json).
- [Scanner and recovery instructions](../../tools/literature/software_frontier/README.md).

```powershell
python tools/literature/software_frontier/intake.py `
  --out build/literature-review/monocle-runtime/new-software-source-audit
python tools/literature/software_frontier/test_intake.py `
  --census build/literature-review/monocle-runtime/new-software-source-audit/CENSUS.json `
  --out build/literature-review/monocle-runtime/new-software-regression-check
```

Use NEW paths and the exact permitted XML cache. **SKIP3 skips the source audit** when required cached inputs cannot be read. Restore the exact preserved sources where permitted and retry. **FAIL2 admits no audit:** retain the failure and warnings, correct the stated name/identity/anchor error and rerun the complete scan. No package installation or paper analysis occurs in this intake. These checks bind source identities and selected regression cases; they do not automatically prove every human semantic assertion or complete paper reproduction.
