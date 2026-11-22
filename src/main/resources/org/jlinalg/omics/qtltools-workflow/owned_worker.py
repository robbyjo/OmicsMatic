"""Own and record one exact QTLtools Linux process group; never install or choose a fallback."""
from pathlib import Path
import argparse,ctypes,hashlib,json,os,platform,re,resource,signal,shutil,subprocess,sys,time

def save(path,value):
    with Path(path).open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def pin(path):
    path=Path(path).resolve(strict=True)
    with path.open('rb') as stream:return dict(path=str(path),bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def proc(pid):
    try:
        text=Path('/proc',str(pid),'stat').read_text();f=text[text.rfind(')')+2:].split();return dict(pid=pid,state=f[0],group=int(f[2]),session=int(f[3]),start=f[19])
    except (FileNotFoundError,ProcessLookupError):return None
def members(group):
    rows=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:row=proc(int(path.name))
        except PermissionError:continue
        if row and row['group']==row['session']==group and row['state']!='Z':rows.append(row)
    return rows
def stop(ledger,token):
    binding=json.loads(Path(ledger).read_bytes())
    if binding['token']!=token or binding['pid']<=1 or binding['group']!=binding['pid'] or binding['session']!=binding['pid']:raise ValueError('Owned QTLtools identity differs')
    leader=proc(binding['pid'])
    if leader and any(leader[key]!=binding[key] for key in ['start','group','session']):raise ValueError('Owned PID identity changed;refusing signal')
    before=members(binding['group'])
    for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,5)]:
        if not members(binding['group']):break
        try:os.killpg(binding['group'],sig)
        except ProcessLookupError:break
        deadline=time.monotonic()+seconds
        while members(binding['group']) and time.monotonic()<deadline:time.sleep(.025)
    after=members(binding['group']);return dict(owned_group=binding['group'],before=before,live_writers_after=after,complete_cleanup=not after)
def unavailable(root,message):
    save(root/'unavailable.json',dict(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED',reason=message,required_action='Supply the exact permitted executable/libraries or a compatible Linux/WSL environment;skip the dependent analysis if installation is prohibited. No edition substitution.'));return 3

def cancelled(root,token):
    path=root/'cancel.request.json'
    if not path.exists():return False
    if json.loads(path.read_bytes())['token']!=token:raise ValueError('Cancellation token differs')
    return True

def stop_all(spec_path,token):
    spec=json.loads(Path(spec_path).read_bytes());root=Path(spec['worker'])
    if spec['token']!=token:raise ValueError('Supervisor cancellation token differs')
    if not cancelled(root,token):save(root/'cancel.request.json',dict(token=token))
    deadline=time.monotonic()+5
    while not (root/'supervisor.owned.json').exists() and time.monotonic()<deadline:time.sleep(.025)
    if not (root/'supervisor.owned.json').exists():return dict(complete_cleanup=False,reason='Supervisor start acknowledgement unavailable;cancel marker forbids future analysis',pending_start_refused_by_cancel_marker=True)
    supervisor=stop(root/'supervisor.owned.json',token)
    original=stop(root/'owned.json',token) if (root/'owned.json').exists() else dict(complete_cleanup=True,original_started=False)
    return dict(complete_cleanup=supervisor['complete_cleanup'] and original['complete_cleanup'],supervisor=supervisor,original=original,pending_start_refused_by_cancel_marker=True)

class LaunchCancelled(Exception):pass

def launch(spec_path):
    spec=json.loads(spec_path.read_bytes());root=Path(spec['worker'])
    if sys.platform!='linux':return execute(spec_path)
    if os.getsid(0)!=os.getpid() or os.getpgrp()!=os.getpid():os.setsid()
    identity=proc(os.getpid());identity.pop('state');identity['token']=spec['token']
    save(root/'supervisor.owned.json',identity)
    def interrupted(signum,frame):raise LaunchCancelled(str(signum))
    for sig in [signal.SIGTERM,signal.SIGINT,signal.SIGHUP]:signal.signal(sig,interrupted)
    try:
        if cancelled(root,spec['token']):return 2
        return execute(spec_path)
    finally:
        for sig in [signal.SIGTERM,signal.SIGINT,signal.SIGHUP]:signal.signal(sig,signal.SIG_IGN)
        save(root/'supervisor.closed.json',dict(token=spec['token'],pid=identity['pid'],start=identity['start'],supervisor_finished=True,cancellation_requested=cancelled(root,spec['token'])))
def execute(spec_path):
    spec=json.loads(spec_path.read_bytes());root=Path(spec['worker']);profile=spec['profile'];executable=Path(spec['command'][0])
    if cancelled(root,spec['token']):raise LaunchCancelled('Cancelled before original runtime inspection')
    if sys.version_info<(3,11) or sys.platform!='linux' or platform.machine().lower() not in {'x86_64','amd64'}:return unavailable(root,'Require Python3.11+ on Linux x86_64,host-native or explicitly selected WSL')
    if shutil.which('ldd') is None:return unavailable(root,'ldd is unavailable;provide the permitted Linux runtime inspection utility')
    if not executable.is_file() or not os.access(executable,os.X_OK):return unavailable(root,'Exact executable is missing or not executable: '+str(executable))
    binary=pin(executable)
    if binary['sha256']!=profile['sha256'] or binary['bytes']!=profile['bytes']:raise ValueError('Qualified executable identity differs')
    env=dict(os.environ)
    for key in list(env):
        if key.startswith('LC_') or key in ['LD_PRELOAD','LD_LIBRARY_PATH','DYLD_INSERT_LIBRARIES','DYLD_LIBRARY_PATH','LANG','LANGUAGE']:env.pop(key,None)
    env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
    libraries=spec['library_directories']
    if any(not Path(path).is_dir() for path in libraries):return unavailable(root,'Declared private library directory is unavailable')
    if libraries:env['LD_LIBRARY_PATH']=':'.join(libraries)
    ldd=subprocess.run(['ldd',str(executable)],env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=20)
    (root/'ldd.stdout').write_bytes(ldd.stdout);(root/'ldd.stderr').write_bytes(ldd.stderr)
    if ldd.returncode!=0 or b'not found' in ldd.stdout:return unavailable(root,'A declared original runtime dependency cannot load;read ldd.stdout/ldd.stderr and supply the exact permitted library')
    loaded=[]
    for line in ldd.stdout.decode().splitlines():
        words=line.split()
        if len(words)>2 and words[1]=='=>' and words[2].startswith('/'):loaded.append(pin(words[2]))
    for sha in profile['critical_library_sha256']:
        if not any(row['sha256']==sha for row in loaded):return unavailable(root,'An exact qualified scientific library is missing;required SHA256 '+sha)
    save(root/'runtime-capture.json',dict(executable=binary,libraries=loaded,edition=profile['edition'],critical_library_sha256=profile['critical_library_sha256'],environment={key:env[key] for key in ['LANG','LC_ALL','OMP_NUM_THREADS','OPENBLAS_NUM_THREADS','MKL_NUM_THREADS','LD_LIBRARY_PATH'] if key in env},owned_child_core_dump_limit_bytes=0,identity_scope='Exact executable and scientific-library hashes;all observed OS libraries retained;not producer authentication or a cross-host PRNG-identity claim'))
    if cancelled(root,spec['token']):raise LaunchCancelled('Cancelled before forking original')
    # Fork/stop handshake records ownership before even an immediately exiting
    # original process can disappear. Only the fresh child creates this session.
    signals=[signal.SIGTERM,signal.SIGINT,signal.SIGHUP]
    blocked=signal.pthread_sigmask(signal.SIG_BLOCK,signals)
    parent_pid=os.getpid();pid=os.fork()
    if pid==0:
        try:
            # Linux parent-death protection covers the pre-ledger handshake.
            libc=ctypes.CDLL(None,use_errno=True)
            if libc.prctl(1,int(signal.SIGKILL),0,0,0)!=0 or os.getppid()!=parent_pid:os._exit(127)
            resource.setrlimit(resource.RLIMIT_CORE,(0,0))
            os.setsid();os.kill(os.getpid(),signal.SIGSTOP)
            for sig in signals:signal.signal(sig,signal.SIG_DFL)
            signal.pthread_sigmask(signal.SIG_SETMASK,blocked)
            os.chdir(spec['working_directory']);os.execve(str(executable),spec['command'],env)
        except BaseException as error:
            os.write(2,('Original executable failed to start: '+repr(error)+'\n').encode())
            try:unavailable(root,'Original executable could not start: '+repr(error))
            finally:os._exit(127)
    ledger=root/'owned.json'
    try:
        _,status=os.waitpid(pid,os.WUNTRACED)
        if not os.WIFSTOPPED(status):raise RuntimeError('Owned original exited before handshake')
        identity=proc(pid)
        if identity is None or identity['group']!=pid or identity['session']!=pid:raise RuntimeError('Owned original process identity unavailable')
        binding=dict(token=spec['token'],pid=pid,group=pid,session=pid,start=identity['start'],command=spec['command'],launcher_pid=parent_pid)
        save(ledger,binding)
    except BaseException:
        # This is the fresh unreaped child,not an arbitrary ledger identity.
        try:os.kill(pid,signal.SIGKILL)
        except ProcessLookupError:pass
        try:os.waitpid(pid,0)
        except ChildProcessError:pass
        signal.pthread_sigmask(signal.SIG_SETMASK,blocked);raise
    class OwnedSignal(Exception):pass
    def interrupted(signum,frame):raise OwnedSignal(str(signum))
    for sig in signals:signal.signal(sig,interrupted)
    code=2;wait_completed=False
    try:
        signal.pthread_sigmask(signal.SIG_SETMASK,blocked)
        if cancelled(root,spec['token']):raise LaunchCancelled('Cancelled before original exec')
        os.kill(pid,signal.SIGCONT);_,status=os.waitpid(pid,0);code=os.waitstatus_to_exitcode(status);wait_completed=True
    finally:
        for sig in signals:signal.signal(sig,signal.SIG_IGN)
        closure=stop(ledger,spec['token']);closure.update(original_wait_completed=wait_completed,original_exit_code=code if wait_completed else None);save(root/'owned.closed.json',closure)
        try:os.waitpid(pid,0)
        except ChildProcessError:pass
        if not closure['complete_cleanup']:raise RuntimeError('Original group has remaining writers')
    for row in [binary,*loaded]:
        if pin(row['path'])!=row:raise ValueError('Original runtime/library changed during execution')
    save(root/'runtime-after.json',dict(executable=binary,libraries=loaded,unchanged=True));return code if code>=0 else 128-code
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['launch','stop']);p.add_argument('--spec',type=Path);p.add_argument('--ledger',type=Path);p.add_argument('--token');args=p.parse_args()
    if args.mode=='stop':
        value=stop_all(args.spec,args.token) if args.spec else stop(args.ledger,args.token);print(json.dumps(value));return 0 if value['complete_cleanup'] else 2
    return launch(args.spec)
if __name__=='__main__':
    try:raise SystemExit(main())
    except Exception as error:print('FAIL2: owned QTLtools worker: '+repr(error),file=sys.stderr);raise SystemExit(2)
