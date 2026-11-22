# Lead-to-known locus novelty review

`fuma-tools --method locus-novelty` implements a native Java supplied-reference
distance rule. It compares each lead with every selected known-association row
on the same literal chromosome. It retains all nearby associations, including
repeated variant IDs/positions from distinct source rows. It performs no LD
discovery, allele harmonization, catalog acquisition, trait synonym matching or
automatic genome conversion.

**ABSENT MATCHES REQUIRE REFERENCE COVERAGE REVIEW.** A no-match result becomes
`NOVEL_RELATIVE_TO_REFERENCE` only when investigator-declared coverage spans
every queried integer coordinate for the selected reference population. Without
that assertion it is `UNASSESSABLE_REFERENCE_COVERAGE`. A known match remains
`KNOWN_WITHIN_WINDOW` even if coverage elsewhere is incomplete. Required action:
review the catalog release/scope, build, trait policy, endpoints and all matches.
Coverage assertions are not independently verified literature completeness;
reference-relative novelty is not universal novelty or causal evidence.

**JAVA UNAVAILABLE OR INSTALLATION FORBIDDEN: SKIP THE ANALYSIS (verifier exit 3).**
Required action: provide Java 17+ on a permitted supported host, using the public
verifier's `--java` if needed. If installation cannot be permitted, retain the
dependent analysis as skipped. Analysis never installs. These native recipes need
no MAGMA, R or upstream FUMA runtime. Unavailable required upstream software
means skip its affected discovery/generation analysis and supply valid permitted
inputs; this module does not invent missing loci or associations.

**INPUT/IDENTITY/CAPACITY FAILURE (exit 2):** inspect `WARNINGS.md`,
`analysis-status.json`, `session.json` and `partial/`, correct the named cause and
rerun to a NEW directory. Diagnostic partial tables are not completed results.
Successful exit 0 still requires review. Unassessable coverage is a completed,
inconclusive comparison state, not a fabricated null or a runtime failure.

## Runnable example

Use the [self-contained synthetic example](../../../examples/fuma-novelty-workflow/README.md)
and [validation JSON](fuma-novelty-validation.json). Its scoped development JAR is
`build/literature-review/fuma-novelty-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`3549531160ee72e8688cb1df4f505a01eb366d1dfc0d62902415febc8bbea5fe`.
This is local development, with no published-release or deployment claim.

For real data, prepare unquoted UTF-8 TSV files with these required headers:

| Input | Required columns | Meaning |
| --- | --- | --- |
| Leads | `locus`, `trait`, `chromosome`, `position` | One lead per locus/trait pair; IDs are unique within each exact trait. |
| Known | `variant`, `trait`, `chromosome`, `position` | Every selected known association; repeated IDs/positions remain separate rows. |
| Coverage | `trait`, `chromosome`, `start`, `end` | One-based closed intervals declaring completeness of the selected supplied reference. |

Additional columns remain byte-exact in snapshots, including study/citation data.
They do not alter the coordinate rule. Blank/malformed rows, duplicate headers,
invalid UTF-8 and controls fail. Header-only known/coverage tables are allowed;
an empty lead population fails. Coordinates are integers in 1–2,147,483,647.
Build strings must match exactly; this is a declared identity check, not sequence
or coordinate validation against an assembly database. Chromosome and trait
strings are literal: `1` differs from `chr1`, and `X` differs from `23`.

```powershell
java -jar $jar fuma-tools --method locus-novelty --build GRCh37 --known-build GRCh37 --catalog-id my-catalog-snapshot --analysis-id my-leads --leads leads.tsv --known known.tsv --coverage coverage.tsv --radius 250000 --boundary inclusive --trait-policy exact --out build/novelty-NEW
```

Set `$jar` to the validated current candidate above. Required choices have no
inferred historical defaults:

- `--boundary inclusive` includes distance exactly equal to radius; `strict` excludes it.
- `--trait-policy exact` requires exact trait identity; `all-known` selects all supplied known traits.
- For `all-known`, coverage rows must use literal `*` as trait, asserting coverage of all selected traits.
- `--coverage none` supplies no completeness assertion; no empty-match window is called novel.

Overlapping or adjacent coverage intervals combine. A one-base gap prevents a
covered-absence claim. Integer query windows apply the selected boundary and clip
to the supported positive integer coordinate domain; no actual chromosome length
is inferred. Trait maps, resource selection, known-association significance and
catalog completeness must be resolved before input preparation.

`--novelty-options-file OPTIONS.json` accepts string-valued options; paths resolve
relative to that carrier. Direct options explicitly override carrier values.
The typed parent accepts independent `locus_novelty`, with the same string-valued
choices and inherited build. No automatic conversion of a `loci` stage's output
is implemented. Independent novelty can complete while another stage fails or
skips; require its own completed markers before any independent use and retain
the parent workflow's failed/partially-skipped status.

## Evidence and limits

`locus-novelty.tsv` retains every lead and its effective window, coverage flag,
known-match count, nearest distance and status. `known-matches.tsv` retains every
lead/known match, ordered by input lead then known coordinate/source row.
`known-rows.tsv` retains all known source-row dispositions; `coverage-rows.tsv`
retains all coverage assertions. Source row numbers are one-based data rows;
physical source lines include the header. `resource-ledger.tsv` records hashes
and byte counts of exact `source-inputs/` snapshots. `session.json` records
identities, policies, populations and measured work. Repeated associations are
not independent discoveries or allele matches.

Inputs are captured and parsed with bounded streaming reads; originals and
snapshots are hash-checked before and after analysis. Defaults bound each input
at 268,435,456 bytes, combined data rows at 1,000,000, input columns at 256, line
size at 1,048,576 bytes, input cells at 50,000,000, emitted matches at 2,000,000,
and measured operations at 100,000,000. Scientific files plus raw snapshots share
a 1,073,741,824-byte budget; the four scientific tables share a 5,000,000-row
budget. `--timeout 300` is cooperative. These are admission bounds, not OS memory
quotas. No population is silently truncated. Diagnostic metadata remains writable
when a scientific budget fails.

The reviewed [P123 paper](https://www.nature.com/articles/s41588-026-02741-5)
reports ±250 kb around the lead but its reviewed methods paragraph does not
specify the exact endpoint inequality, trait filter or catalog snapshot. This
implementation exposes those choices. It does not claim that a selected choice
reproduces historical FUMA 1.6.1 defaults. FUMA's exact candidate-position GWAS
Catalog annotation is a different operation.

Validation includes 16 native tests with an independent per-base coverage oracle
over 7,200 random lead windows per runtime, 27 CLI tests on Windows Java21/25 and
Linux21, a 1,258-test/92-suite combined gate, strict Java17 compilation and clean
Javadoc. All 1,258 exact-JAR tests pass on Windows21/25. Sixty-three actual
packaged main commands cover complete independent integer reference comparisons,
large/duplicate/edge populations, invalid inputs and independent parent failure/
skip behavior. Read the validation JSON for public recipe runs and retained
development failures. Broader FUMA remains missing; no new unique tool is counted
by this added workflow. Cell Ranger remains excluded.
