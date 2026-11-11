# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Portable native prediction evidence, complete independent memberships and primary-source bindings."""
import gzip
import hashlib
import io
import json
import re
import tarfile
import xml.etree.ElementTree as ET
import zipfile
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack, byte_pin
from catalog_source_integrity import verify_source, reverse_delta
from validate_cicero_prediction import audit_bytes
from collect_omics_review import paragraphs

BASE = 'build/literature-review/monocle-runtime/'
FIT = 'build/literature-review/fitdistrplus-runtime/'


def verify():
    folder = ROOT / 'docs/literature/cicero-prediction-development'
    data = ROOT / 'docs/literature/omics-2023-2026'
    p = json.loads((folder / 'VALIDATION.json').read_bytes())
    assert p['status'] == 'passed-native-Cicero-prediction-reuse-and-source-discovery-controls'
    assert p['workflow_admitted'] and not p['implementation_credit'] and not p['whole_goal_complete']
    for line in (folder / 'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest, name = line.split('  ', 1)
        assert name in ['controls.zip', 'WARNINGS.md', 'VALIDATION.json'] and pin(folder / name)['sha256'] == digest
    assert pin(folder / 'controls.zip') == p['archive'] and pin(folder / 'WARNINGS.md') == p['warnings_document']
    files = unpack(folder / 'controls.zip', p)
    for name, value in p['source_pins'].items():
        verify_source(ROOT, data, name, value['sha256'])
        assert byte_pin(files['current-source/' + name]) == value
    for name, value in p['parent_proofs'].items():
        assert pin(ROOT / name) == value
    get = lambda name: json.loads(files[name])
    build = get(BASE + 'cicero-prediction-build-3/COMPLETED.json')
    controls = get(BASE + 'cicero-prediction-validation-2/COMPLETED.json')
    example = get(BASE + 'cicero-prediction-example-1/COMPLETED.json')
    assert all(proof['artifact'] == p['artifact'] for proof in [build, controls, example])
    assert build['parent_artifact'] == p['parent_artifact']
    before = get('artifact-parent-entries.json')
    after = get('artifact-candidate-entries.json')
    assert all(after[name] == value for name, value in before.items())
    assert len(before) == p['unchanged_parent_entries'] and set(after) - set(before) == set(p['added_entries'])
    for name in p['added_entries']:
        assert byte_pin(files['candidate-owned-entry/' + name]) == after[name]
    for label, key in [('parent', 'parent_artifact'), ('candidate', 'artifact')]:
        path = ROOT / p[key]['path']
        if path.exists():
            assert pin(path) == {k: p[key][k] for k in ['bytes', 'sha256']}
            with zipfile.ZipFile(path) as archive:
                assert len(archive.namelist()) == len(set(archive.namelist()))
                assert {n: byte_pin(archive.read(n)) for n in archive.namelist()} == (before if label == 'parent' else after)
    expected_causes = {
        'duplicate-accessible': 'Duplicate accessible peak ID', 'unknown-source': 'Unknown source peak ID',
        'unknown-target': 'Unknown target peak ID', 'duplicate-prediction': 'Duplicate prediction ID',
        'malformed-interval': 'Invalid interval', 'mixed-endings': 'Mixed line endings refused',
        'input-capacity': 'oversized Nextflow file', 'row-capacity': 'Input exceeds maximum_rows',
        'decision-capacity': 'Complete membership family exceeds maximum_decisions',
        'operation-capacity': 'Accessibility operation budget exceeded', 'undeclared-cell': 'undeclared cell type',
        'output-table-capacity': 'Generated tables exceed maximum_output_table_bytes',
        'late-operation-capacity': 'Accessibility operation budget exceeded'}
    total_decisions, total_retained, successful = 0, 0, 0
    assert len(controls['JVMs']) == 3
    for jvm in controls['JVMs']:
        assert jvm['tests'] == 7 and jvm['skipped'] == 0 and len(jvm['protocols']) == 19
        prefix = BASE + 'cicero-prediction-validation-2/' + jvm['JVM'] + '/'
        process = get(prefix + 'contracts.process.json')
        assert process['exit_code'] == process['expected'] == 0
        for control in jvm['protocols']:
            root = prefix + control['id'] + '/result/'
            state = get(root + 'analysis-status.json')
            assert state['result_admitted'] is control['result_admitted'] is (control['exit_code'] == 0)
            assert any(n.startswith(root + 'results/') for n in files) is state['result_admitted']
            assert 'REQUIRED ACTION:' in files[root + 'WARNINGS.md'].decode('utf8')
            if state['result_admitted']:
                report = audit_bytes({name[len(root):]: raw for name, raw in files.items() if name.startswith(root)})
                assert all(control[k] == v for k, v in report.items())
                total_decisions += report['decisions']
                total_retained += report['retained']
                successful += 1
            else:
                assert state['status'] == 'FAIL2_NO_RESULT_ADMITTED' and expected_causes[control['id']] in state['cause']
                if control['id'] == 'late-operation-capacity':
                    assert control['earlier_completed_cell_types_rejected'] == 2
                    for ordinal in [1, 2]:
                        name = 'cell-type-%04d.tsv' % ordinal
                        assert files[root + 'unadmitted-partial-results/' + name] == files[prefix + 'source-only/result/results/' + name]
    root = BASE + 'cicero-prediction-example-1/result/'
    report = audit_bytes({name[len(root):]: raw for name, raw in files.items() if name.startswith(root)})
    assert report == example['comparison'] and report['decisions'] == 9 and report['retained'] == 3
    total_decisions += report['decisions']
    total_retained += report['retained']
    successful += 1
    assert (successful, total_decisions, total_retained) == (p['successful_families'], p['independent_membership_decisions'], p['retained_prediction_rows'])
    assert successful == 19 and total_decisions == 43317
    roles = get(BASE + 'cicero-prediction-source-review-1/COMPLETED.json')
    assert len(roles['primary_pointers']) == 3 and roles['fitdistrplus_catalog_registration_pending']
    scan = get(FIT + 'source-role-scan-2/COMPLETED.json')
    assert len(scan['source_records']) == len({r['pmcid'] for r in scan['source_records']}) == 250
    assert scan['named_papers'] == 1 and len(scan['matches']) == 1
    assert (scan['matches'][0]['paper_id'], scan['matches'][0]['paragraph_index']) == ('P043', 51)
    pattern = re.compile(r'(?<![A-Za-z0-9_])fitdistrplus(?![A-Za-z_])', re.I)
    live_hits = []
    cache = ROOT / 'build/literature-review/xml'
    available = all((cache / (row['pmcid'] + '.xml.gz')).exists() for row in scan['source_records'])
    if available:
        for source in scan['source_records']:
            raw = gzip.decompress((cache / (source['pmcid'] + '.xml.gz')).read_bytes())
            assert hashlib.sha256(raw).hexdigest() == source['xml_sha256']
            xml = ET.fromstring(raw)
            assert next(e.text for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type') == 'doi').lower() == source['doi'].lower()
            for paragraph in paragraphs(xml):
                digest = hashlib.sha256(paragraph['text'].encode('utf8')).hexdigest()
                if pattern.search(paragraph['match_text']) or pattern.search(paragraph['text']):
                    live_hits.append(dict(paper_id=source['paper_id'], pmcid=source['pmcid'], doi=source['doi'], paragraph_index=paragraph['paragraph_index'], section=paragraph['section'], paragraph_sha256=digest))
                for pointer in roles['primary_pointers']:
                    if pointer['paper_id'] == source['paper_id'] and pointer['paragraph_index'] == paragraph['paragraph_index']:
                        assert pointer['paragraph_sha256'] == digest and pointer['xml_sha256'] == source['xml_sha256']
        assert live_hits == scan['matches']
    intake = get(FIT + 'source-intake-1/COMPLETED.json')
    assert byte_pin(files['current-source/tools/literature/intake_fitdistrplus_source.py']) == intake['source_pin']
    archive = files[FIT + 'source-intake-1/fitdistrplus_1.2-6.tar.gz']
    assert byte_pin(archive) == intake['archive'] and hashlib.md5(archive).hexdigest() == intake['repository_MD5']
    index = files[FIT + 'source-intake-1/PACKAGES.gz']
    assert byte_pin(index) == intake['index']
    record = next(row for row in gzip.decompress(index).decode('utf8').split('\n\n') if row.startswith('Package: fitdistrplus\n'))
    assert 'Version: 1.2-6\n' in record and 'MD5sum: ' + intake['repository_MD5'] in record
    members = {}
    with tarfile.open(fileobj=io.BytesIO(archive), mode='r:gz') as original:
        for member in original.getmembers():
            if member.isfile():
                name = member.name.removeprefix('fitdistrplus/')
                value = original.extractfile(member).read()
                assert files[FIT + 'source-intake-1/source/' + name] == value
                members[name] = byte_pin(value)
    assert members == intake['source_members'] and len(members) == 165
    assert not intake['installation_performed'] and not intake['analysis_admitted'] and not intake['paper_version_known']
    assert FIT + 'source-role-scan-1/DIAGNOSIS.txt' in files and BASE + 'cicero-prediction-build-1/DIAGNOSIS.txt' in files
    delta = get('current-source/docs/literature/omics-2023-2026/cicero-prediction-checkout-shared-source-compatibility.json')
    assert len(delta['paths']) == 1 and delta['paths'][0]['path'] == '.gitattributes'
    reverse_delta(files['current-source/.gitattributes'], delta['paths'][0])
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    assert p['canonical_executable_unchanged'] == build['canonical_executable_unchanged']
    if canonical.exists():
        assert pin(canonical) == p['canonical_executable_unchanged']
    print('Verified native Cicero reuse: 43,317 independent memberships, 57 runtime protocols, seven contracts per JVM, exact retained row bytes, primary source roles and 165 official fitdistrplus files; full XML cache ' + ('rechecked' if available else 'unavailable, sealed source receipts checked'))
    return p


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/decision checks')
    verify()
