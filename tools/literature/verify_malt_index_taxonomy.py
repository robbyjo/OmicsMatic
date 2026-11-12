#!/usr/bin/env python3
"""Rebuild/replay complete saved native-index controls without installing or refitting upstream MALT."""
from pathlib import Path,PurePosixPath
from collections import Counter
import argparse,gzip,hashlib,json,lzma,os,re,shutil,subprocess,sys,traceback,zipfile
ROOT=Path(__file__).resolve().parents[2];DATA=ROOT/'docs/literature/malt-index-taxonomy-development'
ACTION='Required action: retain failed replay outputs and diagnostics. Supply this checkout\'s complete controls.zip.xz, matching source files, a permitted JDK21 or JDK25 and a NEW --out directory. If compiler/download/install access is unavailable or forbidden, skip dependent analysis. Resolve integrity or complete-input errors before retrying; do not substitute a prefix, silently repair records or interpret a skip as a biological result.'
MAX_FILE=128*1024*1024;MAX_TOTAL=512*1024*1024
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8',newline='\n')
def member(n):
 p=PurePosixPath(n)
 if not n or p.is_absolute() or '\\' in n or any(c in n for c in ['\0','\r','\n']) or any(x in ['', '.', '..'] for x in n.split('/')):raise ValueError('Unsafe complete evidence member')
 return n
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java-bin',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_NATIVE_INDEX_REPLAY',whole_goal_complete=False,original_alignment_refit=False)
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;validation=json.loads((DATA/'VALIDATION.json').read_bytes());archive=DATA/'controls.zip.xz'
  if pin(archive)!=validation['archive']:raise ValueError('Complete evidence archive SHA256 differs')
  before=validation['sources']
  for n,p in before.items():
   if pin(ROOT/member(n))!=p:raise ValueError('Replay/build source differs: '+n)
  rawzip=out/'controls.zip';total=0
  with lzma.open(archive,'rb') as source,rawzip.open('xb') as target:
   for block in iter(lambda:source.read(1048576),b''):
    total+=len(block)
    if total>MAX_TOTAL:raise ValueError('Complete evidence container exceeds capacity')
    target.write(block)
  with zipfile.ZipFile(rawzip) as z:
   infos=z.infolist();names=[x.filename for x in infos]
   if len(names)>10000 or len(set(names))!=len(names) or any(x.is_dir() or x.file_size>MAX_FILE or x.external_attr>>16&0o170000 not in [0,0o100000] for x in infos):raise ValueError('Unsafe/over-capacity archive inventory')
   for n in names:
    if n!='MANIFEST.json' and not re.fullmatch(r'objects/[a-f0-9]{64}',n):raise ValueError('Unexpected evidence object name')
   if z.getinfo('MANIFEST.json').file_size>16*1024*1024:raise ValueError('Complete manifest exceeds capacity')
   manifest=json.loads(z.read('MANIFEST.json'))
   if manifest['schema']!='MALT_NATIVE_INDEX_EVIDENCE_V1':raise ValueError('Unknown native evidence schema')
   files=manifest['files'];objects={}
   for n,p in files.items():
    member(n)
    if not re.fullmatch(r'[a-f0-9]{64}',p['sha256']) or not 0<=p['bytes']<=MAX_FILE:raise ValueError('Invalid complete evidence fingerprint')
    key='objects/'+p['sha256']
    if key in objects and objects[key]!=p:raise ValueError('Conflicting evidence object size')
    objects[key]=p
   if set(names)!=set(objects)|{'MANIFEST.json'} or sum(p['bytes'] for p in objects.values())>MAX_TOTAL:raise ValueError('Incomplete/extra/over-capacity evidence objects')
   for n,p in objects.items():
    info=z.getinfo(n)
    if info.file_size!=p['bytes']:raise ValueError('Evidence object size differs')
    h=hashlib.sha256();count=0
    with z.open(n) as source:
     for block in iter(lambda:source.read(1048576),b''):h.update(block);count+=len(block)
    if dict(bytes=count,sha256=h.hexdigest())!=p:raise ValueError('Complete evidence object SHA256 differs')
   def read(n):
    p=files[n]
    if p['bytes']>8*1024*1024:raise ValueError('Small evidence comparison capacity exceeded')
    return z.read('objects/'+p['sha256'])
   comparisons=[]
   for case in manifest['oracle_cases']:
    name=case['name']
    def sam(n):
     text=gzip.decompress(read(n))
     if len(text)>MAX_FILE:raise ValueError('Decoded complete SAM exceeds capacity')
     return Counter(x for x in text.decode('utf-8').splitlines() if not x.startswith('@'))
    left=sam(case['native_sam']);right=sam(case['baseline_sam'])
    if left!=right or sum(left.values())!=case['counters']['alignments']:raise ValueError('Complete captured SAM source-oracle rows differ')
    for namepart in ['reads.tsv','matches.tsv','classes.tsv','counts.tsv']:
     if read(case['native_rma']+'/'+namepart)!=read(case['baseline_rma']+'/'+namepart):raise ValueError('Complete captured RMA source-oracle fields differ')
    comparisons.append(dict(name=name,**case['counters'],canonical_sam_and_rma_equal=True))
   restored=0
   for n,p in files.items():
    if n.startswith('bundle/'):
     restored+=p['bytes']
     if restored>MAX_TOTAL:raise ValueError('Complete restored bundle exceeds capacity')
     target=out/n;target.parent.mkdir(parents=True,exist_ok=True)
     with z.open('objects/'+p['sha256']) as source,target.open('xb') as dest:shutil.copyfileobj(source,dest,1048576)
  env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']};command=[sys.executable,str(ROOT/'tools/literature/build_malt_index_taxonomy.py'),'--java-bin',str(a.java_bin.resolve()),'--out',str(out/'build')]
  r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=180,env=env);(out/'build.stdout').write_bytes(r.stdout);(out/'build.stderr').write_bytes(r.stderr);save(out/'build.process.json',dict(command=command,exit_code=r.returncode))
  if r.returncode==3:raise FileNotFoundError('Required compiler/environment unavailable; inspect build diagnostics')
  if r.returncode:raise ValueError('Native rebuild failed')
  binary=a.java_bin.resolve()/('java.exe' if os.name=='nt' else 'java');command=[str(binary),'-Xmx3g','-jar',str(out/'build/malt-index-taxonomy-development.jar'),'--verify',str(out/'bundle')]
  r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=180,env=env);(out/'replay.stdout').write_bytes(r.stdout);(out/'replay.stderr').write_bytes(r.stderr);save(out/'replay.process.json',dict(command=command,exit_code=r.returncode))
  if r.returncode:raise ValueError('Complete native saved replay failed; retain full diagnostics')
  audit=json.loads((out/'bundle/audit.json').read_bytes())['scientific']
  if audit['references']!=3 or audit['reference_bases']!=52244 or audit['taxon_by_accession']!={'AB177354.1':8574,'NC_007596.2':37349,'NC_012920.1':9606}:raise ValueError('Full native mapping facts differ')
  if pin(archive)!=validation['archive'] or any(pin(ROOT/n)!=p for n,p in before.items()):raise ValueError('Evidence or source changed during replay')
  state.update(status='PASS_COMPLETE_NATIVE_MALT_INDEX_SAVED_REPLAY_ONLY',archive=validation['archive'],evidence_files=len(files),object_files=len(objects),restored_bundle_bytes=restored,source_oracle_comparisons=comparisons,native_references=3,native_reference_bases=52244,complete_queries=sum(x['queries'] for x in comparisons),aligned_queries=sum(x['aligned_queries'] for x in comparisons),alignments=sum(x['alignments'] for x in comparisons),upstream_program_required_for_saved_replay=False,paper_original_index_path_qualified=False,producer_authenticated=False)
  save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Saved native indexing replay only\n\nNo original alignment was refitted. Captured oracle equality is checked as evidence; the unsigned producer is not authenticated. Whole MALT/HOPS/paper reproduction remains incomplete.\n\n'+ACTION+'\n',encoding='utf-8',newline='\n');print(state['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;state.update(status='SKIP3_NATIVE_INDEX_REPLAY_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_NATIVE_INDEX_REPLAY',cause=repr(e),traceback=traceback.format_exc(),dependent_analysis_skipped=True,required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(e)+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
  print(state['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Do not disable complete-input evidence checks')
 raise SystemExit(main())
