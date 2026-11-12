# Owned MALT0.5.2 seed building and explicit native mapping

This Java component accepts complete DNA FASTA inputs and an explicit accession
mapping. It runs the pinned original Linux MALT0.5.2 builder under the owned
process supervisor, retains its unclassified index, and creates a separately
labelled mapped index with the existing native Java mapping implementation.
It checks every reference sequence, full header, ordinal, offset, taxonomy ID and
original FASTA-consumption counter. No reference or mapping namespace is guessed.

**This is an explicit method alternative.** The pinned original0.5.2 accession
mapping did not classify the controlled references when organism-name parsing
was disabled. This component requires `--method native-accession-alternative`;
its output must not be presented as the authors' unchanged accession-map path.
Whole MALT/HOPS/MaltExtract and biological paper reproduction remain incomplete.
The [paper-role review](../MALT_HOPS_PAPER_ROLES.md) distinguishes the initial
screen, custom0.5.2 screen, index construction and downstream extraction roles.

## Build and run

The minimal component needs only a permitted JDK21 or JDK25. It compiles to
Java17 bytecode; the qualified application runtimes are21 and25. It does not
overwrite the project's existing executable or bundle upstream programs.

```text
python tools/literature/malt052/build_index.py --java-bin /path/to/jdk21/bin --out /path/to/NEW-component
```

Original fitting additionally requires Linux x86_64, `setsid`, `prlimit`, and the
complete pinned MALT0.5.2 provider with its bundled Java14. Obtain it only where
downloads/installation are permitted:

```text
python tools/literature/malt052/setup.py --out /var/tmp/NEW-malt052-provider
java -Xmx3g -jar /path/to/NEW-component/malt052-index-development.jar --runtime /var/tmp/NEW-malt052-provider --fasta /path/to/references.fa --mapping /path/to/accessions.tsv --format two-column --namespace literal --method native-accession-alternative --out /var/tmp/NEW-index-run
```

Run the fitting command inside Linux/WSL. `--reader-only` supplies fewer runtime
files and cannot execute original fitting. The controlling application uses the
selected modern Java; only the unchanged upstream child uses bundled Java14.
No system Java installation is changed.

For several FASTA files, replace `--fasta` with `--fasta-list references.tsv`:

```text
fasta
genome-A.fa
genome-B.fa.gz
```

The literal one-column header is `fasta`. Each subsequent row is one complete
path, resolved relative to the list's directory. All files and their order are
retained. A repeated file or duplicate first-word accession is refused. No
directory discovery or silent concatenation chooses a reference population.

`two-column` requires literal first-word keys and positive decimal taxonomy IDs.
For the literal four-column NCBI `accession2taxid` format choose
`--format ncbi-accession2taxid` and explicitly select `--namespace accession` or
`--namespace accession.version`. No version suffix is stripped. Every reference
must have a consistent mapping to a taxon in this exact index's taxonomy.

On success, inspect:

- `native/index/`: the complete unchanged original unclassified seed index.
- `derived/index/`: the separately labelled native-mapped index for subsequent
  explicitly declared native-alternative alignment.
- `inputs/`: complete byte-pinned FASTA files, mapping and supplied reference list.
- `native/process/`: actual command, process limits, exit/cleanup receipts and
  full upstream stdout/stderr.
- `audit.json`, `WARNINGS.md`, `COMPONENT_COMPLETED.json`: complete-population
  checks and the narrowly stated scope.

Defaults are two workers,4GiB upstream heap, one parallel-GC thread and600seconds.
The paper's130G request can be represented as `--heap-mib 133120`; it was not
executed in these controls. `--threads 16` is representable too. Raise explicit
byte, file, reference, base, line, mapping-row or wall capacities when needed;
crossing a capacity refuses the complete analysis rather than taking a prefix.
Sampled RSS monitoring is not a hard aggregate RSS limit.

## Saved verification and evidence

The seed bundle can be verified on Windows or Linux without any original runtime:

```text
java -Xmx3g -jar /path/to/NEW-component/malt052-index-development.jar --verify /path/to/COMPLETE-index-run
```

Verification checks every captured member, recomputes the complete FASTA/index
and mapping relationships, and binds saved resource declarations and FASTA order
to the original owned-process command and limits. It refuses rehashed false
resource declarations, insufficient input capacity, false tool credit and a
failure marker in a purported completed bundle. This is consistency verification,
not producer authentication.

The qualification uses three complete public mitochondrial reference genomes
(Boa constrictor AB177354.1, Mammuthus primigenius NC_007596.2 and Homo sapiens
NC_012920.1), totaling52,244 bases. Both a single FASTA and an ordered three-file
list are built by original MALT. Two upstream seed-table files differ between
the productions; those differences are retained. The reference databases and
taxonomy mappings agree. All four complete original alignment populations are
checked on each production against the original complete SAM oracles. These are
software controls, not the paper's91-genome spirochete database or biological
replication. The original-API FASTA controls include12 matching complete native
populations, four declared refusals and all127 source DNA5 lookup values.

The complete combined evidence replay rebuilds both Java components, verifies
both seed captures, checks eight complete saved alignment/RMA populations against
the direct original Java14 API, replays all16 FASTA intake controls and refuses
all four deliberately rehashed metadata contradictions:

```text
python tools/literature/malt052/verify_index.py --java-bin /path/to/jdk21/bin --runtime /path/to/malt052-reader --download-htsjdk --out /path/to/NEW-combined-replay
```

For an offline replay replace `--download-htsjdk` with
`--htsjdk-jar /path/to/htsjdk-5.0.0.jar`. The combined verifier needs the exact
reader provider containing `jars/`; it never refits original seed building or
alignment. Reserve at least5GiB of output space and allow Java heaps up to3GiB.
All logical data are restored and checked, including the deliberately corrupted
copies; object deduplication reduces the archive size, not the populations tested.

See the current validation, source views, replay and warning receipts in this
directory. The [earlier alignment archive](../malt052-development/README.md)
separately retains12,275 original-source assignment cases and the full taxonomy.

## Warnings and recovery

Read [WARNINGS.md](WARNINGS.md) before using an index. Missing/forbidden runtime
or installation returns `SKIP3` and skips dependent analysis. Invalid or incomplete
data returns `FAIL2` and admits no result. Both provide named required actions;
owned failures retain diagnostics. Every retry requires a NEW output directory.

Blank-prefixed FASTA and empty records can make this original loader consume zero
references while exiting successfully. The native intake refuses those cases.
Unicode headers, ordinary wrapping, CRLF and gzip are checked against the actual
original loader; non-ASCII sequence bytes fail rather than being replaced.
The native mapping stage requires ungapped genomic reference bytes. Source DNA5
retains `-`, but that mapping stage refuses a gapped index. Do not remove gaps,
rewrite accessions, trim a prefix or omit a record automatically. Retain the raw
inputs; provide a deliberately corrected complete input as a new version with
provenance, or skip the dependent analysis if an appropriate input cannot be supplied.
