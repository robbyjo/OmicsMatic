# StringTie: typed assembly, estimation, merge and portable verification

This workflow runs the exact original StringTie executable through the Java
project, with native Java input/output audits and a complete captured analysis
bundle. Short-read assembly, guided assembly, reference-only estimation with
gene/Ballgown tables, and transcript merging are supported. It does not implement
alignment, long-read/mixed-read modes, differential expression or whole-paper
pipelines. Cell Ranger remains excluded.

**Use the development artifact recorded in
[`stringtie-typed-workflow/VALIDATION.json`](stringtie-typed-workflow/VALIDATION.json).**
The older canonical/released JAR does not contain this workflow. The standalone
entry point is `org.jlinalg.cli.StringTieToolsCli`; root-command/catalog promotion
is a subsequent integration step. This checkpoint remains local and unreleased.

## Warnings and recovery

**SKIP3 means skip the dependent analysis.** Read console stderr and, when an
owned output directory exists, `WARNINGS.md` and `FAILURE.json`. Supply the named
exact runtime, helper, library, native Linux filesystem, readable procfs or
execution capability only where installation/execution are permitted. Otherwise
leave the analysis skipped. Execution requires **Linux x86_64 Java**, including
Linux Java inside WSL on Windows. Copied-bundle verification and setup export also
work with Windows Java. Do not substitute another edition automatically.

**FAIL2 means no result is admitted.** Invalid/changed inputs or evidence,
original process errors, failed native output checks, exhausted capacities,
cancellation and finalization failures retain the named stage and diagnostics.
Preserve the complete attempt, read its original logs, correct the named cause
and retry into NEW output paths. Never delete or overwrite original inputs or
partials to make a failed attempt appear successful. Host loader/configuration
changes during an admission invalidate that attempt; retry on a stable supported
host. The development controls retain an actual `/etc/resolv.conf` census change
as a failed attempt rather than ignoring it.

Require exit0, `COMPLETED.json`, its exact complete manifest/file inventory, no
`INCOMPLETE.json` or `FAILURE.json`, and successful `--verify`. Setup or synthetic
health completion alone does not admit a user fit. An empty output is explicit
and is not biological absence. Estimation omissions are never filled with zeros.

Read every analysis `WARNINGS.md`, `metadata/INPUT-AUDIT.json`,
`metadata/OUTPUT-AUDIT.json`, `fit/process.stderr`, `fit/WARNINGS.md`,
`fit/process.json` and the runtime process logs. Native audits preserve original
values and uncertainty; they do not certify biological accuracy or independently
infer StringTie's counts and coverage from reads. Gene-reduction residuals are
diagnostic. Denominator bounds constrain source arithmetic; they do not recover
hidden normalization quantities. Complete input/workspace limits do not guarantee
available JVM heap. Aggregate RSS is sampled, not a hard aggregate memory bound.

## Install the exact runtime and run a first example

Read the [standalone setup guide](STRINGTIE_INTAKE_SETUP_DEVELOPMENT.md).
Export the kit with this development JAR and follow the exported `RUN-SETUP.txt`.
Installation and download require the corresponding explicit installer flags and
must be permitted by the operating environment. No installation/download occurs
during analysis. Keep runtime/setup paths outside the analysis directory.

Exact supported editions are **1.3.0, 1.3.6, 2.1.2, 2.1.7, 2.2.1, 3.0.0 and
3.0.3**. Select the profile needed by the study; an unspecified paper edition
remains unspecified. Original edition-specific licenses are retained. Exact
StringTie1.3.0 lacks the exposed `--rf`/`--fr` interface; requesting it is SKIP3.

From the repository root with Linux Python/Java, set the JAR path recorded in
the validation file (replace this variable with that absolute path):

```bash
jar=/mnt/d/projects/JLinAlg/build/literature-review/stringtie-runtime/workflow-matrix-3/omicsmatic-0.3.7-stringtie-workflow-development.jar

java -cp "$jar" org.jlinalg.cli.StringTieToolsCli \
  --export-runtime-setup /var/tmp/stringtie-kit-NEW

# Read /var/tmp/stringtie-kit-NEW/RUN-SETUP.txt. Only where permitted:
python3 -I -B /var/tmp/stringtie-kit-NEW/install_stringtie.py \
  --kit /var/tmp/stringtie-kit-NEW \
  --profile linux-x86_64-stringtie-3.0.3 \
  --home /var/tmp/stringtie-home-NEW \
  --report /var/tmp/stringtie-setup-NEW \
  --cache /var/tmp/stringtie-cache \
  --allow-install --allow-download

python3 -I -B examples/stringtie/prepare_example.py \
  --kit /var/tmp/stringtie-kit-NEW \
  --out /var/tmp/stringtie-example-NEW

java -cp "$jar" org.jlinalg.cli.StringTieToolsCli \
  --recipe /var/tmp/stringtie-example-NEW/estimate.json \
  --home /var/tmp/stringtie-home-NEW \
  --setup-report /var/tmp/stringtie-setup-NEW \
  --out /var/tmp/stringtie-example-NEW/estimation

java -cp "$jar" org.jlinalg.cli.StringTieToolsCli \
  --verify /var/tmp/stringtie-example-NEW/estimation
```

Use NEW directories with existing canonical parents. Runtime/analysis paths must
be absolute ASCII paths without whitespace on the native Linux filesystem.
The [example README](../../examples/stringtie/README.md) also gives assembly and
merge commands. Its inputs are artificial two-contig data: 840 mapped reads,
120 spliced reads, four reference transcripts and three genes. Preparing that
example performs no analysis, installation or download.

## Supply your own typed request

This complete reference-only estimation request is copyable after replacing the
paths and genome-build label. All input paths must be absolute and canonical:

```json
{
  "schema_version": 1,
  "profile": "linux-x86_64-stringtie-3.0.3",
  "mode": "ESTIMATE",
  "bam": "/data/study/aligned.coordinate.bam",
  "reference_sizes": "/data/study/reference.sizes",
  "guide": "/data/study/annotation.gtf",
  "merge_inputs": [],
  "genome_build": "GRCh38-with-exact-study-reference-provenance",
  "strand": "UNSTRANDED",
  "bam_policies": {
    "missing_NH": "ERROR",
    "missing_XS": "ERROR",
    "sort_header": "REQUIRE_COORDINATE"
  },
  "population": "COMPLETE",
  "threads": 4,
  "parameters": {
    "trimming": "ORIGINAL_DEFAULT",
    "minimum_length": null,
    "minimum_isoform_fraction": null,
    "minimum_coverage": null,
    "minimum_FPKM": null,
    "minimum_TPM": null
  },
  "limits": {}
}
```

`reference.sizes` contains exact `name<TAB>positive_length` rows, with no header,
blank rows or aliases. Lengths/coordinates use the positive signed32 domain.
The supplied dictionary must match the complete BAM dictionary. The genome-build
label records a declaration; it does not authenticate a FASTA, guide or assembly.

The BAM transport is complete ordinary BGZF BAM, with a canonical terminal EOF.
Every physical block, frame, record, dictionary, auxiliary tag, coordinate order
and decoded record is checked. No filtering, sorting, aliasing or conversion
occurs. SAM, CRAM and `CG` long-CIGAR transport require separate admission.
Duplicate, secondary, supplementary, QC-fail and unmapped records/flags remain
retained; mate reciprocity and the original usable-read population are not
inferred. Empty BAM is explicit. Missing/zero NH may use the inspected source's
fallback of one only with `missing_NH: "ORIGINAL_ONE"`; missing/zero and assumed
counts stay distinct. `missing_XS: "KEEP_UNRESOLVED"` retains unresolved spliced
strand explicitly. Alignment orientation and `ts` do not silently replace `XS`.
`sort_header: "VERIFY_RECORDS"` permits an undeclared sort header while still
requiring actual complete coordinate order.

`strand` is `UNSTRANDED`, `FR_FIRSTSTRAND` (source `--rf`) or `FR_SECONDSTRAND`
(source `--fr`). Choose the study's exact library convention. The recorded P171
literal `-rf` failed in all seven original editions; the workflow does not accept
or silently correct that spelling. Its documented `--rf` operation is separate
and remains unsupported by the exact1.3.0 interface.

For `ASSEMBLE`, keep `bam`, use an optional `guide` (or null), set `population`
to null, and leave `merge_inputs` empty. For `MERGE`, set `bam`, `bam_policies`
and `population` to null, use `strand: "UNSTRANDED"`, and provide the complete
ordered `merge_inputs` list. `guide` is optional. Merge inputs retain their exact
bytes and can be original assembled or merged GTFs; genuinely empty source
assemblies are explicit. Reference annotations require at least one complete
exon-bearing transcript.

An unset threshold means the exact original edition's default, recorded as null;
no later release's numerical defaults are substituted. The typed mapping is:

| Setting | Original option | Operations |
| --- | --- | --- |
| `trimming: "DISABLE"` | `-t` | assembly |
| `minimum_length` | `-m` | assembly, merge |
| `minimum_isoform_fraction` | `-f` | assembly, merge |
| `minimum_coverage` | `-c` | assembly, merge |
| `minimum_FPKM` | `-F` | merge |
| `minimum_TPM` | `-T` | merge |

For reference-only estimation, keep these thresholds null and trimming at
`ORIGINAL_DEFAULT`. `population: "COMPLETE"` requires every guide transcript to
appear. `"RETAIN_OMISSIONS"` records every omitted reference identity and does
not invent zero abundance. Extra transcripts or changed reference geometry are
errors under either policy. All six gene/Ballgown tables, complete feature/link
populations, original cell precision and exact-edition normalization constraints
are checked. Assembly/merge transcript discovery is performed by the original
runtime and structurally audited; it is not a reimplementation of its fitter.
GTF/GFF3 conversion and adjacent guide-exon loader normalization are not inferred.

## Capacities and result bundles

`limits: {}` selects explicit saved defaults. Important defaults include combined
input 8GiB, decoded BAM 32GiB, ten million BAM records, text 256Mi characters,
five million GTF features, 500,000 transcripts, 16MiB metadata, one day wall/CPU,
16GiB process address space, 64GiB per file and 256GiB complete workspace. These
are capacities, not expected usage. Large studies may need deliberate changes
before execution and sufficient Java heap.

Supported override keys are `input_bytes`, `bam_decoded_bytes`, `bam_records`,
`gtf_lines`, `gtf_features`, `gtf_transcripts`, `gtf_exons_per_transcript`,
`gtf_attributes`, `line_characters`, `text_characters`, `table_rows`,
`topology_work`, `references`, `metadata_bytes`, `wall_seconds`, `cpu_seconds`,
`address_space_bytes`, `file_bytes`, `open_files`, `workspace_bytes`, `files`,
`processes`, `threads`, `sampled_rss_bytes` and `poll_millis`. Unknown keys fail.
The complete selected policy is saved in `metadata/policy.json`; no capacity is
silently raised after a failure. The final bundle must leave 16MiB workspace for
finalization metadata. Fresh synthetic runtime health has its own small fixed
capacities; it does not consume the user-fit settings silently.

A completed bundle retains the unchanged JSON recipe and sources, complete setup
receipt, current-host/runtime admission and fresh synthetic health, original fit
command/stdout/stderr/cleanup ledger, complete raw GTF/tables, native audits,
warnings and a closed manifest. The final guard is removed only after complete
captured verification and a final live source/runtime check.

Copy the entire bundle to another machine and run `--verify` using the same
development JAR. On Windows, use Windows Java and a Windows bundle path. Recorded
Linux producer paths remain opaque provenance; the verifier opens only captured
inputs and outputs. It reparses the complete BAM, GTF, tables, normalization and
synthetic health, checks command/exit/cleanup bindings, warning text and all file
hashes/types/populations, and rejects extra or partial members. Verification
does not rerun fitting or attest the current live producer host. Hashes establish
integrity, not authenticity, historical execution or biological accuracy.

Validation includes real seven-edition original fits, independent original
output comparisons, copied verification on three JVM environments, consistently
rehashed invalid-bundle rejection, an actual cancelled owned user process, and
the documented standalone example. Exact counts, retained failures, source pins,
artifact identity and remaining integration work are in the validation record.

All 24 protocol cases passed independent comparisons with the pinned original
controls, and all 72 copied-bundle verifications passed across Windows Java21,
Windows Java25 and Linux Java21. Each environment passed 1,353 tests without
skips. The successful validation reused 23 individually complete fits only after
rebuilding the exact same candidate bytes and preserving every scientific class;
all copied bundles were reverified on all three environments. The last protocol
was newly fitted. This avoids rerunning successful analyses after an unrelated
host-change or validation-harness failure.

The standalone exporter, example preparer and all three documented operations
also passed, followed by three copied Windows verifications. The example's
one-input default merge matched a fresh unmodified original invocation. An actual
owned user fit was cancelled after launch, retained FAIL2 diagnostics and its
guard, and left no live owned workers. Eight consistently rehashed invalid-bundle
cases were rejected by the actual public CLI on all three JVM environments.

The example launcher emitted a WSL systemd-user-session startup warning in
`evidence/workflow-example-1/native.stderr` in the evidence archive. The launcher,
native checks, all fits and cleanup still succeeded. This workflow does not need
a systemd user service; no systemd configuration was changed. If a separate WSL
service depends on that session, inspect its `journalctl` diagnostics separately.
For a StringTie failure, follow the named SKIP3/FAIL2 stage and logs above.

Earlier failed attempts remain retained. The first Java compilation rejected an
unchecked long-to-int capacity conversion. The first full workflow attempt
retained 18 valid fits before a live resolver-config change invalidated runtime
admission. A second retained 23 valid fits before a harness assertion incorrectly
expected `ALL_ZERO_UNRESOLVED` for an empty BAM. The native checker correctly
reported `NO_POSITIVE_NUMERATOR`, with zero positive numerators and outputs; the
assertion was corrected without changing the fitter, parser or normalization
method. Copied Linux verification uses byte-identical copied JARs and complete
bundles on the native Linux filesystem to avoid incidental DrvFs metadata costs.

Eight detailed paper-role reviews and five supporting pointers have also been
prepared without changing the catalog. They correct P102's missed 1.3.6 version
in the draft, keep P110's unreported TPM edition distinct from its 2.1.2 assembly
edition, preserve P109's unclear mapping-state wording, P171's literal flag
failure, and P183's unresolved reference/guide compatibility. Applying those
reviews, root dispatch, catalog promotion and its UI/provider checks remain next.
