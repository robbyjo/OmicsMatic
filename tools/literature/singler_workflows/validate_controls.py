#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent original annotation versus Worker.R, including all original fields."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,subprocess,sys,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,load,save,pin,check,check_output,environment,inventory,profile

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--edition',required=True);p.add_argument('--runtime',type=Path,required=True)
    p.add_argument('--out',type=Path,required=True);p.add_argument('--jobs',type=int,default=3)
    a=p.parse_args();out=a.out.absolute();owned=False
    try:
        ready=load(a.runtime/'READY.json');selected=profile(a.edition)
        out=check_output(out,[HERE,a.runtime,ready['workspace'],*ready['source_paths'].values()])
        out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\nDirect/Worker comparisons are separate from public-runner guard tests.\n')
        if a.jobs not in range(1,5):raise ValueError('Use one to four independent controls at a time')
        before={p.relative_to(HERE).as_posix():pin(p) for p in HERE.rglob('*') if p.is_file()}
        save(out/'SOURCES.json',before);check(ready,a.edition)
        for name in ['home','tmp','cases']:(out/name).mkdir()
        rscript=Path(ready['r_prefix'])/'bin/Rscript'
        def run(label,command,cwd):
            with (cwd/(label+'.stdout')).open('xb') as so,(cwd/(label+'.stderr')).open('xb') as se:
                c=subprocess.Popen(list(map(str,command)),cwd=cwd,env=environment(ready,cwd),stdout=so,stderr=se,stdin=subprocess.DEVNULL)
                save(cwd/(label+'-LIVE.json'),dict(pid=c.pid,command=list(map(str,command))))
                try:code=c.wait(timeout=1800)
                except subprocess.TimeoutExpired:c.terminate();c.wait(timeout=30);raise
            save(cwd/(label+'-PROCESS.json'),dict(command=list(map(str,command)),exit_code=code));return code
        if run('make-inputs',[rscript,'--vanilla',HERE/'MakeControls.R',ready['library'],a.edition,out/'inputs'],out):
            raise ValueError('Synthetic input preparation failed; retain complete logs')
        def case(request):
            folder=out/'cases'/request.stem;folder.mkdir()
            for n in ['home','tmp','worker','worker/original']:(folder/n).mkdir()
            control=dict(edition=a.edition,r_version=selected['r_version'],library=ready['library'],
              base_library=str(Path(ready['r_prefix'])/'lib/R/library'),source_paths=ready['source_paths'],
              package_versions={n:v['version'] for n,v in selected['packages'].items()},
              request=str(request),audit=str(HERE/'AuditNamespace.R'),output=str(folder/'direct'))
            save(folder/'direct-control.json',control)
            direct=run('direct',[rscript,'--vanilla',HERE/'DirectReference.R',folder/'direct-control.json'],folder)
            control['output']=str(folder/'worker/original');save(folder/'worker-control.json',control)
            worker=run('worker',[rscript,'--vanilla',HERE/'Worker.R',folder/'worker-control.json'],folder)
            print('FRESH_ORIGINAL_SINGLER_CONTROL',a.edition,request.stem,'direct',direct,'worker',worker,flush=True)
            return dict(name=request.stem,direct=direct,worker=worker)
        with ThreadPoolExecutor(max_workers=a.jobs) as executor:calls=list(executor.map(case,sorted((out/'inputs').glob('*.rds'))))
        save(out/'CALLS.json',calls)
        if any(c['direct'] not in [0,2] or c['worker'] not in [0,2] for c in calls):
            raise ValueError('Unexpected process termination; no full-object qualification; retain all original raw diagnostics')
        if run('compare',[rscript,'--vanilla',HERE/'CompareControls.R',ready['library'],out/'cases',out/'inputs',out],out):
            raise ValueError('Complete original comparisons differ; inspect every complete object, condition and RNG record')
        check(ready,a.edition)
        if before!={p.relative_to(HERE).as_posix():pin(p) for p in HERE.rglob('*') if p.is_file()}:
            raise ValueError('Qualification code changed during controls')
        save(out/'MANIFEST.json',inventory(out/'cases'))
        result=dict(status='PASS_FRESH_COMPLETE_ORIGINAL_SINGLER_WORKER_CONTROLS_NOT_PUBLIC_RUNNER_GUARDS',edition=a.edition,
          controls=len(calls),complete_returns=sum(c['direct']==0 for c in calls),original_failures_without_result=sum(c['direct']==2 for c in calls),
          comparisons=pin(out/'COMPARISONS.json'),complete_original_records_retained=True,source_or_dependency_algorithms_patched=False,
          whole_tool_qualified=False,whole_paper_reproduced=False,new_family_credit=0,whole_goal_complete=False)
        save(out/'COMPLETED.json',result);print(result['status'],a.edition,flush=True);return 0
    except BaseException as e:
        print('FAIL2_SINGLER_CONTROL_VALIDATION',repr(e),ACTION,flush=True)
        if owned:save(out/'FAILURE.json',dict(cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION))
        return 2

if __name__=='__main__':raise SystemExit(main())
