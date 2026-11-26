# ASCAT source-role audit, second context

This audit retains the original 250-paper cohort, all eight direct ASCAT/ascatNgs paragraph contexts and four distinct paper roles. It adds the original P129/P146 supplements retrieved through the [official PMC Cloud Service](https://pmc.ncbi.nlm.nih.gov/tools/pmcaws/). PDF SHA-256 and official MD5 identities, source URLs and reviewed pages are recorded in `CENSUS.json` and `ROLES.json`. Raw PDFs, article text and review images remain private. The earlier main-text audit and its failed retrieval/legacy-R attempts remain historical evidence with unchanged seals.

P111's targeted ASCAT3.2.0 analysis, P129's ASCAT2.5.2 benchmark comparator, P143's import of released TCGA estimates, and P146's exome fitting are distinct roles. P146 reports penalty100 but leaves the core R version unreported; its separate WGS workflow uses internal ascatNgs4.5.0. Its Methods/Results differ at equality to90percent goodness of fit. No software version, parameter, coordinate convention or event-matching rule is inferred to complete an author recipe.

`SUPPLEMENTARY-TOOLS.json` catalogs two additional names found on P146 supplement page7: ConstructASCATFiles and PREASCAT. Their source snapshots were inspected read-only. No source-wide permission declaration was identified for their own analysis code; third-party SAMtools/IDE notices do not establish terms for that code. **SKIP3: these preprocessing runtime analyses were skipped.** Obtain applicable permission or licensing from the rights holders before runtime integration, or retain the skip. Neither tool was installed, executed or redistributed. Their paper versions are unreported, and the inspected commits are not asserted to be the author commits.

These two names remain an explicit supplementary catalog addendum pending canonical supplement-evidence integration. They are outside the current451-label/490-provider/2,731-observation counts. The existing registry is not a discovery ceiling; full-paper and supplement completeness review remains open. A source-role review alone adds no runtime or family credit.

With the retained permitted inputs, verify all original identities using:

```text
python -B tools/literature/ascat_primary.py --cache /absolute/original-250-XML-cache --supplements /absolute/original-PDF-directory
```

See the [workflow guide](../../ascat-tools.md) for commands, source profiles, warnings and recovery steps. No raw article content or private runtime/process evidence is published here.
