#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual public-runner success and failure controls; no original source repair."""
from pathlib import Path
import argparse,subprocess,sys,traceback
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;MODULE=HERE.parent/'singler_workflows'
sys.path.insert(0,str(MODULE))
from Runtime import ACTION,load,save,pin,inventory,environment,check,check_output

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--provider-root',required=True,type=Path);p.add_argument('--provider-suffix',default='dev-3')
    p.add_argument('--controls-suffix',default='2');p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();out=a.out.absolute();owned=False;results=[]
    try:
        versions=['1.2.4','1.8.1','2.2.0','2.6.0','2.8.0']
        providers={v:a.provider_root/('singler-provider-'+v+'-'+a.provider_suffix) for v in versions}
        ready={v:load(providers[v]/'READY.json') for v in versions}
        out=check_output(out,[MODULE,HERE,*providers.values(),*[r['workspace'] for r in ready.values()]])
        out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\nOwned failure injection never changes protected runtime/source/original inputs.\n')
        for n in ['home','tmp','calls']:(out/n).mkdir()
        def inputs(v):return a.provider_root/('singler-controls-'+v+'-'+a.controls_suffix)/'inputs'
        def run(name,command,expected):
            folder=out/'calls'/name;folder.mkdir()
            with (folder/'stdout').open('xb') as so,(folder/'stderr').open('xb') as se:
                c=subprocess.Popen(list(map(str,command)),stdin=subprocess.DEVNULL,stdout=so,stderr=se)
                save(folder/'LIVE.json',dict(pid=c.pid,command=list(map(str,command))))
                try:code=c.wait(timeout=1200)
                except subprocess.TimeoutExpired:c.terminate();c.wait(timeout=30);raise
            save(folder/'PROCESS.json',dict(command=list(map(str,command)),exit_code=code,expected_exit=expected))
            if code!=expected:raise ValueError(name+' expected '+str(expected)+' but got '+str(code)+'; read full retained consoles')
            if expected and (out/name/'COMPLETED.json').exists():raise ValueError('Failed guard admitted a completion: '+name)
            results.append(dict(name=name,exit_code=code,passed=True));print('ACTUAL_PUBLIC_SINGLER_GUARD',name,code,flush=True)
        def command(v,provider,request,target):return [sys.executable,'-B',MODULE/'run.py','--edition',v,'--runtime',provider,'--request',request,'--out',target]
        for v in versions:
            check(ready[v],v);name='success-'+v;target=out/name
            run(name,command(v,providers[v],inputs(v)/'dense-default.rds',target),0)
            result=load(target/'COMPLETED.json')
            if result['status']!='PASS_ORIGINAL_SINGLER_COMPLETE_RETURN_AND_RECORDS':raise ValueError('Unexpected real public-runner success status')
            direct=a.provider_root/('singler-controls-'+v+'-'+a.controls_suffix)/'cases/dense-default/direct'
            rscript=Path(ready[v]['r_prefix'])/'bin/Rscript'
            with (out/('compare-'+v+'.stdout')).open('xb') as so,(out/('compare-'+v+'.stderr')).open('xb') as se:
                c=subprocess.run([str(rscript),'--vanilla',str(HERE/'ComparePublic.R'),ready[v]['library'],str(direct),str(target),str(out/('compare-'+v+'.rds'))],
                  env=environment(ready[v],out),stdout=so,stderr=se,stdin=subprocess.DEVNULL)
            if c.returncode:raise ValueError('Complete public result differs from independent original '+v)
        v='2.8.0';provider=providers[v];request=inputs(v)/'dense-default.rds'
        target=out/('success-'+v);before=inventory(target)
        run('existing-output-refusal',command(v,provider,request,target),2)
        if inventory(target)!=before:raise ValueError('Existing original output changed')
        for name,edition,rt,req,expected in [
          ('unresolved-literal-2.41','2.41',provider,request,3),
          ('missing-provider',v,out/'not-present-provider',request,3),
          ('wrong-provider-edition','1.8.1',provider,request,2),
          ('stale-provider',v,a.provider_root/('singler-provider-'+v+'-dev-1'),request,2),
          ('request-edition-mismatch','1.8.1',providers['1.8.1'],request,2),
          ('original-label-count-error',v,provider,inputs(v)/'label-count-mismatch.rds',2)]:
            run(name,command(edition,rt,req,out/name),expected)
        for mode,expected in [('recording-fault',2),('early-zero-exit',2),('native-signal',2),('captured-input-tamper',2),('timeout',3)]:
            name='owned-'+mode
            run(name,[sys.executable,'-B',HERE/'instrumented_runner.py','--mode',mode,'--edition',v,'--runtime',provider,'--request',request,'--out',out/name],expected)
            observation=load(out/name/'OWNED-GUARD-OBSERVATION.json')
            if mode in ['recording-fault','captured-input-tamper'] and not observation['original_return_recorded']:
                raise ValueError('Intended post-return guard never reached an original return')
            if mode=='early-zero-exit':
                failure=load(out/name/'FAILURE.json')
                if failure['actual_original_process_exit_code']!=0 or failure['original_R_status_available']:
                    raise ValueError('Zero-exit-without-completion guard did not exercise the intended boundary')
            if mode=='native-signal':
                failure=load(out/name/'FAILURE.json')
                if failure['actual_original_process_exit_code']!=-11 or failure['original_R_status_available']:
                    raise ValueError('Native-signal guard did not exercise actual fatal process termination')
        for version,r in ready.items():check(r,version)
        save(out/'MANIFEST.json',inventory(out/'calls'))
        save(out/'COMPLETED.json',dict(status='PASS_ACTUAL_PUBLIC_SINGLER_RUNNER_GUARDS',guards=results,
          five_complete_public_results_compared_to_independent_originals=True,protected_runtime_or_source_patched=False,
          owned_fault_injection_disclosed=True,new_family_credit=0,whole_goal_complete=False))
        print('PASS_ACTUAL_PUBLIC_SINGLER_RUNNER_GUARDS',len(results),flush=True);return 0
    except BaseException as e:
        print('FAIL2_SINGLER_PUBLIC_GUARDS',repr(e),ACTION,flush=True)
        if owned:save(out/'FAILURE.json',dict(cause=repr(e),traceback=traceback.format_exc(),completed_guards=results,required_action=ACTION))
        return 2

if __name__=='__main__':raise SystemExit(main())
