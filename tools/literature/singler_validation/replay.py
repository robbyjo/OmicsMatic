#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Full frozen-record readback and fresh original replay for all five SingleR releases."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,importlib.util,subprocess,sys,traceback
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];MODULE=ROOT/'tools/literature/singler_workflows'
sys.path.insert(0,str(MODULE))
from Runtime import ACTION,Unavailable,check,check_output,environment,load,pin,save,sources
spec=importlib.util.spec_from_file_location('singler_replay_evidence',ROOT/'tools/literature/scrattchbigcat005_workflows/Evidence.py')
evidence=importlib.util.module_from_spec(spec);spec.loader.exec_module(evidence)
VERSIONS=['1.2.4','1.8.1','2.2.0','2.6.0','2.8.0'];CASES=load(HERE/'cases.json')
DATA=ROOT/'docs/literature/singler-workflow-validation'

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--runtime',action='append',required=True,help='EDITION=/absolute/completed/provider; supply all five declared editions')
    p.add_argument('--evidence',type=Path,default=DATA);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--jobs',type=int,default=3)
    a=p.parse_args();out=a.out.absolute();owned=False;launched=False
    try:
        providers={}
        for row in a.runtime:
            version,path=row.split('=',1)
            if version in providers:raise ValueError('Duplicate runtime edition')
            providers[version]=Path(path).resolve()
        if set(providers)!=set(VERSIONS):raise Unavailable('All five exact permitted providers are required for complete scientific replay')
        ready={v:load(providers[v]/'READY.json') for v in VERSIONS}
        out=check_output(out,[HERE,MODULE,a.evidence,*providers.values(),*[r['workspace'] for r in ready.values()]])
        out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\nThis verifier performs 250 fresh original synthetic controls. It reproduces no paper.\n')
        if a.jobs not in range(1,7):raise ValueError('Use one to six independent original calls at a time')
        proof=load(a.evidence/'QUALIFICATION.json')
        if proof['status']!='PASS_SCOPED_FIVE_ORIGINAL_SINGLER_RELEASES_AND_COMPLETE_RECORDING':raise ValueError('Unsupported scientific qualification')
        for name,identity in proof['source_files'].items():
            if pin(ROOT/name)!=identity:raise ValueError('Qualified source differs: '+name)
        index=evidence.extract(a.evidence/'complete-validation-records.zip',out/'records',proof);records=out/'records'
        for name,identity in proof['source_files'].items():
            if pin(records/'qualified-project-sources'/name)!=identity:raise ValueError('Complete archived source differs: '+name)
        if sys.platform!='linux':raise Unavailable('Fresh original scientific replay requires permitted Linux/WSL')
        if len(CASES)!=50 or CASES!=sorted(set(CASES)):raise ValueError('Exact 50-case source population required')
        for v in VERSIONS:check(ready[v],v)
        before={v:sources(v) for v in VERSIONS}
        for name in ['home','tmp','fresh']:(out/name).mkdir()
        def run(version,label,command,cwd,expected=(0,)):
            nonlocal launched
            with (cwd/(label+'.stdout')).open('xb') as so,(cwd/(label+'.stderr')).open('xb') as se:
                child=subprocess.Popen(list(map(str,command)),cwd=cwd,env=environment(ready[version],cwd),stdin=subprocess.DEVNULL,stdout=so,stderr=se)
                launched=True;save(cwd/(label+'-LIVE.json'),dict(pid=child.pid,command=list(map(str,command))))
                try:code=child.wait(timeout=1800)
                except subprocess.TimeoutExpired:child.terminate();child.wait(timeout=30);raise
            save(cwd/(label+'-PROCESS.json'),dict(command=list(map(str,command)),exit_code=code))
            if code not in expected:raise ValueError('Original replay step failed: '+version+'/'+label+'; read full logs')
            return code
        for version in VERSIONS:
            captured=records/'current'/('controls-'+version);folder=out/'fresh'/version;folder.mkdir()
            for n in ['home','tmp','cases']:(folder/n).mkdir()
            result=load(captured/'COMPLETED.json')
            if result['status']!='PASS_FRESH_COMPLETE_ORIGINAL_SINGLER_WORKER_CONTROLS_NOT_PUBLIC_RUNNER_GUARDS' or result['controls']!=50:
                raise ValueError('Incomplete qualified original population: '+version)
            if sorted(p.stem for p in (captured/'inputs').glob('*.rds'))!=CASES:raise ValueError('Original input population differs: '+version)
            command=[Path(ready[version]['r_prefix'])/'bin/Rscript','--vanilla',MODULE/'MakeControls.R',ready[version]['library'],version,folder/'regenerated-inputs',captured/'inputs/approximate-Annoy-neighbours.rds']
            run(version,'regenerate-inputs',command,folder)
            for name in ['CONTROLS.json',*[c+'.rds' for c in CASES]]:
                if pin(captured/'inputs'/name)!=pin(folder/'regenerated-inputs'/name):raise ValueError('Complete captured input/RNG differs from source regeneration: '+version+'/'+name)
        def direct(task):
            version,name=task;r=ready[version];folder=out/'fresh'/version/'cases'/name;folder.mkdir()
            for n in ['home','tmp']:(folder/n).mkdir()
            original_control=load(records/'current'/('controls-'+version)/'cases'/name/'direct-control.json')
            control=dict(original_control,library=r['library'],base_library=str(Path(r['r_prefix'])/'lib/R/library'),
              source_paths=r['source_paths'],request=str(records/'current'/('controls-'+version)/'inputs'/(name+'.rds')),output=str(folder/'direct'),audit=str(MODULE/'AuditNamespace.R'))
            save(folder/'direct-control.json',control)
            code=run(version,'direct',[Path(r['r_prefix'])/'bin/Rscript','--vanilla',MODULE/'DirectReference.R',folder/'direct-control.json'],folder,(0,2))
            print('FRESH_FROZEN_SINGLER_ORIGINAL_REPLAY',version,name,code,flush=True)
        with ThreadPoolExecutor(max_workers=a.jobs) as pool:list(pool.map(direct,[(v,c) for v in VERSIONS for c in CASES]))
        for version in VERSIONS:
            r=ready[version];folder=out/'fresh'/version;rscript=Path(r['r_prefix'])/'bin/Rscript'
            run(version,'compare-both-frozen-channels',[rscript,'--vanilla',HERE/'CompareReplay.R',r['library'],records/'current'/('controls-'+version),folder/'cases',folder,HERE/'cases.json'],folder)
            run(version,'compare-complete-public-success',[rscript,'--vanilla',HERE/'ComparePublic.R',r['library'],folder/'cases/dense-default/direct',
              records/'current/public-runner'/('success-'+version),folder/'public-science.rds'],folder)
        for kind,version in [('offline124','1.2.4'),('download280','2.8.0')]:
            r=ready[version];folder=out/'fresh'/version
            run(version,'compare-installed-'+kind,[Path(r['r_prefix'])/'bin/Rscript','--vanilla',HERE/'ComparePublic.R',r['library'],folder/'cases/dense-default/direct',
              records/'current'/('installed-analysis-'+kind)/'analysis',folder/('installed-'+kind+'.rds')],folder)
        folder=out/'fresh/2.8.0';r=ready['2.8.0'];rscript=Path(r['r_prefix'])/'bin/Rscript'
        for label in ['owned-recording-fault','owned-captured-input-tamper']:
            target=records/'current/public-runner'/label
            if (target/'COMPLETED.json').exists() or load(target/'FAILURE.json')['complete_result_admitted']:
                raise ValueError('Failed public run incorrectly admitted: '+label)
            run('2.8.0','compare-unadmitted-original-'+label,[rscript,'--vanilla',HERE/'ComparePublic.R',r['library'],folder/'cases/dense-default/direct',target,folder/(label+'.rds')],folder)
        run('2.8.0','compare-public-original-error',[rscript,'--vanilla',HERE/'ComparePublicFailure.R',r['library'],folder/'cases/label-count-mismatch/direct',
          records/'current/public-runner/original-label-count-error',folder/'public-original-error.rds'],folder)
        for version in VERSIONS:
            check(ready[version],version)
            if sources(version)!=before[version]:raise ValueError('Protected adapter changed during replay')
        for name,identity in proof['source_files'].items():
            if pin(ROOT/name)!=identity:raise ValueError('Qualified source changed during frozen replay: '+name)
        result=dict(status='PASS_FULL_FROZEN_SINGLER_RECORDS_AND_250_FRESH_ORIGINAL_REPLAYS',
          logical_files=len(index['files']),profiles=VERSIONS,original_controls=250,
          both_complete_frozen_original_channels_compared=True,complete_conditions_and_rng_compared=True,
          five_public_successes_both_installed_analyses_and_retained_postreturn_failures_compared=True,
          whole_input_regeneration_verified=True,captured_Annoy_directory_supplied_explicitly_to_original_constructor=True,source_or_dependency_algorithms_patched=False,
          whole_tool_qualified=False,whole_paper_reproduced=False,new_family_credit=0,whole_goal_complete=False)
        save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
    except BaseException as e:
        code=3 if isinstance(e,Unavailable) or (not launched and isinstance(e,OSError) and not isinstance(e,FileExistsError)) else 2
        label='SKIP3_SINGLER_REPLAY_UNAVAILABLE' if code==3 else 'FAIL2_SINGLER_REPLAY_UNADMITTED'
        print(label,repr(e),ACTION,flush=True)
        if owned:
            try:save(out/'FAILURE.json',dict(status=label,cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION))
            except BaseException as recording:print('FAIL2_REPLAY_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
        return code

if __name__=='__main__':raise SystemExit(main())
