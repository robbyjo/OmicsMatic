"""Process-only controls for the exact Linux supervisor; no R or scientific result is used."""
from pathlib import Path
import argparse,hashlib,json,os,signal,subprocess,sys,time

FAKE='''#!/usr/bin/python3
from pathlib import Path
import json,os,signal,subprocess,sys,time
root=Path(sys.argv[-1]);plan=json.loads((root/'plan.json').read_bytes());child=None
if plan['case'] in ['timeout','cancel','signal','parent-death']:
 child=subprocess.Popen(['/usr/bin/python3','-c','import signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); time.sleep(600)'])
(root/'worker/FAKE-STARTED.json').write_text(json.dumps(dict(pid=os.getpid(),child=None if child is None else child.pid)))
if plan['case']=='normal':time.sleep(.2)
elif plan['case']=='output-cap':(root/'worker/over-cap.bin').write_bytes(b'x'*200000);time.sleep(600)
elif plan['case']=='address-space':v=bytearray(800*1024*1024);time.sleep(600)
else:time.sleep(600)
'''

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def live(pid):
    try:
        text=Path('/proc',str(pid),'stat').read_text();return text[text.rindex(')')+2:].split()[0]!='Z'
    except FileNotFoundError:return False
def wait_file(path,process,timeout=25):
    until=time.monotonic()+timeout
    while not path.is_file():
        if process.poll() is not None:raise RuntimeError('Owned controller exited before '+path.name)
        if time.monotonic()>until:raise TimeoutError('Owned controller observation exceeded deadline: '+str(path))
        time.sleep(.05)
def parent(root):
    with (root/'worker/supervisor.stdout').open('xb') as stdout,(root/'worker/supervisor.stderr').open('xb') as stderr:
        child=subprocess.Popen([sys.executable,'-I','-B',str(root/'source/supervisor.py'),str(root),str(os.getpid())],stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
        save(root/'PARENT-STARTED.json',dict(parent=os.getpid(),supervisor=child.pid));return child.wait(timeout=45)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path);parser.add_argument('--parent',type=Path);args=parser.parse_args()
    if args.parent:return parent(args.parent)
    assert sys.platform=='linux' and args.out is not None
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False);source=Path(__file__).resolve().parents[4]/'src/main/resources/org/jlinalg/omics/monocle100-neighbors/supervisor.py';source_raw=source.read_bytes();results={}
    for name in ['normal','timeout','cancel','signal','parent-death','supervisor-death','output-cap','address-space']:
        root=out/name;(root/'worker').mkdir(parents=True);(root/'source').mkdir();(root/'source/supervisor.py').write_bytes(source_raw);(root/'source/worker.R').write_text('# process-only synthetic test\n');script=root/'fake-Rscript';script.write_text(FAKE);script.chmod(0o700)
        save(root/'plan.json',dict(case=name));save(root/'worker/runtime.json',dict(rscript=str(script),r_home=str(root),native_library_path='/usr/lib'))
        save(root/'worker/parameters.json',dict(timeout_seconds=1 if name=='timeout' else 30,maximum_address_space_bytes=536870912,maximum_output_bytes=100000 if name=='output-cap' else 10485760,maximum_files=100,maximum_sampled_rss_bytes=268435456))
        controller=subprocess.Popen([sys.executable,'-I','-B',str(Path(__file__).resolve()),'--parent',str(root)],stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        group=None;supervisor=None
        try:
            wait_file(root/'PARENT-STARTED.json',controller);supervisor=json.loads((root/'PARENT-STARTED.json').read_bytes())['supervisor']
            wait_file(root/'worker/FAKE-STARTED.json',controller);group=json.loads((root/'worker/FAKE-STARTED.json').read_bytes())['pid']
            if name=='cancel':(root/'CANCEL_REQUESTED').write_text('Owned synthetic cancellation\n')
            if name=='signal':os.kill(supervisor,signal.SIGTERM)
            if name=='parent-death':os.kill(controller.pid,signal.SIGKILL)
            if name=='supervisor-death':os.kill(supervisor,signal.SIGKILL)
            code=controller.wait(timeout=40)
            if name=='parent-death':
                until=time.monotonic()+15
                while not (root/'worker/PROCESS.json').exists() and live(supervisor) and time.monotonic()<until:time.sleep(.05)
            until=time.monotonic()+5
            while live(group) and time.monotonic()<until:time.sleep(.05)
            assert not live(group),'Owned fake R leader survived cleanup'
            if name=='supervisor-death':assert code!=0;record=dict(status='SUPERVISOR_SIGKILL_R_LEADER_KERNEL_TERMINATION',R_leader_alive=False)
            else:
                record=json.loads((root/'worker/PROCESS.json').read_bytes());assert not record['owned_live_after_cleanup']
                assert record['status']==('PASS_PROCESS_ONLY' if name=='normal' else 'FAIL2_ORIGINAL_PROCESS' if name=='address-space' else 'FAIL2_SUPERVISION'),record
            assert not (root/'COMPLETED.json').exists() and not (root/'analysis.json').exists()
            results[name]=dict(expected=True,controller_exit=code,record=record,no_scientific_analysis_run=True);save(root/'RESULT.json',results[name]);print('PASS process-only',name,flush=True)
        finally:
            if controller.poll() is None:controller.terminate();controller.wait(timeout=10)
            if supervisor is not None and live(supervisor):os.kill(supervisor,signal.SIGTERM)
            if group is not None:
                try:os.killpg(group,signal.SIGKILL)
                except ProcessLookupError:pass
    assert source.read_bytes()==source_raw
    save(out/'RESULT.json',dict(status='PASS8_PROCESS_ONLY_SUPERVISOR_CONTROLS',source_sha256=hashlib.sha256(source_raw).hexdigest(),cases=results,scientific_analyses_run=0,qualification_scope='Exact supervisor with synthetic Python parents/workers, not a fresh scientific R run; kernel death signal applies to the R leader, not an assertion about simultaneous hard-killed detached descendants.'))
    return 0
if __name__=='__main__':raise SystemExit(main())
