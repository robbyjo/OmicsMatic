#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete retained evidence, restore fixed original source, rebuild and rerun actual macros offline."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,tarfile,traceback,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];DATA=ROOT/'docs/literature/imagej154t-workflow-development'
ACTION='Required action: supply permitted full JDK21/25, working AWT display, explicit --allow-build --allow-analysis and NEW output to rebuild and rerun actual original macro comparisons. Missing/forbidden prerequisites SKIP3 skip dependent analysis. No network or system installation is needed by this verifier: complete original source is retained. Keep FAIL2 diagnostics and resolve source/evidence/output contradictions without patching original code. This qualifies synthetic original1.54t core workflows, not Fiji/plugins, paper reproduction, all ImageJ commands or the whole250-paper objective.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def evidence(out):
 v=json.loads((DATA/'VALIDATION.json').read_bytes())
 if v['status']!='PASS_ORIGINAL_IMAGEJ154T_COMPLETE_MACRO_WORKFLOW' or v['new_tool_credit'] or v['whole_goal_complete'] or v['original_source_patched']:raise ValueError('Unsupported validation scope')
 for n,wanted in v['sources'].items():
  if pin(ROOT/n)!=wanted:raise ValueError('Qualified source differs: '+n)
 for line in (DATA/'SHA256SUMS').read_text(encoding='ascii').splitlines():
  sha,n=line.split('  ',1)
  if '/' in n or '\\' in n or n in ['.','..'] or pin(DATA/n)['sha256']!=sha:raise ValueError('Public evidence checksum/member differs')
 if pin(DATA/'controls.zip')!=v['archive']:raise ValueError('Complete evidence archive differs')
 with zipfile.ZipFile(DATA/'controls.zip') as z:
  infos=z.infolist()
  if len({i.filename for i in infos})!=len(infos) or len(infos)>20000 or sum(i.file_size for i in infos)>536870912:raise ValueError('Complete archive population/capacity differs')
  if z.getinfo('LAYOUT.json').file_size>16777216:raise ValueError('Complete logical archive metadata exceeds capacity')
  layout=json.loads(z.read('LAYOUT.json'))
  if layout!={'schema':'IMAGEJ154T_COMPLETE_OBJECTS_V1','files':v['archive_members']}:raise ValueError('Complete logical evidence population differs')
  wanted={}
  for n,row in layout['files'].items():
   sha=row['sha256'];size=row['bytes']
   if not isinstance(size,int) or size<0 or size>134217728 or len(sha)!=64 or any(x not in '0123456789abcdef' for x in sha):raise ValueError('Unsafe evidence fingerprint')
   if sha in wanted and wanted[sha]!=size:raise ValueError('Conflicting evidence object size')
   wanted[sha]=size
  if set(z.namelist())!={'LAYOUT.json',*('objects/'+n for n in wanted)}:raise ValueError('Missing/extra complete evidence object')
  objects=out/'objects';objects.mkdir()
  for sha,size in wanted.items():
   info=z.getinfo('objects/'+sha)
   if info.file_size!=size or info.is_dir() or (info.external_attr>>16)&0o170000==0o120000:raise ValueError('Unsafe evidence object')
   raw=z.read(info)
   if len(raw)!=size or hashlib.sha256(raw).hexdigest()!=sha:raise ValueError('Complete evidence object differs')
   (objects/sha).write_bytes(raw)
  def content(n):return (objects/layout['files'][n]['sha256']).read_bytes()
  for platform in ['windows21','windows25','linux21']:
   prefix=platform+'/controls/';saved=json.loads(content(prefix+'COMPLETED.json'))
   if saved['status']!='PASS_COMPLETE_ORIGINAL_IMAGEJ154T_MACROS_PIXELS_METADATA_AND_REFUSALS' or (saved['original_macro_cases'],saved['complete_original_outputs'],saved['refusal_cases'])!=(5,14,21) or set(saved['complete_original_cases'])!={'byte','hyper','float','rgb','particles'} or any(c['exit_code']!=c['expected_exit'] for c in saved['calls']):raise ValueError('Incomplete retained original platform population')
   for case,rows in saved['complete_original_cases'].items():
    for n,row in rows.items():
     reference=prefix+'original-'+case+'/results/'+n;actual=prefix+case+'/results/'+n
     if not row['complete_file_byte_identical'] or layout['files'][reference]!=row['original'] or layout['files'][actual]!=row['adapter'] or content(reference)!=content(actual):raise ValueError('Complete retained original/adapter output differs')
     if 'complete_original_pixels_axes_calibration_identical' in row and (not row['complete_original_pixels_axes_calibration_identical'] or content(prefix+'original-'+case+'/'+n+'.pixels.tsv')!=content(prefix+case+'/'+n+'.pixels.tsv')):raise ValueError('Complete retained independently decoded original pixels differ')
   caught=json.loads(content(prefix+'plugin-caught-error/worker-status.json'));zero=json.loads(content(prefix+'plugin-exit-zero/process-exit.json'))
   if not any('original caught fixture exception' in x for x in caught['recorded_original_failures']) or zero['exit_code']!=0 or prefix+'plugin-exit-zero/worker-status.json' in layout['files']:raise ValueError('Retained original exception/zero-exit refusal differs')
   for case in ['plugin-caught-error','plugin-exit-zero','timeout','core-override']:
    if json.loads(content(prefix+case+'/analysis-status.json'))['result_admitted']:raise ValueError('Retained original failure was admitted')
  source=objects/layout['files']['upstream/source.tar']['sha256'];profile=json.loads((HERE/'source-profile.json').read_bytes())
  if pin(source)!=profile['source_archive']:raise ValueError('Complete fixed original archive differs')
  extracted=out/'source';extracted.mkdir()
  with tarfile.open(source) as tar:
   members=[m for m in tar.getmembers() if not m.isdir()]
   if len(members)!=len(profile['source_members']) or {m.name for m in members}!=set(profile['source_members']):raise ValueError('Complete original source population differs')
   for m in members:
    if not m.isfile() or m.issym() or m.islnk() or m.name.startswith('/') or '\\' in m.name or any(p in ['','.','..'] for p in m.name.split('/')):raise ValueError('Unsafe original source member')
    wanted=profile['source_members'][m.name]
    if m.size!=wanted['bytes']:raise ValueError('Original source size differs')
    stream=tar.extractfile(m);raw=stream.read() if stream else b''
    if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=wanted:raise ValueError('Original complete source differs')
    target=extracted/m.name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
 return v,extracted
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--allow-build',action='store_true');p.add_argument('--allow-analysis',action='store_true');p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  jdk=a.java_bin.resolve()
  if out.is_relative_to(jdk) or jdk.is_relative_to(out):raise ValueError('Output overlaps protected compiler')
  out.mkdir(parents=True,exist_ok=False);owned=True;v,source=evidence(out)
  if not a.allow_build or not a.allow_analysis:raise OSError('Build or analysis permission absent; evidence verified but dependent build/macro analysis skipped')
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=600);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode==3:raise OSError('Original runtime/display prerequisite unavailable at '+label+'; retain diagnostics and skip dependent analysis')
   if r.returncode:raise ValueError('Actual original source/control check failed: '+label)
  run('provider',[sys.executable,HERE/'provider.py','--java-bin',jdk,'--source-root',source,'--allow-build','--out',out/'provider']);run('build',[sys.executable,HERE/'build.py','--java-bin',jdk,'--provider',out/'provider','--out',out/'build']);run('actual-controls',[sys.executable,HERE/'test_workflow.py','--java-bin',jdk,'--original-jar',out/'provider/ij-1.54t.jar','--jar',out/'build/imagej154t-workflow-development.jar','--out',out/'actual-controls']);run('setup-refusals',[sys.executable,HERE/'test_setup.py','--java-bin',jdk,'--source-root',source,'--out',out/'setup-refusals']);c=json.loads((out/'actual-controls/COMPLETED.json').read_bytes())
  if (c['original_macro_cases'],c['complete_original_outputs'],c['refusal_cases'],c['synthetic_plugin_success_cases'])!=(5,14,21,1) or any(not row['complete_file_byte_identical'] for case in c['complete_original_cases'].values() for row in case.values()):raise ValueError('Incomplete actual original control population')
  for n,wanted in v['sources'].items():
   if pin(ROOT/n)!=wanted:raise ValueError('Qualified source changed during actual replay')
  result=dict(status='PASS_OFFLINE_SOURCE_REBUILD_AND_ACTUAL_ORIGINAL_IMAGEJ154T_CONTROLS',calls=calls,original_macro_cases=5,complete_byte_identical_original_outputs=14,refusals=21,setup_refusals=7,synthetic_plugin_success_cases=1,original_source_patched=False,external_plugins_qualified=False,paper_reproduction=False,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_ORIGINAL_RUNTIME_OR_POLICY_UNAVAILABLE' if code==3 else 'FAIL2_IMAGEJ154T_REBUILD_REPLAY',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',v);(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
