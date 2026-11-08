# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run one original Hail reference in its own Linux process group; retain bounded cleanup receipts."""
import argparse,json,os,signal,subprocess,time
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]

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

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--setup',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--worker',type=Path,required=True);parser.add_argument('--timeout',type=int,default=900);args=parser.parse_args();out=args.out.resolve();worker=args.worker.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and worker.parent==out and 1<=args.timeout<=1800;setup=json.loads((args.setup/'COMPLETED.json').read_bytes());assert setup['status']=='PASS_PRIVATE_ORIGINAL_HAIL139_SPARK353_REFERENCE_ONLY';env=dict(os.environ)
    for key in list(env):
        if key.startswith(('HAIL_','SPARK_','PYSPARK_','PYTHON','PIP_')) or key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','HADOOP_CONF_DIR','YARN_CONF_DIR']:env.pop(key,None)
    for name in ['private-temp','private-config','private-cache']:(out/name).mkdir()
    env.update(LANG='C.UTF-8',LC_ALL='C.UTF-8',JAVA_HOME=setup['java_home'],SPARK_LOCAL_IP='127.0.0.1',TMPDIR=str(out/'private-temp'),XDG_CONFIG_HOME=str(out/'private-config'),XDG_CACHE_HOME=str(out/'private-cache'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1');command=[setup['runtime_python'],'-I','-B','-X','pycache_prefix='+str(out/'private-pycache'),str(worker),str(out),str(out/'cases.json')];record=dict(command=command,status='RUNNING_ORIGINAL_REFERENCE_ONLY',started_at=time.time());child=None;error=None;started=time.monotonic()
    try:
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:
            child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True);record['owned_process_group']=child.pid;save(out/'original.STARTED.json',record)
            while child.poll() is None:
                if time.monotonic()-started>args.timeout:raise TimeoutError('Original reference exceeded its declared wall-clock limit; no result admitted')
                time.sleep(.1)
            record.update(exit_code=child.returncode,status='completed-original-process-only' if child.returncode==0 else 'FAIL2_ORIGINAL_REFERENCE_PROCESS')
    except Exception as failure:error=failure;record.update(status='FAIL2_ORIGINAL_REFERENCE_SUPERVISION',error=repr(failure))
    finally:
        if child is not None:
            record['owned_before_cleanup']=members(child.pid)
            for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                if not members(child.pid):break
                try:os.killpg(child.pid,sig)
                except ProcessLookupError:pass
                deadline=time.monotonic()+seconds
                while any(row['state']!='Z' for row in members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
            child.wait(timeout=10);record['owned_after_cleanup']=members(child.pid);record['owned_live_after_cleanup']=[row for row in record['owned_after_cleanup'] if row['state']!='Z']
            if record['owned_live_after_cleanup']:error=RuntimeError('Owned reference workers remain alive');record.update(status='FAIL2_OWNED_CLEANUP_INCOMPLETE',error=repr(error))
        record['elapsed_seconds']=time.monotonic()-started;save(out/'original.process.json',record)
    return 2 if error is not None or record.get('exit_code')!=0 else 0

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original reference supervision checks')
    raise SystemExit(main())
