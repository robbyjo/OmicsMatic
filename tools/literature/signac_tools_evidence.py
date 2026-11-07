# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline integrity and scope checks for the public Signac interface and frozen parents."""
import gzip
import io
import json
import math
import zipfile
from setup_deseq2_reference import ROOT, pin
from catalog_source_integrity import verify_source
from signac_scan_evidence import unpack, byte_pin

PREFIX = 'build/literature-review/signac-runtime/'
WORKFLOWS = {'normalize','lsi','fragments','annotations','motifs','scan','links','markers','peaks','coverage','chromvar'}
PARENTS = ['signac-normalization-development','signac-lsi-development','signac-fragments-development',
    'signac-annotation-development','signac-motif-development','signac-scan-development','signac-link-development',
    'seurat-differential-development','signac-peaks-development','signac-coverage-development','chromvar-tools-development']


def parent_bindings():
    records = {}
    source_checks = 0
    for name in PARENTS:
        folder = ROOT/'docs/literature'/name
        proof = json.loads((folder/'VALIDATION.json').read_bytes())
        assert pin(folder/'controls.zip') == proof['archive'], name
        for path, value in proof['source_pins'].items():
            verify_source(ROOT, ROOT/'docs/literature/omics-2023-2026', path, value['sha256'])
            source_checks += 1
        records[name] = dict(validation=pin(folder/'VALIDATION.json'), archive=pin(folder/'controls.zip'))
    assert source_checks == 1373
    return records, source_checks


def verify(emit=False):
    folder = ROOT/'docs/literature/signac-tools-development'
    proof = json.loads((folder/'VALIDATION.json').read_bytes())
    assert proof['schema_version'] == 1 and proof['status'] == 'passed-public-scoped-Signac-tools-workflows'
    assert pin(folder/'controls.zip') == proof['archive'] and pin(folder/'WARNINGS.md') == proof['warnings_document']
    files = unpack(folder/'controls.zip', proof)
    data = ROOT/'docs/literature/omics-2023-2026'
    for name, wanted in proof['source_pins'].items():
        assert byte_pin(files['current-source/'+name]) == wanted
        verify_source(ROOT, data, name, wanted['sha256'])
    parents, count = parent_bindings()
    assert parents == proof['parents'] and count == proof['parent_source_checks']
    def record(name):
        return json.loads(files[PREFIX+name])
    native = record('tools-native-matrix-2/COMPLETED.json')
    assert native['tests_per_JVM'] == 1679 and len(native['JVMs']) == 3
    assert all(r['tests'] == r['passed'] == 1679 and r['skipped'] == 0 for r in native['JVMs'])
    for row in native['JVMs']:
        process = record('tools-native-matrix-2/'+row['JVM']+'.process.json')
        assert process['exit_code'] == 0
        text = files[PREFIX+'tools-native-matrix-2/'+row['JVM']+'.stdout'].decode('utf8')
        assert '1679 tests successful' in text and '0 tests failed' in text and '0 tests skipped' in text
    entries = record('tools-native-matrix-2/parent-entries.json')
    assert entries['changed'] == ['org/jlinalg/cli/JLinAlgCli.class']
    assert entries['added'] and all(n.startswith(('org/jlinalg/cli/SignacToolsCli','org/jlinalg/cli/SignacNormalizationCli',
        'org/jlinalg/omics/signac-normalize/','org/jlinalg/omics/signac-tools/')) for n in entries['added'])
    matrix = record('tools-public-workflows-3/COMPLETED.json')
    assert matrix['status'] == 'passed-real-public-Signac-workflow-matrix'
    assert matrix['artifact'] == native['artifact'] == {k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert (len(matrix['analyses']),matrix['completed_analyses'],matrix['successful_portable_verifications'],
        matrix['expected_missing_runtime_skips'],matrix['expected_failures']) == (17,21,51,2,2)
    assert {r['workflow'] for r in matrix['analyses']} == WORKFLOWS
    assert len(matrix['processes']) == 76
    for row in matrix['processes']:
        assert row == record('tools-public-workflows-3/'+row['label']+'.process.json')
        assert row['exit_code'] == row['expected_exit_code']
        assert '-jar' in row['command'] and 'signac-tools' in row['command']
    for row in matrix['analyses']:
        prefix = PREFIX+'tools-public-workflows-3/'+row['label']+'-result/'
        state = json.loads(files[prefix+'analysis-status.json'])
        assert state['status'] in {'completed','completed-with-warnings'} and state.get('results_admitted',True) is True
    # Independently recheck all retained public normalization values and sparse coordinates.
    fixture = files['current-source/src/test/resources/org/jlinalg/sequence/signac-normalization/original-controls.zip']
    numeric_fields = nonfinite = 0
    with zipfile.ZipFile(io.BytesIO(fixture)) as original:
        name = 'hand-count-versus-document-frequency/'
        reference = [r.split('\t') for r in original.read(name+'normalized.tsv').decode().splitlines()]
        selections = [r.split('\t') for r in original.read(name+'selections.tsv').decode().splitlines()]
        labels = [r['label'] for r in matrix['analyses'] if r['workflow']=='normalize'] + [r['label'] for r in matrix['additional_native_platform_runs']]
        assert len(labels) == 11
        for label in labels:
            request = record('tools-public-workflows-3/'+label+'.json')
            prefix = PREFIX+'tools-public-workflows-3/'+label+'-result/'
            spec = request['selection']
            selected = [r for r in selections if r[0]==spec['kind'] and float(r[1])==spec.get('value',0)]
            assert len(selected) == 1
            assert files[prefix+'variable-features.tsv'].decode('utf8').splitlines() == (selected[0][3].split(',') if selected[0][3] else [])
            for key in ['features.tsv','cells.tsv']:
                assert files[prefix+key] == original.read(name+key)
            if request['mode'] != 'NORMALIZE':
                assert prefix+'normalized.mtx' not in files
                continue
            rows = [r for r in reference if int(r[0])==request.get('tfidf_method',1) and float(r[1])==request.get('scale_factor',10000) and (r[2]=='true')==('idf' in request)]
            assert len(rows) == 1
            expected = [float(x.replace('Inf','inf')) for x in rows[0][3].split(',')]
            matrix_rows = [r.split() for r in files[prefix+'normalized.mtx'].decode().splitlines() if not r.startswith('%')]
            assert list(map(int,matrix_rows[0])) == [3,3,5]
            assert [list(map(int,r[:2])) for r in matrix_rows[1:]] == [[1,1],[2,1],[1,2],[3,2],[3,3]]
            actual = [float(r[2].replace('Inf','inf')) for r in matrix_rows[1:]]
            assert len(expected)==len(actual)==5
            for a,b in zip(actual,expected):
                if math.isfinite(b):
                    assert math.isfinite(a) and abs(a-b)<=max(16*math.ulp(b),abs(b)*3e-13)
                else:
                    assert a==b
                    nonfinite+=1
                numeric_fields+=1
    assert numeric_fields == 40 and nonfinite == 9
    for name, code in [('missing-r',3),('missing-macs',3),('missing-counts',2),('wrong-workflow-verify',2)]:
        assert record('tools-public-workflows-3/'+name+'.process.json')['exit_code'] == code
    index = json.loads(files['current-source/src/main/resources/org/jlinalg/omics/signac-tools/WORKFLOWS.json'])
    assert {r['name'] for r in index['workflows']}==WORKFLOWS and len(index['workflows'])==11
    assert not index['analysis_installs_software'] and not index['reinterprets_request_paths_or_scientific_settings']
    assert not index['duplicate_chromVAR_implementation_credit']
    roles = json.loads(files['current-source/docs/literature/omics-2023-2026/signac-role-coverage.json'])
    assert roles['applied'] and roles['reviewed_roles']==10 and roles['recovered_analysis_captions']==['P134']
    assert roles['prior_reviews_preserved']==880 and roles['total_reviews']==890
    for name in ['tools-test-attempt-1/dispatch-tests.xml','tools-public-workflows-1/attempted-validator.py',
        'primary-role-curation-dry-1/attempted-caption-guard.py']:
        assert PREFIX+name in files
    canonical = ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():
        assert pin(canonical)==proof['canonical_executable_preserved']
    artifact = ROOT/proof['artifact']['path']
    if artifact.exists():
        assert pin(artifact)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert all(proof[k] is False for k in ['analysis_installed_software','historical_whole_packages',
        'independent_all_upstream_fit_replay','PNG_pixel_replay','execution_authentication','biological_validity_certified',
        'whole_paper_reproduced','full_project_test_suite_run','catalog_implementation_credit','released','pushed','deployed','whole_goal_complete'])
    if emit:
        print(json.dumps(dict(status='passed-public-Signac-tools-offline-evidence',tests_per_JVM=1679,JVMs=3,
            public_analyses=21,portable_verifications=51,expected_skips=2,expected_failures=2,
            normalization_coordinates_compared=numeric_fields,nonfinite_coordinates_retained=nonfinite,
            parent_source_checks=count,logical_files=proof['logical_files'])))
    return proof
