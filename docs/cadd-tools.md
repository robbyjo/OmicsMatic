# Native CADD 1.6 score components

`cadd-tools` adds native Java indexed score lookup, explicit CADD variant preparation, and raw-to-PHRED calibration. These components support the precomputed-score uses found in the paper catalog. They do not generate new raw scores from the CADD prediction model.

**Read `WARNINGS.md` and `analysis-status.json` before using a result.** Exit 3 means the dependent analysis was skipped: restore the named permitted input/resource or retain the skip. Exit 2 means no complete result was admitted: preserve the diagnostics, correct the named input, hash, index, schema or capacity problem, then use a new output directory. A missing score is unassessable and must not become a benign or zero score.

```text
java -jar omicsmatic-0.4.1.jar cadd-tools --request request.json
java -jar omicsmatic-0.4.1.jar cadd-tools --verify saved-bundle
```

Native execution needs Java and the supplied data files. It does not install or invoke Python, Perl, VEP, conda or a public scoring API. Relative input paths resolve beside the request. Output paths must be new.

## Indexed score lookup

Provide complete local BGZF CADD **1.6** six-column score files and their exact tabix indices. Each table must contain its declared assembly/version header and `#Chrom`, `Pos`, `Ref`, `Alt`, `RawScore`, `PHRED` columns in that order. Supply SHA256 values computed over each entire file. A caller-supplied hash provides an integrity binding; it does not prove that the table came from CADD or contains every possible variant. Retain its download/source provenance separately.

```json
{
  "schema_version": 1,
  "mode": "LOOKUP",
  "out": "cadd-result",
  "assembly": "GRCh38",
  "cadd_version": "1.6",
  "input_vcf": "variants.vcf.gz",
  "representation": "literal",
  "score_tables": [
    {
      "id": "all-snvs",
      "file": "whole_genome_SNVs.tsv.gz",
      "index": "whole_genome_SNVs.tsv.gz.tbi",
      "sha256": "REPLACE_WITH_COMPLETE_FILE_SHA256",
      "index_sha256": "REPLACE_WITH_COMPLETE_INDEX_SHA256"
    }
  ]
}
```

Add companion indel tables explicitly when needed. Tables are searched in the declared order. Every exact hit from the first matching table is retained, including duplicate source hits. Later tables cannot silently override that result. Coordinates, reference alleles and alternate alleles must match literally. `chr1` does not become `1`; alleles are not swapped, left-aligned or lifted between assemblies. The original VCF, including identifiers, INFO fields, samples and genotypes, is captured unchanged. Every original alternate allele receives a ledger row.

Use `"representation": "original-CADD1.6-preparation"` only when the source preparation is intended. This explicitly uppercases A/C/G/T alleles and trims shared prefixes followed by shared suffixes while retaining an anchor. It records the original and resulting representation, trim counts, and source omissions for equal REF/ALT or non-ACGT alleles. It does not check REF against a FASTA, left-align indels, deduplicate original observations or run the rest of the original CADD workflow.

Results contain `alleles.tsv`, every matched source row in `scores.tsv`, and `lookup-variants.vcf`. An eligible allele without an exact score has state `NO_SCORE_DEPENDENT_PREDICTION_SKIPPED`. Source-omitted alleles also remain explicit. A completed lookup is a complete account of the lookup; it does not imply that every allele acquired a score. Original table headers, all overlapping query candidates, resource identities and counts are retained in the private bundle.

The [official CADD 1.6 GRCh38 download directory](https://krishna.gs.washington.edu/download/CADD/v1.6/GRCh38/) provides the all-SNV and population-indel tables with [official MD5 checksums](https://krishna.gs.washington.edu/download/CADD/v1.6/GRCh38/MD5SUMs). Verify the original checksum before computing the SHA256 used in a request. Source-component qualification and complete genome-wide resource qualification are separate; consult the dated qualification/handover before claiming that a particular reference resource was validated.

## Raw-to-PHRED calibration

Provide a UTF-8 two-column input with the exact header `id<TAB>raw_score`, and the complete model/assembly-specific conversion table. Duplicate and Unicode identifiers are retained. The source conversion chooses the smallest raw boundary greater than or equal to the supplied raw score, clamps values beyond the table endpoints, and retains the table's PHRED text. It does not interpolate or estimate a new percentile from the submitted variants. Repeated raw boundaries use the last table value, matching the original dictionary behavior.

```json
{
  "schema_version": 1,
  "mode": "CALIBRATE",
  "out": "calibrated-result",
  "assembly": "GRCh38",
  "cadd_version": "1.6",
  "input_scores": "raw-scores.tsv",
  "conversion_table": "conversionTable_CADDv1.6-GRCh38.txt",
  "conversion_sha256": "ca317ac96abe9b8a5aed334825017720a22d6756ba551706671dacfb0704cebe"
}
```

The original CADD1.6 GRCh37 table SHA256 is `4ae0c6307d2f39f7cccec3ff2800f33fbc8d28afbfd3c598b7ebba123000e88a`. These bind the tables in [CADD-scripts commit d0a49ae](https://github.com/kircherlab/CADD-scripts/tree/d0a49aeabba01b75d1f0d1061a2f20c1404d1337), whose script wrapper is 1.6.1 and score edition is 1.6. The upstream source and tables are not bundled in this project; acquire them privately under their applicable terms. The declared assembly/version is not independently certified for arbitrary caller-provided tables.

`calibrated.tsv` preserves the submitted raw token, source-style six-decimal raw formatting, PHRED token and state. Source `NaN` produces PHRED `NA`; infinities retain the source endpoint behavior. The original dictionary search also prints a diagnostic for `NaN`; its unchanged stdout is retained in the private source comparison, and the native ledger exposes the row as unassessable. All nonfinite raw rows are explicitly unassessable for dependent interpretation. They are never converted to finite raw scores.

## Verification, scope and capacities

Saved verification checks the entire bundle, the producing classes/JAR containers, original scientific options, and every recomputed native result byte. Lookup verification uses retained candidate rows and table metadata; it does not reread external reference files or independently authenticate an absent score. Calibration verification rereads the entire captured conversion table. Integrity hashes do not protect against coordinated replacement of all evidence.

Requests have explicit byte, decoded-character, line, variant, candidate/hit and conversion-row capacities. Exceeding a bound fails the complete result; records are never truncated to obtain success. These checks are not operating-system memory/disk quotas. The full external table and index are hashed before lookup and again after lookup, which can take substantial time for genome-wide files.

CADD1.4 and1.7 model/data editions, generating raw scores from reference annotations, CADD-SV1.1, dbNSFP/VEP integration, clinical thresholds and complete author workflows remain separately unqualified. See the [19 source-reviewed paper roles](literature/cadd-source-audit/ROLES.json). In particular, retain the unusual CADD-SV label for the P192 missense example and the raw CADD-SV structural-variant analysis in P227; neither is ordinary SNV PHRED conversion.
