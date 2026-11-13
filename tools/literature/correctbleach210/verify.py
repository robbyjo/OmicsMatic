#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete retained evidence, restore fixed dependencies/source, rebuild and rerun original controls offline."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,tarfile,traceback,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];DATA=ROOT/'docs/literature/correctbleach210-workflow-development'
ACTION='Required action: provide permitted JDK21/25, explicit --allow-build --allow-analysis and NEW output. Without those permissions or runtime prerequisites, SKIP3 skips the dependent build/analysis after evidence verification. All fixed original source/dependency artifacts are retained; no network or system installation is needed. Keep every FAIL2 diagnostic and resolve source, full-output or admission contradictions without patching original behavior. Histogram matching is inappropriate for intensity quantification. P026 edition/method/settings remain unreported; no paper reproduction, catalog credit or whole250-paper completion is claimed.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def evidence(out):
 v=json.loads((DATA/'VALIDATION.json').read_bytes())
 if v['status']!='PASS_ORIGINAL_CORRECTBLEACH210_AND_NATIVE_FULL_IMAGE_WORKFLOWS' or v['original_source_patched'] or v['new_tool_credit'] or v['whole_goal_complete']:raise ValueError('Unsupported qualification scope')
 for name,wanted in v['sources'].items():
  if pin(ROOT/name)!=wanted:raise ValueError('Qualified source differs: '+name)
 for line in (DATA/'SHA256SUMS').read_text(encoding='ascii').splitlines():
  wanted,name=line.split('  ',1)
  if '/' in name or '\\' in name or name in ['.','..'] or pin(DATA/name)['sha256']!=wanted:raise ValueError('Public evidence checksum/member differs')
 if pin(DATA/'controls.zip')!=v['archive']:raise ValueError('Complete evidence archive differs')
 profile=json.loads((HERE/'source-profile.json').read_bytes())
 with zipfile.ZipFile(DATA/'controls.zip') as z:
  entries=z.infolist()
  if len({i.filename for i in entries})!=len(entries) or len(entries)>20000 or sum(i.file_size for i in entries)>536870912 or z.getinfo('LAYOUT.json').file_size>16777216:raise ValueError('Complete evidence population/capacity differs')
  layout=json.loads(z.read('LAYOUT.json'))
  if layout!={'schema':'CORRECTBLEACH210_COMPLETE_OBJECTS_V1','files':v['archive_members']}:raise ValueError('Complete logical evidence population differs')
  wanted={}
  for name,row in layout['files'].items():
   sha=row['sha256'];size=row['bytes']
   if not isinstance(size,int) or size<0 or size>134217728 or len(sha)!=64 or any(c not in '0123456789abcdef' for c in sha):raise ValueError('Unsafe evidence fingerprint')
   if sha in wanted and wanted[sha]!=size:raise ValueError('Conflicting evidence object size')
   wanted[sha]=size
  if set(z.namelist())!={'LAYOUT.json',*('objects/'+sha for sha in wanted)}:raise ValueError('Missing/extra complete evidence object')
  objects=out/'objects';objects.mkdir()
  for sha,size in wanted.items():
   info=z.getinfo('objects/'+sha)
   if info.file_size!=size or info.is_dir() or (info.external_attr>>16)&0o170000==0o120000:raise ValueError('Unsafe evidence object')
   raw=z.read(info)
   if len(raw)!=size or hashlib.sha256(raw).hexdigest()!=sha:raise ValueError('Complete evidence object differs')
   (objects/sha).write_bytes(raw)
  def content(name):return (objects/layout['files'][name]['sha256']).read_bytes()
  for platform in ['windows21','windows25','linux21']:
   prefix=platform+'/workflow/';saved=json.loads(content(prefix+'COMPLETED.json'))
   if saved['status']!='PASS_COMPLETE_ORIGINAL_CORRECTBLEACH210_AND_NATIVE_IMAGE_WORKFLOWS' or (saved['source_image_cases'],saved['complete_native_histogram_cases'],saved['request_refusals'])!=(21,6,24) or any(r['exit_code']!=r['expected_exit'] for r in saved['calls']):raise ValueError('Incomplete retained original workflow controls')
   for case,row in saved['complete_original_cases'].items():
    filename='corrected.tif' if row['original_exit']==0 else 'unadmitted-original-image.tif';ref=prefix+'original-'+case+'/'+filename;actual=prefix+case+'/'+filename
    if not row['complete_file_byte_identical'] or layout['files'][ref]!=row['complete_original_image'] or layout['files'][actual]!=row['complete_adapter_image'] or content(ref)!=content(actual):raise ValueError('Complete original/adapter TIFF differs')
    status=json.loads(content(prefix+case+'/analysis-status.json'))
    if status['result_admitted']!=(row['adapter_exit']==0):raise ValueError('Retained original failure admission differs')
    if 'complete_native_image' in row:
     native=prefix+case+'-native/'+filename
     if not row['complete_native_file_byte_identical'] or layout['files'][native]!=row['complete_native_image'] or content(ref)!=content(native):raise ValueError('Complete native TIFF differs')
    if row['method']=='EXPONENTIAL_FIT':
     model=json.loads(content(prefix+case+'/worker-status.json'))['actual_original_exponential_model']
     if model['second_fit_performed'] or model['reportable_fit']!=(row['adapter_exit']==0):raise ValueError('Retained actual fitted-model admission differs')
     if row['original_exit']==0 and [p['value'] for p in model['parameters_including_residual_sum'][:3]]!=content(prefix+'original-'+case+'.stdout').decode('utf8').strip().splitlines()[-1].split(','):raise ValueError('Retained actual fit differs from published original parameters')
   native=json.loads(content(platform+'/native/COMPLETED.json'))
   if (native['status'],native['cases'],native['complete_lookup_rows'],native['complete_cdf_raw_values'])!=('PASS_COMPLETE_ORIGINAL_CORRECTBLEACH210_HISTOGRAM_REFERENCE_AND_NATIVE',1048,212763,426993) or native['reference']!=layout['files'][platform+'/native/complete-original.bin'] or b'cases=1048 lookup_rows=212763 cdf_values=426993' not in content(platform+'/native/complete-native.stdout'):raise ValueError('Incomplete retained original/native histogram controls')
  setup=json.loads(content('setup/COMPLETED.json'));download=json.loads(content('download/READY.json'))
  if setup['setup_refusals']!=12 or setup['image_analysis_executed'] or setup['dependent_original_compiler_launched'] or download['status']!='READY_PRIVATE_COMPLETE_ORIGINAL_CORRECTBLEACH210_NO_ANALYSIS' or download['original_source_patched']:raise ValueError('Retained setup/download qualification differs')
  parent=content('upstream/parent-pom.xml')
  if dict(bytes=len(parent),sha256=hashlib.sha256(parent).hexdigest())!={k:profile['parent_pom'][k] for k in ['bytes','sha256']}:raise ValueError('Retained original dependency-management POM differs')
  if hashlib.sha256(content('upstream/fiji-published.jar')).hexdigest()!='65b861ebae641b2aaa235e296f42afdb948d356bbddec92d2808f0c40da1174a':raise ValueError('Second independent published original plugin copy differs')
  for name in ['zero-ratio','increasing-exp','channels-volume']:
   if json.loads(content('linux21/workflow/'+name+'/analysis-status.json'))['result_admitted']:raise ValueError('Original failure/partial output admitted')
  sourceArchive=objects/layout['files']['upstream/source.tar']['sha256']
  if pin(sourceArchive)!=profile['source_archive']:raise ValueError('Complete fixed original Git archive differs')
  source=out/'source';source.mkdir()
  with tarfile.open(sourceArchive) as tar:
   members=[m for m in tar.getmembers() if not m.isdir()]
   if len(members)!=len(profile['source_members']) or {m.name for m in members}!=set(profile['source_members']):raise ValueError('Complete original source population differs')
   for member in members:
    if not member.isfile() or member.issym() or member.islnk() or member.name.startswith('/') or '\\' in member.name or any(p in ['','.','..'] for p in member.name.split('/')):raise ValueError('Unsafe original source member')
    expected=profile['source_members'][member.name]
    if member.size!=expected['bytes']:raise ValueError('Original source member capacity differs')
    stream=tar.extractfile(member);raw=stream.read() if stream else b''
    if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=expected:raise ValueError('Complete original source bytes differ')
    target=source/member.name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
  deps=out/'dependencies';deps.mkdir();reference=None
  for artifact in profile['dependency_artifacts']:
   name=artifact['path'];raw=content('upstream/dependencies/'+name);expected={k:artifact[k] for k in ['bytes','sha256']}
   if '/' in name or '\\' in name or dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=expected:raise ValueError('Complete fixed dependency differs')
   (deps/name).write_bytes(raw)
   if artifact['sha256']=='65b861ebae641b2aaa235e296f42afdb948d356bbddec92d2808f0c40da1174a':reference=deps/name
  if reference is None:raise ValueError('Independent published original plugin absent')
 return v,source,deps,reference
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--allow-build',action='store_true');p.add_argument('--allow-analysis',action='store_true');p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  jdk=a.java_bin.resolve()
  for protected in [jdk,HERE,DATA]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected compiler/source/evidence')
  out.mkdir(parents=True,exist_ok=False);owned=True;v,source,deps,reference=evidence(out)
  if not a.allow_build or not a.allow_analysis:raise OSError('Build or analysis permission absent; complete evidence verified but dependent build/analysis skipped')
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=1200);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode==3:raise OSError('Permitted original runtime prerequisite unavailable at '+label)
   if r.returncode:raise ValueError('Actual original/native control failed: '+label)
  run('provider',[sys.executable,HERE/'provider.py','--java-bin',jdk,'--source-root',source,'--dependency-root',deps,'--allow-build','--out',out/'provider']);run('build',[sys.executable,HERE/'build.py','--java-bin',jdk,'--provider',out/'provider','--out',out/'build']);run('native',[sys.executable,HERE/'test_native.py','--java-bin',jdk,'--reference-plugin',reference,'--out',out/'native']);run('workflow',[sys.executable,HERE/'test_workflow.py','--java-bin',jdk,'--provider',out/'provider','--reference-plugin',reference,'--jar',out/'build/correctbleach210-workflow-development.jar','--out',out/'workflow']);run('setup',[sys.executable,HERE/'test_setup.py','--java-bin',jdk,'--source-root',source,'--dependency-root',deps,'--out',out/'setup'])
  for name,wanted in v['sources'].items():
   if pin(ROOT/name)!=wanted:raise ValueError('Qualified source changed during actual replay')
  result=dict(status='PASS_OFFLINE_ORIGINAL_CORRECTBLEACH210_REBUILD_AND_ACTUAL_NATIVE_IMAGE_CONTROLS',calls=calls,source_image_cases=21,complete_native_histogram_cases=6,request_refusals=24,setup_refusals=12,original_histogram_cases=1048,complete_lookup_rows=212763,complete_cdf_raw_values=426993,original_source_patched=False,second_exponential_fit_performed=False,paper_reproduction=False,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;result=dict(status='SKIP3_ORIGINAL_RUNTIME_OR_POLICY_UNAVAILABLE' if code==3 else 'FAIL2_CORRECTBLEACH210_OFFLINE_REPLAY',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
