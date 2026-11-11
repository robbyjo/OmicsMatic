# Sniffles execution development checkpoint

**Calling validation is unfinished. Sniffles remains missing in the tool catalog.**
This checkpoint adds Java runtime guards, a typed calling workflow, complete VCF
exports and portable frozen verification. It records successful compilation,
runtime health experiments and refusal/process controls. It grants **no calling
adapter availability, paper reproduction or additional integration credit**.
The separately validated [native VCF component](SNIFFLES_VCF_COMPONENT.md) remains
unchanged. The canonical OmicsMatic executable has not been rebuilt.

## Warnings and required actions

- **Do not use provisional case outputs.** Only a complete workflow receipt with
  `PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW` and
  `analysis_admitted: true` can admit its batch. A failed, skipped, interrupted or
  cancelled batch admits none of its earlier cases. This development checkpoint
  does not certify that the calling workflow has passed scientific validation.
- **Missing or forbidden dependencies mean SKIP3, exit 3.** Read terminal errors
  and `WARNINGS.md`. Where installation/copying is permitted, prepare the exact
  tested private runtime and a NEW binding. Otherwise skip dependent calling.
  Analysis never installs software. Unsupported runtime hashes or source
  editions are refused; changing saved hashes does not establish compatibility.
- **FAIL2, exit 2, admits no result.** Preserve logs and partial files, correct the
  named data/runtime/resource problem and retry into NEW paths. Existing output
  directories are refused and retained. `CANCEL_REQUESTED` in an active workflow
  directory requests cancellation and owned-process cleanup.
- **Clean-checkout runtime provisioning remains unfinished.** The current export
  helper copies a previously prepared, exact local reference runtime from ignored
  workspace data. Those data are not supplied by a Git clone. Do not treat this
  helper as a general downloader or installer. If the tested runtime is absent,
  calling must remain skipped until a permitted, validated preparation path is
  available. The user has authorized permitted downloads/installs; that does not
  waive runtime or validation checks.
- **FIFO test coverage is unavailable on this workspace filesystem.** `mkfifo`
  returns errno 95 on the Windows-mounted WSL workspace. The corrected controls
  explicitly skip this case. To claim FIFO rejection coverage, rerun the controls
  on a permitted Linux workspace filesystem that supports FIFO creation. Do not
  bypass workspace permissions. Prior generic expected-failure receipts did not
  prove FIFO rejection; their original diagnostics are retained and superseded.
- **Legacy source behavior is retained.** Original 1.0.7 omitted the initial
  120-bp deletion in the constructed reference experiment. No missing call is
  fabricated. `RE` in legacy VCF and `SUPPORT` in modern VCF describe read support;
  neither is silently substituted for a paper's unspecified depth criterion.
- Frozen verification checks bytes and structural consistency. It does not rerun
  Sniffles, check today's runtime, authenticate past execution, independently
  refit SVs or certify biological accuracy. Its warnings retain the original
  batch's warning messages.

## Tested development scope

Both isolated artifacts compile with `--release 17 -Xlint:all -Werror`, using
JDK 21 and JDK 25. Their pre-existing native VCF class families match the
corresponding frozen native artifacts byte for byte. Public refusal checks run
both compiler artifacts on Windows Java 21, Windows Java 25 and Linux Java 21;
Java 17 execution has not been tested.

Thirty public checks cover missing/unsupported runtime handling, refusal of
failed scientific batches, missing requests and preservation of existing binding
and verification outputs. Real Linux process controls run against both compiler
artifacts: **22 passed controls and two explicit FIFO skips**. They cover normal
completion, nonzero exit, timeout, workspace capacity, cancellation, an escaped
orphan, a symlink, temporary files, refusal before launch, thread interruption
and unreadable live process metrics. An unrelated sentinel survives each suite;
the suite then cleans up its own sentinel. These are process controls, not SV
calling validation. Special-node evidence is archived as metadata without
following links or opening FIFOs.

A separate original-runtime binding experiment passed dependency imports and
version probes for exact original sources from the
[official Sniffles repository](https://github.com/fritzsedlazeck/Sniffles):

| Edition | Original commit |
| --- | --- |
| 1.0.7 | `752bd21266125c16f1ea17d1b19c7a76860d4856` |
| 2.0.2 | `5737eb7bc5218c79f990d9ddf138c144bdd6cbad` |
| 2.0.7 | `60e70150ca943041250867d0facf48463da1aee8` |
| 2.3.2 | `034d8a907ba535d53169975e39174418d44c6f26` |
| 2.4 | `7f71d13d011b0df3d29da90a294507530181e2b9` |

These are source editions, not identified author binaries. The development
profile requires the tested Linux x86_64 Python 3.12.3 binary/standard library,
pysam 0.23.3, edlib 1.3.9.post1, psutil 7.1.0 and the tested original 1.0.7 native
binary. A matching version label alone is insufficient. The Java guard checks
complete private source/library populations before Python starts; ELF dependency
and search-path checks retain actual loader diagnostics. These checks are not
an operating-system security sandbox.

Python starts with `-B -P -s -S`, a fresh nonexistent bytecode-cache prefix,
explicit standard-library/private-site paths and effective `PYTHONHASHSEED=0`.
The bootstrap initializes the standard library's `site.setquit()` helpers because
original 2.3.2 calls `exit()`. It does not run `site.main()`, `.pth`, sitecustomize
or usercustomize, and does not replace scientific Sniffles functions. Source
and dependency health checks are distinct from completed scientific analysis.

## Development commands

These commands describe developer interfaces; the warnings above are prerequisites.
Use absolute paths appropriate to your machine and NEW output directories.

Build the isolated artifact from Windows using the existing configured JDK:

```powershell
python tools/literature/build_sniffles_execution.py --out build/literature-review/sniffles-runtime/NEW_BUILD
```

On a permitted matching Linux host, bind an already prepared/exported runtime:

```sh
java -cp /absolute/sniffles-execution-development.jar \
  org.jlinalg.cli.SnifflesRuntimeCli \
  --root /absolute/private-runtime --out /absolute/NEW_BINDING
```

The private runtime contains only `python`, `legacy-sniffles`, `site/` and
`sources/vEDITION/` with the exact packaged profile members. A successful binding
permits further checks; it admits no SV analysis. It must remain immutable.
Request files pin that binding's `COMPLETED.json` by complete byte count and SHA-256.

```sh
java -cp /absolute/sniffles-execution-development.jar \
  org.jlinalg.cli.SnifflesWorkflowCli --request /absolute/request.json

java -cp /absolute/sniffles-execution-development.jar \
  org.jlinalg.cli.SnifflesVerifyCli \
  --result /absolute/COMPLETE_BATCH --out /absolute/NEW_VERIFICATION
```

The verifier requires the corresponding helper/profile edition. It can run on
Windows without the original Linux runtime. A verifier success is explicitly a
frozen integrity/consistency result, not a new caller run.

## Typed input contract and retained outputs

`schema_version: 1` requests contain `out`, `runtime: {path, pin}`, `inputs`,
`analyses` and optional `limits`. Each input declares an ASCII `id`, `kind`,
absolute `path` and complete `pin: {bytes, sha256}`. Supported kinds are `bam`,
`cram`, `fasta`, `bed` and `vcf`. Alignments may refer to a declared FASTA input by
`reference`; CRAM requires it. Every declared input must be used. There is no
arbitrary command or argument field. See `SnifflesWorkflowRequest.java` for the
closed schema and all explicit capacity limits.

Each analysis selects `id`, exact `edition` and alignment `input`. Optional
settings are `tandem_repeats`, `genotype_vcf`, `phase`, `legacy_genotype`,
`threads`, `minsvlen`, `minsupport`, `mapq`, `minimum_alignment_length` and
`sample_id`. Omitted/null optional scalars leave original source defaults.
Unsupported legacy modes are skipped; legacy `--cluster` is not substituted for
read-assignment `--phase`. Different samples/technologies remain separate
analyses. Merging, caller consensus, haplotagging and downstream paper filters
are separate operations.

The workflow captures unchanged inputs, streams complete alignments through
original pysam, creates NEW indexes and validates references before calling.
It does not sort, filter, repair alignments or consume/modify supplied indexes.
Available M5 digests are checked; a BAM dictionary without M5 does not establish
assembly identity. CRAM uses an owned reference cache generated from declared
matching FASTA, with local-only `REF_PATH`/`REF_CACHE`. Early Sniffles versions
do not pass their configured reference to every CRAM opening. The local cache
uses HTSlib's unformatted uppercase, MD5-named sequence convention documented in
the [official reference-sequence guide](https://www.htslib.org/doc/reference_seqs.html).
CRAM/BGZF-reference and CSI calling validation remains pending in this checkpoint.

Each case retains complete original stdout/stderr, the original VCF, all header
lines, full raw variant rows, INFO/FORMAT/sample values and native summaries.
Captured legacy BAM path sample labels are retained with explicit original to
captured input mapping. No output projection or silent identifier repair is
used. Original fatal diagnostics are checked even if a source program exits zero.
The whole batch verifies source/runtime/input/output integrity and owned-worker
cleanup before admission. Logs and warnings remain visible on failure.

Owned Linux processes have wall/CPU/address-space/file/open-file limits and
sampled workspace/process/thread/RSS accounting. RSS is sampled, not a hard
aggregate memory reservation. Limits apply per command except complete-workspace
budgets. Native exports reserve byte allowances before writing and never drop
rows to conceal capacity failures. PID start identities and owned sessions/cwd
are checked during cleanup; unrelated processes are not killed.

## Frozen checkpoint inspection

`sniffles-execution-development/VALIDATION.json` pins both artifacts, source
snapshots and a content-addressed evidence ZIP. Run:

```sh
python tools/literature/sniffles_execution_development_evidence.py
python tools/literature/sniffles_execution_development_evidence.py --archive-only
```

The default checks current source pins and preservation of the canonical
executable. Archive-only checks the portable frozen bundle. Neither launches
Java, Python scientific packages or Sniffles. The archive retains unsuccessful
preparation/control attempts with their errors and the corrected FIFO claim.
Live calling batches are excluded from this checkpoint's validation claims.
