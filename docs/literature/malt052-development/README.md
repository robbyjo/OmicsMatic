# MALT0.5.2 alignment evidence: current replay status

The complete portable replay now passes on **Windows Java21, Windows Java25 and
Linux Java21**. Each run checks all four captured populations, all16,862 queries,
122 alignments, every raw RMA field against the captured original-API oracles,
12,275 source assignment cases and the complete2,259,893-node effective taxonomy.
See [PORTABLE_REPLAYS.json](PORTABLE_REPLAYS.json) and
[PORTABLE_VALIDATION.json](PORTABLE_VALIDATION.json).

**The original accession-map path remains unqualified.** Whole MALT/HOPS/MaltExtract
and paper reproduction remain incomplete; this checkpoint adds no whole-tool
credit. A saved replay checks complete software-profile evidence, without
refitting original alignment or authenticating a producer.

The separate [owned seed-build component](../malt052-build-development/README.md)
now implements original seed construction followed by an explicitly selected
native accession-mapping alternative. It is not part of this earlier alignment
archive.

From the repository root, after installing an allowed JDK21 or JDK25 and obtaining
the pinned reader provider:

```text
python tools/literature/malt052/verify.py --java-bin /path/to/jdk21/bin --runtime /path/to/malt052-reader --download-htsjdk --out /path/to/NEW-replay
```

Use `--htsjdk-jar /path/to/htsjdk-5.0.0.jar` instead of `--download-htsjdk` for an
offline replay. Obtain the reader with
`python tools/literature/malt052/setup.py --reader-only --out NEW-provider` only
where downloading and installation are permitted. Its root must contain `jars/`.
The verifier checks the exact provider/dependency bytes and compiles its own
current Java component. It restores all463,131,120 logical evidence bytes; reserve
at least1GiB of output space and allow the declared Java heaps up to3GiB.

**If a required runtime, compiler, download or installation is unavailable or
forbidden, skip the dependent replay/analysis.** Keep `FAILURE.json`,
`WARNINGS_FAILURE.md` and all diagnostics. Follow the named recovery action and
retry a NEW output directory. Do not substitute a prefix or repaired record or
interpret a skip or empty alignment as biological absence.

The earlier [guide](../MALT052.md), `VALIDATION.json`, `WARNINGS.md`,
`LINUX_SOURCE_SKIP.json` and their checkpoint checksums retain their historical
bytes. The earlier Linux availability failure has since recovered; original
Linux assignment controls and current portable replays pass. `ARCHIVE_SEAL.json`
retains the initial pending seal, and `CHECKPOINT_SHA256SUMS` retains the earlier
ledger. No historical failure or original output was replaced.
