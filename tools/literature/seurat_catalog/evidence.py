# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind actual cumulative predecessor bytes and unchanged Seurat qualifications."""
import json
from pathlib import Path
import subprocess
import sys
import zipfile

from metal_catalog import catalog as metal
from metal_catalog import evidence as previous
from seurat_catalog.model import META, DATA
from seurat_catalog.primary import ROOT, encoded, require
from seurat_workflow import verify as rna
from seurat_preprocessing import verify as native

BASE = '9d4a26933ad17318a58f45f9dcafc18c33848956'
FOUNDATION = sorted(set(previous.FOUNDATION + metal.OWN + [
    'tools/literature/seurat_workflow/verify.py',
    'tools/literature/seurat_preprocessing/verify.py',
    'tools/literature/verify_seurat_differential_development.py',
    'tools/literature/verify_seurat_marker_summary.py',
    'tools/literature/signac_scan_evidence.py',
    'tools/literature/setup_deseq2_reference.py',
]))
pin, rawpin, git = previous.pin, previous.rawpin, previous.git
packet_write, packet_read = previous.packet_write, previous.packet_read
COMPONENTS = {
    'rna': 'docs/literature/seurat-workflow-development',
    'native_preprocessing': 'docs/literature/seurat-preprocessing-development-v2',
    'markers': 'docs/literature/seurat-differential-development',
    'native_marker_summaries': 'docs/literature/seurat-marker-summary-development',
}
MARKERS = {
    'markers': 'tools/literature/verify_seurat_differential_development.py',
    'native_marker_summaries': 'tools/literature/verify_seurat_marker_summary.py',
}


def baseline():
    before = {name: git('show', BASE + ':' + name) for name in META}
    proof = json.loads(git('show', BASE + ':' + metal.PROOF))
    require(proof['after'] == {n: rawpin(b) for n, b in before.items()},
            'Seurat predecessor differs from the actual completed METAL admission')
    return before


def predecessor(cache, before):
    """Re-run unchanged METAL/LDAK logic on exact retained stage bytes, no overlay."""
    path = ROOT / metal.PROOF
    require(path.read_bytes() == git('show', BASE + ':' + metal.PROOF), 'METAL proof changed')
    proof = json.loads(path.read_bytes())
    require(proof['status'] == metal.STATUS and proof['base_commit'] == previous.BASE,
            'Different METAL predecessor stage')
    require(proof['source_code'] == {n: pin(ROOT / n) for n in metal.OWN},
            'Unchanged METAL admission source is required')
    for n, wanted in proof['evidence'].items():
        require(pin(ROOT / n) == wanted, 'METAL retained evidence changed: ' + n)
    for n, wanted in proof['foundation_sources'].items():
        require(pin(ROOT / n) == rawpin(git('show', previous.BASE + ':' + n)) == wanted,
                'METAL foundation changed: ' + n)
    with zipfile.ZipFile(ROOT / metal.FOLDER / 'catalog-before.zip') as archive:
        require(len(archive.namelist()) == 8 and set(archive.namelist()) == set(META),
                'METAL before archive population differs')
        old = {n: archive.read(n) for n in META}
    require(old == previous.baseline(), 'METAL before archive differs from actual earlier Git state')
    require((ROOT / metal.FOLDER / 'PREVIOUS-SHA256SUMS').read_bytes() ==
            git('show', previous.BASE + ':' + DATA + '/SHA256SUMS'), 'Old METAL manifest differs')
    earlier = previous.predecessor(cache, old)
    require(earlier == proof['predecessor_audit'], 'Retained unchanged LDAK predecessor result differs')
    census = metal.primary(cache, old)
    require(census == json.loads((ROOT / metal.COVERAGE).read_bytes()), 'METAL source census differs')
    result = metal.check(old, before, census)
    require(proof['before'] == {n: rawpin(b) for n, b in old.items()} and
            proof['after'] == {n: rawpin(b) for n, b in before.items()}, 'METAL actual stage bindings differ')
    require(all(proof.get(k) == v for k, v in result.items()), 'METAL recorded counts differ')
    original, public = previous.public_qualification()
    q = proof['workflow_qualification']
    require(q['public_audit'] == public and q['source_guards'] == original['source_guards']
            and q['audit_source_guard'] == original['audit_source_guard']
            and q['closed_index'] == original['private_closed_index'] and q['reader'] == original['reader'],
            'METAL qualified source/runtime identity differs')
    require(q['original_qualification'] == pin(previous.workflow.EVIDENCE / 'VALIDATION.json')
            and q['original_public_archive'] == original['archive']
            and q['reproduction_scripts_archive'] == original['reproduction_scripts_archive'],
            'METAL qualification artifact binding differs')
    count = packet_read(ROOT / metal.FOLDER / 'qualification-evidence.zip', proof['qualification_packet'])
    with zipfile.ZipFile(ROOT / metal.FOLDER / 'qualification-evidence.zip') as archive:
        index_bytes = archive.read('closed-INDEX.json')
        require(rawpin(index_bytes) == q['closed_index'], 'METAL closed index differs')
        index = json.loads(index_bytes)
        require(index['reader']['pin'] == q['reader'] and len(index['payloads']) == 813
                and len(index['admitted_bundles']) == 28, 'METAL reader population differs')
        report_bytes = archive.read('current-reader-checks/COMPLETED.json')
        expected = dict(public)
        expected.pop('reproduction_script_payloads')
        expected.update(private_payloads_rehashed=813, private_bundles_reverified_now=28,
                        status=original['status'], original_runtime_reexecuted=False,
                        whole_goal_complete=False, new_family_credit=0, public_views_are_standalone_bundles=False)
        require(rawpin(report_bytes) == q['retained_reader_check'] and json.loads(report_bytes) == expected,
                'METAL retained frozen-reader audit differs')
        for n, wanted in original['source_guards'].items():
            require(rawpin(archive.read('qualified-project-source/' + n)) == wanted,
                    'Archived METAL source differs: ' + n)
        require(rawpin(archive.read('qualified-auditor.py')) == original['audit_source_guard'],
                'Archived METAL auditor differs')
        for n in index['admitted_bundles']:
            for suffix in ['.stdout', '.stderr']:
                require('current-reader-checks/' + n + suffix in archive.namelist(), 'Missing METAL reader console')
    from catalog_source_integrity import reverse_delta, verify_source
    compat = json.loads((ROOT / metal.COMPAT).read_bytes())
    require(compat['schema_version'] == 1 and compat['status'] == 'passed-dispatch-catalog-only-source-deltas'
            and len(compat['paths']) == 1, 'METAL review compatibility scope differs')
    n = 'tools/literature/reviewed-uses.json'
    require(compat['paths'][0]['path'] == n and reverse_delta(before[n], compat['paths'][0]) == old[n],
            'METAL review inverse differs')
    verify_source(ROOT, ROOT / DATA, n, rawpin(old[n])['sha256'])
    return dict(status='PASS_UNCHANGED_METAL_AND_LDAK_ON_ACTUAL_STAGE_BYTES', result=result,
                proof=pin(path), qualification_payloads_read=count,
                ldak_qualification_payloads_read=earlier['qualification_payloads_read'],
                primary_xml_hashes_checked=250, source_overlay_used=False,
                original_runtime_reexecuted=False, physical_private_store_rehashed=False)


def public_qualification(out):
    """Run the original, unchanged source/archive auditors; no R or installations."""
    out = Path(out)
    out.mkdir(parents=True, exist_ok=False)
    proofs, sources, artifacts, payload = {}, {}, {}, {}
    for name, folder in COMPONENTS.items():
        vpath = folder + '/VALIDATION.json'
        raw = (ROOT / vpath).read_bytes()
        require(raw == git('show', BASE + ':' + vpath), 'Frozen component proof changed: ' + name)
        proof = json.loads(raw)
        proofs[name] = proof
        payload['original-proofs/' + name + '.json'] = raw
        artifacts[vpath] = rawpin(raw)
        archive_name = 'qualification-evidence.zip' if name in ['rna', 'native_preprocessing'] else 'controls.zip'
        apath = folder + '/' + archive_name
        require(pin(ROOT / apath) == proof['archive'], 'Frozen component archive differs: ' + name)
        artifacts[apath] = proof['archive']
        for n, wanted in proof.get('source_guards', proof.get('source_pins', {})).items():
            require(pin(ROOT / n) == rawpin(git('show', BASE + ':' + n)) == wanted,
                    'Qualified component source changed: ' + n)
            require(n not in sources or sources[n] == wanted, 'Conflicting component source guards')
            sources[n] = wanted
        for n, wanted in proof.get('fixture_guards', {}).items():
            require(pin(ROOT / n) == wanted, 'Native fixture changed: ' + n)
            artifacts[n] = wanted
    reports = dict(rna=rna.audit(out), native_preprocessing=native.verify())
    # The historical mains also require an unrelated mutable build/cli JAR.
    # It was not retained at this checkpoint. Do not edit/overlay their logic,
    # invent a replacement hash, or call this a successful full historical audit.
    # Independently validate their exact retained scientific sources/archives;
    # qualification() additionally bridges exact frozen method JARs to today's JAR.
    from signac_scan_evidence import unpack
    for name, script in MARKERS.items():
        folder = ROOT / COMPONENTS[name]
        files = unpack(folder / 'controls.zip', proofs[name])
        for n, wanted in proofs[name]['source_pins'].items():
            require(rawpin(files['current-source/' + n]) == wanted, 'Archived marker source differs: ' + n)
        reports[name] = dict(status='PASS_RETAINED_SCIENTIFIC_SOURCE_AND_COMPLETE_MARKER_ARCHIVE_ONLY',
            logical_files=len(files), historical_full_verifier_executed=False,
            current_statistical_refit=False, original_qualification_scope=proofs[name]['status'])
        payload['public-checks/' + name + '.json'] = encoded(reports[name])
        sources[script] = pin(ROOT / script)
    legacy = proofs['markers']['canonical_executable_preserved']
    require(legacy == proofs['native_marker_summaries']['canonical_executable_preserved'],
            'Historical canonical identity differs between marker qualifications')
    sources['tools/literature/seurat_workflow/verify.py'] = pin(Path(rna.__file__))
    sources['tools/literature/seurat_preprocessing/verify.py'] = pin(Path(native.__file__))
    for n in sources:
        payload['qualified-project-source/' + n] = (ROOT / n).read_bytes()
    q = dict(status='PASS_RNA_NATIVE_AND_RETAINED_MARKER_EVIDENCE_WITH_HISTORICAL_AUDIT_SKIPPED',
             reports=reports, source_guards=sources, artifact_guards=artifacts,
             historical_full_marker_audit=dict(status='SKIP3_MISSING_EXACT_HISTORICAL_CANONICAL_JAR',
                 expected=legacy, path='build/cli/omicsmatic-0.3.7.jar',
                 affected_scope='Historical full offline marker-verifier mains only; current RNA/native audits and explicit method-JAR identity checks are separate.',
                 required_action='Restore the original canonical JAR with exactly this byte count/SHA-256 to rerun those unchanged historical mains; otherwise leave them skipped. Do not replace it with the different published release or refresh old hashes.'),
             original_R_reexecuted=False,
             new_JVM_tests_executed=False, whole_tool_qualified=False, whole_paper_reproduced=False)
    payload['public-checks/RESULTS.json'] = encoded(q)
    return q, payload


def method_bridge(artifact_root, candidate, payload):
    """Bind exact retained, originally tested marker bytecode to the current JAR."""
    rows = {}
    with zipfile.ZipFile(candidate) as current:
        require(len(current.namelist()) == len(set(current.namelist())), 'Duplicate current JAR members')
        for name in MARKERS:
            proof = json.loads((ROOT / COMPONENTS[name] / 'VALIDATION.json').read_bytes())
            artifact = Path(artifact_root) / proof['artifact']['path']
            require(pin(artifact) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']},
                    'Supply the exact frozen marker development JAR: ' + str(artifact))
            with zipfile.ZipFile(artifact) as original:
                require(len(original.namelist()) == len(set(original.namelist())), 'Duplicate frozen marker JAR members')
                selected = proof['new_entries'] if name == 'markers' else proof['new_class_entries']
                require(selected, 'Frozen marker JAR contains no selected method classes')
                for n in selected:
                    rna.safe_name(n)
                    raw = original.read(n)
                    require(current.read(n) == raw, 'Current marker method differs from tested original: ' + n)
                    key = 'tested-marker-methods/' + name + '/' + n
                    payload[key] = raw
                rows[name] = dict(frozen_artifact=pin(artifact), current_method_payloads={n: rawpin(original.read(n)) for n in selected})
    return rows


def qualification(out, candidate, parent, java, artifact_root):
    public, payload = public_qualification(Path(out) / 'component-checks')
    require(candidate is not None and parent is not None and java is not None,
            'Plan/apply require the exact qualified --candidate-jar, --parent-jar and --java')
    checks = Path(out) / 'reader-checks'
    checks.mkdir()
    report = rna.audit(checks, Path(candidate), Path(parent), Path(java))
    require(report['prior_JAR_members_checked_now'] == 23063 and report['java_capture_verifications_now'] == 7,
            'All original parent payloads and seven actual Java capture audits are required')
    payload['reader-checks/RESULTS.json'] = encoded(report)
    for p in checks.iterdir():
        if p.is_file():
            payload['reader-checks/' + p.name] = p.read_bytes()
    bridge = method_bridge(artifact_root, candidate, payload)
    q = dict(status='PASS_CURRENT_SEURAT_SOURCES_ARCHIVES_AND_FROZEN_JAVA_READER',
             public=public, reader_audit=report, reader=pin(candidate), parent=pin(parent),
             marker_method_identity=bridge,
             original_R_reexecuted=False, historical_author_runtime_executed=False,
             complete_author_workflow_qualified=False, whole_tool_qualified=False,
             whole_paper_reproduced=False, new_family_credit=0)
    return q, payload


def verify_qualification(q, packet, out, expected_packet):
    require(q['status'] == 'PASS_CURRENT_SEURAT_SOURCES_ARCHIVES_AND_FROZEN_JAVA_READER',
            'Different Seurat qualification scope')
    public, _ = public_qualification(Path(out) / 'component-checks')
    require(q['public'] == public, 'Current unchanged component qualification differs')
    count = packet_read(packet, expected_packet)
    with zipfile.ZipFile(packet) as archive:
        require(json.loads(archive.read('public-checks/RESULTS.json')) == public,
                'Archived public component qualification differs')
        report = json.loads(archive.read('reader-checks/RESULTS.json'))
        require(report == q['reader_audit'] and report['prior_JAR_members_checked_now'] == 23063
                and report['java_capture_verifications_now'] == 7, 'Retained Java reader audit differs')
        with zipfile.ZipFile(ROOT / COMPONENTS['rna'] / 'qualification-evidence.zip') as original:
            package = json.loads(original.read('qualification/PACKAGE.json'))
        require(q['reader'] == package['candidate'] and q['parent'] == package['parent'],
                'Frozen reader JAR lineage differs')
        for n, wanted in public['source_guards'].items():
            require(rawpin(archive.read('qualified-project-source/' + n)) == wanted,
                    'Archived current scientific source differs: ' + n)
        require(set(q['marker_method_identity']) == set(MARKERS), 'Marker method identity population differs')
        for name in MARKERS:
            require(json.loads(archive.read('public-checks/' + name + '.json')) == public['reports'][name],
                    'Retained marker archive audit differs')
            bridge = q['marker_method_identity'][name]
            original_proof = json.loads((ROOT / COMPONENTS[name] / 'VALIDATION.json').read_bytes())
            require(bridge['frozen_artifact'] == {k: original_proof['artifact'][k] for k in ['bytes', 'sha256']}
                    and bridge['current_method_payloads'], 'Marker artifact lineage differs')
            expected_names = original_proof['new_entries'] if name == 'markers' else original_proof['new_class_entries']
            require(set(bridge['current_method_payloads']) == set(expected_names), 'Marker method payload population differs')
            for n, wanted in bridge['current_method_payloads'].items():
                require(rawpin(archive.read('tested-marker-methods/' + name + '/' + n)) == wanted,
                        'Archived tested marker class differs: ' + n)
        for i in range(7):
            require('reader-checks/java-' + str(i) + '.stdout' in archive.namelist()
                    and 'reader-checks/java-' + str(i) + '.stderr' in archive.namelist(),
                    'Missing genuine reader console')
    require(all(q.get(k) is False for k in ['original_R_reexecuted', 'historical_author_runtime_executed',
            'complete_author_workflow_qualified', 'whole_tool_qualified', 'whole_paper_reproduced']),
            'Unproved author runtime or whole-tool claim')
    return dict(public=public, qualification_packet_payloads=count, actual_JAR_reexecuted_now=False)
