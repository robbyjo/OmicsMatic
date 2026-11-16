#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Qualify complete runtime bytes or owned Linux processes; never admit a user analysis."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,signal,stat,subprocess,sys,tempfile,time,traceback
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
ACTION='Keep original inputs, logs, failed attempts and retained native scratch. Missing or forbidden prerequisites skip dependent analysis. Resolve the named cause only where permitted and retry NEW output; never refresh an old receipt to force a pass. Primitive controls are not workflow, paper or catalog admission.'
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def identity(pid):
    try:
        data=Path('/proc',str(pid),'stat').read_text().rsplit(')',1)[1].split();return dict(pid=pid,start_ticks=int(data[19]),state=data[0])
    except FileNotFoundError:return None
def alive(value):
    now=identity(value['pid']);return now is not None and now['start_ticks']==value['start_ticks'] and now['state'] not in ['Z','X']
def kill_owned(value):
    if alive(value):os.kill(value['pid'],signal.SIGKILL)
def probe(compiled,case,mode,path,shutdown_when=None,timeout=600):
    case.mkdir(parents=True,exist_ok=False);command=['/usr/bin/java','-cp',compiled['classpath'],compiled['probe'],mode,str(path)];start=time.monotonic();timed_out=False
    with (case/'stdout').open('xb') as so,(case/'stderr').open('xb') as se:
        p=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True);original=identity(p.pid)
        try:
            if shutdown_when is not None:
                while not shutdown_when.exists():
                    assert p.poll() is None,'Probe exited before controlled source process started'
                    assert time.monotonic()-start<30,'Controlled source did not start'
                    time.sleep(.01)
                p.terminate()
            try:p.wait(timeout=timeout)
            except subprocess.TimeoutExpired:timed_out=True;p.terminate();p.wait(timeout=15)
        finally:
            if p.poll() is None:p.kill();p.wait(timeout=10)
    result=dict(command=command,java_process=original,exit_code=p.returncode,timed_out=timed_out,seconds=round(time.monotonic()-start,3));write(case/'PROCESS.json',result);return result
def inventory(environment):
    rows=[]
    for path in sorted(environment.rglob('*')):
        info=path.lstat();kind='link' if path.is_symlink() else 'directory' if path.is_dir() else 'file';target=os.readlink(path) if kind=='link' else '-';resolved=str(path.resolve());size=path.stat().st_size if path.is_file() else 0;digest=pin(path)['sha256'] if path.is_file() else '-'
        rows.append(['env',path.relative_to(environment).as_posix(),kind,str(info.st_mode&0o777),str(size),target,resolved,digest])
    return rows
def manifest(path,rows):path.write_text('root\tpath\tkind\tmode\tbytes\ttarget\tresolved\tsha256\n'+''.join('\t'.join(row)+'\n' for row in rows),encoding='utf-8',newline='\n')
def runtime_controls(compiled,stage,runtime):
    original={p.name:pin(p) for p in runtime.iterdir()};positive=probe(compiled,stage/'selected-runtime','runtime',runtime)
    assert positive['exit_code']==0 and not positive['timed_out'],(stage/'selected-runtime/stderr').read_text()
    reported=json.loads((stage/'selected-runtime/stdout').read_bytes());assert reported['entries']==json.loads((runtime/'READY.json').read_bytes())['entries']
    cases=[]
    for mutation in ['complete','bytes-same-size-and-mtime','extra-member','missing-member','mode','link-target','resolved-target','duplicate-row','malformed-size']:
        case=stage/('payload-'+mutation);case.mkdir();env=case/'environment';env.mkdir();(env/'nested').mkdir();(env/'first').write_bytes(b'first\n');(env/'nested/last').write_bytes(b'last!\n');(env/'alias').symlink_to('first');rows=inventory(env)
        if mutation=='bytes-same-size-and-mtime':
            path=env/'nested/last';info=path.stat();path.write_bytes(b'WRONG\n');os.utime(path,ns=(info.st_atime_ns,info.st_mtime_ns))
        elif mutation=='extra-member':(env/'extra').write_bytes(b'not declared\n')
        elif mutation=='missing-member':(env/'nested/last').rename(case/'retained-missing-member')
        elif mutation=='mode':
            path=env/'first';path.chmod(0o700);assert path.stat().st_mode&0o777==0o700
        elif mutation=='link-target':
            (env/'alias').rename(case/'retained-link');(env/'alias').symlink_to('nested/last')
        elif mutation=='resolved-target':rows[0][6]=str(env/'nested/last')
        elif mutation=='duplicate-row':rows.append(rows[0])
        elif mutation=='malformed-size':rows[0][4]='NaN'
        manifest(case/'manifest.tsv',rows);write(case/'REQUEST.json',dict(manifest=str(case/'manifest.tsv'),environment=str(env),entries=len(rows)))
        result=probe(compiled,case/'probe','payload',case/'REQUEST.json',timeout=30);expected=mutation=='complete';assert (result['exit_code']==0)==expected and not result['timed_out'],mutation
        cases.append(dict(case=mutation,accepted=expected,process=result))
    for mutation in ['false-qualification','wrong-version','wrong-architecture','extra-metadata']:
        case=stage/('adoption-'+mutation);shutil.copytree(runtime,case);ready=json.loads((case/'READY.json').read_bytes())
        if mutation=='false-qualification':ready['whole_goal_complete']=True
        elif mutation=='wrong-version':ready['versions']['snakemake']='0.0.0'
        elif mutation=='wrong-architecture':ready['architecture']='aarch64'
        else:(case/'unexpected').write_bytes(b'not an adopted member\n')
        (case/'READY.json').write_text(json.dumps(ready,indent=2)+'\n',encoding='utf-8',newline='\n');result=probe(compiled,stage/('reject-'+mutation),'runtime',case,timeout=30);assert result['exit_code']!=0 and not result['timed_out'];cases.append(dict(case=mutation,accepted=False,process=result))
    assert {p.name:pin(p) for p in runtime.iterdir()}==original
    return dict(status='PASS_COMPLETE_SELECTED_RUNTIME_AND_CORRUPTION_CONTROLS_NOT_ANALYSIS',runtime_process=positive,reported_runtime=reported,synthetic_cases=cases,original_adoption_unchanged=True)
PROGRAM='''from pathlib import Path
import json,os,sys,time
root=Path(sys.argv[1]);mode=sys.argv[2]
def record(name):
    fields=Path('/proc/self/stat').read_text().rsplit(')',1)[1].split()
    (root/name).write_text(json.dumps(dict(pid=os.getpid(),start_ticks=int(fields[19]),cwd=os.getcwd()))+'\\n')
record('parent.json')
if mode=='success':
    (root/'output').write_bytes(b'controlled success\\n')
else:
    child=os.fork()
    if child==0:
        os.chdir('/');record('child.json');time.sleep(120)
    else:
        while not (root/'child.json').exists():time.sleep(.001)
        if mode!='orphan':time.sleep(120)
'''
def process_controls(compiled,stage):
    cases=[]
    for mode in ['success','orphan','timeout','shutdown']:
        root=stage/mode;root.mkdir();working=root/'working';working.mkdir();program=root/'controlled.py';program.write_text(PROGRAM,encoding='utf-8',newline='\n')
        unrelated=None;unrelated_id=None;created=[]
        try:
            if mode=='orphan':
                unrelated=subprocess.Popen(['/usr/bin/python3','-I','-B','-c','import time;time.sleep(120)'],cwd=working,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True);unrelated_id=identity(unrelated.pid);assert unrelated_id and alive(unrelated_id);write(root/'UNRELATED.json',dict(identity=unrelated_id,working_directory=str(working)))
            write(root/'REQUEST.json',dict(command=['/usr/bin/python3','-I','-B',str(program),str(working),mode],environment=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8'),owned=str(root),working=str(working),wall_millis=1000 if mode=='timeout' else 30000))
            result=probe(compiled,root/'probe','process',root/'REQUEST.json',shutdown_when=working/'child.json' if mode=='shutdown' else None,timeout=45)
            assert not result['timed_out'];expected=mode in ['success','orphan'];assert (result['exit_code']==0)==expected,(mode,(root/'probe/stderr').read_text())
            for name in ['parent.json','child.json']:
                if (working/name).exists():created.append(json.loads((working/name).read_bytes()))
            assert created and all(not alive(value) for value in created),(mode,created)
            ledger=root/'process'/('shutdown.json' if mode=='shutdown' else 'process.json');record=json.loads(ledger.read_bytes());assert not record['owned_live_after_cleanup']
            known={row['pid']:row['startTicks'] for row in record['owned_processes']}
            if mode!='success':
                child=json.loads((working/'child.json').read_bytes());assert child['cwd']=='/' and known.get(child['pid'])==child['start_ticks']
            if unrelated is not None:assert unrelated.poll() is None and alive(unrelated_id) and unrelated_id['pid'] not in known,'Unrelated process sharing the cwd must survive owned cleanup'
            if mode=='success':assert (working/'output').read_bytes()==b'controlled success\n'
            if not expected:assert not any(root.rglob('COMPLETED.json'))
            cases.append(dict(case=mode,expected_success=expected,process=result,created_processes=created,owned_source_processes_terminal=True,unrelated_same_cwd_survived=mode=='orphan',ledger=str(ledger)))
        finally:
            for name in ['parent.json','child.json']:
                if (working/name).exists():kill_owned(json.loads((working/name).read_bytes()))
            if unrelated is not None:
                if unrelated_id:kill_owned(unrelated_id)
                unrelated.wait(timeout=10)
    return dict(status='PASS_OWNED_LINUX_PROCESS_SUCCESS_ORPHAN_TIMEOUT_SHUTDOWN_NOT_ANALYSIS',cases=cases,scope='PID/start-time and registered-session cleanup of ordinary local subprocesses; not a malicious-code sandbox, remote-job cancellation or SIGKILL survival guarantee')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--compiled',type=Path,required=True);p.add_argument('--runtime',type=Path);p.add_argument('--part',choices=['runtime','process'],required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);stage=None
    shutil.copyfile(Path(__file__),out/'as-run-qualifier.py')
    try:
        assert sys.platform=='linux','Use permitted Linux Java and native scratch storage'
        compiled=json.loads((a.compiled/'COMPILED.json').read_bytes());assert compiled['status']=='PASS_FROZEN_CURRENT_ADAPTER_COMPILES_NOT_WORKFLOW_QUALIFICATION'
        def stable():
            assert all(pin(a.compiled/'classes'/name)==expected for name,expected in compiled['classes'].items())
            jar=compiled['baseline_jar'];assert pin(Path(jar['path']))=={k:jar[k] for k in ['bytes','sha256']}
        stable();stage=Path(tempfile.mkdtemp(prefix='omicsmatic-snakemake-primitives-',dir='/tmp')).resolve();assert stage.parent==Path('/tmp') and stage.name.startswith('omicsmatic-snakemake-primitives-')
        write(out/'DEPLOYMENT.json',dict(native_stage=str(stage),stage_retained=True,compiled_record=pin(a.compiled/'COMPILED.json'),producer=pin(Path(__file__)),part=a.part))
        (out/'WARNINGS.md').write_text('# Development qualification only\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
        result=runtime_controls(compiled,stage,a.runtime.resolve()) if a.part=='runtime' else process_controls(compiled,stage)
        stable();shutil.copytree(stage,out/'records',symlinks=True);result.update(native_stage=str(stage),new_catalog_credit=0,user_analysis_admitted=False,whole_goal_complete=False);write(out/'COMPLETED.json',result);print(result['status'],flush=True)
    except Exception as error:
        if stage is not None and not (out/'records').exists():shutil.copytree(stage,out/'records',symlinks=True)
        write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),native_stage=str(stage),required_action=ACTION));raise
if __name__=='__main__':main()
