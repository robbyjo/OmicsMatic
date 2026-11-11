# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify sealed development workflow controls and original atomic export transport; installs nothing."""
import csv
import io
import json
from pathlib import Path
import zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source


def tables(files):
    checked=0
    for name,raw in files.items():
        if not name.endswith('.json') or '/cases/' not in name and '/results/' not in name:continue
        frame=json.loads(raw)
        if not isinstance(frame,dict) or not {'rows','columns','complete_original_RDS'}.issubset(frame):continue
        columns=frame['columns'];rows=list(csv.reader(io.StringIO(files[name[:-5]+'.tsv'].decode('utf8'),newline=''),delimiter='\t',strict=True))
        assert rows[0]==[c['name'] for c in columns] and len(rows)==frame['rows']+1
        assert name[:-5]+'.rds' in files and Path(name[:-5]+'.rds').name==frame['complete_original_RDS']
        for i,column in enumerate(columns):
            assert [r[i] for r in rows[1:]]==column['tokens'] and len(column['kinds'])==frame['rows']
            for token,kind in zip(column['tokens'],column['kinds']):
                assert isinstance(token,str) and kind in ['value','NA','NaN','positive-infinity','negative-infinity']
                if kind!='value':assert token=={'NA':'NA','NaN':'NaN','positive-infinity':'Inf','negative-infinity':'-Inf'}[kind]
        checked+=1
    return checked


def verify():
    target=ROOT/'docs/literature/monocle3-workflow-development';data=ROOT/'docs/literature/omics-2023-2026'
    p=json.loads((target/'VALIDATION.json').read_bytes());assert p['status']=='passed-scoped-original-Monocle3-workflow-development-controls'
    for line in (target/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest,name=line.split('  ',1);assert name in ['VALIDATION.json','controls.zip','WARNINGS.md'] and pin(target/name)['sha256']==digest
    assert pin(target/'controls.zip')==p['archive'] and pin(target/'WARNINGS.md')==p['warnings_document']
    files=unpack(target/'controls.zip',p)
    for name,wanted in p['source_pins'].items():
        verify_source(ROOT,data,name,wanted['sha256']);assert byte_pin(files['current-source/'+name])==wanted
    before=json.loads(files['artifact-parent-entries.json']);after=json.loads(files['artifact-candidate-entries.json'])
    assert all(after[n]==v for n,v in before.items()) and len(before)==p['unchanged_parent_entries']
    assert set(after)-set(before)==set(p['added_entries'])
    for name in p['added_entries']:assert byte_pin(files['candidate-owned-entry/'+name])==after[name]
    for label,key in [('parent','parent_artifact'),('candidate','artifact')]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as z:assert {n:byte_pin(z.read(n)) for n in z.namelist()}==(before if label=='parent' else after)
    get=lambda name:json.loads(files['build/literature-review/monocle-runtime/'+name])
    for name in ['workflow-validation-4','workflow-families-3','workflow-variants-2','workflow-contracts-2','workflow-example-2']:
        assert get(name+'/COMPLETED.json')['artifact']==p['artifact']
    assert get('workflow-validation-4/COMPLETED.json')['comparison']['all_atomic_coefficient_and_graph_test_columns_equal']
    f=get('workflow-families-3/COMPLETED.json');assert len(f['families'])==8
    assert f['families']['counts/gaussian']['result_admitted'] is False and all(v['all_atomic_model_and_coefficient_columns_equal'] for v in f['families'].values())
    assert sum(v['result_admitted'] for v in f['families'].values())==7
    expected={'counts-adapter':0,'binary-adapter':0,'original-gaussian-status-failure':2,'atomic-original-failure':2,'missing-runtime':3,'old-edition':3,'timeout':2,'output-capacity':2,'input-capacity':2,'original-gene-failure':2}
    assert {r['label']:r['exit_code'] for r in f['runs']}==expected
    v=get('workflow-variants-2/COMPLETED.json');assert v['literal_Unicode_axes_and_NA_preserved'] and v['comparison']['cases']==2
    c=get('workflow-contracts-2/COMPLETED.json');assert c['tests_per_JVM']==8 and len(c['JVMs'])==3 and all(r['exit_code']==0 and r['tests']==8 for r in c['JVMs'])
    assert tables(files)==p['atomic_table_exports_checked']>0
    preservation=get('workflow-checkpoint-preservation-1/COMPLETED.json');assert preservation['preexisting_R_libraries_unchanged'] and len(preservation['preserved_unrelated_sources'])==18
    assert p['implementation_credit'] is False and p['whole_goal_complete'] is False and p['root_dispatcher_changed'] is False
    print('Verified scoped original Monocle3 workflow controls, full evidence, original failures and literal table exports')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable evidence checks')
    verify()
