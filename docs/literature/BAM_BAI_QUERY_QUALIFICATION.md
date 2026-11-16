# Native BAM/BAI audit: coordinate, query and large-file controls

**Warning: original Sambamba 0.6.6 has two distinct observed failures below.
An affected original sidecar or failed marking/removal run is not accepted.
This checkpoint qualifies the described audit and query controls; it does not
promote historical workflow receipts, reproduce a paper or add catalog credit.**

This continues the [initial native audit checkpoint](BAM_BAI_AUDIT_DEVELOPMENT.md)
without changing its production Java implementation or rewriting its saved
evidence. `BamBaiAudit.audit` remains the read-only library API. The existing
`hts-tools --engine native --method sam-index` command supplies fresh Java
indexes. This work adds independent fixtures, real original-runtime controls,
offline regression tests and [saved evidence](bai-query-qualification/STATUS.json).

Five independent synthetic cases cover leading/intermediate/trailing empty
references; placed-unmapped and unplaced records; 16-kb, 128-kb and larger
coordinate boundaries; long spliced reference spans; the final supported
reference coordinate in the fixture; and a 50,000-base record crossing BGZF
members. The generator writes BAM bytes directly. Its expected query names
come from the declared synthetic record geometry, independently of HTSJDK or
the native audit implementation.

There are 51 actual BAM/index pairs: native Java input indexes, direct original
Sambamba indexes, original mark/remove sidecars and fresh native indexes of
those original output BAMs. Across these pairs, all 30,070 mapped-read regional
queries match the independently specified ordered names. Three original 0.6.6
indexes are refused for inconsistent metadata; the other 48 pass the audit.
Successful regional queries on the three refused originals do not override
their metadata failure. These query checks explicitly exclude placed-unmapped
records and use full reference-span overlap, including CIGAR `N` and `D` spans.

Nine further checks use retained original four-million-record BAMs and fresh
native indexes with actual 64-MiB maximum Java heaps. Five checks contain four
million records each; four check two-record removal outputs. Each check runs
the complete native audit, verifies every sequential record against the
independent generator's name/flag/coordinate/quality pattern, and checks every
record returned by two indexed point queries plus an empty-region query.
Across the nine checks, each of those three passes verifies 20,000,008 records.
The original BAMs and sidecars remain unchanged. Fresh Java index construction
also ran with 64-MiB heaps; original duplicate classification was not rerun for
these retained large files in this checkpoint.

Thirteen further controls reject nine malformed or inconsistent indexes and
accept four legal offset/optional-metadata variants. In the critical negative
control, a wrong linear offset is placed in an earlier empty window, before a
long read's own windows. An actual HTSJDK query then loses the expected read;
the audit refuses that index. Legal short-BGZF-member-end and physical-EOF
offset aliases stay queryable without rewriting index bytes. Other controls
exercise interior offsets, nonexistent BGZF blocks, wrong bins, chunks crossing
references, false counts, truncation and trailing bytes.

Four new offline regression tests replay all 51 small pairs and 13 controls,
including all 30,070 mapped-query expectations and explicit interruption. The
135,830-byte [fixture archive](../../src/test/resources/org/jlinalg/alignment/bai-wide-original-native.zip)
contains 132 members and only synthetic data. Fourteen focused Java tests
passed across the new tests and existing BAI/streaming BAM audit suites, with
no failures, errors or skips. Those unit tests replay saved original outputs;
the separate saved runtime records document the actual original executions.

## Failures and required actions

**0.6.6 metadata extent failure:** the `wide_unplaced_tail` direct, mark and
remove indexes include an unplaced tail in the last placed reference's metadata
extent. This agrees with the previously retained small-fixture defect. Retain
the original BAM/index and failure. For a new analysis, create a separate native
Java index, audit the exact pair and explicitly select that new index downstream.
Do not replace the original sidecar or silently promote an old receipt.

**0.6.6 quality-score overflow:** both mark and remove attempts on the unchanged
50,000-base, quality-30 record fail with `Conversion positive overflow` and admit
no analysis. The checked conversion in original
[0.6.6 `computeScore`](https://github.com/biod/sambamba/blob/v0.6.6/sambamba/markdup.d#L708)
uses a 16-bit score; that record's score is 1,500,000. Original
[0.8.2](https://github.com/biod/sambamba/blob/v0.8.2/sambamba/markdup.d#L710)
uses 32 bits, and its mark/remove runs on the same unchanged fixture pass these
controls. Use a separately qualified permitted edition/tool for a new analysis,
record the edition change, or skip the affected analysis. Do not trim reads,
alter qualities or claim the failed 0.6.6 workflow was reproduced. The first
incomplete wide-matrix attempt and all raw failure consoles remain retained.

The strict BAM/BAI profile and explicit bounds in the initial API guide still
apply. CSI, partial coordinates, arbitrary producer compatibility and every
downstream consumer are not established by these controls. The existing
Sambamba execution profiles keep their original collateral-index warnings;
successful per-file verification is a separate step. Missing or forbidden
prerequisites require skipping the dependent analysis until a permitted setup
is supplied. These controls used the already permitted cached runtimes and
required no new software installation.

## Recheck and provenance

Run the offline regression tests from the project root:

```text
gradlew.bat :test --tests org.jlinalg.alignment.BamBaiWideAuditTest --tests org.jlinalg.alignment.BamBaiAuditTest --tests org.jlinalg.alignment.SambambaStreamingBamAuditTest
```

Read the saved public metadata and source snapshots:

```text
python -B tools/literature/bai_validation/records.py
```

That reader does not rerun scientific analysis. Add `--private-base
build/bai-audit` to check the complete named new control records retained
locally; add `--large-originals build/sambamba-streaming/large-originals-1` to
also read back the external retained large inputs. Large BAMs and executable
JARs remain local and are not in the public metadata archive. The helper
scripts' `--help` describes how to create new original-runtime control outputs.
SHA-256 pins establish integrity, not authorship or executable authenticity.
Whole-current-tree catalog admission and the full 250-paper goal remain open.
