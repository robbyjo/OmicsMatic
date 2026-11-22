# Frozen synthetic FUMA locus references

`reference-cases.json` contains 43 explicit synthetic input protocols, unchanged
original function output and independent interval-graph component expectations.
The four defective original locus partitions remain retained evidence; they are
not valid native golden partitions. Input parser and coordinate-tie differences
are explicit in the checker. No paper/individual-level data is included.

From the repository root:

```text
python tools/literature/verify_fuma_loci.py --jar JAR --java JAVA --out NEW_DIRECTORY
```

Use the development JAR named in `fuma-locus-validation.json`. The verifier needs
only Python 3 standard library and compatible Java; no original FUMA runtime,
network access or installer is used. The output must be new. Missing Java skips
verification with exit 3 and required recovery; failed comparisons return 2.
Read `verification.json`, per-case stderr and retained result WARNINGS.md.

Fixture hashes/source identity are in `provenance.json`. Regeneration requires
the pinned original functions and independently prepared indexed references;
do not replace source or graph expectations with native output snapshots.
