# Original extraction and HOPS assessment from RMA files

**Development workflow: full HOPS/MALT qualification and biological paper
reproduction remain unfinished.** This coordinator runs original MaltExtract1.7
and the original HOPS0.35-distribution AMPS R script in sequence. It retains the
complete extraction, plots, workspace and process records, and checks that the
assessment consumed the exact extraction produced by this run. It does not
build a MALT index, align reads or automate manual authentication.

The only current fitting profile is explicit `complete-def-anc`. It disables
original sampling, destacking and duplicate removal. That changes the method;
it is not an inferred setting from P002 or a substitute for stock HOPS settings.
The original driver and stock ancient-extraction workflow still need separate
qualification. This development workflow adds no catalog tool credit.

Current controls cover 41 focused/adjacent tests on each of Windows JDK21 and
JDK25, complete saved workflows and nine specific contradiction refusals. An
independent original-launcher run matches the coordinator's 172 scientific
extraction files, 280 RData fields, TSV and four PDFs apart from dates. JDK21-
and JDK25-built applications have run under Linux JDK21; Linux JDK25 execution
is untested. Complete portable combined-workflow evidence packaging remains
pending. The original driver comparison used the declared complete profile;
it does not qualify its stock profile.

## Prepare complete inputs and permitted providers

Use native Linux x86_64 with application JDK21. In WSL, use a NEW directory
under `/var/tmp`, not a Windows-mounted filesystem. Original plotting requires
an output prefix containing only ASCII letters/digits, slash, underscore, dot,
colon or hyphen. Spaces and shell punctuation are refused before fitting.

Prepare the full MaltExtract1.7/Java8 provider and complete matching
`ncbi.map`/`ncbi.tre` resources using the
[MaltExtract integration guide](MALTEXTRACT_INTEGRATION.md). Prepare the isolated
public R4.0.5 provider using the
[HOPS postprocessing guide](HOPS_POSTPROCESSING.md). Install/download only where
permitted. A modern Java runtime cannot replace the original Java8 fitting
provider. The separate modern application coordinates that provider.

Supply complete classified RMA6 files, a UTF-8 target file with one literal
taxon name per line, and a TSV such as:

```text
sample_id	rma6
sampleA	/path/to/sampleA.rma6
sampleB	/path/to/sampleB.rma6
```

Use real tab characters. Relative RMA paths resolve against the list's directory.
Sample labels must be unique and follow the MaltExtract guide's restrictions.
No aliases, renamed taxa or silently filtered populations are supplied.

## Build and run

From the repository root:

```sh
python3 tools/literature/hops_assessment_workflow/build.py \
  --java-bin /path/to/jdk21/bin --out /var/tmp/hops-assessment-build-NEW

java -Xmx3g -jar /var/tmp/hops-assessment-build-NEW/hops-assessment-workflow-development.jar \
  --maltextract-runtime /var/tmp/maltextract17-runtime \
  --post-runtime /var/tmp/hops-r405 \
  --resources /path/to/complete-resources \
  --targets /path/to/targets.txt \
  --rma-list /path/to/samples.tsv \
  --profile complete-def-anc \
  --input-provenance supplied \
  --emit-json true \
  --out /var/tmp/hops-assessment-NEW
```

Choose `--input-provenance native-accession-alternative` instead when the RMA
was produced using the explicitly declared native accession-to-taxonomy index
alternative. That choice records provenance; it does not establish that an
author's historical index was reproduced. `--emit-json false` retains the
original non-JSON workflow. Read `--help` for complete capacity/resource options.
Shared limits are passed to both stages; exceeding a limit refuses the complete
analysis rather than sampling it.

On success, inspect the root `WARNINGS.md`, `extraction/bundle/WARNINGS.md`,
`assessment/bundle/WARNINGS.md`, both stage stderr logs and the original plots
under `assessment/original`. Preserve the whole directory. The root
`COMPONENT_COMPLETED.json` records a completed software workflow, not biological
authenticity. Source classes are screening criteria. Missing or insufficient
evidence remains unassessable; it does not establish absence.

## Verify a saved complete workflow

Saved verification needs the exact external1.7 reader JAR, the permitted public
R4.0.5 provider and application JDK21. It does not require the original Java8
fitting runtime or the old input paths to remain available:

```sh
java -Xmx3g -jar /var/tmp/hops-assessment-build-NEW/hops-assessment-workflow-development.jar \
  --verify /path/to/complete-saved-workflow \
  --reader-runtime /path/to/maltextract17-reader \
  --post-runtime /var/tmp/hops-r405 \
  --out /var/tmp/hops-assessment-verify-NEW
```

This rechecks both complete stages, their exact handoff, original input and
resource declarations, and freshly decoded RData scientific fields. It repeats
no original fitting/plotting and does not authenticate the producer.

## Warnings, errors and recovery

**SKIP3: a required runtime, installation or Linux execution is unavailable or
forbidden. No dependent assessment is admitted.** Retain `FAILURE.json`,
`WARNINGS_FAILURE.md` and the named stage's stderr. Install the exact provider
in a NEW permitted location, or leave the dependent analysis skipped.

**FAIL2: inputs, capacity, identity, scientific checks or an original process
failed. No final workflow result is admitted.** Follow the named error and
retain every failed attempt. Correct the input or resource issue, then use a
NEW output directory. Do not alter saved evidence to make verification pass.

An extraction may finish before later assessment fails. Its independently
completed bundle remains retained and can be used with the separate
postprocessing application after resolving that stage's prerequisite. This does
not turn the failed combined workflow into a completed one. Empty-candidate
plotting failures and insufficient data must not be relabeled as absence.
