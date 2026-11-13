# Additional named-software source audit

**Draft intake, not catalog admission.** Name matches are candidates, not evidence that every named tool ran or that an implementation is available. This directory records a fresh census of the exact existing 250-paper primary-source cohort and manually authored source-anchor reviews. The main inventory, paper records, existing reviews, implementation badges and scientific verifiers are unchanged.

The review distinguishes analytical operations, data import, acquisition, visual inspection, presentation, environment-only package listings, references to earlier output files, historical implementations, hardware and ordinary words. It retains reported edition strings and unresolved operation/model/input details. A paragraph-level review is not a whole-paper reproduction. Even when every named anchor for a candidate paper-name pair is reviewed, admitting it requires a separate family-identity, role-coverage and implementation assessment.

`candidate-registry.json` contains proposed name patterns, including the spaced `Image J` alias for the existing family. A pattern is not a confirmed new family. PyTorch Geometric, Harmony-PyTorch and PyTorch-Lightning have distinct patterns; their suffixes are not counted as standalone PyTorch names. `anchor-reviews.json` stores the manual classifications and summaries with exact primary DOI, PMCID, paragraph index, section and paragraph hash. `reading-ledger.json` records completed reading batches; it does not assert that all newly discovered anchors have been curated. Later candidate expansion can expose additional paragraphs requiring review.

```powershell
python tools/literature/software_frontier/intake.py `
  --out build/literature-review/monocle-runtime/new-software-source-audit
```

Use Python3.11 or later and a NEW output directory. The exact cached primary sources must be present at `build/literature-review/xml/<PMCID>.xml.gz`. The helper checks all250 unique identities against the frozen source cohort, each uncompressed XML hash and DOI, every manual source binding, and stable code/curation inputs before and after scanning.

**SKIP3 skips the fresh source audit** if a required source/cache file cannot be read. Restore the exact preserved cache matching the recorded hash only where permitted, then retry a new output path. Do not rerun a historical catalog generator over current metadata to recover the cache. **FAIL2 admits no audit:** retain `FAILURE.json`, warnings and source intake, correct the named pattern/anchor/identity error, and retry NEW paths. These commands do not download software, install dependencies or execute paper analyses.

The output includes every candidate match, reviewed and pending anchor counts, explicit exclusions, fully reviewed named-anchor groups, and zero-hit patterns. Unreviewed source contexts remain in the ignored build directory. Large BioRender-only paragraphs have a separate explicitly labeled targeted-context queue; those excerpts are not represented as complete paragraph reads. Mechanical validation checks identities, completeness and source binding; semantic assertions remain the responsibility of the individual reviews.

The full requested paper/tool audit and software implementation goal remains open. This intake supplies no new implementation credit by itself.
