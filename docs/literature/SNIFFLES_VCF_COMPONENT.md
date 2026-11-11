# Sniffles VCF component: development checkpoint

**The Sniffles execution adapter is still under development. This component reads complete caller output; it does not call structural variants. Sniffles remains marked missing in the main tool catalog, and this checkpoint adds no implemented-tool credit.**

The fixed 250-paper cohort contains six Sniffles observations, reporting five editions: 1.0.7, 2.0.2, 2.0.7, 2.3.2 and 2.4. The [original repository](https://github.com/fritzsedlazeck/Sniffles) supplies the corresponding source tags. Exact commits, archive digests, complete source populations and six primary-article role drafts are retained in this checkpoint's evidence. A tag or a successful source build does not identify an author's binary.

## Inspect an existing VCF

Java 17 or later is required. This command uses the separate development JAR; it does not rebuild the main OmicsMatic executable.

In PowerShell, replace the input and output paths with your own absolute paths. The output's parent directory must already exist, and the output directory itself must be new:

```powershell
java -jar docs/literature/sniffles-vcf-development/sniffles-vcf-development.jar `
  --edition 2.4 `
  --vcf D:/my-analysis/calls.vcf `
  --out D:/my-analysis/sniffles-inspection-1
```

The input must be a complete, plain UTF-8 VCF from the declared Sniffles edition, with its original sample columns. This command does not decompress files. Keep the original compressed file and use a separately bounded decompression step if necessary.

Read `WARNINGS.md` and `COMPLETED.json` first. Only exit code 0 together with `PASS_COMPLETE_NATIVE_SNIFFLES_VCF_INSPECTION_ONLY` and `inspection_admitted: true` admits the whole inspection. Any partial export from a failed attempt must remain unused. Retry failures with a new output directory.

The output contains:

- `original.vcf`: an unchanged copy, with the original input path and SHA-256 recorded in `input.json`.
- `header.json`: every metadata line and the original column header, including duplicate legacy declarations and duplicate sample labels retained by position.
- `variants.jsonl`: every original record, all columns, ordered INFO entries, FORMAT keys and sample values. The line body is retained without its newline terminator. Missing dots and omitted trailing fields remain distinct; no values are padded or repaired.
- `summary.json`: record/type counts, exact `PASS` counts, support availability and separate counts of phased genotype strings and INFO `PHASE` records.
- `effective-limits.json`: the actual capacities used. The command permits 256 MiB of input, one million records, 16 Mi characters per line, 100,000 metadata lines and 10,000 samples.

Supporting-read counts use INFO `RE` in 1.0.7 and INFO `SUPPORT` in Sniffles2. Neither is automatically treated as the unspecified read-depth field in a paper. Signed SV lengths, symbolic alleles, breakend alleles and absent BND lengths/endpoints are preserved. The reader does not normalize coordinates or alleles, infer genotypes, filter records, merge calls or reconstruct haplotypes. Textual validation does not certify all legacy metadata declarations against the VCF standard or establish biological correctness.

## Use the Java API

`org.jlinalg.genetics.SnifflesVcf.read` takes an input stream, an explicit `Edition`, explicit `Limits`, a header sink and a variant sink. It streams records and returns a complete summary. Sink output remains provisional until the call returns successfully; an execution workflow must also admit its entire original-process batch.

```java
try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of("calls.vcf"))) {
    var summary = org.jlinalg.genetics.SnifflesVcf.read(
        input,
        org.jlinalg.genetics.SnifflesVcf.Edition.V2_4,
        org.jlinalg.genetics.SnifflesVcf.Limits.bounded(),
        header -> System.out.println(header.samples()),
        variant -> System.out.println(variant.rawLine()));
    System.out.println(summary.records());
}
```

For larger data, supply explicit API capacities and sufficient Java heap. An absent scalar integer returns an empty `OptionalLong`; it is not changed to zero. Numeric access fails for a malformed, repeated, flag-valued or overflowing named scalar. Opaque INFO values remain literal strings. These are structural data-access rules, not a new SV-calling algorithm.

## Prepare the original reference software

The preparation script requires Linux x86_64 and Python 3.12. It downloads official packages into a new workspace, creates a private environment and builds the legacy caller from an unchanged source copy. It does not use a system package manager or alter existing Python libraries. Both download and installation must be permitted:

```bash
/usr/bin/python3 -B tools/literature/setup_sniffles_reference_linux.py \
  --intake /absolute/project/build/literature-review/sniffles-runtime/source-intake-3 \
  --out /absolute/project/build/literature-review/sniffles-runtime/my-new-reference \
  --allow-download --allow-install
```

The intake is created separately with `tools/literature/intake_sniffles_reference.py --out build/literature-review/sniffles-runtime/source-intake-3` on the project's Windows host. Use a fresh directory if that name already exists. The preparation currently uses the already verified official pip bootstrap wheel at `build/literature-review/hail-runtime/pip-bootstrap-intake-1/pip-24.3.1-py3-none-any.whl`; a missing bootstrap is a named setup failure, not permission to install globally.

**If downloads or installation are forbidden, or a named runtime dependency fails, the script returns SKIP3 and dependent SV analysis must be skipped.** Read the retained logs and the required action in `WARNINGS.md`. Where permitted, resolve the named package, compiler, permission or resource problem and retry in a new private directory. Otherwise supply a permitted runtime and retain the skip. Analysis commands must not install dependencies implicitly.

The first legacy GCC build failed on BamTools' C++17 comparator requirement. Its complete logs remain retained. The fresh build selects C++11 through CMake options and leaves the original source files unchanged. Compiler warnings remain visible in the original logs. Modern dependencies are explicitly recorded; these environments are not claimed to match the papers' unreported dependency builds.

**The constructed alignments contain three indels, but original Sniffles 1.0.7 discovery returned two: the 90-bp insertion and the later 70-bp deletion. It omitted the initial 120-bp deletion.** The first failed three-event expectation and its complete original VCF/logs remain retained. Reference comparisons preserve the actual caller output and do not fabricate that missing event or certify sensitivity. The source's alignment-event traversal is relevant to further investigation; this checkpoint does not claim an independently established cause.

## Validation and evidence

The [frozen validation receipt](sniffles-vcf-development/VALIDATION.json) records 14 original-caller controls across the five source editions: separate sample calls, tandem-repeat guidance, phased calling and legacy/modern force genotyping. Their 39 complete VCF records are compared with both compiler builds on Windows Java 21, Windows Java 25 and Linux Java 21: 84 complete VCF comparisons covering 234 records, 246 format/boundary controls and 24 public CLI failure controls. Late malformed data admits no partial inspection; reusing an output directory preserves the previous result. Setup without download/install permission starts no process and returns SKIP3.

All native API class bytes match between the JDK 21 and JDK 25 builds; the CLI class differs. Both are compiled for Java 17 bytecode and tested separately. Java 17 itself was not used as an execution host in these controls. The canonical OmicsMatic executable and the current tool catalog remain unchanged.

Verify the published evidence without executing Java or Sniffles:

```powershell
python tools/literature/sniffles_native_evidence.py
```

This checks current source pins, both shipped JARs, the complete content-addressed evidence archive, original source archives and every archived native comparison. `--archive-only` checks the frozen bundle without requiring current workspace source files or the canonical executable. This is an integrity/consistency check, not an independent rerun of SV calling or an authenticity signature. Article role drafts contain source links and paragraph hashes; full article paragraphs are not redistributed in this bundle.

## What remains

The Java execution adapter still needs immutable runtime/host binding, supervised original caller execution, complete input and output admission, failure/cancellation controls, beginner workflow examples and catalog integration. The legacy population workflow, separate tumor/normal calling, independent HiFi/ONT calls, tandem-repeat guidance and phased calling must retain their distinct meanings. SURVIVOR/Jasmine merging, Iris polishing, WhatsHap phasing and haplotagging, and bcftools consensus are separate tools. This component does not implement those downstream operations or certify any paper reproduction.
