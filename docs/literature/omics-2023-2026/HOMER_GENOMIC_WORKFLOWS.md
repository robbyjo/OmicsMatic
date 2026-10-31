# HOMER genomic motifs with a supplied background

The development `homer-tools genome` command runs original HOMER 5.1, 4.11 or
4.11.1 with a supplied custom FASTA, target regions and background regions.
Default `analysis both` includes known enrichment, de novo discovery, known-motif
matching and original SVG/HTML reports. `analysis known` or `analysis denovo`
selects one phase explicitly. It uses the original old-background algorithm,
including `-useOldBg` on 5.1. Native Java audits the complete intake, extraction,
composition weighting, final populations and fixed-model statistics.

Read [current phase validation](homer-genome-phase-validation.json),
[five runnable recipes](../../../examples/homer-genome-workflow/README.md) and
[runtime setup](../../../tools/literature/HOMER_GENOMIC_RUNTIME.md).
The exact current development JAR is identified by path and SHA-256 in validation.
This is a bounded development workflow. The broader HOMER catalog badge remains
missing. [Generated backgrounds now have their own operation admissions, choices and validation](HOMER_GENERATED_WORKFLOWS.md). Named genome packages, repeat/TE resources,
peak/coverage and RNA workflows remain unfinished. The 250-paper goal stays active.

## Warnings, skips and required actions

**Missing, forbidden, unsupported or changed dependencies: SKIP the affected
analysis, exit 3.** Required action: use Linux x86_64 Java 17+, inside WSL on
Windows, and an admitted genomic home for the exact selected version. Core and
annotation homes do not admit genomic motifs. Where installation is permitted:

```sh
python3 tools/literature/install_homer_genome.py \
  --profile linux-x86_64-genome-5.1 --home NEW_PRIVATE_HOME \
  --cache ARCHIVE_CACHE --report NEW_REPORT --allow-install \
  --compiler /PATH/TO/g++ --make /PATH/TO/make \
  --archive-dir VERIFIED_OFFLINE_ARCHIVES
```

Choose 4.11 or 4.11.1 explicitly when required. Add `--allow-download` only where
permitted; otherwise provide the pinned offline archives or populated cache.
The setup builds the original extractor with its installation directory
configured. It needs C++11 headers, pthread, math and static C++/GCC libraries.
No OS packages or genome data are installed. Analysis never installs or
substitutes a version. If installation is forbidden or dependencies remain
unavailable, retain the skip and continue independent analyses.

**Failures exit 2 and quarantine provisional files under `partial/`.** Required
action: inspect `WARNINGS.md`, `analysis-status.json`, `session.json`,
`original.stderr` and retained inputs/ledgers. Correct the named cause and use
a NEW output directory. Partial tables are not completed results. An error
before staging may have no files to quarantine. Preserve old homes and reports;
do not edit admission markers to bypass changed files or host dependencies.

**Legacy known-report division by zero is an analysis failure, even if the
outer wrapper exits 0.** Original 4.11/4.11.1 infer displayed population totals
from a motif's rounded hit percentages and can divide by zero at zero percent.
The adapter promotes `Illegal division by zero in findKnownMotifs.pl` into the
main warning and withholds provisional results. Required action: retain the
failure, inspect the complete input/model contrast and original diagnostics,
and select any scientifically appropriate separately admitted version or
operation explicitly before a new run. Version 5.1 guards that division but
can print a fallback population size of one; the native audit retains actual
group populations separately. There is no automatic version substitution or
upstream scientific patch. A missing report is not a finding of no enrichment.

Readable `/proc` is required for summed-RSS monitoring. Periodic time, workspace
and RSS checks are not OS quotas; shared memory can be counted more than once.
Input sizes, model/record counts, extraction work and search neighborhoods have
explicit ceilings. Review `-Xmx` and resource estimates for real datasets.

Original archives use HTTP content pins without independently authenticated
signatures. Original GPL notices are retained; binaries are installed privately.
Cell Ranger remains excluded by user request.

## Explicit phase selection

`--analysis both` is the default and preserves the complete workflow. With
`--analysis known`, supply `known-motifs` and remove `comparison-motifs`,
`motif-length`, `mismatches` and `motifs-to-find`. Known-only retains the same
extraction, composition weighting and chosen normalization. It does not require
discovery seed windows or allocate discovery search neighborhoods; its actual
fixed-model audit retains work and memory bounds. Threads 1 through 10 are
allowed for known-only. Discovery still requires a divisor of ten to preserve
the original wrapper's trial count.

With `--analysis denovo`, supply `comparison-motifs` and remove `known-motifs`
and `known-strand-policy`. De novo-only retains learned-model matching and
SVG/HTML reports. Forward selection works on all three releases without a
known-enrichment strand permission for a phase that is not run. Similarity to
a known model is not known-motif enrichment.

Unused phase-specific options fail input validation with the option to remove;
they are not silently ignored. Every session records `requested_analysis`,
`known_analysis` and `discovery_analysis`. Unrequested phases have **null motif
counts and no result tables**, with `not-requested` status. This is not a finding
of zero enrichment or zero discovered motifs. Empty discovery remains a separate
completed-phase result with a warning. Read the requested-phase tables and
original reports identified in that session.

## Reference, coordinates and retained populations

Provide a declared reference build, plain/gzip FASTA, exact contig names and
BED6 or HOMER6/7 positions. Declarations do not prove build compatibility.
FASTA is streamed into a case-preserving indexed LF copy. Raw bytes, complete
headers, source IDs, scores and optional focus values remain retained. Internal
ASCII aliases map bijectively to source identities. UTF-8 paths need a UTF-8
host locale; installation homes remain shell-safe ASCII. An ASCII-named
`--options-file` accepts string-valued JSON; direct flags override its values.

BED6 is zero-based half-open; HOMER is one-based inclusive, with `+/-` or `0/1`
strand. Choose unknown-strand, duplicate-ID, alphabet, lowercase masking,
background-overlap, GC/CpG/none weighting, strands and statistical test policies
explicitly. All extraction intervals must fit the exact contig. There is no
silent contig renaming, build conversion or repair of original boundary quirks.

Original numeric size **N yields N+1 inclusive bases**. Asymmetric START,END
uses width END-START and a strand-oriented midpoint displacement. Overlap
removal has a separate source rule: it compares inclusive windows about integer
peak midpoints with half-width floor(N/2), before asymmetric displacement.
With `given`, it compares the source inclusive intervals. Do not replace this
with strict center-distance or resized extraction overlap.

Every region remains in `regions.tsv`, `population-plan.tsv` and `population.tsv`.
GC/CpG frequencies use canonical-base denominators and a six-decimal intermediate
print with the original binary half-even tie behavior. N does not bridge adjacent
canonical pairs. Unsupported bins drop both roles before supported totals and
five-decimal weights are recomputed. Quality and overlap exclusions are explicit.
`seq.tsv` can legitimately contain sequences absent from the final group file;
the audit checks complete membership rather than inferring it from table headers.
`discovery-input.tsv` distinguishes canonical-width seed eligibility from final
weighted analytic membership. No source record is silently dropped.

## Version-specific strand and matrix behavior

**4.11 and 4.11.1 ignore forward-only selection for known enrichment.** Their
wrapper runs known enrichment on BOTH strands while normalization/discovery use
FORWARD. Default `--known-strand-policy require-match` skips that legacy
configuration when known enrichment is requested. Explicit `original-wrapper` retains the source behavior and
records the distinct effective strands in warnings/session output. Version 5.1
forwards the choice to known enrichment correctly. Do not claim that a legacy
forward-only wrapper request produced forward-only known enrichment.

`--known-motifs profile` and `--comparison-motifs profile` select the pinned
release resources explicitly. Their original rows can be nonunit/count weights;
fixed scoring preserves those values and uses log(max(value,0.001)/0.25).
Source thresholds, log P and extra header metadata are retained. Custom models
default to strict probability validation; choose `--matrix-values original-weights`
explicitly for raw weights. Raw weights are not calibrated probabilities.
The separate public `HomerMotifs` probability API remains strict.

## Statistics and interpretation

`results.tsv` preserves original known statistics alongside native weighted
presence, full log P and the actual floored integer test inputs. Original sequence
and weight accumulation order is retained. Original table headings and rounded
percentages do not define an exact population size. Background normalization is
replayed through the original engine using independently prepared native initial
weights and the exact retained sequence order. It does not reconstruct that
optimizer in Java or establish a weighted sampling model.

Known log P has two output-rounding stages: a six-decimal intermediate, then
three-decimal scientific formatting. The original `q-value (Benjamini)`
exponentiates the intermediate log P, groups tied P values at their last rank
and takes a **forward cumulative maximum** of m*p/rank, clipped at one.
**This is not standard BH's reverse cumulative minimum.** The original q and
native reconstruction of the actual source calculation remain separately named.
No E value or additional multiple-testing family is inferred.

`discovery-results.tsv` and `discovery-presence.tsv` retain conservative
print-precision presence bounds for learned models. These are not exact
optimizer reconstruction. Inspect original models, `original-out/`,
`original.stdout/stderr`, all ledgers and warnings before inference. Technical
agreement on synthetic fixtures does not establish biological or full-paper parity.

## Reproduce the public workflow checks

```sh
python3 tools/literature/verify_homer_genome_workflow.py \
  --jar /PATH/TO/DEVELOPMENT.jar --out NEW_VERIFICATION \
  --home-5.1 /PRIVATE/HOMER_51 --home-4.11 /PRIVATE/HOMER_411 \
  --home-4.11.1 /PRIVATE/HOMER_4111 --java /usr/bin/java
```

The verifier generates the checked-in recipes unchanged, runs all five on each
version and compares fifteen fresh direct original executions on the exact staged
inputs. It retains complete known/sequence/model byte comparisons, normalized
group comparisons, SVG/HTML report checks, commands, source/input/JAR hashes and
runtime bindings. Failed/skipped checks remain prominent in `verification.json`
and `WARNINGS.md`; they are not passes. It never installs or mutates a home.
Verification adds a 120-second per-original-process limit and a 600-second outer
command limit. Java retains its own resource monitoring; the verifier checks
workspace size after Java exits, avoiding a live directory walk during atomic
output publication. Original reference commands retain their live workspace
bound. Publication failures preserve the named staging directory and remain
failed checks; they are not silently retried as successes.
The separate primitive verifier covers 5,496 original/native comparisons,
including decimal ties. The validation JSON separates current and historical
milestones and records the exact scope of every count. The earlier 805-test
both-phase milestone remains retained in `homer-genome-validation.json`.
