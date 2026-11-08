# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bound original Palantir worker in an owned Linux session; preserve logs, process receipts and complete failed batches."""
import argparse,json,os,signal,stat,subprocess,time
from pathlib import Path

def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def members(group):
    result=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            raw=(path/'stat').read_text();fields=raw[raw.rindex(')')+2:].split()
            if int(fields[2])==group:result.append(dict(pid=int(path.name),state=fields[0],process_group=int(fields[2]),session=int(fields[3]),start_ticks=int(fields[19])))
        except (FileNotFoundError,ProcessLookupError,PermissionError):pass
    return result
def footprint(home,maximum_bytes,maximum_files):
    total=0;count=0
    for directory,folders,files in os.walk(home,followlinks=False):
        for name in [*folders,*files]:
            try:
                info=(Path(directory)/name).lstat();count+=1
                if not (stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode) or stat.S_ISLNK(info.st_mode)):raise ValueError('Special output member is outside the supported workflow')
                if not stat.S_ISDIR(info.st_mode):total+=info.st_size
            except FileNotFoundError:continue
            if count>maximum_files or total>maximum_bytes:raise ValueError('Complete sampled output capacity exceeded; no partial batch admitted')
    return dict(bytes=total,files=count)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--worker',required=True,type=Path);p.add_argument('--request',required=True,type=Path);p.add_argument('--timeout',type=int,default=1200);p.add_argument('--maximum-output-bytes',type=int,default=1073741824);p.add_argument('--maximum-files',type=int,default=100000);a=p.parse_args();home=a.out.resolve();assert home==a.out and a.worker.resolve().parent==home and a.request.resolve().parent==home and 1<=a.timeout<=86400 and 1024<=a.maximum_output_bytes<=1099511627776 and 1<=a.maximum_files<=1000000;runtime=json.loads(a.runtime.read_bytes());assert runtime['status']=='PASS_BOUND_ORIGINAL_PALANTIR_WORKFLOW_RUNTIME_ONLY' and runtime['runtime_trees_unchanged'] is True;env=dict(os.environ)
    for key in list(env):
        if key.startswith(('R_','OMICS_CELLPHONEDB_','PYTHON','PIP_','CONDA_')) or key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','LD_LIBRARY_PATH','LD_PRELOAD']:env.pop(key,None)
    for name in ['private-temp','private-config','private-cache','private-home']:(home/name).mkdir()
    env.update(HOME=str(home/'private-home'),NUMBA_CACHE_DIR=str(home/'private-cache/numba'),PYTHONHASHSEED=str(json.loads(a.request.read_bytes())['python_hash_seed']),LANG='C.UTF-8',LC_ALL='C.UTF-8',R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',CONDA_PREFIX=runtime['runtime_environment'],PATH=str(Path(runtime['runtime_environment'])/'bin')+os.pathsep+env.get('PATH',os.defpath),TMPDIR=str(home/'private-temp'),XDG_CONFIG_HOME=str(home/'private-config'),XDG_CACHE_HOME=str(home/'private-cache'),PYTHONDONTWRITEBYTECODE='1',PYTHONPYCACHEPREFIX=str(home/'private-pycache'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1');command=[runtime['runtime_python'],'-B','-P','-s','-X','pycache_prefix='+str(home/'private-pycache'),str(a.worker),str(home),str(a.request)];record=dict(command=command,status='RUNNING_ORIGINAL_WORKFLOW_ONLY',started_at=time.time(),effective_path_prefix=str(Path(runtime['runtime_environment'])/'bin'),supervision_scope='Owned Linux process group; sampled wall/output limits, not hard OS memory/CPU containment. Self-detaching jobs are outside this profile.');child=None;failure=None;started=time.monotonic();cancelled=[]
    def cancel(signum,frame):cancelled.append(signum)
    for sig in [signal.SIGINT,signal.SIGTERM,signal.SIGHUP]:signal.signal(sig,cancel)
    try:
        with (home/'original.stdout').open('xb') as stdout,(home/'original.stderr').open('xb') as stderr:
            child=subprocess.Popen(command,cwd=home,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True);record['owned_process_group']=child.pid;save(home/'original.STARTED.json',record)
            while child.poll() is None:
                if cancelled or (home/'CANCEL_REQUESTED').exists():raise InterruptedError('Owned worker cancellation requested')
                if time.monotonic()-started>a.timeout:raise TimeoutError('Original Palantir workflow exceeded explicit timeout')
                record['latest_sampled_footprint']=footprint(home,a.maximum_output_bytes,a.maximum_files);time.sleep(.1)
            record.update(exit_code=child.returncode,status='completed-original-process-only' if child.returncode==0 else 'FAIL2_ORIGINAL_WORKFLOW_PROCESS');record['final_sampled_footprint']=footprint(home,a.maximum_output_bytes,a.maximum_files)
    except Exception as error:failure=error;record.update(status='FAIL2_ORIGINAL_WORKFLOW_SUPERVISION',error=repr(error))
    finally:
        if child is not None:
            record['owned_before_cleanup']=members(child.pid)
            for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                if not members(child.pid):break
                try:os.killpg(child.pid,sig)
                except ProcessLookupError:pass
                deadline=time.monotonic()+seconds
                while any(v['state']!='Z' for v in members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
            child.wait(timeout=10);record['owned_after_cleanup']=members(child.pid);record['owned_live_after_cleanup']=[v for v in record['owned_after_cleanup'] if v['state']!='Z']
            if record['owned_live_after_cleanup']:failure=RuntimeError('Owned original workers remain alive');record.update(status='FAIL2_OWNED_CLEANUP_INCOMPLETE',error=repr(failure))
        record['elapsed_seconds']=time.monotonic()-started;save(home/'original.process.json',record)
    return 2 if failure is not None or record.get('exit_code')!=0 else 0

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original workflow supervision checks')
    raise SystemExit(main())
