# nf-core captured execution and native ChIP-seq component audits

**Development checkpoint: nf-core is still pending. These components add no implemented-tool credit.** All 250 papers are collected; the catalog still has 99 new scoped integrations (19 native methods and 80 upstream adapters), or 102 including three existing integrations. Six nf-core paper-role drafts remain unapplied. The full project goal remains open.

This checkpoint adds a Linux process monitor and native Java checks over the **complete** outputs of the original nf-core/chipseq 1.2.1 workflow. It builds on the separate [configuration and input-capture foundation](NFCORE_FOUNDATION.md). The released 0.3.7 executable has no command for these development components. They are separate JARs; the root CLI and released JAR are unchanged.

## Warnings and required actions

**Do not use this checkpoint as a full nf-core scientific admission or a paper reproduction.** Complete original runtime/image admission and the remaining peak, differential, library-complexity, annotation and other QC contracts are still required. Other nf-core families and versions remain unfinished. Cell Ranger stays excluded at the user's request.

| Outcome | Meaning | Required action |
| --- | --- | --- |
| SKIP3 | The exact permitted runtime or dependency is unavailable. | Skip dependent analysis. Complete permitted setup using the exported installer and explicit download/install flags. Analysis never installs software automatically. |
| FAIL2 | Invalid inputs, changed evidence, capacity, process or finalization failure. | Admit no result. Read `FAILURE.json`, `WARNINGS.md`, stderr and the original trace/log. Preserve the attempt. Correct the named cause and use a new path when changing the immutable request or capacities. |
| Complete captured workflow | Declared input/output, command, integrity and original task-completion contracts passed. | Read the latest warnings and verify the bundle. Complete the scientific checks before downstream scientific claims. |
| Complete component audit | The specific native checks below passed. | Retain the audit and original workflow evidence. This gives zero nf-core tool credit and no whole-pipeline or biological certificate. |

The actual captured test reports **130 runtime filesystem-mode departures**. Runtime bytes and link targets matched, and required executable access passed. If exact POSIX modes are required, use a permitted filesystem and fresh setup paths. Its process monitor also reports bounded lifecycle resampling: no live RSS or thread count was imputed. Persistent incoherence fails analysis and triggers owned-process cleanup. Original warnings are retained in the evidence archive.

Earlier monitor failures, an incomplete cleanup, the separately verified retirement of the identified leftover process, interrupted audits and packaging failures are retained. The failed original six-sample control is retained separately from the successful derivative. Never relabel these attempts as successes or resume a root whose cleanup/working-state evidence is incomplete. Java 25 also prints upstream library deprecation warnings; raw stderr is retained. Java 21 is the tested choice for avoiding those Java 25 warnings.

## What the Java code checks

`LinuxProcessSnapshot` anchors Linux `/proc` reads to an open process directory, checks PID/start-time identity, and separates identity-only cleanup from live resource measurements. `NfcoreProcess` records owned observations and briefly resamples transient lifecycle inconsistencies within the declared polling interval. A missing live gauge is never assigned zero. Terminal identities are distinguished from zombie leaders with live workers. Cleanup retains ownership and identity checks.

`NfcoreChipseq121Mappings` reads the literal `design_reads.csv` and `design_controls.csv` emitted by the original checker. It preserves identifiers, paths, encounter order, technical versus biological replicate relationships, control edges and antibody flags. Original checker defects are detected without repairing their output: controls demonstrate the `_T10` truncation defect and an absent fallback replicate.

`NfcoreScientificFiles` reads verified immutable objects rather than producer paths or URLs. It distinguishes separate original files with identical bytes from symlink/hard-link aliases. Named FASTQ copies preserve compression suffixes for extension-aware readers. Declared capacities refuse the complete operation instead of returning a shortened population.

The source-specific component CLI checks all 85 locked original workflow files, complete FASTQ streams and mate identities, the exact FASTA/GTF dictionary, every library BAM's reference/read-group/sample identities, flagstat totals and mapped QC partitions, and IP FRiP values. It uses the project's existing Java FASTA, FASTQ, GTF and interval implementations and exact existing HTSJDK dependencies.

Native FRiP reproduces this source recipe: count each overlapping peak row at a 20% alignment outer-span overlap, keep unsplit CIGAR spans, and divide by QC-passing mapped reads from SAMtools flagstat. Overlapping peaks may produce a ratio above one; no clamping occurs. A zero denominator remains unassessable. Original numeric tokens are retained and compared at the original display precision. Original BEDTools 2.29.2 controls establish its float arithmetic and zero default B-overlap threshold.

## Validation scope

- Both Java 21 and Java 25 builds pass the original Nextflow 21.04.1, 22.10.6 and 24.04.4 execution/resume controls. The 15-case matrix and 12 saved-result checks include ignored task failures, missing outputs/tools, command resealing, corrupt objects and stale-success refusal.
- Both process builds pass portable checks on Windows Java 21/25 and Linux Java 21, actual child churn, nonzero exits, capacity/integrity refusals, and a zombie leader with a live pthread worker.
- Eleven original Python design-checker cases and eight original SAMtools/BEDTools/awk cases establish mapping, BAM identity and FRiP behavior. Expected upstream errors and native refusals remain visible.
- Actual complete-capture controls test equal-byte distinct mates, symlinks, hard links, preserved gzip names, out-of-capture paths and capacity refusals on both compiler builds and all three JVM environments.
- Four complete native audits agree across Windows Java 21/25 and Linux Java 21 using both compiler builds. Named-view and BAM capacity refusals and existing-output protection also pass.

The captured original ChIP-seq run has **97 tasks: 88 succeeded and 9 cached, with zero failed/ignored/aborted tasks**, and 367 output files. It uses the full original six-sample fixture plus an explicitly labelled PCR-duplicate derivative: 116,195 pairs per sample, comprising every original 100,000 pair plus 16,195 simulated PCR pairs. Original compressed members are unchanged. The derivative addresses the original fixture's insufficient duplication depth for PRESEQ; it is not independent biological evidence or author paper data. All scientific branches and the original 500,000 fingerprint bins remain enabled. Four native FRiP ratios agree with the original reported tokens.

## Run or verify the components

Obtain the development JARs and their checksums from [the checkpoint directory](nfcore-captured-development/README.md). Verify that checkpoint first, from the repository root:

```powershell
python tools/literature/verify_nfcore_captured_checkpoint.py
```

Verify an already complete captured workflow session or standalone attempt:

```powershell
java -jar docs/literature/nfcore-captured-development/nfcore-captured-javac21.jar --verify D:\results\complete-nfcore-attempt
```

Audit complete source-specific objects. Replace the input and output paths; the output parent must already exist and the output directory must be new and disjoint from the original session. Declare the actual source recipe's peak type and read mode explicitly:

```powershell
java -Xmx1024m -jar docs/literature/nfcore-captured-development/nfcore-chipseq121-audit-javac21.jar --engine-result D:\results\complete-nfcore-attempt --peak-type broadPeak --read-mode paired --out D:\results\new-component-audit
```

Require exit zero and `COMPONENT_AUDIT_COMPLETED.json`; inspect `audit.json` and `WARNINGS.md`. The component marker deliberately differs from a scientific-admission marker. The CLI's `--help` documents explicit complete-stream capacities. A plain results folder, partial export or failed latest attempt is insufficient.

Original workflow execution requires the exact permitted Linux runtime, exported setup and a typed captured recipe; see the foundation guide and `nfcore-captured-javac21.jar --help`. These development components do not infer unreported author settings or automatically prepare a complete scientific analysis.

The evidence ZIP includes complete small synthetic engine/capture controls and metadata/reports for the large ChIP-seq execution. Its full approximately 909 MiB immutable attempt, original inputs, image and work/cache trees remain locally retained; they are **not bundled in the public ZIP**. Repeating the large component audit requires that complete attempt or a newly captured complete original workflow run. Hashes establish integrity, not execution authenticity. The checkpoint verifier checks frozen bytes and stated evidence contracts; it does not repeat the scientific analysis.
