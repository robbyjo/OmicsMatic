# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Grant only scoped public original Tomtom comparison credit after complete frozen workflow gates."""
import hashlib
import json
from pathlib import PurePosixPath
import stat
import zipfile
from catalog_source_integrity import verify_source
from catalog_fixed_cohort import verify_fixed_cohort


def sha(raw):return hashlib.sha256(raw).hexdigest()


def verify(root,data):
    folder=root/'docs/literature/meme-tomtom-workflow-development';proof=json.loads((folder/'VALIDATION.json').read_bytes());assert proof['schema_version']==1 and proof['status']=='passed-public-original-Tomtom-workflow-setup-example-and-portable-contracts'
    sums=dict(line.split('  ',1)[::-1] for line in (folder/'SHA256SUMS').read_text().splitlines());assert set(sums)=={'controls.zip','VALIDATION.json'}
    for name,digest in sums.items():assert sha((folder/name).read_bytes())==digest
    archive=folder/'controls.zip';assert archive.stat().st_size==proof['archive']['bytes'] and sha(archive.read_bytes())==proof['archive']['sha256']
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.infolist())==len(set(z.namelist()))==proof['archive']['entries']==len(proof['archive_files'])
        for entry in z.infolist():
            name=entry.filename;member=PurePosixPath(name);assert not member.is_absolute() and '..' not in member.parts and '\\' not in name and stat.S_IFMT(entry.external_attr>>16)==stat.S_IFREG;raw=z.read(name);expected=proof['archive_files'][name];assert len(raw)==expected['bytes'] and sha(raw)==expected['sha256']
        for name,value in proof['source_pins'].items():
            raw=z.read('source/'+name);assert len(raw)==value['bytes'] and sha(raw)==value['sha256'];verify_source(root,data,name,value['sha256'])
    assert proof['exact_source_bytecode_classes']==64 and len(proof['added_archive_entries'])==16 and proof['semantic_mutations_with_recomputed_hashes']==14
    assert sorted(r['name'] for r in proof['platform_validation'])==['linux-java21','windows-java21','windows-java25'] and all(r['tests']==r['passed']==286 and r['skipped']==0 for r in proof['platform_validation'])
    assert (proof['complete_public_control_workflows'],proof['complete_documented_workflows'],proof['portable_actual_main_verifications'],proof['original_user_fits'],proof['fresh_original_health_fits'])==(2,2,6,4,8)
    assert all(proof[k] for k in ['public_execution_adapter_available','standalone_setup_available','documented_example_validated']) and not any(proof[k] for k in ['copied_record_is_runtime_ticket','released','pushed','deployed','whole_goal_complete'])
    artifact=root/proof['artifact']['path']
    if artifact.exists():assert artifact.stat().st_size==proof['artifact']['bytes'] and sha(artifact.read_bytes())==proof['artifact']['sha256']
    roles=json.loads((data/'meme-program-role-coverage.json').read_bytes());assert roles['applied'] and roles['total_reviews']==815 and roles['supporting_pointers']==248 and not roles['implementation_promoted']
    prior=json.loads((data/'meme-program-catalog-validation.json').read_bytes());raw=(data/'meme-program-role-coverage.json').read_bytes();expected=prior['archive_files']['catalog-snapshot/meme-program-role-coverage.json'];assert len(raw)==expected['bytes'] and sha(raw)==expected['sha256']
    reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes());verify_source(root,data,'tools/literature/reviewed-uses.json',roles['reviews_sha256']);selected=[r for r in reviews['reviews'] if r['tool_id']=='tomtom'];assert len(selected)==7
    papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(root,data,papers);observed={p['paper_id']:p for p in papers if any(t['tool_id']=='tomtom' for t in p['tools'])};assert set(observed)=={'P013','P047','P061','P065','P067','P133','P139'}
    for review in selected:
        paper=next(p for p in observed.values() if p['pmcid']==review['pmcid']);tool=next(t for t in paper['tools'] if t['tool_id']=='tomtom');assert paper['doi']==review['doi'] and tool['evidence_paragraph_sha256']==review['evidence_paragraph_sha256'] and tool['use_summary']==review['use_summary'] and tool['implementation_scope_note']==review['implementation_scope_note']
    return proof


def tomtom_metadata(root,data):
    proof=verify(root,data)
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/TomtomToolsCli.java',coverage_note='New scoped original5.5.9 DNA motif comparison integration; seven reviewed roles include two inherited motif-cluster resources, not seven reconstructed fits.',implementation_scope='Typed tomtom-tools TEXT/FULL_REPORT fits through the exact unmodified original Linux x86_64 MEME Suite5.5.9 source build. Explicit complete query/ordered targets, background, distance, strand/pseudocount/overlap/internal/complete-score choices and cutoff. Native Java preserves unchanged inputs and all motif/default/normalization ledgers, independently checks complete text or XML/TSV plus every HTML scientific display field, and retains every declared pair with unavailable unreported values as NA. Separately permitted source setup, fresh source/host/ELF/Perl health, strict owned process accounting and portable whole-bundle verification. No silent mode/settings/version substitution, motif filtering, inferred zero or q-value recomputation. Historical editions, unknown paper resources/settings, MEME/ChIP/DREME/STREME discovery, CentriMo and complete paper pipelines remain separate. Read MEME_TOMTOM_WORKFLOW_DEVELOPMENT.md and examples/tomtom/README.md.',validation_note='286 actual-JAR tests each Windows Java21/25 and Linux Java21 without skips; strict exact-source64-class match and unrelated archive preservation. Fourteen inconsistent bundles with recomputed hashes rejected. Two final public controls and two documented workflows through a separately source-built exact runtime retain36 user rows and run eight fresh health fits; six copied actual-main verifications pass across three JVMs. Setup skip/corrupt-archive and detached cancellation/timeout/nonzero cleanup controls pass. Synthetic three-motif databases do not calibrate P-values or certify biology, historical paper equivalence or execution authenticity.',project_adapter_available=True,canonical_adapter_available=False,development_adapter_cli='tomtom-tools',development_candidate=proof['artifact']['path'],development_candidate_sha256=proof['artifact']['sha256'])


TOMTOM_WARNING='''## Tomtom: original motif comparison, setup and required recovery

**Current source includes tomtom-tools TEXT and FULL_REPORT; older canonical/released JARs lack this command.** Read MEME_TOMTOM_WORKFLOW_DEVELOPMENT.md, meme-tomtom-workflow-development/VALIDATION.json and examples/tomtom/README.md. Export setup separately with --export-runtime-setup. Only permitted --allow-build/--allow-download and confirmed --research-nonprofit use are supported; a pinned offline --archive is accepted. No host packages or dependencies are installed automatically. Linux x86_64 Java is required for fitting; copied complete bundles verify on Windows Java without opening original producer paths. Original compiled homes embed their prefix and cannot simply be moved.

**SKIP3: required exact runtime, permission, library, Perl/XML helper, supported host or input capability unavailable means skip dependent analysis. FAIL2: input, integrity, process, numerical, capacity, cancellation, cleanup or finalization failure means no result admitted.** Read WARNINGS.md, FAILURE.json and its named stage, every original process/build log, metadata/producer.json, OUTPUT-AUDIT.json and PAIR-LEDGER.tsv. Preserve partials/native workers, correct only through permitted setup and retry into NEW paths. Require overall COMPLETED.json and successful --verify; component/runtime markers alone do not admit a result. Native process limits are not a Java heap guarantee.

**Preserve numerical and paper uncertainty.** Unreported pairs have NA values, never imputed zeros or biological absences. Original q-values use all tested target orientations, not just reported best matches; never recalculate them from displayed rows. Printed zero can be rounding/underflow. Tiny databases below50 target motifs do not establish reliable original P-value calibration. Full reports require complete XML, TSV and HTML; no fallback mode or extra scientific run is invented. P047/P061 inherited clusters are resource-author comparisons, not new fits. P067 has documented Kullback/text/pseudocount/overlap choices but an unstated Tomtom edition and other unknowns. Historical versions/resources, discovery programs and complete paper pipelines remain open. Cell Ranger remains excluded. Hashes prove integrity, not authenticity or biological accuracy.
'''

TOMTOM_ACTIONS='''<section id="tomtom-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Tomtom: original comparison and required recovery</h2>
<p>Use current-source <strong>tomtom-tools</strong> with explicit <strong>TEXT</strong> or <strong>FULL_REPORT</strong> and original <strong>5.5.9</strong>. Older canonical/released JARs lack this command. Read <code>MEME_TOMTOM_WORKFLOW_DEVELOPMENT.md</code>, <code>meme-tomtom-workflow-development/VALIDATION.json</code> and <code>examples/tomtom/README.md</code>. Linux x86_64 Java/WSL performs fits; Windows Java also verifies copied whole bundles without opening producer paths.</p>
<p>Export <code>--export-runtime-setup</code>, read <code>RUN-SETUP.txt</code>/<code>UPSTREAM-COPYING</code> and use only permitted <code>--allow-build</code>/<code>--allow-download</code> or a pinned offline <code>--archive</code>, with <code>--research-nonprofit</code>. No automatic host-package installation occurs. Preserve the exact compiled prefix.</p>
<p><strong>SKIP3: required permission/dependency/capability unavailable means skip dependent analysis. FAIL2: invalid input, integrity, process, numerical, capacity, cancellation, cleanup or finalization failure admits no result.</strong> Read <code>WARNINGS.md</code>, <code>FAILURE.json</code>, its named stage, all original logs, <code>metadata/producer.json</code>, <code>OUTPUT-AUDIT.json</code> and <code>PAIR-LEDGER.tsv</code>. Preserve partials/native workers, correct the cause and use NEW paths. Require overall <code>COMPLETED.json</code> plus successful <code>--verify</code>. Native process limits are not a Java heap guarantee.</p>
<p><strong>Unreported pairs have NA values, never zero or biological absence.</strong> Original q-values use all tested orientations; do not recalculate them from best-match rows. Printed zero can be rounding/underflow. Fewer than50 targets do not calibrate original significance. Full reports require XML, TSV and HTML; no silent output/settings/version substitution occurs. Two paper roles reuse resource-author clusters; unknown historical settings/databases and discovery programs remain separate.</p>
<p><strong>Validation:</strong> 286 actual-JAR checks per JVM on Windows Java21/25 and Linux Java21 without skips; 14 semantic mutations with recomputed hashes rejected. Four final public/example synthetic user fits retain36 rows plus eight fresh original health fits; six copied actual-main verifications pass. Permitted offline source setup and detached-worker recovery controls pass. These controls do not certify historical paper reproduction, biological accuracy or authenticity. Cell Ranger remains excluded.</p></section>'''
