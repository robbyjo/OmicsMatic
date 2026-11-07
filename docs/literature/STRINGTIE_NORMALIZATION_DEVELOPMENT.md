# StringTie development: original-edition finalization and normalization checks

**StringTie is still an unfinished public integration.** This checkpoint adds a
native Java finalizer and transcript normalization-consistency audit. It does not
add a `stringtie-tools` command, assemble alignments, fit abundance, recover exact
hidden denominators or certify biological accuracy. Catalog totals remain 67 new
scoped integrations (70 including three existing adapters), with 260 tools
missing and 819 reviewed uses from 250 papers. Cell Ranger remains excluded.

The new API is
`src/main/java/org/jlinalg/sequence/StringTieNormalization.java`. The exact
development artifact and validation evidence belong to
`stringtie-normalization-development/VALIDATION.json`. The preceding
[GTF](STRINGTIE_DEVELOPMENT.md) and
[gene/Ballgown table](STRINGTIE_TABLES_DEVELOPMENT.md) sources remain unchanged.
The canonical/released JAR is unchanged and lacks the new normalization class.
This checkpoint is local, unreleased, unpushed and undeployed.

## Warnings and required actions

**A compatible denominator range is not the recovered true denominator.** The
native audit checks whether all emitted transcript values could share a source
normalization denominator after the selected edition's arithmetic and original
six-decimal reporting. It returns conservative compatibility bounds, not an
exact fit, confidence interval or biological result. A coherently changed factor
can remain compatible. Independently validate the actual original alignment,
reference population and denominators before making an inference claim.

**Zero output may be unresolved, not observed absence.**
`NO_POSITIVE_NUMERATOR` means no positive source numerator constrains that
denominator. `ALL_ZERO_UNRESOLVED` means positive input numerators coexist with
all-zero printed output; rounding or a guarded zero branch may explain it. Its
bounds apply only conditionally to the active branch. Preserve these states and
skip dependent interpretations that require an identified normalization factor.
Do not infer an exact denominator or biological zero from either state.

**Nonfinite source arithmetic is never silently replaced with zero.** The pure
finalizer retains finite, positive/negative infinity and NaN classes. Nonfinite
values have no fabricated finite `fixed6` string. Old unguarded division can
produce NaN/infinity where a later edition's source guard returns zero. Inspect
all value classes and the exact edition; skip dependent finite-valued analysis
if a required quantity is nonfinite. NaN payload/sign and C-library spelling are
not claimed to be portable.

**Edition and rounding matter.** Exact supported editions are 1.3.0, 1.3.6,
2.1.2, 2.1.7, 2.2.1, 3.0.0 and 3.0.3. The finalizer uses double precision in
1.3.6 and 3.0.3 and float precision in the other captured editions. Float
intermediate products, parse rounding, negative-coverage clamping, signed zero
and source guard thresholds are retained. Never select a newer edition for an
unreported paper run without explicit evidence. P110's separate TPM run remains
unversioned; its assembly edition is not automatically its TPM edition.

**Do not double or renormalize already-rounded values and call them source
output.** Repeated decimal rounding can destroy compatibility with a single
factor. The first synthetic scaling test made that invalid assumption and is
retained as a failed test attempt. Its replacement uses values from the original
C++ expression controls at one explicit alternative denominator. The numerical
consistency check was not weakened. Original TPM above one million remains
unchanged; it is not forced to a desired sum.

**Failures admit no normalization audit.** A changed reference identity,
internal exon geometry, emission population/order, GTF-to-Ballgown coverage
readback, finalized FPKM, original output precision or incompatible common
factor causes an exception. Cancellation also propagates. Preserve original
inputs, outputs, ledgers and logs, read the named transcript/stage, correct the
cause and retry into NEW paths. Validator failures retain `FAILURE.json`,
`INCOMPLETE.json` and process output; require a complete successful receipt.

**These Java checks require no StringTie installation.** Running original
StringTie still requires an exact permitted runtime. If access or installation
is forbidden or a required dependency is unavailable, skip dependent analysis,
name the unavailable requirement and provide the exact permitted recovery step.
Do not substitute a different edition. The retained private Linux originals
and arithmetic controls do not establish admission for an arbitrary host.

## Native finalizer

`finalizeCoverage` accepts an exact edition, a bounded original fixed-point
coverage token and explicit finite nonnegative `Frag_Len`/`Cov_Sum` denominator
values. It performs the extracted original parse, clamp, multiplication,
division, final cast and finite six-decimal reporting. Source-expression
negative-input controls are allowed; this does not make negative coverage an
admitted scientific GTF result. Overflow and zero denominators remain visible.
Finite formatting rounds the exact binary value to nearest-even, preserving a
negative zero sign when the source arithmetic retains it.

For example, with deliberately supplied **synthetic test denominators**:

```java
var result = StringTieNormalization.finalizeCoverage(
    StringTieNormalization.Edition.V3_0_3,
    "21.337809", 28000.0, 98.399105);
// Inspect readbackCoverage(), fpkm() and tpm().numberClass() before using fixed6().
// These supplied denominators are test inputs, not estimates recovered from data.
```

## Captured-output consistency audit

Call `auditEstimation` with an exact edition, a complete native ESTIMATION GTF,
the complete checked `StringTieTables.Audit`, and a cancellation checkpoint. It
rebinds every emitted GTF identity, gene, chromosome, strand, span, exon count,
exonic length and internal exon geometry to the checked table topology. Original
emission order and every explicit omitted identity remain checked.

The selected source parse/clamp of the GTF coverage must reproduce the Ballgown
coverage field. GTF and Ballgown finalized FPKM values must agree. Every source
FPKM/TPM token must have original six-decimal precision or the explicit literal
zero shortcut; missing values are not inserted.

For positive numerators, the checker intersects denominator constraints over the
complete emitted transcript family. These account conservatively for decimal
reporting, the source float/double final cast and binary64 division. Decimal
division uses directed 80-digit bounds. A guarded active branch additionally
requires the original threshold condition (`Frag_Len > 0.001` and
`Cov_Sum > 0.00001` where those guards exist); the stored threshold and interval
must both be considered. It does not use a one-million sum as a replacement for
the source arithmetic. Nonfinite numerators cannot support a finite audit and
produce a named error rather than invented bounds.

Gene-reduction residuals from the table audit remain diagnostics. This step does
not certify gene coverage, gene abundance inference, read counts, exon coverage
moments or a complete paper pipeline.

## Evidence and remaining implementation

The arithmetic fixture contains 1,008 cases across seven editions, generated
from **unmodified extracted source expressions** in an explicitly compiled
C++/round-to-nearest harness. Cases include float/double readback, clamping,
negative zero, guard thresholds, rounding boundaries, subnormal denominators,
large products and nonfinite results. These are not 1,008 whole-StringTie fits.
All original expressions, source pins, harnesses, outputs and edition-specific
licenses are retained. No third-party executable is bundled in the fixtures.

The complete Java suite has 1,256 checks: 1,065 normalization checks plus the
unchanged 191 GTF/table checks. It compares all arithmetic controls and all 35
previously captured whole-tool table families, along with tampered factors,
changed geometry, explicit omissions, unresolved zeros and cancellation.
Exact Java 17 classes, packaged execution on three JVMs, Javadoc and artifact
integrity are recorded in `VALIDATION.json`. No new whole-StringTie run is claimed
by this arithmetic phase.

Remaining public integration work includes independent alignment/count/coverage
and gene-inference checks where supportable, exact original runtime setup and
host admission, typed assembly/estimation/merge workflows, complete portable
bundle verification, paper-use curation and catalog/browser gates. StringTie
remains missing until the required public workflow gates pass.

Official references: [StringTie source releases](https://github.com/gpertea/stringtie/releases),
[manual](https://www.ccb.jhu.edu/software/stringtie/index.shtml?t=manual).
