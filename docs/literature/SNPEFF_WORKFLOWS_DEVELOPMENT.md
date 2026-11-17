# SnpEff annotation workflows: development profile

**Build current source. Released OmicsMatic 0.4.0 does not contain these commands.**
This work adds no catalog integration credit yet. The full 250-paper goal remains
open. The four source roles below remain distinct from the local controls.

**Missing or forbidden prerequisite: SKIP3, skip the dependent analysis.** Obtain
the named permitted runtime/reference and retry a **new** output directory.
**Other errors: FAIL2, admit no complete result.** Read `FAILURE.txt`,
`WARNINGS.md`, `diagnostics.tsv` and `original.stderr`; retain inputs, partials and
failed attempts. Do not refresh hashes, replace editions, or edit a bundle to
make verification succeed.

## Selected editions and reported roles

| Paper | Reported SnpEff use | Required distinction |
|---|---|---|
| [P002](https://doi.org/10.1038/s41586-024-08515-5) | 3.1i, build 2012-12-12; functional annotation of ancient/modern *T. pallidum* SNPs. The author repository specifies TXT input/output and `-ud 100`. | The custom `TpallidumNC0214902` reference represents NC_021490.2. MultiVCFAnalyzer integration and lineage comparisons are separate. |
| [P045](https://doi.org/10.1038/s41586-025-09811-4) | Annotated biallelic SNPs after lifting to GRCh38, using `hg38kg`. | Engine version and complete options remain unknown. Do not substitute another GRCh38 transcript database. |
| [P171](https://doi.org/10.1038/s41588-026-02678-9) | SnpEff 5.2 annotated SNPs and small indels in tetraploid wheat. | The exact wheat annotation database/options remain unresolved. Mapping, imputation and structural-variant genotyping are separate. |
| [P207](https://doi.org/10.1016/j.ajhg.2024.08.021) | Exome annotation with Ensembl85; most deleterious transcript consequence supported gene-level burden classes. | Engine/build are unresolved here. External missense scores, transcript severity policy, AAF filtering and burden tests are separate. |

The modern profile executes the [official 5.2 distribution](https://snpeff-public.s3.amazonaws.com/versions/snpEff_v5_2_core.zip).
The legacy profile builds the [official SVN r354 sources](https://svn.code.sf.net/p/snpeff/code/!svn/bc/354/SnpEffect/trunk/),
which identify themselves as 3.1i, build 2012-12-12. This is a **selected source
build**, not the authors' original binary; their source revision and binary hash
are unknown. The archive named `snpEff_v3_1_core.zip` contains **3.1m**. Its engine
is excluded; only its external dependency classes support rebuilding r354.
Every SnpEff and bundled Flanagan class comes from the unmodified r354 sources.

The [historical POM](https://svn.code.sf.net/p/snpeff/code/!svn/bc/354/SnpEffect/trunk/pom.xml)
declares LGPL3. Bundled Flanagan sources also carry noncommercial and
redistribution restrictions. **Use legacy privately for academic noncommercial
research; do not redistribute its runtime or containing evidence bundles without
the required permissions.** Acknowledge Michael Thomas Flanagan at
`www.ee.ucl.ac.uk/~mflanaga`, and retain the original notices. The modern
distribution's MIT license does not change those historical terms.

## Prepare a runtime

Run from the repository root using Python 3.11 or later. Each output path must
be new. Downloading happens here, with explicit permission; annotation never
installs software. Omit `--allow-download` to forbid network access. You can
instead supply an already downloaded exact archive with `--archive PATH`.

```powershell
python tools/literature/snpeff_original/setup.py --edition 5.2 --allow-download --out build/snpeff-runtime-52
```

Legacy requires the pinned `javac 21.0.4` compiler and acknowledgment of its
private research scope. All compilation consoles and source hashes remain in
the output. Missing/forbidden prerequisites return SKIP3. A binary mismatch is
FAIL2; retain it and investigate the compiler/platform without changing pins.

```powershell
python tools/literature/snpeff_original/setup.py --edition 3.1i-r354-source --allow-download --research-use --javac "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe" --out build/snpeff-runtime-31i
```

`--source-directory PATH` permits offline use of an already exported r354 tree;
every selected file must match `source-profile.json`. A prepared runtime is not
an analysis result or a paper environment. The helper downloads no reference
genome or paper dataset.

## Run the independent tutorial

Build the application's current source with `./gradlew.bat executableJar` on
Windows or `./gradlew executableJar` on Linux. The following **synthetic**
tutorial builds a small reference with CDS and protein checks enabled. It is
not an author dataset. The helper uses `java` on PATH, or `--java PATH`.

```powershell
python examples/snpeff-workflow/prepare.py --edition 5.2 --runtime build/snpeff-runtime-52 --out build/snpeff-tutorial
java -jar build/cli/omicsmatic-0.4.0.jar snpeff-tools run --request build/snpeff-tutorial/request.properties --out build/snpeff-run
java -jar build/cli/omicsmatic-0.4.0.jar snpeff-tools verify --bundle build/snpeff-run --out build/snpeff-verification
java -jar build/cli/omicsmatic-0.4.0.jar snpeff-tools replay --bundle build/snpeff-run --out build/snpeff-replay
```

For the legacy tutorial, change the preparation edition/runtime to
`3.1i-r354-source` / `build/snpeff-runtime-31i`, and choose new paths. The generated
request selects the distinct TXT input/output profile automatically.

## Annotate a real dataset

Supply the exact permitted database and its original SnpEff config. `data`
names the **complete data parent**, containing `GENOME/snpEffectPredictor.bin`
and all required sequence/reference files. Preserve the correct assembly and
transcript release; neither the tutorial nor a similarly named database is a
replacement for a paper's database.

A Java properties request must declare exactly these fields. Relative paths
resolve against the request file. Use forward slashes in Windows paths and
escape a literal backslash as `\\`.

```properties
edition=5.2
jar=runtime/snpeff.jar
config=reference/snpEff.config
data=reference/data
genome=EXACT_DATABASE_IDENTIFIER
variants=input.vcf
format=vcf
up_down_stream=100
heap_mb=4096
wall_ms=3600000
max_bytes=107374182400
max_files=100000
```

Those paths and database name are placeholders; select the verified reference
for your own dataset. Set `up_down_stream` deliberately; 100 is the P002 reported
setting and the tutorial choice, not an inferred default for other papers.
Modern format is `vcf`; legacy edition `3.1i-r354-source` requires `format=txt`.
All other supported effect settings remain original upstream defaults. The raw
config is captured; the effective config changes only the operational data
directory property so analysis reads the captured reference.

## Outputs, checks and limits

`original.stdout` and `original.stderr` retain the original engine output.
`annotations.tsv` keeps **every** ANN allele/transcript and literal HGVS/SO
fields, or the original 24 legacy TXT columns. `diagnostics.tsv` retains
annotation diagnostics. Original ERROR tokens prevent completion. Original 5.2
reserializes QUAL through a Java Double (`60` becomes `60.0`); the audit checks
that exact source behavior and does not rewrite either raw file. Sample values
and all other VCF columns/INFO values must remain unchanged.

`WARNINGS.md` begins with observed diagnostic counts and required review actions.
`SOURCE-DIAGNOSTICS.tsv` counts each complete original diagnostic string per
annotation row; combined strings remain combined. These are not counts of
unique variants. Failed/skipped analyses put their failure and recovery action
at the top of the warning report. An upstream exit code of zero is insufficient:
the controls observed original engines exiting zero after chromosome rejection,
and the wrapper correctly admitted no complete result.

There is no inferred pathogenicity, most-severe transcript selection or burden
classification. MODERATE is not a deleterious-missense label. Legacy chromosome
normalization and the original `Trancript_ID` / `Custom_interval_ID` columns are
preserved. The legacy native audit checks format and diagnostics; it does not
independently classify consequences or establish per-input consequence coverage.

`verify` checks directory membership, hashes and independently regenerated native
tables, without executing SnpEff. `replay` actually executes the captured original
runtime/reference/input and compares complete raw annotation, table and
diagnostic bytes. Require completion plus successful verification; use replay
for a real repeat. Integrity is not authenticity or biological validation.

VCF accepts literal DNA SNP/MNP/small-indel alleles, including multiple ALT
alleles. Symbolic SVs, breakends and gVCF records require another qualified
profile. Inputs are uncompressed text; preserve the raw compressed original and
record an explicit decompression step if preparing a text copy. Audit lines are
bounded to 16,777,216 UTF-16 code units. Resource limits
allow config/manifest files up to 64 MiB, including the official 19 MB config, and
cover child heap, child wall time and captured/final workspace inventories;
they are not an OS disk quota or total-process memory cap. Parent audit memory
and child JVM/native overhead are separate. Bundles are directories; linked and
special files are refused. Keep restricted legacy bundles private.

The original child JVM uses explicit UTF-8, LF newlines, UTC and English locale
so the same captured annotation can be repeated across operating systems without
rewriting its raw output. These operational settings are recorded in the command.
Java option/classpath injection variables are removed from the child environment.

## Verified development checkpoint

The current implementation passed 35 Java tests, three Python policy controls,
both original tutorial builds, 14 public execution/verification/replay controls,
and 16 missing-prerequisite, timeout, mutation, incorrect-count, wrong-edition
and actual original-engine error controls. Both profiles completed genuine
Windows-to-WSL replay and a second generation back on Windows.

The complete original `hg38kg` reference was also exercised with 969 independent
synthetic variants from UCSC hg38 sequence on chromosomes 1, 17 and M. The run
produced 11,905 annotation rows; genuine Windows and WSL replays matched their
raw annotation and native tables. The selected archive is
[the upstream hg38kg database in compatible 5.0 serialization](https://snpeff-public.s3.amazonaws.com/databases/v5_0/snpEff_v5_0_hg38kg.zip),
used by the 5.2 engine without changing the database identifier. Its SHA-256 is
`14a2b9f68e4e20a56d9628046b285ae979aacdf458af1a97123103cec8d3910a`.
The paper's engine version and exact database snapshot remain unknown.

**The human-reference control retained 1,871 diagnostic rows and zero original
ERROR rows:** 1,754 `WARNING_TRANSCRIPT_INCOMPLETE`, 98
`WARNING_TRANSCRIPT_MULTIPLE_STOP_CODONS`, 10 combined incomplete-transcript /
realignment messages, and nine `INFO_REALIGN_3_PRIME` messages. Review these
original diagnostics and the reference transcript models before downstream
functional interpretation. No warning was suppressed or converted to a
pathogenicity/burden class.

Installation controls retained a SourceForge HTTP403 skip. A verified cached
official dependency archive and pinned r354 sources allowed the private legacy
build to proceed. If no permitted matching cache/download is available, leave
the dependent legacy analysis skipped. The old config's Azure database endpoint
failed DNS; the matching identifier was obtained from the current official S3
repository, with its own recorded checksum. Python's UCSC HTTPS request failed
certificate-chain verification; the identical official URLs were retrieved
through normally validating system HTTPS. Certificate verification stayed enabled.

See [validation facts](snpeff-workflow-development/VALIDATION.json) and
[paper-role evidence](snpeff-workflow-development/PAPER-ROLES.json).
Historical attempts, matching readers and all restricted runtimes remain in
private development evidence. No whole-tool, whole-paper or catalog admission
is claimed. Catalog counts and all predecessor receipts remain unchanged;
the next step is a separately verified source transition and catalog admission.
