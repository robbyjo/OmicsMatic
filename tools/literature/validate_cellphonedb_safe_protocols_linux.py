# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual safe-worker errors, explicit source fallback, atomic batches and owned timeout/output cleanup. No executable deserialization."""
import argparse,json,os,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.absolute();assert sys.platform=='linux' and out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);kit=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-workflow';setup=ROOT/'build/cellphonedb-workflow-case-sensitive-1/setup-1';binding=json.loads((setup/'COMPLETED.json').read_bytes());assert binding['status']=='PASS_BOUND_ORIGINAL_CELLPHONEDB_WORKFLOW_RUNTIME_ONLY';prefix=Path(binding['runtime_environment']);python=prefix/'bin/python';fixture=ROOT/'build/literature-review/cellphonedb-runtime/workflow-fixtures-5';manifest=json.loads((fixture/'COMPLETED.json').read_bytes());pins={str(p):pin(p) for p in [Path(__file__).resolve(),kit/'worker.py',kit/'codec.py',kit/'supervisor.py',kit/'profiles.json',kit/'worker-profile.json',setup/'COMPLETED.json']};state=dict(status='FAIL2_SAFE_CELLPHONEDB_PROTOCOL_CONTROLS',implementation_credit=False,whole_goal_complete=False,protocols=[]);before=None
    def members(group):
        result=[]
        for p in Path('/proc').iterdir():
            if not p.name.isdigit():continue
            try:
                fields=(p/'stat').read_text().split(') ',1)[1].split()
                if int(fields[2])==group and fields[0]!='Z':result.append(int(p.name))
            except (OSError,ValueError,IndexError):pass
        return result
    def execute(name,analyses,expected,timeout=1200,output=2147483648):
        home=out/name;home.mkdir();request=dict(schema_version=1,edition='5.0.1',analyses=analyses,maximum_cells=100000,maximum_genes=100000,maximum_matrix_cells=50000000,python_hash_seed=0);save(home/'request.json',request);(home/'runtime.original.json').write_bytes((setup/'COMPLETED.json').read_bytes())
        for n in ['codec.py','worker.py','supervisor.py']:(home/n).write_bytes((kit/n).read_bytes())
        command=[str(python),'-I','-B',str(home/'supervisor.py'),'--runtime',str(home/'runtime.original.json'),'--out',str(home),'--worker',str(home/'worker.py'),'--request',str(home/'request.json'),'--timeout',str(timeout),'--maximum-output-bytes',str(output),'--maximum-files','100000'];child=None;record=dict(name=name,command=command,expected=expected);start=time.monotonic()
        try:
            with (home/'supervisor.stdout').open('xb') as first,(home/'supervisor.stderr').open('xb') as second:
                child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);save(home/'PARENT-STARTED.json',dict(pid=child.pid,owned_process_group=child.pid,command=command));code=child.wait(timeout=timeout+45)
            record['exit_code']=code;assert (code==0)==(expected=='pass-original-only'),'Unexpected protocol exit'
            process=json.loads((home/'original.process.json').read_bytes());assert not process['owned_live_after_cleanup'];record['original_process']=process
            if expected=='pass-original-only':
                report=json.loads((home/'COMPLETED.json').read_bytes());assert report['status']=='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_WORKFLOWS_ONLY' and not report['result_admitted'] and len(report['cases'])==len(analyses);sampling=json.loads((home/analyses[0]['id']/'subsampling-state.json').read_bytes());assert sampling['source_errors'] and sampling['returned_cells']==sampling['input_cells'];record['explicit_full_count_fallback']=sampling
            elif (home/'COMPLETED.json').exists():
                report=json.loads((home/'COMPLETED.json').read_bytes());assert report['status']=='FAIL2_ORIGINAL_CELLPHONEDB_BATCH_UNADMITTED' and not report['result_admitted'];record['original_error']=report.get('error');record['earlier_provisional_cases']=len(report.get('earlier_cases_provisional',[]));assert record['earlier_provisional_cases']==(1 if name=='atomic-later-case-failure' else 0)
            else:assert name in ['wall-time-cap','output-cap'],'Missing expected original error receipt'
        finally:
            if child is not None:
                if members(child.pid):os.killpg(child.pid,signal.SIGTERM);time.sleep(.2)
                if members(child.pid):os.killpg(child.pid,signal.SIGKILL)
                child.wait(timeout=10);record['owned_live_after_cleanup']=members(child.pid);assert not record['owned_live_after_cleanup']
            record['elapsed_seconds']=time.monotonic()-start;save(home/'PROTOCOL.json',record);state['protocols'].append(record)
    try:
        before=inventory(prefix,progress=out/'runtime-before.progress.json');save(out/'runtime-before.json',before);assert before==json.loads((setup/'runtime-after.json').read_bytes());cases=json.loads((fixture/'cases.json').read_bytes());negatives=[]
        for c in cases['cases']:
            if c['expected_error'] is None:continue
            original=fixture/c['id'];a=dict(id=c['id'],method=c['method'],counts=dict(path=str(original/'counts.tsv'),format='tsv'),counts_data=c['counts_data'],metadata=dict(path=str(original/'meta.tsv'),format='tsv'),database=dict(path=str(fixture/c.get('database',cases['database'])),edition='synthetic-v1',source='Project-authored source-error controls'),threshold=c['threshold'],separator=c['separator'],result_precision=c['result_precision'],score_interactions=c['score_interactions'],threads=c['threads'],global_seed=7221)
            for key in ['microenvironments','degs','active_tfs']:
                if c[key] is not None:a[key]=dict(path=str(original/(key+'.tsv')),format='tsv')
            negatives.append(a)
        assert len(negatives)==6
        for a in negatives:execute(a['id'],[a],'fail2')
        positive=json.loads((ROOT/'build/literature-review/cellphonedb-runtime/safe-worker-controls-3/request.json').read_bytes())['analyses'];execute('atomic-later-case-failure',[positive[0],negatives[0]],'fail2');sampling=json.loads(json.dumps(next(a for a in positive if a['id']=='statistical-subsampling-original-geometry')));sampling['statistical']['subsampling']['pcs']=1000;sampling['statistical']['subsampling']['failure_policy']='fail';execute('subsampling-failure-forbidden',[sampling],'fail2');sampling['statistical']['subsampling']['failure_policy']='retain-original-full-counts';execute('subsampling-fallback-explicit',[sampling],'pass-original-only');unsafe=json.loads(json.dumps(positive[0]));unsafe['counts']['format']='pickle';execute('unsafe-object-format-rejected',[unsafe],'fail2');execute('wall-time-cap',[positive[0]],'fail2',timeout=1);execute('output-cap',[positive[0]],'fail2',output=1024);state['status']='PASS_TWELVE_SAFE_CELLPHONEDB_PROTOCOL_CONTROLS_ONLY'
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if before is not None:
            after=inventory(prefix,progress=out/'runtime-after.progress.json');save(out/'runtime-after.json',after);state['runtime_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_SAFE_PROTOCOL_RUNTIME_CHANGED',error='Complete runtime changed')
        state['source_inputs_unchanged']=all(pin(Path(n))==want for n,want in pins.items()) and all(pin(fixture/n)==want for n,want in manifest['files'].items());state['source_pins']=pins
        if not state['source_inputs_unchanged']:state.update(status='FAIL2_SAFE_PROTOCOL_SOURCE_INPUT_CHANGED',error='Bound input/source changed')
        save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable protocol admission assertions')
    raise SystemExit(main())
