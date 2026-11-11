# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the actual Java Hail workflow CLI, retaining full requests/results and prominent dependency skips. No installation or cloud job."""
import argparse,copy,json,os,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--controls',type=Path,required=True);p.add_argument('--runtime',type=Path);p.add_argument('--edition',choices=['0.2.139','0.2.130'],default='0.2.139');p.add_argument('--mode',choices=['skip-controls','positive','late-liftover-failure','batch-failure','timeout'],required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_JAVA_HAIL_WORKFLOW_VALIDATION_INCOMPLETE',implementation_credit=False,whole_goal_complete=False);sources={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))};(out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};sources.update(build['source_pins']);auditor=ROOT/'tools/literature/audit_hail_workflow_controls.py';sources[auditor.relative_to(ROOT).as_posix()]=pin(auditor);assert all(pin(ROOT/n)==v for n,v in sources.items());fixture=json.loads((a.controls/'cases.json').read_bytes());cases=fixture['cases'];cases[3]['input']=dict(kind='matrix-table',from_case='filtered-qc');cases[5]['input']=dict(kind='matrix-table',from_case='make-synthetic-summary');template=dict(schema_version=1,edition=a.edition,runtime_manifest=linux(a.runtime.resolve()/'COMPLETED.json') if a.runtime else linux(out/'runtime-unavailable.json'),out='',limits=fixture['limits'],cases=cases,maximum_input_bytes=67108864,maximum_output_bytes=536870912,maximum_files=100000,timeout_seconds=1200);environment=dict(os.environ)
        for key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:environment.pop(key,None)
        linux_java=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','-u','JAVA_TOOL_OPTIONS','-u','_JAVA_OPTIONS','-u','JDK_JAVA_OPTIONS','/usr/bin/java'];windows21=[str(JAVA21/'java.exe')];windows25=['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'];scenarios=[]
        if a.mode=='skip-controls':
            for name,runtime in [('windows21-host-skip',windows21),('windows25-host-skip',windows25),('linux-missing-runtime',linux_java),('linux-service-unconfigured',linux_java)]:
                request=copy.deepcopy(template);destination=out/(name+'-result');request['out']=str(destination) if name.startswith('windows') else linux(destination);request['runtime_manifest']=str(out/'missing-runtime.json') if name.startswith('windows') else linux(out/'missing-runtime.json')
                if name=='linux-service-unconfigured':request['cases'][7]['backend']='SERVICE'
                scenarios.append((name,runtime,request,3,destination))
        else:
            assert a.runtime,'Positive/failure original analysis needs an exact bound runtime';runtime=json.loads((a.runtime/'COMPLETED.json').read_bytes());assert runtime['status']=='PASS_BOUND_ORIGINAL_HAIL_WORKFLOW_REFERENCE_ONLY';request=copy.deepcopy(template);destination=out/'result';request['out']=linux(destination)
            for case in request['cases']:
                if case['kind']=='batch-local':
                    for job in case['jobs']:job['argv'][0]=runtime['runtime_python']
            if a.mode=='late-liftover-failure':request['cases']=[request['cases'][0],request['cases'][6]];request['cases'][1]['unmapped_policy']='FAIL_ATOMIC'
            elif a.mode=='batch-failure':request['cases']=[request['cases'][0],dict(id='failed-job',kind='batch-local',backend='LOCAL',inputs=[],jobs=[dict(id='expected-fail',argv=[runtime['runtime_python'],'-c','import sys; sys.exit(7)'],outputs=[],depends_on=[])])]
            elif a.mode=='timeout':request['timeout_seconds']=1;request['cases']=[request['cases'][0]]
            scenarios.append((a.mode,linux_java,request,0 if a.mode=='positive' else 2,destination))
        reports=[]
        for name,runtime,request,expected,destination in scenarios:
            request_file=out/(name+'.request.json');save(request_file,request);request_pin=pin(request_file);windows=name.startswith('windows');command=[*runtime,'-Xmx2g','-cp',str(jar) if windows else linux(jar),'org.jlinalg.cli.HailWorkflowCli','--request',str(request_file) if windows else linux(request_file)]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=environment,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode,expected=expected));assert r.returncode==expected,'Actual Java CLI unexpected exit: '+name;result=json.loads((destination/'analysis-status.json').read_bytes());warning=(destination/'WARNINGS.md').read_text(encoding='utf8');assert result['result_admitted'] is (expected==0) and 'Required action:' in warning and request_pin==pin(request_file)
            if expected==3:assert result['status']=='SKIP3_HAIL_DEPENDENCIES_UNAVAILABLE' and 'SKIP3' in warning and not (destination/'worker').exists() and not (destination/'inputs').exists(),'Skipped analysis unexpectedly captured inputs/launched workers'
            else:
                process=json.loads((destination/'worker/original.process.json').read_bytes());assert process['owned_live_after_cleanup']==[];assert json.loads((destination/'worker/java-process.json').read_bytes())['owned_live_after_cleanup']==[]
                if expected==0:
                    report=json.loads((destination/'worker/COMPLETED.json').read_bytes());assert report['status']=='PASS_COMPLETE_ORIGINAL_HAIL_WORKFLOW_CANDIDATE_ONLY' and report['complete_native_readback_verified'] is True and len(result['cases'])==10;assert pin(destination/'manifest.json')['sha256']==result['manifest_sha256'];manifest=json.loads((destination/'manifest.json').read_bytes())
                    for path,want in manifest['files'].items():assert pin(destination/path)==want,path
                    from audit_hail_workflow_controls import audit
                    save(out/'independent-output-audit.json',audit(destination/'worker',a.controls.resolve()))
                elif a.mode in ['late-liftover-failure','batch-failure']:report=json.loads((destination/'worker/COMPLETED.json').read_bytes());assert report['status']=='FAIL2_ORIGINAL_HAIL_WORKFLOW_INCOMPLETE' and len(report['cases'])==1 and (destination/'worker/raw-mixed/result.mt').is_dir(),'Earlier successful case must remain provisional and retained'
            reports.append(dict(name=name,exit_code=r.returncode,result_admitted=result['result_admitted'],status=result['status'],result_path=destination.relative_to(ROOT).as_posix()));print(name,'verified exit',r.returncode,flush=True)
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};state.update(status='PASS_ACTUAL_JAVA_HAIL_WORKFLOW_'+a.mode.upper().replace('-','_')+'_ONLY',source_pins=sources,artifact=build['artifact'],edition=a.edition,reports=reports);print(state['status'])
    except Exception as error:state.update(error=repr(error),required_action='Do not admit a partial workflow or count Hail as implemented. Preserve this request, original/native outputs and logs, inspect JVM stderr/error.traceback and correct the demonstrated discrepancy before retrying NEW paths. Required unavailable/forbidden dependencies keep analysis skipped.');(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state['required_action']+'\n\n'+repr(error)+'\n',encoding='utf8');print(state['status'],repr(error))
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual CLI/admission checks')
    raise SystemExit(main())
