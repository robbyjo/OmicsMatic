# Hifiasm catalog integration

The catalog records seven primary-source roles across the fixed 250-paper
cohort. The [execution guide](HIFIASM.md) gives copyable build, setup, binding,
analysis and verification commands with prominent warnings and recovery steps.

| Paper | Reported hifiasm use | Boundary retained |
| --- | --- | --- |
| P068 | 0.19.5-r592 default HiFi; graph converted to two FASTA files | Partially phased; exact paper binary unresolved |
| P101 | 0.14.2-r315, `-l2 -u`, millet assemblies | RagTag scaffolding is separate |
| P109 | 0.19.5-r587 with HiFi, ONT and Hi-C | Complete flags and ONT length filter unreported; other assembly and QC steps separate |
| P164 | 0.19.5 with HiFi/ONT trio assembly | Revision and dirty Yak build unresolved; native helpers do not reproduce Yak Bloom admission |
| P171 | 0.22.0-r689 with HiFi/UL and separate ONT-only assembly | Reported UL selection retained; ONT-only subset unspecified; downstream gap filling separate |
| P235 | Reused HPRC trio assemblies; HGSVC 0.16.1 pseudo-haploid and 0.15.1 trio assemblies | Reuse distinguished from new execution; XML bibliography marker 11 is separate from the version text |
| P248 | 0.19.5 default HiFi cucumber assembly | Revision unresolved; RagTag, BUSCO, mapping and Merqury separate |

The source audit checks all 250 original XML hashes and DOIs, all 2,465 observed
tool anchors and all 1,000 reviewed roles. Seven hifiasm roles add three supporting
pointers; all 993 preceding reviews remain exact. The original XML is retained,
including the P235 bibliography markup; no automatic version-digit repair is
applied. Paper licenses and attribution remain in the fixed cohort records.

The [catalog proof](omics-2023-2026/hifiasm-catalog-validation.json) separately
records the one-row availability change, source audit, scientific gates and
browser checks. Exact inverse descriptors cover only the existing seven-path
metadata/dispatcher allowlist. Scientific sources and historical evidence are
unchanged. Historical catalog checks use explicitly recorded archived metadata
views while scientific files remain live and strict.

Current focused browser validation uses
`tools/literature/check_hifiasm_catalog_review_ui.cjs`. An earlier browser draft
captured during execution sealing remains byte-exact at its historical path;
the current wrapper uses the catalog-specific checker. Both capture snapshots
and the explicit reuse of general checks on identical page bytes are retained.

The count is 89 new scoped integrations: 17 native Java subsets and 72 upstream
adapters; 92 including three existing adapters. There are 252 missing tools.
The canonical executable is unchanged. This checkpoint does not reproduce whole
packages, papers or biological results, and the broader goal remains active.
