# Merqury roles in the frozen 250-paper cohort

The cohort interval remains 2023-10-23 through 2026-10-23. All 250 exact primary XMLs were checked; five matching method paragraphs belong to four papers. One additional reference-section pointer is retained. This source audit makes no runtime or whole-paper admission.

| Paper | Reported Merqury | Use |
| --- | --- | --- |
| [P101](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703678/) | 1.3 | Assembly k-mer completeness, consensus quality and false duplication. BUSCO, LAI and structural-variant checks have separate roles. |
| [P164](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597820/) | 1.3 | Parental k-mer databases for Verkko, plus assembly completeness. Yak supplies distinct hifiasm databases and phasing-error calculations. |
| [P171](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447107/) | 1.3 | Consensus quality and completeness from HiFi/Illumina read spectra. The article states defaults; separate versus combined read databases and exact k remain unresolved. |
| [P248](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447089/) | 1.1 | K-mer completeness, base-pair quality and false duplication. Other aligners, assembly and gene-completeness tools remain separate. |

`ROLES.json` preserves raw catalog source/version fields, exact paragraph anchors and paraphrased reviewed uses. P164's prior completeness review is expanded to include its parental-database role. Three observations become eligible for new source-review credit; no paper, observation or provider is added. Runtime qualification and catalog admission are separate steps. Exact author commands, read/reference preparation, k and compression choices are not inferred.

Run the read-only audit with the retained original XML cache:

```sh
python tools/literature/merqury_primary.py --root /absolute/project --cache /absolute/original-xml-cache
```

Missing permitted originals give SKIP3. Changed, incomplete or inconsistent originals give FAIL2. Restore the exact inputs/parser and preserve historical hashes and failed attempts. Raw article contexts remain private, and literature HTML reports remain withheld from Pages.
