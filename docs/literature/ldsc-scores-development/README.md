# Native reference LD-score preparation component

The [Java API and runnable SDK example](../../vignettes/ldsc-ld-scores.md) supply
hard-call reference LD scores, explicit source filtering/chunk semantics,
full/common annotation sums and binary overlap matrices. Read
[WARNINGS.md](WARNINGS.md) before using or interpreting results.

- **37 original BED-based models**, including real original decoding, filtering,
  imputation, normalization and chunked score accumulation.
- **75,226 checked reference fields**: original outputs/traces, summaries from
  the original filtered population and independent raw-call audit expectations.
  `REFERENCE-ORIGINS.json` separates those groups; they are not all upstream
  result-object fields or additional independent models.
- **Nine tests against the packaged library on Windows and the same nine on
  Linux**, with no failures/skips. All 37 complete model payloads match byte for
  byte between platforms. Every previous workflow JAR member is unchanged.
- L036 has **4,099 input variants and 512 samples**. Its native counter records
  a peak cache of **51,200 normalized genotype cells** and 286,465 unique
  correlations, rather than retaining the full normalized genotype matrix.
  The gapped synthetic map deliberately exercises chunk expansion; its
  beyond-window pairs are retained and warned about.
- The unmodified upstream L2 entry point ran on L018. The public Java example
  matches its 42 variant IDs, full/common M summaries and scores within the
  upstream CLI's three-decimal printing precision. Native scores additionally
  match the full-precision original core reference. L018 is already one of the
  37 models, rather than a 38th independent original model.

`VALIDATION.json` binds current scientific sources, artifact and `checks.zip`.
The archive contains full original references and synthetic inputs, source
snapshots, private-runtime manifests/setup records, Windows/Linux tests and
payloads, upstream CLI execution, the public example comparison and current
catalog/workflow preservation checks. `ENTRY-MANIFEST.json` indexes every
payload; every payload is read back. The retained verifier rechecks source and
archive integrity without rerunning the scientific runtime:

```powershell
python -B tools/literature/ldsc_scores/verify.py --out build/ldscore-api-recheck-1
```

The selected source is CBIIT LDSC commit
`6c673952cee74bd5c57aef1555a03b1c015399a0`. Its complete extraction is checked
against the saved commit archive. Source functions are unchanged; namespace
loading bypasses the unrelated web initializer and a trace hook only observes
chunk/normalization state. The original L2 command uses its real gzip program.

The private Python 3.10.11 / NumPy 1.23.3 / SciPy 1.9.2 / pandas 1.5.0 runtime
was copied for this reference. The original 4,424-file runtime remains unchanged.
The new runtime adds the selected upstream requirement
[bitarray 2.6.0, under the PSF license](https://pypi.org/project/bitarray/2.6.0/),
verified against its official wheel digest; all 435 package tests pass. Its
4,441 installed files are pinned and preserved. These are development/runtime
provenance facts, not a claim that every optional LDSC package dependency or
unrelated subcommand is installed or qualified.

No historical fingerprints are refreshed. The current catalog remains 128 new
scoped integrations, including one LDSC family; this method adds no second
family credit. Captured LD-score/annotation preparation workflows and remaining
source-specific LDSC methods remain open. No website deployment or release
occurs in this component qualification.
