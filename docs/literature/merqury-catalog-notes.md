# Merqury catalog admission

The frozen 250-paper cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG papers dated 2023-10-23 through 2026-10-23. Merqury's source audit covers all 250 papers and identifies four method uses. Three uses are newly reviewed; P164's existing review now also records its parental k-mer database preparation for Verkko. Assembly execution, Yak's separately attributed phasing analysis, and unreported author parameters remain outside the qualification.

The admission adds one scoped upstream family with native Java checks, without duplicate native family credit. Current totals are 169 scoped families (34 native, 135 upstream), 172 including three existing adapters, 1,812 reviewed uses and 919 pending uses. There are still 252 missing labels; the full implementation goal remains open.

[Qualification and warnings](merqury-validation/README.md) describe the nine tested workflows, retained source failures, runtime requirements and recovery actions. [The command guide](../MERQURY_TOOLS.md) gives editable request templates. Use a current source build; previous released 0.4.1 binaries predate this feature. The literature HTML reports remain withheld from Pages.

`merqury-catalog-admission/BASELINE.json` pins the exact predecessor metadata archive, source-role record, qualification and transformer code. `PREVIOUS-SHA256SUMS` and `PREVIOUS-REGISTRY.json` preserve the previous catalog. `REGISTRY.json` and `STATUS.json` describe the narrow transition; the main catalog adds only its Merqury role-coverage and validation checksum members. Earlier validation manifests remain unchanged.

Run the source-bound read-only check from the matching published Git revision:

```sh
python3 -B tools/literature/merqury_catalog.py --root /absolute/matching-source
```

This checks the saved catalog transition and frozen source record. It does not execute upstream tools or reanalyze a paper. Preserve this revision or its exact archived source context when later catalog work changes shared metadata.
