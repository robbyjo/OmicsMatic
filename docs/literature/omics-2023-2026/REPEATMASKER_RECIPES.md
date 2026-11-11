# RepeatMasker recipe and protected input contract

**Development command:** `repeat-tools` now exposes native `CHECK`, `MASK`
and `AUDIT` from a JAR rebuilt from this checkout. The
[copyable workflow](../../../examples/repeat-native-workflow/README.md) includes
all three recipes, synthetic data, output names and recovery examples. The
[Java annotation API example](../../../examples/native-repeat-annotations/README.md)
also remains available. Native operations require Java 17 or later and no
RepeatMasker installation.

```text
java -Xmx256m -jar build/cli/omicsmatic-0.3.7.jar repeat-tools --recipe examples/repeat-native-workflow/mask.json --out build/repeat-native-mask-example
java -jar build/cli/omicsmatic-0.3.7.jar repeat-tools --verify build/repeat-native-mask-example
```

Always use a new output directory and read `WARNINGS.md`. Each operation has
distinct completion flags; no native completion certifies an original search,
annotation completeness or biological absence. Require `COMPLETED.json`, its
matching manifest and read-only verification. Original `SEARCH` is separately available with an exact admitted runtime; see the [public workflow](REPEATMASKER_PUBLIC_WORKFLOW.md). Missing or unsupported prerequisites retain `SKIP3` and required actions. No analysis installs software or substitutes an edition.

Analysis `--options-file` accepts UTF-8 `recipe` and `out` string fields and an optional `runtime` string for SEARCH; no other fields are accepted.
For a launcher that cannot pass Unicode verification paths, use
`--verify-options-file` with exactly one UTF-8 `directory` string. These paths
resolve from the working directory, with absolute paths recommended. Both
options files are checked through EOF and for changes during execution.

Whole-bundle workspace/member budgets include root metadata and components.
The controller subtracts existing metadata and reserves 256 KiB/eight members
from component budgets for finalization and retained diagnostics. The component
64 KiB/one-member failure reserve described below remains separate. Correct a
named capacity failure and rerun to a new directory; partial files are never
admitted as a completed result.

Requests distinguish four operations: `CHECK` prepares inputs, `SEARCH`
requests an original upstream search, `MASK` requests native masking of
supplied annotations, and `AUDIT` checks a supplied complete masked FASTA.
Preparing inputs certifies structure and the declared computational contracts.
It does not certify an upstream search completed or establish biological absence.

The internal native `MASK` executor produces `native-masked.fa`, per-query
`native-coverage.tsv`, `native-mask.json` and `WARNINGS.md`. It rereads the full
output and verifies protected inputs and output hashes before returning a
result. The files are native derivations of supplied annotations; they do not
certify an original search or biological absence. Preparations and results
share the declared workspace byte/member budgets, with a 64 KiB/one-member
failure diagnostic reserve. A failed run retains `NATIVE_MASK_FAILED.txt` and
partials; correct the named cause and retry to a new directory. See the
[execution validation and recovery notes](REPEATMASKER_NATIVE_STATUS.md).

The runnable toy recipes use explicit small capacities and `-Xmx256m`. The
illustrative preset-only request below uses a default 1 GiB retained capacity;
use a reviewed heap such as `-Xmx2g`, or declare smaller capacities that cover
the complete data. Declared retained storage must fit within 75% of the heap.

A native annotation request has this shape. Replace the assembly and review
statements with information about the actual data. Paths resolve within the
declared `resource_root`; choose the actual OUT/UCSC layout and compression.

```json
{
  "schema_version": 1,
  "operation": "MASK",
  "mode": "ANNOTATION_REUSE",
  "profile": "native-repeat-annotations-v1",
  "resource_root": ".",
  "assembly": "YOUR_REFERENCE_AND_EDITION",
  "input_review": {
    "organism": "YOUR_ORGANISM",
    "genome_provenance": "REFERENCE_SOURCE_AND_CHECKSUM",
    "repeat_library_provenance": "Not used for this native import",
    "annotation_provenance": "ANNOTATION_SOURCE_ASSEMBLY_AND_EDITION",
    "coordinate_policy": "Declared OUT or UCSC source layout; internal genomic coordinates are zero-based half-open",
    "paper_settings_status": "State which paper settings are known and which remain unresolved"
  },
  "query": {
    "path": "genome.fa.gz",
    "encoding": "GZIP_UTF8"
  },
  "annotations": {
    "path": "rmsk.tsv.gz",
    "encoding": "GZIP_UTF8",
    "format": "UCSC_RMSK_WITH_BIN",
    "completion_evidence": "EXTERNAL_NOT_CERTIFIED"
  },
  "staging_policy": "EXACT_SOURCE",
  "mask": "SOFT",
  "selection": {
    "classes": "ALL",
    "class_names": [],
    "names": "ALL",
    "repeat_names": [],
    "marked_overlap": "INCLUDE"
  },
  "limits": {
    "preset": "bounded-repeat-v1"
  }
}
```

`AUDIT` additionally requires `masked`, with the same `path` and `encoding`
fields as `query`. `CHECK` and `MASK` reject that field. Annotation formats
are exactly `OUT_WITH_ID`, `OUT_WITHOUT_ID`, `UCSC_RMSK_WITH_BIN` and
`UCSC_RMSK_NO_BIN`. UCSC signed consensus slots remain raw. Every annotation
must join an exact FASTA query with compatible bounds and source length hints,
including rows excluded by selection. Classes and repeat names select their
intersection: use `EXACT` with a nonempty array of unique exact labels.
`marked_overlap` is `INCLUDE` or `EXCLUDE`. No motif richness, biological class,
query alias or transcript/exon denominator is inferred.

Search modes are `CUSTOM_LIBRARY`, `SPECIES` and `SIMPLE_ONLY`. Custom searches
require `library`, with an explicit FASTA path and encoding. The other two
require an explicit `species` name or taxon identifier. Library base names
must be unique even when classification tags differ. A `#class/family` tag
is preserved; the preparation does not invent one.

Current internal profile declarations are:

| Profile | Resource scope |
| --- | --- |
| `native-repeat-annotations-v1` | Native annotation reuse |
| `linux-x86_64-repeatmasker-4.2.6-custom-rmblast` | Original 4.2.6 custom-library foundation |
| `linux-x86_64-repeatmasker-4.1.5-dfam3.7-rmblast` | Original 4.1.5 with explicit bundled Dfam 3.7 |

**Edition warning:** requesting an unsupported profile raises a distinct
unavailable-capability error. No current version replaces a historical one.
The paper's exact Dfam release and every dependency edition remain unresolved.
An official bundled resource does not resolve those missing method details.
RepBase-dependent work requires an appropriately licensed local resource.

The internal original payload/resource foundation preserves complete clean
sealed source/configuration bytes and creates a new analysis-owned library/cache tree.
Only the historical working-copy LIBDIR value is configured, with exact
original/configured hashes and a verified inverse; algorithm bytes stay unchanged.
The historical 5.86 GB HDF5 library counts at its full logical size even when
hardlinked. Original paths require canonical ASCII without whitespace or shell
metacharacters. These components do not yet establish scientific runtime
admission or expose original searches; read the
[current validation and health status](REPEATMASKER_NATIVE_STATUS.md).
Export the standalone private payload installer with
`repeat-tools --export-runtime-setup NEW_DIRECTORY`; read the
[setup guide](REPEATMASKER_PAYLOAD_SETUP.md). Installation success alone does
not admit original SEARCH or a scientific runtime.

Search requests require `parameters` and `process_limits`. Parameters include
`speed` (`DEFAULT`, `SENSITIVE`, `QUICK`, `RUSH`), `parallel_batches`, `rna`
and `low_complexity` (`INCLUDE`/`EXCLUDE`). Optional
`maximum_divergence_percent` is an exact decimal string, such as `"40"`.
Zero is rejected because original `-div 0` is treated as unset. `SIMPLE_ONLY`
keeps low-complexity/simple-repeat searching enabled and requests `-noint`;
the paper did not report that exact flag. The original program uses four
RMBlast engine threads per parallel batch, with additional orchestration
capacity required. `process_limits` uses preset `bounded-process-v1` and
explicitly bounded wall time, address space, CPU, output file sizes, file
descriptors, workspace, members, processes, threads and sampled RSS.
The internal supervisor enforces these requested budgets in eight controlled
original searches, accounting for protected preparations and run files together.
Hard address-space, CPU, file-size and descriptor limits are per process;
aggregate RSS is sampled. The public runtime/resource/cache binding is now validated for the declared profiles. Process completion alone does not certify a scientific result. Both tested original editions can emit a no-hit `.out` without `.masked` or `.tbl`; the admitted result contract preserves these absences explicitly. Read the
[current validation and no-hit warning](REPEATMASKER_NATIVE_STATUS.md).

**Staging decision:** native annotation reuse requires `EXACT_SOURCE`.
It preserves identifiers, full headers and base case while writing plain
UTF-8 with 60-base LF wrapping. Original compressed/plain bytes remain in
snapshots. Search `EXACT_SOURCE` refuses inputs requiring original FastaDB
cleanup, including lowercase bases, unsupported identifier/header forms,
names ending in `.seq`, and DNA-like header suffixes that the original parser
would reinterpret. Query names in that policy use the admitted ASCII form
and at most 50 characters.

For searches, the explicit alternative
`ASCII_ORDINAL_QUERY_IDS_AND_UPPERCASE_FASTA` uses `Q0000000001`-style query
IDs and uppercase FASTA bases. Original query names, full headers, case,
sequence hashes, lengths and ordinal mappings remain recorded. Library IDs
and classification tags are preserved. This is an explicit representation
choice; genomic positions and sequence lengths remain unchanged. No U-to-T
replacement is inferred: original-search admission rejects `U`.

**Limits and recovery:** whole-number fields require integer JSON literals,
such as `16777216`, without decimal points or exponents. Limits cover complete
source bytes, decoded text, records, residues, lines, headers, annotation
retention, interval indexes, preparation workspace and file counts. The
declared memory budget covers seven bounded FASTA censuses, annotations and
the interval index. It must fit within 75% of the Java maximum heap; a
`-Xmx2g` heap covers the default 1 GiB retained budget. Larger actual data may
require reviewed larger limits and a larger heap. Input excess causes a
failure with retained data; it is never truncated into a successful result.

A new preparation directory contains original `input-snapshots`, plain
`stage` files and `preparation.json`. Originals and snapshots are hashed
before and after staging. Later verification checks every original,
snapshot and prepared member and refuses changes or unexpected files.
The workspace reserves 64 KiB and one member slot for failure diagnostics.
`PREPARATION_FAILED.txt` prominently identifies incomplete preparation and
the required action. Retain the directory and originals, correct the stated
data/format/limit/heap cause, and retry with a **new** work directory.
Existing output directories and protected sources are never overwritten.
Preparation emits no scientific completion marker.

The internal original-stage primitive now uses the supported original
`-nopost` search followed by independently supervised original `ProcessRepeats`.
It checks raw-result headers/census through complete EOF before postprocessing,
and complete annotation/mask populations afterwards. Both stage exit/cleanup
records are required. Current raw headers precede alignments; historical
headers can trail them. Above10million query bases the original raw result is
gzip; every CRC/member/trailer and physical EOF is checked. Query-count/base,
edition/mode/engine mismatches, duplicate or missing headers and truncation fail.
No-hit mask/table absences remain absent and never certify biological absence.

This component is now used by the separately validated public SEARCH workflow. Host/provider admission and final workflow gates pass for the declared profiles; SEARCH without required prerequisites returns SKIP3. Process limits apply independently to each
original stage while logical input/resource/output capacity is shared. Hashing
and output-audit time are outside the watched child wall limit. Read the
[exact evidence and remaining scope](REPEATMASKER_NATIVE_STATUS.md).
