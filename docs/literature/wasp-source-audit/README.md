# WASP source roles

All 250 frozen primary XMLs were checked for WASP, WASP2 and `waspOutputMode`,
including main methods and the other XML structures recorded in `CENSUS.json`.
Four main-text contexts and one bibliography-containing back structure were
found. Original XML text stays private; public records contain identities,
locators, hashes and project-authored role summaries.

| Paper | Reviewed interpretation | Standalone adapter relationship |
| --- | --- | --- |
| P007 | [Modified WASP through Hornet](https://doi.org/10.1038/s41586-026-11053-x) for hybrid-species allele-specific expression | Separate Hornet pipeline; native allele component alone does not finish it. |
| P073 | [STARsolo built-in WASP](https://doi.org/10.1038/s41588-024-02019-8) in barcode/UMI-aware RNA processing | Separate STARsolo version and processing mode. |
| P157 | [Explicitly omitted WASP](https://doi.org/10.1038/s41588-024-01896-3) for the stated sQTL filtering step | No WASP runtime is required for that omitted step; whole-paper non-use is not inferred. |
| P187 | [Standalone WASP0.3.4 remapping](https://doi.org/10.1016/j.ajhg.2025.11.009) on pseudobulk ATAC BAMs with BWA0.7.17 | Original mapping workflow is the relevant tool family; exact author revision and preparation remain unqualified. |

P007 and P073 already had source-use reviews. P157 and P187 are the two new
eligible reviews. The source audit alone awards no implementation-family credit.
`ROLES.json` preserves every original observation and its complete primary
anchors, including the omitted-step interpretation. Supplemental recipe
completeness and whole-paper reproduction remain open.

Verify against the retained private XML cache:

```sh
python -B tools/literature/wasp_source_audit.py --xml-cache /absolute/original-xml-cache
```

The [original mapping documentation](https://github.com/bmvdgeijn/WASP/blob/d3b8447fd7719fffa00b856fd1f27c845554693e/mapping/README.md)
describes an initial alignment, allele substitution, remapping and filtering.
The selected official commit is not proof of P187's author revision. See
[workflow inputs and recovery](../../wasp-tools.md).
