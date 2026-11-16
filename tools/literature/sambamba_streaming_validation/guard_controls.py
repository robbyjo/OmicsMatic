#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Linux-only real original-process limit, cancellation and parent-death controls."""
from pathlib import Path
import argparse,hashlib,json,os,signal,subprocess,sys,time

def save(path,value):
    with path.open('xb') as f:f.write((json.dumps(value,indent=2)+'\n').encode())
def properties(path):
    rows={}
    for line in path.read_text().splitlines():
        if line and not line.startswith(('#','!')) and '=' in line:
            key,value=line.split('=',1);rows[key]=value
    return rows
def proc(pid):
    try:
        raw=Path('/proc',str(pid),'stat').read_text();values=raw[raw.rfind(')')+2:].split()
        return dict(state=values[0],parent=int(values[1]),start_ticks=values[19])
    except FileNotFoundError:return None
def running(pid,start):
    value=proc(pid);return value is not None and value['state']!='Z' and value['start_ticks']==start
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--binary',type=Path,required=True)
    p.add_argument('--small',type=Path,required=True);p.add_argument('--stress',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    if sys.platform!='linux':raise FileNotFoundError('Run this process-ownership validator in the permitted Linux/WSL Python environment')
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);common=['/usr/bin/java','-Xmx2g','-cp',str(a.jar.resolve()),'org.jlinalg.cli.SambambaStreamingWorkflowCli']
    defaults=dict(sambamba=str(a.binary.resolve()),edition='0.8.2',action='mark',threads='1',compression='6',hash_table_size='262144',overflow_list_size='200000',sort_buffer_mb='16',io_buffer_mb='1',wall_ms='60000',address_space_mb='1024',work_mb='512',max_files='512',audit_compressed_bytes='1099511627776',audit_decoded_bytes='8796093022208',audit_records='1099511627776',audit_record_bytes='16777216',audit_header_bytes='4194304',audit_dictionary_bytes='4194304')
    checks=[]
    def request(where,source,changes):
        values=dict(defaults,bam=str(source.resolve()));values.update(changes);path=where/'request.properties'
        path.write_text('\n'.join(k+'='+v for k,v in values.items())+'\n');return path
    for name,source,changes,needle in [
      ('real-wall',a.stress,dict(wall_ms='1'),'wall_ms'),
      ('real-address-space',a.stress,dict(address_space_mb='8'),'Original mark exited'),
      ('real-work-entry-limit',a.small,dict(max_files='30'),'max_files')]:
        where=out/name;where.mkdir();req=request(where,source,changes);command=common+['--request',str(req),'--out',str(where/'analysis')]
        with (where/'java.stdout').open('xb') as so,(where/'java.stderr').open('xb') as se:r=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
        failure=(where/'analysis/FAILURE.txt').read_text();assert r.returncode==2 and needle in failure and not (where/'analysis/COMPLETED.properties').exists(),(name,r.returncode,failure)
        checks.append(dict(case=name,exit_code=r.returncode,original_mark_started=(where/'analysis/mark.pid.properties').exists(),failure=failure));print('PASS',name,flush=True)
    for name,kill_signal in [('real-interrupt',signal.SIGTERM),('real-parent-death',signal.SIGKILL)]:
        where=out/name;where.mkdir();req=request(where,a.stress,{})
        unrelated=subprocess.Popen([sys.executable,'-u','-c','import sys;sys.stdin.readline();print("UNRELATED_COMPLETED_NORMALLY",flush=True)'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        so=(where/'java.stdout').open('xb');se=(where/'java.stderr').open('xb')
        parent=subprocess.Popen(common+['--request',str(req),'--out',str(where/'analysis')],stdin=subprocess.DEVNULL,stdout=so,stderr=se)
        child=None;child_start=None;parent_start=proc(parent.pid)['start_ticks'];deadline=time.monotonic()+180
        try:
            pidfile=where/'analysis/mark.pid.properties'
            while time.monotonic()<deadline:
                if parent.poll() is not None:raise RuntimeError('Parent completed before the actual native lifecycle control: '+str(parent.returncode))
                if pidfile.exists():
                    try:
                        data=properties(pidfile);candidate=int(data['pid']);state=proc(candidate)
                        if state and state['parent']==parent.pid and Path('/proc',str(candidate),'exe').resolve()==a.binary.resolve():
                            child=candidate;child_start=state['start_ticks'];break
                    except (OSError,KeyError,ValueError):pass
                time.sleep(.002)
            if child is None:raise RuntimeError('Original Sambamba process was not observed alive')
            assert proc(parent.pid)['start_ticks']==parent_start
            os.kill(parent.pid,kill_signal);parent.wait(timeout=15)
            deadline=time.monotonic()+5
            while running(child,child_start) and time.monotonic()<deadline:time.sleep(.005)
            alive=running(child,child_start);unrelated_alive=unrelated.poll() is None
            assert unrelated_alive,'Unrelated owned control process was interrupted'
            unrelated.stdin.write(b'finish\n');unrelated.stdin.flush();unrelated.stdin.close();unrelated.wait(timeout=10)
            unrelated_output=unrelated.stdout.read();assert unrelated.returncode==0 and b'UNRELATED_COMPLETED_NORMALLY' in unrelated_output
            assert not alive,'Original child kept running after cancellation/parent death'
            assert not (where/'analysis/COMPLETED.properties').exists()
            checks.append(dict(case=name,signal=int(kill_signal),parent_pid=parent.pid,child_pid=child,child_start_ticks=child_start,
                parent_exit_code=parent.returncode,child_running_after=False,child_final_proc=proc(child),unrelated_survived=True,unrelated_exit_code=unrelated.returncode))
            print('PASS',name,'unrelated survived',flush=True)
        finally:
            if parent.poll() is None:parent.kill();parent.wait(timeout=10)
            if child is not None and running(child,child_start):os.kill(child,signal.SIGKILL)
            if unrelated.poll() is None:
                try:unrelated.stdin.write(b'finish\n');unrelated.stdin.flush();unrelated.stdin.close()
                except BrokenPipeError:pass
                unrelated.wait(timeout=10)
            so.close();se.close()
    save(out/'COMPLETED.json',dict(status='PASS_REAL_WALL_ADDRESS_WORK_INTERRUPT_AND_PARENT_DEATH_CONTROLS',checks=checks,all_original_children_stopped=True,unrelated_jobs_completed_normally=True,new_catalog_credit=0))
if __name__=='__main__':
    try:main()
    except Exception as e:print('FAIL2_PROCESS_GUARDS_UNQUALIFIED',repr(e),flush=True);raise
