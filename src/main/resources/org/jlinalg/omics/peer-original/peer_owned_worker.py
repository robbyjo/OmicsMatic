"""Own one POSIX process group for the explicitly requested WSL PEER worker."""
from pathlib import Path
import argparse,json,os,signal,subprocess,sys,time

def save(path,value):
    with Path(path).open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')

def proc(pid):
    try:
        # comm can contain spaces and parentheses. Remaining fields begin at state.
        raw=Path('/proc',str(pid),'stat').read_text();fields=raw[raw.rfind(')')+2:].split()
        return dict(pid=pid,state=fields[0],group=int(fields[2]),session=int(fields[3]),start=fields[19])
    except (FileNotFoundError,ProcessLookupError):return None

def members(group):
    result=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:row=proc(int(path.name))
        except PermissionError:continue
        if row and row['group']==group and row['session']==group and row['state']!='Z':result.append(row)
    return result

def stop(ledger,token):
    binding=json.loads(Path(ledger).read_bytes())
    if binding['token']!=token or binding['pid']<=1 or binding['group']!=binding['pid']:raise ValueError('Owned process identity differs')
    leader=proc(binding['pid'])
    if leader and (leader['start']!=binding['start'] or leader['session']!=binding['group'] or leader['group']!=binding['group']):raise ValueError('Original owned PID was replaced; refusing to signal')
    before=members(binding['group'])
    for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,5)]:
        if not members(binding['group']):break
        try:os.killpg(binding['group'],sig)
        except ProcessLookupError:break
        deadline=time.monotonic()+seconds
        while members(binding['group']) and time.monotonic()<deadline:time.sleep(.025)
    after=members(binding['group']);return dict(owned_group=binding['group'],before=before,live_writers_after=after,complete_cleanup=not after)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('mode',choices=['launch','stop']);parser.add_argument('--ledger',type=Path,required=True);parser.add_argument('--token',required=True);parser.add_argument('--command',nargs=argparse.REMAINDER,default=[]);args=parser.parse_args()
    if args.mode=='stop':
        result=stop(args.ledger,args.token);print(json.dumps(result));return 0 if result['complete_cleanup'] else 2
    command=args.command[1:] if args.command[:1]==['--'] else args.command
    if not command:raise ValueError('Missing exact owned worker command')
    try:process=subprocess.Popen(command,stdin=subprocess.DEVNULL,start_new_session=True)
    except (FileNotFoundError,PermissionError) as error:
        save(str(args.ledger)+'.unavailable.json',dict(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED',error=repr(error)));return 3
    identity=proc(process.pid)
    if identity is None:process.wait();raise RuntimeError('Owned process ended before its identity could be recorded')
    binding=dict(token=args.token,pid=process.pid,group=process.pid,start=identity['start'],command=command,launcher_pid=os.getpid())
    try:save(args.ledger,binding)
    except BaseException:
        try:os.killpg(process.pid,signal.SIGKILL)
        except ProcessLookupError:pass
        process.wait(timeout=10);raise
    class OwnedSignal(Exception):pass
    def interrupted(signum,frame):raise OwnedSignal(str(signum))
    for sig in [signal.SIGTERM,signal.SIGINT,signal.SIGHUP]:signal.signal(sig,interrupted)
    try:return process.wait()
    finally:
        for sig in [signal.SIGTERM,signal.SIGINT,signal.SIGHUP]:signal.signal(sig,signal.SIG_IGN)
        result=stop(args.ledger,args.token);process.wait(timeout=10)
        save(str(args.ledger)+'.closed.json',result)
        if not result['complete_cleanup']:raise RuntimeError('Owned group still has live writers')

if __name__=='__main__':
    try:raise SystemExit(main())
    except Exception as error:print('FAIL2: owned PEER transport failed: '+repr(error),file=sys.stderr);raise SystemExit(2)
