# Continue the full 250-paper implementation goal

This is a native Hornet allele-component milestone. The full goal remains active.
Do not count it as another completed family or a reproduced paper. Canonical
totals remain 250 papers, 453 labels, 492 effective providers, 2,733 observations,
1,825 reviewed / 908 pending uses, and 172 scoped families (34 native +138
upstream). The effective registry is
`docs/literature/rfmix-catalog-admission/REGISTRY.json`, not the425-definition base.
The frozen source window remains2023-10-23 through2026-10-23. Continue discovering
unregistered methods when reviewing the complete source cohort.

## Current evidence and runtimes

Own checkout: `D:/projects/JLinAlg/build/fu-1`, branch `codex/wasp-20261127`.
Private stage: `build/wasp-896`. The exact producing JAR is
`project-build-Windows-2/project-build/cli/omicsmatic-0.4.1.jar` under that stage;
SHA256 `057701499b086408fbe8e8c84bd9ccaf9009adaa12316d6285cdbbfd4062401d`.
Preserve it and all qualification-proof files. Both the complete 334-case
fixture and the 341-test-per-OS suite passed, along with packaged replay and
negative controls. No canonical catalog rows were changed here.

WSL distribution: `JLinAlg-Omics-SAIGE-20261026`.
Private runtime: `/opt/omicsmatic-wasp034-hornet-20261127-1`.

- `source/WASP`:82 unchanged non-example-data source/docs/test files from
  `d3b8447fd7719fffa00b856fd1f27c845554693e`. This is the last official commit
  before2023-02-10, not proof of P187's author revision. The official0.3.4 tag is
  `f980683cff5638660a966ff0d7c69598bc056e98`.
- `source/Hornet`: original11-node tree at
  `e642ac0ee9ed6625ce415305b9ced4d686403c79`, including its two internal `_2.py`
  symlinks. The original ZIP and per-Git-blob validation are retained. Do not
  replace links with ordinary files or infer that this is P007's exact revision.
- `venv/bin/python`: isolated Python3.11 venv with NumPy1.23.5, SciPy1.10.1,
  Pysam0.21.0, PyTables3.8.0, H5py3.8.0 and Pytest7.4.4. Selected WASP uses
  `np.int`; no monkeypatch/source change was made. Base interpreter/stdlib are
  from the existing ASCAT runtime; preserve that runtime too.
- `tools/bin/bwa`: unmodified0.7.17-r1188 source built at
  `9f26bfcc7780753129b60717ecab0ebba6f04b7c`, using `-fcommon` for modern GCC.
  The original source/archive, compiler flags and executable identity are kept.
- `samtools-helper-1/usr/bin`: private Ubuntu Samtools1.19.2 and Bowtie2.5.2
  helpers. Supply only that helper prefix's `usr/lib/x86_64-linux-gnu` in its
  isolated library path; do not contaminate existing runtimes.

All48 original WASP mapping tests passed, including HDF5/haplotype and original
filtering cases. Tests generated synthetic HDF5 through PyTables. `snp2h5` is
not built: system HDF5 development headers were absent. Its original C source
is retained; a separate private permitted dependency install/build remains
available if needed. No full project adapter has been qualified yet.

## Next implementation work

1. Complete Hornet's BAM/FASTQ preparation and original remap-filter semantics,
   then the mapper adapter. Do not treat the allele API's result lists as FASTQ.
   The original writer reverses/complements reverse-strand sequences without
   reversing qualities; inspect, preserve and prominently qualify that source
   behavior. The original filter expects read-name-sorted remaps, uses a distinct
   colon-delimited name protocol, rejects certain CIGAR operations, and can fail
   on empty remaps. Numeric read IDs interact with name sorting; audit IDs above9
   and missing groups rather than assuming the original is correct. Empty or
   malformed original executions must remain errors, not repaired successes.
2. Implement standalone WASP's separate text and phased-HDF5/sample-subset modes,
   limits, overlap handling, source-specific read names and CIGAR-aware remap
   filtering. OriginalWASP tests are a useful oracle, not a substitute for actual
   project capture/replay, mapper execution and independent complete-read audits.
   CHT is a separate component whose use has not been established by these
   mapping contexts. Use native Java where practical, tested original adapters
   for substantial mapper/HDF5 work, and exact source/runtime/JAR inventories.
3. Address STARsolo2.7.10a's built-in WASP mode separately. The existing bulk STAR
   2.7.11b adapter explicitly excludes STARsolo/WASP and does not supply coverage.
4. Review all four original contexts and separate Hornet observations before
   canonical admission. Archive predecessor metadata, preserve unrelated rows
   and run the established full-cohort validators. This component earned no
   review credit. Do not duplicate the existing Hornet label.

## Actual paper contexts to retain

- P007 /PMC13668659 /10.1038/s41586-026-11053-x: modified WASP linked to Hornet,
  separate human/chimpanzee and human/gorilla mapping, STAR2.7.11b, specified
  junction input/options and Picard random duplicate scoring. Exact fork revision
  and complete species-specific variant preparation remain unestablished.
- P073 /PMC11631754 /10.1038/s41588-024-02019-8: STARsolo2.7.10a's
  `--waspOutputMode`, sample post-QC VCF, GRCh38/GENCODE32, barcode/UMI settings,
  Picard dedup and retained unique proper pairs passing WASP. Cell Ranger stays
  excluded; an independently supplied whitelist does not authorize that runtime.
- P157 /PMC11525184 /10.1038/s41588-024-01896-3: **explicitly omitted WASP and
  z-score filtering for the sQTL analysis.** Correct the pending generic-use
  summary to this explicit omission; retain its observation and exact locator.
  This does not establish whole-paper nonuse beyond the stated step.
- P187 /PMC12805840 /10.1016/j.ajhg.2025.11.009: standalone WASP0.3.4 with
  updates as of2023-02-09 on cell-type/donor pseudobulk ATAC. Authors approximated
  mapping with BWA-mem0.7.17 `-M -I250,150` because Cell Ranger could not be used
  in WASP. Version/date alone do not identify an author commit.

The WASP observations forP007/P073 were already reviewed; P157/P187 remained
pending at this milestone. Inspect the separate Hornet observation's review
status before counting. Private census checked all250 original XML identities;
article text remains private. Positive mapping uses, explicit omissions and
bibliographic mentions are different source statements.

## Persistent user decisions and preservation

Prioritize high-impact tools. Private eligible downloads/installs are authorized.
Missing/forbidden prerequisites must yield prominentSKIP3 and precise recovery
steps; invalid/changed inputs, runtimes, source, models, capacity or cancellation
yieldFAIL2 without a complete result. Preserve failed attempts. Academic terms
already accepted in the conversation remain authorized. Cell Ranger stays
excluded. MaxQuant runtime analysis stays skipped, with MaxQuant/Andromeda/MaxDIA
pending. Latest iDEP requires its own source/licence assessment. ConstructASCATFiles
and PREASCAT permissions remain pending. RFMixSIS final iteration and legacy1.5.4
remain unqualified; preserve all old warnings, archives and producing JARs.

Nineteen worktrees exist, eighteen foreign, with51 foreign tracked edits at the
prepublication snapshot. Preserve them, the detached root checkout, active jobs,
original scientific inputs/outputs and required runtimes. No subagents were used.
Feature-local verified cleanup is mandatory after validation/publication. Never
blanket-delete `build/`, use broad `git clean` or retire uncertain worktrees.

## Publication and cleanup complete

The native component was published on main at `c6453daeade716c37f4036d22b055542ff6d5ba9`; all16 files passed complete hosted readback. `COMPLETION.json` records the subsequent cleanup. Removed15,194 verified temporary files,386,178,097 logical bytes (368MiB): Windows compiler/resource/cache copies plus the private pip cache and BWA compiler copy. Exact compiler recovery ZIP, all14 original wheels, producing JAR, source archives, original runtime and synthetic/source evidence are retained. Observed D: free-space change including recovery was213508096bytes; the Linux filesystem gained100581376bytes. Filesystem observations include allocation overhead and concurrent accounting; no host VHD shrink was requested.

Post-cleanup341 native tests, a complete packaged replay and original Hornet/Python/BWA/Samtools/Bowtie2 health checks passed. All893 current protected files and4,487 predecessor RFMix files passed full-hash verification. All18 foreign worktrees,51 tracked foreign edits, HEADs and statuses are unchanged. All19 worktrees remain; no process was stopped. Cleanup receipts/plans list exact removed paths and recovery mappings. The two failed cleanup-planning attempts deleted nothing and remain retained. Continue the full mapping implementation and source review above; the component added no family or review credit.
