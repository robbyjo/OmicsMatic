# Original RepeatMasker workflow: development guide

**Validation is still in progress. Use synthetic data while this gate is open.**
The new public entry points compile and current-profile searches have passed
independent full-output comparisons. The complete profile/case audit and catalog
admission remain pending. RepeatMasker is outside the completed tool count.
Use a JAR built from the current source; the earlier published 0.3.7 release
does not provide these development commands.

## Prepare and admit a runtime

Run original programs with Linux x86-64 Python and Java, including inside WSL
on Windows. Native annotation reuse remains available separately. First follow
the [payload setup guide](REPEATMASKER_PAYLOAD_SETUP.md) to obtain and verify a
permitted pinned payload. Setup and runtime admission are distinct operations.

Create `admit-current.json` with your actual absolute Linux paths:

```json
{
  "profile": "linux-x86_64-repeatmasker-4.2.6-custom-rmblast",
  "payload_home": "/opt/my-repeatmasker-current-payload",
  "python": null,
  "out": "/opt/my-repeatmasker-current-runtime"
}
```

The output must not exist. It must be separate from the sealed payload. Admit
it explicitly:

```sh
java -Xmx512m -jar /path/to/current-source-built.jar repeat-tools \
  --admit-runtime-options-file admit-current.json
```

Admission runs fixed original controls, captures the complete declared host
before and after them, and seals the retained evidence. Admission does not
complete a study search. For the historical profile, use
`linux-x86_64-repeatmasker-4.1.5-dfam3.7-rmblast`, its separately installed
historical payload and the exact permitted CPython 3.12.3 executable in
`python`; use a different NEW runtime home. The historical resource is the
pinned bundled Dfam 3.7 input. A paper mentioning a default Dfam library does
not establish that it used this exact release.

## Run the small synthetic example

From a source checkout, prepare three synthetic query records and their
declared custom repeat library. This helper extracts the byte-pinned fixtures
from the JAR and writes the complete recipe, options and input provenance. It
does not install software, admit a runtime or run an analysis.

```sh
python3 examples/repeat-original-workflow/prepare_inputs.py \
  --jar /path/to/current-source-built.jar \
  --runtime /opt/my-repeatmasker-current-runtime \
  --out /path/to/NEW-repeat-demo

java -Xmx512m -jar /path/to/current-source-built.jar repeat-tools \
  --options-file /path/to/NEW-repeat-demo/search-options.json

java -Xmx512m -jar /path/to/current-source-built.jar repeat-tools \
  --verify /path/to/NEW-repeat-demo/result
```

Require exit 0, `result/COMPLETED.json`, its matching manifest and successful
verification. Read `result/WARNINGS.md`, `session.json`, `analysis-status.json`
and `results.json`. The original annotation and masked FASTA, when produced,
are in `result/original/post-input`; both original process ledgers are retained
under `original/search` and `original/postprocess`. A genuine no-hit run keeps
the upstream masked FASTA and summary table absent. It does not fabricate an
unchanged mask or certify biological absence.

To exercise explicit identifier/case mapping and a gzip input, prepare a
different NEW demo with `--map-query-ids --gzip-query`. Original UTF-8 headers,
identifiers and base-case hashes remain in the source snapshots and mapping
ledger. The declared staging policy maps query identifiers to `Q0000000001`,
`Q0000000002`, etc., and uppercases DNA. Review
`result/prepared/preparation.json` to interpret annotation identifiers.
Library identifiers stay unchanged. DNA `U` is rejected; no U-to-T substitution
is inferred. Exact-source searches require upstream-compatible ASCII query
identifiers and uppercase DNA.

## Replace the synthetic inputs with reviewed study inputs

Start from a NEW preparation directory and edit its complete `recipe.json`.
Provide your assembly, organism, genome and library provenance, annotation
provenance, coordinate policy and paper-settings review. Set `resource_root`
to the common canonical directory containing every declared input; input paths
cannot escape it. Declare each plain/gzip encoding, staging policy, masking
mode and scientific parameter explicitly. The current profile supports a
supplied custom library. The historical profile also supports explicit
species and simple-repeat requests. Unreported paper settings are not guessed.

The example's whole-workflow ceiling is 64 GiB and 100,000 members. This is a
limit, not a disk preallocation. It covers protected inputs, source resources,
generated caches, both full host captures, original outputs and final metadata.
Complete logical hardlink bytes count. Per-stage process limits remain
separate; their wall limit is not a whole-workflow deadline. Review FASTA,
annotation, index, heap, process and workspace capacities for the complete
study. Capacity failures do not truncate data or select a smaller subset.

## Warnings, failures and required actions

**SKIP3: missing, forbidden or unsupported prerequisites.** Supply the exact
permitted payload, provider and admitted runtime, or use Linux Java inside
WSL. If installation is forbidden, keep the warning and leave the dependent
analysis skipped. Analysis and admission never download or install software.

**FAIL2: invalid input, integrity change, execution failure, exceeded capacity
or interruption. No completed result is admitted.** Read the named error and
retained diagnostics. Preserve the failed attempt, correct the cause and use
NEW output paths. Never repair a sealed payload/runtime in place.

**Host dependency changed during capture:** stabilize the host configuration
and retry in a NEW output. If the stable dependency identity differs from the
admitted runtime, admit a NEW runtime on that host. The check is not relaxed
because a host file is rewritten automatically.

Keep the exact canonical external recipe/input paths, admitted runtime and
payload when retaining a completed search. Verification independently reads
their evidence and replays the declared staging transformation. These bundles
are not portable by copying or renaming their directory. Read-only verification
runs no original program or host probe and does not admit the current host.
Checksums are integrity evidence, not authenticity signatures. No completion
flag certifies biological accuracy, whole-paper reconstruction or absence of
repeats. Cell Ranger remains excluded.
