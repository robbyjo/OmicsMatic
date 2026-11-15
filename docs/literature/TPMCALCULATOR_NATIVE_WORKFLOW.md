# Native TPMCalculator file workflow

This development milestone adds a runnable Java workflow and saved-input replay to the [native core](TPMCALCULATOR_NATIVE_DEVELOPMENT.md). **Catalog admission remains pending; this milestone adds zero catalog credit.** The 250-paper catalog still reports 121 scoped new integrations, including 20 native methods. The larger literature and implementation task remains open.

The standalone entry point is `org.jlinalg.cli.TpmCalculatorCli`. It reads GTF and BAM files, calculates the selected original counting/TPM rules in Java, and writes source-layout tables plus complete state and provenance. The worker is another Java process; no original TPMCalculator, aligner, counting program or installer is invoked. It uses the project's exact HTSJDK 5.0.0 transport with JDK decompression explicitly selected.

## Run an analysis

Use Java 17 or later and build the project application:

```powershell
.\gradlew.bat executableJar --no-daemon --console=plain
```

Save the following as `request.json`. Replace the GTF and BAM names with your own permitted local files. Relative input paths resolve from the request file's parent. Every option is explicit; the values below are project choices, not settings recovered from the paper.

```json
{
  "schema": 1,
  "profile": "native-java-tpmcalculator-0.0.4-gnu64",
  "annotation": "annotation.gtf",
  "samples": [{"name": "sample1", "bam": "sample1.bam"}],
  "options": {
    "gene_key": "gene_id",
    "transcript_key": "transcript_id",
    "intron_cutoff": 16,
    "minimum_mapq": 0,
    "minimum_overlap": 8,
    "properly_paired_only": false,
    "extended_output": true,
    "all_features": true
  },
  "limits": {
    "input_bytes": 536870912,
    "bundle_bytes": 536870912,
    "files": 10000,
    "wall_seconds": 120,
    "heap_mib": 256
  }
}
```

The output must be a new directory under an existing canonical parent, separate from all inputs and any saved bundle. Keep the application, dependency and input files at canonical paths without symbolic links or hard links. Sample names must be nonempty and exactly distinct. Their order controls the original sample-state normalization sequence. Display names are preserved; generated paths use sample ordinals to avoid turning identifiers into filenames.

```powershell
java -cp build/cli/omicsmatic-0.3.7.jar org.jlinalg.cli.TpmCalculatorCli run --request request.json --out analysis-001
java -cp build/cli/omicsmatic-0.3.7.jar org.jlinalg.cli.TpmCalculatorCli verify --bundle analysis-001 --out verification-001
```

The same commands work in a POSIX shell. Alternatively, use `build/libs/OmicsMatic-0.3.7.jar` together with the exact declared HTSJDK 5.0.0 JAR on the classpath: separate the two paths with `;` on Windows or `:` on Linux. The official dependency JAR SHA-256 is `59d862b74ec3e847ec84bc87bed34cecd1a80095495d965b9403f863e374e99c`. A different HTSJDK edition is an unavailable prerequisite for this fixed profile. The standalone class must be named explicitly; the root command dispatcher does not register this development workflow.

The byte limits are aggregate capacities, not per-file sizes. Input capture has its own aggregate limit. The bundle limit covers captured inputs, products, metadata and streams. The file count includes directories. Each request, metadata document and state line has a 16 MiB capacity. The worker has the requested Java heap limit; wall time, workspace size, file count and streams are monitored. These are not operating-system total-memory or disk quotas. Choose adequate permitted limits for your own data; the example is a small-run configuration. Capacity failures retain partial records and admit no result. Integer options require integer JSON tokens, not values such as `8.0`; unknown keys and duplicate keys fail explicitly.

## Read the result and warnings

**A successful worker return is insufficient.** Accept an analysis only when the public process exits zero and its final `COMPLETED.json` has `operation: "run"` and `result_admitted: true`. Read `WARNINGS.md`, `WARNINGS.json` and `products/CONDITIONS.json` before interpreting the values.

`products/sample-000001/` contains `genes.out`, `genes.ent` and `genes.uni`. With `extended_output: true`, it also contains `transcripts.out` and `transcripts.ent`. Conditions record the exact sample-name-to-directory mapping. `all_features` controls the source's gene-table selection; original transcript selection remains subject to its processed flag. The complete state always includes modeled genes, transcripts and features, including missing/present-zero data and unprocessed records.

`products/STATE-SCHEMA.json` defines all 32 state columns. `state-raw.jsonl` retains every returned double's raw 64-bit IEEE representation. `state.jsonl` retains exact hexadecimal finite values, infinities and the NaN classification. Source tables use the selected original six-significant-digit format, including rounding of large summary counts. Their NaN spelling is the declared canonical GNU token `-nan`; raw native NaN sign and payload remain in the raw state. Replay compares exact semantic state and table bytes, without requiring identical NaN payload/sign across Java hosts. It does not replace nonfinite values with zero.

The [core guide](TPMCALCULATOR_NATIVE_DEVELOPMENT.md) describes inherited counting rules and input boundaries. In particular, literal `-AS` gene exclusions, separate mate counts, strand-independent assignment, unusual CIGAR/block behavior, GNU gene ordering, bounded backwards scans, partial splice updates, repeated-transcript behavior, unsigned coordinates and single-precision bridge TPM are retained. Overlapping annotations can produce nonfinite results. A scientific change to the input or rules requires a new analysis; do not silently repair a saved result.

| Public exit | Meaning | Required action |
| --- | --- | --- |
| `0` | Analysis admitted, or saved-input verification passed. | Inspect the final marker and warnings. Verification has `operation: "verify"`, `saved_replay_verified: true`, and `result_admitted: false`; it is not another analysis. |
| `3` (`SKIP3`) | A named permitted input, output prerequisite, Java executable or exact dependency/implementation is unavailable. | Supply the prerequisite named in stderr and `FAILURE.json`, where an output directory could be created. If installation or access is forbidden, leave the analysis skipped. Retry only in a new output directory. |
| `2` (`FAIL2`) | Parsing, computation, replay, capacity, input integrity, cleanup or recording failed. | Preserve the partial directory. Read `FAILURE.json`, `failure-trace.txt`, `WARNINGS.md`, and complete `worker/stdout.bin` and `worker/stderr.bin` when present. Resolve the named cause and use new output paths. Never consume partial products as a completed analysis. |

Failures after native computation returns, during parent finalization, or after completion promotion revoke admission. Any quarantined `COMPLETED-UNADMITTED-*.json` is historical evidence only. If failure recording itself cannot finish, stderr still states that no complete result is admitted. An existing output directory is preserved and rejected.

## Provenance and verification

The bundle captures the full original request, effective choices, complete GTF/BAM bytes, source file identities, implementation class/dependency hashes, Java/runtime details, fixed worker recipe, launch arguments and clean environment, complete streams, termination, all products, conditions, warnings and a complete inventory. The final marker is written last. Hashes establish byte integrity; they do not authenticate authorship.

`verify` checks that complete inventory, all recorded contradictions and conditions, then launches a fresh native worker using the saved GTF/BAM bytes. The original external input locations need not exist. It compares recomputed semantic state and every source table, and checks that the saved bundle remained unchanged. Resealing hashes around a wrong scientific row does not make replay pass. Verification requires the same recorded scientific implementation; preserve the matching application artifact for long-term replay. Do not put verification outputs inside the saved bundle or verify a verification directory as though it were an analysis.

## Comparison scope and evidence

The selected original is TPMCalculator tag **0.0.4**, commit `b275fb3d749dc46e7e0efeca9bdd1c230702339d`; its executable reports **0.0.3**. Source-equivalent quantification code in tag 0.0.6 was inspected, but its executable was not run. These are project comparison identities. P050 does not identify its TPMCalculator version, options, exact GTF or output selection. This workflow does not reproduce the paper's biological results.

The qualification comprises **141 passing focused tests**, **64 actual packaged source-table comparisons and complete-input replays on each of Windows Java 25 and Linux Java 21**, nine public failure controls on each host, and five cross-host replays in each direction. There are also **50,049 original C++ numeric-formatting controls**. The 57 original full-state cases check all 32 fields, exact integer/presence inventories and nonfinite classifications, with a 64-ULP finite floating tolerance. Source-layout output tables are byte-compared. Seven further original cases cover output modes, custom annotation keys and control characters in identifiers. Synthetic controls establish this bounded comparison scope, not general input compatibility or biological accuracy.

The self-contained application JAR used in the quick start also passed five complete-bundle replays on each host, using bundles produced on the other system.

The [workflow qualification](tpmcalculator-workflow-development/QUALIFICATION.json) and complete indexed record archive preserve successful and deliberately failed public calls. The earlier immutable component evidence retains prior native comparison failures and original build failures. To check the saved public evidence without installing an original tool:

```powershell
python -B tools/literature/tpmcalculator_validation/freeze_workflow_records.py
python -B tools/literature/tpmcalculator_validation/freeze_core_records.py
```

Focused source checks can be rerun with:

```powershell
.\gradlew.bat test --tests org.jlinalg.cli.TpmCalculatorWorkflowTest --tests org.jlinalg.cli.TpmCalculatorOriginalTest --tests org.jlinalg.sequence.TpmCalculatorOrderTest jar --no-daemon --console=plain
```

Catalog review and registry admission are separate pending work. The P050 literal `iDEP.96` also needs its own missing-family review; it receives no implementation credit from this TPMCalculator workflow.
