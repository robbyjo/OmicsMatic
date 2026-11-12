#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Restore and replay complete MALT build/BlastN workflow controls without original fitting."""
from pathlib import Path
import argparse,hashlib,json,lzma,os,shutil,subprocess,sys,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3];DATA=ROOT/'docs/literature/malt052-workflow-development'
ACTION='Required action: retain all diagnostics; supply a permitted JDK21/25, the exact separate MALT0.5.2 reader provider, exact HTSJDK5.0.0 or permitted download, sufficient storage for complete evidence and a NEW output directory. If required install/runtime/execution is unavailable or forbidden, skip dependent analysis. Resolve the named integrity/scientific/capacity issue before retrying. Saved controls repeat no original fitting and establish no producer authentication, historical-database or biological reproduction.'
class Unavailable(OSError):pass
def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);size+=len(b)
 return dict(bytes=size,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def member(n):
 if not isinstance(n,str) or not n or n.startswith('/') or any(c in n for c in ['\\',':','\0']) or any(p in ['', '.', '..'] for p in n.split('/')):raise ValueError('Unsafe complete evidence member')
 return n
def restore(archive,out,expected):
 raw=out/'complete-evidence.zip';size=0
 with lzma.open(archive,'rb') as source,raw.open('xb') as target:
  for b in iter(lambda:source.read(1048576),b''):
   size+=len(b)
   if size>expected['uncompressed_zip_bytes']:raise ValueError('Complete evidence ZIP exceeds its recorded bytes')
   target.write(b)
 if size!=expected['uncompressed_zip_bytes']:raise ValueError('Complete evidence ZIP is truncated')
 evidence=out/'evidence';evidence.mkdir()
 with zipfile.ZipFile(raw) as z:
  infos=z.infolist();names=[i.filename for i in infos]
  if len(names)!=len(set(names)) or any(i.flag_bits&1 or i.compress_type!=zipfile.ZIP_STORED for i in infos):raise ValueError('Ambiguous/encrypted/unsupported complete object archive')
  if z.getinfo('LAYOUT.json').file_size>67108864:raise ValueError('Complete object layout exceeds byte capacity')
  layout_raw=z.read('LAYOUT.json')
  if len(layout_raw)>67108864 or dict(bytes=len(layout_raw),sha256=hashlib.sha256(layout_raw).hexdigest())!=expected['layout']:raise ValueError('Complete object layout differs')
  layout=json.loads(layout_raw)
  if set(layout)!=set(['schema','files']) or layout['schema']!='MALT052_COMPLETE_WORKFLOW_OBJECTS_V1':raise ValueError('Unknown complete workflow object layout')
  files=layout['files'];objects={}
  for name,value in files.items():
   member(name)
   if set(value)!=set(['bytes','sha256','mode']) or not isinstance(value['bytes'],int) or value['bytes']<0 or not isinstance(value['mode'],int) or value['mode']&~0o777:raise ValueError('Invalid complete logical evidence entry')
   digest=value['sha256']
   if not isinstance(digest,str) or len(digest)!=64 or any(c not in '0123456789abcdef' for c in digest):raise ValueError('Invalid complete object digest')
   if digest in objects and objects[digest]!=value['bytes']:raise ValueError('Contradictory duplicate object size')
   objects[digest]=value['bytes']
  if set(names)!={'LAYOUT.json',*['objects/'+s for s in objects]}:raise ValueError('Extra/missing complete archive object')
  if (len(files),len(objects),sum(v['bytes'] for v in files.values()))!=(expected['logical_files'],expected['unique_objects'],expected['restored_bytes']):raise ValueError('Complete logical evidence population differs')
  for name,value in files.items():
   p=evidence/member(name);p.parent.mkdir(parents=True,exist_ok=True)
   if not p.resolve().is_relative_to(evidence.resolve()):raise ValueError('Complete evidence member escapes restore root')
   info=z.getinfo('objects/'+value['sha256'])
   if info.file_size!=value['bytes']:raise ValueError('Complete object byte count differs')
   with z.open(info) as source,p.open('xb') as target:shutil.copyfileobj(source,target,1048576)
   if pin(p)!={k:value[k] for k in ['bytes','sha256']}:raise ValueError('Complete restored evidence bytes differ')
   if os.name!='nt':p.chmod(value['mode'])
 return evidence,files
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for n in ['java-bin','reader-runtime','out']:p.add_argument('--'+n,required=True,type=Path)
 g=p.add_mutually_exclusive_group(required=True);g.add_argument('--htsjdk-jar',type=Path);g.add_argument('--download-htsjdk',action='store_true');a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_COMPLETE_MALT052_WORKFLOW_REPLAY',new_tool_credit=0,whole_goal_complete=False,original_fitting_repeated=False,calls=[])
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;v=json.loads((DATA/'VALIDATION.json').read_bytes())
  if v['status']!='PASS_COMPLETE_OWNED_MALT052_BUILD_BLASTN_WORKFLOW_CONTROLS' or v['new_tool_credit']!=0 or v['whole_goal_complete']:raise ValueError('Unsupported complete workflow qualification scope')
  for n,value in v['sources'].items():
   if pin(ROOT/member(n))!=value:raise ValueError('Current complete workflow replay source differs: '+n)
  if pin(DATA/'controls.zip.xz')!=v['archive']['pin']:raise ValueError('Complete workflow archive differs')
  if shutil.disk_usage(out).free<max(8*1024**3,v['archive']['restored_bytes']+v['archive']['uncompressed_zip_bytes']+1024**3):raise Unavailable('Insufficient free space for complete workflow restoration')
  if not (a.reader_runtime/'jars/megan.jar').is_file():raise Unavailable('Required exact separate MALT0.5.2 reader provider is unavailable')
  evidence,files=restore(DATA/'controls.zip.xz',out,v['archive']);suffix='.exe' if os.name=='nt' else '';java=a.java_bin/('java'+suffix)
  def run(label,command,expected=0,cause=None):
   command=list(map(str,command));r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=900);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected,expected_cause=cause);save(out/(label+'.process.json'),row);state['calls'].append(row)
   if r.returncode==3 and expected!=3:raise Unavailable('Dependent verification skipped at '+label+'; inspect the retained prerequisite diagnostics')
   if r.returncode!=expected:raise ValueError('Complete workflow replay failed at '+label)
   if cause:
    text=r.stderr.decode(errors='replace')
    if cause not in text or 'NO RESULT ADMITTED' not in text or 'Required action:' not in text or 'Complete workflow inventory differs' in text:raise ValueError('Generic/different refusal masks intended semantic contradiction: '+label)
  build=out/'build';command=[sys.executable,ROOT/'tools/literature/malt052_workflow/build.py','--java-bin',a.java_bin,'--out',build]
  command+=['--htsjdk-jar',a.htsjdk_jar] if a.htsjdk_jar else ['--download-htsjdk'];run('build',command);jar=build/'malt052-workflow-development.jar';(out/'calls').mkdir()
  for name in v['complete_workflows']:
   run('verify-'+name,[java,'-Xmx3g','-jar',jar,'--verify',evidence/'workflows'/member(name),'--reader-runtime',a.reader_runtime,'--out',out/'calls'/('verify-'+name)])
   r=json.loads((out/'calls'/('verify-'+name)/'VERIFIED.json').read_bytes())
   if r['status']!='pass-complete-owned-malt052-build-blastn-workflow' or not r['identical_complete_index_handoff'] or not r['complete_fasta_mapping_query_rechecked'] or r['original_fitting_repeated']:raise ValueError('Complete saved verification scope differs')
   print('Complete workflow',name,'passed',flush=True)
  for row in v['specific_refusals']:run('refuse-'+row['label'],[java,'-Xmx3g','-jar',jar,'--verify',evidence/'refusals'/member(row['label']),'--reader-runtime',a.reader_runtime,'--out',out/'calls'/('refuse-'+row['label'])],2,row['expected_cause'])
  for name,value in files.items():
   if pin(evidence/name)!={k:value[k] for k in ['bytes','sha256']}:raise ValueError('Read-only replay changed complete evidence: '+name)
  for n,value in v['sources'].items():
   if pin(ROOT/n)!=value:raise ValueError('Current complete workflow replay source changed')
  state.update(status='PASS_COMPLETE_OWNED_MALT052_BUILD_BLASTN_SAVED_REPLAY',complete_workflows=len(v['complete_workflows']),specific_refusals=len(v['specific_refusals']),restored_members=len(files),restored_bytes=sum(row['bytes'] for row in files.values()),producer_authenticated=False);save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Saved software-control replay only\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,(OSError,Unavailable)) else 2;state.update(status='SKIP3_COMPLETE_MALT052_WORKFLOW_REPLAY_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_COMPLETE_MALT052_WORKFLOW_REPLAY',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete evidence/source checks')
 raise SystemExit(main())
