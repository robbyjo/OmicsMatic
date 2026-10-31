# Original HOMER fixed-motif workflows

The development command `homer-tools` runs the unmodified original `homer2`
core for fixed known-motif enrichment and motif-site finding. It verifies every
result against native Java audits and preserves original numeric strings beside
the native values. `homer-core-validation.json` identifies the tested candidate.
The Java-only `homer-scan` command and `HomerEnrichment` API remain available.
Original de-novo discovery is now available; read
[the discovery guide](HOMER_DISCOVERY_WORKFLOWS.md) and current
`homer-discovery-validation.json` for the executable and scientific scope.

**Broader HOMER work remains unfinished.** This command does not yet implement
`findMotifsGenome.pl`, genome/background construction,
annotation, tag directories, peak calling, coverage or RNA/exon/RPKM counting.
A successful core analysis does not reconstruct those paper workflows. Read
[the source-role review](HOMER_SOURCE_REVIEW.md) before choosing an operation.
P126 reuses HOMER-origin motif models through MotifDB without reporting a HOMER
program run; P040's HOMER 5.1 RNA operation is not a motif analysis.

## Installation and unavailable dependencies

**SKIP the affected upstream analysis when installation or execution is forbidden,
or when a dependency is unavailable, unsupported or changed.** The command exits
3 and writes a prominent cause and Required action in `WARNINGS.md`.
Analysis never installs, downloads, changes versions or silently falls back to a
native method. Cell Ranger, ARC and ATAC remain excluded.

Use Linux x86_64 Java and Python; on Windows use Linux Java/Python inside WSL.
The installer admits exact original releases 5.1, 4.11 and 4.11.1 as separate
profiles. Choose the required release explicitly. Setup installs only the
private core binary and original GPL notice, with host-library hashes and actual
scanning, two known-motif statistics and discovery health checks. Discovery
health does not establish a project discovery workflow.

Where installation is permitted, from the repository root inside Linux:

```sh
python3 tools/literature/install_homer.py \
  --profile linux-x86_64-5.1 \
  --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_SETUP_REPORT \
  --archive-dir OFFLINE_ARCHIVE_DIRECTORY --allow-install
```

Supply the directory containing the original `homer.v5.1.zip`,
`homer.v4.11.zip` or `homer.v4.11.1.zip`. Add `--allow-download` only where
downloads are permitted. Existing homes, corrupt caches, partial downloads and
failed reports are preserved. Correct the named cause and choose new paths.
The official archives were available over HTTP; the pinned hashes identify
downloaded content and are **not independently authenticated upstream signatures**.
The repository does not redistribute the original binary. Linux kernel, CPU,
loader semantics and setup Python remain host dependencies. Re-admit a new
profile after a host/library change; do not edit the marker to bypass it.

## Commands and required choices

See [the runnable examples](../../../examples/homer-core-workflow/README.md).
Select `check`, `find` or `known`. Required flags are explicit:

| Choice | Meaning |
| --- | --- |
| `--home`, `--profile` | Private admitted home and exact original release. |
| `--targets`, `--motifs` | Complete FASTA and supplied HOMER probability matrices. |
| `--strands forward\|reverse\|both` | Orientations tested in both engines. |
| `--alphabet strict-dna\|mask-n` | A/C/G/T/N, or explicit printable-ASCII masking to N. U becomes N, never T. |
| `--duplicate-ids error\|retain-ordinal` | Reject repeated source IDs, or keep every record with its own ordinal. |
| `find --offset midpoint\|INTEGER` | Original strand anchors; no header/genome interpretation. |
| `known --background` | Complete supplied background FASTA; no automatic sampling or shuffling. |
| `known --overlap-policy error\|retain` | Reject, or explicitly retain matching target/background IDs or normalized DNA. |
| `known --statistic hypergeometric\|binomial` | One-sided sequence-presence test. |
| `known --normalization none` | Fixed unweighted core; another normalization request skips instead of using none. |

Direct flags override an optional ASCII-named UTF-8 string-options JSON file
passed with `--options-file`. Unknown, recursive, non-string and inapplicable
options fail. No arbitrary upstream argument or shell command is accepted.

## Statistics, identities and coordinates

Known-motif enrichment counts each sequence once when it has any threshold-passing
site. It does not treat every motif site as an independent sample. All records,
including N-only, short and zero-hit sequences, remain in the populations.
Empty FASTA records fail explicitly because the original FASTA reader omits them.
At least two background sequences are required for the audited floor/ceiling.
Overlapping roles are never deduplicated; their sampling interpretation needs
review even when explicit `retain` permits execution.

The hypergeometric population combines the supplied target and background roles.
The binomial background rate is clamped to `[1/B,(B-1)/B]`, matching the inspected
original code. The log2 enrichment ratio uses a one-sequence pseudocount in each
role. Original `1e...` strings truncate `logP/2.3026` toward zero and are coarse
displays, not precise P values. `results.tsv` retains those strings, original
three-decimal log P/ratio and two-decimal percentages, plus separate native
full-precision log statistics. Audits respect printed precision. No threshold
optimization, sequence/GC/oligo normalization, P-family selection, multiple-testing
adjustment, q value or E value is inferred.

Unique ASCII sequence/motif aliases prevent upstream parser ambiguity. The full
source bytes, names, UTF-8 headers, original ordinals and model metadata remain
in immutable snapshots and ledgers. The staged model keeps the supplied
probabilities and cutoff; its name/consensus fields are aliases, not new scientific
models. Fixed scores sum `ln(max(p,0.001)/0.25)` without row normalization.

Find results include zero-based half-open sequence intervals and original
plus-left/minus-right anchors with the explicit offset or midpoint. Minus matched
text remains forward-oriented. Each original site is valid and unique, and the
total must equal the complete native scan. No chromosome or assembly is inferred.
Signed integer coordinate overflow fails before execution.

**Capacity correction:** the earlier dense-scan refusal was incorrect.
Original HOMER doubles the longest-sequence value before allocating site arrays.
All 185 scanner cases pass each release, including all 11 previously excluded
dense cases:555 comparisons/8,556 hits. Dense `find` retains all records within
ordinary resource capacities. Read `homer-capacity-correction.json`; old
exclusions/skip controls remain retained as invalidated evidence. Use the current
candidate; older core-candidate-1 still refuses these valid inputs.

## Outputs, failures and resource recovery

**Always read `WARNINGS.md`, `analysis-status.json`, `session.json` and
`original.stderr`.** Exit 0 means complete audited output with review warnings.
Exit 3 is an explicit dependency/capability skip. Exit 2 is an input, process,
resource, identity or audit failure. Both unsuccessful statuses move provisional
files under `partial/`; those tables must not be used as completed results.
Correct the cause and rerun to a new directory. Existing results stay intact.

Outputs retain raw `input-*` snapshots, staged files, `models.tsv`, `sequences.tsv`,
every model/sequence pair in `native-scan-counts.tsv`, complete original stdout
and stderr, `results.tsv`, runtime/input hashes and the transaction manifest.
Runtime files, host libraries, source files and staged inputs are checked again
before success. Owned processes are monitored and joined after timeout/failure.

Positive capacity flags bound inputs, decoding, residues, records, models, native
score operations, hits and workspace. See `--help` for their defaults. Major
retained allocations are checked against the workspace and Java maximum heap;
review capacities and `-Xmx` before increasing them. Raw snapshots and native
audit streams share a byte budget. Original output is monitored periodically,
which is not an OS-enforced disk or RSS quota. Final status/manifest diagnostics
add metadata outside that byte check. Resource failures do not publish truncated
successful tables.
