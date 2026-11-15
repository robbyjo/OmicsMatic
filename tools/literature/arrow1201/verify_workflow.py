#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute real Arrow I/O/query controls against independent literal upstream calls."""
from pathlib import Path
import argparse, subprocess, sys, time, traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,load,save,pin,check,environment

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    parser.add_argument('--runtime',required=True,type=Path)
    parser.add_argument('--out',required=True,type=Path)
    args=parser.parse_args();out=args.out.absolute();owned=False
    def run(label,command,env):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
            child=subprocess.run(list(map(str,command)),cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
        save(out/(label+'-PROCESS.json'),dict(command=list(map(str,command)),exit_code=child.returncode))
        print('ORIGINAL_ARROW1201_CONTROL_PROCESS',label,child.returncode,flush=True)
        return child.returncode
    try:
        runtime=args.runtime.resolve()
        if out!=out.resolve() or out.is_relative_to(runtime) or runtime.is_relative_to(out):
            raise ValueError('Use a NEW canonical verification directory separate from the provider')
        out.mkdir(parents=True,exist_ok=False);owned=True
        if sys.platform!='linux':raise Unavailable('Complete original comparisons require permitted Linux/WSL')
        ready=load(runtime/'READY.json');check(ready)
        for name in ['home','tmp']:(out/name).mkdir()
        env=environment(ready,out);rscript=Path(ready['r_prefix'])/'bin/Rscript'
        fixtures=out/'fixtures'
        if run('generate',[rscript,'--vanilla',HERE/'GenerateWorkflowControls.R',fixtures],env):
            raise ValueError('Original fixture generation failed; inspect complete logs')
        errors={'read-broken','query-missing-column','query-unsupported'}
        names=sorted(p.stem for p in (fixtures/'requests').glob('*.rds'))
        if len(names)!=13:raise ValueError('Expected complete 13-case original control population')
        cases={}
        for name in names:
            case=out/name;case.mkdir()
            adapter=case/'adapter';reference=case/'original-reference'
            code=run(name+'-adapter',[sys.executable,'-B',HERE/'run.py','--runtime',runtime,
                '--request',fixtures/'requests'/(name+'.rds'),'--assets',fixtures/'assets','--out',adapter],env)
            if code != (2 if name in errors else 0):raise ValueError('Unexpected adapter status for '+name)
            if run(name+'-direct',[rscript,'--vanilla',HERE/'DirectWorkflowReference.R',name,fixtures/'assets',reference],env):
                raise ValueError('Independent original reference harness failed: '+name)
            returned=load(reference/'REFERENCE.json')['returned']
            if returned != (name not in errors):raise ValueError('Unexpected original return/failure for '+name)
            if run(name+'-compare',[rscript,'--vanilla',HERE/'CompareWorkflow.R',reference,adapter/'original',case/'COMPARISON.json'],env):
                raise ValueError('Independent original value/RNG or error contract differs: '+name)
            cases[name]=dict(adapter_exit=code,reference_returned=returned,comparison=load(case/'COMPARISON.json'))
        check(ready)
        record=dict(status='PASS_ARROW1201_DEVELOPMENT_DIRECT_ORIGINAL_VALUE_AND_ERROR_CONTROLS',
            controls=13,complete_returns=10,original_failures_without_result=3,
            complete_return_identity_and_numeric_payloads=True,complete_RNG_compared=True,
            complete_original_conditions_retained=True,full_condition_identity_claimed=False,
            live_arrow_pointer_identity_claimed=False,reported_dimension_fixture=[50000,500],
            author_parquet_schema_recovered=False,cases=cases,provider=pin(runtime/'READY.json'),
            new_family_credit=0,catalog_admission_pending=True,whole_goal_complete=False)
        save(out/'COMPLETED.json',record)
        (out/'WARNINGS.md').write_text('# Development controls; final qualification and catalog admission pending\n\n'
            'Complete original R conditions are retained, including all original failures. The comparator reports '
            'whole-condition identity separately; the admission test currently checks complete returned values, '
            'numeric bit payloads, RNG and original error class/message. Review all context differences before qualification.\n\n'
            +ACTION+'\n',encoding='utf-8')
        print(record['status'],flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        status='SKIP3_ARROW1201_CONTROL_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ARROW1201_CONTROL_UNADMITTED'
        print(status,repr(error),ACTION,flush=True)
        if owned:
            try:
                save(out/'FAILURE.json',dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
                (out/'WARNINGS.md').write_text('# '+status+'\n\n'+repr(error)+'\n\n'+ACTION+'\n',encoding='utf-8')
            except BaseException as recording:
                code=2;print('FAIL2_CONTROL_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':raise SystemExit(main())
