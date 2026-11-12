# Original HOPS extraction and candidate assessment

**Development adapter. Catalog admission remains pending; whole HOPS/MALT and
biological paper reproduction are not qualified.** Read the current
[validation record](hops-mepo-development/VALIDATION.json) for the evidence and
saved-replay status.
This application runs the unchanged HOPS launcher in local `me_po` mode on
complete classified RMA6 inputs. Original MaltExtract1.7 creates the extraction
tables and original AMPS R creates the candidate plots, heatmap, TSV and RData.
Java checks the complete captured populations and scientific fields.

Two explicit profiles are available:

| Profile | Original sampling, destacking and duplicate removal |
| --- | --- |
| `source-def-anc` | Enabled, preserving the original stock method |
| `complete-def-anc` | Deliberately disabled; this changes the method |

**Stock MaltExtract1.7 can examine only the first 10,000 reads assigned to a
class, even when its `downSampling?` output says `Off`.** Complete raw inputs
are still captured and checked. Retain stock outputs and their warnings.
If full-population extraction is intended, make the scientific decision to use
`complete-def-anc` and start a NEW run. Do not change profiles merely to obtain a
passing plot. Controls show that the profiles can produce different scientific
tables and different numbers of candidate PDFs.

P002 does not report the HOPS version/settings needed to reconstruct its exact
screen. These are declared software choices. The tested distribution is
HOPS0.35, whose original launcher reports0.33; program hashes identify the exact
software. This adapter does not reconstruct the authors' historical database,
build a MALT index, align reads or replace manual authentication.

## Prepare the application and exact providers

Use native Linux x86_64 with application JDK21. JDK21/JDK25 builds and native unit
tests are supported; original processing is qualified under Linux JDK21. In WSL,
put output on its native filesystem, for example `/var/tmp`. The plotting output
prefix must contain only ASCII letters/digits, slash, underscore, dot, colon or
hyphen. Spaces, shell punctuation and unsafe target names are refused before
original execution, without renaming them.

Build from the repository root:

```sh
python3 tools/literature/hops_mepo/build.py \
  --java-bin /path/to/jdk21/bin --out /var/tmp/hops-mepo-build-NEW
```

Prepare the full MaltExtract1.7/Java8 provider and complete matching `ncbi.map`
and `ncbi.tre` using the
[MaltExtract guide](MALTEXTRACT_INTEGRATION.md). Prepare the separate public
R4.0.5/HOPS0.35 provider using the
[HOPS postprocessing guide](HOPS_POSTPROCESSING.md). The build includes no
upstream executable, Java8 installation or R installation. Download/install
only where permitted.

Supply a UTF-8 file containing one literal target name per line and a complete
sample TSV with real tab separators:

```text
sample_id	rma6
sampleA	/path/to/sampleA.rma6
sampleB	/path/to/sampleB.rma6
```

Relative RMA paths resolve against the list's directory. Labels must be unique
ASCII identifiers starting with a letter and containing only letters, digits,
underscore or hyphen, with at most64 characters. Case-colliding labels and
ambiguous or missing taxon names are refused.

## Run and inspect the outputs

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

Use `native-accession-alternative` instead of `supplied` when the classified RMA
was produced with that explicitly declared MALT index alternative. This records
provenance and does not establish an author database reconstruction. Use
`--help` for complete capacity/resource options. Exceeding an adapter capacity
refuses the whole analysis; the selected stock method's own sampling remains
unchanged.

Inspect `bundle/WARNINGS.md`, `driver-process/process.stderr`,
`export-process/process.stderr`, and all original results under
`original/maltExtract`. The latter includes FASTA/BLAST output, tables,
`heatmap_overview_Wevid.tsv`, PDFs and `analysis.RData`. This original driver
configuration does not request JSON. Preserve the whole directory. `bundle`
contains complete inputs and a flat, indexed copy of every original output.
The completion marker describes software consistency checks, not authenticity.
Classes are screening criteria; missing or insufficient evidence remains
unassessable and does not establish absence.

## Verify a complete saved bundle

```sh
java -Xmx3g -jar /var/tmp/hops-mepo-build-NEW/hops-mepo-development.jar \
  --verify /path/to/saved-run/bundle \
  --reader-runtime /path/to/exact-maltextract17-reader \
  --post-runtime /var/tmp/hops-r405 \
  --out /var/tmp/hops-mepo-verify-NEW
```

This needs the exact external1.7 reader and permitted R4.0.5 provider, but not
the original Java8 installation or old source paths. It checks original method,
command, input and process declarations, reads the complete captured RMA and
resources, freshly decodes RData and checks all scientific fields. It repeats
no original fitting/plotting and does not authenticate a producer.

## Warnings, errors and required actions

**SKIP3: a required exact runtime, permitted installation or Linux execution is
unavailable. Skip the dependent analysis.** Retain the failed attempt, its
`FAILURE.json` and `WARNINGS_FAILURE.md`. Install the exact provider in a NEW
permitted location, or leave that analysis skipped.

**FAIL2: no result is admitted.** Retain all inputs and diagnostics. Follow the
named input, integrity, capacity, process or scientific error before using a
NEW output directory. Do not change saved files to make verification pass.

The original HOPS driver can return zero even when a child fails. This adapter
requires both child exits to be zero and checks all complete outputs. An empty
candidate plot can fail in original R; that failure is retained and is not a
validated absence result. Original extraction fatal logs are also refused.
Review all upstream warnings and candidate profiles manually.
