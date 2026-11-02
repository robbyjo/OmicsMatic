# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verified scoped MAFFT coverage and explicit required actions."""
import json
from catalog_source_integrity import verify_source
from mafft_workflow_validation import verify, sha


def mafft_metadata(root, data):
    verify(root, data, emit=False)
    proof = json.loads((data / 'mafft-workflow-validation.json').read_bytes())
    roles = json.loads((data / 'mafft-role-coverage.json').read_bytes())
    assert roles['status'] == 'passed-eight-MAFFT-primary-source-role-reviews' and roles['applied'] is True
    assert roles['reviewed_roles'] == 8 and roles['total_reviews'] == 759 and roles['whole_paper_reproduced'] is False
    verify_source(root, data, 'tools/literature/reviewed-uses.json', roles['reviews_sha256'])
    for name, field in [('tools/literature/curate_mafft_uses.py', 'curator_sha256'), ('tools/literature/mafft-use-reviews.json', 'draft_sha256')]:
        assert sha((root / name).read_bytes()) == roles[field]
    return dict(
        implementation_status='upstream-adapter-validated',
        implementation_source='src/main/java/org/jlinalg/cli/MafftToolsCli.java',
        coverage_note='The pre-expansion inventory lacked MAFFT. The current-source typed alignment/addition command has validated scoped contracts; eight primary-source paper roles retain explicit unknown settings and downstream boundaries.',
        implementation_scope='Exact original Linux x86_64 editions 7.313, 7.475, 7.487, 7.490, 7.505, 7.526 through mafft-tools. Complete plain UTF-8 nucleotide/protein FASTA; explicit alphabet, symbol, header, gap, algorithm, scoring, thread and order choices. De novo alignment or addition to a supplied backbone, with optional keep-length and a complete insertion-deletion map. Source-default/auto, local/global/generalized-pair and FFT strategies; explicit refinement maximum; source defaults or explicit BLOSUM30/45/62/80 and gap settings. Native Java checks every original/prepared/source/restored cell, identity, header, retained residue case and residue-ledger row; complete hashes, host/process receipts and exact command/recipe binding are retained. Runtime setup is explicit and separate; no analysis-time installation. Use Linux Java, including WSL on Windows, and an admitted exact private home. Native parsing and completed-bundle verification also work on Windows. RNA structure, DASH/network, custom matrices, codon translation, direction inference, add-fragments/add-profile, batch execution, unresolved paper settings and whole-paper reproduction remain open. Read MAFFT_DEVELOPMENT.md.',
        validation_note='57 actual packaged protocols match expected outcomes: 35 finalized alignments, 13 SKIP3 outcomes and 9 FAIL2 outcomes. One controlled finalization failure follows a completed original fit; 36 original processes completed but only 35 results were admitted. They reuse 9 tiny input-byte populations, not independent biological datasets. All 1678 original-residue ledger cells and 37 deliberate deletions are independently checked; 24 alignment/map files match original bytes across six editions. All 180 focused cases run against actual JARs on three JVMs: Windows Java 21/25 each 159 passes plus 21 Linux-only aborts; Linux Java 21 has 179 passes plus 1 Windows-only abort. A setup-message-only source/class-constant delta preserves every scientific instruction. Full Java/publication gate counts are recorded separately in the validation report. No biological alignment-accuracy or paper-reconstruction certificate is claimed.',
        project_adapter_available=True, canonical_adapter_available=False, development_adapter_cli='mafft-tools',
        development_candidate=proof['packaged_candidate'], development_candidate_sha256=proof['packaged_candidate_sha256'],
        workflow_validation='mafft-workflow-validation.json', source_role_coverage='mafft-role-coverage.json', source_roles_reviewed=8,
        runtime_profiles=['linux-x86_64-mafft-' + version for version in proof['exact_editions']],
        runtime_setup_export_available=True, download_install_available=True, runtime_installer='tools/literature/install_mafft.py',
        whole_paper_reproduced=False, biological_accuracy_certified=False, paper_exact_unknown_settings_available=False)


MAFFT_WARNING = '''
## MAFFT: exact editions, complete residues and required actions

Use current-source **mafft-tools** and read **MAFFT_DEVELOPMENT.md**. Older released 0.3.7 executables lack this command. Export the runtime kit with **--export-runtime-setup NEW_DIRECTORY**. Setup uses Linux x86_64 Python 3.11+, existing permitted helpers and explicit **--allow-install** / **--allow-download** flags; complete offline archives use **--archive-dir**. Analysis requires Linux Java (WSL on Windows), an admitted exact home and NEW canonical ASCII Linux output paths. Original input paths/headers can be Unicode. Setup completion alone admits no analysis.

**SKIP3: skip dependent analysis when the exact runtime or capability is unavailable/forbidden. FAIL2: no result is admitted for invalid input, integrity, process, capacity or finalization failure.** Read **WARNINGS.md**, **session.json**, **output-audit.json**, **taxon-map.json**, **residue-map.tsv** and retained process/host logs. Require a valid **COMPLETED.json** and **mafft-tools --verify DIRECTORY**. Preserve partials, correct the named cause and retry into NEW paths. Hashes prove integrity, not authenticity.

**Keep-length deliberately deletes insertions:** every retained/deleted original residue remains in the full ledger. Original all-gap backbone-column identities can be unresolved; do not infer a unique column map from counts. Refinement maxima do not prove every iteration ran; **iteration_threads=0** disables refinement multithreading, not refinement. Literal unusual symbols receive the original program's scoring interpretation. Multithreaded refinement can vary. **Exact 7.475 retains the maintainer FFT-NS-i/auto memory-bug warning**; tiny successful controls do not establish large-input safety.

**Do not guess paper settings.** P052 reports only major v7 and has no paper-exact release; P101 does not specify alphabet/algorithm. Other papers omit complete commands. Require declared choices and effective author settings for paper-exact execution. Manual curation, trimming, phylogeny, codon back-translation and full paper pipelines remain separate. Cell Ranger remains excluded.

'''

MAFFT_ACTIONS = '''<section id="mafft-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>MAFFT alignment and addition: exact editions and required recovery</h2>
<p>Use current-source <code>mafft-tools</code>; read <code>MAFFT_DEVELOPMENT.md</code>, <code>mafft-workflow-validation.json</code> and <code>mafft-role-coverage.json</code>. Older released 0.3.7 executables lack this command. Six exact Linux editions support declared nucleotide/protein FASTA, de novo alignment or addition to a supplied backbone. Choose alphabet, symbols, headers, gaps, algorithm, scoring, threads and output order explicitly.</p>
<p><strong>Setup is separate.</strong> Export with <code>mafft-tools --export-runtime-setup NEW_DIRECTORY</code>. Use Linux x86_64 Python 3.11+, existing permitted helpers, private Linux homes and explicit <code>--allow-install</code>/<code>--allow-download</code> flags. Complete offline archives use <code>--archive-dir</code>. Analysis requires Linux Java, including WSL on Windows, and a NEW canonical ASCII Linux output path. Original FASTA paths and headers can be Unicode. Setup completion alone admits no analysis.</p>
<p><strong>SKIP3: skip dependent analysis when the exact runtime or capability is unavailable/forbidden. FAIL2: no result is admitted for invalid input, integrity, process, capacity or finalization failure.</strong> Required action: inspect <code>WARNINGS.md</code>, <code>session.json</code>, <code>output-audit.json</code>, <code>taxon-map.json</code>, <code>residue-map.tsv</code> and raw process/host logs. Preserve partials, correct the named cause and use NEW paths. Require valid <code>COMPLETED.json</code> and <code>mafft-tools --verify DIRECTORY</code>. Hashes prove integrity, not authenticity.</p>
<p><strong>Review every intentional deletion.</strong> Keep-length deletes insertions; all original residues remain in the complete retained/deleted ledger. Gap-only backbone-column identities can remain unresolved. A maximum does not assert every iteration ran; <code>iteration_threads=0</code> disables only refinement multithreading. Literal unusual-symbol scoring remains source behavior, and multithreaded refinement can vary. <strong>Exact 7.475 retains the FFT-NS-i/auto memory-bug warning.</strong> Small controls do not establish large-input safety.</p>
<p><strong>57 protocols: 35 completed alignments, 13 expected skips, nine expected failures.</strong> All 1678 original-residue cells and 37 deliberate deletions are checked; 24 alignment/map outputs match original bytes across six editions. The 180-case actual-JAR matrix has 159 Windows passes with 21 Linux-only aborts and 179 Linux passes with 1 Windows-only abort. Nine tiny input-byte populations are reused; no biological-accuracy or paper-reconstruction certificate is claimed.</p>
<p><strong>Paper-exact settings remain explicit.</strong> P052 major v7 has no exact release; P101 alphabet/algorithm are unspecified. Do not substitute or infer them. RNA structure, DASH/network, custom matrices, codon translation, direction inference, add-fragments/add-profile, batch workflows, manual curation and downstream pipelines remain separate. Cell Ranger remains excluded.</p></section>'''
