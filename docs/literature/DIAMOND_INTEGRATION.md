# DIAMOND protein and translated-query searches

`diamond-tools` creates an original DIAMOND protein database and runs original
`blastp` or standard-genetic-code, non-frameshift `blastx`. Supported source
profiles are **2.0.0, 2.0.15 and 2.1.8**. Native Java checks complete input
populations, literal identifiers, alignment coordinates, identity/gap counts,
coverage and query dispositions. DIAMOND remains responsible for alignments,
scoring and E-values; Java does not independently refit those calculations.

**Require exit 0, `result_admitted=true`, successful complete `--verify`, and
review of `WARNINGS.md`, provenance, raw hits and original logs.** Runtime
health alone does not admit an alignment. Historical papers and biological
interpretations have not been reproduced.

## Warnings and required actions

| Outcome | Required action |
| --- | --- |
| **SKIP3 / exit 3** | **Skip the dependent analysis.** Supply the exact permitted Linux runtime or explicitly download it only where allowed. If installation/download is unavailable or forbidden, keep the analysis skipped. Analysis installs nothing. |
| **FAIL2 / exit 2** | **Admit no result**, including earlier successful steps. Preserve the attempt, inspect the named cause and original logs, correct it explicitly and retry new output paths. |
| Original crash or rejected option | Keep the source failure. Do not change options, drop a query file, replace the version or admit a partial output automatically. |
| Rounded E-values, bit scores or identities | Preserve raw fields. Rounded strings do not recover exact strict paper thresholds; reported zero E-values do not establish exact zero tail probabilities. |
| Duplicate query or reference IDs | Keep every original record. Hits cannot be assigned to indistinguishable individual records; duplicate query dispositions remain explicitly ambiguous. |
| No reported hit | Treat it as a source reporting disposition, not proof of biological absence or a successfully established annotation. |
| Existing `.dmnd` input | The full database and original `dbinfo` are retained. Independent reconstruction of its reference sequence population is not performed. |

The unchanged source has material option and edition limits:

- A positive `--min-score` uses a **bit-score** cutoff and overrides E-value
  filtering. Both requested arguments remain recorded.
- `--top` below 100 conflicts with explicit `--max-target-seqs` in these
  versions. Use `max_target_seqs: null` for an explicitly chosen top-range
  search. `top_percent: 100` is the source's ordinary target-limit sentinel;
  it does not request unlimited reporting.
- Extended audit-field experiments crashed the original 2.0.15 binary. The
  admitted format uses its ordinary 12 fields plus `qlen` and `slen`, which
  passed complete population comparisons. Previous failed experiments remain
  in the evidence archive.
- Original 2.0.0 crashed for the tested combination of disabled masking and
  composition-based statistics, including ordinary 12-field output. That
  failure is preserved.
- Original 2.1.8 rejected the tested pair of equally populated FASTQ files,
  compressed and uncompressed, with “Unequal number of sequences in paired
  read files.” Both single-file FASTQ cases worked; the same paired fixtures
  worked in 2.0.15. This observation does not establish failure for every paired
  input. The adapter preserves the requested edition and both files.

Warnings and actions are printed even when failure precedes output creation.
Owned workers are stopped and awaited on timeout. Failed outputs remain
unadmitted, with an explicit warning if quarantine fails. Capacities are logical
limits, not host RAM/disk quotas or producer authentication. A deliberately tiny
block size can greatly increase runtime; a recorded 120-second failure was
retained before an explicit retry with a 300-second limit.

## Start with the standalone Java command

The self-contained development JAR needs Java 17 or later. New alignment
analyses require **Linux x86_64 Java**; explicitly selected WSL is supported.
Saved-bundle verification works on Windows without DIAMOND installation.

From the repository root, on Linux:

```sh
java -jar docs/literature/diamond-development/diamond-javac21.jar --help
java -jar docs/literature/diamond-development/diamond-javac21.jar --export-runtime-setup /work/diamond/setup-new
```

The export does not install anything. Choose one permitted runtime setup:

```sh
# Explicit download of the exact official release into a new private directory:
python3 /work/diamond/setup-new/setup_diamond_runtime.py --version 2.1.8 --out /work/diamond/runtime-new --allow-download

# Alternatively, validate an existing exact original binary:
python3 /work/diamond/setup-new/setup_diamond_runtime.py --version 2.1.8 --out /work/diamond/runtime-existing-new --binary /opt/diamond-2.1.8/diamond
```

Setup needs Linux x86_64 Python 3.11 or later and checks the original archive,
binary, version and selected dynamic libraries/loader configuration. It never
uses an unrecognized binary as a replacement. If it returns exit 3, read its
`WARNINGS.md`, skip the dependent analysis and supply the named prerequisites
only where allowed. Runtime profiles bind local absolute Linux paths; prepare
a new profile after moving the executable or changing selected dependencies.

## A complete request

Save this as `/work/diamond/request.json`, replacing the input paths with your
complete files. The chosen output directory must not already exist. All fields
below are required; `null` explicitly leaves an optional argument to that
original version's behavior. Scientific settings are examples, not inferred
paper parameters.

```json
{
  "schema_version": 1,
  "out": "/work/diamond/result-new",
  "runtime_profile": "/work/diamond/runtime-new/runtime-profile.json",
  "analysis": {
    "schema_version": 1,
    "source_label": "2.1.8",
    "database": {"kind": "protein-fasta", "path": "/work/reference.faa"},
    "queries": ["/work/query.faa"],
    "search": {
      "mode": "blastp",
      "sensitivity": "sensitive",
      "algorithm": "auto",
      "evalue": 0.001,
      "min_bitscore": null,
      "max_target_seqs": 50,
      "top_percent": null,
      "max_hsps": 1,
      "min_identity": null,
      "query_cover": null,
      "subject_cover": null,
      "no_self_hits": false,
      "matrix": "BLOSUM62",
      "gap_open": null,
      "gap_extend": null,
      "comp_based_stats": 1,
      "masking": null,
      "query_gencode": 1,
      "block_size": 0.01,
      "index_chunks": 1,
      "threads": 1
    }
  },
  "limits": {
    "input_bytes": 1000000000,
    "decoded_bytes": 2000000000,
    "output_bytes": 4000000000,
    "wall_seconds": 3600,
    "max_files": 10000,
    "max_records": 1000000,
    "max_record_length": 1000000,
    "max_line_characters": 1000000,
    "max_alignment_rows": 10000000
  }
}
```

```sh
java -Xmx2g -jar docs/literature/diamond-development/diamond-javac21.jar --request /work/diamond/request.json
java -Xmx2g -jar docs/literature/diamond-development/diamond-javac21.jar --verify /work/diamond/result-new
```

For translated nucleotide queries, explicitly choose `mode: "blastx"` and supply
nucleotide FASTA or four-line FASTQ. Gzip is detected from file bytes. No
frameshift profile or alternate genetic code is supplied. Protein references
must be FASTA; `database.kind: "diamond-db"` accepts an existing compatible
original native database instead. Two query files are accepted only for the
later editions, with the 2.1.8 source warning above. Input parsing rejects
empty/leading-space identifiers and malformed records; it never edits them.
Complete original bytes, full titles and the source's first ID token are kept.

For all-versus-all protein comparisons, explicitly supply the same complete
protein FASTA as reference and query. Choose self-hit policy explicitly;
`no_self_hits` is forwarded to the original source. Target limits, sensitivity,
ties and heuristic search behavior remain original DIAMOND behavior.

On Windows, verify an already copied complete bundle with:

```powershell
java -Xmx2g -jar docs/literature/diamond-development/diamond-javac21.jar --verify C:/work/diamond/result-copied
```

The bundle contains original input copies and hits, process commands/logs,
`input-records.jsonl`, `audited-hits.tsv`, `query-dispositions.jsonl`, provenance,
warnings and complete file hashes. Verification checks the full population,
source/runtime metadata, exact scientific command spelling and native transport
replay. It runs no original alignment and authenticates no producer. Preserve
the complete directory when copying it.

## Main OmicsMatic route

The root supplement preserves every predecessor scientific class/resource byte
and adds `diamond-tools`. It requires the separately recorded canonical JAR;
the standalone command above is suitable when that dependency is absent.

```sh
java -cp 'docs/literature/diamond-development/diamond-root-javac21.jar:build/cli/omicsmatic-0.3.7.jar' org.jlinalg.cli.JLinAlgCli diamond-tools --request /work/diamond/request.json
java -cp 'docs/literature/diamond-development/diamond-root-javac21.jar:build/cli/omicsmatic-0.3.7.jar' org.jlinalg.cli.JLinAlgCli diamond-tools --verify /work/diamond/result-new
```

On Windows, use `;` between classpath entries. The canonical released JAR stays
unchanged; these are development artifacts, with no release/deployment claim.

## Five primary paper roles

| Paper | DIAMOND use | Scope that remains explicit |
| --- | --- | --- |
| [P017](https://doi.org/10.1038/s41586-023-06955-z) | Sensitive protein/translated-query searches for novelty, expression, ecology, functional matches and abundance evidence. | No DIAMOND version is reported. Query, target and unspecified coverage descriptions differ by step; strict/inclusive boundaries and database releases cannot be inferred from a generic coverage label. Downstream annotation, abundance and t-SNE remain separate. |
| [P037](https://doi.org/10.1038/s41586-023-06838-3) | 2.0.0 protein alignments, followed by use of 50 highest bit-score hits in domain annotation. | Original target reporting is available. Domain DBSCAN, KDE, local minima and majority-rule annotation remain separate; no study-specific tie policy is inferred. |
| [P054](https://doi.org/10.1038/s41586-025-09794-2) | 2.1.8 protein searches against curated marker references with reported score cutoff 100. | **The paper wording does not resolve raw versus bit score or an external filter. Require its exact interpretation before claiming that study cutoff.** Original `min-score` means bit score. Score-ratio selection and GraftM remain separate. |
| [P171](https://doi.org/10.1038/s41588-026-02678-9) | 2.1.8 comparisons against UniMag, UniPoa and PTREP. | Supply the exact curated references and search settings. HC/LC classification, completeness, Iso-seq promotion and InterProScan remain separate. |
| [P248](https://doi.org/10.1038/s41588-026-02682-z) | 2.0.15 NR annotation and all-versus-all protein comparisons at a 1e-5 E-value threshold. | NR release, exact strict threshold treatment, mSynOrths, gene-family classification and evolutionary-rate analyses remain separate. |

These are scoped tool integrations, not full paper or whole-package
implementations. Source candidates are pinned to official
[DIAMOND releases](https://github.com/bbuchfink/diamond/releases) and immutable
source commits in `SOURCE-LOCK.json`. The unchanged binaries are downloaded
explicitly; the adapter and original GPL-3 license/notice are packaged with the
development artifacts. Reproducing historical compilation is not claimed.

See [validation and artifact hashes](diamond-development/VALIDATION.json) and
[the current catalog](omics-2023-2026/README.md).
