# Warnings and required actions

**This is a native mathematical component, not a complete METAL workflow.**
Use it only for already aligned statistics for the same hypothesis in
independent cohorts, with explicitly declared positive effective sample sizes.
It supplies no allele/strand harmonization, overlap correction, genomic control,
effective-N estimation, beta/SE reconstruction or paper-specific pipeline.
Supply those prerequisites explicitly or do not run the dependent analysis.

**Invalid or unrepresentable model: no complete result.** The API refuses the
complete model rather than dropping cohorts or terms. Preserve original input
and the exception, correct the identified input/numerical/capacity issue, then
retry the whole intended population. Never repair identifiers, NaN/Inf, signs
or weights silently. Do not use a failed or partially prepared model as a
complete result. Library failures are exceptions, not CLI exit markers.

**P=0 input is not recoverable without additional trustworthy information.**
Supply a known finite natural log P to `fitLogPValues`, or leave that analysis
failed/skipped. Do not choose a P floor. Result P underflow is explicitly
flagged and retains finite natural log P. Input logs are natural logs, not
negative log10 values. A single cohort has no defined heterogeneity inference;
P/log P/I-squared are null.

**Original METAL can exit 0 with unusable results.** The retained boundary run
omits zero/negative P rows and emits nonfinite values for NaN input. Its status
is FAIL2: no complete result. Read raw consoles and output, correct invalid
inputs explicitly and rerun into new output. The unfinished original-runtime
adapter must enforce admission beyond process exit. Original zero-effect sign
inference and printed tiny weights must not be silently transferred into native
input declarations. The API requires an explicit direction of -1 or +1.

**Source-specific outputs differ.** Original METAL prints base-10 log P and
uses P=1 conventions for single-cohort/sufficiently small-Q heterogeneity.
The native API returns natural logs, null single-cohort inference and ordinary
chi-square tails for small Q. The retained small-Q native P is approximately
0.999887, while the original output is 1. Keep these differences visible;
do not relabel this component whole-tool source parity.

**Missing permitted runtime: dependent analysis skipped.** Native execution
requires Java 17+ and a matching current JAR. Install/supply that prerequisite
where the operating environment permits, or leave the analysis skipped and
state exactly what is missing. The saved-evidence developer audit additionally
needs Python 3.9+. Its SKIP3 means missing prerequisite; FAIL2 means no
qualification admitted. Retry audits with a NEW output directory. None of
these tools silently installs software.

**Private original runtime must not be redistributed.** Original METAL source
headers restrict redistribution. This qualification contains independently
written Java, synthetic controls and generated audit outputs; the private
source, archive, executable and original bundled examples stay local. This
native component itself has no METAL runtime dependency.

Use the exact qualified source/JAR and retain all historical hashes and matching
readers. A common version label does not prove matching source. The public
0.4.0 release predates this component. No biological validation, paper
replication, complete METAL implementation or new tool-family admission is
claimed. The typed original adapter and the 24 paper-role reviews remain open.
