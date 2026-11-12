# HOPS: current scoped integration

The project provides an original HOPS `me_po` adapter for candidate assessment
from classified MALT RMA6 output. It runs the unchanged HOPS0.35-distribution
launcher, original MaltExtract1.7 under Java8 and original AMPS R under R4.0.5.
It retains complete inputs, extraction tables, candidate PDFs, heatmap, TSV and
workspace and checks the scientific fields with native Java.

**The scope is output-producing candidate assessment.** Manual authentication,
MALT index/alignment, historical database reconstruction, unknown author settings
and paper-wide biological reproduction remain separate. The canonical/released
OmicsMatic executable and root dispatcher are unchanged. Use the standalone
development application and exact external providers below.

The source-bound [qualification checkpoint](hops-mepo-development/VALIDATION.json)
and [saved replay](hops-mepo-development/PUBLIC_REPLAY.json) retain their original
zero-credit development scope. Their admission-pending notices describe that
historical checkpoint. Current catalog admission is recorded separately in
[the catalog proof](omics-2023-2026/hops-mepo-adapter-validation.json) and
[paper-role coverage](omics-2023-2026/hops-mepo-adapter-role-coverage.json).
The fixed250-paper implementation and semantic-completeness review remain open.

## Choose the method explicitly

| Profile | Meaning |
| --- | --- |
| `source-def-anc` | Original sampling, destacking and duplicate removal remain enabled |
| `complete-def-anc` | Those three operations are deliberately disabled, changing the method |

**Stock1.7 can process only the first10,000 assigned reads even if its
downSampling? output says Off.** Retain stock results and warnings; skip
full-population interpretation when this occurs. Choose a complete profile only
as an explicit scientific method change and start a NEW run. Do not switch
merely to make a plot/check pass. The controls differ in51 scientific files
between profiles, with two versus four expected candidate/heatmap PDFs.

## Prepare and run

Use native Linux x86_64 application JDK21. JDK21/JDK25-built applications have
been run under Linux JDK21; Linux JDK25 execution is untested. In WSL, use a NEW
output on its native filesystem, such as `/var/tmp`, not `/mnt/c` or `/mnt/d`.
The original plotting output prefix accepts only ASCII letters/digits, slash,
underscore, dot, colon or hyphen. Unsafe prefixes/target identities are refused
without renaming.

Follow the [MaltExtract setup guide](MALTEXTRACT_INTEGRATION.md) for the full
MaltExtract1.7/Java8 provider and complete matching `ncbi.map`/`ncbi.tre`.
Follow the [R-provider setup guide](HOPS_POSTPROCESSING.md) for public R4.0.5/HOPS.
Download/install only where permitted. No installed runtime or upstream program
is bundled in the development application.

From the repository root, build a NEW application directory:

```sh
python3 tools/literature/hops_mepo/build.py \
  --java-bin /path/to/jdk21/bin --out /var/tmp/hops-mepo-build-NEW
```

Supply one literal target taxon name per line and a complete TSV with real tabs:

```text
sample_id	rma6
sampleA	/path/to/sampleA.rma6
sampleB	/path/to/sampleB.rma6
```

Relative RMA paths resolve against the TSV directory. Sample labels start with
an ASCII letter, contain only letters/digits/underscore/hyphen and are at most64
characters. Case-colliding labels and missing/ambiguous taxa are refused.

```sh
java -Xmx3g -jar /var/tmp/hops-mepo-build-NEW/hops-mepo-development.jar \
  --java8-runtime /var/tmp/maltextract17-runtime \
  --post-runtime /var/tmp/hops-r405 \
  --resources /path/to/complete-resources \
  --targets /path/to/targets.txt \
  --rma-list /path/to/samples.tsv \
  --profile source-def-anc \
  --input-provenance supplied \
  --out /var/tmp/hops-mepo-NEW
```

Use `native-accession-alternative` provenance when the RMA came from that
explicitly declared MALT index alternative. This does not establish the authors'
historical index. Read `--help` for exact capacity/resource options. Adapter
capacity failures refuse complete analysis, while the selected upstream stock
method's own sampling is preserved.

Inspect `bundle/WARNINGS.md`, both process stderr files, and original outputs at
`original/maltExtract`. Preserve the full run. This driver configuration does not
request JSON. Classes are screening criteria; missing/insufficient evidence
remains unassessable and does not establish absence.

## Verify saved evidence

```sh
java -Xmx3g -jar /var/tmp/hops-mepo-build-NEW/hops-mepo-development.jar \
  --verify /path/to/saved-run/bundle \
  --reader-runtime /path/to/exact-maltextract17-reader \
  --post-runtime /var/tmp/hops-r405 \
  --out /var/tmp/hops-mepo-verify-NEW
```

Verification needs the exact external1.7 reader and R provider, not the original
Java8 installation or old input paths. It rechecks complete captured data,
methods, commands, scientific fields and freshly decoded RData. It repeats no
fitting/plotting and authenticates no producer.

## Warnings and required actions

**SKIP3: required exact runtime, allowed installation or Linux execution is
unavailable. Skip the dependent analysis.** Retain the failed attempt and its
diagnostics. Install the exact providers in NEW permitted locations or leave
that analysis skipped. Fitting also needs executable `/usr/bin/python3`,
`/usr/bin/setsid` and `/usr/bin/prlimit`.

**FAIL2: no result is admitted.** Read `FAILURE.json`, `WARNINGS_FAILURE.md` and
the named `process.stderr`. Resolve the named input, capacity, integrity,
process or scientific issue before a NEW run. Do not edit saved evidence to
make verification pass.

The original driver can return0 after a child returns1. The adapter independently
checks both child exits, fatal extraction logs and complete outputs. Failed or
empty candidate plots admit no result; inspect profiles manually. Nonfinite
values remain literal. Checksums establish integrity, not biological truth or
producer authenticity. The declared version/profiles do not recover P002's
unreported HOPS settings or its dated reference database.
