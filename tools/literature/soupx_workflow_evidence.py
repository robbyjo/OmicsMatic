# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Portable strict verification of complete typed SoupX source/evidence; no R or installation needed."""
import json,zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

BASE='build/literature-review/soupx-runtime/'


def verify():
    folder=ROOT/'docs/literature/soupx-workflow-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-typed-original-SoupX-ambient-workflows' and p['adapter_admitted'] and p['implementation_credit'] and not p['canonical_adapter_available'] and not p['whole_goal_complete'];assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings_document'];files=unpack(folder/'controls.zip',p)
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','VALIDATION.json'] and pin(folder/name)['sha256']==sha
    for n,v in p['source_pins'].items():assert pin(ROOT/n)==v and byte_pin(files['current-source/'+n])==v
    for n,v in p['parent_proofs'].items():assert pin(ROOT/n)==v
    get=lambda n:json.loads(files[n]);proofs={k:get(BASE+n+'/COMPLETED.json') for k,n in p['final_attempts'].items()};build=proofs['build'];assert all(x['artifact']==p['artifact'] for k,x in proofs.items() if k!='setup')
    for x in proofs.values():
        for n,v in x['source_pins'].items():
            if n.startswith('build/'):continue
            assert pin(ROOT/n)==v
    assert len(proofs['native']['JVMs'])==3 and all(r['tests']==30 and r['skipped']==0 and r['exit_code']==0 for r in proofs['native']['JVMs']);assert len(proofs['runtime']['protocols'])==14 and len(proofs['platform']['protocols'])==3 and len(proofs['literal']['protocols'])==2 and len(proofs['setup']['protocols'])==3 and proofs['example']['corrected_coordinates_compared']==3422
    # Check every retained successful worker's before/after library inventory,
    # atomic admission and cross-format complete corrected transport inventory.
    admitted=0;matrix_files=0
    for name,raw in files.items():
        if name.endswith('/LIBRARIES-BEFORE.json') and name.removesuffix('BEFORE.json')+'AFTER.json' in files:assert raw==files[name.removesuffix('BEFORE.json')+'AFTER.json']
        if name.endswith('/analysis-status.json'):
            if '/source-snapshot/' in name:
                original=name.split('/source-snapshot/',1)[1]
                assert files[original]==raw,'Captured status must equal its complete retained original bundle status'
                continue
            state=json.loads(raw);prefix=name.removesuffix('analysis-status.json');assert state['result_admitted']==(state['status']=='PASS_ORIGINAL_SOUPX_AMBIENT_WORKFLOW')
            if state['result_admitted']:admitted+=1;assert prefix+'provenance.json' in files
            else:assert not any(n.startswith(prefix+'results/') for n in files)
        if name.endswith('/corrected-counts.json'):
            record=json.loads(raw);prefix=name.removesuffix('corrected-counts.json');seen=set()
            for part in record['entry_parts']:
                for row in get(prefix+part)['entries']:
                    key=(row['row'],row['column']);assert key not in seen and row['kind']=='value';seen.add(key)
            assert len(seen)==record['entry_count'] and prefix+'corrected-counts.rds' in files
            if prefix+'corrected-counts.mtx' not in files:
                assert name.startswith(tuple(BASE+'workflow-worker-probe-'+v+'-1/stage/worker/cases/' for v in ['1.6.2','1.3.0'])),'Only the two retained pre-Matrix-Market worker prototypes lack MTX exports'
                assert not any(n.startswith(name.split('/stage/worker/')[0]+'/stage/') and n.endswith('/analysis-status.json') for n in files),'Worker-only prototypes cannot claim Java result admission'
            else:matrix_files+=1
    assert admitted>0 and matrix_files>=45
    old=get('artifact-parent-entries.json');new=get('artifact-candidate-entries.json');assert len(old)==p['unchanged_parent_entries'] and all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(p['added_entries'])
    for name in p['added_entries']:assert byte_pin(files['candidate-owned-entry/'+name])==new[name]
    for label,key in [('parent','parent_artifact'),('candidate','artifact')]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as z:assert len(z.namelist())==len(set(z.namelist())) and {n:byte_pin(z.read(n)) for n in z.namelist()}==(old if label=='parent' else new)
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==p['canonical_executable_unchanged']
    assert p['exact_full_corrected_matrix_comparisons']==45;print('Verified typed SoupX workflows:30 checks/JVM,14 runtime protocols,45 exact complete corrected matrices, explicit Linux/dependency skips and private setup; no whole-goal completion');return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/evidence/admission checks')
    verify()
