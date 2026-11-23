# Unfinished MR-PRESSO development checkpoint

Read [the complete handover](../HANDOVER_2026-11-23.md) first. This packet preserves
development progress and failures. **It grants no workflow qualification, catalog
admission, new family or paper-use credit.** The complete 250-paper goal remains
open. The canonical catalog remains at 152 scoped families and 1,037 pending uses.

From the exact development checkout, with Python 3.11+ and assertions enabled:

```powershell
python -B docs/literature/mrpresso-development-handover-20261123/verify_checkpoint.py
```

The standard-library reader checks the packet, 29 current source files, all 873
archived members, saved test counts, the old verifier invalidation and the new
scoped handover checks. It does not execute R/Java, rerun analyses, independently
reproduce numerical results or validate an arbitrary analysis capture. Hashes
check integrity, not identity, signature or authenticity. Run it at the recorded
Git checkpoint; later source changes must not be hidden by refreshing old hashes.

`STATUS.json` records 791 passing normal-build tests and 30 packaged handover
commands on Windows Java 21/25 and Linux Java 21, including eight expected FAIL2
refusals. These overlap the earlier regression suites and are not 791 independent
scientific comparisons. The original-R component scope is 25 calls: 20 finite
comparisons, four original errors and one aliased/unreportable return. The original
RNG references cover 12 profiles. The evidence archive keeps their generators,
typed JSON/RDS, source contexts, original errors, logs and exact test XML.

**Do not use the preliminary 7b9bedea... producer to establish capture validity.**
Its fixed absolute tolerance accepted coefficients/standard errors inflated one
million times when outcome units were tiny. Its witness and source remain intact.
The new 0e44e460... producer rejects that coherent resealed alteration on all
three JVMs, rejects false TSV projections, and performs valid RUN/VERIFY/REPLAY.
Full original-runtime and packaged qualification is still incomplete.

`helpers/` contains byte-preserved development scripts copied from the root
workspace's `build/` directory. Their recorded absolute paths target this machine
and the nested `build/fu-1` checkout. Several deliberately create output with
`mkdir()` and refuse an existing folder. **Inspect/adapt them before reuse**; do
not rerun prepare/install/generator scripts over old evidence. The old smoke/probe
helpers intentionally target the invalidated producer. They are retained as
reproduction context, not recommended analysis commands. No helper is executed by
the checkpoint reader.

The archive contains synthetic reference data and already public upstream source,
not full paper texts, private primary paragraphs/tables or cohort data. Runtime
binaries and the two 198 MB producer JARs remain at their recorded private paths.
Transfer those, the entire private `build/mrpresso-1` tree, the full primary cache,
private high-impact audit, previous evidence roots and the complete WSL base OS
before machine retirement. This packet is not a complete system backup.

Original and failed MR evidence, runtimes, worktrees and producers remain required.
Only the redundant packaging scratch copy of this packet's archive is eligible
for removal after byte-exact publication/readback. Its final cleanup receipt is
retained privately under `build/git-publish/20261123-mrpresso-handover-161/` in the
root workspace. Earlier exact cleanup receipts retain their own scope.
