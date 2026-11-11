# Salmon integration development

**Salmon remains missing in the tool catalog.** The current source includes a
native Java streaming decoder for complete original bootstrap counts. A typed
public quantification command, managed runtime admission and complete index/
reference/read/output audits remain unfinished. Do not use the development
controls as an admission ticket for a user analysis.

Eight reviewed paper uses distinguish Salmon **1.0.0, 1.1.0, 1.5.2, 1.8.0 and
1.9.0**, and preserve three papers' unstated editions. P108 uses separate 1.5.2
GTEx and 1.8.0 brain panels, decoy-aware references, bias correction and 50
bootstraps. The captured author module linked by P227 specifies paired ISR,
sequence/GC/position bias, variational optimization and 100 bootstraps; its branch
snapshot does not authenticate every historical run or external index/conda
environment. P190 within-gene percentages and the P227 ambiguous denominator
remain distinct. P226's stated TPM-to-DESeq2 input remains unresolved: TPM is
relative abundance, not raw integer counts. Do not silently substitute units.

## Native inferential-count decoder

`org.jlinalg.sequence.SalmonInferentialReplicates.read` accepts complete original
`names.tsv.gz` and `bootstraps.gz` streams, an exact ordered transcript population,
the declared positive replicate count, an explicit whole-population value bound,
a staged consumer and a cancellation callback. The fixed admitted encoding is
original Linux x86_64 sample-major little-endian IEEE754 binary64. Do not guess
the producer architecture or derive a smaller sample count from truncated data.

The decoder checks every exact UTF-8 identity and value, both full gzip members,
CRC/size, and compressed/decompressed end of file. It rejects duplicate names,
wrong order, extra members/trailing garbage, truncation, nonfinite/negative values
and capacity/cancellation failures. Original signed zero is retained and reported.
The complete receipt is returned only after all checks pass. Consumer callbacks
can precede a later error: **stage their output and admit it only after successful
return**. These are estimated fragment counts, not observed integer reads. This
decoder does not run mapping, an optimizer, bias fitting or uncertainty calibration.

The source provenance and ABI are documented in
`src/main/resources/META-INF/NOTICE-salmon.txt`. Current development evidence is
in `salmon-native-development/VALIDATION.json`; read its precise platform and
scope conditions. The canonical executable was preserved. No public release or
deployment is claimed.

## Original-engine controls and retained failures

Five unmodified official releases passed startup/help checks. Twenty-five
original quantifications on one synthetic 40-target population passed across
the five editions: paired EM with sequence/GC/position bias and 50 bootstraps;
paired VB with the same biases and 100 bootstraps; single-end EM with supplied
fragment-length parameters and five bootstraps; paired VB without replicates;
and transcriptome-alignment EM with three bootstraps. All 1,000 quantification
rows are retained. The native decoder matches every one of the 31,600 original
bootstrap values bit for bit on Windows Java 21/25 and Linux Java 21. All 27
focused integrity/capacity/cancellation tests pass on each JVM without skips.
Every prior packaged Java class is byte-identical in this additive candidate.

**Observed original process failure:** the four-target reference with position
bias enabled crashed in four tested editions (1.0.0, 1.1.0, 1.5.2 and 1.8.0).
The 1.8.0 failure persists at 8/32 GiB address space and one/two threads; it also
occurs without bootstrap sampling. Omitting position bias succeeds on that
population. The 40-target control succeeds with position bias enabled and the
same scientific options. The exact root cause is unproven; original positional
model serialization is a source-level lead, not a confirmed stack diagnosis.
The optional signal tracer was skipped because Linux GCC is absent. That compiler
is not a dependency of the decoder or the original runtime. All original failed
outputs and process receipts are retained; none is an admitted result.

Synthetic inputs remain below the original five-million-fragment burn-in
threshold. Original warnings and observed-mapping effective-length fallback
remain in the raw logs. These controls reuse tiny artificial data and do not
certify biological accuracy, model calibration, paper reconstruction or behavior
at production scale.

## Warnings and next required actions

**Skip dependent project analysis until a complete typed quantification workflow
and managed runtime admission pass.** Runtime startup and native parsing alone
are insufficient. Execution will require a permitted exact Linux runtime,
declared complete references/decoys/reads, explicit library and inference choices,
complete output auditing and retained upstream warnings.

**SKIP3:** when an exact runtime, required resource or capability is unavailable
or forbidden, skip the affected analysis, name the missing dependency and explain
the permitted setup action. Download/install only where the operating environment
allows it. **FAIL2:** invalid input, changed evidence, upstream process failure,
numerical/capacity/cancellation or finalization failure admits no result. Inspect
the original command, stderr, metadata and complete file family; preserve partials
and use NEW paths for corrected requests. A partially written `quant.sf` or a
valid bootstrap prefix is not successful quantification.

Current development scripts use new owned Linux `/var/tmp` reference areas;
an earlier `/tmp` area disappeared between commands and no quantification ran
from that missing state. A terminal failure is retained before a fresh attempt.
Hashes prove integrity, not authenticity. Component dependencies, downstream
aggregation/differential models and unknown paper settings remain separate.
Cell Ranger remains excluded at the user's request.
