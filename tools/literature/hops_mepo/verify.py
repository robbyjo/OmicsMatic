#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete original HOPS me_po controls and targeted refusals without fitting."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,lzma,os,shutil,subprocess,sys,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3];DATA=ROOT/'docs/literature/hops-mepo-development'
ACTION='Required action: retain every failed replay and diagnostic. Supply complete matching source/evidence, permitted native Linux x86_64 application JDK21, the exact external MaltExtract1.7 reader, the public R4.0.5 provider, and a NEW native-Linux output directory with at least8GiB free. Install/download only where permitted. Missing or forbidden prerequisites skip dependent replay/analysis. Resolve named integrity, complete-capacity or scientific issues without repairing evidence. Replay freshly decodes RData but repeats no original fitting/plotting, authenticates no producer and does not qualify whole HOPS/MALT or the250-paper goal.'
class Unavailable(Exception):pass
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def member(name):
 p=PurePosixPath(name)
 if not name or p.is_absolute() or '\\' in name or any(ord(c)<32 or ord(c)==127 for c in name) or any(x in ['','.','..'] for x in name.split('/')):raise ValueError('Unsafe complete evidence member')
 return name
def restore(archive,out):
 container=out/'controls.zip';expanded=0
 with lzma.open(archive,'rb') as source,container.open('xb') as target:
  for b in iter(lambda:source.read(1048576),b''):
   expanded+=len(b)
   if expanded>2*1024**3:raise ValueError('Complete object container exceeds2GiB')
   target.write(b)
 evidence=out/'evidence';evidence.mkdir()
 with zipfile.ZipFile(container) as z:
  names=set()
  for info in z.infolist():
   name=member(info.filename)
   if name in names or info.is_dir() or info.file_size>268435456 or (info.external_attr>>16)&0o170000 not in [0,0o100000]:raise ValueError('Invalid/duplicate complete evidence object')
   names.add(name)
  if len(names)>50000 or 'MANIFEST.json' not in names or z.getinfo('MANIFEST.json').file_size>67108864:raise ValueError('Complete evidence manifest capacity differs')
  manifest=json.loads(z.read('MANIFEST.json'))
  if manifest.get('schema')!='HOPS035_MEPO_COMPLETE_EVIDENCE_V1':raise ValueError('Unsupported complete me_po evidence schema')
  files={member(n):v for n,v in manifest['files'].items()};objects={}
  if len(files)>50000:raise ValueError('Complete logical member capacity exceeded')
  for v in files.values():
   if set(v)!=set(['bytes','sha256','mode']) or type(v['bytes']) is not int or not 0<=v['bytes']<=268435456 or type(v['mode']) is not int or not 0<=v['mode']<=0o777 or len(v['sha256'])!=64 or any(x not in '0123456789abcdef' for x in v['sha256']):raise ValueError('Invalid complete member identity/mode')
   value={k:v[k] for k in ['bytes','sha256']}
   if v['sha256'] in objects and objects[v['sha256']]!=value:raise ValueError('Conflicting complete object identity')
   objects[v['sha256']]=value
  if names!=set(['MANIFEST.json'])|{'objects/'+n for n in objects} or sum(v['bytes'] for v in objects.values())>2*1024**3 or sum(v['bytes'] for v in files.values())>8*1024**3:raise ValueError('Complete object/member population or byte capacity differs')
  for digest,value in objects.items():
   h=hashlib.sha256();n=0
   with z.open('objects/'+digest) as f:
    for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
   if dict(bytes=n,sha256=h.hexdigest())!=value:raise ValueError('Complete evidence object differs')
  for name,v in files.items():
   p=evidence/name;p.parent.mkdir(parents=True,exist_ok=True)
   with z.open('objects/'+v['sha256']) as source,p.open('xb') as target:shutil.copyfileobj(source,target,1048576)
   p.chmod(v['mode'])
   if pin(p)!={k:v[k] for k in ['bytes','sha256']}:raise ValueError('Complete restored evidence member differs')
 return evidence,files,len(objects)
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for n in ['java-bin','reader-runtime','post-runtime','out']:p.add_argument('--'+n,required=True,type=Path)
 a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_HOPS_MEPO_SAVED_REPLAY',original_fitting_repeated=False,original_plotting_repeated=False,new_tool_credit=0,whole_hops_qualified=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if sys.platform!='linux' or os.uname().machine not in ['x86_64','amd64']:raise Unavailable('Native Linux x86_64 required for fresh RData verification')
  if shutil.disk_usage(out).free<8*1024**3:raise Unavailable('Reserve at least8GiB free for complete restoration and replay')
  reader=a.reader_runtime.resolve();post=a.post_runtime.resolve()
  if not (reader/'MaltExtract1.7.jar').is_file() or not (post/'RUNTIME_READY.json').is_file():raise Unavailable('Exact external1.7 reader and public R4.0.5 provider required')
  validation=json.loads((DATA/'VALIDATION.json').read_bytes())
  if validation['status']!='PASS_ORIGINAL_HOPS035_MEPO_COMPLETE_OPERATION_CONTROLS' or validation['new_tool_credit']!=0 or validation['whole_hops_qualified']:raise ValueError('Unsupported original me_po qualification scope')
  if pin(DATA/'controls.zip.xz')!=validation['archive']:raise ValueError('Complete me_po evidence archive differs')
  for name,value in validation['sources'].items():
   if pin(ROOT/member(name))!=value:raise ValueError('Current me_po replay source differs: '+name)
  evidence,files,objects=restore(DATA/'controls.zip.xz',out);calls=[]
  def run(label,command,expected=0,cause=None):
   command=list(map(str,command));r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=600);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected,expected_cause=cause);save(out/(label+'.process.json'),row);calls.append(row)
   if r.returncode==3 and expected!=3:raise Unavailable('Required runtime unavailable at '+label+'; inspect diagnostics and skip dependent analysis')
   if r.returncode!=expected:raise ValueError('Complete me_po replay failed at '+label)
   if cause:
    text=r.stderr.decode(errors='replace')
    if cause not in text or 'NO RESULT ADMITTED' not in text or 'Required action:' not in text or 'Complete saved original-operation inventory differs' in text:raise ValueError('Wrong/generic failure masks intended semantic refusal: '+label)
   return row
  build=out/'build';run('build',[sys.executable,ROOT/'tools/literature/hops_mepo/build.py','--java-bin',a.java_bin,'--out',build]);jar=build/'hops-mepo-development.jar';java=a.java_bin/'java';(out/'calls').mkdir()
  positives=['source-multi-1','complete-multi-1','source-multi-jdk25-1']
  for name in positives:
   run('verify-'+name,[java,'-Xmx3g','-jar',jar,'--verify',evidence/'controls'/name/'bundle','--reader-runtime',reader,'--post-runtime',post,'--out',out/'calls'/name]);result=json.loads((out/'calls'/name/'VERIFIED.json').read_bytes())
   if result['status']!='pass-complete-original-hops035-mepo-saved-verification' or not result['fresh_RData_decoding'] or result['original_fitting_repeated']:raise ValueError('Saved original me_po verification scope differs')
   print('Complete original me_po control',name,'passed',flush=True)
  refusals=json.loads((evidence/'refusals/COMPLETED.json').read_bytes())
  negatives=[c for c in refusals['calls'] if c['expected_exit']==2]
  if len(negatives)!=10 or not refusals['specific_causes_checked']:raise ValueError('Complete specific contradiction population differs')
  for c in negatives:
   name=c['label'];run('refuse-'+name,[java,'-Xmx3g','-jar',jar,'--verify',evidence/'refusals/fixtures'/name,'--reader-runtime',reader,'--post-runtime',post,'--out',out/'calls'/('refuse-'+name)],2,c['expected_cause'])
  comparison=json.loads((evidence/'original-comparison/COMPLETED.json').read_bytes())
  if comparison['status']!='PASS_BOTH_ORIGINAL_HOPS_MEPO_PROFILES_FULL_OUTPUT_COMPARISONS' or len(comparison['results'])!=2:raise ValueError('Independent original launcher comparisons differ')
  for item in comparison['results']:
   label='source-multi-1' if item['profile']=='source-def-anc' else 'complete-multi-1';original=json.loads((evidence/'original-comparison'/label/'scientific.json').read_bytes());saved=json.loads((evidence/'controls'/label/'bundle/scientific-export.json').read_bytes())
   if original!=saved or len(item['scientific_files'])!=173:raise ValueError('Complete original reference fields/file population differ')
  failed=evidence/'failed/empty-stock-1'
  if not (failed/'FAILURE.json').is_file() or (failed/'bundle/COMPONENT_COMPLETED.json').exists() or json.loads((failed/'driver-process/process.json').read_bytes())['exit_code']!=0:raise ValueError('Original zero-driver/failed-child attempt lost or admitted')
  text=(failed/'driver-process/process.stderr').read_text()
  if 'Process Exited with status: 1' not in text or "increasing 'x' and 'y' values expected" not in text:raise ValueError('Original empty-plot child failure evidence differs')
  for name,value in files.items():
   p=evidence/name
   if pin(p)!={k:value[k] for k in ['bytes','sha256']} or p.stat().st_mode&0o777!=value['mode']:raise ValueError('Read-only replay changed complete evidence: '+name)
  for name,value in validation['sources'].items():
   if pin(ROOT/name)!=value:raise ValueError('Current me_po replay source changed')
  state.update(status='PASS_COMPLETE_ORIGINAL_HOPS_MEPO_SAVED_REPLAY',restored_members=len(files),restored_bytes=sum(v['bytes'] for v in files.values()),unique_objects=objects,complete_workflows=3,specific_refusals=10,independent_original_comparisons=2,original_child_failure_retained=True,commands=calls)
  save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Saved software-control replay only\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,(Unavailable,PermissionError,FileNotFoundError)) else 2;state.update(status='SKIP3_MEPO_REPLAY_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_HOPS_MEPO_SAVED_REPLAY',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,dependent_analysis_skipped=True)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source/evidence checks')
 raise SystemExit(main())
