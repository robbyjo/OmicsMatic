# Exact historical KING2.0 and2.2.7: bounded development adapter

**KING2.0 has a reproducible count/coefficient discrepancy. The adapter rejects
the entire analysis whenever its original output disagrees with the complete
native genotype census.** In the author's332-participant example, both official
Linux2.0 builds report17,752 jointly called variants for one pair; the captured
BED data contain17,738. Their reported kinship is also different. The
`--bit64` experiment did not remove that discrepancy. Original values and
native diagnostics are preserved; no repair, padding workaround, genotype
regeneration, hidden flag or version substitution is applied.

This checkpoint runs the exact original default `--kinship` or `--duplicate`
operation for an explicitly selected version. It complements the independently
frozen [KING2.3.2 adapter](KING_INTEGRATION.md). It does not change the released
JAR or add a root CLI command. Catalog admission, hosted PLINK2/SNPRelate roles
and the full250-paper goal remain unfinished; catalog95/98/246 is unchanged.

| Outcome | Admission | Required action |
| --- | --- | --- |
| Exit0, `result_admitted=true`, complete `--verify` passes | Complete declared transport checks passed. | Read warnings, original reports, `scientific-audit.json` and native count dispositions before downstream use. |
| Exit2, scientific discrepancy or other failure | No result is admitted, including otherwise successful source steps. | Preserve the attempt. Inspect `scientific-audit.json`, `native-pair-counts.tsv`, original logs and `unadmitted-worker/`. Resolve the named cause explicitly and retry new paths. A different version is a new scientific request, not reproduction of KING2.0. |
| Exit3, required runtime/platform unavailable | Dependent exact-version analysis is skipped. | Supply the named compatible permitted original runtime with the exported helper. Permit downloads only where allowed. If installation is forbidden or prerequisites cannot be supplied, keep the analysis skipped. |

The author's [history page](https://www.kingrelatedness.com/history.shtml) links
the original archives. Keep original KING programs, author source and archives
private under the author's research-use terms. Our public artifacts contain
our code and synthetic controls, without those original programs/source or the
author's example genotypes.

## Prepare an exact private runtime

The Java17-targeted standalone adapter is built separately with JDK21 and JDK25;
execution is tested on Windows Java21/25 and Linux Java21.

```powershell
java -jar docs/literature/king-historical-development/king-historical-javac21.jar --help
java -jar docs/literature/king-historical-development/king-historical-javac21.jar --export-runtime-setup D:/work/historical-king-setup-new
python D:/work/historical-king-setup-new/setup_king_historical_runtime.py --research-use --profile 2.2.7-windows --executable D:/private/king227.exe --out D:/private/king227-profile-new
```

For a Linux profile, execute setup with Linux Python and analysis with Linux
Java, explicitly choosing WSL and translating paths where appropriate.

```bash
python3 /work/historical-king-setup-new/setup_king_historical_runtime.py --research-use --profile 2.0-linux --executable /private/king20 --out /private/king20-profile-new
```

Available exact profiles are `2.2.7-windows`, `2.2.7-linux`, `2.0-linux` and
`2.0-ubuntu`. The two2.0 Linux archives are distinct original executables with
distinct library populations. No Windows2.0 executable is supplied by this
checkpoint. A Linux request cannot execute with Windows Java.

If downloading is permitted, replace `--executable PATH` with
`--allow-download`. Setup checks the exact original archive and executable
hashes. It installs no operating-system packages and performs no analysis.
Required Linux packages are named in setup warnings: `zlib1g`, `libstdc++6`,
`libgcc-s1`, `libgomp1`, `libc6`, and `libquadmath0` for the2.2.7 profile. Have
the environment administrator supply missing prerequisites where permitted;
otherwise keep analysis skipped. Existing runtime libraries remain unchanged.

## Request and verify

All fields and capacities are required. Unknown fields and arbitrary source
flags are rejected. `version` must explicitly match the runtime profile.
`mode` is `kinship` or `duplicate`, with `degree: 0`. No unrelated-selection or
IBD mode is inferred from a paper's version statement.

```json
{
  "schema_version": 1,
  "version": "2.2.7",
  "mode": "kinship",
  "degree": 0,
  "threads": 1,
  "bfile": "/work/prepared/cohort",
  "runtime_profile": "/private/king227-profile-new/runtime-profile.json",
  "out": "/work/historical-king-result-new",
  "limits": {
    "input_bytes": 100000000,
    "output_bytes": 200000000,
    "output_files": 1000,
    "samples": 500,
    "variants": 20000,
    "pair_variant_operations": 2000000000,
    "working_bytes": 200000000,
    "wall_seconds": 120,
    "rows": 200000
  }
}
```

```bash
java -Xmx1g -jar /work/king-historical-javac21.jar --request /work/request.json
java -Xmx1g -jar /work/king-historical-javac21.jar --verify /work/historical-king-result-new
```

Windows uses the same options with Windows paths. Saved verification is
portable and requires no original KING installation. It checks the complete
saved file population, version/runtime/command bindings, captured input hashes,
native count report and scientific audit. It does not execute KING or
authenticate the producer.

Input requirements and conservative capacities match the2.3.2 adapter:
caller-prepared strictly coordinate-sorted unique-ID A/C/G/T SNPs on numeric
diploid autosomes1..22, a complete variant-major BED/BIM/FAM, and unique
composite FID/IID values. The adapter performs no filtering, sorting, pruning,
imputation or identifier/allele repair. Complete native pair counts require
storage growing with sample pairs and work growing with pairs times variants;
production GWAS cohort scale is not validated. Capacities are logical checks,
not hard operating-system quotas.

## Scientific and source distinctions

* All original genotype inputs and reports remain byte-identical. Windows2.2.7
  also leaves `original$TMP$.ped` and `original$TMP$.dat`; those original files
  are retained and included in the complete result inventory.
* Unfiltered Windows2.2.7 `.kin0` is headerless. The parser attaches its typed
  schema while preserving the original first row and all report bytes.
* KING2.0 prints within-family `HetHet` at three decimals;2.2.7 prints four.
  Verification uses those observed precisions. Jointly called variant counts
  are exact; no count discrepancy is tolerated. The other checked printed
  fractions/coefficients retain their actual source precisions.
* Native counts check every within-family and between-family row. The original
  within-family formula uses average heterozygosity; between-family uses the
  smaller denominator. Negative finite values remain unchanged. Source-zero
  coefficients at zero heterozygosity remain unassessable. Native NaN/negative
  infinity and dispositions remain visible in `native-pair-counts.tsv`.
* Duplicate reports retain the original default80% heterozygote-concordance
  screen. Returned IBS counts and all three concordance fractions are checked.
  A source no-duplicate declaration is retained; absence and candidate-screen
  completeness are not independently refitted.
* Original pedigree `Z0`, `Phi` and `Error` remain source results. Their pedigree
  inference, biological validity, cohort-overlap decision and sample-removal
  policy are not independently reproduced.

The source-bound [KING paper audit](NATIVE_KING_ROBUST.md) separates the actual
uses. P130 namesKING2.2.7 to identify ROSMAP/NACC overlap and preferentially keep
NACC records, without specifying an exact flag or cutoff. P224 namesKING2.0
and removal at kinship≥0.0884 across sex strata, without specifying the removal
tie policy. This adapter does not invent those missing details. The native
[exact cutoff API](NATIVE_KING_THRESHOLDS.md) evaluates explicitly chosen count
formulas and strict/inclusive comparators; it cannot repair a failed2.0 run or
infer a paper's retained participants.

## Evidence and pending scope

[VALIDATION.json](king-historical-development/VALIDATION.json) and its public
synthetic archive bind code, artifacts, complete outputs and negative controls.
The source probe also compared both2.0 builds with and without `--bit64` and
retained the discrepancy. Synthetic8191/8193/8194/8207-variant inputs expose
the source discrepancy at incomplete16-variant words; an8208-variant control
and the8192-variant control pass. Inputs are newly declared controls; no
original input is padded or changed to admit an analysis.

The full36-case execution suite checks all four profiles, the complete private
author example, duplicate/no-duplicate behavior, synthetic boundaries and
portable saved verification. Further checks exercise JDK25 execution/replay,
resealed scientific/identity/warning mutations, skip protocols and attempts to
promote actual source-discrepant output into an admitted result. That promotion
is rejected by replaying the native scientific audit.

```powershell
python tools/literature/verify_king_historical_evidence.py
```

The public verifier runs saved checks on own synthetic inputs, including complete
scientific replay of deliberately promoted negative controls. It never executes
an original KING program. The frozen2.3.2/native artifacts, released JAR and
unrelated worktree changes remain preserved. Root CLI/catalog integration,
PLINK2-hosted KING and the SNPRelate/GENESIS context remain unfinished. The
SNPRelate setup helper currently prepares a private original R runtime only;
installation is not analysis admission.
