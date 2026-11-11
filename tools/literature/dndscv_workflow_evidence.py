# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Portable strict dndscv source, complete-table/array, atomic-admission and artifact evidence checks."""
import gzip,io,json,math,struct,tarfile,zipfile
from pathlib import PurePosixPath
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

BASE='build/literature-review/dndscv-runtime/'


def verify():
    folder=ROOT/'docs/literature/dndscv-workflow-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-typed-original-dndscv-selection-workflows' and p['adapter_admitted'] and p['implementation_credit'] and not p['canonical_adapter_available'] and not p['whole_goal_complete'];assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings_document'];files=unpack(folder/'controls.zip',p)
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','VALIDATION.json'] and pin(folder/name)['sha256']==sha
    for n,v in p['source_pins'].items():assert pin(ROOT/n)==v and byte_pin(files['current-source/'+n])==v
    for n,v in p['parent_proofs'].items():assert pin(ROOT/n)==v
    get=lambda n:json.loads(files[n]);reports={k:get(BASE+n+'/COMPLETED.json') for k,n in p['final_attempts'].items()};build=reports['build'];assert all(r['artifact']==p['artifact'] for k,r in reports.items() if k!='setup');assert reports['runtime']['status']=='passed-original-dndscv-runtime-options-and-atomic-failure-protocols';assert len(reports['runtime']['protocols'])==p['runtime_protocols']==24
    for report in reports.values():
        for name,value in report['source_pins'].items():
            if not name.startswith('build/'):assert pin(ROOT/name)==value
    assert all(r['tests']==17 and r['exit_code']==r['skipped']==0 for r in reports['native']['JVMs']) and len(reports['native']['JVMs'])==3;coding=get(BASE+'coding-validation-3/COMPLETED.json');assert coding['component_admitted'] and coding['complete_opportunity_entries']==43008 and all(r['tests']==64 and r['exit_code']==r['skipped']==0 for r in coding['JVMs']) and len(coding['JVMs'])==3
    omitted=get('PUBLIC-OMISSION-RECEIPTS.json');assert not omitted['objects_replaced_or_recomputed'] and len(omitted['files'])==p['public_omission_receipts'];assert all(n not in files and v['bytes']>=0 and len(v['sha256'])==64 and v['reason'] for n,v in omitted['files'].items());assert any(n.endswith('/original-return.rds') for n in omitted['files'])
    source=ROOT/'docs/literature/dndscv-source';sp=json.loads((source/'VALIDATION.json').read_bytes());assert pin(source/'source.zip')==sp['archive'];sf=unpack(source/'source.zip',sp);receipt=json.loads(sf['SOURCE.json']);profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/dndscv-workflow/SOURCE-PROFILE.json').read_bytes());assert receipt['commit']==profile['commit']==sp['source_commit'] and receipt['source_members']==profile['files'] and byte_pin(sf['source.tar.gz'])==receipt['archive'];members={}
    with tarfile.open(fileobj=io.BytesIO(sf['source.tar.gz']),mode='r:gz') as tar:
        for member in tar:
            path=PurePosixPath(member.name);assert not path.is_absolute() and '..' not in path.parts and path.parts[0]=='dndscv-'+profile['commit']
            if member.isdir():continue
            assert member.isfile() and len(path.parts)>1;name=PurePosixPath(*path.parts[1:]).as_posix();assert name in profile['files'] and name not in members;members[name]=byte_pin(tar.extractfile(member).read());assert members[name]==profile['files'][name]
    assert members==profile['files'] and len(members)==60
    def table(name):
        record=get(name);prefix=name.rsplit('/',1)[0]+'/';columns=record['column_names'];names=[];rows=[];kinds=[]
        for part in record['parts']:
            value=get(prefix+part);assert [c['name'] for c in value['columns']]==columns;assert value['rows']==len(value['row_names']);names+=value['row_names']
            for column in value['columns']:
                assert len(column['tokens'])==len(column['kinds'])==value['rows']
                if column.get('original_dim') is not None:assert column['original_dim']==[record['rows']]
                for token,kind in zip(column['tokens'],column['kinds']):assert kind in ['value','NA','NaN','positive-infinity','negative-infinity'] and (kind=='value' or token=={'NA':'NA','NaN':'NaN','positive-infinity':'Inf','negative-infinity':'-Inf'}[kind])
            rows.extend([[c['tokens'][i] for c in value['columns']] for i in range(value['rows'])]);kinds.extend([[c['kinds'][i] for c in value['columns']] for i in range(value['rows'])])
        assert len(rows)==record['rows']==len(names);lines=files[name[:-5]+'.tsv'].decode('utf8').splitlines();assert lines==['\t'.join(['__original_R_row_name__',*columns]),*['\t'.join([n,*row]) for n,row in zip(names,rows)]];assert name[:-5]+'.rds' in files
        return columns,names,rows,kinds
    admitted=0;tables=0;array_pairs=0
    for name,raw in files.items():
        if name.endswith('/LIBRARIES-BEFORE.json') and name.removesuffix('BEFORE.json')+'AFTER.json' in files:assert raw==files[name.removesuffix('BEFORE.json')+'AFTER.json']
        if name.endswith('/analysis-status.json'):
            state=json.loads(raw);prefix=name.removesuffix('analysis-status.json');assert state['result_admitted']==(state['status']=='PASS_ORIGINAL_DNDSCV_SELECTION_WORKFLOW')
            if state['result_admitted']:admitted+=1;assert prefix+'provenance.json' in files
            else:assert not any(n.startswith(prefix+'results/') for n in files)
        if name.endswith('/process-receipt.json'):assert all(not row['alive_after_cleanup'] for row in json.loads(raw)['owned_process_cleanup'])
        if name.endswith('.json'):
            record=json.loads(raw)
            if isinstance(record,dict) and {'column_names','parts','rows'}<=record.keys():table(name);tables+=1
        if name.endswith('/matrix-axes.json'):
            axes=json.loads(raw);prefix=name.removesuffix('matrix-axes.json')
            if 'dim' in axes and prefix+'N.f64be.gz' in files:
                shape=axes['dim'];assert shape[:2]==[192,4];expected=math.prod(shape)*8
                for label in ['N','L']:
                    raw_array=gzip.decompress(files[prefix+label+'.f64be.gz']);assert len(raw_array)==expected and all(math.isfinite(x[0]) and x[0]>=0 and (label=='L' or x[0]==math.floor(x[0])) for x in struct.iter_unpack('>d',raw_array))
                array_pairs+=1
    assert admitted>=20 and tables>100 and array_pairs>20
    def equal(home,reference):
        count=0
        for label in ['globaldnds','sel_cv','sel_loc','annotmuts','genemuts','geneindels','mle_submodel','syncv','wrongmuts','exclmuts']:
            name=reference+'/'+label+'.json';observed=home+'/'+label+'.json'
            if name not in files:assert observed not in files;continue
            expected=get(name);complete=table(observed);assert complete[:3]==([c['name'] for c in expected['columns']],expected['row_names'],[[c['tokens'][i] for c in expected['columns']] for i in range(expected['rows'])]);assert complete[3]==[[c['kinds'][i] for c in expected['columns']] for i in range(expected['rows'])];count+=1
        for label in ['N','L']:assert gzip.decompress(files[home+'/'+label+'.f64be.gz'])==gzip.decompress(files[reference+'/'+label+'.f64be.gz'])
        return count
    comparisons=0
    for case in reports['probe']['reference_comparisons']:assert equal(BASE+p['final_attempts']['probe']+'/result/results/'+case['id'],BASE+'original-controls-1/'+case['id'])==case['complete_original_tables_compared'];comparisons+=1
    for protocol in reports['runtime']['protocols']:
        for case in protocol.get('reference_comparisons',[]):
            reference=BASE+('original-controls-1/' if protocol['id'] in ['original-options-and-gene-set','no-covariates'] else 'edge-original-controls-1/' if protocol['id'] in ['sample-event-limit-median','hg38-standard-code-synthetic-events'] else 'additional-original-controls-2/')+case['id']
            if case['id']=='per-site-coverage':reference=BASE+'site-coverage-reference-recovery-1'
            assert equal(BASE+p['final_attempts']['runtime']+'/'+protocol['id']+'/result/results/'+case['id'],reference)==case['complete_original_tables_compared'];comparisons+=1
    assert equal(BASE+p['final_attempts']['platform']+'/windows-java25/result/results/targeted-2',BASE+'original-controls-1/targeted-2')==8;comparisons+=1;assert equal(BASE+p['final_attempts']['example']+'/example/result/results/targeted-2',BASE+'original-controls-1/targeted-2')==8;comparisons+=1;assert comparisons==p['complete_original_case_comparisons']==24
    old=get('artifact-parent-entries.json');new=get('artifact-candidate-entries.json');assert len(old)==p['unchanged_parent_entries'] and all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(p['added_entries']);assert all(byte_pin(files['candidate-owned-entry/'+n])==new[n] for n in p['added_entries'])
    for key,entries in [('parent_artifact',old),('artifact',new)]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as z:assert len(z.namelist())==len(set(z.namelist())) and {n:byte_pin(z.read(n)) for n in z.namelist()}==entries
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==p['canonical_executable_unchanged']
    print('Verified dndscv:64 coding +17 workflow tests/JVM,24 runtime protocols,24 complete original case comparisons; exact source/profile and explicit public opaque-object omission receipts; no whole-goal completion');return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/evidence/admission checks')
    verify()
