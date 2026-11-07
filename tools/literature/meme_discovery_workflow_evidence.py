# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline source, complete control and artifact audit for the scoped MEME discovery workflow."""
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools/literature'))
from validate_mashr_native import pin
from signac_scan_evidence import byte_pin, unpack
from catalog_source_integrity import verify_source


def verify(folder=None):
    folder = Path(folder).resolve() if folder else ROOT/'docs/literature/meme-discovery-workflow-development'
    assert folder.is_relative_to(ROOT)
    proof = json.loads((folder/'VALIDATION.json').read_bytes())
    assert proof['schema_version'] == 1 and proof['status'] == 'passed-scoped-MEME-discovery-workflow-development'
    assert pin(folder/'controls.zip') == proof['archive'] and pin(folder/'WARNINGS.md') == proof['warnings_document']
    files = unpack(folder/'controls.zip', proof)
    for name,wanted in proof['source_pins'].items():
        verify_source(ROOT,ROOT/'docs/literature/omics-2023-2026',name,wanted['sha256'])
        assert byte_pin(files['current-source/'+name]) == wanted, name
    prefix = 'build/literature-review/meme-runtime/'
    def record(name): return json.loads(files[prefix+name])
    parent, backend, root = [json.loads(files['artifact-entries/'+n+'.json']) for n in ['native_parent','backend','root']]
    build = record('workflow-build-16/COMPLETED.json'); route = record('root-build-17/COMPLETED.json')
    assert set(parent) <= set(backend) and all(parent[n] == backend[n] for n in parent)
    assert set(backend)-set(parent) == set(build['additions']) and root.keys() == backend.keys()
    assert [n for n in root if root[n] != backend[n]] == ['org/jlinalg/cli/JLinAlgCli.class']
    assert proof['artifact_chain'] == dict(native_parent=build['parent_artifact'],backend=build['artifact'],root=route['artifact'])
    assert route['parent_artifact'] == build['artifact'] and route['artifact'] == proof['artifact']
    assert pin(ROOT/'docs/literature/meme-discovery-sites-development/VALIDATION.json') == proof['parent_native_validation']
    for name,artifact in proof['artifact_chain'].items():
        path = ROOT/artifact['path']
        if path.exists():
            assert pin(path) == {k:artifact[k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as archive:
                assert {n:dict(byte_pin(archive.read(n)),directory=archive.getinfo(n).is_dir()) for n in archive.namelist()} == json.loads(files['artifact-entries/'+name+'.json'])
                if name == 'root':
                    for logical,raw in files.items():
                        if logical.startswith('candidate-owned-entries/'): assert archive.read(logical.removeprefix('candidate-owned-entries/')) == raw
    matrix = record('workflow-contracts-16-2/COMPLETED.json')
    assert matrix['artifact'] == build['artifact'] and matrix['tests_per_JVM'] == 91 and len(matrix['JVMs']) == 3
    for row in matrix['JVMs']:
        assert row['tests'] == 91 and row['exit_code'] == row['skipped'] == 0
        log = files[prefix+'workflow-contracts-16-2/'+row['JVM']+'.stdout'].decode('utf8')
        assert '91 tests successful' in log and '0 tests failed' in log and '0 tests skipped' in log
    profiles = set(proof['exact_profiles']); assert len(profiles) == 4
    for name,empty in [('user-workflows-11-1',False),('empty-user-workflows-16-1',True)]:
        batch = record(name+'/COMPLETED.json')
        assert set(batch['profiles']) == profiles and batch['original_analyses'] == 4 and batch['portable_verifications'] == 12
        if empty: assert batch['artifact'] == build['artifact'] and batch['expected_zero_motifs']
        assert len(batch['Windows_records']) == 8 and all(r['exit_code'] == 0 for r in batch['Windows_records'])
        for profile in profiles:
            base = name+'/'+profile+'/'
            assert record(base+'discover.process.json')['exit_code'] == record(base+'verify-linux.process.json')['exit_code'] == 0
            assert record(base+'result/OUTPUT-AUDIT.json')['motifs'] == (0 if empty else 2)
            assert record(base+'result/COMPLETED.json')['analysis_admitted']
    example = record('root-example-17-1/COMPLETED.json')
    assert example['artifact'] == proof['artifact'] and example['original_analyses'] == 1 and example['portable_verifications'] == 3
    root_checks = record('root-modern-Windows-checks-17-1/COMPLETED.json')
    assert root_checks['artifact'] == proof['artifact'] and root_checks['cases'] == 16 and all(r['exit_code'] == r['expected_exit'] for r in root_checks['records'])
    recovery = record('workflow-recovery-17-1/COMPLETED.json')
    assert recovery['artifact'] == proof['artifact'] and recovery['expected_missing_runtime_skips'] == 2 and recovery['reminted_corruption_refusals'] == 36
    assert recovery['refused_failed_bundles'] == 2 and len(recovery['records']) == 40 and all(r['exit_code'] == r['expected_exit'] for r in recovery['records'])
    diagnostics = record('diagnostic-paths-after-13-1/COMPLETED.json')
    assert len(diagnostics['records']) == 2 and all(r['exact_path_preserved'] and r['prominent_failure'] for r in diagnostics['records'])
    for name in ['empty-original-source-13-1','empty-original-source-16-1']:
        original = record(name+'/COMPLETED.json')
        assert original['profiles'] == 4 and original['original_exits'] == [0]*4 and original['original_motif_counts'] == [0]*4
        assert original['public_runtime_or_analysis_admitted'] is False
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    assert (proof['original_positive_analyses'],proof['original_empty_analyses'],proof['portable_verifications']) == (5,4,27)
    for name in ['source_fit_independently_reimplemented','source_significance_independently_reimplemented','historical_whole_suite','whole_paper_reproduced',
                 'biological_validity_certified','execution_authentication','released','pushed','deployed','whole_goal_complete']:
        assert proof[name] is False, name
    return proof


if __name__ == '__main__':
    try:
        if not __debug__: raise RuntimeError('Never disable source/artifact/control checks')
        proof = verify()
        print(json.dumps(dict(status=proof['status'], tests_per_JVM=proof['tests_per_JVM'], positive_analyses=5, empty_analyses=4, portable_verifications=27, whole_goal_complete=False)))
    except Exception as failure:
        print('MEME WORKFLOW FAIL2: '+str(failure)+'\nREQUIRED ACTION: preserve the evidence and correct the named source, artifact or complete control mismatch; no partial evidence is admitted.', file=sys.stderr)
        raise SystemExit(2)
