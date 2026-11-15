# Original celldex 1.14.0 reference workflows

The project adapter is
[`tools/literature/celldex114/run.py`](../../tools/literature/celldex114/run.py).
Use the [complete runtime/request/retrieval guide](../../tools/literature/celldex114/README.md)
for copyable commands, explicit permission flags and recovery steps. This
development document does not itself grant catalog credit.

P051 [DOI 10.1038/s41586-025-09807-0](https://doi.org/10.1038/s41586-025-09807-0)
reports celldex **1.14.0** for obtaining the literally described MouseRNAseq
ImmGen reference profiles used with SingleR annotation in its PDAC/MI context.
The human-neutrophil subsection and reference wording remain unchanged. Exact
reference functions, snapshots, gene-ID choices and the complete author
environment are not established. ImmGen is a biological reference; Celldex
Therapeutics is a company and adds no software use. SingleR annotation and
SPOTlight deconvolution remain separate tools.

## Original source and comparison environment

The exact official Bioconductor 3.19 archive has 56 regular source members:

`celldex_1.14.0.tar.gz`, 412,271 bytes, SHA-256
`46723b3799792f550b2f74cb161a19f581b042080e9dd9455906a82c0cb4b094`.

Its DESCRIPTION declares GPL-3. Complete file identities, original archive URL
and notices are retained in `source-profile.json`. No target source or dependency
algorithm is patched. The target is built from that unchanged original source
into a private R library; the active namespace is checked against every parsed
original function body. The `defineTextQuery` re-export is checked against its
declared gypsum delegate. Full prefix/library inventories and the exact manifest
and lock identify the selected comparison environment.

R 4.4.3 and Bioconductor 3.20 dependency packages are explicit **project comparison
choices**, including gypsum 1.2.0, alabaster.base/matrix 1.6.1, alabaster.se
1.6.0, ExperimentHub 2.14.0, AnnotationHub 3.14.0 and ensembldb 2.30.0.
The Conda celldex 1.16.0 recipe resolves these dependencies; analysis uses the
separately source-built original **1.14.0** target, not a substituted target
release. GenomeInfoDbData 1.2.13 is installed explicitly from its exact original
archive because package-manager completion alone does not establish data-package
availability. These choices are not recovered author dependencies.

The original 1.14.0 named getters distinguish the dated gypsum path from legacy
ExperimentHub retrieval. Original Ensembl conversion uses AnnotationHub and
ensembldb. Reference downloads use explicit permission and a NEW owned cache;
offline execution isolates the R process from networking. Runtime installation
is a separate operation with its own explicit permission flags.

## Development evidence and limits

Direct unchanged-original calls successfully retrieved the project-selected
`2024-02-26` ImmGen reference (22,134 genes by 830 samples) and MouseRNAseq
reference (21,214 genes by 358 samples), with complete metadata, labels, values,
conditions and RNG retained. Legacy calls, nonmissing/no-ontology modes and
MouseRNAseq Ensembl conversion also returned. The original conversion warning
reported unmapped identifiers; its warning and original selected gene removal
are retained. These are public reference inputs, not synthetic fixtures or
established paper snapshots.

Qualification is performed by independent direct-original versus worker calls
in `tools/literature/celldex114_validation`, including complete serialized
objects/conditions/RNG and independent materialization of lazy assay payloads.
Expected source errors must remain errors without admitted results. A provider
source audit or an operation's availability is not a substitute for those
comparisons, actual public-runner tests or installed-runtime execution.

Reference caches and complete biological reference returns remain retained
locally under their upstream data provenance. The software archive's license
does not by itself establish a redistribution license for every underlying
reference dataset. No reference matrix is assigned a new project license.

**SKIP3 skips dependent retrieval** for a named unavailable or forbidden exact
requirement. **FAIL2 admits no complete result**, including original errors,
native crashes and post-return recording failures. Read the specific cause and
original consoles, preserve originals/partials and retry NEW paths after
providing the permitted requirement or fixing the stated input/filesystem
issue. Ensembl mapping warnings and warning-as-error behavior remain unchanged.
Keep lazy-reference backing files at their recorded paths; no silent reference,
identifier, source, warning-policy or runtime fallback occurs.

Whole-tool validity, biological accuracy and paper reproduction remain
unqualified. Cell Ranger/ARC remain excluded by user request. The full
250-paper review and implementation goal remains open.
