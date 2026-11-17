#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the complete local captured-source control family, with at most two concurrent cases."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,shutil,subprocess,sys,time
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);engine=p.add_mutually_exclusive_group(required=True);engine.add_argument('--compiled',type=Path);engine.add_argument('--jar',type=Path);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--operation',choices=['workflow','bundle'],default='workflow');p.add_argument('--include-profiles',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    if a.jar:a.operation='bundle'
    producer=Path(__file__).with_name('qualify_workflows.py');expected=pin(producer);shutil.copyfile(producer,out/'as-run-qualify_workflows.py');shutil.copyfile(Path(__file__),out/'as-run-workflow_batch.py')
    cases=['config-bindings','standard','script','checkpoint','retry','pipe','repeated','extended','failed-rule','run']+(['profile-resource','profile-conda'] if a.include_profiles else []);write(out/'STARTED.json',dict(cases=cases,operation=a.operation,max_concurrent_cases=2,qualifier=expected,compiled_record=pin(a.compiled/'COMPILED.json') if a.compiled else None,public_jar=pin(a.jar.resolve()) if a.jar else None))
    def run(case):
        command=[sys.executable,'-I','-B',str(producer),*(['--jar',str(a.jar.resolve())] if a.jar else ['--compiled',str(a.compiled.resolve())]),'--runtime',str(a.runtime.resolve()),'--case',case,'--operation',a.operation,'--out',str(out/case)];start=time.monotonic()
        with (out/(case+'.stdout')).open('xb') as so,(out/(case+'.stderr')).open('xb') as se:process=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=660)
        row=dict(case=case,command=command,exit_code=process.returncode,seconds=round(time.monotonic()-start,3));write(out/(case+'.process.json'),row);print(json.dumps(row),flush=True);return row
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:rows=list(pool.map(run,cases))
    write(out/'PROCESSES.json',rows);assert pin(producer)==expected,'Do not change a live qualification producer';assert all(r['exit_code']==0 for r in rows),'Inspect retained failed-case diagnostics; successful cases remain separate'
    for case in cases:assert json.loads((out/case/'COMPLETED.json').read_bytes())['status']=='PASS_CAPTURED_ORIGINAL_WORKFLOW_DEVELOPMENT_CONTROL_NOT_FINAL_ADMISSION'
    status='PASS_TWELVE_CAPTURED_ORIGINAL_SOURCE_AND_PROFILE_CONTROLS_NOT_FINAL_WORKFLOW_ADMISSION' if a.include_profiles else 'PASS_TEN_CAPTURED_ORIGINAL_SOURCE_CONTROLS_NOT_FINAL_WORKFLOW_ADMISSION'
    write(out/'COMPLETED.json',dict(status=status,operation=a.operation,public_cli=a.jar is not None,cases=cases,processes=rows,new_catalog_credit=0,user_analysis_admitted=False,whole_goal_complete=False));print(status,flush=True)
if __name__=='__main__':main()
