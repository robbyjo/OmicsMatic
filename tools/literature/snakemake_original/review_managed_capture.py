#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent complete exported managed execution/object/identity review; not final analysis admission."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,sys
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def load(path):return json.loads(path.read_bytes())
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['source','reference','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--native',action='store_true',help='Read the already terminal native execution roots while their separate evidence export continues.')
    a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-reviewer.py');index=None;pool=a.source/'shared-objects';verified={}
    if a.native:assert sys.platform=='linux' and a.source.resolve()==a.source and not str(a.source).startswith('/mnt/')
    else:
        index=load(a.source/'EXPORTED.json')['files'];assert set(index)=={p.relative_to(a.source).as_posix() for p in a.source.rglob('*') if p.is_file() and p.name!='EXPORTED.json'}
        for path in pool.iterdir():
            value=pin(path);assert value['sha256']==path.name and index[path.relative_to(a.source).as_posix()]==value;verified[path.name]=value
        for name,expected in index.items():
            assert not Path(name).is_absolute() and '..' not in Path(name).parts;path=a.source/name
            if path.parent.name=='objects' and (pool/path.name).exists() and os.path.samefile(path,pool/path.name):assert verified[path.name]==expected
            elif path.parent!=pool:assert pin(path)==expected,name
    complete=load(a.source/'COMPLETED.json');assert len(complete['cases'])==18 and complete['original_software_unchanged'];before=load(a.source/'IMMUTABLE-BEFORE.json');assert before==load(a.source/'IMMUTABLE-AFTER.json')==load(a.reference/'IMMUTABLE-BEFORE.json');rows=[];positive=skipped=failed=identities=0;all_blobs=set()
    for row in complete['cases']:
        parent=a.source/'cases'/row['case'];case=parent/'execution' if a.native else parent;request=load(case/'REQUEST.json');assert (case/'REQUEST.json').read_bytes()==(parent/'recipe.json').read_bytes();assert request['schema_version']==2 and request['execution']['deployment']==row['mode'];root=load(case/'request-source.json')['path'].rsplit('/',1)[0]+'/execution';capture=load(case/'capture.json');parameters=load(case/'parameters.json');assert parameters['huge']==9007199254740993 and type(parameters['huge']) is int and parameters['tiny']==1e-300
        assert parameters['data_path']==root+'/workspace/resources/'+capture['aliases']['data']+'/values.txt' and parameters['destination']==root+'/workspace/published/summary.tsv'
        if row['expected_success']:positive+=1;marker=load(case/'EXECUTED.json');assert marker['user_analysis_admitted'] is False and marker['catalog_credit']==0
        else:
            assert not (case/'EXECUTED.json').exists();failure=load(case/'FAILURE.json');expected=3 if row['scenario'] in ['missing','profile-skip'] else 2;assert failure['exit_code']==expected;skipped+=expected==3;failed+=expected==2
        assert not (case/'COMPLETED.json').exists();controller=load(case/'CONTROLLER.json');ledger=load(case/'process/process.json');assert not ledger['owned_live_after_cleanup'] and not ledger.get('suppressed') and 'cleanup_state' not in ledger and not controller['integrity_errors'];observed={}
        for item in ledger['owned_processes']:observed.setdefault(item['pid'],set()).add(item['startTicks'])
        for policy in controller['policy_processes']:assert len(observed[policy['pid']])==1;identities+=1
        if row['scenario']=='profile-skip':assert not controller['upstream_api_called'] and not list((case/'events').iterdir()) and not any(p['records'] or p['spawn_arguments'] for p in controller['policy_processes'])
        else:assert controller['upstream_api_called']
        invocation=load(case/'invocation.json');assert invocation['selected_deployment']==row['mode'];declared=request['managed_software']
        assert invocation['software_roots']['environments']==(declared['conda_environments'] or root+'/managed/environments') and invocation['software_roots']['images']==(declared['container_images'] or root+'/managed/images')
        pins={}
        def add(value):
            if value is None:return
            old=pins.setdefault(value['sha256'],value);assert old==value
        for filename in ['capture.json','working-before.json','working-after.json']:
            for tree in load(case/filename)['roots']:
                for item in tree['items'].values():
                    if item['kind']=='file':add(item['original']['pin'])
        for filename in ['software-before.json','software-after.json','borrowed-software-before.json']:
            if not (case/filename).exists():continue
            for tree in load(case/filename)['roots'].values():
                for item in tree['nodes'].values():add(item['content'])
        if (case/'outputs.json').exists():
            for output in load(case/'outputs.json'):
                for item in output['members'].values():add(item.get('pin'))
        assert set(pins)=={p.name for p in (case/'objects').iterdir()},row['case']
        for name,expected in pins.items():
            if a.native:assert pin(case/'objects'/name)==expected
            else:assert index[(case/'objects'/name).relative_to(a.source).as_posix()]==expected
        if row['expected_success']:
            outputs=load(case/'outputs.json');table=next(v for v in outputs if v['root']=='published')['members']['']['pin'];assert (case/'objects'/table['sha256']).read_bytes()==b'total\thuge\ttiny\n5\t9007199254740993\t1e-300\n';assert load(case/'completion-events.json')['final_done']>=1
        all_blobs.update(pins);rows.append(dict(case=row['case'],outcome='captured-executed-not-admitted' if row['expected_success'] else 'skip' if row['scenario'] in ['missing','profile-skip'] else 'failure-no-result',complete_blob_references=len(pins),controller_pid=controller['controller_pid']))
    assert (positive,skipped,failed)==(6,6,6);write(out/'COMPLETED.json',dict(status='PASS_INDEPENDENT_EIGHTEEN_CAPTURED_MANAGED_EXECUTION_OUTCOMES_AND_COMPLETE_BLOB_CLOSURES',cases=rows,positive=positive,skipped=skipped,failed=failed,policy_processes=identities,unique_case_blobs=len(all_blobs),verified_export_blobs=len(verified),original_software_matches_reference=True,native_roots_read=a.native,source_record=pin(a.source/('COMPLETED.json' if a.native else 'EXPORTED.json')),user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS_INDEPENDENT_18_MANAGED_CAPTURE_CLOSURES',flush=True)
if __name__=='__main__':main()
