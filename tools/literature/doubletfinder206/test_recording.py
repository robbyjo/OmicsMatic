#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Fault real original runs at terminal recording, and test missing child status."""
from pathlib import Path
import argparse,json,os,signal,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,save,pin

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['runtime','request','out']:p.add_argument('--'+n,required=True,type=Path)
 a=p.parse_args();out=a.out.resolve();out.mkdir(exist_ok=False);receipts=[]
 if sys.platform!='linux':print('SKIP3_RECORDING_CONTROLS_REQUIRE_LINUX',flush=True);return 3
 cases=[('status-destination-directory',['analysis-status.json']),('warnings-destination-directory',['WARNINGS.md']),('manifest-destination-directory',['MANIFEST.json']),('status-and-fallback-directories',['analysis-status.json','FINALIZATION_ERROR.json']),('all-terminal-destinations-directories',['analysis-status.json','FINALIZATION_ERROR.json','WARNINGS.md','MANIFEST.json']),('own-child-exits-without-R-status',[])]
 try:
  for name,blocked in cases:
   folder=out/name;folder.mkdir();analysis=folder/'analysis';command=[sys.executable,'-B',str(HERE/'run.py'),'--runtime',str(a.runtime.resolve()),'--request',str(a.request.resolve()),'--out',str(analysis)]
   with (folder/'driver.stdout').open('xb') as so,(folder/'driver.stderr').open('xb') as se:
    child=subprocess.Popen(command,cwd=folder,stdin=subprocess.DEVNULL,stdout=so,stderr=se);start=time.monotonic()
    while not analysis.is_dir():
     if child.poll() is not None or time.monotonic()-start>300:raise AssertionError('Own output was not created')
     time.sleep(.05)
    for relative in blocked:(analysis/relative).mkdir(exist_ok=False)
    if name=='own-child-exits-without-R-status':
     while not (analysis/'LIVE.json').is_file():
      if child.poll() is not None or time.monotonic()-start>300:raise AssertionError('Own original child did not start')
      time.sleep(.05)
     live=json.loads((analysis/'LIVE.json').read_bytes());pid=live['pid'];proc=Path('/proc')/str(pid)
     cmd=(proc/'cmdline').read_bytes().replace(b'\0',b' ')
     if str(analysis/'control.json').encode() not in cmd or os.getpgid(pid)!=pid:raise AssertionError('Refuse to signal any process except this exact owned original group')
     if (analysis/'original/R-status.json').exists():raise AssertionError('Original run already has a terminal status; no signal sent')
     save(folder/'OWN-SIGNAL.json',dict(pid=pid,pgid=os.getpgid(pid),command=cmd.decode(),signal='SIGTERM',scope='Only this owned test original process group',files_deleted=False));os.killpg(pid,signal.SIGTERM)
    while True:
     try:code=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_OWNED_TERMINAL_RECORD_CONTROL_RUNNING',name,child.pid,flush=True)
   save(folder/'PROCESS.json',dict(command=command,exit_code=code));assert code==2,(name,code)
   terminal=[line for line in (folder/'driver.stdout').read_text().splitlines() if line.startswith(('FAIL2_','SKIP3_','PASS_'))];assert terminal and terminal[-1].startswith('FAIL2_'),name
   for record_name in ['analysis-status.json','FINALIZATION_ERROR.json']:
    path=analysis/record_name
    if path.is_file():
     record=json.loads(path.read_bytes());assert record['exit_code']==2 and not record['result_admitted'],(name,record_name)
   warning=analysis/'WARNINGS.md'
   if warning.is_file():assert warning.read_text().startswith('# FAIL2_'),name
   if name!='own-child-exits-without-R-status':assert (analysis/'original/complete-result.rds').is_file(),'The actual original calculation must precede this recording fault'
   else:assert not (analysis/'original/R-status.json').exists()
   manifest=analysis/'MANIFEST.json'
   if manifest.is_file():
    for relative,wanted in json.loads(manifest.read_bytes())['files'].items():
     if wanted['kind']=='file':assert pin(analysis/relative)=={k:wanted[k] for k in ['bytes','sha256']},(name,relative)
   receipt=dict(status='PASS_ACTUAL_ORIGINAL_RUN_RECORDING_FAULT_FAILS_CLOSED',name=name,exit_code=code,complete_original_result_retained=name!='own-child-exits-without-R-status',terminal_PASS_emitted=False,old_outputs_deleted=False)
   save(folder/'COMPLETED.json',receipt);receipts.append(receipt);print(receipt['status'],name,flush=True)
  save(out/'COMPLETED.json',dict(status='PASS_ALL_SIX_ACTUAL_RECORDING_AND_OWN_CHILD_STATUS_CONTROLS',controls=receipts,count=len(receipts),whole_tool_qualified=False,new_family_credit=0,whole_goal_complete=False));return 0
 except BaseException as e:save(out/'FAILURE.json',dict(cause=repr(e),traceback=traceback.format_exc()));raise
if __name__=='__main__':raise SystemExit(main())
