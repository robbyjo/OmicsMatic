# Eagle BCF input workflow

The native `EagleBcf` component decodes BCF2.2 into canonical VCF while retaining haploid or diploid GT slots, missing alleles, phase separators and typed metadata. The development `eagle-tools` command accepts these inputs for cohort/reference checks and phasing. It supports plain binary BCF and ordinary BGZF framing. Original binary inputs remain protected because text conversion does not recover an earlier VCF's lexical formatting.

**The documented Eagle 2.4.1 subset and all eleven source-role summaries are validated in this development checkout.** Use the [workflow guide](EAGLE_WORKFLOWS.md), [source review](EAGLE_SOURCE_REVIEW.md), [validation](eagle-validation.json) and [copyable BCF examples](../../../examples/eagle-phasing-workflow/README.md). The implementation and private packaged validation are in this checkout; no release or public deployment is implied.

## Declare and preserve binary sources

Each `target`, `reference` or optional `exclusion` independently declares its format. Mixed VCF/BCF roles are supported. A BCF role without an index needs only `path` and `format`:

```json
"target": {"path": "target.bcf", "format": "bcf"}
```

To retain an existing source index, explicitly declare its path and policy:

```json
"target": {
  "path": "target.bcf",
  "format": "bcf",
  "index": "target.bcf.csi",
  "index_policy": "snapshot-only-regenerate"
}
```

**A declared source index is provenance only.** The adapter protects and hashes its bytes, but does not query it or certify that it corresponds to the source BCF. No neighboring index is discovered implicitly. Both `sourceIndexConsumed` and `sourceIndexCorrespondenceCertified` remain false in `input-audit.json`. A fresh CSI for the prepared VCF is generated and independently validated.

Each BCF role retains `source-intake/<role>/source.bcf`, optional `source-index`, `decoded.vcf` and `conversion.json`. Conversion reads the protected binary sequentially and a separate complete VCF/GT audit verifies the decoded population and digest. Native cohort/reference admission then prepares the BGZF/CSI input and records matching decisions. Original sources, protected copies, conversion text and conversion ledgers are checked for mutation around execution. Combined source/conversion/preparation bytes count against the workspace limit.

**Required action:** retain `source-intake/`, all preparation ledgers and `WARNINGS.md` with the completed bundle. Review every conversion warning. Missing/unsupported capabilities produce **SKIP3**; invalid binary data, CRC, mutation or capacity failures produce **FAIL2**. Neither admits a result. Preserve the failed bundle, correct the stated cause and rerun to a new directory. An explicit exclusion still obeys `unmatched`; `REFUSE` does not silently become a drop policy.

## Existing codec limitation

The project's existing generic BCF version override uses HTSJDK 5.0.0's BCF2 decoder. An original BCFtools 1.24 fixture with a valid single-dot missing genotype caused that decoder to throw `ArrayIndexOutOfBoundsException` on an end-of-vector value. Its generic value decoding also prunes missing elements, and its ordinary VCF encoder rounds floating-point values. Merely overriding the accepted BCF version does not establish lossless BCF2.2 conversion.

The new Eagle component uses explicit wire decoding rather than that version override. This change does not certify or modify the separate generic study BCF reader. **Required action:** do not use the generic version override as a conversion oracle for mixed-ploidy or missing-vector BCF2.2 data. Retain its failed diagnostics and use a verified path for those inputs. The [original codec probe](../../../build/literature-review/eagle-runtime/bcf-existing-codec-probe-1/observed.json), [direct metadata API probe](../../../build/literature-review/eagle-runtime/bcf-generic-metadata-probe-1/observed.json), and [version-matched upstream sources](../../../build/literature-review/eagle-runtime/bcf-source-intake-1/observed.json) record the observed boundary.

## Decoding scope

Dictionary indices resolve separately for contigs and field names, including explicit sparse IDX values. Conversion removes IDX annotations from text, preserves per-key values and moves GT first when necessary for VCF. Interior missing numeric entries remain dots; end-of-vector padding ends a field without inventing additional allele slots. Finite Float32 values use round-trip decimal text. Noncanonical NaN payloads are counted and warned about because their payload bits remain only in the binary source.

The admitted header semantics are VCF4.0–4.3 within BCF2.2. The legacy first-allele phase bit has no leading-phase text representation; its presence is counted, and subsequent separators and ploidy match original BCFtools output. Newer VCF header semantics and BCF2.1 require separate admission. GT supports one or two allele slots; unsupported ploidy and values are refused. Strings that require unsupported VCF escaping, reserved primitive values, conflicting dictionary definitions and invalid padding are refused rather than repaired.

The component checks source hashes before and after conversion, complete record lengths, decoded and output byte limits, row/cell and estimated retained-storage capacities. Compressed inputs require complete ordinary BGZF blocks, CRC checks and a final canonical EOF block. Truncation, invalid CRC, trailing bytes and data following vector ends cannot pass complete admission.

The validation corpus covers raw and BGZF files, site-only exclusions, 8/16/32-bit GT vectors, sparse dictionary indices, single-dot and partial missing calls, integer and float arrays, interior missing values, infinities, NaN and signed zero. See the [161-test three-JVM gate](../../../build/literature-review/eagle-runtime/bcf-native-tests-3/observed.json), [six original full-record-payload round trips](../../../build/literature-review/eagle-runtime/bcf-original-roundtrip-2/observed.json), and [direct original BCF versus converted VCF phasing comparison](../../../build/literature-review/eagle-runtime/bcf-original-phasing-1/observed.json). The latter matched complete rows across cohort/reference KEEP/IMPUTE modes, auditing 512 paired calls. These are source-behavior checks, not biological phasing accuracy or historical edition certification.

The public bridge passes [169 strict tests on each of three JVMs](../../../build/literature-review/eagle-runtime/bcf-bridge-native-tests-2/observed.json) and [54 exact-JAR portable protocols](../../../build/literature-review/eagle-runtime/bcf-bridge-packaged-portable-1/observed.json): 21 native checks, nine expected skips, 18 expected failures including existing-output refusals, and six help/version checks. The [copyable BCF archive and recipes](../../../build/literature-review/eagle-runtime/bcf-bridge-public-examples-1/observed.json) pass six native checks and four actual phasing comparisons against direct-original BCF outputs, matching complete variant rows and 512 calls. Source-index provenance remains distinct from index correspondence certification in all of these checks.

The [complete exact-JAR BCF phasing corpus](../../../build/literature-review/eagle-runtime/bcf-bridge-packaged-linux-1/observed.json) passes all seven cohort and 33 reference protocols. Original BCFtools generated all 20 distinct source files without scientific filtering or policy changes. Every complete genotype report matches the retained fresh-original VCF baseline, covering 692,743 calls, including the full original cohort example, missingness/ploidy policies, allele matching, source-order differences and empty selected exclusions. Completion manifests and all protected source/conversion hashes are checked for every case. This establishes the documented 2.4.1 source-behavior contracts, not historical edition or biological accuracy claims.
