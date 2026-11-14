#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute real refusal/skip controls, retaining every child log and partial output."""
from pathlib import Path
import argparse,json,os,shutil,subprocess,sys,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,CORE,save,pin

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['runtime','request','out']:p.add_argument('--'+name,required=True,type=Path)
 p.add_argument('--manager',type=Path);p.add_argument('--source-archive',type=Path)
 a=p.parse_args();out=a.out.resolve();out.mkdir(exist_ok=False);receipts=[]
 def run(name,program,arguments,expected,*,output=None,no_child=True,analysis=True):
  folder=out/name;folder.mkdir();target=folder/'output' if output is None else output
  command=[sys.executable,'-B',str(program),*map(str,arguments),'--out',str(target)]
  with (folder/'child.stdout').open('xb') as so,(folder/'child.stderr').open('xb') as se:child=subprocess.run(command,cwd=folder,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
  save(folder/'PROCESS.json',dict(command=command,exit_code=child.returncode))
  if child.returncode!=expected:raise AssertionError(name+' wrong exit: '+str(child.returncode))
  if analysis:
   status=json.loads((target/'analysis-status.json').read_bytes())
   if status['result_admitted'] or status['exit_code']!=expected:raise AssertionError(name+' admitted an incomplete result')
   if no_child and (target/'LIVE.json').exists():raise AssertionError(name+' launched an original analysis child')
   if expected==2 and not status['status'].startswith('FAIL2'):raise AssertionError(name+' missing FAIL2')
   if expected==3 and not status['status'].startswith('SKIP3'):raise AssertionError(name+' missing SKIP3')
   warning=(target/'WARNINGS.md').read_text()
   if 'unavailable, never zero' not in warning or 'NEW' not in warning:raise AssertionError(name+' missing prominent diagnostics/recovery')
  else:
   if not (target/'FAILURE.json').is_file():raise AssertionError(name+' missing retained prerequisite failure')
  result=dict(status='PASS_ACTUAL_REFUSAL_OR_SKIP_CONTROL',name=name,expected_exit=expected,analysis_child_forbidden=no_child,result_admitted=False)
  save(folder/'COMPLETED.json',result);receipts.append(result);print('PASS_ACTUAL_PROTOCOL_CONTROL',name,flush=True)
 try:
  args=['--runtime',a.runtime.resolve(),'--request',a.request.resolve()]
  if sys.platform!='linux':
   run('native-platform-analysis-skip',HERE/'run.py',args,3)
   run('installation-forbidden',HERE/'install_runtime.py',['--manager',out/'missing-manager'],3,analysis=False)
   run('native-platform-install-skip',HERE/'install_runtime.py',['--manager',out/'missing-manager','--allow-install','--allow-download'],3,analysis=False)
   run('native-platform-adoption-skip',HERE/'adopt_runtime.py',['--prefix',out/'missing-prefix','--source',out/'missing-source'],3,analysis=False)
  else:
   run('missing-runtime-skip',HERE/'run.py',['--runtime',out/'missing-runtime','--request',a.request.resolve()],3)
   changed=out/'deliberately-changed-runner';changed.mkdir()
   for name in CORE:shutil.copyfile(HERE/name,changed/name)
   with (changed/'Worker.R').open('ab') as stream:stream.write(b'\n# Deliberate refusal control: original analysis must not execute.\n')
   run('changed-runner-refused',changed/'run.py',args,2)
   fake=out/'deliberately-changed-provider';fake.mkdir();ready=json.loads((a.runtime/'READY.json').read_bytes());ready['provider']=str(fake)
   for key in ['r_prefix','library']:shutil.copyfile(a.runtime/(key+'-files.json'),fake/(key+'-files.json'))
   with (fake/'r_prefix-files.json').open('ab') as stream:stream.write(b'\n ')
   save(fake/'READY.json',ready)
   run('changed-inventory-refused',HERE/'run.py',['--runtime',fake,'--request',a.request.resolve()],2)
   run('file-capacity-refused-before-child',HERE/'run.py',args+['--maximum-files','1'],2)
   run('byte-capacity-refused-before-child',HERE/'run.py',args+['--maximum-workspace-bytes','1'],2)
   run('own-wall-limit-fails-closed',HERE/'run.py',args+['--wall-seconds','1'],2,no_child=False)
   run('installation-forbidden',HERE/'install_runtime.py',['--manager',out/'missing-manager'],3,analysis=False)
   if a.manager is not None and a.source_archive is not None:
    run('downloads-forbidden-empty-cache',HERE/'install_runtime.py',['--manager',a.manager.resolve(),'--source-archive',a.source_archive.resolve(),'--allow-install'],3,analysis=False)
  save(out/'COMPLETED.json',dict(status='PASS_ACTUAL_PROTOCOL_CONTROLS',platform=sys.platform,controls=receipts,controls_count=len(receipts),original_inputs_changed=False,new_family_credit=0,whole_goal_complete=False));return 0
 except BaseException as e:save(out/'FAILURE.json',dict(cause=repr(e),traceback=traceback.format_exc()));raise
if __name__=='__main__':raise SystemExit(main())
