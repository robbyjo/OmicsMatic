# MAFFT development: complete inputs, alignment audits and original controls

**WARNING — the public MAFFT integration is unfinished.** The catalog still lists MAFFT as missing. Current source provides native Java FASTA, alignment-retention and add-map audits, plus six locked original Linux release definitions. Managed runtime setup/admission, typed execution, owned-process limits and a final public analysis bundle still require implementation and validation. Dependent MAFFT analyses must remain skipped in the project until those gates are available. The native audit APIs can already inspect supplied data without MAFFT installed.

The eight paper uses report six exact editions: **7.313, 7.475, 7.487, 7.490, 7.505 and 7.526**. P052 reports only major edition **7**, for an add-to-alignment tool. Do not silently replace that unresolved edition with one of the six releases.

## Native Java APIs

**org.jlinalg.sequence.MafftSequences** reads complete plain UTF-8 FASTA with explicit nucleotide/protein alphabet, symbol policy, header interpretation and aligned/unaligned layout. It retains row order, original headers, literal residue characters, case and gap characters. Sequence spaces and tabs are formatting: they are removed and counted. Complete sequence, cell, decoded-character, line, header and estimated-retention capacities are explicit. Limits reject the entire input; no prefix, shortened sequence or sampled population is returned.

Supplied readers are owned. EOF and successful close are required before a Data result is returned. Malformed UTF-8, duplicate identities, bad later rows, cancellation and read/close failures return no data. FIRST_FIELD preserves the original description but uses only the first ASCII space/tab-delimited field as the taxon identity; collisions fail. WHOLE_HEADER uses the exact whole header, including Unicode. Name mappings are copied before exports and audits, so writer/mapping callbacks cannot rebind later taxa.

Symbols.BIOLOGICAL is the standard MAFFT input set admitted by this integration; it is not a statement that other symbols are biologically invalid. Original controls show that protein U needs the explicit source --anysymbol choice. ANY_PRINTABLE retains non-whitespace printable ASCII symbols literally. The aligner and its scoring interpretation still require a separate explicit contract.

~~~java
var input = MafftSequences.readFasta(
    Path.of("sequences.fasta"),
    MafftSequences.Alphabet.NUCLEOTIDE,
    MafftSequences.Symbols.BIOLOGICAL,
    MafftSequences.Headers.WHOLE_HEADER,
    MafftSequences.Layout.SEQUENCES,
    MafftSequences.Gaps.ALLOW,
    MafftSequences.Limits.defaults());
var originalToOpaque = input.originalToOpaque();
try (var writer = Files.newBufferedWriter(
        Path.of("prepared.fasta"), StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW)) {
    // Explicitly remove existing input gaps for a fresh alignment.
    // The input object's original rows remain unchanged.
    input.writeFasta(writer, originalToOpaque, true, 80);
}
~~~

These methods parse and prepare data; the example does not execute MAFFT. Keep the original source file and its raw-byte hash separately. These APIs do not seal external files or runtimes before/after execution. Estimated retained bytes are a capacity estimate, not a hard process-RAM guarantee.

**MafftSequences.audit()** checks the exact complete output taxon set, explicit input/source row-order policy, every original non-gap residue, every output cell and all-gap columns. ASCII case may change and changes are counted; U and T remain distinct. Omitted, inserted or changed residues fail. A one-to-many alias cannot hide an extra output row. The audit does not score alignment optimality or biological accuracy.

## Existing alignments and deliberate deletion

**auditBackbone(..., Backbone.EXPAND)** checks every informative original backbone column while permitting insertion of backbone-gap columns and source removal of all-gap columns. It reports original and output backbone-gap counts separately. **It does not infer unique original-to-output identities for all-gap columns.** Do not derive an exact deletion/ownership map from the difference between those counts.

Backbone.KEEP_LENGTH requires the complete original backbone width and every original character/gap at its indexed column. An ordinary residue-preserving audit is insufficient for --keeplength, because the original program deliberately deletes insertions from added sequences.

**org.jlinalg.sequence.MafftAddMap** reads every original --mapout row. It verifies complete added-taxon families, every original ungapped residue position, the raw input character column, every mapped output position and every deleted residue. Positions are one-based; output column -1 means a source-deleted insertion. Raw and source-printed residue characters remain separate, with case changes counted.

**Callback rows remain provisional until EOF and successful close return a complete audit.** A later truncation, extra residue, duplicate taxon/coordinate, unaccounted output residue, changed symbol, capacity failure or close failure invalidates the map. Preserve partial ledgers but never admit them as complete. Combine the complete taxon/order check, backbone audit and add-map audit before accepting a keep-length result.

The maintainer documents that map output activates keep-length behavior and that inserted residues can be removed. These are explicit analysis choices, not harmless formatting conversions. See the [original add/keep-length documentation](https://mafft.cbrc.jp/alignment/software/addsequences.html).

## Original runtime and test evidence

The maintainer's unmodified [Linux portable distributions](https://mafft.cbrc.jp/alignment/software/linuxportable.html) were downloaded for all six exact editions and unpacked privately. No compiler or global installation was needed. Original programs remain outside the repository. The retained [BSD notice](../../tools/literature/mafft-runtime/BSD-NOTICE.txt), [download/member lock](../../tools/literature/mafft-runtime-lock.json) and four synthetic health inputs describe the development resources; they are not completed managed-runtime admission.

**WARNING — original 7.475 memory behavior.** The maintainer reports an FFT-NS-i memory-allocation bug in 7.463–7.486 that can also affect --auto. Preserve the exact requested release and any failed outputs; never silently upgrade a paper's edition. Successful tiny single-thread controls do not establish large-input or multithread resource behavior. See the [official changelog](https://mafft.cbrc.jp/alignment/software/changelog.html).

The [complete original-control archive](../../src/test/resources/org/jlinalg/sequence/mafft-original/original-controls.zip) retains 60 actual protocols across six editions: **48 alignment fits, six explicit standard-protein alphabet refusals and six version probes**. Eight successful protocol types reuse six small synthetic input families; these are not 48 independent biological datasets. The controls cover nucleotide auto/global-pair alignment, protein L-INS-i, add/keep-length, unusual symbols and existing gaps. All owned process groups were confirmed terminal. The six keep-length controls retain complete maps for 72 added residues, including **30 deliberate deletions**.

Current native/replay tests verify all 60 controls without an upstream installation. Earlier native test and archive snapshots are retained in the development evidence rather than relabelled or overwritten. These source-consistency and input-integrity checks do not reconstruct a paper's alignments, manual curation or downstream phylogeny.

~~~powershell
.\gradlew.bat test --tests "org.jlinalg.sequence.Mafft*" --tests "org.jlinalg.cli.MafftOriginalReplayTest"
~~~

## Required remaining work and recovery

Complete the private installer and read-only Java admission, declared algorithm/scoring/add policies, owned runtime execution, input/output hash binding, original-name/case restoration and atomic completion bundle. Test the actual packaged public workflows and refusal paths before promoting MAFFT in the catalog. Paper-specific source review still needs to be applied to the master catalog.

The required public policy is **SKIP3 for an unavailable/forbidden exact runtime or unsupported capability**, and **FAIL2 with no admitted result for invalid input, integrity, process, capacity or finalization failures**. Those exit-code/bundle contracts remain implementation work; the current native APIs report exceptions. Preserve raw/partial files, correct the named cause and use new output paths. Native exceptions, version probes and one valid alignment row are not public completion receipts.

RNA structural extensions, DASH/network-backed alignment, arbitrary score files, orientation inference, codon back-translation and full paper pipelines retain separate unfinished contracts. Cell Ranger remains excluded. The full 250-paper goal remains active, with 58 new scoped integrations and 261 named tools still missing.
