# Synthetic original-source oracles

`original-oracle.json.gz` contains six complete original WASP/BWA workflows on
project-owned synthetic chromosomes and reads: single/paired text variants,
single/paired HDF5 sample selection, monomorphic selected samples, and seeded
original paired duplicate removal. Eight BAM stages retain complete scientific
SAM fields. Only headers were normalized to their sequence dictionaries for
portable native tests; raw process headers and traces remain private.
Expected filter counters came from original stderr, independently of Java.

`original-converter-oracle.json.gz` contains two synthetic VCF inputs and complete
fixed-type populations read from original C `snp2h5` output: 18 variants and a
separate mixed-GT/case control. It includes fully and partially missing GT,
unphased calls, a nonleading GT FORMAT field, lowercase alleles and a non-PASS
FILTER row. The original missing-call phase flag is retained.

Both fixtures name the exact original WASP revision. Expected results were
produced by the original upstream programs and read directly from their outputs,
without the project's Java implementation. No author datasets, original
software, article text or raw process traces are distributed. Unit tests exercise
the source rules and refusals; full packaged execution, fresh replay and
independent original workflow comparisons are separate private qualification.
