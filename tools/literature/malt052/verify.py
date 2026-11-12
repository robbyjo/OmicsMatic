#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Rebuild and replay complete MALT0.5.2 controls, without original alignment refits."""
from pathlib import Path,PurePosixPath
from collections import Counter
import argparse,gzip,hashlib,json,lzma,os,re,shutil,subprocess,sys,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3]
DATA=ROOT/'docs/literature/malt052-development'
MAX_FILE=128*1024*1024;MAX_TOTAL=512*1024*1024
ACTION='Required action: retain all failed replay output and diagnostics. Supply this checkout\'s complete controls.zip.xz and matching sources, a permitted JDK21 or JDK25, the pinned MALT0.5.2 reader provider ROOT containing jars/, the exact HTSJDK5 jar or permission to download it, and a NEW output directory. Install the reader with tools/literature/malt052/setup.py --reader-only where permitted. If a required download/install/compiler/runtime is unavailable or forbidden, skip dependent analysis. Resolve the named integrity, format or complete-input capacity issue before retrying. No subset, repaired record or biological finding is substituted.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for block in iter(lambda:f.read(1048576),b''):h.update(block);n+=len(block)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8',newline='\n')
def member(n):
 p=PurePosixPath(n)
 if not n or p.is_absolute() or '\\' in n or ':' in n or any(ord(c)<32 or ord(c)==127 for c in n) or any(x in ['', '.', '..'] for x in n.split('/')):raise ValueError('Unsafe evidence member')
 return n
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java-bin',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--out',type=Path,required=True);g=p.add_mutually_exclusive_group();g.add_argument('--htsjdk-jar',type=Path);g.add_argument('--download-htsjdk',action='store_true');a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_MALT052_PORTABLE_REPLAY',original_alignment_refit=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;validation=json.loads((DATA/'PORTABLE_VALIDATION.json').read_bytes());archive=DATA/'controls.zip.xz';before=validation['sources']
  if pin(archive)!=validation['archive']:raise ValueError('Complete archive bytes/SHA256 differ')
  for n,value in before.items():
   if pin(ROOT/member(n))!=value:raise ValueError('Build/replay source differs: '+n)
  rawzip=out/'controls.zip';expanded=0
  with lzma.open(archive,'rb') as source,rawzip.open('xb') as target:
   for block in iter(lambda:source.read(1048576),b''):
    expanded+=len(block)
    if expanded>MAX_TOTAL:raise ValueError('Complete evidence container exceeds capacity')
    target.write(block)
  with zipfile.ZipFile(rawzip) as z:
   infos=z.infolist();names=[x.filename for x in infos]
   if len(names)>10000 or len(set(names))!=len(names) or any(x.is_dir() or x.file_size>MAX_FILE or (x.external_attr>>16&0o170000) not in [0,0o100000] for x in infos):raise ValueError('Unsafe/over-capacity complete archive inventory')
   if any(n!='MANIFEST.json' and not re.fullmatch(r'objects/[a-f0-9]{64}',n) for n in names) or z.getinfo('MANIFEST.json').file_size>16*1024*1024:raise ValueError('Unexpected archive name/manifest capacity')
   manifest=json.loads(z.read('MANIFEST.json'))
   if manifest['schema']!='MALT052_ALIGNMENT_EVIDENCE_V1' or manifest['cases']!=['A','B','dna5','support59']:raise ValueError('Unsupported complete evidence schema/cases')
   files=manifest['files'];objects={}
   for n,value in files.items():
    member(n)
    if not re.fullmatch(r'[a-f0-9]{64}',value['sha256']) or not 0<=value['bytes']<=MAX_FILE:raise ValueError('Invalid evidence fingerprint')
    key='objects/'+value['sha256']
    if key in objects and objects[key]!=value:raise ValueError('Conflicting object lengths')
    objects[key]=value
   if set(names)!=set(objects)|{'MANIFEST.json'} or sum(v['bytes'] for v in objects.values())>MAX_TOTAL:raise ValueError('Missing/extra/over-capacity evidence objects')
   for n,value in objects.items():
    if z.getinfo(n).file_size!=value['bytes']:raise ValueError('Object length differs')
    h=hashlib.sha256();count=0
    with z.open(n) as source:
     for block in iter(lambda:source.read(1048576),b''):h.update(block);count+=len(block)
    if dict(bytes=count,sha256=h.hexdigest())!=value:raise ValueError('Complete object SHA256 differs')
   def read(n):
    value=files[n]
    if value['bytes']>16*1024*1024:raise ValueError('Small comparison object capacity exceeded')
    return z.read('objects/'+value['sha256'])
   def sam(n):
    with gzip.GzipFile(fileobj=__import__('io').BytesIO(read(n))) as stream:raw=stream.read(MAX_FILE+1)
    if len(raw)>MAX_FILE:raise ValueError('Decoded complete SAM exceeds capacity')
    return Counter(x for x in raw.decode('utf-8').splitlines() if not x.startswith('@'))
   sam_comparisons=[]
   for case in manifest['sam_oracles']:
    left=sam(case['captured']);right=sam(case['baseline'])
    if left!=right or sum(left.values())!=case['alignments']:raise ValueError('Complete captured source SAM rows differ: '+case['case'])
    sam_comparisons.append(dict(case=case['case'],alignments=case['alignments'],all_rows_equal=True))
   restored=0
   for n,value in files.items():
    if n.startswith(('bundles/','source-tree/','source-oracle/','source-cases/')):
     restored+=value['bytes']
     if restored>MAX_TOTAL:raise ValueError('Complete restored replay inputs exceed capacity')
     dest=out/n;dest.parent.mkdir(parents=True,exist_ok=True)
     with z.open('objects/'+value['sha256']) as source,dest.open('xb') as target:shutil.copyfileobj(source,target,1048576)
  env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']};java=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else '';runtime=a.runtime.resolve();processes=[]
  def run(label,command):
   r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=240,env=env);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);record=dict(command=command,exit_code=r.returncode);save(out/(label+'.process.json'),record);processes.append(record)
   if r.returncode==3:raise FileNotFoundError('Required dependency/environment unavailable at '+label+'; inspect full saved diagnostics')
   if r.returncode:raise ValueError('Replay command failed at '+label+'; inspect full saved diagnostics')
  command=[sys.executable,str(ROOT/'tools/literature/malt052/build.py'),'--java-bin',str(java),'--out',str(out/'build')]
  if a.htsjdk_jar:command+=['--htsjdk-jar',str(a.htsjdk_jar.resolve())]
  elif a.download_htsjdk:command+=['--download-htsjdk']
  else:raise FileNotFoundError('Supply exact --htsjdk-jar or permitted --download-htsjdk')
  run('build',command);artifact=out/'build/malt052-alignment-development.jar';classes=out/'probe-classes';classes.mkdir();probes=['Malt052AssignmentNativeProbe','Malt052NativeTreeProbe','Malt052RmaNativeProbe']
  run('compile-probes',[str(java/('javac'+suffix)),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(artifact),'-d',str(classes),*[str(ROOT/'tools/literature/java'/(n+'.java')) for n in probes]])
  cp=str(classes)+os.pathsep+str(artifact);results=[]
  for case in manifest['cases']:
   bundle=out/'bundles'/case;run(case+'-saved-replay',[str(java/('java'+suffix)),'-Xmx3g','-jar',str(artifact),'--verify',str(bundle),'--runtime',str(runtime)]);audit=json.loads((bundle/'audit.json').read_bytes());science=audit['scientific'];run(case+'-raw-fields',[str(java/('java'+suffix)),'-Xmx2g','-cp',cp,'Malt052RmaNativeProbe',str(runtime),str(bundle/'native/rma/reads.rma6'),str(out/(case+'-raw-fields'))])
   for n in ['reads.tsv','matches.tsv','classes.tsv','counts.tsv','identities.tsv']:
    if (out/(case+'-raw-fields')/n).read_bytes()!=(out/'source-oracle'/case/n).read_bytes():raise ValueError('Complete raw RMA field/bit comparison differs: '+case+'/'+n)
   results.append(dict(case=case,input_queries=science['input_queries'],aligned_queries=science['aligned_queries'],alignments=science['alignments'],stored_reads=science['rma_stored_reads'],selected_minimum_support=science['selected_minimum_support']))
   print(case,'complete replay and raw fields passed',flush=True)
  run('native-assignment',[str(java/('java'+suffix)),'-Xmx3g','-cp',cp,'Malt052AssignmentNativeProbe',str(runtime),str(out/'source-cases/cases.tsv'),str(out/'native-assignment')])
  expected=(out/'source-oracle/synthetic.tsv').read_bytes()
  if (out/'native-assignment/synthetic.tsv').read_bytes()!=expected or len(expected.splitlines())-1!=12275:raise ValueError('Complete12275 source/native assignment cases differ')
  run('native-tree',[str(java/('java'+suffix)),'-Xmx3g','-cp',cp,'Malt052NativeTreeProbe',str(runtime),str(out/'source-tree'),str(out/'native-tree')])
  if sum(r['input_queries'] for r in results)!=16862 or sum(r['alignments'] for r in results)!=122:raise ValueError('Complete control population differs')
  support=next(r for r in results if r['case']=='support59')
  if support['stored_reads']!=6 or support['aligned_queries']!=5 or support['selected_minimum_support']!=3:raise ValueError('Complete stored-read support denominator differs')
  for n,value in files.items():
   if n.startswith(('bundles/','source-tree/','source-oracle/','source-cases/')) and pin(out/n)!=value:raise ValueError('Restored evidence changed: '+n)
  if pin(archive)!=validation['archive'] or any(pin(ROOT/n)!=v for n,v in before.items()):raise ValueError('Evidence or code changed during replay')
  state.update(status='PASS_PORTABLE_COMPLETE_MALT052_ALIGNMENT_READER_AND_NATIVE_ASSIGNMENT_REPLAY_ONLY',archive=validation['archive'],logical_evidence_files=len(files),unique_objects=len(objects),restored_bytes=restored,complete_cases=results,captured_sam_oracles=sam_comparisons,source_assignment_cases=12275,effective_taxonomy_nodes=2259893,raw_rma_field_comparisons=4,original_programs_bundled=False,producer_authenticated=False,whole_malt_or_hops_admitted=False,new_tool_credit=0);save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Saved software-profile replay only\n\nNo original alignment was refitted and no producer is authenticated. Full MALT/HOPS/MaltExtract, original accession-map indexing and paper reproduction remain incomplete.\n\n'+ACTION+'\n',encoding='utf-8',newline='\n');print(state['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;state.update(status='SKIP3_MALT052_REPLAY_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_MALT052_PORTABLE_REPLAY',cause=repr(e),traceback=traceback.format_exc(),dependent_analysis_skipped=True,required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(e)+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
  print(state['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable evidence checks')
 raise SystemExit(main())
