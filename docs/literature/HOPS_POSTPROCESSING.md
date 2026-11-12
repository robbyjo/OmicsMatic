# Original HOPS postprocessing with native Java checks

This stage runs the unchanged AMPS R script from the GPL HOPS0.35 distribution
on a complete, verified original MaltExtract1.7 bundle. It retains the original
heatmap, candidate-profile PDFs, TSV, optional JSON and `analysis.RData`. Java
independently recomputes the source statistics/classes and checks every saved
scientific field. Saved verification decodes the original RData again.

**Whole HOPS/MALT implementation and biological paper reproduction remain
incomplete.** This is the postprocessing stage, with zero additional tool-count
credit. At this checkpoint the250-paper catalog has100 new scoped integrations and246
missing tools. P002 reports HOPS candidate assessment but does not identify its
HOPS version/settings. The declared software version here is a tested choice;
it does not recover the authors' choices or automate their manual assessment.

## Prepare the application and providers

Use native Linux x86_64 and application JDK21 for qualified original processing
and fresh RData verification. JDK21/25 builders and native unit-test JVMs are
tested; Linux JDK25 execution is untested at this checkpoint. Both builds target
Java17 bytecode. Windows can build the module and run native unit tests, while
original execution/RData decoding require Linux. In WSL, put output directories on its native filesystem,
such as `/var/tmp`, rather than `/mnt/c` or `/mnt/d`.

**Original plotting requires a safe output prefix.** Its unchanged R script
passes the prefix to an unquoted shell `mkdir`. Use only ASCII letters/digits,
slash, underscore, dot, colon or hyphen, for example `/var/tmp/hops-post-NEW`.
Spaces, shell punctuation and other characters cause FAIL2 before original
execution. Retain that failed attempt and choose a NEW prefix; no path is
renamed. Saved verification only loads RData and does not run that shell command.

Build this separate development application from the repository root:

```sh
python3 tools/literature/hops035_workflow/build.py \
  --java-bin /path/to/jdk21/bin --out /var/tmp/hops-build-NEW
```

The [JDK21-built candidate](hops035-postprocessing-development/hops035-postprocessing-jdk21.jar)
and [JDK25-built candidate](hops035-postprocessing-development/hops035-postprocessing-jdk25.jar)
contain the same application resources. The canonical/released OmicsMatic JAR
and root command dispatcher are unchanged. These candidates bundle no upstream
MALT/MaltExtract/HOPS executable, R installation, eager image or GATK program.

The R-only provider uses102 exact public packages, without solving for newer
versions or changing a system environment. Install/download only where allowed:

```sh
python3 tools/literature/hops035_workflow/setup.py \
  --out /var/tmp/hops-r-NEW --allow-install --allow-download
```

For an offline install, replace `--allow-download` with
`--package-cache /path/to/COMPLETE-pinned-archive-cache` and provide the exact
HOPS archive with `--hops-archive /path/to/hops-0.35-hdfd78af_1.tar.bz2`.
Keep the provider's `packages`, `r`, `program` and `RUNTIME_READY.json` together:
the adapter checks the complete recorded provider and archive identities.

Also supply the exact external1.7 reader JAR in a reader-runtime directory,
using the [MaltExtract integration guide](MALTEXTRACT_INTEGRATION.md).
The reader needs no original Java8 fitting runtime for this postprocessing
stage. If an extraction bundle does not yet exist, first follow the complete
[MaltExtract workflow](maltextract17-workflow-development/README.md), choosing
its method explicitly. For the supported original `def_anc` postprocessing,
the tested extraction choice is `complete-def-anc`, which deliberately disables
sampling, destacking and duplicate removal. Those choices change the method;
they are not inferred paper defaults.

**Missing or forbidden prerequisites mean SKIP3 and no dependent analysis.**
Retain the failed prefix and its warnings. Install the permitted exact provider
in a NEW directory, or leave the dependent stage skipped. Do not substitute a
different R version or claim success from package probes alone.

## Run and inspect the original results

For an already verified extraction at `/var/tmp/extraction-NEW/bundle`:

```sh
java -Xmx3g -jar /var/tmp/hops-build-NEW/hops035-postprocessing-development.jar \
  --maltextract-bundle /var/tmp/extraction-NEW/bundle \
  --reader-runtime /var/tmp/maltextract17-reader \
  --post-runtime /var/tmp/hops-r-NEW \
  --filter def_anc --emit-json true \
  --out /var/tmp/hops-post-NEW
```

`--emit-json false` retains the original non-JSON workflow. Both choices are
tested. The complete extraction bundle and all original table populations are
captured; capacities refuse excess input rather than sample or truncate it.
See `--help` for explicit byte/file/table/sample/taxon/thread/time limits.

Inspect these paths under the output directory:

| Path | Contents and purpose |
| --- | --- |
| `original/heatmap_overview_Wevid.pdf` | Original reduced overview plot. |
| `original/pdf_candidate_profiles/` | Every expected original candidate-profile PDF. |
| `original/analysis.RData` | Unchanged original workspace for further inspection. |
| `original/heatmap_overview_Wevid.tsv` | Original table, including its quoting/name formatting. |
| `original/heatmap_overview_Wevid.json` | Original sample-to-taxon array cells, when requested. |
| `bundle/audit.json` | Complete native statistics/classes and scientific comparisons. |
| `bundle/scientific-export.json` | Original workspace fields with exact double bits and distinct NA/NaN/Inf. |
| `bundle/extraction/` | Complete verified MaltExtract input bundle. |
| `bundle/original-files.json` | Literal original paths mapped to portable unchanged flat members. |
| `post-process/process.stderr` | Full original R warnings/errors. |
| `export-process/process.stderr` | Full original workspace-decoding diagnostics. |
| `bundle/WARNINGS.md` | Interpretation restrictions and recovery actions. |

Classes1..4 are the original hierarchical screening criteria. They are not
read counts, evidence of biological absence or authentication decisions. Missing
statistics remain unassessable even where the source class matrix contains1.
Manual review of the original plots/tables remains necessary.

## Verify saved results and software controls

Saved verification uses a NEW output directory, reads the captured inputs and
freshly decodes RData with the exact public R provider. It does not rerun original
fitting or plotting and does not authenticate who produced the bundle:

```sh
java -Xmx3g -jar /var/tmp/hops-build-NEW/hops035-postprocessing-development.jar \
  --verify /var/tmp/hops-post-NEW/bundle \
  --reader-runtime /var/tmp/maltextract17-reader \
  --post-runtime /var/tmp/hops-r-NEW \
  --out /var/tmp/hops-verification-NEW
```

For complete saved qualification evidence, including altered-bundle refusals:

```sh
python3 tools/literature/hops035_workflow/verify.py \
  --java-bin /path/to/jdk21/bin \
  --reader-runtime /var/tmp/maltextract17-reader \
  --post-runtime /var/tmp/hops-r-NEW \
  --out /var/tmp/hops-controls-NEW
```

The complete controls cover three successful workflows,22 sample–taxon pairs
and308 node/numeric fields, both JSON choices, two independently installed public
R providers, and nine rehashed contradiction refusals. Three independent original
RData productions agree. All31 focused/adjacent Java tests pass on JDK21 and25;
the separate native source oracle covers2901 statistic pairs,420 class boundaries
and seven refusals. See [validation](hops035-postprocessing-development/VALIDATION.json)
and [warnings](hops035-postprocessing-development/WARNINGS.md) for exact scope.

## Failures and required actions

**FAIL2 means no result admitted. Retain the entire failed directory.**

| Cause | Required action |
| --- | --- |
| Original `default` mode reports `undefined columns selected` | This is a retained failure of the unchanged script. If default-only processing is required, skip this R stage and review the original MaltExtract tables. Change to `def_anc` only as a documented, scientifically justified method choice with matching extraction outputs. |
| Empty reduced matrix reports `increasing 'x' and 'y' values expected` | Retain the failed plot and input tables. Do not call the failure a validated absence result. Review the unassessable/empty candidate set manually. |
| Unsupported target characters | The unchanged script uses an unquoted shell `mkdir`. This stage qualifies ASCII letters/digits/spaces/underscore/dot/hyphen; it refuses other literal identities without renaming them. Use a separately qualified upstream version that supports the identities, or skip the affected original postprocessing. |
| Byte, table, sample, taxon or other capacity exceeded | Increase the named explicit capacity if resources permit, then use a NEW output directory. Never truncate the population to get a result. |
| Changed input, RData, output, command, scope or process metadata | Restore the matching complete evidence or investigate its provenance. Rehashing altered evidence does not make it valid. |
| Existing output directory | Keep it unchanged and choose a NEW directory. |

The pinned public package closure retains an unused GCC9 compatibility symlink
that dangles with its newer `libstdcxx-ng` package. Its exact target was checked
against the pinned upstream GCC archive; it is not repaired. R postprocessing is
validated, while GCC9 compilation is outside this provider's qualification.
The HOPS distribution is0.35 even though its launcher reports0.33; the exact
original script hash and archive identify this stage.
