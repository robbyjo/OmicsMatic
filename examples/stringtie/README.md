# Synthetic StringTie assembly, estimation and merge

These artificial two-contig inputs exercise the complete workflow. They are not
human data or a reproduction of a published study. There are 840 mapped reads,
120 spliced reads, four guide transcripts and three genes.

Use the exact development JAR recorded in
[`STRINGTIE_WORKFLOW.md`](../../docs/literature/STRINGTIE_WORKFLOW.md).
The older canonical/released JAR does not contain this workflow. Run execution
commands with **Linux x86_64 Java**, including permitted Linux Java inside WSL on
Windows. Use private directories on the native Linux filesystem and NEW paths.

1. Export the standalone kit with the development JAR, then follow
   [`RUN-SETUP.txt`](../../src/main/resources/org/jlinalg/omics/stringtie-runtime-setup/README.txt)
   in that export and the
   [setup guide](../../docs/literature/STRINGTIE_INTAKE_SETUP_DEVELOPMENT.md).
   Install/download only where permitted. Select the same exact profile for the
   managed runtime and the example. Setup completion does not admit an analysis.
2. Prepare the synthetic inputs and three JSON requests. This performs no
   download, installation or analysis:

   ```bash
   python3 -I -B examples/stringtie/prepare_example.py \
     --kit /var/tmp/stringtie-kit-NEW \
     --out /var/tmp/stringtie-example-NEW \
     --profile linux-x86_64-stringtie-3.0.3
   ```

3. Set the development JAR path from the workflow guide and run the three
   operations below. Replace the example, home and setup-report paths with the
   exact paths you created. Run assembly before merge; merge reads the assembly
   output. Estimation can run independently.

   ```bash
   java -cp "$jar" org.jlinalg.cli.StringTieToolsCli \
     --recipe /var/tmp/stringtie-example-NEW/assemble.json \
     --home /var/tmp/stringtie-home-NEW \
     --setup-report /var/tmp/stringtie-setup-NEW \
     --out /var/tmp/stringtie-example-NEW/assembly

   java -cp "$jar" org.jlinalg.cli.StringTieToolsCli \
     --recipe /var/tmp/stringtie-example-NEW/estimate.json \
     --home /var/tmp/stringtie-home-NEW \
     --setup-report /var/tmp/stringtie-setup-NEW \
     --out /var/tmp/stringtie-example-NEW/estimation

   java -cp "$jar" org.jlinalg.cli.StringTieToolsCli \
     --recipe /var/tmp/stringtie-example-NEW/merge.json \
     --home /var/tmp/stringtie-home-NEW \
     --setup-report /var/tmp/stringtie-setup-NEW \
     --out /var/tmp/stringtie-example-NEW/merged
   ```

4. Require exit0, `COMPLETED.json`, its complete manifest, no `INCOMPLETE.json` or
   `FAILURE.json`, and successful verification. Read every analysis `WARNINGS.md`,
   `metadata/INPUT-AUDIT.json`, `metadata/OUTPUT-AUDIT.json` and the original
   `fit/process.stderr`, `fit/WARNINGS.md` and runtime logs.

   ```bash
   java -cp "$jar" org.jlinalg.cli.StringTieToolsCli \
     --verify /var/tmp/stringtie-example-NEW/estimation
   ```

**SKIP3 means skip the dependent analysis.** Supply the named exact runtime,
helper, library, filesystem or execution capability only where permitted. Read
stderr and retained `FAILURE.json`; do not substitute another version silently.

**FAIL2 means no result is admitted.** Preserve the attempt, read the named stage
and original logs, correct invalid inputs or explicitly review insufficient
capacities, and retry into NEW output paths. Original inputs and partial results
are not deleted or overwritten.

Copied complete bundles can also be verified on Windows using Windows Java and
the copied bundle path. Verification opens only captured inputs/results; it does
not open Linux producer paths or reexecute the original fit. Checksums prove
integrity, not authenticity or biological accuracy. Cell Ranger remains excluded.
