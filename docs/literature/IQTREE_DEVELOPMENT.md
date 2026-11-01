# IQ-TREE development: alignments, trees and source controls

**WARNING — the public IQ-TREE analysis adapter is unfinished.** The catalog continues to list IQ-TREE as missing. Current source provides private runtime setup, read-only Java runtime admission, complete alignment admission, Newick collection reading and fixed-tree JC69 evaluation. A typed execution command, model selection, tree search, support estimation, partitions and dating still require implementation and end-to-end validation. Runtime setup and these foundations do not reproduce the nine paper workflows. Do not substitute an installed release for an unresolved reported edition.

## Prepare an exact private runtime

Use the current source checkout's `tools/literature/install_iqtree.py` with **Linux x86_64 Python 3.11 or later**, permitted `/usr/bin/prlimit` and `/usr/bin/setsid`, and fresh home/report paths. Windows users must run this helper with Linux Python inside WSL. Keep runtime homes and reports on a **Linux filesystem that preserves POSIX modes**; inside WSL use a location under `$HOME`. A Windows-mounted checkout may hold the source, but a default `/mnt/c` or `/mnt/d` filesystem may not preserve the required directory/file modes. If setup reports a mode mismatch, preserve the failed files and choose fresh paths on the Linux filesystem. The original IQ-TREE executables are static Linux programs; the existing OS process helpers remain explicit prerequisites. No package manager, global PATH change or system-package installation is performed.

The eight admitted profiles are `linux-x86_64-iqtree-` followed by exactly **1.6.12**, **2.1.2**, **2.1.4-beta**, **2.2.0.3**, **2.3.1**, **2.3.5**, **2.3.6** or **2.4.0**. Select the edition required for the intended role. Unresolved `1.6.619`, unspecified dating editions and other releases are unavailable; the helper never replaces them with a different edition.

```sh
iqtree_checkout="$PWD"
iqtree_runtime_base="$HOME/.local/share/omicsmatic/iqtree"
mkdir -p "$iqtree_runtime_base/homes" "$iqtree_runtime_base/reports" \
  "$iqtree_runtime_base/caches"
python3 "$iqtree_checkout/tools/literature/install_iqtree.py" \
  --profile linux-x86_64-iqtree-2.3.6 \
  --home "$iqtree_runtime_base/homes/iqtree-2.3.6-1" \
  --cache "$iqtree_runtime_base/caches/archives" \
  --report "$iqtree_runtime_base/reports/iqtree-2.3.6-1" \
  --allow-install --allow-download
```

Parents must already exist. Home, cache and report must occupy separate nonoverlapping trees with canonical paths. A report is a **new directory**, not a JSON filename. New installation and downloading each require their explicit flag. To use permitted existing official archives without downloading, omit `--allow-download` and add `--archive-dir /path/to/verified-archives`; original archive names and complete hashes must match the lock. A corrupt cache or prior partial download is preserved and refused, even when another archive is available. Inspect it and use a fresh cache rather than silently replacing it.

Each home contains all five original archive files, the GPL notice, two synthetic health inputs and a marker. All file bytes, modes, complete membership and static ELF headers are checked. Setup runs the original version probe and one complete four-taxon, twelve-site, fixed-tree JC69 smoke test with bounded processes and a clean environment. The tree and every site likelihood must match their source/native expectations. This tests runtime startup and a small fixed-model calculation, not tree-search, support accuracy, another model or paper reconstruction.

Reverification of a completed home uses a new report and repeats the same smoke test without modifying the home or requiring install/download flags:

```sh
python3 "$iqtree_checkout/tools/literature/install_iqtree.py" \
  --profile linux-x86_64-iqtree-2.3.6 \
  --home "$iqtree_runtime_base/homes/iqtree-2.3.6-1" \
  --cache "$iqtree_runtime_base/caches/archives" \
  --report "$iqtree_runtime_base/reports/iqtree-2.3.6-verify-2"
```

**WARNING — require completed setup, not a marker or exit code alone.** Read `installation.json`, `WARNINGS.md`, both `version/` and `health/` process records, and the final `COMPLETED.json`. Its report-file hashes and runtime-marker hash must match. A provisional home whose finalization failed is refused on reuse; preserve it and use a fresh home/report after fixing the cause. A finalization error also writes `FAILURE.json` when possible. Setup always records `scientific_analysis_admitted: false`.

**SKIP3 means skip the dependent analysis:** an exact archive, permitted installation/download, host, helper or edition is unavailable. **FAIL2 means no result is admitted:** input, integrity, process, capacity or finalization failed. Both print a prominent error/skip heading and a required action. Keep failed homes, reports, partial downloads and original output values. Correct the named cause and use new paths. Runtime installation does not automatically start an analysis on user data.

Real controls cover installation and rechecking of all eight profiles, an official download, changed members/modes/programs/reports, failed finalization, refused provisional reuse, missing flags and the unresolved edition: 31 setup protocols, with 21 completed setups/rechecks and 10 expected refusals. These repeat **one synthetic health dataset**; the failed-finalization control also runs the smoke test before its setup is refused. All 31 installer unit controls pass on Linux, including timeout/cancellation, descendant cleanup, output limits and unrelated-process preservation. Windows runs the 24 portable controls and explicitly skips the seven Linux process controls.

`IqtreeRuntime` performs read-only admission from Java: exact resources/installer/host/helpers, complete private files and setup reports, static ELF, process command/cleanup receipts, and an independent native replay of all twelve health sites. It exports the exact setup resources for the forthcoming public command. The source helper above is currently the supported setup entry point; a public analysis/export CLI has not been added yet.

The package-private `IqtreeProcess` primitive is execution scaffolding for that forthcoming command. It binds the admitted runtime, checks inputs before and after execution, applies explicit process/workspace limits, and records owned-process cleanup. Its success status is `completed-process-only`; it cannot write a scientific completion marker. Process controls cover cancellation, timeout, orphaned workers, changed inputs, output links and capacity failures while preserving an unrelated process. This does not supply a typed analysis recipe or validate tree inference, model selection or support estimation.

## Native input and tree APIs

`org.jlinalg.phylogeny.PhylogeneticAlignment` reads aligned FASTA and explicitly declared sequential/interleaved PHYLIP. PHYLIP names may use relaxed fields or fixed ten-column fields; continuation names are a separate declared interleaved option. FASTA header interpretation is explicit: `WHOLE_HEADER` or `FIRST_FIELD`. Short-name collisions fail rather than merge. Original headers, row order, state characters and case remain available. Sequence spaces/tabs are removed; no site or taxon is sampled or dropped. Input must reach EOF and close successfully before any alignment is returned.

Use DNA, amino-acid or **standard genetic code** codon data explicitly. Other genetic codes, NEXUS/CLUSTAL/MSF, compressed input and grouped-character encodings are not admitted by this parser. Their remaining contracts must be implemented explicitly. DNA `U` is a source thymine alias. Protein `B`, `Z` and `J` remain ambiguities; source `U`, `O` and `*` are unknown protein states. For codons, an ambiguity or partial missingness makes the **whole source codon unknown**, rather than summing the possible codons. Unambiguous standard-code stops and incomplete triplets fail without trimming. Statistics distinguish raw character cells from model sites; a codon model site contains three character columns.

**WARNING — version-specific `!` support.** Real controls show that 1.6.12, 2.1.2, 2.1.4-beta and 2.2.0.3 reject this marker; tested 2.3.1, 2.3.5, 2.3.6 and 2.4.0 accept it as a gap. Call `requireIqtreeVersion()` before source execution. Preserve a refused input and resolve its source encoding; do not silently replace the marker or change editions. The separate `jc69Sequences()` view maps admitted DNA aliases to the native JC69 alphabet while retaining original sequences.

`originalToOpaque()` and `opaqueToOriginal()` provide a reversible mapping. `writeFasta(writer, true, 80)` writes every row and column using safe internal IDs. These avoid upstream name shortening and character replacement. Restore names in checked trees with `NewickTree.relabelLeaves()`; it preserves branch precision, comments and internal support labels. Keep the mapping and original header ledger with every future analysis result.

```java
var alignment = PhylogeneticAlignment.readFasta(
    Path.of("aligned.fasta"), PhylogeneticAlignment.Kind.DNA,
    PhylogeneticAlignment.HeaderPolicy.WHOLE_HEADER,
    PhylogeneticAlignment.Limits.defaults());
alignment.requireIqtreeVersion("2.3.6");
try (var writer = Files.newBufferedWriter(
        Path.of("prepared.fasta"), StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW)) {
    alignment.writeFasta(writer, true, 80);
}
// Preserve alignment.opaqueToOriginal() and all original headers.
```

`NewickReader` streams a complete collection, including quoted delimiters and nested comments. Returned individual records are provisional until EOF and successful close. A bad later tree, malformed UTF-8, trailing token, read/close error or exceeded capacity invalidates the collection. Closing early never certifies it. Final balanced document comments and whitespace remain available as `trailingText()` after successful close. Per-tree, collection-character and tree-count limits are explicit; no prefix is accepted as the complete collection.

```java
var reader = NewickReader.open(Path.of("bootstrap.trees"),
    NewickReader.Limits.defaults());
long count = 0;
try (reader) {
    NewickTree tree;
    while ((tree = reader.nextTree()) != null) {
        if (!tree.taxa().equals(alignment.opaqueToOriginal().keySet()))
            throw new IllegalArgumentException("Exact bootstrap taxon set differs");
        // Audit the whole tree; require metric lengths only if requested.
        count++;
    }
}
if (!reader.isComplete() || count != 1000)
    throw new IllegalArgumentException("Incomplete declared bootstrap collection");
```

The example validates a supplied collection; it does not generate bootstrap trees or infer their support algorithm. Production execution must additionally protect/hash input and output files, check the declared replicate count and output roles, and finish a public completion manifest. An unrooted tree's displayed first taxon is not a biological rooting claim. Root stems are retained but excluded from taxon-to-taxon distances. Shared ancestral stems are excluded before metric sums, so they cannot overflow a finite sister-tip distance.

## Retained original controls

The [alignment replay archive](../../src/test/resources/org/jlinalg/phylogeny/iqtree-alignment/original-controls.zip) retains **128 actual protocols across eight original Linux releases**: 68 fits and 60 refusals. Every input, output, stdout/stderr, original command, executable/archive hash, collector and controller is included with complete checksums. Twelve refusals preserve the older releases' `!` limitation; sixteen preserve default-parser failures on wrapped sequential PHYLIP. Separate runs with original `--sequential` succeed in all eight releases. The native parser requires the layout explicitly and can prepare canonical FASTA.

An independent explicit ancestral-state enumeration checks every site and total in **56 fixed-tree DNA JC69 and protein Poisson fits**. These are complete synthetic controls, not model search or biological benchmarks. The other twelve fits use source `MG+F1X4`: their complete data, topology, output dimensions and whole-codon ambiguity behavior are audited, but their fitted MG likelihood is **not independently validated**.

**WARNING — signed source roundoff is retained.** Fully missing codon sites have mathematical log likelihood zero. These original programs report tiny signed departures, approximately `-6.0e-15` to `4.2e-15`, on all 36 such control-site outputs. The archive retains the exact strings and an explicit theoretical-zero ledger. No output is clamped, rewritten or presented as an independently reproduced codon likelihood.

The earlier [fixed-JC69 archive](../../src/test/resources/org/jlinalg/phylogeny/iqtree-fixed-jc69/original-controls.zip) retains eight additional complete fixed-tree fits and the reported-option refusals below. The focused native/replay suite also checks malformed and later-truncated collections, capacities, Unicode identities, complete PHYLIP dimensions, mapping reversibility, codon stops and source aliases.

```text
./gradlew test --tests 'org.jlinalg.phylogeny.*'
```

`tools/literature/collect_iqtree_alignment_controls.py` independently checks retained original captures and freezes a **new** replay archive and report. It launches no scientific program and refuses existing destinations. The upstream programs remain private and are not redistributed in either archive. Release-download hashes are observed integrity pins; no upstream signature or published asset digest is claimed.

## Primary-paper roles and unresolved settings

These roles were checked against the cached full primary articles, retaining separate paragraphs and editions. They do not establish exhaustive software extraction from each paper. The master catalog remains at its existing review status until the IQ-TREE role-update proof is applied.

| Paper | Reported IQ-TREE role and edition | Boundary to retain |
| --- | --- | --- |
| [P037](https://doi.org/10.1038/s41586-023-06838-3), paragraph 40 | 1.6.12, maximum-likelihood nucleotide tree from 142 aligned core genes for Bifidobacterium MAG classification; B. gallicum DSM 20093 outgroup. | Roary, MAFFT and trimming precede tree inference; native parsing does not perform them. |
| [P054](https://doi.org/10.1038/s41586-025-09794-2), paragraphs 87–90 | Distinct roles: 2.2.0.3 protein trees with ModelFinder/1,000 ultrafast replicates; 2.4.0 amoA nucleotide tree using GTR+F+I+R10; 2.1.2 concatenated single-copy protein tree using WAG+G and UFBoot; 2.2.0.3 cNxrA tree using LG+F+R10. | Preserve the role-specific edition, alphabet and model. ARB rerooting and iTOL are downstream. |
| [P066](https://doi.org/10.1038/s41586-026-10932-7), paragraph 30 | 2.3.1 mammal/species and gene trees, reported `GTR+F3x4+R6`, ultrafast support, ALRT, BNNI, safe mode and saved best-model NEXUS. | Controlled DNA and CODON runs both reject the literal model. Obtain effective author commands/model definitions before paper-exact execution. Do not reverse the article's ambiguous “less than 50% gaps” filtering language. Dating uses downstream MCMCtree/PAML. |
| [P101](https://doi.org/10.1038/s41588-023-01571-z), paragraph 30 | 2.1.4-beta, GTR+R10 maximum-likelihood tree of 516 millet accessions from 4DTv SNP sites. | Retain the beta edition and the upstream 4DTv definition. |
| [P109](https://doi.org/10.1038/s41588-024-01967-5), paragraph 33 | Literal reported edition **1.6.619**, VT+F+R5, 1,000 ultrafast replicates in a WGS population study. | The literal is unresolved and is not a citation-number join. VT is a protein model; the effective input alphabet is unspecified. Do not normalize the edition to 1.6.6 or change the model to GTR. |
| [P110](https://doi.org/10.1038/s41588-025-02409-6), paragraphs 29 and 44 | 2.1.2 MT-A70 protein phylogeny after MAFFT/trimAl, automatic model selection and 1,000 ultrafast/ALRT replicates; related phylogenetic input for downstream selection tests. | PAL2NAL/CodeML selection tests are separate analyses. |
| [P171](https://doi.org/10.1038/s41588-026-02678-9), paragraphs 44 and 46 | An OrthoFinder `-T iqtree` role with unspecified executable edition; separate direct 2.3.5 single-copy protein trees with literal `-AUTO`, followed by ASTRAL. | Original 2.3.5 rejects `-AUTO`. Obtain the effective command; do not translate it into thread auto-detection or model selection. |
| [P235](https://doi.org/10.1016/j.ajhg.2024.06.007), paragraph 6 | 1.6.12 maximum-likelihood trees, automatic model selection and 1,000 bootstraps; separately named `iqtree2` for chimpanzee-calibrated coalescence dating. | Bootstrap algorithm and dating executable edition are unspecified. Do not assign the tree edition to dating or substitute IQ-TREE 3's dating implementation. |
| [P248](https://doi.org/10.1038/s41588-026-02682-z), paragraph 52 | 2.3.6 GTR+R10 maximum-likelihood tree of 422 cucumber accessions from 4DTv sites. | Preserve the degenotate-derived site definition and accession set. |

For upstream reference, consult the [input tutorial](https://iqtree.github.io/doc/Tutorial), [command reference](https://iqtree.github.io/doc/Command-Reference), [substitution models](https://iqtree.github.io/doc/Substitution-Models) and [dating documentation](https://iqtree.github.io/doc/Dating). Current documentation is not proof that every historical release implements every listed feature.

## Required recovery and remaining implementation

**Unavailable or forbidden upstream runtime/capability: skip the dependent analysis and give its precise required action.** Native input methods require no IQ-TREE installation. They do not authorize source execution or certify an unavailable historical edition. Invalid alignment content, integrity failures, failed execution or exceeded capacities must admit no scientific result; preserve original/partial files, diagnostics and the exact mapping, correct the named cause and use fresh output paths. Do not treat a version probe, an exit code alone, or one valid bootstrap tree as a complete analysis result.

Next work is owned bounded Java execution, a typed recipe/CLI and complete model/tree/support/partition/dating output audits, followed by actual packaged analysis workflows and a separately proved catalog update. Cell Ranger remains excluded. The full 250-paper implementation goal remains active.
