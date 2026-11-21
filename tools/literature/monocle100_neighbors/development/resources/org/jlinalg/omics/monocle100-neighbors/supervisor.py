# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Own the Linux R process group and retain complete time/resource/cleanup receipts."""
from pathlib import Path
import json,os,resource,signal,stat,subprocess,sys,time

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def members(group):
    result=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            value=(path/'stat').read_text();fields=value[value.rindex(')')+2:].split()
            if int(fields[2])==group:result.append(dict(pid=int(path.name),state=fields[0],start_ticks=int(fields[19])))
        except (FileNotFoundError,PermissionError,ProcessLookupError):pass
    return result
def footprint(root):
    result={}
    for directory,dirs,files in os.walk(root,followlinks=False):
        for name in [*dirs,*files]:
            path=Path(directory)/name
            try:info=path.lstat()
            except FileNotFoundError:continue
            if not (stat.S_ISREG(info.st_mode) or stat.S_ISDIR(info.st_mode)):raise ValueError('Special/symlink output is outside the complete-capture profile')
            if stat.S_ISREG(info.st_mode):result[path.relative_to(root).as_posix()]=info.st_size
    return result

def main():
    if sys.platform!='linux' or sys.version_info<(3,10):raise RuntimeError('Use Linux Python3.10+ for owned R execution')
    root=Path(sys.argv[1]).resolve();out=root/'worker';runtime=json.loads((out/'runtime.json').read_bytes());parameters=json.loads((out/'parameters.json').read_bytes());cancelled=[]
    for sig in [signal.SIGINT,signal.SIGTERM,signal.SIGHUP]:signal.signal(sig,lambda signum,frame:cancelled.append(signum))
    for name in ['home','temporary','cache']:(out/name).mkdir()
    env=dict(PATH=str(Path(runtime['rscript']).parent)+':/usr/bin:/bin',R_HOME=runtime['r_home'],LD_LIBRARY_PATH=runtime['native_library_path'],LANG='C.UTF-8',LC_ALL='C.UTF-8',R_LIBS_USER='',R_LIBS_SITE='',R_ENVIRON='/dev/null',R_ENVIRON_USER='/dev/null',R_PROFILE='/dev/null',R_PROFILE_USER='/dev/null',HOME=str(out/'home'),TMPDIR=str(out/'temporary'),R_USER_CACHE_DIR=str(out/'cache'),XDG_CACHE_HOME=str(out/'cache'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1')
    command=[runtime['rscript'],'--vanilla',str(root/'source/worker.R'),str(root)];baseline=footprint(root);started=time.monotonic();child=None;record=dict(command=command,status='RUNNING',timeout_seconds=parameters['timeout_seconds'],maximum_address_space_bytes=parameters['maximum_address_space_bytes'],scope='Owned Linux R session/process group. Hard per-process address-space limit; sampled aggregate RSS/output/wall checks are not hard aggregate memory or disk quotas. Self-detaching processes are outside this profile.');failure=None;maximum_rss=0
    def limits():resource.setrlimit(resource.RLIMIT_AS,(parameters['maximum_address_space_bytes'],parameters['maximum_address_space_bytes']))
    try:
        with (out/'stdout.log').open('xb') as stdout,(out/'stderr.log').open('xb') as stderr:
            child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True,preexec_fn=limits)
            record['owned_process_group']=child.pid;save(out/'PROCESS-STARTED.json',record)
            while True:
                if cancelled or (root/'CANCEL_REQUESTED').exists():raise InterruptedError('Owned worker cancellation requested')
                if time.monotonic()-started>parameters['timeout_seconds']:raise TimeoutError('Original neighbor construction exceeded explicit timeout')
                current=footprint(root);extra=sum(max(0,size-baseline.get(name,0)) for name,size in current.items())
                if extra>parameters['maximum_output_bytes'] or len(current)>parameters['maximum_files']:raise RuntimeError('Complete output capacity exceeded; no partial graph admitted')
                rss=0
                for member in members(child.pid):
                    try:
                        for line in Path('/proc',str(member['pid']),'status').read_text().splitlines():
                            if line.startswith('VmRSS:'):rss+=int(line.split()[1])*1024
                    except (FileNotFoundError,PermissionError,ProcessLookupError):pass
                maximum_rss=max(maximum_rss,rss)
                if rss>parameters['maximum_sampled_rss_bytes']:raise MemoryError('Sampled aggregate RSS exceeds the declared limit')
                if child.poll() is not None:break
                time.sleep(.1)
            record['exit_code']=child.returncode;record['status']='PASS_PROCESS_ONLY' if child.returncode==0 else 'SKIP3_PROCESS_RUNTIME' if child.returncode==3 else 'FAIL2_ORIGINAL_PROCESS'
    except Exception as error:
        failure=error;record.update(status='SKIP3_EXECUTION_UNAVAILABLE' if isinstance(error,(FileNotFoundError,PermissionError)) else 'FAIL2_SUPERVISION',error=repr(error))
    finally:
        if child is not None:
            record['owned_before_cleanup']=members(child.pid)
            for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                if not members(child.pid):break
                try:os.killpg(child.pid,sig)
                except ProcessLookupError:pass
                until=time.monotonic()+seconds
                while any(member['state']!='Z' for member in members(child.pid)) and time.monotonic()<until:time.sleep(.05)
            child.wait(timeout=10);record['owned_after_cleanup']=members(child.pid);record['owned_live_after_cleanup']=[member for member in record['owned_after_cleanup'] if member['state']!='Z']
            if record['owned_live_after_cleanup']:failure=RuntimeError('Owned original worker survived cleanup');record.update(status='FAIL2_CLEANUP_INCOMPLETE',error=repr(failure))
        record['elapsed_seconds']=time.monotonic()-started;record['maximum_sampled_rss_bytes']=maximum_rss;save(out/'PROCESS.json',record)
    if record['status'].startswith('SKIP3'):return 3
    return 0 if failure is None and record.get('exit_code')==0 else 2

if __name__=='__main__':raise SystemExit(main())
