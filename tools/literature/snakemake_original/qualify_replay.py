#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Qualify two genuine engine rerun generations with the controlled original caller paths unavailable."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,signal,subprocess,sys,tempfile,time,traceback
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def files(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--compiled',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--parent-case',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py');stage=None;moved=[];rows=[];before={}
    try:
        assert sys.platform=='linux';compiled=json.loads((a.compiled/'COMPILED.json').read_bytes());jar=compiled['baseline_jar'];assert pin(Path(jar['path']))=={k:jar[k] for k in ['bytes','sha256']};assert all(pin(a.compiled/'classes'/n)==v for n,v in compiled['classes'].items())
        parent=a.parent_case.resolve()/'records/execution/bundle';parent_before=files(parent);control=json.loads((a.parent_case/'COMPLETED.json').read_bytes());assert not control['expected_failure']
        deployment=json.loads((a.parent_case/'DEPLOYMENT.json').read_bytes());caller=Path(deployment['native_stage']);assert caller.parent==Path('/tmp') and caller.name.startswith('omicsmatic-snakemake-captured-')
        capture=json.loads((parent/'capture.json').read_bytes());originals=[Path(row['source']) for row in capture['roots']]
        for original in originals:
            assert original.parent==caller and not original.is_symlink() and original.name in ['input','data']
            if original.exists():
                before[str(original)]=files(original);retained=caller/('retained-for-rerun-'+original.name);assert not retained.exists();original.rename(retained);moved.append((original,retained))
        assert all(not p.exists() for p in originals);write(out/'ORIGINAL-CALLER-UNAVAILABLE.json',dict(paths=list(map(str,originals)),controlled_originals_temporarily_relocated=[dict(original=str(p),retained=str(q)) for p,q in moved],already_absent=[str(p) for p in originals if str(p) not in before],project_caller_copies_unchanged=True))
        stage=Path(tempfile.mkdtemp(prefix='omicsmatic-snakemake-replay-control-',dir='/tmp'))
        def run(mode,path,name):
            command=['/usr/bin/java','-cp',compiled['classpath'],compiled['probe'],mode,str(path)];start=time.monotonic()
            with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:
                process=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
                try:process.wait(timeout=240)
                except BaseException:
                    process.terminate()
                    try:process.wait(timeout=15)
                    except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
                    raise
            row=dict(name=name,command=command,exit_code=process.returncode,seconds=round(time.monotonic()-start,3));write(out/(name+'.PROCESS.json'),row);rows.append(row);assert process.returncode==0,(name,(out/(name+'.stderr')).read_text())
        run('verify',parent,'parent')
        for generation in [1,2]:
            output=stage/('generation-'+str(generation));request=stage/('request-'+str(generation)+'.json');write(request,dict(bundle=str(parent),runtime=str(a.runtime.resolve()),out=str(output)));run('replay',request,'rerun-'+str(generation));parent=output/'bundle';run('verify',parent,'verify-'+str(generation));assert all(not p.exists() for p in originals)
        assert files(a.parent_case/'records/execution/bundle')==parent_before
        shutil.copytree(stage,out/'records',symlinks=True)
    except BaseException as error:
        if stage is not None and not (out/'records').exists():shutil.copytree(stage,out/'records',symlinks=True)
        write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),processes=rows));raise
    finally:
        restored=[]
        for original,retained in reversed(moved):
            assert not original.exists() and retained.parent==caller and original.parent==caller;retained.rename(original);assert files(original)==before[str(original)];restored.append(str(original))
        write(out/'CALLER-RESTORED.json',dict(controlled_originals_restored_byte_for_byte=restored))
    write(out/'COMPLETED.json',dict(status='PASS_TWO_FRESH_ORIGINAL_ENGINE_GENERATIONS_WITHOUT_ORIGINAL_CALLER_PATHS',processes=rows,caller_paths_unavailable_throughout_reruns=True,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS_TWO_FRESH_ORIGINAL_ENGINE_GENERATIONS_WITHOUT_ORIGINAL_CALLER_PATHS',flush=True)
if __name__=='__main__':main()
