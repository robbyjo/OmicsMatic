# Prism source correction for P215

The canonical catalog now records Prism in **47 papers**. P215
([primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11480806/),
DOI [10.1016/j.ajhg.2024.07.022](https://doi.org/10.1016/j.ajhg.2024.07.022))
used the unversioned name `Prism`, which the earlier expression missed.
Its five matching primary paragraphs support **one paper/tool record, one
individual review and four supporting pointers**. They are not five software
executions or five independent observations.

All 250 exact primary XML identities are checked before admission. The current
totals are 446 families, 2,694 paper/tool observations, 1,219 individually
reviewed uses and 1,475 awaiting individual review. The frozen cohort, source
dates and every previous observation/review are preserved. The admitted counts
remain 127 scoped integrations (23 native Java, 104 upstream), 260 missing
families and 130 integrations including three existing adapters. Prism remains
missing at the family level; source curation adds no implementation badge.

The new primary anchor is paragraph 26, “Statistical and genomic analysis.”
It describes two-sided unpaired t-tests on replicate-average measurements in
Superplot quantification. Paragraphs 12 and 25 separately name Prism for IHC
t-tests. Paragraphs 11 and 20 list Prism among software for RNA-seq and
ChIP/ATAC statistical work without assigning specific operations. Those four
paragraphs are retained as supporting evidence with exact source hashes and
sections in [CENSUS.json](prism-source-catalog/CENSUS.json).

**Unresolved settings remain explicit.** The word “latest” does not identify a
numeric Prism release. Equal-variance versus Welch assumptions, exact author
inputs, outcomes/contrast families and historical settings are not recovered.
Replicate averages are the stated test units; the individual captured images
must not silently become independent biological replicates. This review does
not reproduce author data or select a test automatically.

Current source builds provide separate native
[Tukey–Kramer](../vignettes/posthoc-comparisons.md) and
[independent-sample Dunn](../vignettes/dunn-comparisons.md) components for other
explicitly justified models. Their existence does not establish that P215 used
either method or that every Prism role is implemented. Ordinary, repeated and
unequal-variance Dunnett contexts remain separate outstanding work.

**Checksum discrepancies retained:** the predecessor's current catalog checksum
list omitted 71 existing catalog files and was stale for five canonical files:
`papers.json`, `tool-inventory.json`,
`summary.json`, `README.md` and `WARNINGS.md`. Their actual predecessor bytes
match the recorded earlier catalog admission and historical Git commit. The
All 71 previously unlisted files are checked against their actual historical
Git bytes. The original checksum list is retained verbatim as
`prism-source-catalog/PREVIOUS-SHA256SUMS`. The current catalog checksum list is
regenerated for the current files. Historical qualification files, original
scientific results and their recorded fingerprints are not refreshed.

The exact eight-file predecessor is retained in
`prism-source-catalog/catalog-before.zip`, tied to actual Git commit
`f76e262494152572d3514dc7845c7c460e74c2ae`. The source census, effective
485-definition registry, verifier source and inverse of the single review
append remain available. This expands the existing Prism name expression and
preserves all other definitions. It does not rerun extraction or overwrite the
old extraction cache. Historical catalog generators must not overwrite the
cumulative current catalog.

Recheck using the current source entry point and the retained primary cache:

```sh
python3 -B tools/literature/common_statistics/prism_catalog.py --verify \
  --primary-cache PATH_TO_ORIGINAL_XML_CACHE --out build/NEW_PRISM_CHECK
```

`--plan` computes a reviewable transition without writing canonical metadata;
`--apply` admits that exact transition only against its verified predecessor.
Neither mode performs a scientific analysis, installs software, deploys the
website or publishes a release. Later catalog changes need their own recorded
transition; do not force this frozen checkpoint over a later catalog.

**Required actions:** missing or forbidden scientific prerequisites mean
**SKIP3**, with the dependent analysis skipped. Supply the named permitted
source/runtime/license/reference or leave that analysis skipped. Install only
where allowed. Other input, numerical, source or integrity failures mean
**FAIL2**, with no complete result admitted. Keep complete inputs, warnings,
logs, partials and historical evidence; resolve the specific cause and retry
new output. Do not repair identifiers, remove failures, substitute author
versions or refresh historical fingerprints to force success. This catalog
source check needs the original cached XML and Python; it does not require a
Prism installation. Full semantic review and implementation of the 250-paper
cohort remain open.
