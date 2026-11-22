# Original RefSeq exon raw counts and RPKM

`homer-tools rna` is a development operation for original HOMER 5.1
`analyzeRepeats.pl` RefSeq exon quantification. It uses a separate RNA runtime
admission and pinned v7.0 hg38/mm10 RNA resources. Native Java checks complete
SAM/tag populations, source exon expansion, source boundary ownership and every
final raw/RPKM row. This entry does not perform differential expression.

[The validation record](homer-rna-validation.json) identifies development candidate
`fe05465a`: 1,014 focused tests in 79 suites, strict Java 17, Javadoc and packaging,
plus 1,014 exact-JAR tests on each Java 21/25, pass without skips. Two packaged
recipes and two fresh complete original comparisons agree in all tag/QC families
and every raw/RPKM header, metadata, length, copy and value column apart from
declared work/command paths. The complete two-genome population has 1,553,877 exon
parts, 3,107,754 part/sample counts and 489,584 final sample values. Twelve checks
before launch, two failures after launch, two Windows skips, six SAM-option
protocols, nine Python policy tests, four setup/reuse controls and three verifier
diagnostic controls pass. Retained failed attempts and their corrections remain
linked in the report. This is scoped isolated-worktree validation; no full ROOT
gate, release, deployment or biological accuracy certificate is claimed.

[The role audit](homer-role-coverage.json) maps eleven HOMER software uses and one
motif-resource reuse entry to these development capabilities. P126 does not report
a HOMER executable run. Unspecified editions/settings and P215 E-value wording
remain explicit; the entire HOMER package and other paper tools are separate from
these twelve catalogued roles. The broader goal remains active with 268 missing
integrations and paper-wide semantic review open.

## Paper role and choices

[P040, Nature](https://doi.org/10.1038/s41586-026-10954-1)
([primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13645638/)) uses
HOMER 5.1 `analyzeRepeats.pl` to obtain raw exon counts and RPKM for RefSeq genes
in bulk RNA-seq before DESeq2 1.48.2. The source does not state assembly,
RefSeq edition, strand or complete paired assignment/filtering commands.
The selected v7.0 resources and all explicit options are study choices, not
recovered paper defaults. Downstream expression eligibility, DESeq2 model,
scaling, fold-change and adjusted-P rules are separate operations.

This development command accepts plain UTF8 SAM that has already been aligned
and processed as intended. It supplies no alignment, deduplication, gene namespace
conversion, DESeq2/R, GO or Cell Ranger analysis. Original annotation columns
are retained as upstream diagnostics; an audited numeric matrix is not a
certification of those annotations or biological interpretation.

## Prepare software and reference data

Use Linux x86_64 Python and Java, inside WSL on Windows. Read
[the executable examples](../../../examples/homer-rna-workflow/README.md) for
copyable setup, input generation and analysis commands. Setup and analysis have
separate permissions. Analysis never installs software, changes a selected
edition or substitutes a different program.

`prepare_homer_rna_resources.py` verifies or explicitly extracts eight original
files from the pinned human, mouse, hg38 and mm10 v7.0 archives. The four archives
total approximately 3 GB. It retains RNA, chromosome-length and accession/
description resources only. Download requires `--allow-download`; extraction
requires `--allow-install`. For offline setup, supply an `--archive-dir` with
the exact archive names and content hashes in `homer-rna-lock.json`.

`install_homer_rna.py` installs original helper bodies, configures four Perl
installation prefixes and builds the original tag helper with a configured C++
installation prefix. Scientific source remains unchanged. It needs a compatible
C++11 compiler, make, headers, pthread/math and static C++/GCC libraries; Perl,
POSIX utilities and host libraries are separately bound. No OS package manager
is invoked. Where installation is forbidden, an administrator must supply the
permitted dependencies before the affected analysis can run.

Setup runs two synthetic tag populations, whole exon expansion, two complete
part-count calls and raw/RPKM matrices for each admitted genome: fourteen root
operations. It writes admission only after all checks pass. This can take many
minutes and needs at least 6 GiB available memory and 4 GiB allowed upstream RSS;
the default allowance is 8 GiB. Java heap is separate. Existing homes/resources
can be verified without reinstalling. Preserve failed reports and partial homes;
correct the named cause and use new paths.

Other HOMER core, named-annotation and ATAC admissions do not admit RNA. No
software binary or reference database is redistributed. Original GPL notices
are retained. Official HTTP archive pins identify bytes, without an independently
authenticated upstream signature.

## Inputs and explicit policies

Provide `--sam-list` with the exact header below and one to 32 unique replicate
IDs. SAM paths can be absolute or relative to the manifest. Full original bytes
and Unicode identities remain in snapshots and JSON-escaped provenance fields;
safe staged aliases prevent original shell/parser ambiguity.

```text
replicate_id	sam_path
sample-1	sample-1.sam
sample-2	sample-2.sam
```

Choose `--genome hg38|mm10` and state `--reference-provenance`. The exact admitted
chromosome-length resource is used without inferred contig aliases. Each SAM
`@SQ` length must match, and its header must include every eligible mapped
contig. A provenance label alone does not prove alignment to that assembly.

All scientific choices below are required:

| Option | Choices and meaning |
| --- | --- |
| `mapping` | `unique`, `keep-one`, `keep-all`; follows original MAPQ/secondary rules |
| `minimum-mapq` | integer 0–255; require explicit 0 when ignored by keep-one/keep-all |
| `mates` | `both`, `first`, `second`; original mate-flag selection |
| `flip-strands` | `true` or `false`; explicit original strand flip |
| `strand-specific-paired` | `true` or `false`; original second-mate strand flip |
| `relative-strand` | `both`, `same`, `opposite`, relative to each reference feature |
| `condensation` | `copy-families`, `distinct-transcripts`, `exon-parts` |
| `normalization` | `raw`, `rpkm`, `both` |
| `reference-irregularities` | `error` or `retain-original` for missing/overrun reference rows |
| `short-exon-parts` | `error` or `retain-original` for empty/one-base parts |
| `duplicate-alignments`, `supplementary-alignments` | `error` or `retain-upstream` for accepted flagged alignments |
| `unpaired-alignments`, `improper-pairs` | `error` or `retain-upstream` for accepted upstream states |
| `identical-replicates` | `error` or `retain-explicit` for identical SAM bytes in separate replicates |

Original matching-block CIGAR projection is retained. It differs from a standard
SAM reference-span projection: soft clips are ignored, insertions subtract
coordinate length, and D/N/X/H/P advance and break matching blocks. This command
does not repair SAM or infer proper pairing. Paired-flag rows have weight 0.5,
including when only one mate is selected. Whole-directory weight must reach 1.0;
the source can otherwise exit zero without valid tagInfo. No artificial
rescaling or rounding is applied.

## Counting, lengths and identities

Every original RNA line receives a retained disposition. The native parser sorts
feature breakpoints and includes E-prefixed UTR/exon parts exactly as original
`-count exons` does. It preserves empty parts with `end=start-1` and one-base
parts. Missing-resource contigs can contribute zero only when actual tags are
absent; no contig or reference length is invented.

The fixed counter uses inclusive 5-prime tag anchors, `tagAdjust0` and no tag cap.
It does not count fragment or spliced-block overlap across exons. Original sorted
boundary ownership can discard or assign tags differently from literal independent
interval containment. Exact geometry ties are audited as correlated owner
families. All source parts and whole-family counts must agree; split or duplicated
ownership cannot pass as an arbitrary tied result. `part-counts.tsv` preserves
literal counts beside the independently audited source interior and original
counts; `boundary-families.tsv` retains family membership and discarded mass.

Source effective length is the sum of **end minus start**, without inclusive
correction. Copy-family counts sum; copy lengths average. Existing terminal
`-HOMER` suffixes remain visible in raw/execution identities and default condensed
families. Every contributor is retained. Original representative coordinates and
strands are checked against contributor families, without inventing a canonical
transcript or highest-expression representative.

For a valid source length, RPKM is:

```text
raw_count × (1,000,000 / whole_tagInfo_mass) × (1,000 / source_effective_length)
```

The denominator includes the whole directory, not just assigned exonic tags.
Original headers can call the value FPKM; the actual audited arithmetic is above.
The native check retains original double-operation order and three-decimal output.
It refuses the source's below-one length fallback as valid RPKM. Exon-part RPKM
over resources with empty/one-base parts therefore fails: choose a scientifically
justified aggregation or raw normalization explicitly. Failed partial matrices
remain quarantined, including an earlier successful raw phase.

Raw half counts remain fractional. **Do not feed them to DESeq2 without reviewing
its input requirements and the intended upstream counting model.** This entry
does not silently round, discard parts, change lengths or fit a differential model.

## Outputs, capacities and failures

Read `WARNINGS.md`, `session.json` and every original stderr file. Key outputs are
immutable source SAM/list/dictionary copies; `replicates.tsv`, `alignments.tsv`,
native expected/actual full tag ledgers; `reference-lines.tsv`, `transcripts.tsv`,
`exons.tsv`, native/original expanded tables; `part-counts.tsv`,
`boundary-families.tsv`, `contributors.tsv`; and complete original/raw/RPKM tables
beside native matrices. Unexpressed rows remain in the audited population.

Optional capacities have explicit defaults: total alignment records 10 million,
input 64 GiB, decoded characters 100 billion, workspace 128 GiB, upstream RSS
8 GiB, timeout 3,600 seconds, per-SAM line 65,536 characters, chromosome tags
2 million, disk ledger keys 100,000, transcripts 200,000, exon parts 2 million,
features per transcript 10,000, exons per chromosome 1 million, output rows
2 million and contributors per family 10,000. Reference parsing has a separate
one-million-character line bound and uses the configured decoded bound. Run
`homer-tools rna --help` for flag names. Size Java heap for all requested sample
vectors and reference/contributor populations; 3 GiB supports the tiny two-sample
whole-reference technical fixtures. Limits reject complete populations rather
than silently truncating them.

Owned original children are joined on completion and failure. Time, workspace and
RSS checks are periodic monitors, not OS quotas. Java heap is separate from
upstream RSS. Output parents must be real ASCII Linux paths containing letters,
digits, underscore, dot, slash or hyphen. Outputs and runtime homes must be
separate and nonnested. Options JSON contains UTF8 strings only; direct flags
override it. Unknown, recursive or unused raw options are refused.

**Exit 3 — analysis skipped:** a dependency or capability is missing, forbidden,
unsupported or changed. The report names the cause and gives a **Required action**.
Supply the named permitted software/reference/host or correct capacities, then
rerun to new paths. No alternate edition or implementation is substituted.

**Exit 2 — analysis failed:** correct the named input, policy, identity,
population, numeric, timeout or output-audit error. Inputs and partial diagnostics
are retained under `partial/`. A successful original subprocess does not make
later failed matrices completed results. Existing user outputs are preserved.

**Exit 0 — completed with review warnings:** all requested source populations
and printed values passed the independent audits. Review scientific choices,
upstream preprocessing, source metadata and downstream requirements before
interpreting results. This is technical agreement, not a biological, full-paper
or production-data accuracy certificate. The broader implementation goal remains
active and Cell Ranger remains excluded.
