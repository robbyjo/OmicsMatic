#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run both original-runtime adapter modes and genuine replays on all fixed synthetic cases."""
from pathlib import Path
import argparse,concurrent.futures,functools,hashlib,json,os,shutil,subprocess,sys,time,zipfile

ROOT=Path(__file__).resolve().parents[3]
FIXTURE=ROOT/'src/test/resources/org/jlinalg/alignment/sambamba-original-markdup-066-082.zip'
def pin(p):
    raw=Path(p).read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(p,x):
    with Path(p).open('xb') as f:f.write((json.dumps(x,indent=2)+'\n').encode())
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True)
    p.add_argument('--binary066',type=Path,required=True);p.add_argument('--binary082',type=Path,required=True)
    p.add_argument('--out',type=Path,required=True);p.add_argument('--wsl');a=p.parse_args()
    if os.name=='nt' and not a.wsl:raise FileNotFoundError('Select the permitted WSL distribution with --wsl; the execution JVM must be Linux')
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
    if pin(FIXTURE)['sha256']!='a2727c48839058a5f24688f356904e0d21005a9523e8c99cf7e1c51be26df1e8':raise ValueError('Exact independent original fixture differs')
    jar=out/'candidate.jar';before=pin(a.jar);shutil.copy2(a.jar,jar)
    if before!=pin(jar) or before!=pin(a.jar):raise ValueError('Candidate changed during capture')
    reference=out/'reference';reference.mkdir()
    with zipfile.ZipFile(FIXTURE) as z:
        for name in z.namelist():
            target=reference/name
            if not target.resolve().is_relative_to(reference) or target.exists():raise ValueError('Unsafe or duplicate fixture member')
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(name))
    prefix=['wsl.exe','-d',a.wsl,'--'] if os.name=='nt' else []
    @functools.lru_cache(maxsize=None)
    def convert(text):
        path=Path(text).resolve()
        if os.name!='nt':return str(path)
        r=subprocess.run([*prefix,'/usr/bin/wslpath','-u',path.as_posix()],capture_output=True,timeout=30,check=True)
        return r.stdout.decode().strip()
    def linux(path):return convert(str(path))
    common=[*prefix,'/usr/bin/timeout','--signal=TERM','--kill-after=5','300','/usr/bin/java','-Xmx2g','-cp',linux(jar),'org.jlinalg.cli.SambambaStreamingWorkflowCli']
    runs=out/'runs';runs.mkdir();checks=[]
    def run(label,command,where):
        start=time.monotonic();r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=330)
        (where/(label+'.stdout')).write_bytes(r.stdout);(where/(label+'.stderr')).write_bytes(r.stderr)
        row=dict(command=command,exit_code=r.returncode,seconds=round(time.monotonic()-start,2));save(where/(label+'.process.json'),row)
        if r.returncode:raise RuntimeError(label+' failed: '+r.stderr.decode(errors='replace')[-1600:])
        return row
    cases=sorted(p.name for p in reference.iterdir() if p.is_dir())
    if len(cases)!=8:raise ValueError('Complete original case inventory differs')
    def one(task):
        name,edition,mode=task;where=runs/name/edition/mode;where.mkdir(parents=True)
        request=where/'request.properties'
        config=dict(bam=linux(reference/name/'input.bam'),sambamba=linux(a.binary066 if edition=='0.6.6' else a.binary082),edition=edition,action=mode,
            threads='1',compression='6',hash_table_size='1024',overflow_list_size='64',sort_buffer_mb='16',io_buffer_mb='1',wall_ms='60000',address_space_mb='1024',work_mb='256',max_files='128',audit_compressed_bytes='1099511627776',audit_decoded_bytes='8796093022208',audit_records='1099511627776',audit_record_bytes='16777216',audit_header_bytes='4194304',audit_dictionary_bytes='4194304')
        request.write_bytes(('\n'.join(k+'='+v for k,v in config.items())+'\n').encode())
        normal=run('analysis',[*common,'--request',linux(request),'--out',linux(where/'analysis')],where)
        replay=run('verification',[*common,'--verify',linux(where/'analysis'),'--out',linux(where/'verification')],where)
        row=dict(case=name,edition=edition,mode=mode,analysis=normal,verification=replay)
        print(json.dumps(dict(case=name,edition=edition,mode=mode,status='PASS_ORIGINAL_EXECUTION_AND_GENUINE_REPLAY')),flush=True);return row
    tasks=[(name,version,mode) for name in cases for version in ['0.6.6','0.8.2'] for mode in ['mark','remove']]
    failures=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures={pool.submit(one,task):task for task in tasks}
        for future in concurrent.futures.as_completed(futures):
            try:checks.append(future.result())
            except BaseException as e:failures.append(dict(task=futures[future],error=repr(e)));print('CASE_FAILURE',futures[future],repr(e),flush=True)
    save(out/'CASE-RESULTS.json',dict(checks=checks,failures=failures))
    if failures or len(checks)!=32:raise ValueError('Full original-runtime matrix is incomplete; retain all failures')
    comparison=[*prefix,'/usr/bin/java','-Xmx2g','-cp',linux(jar),linux(ROOT/'tools/literature/sambamba_execution_validation/CompareAll.java'),linux(reference),linux(runs),linux(out/'BASELINE-COMPARISON.properties')]
    run('baseline-comparison',comparison,out)
    save(out/'COMPLETED.json',dict(status='PASS_32_ORIGINAL_RUNTIME_CASES_32_GENUINE_REPLAYS_AND_96_BASELINE_BAM_COMPARISONS',candidate=pin(jar),case_count=32,genuine_original_replay_count=32,baseline_BAM_comparisons=96,checks=checks,bai_independently_qualified=False,whole_paper_reproduced=False,new_catalog_credit=0,whole_goal_complete=False))
    print('PASS_32_CASES_32_REPLAYS_96_BASELINE_BAM_COMPARISONS',flush=True)
if __name__=='__main__':
    try:main()
    except FileNotFoundError as e:print('SKIP3_EXECUTION_VALIDATION_PREREQUISITE',repr(e),'Supply the named permitted runtime/JAR/files and retry NEW paths.',flush=True);raise SystemExit(3)
    except Exception as e:print('FAIL2_EXECUTION_VALIDATION_UNADMITTED',repr(e),'Retain every output and error; resolve the cause and retry NEW paths.',flush=True);raise SystemExit(2)
