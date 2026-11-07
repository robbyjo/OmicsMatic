# StringTie project command and catalog scope

The development build now exposes `stringtie-tools` through the project's main
command interface. The earlier [standalone workflow](STRINGTIE_WORKFLOW.md),
scientific classes, seven exact editions and complete analysis-bundle format are
unchanged. This is a local development integration; the canonical/released JAR
does not contain this command yet.

Use the exact development artifact recorded in
[`stringtie-main-workflow/VALIDATION.json`](stringtie-main-workflow/VALIDATION.json).
For this checkout, with **Linux x86_64 Java** (including permitted Linux Java
inside WSL on Windows):

```bash
jar=/mnt/d/projects/JLinAlg/build/literature-review/stringtie-runtime/root-matrix-1/omicsmatic-0.3.7-stringtie-tools-development.jar

java -jar "$jar" stringtie-tools --help
java -jar "$jar" stringtie-tools --runtime-profiles
java -jar "$jar" stringtie-tools --export-runtime-setup /var/tmp/stringtie-kit-NEW
```

Read the exported `RUN-SETUP.txt` and the
[setup instructions](STRINGTIE_INTAKE_SETUP_DEVELOPMENT.md). Install/download only
where permitted, using explicit installer flags and the exact selected profile.
Setup success is separate from analysis success. Keep the managed runtime and
setup-report directories outside the analysis directory. Execution performs no
automatic installation or download.

The [synthetic example](../../examples/stringtie/README.md) prepares complete
inputs and typed JSON recipes without running an analysis. Its standalone
commands also work with this new JAR. The equivalent project commands are:

```bash
python3 -I -B examples/stringtie/prepare_example.py \
  --kit /var/tmp/stringtie-kit-NEW --out /var/tmp/stringtie-example-NEW

java -jar "$jar" stringtie-tools \
  --recipe /var/tmp/stringtie-example-NEW/assemble.json \
  --home /var/tmp/stringtie-home-NEW \
  --setup-report /var/tmp/stringtie-setup-NEW \
  --out /var/tmp/stringtie-example-NEW/assembly

java -jar "$jar" stringtie-tools \
  --recipe /var/tmp/stringtie-example-NEW/estimate.json \
  --home /var/tmp/stringtie-home-NEW \
  --setup-report /var/tmp/stringtie-setup-NEW \
  --out /var/tmp/stringtie-example-NEW/estimation

java -jar "$jar" stringtie-tools \
  --recipe /var/tmp/stringtie-example-NEW/merge.json \
  --home /var/tmp/stringtie-home-NEW \
  --setup-report /var/tmp/stringtie-setup-NEW \
  --out /var/tmp/stringtie-example-NEW/merged

java -jar "$jar" stringtie-tools \
  --verify /var/tmp/stringtie-example-NEW/estimation
```

Run assembly before merge, since the merge recipe references that output.
Estimation is independent. Use NEW directories with existing canonical parents.
The [complete recipe reference](STRINGTIE_WORKFLOW.md) explains assembly,
estimation, merge, source thresholds, missing-tag/strand choices and capacities.
Copied-bundle verification and setup export also work with Windows Java.

## Warnings and required actions

**SKIP3 means skip the dependent analysis.** Supply the named exact runtime,
helper, library, Linux filesystem, readable procfs or execution capability only
where permitted. Otherwise keep the analysis skipped. Read stderr, `WARNINGS.md`
and `FAILURE.json` when an attempt directory could be created. Do not silently
substitute editions. `--export-runtime-setup` only copies the standalone kit.

**FAIL2 means no result is admitted.** Invalid or changed inputs/evidence,
original process errors, failed native audits, capacity exhaustion, cancellation
or finalization failure retain their named stage and logs. Preserve all partials,
correct the cause and retry into NEW output paths. Read `fit/process.stderr`,
`fit/process.json`, runtime logs and both native audits under `metadata/`.

Require exit0, `COMPLETED.json`, its complete manifest/file inventory, no
`INCOMPLETE.json` or `FAILURE.json`, and successful `--verify`. A copied verifier
opens only captured inputs/results, not recorded producer paths. It does not
rerun the fitter or attest a current live producer host. Hashes establish
integrity, not authenticity or biological accuracy.

**Reference, population and numerical uncertainty remain explicit.** Declare the
exact sizes, build label, compatible guide and source settings. No aliases,
sorting, filtering, input conversion or missing-value zero filling occur.
`RETAIN_OMISSIONS` retains missing guide identities without invented values.
Gene-reduction residuals are diagnostic; hidden denominators are not recovered
and TPM sums are not forced. Empty output is not biological absence. Complete
capacities are not JVM heap guarantees, and aggregate process RSS is sampled.

Native audits cover complete ordinary BGZF BAM, supported GTF, all six original
gene/Ballgown tables and exact-edition normalization. SAM, CRAM, long-CIGAR
transport, GFF3 conversion, adjacent guide-exon loader normalization and long-read
or mixed-read fitting need separate admission. Alignment, FASTA extraction,
annotation integration, differential expression and complete paper pipelines
remain separate. Cell Ranger remains excluded.

## Source roles retained in the catalog

All eight StringTie source papers were reviewed against their exact primary XML
and paragraph hashes. The update preserves the 250-paper cohort, every existing
tool observation and the earlier819 reviews, while adding eight reviewed uses
and five supporting pointers. Paper-wide semantic completeness remains open.

* P101:2.1.7 default assembly provides transcriptome evidence for MAKER; alignment
  and transcript-sequence extraction remain separate.
* P102 explicitly reports1.3.6 for TPM. The earlier automated null-version
  extraction is corrected; the guide and exact quantification command remain
  unspecified.
* P109's1.3.0 mapping-state wording remains unclear and is not relabeled as
  alignment or claimed as reproduced.
* P110's unreported TPM edition remains distinct from2.1.2 transcript assembly
  and the separate Trinity/Mikado/PASA annotation workflow.
* P171's literal `-rf` fails in all seven retained original editions. The explicit
  documented `--rf` control is separate and supported by six editions. The3.0.0
  assembly and merge roles retain the stated settings and unprinted commands.
* P183 retains guided `-G` assembly and `--merge`, an unspecified edition and
  unresolved hg19/GENCODEv38 compatibility. GffCompare, filtering and RNA-SeQC
  expression estimation remain separate.
* P223's unspecified HISAT2/StringTie/Ballgown wording does not imply a precise
  StringTie command. Salmon counts, edgeR and junction counting remain separate.
* P248 uses2.2.1 assembly as annotation evidence; an unprinted strand convention
  is not inferred from the strand-specific input description.

The frozen standalone evidence contains 27 successful user-fit cases,75 copied
bundle verifications,1,353 tests per JVM environment, original comparisons and
retained negative controls. Root integration changes one dispatcher class only;
its exact overlay,1,361 tests per environment, actual main-command fits/verifies
and untouched scientific entries are recorded in the new validation checkpoint.
These controls establish the documented scope, not biological or whole-paper
replication. Literature pages remain withheld from deployment until the next
authorized build.
