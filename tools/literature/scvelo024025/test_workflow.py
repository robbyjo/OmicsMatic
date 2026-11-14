#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise full original-worker policies, literal identities, errors, malformed reports and owned limits."""
from pathlib import Path
import argparse,concurrent.futures,copy,hashlib,json,os,shutil,subprocess,sys,threading,time,traceback
HERE=Path(__file__).resolve().parent
from make_example import request

def save(p,v):p.write_bytes((json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode('utf8'))
def load(p):return json.loads(p.read_bytes())
def pin(p):
 b=p.read_bytes();return dict(bytes=len(b),sha256=hashlib.sha256(b).hexdigest())

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--provider',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--workers',type=int,choices=[1,2,3,4],default=4);p.add_argument('--other-provider',type=Path);a=p.parse_args();out=a.out.resolve();provider=a.provider.resolve()
 for protected in [provider,HERE]:
  if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected original provider/source')
 out.mkdir(parents=True,exist_ok=False);inputs=out/'control-inputs';inputs.mkdir();literal=inputs/'literal.txt';literal.write_bytes('α\tNA\tfalse\t\n'.encode('utf8'));before=pin(literal);provider_before=pin(provider/'READY.json');cases=[];case_lock=threading.Lock()
 def normal(name,script="(result_directory/'result.txt').write_text('complete α NA',encoding='utf8');analysis_complete='COMPLETE'\n"):
  source=inputs/(name+'.py');source.write_bytes(script.encode('utf8'));value=request(provider,source,{'literal.txt':literal},{'result.txt':1},out/name);value.update(required_symbols=[],required_distributions={},expected_return='COMPLETE',analysis_population='One complete explicit supervision control; no author data or scientific completion inferred.');return value
 def execute(name,value,expected,analysis=None,process=None,blocked_warnings=False,existing=False):
  target=Path(value['out']);old=pin(target/'analysis-status.json') if existing else None;path=out/(name+'.request.json');save(path,value);cmd=[sys.executable,str(HERE/'run.py'),'--request',str(path)];start=time.time();r=subprocess.run(cmd,capture_output=True,timeout=300);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
  assert r.returncode==expected,(name,r.returncode,r.stdout[-4000:],r.stderr[-3000:])
  state=load(target/'analysis-status.json') if (target/'analysis-status.json').is_file() else None
  if existing:assert pin(target/'analysis-status.json')==old
  elif state is not None:
   assert state['exit_code']==expected and state['result_admitted']==(expected==0),(name,state)
   if blocked_warnings:assert 'could not write WARNINGS.md' in r.stdout.decode('utf8')
   else:assert 'SKIP3' in (target/'WARNINGS.md').read_text() and 'FAIL2' in (target/'WARNINGS.md').read_text()
  if analysis is not None:assert (target/'diagnostics/analysis-started.json').is_file()==analysis,name
  if process is not None:assert (target/'process-live.json').is_file()==process,name
  row=dict(id=name,exit_code=expected,analysis_started=(target/'diagnostics/analysis-started.json').is_file(),worker_started=(target/'process-live.json').is_file(),result_admitted=expected==0,command=cmd,elapsed_seconds=time.time()-start);
  with case_lock:cases.append(row);save(out/'CASES.json',cases)
  print(name,'expected exit',expected,flush=True);return target,state
 if sys.platform!='linux':execute('unsupported-platform',normal('unsupported-platform'),3,False,False)
 else:
  def case_0():
   name,key=([('execution-forbidden','execution_policy'),('trusted-code-forbidden','trusted_code_policy')])[0]
   value=normal(name);value[key]='FORBID';execute(name,value,3,False,False)
  def case_1():
   name,key=([('execution-forbidden','execution_policy'),('trusted-code-forbidden','trusted_code_policy')])[1]
   value=normal(name);value[key]='FORBID';execute(name,value,3,False,False)
  def case_2():
   name,key,value=([('missing-provider','provider_directory',str(out/'absent-provider')),('missing-script','script',str(out/'absent-script.py'))])[0]
   v=normal(name);v[key]=value;execute(name,v,3,False,False)
  def case_3():
   name,key,value=([('missing-provider','provider_directory',str(out/'absent-provider')),('missing-script','script',str(out/'absent-script.py'))])[1]
   v=normal(name);v[key]=value;execute(name,v,3,False,False)
  def case_4():
   v=normal('missing-input')
   v['inputs']={'literal.txt':str(out/'absent-input.txt')}
   execute('missing-input',v,3,False,False)
  def case_5():
   v=normal('literal-missing-symbol')
   v['required_symbols']=['pp.OmicsMaticLiteralMissingFunction']
   execute('literal-missing-symbol',v,3,False,True)
  def case_6():
   name,packages=([('missing-required-distribution',{'OmicsMaticLiteralMissingPackage':'0.0.0'}),('wrong-required-version',{'scvelo':'0.0.0'})])[0]
   v=normal(name);v['required_distributions']=packages;execute(name,v,3,False,True)
  def case_7():
   name,packages=([('missing-required-distribution',{'OmicsMaticLiteralMissingPackage':'0.0.0'}),('wrong-required-version',{'scvelo':'0.0.0'})])[1]
   v=normal(name);v['required_distributions']=packages;execute(name,v,3,False,True)
  def case_8():
   target,state=execute('warnings-visible',normal('warnings-visible',"import warnings,logging\nwarnings.simplefilter('always',UserWarning);warnings.warn('original warning line1\\noriginal warning line2',UserWarning);logging.warning('original log α');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),0,True,True)
   conditions=load(target/'diagnostics/conditions.json')
   assert any(c['kind']=='warning' and c['message']=='original warning line1\noriginal warning line2' for c in conditions) and any(c['kind']=='log' and c['message']=='original log α' for c in conditions)
   assert state['warning_review_required'] and 'original log α' in (target/'process.stderr').read_text()
  def case_9():
   target,state=execute('warning-as-error',normal('warning-as-error',"import warnings\nwarnings.simplefilter('error');warnings.warn('original promoted warning',UserWarning)\n"),2,True,True)
   assert state['original_errors']==1 and 'original promoted warning' in (target/'process.stderr').read_text()
  def case_10():
   name,script=([
      ('original-package-error',"original_scvelo.tl.velocity(None)\n"),
      ('explicit-original-error',"(result_directory/'result.txt').write_text('partial exact α');raise RuntimeError('original explicit failure α')\n"),
      ('zero-exit-early-quit',"import os\n(result_directory/'result.txt').write_text('partial exact α');os._exit(0)\n"),
      ('missing-marker',"(result_directory/'result.txt').write_text('complete')\n"),
      ('wrong-marker',"(result_directory/'result.txt').write_text('complete');analysis_complete='WRONG'\n"),
      ('missing-result',"analysis_complete='COMPLETE'\n"),
      ('extra-result',"(result_directory/'result.txt').write_text('complete');(result_directory/'extra.txt').write_text('extra');analysis_complete='COMPLETE'\n"),
      ('changed-captured-input',"(input_directory/'literal.txt').write_text('changed');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('extra-captured-input',"(input_directory/'extra.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('unexpected-root-output',"(result_directory.parent/'undeclared-result.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('workspace-symlink',"(result_directory/'result.txt').symlink_to(input_directory/'literal.txt');analysis_complete='COMPLETE'\n")])[0]
   target,state=execute(name,normal(name,script),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and not state['condition_counts_complete'] and state['original_warnings'] is None
  def case_11():
   name,script=([
      ('original-package-error',"original_scvelo.tl.velocity(None)\n"),
      ('explicit-original-error',"(result_directory/'result.txt').write_text('partial exact α');raise RuntimeError('original explicit failure α')\n"),
      ('zero-exit-early-quit',"import os\n(result_directory/'result.txt').write_text('partial exact α');os._exit(0)\n"),
      ('missing-marker',"(result_directory/'result.txt').write_text('complete')\n"),
      ('wrong-marker',"(result_directory/'result.txt').write_text('complete');analysis_complete='WRONG'\n"),
      ('missing-result',"analysis_complete='COMPLETE'\n"),
      ('extra-result',"(result_directory/'result.txt').write_text('complete');(result_directory/'extra.txt').write_text('extra');analysis_complete='COMPLETE'\n"),
      ('changed-captured-input',"(input_directory/'literal.txt').write_text('changed');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('extra-captured-input',"(input_directory/'extra.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('unexpected-root-output',"(result_directory.parent/'undeclared-result.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('workspace-symlink',"(result_directory/'result.txt').symlink_to(input_directory/'literal.txt');analysis_complete='COMPLETE'\n")])[1]
   target,state=execute(name,normal(name,script),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and not state['condition_counts_complete'] and state['original_warnings'] is None
  def case_12():
   name,script=([
      ('original-package-error',"original_scvelo.tl.velocity(None)\n"),
      ('explicit-original-error',"(result_directory/'result.txt').write_text('partial exact α');raise RuntimeError('original explicit failure α')\n"),
      ('zero-exit-early-quit',"import os\n(result_directory/'result.txt').write_text('partial exact α');os._exit(0)\n"),
      ('missing-marker',"(result_directory/'result.txt').write_text('complete')\n"),
      ('wrong-marker',"(result_directory/'result.txt').write_text('complete');analysis_complete='WRONG'\n"),
      ('missing-result',"analysis_complete='COMPLETE'\n"),
      ('extra-result',"(result_directory/'result.txt').write_text('complete');(result_directory/'extra.txt').write_text('extra');analysis_complete='COMPLETE'\n"),
      ('changed-captured-input',"(input_directory/'literal.txt').write_text('changed');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('extra-captured-input',"(input_directory/'extra.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('unexpected-root-output',"(result_directory.parent/'undeclared-result.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('workspace-symlink',"(result_directory/'result.txt').symlink_to(input_directory/'literal.txt');analysis_complete='COMPLETE'\n")])[2]
   target,state=execute(name,normal(name,script),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and not state['condition_counts_complete'] and state['original_warnings'] is None
  def case_13():
   name,script=([
      ('original-package-error',"original_scvelo.tl.velocity(None)\n"),
      ('explicit-original-error',"(result_directory/'result.txt').write_text('partial exact α');raise RuntimeError('original explicit failure α')\n"),
      ('zero-exit-early-quit',"import os\n(result_directory/'result.txt').write_text('partial exact α');os._exit(0)\n"),
      ('missing-marker',"(result_directory/'result.txt').write_text('complete')\n"),
      ('wrong-marker',"(result_directory/'result.txt').write_text('complete');analysis_complete='WRONG'\n"),
      ('missing-result',"analysis_complete='COMPLETE'\n"),
      ('extra-result',"(result_directory/'result.txt').write_text('complete');(result_directory/'extra.txt').write_text('extra');analysis_complete='COMPLETE'\n"),
      ('changed-captured-input',"(input_directory/'literal.txt').write_text('changed');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('extra-captured-input',"(input_directory/'extra.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('unexpected-root-output',"(result_directory.parent/'undeclared-result.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('workspace-symlink',"(result_directory/'result.txt').symlink_to(input_directory/'literal.txt');analysis_complete='COMPLETE'\n")])[3]
   target,state=execute(name,normal(name,script),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and not state['condition_counts_complete'] and state['original_warnings'] is None
  def case_14():
   name,script=([
      ('original-package-error',"original_scvelo.tl.velocity(None)\n"),
      ('explicit-original-error',"(result_directory/'result.txt').write_text('partial exact α');raise RuntimeError('original explicit failure α')\n"),
      ('zero-exit-early-quit',"import os\n(result_directory/'result.txt').write_text('partial exact α');os._exit(0)\n"),
      ('missing-marker',"(result_directory/'result.txt').write_text('complete')\n"),
      ('wrong-marker',"(result_directory/'result.txt').write_text('complete');analysis_complete='WRONG'\n"),
      ('missing-result',"analysis_complete='COMPLETE'\n"),
      ('extra-result',"(result_directory/'result.txt').write_text('complete');(result_directory/'extra.txt').write_text('extra');analysis_complete='COMPLETE'\n"),
      ('changed-captured-input',"(input_directory/'literal.txt').write_text('changed');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('extra-captured-input',"(input_directory/'extra.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('unexpected-root-output',"(result_directory.parent/'undeclared-result.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('workspace-symlink',"(result_directory/'result.txt').symlink_to(input_directory/'literal.txt');analysis_complete='COMPLETE'\n")])[4]
   target,state=execute(name,normal(name,script),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and not state['condition_counts_complete'] and state['original_warnings'] is None
  def case_15():
   name,script=([
      ('original-package-error',"original_scvelo.tl.velocity(None)\n"),
      ('explicit-original-error',"(result_directory/'result.txt').write_text('partial exact α');raise RuntimeError('original explicit failure α')\n"),
      ('zero-exit-early-quit',"import os\n(result_directory/'result.txt').write_text('partial exact α');os._exit(0)\n"),
      ('missing-marker',"(result_directory/'result.txt').write_text('complete')\n"),
      ('wrong-marker',"(result_directory/'result.txt').write_text('complete');analysis_complete='WRONG'\n"),
      ('missing-result',"analysis_complete='COMPLETE'\n"),
      ('extra-result',"(result_directory/'result.txt').write_text('complete');(result_directory/'extra.txt').write_text('extra');analysis_complete='COMPLETE'\n"),
      ('changed-captured-input',"(input_directory/'literal.txt').write_text('changed');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('extra-captured-input',"(input_directory/'extra.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('unexpected-root-output',"(result_directory.parent/'undeclared-result.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('workspace-symlink',"(result_directory/'result.txt').symlink_to(input_directory/'literal.txt');analysis_complete='COMPLETE'\n")])[5]
   target,state=execute(name,normal(name,script),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and not state['condition_counts_complete'] and state['original_warnings'] is None
  def case_16():
   name,script=([
      ('original-package-error',"original_scvelo.tl.velocity(None)\n"),
      ('explicit-original-error',"(result_directory/'result.txt').write_text('partial exact α');raise RuntimeError('original explicit failure α')\n"),
      ('zero-exit-early-quit',"import os\n(result_directory/'result.txt').write_text('partial exact α');os._exit(0)\n"),
      ('missing-marker',"(result_directory/'result.txt').write_text('complete')\n"),
      ('wrong-marker',"(result_directory/'result.txt').write_text('complete');analysis_complete='WRONG'\n"),
      ('missing-result',"analysis_complete='COMPLETE'\n"),
      ('extra-result',"(result_directory/'result.txt').write_text('complete');(result_directory/'extra.txt').write_text('extra');analysis_complete='COMPLETE'\n"),
      ('changed-captured-input',"(input_directory/'literal.txt').write_text('changed');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('extra-captured-input',"(input_directory/'extra.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('unexpected-root-output',"(result_directory.parent/'undeclared-result.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('workspace-symlink',"(result_directory/'result.txt').symlink_to(input_directory/'literal.txt');analysis_complete='COMPLETE'\n")])[6]
   target,state=execute(name,normal(name,script),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and not state['condition_counts_complete'] and state['original_warnings'] is None
  def case_17():
   name,script=([
      ('original-package-error',"original_scvelo.tl.velocity(None)\n"),
      ('explicit-original-error',"(result_directory/'result.txt').write_text('partial exact α');raise RuntimeError('original explicit failure α')\n"),
      ('zero-exit-early-quit',"import os\n(result_directory/'result.txt').write_text('partial exact α');os._exit(0)\n"),
      ('missing-marker',"(result_directory/'result.txt').write_text('complete')\n"),
      ('wrong-marker',"(result_directory/'result.txt').write_text('complete');analysis_complete='WRONG'\n"),
      ('missing-result',"analysis_complete='COMPLETE'\n"),
      ('extra-result',"(result_directory/'result.txt').write_text('complete');(result_directory/'extra.txt').write_text('extra');analysis_complete='COMPLETE'\n"),
      ('changed-captured-input',"(input_directory/'literal.txt').write_text('changed');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('extra-captured-input',"(input_directory/'extra.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('unexpected-root-output',"(result_directory.parent/'undeclared-result.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('workspace-symlink',"(result_directory/'result.txt').symlink_to(input_directory/'literal.txt');analysis_complete='COMPLETE'\n")])[7]
   target,state=execute(name,normal(name,script),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and not state['condition_counts_complete'] and state['original_warnings'] is None
  def case_18():
   name,script=([
      ('original-package-error',"original_scvelo.tl.velocity(None)\n"),
      ('explicit-original-error',"(result_directory/'result.txt').write_text('partial exact α');raise RuntimeError('original explicit failure α')\n"),
      ('zero-exit-early-quit',"import os\n(result_directory/'result.txt').write_text('partial exact α');os._exit(0)\n"),
      ('missing-marker',"(result_directory/'result.txt').write_text('complete')\n"),
      ('wrong-marker',"(result_directory/'result.txt').write_text('complete');analysis_complete='WRONG'\n"),
      ('missing-result',"analysis_complete='COMPLETE'\n"),
      ('extra-result',"(result_directory/'result.txt').write_text('complete');(result_directory/'extra.txt').write_text('extra');analysis_complete='COMPLETE'\n"),
      ('changed-captured-input',"(input_directory/'literal.txt').write_text('changed');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('extra-captured-input',"(input_directory/'extra.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('unexpected-root-output',"(result_directory.parent/'undeclared-result.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('workspace-symlink',"(result_directory/'result.txt').symlink_to(input_directory/'literal.txt');analysis_complete='COMPLETE'\n")])[8]
   target,state=execute(name,normal(name,script),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and not state['condition_counts_complete'] and state['original_warnings'] is None
  def case_19():
   name,script=([
      ('original-package-error',"original_scvelo.tl.velocity(None)\n"),
      ('explicit-original-error',"(result_directory/'result.txt').write_text('partial exact α');raise RuntimeError('original explicit failure α')\n"),
      ('zero-exit-early-quit',"import os\n(result_directory/'result.txt').write_text('partial exact α');os._exit(0)\n"),
      ('missing-marker',"(result_directory/'result.txt').write_text('complete')\n"),
      ('wrong-marker',"(result_directory/'result.txt').write_text('complete');analysis_complete='WRONG'\n"),
      ('missing-result',"analysis_complete='COMPLETE'\n"),
      ('extra-result',"(result_directory/'result.txt').write_text('complete');(result_directory/'extra.txt').write_text('extra');analysis_complete='COMPLETE'\n"),
      ('changed-captured-input',"(input_directory/'literal.txt').write_text('changed');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('extra-captured-input',"(input_directory/'extra.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('unexpected-root-output',"(result_directory.parent/'undeclared-result.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('workspace-symlink',"(result_directory/'result.txt').symlink_to(input_directory/'literal.txt');analysis_complete='COMPLETE'\n")])[9]
   target,state=execute(name,normal(name,script),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and not state['condition_counts_complete'] and state['original_warnings'] is None
  def case_20():
   name,script=([
      ('original-package-error',"original_scvelo.tl.velocity(None)\n"),
      ('explicit-original-error',"(result_directory/'result.txt').write_text('partial exact α');raise RuntimeError('original explicit failure α')\n"),
      ('zero-exit-early-quit',"import os\n(result_directory/'result.txt').write_text('partial exact α');os._exit(0)\n"),
      ('missing-marker',"(result_directory/'result.txt').write_text('complete')\n"),
      ('wrong-marker',"(result_directory/'result.txt').write_text('complete');analysis_complete='WRONG'\n"),
      ('missing-result',"analysis_complete='COMPLETE'\n"),
      ('extra-result',"(result_directory/'result.txt').write_text('complete');(result_directory/'extra.txt').write_text('extra');analysis_complete='COMPLETE'\n"),
      ('changed-captured-input',"(input_directory/'literal.txt').write_text('changed');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('extra-captured-input',"(input_directory/'extra.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('unexpected-root-output',"(result_directory.parent/'undeclared-result.txt').write_text('extra');(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),
      ('workspace-symlink',"(result_directory/'result.txt').symlink_to(input_directory/'literal.txt');analysis_complete='COMPLETE'\n")])[10]
   target,state=execute(name,normal(name,script),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and not state['condition_counts_complete'] and state['original_warnings'] is None
  def case_21():
   v=normal('truncated-result',"(result_directory/'result.txt').write_bytes(b'x');analysis_complete='COMPLETE'\n")
   v['expected_outputs']['result.txt']=2
   execute('truncated-result',v,2,True,True)
  def case_22():
   v=normal('wall-time-cap',"import time\ntime.sleep(60);(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n")
   v['limits']['timeout_seconds']=6
   target,state=execute('wall-time-cap',v,2,None,True)
   pid=load(target/'process-live.json')['pid']
   try:os.kill(pid,0);raise AssertionError('Owned timed-out original worker remains live')
   except ProcessLookupError:pass
  def case_23():
   name,script,limits=([
      ('file-byte-cap',"import time\n(result_directory/'result.txt').write_bytes(b'x'*(32*1024*1024));time.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_file_bytes':16*1024*1024}),
      ('workspace-byte-cap',"import time\n(result_directory/'result.txt').write_bytes(b'x'*(80*1024*1024));time.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_workspace_bytes':64*1024*1024,'maximum_file_bytes':128*1024*1024}),
      ('workspace-file-cap',"import time\nfor i in range(120):(result_directory/(str(i)+'.txt')).write_text('x')\ntime.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_files':64}),
      ('workspace-file-cap-after-readiness',"import time\nfor i in range(1024):(result_directory/(str(i)+'.txt')).write_text('x')\ntime.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_files':512})])[0]
   v=normal(name,script);v['limits'].update(limits);target,state=execute(name,v,2,None if name=='workspace-file-cap' else True,True);assert 'capacity' in state['status'],(name,state)
  def case_24():
   name,script,limits=([
      ('file-byte-cap',"import time\n(result_directory/'result.txt').write_bytes(b'x'*(32*1024*1024));time.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_file_bytes':16*1024*1024}),
      ('workspace-byte-cap',"import time\n(result_directory/'result.txt').write_bytes(b'x'*(80*1024*1024));time.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_workspace_bytes':64*1024*1024,'maximum_file_bytes':128*1024*1024}),
      ('workspace-file-cap',"import time\nfor i in range(120):(result_directory/(str(i)+'.txt')).write_text('x')\ntime.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_files':64}),
      ('workspace-file-cap-after-readiness',"import time\nfor i in range(1024):(result_directory/(str(i)+'.txt')).write_text('x')\ntime.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_files':512})])[1]
   v=normal(name,script);v['limits'].update(limits);target,state=execute(name,v,2,None if name=='workspace-file-cap' else True,True);assert 'capacity' in state['status'],(name,state)
  def case_25():
   name,script,limits=([
      ('file-byte-cap',"import time\n(result_directory/'result.txt').write_bytes(b'x'*(32*1024*1024));time.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_file_bytes':16*1024*1024}),
      ('workspace-byte-cap',"import time\n(result_directory/'result.txt').write_bytes(b'x'*(80*1024*1024));time.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_workspace_bytes':64*1024*1024,'maximum_file_bytes':128*1024*1024}),
      ('workspace-file-cap',"import time\nfor i in range(120):(result_directory/(str(i)+'.txt')).write_text('x')\ntime.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_files':64}),
      ('workspace-file-cap-after-readiness',"import time\nfor i in range(1024):(result_directory/(str(i)+'.txt')).write_text('x')\ntime.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_files':512})])[2]
   v=normal(name,script);v['limits'].update(limits);target,state=execute(name,v,2,None if name=='workspace-file-cap' else True,True);assert 'capacity' in state['status'],(name,state)
  def case_26():
   name,script,limits=([
      ('file-byte-cap',"import time\n(result_directory/'result.txt').write_bytes(b'x'*(32*1024*1024));time.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_file_bytes':16*1024*1024}),
      ('workspace-byte-cap',"import time\n(result_directory/'result.txt').write_bytes(b'x'*(80*1024*1024));time.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_workspace_bytes':64*1024*1024,'maximum_file_bytes':128*1024*1024}),
      ('workspace-file-cap',"import time\nfor i in range(120):(result_directory/(str(i)+'.txt')).write_text('x')\ntime.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_files':64}),
      ('workspace-file-cap-after-readiness',"import time\nfor i in range(1024):(result_directory/(str(i)+'.txt')).write_text('x')\ntime.sleep(3);analysis_complete='COMPLETE'\n",{'maximum_files':512})])[3]
   v=normal(name,script);v['limits'].update(limits);target,state=execute(name,v,2,None if name=='workspace-file-cap' else True,True);assert 'capacity' in state['status'],(name,state)
  def case_27():
   v=normal('invalid-script-UTF8')
   Path(v['script']).write_bytes(b'\xff')
   execute('invalid-script-UTF8',v,2,False,False)
  def case_28():
   v=normal('wrong-edition')
   v['version']='0.24'
   execute('wrong-edition',v,3,False,False)
  def case_29():
   v=normal('complete-positive')
   target,state=execute('complete-positive',v,0,True,True)
   execute('existing-output-refusal',v,2,None,None,existing=True)
  def case_30():
   malformed=[('truncated-worker-status','{"status":'),('negative-count-worker-status','{"warning_count":-1,"log_count":0,"error_count":0}'),('boolean-count-worker-status','{"warning_count":false,"log_count":0,"error_count":0}'),('fractional-count-worker-status','{"warning_count":0.0,"log_count":0,"error_count":0}'),('missing-count-worker-status','{"warning_count":0,"log_count":0}'),('duplicate-count-worker-status','{"warning_count":0,"warning_count":1,"log_count":0,"error_count":0}'),('nonfinite-count-worker-status','{"warning_count":NaN,"log_count":0,"error_count":0}'),('nonobject-worker-status','[]')]
   name,raw=(malformed)[0]
   script="import os\n(result_directory/'result.txt').write_text('partial exact α');(diagnostic_directory/'worker-status.json').write_bytes("+repr(raw.encode('ascii'))+");os._exit(0)\n"
   target,state=execute(name,normal(name,script),2,True,True);assert load(target/'process-exit.json')['exit_code']==0 and (target/'diagnostics/worker-status.json').read_bytes()==raw.encode('ascii')
   assert not state['condition_counts_complete'] and state['warning_review_required'] and all(state[n] is None for n in ['original_warnings','original_log_conditions','original_errors'])
   assert 'unavailable' in (target/'WARNINGS.md').read_text() and (target/'WORKER_DIAGNOSTIC_ERROR.txt').is_file() and not (target/'result-files.json').exists()
  def case_31():
   malformed=[('truncated-worker-status','{"status":'),('negative-count-worker-status','{"warning_count":-1,"log_count":0,"error_count":0}'),('boolean-count-worker-status','{"warning_count":false,"log_count":0,"error_count":0}'),('fractional-count-worker-status','{"warning_count":0.0,"log_count":0,"error_count":0}'),('missing-count-worker-status','{"warning_count":0,"log_count":0}'),('duplicate-count-worker-status','{"warning_count":0,"warning_count":1,"log_count":0,"error_count":0}'),('nonfinite-count-worker-status','{"warning_count":NaN,"log_count":0,"error_count":0}'),('nonobject-worker-status','[]')]
   name,raw=(malformed)[1]
   script="import os\n(result_directory/'result.txt').write_text('partial exact α');(diagnostic_directory/'worker-status.json').write_bytes("+repr(raw.encode('ascii'))+");os._exit(0)\n"
   target,state=execute(name,normal(name,script),2,True,True);assert load(target/'process-exit.json')['exit_code']==0 and (target/'diagnostics/worker-status.json').read_bytes()==raw.encode('ascii')
   assert not state['condition_counts_complete'] and state['warning_review_required'] and all(state[n] is None for n in ['original_warnings','original_log_conditions','original_errors'])
   assert 'unavailable' in (target/'WARNINGS.md').read_text() and (target/'WORKER_DIAGNOSTIC_ERROR.txt').is_file() and not (target/'result-files.json').exists()
  def case_32():
   malformed=[('truncated-worker-status','{"status":'),('negative-count-worker-status','{"warning_count":-1,"log_count":0,"error_count":0}'),('boolean-count-worker-status','{"warning_count":false,"log_count":0,"error_count":0}'),('fractional-count-worker-status','{"warning_count":0.0,"log_count":0,"error_count":0}'),('missing-count-worker-status','{"warning_count":0,"log_count":0}'),('duplicate-count-worker-status','{"warning_count":0,"warning_count":1,"log_count":0,"error_count":0}'),('nonfinite-count-worker-status','{"warning_count":NaN,"log_count":0,"error_count":0}'),('nonobject-worker-status','[]')]
   name,raw=(malformed)[2]
   script="import os\n(result_directory/'result.txt').write_text('partial exact α');(diagnostic_directory/'worker-status.json').write_bytes("+repr(raw.encode('ascii'))+");os._exit(0)\n"
   target,state=execute(name,normal(name,script),2,True,True);assert load(target/'process-exit.json')['exit_code']==0 and (target/'diagnostics/worker-status.json').read_bytes()==raw.encode('ascii')
   assert not state['condition_counts_complete'] and state['warning_review_required'] and all(state[n] is None for n in ['original_warnings','original_log_conditions','original_errors'])
   assert 'unavailable' in (target/'WARNINGS.md').read_text() and (target/'WORKER_DIAGNOSTIC_ERROR.txt').is_file() and not (target/'result-files.json').exists()
  def case_33():
   malformed=[('truncated-worker-status','{"status":'),('negative-count-worker-status','{"warning_count":-1,"log_count":0,"error_count":0}'),('boolean-count-worker-status','{"warning_count":false,"log_count":0,"error_count":0}'),('fractional-count-worker-status','{"warning_count":0.0,"log_count":0,"error_count":0}'),('missing-count-worker-status','{"warning_count":0,"log_count":0}'),('duplicate-count-worker-status','{"warning_count":0,"warning_count":1,"log_count":0,"error_count":0}'),('nonfinite-count-worker-status','{"warning_count":NaN,"log_count":0,"error_count":0}'),('nonobject-worker-status','[]')]
   name,raw=(malformed)[3]
   script="import os\n(result_directory/'result.txt').write_text('partial exact α');(diagnostic_directory/'worker-status.json').write_bytes("+repr(raw.encode('ascii'))+");os._exit(0)\n"
   target,state=execute(name,normal(name,script),2,True,True);assert load(target/'process-exit.json')['exit_code']==0 and (target/'diagnostics/worker-status.json').read_bytes()==raw.encode('ascii')
   assert not state['condition_counts_complete'] and state['warning_review_required'] and all(state[n] is None for n in ['original_warnings','original_log_conditions','original_errors'])
   assert 'unavailable' in (target/'WARNINGS.md').read_text() and (target/'WORKER_DIAGNOSTIC_ERROR.txt').is_file() and not (target/'result-files.json').exists()
  def case_34():
   malformed=[('truncated-worker-status','{"status":'),('negative-count-worker-status','{"warning_count":-1,"log_count":0,"error_count":0}'),('boolean-count-worker-status','{"warning_count":false,"log_count":0,"error_count":0}'),('fractional-count-worker-status','{"warning_count":0.0,"log_count":0,"error_count":0}'),('missing-count-worker-status','{"warning_count":0,"log_count":0}'),('duplicate-count-worker-status','{"warning_count":0,"warning_count":1,"log_count":0,"error_count":0}'),('nonfinite-count-worker-status','{"warning_count":NaN,"log_count":0,"error_count":0}'),('nonobject-worker-status','[]')]
   name,raw=(malformed)[4]
   script="import os\n(result_directory/'result.txt').write_text('partial exact α');(diagnostic_directory/'worker-status.json').write_bytes("+repr(raw.encode('ascii'))+");os._exit(0)\n"
   target,state=execute(name,normal(name,script),2,True,True);assert load(target/'process-exit.json')['exit_code']==0 and (target/'diagnostics/worker-status.json').read_bytes()==raw.encode('ascii')
   assert not state['condition_counts_complete'] and state['warning_review_required'] and all(state[n] is None for n in ['original_warnings','original_log_conditions','original_errors'])
   assert 'unavailable' in (target/'WARNINGS.md').read_text() and (target/'WORKER_DIAGNOSTIC_ERROR.txt').is_file() and not (target/'result-files.json').exists()
  def case_35():
   malformed=[('truncated-worker-status','{"status":'),('negative-count-worker-status','{"warning_count":-1,"log_count":0,"error_count":0}'),('boolean-count-worker-status','{"warning_count":false,"log_count":0,"error_count":0}'),('fractional-count-worker-status','{"warning_count":0.0,"log_count":0,"error_count":0}'),('missing-count-worker-status','{"warning_count":0,"log_count":0}'),('duplicate-count-worker-status','{"warning_count":0,"warning_count":1,"log_count":0,"error_count":0}'),('nonfinite-count-worker-status','{"warning_count":NaN,"log_count":0,"error_count":0}'),('nonobject-worker-status','[]')]
   name,raw=(malformed)[5]
   script="import os\n(result_directory/'result.txt').write_text('partial exact α');(diagnostic_directory/'worker-status.json').write_bytes("+repr(raw.encode('ascii'))+");os._exit(0)\n"
   target,state=execute(name,normal(name,script),2,True,True);assert load(target/'process-exit.json')['exit_code']==0 and (target/'diagnostics/worker-status.json').read_bytes()==raw.encode('ascii')
   assert not state['condition_counts_complete'] and state['warning_review_required'] and all(state[n] is None for n in ['original_warnings','original_log_conditions','original_errors'])
   assert 'unavailable' in (target/'WARNINGS.md').read_text() and (target/'WORKER_DIAGNOSTIC_ERROR.txt').is_file() and not (target/'result-files.json').exists()
  def case_36():
   malformed=[('truncated-worker-status','{"status":'),('negative-count-worker-status','{"warning_count":-1,"log_count":0,"error_count":0}'),('boolean-count-worker-status','{"warning_count":false,"log_count":0,"error_count":0}'),('fractional-count-worker-status','{"warning_count":0.0,"log_count":0,"error_count":0}'),('missing-count-worker-status','{"warning_count":0,"log_count":0}'),('duplicate-count-worker-status','{"warning_count":0,"warning_count":1,"log_count":0,"error_count":0}'),('nonfinite-count-worker-status','{"warning_count":NaN,"log_count":0,"error_count":0}'),('nonobject-worker-status','[]')]
   name,raw=(malformed)[6]
   script="import os\n(result_directory/'result.txt').write_text('partial exact α');(diagnostic_directory/'worker-status.json').write_bytes("+repr(raw.encode('ascii'))+");os._exit(0)\n"
   target,state=execute(name,normal(name,script),2,True,True);assert load(target/'process-exit.json')['exit_code']==0 and (target/'diagnostics/worker-status.json').read_bytes()==raw.encode('ascii')
   assert not state['condition_counts_complete'] and state['warning_review_required'] and all(state[n] is None for n in ['original_warnings','original_log_conditions','original_errors'])
   assert 'unavailable' in (target/'WARNINGS.md').read_text() and (target/'WORKER_DIAGNOSTIC_ERROR.txt').is_file() and not (target/'result-files.json').exists()
  def case_37():
   malformed=[('truncated-worker-status','{"status":'),('negative-count-worker-status','{"warning_count":-1,"log_count":0,"error_count":0}'),('boolean-count-worker-status','{"warning_count":false,"log_count":0,"error_count":0}'),('fractional-count-worker-status','{"warning_count":0.0,"log_count":0,"error_count":0}'),('missing-count-worker-status','{"warning_count":0,"log_count":0}'),('duplicate-count-worker-status','{"warning_count":0,"warning_count":1,"log_count":0,"error_count":0}'),('nonfinite-count-worker-status','{"warning_count":NaN,"log_count":0,"error_count":0}'),('nonobject-worker-status','[]')]
   name,raw=(malformed)[7]
   script="import os\n(result_directory/'result.txt').write_text('partial exact α');(diagnostic_directory/'worker-status.json').write_bytes("+repr(raw.encode('ascii'))+");os._exit(0)\n"
   target,state=execute(name,normal(name,script),2,True,True);assert load(target/'process-exit.json')['exit_code']==0 and (target/'diagnostics/worker-status.json').read_bytes()==raw.encode('ascii')
   assert not state['condition_counts_complete'] and state['warning_review_required'] and all(state[n] is None for n in ['original_warnings','original_log_conditions','original_errors'])
   assert 'unavailable' in (target/'WARNINGS.md').read_text() and (target/'WORKER_DIAGNOSTIC_ERROR.txt').is_file() and not (target/'result-files.json').exists()
  def case_38():
   target,state=execute('warning-report-path-blocked',normal('warning-report-path-blocked',"(result_directory.parent/'WARNINGS.md').mkdir();(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),2,True,True,blocked_warnings=True)
   assert state['warning_review_required'] and (target/'WARNINGS.md').is_dir()
  def case_39():
   target,state=execute('status-report-path-blocked',normal('status-report-path-blocked',"(result_directory.parent/'analysis-status.json').mkdir();(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"),2,True,True)
   assert state is None and (target/'analysis-status.json').is_dir() and 'FAIL2' in (target/'WARNINGS.md').read_text()
  def case_40():
   script="from multiprocessing import get_context\nmanager=get_context('fork').Manager();queue=manager.Queue();queue.put('α NA');value=queue.get();manager.shutdown();assert value=='α NA';(result_directory/'result.txt').write_text(value);analysis_complete='COMPLETE'\n"
   execute('fork-manager-round-trip',normal('fork-manager-round-trip',script),0,True,True)
  def case_41():
   script="from multiprocessing import get_context\nmanager=get_context('spawn').Manager();queue=manager.Queue();queue.put('β NA');value=queue.get();manager.shutdown();assert value=='β NA';(result_directory/'result.txt').write_text(value);analysis_complete='COMPLETE'\n"
   execute('spawn-manager-round-trip',normal('spawn-manager-round-trip',script),0,True,True)
  def case_42():
   script="from pathlib import Path\nimport os\n(Path(os.environ['TMPDIR'])/'payload.bin').write_bytes(bytes([0,78,65,255]));(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"
   target,state=execute('native-temporary-bytes-retained',normal('native-temporary-bytes-retained',script),0,True,True)
   inventory=load(target/'native-temporary-inventory.json');original=Path(inventory['original_root'])/'payload.bin'
   assert (target/'native-temporary/payload.bin').read_bytes()==original.read_bytes()==b'\x00NA\xff' and inventory['original_root_retained'] and state['native_temporary_copy_complete']
  def case_43():
   script="from pathlib import Path\nimport os,time\n(Path(os.environ['TMPDIR'])/'payload.bin').write_bytes(b'x'*(32*1024*1024));time.sleep(3);(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"
   value=normal('native-temporary-file-cap',script);value['limits']['maximum_file_bytes']=16*1024*1024
   target,state=execute('native-temporary-file-cap',value,2,True,True);assert 'capacity' in state['status'];inventory=load(target/'native-temporary-inventory.json');assert pin(target/'native-temporary/payload.bin')==pin(Path(inventory['original_root'])/'payload.bin') and (target/'native-temporary/payload.bin').stat().st_size>0
  def case_44():
   script="from pathlib import Path\nimport os,time\n(result_directory/'result.txt').write_bytes(b'x'*(32*1024*1024));(Path(os.environ['TMPDIR'])/'payload.bin').write_bytes(b'y'*(32*1024*1024));time.sleep(3);analysis_complete='COMPLETE'\n"
   value=normal('combined-two-root-byte-cap',script);value['limits'].update(maximum_workspace_bytes=48*1024*1024,maximum_file_bytes=64*1024*1024)
   target,state=execute('combined-two-root-byte-cap',value,2,True,True);assert 'capacity' in state['status'];inventory=load(target/'native-temporary-inventory.json');assert pin(target/'native-temporary/payload.bin')==pin(Path(inventory['original_root'])/'payload.bin') and (target/'native-temporary/payload.bin').stat().st_size>0
  def case_45():
   if a.other_provider is None:raise ValueError('Supply the other exact original edition provider for the cross-edition refusal control')
   value=normal('supported-provider-edition-mismatch');assert load(a.other_provider/'READY.json')['version']!=value['version'];value['provider_directory']=str(a.other_provider.resolve())
   execute('supported-provider-edition-mismatch',value,3,False,False)
  def case_46():
   value=normal('unsupported-mode');value['mode']='NOT_SCRIPT';execute('unsupported-mode',value,2,False,False)
  def case_47():
   script="from pathlib import Path\nimport os,time\n(Path(os.environ['TMPDIR'])/'outside-link').symlink_to(input_directory/'literal.txt');time.sleep(3);(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"
   target,state=execute('native-temporary-symlink-refused',normal('native-temporary-symlink-refused',script),2,True,True);inventory=load(target/'native-temporary-inventory.json');assert inventory['files']['outside-link']['kind']=='symlink' and 'outside-link' not in [p.name for p in (target/'native-temporary').iterdir()]
  def case_48():
   script="import warnings\nwarnings.simplefilter('ignore',UserWarning);warnings.warn('explicitly ignored original warning',UserWarning);(result_directory/'result.txt').write_text('complete');analysis_complete='COMPLETE'\n"
   target,state=execute('ignored-warning-filter-preserved',normal('ignored-warning-filter-preserved',script),0,True,True)
   assert not any('explicitly ignored original warning' in c.get('message','') for c in load(target/'diagnostics/conditions.json'))
   assert 'explicitly ignored original warning' not in (target/'process.stderr').read_text()
  callbacks=[case_0,case_1,case_2,case_3,case_4,case_5,case_6,case_7,case_8,case_9,case_10,case_11,case_12,case_13,case_14,case_15,case_16,case_17,case_18,case_19,case_20,case_21,case_22,case_23,case_24,case_25,case_26,case_27,case_28,case_29,case_30,case_31,case_32,case_33,case_34,case_35,case_36,case_37,case_38,case_39,case_40,case_41,case_42,case_43,case_44,case_45,case_46,case_47,case_48]
  errors=[]
  with concurrent.futures.ThreadPoolExecutor(max_workers=a.workers) as pool:
   futures={pool.submit(fn):fn.__name__ for fn in callbacks}
   for future,name in futures.items():
    try:future.result()
    except BaseException as error:errors.append(dict(case=name,cause=repr(error),traceback=traceback.format_exc()))
  if errors:save(out/'HARNESS_ERRORS.json',errors);raise RuntimeError(errors)
 for row in cases:
  folder=out/row['id'];manifest=folder/'MANIFEST.json'
  if not manifest.is_file():continue
  for n,wanted in load(manifest)['files'].items():
   path=folder/n
   if wanted.get('kind')=='symlink':assert path.is_symlink() and os.readlink(path)==wanted['target']
   else:assert pin(path)==wanted,(row['id'],n)
 assert pin(literal)==before and pin(provider/'READY.json')==provider_before
 result=dict(status='PASS_ORIGINAL_SCVELO_WORKFLOW_POLICIES_CONDITIONS_COMPLETENESS_AND_RECOVERY',controls=len(cases),cases=cases,original_inputs_provider_unchanged=True,failed_partial_outputs_retained=True,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],'controls='+str(len(cases)),flush=True)
if __name__=='__main__':main()
