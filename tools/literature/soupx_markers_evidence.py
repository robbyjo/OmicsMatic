# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Portable complete native-marker evidence audit; no upstream runtime or installation."""
import gzip,json,zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from validate_soupx_markers import audit

BASE='build/literature-review/soupx-runtime/'


def verify():
    folder=ROOT/'docs/literature/tfidf-markers-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-native-tfidf-marker-component-controls' and p['component_admitted'] and not p['ambient_workflow_admitted'] and not p['implementation_credit'] and not p['whole_goal_complete'];assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings_document']
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','VALIDATION.json'] and pin(folder/name)['sha256']==sha
    files=unpack(folder/'controls.zip',p)
    for name,wanted in p['source_pins'].items():assert pin(ROOT/name)==wanted and byte_pin(files['current-source/'+name])==wanted
    get=lambda name:json.loads(files[BASE+name]);build=get('marker-build-1/COMPLETED.json');validation=get('marker-validation-3/COMPLETED.json');boundary=get('marker-boundaries-2/COMPLETED.json');assert all(r['artifact']==p['artifact'] for r in [build,validation,boundary]);assert len(validation['JVMs'])==3 and all(r['tests']==171 and r['skipped']==0 for r in validation['JVMs']);assert len(validation['protocols'])==28
    assert files[BASE+'source-intake-1/SKIP3.json'] and files['retained-attempts-diagnosis.json'] and files['licenses/upstream-SoupX-GPL-2.txt']
    sources={}
    for version,name in [('1.6.2','source-intake-1/1.6.2'),('1.3.0','source-recovery-1/1.3.0')]:
        receipt=get(name+'/SOURCE.json');assert receipt['version']==version
        for n,wanted in receipt['source_members'].items():assert byte_pin(files[BASE+name+'/source/'+n])==wanted
        sources[version]=receipt
    controls={}
    for version,attempt in [('1.6.2',1),('1.3.0',2)]:
        setup=get(f'reference-{version}-{attempt}/COMPLETED.json');assert setup['status']=='installed-source-bound-original-SoupX-reference-only' and setup['version']==version and setup['preexisting_libraries_unchanged'];prefix=BASE+f'reference-{version}-{attempt}/';assert files[prefix+'LIBRARIES-BEFORE.json']==files[prefix+'LIBRARIES-AFTER.json'] and setup['preexisting_files']==32321
        for name,value in setup['source_pins'].items():assert byte_pin(files[prefix+'source-snapshot/'+name])==value
        source=f'marker-controls-{version}-3/';report=get(source+'COMPLETED.json');data=get(source+'controls.json');assert report['status']=='captured-original-SoupX-marker-controls' and report['preexisting_libraries_unchanged'] and not report['software_installed'] and len(data['cases'])==83 and sum(r['original']['returned'] for r in data['cases'])==79 and data['source_bodies_and_formals_checked_before_and_after'];assert files[BASE+source+'LIBRARIES-BEFORE.json']==files[BASE+source+'LIBRARIES-AFTER.json'];assert report['controls']==byte_pin(files[BASE+source+'controls.json'])
        for name,value in report['source_pins'].items():assert byte_pin(files[BASE+source+'source-snapshot/'+name])==value
        fixture=files['current-source/src/test/resources/org/jlinalg/singlecell/soupx-markers/original-'+version+'-v2.json.gz'];assert gzip.decompress(fixture)==files[BASE+source+'controls.json'];assert files[BASE+source+'original-objects.rds'];controls[version]={r['id']:r for r in data['cases']}
    for row in validation['protocols']:
        prefix=BASE+'marker-validation-3/'+row['id']+'/result/';state=files.get(prefix+'analysis-status.json')
        if state is None:assert row['exit_code']==2 and not row['result_admitted'];continue
        state=json.loads(state);assert state['result_admitted']==row['result_admitted']==(row['exit_code']==0);assert any(n.startswith(prefix+'results/') for n in files)==state['result_admitted'];assert 'REQUIRED ACTION:' in files[prefix+'WARNINGS.md'].decode('utf8')
        if state['result_admitted']:
            reference=None if row['id']=='documented-example' else controls['1.6.2'][row['id']];result=audit({n[len(prefix):]:raw for n,raw in files.items() if n.startswith(prefix)},reference);assert all(row[k]==v for k,v in result.items())
        else:assert state['status']=='FAIL2_NO_RESULT_ADMITTED' and state['cause']
    assert files[BASE+'marker-validation-3/late-output-capacity/result/unadmitted-partial-results/statistics.tsv']==files[BASE+'marker-validation-3/ties-zero-gene/result/results/statistics.tsv']
    assert boundary['preexisting_libraries_unchanged'] and len(boundary['protocols'])==4 and files[BASE+'marker-boundaries-2/LIBRARIES-BEFORE.json']==files[BASE+'marker-boundaries-2/LIBRARIES-AFTER.json'];assert files[BASE+'marker-boundaries-1/LIBRARIES-BEFORE.json']==files[BASE+'marker-boundaries-2/LIBRARIES-BEFORE.json']
    for row in boundary['protocols']:
        assert row['exact_rational_BH_q']==1 and row['near_cutoff_warning_retained'] and row['selection_differs']==(row['native_selected']!=row['original_selected']);prefix=BASE+'marker-boundaries-2/'+row['version']+'/native-'+str(row['trial'])+'/result/';assert 'CUTOFF REVIEW REQUIRED:' in files[prefix+'WARNINGS.md'].decode('utf8');state=json.loads(files[prefix+'analysis-status.json']);assert state['result_admitted'];ref=get('marker-boundaries-2/'+row['version']+'/reference.json');assert ref['source_bodies_and_formals_checked_before_and_after'] and float(ref['calls'][row['trial']-1]['fdr'])==float(row['fdr'])
    roles=get('role-audit-2/COMPLETED.json');drafts=get('role-audit-2/draft-reviews.json');assert roles['status']=='audited-seven-SoupX-primary-roles-drafts-not-applied' and not roles['applied'] and roles['prior_reviews_preserved']==918 and len(drafts)==7 and sum(len(r['review']['supporting_evidence']) for r in drafts)==2
    before=json.loads(files['artifact-parent-entries.json']);after=json.loads(files['artifact-candidate-entries.json']);assert all(after[n]==v for n,v in before.items()) and len(before)==p['unchanged_parent_entries'] and set(after)-set(before)==set(p['added_entries'])
    for name in p['added_entries']:assert byte_pin(files['candidate-owned-entry/'+name])==after[name] and name.endswith('.class')
    for label,key in [('parent','parent_artifact'),('candidate','artifact')]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as z:assert len(z.namelist())==len(set(z.namelist())) and {n:byte_pin(z.read(n)) for n in z.namelist()}==(before if label=='parent' else after)
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==p['canonical_executable_unchanged']
    print('Verified native markers:171 checks/JVM,28 complete packaged protocols,4 boundary protocols,7 unapplied paper roles; ambient workflow remains unfinished')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific/source checks')
    verify()
