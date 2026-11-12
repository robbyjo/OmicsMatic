# Native MaltExtract1.7 complete read projection

This component extracts literal read headers and sequences from complete classified
RMA6 inputs. Original MaltExtract1.7 decodes RMA6; native Java implements the
qualified default/ancient read projection. The complete input population, selected
stored taxon IDs, taxonomy, parameters, output files and diagnostics are retained.

**The method must be selected explicitly.** It disables destacking, duplicate
removal and downsampling. These choices change results relative to stock
MaltExtract1.7. Whole MaltExtract/HOPS, name/descendant selection, original
summary/damage statistics and biological paper reproduction remain incomplete.
This development component earns zero whole-tool catalog credit.

The [paper-role review](../MALT_HOPS_PAPER_ROLES.md) identifies MaltExtract use in
P002 supplement section3.3: extraction of target candidates from custom MALT0.5.2
screening output, followed by manual summary/damage assessment and a separate
bacterial rescreen. The authors' MaltExtract version, filters and resources are
unreported. Version1.7 and the complete-projection choices here are explicit
software profiles, not inferred historical settings.

## Build and obtain the reader

Use a permitted JDK21 or JDK25. The application compiles to Java17 bytecode and
has been qualified on those modern application runtimes. Native extraction does
not require installing Java8 or running the original MaltExtract executable.

```text
python tools/literature/maltextract17/build.py --java-bin /path/to/jdk21/bin --out /path/to/NEW-build
python tools/literature/maltextract17/setup.py --out /path/to/NEW-reader
```

The setup command downloads the exact public HOPS0.35 bioconda build1 archive
and selects its unchanged MaltExtract1.7.jar, GPL license and package metadata.
An offline alternative is `--archive /path/to/hops-0.35-hdfd78af_1.tar.bz2`.
The complete archive is73,005,231bytes, SHA256
`79105a2c3a447d31dc1b00ff3c74505bfe56854cf302346b6347a0eb1fee2e2d`.
The selected10,770,756byte reader has SHA256
`8a5be1b5ae43f329995a4647466a7ca3aa698d180cef94dec974ffe91bb3a2ca`.
No system Java, Conda environment or existing project executable is changed.

The [official HOPS repository](https://github.com/rhuebler/HOPS) and the
package's retained GPL license identify this distribution. The old standalone
MaltExtract README calls itself outdated; its defaults are not used as the
authority for this implementation. The pinned binary, direct Java8 source API
outputs and actual original executions establish the tested behaviour.

## Select the complete stored groups deliberately

The required one-column TSV has the literal header `taxon_id`, followed by
positive decimal IDs, without blank rows, duplicates, aliases or version repair.
Save this TSV as UTF-8 without a byte-order mark:

```text
taxon_id
9606
37349
```

These example IDs are the human and mammoth groups in the public software
controls. They are not a pathogen candidate list. Supply a complete numeric
`ncbi.tre` with the intended IDs. Native parsing retains explicit finite branch
lengths and the complete topology; it adds no synthetic taxa or inferred weights.

**This list names stored classification groups.** A parent ID alone does not
select descendant groups. This command does not resolve species names, discover
descendants, aggregate a taxonomic subtree or guess a candidate population.
The complete target-species selection stage must be qualified separately.

## Extract and verify

```text
java -Xmx3g -jar /path/to/NEW-build/maltextract17-reads-development.jar --runtime /path/to/NEW-reader --input /path/to/input.rma6 --taxonomy /path/to/ncbi.tre --taxa /path/to/taxa.tsv --mode both --method source17-complete-projection --input-provenance supplied --out /path/to/NEW-extraction
```

Use `--input-provenance native-accession-alternative` when the RMA came from the
explicit native accession-mapping alternative. Retain that lineage in downstream
analyses. `supplied` is a declaration about an externally supplied RMA, not proof
of its producer or classification accuracy.

`--mode default`, `ancient` or `both` is required. The default read-projection
parameters are top fraction0.01, minimum identity0percent, no length maximum,
minimum complexity0, all retained top matches and double-stranded lesion rules.
`--help` lists explicit parameter overrides and complete-population capacities.
Identity uses the clearly named `--minimum-identity-percent` in percent units;
it does not reproduce the original CLI's hidden conversion of values<=1.

Inspect these files before using a result:

- `inputs/`: complete unchanged RMA6, taxonomy and deliberate taxon list.
- `derived/default/ID.fasta` and/or `derived/ancient/ID.fasta`: native projected
  reads in deterministic header/sequence order; an empty projection is an empty
  file, not evidence of biological absence.
- `audit.json`: complete stored/available match counts, exact reader identity,
  taxonomy counts, per-group projection counts, parameters and input provenance.
- `WARNINGS.md`: method differences, reader diagnostics and required actions.
- `COMPONENT_COMPLETED.json`: the complete captured file inventory.

Read-only saved verification recomputes every projection from the captured RMA
and taxonomy with the exact reader:

```text
java -Xmx3g -jar /path/to/NEW-build/maltextract17-reads-development.jar --verify /path/to/NEW-extraction --runtime /path/to/NEW-reader
```

Verification checks consistency, not producer authentication. It refuses failed
or provisional bundles, extra/missing/changed members, false whole-tool credit,
inadequate declared capacities and rehashed contradictory scientific counts.

## Qualification and portable replay

The evidence retains all original output from three original1.7 profiles:
stock default, default with removal/sampling disabled, and `def_anc` with those
operations disabled. Four complete input RMAs contain112 stored reads and122
stored alignments. These are software controls derived from three complete
public mitochondrial genomes, not the paper's spirochete references or patients.
The pinned HOPS taxonomy is a software-control resource; it is not the authors'
dated2017 database.

The comparisons cover all original/native RMA fields and float bits, every
selected class's read/sequence multiset and filter counts,4,018 complexity/damage
cases,2,640 float-filter/first-score/ancient-cap cases, and every one of2,258,978
taxonomy nodes with its parent, depth, child count and branch-length bits.
Thirteen unit/regression tests passed with no skips or failures. Complete native
CLI runs and saved verification retain the four positive populations and four
deliberately rehashed metadata contradictions. Source captures and failed
development attempts are retained with their exact source views and no admitted
result claims.

The portable verifier rebuilds from the matching source, restores the complete
evidence, checks full source oracles, recomputes every saved native projection
and refuses the contradictory bundles:

```text
python tools/literature/maltextract17/verify.py --java-bin /path/to/jdk21/bin --runtime /path/to/NEW-reader --out /path/to/NEW-replay
```

Reserve at least2GiB of free output space and allow Java heaps up to3GiB. The
archive deduplicates equal complete files; it does not sample populations. Original
Java8 fitting/extraction is not repeated by saved replay. See the machine-readable
validation and replay receipts for the platforms actually completed.

Read [WARNINGS.md](WARNINGS.md) for source limitations, failure codes and recovery.
