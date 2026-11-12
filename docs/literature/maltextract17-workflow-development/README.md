# Original MaltExtract1.7 target-species workflow

This development adapter runs the unchanged original MaltExtract1.7 program on
complete classified RMA6 inputs. It resolves literal species names, reproduces
the source descendant-selection rule, and retains every original summary,
damage, coverage, distribution, FASTA, BLAST and diagnostic file. Original
statistics are produced by the upstream program. Native Java independently
checks names, selected stored classes, input counts, extraction counters and
complete-profile read multisets.

**Whole MALT/HOPS qualification and biological paper reproduction remain open.**
This development milestone has zero whole-tool catalog credit while the final
qualification and catalog admission remain pending. It does not establish
pathogen authenticity or absence. The canonical/released project executable
is unchanged; use the separate development JAR below.

P002 supplement section3.3 reports MaltExtract extraction of
*Treponema pallidum* subsp. *pallidum* candidates, followed by manual
summary/damage assessment and a separate7508-genome bacterial rescreen.
The paper does not report the MaltExtract version, filters or resource version.
Version1.7 and the profiles here are explicit software choices. See the
[paper-role review](../MALT_HOPS_PAPER_ROLES.md) for the exact supplementary
source pointer. The HOPS taxonomy used in controls is not the authors' dated
database or their91-spirochete reference collection.

## Build and obtain the permitted runtime

Use a permitted JDK21 or JDK25 for the application. Compilation targets Java17
bytecode; the documented modern runtimes are the tested choices.

```text
python tools/literature/maltextract17_workflow/build.py --java-bin /path/to/jdk21/bin --out /path/to/NEW-build
python tools/literature/maltextract17_workflow/setup.py --out /native/linux/path/NEW-runtime
```

The setup command selects the exact MaltExtract1.7 program and GPL license from
HOPS0.35 bioconda build1 and retains the complete140-member OpenJDK8.0.144 JRE.
It preserves POSIX modes and links on a native Linux filesystem. No system
Java or Conda environment is changed. Offline setup accepts `--hops-archive`
and `--java-archive` with the pinned complete package files.

The [official HOPS repository](https://github.com/rhuebler/HOPS) and the retained
package license identify the GPL distribution. Its MaltExtract1.7.jar is
10,770,756bytes, SHA256
`8a5be1b5ae43f329995a4647466a7ca3aa698d180cef94dec974ffe91bb3a2ca`.
The complete selected runtime inventory has SHA256
`c697e187d24257ffed984d89ee02a2431efa03147b280f2c49c3518aef0d8d14`.
The outdated standalone MaltExtract repository is not used as the license or
default-setting authority.

**If installation or execution is unavailable or forbidden, the dependent
analysis is skipped with SKIP3 and explicit recovery instructions.** A bad or
incomplete input, exceeded capacity, process failure, severe upstream error
or inconsistent output returns FAIL2 and admits no result. The complete failed
workspace is retained. Retry with corrected prerequisites and a NEW directory.

## Supply complete inputs and choose the method

Put complete `ncbi.map` and `ncbi.tre` in one resource directory. The map is
literal TAB-separated integer ID/name data. The tree is complete numeric Newick
with supported finite branch lengths. Names are case-sensitive; no aliases,
underscore conversion, name repair or automatic taxonomy download occurs.
Original last-assignment map behavior and target-file `String.trim()` lookup
are recorded explicitly. Unknown requested names and output-name collisions
refuse the run instead of silently losing a species.

Save a UTF8 names file with no header, for example:

```text
Treponema pallidum subsp. pallidum
```

This subspecies is taxon161 in the control resource. Taxon243276 is the Nichols
strain there. Select names from your actual complete resource; do not treat an
example ID as a universal database/version mapping.

Save a UTF8 TSV with a literal header and one row per complete RMA6 input:

```text
sample_id	rma6
sampleA	/path/to/sampleA.rma6
sampleB	/path/to/sampleB.rma6
```

Use actual TAB characters between columns. Relative paths resolve against the
TSV directory. Sample labels must start with an ASCII letter and contain only
letters, digits, `_` and `-`, at most64characters, with no case-insensitive
duplicates. They are deliberate output labels; biological read names and
sequences are retained literally.

Choose one required profile:

| Profile | Original filter | Removal and sampling |
| --- | --- | --- |
| `source-default` | `default` | Original destacking, duplicate removal and downsampling enabled |
| `complete-default` | `default` | All three explicitly disabled |
| `complete-def-anc` | `def_anc` | All three explicitly disabled; default and ancient outputs |

The adapter explicitly requests top fraction0.01, reads, matches and verbose
diagnostics. These fixed profiles use source minimum identity0, no length
maximum, minimum complexity0 and double-stranded rules. Unreported historical
paper settings are not inferred. See `--help` for threads, heap, wall-time and
complete-input capacities; exceeding a capacity refuses the run.

```text
java -Xmx3g -jar /path/to/NEW-build/maltextract17-workflow-development.jar --runtime /native/linux/path/NEW-runtime --resources /path/to/resources --targets /path/to/targets.txt --rma-list /path/to/samples.tsv --profile complete-def-anc --input-provenance supplied --out /native/linux/path/NEW-run
```

Use `--input-provenance native-accession-alternative` for RMA output derived from
the explicitly different native MALT accession-mapping alternative. `supplied`
declares an external input; it does not authenticate the producer. Runtime
fitting requires Linux x86_64 and a native filesystem. The original Java8 child
has a bounded owned process group, retained limits/receipts and local cleanup.
Sampled aggregate RSS is not a hard aggregate memory bound.

## Inspect the result and warnings

- `original/` retains all original files under their original Linux names.
- `process/` retains complete stdout, stderr, launch and final process receipts.
- `bundle/inputs/` retains complete unchanged RMA files, resources and lists.
- `bundle/outputs/` contains every original file's unchanged bytes under flat
  portable member names. `bundle/original-files.json` maps each literal original
  relative path to its member and checksum. Colons and other original Linux
  filename characters are retained in the mapping, rather than repaired.
- `bundle/audit.json` records all requested names, source map assignments,
  selected classes, complete stored counts, original counters and warnings.
- `bundle/WARNINGS.md` prominently reports sampling, nonfinite values and
  required actions. A failed run also has `FAILURE.json` and
  `WARNINGS_FAILURE.md` and cannot be admitted as a completed bundle.

**The stock profile can process only the first10000 stored reads of a class
while reporting `downSampling? = Off`.** In the actual10001-read control,
stock processing examined10000 and destacked to1 retained read. The explicitly
complete default profile examined and retained all10001. If full-population
extraction is intended, deliberately select a complete profile and use a NEW
output directory. The adapter retains stock output but skips its use as a
full-population analysis. It never switches methods automatically.

**Original `NaN`/`Inf` cells and ambiguous table widths are retained.** No zero
replacement, imputation or row padding occurs. Dependent interpretation of a
ragged table is skipped with a warning. A required counter row that cannot be
interpreted safely refuses admission. The damage-table `considered_Matches`
counts retained alignments of source length>=20; it is distinct from the filter
table's alignment count. Original SEVERE logging can accompany exit0; that is
treated as a failed analysis, not a successful result.

## Saved verification and complete evidence replay

Move or copy the complete `bundle/` directory as a unit. Read-only verification
on Windows or Linux needs the exact reader JAR, not the original Java8 JRE:

```text
python tools/literature/maltextract17/setup.py --out /path/to/NEW-reader
java -Xmx3g -jar /path/to/NEW-build/maltextract17-workflow-development.jar --verify /path/to/bundle --runtime /path/to/NEW-reader
python tools/literature/maltextract17_workflow/verify.py --java-bin /path/to/jdk21/bin --runtime /path/to/NEW-reader --out /path/to/NEW-replay
```

Reserve at least8GiB free space for the complete evidence replay. It rebuilds
the adapter, checks complete original name/descendant oracles, verifies seven
complete workflow captures, compares all scientific output bytes for the four
standard captures with independent originals, and refuses eight rehashed
contradictions. Failed fitting attempts and original diagnostics remain retained.
Machine-readable validation/replay receipts establish which runs have completed.

**Saved verification checks consistency, not authenticity.** It recomputes
names/descendants, RMA counts, extraction counters and complete-profile FASTA
multisets. It does not rerun original Java8 execution or independently recalculate
arbitrary damage/coverage cells. Fixed software-control statistics are compared
byte-for-byte with independent original captures. Coordinated rehashed edits
cannot be authenticated by checksums. Manual candidate assessment and the
paper's separate bacterial rescreen remain separate operations.
