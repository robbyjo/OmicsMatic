# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Portable complete-source/evidence and independent probability/BH audit; installs nothing."""
import csv
import io
import json
import math
from pathlib import PurePosixPath
import zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source,reverse_delta


def verify():
    folder=ROOT/'docs/literature/monocle2-differential-development';data=ROOT/'docs/literature/omics-2023-2026';p=json.loads((folder/'VALIDATION.json').read_bytes())
    assert p['status']=='passed-typed-original-Monocle2-differential-development-controls' and p['adapter_admitted'] and not p['implementation_credit'] and not p['whole_goal_complete']
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest,n=line.split('  ',1);assert n in ['controls.zip','WARNINGS.md','VALIDATION.json'] and pin(folder/n)['sha256']==digest
    assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings_document'];files=unpack(folder/'controls.zip',p)
    for n,v in p['source_pins'].items():verify_source(ROOT,data,n,v['sha256']);assert byte_pin(files['current-source/'+n])==v
    for n,v in p['parent_proofs'].items():assert pin(ROOT/n)==v
    base='build/literature-review/monocle-runtime/';get=lambda n:json.loads(files[base+n]);build=get('differential-build-4/COMPLETED.json');assert build['artifact']==p['artifact'] and build['parent_artifact']==p['parent_artifact']
    before=json.loads(files['artifact-parent-entries.json']);after=json.loads(files['artifact-candidate-entries.json']);assert all(after[n]==v for n,v in before.items()) and len(before)==p['unchanged_parent_entries'] and set(after)-set(before)==set(p['added_entries'])
    for n in p['added_entries']:assert byte_pin(files['candidate-owned-entry/'+n])==after[n]
    for label,key in [('parent','parent_artifact'),('candidate','artifact')]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as z:assert len(z.namelist())==len(set(z.namelist())) and {n:byte_pin(z.read(n)) for n in z.namelist()}==(before if label=='parent' else after)
    profile=json.loads(files['current-source/src/main/resources/org/jlinalg/omics/monocle2-differential/SOURCE-PROFILE.json']);assert profile['version']=='2.26.0' and profile['r_version']=='4.2.1' and len(profile['guarded_functions'])==11
    for n,v in profile['files'].items():assert byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/monocle2-differential/'+n])==v
    validation=get('differential-validation-4/COMPLETED.json');variants=get('differential-variants-4/COMPLETED.json');contracts=get('differential-contracts-3/COMPLETED.json');example=get('differential-example-2/COMPLETED.json');assert all(x['artifact']==p['artifact'] for x in [validation,variants,contracts,example])
    assert len(validation['comparisons'])==6 and sum(c['rows'] for c in validation['comparisons'].values())==720 and all(c['all_original_columns_equal'] and c['counts_axes_size_factors_and_dispersions_equal'] for c in validation['comparisons'].values())
    assert contracts['tests_per_JVM']==10 and len(contracts['JVMs'])==3 and all(r['tests']==10 and r['exit_code']==0 and r['skipped']==0 for r in contracts['JVMs'])
    assert example['comparison']==dict(rows=12,all_original_columns_equal=True)
    expected={'nb-fixed-relative':True,'nb-fixed-unscaled':True,'nb-estimated':False,'normal':True,'binary':False,'proportions':False};assert {k:v['returned'] for k,v in variants['original_family_outcomes'].items()}==expected
    assert all(v['error']=='the condition has length > 1' for v in variants['original_family_outcomes'].values() if not v['returned'])
    assert set(variants['comparisons'])=={k for k,v in expected.items() if v} and sum(c['rows'] for c in variants['comparisons'].values())==36
    assert len(variants['protocols'])==13
    for row in variants['protocols']:
        root='differential-variants-4/'+row['id']+'-result/';state=get(root+'analysis-status.json');assert state['result_admitted'] is row['result_admitted'] is (row['exit_code']==0)
        assert ('REQUIRED ACTION:' in files[base+root+'WARNINGS.md'].decode('utf8'))
        assert any(n.startswith(base+root+'results/') for n in files) is state['result_admitted']
        if row['owned_cleanup_checked']:assert all(not c['alive_after_cleanup'] for c in get(root+'worker/process-receipt.json')['owned_process_cleanup'])
        if row['exit_code']==3:assert state['status']=='SKIP3_DEPENDENT_ANALYSIS_SKIPPED'
        elif row['exit_code']==2:assert state['status']=='FAIL2_NO_RESULT_ADMITTED'
    failed=get('differential-variants-4/atomic-original-failed-genes-result/worker/cases/singular-second/tests.json');cols={c['name']:c['tokens'] for c in failed['columns']};assert set(cols['status'])=={'FAIL'} and set(cols['pval'])==set(cols['qval'])=={'1'}
    assert get('differential-variants-4/atomic-original-failed-genes-result/worker/cases/valid-first/source-status.json')['status']=='original-returned'
    # Independently audit every admitted atomic TSV/JSON export and BH family.
    rows_checked=0;exports=0
    for n,raw in files.items():
        if not n.startswith(base) or '/results/' not in n or not n.endswith('.json'):continue
        if not any(n.startswith(base+d+'/') for d in ['differential-validation-4','differential-variants-4','differential-example-2']):continue
        table=json.loads(raw)
        if not isinstance(table,dict) or 'columns' not in table:continue
        columns=table['columns'];assert len({c['name'] for c in columns})==len(columns) and all(len(c['tokens'])==len(c['kinds'])==table['rows'] for c in columns)
        for c in columns:
            for token,kind in zip(c['tokens'],c['kinds']):
                assert kind in ['value','NA','NaN','positive-infinity','negative-infinity']
                if kind!='value':assert token=={'NA':'NA','NaN':'NaN','positive-infinity':'Inf','negative-infinity':'-Inf'}[kind]
        parsed=list(csv.reader(io.StringIO(files[str(PurePosixPath(n).with_suffix('.tsv'))].decode('utf8'),newline=''),delimiter='\t'))
        assert parsed==[[c['name'] for c in columns],*list(map(list,zip(*(c['tokens'] for c in columns))))];exports+=1
        if PurePosixPath(n).name!='tests.json':continue
        c={c['name']:c['tokens'] for c in columns};values=list(map(float,c['pval']));q=list(map(float,c['qval']));assert c['status']==['OK']*len(values) and all(math.isfinite(x) and 0<=x<=1 for x in [*values,*q])
        order=sorted(range(len(values)),key=lambda i:values[i]);minimum=1.0
        for i in reversed(range(len(values))):minimum=min(minimum,len(values)/(i+1)*values[order[i]]);assert abs(minimum-q[order[i]])<=5e-13*max(abs(minimum),1e-300)
        rows_checked+=len(values)
    assert rows_checked==768 and exports==26
    delta=json.loads(files['current-source/docs/literature/omics-2023-2026/monocle2-differential-checkout-shared-source-compatibility.json']);assert len(delta['paths'])==1 and delta['paths'][0]['path']=='.gitattributes';reverse_delta(files['current-source/.gitattributes'],delta['paths'][0])
    for attempt in ['differential-variants-1','differential-variants-2','differential-validation-2']:assert base+attempt+'/DIAGNOSIS.txt' in files
    print('Verified Monocle2 adapter: 768 admitted original rows with independent BH, 26 atomic exports, 13 runtime protocols, three JVMs and unchanged parent entries')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/scientific evidence assertions')
    verify()
