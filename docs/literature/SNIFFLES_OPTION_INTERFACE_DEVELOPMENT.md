# Sniffles option interface under validation

**Historical development note.** The pending statements below describe the
isolated candidate stage. For current availability and admission requirements,
read [SNIFFLES_WORKFLOWS.md](SNIFFLES_WORKFLOWS.md) and its separately verified
execution proof. The failed initial header comparison and cancellation diagnostic
suite retain their original failure status; later checks have separate receipts.

**Calling validation and catalog integration remain unfinished.** The existing
development artifacts are not credited as an available Sniffles integration.
The running default-mode tests and an isolated interface candidate are separate
experiments. Keep their original artifacts, requests, outputs and warnings.

## A source-edition mismatch that must remain visible

**Do not use the development interface's legacy `minimum_alignment_length` as
an equivalent read-length filter.** That interface maps it to original 1.0.7
`--min_seq_size`. The original C++ option controls event-segment handling; modern
Sniffles filters `query_alignment_length` with `--min-alignment-length`.
The original definitions and uses are retained in the frozen source archives
and are visible in the immutable upstream
[legacy option definition](https://github.com/fritzsedlazeck/Sniffles/blob/752bd21266125c16f1ea17d1b19c7a76860d4856/src/Sniffles.cpp#L53),
[legacy event handling](https://github.com/fritzsedlazeck/Sniffles/blob/752bd21266125c16f1ea17d1b19c7a76860d4856/src/sub/Detect_Breakpoints.cpp#L542)
and [modern alignment filter](https://github.com/fritzsedlazeck/Sniffles/blob/7f71d13d011b0df3d29da90a294507530181e2b9/src/sniffles/leadprov.py#L594).

Direct upstream experiments on the same constructed alignments demonstrate the
difference: a value of 1,000,000 retains two legacy calls, while the modern
alignment filter produces a valid empty callset. Do not infer a legacy read
prefilter from this option, or infer any caller threshold from a paper's
separate read-length preparation statement.

The corrected candidate exposes two distinct settings:

| JSON setting | Original edition and option |
| --- | --- |
| `legacy_minimum_segment_size` | 1.0.7 `--min_seq_size` only |
| `minimum_alignment_length` | Modern `--min-alignment-length` only |

Wrong-edition requests return SKIP3 before execution; no edition or filter is
substituted. Omission/null leaves the original default. Explicit zero is retained
as an explicit source value. The candidate does not filter or repair alignments
in Java.

Five completed direct-source option experiments retain seven complete records:
the differing high-threshold behavior, legacy numeric/genotype settings, modern
automatic support with a literal sample label, and numeric phased CRAM with
declared matching FASTA. The labels include leading dashes and spaces and remain
literal VCF sample values. These are source/format controls, not biological
benchmarks or paper reproductions.

## Isolated candidate and running validation

`prepare_sniffles_option_interface.py` snapshots the current sources, generates
an exact reviewable patch and builds the two candidate classes against the
unchanged JDK21/JDK25 development artifacts. It leaves the working sources used
by active tests untouched. All other class/resource bytes remain unchanged.

`validate_sniffles_option_interface.py` uses the separately installed and bound
exact runtime. Its planned complete batch has ten cases and twenty records,
covering all five editions, the new thresholds, literal IDs, phased CRAM, legacy
DUP behavior, modern force genotyping and tandem-repeat input. A success requires
every raw record, INFO/FORMAT/sample value and header relationship to match the
direct original references. Timestamp, command and legacy captured-input sample
path differences are checked explicitly; no scientific value is normalized.

The validator also requires six portable frozen checks across both compiler
artifacts and Windows21/Windows25/Linux21, retaining original warnings. A new
controlled copy has a false read-support view and recomputed inventory hashes;
the verifier must reject its inconsistency with the unchanged original VCF.
**These checks remain pending until their complete PASS receipt exists.**

The main workflow candidate keeps complete runtime/data checks immediately before
and after each original caller/preparation process. Java-only view/bookkeeping
steps check frozen data, member populations and capacities. The final complete
library census has full checks before and after it, with cancellation and native
helper checks on every probe. No checksum cache crosses original processes.
Actual source-fatal, final-census data mutation and final-census cancellation tests
must prove that no earlier provisional case becomes admitted after failure.

## Apply only verified bytes

After the running tests terminate and the corrected option candidate passes,
require the working source hashes to match the candidate's recorded before state.
Apply its exact two source files and rebuild normally with both compilers. Check
that the normal artifacts match the tested isolated artifacts byte for byte.
If they differ, retain the discrepancy and validate the normal artifacts; do not
rewrite old evidence hashes to bless them. Historical checkpoints retain their
own sources/artifacts and archive-only verification. Their source checks must not
be relabeled as checks of subsequently changed code.

The six primary paper reviews have a validated, unapplied candidate containing
1,009 reviews and 353 supporting pointers, preserving all 1,003 prior reviews.
P102's filter paragraph has an explicit checked method-detail link to its named
Sniffles paragraph. Calling proof, source-role application, catalog rendering and
existing scientific/browser gates must pass before adding availability credit.

Read the [runtime installation guide](SNIFFLES_RUNTIME_INSTALLATION.md) for
permitted private installation and SKIP3 recovery, and the
[execution development guide](SNIFFLES_EXECUTION_DEVELOPMENT.md) for whole-batch
admission. Preserve legacy DUP-to-INV discrepancies and missing calls; never
relabel them. A source-fatal exit-zero run is failed output, not an empty callset.
Frozen verification does not rerun callers, authenticate execution, refit SVs or
certify biological/paper reproduction. The canonical executable remains unchanged.
