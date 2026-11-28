# Complete CADD 1.6 GRCh38 score resources

This checkpoint validates the existing native `cadd-tools LOOKUP` operation against complete original GRCh38 CADD1.6 precomputed score tables. It adds resource qualification to the [native components](cadd-tools.md) and [linear model core](cadd-linear-scoring.md). Catalog credit remains unchanged; this is additional validation of the already counted CADD family.

**Read `WARNINGS.md` and `analysis-status.json` in each analysis bundle.** A completed lookup can contain unscored alleles. Keep those alleles unassessable and skip their dependent prediction; a missing score is never zero or benign. SKIP3/exit3 requires the named exact permitted prerequisite, or the analysis stays skipped. FAIL2/exit2 admits no complete result: retain diagnostics, correct the named hash, input, index, schema or capacity problem, and use a new output directory.

Obtain the original files privately from the [official CADD1.6 GRCh38 directory](https://krishna.gs.washington.edu/download/CADD/v1.6/GRCh38/). Compare every file with the [official MD5 checksums](https://krishna.gs.washington.edu/download/CADD/v1.6/GRCh38/MD5SUMs), then bind its complete SHA256 in the request. The checked resource identities are recorded in [RESOURCES.json](literature/cadd-genome-validation/RESOURCES.json). They cover both complete BGZF score tables and both complete tabix indices, totaling 87,762,995,563 bytes. These files and their extracted rows are private and are not redistributed by OmicsMatic.

Use the actual local file paths in a new request:

```json
{
  "schema_version": 1,
  "mode": "LOOKUP",
  "out": "new-cadd-result",
  "assembly": "GRCh38",
  "cadd_version": "1.6",
  "input_vcf": "variants.vcf",
  "representation": "original-CADD1.6-preparation",
  "maximum_resource_bytes": 100000000000,
  "score_tables": [
    {
      "id": "complete-all-snvs",
      "file": "whole_genome_SNVs.tsv.gz",
      "index": "whole_genome_SNVs.tsv.gz.tbi",
      "sha256": "a0909305601b70b05ff52daa4777312f379887a8fa888a5a20c6be90364a40bb",
      "index_sha256": "9ecd5038ca97ba11715e62447c874368281b0e464f666535c5d84d2ea1d87aa2"
    },
    {
      "id": "complete-gnomad-indels",
      "file": "gnomad.genomes.r3.0.indel.tsv.gz",
      "index": "gnomad.genomes.r3.0.indel.tsv.gz.tbi",
      "sha256": "9b199b4c8a680c243a444e7d036c571b0063d901d53cac37be36c24fcb77fcce",
      "index_sha256": "3bba873b2408c5f623ce47325d513056b90affa6479192deb98a82ca6758f41f"
    }
  ]
}
```

```text
java -jar omicsmatic-0.4.1.jar cadd-tools --request request.json
java -jar omicsmatic-0.4.1.jar cadd-tools --verify new-cadd-result
```

The explicit preparation option uppercases and trims alleles according to the original CADD source while preserving every original variant and genotype in the bundle and transformation ledger. Choose `literal` when that transformation is not intended. Both retain literal chromosome names; `chr1` is not aliased to `1`. Neither checks the reference FASTA, lifts coordinates or left-aligns an indel. Tables are searched in the declared order and every exact hit from the first matching table is retained. The population-indel table does not cover every possible indel.

The Java implementation hashes the entire files before and after lookup. This can take several minutes and is required for complete resource binding. Saved verification recomputes every ledger from the captured VCF and candidate rows on Windows or Linux. It does not reread the large external files or independently authenticate an absent score. The separate resource verifier can explicitly rehash the whole files when needed:

```text
python tools/literature/verify_cadd_genome_resources.py
python tools/literature/verify_cadd_genome_resources.py --private build/tool-audit-918
python tools/literature/verify_cadd_genome_resources.py --private build/tool-audit-918 --resources build/tool-audit-913/scores-download-1
```

The [qualification](literature/cadd-genome-validation/QUALIFICATION.json) retains the exact original-source comparison and scope. The fixture contains all six original example records, every first/last indexed position on all 24 contigs of each table, every alternate at those positions, anchored representations, explicit source omissions and an uncovered literal chromosome. Entire original reference files are used; selecting comparison variants does not create reduced reference tables.

Original CADD workflow deployment is separately recorded: the unchanged source uses Snakemake8.25.2, a compatible Conda24.11.3 manager and an exact private clone of all 316 scientific packages. All four original workflow configurations reuse that environment, and the original preparation rule runs on all six source input variants. The external setup marker was written only after exact package and activation checks; this was a predeployed package clone rather than a fresh Snakemake dependency solve.

**Full raw-VCF annotation/prediction remains unqualified.** The complete original GRCh38 annotation archive is still required: 220,210,162,411 bytes, official MD5 `46bae9a0acce192ae6ff34b0e496194b`. Finish its active download, verify the entire checksum, bind all required reference, track, index and VEP95 cache files, and qualify the full original workflow before using it. Retain all variants, features and tracks. GRCh37 full reference resources, CADD1.4/1.7, CADD-SV and paper-specific downstream interpretations require separate qualification. See the [handover](literature/cadd-genome-validation/HANDOVER.md).
