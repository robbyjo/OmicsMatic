# Native MEME discovery site, matrix and diagram checks

**WARNINGS — this is a validated Java component, not a completed MEME execution
adapter. No new catalog tool is credited.** The catalog still has 72 new scoped
integrations, 75 including existing adapters, and 256 missing named tools. Motif
discovery execution, motif-significance derivation, full report-family auditing
and a public execution command remain open.

## What is available

`org.jlinalg.sequence.MemeDiscoverySites.read` checks a complete original classic
DNA discovery XML against **independently supplied** inputs, sequence weights,
resolved background, edition, occurrence model, strand choice, widths, motif
limit, prior strength and declared source seed. It preserves the complete raw
XML and never opens producer paths. It checks the original XML identity again after
arithmetic.

For each original motif it checks every contributing word and its oriented
ten-base flanks against the original zero-based input coordinates. It enforces
OOPS, ZOOPS and ANR occurrence/nonoverlap constraints, reconstructs weighted
position counts and all printed probability cells, and reconstructs the
Dirichlet-smoothed centibit matrix. It recalculates every contributing-site
position P value and every complete input-order sequence diagram, including all
selected hit identities, strands, coordinates and P values.

`org.jlinalg.sequence.MemeDiscoveryScoring` provides the underlying immutable
score model and ordered nonoverlap tiling API. It exposes the complete integer
matrix, complete null-tail population and work counts, with defensive array/list
ownership and cancellation checks. Explicit model, sequence, hit, workspace and
operation limits refuse complete calculations; they never truncate a population.
Workspace bounds are conservative logical estimates, not heap or OS quotas.

The [runnable example](../../examples/meme-discovery-sites/README.md) performs a
portable audit using exact retained synthetic FASTA/XML and independently
recorded settings. It needs Java and the checked fixture, not an installed MEME
runtime. The example's small fixture reader is not a production FASTA parser.

## Source conventions that affect interpretation

Discovery-site scoring uses the original **100-point** per-column lattice,
fourteen-significant-digit scale, and independently rounded three-digit
background without renormalization. This is different from the FIMO native
scanner's 1000-point convention. The background supplied to the API must already
be resolved for the original strand handling; it is not recovered from rounded
XML output.

With both strands enabled, a contributing site's P value uses the **better
scoring orientation**, which need not be its EM-assigned contributing strand.
The position P value is not doubled. Plus wins score ties. Diagram candidates
require P strictly below 1e-4; later candidates replace overlapping earlier hits
only when their P is strictly smaller than the product of overlapping P values.
Original motif/coordinate priority remains meaningful.

The sequence statistic uses the best position score of **every scorable motif**,
including motifs with no displayed hit. It uses the original small-P
extreme-value approximation and product-of-uniforms calculation. Positive
factors that underflow to binary64 zero remain zero with an explicit flag;
no log-space substitute silently changes the original statistic. Rounded
background mass and other source numerical conventions are preserved.

## Validation and artifact

**94 checks passed without test skips on each of Windows Java 21, Windows Java
25 and Linux Java 21.** The tested population comprises 42 complete original
discovery outputs from 4.9.1 base, 4.9.1 patch 2, 5.5.4 and 5.5.9:

- 84 original motifs and 4,136 contributing sites;
- 3,040 complete input-order sequence diagrams and 892 selected scan hits;
- every cell in the complete 64,284-entry null-tail population;
- OOPS/ZOOPS/ANR, both/forward strands, asymmetric backgrounds, varied prior
  strengths, weighted sequences, variable lengths, edge flanks and mixed widths;
- 41 scientific mutations, including missing/duplicated populations, incorrect
  coordinates/words/weights, altered matrix/score cells, invalid probabilities,
  altered settings and changed scan diagrams;
- capacity/cancellation checks, a same-length/same-timestamp input mutation during
  arithmetic, source tie ordering, zero information, short-sequence semantics and
  explicitly retained product underflow.

The reference oracle independently enumerates complete half-width words with
**integer probability masses**, combines the resulting polynomials, and obtains
exact rational null tails. It does not use the Java sequential floating-point
convolution. The fixture freezer independently rereads the original raw
FASTA/WEIGHTS/background files and checks their exact relation to the prepared
records. Those raw files, all original XML and the independent results are
retained. These are mathematical/synthetic controls, not biological validation.

The separate artifact is
`build/literature-review/meme-runtime/discovery-sites-build-2/omicsmatic-0.3.7-meme-discovery-sites-development.jar`.
Its SHA-256 is
`7e2f7e2c7464e0173a7419e26fec420debbf350fe177df673e42fc6231fb4594`.
It adds 19 exact-source classes and preserves every prior STREME development
artifact entry's bytes. The canonical release JAR remains unchanged.

The example passed on all three JVMs, checking 53 contributing sites, 60 diagrams
and 55 selected hits per run. Three existing-output refusals returned FAIL2 and
preserved all earlier output bytes. A first development build failed to compile
the test harness because of a generic map type; its source, compiler log and
failure receipt remain retained and contribute no scientific validation credit.

The [validation record](meme-discovery-sites-development/VALIDATION.json), evidence
ZIP and `tools/literature/meme_discovery_sites_evidence.py` retain and verify this
scope. No release, GitHub push or deployment occurs at this checkpoint.

## Remaining boundaries and required actions

**This component does not fit EM or independently derive motif significance.**
Original E/P significance attributes stay literal in the raw motif. A dedicated
test confirms that changing an opaque motif E value does not become an
independent significance validation. The expected object binds the choices
listed above; it does not bind every possible command-line setting. A complete
execution workflow must separately bind its full typed request and original
command to the captured inputs, runtime, process and complete output family.

The prepared-input scope is uppercase canonical DNA with positive weights, a
positive resolved order-zero background, classic objective and positive
Dirichlet prior. Aliases, ambiguities, source filtering, omitted brief output,
other objectives/priors and empty motif populations require their own source
audits; none is silently normalized or inferred. Legacy 4.9.1 has no CLI `-seed`
option; its XML version does not establish its patch level or author identity.

The two legacy MEME-ChIP MEME subruns are included in the numerical/site audit.
**This does not close their combined-report warnings:** both original pipelines
emit XML::Simple missing-key warnings, and the base edition also emits JsonWr
uninitialized-value warnings. Their combined report metadata and full output
families remain unadmitted. Retain and inspect the original warnings and nested
outputs; do not infer a valid pipeline from exit0 or this component receipt.

**SKIP3 — skip the affected analysis** if an original runtime/dependency cannot
be installed or executed in the operating environment. Name the missing edition,
shell, Perl module, report helper or execution capability, and supply it only on
a permitted host. Otherwise leave that analysis skipped. The Java component
itself needs no external installation. Unsupported prepared-input behavior also
requires a matching audit rather than a silent input or edition replacement.

**FAIL2 — admit no result** for failed processes, changed/malformed/inconsistent
inputs, incomplete populations, capacity refusals or verification failures.
Preserve the entire attempt, inspect the named source/process/verification log,
correct the cause and retry into a **new** directory. Hashes establish integrity,
not authentic execution, a reproduced paper or biological validity. The full
250-paper implementation goal remains active.
