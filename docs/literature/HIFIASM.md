# hifiasm assembly with native Java read and graph checks

`HifiasmWorkflowCli` runs the original hifiasm assembler and uses Java to inspect
the complete read population, every emitted GFA graph, and literal FASTA exports.
The assembler performs error correction, overlaps, consensus and phasing.
Java supplies bounded read admission, parental k-mer helpers, graph statistics,
FASTA extraction and whole-batch admission.

This is a **validated development integration** for the declared modes and
limits below. The canonical executable is unchanged. Build and invoke the
separate development entry points shown here.

## Warnings and required actions

**Require exit 0 AND `COMPLETED.json` with `analysis_admitted: true`.** Read
`WARNINGS.md`, `analyses/*/graph-audit.json`, every original stderr and process
receipt. A GFA, FASTA, exit 0 from the assembler or per-case `PROVISIONAL.json`
alone does not admit any result.

**SKIP3: skip the dependent analysis.** Supply the exact permitted runtime,
successful setup/binding receipt and compatible Linux x86_64 host, or retain the
skip. Windows Java can export sources and verify retained results. Assembly uses
Linux Java, including WSL with Linux paths. If installation is forbidden or
required development/runtime dependencies are unavailable, skip analysis.
Analysis never installs software or changes shared libraries.

**FAIL2: no case in the requested batch is admitted.** Retain all original inputs,
logs and provisional outputs. Correct the named input, source, host, capacity or
process problem and retry NEW paths. Never update old scientific binding hashes
to conceal a change. Missing or unwritable final receipts mean no admission.

**Do not use Hi-C with 0.14.2-r315 or 0.15.1-r328 in this integration.** Retained
original controls crash. The adapter returns SKIP3 for these combinations. Choose
a separately validated newer edition explicitly if appropriate to the analysis;
the adapter never substitutes one. Neither old-edition paper role requires Hi-C.

**ONT-only 0.22.0-r689 requires FASTQ qualities.** The original reports an error
and exits 0 for FASTA. Java rejects that input before assembly. Do not invent
qualities for experimental FASTA files. The synthetic controls declare their
artificial qualities separately from real sequencing measurements.

**Large parental databases need explicit storage and original-process
resources.** The workflow scans every YAK entry while holding one bucket's hash
words for duplicate detection. `parent_entries_per_bucket` defaults to 4M and
can be raised to 25M; total entries are bounded by the complete input byte limit.
Set appropriate `input_bytes`, `workspace_bytes`, Java heap and original-process
limits for large inputs. Streaming inspection does not reduce hifiasm's own
memory needs. Do not truncate counts or substitute a smaller database to conceal
a capacity failure.

Native GFA/FASTA checks are structural. They do not independently establish
consensus accuracy, full phasing, completeness or reproduction of a paper.
Default HiFi output can be partially phased. Keep RagTag, gap filling, polishing,
BUSCO, mapping and Merqury assessment as separate analyses.

## Build the development candidate

From the repository root with the validated GSEApy execution parent artifact
already present:

```powershell
python -B tools/literature/build_hifiasm_execution.py --out build/literature-review/hifiasm-runtime/NEW-BUILD
$hifiasmJar = 'build/literature-review/hifiasm-runtime/NEW-BUILD/omicsmatic-0.3.7-hifiasm-foundation-development.jar'
java -cp $hifiasmJar org.jlinalg.cli.HifiasmSetupCli --out build/NEW-HIFIASM-KIT
```

The builder requires the exact parent artifact validated by
[`GSEAPY_EXECUTION.md`](GSEAPY_EXECUTION.md). If it is absent, follow that build
history; do not substitute another JAR or refresh the parent proof. Export needs
a NEW directory under an existing parent and installs nothing. The kit contains
six immutable original source archives, exact source profiles and original MIT
licenses. No Yak executable is included.

## Compile and bind an explicit edition

Select exactly one:

| Edition | Declared adapter modes |
| --- | --- |
| `0.14.2-r315`, `0.15.1-r328` | HiFi, trio |
| `0.16.1-r375` | HiFi, trio, Hi-C |
| `0.19.5-r587`, `0.19.5-r592` | HiFi, trio, Hi-C; each with optional UL reads |
| `0.22.0-r689` | The preceding modes, plus ONT-only FASTQ |

The official 0.19.5 release is r587. A separate immutable upstream commit supplies
r592; the label alone does not identify the binary used by a paper. Papers that
report only 0.19.5 retain an unresolved revision suffix.

On permitted Linux x86_64, provide Java 17+, make, GCC/G++ and zlib development
files. Use the operating environment's permitted package manager if needed.
Keep existing bound scientific hosts unchanged; use a compatible separate host
when package changes would affect them. If installation is not allowed, retain
SKIP3. No package-manager command is run by this analysis adapter.

For example, replace `/ABS/...` with canonical absolute paths. Create the work
parent and empty source directory first; the build and binding output directories
must not already exist:

```sh
mkdir /ABS/NEW-WORK
mkdir /ABS/NEW-WORK/source
tar -xzf /ABS/NEW-HIFIASM-KIT/original/0.19.5-r587.tar.gz \
  --strip-components=1 -C /ABS/NEW-WORK/source
```

Save this as `/ABS/NEW-WORK/build-request.json`:

```json
{
  "schema_version": 1,
  "mode": "BUILD_REFERENCE_ONLY",
  "allow_install": true,
  "edition": "0.19.5-r587",
  "source": "/ABS/NEW-WORK/source",
  "out": "/ABS/NEW-WORK/build-receipts",
  "compiler_threads": 2
}
```

```sh
java -cp /ABS/DEVELOPMENT.jar org.jlinalg.cli.HifiasmSetupCli \
  --build-request /ABS/NEW-WORK/build-request.json
```

`allow_install` permits local compilation of the unchanged bundled sources.
It does not permit OS package installation by the command. Require exit 0 and
`PASS_ORIGINAL_HIFIASM_SOURCE_COMPILATION_ONLY`. Original 0.14.2/0.15.1 use exit 1
for `--version`; a separate exact-source interpretation records that fact while
retaining the strict raw process receipt. No nonzero compilation or assembly
exit is accepted.

Save the binding request:

```json
{
  "schema_version": 1,
  "mode": "BIND_REFERENCE_ONLY",
  "build_receipt": "/ABS/NEW-WORK/build-receipts/COMPLETED.json",
  "out": "/ABS/NEW-WORK/runtime"
}
```

```sh
java -cp /ABS/DEVELOPMENT.jar org.jlinalg.cli.HifiasmRuntimeCli \
  --request /ABS/NEW-WORK/bind-request.json
```

Require exit 0 and `PASS_BOUND_ORIGINAL_HIFIASM_RUNTIME_ONLY`. Binding captures the
original binary/source provenance, helper programs, ELF providers, loader
configuration, declared search candidates and absence states. It does not run
or admit an assembly. Changed bindings require NEW compatible setup paths.

## Run a synthetic example

The creator writes simulated reads and an explicit request, and runs no analysis:

```sh
python3 -B tools/literature/create_hifiasm_example.py \
  --runtime /ABS/NEW-WORK/runtime/COMPLETED.json \
  --edition 0.19.5-r587 --out build/NEW-HIFIASM-EXAMPLE
java -Xmx3g -cp /ABS/DEVELOPMENT.jar org.jlinalg.cli.HifiasmWorkflowCli \
  --request "$PWD/build/NEW-HIFIASM-EXAMPLE/request.json"
cat build/NEW-HIFIASM-EXAMPLE/result/COMPLETED.json
cat build/NEW-HIFIASM-EXAMPLE/result/WARNINGS.md
```

On Windows, the creator's `--path-style wsl` explicitly converts drive paths.
Supply the runtime as a Linux path and invoke the workflow through WSL Linux
Java. Do not pass a Windows path to Linux Java.

This example has two synthetic 300kb haplotypes, 12kb error-free HiFi reads and
explicit `bloom_bits: 0`. It is a software control, not paper data or a genome
quality benchmark. Its graph views overlap; do not sum their bases as a genome
size. A small test cannot estimate real organism resource requirements.

## Describe real inputs and modes

Every input declares its canonical path, complete byte count and lowercase
SHA-256. `kind` is `reads` or `yak`. A minimal HiFi request is:

```json
{
  "schema_version": 1,
  "out": "/ABS/NEW-RESULT",
  "runtime": "/ABS/NEW-WORK/runtime/COMPLETED.json",
  "edition": "0.19.5-r587",
  "inputs": {
    "hifi": {
      "kind": "reads", "path": "/ABS/reads.fa.gz",
      "bytes": 123, "sha256": "REPLACE_WITH_THE_ACTUAL_64_HEX_DIGIT_SHA256"
    }
  },
  "analyses": [
    {"id": "sampleA", "mode": "hifi", "reads": ["hifi"],
     "threads": 1, "bloom_bits": 0}
  ],
  "limits_profile": "bounded-v1"
}
```

The byte count and hash above are placeholders; copying them unchanged fails.
Use the example creator for a fully populated runnable request. For real inputs,
calculate both from the complete original file and preserve that original.
Inputs are copied unchanged into the result; filenames do not determine format.

Use `mode: "trio"` with `paternal` and `maternal` naming two `kind: "yak"`
inputs. Use `mode: "hic"` with
`"hic_pairs": [{"first": "hic1", "second": "hic2"}]` and declare those read
inputs. Append `-ul` to either mode or `hifi` and add
`"ultralong": ["ul"]` for one explicitly prepared UL file. ONT-only uses
`mode: "ont"` and places its FASTQ files in `reads`. Modes must agree with all
declared inputs; unused inputs and unknown options fail.

Every analysis explicitly supplies `threads` and `bloom_bits`. Bloom bits are 0
or 20..40; the requested allocation must fit the original address-space limit.
Zero disables Bloom filtering. `purge_level` optionally supplies `-l` (0..3),
and `disable_post_join: true` supplies `-u`. Omitted options retain upstream
defaults. For the millet role, explicitly use purge level 2 and disabled
post-join. No paper's unreported settings are inferred.

Reads support plain/gzip FASTA or FASTQ with wrapped sequence/quality lines,
bounded complete headers, literal case/U/IUPAC bases and Phred+33 qualities.
The stricter adapter profile rejects empty/duplicate identities, malformed or
mixed records, repeated `+` identity mismatches, blank sequence/quality lines,
non-ASCII headers and capacity violations. It does not silently rename reads,
convert U, impute qualities or normalize bases. Source read correction remains
upstream. Hi-C mates must match in order/count; exact identities and the terminal
`/1`/`/2` convention are supported with an explicit pair ledger. A Hi-C output
path cannot contain a comma because upstream uses comma-separated file lists.

## Capacities, cancellation and outputs

`bounded-v1` is a small-control profile. Its effective values are retained in
`effective-limits.json`: 256MiB encoded/512MiB decoded per input, 8M bases per
read, 1M reads, 200M bases, 64MiB per GFA, 16M bytes per GFA line, 4M entries per
parental bucket, 600s wall/CPU per original assembly, 8GiB address space, 1GiB per file,
2GiB total owned workspace, 4096 files, 32 processes, 256 threads and 6GiB
sampled RSS. Original stderr admission is bounded at 16MiB. GFA parsing holds
one complete graph in Java memory; provide sufficient Java heap.

Larger real assemblies require explicit `limits` overrides and enough storage,
Java heap and original-process resources. Do not rely on a small-control profile
for human or plant WGS. Supported override names are listed in
[`HifiasmWorkflowRequest.java`](../../src/main/java/org/jlinalg/cli/HifiasmWorkflowRequest.java).
Hard per-process AS/CPU/file/nofile limits and owned-process cleanup are combined
with wall/output/process/thread checks. Sampled RSS is observational, not a hard
aggregate RSS limit.

Create `CANCEL_REQUESTED` inside the active result directory to request owned
cancellation, including during final verification. Keep the entire failed
directory. Nested causes are retained in `error.traceback.txt` and prominently
listed in `WARNINGS.md`. All cases remain provisional
until the final batch receipt passes. Earlier original outputs, native exports,
metadata and complete case populations are checked again before admission.

`analyses/<id>/original` retains every original file, including binary caches.
Runs always use a NEW prefix and `-i`; those caches are not reused.
`analyses/<id>/native/*.fasta` contains literal sequence-bearing GFA segments in
source order. Missing-sequence views retain an omission ledger and no invented
bases. Directed links, original tags, gaps, virtual reads, empty haplotypes and
source diagnostics remain visible in the graphs and audit reports. Placement
coordinates refer to source-corrected reads; raw read-length bounds are not
inferred. Native N50/L50 are exact per-graph length summaries, not QC substitutes.

Verify the retained complete population without rerunning assembly:

```sh
java -cp /ABS/DEVELOPMENT.jar org.jlinalg.cli.HifiasmVerifyCli --out /ABS/NEW-RESULT
```

This checks integrity and the original admission/cleanup receipts. It does not
refit the assembler or independently certify biology. Preserve warnings and all
failed or superseded attempts as evidence.

## Native Java helpers

`HifiasmReads` provides the bounded streaming input census and paired checks.
`HifiasmGfa` parses the complete hifiasm H/S/L/A dialect, preserves literal fields
and directed order, summarizes lengths and exports FASTA. It is not a generic
GFA path assembler. `HifiasmYak` performs the workflow's complete streaming YAK2
inspection, including per-bucket duplicate checks and literal counter summaries.
`HifiasmParentalKmers` provides source-compatible k-mer hashes,
direct counts, YAK2 serialization and parental classification. Direct counting
does not reproduce Yak's Bloom-filter admission or identify a dirty paper build.
Read provenance cannot be recovered from a YAK file that does not encode it.
The separate materialized `HifiasmParentalKmers.Table` API retains a 25M-entry
limit; the workflow uses the streaming inspector instead.
For either native writer, wrap file streams in `BufferedOutputStream` and close
them at the call site.

## Validation and boundaries

The [execution proof](hifiasm-execution-development/VALIDATION.json) binds six
original source builds and runtime profiles, 27 fresh public workflows and all
246 emitted GFA views. Every view matches a separately executed original
assembly control byte for byte; independent Python checks cover the complete
native graph census and literal FASTA exports.

Native read, graph and parental components pass on Windows Java 21/25 and Linux
Java 21. A separate source-checked streaming-parental test covers 27,000,027
entries under a 128 MiB Java heap. An additional full gzip HiFi/trio/UL workflow
uses 27,299,997 paternal entries under the same Java heap and preserves every
original graph byte. These are synthetic controls, not a human or plant genome
benchmark. Original-process memory is separate from the Java heap.

Eleven actual skip, failure and cancellation controls prevent whole-batch
admission. Sixteen retained-result corruptions are rejected; intact verification
also passes on all three JVMs. Failed historical controls and an observer-race
retry remain explicitly recorded. Integrity checks do not refit the assembler,
authenticate evidence or certify consensus accuracy, phasing or paper results.

Seven catalog roles retain each paper's reported edition, options, assembly
reuse and separate scaffolding/QC boundaries. Unreported revision suffixes and
settings remain unresolved. The broader 250-paper implementation goal remains
active.

Official references: [hifiasm source](https://github.com/chhylp123/hifiasm),
[parameter reference](https://hifiasm.readthedocs.io/en/latest/parameter-reference.html),
and [output interpretation](https://hifiasm.readthedocs.io/en/latest/interpreting-output.html).
