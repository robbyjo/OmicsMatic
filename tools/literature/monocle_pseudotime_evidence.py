# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit complete sealed native pseudotime evidence without installing or running upstream software."""
import gzip
import json
import sys
import zipfile
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source


def verify():
    folder=ROOT/'docs/literature/monocle3-pseudotime-development';data=ROOT/'docs/literature/omics-2023-2026'
    proof=json.loads((folder/'VALIDATION.json').read_bytes())
    assert proof['status']=='passed-native-Monocle3-prepared-pseudotime-component'
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','VALIDATION.json'] and pin(folder/name)['sha256']==digest
    assert pin(folder/'controls.zip')==proof['archive'] and pin(folder/'WARNINGS.md')==proof['warnings_document']
    files=unpack(folder/'controls.zip',proof)
    for name,wanted in proof['source_pins'].items():
        verify_source(ROOT,data,name,wanted['sha256']);assert byte_pin(files['current-source/'+name])==wanted
    before=json.loads(files['artifact-parent-entries.json']);after=json.loads(files['artifact-candidate-entries.json'])
    assert all(after[n]==v for n,v in before.items()) and len(before)==proof['unchanged_parent_entries']
    assert set(after)-set(before)==set(proof['added_entries']) and len(proof['added_entries'])==28
    for name in proof['added_entries']:assert byte_pin(files['candidate-owned-entry/'+name])==after[name]
    for label,key in [('parent','parent_artifact'),('candidate','artifact')]:
        path=ROOT/proof[key]['path']
        if path.exists():
            assert pin(path)=={k:proof[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as jar:
                assert {n:byte_pin(jar.read(n)) for n in jar.namelist()}==(before if label=='parent' else after)
    base='build/literature-review/monocle-runtime/'
    get=lambda name:json.loads(files[base+name])
    original=get('pseudotime-original-2/COMPLETED.json')
    assert original['original_whole_package_loaded'] is False and original['original_fit_performed'] is False
    plan=get('pseudotime-original-2/PLAN.json');assert len(plan['cases'])==173
    fixture=json.loads(gzip.decompress(files['current-source/src/test/resources/org/jlinalg/singlecell/monocle3-pseudotime/original-controls-v1.json.gz']))
    assert fixture['plan']==plan and len(fixture['controls'])==519
    returned=errors=0
    for version in proof['original_profiles']:
        report=get('pseudotime-original-2/'+version+'/ORIGINAL.json')
        assert report['original_whole_package_loaded'] is False and report['original_fit_performed'] is False
        assert [r['id'] for r in report['cases']]==[r['id'] for r in plan['cases']]
        for row in report['cases']:
            assert row==get('pseudotime-original-2/'+version+'/'+row['id']+'/original.json')
            assert dict(version=version,**row) in fixture['controls']
            if row['returned']:
                returned+=1
                source=next(c for c in plan['cases'] if c['id']==row['id'])
                assert row['graph_vertex_order']==source['graph_vertex_ids']
                assert len(row['graph_pseudotime'])==len(source['graph_vertex_ids'])
                assert len(row['input_cell_pseudotime'])==len(source['cell_ids'])
                assert len(row['selected_cell_indices_zero_based'])==len(source['roots'])
                assert base+'pseudotime-original-2/'+version+'/'+row['id']+'/original.rds' in files
            else:
                errors+=1;assert row['id']=='one-dimensional-multiple-roots'
                assert row['source_error']['message']=='x and y must be conform in columns.'
        baseline=next(r for r in report['cases'] if r['id']=='nearest-cell-is-origin')
        assert baseline['selected_cell_indices_zero_based']==[1] and baseline['input_cell_pseudotime']==['3','0','5']
    assert (returned,errors)==(proof['original_returns'],proof['original_errors'])==(516,3)
    assert get('pseudotime-original-1/INVALIDATED.json')['admissible_reference_cases']==0
    matrix=get('native-matrix-2/COMPLETED.json');assert matrix['artifact']==proof['artifact']
    assert matrix['tests_per_JVM']==529 and len(matrix['JVMs'])==3
    for row in matrix['JVMs']:
        assert row['exit_code']==0 and row['tests']==529 and row['skipped']==0
        text=files[base+'native-matrix-2/'+row['JVM']+'.stdout'].decode('utf8')
        for expected in ['529 tests found','529 tests successful','0 tests skipped','0 tests failed']:
            assert expected in text,expected
    example=get('example-1/COMPLETED.json');assert example['artifact']==proof['artifact'] and len(example['runs'])==2
    for row in example['runs']:
        assert row['exit_code']==0
        assert b'Pseudotime: [3.0, 0.0, 5.0]' in files[base+'example-1/'+row['JVM']+'.stdout']
        assert files[base+'example-1/'+row['JVM']+'.stderr']==b''
    failed=get('reference-setup-1/RESULT.json')
    assert failed['status']=='SKIP3_MONOCLE_REFERENCE_UNAVAILABLE' and failed['analysis_admitted'] is False
    assert failed['preexisting_libraries_unchanged'] and get('reference-setup-1/BASES-BEFORE.json')==get('reference-setup-1/BASES-AFTER.json')
    assert b'STRING_PTR' in files[base+'reference-setup-1/installer.stderr']
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==proof['canonical_executable_unchanged']
    for name in ['original_full_package_loaded','original_full_package_fit','workflow_admitted','implementation_credit',
            'native_DLL_source_authentication','execution_authentication','biological_validity_certified','whole_paper_reproduced',
            'full_project_test_suite_run','released','pushed','deployed','whole_goal_complete']:
        assert proof[name] is False
    return proof


if __name__=='__main__':
    try:
        if not __debug__:raise RuntimeError('Never disable complete evidence checks')
        p=verify();print(json.dumps({k:p[k] for k in ['status','original_returns','original_errors','tests_per_JVM','JVMs','implementation_credit']}))
    except Exception as error:
        print('MONOCLE NATIVE EVIDENCE FAIL2: '+str(error)+'\nREQUIRED ACTION: preserve the discrepancy and repeat complete source/control verification; no partial evidence admitted.',file=sys.stderr)
        raise
