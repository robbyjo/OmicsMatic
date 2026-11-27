# Original Hornet component controls

`oracle.json` contains project-owned synthetic inputs and unmodified original
Hornet function results, not article text or copied implementation files.
The exact original source hash, revision, Python/Pysam versions and seed are
inside the fixture. There are 334 cases: 332 successes and two original errors.
Three reverse-complement controls are included separately.

Regenerate with a permitted private original runtime that supplies Pysam0.21.0:

```sh
python -B tools/literature/hornet_alleles_oracles.py \
  --original-file /absolute/Hornet/mapping/find_intersecting_snps.py \
  --out /absolute/new-oracle-directory
```

The generator verifies the original file identity before import. It downloads
nothing and refuses an existing output directory. Its 2026-11-27 private runtime
replay reproduced the complete fixture byte for byte. A different Python version
can change the recorded version string; compare all cases and source identity
explicitly before replacing any fixture. Do not regenerate expected answers from
the Java implementation.

The source's phased-single-read error and Pysam padding error are negative
controls. The native component rejects padded CIGAR explicitly rather than
silently substituting a different padding convention. This fixture does not
qualify BAM/FASTQ preparation, mapper execution or remapped-read filtering.
