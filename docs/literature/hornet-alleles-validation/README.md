# Hornet allele-component validation

**This milestone qualifies the native allele-sequence component only.** It does
not complete Hornet, standalone WASP, STARsolo's WASP mode or a paper recipe.
The 250-paper goal remains active, and canonical implementation/review counts
remain unchanged: 172 scoped families (34 native, 138 upstream), 1,825 reviewed
and 908 pending uses, with 251 labels still missing.

The [command documentation](../../hornet-alleles.md) explains input orientation,
coordinates, retained source quirks, capacities, failures and recovery.
[QUALIFICATION.json](QUALIFICATION.json) records source identities, producer,
scope, failures and validation totals. `source-context.zip` preserves the exact
owned source context, Apache licence/attribution and synthetic fixture. The
original upstream implementation, runtimes and raw process traces remain private.

Validation completed:

- 334 synthetic original Hornet cases: 332 successful sequence/counter cases and
  two original errors; three original reverse-complement cases.
- 341 native tests on each of Windows and Linux, with no skipped/failed tests.
- Nine packaged captures, nine complete replays and nine failure controls per OS.
  Controls include source errors, capacity failure, missing input, refusal to
  overwrite an existing capture, extra/missing/changed files and rejection of a
  changed result whose manifest/completion hashes were recomputed.
- Public original-oracle generator reproduced the entire fixture byte for byte.
- Original unmapped-flag asymmetry independently confirmed with the unmodified
  Hornet module.
- All 48 unmodified original WASP mapping tests passed in an isolated runtime.
  Seven NumPy deprecation warnings are retained. This validates that private
  original test environment; it does not qualify a project WASP adapter.

Verify the exact current source context:

```powershell
python -B tools/literature/verify_hornet_alleles.py
python -B tools/literature/verify_hornet_alleles.py --evidence build/wasp-896
```

The second command additionally checks all protected private files. Later code
or catalog changes must not refresh these saved seals. Restore this milestone's
archived code and the catalog at `parent_commit` for historical verification.
Older RFMix qualifications likewise retain their own source contexts and
producing JARs; this changed dispatch does not requalify their historical JARs.

The first Java build failed because a Java21 list method was used against the
project's Java17 target. It was corrected to the Java17-compatible method, and
the failed build context/logs are retained. Failed source intake and prerequisite
attempts are also retained explicitly in the qualification record.

See [HANDOVER.md](HANDOVER.md) for the remaining mapping work. Cleanup/publication
receipts are recorded separately after the feature commit; they do not rewrite
the scientific qualification.
