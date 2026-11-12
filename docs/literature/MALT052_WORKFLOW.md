# Complete MALT0.5.2 DNA index-to-alignment workflow

`Malt052WorkflowCli` owns the complete path from FASTA references and an accession
mapping to a classified index, original BlastN alignment, SAM/RMA output and
native scientific verification. It combines the existing source-tested index
builder and alignment components and checks their complete index handoff.

**Select `--method native-accession-alternative` explicitly.** Unchanged original
MALT0.5.2 builds the unclassified seed index. Native Java produces a separate
mapped index because the original0.5.2 accession-map path remains unqualified.
Both indexes are retained. This is a declared method difference.

**This development workflow does not reproduce the paper's historical reference
databases, recover unreported author settings, authenticate a pathogen or qualify
all MALT/HOPS operations.** The relevant paper is [P002, Ancient genomes reveal a
deep history of Treponema pallidum in the Americas](https://doi.org/10.1038/s41586-024-08515-5).
See the source-bound [paper-role assessment](MALT_HOPS_PAPER_ROLES.md) and existing
[MALT alignment guide](MALT052.md). Their earlier component limitations remain
historical evidence; this coordinator supplies the previously separate owned
build-to-alignment operation. Catalog admission is a separate reviewed step.

## Build and install prerequisites

From the repository root, with a permitted JDK21 or25:

```bash
python3 tools/literature/malt052_workflow/build.py \
  --java-bin /usr/bin --download-htsjdk \
  --out build/malt052-workflow-1

python3 tools/literature/malt052/setup.py \
  --out /var/tmp/my-malt052-provider-1
```

`--java-bin` identifies the directory containing `java`, `javac` and `jar`.
Use your actual JDK directory if `/usr/bin` does not select JDK21 or25. Offline
builds accept `--htsjdk-jar /path/to/htsjdk-5.0.0.jar` instead of downloading.
Keep that exact dependency beside `malt052-workflow-development.jar`, along with
the project license. Original fitting requires Linux x86_64, util-linux `setsid`
and `prlimit`, and the complete pinned original MALT/Java14 provider. The
[existing installation instructions](MALT052.md#install-prerequisites-where-permitted)
document its package checksum and offline archive option. System Java is unchanged.

The released/canonical application JAR does not acquire this command from a
source build. Use the standalone JAR above. Windows supports building and saved
verification; original MALT fitting requires the stated Linux provider.

## Run the complete analysis

Create a complete reference list such as `/data/references.tsv`:

```text
fasta
genome-1.fa.gz
genome-2.fa
```

The literal header is `fasta`. Each following row names one complete FASTA file,
resolved relative to the list's directory. File and reference order are retained.
Input paths must resolve canonically without symbolic-link aliases. Use
`--fasta /data/all-references.fa.gz` instead of `--fasta-list` for one file.

```bash
java -Xmx3g -jar build/malt052-workflow-1/malt052-workflow-development.jar \
  --runtime /var/tmp/my-malt052-provider-1 \
  --fasta-list /data/references.tsv \
  --mapping /data/accession2taxid.tsv.gz \
  --format ncbi-accession2taxid --namespace accession.version \
  --query /data/complete-reads.fastq.gz \
  --method native-accession-alternative --profile CUSTOM052 \
  --out /results/new-malt052-analysis-1
```

Choose the mapping format and namespace deliberately. The alternative
`--format two-column --namespace literal` and the accession formats retain the
semantics documented in the [native mapping guide](MALT_NATIVE_INDEX_TAXONOMY.md).
Every reference must resolve to a positive known taxon. No reference prefix,
identifier repair or partial query population is substituted after a failure.

`CUSTOM052` selects the reported SemiGlobal/90%-identity/25-alignments-per-query/
100000-alignments-per-reference screening profile. Its other declared defaults,
including naive/readCount assignment, are documented in the alignment guide.
`STANDALONE` explicitly selects the distinct Local profile. Additional alignment
options retain the alignment command's names; inspect `--help` and the saved
parameter documents before interpreting an overridden method.

Default original-process resources are two workers, a4096MiB heap, one parallel
GC thread and600seconds per stage. The reported130GiB/16-worker request is
representable with `--heap-mib 133120 --threads 16`; the software controls do not
claim to execute that request. The leading `java -Xmx3g` controls the separate
application heap. Provision both heaps and all complete data where permitted.

Shared explicit file/input-byte, heap, thread, GC and wall-time options apply to
both stages. Without an explicit `--maximum-input-bytes`, the existing stage
defaults remain20GB for building and4GB for alignment. `--help` lists reference,
mapping, query and output capacities. Root inventory defaults are500000files
and1000000000000bytes; `--maximum-workflow-files` and
`--maximum-workflow-bytes` change those checks. Capacities refuse a complete
analysis rather than truncate it. Sampled process RSS is not a hard aggregate
RSS bound.

## Read the outputs and warnings

The complete directory contains:

- `index-build/native/index`: unchanged original unclassified seed index.
- `index-build/derived/index`: separately labelled mapped index.
- `alignment/inputs/database`: the complete byte-identical mapped index consumed
  by alignment.
- `alignment/native/sam/reads.blastn.sam.gz` and
  `alignment/native/rma/reads.rma6`: original alignment outputs.
- `workflow-invocation.json`, `source-bindings.json`, `workflow-audit.json` and
  both child `audit.json` files: declarations, complete input identities,
  stage populations and scientific checks.
- `WARNINGS.md`, stage stdout/stderr, and both
  `native/process/process.stderr` files: interpretation limits and all original
  warnings.
- Root `COMPONENT_COMPLETED.json`: completion of this declared workflow. Its
  inventory includes both child completion markers.

**`SKIP3 DEPENDENT ANALYSIS SKIPPED` means that a required runtime, permitted
installation or execution environment is unavailable. Do not continue dependent
analysis.** Supply the named permitted prerequisite, then retry a **new** output
directory. If installation or execution is forbidden, retain the skip and omit
that dependent analysis.

**`FAIL2 NO RESULT ADMITTED` means an input, capacity, scientific or integrity
check failed.** Inspect `FAILURE.json`, `WARNINGS_FAILURE.md` and the named stage
stderr. Correct that specific issue before retrying a **new** directory. Existing
attempts are preserved. A completed index can remain independently usable after
later alignment fails; the failed workflow has no admitted final result. An empty
or skipped result never establishes biological absence.

## Verify a transferred, complete result

Saved verification works on Windows and Linux with JDK21/25 and the exact
reader-only provider; original Java14 fitting is not repeated. Install that
provider with `tools/literature/malt052/setup.py --reader-only --out NEW_PROVIDER`
using permitted download or the documented offline archive. Keep the complete
workflow directory, including nested completion markers and original inputs.

```powershell
java -Xmx3g -jar build/malt052-workflow-1/malt052-workflow-development.jar `
  --verify D:/results/complete-malt052-analysis-1 `
  --reader-runtime D:/software/malt052-reader-1 `
  --out D:/results/new-malt052-verification-1
```

Verification rechecks both stages' complete scientific results, the entire index
handoff, ordered FASTA/list and mapping/query bindings, profiles, capacities and
recorded original process paths. Linux producer paths are interpreted lexically
on Windows; they need not exist again. Successful consistency verification does
not authenticate the producer or prove biological truth. Read `VERIFIED.json`
only after the command exits successfully.
