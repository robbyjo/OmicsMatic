#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Fresh original-versus-worker controls; separate from the public runner guards."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,subprocess,sys,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,load,save,pin,check,check_output,environment,inventory,profile

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();ready=load(args.runtime/'READY.json');out=args.out.absolute();owned=False
    try:
        out=check_output(out,[HERE,args.runtime,ready['workspace'],*ready['source_paths'].values()])
        out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\nFresh Worker.R controls do not stand in for public run.py guard tests.\n')
        before={p.name:pin(p) for p in HERE.iterdir() if p.is_file()}
        save(out/'SOURCES.json',before);check(ready)
        for name in ['home','tmp','cases']:(out/name).mkdir()
        rscript=str(Path(ready['r_prefix'])/'bin/Rscript')
        def run(label,command,cwd):
            with (cwd/(label+'.stdout')).open('xb') as so,(cwd/(label+'.stderr')).open('xb') as se:
                child=subprocess.Popen(list(map(str,command)),cwd=cwd,env=environment(ready,cwd),stdout=so,stderr=se,stdin=subprocess.DEVNULL)
                save(cwd/(label+'-LIVE.json'),dict(pid=child.pid,command=list(map(str,command))))
                try:code=child.wait(timeout=1800)
                except subprocess.TimeoutExpired:
                    child.terminate();child.wait(timeout=30);raise
            save(cwd/(label+'-PROCESS.json'),dict(command=list(map(str,command)),exit_code=code))
            return code
        assert run('make-inputs',[rscript,'--vanilla',HERE/'MakeControls.R',ready['library'],out/'inputs'],out)==0
        def case(request):
            name=request.stem;folder=out/'cases'/name;folder.mkdir()
            for path in ['home','tmp','worker','worker/original']:(folder/path).mkdir()
            code=run('direct',[rscript,'--vanilla',HERE/'DirectReference.R',ready['library'],
              str(Path(ready['r_prefix'])/'lib/R/library'),request,folder/'direct'],folder)
            control=dict(profile=ready['source_profile'],library=ready['library'],base_library=str(Path(ready['r_prefix'])/'lib/R/library'),
              source_paths=ready['source_paths'],dependency_versions=profile()['dependency_versions'],
              namespace_functions={'scrattch.hicat':360,'RANN':1},audit=str(HERE/'AuditNamespace.R'),
              request=str(request),output=str(folder/'worker/original'))
            save(folder/'worker-control.json',control)
            worker=run('worker',[rscript,'--vanilla',HERE/'Worker.R',folder/'worker-control.json'],folder)
            print('ACTUAL_ORIGINAL_CONTROL',name,'direct',code,'worker',worker,flush=True)
            if code not in [0,2] or worker not in [0,2]:raise ValueError('Unexpected control exit code '+name)
            return dict(name=name,direct=code,worker=worker)
        with ThreadPoolExecutor(max_workers=3) as executor:
            calls=list(executor.map(case,sorted((out/'inputs').glob('*.rds'))))
        save(out/'CALLS.json',calls)
        code=run('compare',[rscript,'--vanilla',HERE/'CompareControls.R',ready['library'],out/'cases',out],out)
        if code:raise ValueError('Complete original comparisons differ; retain all original objects and diagnostics')
        check(ready)
        if before!={p.name:pin(p) for p in HERE.iterdir() if p.is_file()}:raise ValueError('Validation source changed during controls')
        save(out/'MANIFEST.json',inventory(out/'cases'))
        result=dict(status='PASS_FRESH_COMPLETE_ORIGINAL_HICAT109_WORKER_CONTROLS_NOT_PUBLIC_RUNNER_GUARDS',
          controls=len(calls),comparisons=pin(out/'COMPARISONS.json'),complete_original_records_retained=True,
          source_or_dependency_algorithms_patched=False,source_or_runtime_redistribution_authorized=False,
          whole_tool_qualified=False,whole_paper_reproduced=False,new_family_credit=0,whole_goal_complete=False)
        save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
    except BaseException as error:
        print('FAIL2_HICAT109_CONTROL_VALIDATION',repr(error),ACTION,flush=True)
        if owned:save(out/'FAILURE.json',dict(cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
        return 2

if __name__=='__main__':raise SystemExit(main())
