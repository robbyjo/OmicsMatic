# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only frozen-byte and bounded-evidence verification; does not rerun scientific analysis."""
import hashlib, json, stat, sys, zipfile
from pathlib import PurePosixPath
from setup_deseq2_reference import ROOT, pin


def main():
    data = ROOT / 'docs/literature/nfcore-captured-development'
    proof = json.loads((data / 'VALIDATION.json').read_bytes())
    assert proof['status'] == 'PASS_CAPTURED_PROCESS_AND_NATIVE_COMPONENT_CHECKPOINT_ONLY'
    assert proof['claims'] == dict(nfcore_tool_credit=0, total_new_integrations=99, native=19,
        upstream_adapters=80, including_three_existing=102, missing=244, nfcore_status='missing',
        whole_goal_complete=False, full_nfcore_adapter=False, whole_pipeline_scientific_admission=False,
        biological_accuracy_certified=False, paper_reproduction=False)
    expected = set(proof['artifacts']) | {'controls.zip', 'VALIDATION.json'}
    seen = set()
    for line in (data / 'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest, name = line.split('  ', 1)
        assert name in expected and name not in seen and pin(data / name)['sha256'] == digest
        seen.add(name)
    assert seen == expected
    for n, identity in proof['artifacts'].items(): assert pin(data / n) == identity
    assert pin(data / 'controls.zip') == proof['archive']
    for n, identity in proof['source_pins'].items(): assert pin(ROOT / n) == identity, ('Source differs', n)
    for n, identity in proof['catalog_unchanged'].items(): assert pin(ROOT / n) == identity, ('Catalog differs', n)
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    foundation = ROOT / 'docs/literature/nfcore-engine-development'
    assert pin(foundation / 'VALIDATION.json') == proof['predecessor_proof']
    assert len(proof['matrix']['controls']) == 15 and all(r['passed'] for r in proof['matrix']['controls'])
    assert len(proof['integrity']['executions']) == 3 and len(proof['integrity']['saved_verification']) == 12
    assert all(r['passed'] for r in proof['integrity']['saved_verification'])
    assert len(proof['mappings']['cases']) == 11 and len(proof['frip']['cases']) == 8
    assert len(proof['identity']['platforms']) == 6
    assert len(proof['components']['cases']) == 4 and proof['components']['exact_reports_agree']
    assert len(proof['validator_errors']['controls']) == 2 and all(r['exit_code'] == 2 and r['prominent_required_actions'] for r in proof['validator_errors']['controls'])
    assert proof['execution_intake']['output_files'] == 367 and not proof['execution_intake']['scientific_result_admitted']
    with zipfile.ZipFile(data / 'controls.zip') as z:
        infos = z.infolist(); names = [i.filename for i in infos]
        assert len(names) == len(set(names)) and set(names) == set(proof['archive_files']) | set(proof['archive_directories'])
        for item in infos:
            name = item.filename; p = PurePosixPath(name)
            assert not p.is_absolute() and '..' not in p.parts and '\\' not in name
            assert str(p) + ('/' if item.is_dir() else '') == name and not stat.S_ISLNK(item.external_attr >> 16)
            raw = z.read(item)
            if item.is_dir(): assert raw == b''
            else: assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == proof['archive_files'][name]
        for n, identity in proof['source_pins'].items(): assert proof['archive_files']['sources/' + n] == identity
        for key, folder in [('matrix', 'captured-engine-matrix-3'), ('integrity', 'captured-engine-integrity-1'),
                ('mappings', 'mapping-controls-2'), ('frip', 'frip-controls-2'),
                ('identity', 'scientific-input-identity-controls-3'), ('components', 'scientific-cross-platform-4')]:
            assert json.loads(z.read('evidence/' + folder + '/COMPLETED.json')) == proof[key]
        reports = [json.loads(z.read('evidence/scientific-cross-platform-4/' + n + '/audit.json'))
            for n in ['windows21', 'windows25', 'linux21-compiler21', 'linux21-compiler25']]
        assert all(d == reports[0] for d in reports) and not reports[0]['scientific_result_admitted']
        assert len(reports[0]['complete_fastq_streams']) == 6 and len(reports[0]['BAM_and_FRiP']) == 6
        assert all(r['read1_records'] == 116195 and r['read2_records'] == 116195 for r in reports[0]['complete_fastq_streams'])
        assert sum(r['source_frip_applicable'] for r in reports[0]['BAM_and_FRiP']) == 4
        for n in ['windows21-named-capacity', 'windows21-BAM-capacity', 'windows25-named-capacity', 'windows25-BAM-capacity']:
            failure = json.loads(z.read('evidence/scientific-cross-platform-4/' + n + '/FAILURE.json'))
            assert failure['status'] == 'FAIL2_NO_COMPONENT_RESULT'
        for compiler in ['21', '25']:
            with zipfile.ZipFile(foundation / 'nfcore-engine-javac21.jar') as old, zipfile.ZipFile(data / ('nfcore-captured-javac' + compiler + '.jar')) as captured, zipfile.ZipFile(data / ('nfcore-chipseq121-audit-javac' + compiler + '.jar')) as scientific:
                for name in old.namelist():
                    if name != 'META-INF/MANIFEST.MF': assert captured.read(name) == old.read(name) == scientific.read(name)
                build = proof['builds']['scientific' + compiler]
                for key in ['external_java_members', 'existing_native_java_members']:
                    for name, identity in build[key].items():
                        raw = scientific.read(name)
                        assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == identity
    print('PASS frozen captured-process and native ChIP-seq component checkpoint; nf-core pending;0 new tool credit.', flush=True)
    return 0


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable frozen evidence, source or bounded-claim checks')
    try: raise SystemExit(main())
    except Exception as error:
        print('FAIL2 NO RESULT: ' + repr(error) + '\nRequired action: preserve historical evidence, inspect the named discrepancy and make a new validated checkpoint. Do not rewrite old hashes to hide changes.', file=sys.stderr)
        raise SystemExit(2)
