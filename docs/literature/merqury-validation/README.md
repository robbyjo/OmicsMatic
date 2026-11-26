# Scoped Merqury qualification

Merqury adds one upstream tool family with native Java numerical checks. The qualified operations are complete read k-mer counting, one/two-assembly assessment, and parental hap-mer database preparation. This record does not qualify the whole Merqury package, reconstruct all author settings, or reproduce a paper's biological results.

See [the usage guide](../../MERQURY_TOOLS.md), [the four-paper source audit](../merqury-source-audit/README.md), and [the frozen qualification](QUALIFICATION.json). Build from the published source: the older 0.4.1 released JAR predates this command.

Validation passed 31 JUnit tests on each of Windows and Linux with no skipped tests. Nine complete packaged captures passed fresh original-binary and independent Java numerical verification. They include merged and gzip FASTX inputs, one/two-assembly assessment, filtered/unfiltered parental databases with/without a child, and an infinite-QV zero-error case. The original 1.1 assessment also passed full TDF reconstruction. Comparisons covered 614 original histogram-boundary cases and six original false-duplication reports. Three resealed corruptions were refused; missing runtime, process limits, cancellation, and undefined false-duplication populations were also checked.

**Warnings and required actions:** Original dual-assembly runs with the selected 1.3 and 1.4.1 source/runtime combinations failed and remain unadmitted. An explicitly selected `native-boundary-palette-v1` mode on 1.4.1 supplies the bounded histogram and palette corrections described in the guide; there is no automatic fallback. A missing or unavailable prerequisite yields **SKIP3**: install a permitted dependency, provide the complete required input/runtime, and make a new binding/request. Changed bytes, numerical discrepancies, resource limits, or source failures yield **FAIL2**: retain the failed attempt, investigate the reported cause, and use a new output directory. Neither status admits a scientific result. Full replay requires Linux, the exact producing JAR at its recorded path, the unchanged private runtime, and a new scratch directory.

`SOURCES.json` pins the implementation and verifier sources. `EVIDENCE.json` publishes hashes and reviewed counts, while complete inputs, outputs, process traces and runtimes remain private. Hashes show integrity, not authenticity or biological validity. Historical failures and old producer JARs remain retained.

```sh
# Source record only; this does not execute upstream tools.
python3 -B tools/literature/verify_merqury_workflows.py --root /absolute/source

# Retained evidence bytes only; this does not repeat scientific computation.
python3 -B tools/literature/verify_merqury_workflows.py \
  --root /absolute/source --private-root /absolute/private-evidence

# All nine saved binary/numerical replays, using Linux and a NEW receipt directory.
python3 -B tools/literature/verify_merqury_workflows.py \
  --root /absolute/source --private-root /absolute/private-evidence \
  --replay --out /absolute/new-replay-receipts
```

The source audit scans the frozen 250-paper cohort. Catalog admission reviews three additional uses (P101, P171, P248) and expands P164's existing parental-database role. It grants no duplicate native family credit. The source audit's earlier pending-implementation fields are intentionally historical; the catalog admission record supplies the subsequent scoped qualification. The literature report HTML remains withheld from the hosted website.
