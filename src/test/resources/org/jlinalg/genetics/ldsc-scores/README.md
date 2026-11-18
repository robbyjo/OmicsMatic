# Original reference LD-score fixtures

`reference-fixtures.zip` contains 37 complete synthetic PLINK BED/BIM/FAM models,
explicit sample/SNP selections, coordinates/annotations and expected fields.
`generate_reference.py` is the exact generator executed with unchanged CBIIT
LDSC commit `6c673952cee74bd5c57aef1555a03b1c015399a0`, package edition 3.0.2.

The 75,226 checked fields combine original scores/filter indices/window lefts,
observed original chunk/normalization traces, summaries derived from that
filtered annotation population and independent raw-genotype audit expectations.
They are not all fields exposed by an upstream result object. The qualification
records their origin groups separately.

The fixed seed is 19870726. The generator uses real original BED decoding,
filtering, imputation, normalization and LD-score routines; it replaces no
original functions. A trace hook observes fixed source lines for chunk bounds
and means/SDs. The namespace loader bypasses the unrelated web initializer.

Cases include requested chunks 1/2/3/7/50/200, inclusive window boundaries,
duplicate map positions, gaps, whole windows, initial-clique fallback, missing
calls, monomorphic/heterozygous-only exclusions, strict MAF thresholds,
second-allele .05 boundary behavior, sample/SNP subsets and signed/empty
annotation columns. L036 has 4,099 input variants and 512 samples.

The original source is GPL-3.0. The Java implementation and generator use
GPL-3.0-or-later. Reference Python/NumPy/SciPy/pandas/bitarray versions and
original source hashes are recorded in the ZIP provenance and summary JSON.
Native analysis requires Java; the Python environment is reference development
infrastructure, retained separately with its unchanged file manifest.
