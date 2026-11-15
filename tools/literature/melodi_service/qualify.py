#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run explicit public demonstration queries through the built Java API adapter."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,sys,traceback
sys.dont_write_bytecode=True
ACTION='SKIP3 skips missing/forbidden Java, JAR or networking prerequisites. FAIL2 admits no complete operation. Preserve raw responses/failures and retry NEW paths. These are explicit current public demonstration queries, not recovered author inputs or historical results.'
def pin(p):
 h=hashlib.sha256();size=0
 with Path(p).open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())
def save(p,v):
 with Path(p).open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2,ensure_ascii=False,allow_nan=False);f.write('\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java',default='java');p.add_argument('--jar',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-network',action='store_true');a=p.parse_args();out=a.out.absolute();owned=False
 try:
  if out!=out.resolve() or out.is_relative_to(a.jar.resolve()) or a.jar.resolve().is_relative_to(out):raise ValueError('Use a canonical NEW output without protected-input overlap')
  out.mkdir(parents=True,exist_ok=False);owned=True;java=shutil.which(a.java)
  if java is None or not a.jar.is_file() or not a.allow_network:raise OSError('Supply a permitted existing Java17+, built JAR and explicit --allow-network; no query was sent')
  original=pin(a.jar);save(out/'JAR.json',dict(path=str(a.jar.resolve()),**original));rows=[]
  for operation,payload in [('overlap',dict(x=['TP53'],y=['colorectal cancer'])),('enrich',dict(query='TP53')),('sentence',dict(pmid='23715093'))]:
   request=out/(operation+'-request.json');save(request,dict(schema=1,operation=operation,payload=payload,server_snapshot='live-unversioned',timeout_seconds=90,max_response_bytes=67108864))
   command=[java,'-Xmx512m','-cp',str(a.jar.resolve()),'org.jlinalg.cli.MelodiPrestoCli','run','--request',str(request),'--out',str(out/operation),'--allow-network']
   with (out/(operation+'.stdout')).open('xb') as so,(out/(operation+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/(operation+'-LIVE.json'),dict(pid=child.pid,command=command))
    try:code=child.wait(timeout=130)
    except BaseException:
     if child.poll() is None:child.kill();child.wait()
     raise
   row=dict(operation=operation,exit_code=code,command=command);rows.append(row);save(out/(operation+'-PROCESS.json'),row)
   if code==0:
    completed=json.loads((out/operation/'COMPLETED.json').read_bytes());row['records']=completed['records'];row['response']=pin(out/operation/'response.bin')
   else:
    if (out/operation/'COMPLETED.json').exists():raise ValueError('Failed operation admitted a result')
    row['failure']=json.loads((out/operation/'FAILURE.json').read_bytes())
    print('ORIGINAL_OPERATION_NOT_ADMITTED',operation,row['failure']['cause'],flush=True)
   print('OBSERVED_MELODI_SERVICE_OPERATION',operation,code,flush=True)
  save(out/'OPERATIONS.json',rows)
  if pin(a.jar)!=original:raise ValueError('Protected JAR changed during qualification')
  failed=[r for r in rows if r['exit_code']]
  if failed:raise RuntimeError('One or more original operations unavailable or failed; inspect each complete record, never count failure as success')
  save(out/'COMPLETED.json',dict(status='PASS_ALL_THREE_CURRENT_PACKAGED_MELODI_SERVICE_OPERATIONS',operations=rows,author_snapshot_established=False,native_analytic_credit=0,whole_goal_complete=False));return 0
 except BaseException as e:
  code=3 if isinstance(e,OSError) and not isinstance(e,FileExistsError) else 2;print(('SKIP3' if code==3 else 'FAIL2'),repr(e),ACTION,flush=True)
  if owned:save(out/'FAILURE.json',dict(status='SKIP3' if code==3 else 'FAIL2',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION))
  return code
if __name__=='__main__':raise SystemExit(main())
