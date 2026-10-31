# Eagle synthetic phasing example

Use the JAR built from this checkout. Run `java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --help` to confirm that the command is present. The archive contains synthetic inputs with 32 variants, four target samples, eight reference samples and a simple genetic map.

On Windows, extract the inputs from the repository root:

```powershell
Expand-Archive -LiteralPath examples/eagle-phasing-workflow/synthetic-inputs.zip -DestinationPath examples/eagle-phasing-workflow/inputs
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --recipe examples/eagle-phasing-workflow/cohort-check.json --out build/eagle-cohort-check-1
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --recipe examples/eagle-phasing-workflow/reference-check.json --out build/eagle-reference-check-1
```

On Linux, the standard Python library can extract the same inputs:

```bash
python3 -c "import zipfile; zipfile.ZipFile('examples/eagle-phasing-workflow/synthetic-inputs.zip').extractall('examples/eagle-phasing-workflow/inputs')"
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --recipe examples/eagle-phasing-workflow/cohort-check.json --out build/eagle-cohort-check-1
```

Input checks run natively and launch no Eagle process. Read `WARNINGS.md`, `input-audit.json`, and the preparation ledgers. A successful check has `COMPLETED.json` with `input_check_completed: true` and `scientific_result_admitted: false`.

## Check BCF inputs

The separate `synthetic-bcf-inputs.zip` contains original-BCFtools-generated target/reference BCF2.2 files, their source CSI files and the same map. Its SHA-256 is `6e1a175e6857f06e74952b9879f78bf8573d9ed4d4217e27ff88c2f5f26a1df0`. Extract to a separate directory:

```powershell
Expand-Archive -LiteralPath examples/eagle-phasing-workflow/synthetic-bcf-inputs.zip -DestinationPath examples/eagle-phasing-workflow/inputs-bcf
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --recipe examples/eagle-phasing-workflow/cohort-bcf-check.json --out build/eagle-cohort-bcf-check-1
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --recipe examples/eagle-phasing-workflow/reference-bcf-check.json --out build/eagle-reference-bcf-check-1
```

On Linux, extract with the standard Python library:

```bash
python3 -c "import zipfile; zipfile.ZipFile('examples/eagle-phasing-workflow/synthetic-bcf-inputs.zip').extractall('examples/eagle-phasing-workflow/inputs-bcf')"
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --recipe examples/eagle-phasing-workflow/cohort-bcf-check.json --out build/eagle-cohort-bcf-check-1
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools --recipe examples/eagle-phasing-workflow/reference-bcf-check.json --out build/eagle-reference-bcf-check-1
```

These recipes explicitly retain source indexes with `index_policy: "snapshot-only-regenerate"`. **The indexes are provenance only:** they are protected and hashed, never queried or certified against the binary source. Fresh prepared-input CSI files are generated and validated. Retain `source-intake/` and review its binary snapshots, decoded VCF, conversion ledgers and warnings. The [BCF guide](../../docs/literature/omics-2023-2026/EAGLE_BCF_STATUS.md) explains format limits and required actions.

For actual phasing, use Linux Java and an admitted private Eagle 2.4.1 home. The [workflow guide](../../docs/literature/omics-2023-2026/EAGLE_WORKFLOWS.md) explains setup and recipe choices.

```bash
java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools \
  --recipe examples/eagle-phasing-workflow/cohort-phase.json \
  --home /path/to/admitted/eagle-home --out build/eagle-cohort-phase-1

java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools \
  --recipe examples/eagle-phasing-workflow/reference-phase.json \
  --home /path/to/admitted/eagle-home --out build/eagle-reference-phase-1

java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools \
  --recipe examples/eagle-phasing-workflow/cohort-bcf-phase.json \
  --home /path/to/admitted/eagle-home --out build/eagle-cohort-bcf-phase-1

java -jar build/cli/omicsmatic-0.3.7.jar eagle-tools \
  --recipe examples/eagle-phasing-workflow/reference-bcf-phase.json \
  --home /path/to/admitted/eagle-home --out build/eagle-reference-bcf-phase-1
```

**SKIP3 means skip the dependent analysis:** its runtime or capability is unavailable. BCF2.1, newer VCF header semantics and historical Eagle profiles require separate admission. **FAIL2 means no result is admitted:** inspect the retained input, integrity, resource or process error and rerun to a new output directory. Require `COMPLETED.json` and its matching manifest hash before accepting a result. These synthetic examples test call contracts and do not measure biological phase accuracy.
