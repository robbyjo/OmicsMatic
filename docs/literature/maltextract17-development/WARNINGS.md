# Warnings and required actions

**Whole MaltExtract/HOPS and the paper's target-species workflow remain incomplete.**
This is an explicitly selected native read-projection component. Species-name
resolution, complete descendant selection, destacking, duplicate removal,
downsampling, original summary/damage statistics, manual candidate assessment
and the separate bacterial rescreen are outside this component.

**The complete-projection method changes stock filtering.** In one complete
control, stock settings retained22 mammoth reads and disabling removal/sampling
retained54. Declare the selected method and retain the original RMA and settings.
The authors' MaltExtract version, filters and resource choices are unreported;
do not label these controls as their historical environment or biological result.

**A stored parent group does not include descendant groups automatically.** Supply
the complete deliberate `taxon_id` list. No ID, name, sequence, tree or population
is silently repaired, inferred, expanded, omitted or replaced.

The original1.7 source loop can stop after10,000 assigned reads per group while
its downsampling-warning flag is triggered only above100,000. This is a binary
source-inspection finding, not a large-population execution claim. This native
method disables downsampling and refuses declared capacity overruns instead of
taking a prefix. Retain complete population counts in downstream reports.

Source-specific behaviour is preserved: float division against the first match,
the ancient filter's ten-match cap, case-sensitive terminal lesions and length
gate, source name-plus-sequence read keys and the unusual complexity loop.
For example, the source complexity function returns1 for lengths16..32 even for
homopolymers; it is not a corrected sliding-window entropy measure. Some longer
inputs cause a source factorial-index failure. Such a failure admits no result;
do not silently replace the measure or repair the sequence.

Original zero-data FASTA files can contain the literal marker `None` or be empty.
Those original files remain unchanged in the evidence. Do not send a `None`
marker to an aligner as a sequence. Native projected FASTA is empty when no
records survive. Zero extracted reads do not establish biological absence or
absence among queries that MALT did not store in the RMA.

**SKIP3: required reader/runtime unavailable.** The dependent extraction is skipped.
Obtain the exact GPL HOPS0.35/MaltExtract1.7 provider using the documented setup
command only where installation/download is permitted; use a permitted JDK21
or JDK25 and a NEW output directory. If installation is unavailable or forbidden,
keep the dependent analysis skipped. Do not substitute a different version.

**FAIL2: invalid, incomplete, ambiguous, changed or over-capacity data. No result
is admitted.** Retain `FAILURE.json`, `WARNINGS_FAILURE.md`, complete inputs and
all partial outputs. Resolve the named issue, deliberately provide a new input
version with provenance when correction is appropriate, and retry a NEW output
directory. Existing outputs are preserved. No historical or failed evidence is
deleted or overwritten.

Saved replay proves consistency of complete captures and current recomputation.
It does not authenticate a producer, repeat original fitting, establish whole-tool
equivalence or support a pathogen-authenticity conclusion.
