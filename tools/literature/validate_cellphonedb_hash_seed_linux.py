# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual packaged Linux Java original-workflow admission and dependency/error protocols."""
import argparse,json,os,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.absolute();assert sys.platform=='linux' and out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);built=json.loads((args.build/'COMPLETED.json').read_bytes());assert built['status']=='PASS_COMPILED_CELLPHONEDB_HASH_SEED_DEVELOPMENT_ONLY';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};runtime=ROOT/'build/cellphonedb-workflow-case-sensitive-1/setup-1/COMPLETED.json';binding=json.loads(runtime.read_bytes());prefix=Path(binding['runtime_environment']);source=ROOT/'build/literature-review/cellphonedb-runtime/hash-seed-controls-1/request.json';base=json.loads(source.read_bytes());base.update(mode='RUN',runtime=str(runtime));state=dict(status='FAIL2_PACKAGED_CELLPHONEDB_WORKFLOW_CONTROLS',implementation_credit=False,whole_goal_complete=False,processes=[]);before=None;pins={str(p):pin(p) for p in [Path(__file__).resolve(),source,runtime,jar]}
    def execute(name,request,expected):
        home=out/name;home.mkdir();request=json.loads(json.dumps(request));request['out']=str(home/'analysis');save(home/'request.json',request);command=['/usr/bin/java','-Xmx4g','-cp',str(jar),'org.jlinalg.cli.CellPhoneDbWorkflowCliV2','--request',str(home/'request.json')];child=None;record=dict(name=name,command=command,expected_exit=expected)
        try:
            with (home/'stdout').open('xb') as first,(home/'stderr').open('xb') as second:
                child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);save(home/'STARTED.json',dict(pid=child.pid,owned_process_group=child.pid,command=command));code=child.wait(timeout=600)
            record['exit_code']=code;assert code==expected,'Unexpected public command exit: '+name+'; inspect full stdout/stderr';report=json.loads((home/'analysis/analysis-status.json').read_bytes());assert report['result_admitted']==(expected==0) and (home/'analysis/WARNINGS.md').is_file();record['analysis_status']=report
            if expected==0:
                manifest=json.loads((home/'analysis/manifest.json').read_bytes());assert all(pin(home/'analysis'/n)==want for n,want in manifest['files'].items());assert len(report['cases'])==len(request['analyses']) and report['runner_revision']==2 and report['python_startup_audit']['status']=='PASS_EFFECTIVE_PYTHON_STARTUP_ONLY' and report['python_startup_audit']['requested_seed']==request.get('python_hash_seed',0) and report['python_startup_audit']['independent_native_seed0_hashes']==(request.get('python_hash_seed',0)==0)
            if name=='atomic-later-case-failure':
                worker=json.loads((home/'analysis/worker/COMPLETED.json').read_bytes());assert len(worker['earlier_cases_provisional'])==1 and not worker['result_admitted']
        finally:
            if child is not None and child.poll() is None:
                os.killpg(child.pid,signal.SIGTERM)
                try:child.wait(timeout=20)
                except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait(timeout=10)
            record['java_alive_after_cleanup']=child is not None and child.poll() is None;assert not record['java_alive_after_cleanup'];save(home/'PROCESS.json',record);state['processes'].append(record)
    try:
        before=inventory(prefix,progress=out/'runtime-before.progress.json');save(out/'runtime-before.json',before);assert before==json.loads((runtime.parent/'runtime-after.json').read_bytes());execute('complete19',base,0)
        small=json.loads(json.dumps(base));small['analyses']=small['analyses'][:1]
        for seed in [123,4294967295]:
            seeded=json.loads(json.dumps(small));seeded['python_hash_seed']=seed;execute('effective-seed-'+str(seed),seeded,0)
        missing=json.loads(json.dumps(small));missing['runtime']=str(out/'missing-runtime.json');execute('missing-runtime-skip3',missing,3);edition=json.loads(json.dumps(small));edition['edition']='5.1';execute('reported-edition-unavailable-skip3',edition,3)
        atomic=json.loads(json.dumps(small));bad=json.loads(json.dumps(small['analyses'][0]));bad['id']='source-distinct-alias-error';fixture=ROOT/'build/literature-review/cellphonedb-runtime/workflow-fixtures-5';original=fixture/'simple-many-to-one-distinct-alias-annotation-source-error';bad['counts']['path']=str(original/'counts.tsv');bad['metadata']['path']=str(original/'meta.tsv');bad['database']['path']=str(fixture/'synthetic-alias-cellphonedb.zip');atomic['analyses'].append(bad);execute('atomic-later-case-failure',atomic,2)
        corrupt=json.loads(json.dumps(small));fake=json.loads(runtime.read_bytes());fake['profile']['sha256']='0'*64;save(out/'corrupt-runtime.json',fake);corrupt['runtime']=str(out/'corrupt-runtime.json');execute('runtime-profile-corruption-fail2',corrupt,2)
        labels=json.loads(json.dumps(small));meta=out/'leading-zero-metadata.tsv';rows=Path(labels['analyses'][0]['metadata']['path']).read_text(encoding='utf8').splitlines();mapping={'A':'001','B':'002','C':'003'};meta.write_text(rows[0]+'\n'+'\n'.join(row.split('\t')[0]+'\t'+mapping[row.split('\t')[1]] for row in rows[1:])+'\n',encoding='utf8');labels['analyses'][0]['metadata']['path']=str(meta);execute('changed-leading-zero-labels-fail2',labels,2);state.update(status='PASS_EIGHT_PACKAGED_CELLPHONEDB_HASH_SEED_CONTROLS_ONLY',artifact=built['artifact'])
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if before is not None:
            after=inventory(prefix,progress=out/'runtime-after.progress.json');save(out/'runtime-after.json',after);state['runtime_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_PACKAGED_CONTROL_RUNTIME_CHANGED',error='Complete runtime changed')
        state['source_inputs_unchanged']=all(pin(Path(n))==want for n,want in pins.items()) and all(pin(ROOT/n)==want for n,want in built['source_pins'].items());state['source_pins']=pins
        if not state['source_inputs_unchanged']:state.update(status='FAIL2_PACKAGED_CONTROL_SOURCE_CHANGED',error='Bound original/source input changed')
        save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable packaged command assertions')
    raise SystemExit(main())
