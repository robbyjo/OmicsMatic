# Standalone KING2.3.2 adapter: bounded development checkpoint

This adapter runs the exact original research-use KING2.3.2 program. It supports
kinship, degree2/3 filtering, degree2/3 unrelated selection, duplicate screening
and Linux IBD coordinates. A separate Java counter checks the complete captured
hard-call population. Original reports and identifiers remain unchanged.

**Read the exit code, `COMPLETED.json` and `WARNINGS.md` before using a result.**

| Outcome | Meaning | Required action |
| --- | --- | --- |
| `0`, `result_admitted=true`, complete `--verify` passes | The requested original method completed and its declared transport checks passed. | Read the scope, original logs, provenance and count dispositions before downstream analysis. |
| `3`, `SKIP3_KING_DEPENDENCY_UNAVAILABLE` | The exact permitted runtime/platform is unavailable. No dependent analysis is admitted. | Supply the named prerequisite using the exported setup helper. Allow download only where permitted. If installation is forbidden or unavailable, keep the dependent analysis skipped. |
| `2`, `FAIL2_NO_KING_RESULT_ADMITTED` | An input, capacity, runtime, process, fallback or output check failed. | Preserve the attempt and inspect its cause and original logs. Correct the cause explicitly; retry in new paths. All retained worker outputs remain unadmitted. |

In particular, the tested **Windows program does not produce IBD coordinate
files**. An `ibdseg` request with that profile is skipped before execution and
names Linux as the required platform. The adapter does not silently switch hosts.
KING can itself replace IBD analysis with kinship for small samples. Such an
exit-zero fallback is rejected as a result of the requested IBD method.

The catalog currently retains **95 new integrations, 98 including existing
adapters, and 246 missing tools**. This checkpoint is additional development work
and adds no catalog credit yet. The full 250-paper goal remains open. KING2.0,
KING2.2.7, PLINK2-hosted KING and SNPRelate roles need separate closure. The
[15-paper KING role audit](NATIVE_KING_ROBUST.md) distinguishes those contexts.

## Run the development adapter

The two standalone Java17 artifacts are built independently with JDK21 and
JDK25. They include our Java adapter and the unchanged native count component;
they contain no original KING program, author source or example data. They do
not replace the released executable or add a command to the current root CLI.

```powershell
java -jar docs/literature/king232-development/king232-javac21.jar --help
java -jar docs/literature/king232-development/king232-javac21.jar --export-runtime-setup D:/work/king-setup-new
python D:/work/king-setup-new/setup_king_runtime.py --research-use --executable D:/private/king.exe --out D:/private/king-profile-new
```

For Linux, run the helper under Linux Python and analysis under Linux Java. In
WSL, explicitly choose the distribution and translate your paths to `/mnt/...`.

```bash
java -jar /work/king232-javac21.jar --export-runtime-setup /work/king-setup-new
python3 /work/king-setup-new/setup_king_runtime.py --research-use --executable /private/king --out /private/king-profile-new
```

If no executable is available and downloading is permitted, replace
`--executable PATH` with `--allow-download`. The helper checks the exact archive
and executable hashes. It prepares a profile; it performs no analysis and
installs no operating-system packages. Missing Linux prerequisites are named in
its prominent warning: `zlib1g`, `libquadmath0`, `libgomp1` and `libc6`. Have the
environment administrator supply those packages where installation is allowed.
Otherwise keep the dependent analysis skipped.

Use KING within its author's research-use terms. Keep its programs, source and
archives private. Consult the author's [download page](https://www.kingrelatedness.com/Download.shtml)
and [manual](https://www.kingrelatedness.com/manual.shtml) for upstream terms and
method details. This project distributes its own adapter; it does not
redistribute or relicense KING. The author's
[history page](https://www.kingrelatedness.com/history.shtml) links older versions;
their intake alone does not validate an older analysis.

## Request

Save this as a new UTF8 JSON file. All fields and capacities are required;
unknown fields and arbitrary upstream arguments are rejected. Paths can be
absolute or relative to the request file. The BED, BIM and FAM must share the
given prefix. Adjust capacities for the complete input population and the
available environment.

```json
{
  "schema_version": 1,
  "mode": "kinship",
  "degree": 0,
  "threads": 1,
  "bfile": "/work/prepared/cohort",
  "runtime_profile": "/private/king-profile-new/runtime-profile.json",
  "out": "/work/king-result-new",
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
java -Xmx1g -jar /work/king232-javac21.jar --request /work/request.json
java -Xmx1g -jar /work/king232-javac21.jar --verify /work/king-result-new
```

The same invocation works with Windows paths and the Windows runtime for
supported modes. Saved `--verify` works on Windows or Linux without an upstream
KING installation. It replays the complete saved census and transport checks.
It does not execute KING or authenticate the producer.

| `mode` | `degree` | Original operation |
| --- | --- | --- |
| `kinship` | `0` | `--kinship`, complete within-family and between-family reports |
| `kinship` | `2` or `3` | `--kinship --degree N`, with complete within-family reports and source-filtered between-family pairs |
| `unrelated` | `2` or `3` | `--unrelated --degree N`, preserving the original keep/remove choice |
| `duplicate` | `0` | `--duplicate` with the source's default80% heterozygote-concordance screen |
| `ibdseg` | `0` | Linux `--ibdseg`, complete returned pair summaries and coordinate records |

The adapter requires caller-prepared, strictly coordinate-sorted, unique-ID
biallelic A/C/G/T SNPs on numeric diploid autosomes1..22. Composite FID/IID values
must be unique. It rejects unsupported data without silently filtering,
sorting, pruning, imputing, changing alleles, rewriting genotypes or repairing
identifiers. The source may reject otherwise well-formed data and request
genotype regeneration; that failure and its original instructions are retained.
Any preparation is a separate explicit scientific decision.

This checkpoint performs a complete native census over all sample pairs. Its
working storage grows with the number of pairs, and its work grows with pairs
times variants. It is bounded development functionality; production GWAS cohort
scale has not been validated. The conservative `working_bytes` estimate and
other logical capacities are admission checks, not hard operating-system quotas.

## What is checked and retained

`results/` holds the untouched original tables, logs and process records.
`inputs/` holds complete byte-identical BED/BIM/FAM captures. The request,
runtime profile, original-input hashes, complete bundle inventory and
`provenance.json` bind the run and its declared method. Failure outputs are
retained under `unadmitted-worker/` or `unadmitted-results/` and are never
admitted by `--verify`.

`native-pair-counts.tsv` lists every original sample pair, jointly called SNPs,
both-heterozygote calls, opposite homozygotes, both asymmetric heterozygote counts
and explicit native minimum-heterozygosity dispositions. Its diagnostic
coefficient column is explicitly named as the **between-family** profile, even
when the pair is within one family. It does not replace the original
within-family coefficient.

* Kinship checks cover complete pair populations and the source's degree-filtered
  between-family population. Within-family coefficients use the average
  heterozygosity denominator; between-family coefficients use the smaller
  denominator. The unchanged native component supplies exact hard-call counts.
* The tested unfiltered Windows `.kin0` is headerless. All its rows remain
  unchanged; the typed schema is attached by the parser. Windows and Linux can
  differ at the final printed decimal, so count-derived comparisons respect
  the source's four-decimal rounding.
* **Zero smaller-heterozygosity denominators are unassessable.** Original KING
  can print a zero coefficient there. The native count report retains its own
  NaN/negative-infinity arithmetic and disposition; the source zero is never
  treated as evidence of unrelatedness. Negative finite coefficients remain
  unchanged.
* Unrelated selection checks the exact disjoint keep/remove partition of all
  original genotyped participants. It preserves the original algorithm's
  choices. It does not independently prove optimal selection or biological
  unrelatedness.
* Duplicate reports are checked against exact native IBS counts and genotype,
  homozygote and heterozygote concordance fractions. No reported duplicate is
  not a proof of biological absence; screen completeness is not independently
  refitted.
* Linux IBD endpoints, chromosomes, physical spans, SNP counts and genome
  fractions are checked against the captured BIM and usable genome segments.
  The original coordinate header contains an extra empty tab field; its bytes
  remain unchanged while the typed whitespace schema identifies12 data fields.
  `PropIBD` uses source-quantized four-decimal IBD1/2 fractions. KING excludes
  pairs without a segment longer than10Mb and short segments below3Mb.

Pedigree `Z0`, `Phi` and `Error`, inference labels, segment placement and selection
are retained original results. The native transport checks do not independently
refit those methods, establish biological validity or reproduce a paper. Do not
apply strict paper thresholds to rounded printed coefficients as exact values.
Standalone KING, PLINK2-hosted KING and SNPRelate remain distinct contexts.

The exact executable is pinned. Linux execution also checks the complete tested
dynamic-library mapping, those library files and loader metadata before and
after analysis, with loader environment overrides cleared. Windows system DLLs
are not independently pinned. Process monitoring checks observed owned workers;
it is not an operating-system containment or producer-authentication mechanism.

## Evidence and remaining work

[VALIDATION.json](king232-development/VALIDATION.json) records the actual controls
and artifact/source hashes. Its public archive contains only our synthetic
inputs, reports on those inputs, own-code snapshots and execution metadata. It
contains no upstream program/source or original author example data. The larger
author example was tested privately and is summarized by hashes and counts.

Both compiler artifacts are tested through Windows and Linux execution and
portable replay. Checks include all54,946 pairs in the332-participant author
example, degree2/3 reports, keep/remove partitions, a deliberately appended
duplicate, the source allele-major rejection, and source fallbacks. Separate
synthetic controls include a declared duplicate and15 unassessable pairs.
Resealed mutations test numerical fields, population completeness, coordinates,
input bindings, process termination, command identity, runtime identity and
required warnings. Dependency and setup failures test the skip protocol.

```powershell
python tools/literature/verify_king_evidence.py
```

Public evidence verification checks hashes and replays the synthetic saved
bundles without running upstream KING. Original study genotypes, historical
software behavior, exact study cutoffs, PLINK2/SNPRelate hosting and root CLI /
catalog admission remain separate unfinished work. Previously validated native
and DIAMOND artifacts, the released JAR and unrelated worktree edits are
preserved.
