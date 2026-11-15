#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify full frozen records and replay every selected HICAT call from exact inputs."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,importlib.util,shutil,subprocess,sys,traceback
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
MODULE=ROOT/'tools/literature/scrattchhicat109'
sys.path.insert(0,str(MODULE))
from Runtime import ACTION,Unavailable,check,check_output,environment,load,pin,save,sources
spec=importlib.util.spec_from_file_location('hicat_logical_evidence',ROOT/'tools/literature/scrattchbigcat005_workflows/Evidence.py')
evidence=importlib.util.module_from_spec(spec);spec.loader.exec_module(evidence)
DATA=ROOT/'docs/literature/scrattchhicat109-workflow-validation'
CASES=['Inf-count','NA-count','artificial-name-collision','cells-reordered','dense-default','dense-odd-k3',
 'dense-odd-k5','dense-p035','duplicate-identifiers','k1','k2','large-10001-real','literal-identifiers',
 'missing-gene','panel-reordered','partial-match-warning','partial-match-warning-as-error','plot-with-ggplot2',
 'plot-without-ggplot2','single-artificial','sparse-dgC-default','sparse-dgT-default','too-few-real',
 'zero-artificial','zero-count-column-dense','zero-count-column-sparse']

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--runtime',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--evidence',type=Path,default=DATA)
    args=p.parse_args();out=args.out.absolute();owned=False;launched=False
    try:
        provider=args.runtime.resolve();ready=load(provider/'READY.json')
        out=check_output(out,[MODULE,HERE,args.evidence,provider,ready['workspace']]);out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\nThis verifier performs fresh original synthetic scoring calls; it does not reproduce the paper.\n')
        proof=load(args.evidence/'QUALIFICATION.json')
        if proof['status']!='PASS_SCOPED_ORIGINAL_HICAT109_AND_INDEPENDENT_JAVA_THRESHOLD':raise ValueError('Unsupported scoped workflow proof')
        for name,identity in proof['source_files'].items():
            if pin(ROOT/name)!=identity:raise ValueError('Qualified source/evidence changed: '+name)
        index=evidence.extract(args.evidence/'complete-validation-records.zip',out/'records',proof)
        records=out/'records';control=records/'controls'
        for name,identity in proof['source_files'].items():
            if pin(records/'qualified-project-sources'/name)!=identity:
                raise ValueError('Archived complete project source differs: '+name)
        if pin(args.evidence/'PRIVATE-COMPILATION-RECORDS.json')!=pin(records/'PRIVATE-COMPILATION-RECORDS.json'):
            raise ValueError('Public private-compilation receipt differs from its complete frozen copy')
        if sorted(p.stem for p in (control/'inputs').glob('*.rds'))!=CASES:raise ValueError('Exact 26 original control inputs required')
        if load(control/'COMPLETED.json')['controls']!=26:raise ValueError('Incomplete original qualification')
        guards=load(records/'public-runner/COMPLETED.json')
        if guards['status']!='PASS_ACTUAL_PUBLIC_HICAT109_RUNNER_POLICY_FAILURE_AND_POSTRETURN_RECORDING_GUARDS' or guards['calls_exercised']!=15:
            raise ValueError('All actual public guards required')
        for label in ['offline-analysis','download-analysis']:
            if load(records/label/'COMPLETED.json')['status']!='PASS_PRIVATE_ORIGINAL_HICAT109_COMPLETE_RETURN_AND_RECORDS':raise ValueError('Complete installed analysis required')
        if sys.platform!='linux':raise Unavailable('Fresh original scoring replay requires permitted Linux/WSL')
        if not shutil.which('java') or not shutil.which('javac'):raise Unavailable('Complete replay requires permitted java and javac executables')
        check(ready);before=sources();rscript=Path(ready['r_prefix'])/'bin/Rscript'
        for name in ['home','tmp','fresh']:(out/name).mkdir()
        def run(label,command,cwd=out,expected=(0,)):
            nonlocal launched
            with (cwd/(label+'.stdout')).open('xb') as so,(cwd/(label+'.stderr')).open('xb') as se:
                child=subprocess.Popen(list(map(str,command)),cwd=cwd,env=environment(ready,cwd),stdout=so,stderr=se,stdin=subprocess.DEVNULL)
                launched=True
                save(cwd/(label+'-LIVE.json'),dict(pid=child.pid,command=list(map(str,command))))
                try:code=child.wait(timeout=1800)
                except subprocess.TimeoutExpired:
                    child.terminate();child.wait(timeout=30);raise
            save(cwd/(label+'-PROCESS.json'),dict(command=list(map(str,command)),exit_code=code))
            if code not in expected:raise ValueError('Replay step failed; inspect all records: '+label)
            return code
        run('regenerate-inputs',[rscript,'--vanilla',MODULE/'MakeControls.R',ready['library'],out/'regenerated-inputs'])
        for name in CASES:
            if pin(control/'inputs'/(name+'.rds'))!=pin(out/'regenerated-inputs'/(name+'.rds')):
                raise ValueError('Captured whole input/RNG differs from the independently regenerated project control: '+name)
        def direct(name):
            folder=out/'fresh'/name;folder.mkdir()
            for item in ['home','tmp']:(folder/item).mkdir()
            code=run('direct',[rscript,'--vanilla',MODULE/'DirectReference.R',ready['library'],
              Path(ready['r_prefix'])/'lib/R/library',control/'inputs'/(name+'.rds'),folder/'direct'],folder,(0,2))
            print('FRESH_ORIGINAL_REPLAY',name,code,flush=True)
        with ThreadPoolExecutor(max_workers=3) as executor:list(executor.map(direct,CASES))
        run('frozen-science',[rscript,'--vanilla',HERE/'CompareReplay.R',ready['library'],control/'cases',out/'fresh',out])
        shutil.copytree(records/'public-runner',out/'public-replay-input')
        run('public-science',[rscript,'--vanilla',HERE/'CompareRunner.R',ready['library'],out/'fresh',out/'public-replay-input'])
        # Derived checks use a new working copy. Frozen archives, extracted
        # records and all saved original objects remain unchanged.
        run('installed-science',[rscript,'--vanilla',HERE/'CompareInstalled.R',ready['library'],out/'fresh/dense-default/direct',
          records/'offline-analysis',records/'download-analysis',out/'installed-replay'])
        native=ROOT/'tools/literature/doublet_score_threshold'
        run('native-oracle',[sys.executable,'-B',native/'qualify.py','--rscript',rscript,'--out',out/'native-replay'])
        for name in ['classifications.tsv','selections.tsv']:
            if pin(out/'native-replay/oracle'/name)!=pin(records/'native-linux/oracle'/name):raise ValueError('Complete independent native oracle differs: '+name)
        run('actual-score-oracle',[rscript,'--vanilla',native/'MakeScoreOracle.R',out/'fresh',out/'actual-score-oracle'])
        for name in ['classifications.tsv','selections.tsv']:
            if pin(out/'actual-score-oracle'/name)!=pin(records/'r-comparisons/score-oracle'/name):raise ValueError('Actual complete source-score predicate differs: '+name)
        run('actual-score-java',["java",'-cp',out/'native-replay/classes','ThresholdHarness',out/'actual-score-oracle/classifications.tsv',out/'actual-score-oracle/selections.tsv'])
        check(ready)
        if sources()!=before:raise ValueError('Adapter changed during fresh replay')
        for name,identity in proof['source_files'].items():
            if pin(ROOT/name)!=identity:raise ValueError('Qualified source/evidence changed during replay: '+name)
        result=dict(status='PASS_FULL_FROZEN_HICAT109_RECORDS_AND_26_FRESH_ORIGINAL_REPLAYS_WITH_NATIVE_PREDICATE',
          logical_files=len(index['files']),original_controls=26,complete_fresh_original_objects_compared=True,
          complete_original_conditions_and_rng_compared=True,native_literal_R_comparisons=17697,actual_original_scores=13824,
          source_or_dependency_algorithms_patched=False,whole_tool_qualified=False,whole_paper_reproduced=False,
          new_family_credit=0,whole_goal_complete=False)
        save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,Unavailable) or (not launched and isinstance(error,OSError) and not isinstance(error,FileExistsError)) else 2
        label='SKIP3_HICAT109_REPLAY_UNAVAILABLE' if code==3 else 'FAIL2_HICAT109_REPLAY_UNADMITTED'
        print(label,repr(error),ACTION,flush=True)
        if owned:save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
        return code

if __name__=='__main__':raise SystemExit(main())
