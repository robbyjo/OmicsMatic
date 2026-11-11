# STREME enrichment component and historical discovery references

**WARNINGS: motif discovery execution remains unadmitted. No additional catalog
tool is credited by this checkpoint.** The new Java code independently checks
statistical fields; original reference runs supply evidence for the next discovery
workflow stage. The catalog remains at 72 new scoped integrations, 75 including
three existing adapters, with 256 named tools missing.

## Available Java code

`org.jlinalg.sequence.StremeEnrichment.fisher` computes the complete inclusive
fixed-margin right tail with exact integer arithmetic. It returns a reduced
fraction, a binary64 view, a finite log10 view and work counts. It preserves the
exact fraction when binary64 underflows. There is no pseudocount, threshold
selection or automatic multiplicity correction. Explicit population, tail-term,
integer-bit and arithmetic capacities refuse the whole calculation when exceeded;
the supplied cancellation callback runs inside arithmetic loops. Family callers
must also bound cumulative work. These logical bounds are not heap or CPU quotas.

`org.jlinalg.sequence.StremeFisherAudit.read` accepts explicitly declared original
5.5.4 or 5.5.9 STREME differential Fisher XML. It retains the complete raw XML and
every original motif, recalculates all training/test Fisher tables, and checks
P/log10 fields plus the final reported-family E-value multiplier at the source
printed precision. It checks input identity again after arithmetic. Producer paths
stay inert text. Binomial and central-distance objectives produce a named
unsupported-input exception; they are never relabelled as Fisher tests.

**Interpretation:** training probabilities are selection scores. A declared
holdout does not prove partition independence. Without a holdout, the original
empty test-table P=1 and E=reported motif count are sentinels, not validated
significance. Arithmetic agreement does not establish a correct motif matrix,
site score, threshold search, sequence partition, discovery fit, complete output
family or authentic execution.

The [runnable example](../../examples/streme-enrichment/README.md) shows prepared
synthetic counts, complete XML statistical audits, warnings, SKIP3 and FAIL2.
Its source is at `examples/streme-enrichment/Example.java`.

## Validation and artifact

The separate artifact is
`build/literature-review/meme-runtime/streme-enrichment-build-1/omicsmatic-0.3.7-streme-enrichment-development.jar`.
It adds ten classes to the final HTSeq development artifact and preserves every
parent archive entry byte for byte. The canonical release JAR is unchanged.

All **2,220 checks passed on each of Windows Java 21, Windows Java 25 and Linux
Java 21**, with no test skips. They include 2,182 independent Python
`math.comb`/`Fraction` tables, a complete small-table grid, uneven populations,
degenerate margins, near-one tails and underflow. Eight complete original Fisher
XML documents from both suite editions supply 28 independently checked original
training/test tables; four original binomial/central-distance documents establish
unsupported boundaries. Corrupted counts, probabilities, family sizes and logs,
resource limits, cancellation and an input mutation during arithmetic are checked.
These counts describe mathematical and synthetic controls, not biological samples.

The example passed six successful demonstrations and three named SKIP3 cases
across the same JVMs. An earlier example verifier stopped on a Windows stderr
encoding mismatch. Its source/logs are retained; the corrected example emits
explicit UTF-8, and that incomplete attempt contributes no validation credit.

The compact [validation record](streme-enrichment-development/VALIDATION.json)
and evidence ZIP retain the scientific source, fixtures, runner receipts,
development artifact entry hashes, original discovery outputs and failures.
The evidence proves recorded integrity and scope, not execution authentication.
No release, push or deployment is performed by this checkpoint.

## Original edition-specific references

The official [MEME release index](https://meme-suite.org/meme/meme-software/)
supplied separate archives for 5.5.4, unpatched 4.9.1 and patched 4.9.1_2. The
archive pins and full build/preparation receipts are retained. User-confirmed
educational/research/nonprofit use applies to these external Suite references;
do not infer commercial permission.

5.5.4 built successfully with unchanged original sources. The two 4.9.1 builds
needed the legacy GCC common-symbol and GNU inline linkage settings, supplied as
explicit make variables. The old configure script ignores environment CFLAGS;
the failed attempts demonstrating that behavior remain retained. No original C/H
scientific source was edited: all 646 original C/H files in each 4.9.1 edition
match their archive source pins. Original launchers were preserved.

The original 4.9.1 MEME launcher also needs `/bin/csh`; MEME-ChIP needs Perl
`XML::Simple`. Explicit permitted preparation installed tcsh and XML::Simple with
four package dependencies, with no upgrades or removals. Original launchers and
their edition probes then passed. These steps are development preparation, never
an implicit analysis-time installation.

The final reference collections have **46 process calls: 42 exit0 and four
retained original failures**. They cover the newer MEME OOPS/ZOOPS/ANR models on
forward/both strands, supplied/shuffled STREME controls, no holdout, unequal-length
binomial controls and central-distance controls, plus both original 4.9.1 editions'
MEME, DREME and historical-option MEME-ChIP calls. Earlier unsuccessful legacy
collector calls remain separately retained and excluded from these final counts.

**Original warnings remain unresolved scientific gates.** Both old MEME-ChIP
pipelines emit `XML::Simple` missing-key warnings; the unpatched edition also emits
`JsonWr` uninitialized-value warnings. Their exit0 statuses do not admit the
combined report. Inspect the captured stderr, progress logs, nested messages,
motif/site outputs and report carriers before admitting a pipeline. Do not patch
away the original warning or infer valid metadata from a generated HTML file.

Two intentionally unsuccessful modern commands are preserved per edition: the
collector tried unsupported STREME `--norc`, and an invalid-width case failed.
The historically named `streme-no-motifs` case actually reported one motif despite
the stringent requested threshold. It is a tight-threshold control, **not an
empty-result fixture**. No empty-result behavior is claimed.

Original 4.9.1 MEME has no `-seed` option. Failed probes demonstrate this; corrected
controls use its declared upstream defaults rather than inventing a newer-edition
seed mapping. P155 states 4.9.1 without a patch level. P013 leaves the Suite edition
unstated. Its quoted historical option names run in these controls, but the
synthetic inputs are 80 bp, not the paper's 200 bp peaks. None of this establishes
the author environment, paper data, a reproduced paper or historical numerical
equivalence. P235's stated 5.5.4 is retained as its own edition boundary.

## Required action on an unavailable runtime or failed analysis

**SKIP3 — skip the affected analysis** when a dependency or execution capability
is unavailable or forbidden. Report the specific missing original edition,
launcher, shell, Perl module or report helper. Supply that exact runtime and its
dependencies on a permitted Linux host, or leave the analysis skipped. The native
Java statistical component itself requires no Suite installation.

**FAIL2 — admit no result** for a failed process, malformed/changed/inconsistent
input, incomplete output, capacity refusal or failed verification. Preserve the
whole attempt and original logs, correct the named cause, and retry in a **new**
output directory. No nonzero original call or incomplete report is a successful
scientific analysis. The full discovery/site/score/runtime/output-family workflow
and catalog credit remain pending.
