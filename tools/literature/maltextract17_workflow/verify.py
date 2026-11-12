#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Rebuild and replay complete original MaltExtract workflow evidence; no original fitting."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,lzma,os,re,shutil,subprocess,sys,traceback,zipfile

ROOT=Path(__file__).resolve().parents[3]
DATA=ROOT/'docs/literature/maltextract17-workflow-development'
CASES=['source-default-1','complete-default-1','complete-defanc-2','complete-defanc-3','special-names-1','sampling-stock-1','sampling-complete-1']
REFUSALS=['false-tool-credit','insufficient-complete-reads','false-selected-taxa','changed-original-command','hidden-stock-sampling','false-damage-denominator','missing-original-output','contradictory-launch']
READER=dict(bytes=10770756,sha256='8a5be1b5ae43f329995a4647466a7ca3aa698d180cef94dec974ffe91bb3a2ca')
ACTION='Required action: retain all diagnostics and failed replay outputs. Use the matching complete archive and source, a permitted JDK21 or JDK25, the exact HOPS0.35 MaltExtract1.7 reader, and a NEW output directory with at least8GiB free space. Obtain the reader with tools/literature/maltextract17/setup.py only where allowed. If a prerequisite/download/install is unavailable or forbidden, skip dependent replay/analysis. Resolve the named integrity, format or complete-capacity issue without silently repairing evidence. Replay checks stored consistency and software-control oracles, not producer authenticity or biological findings. It does not repeat original Java8 execution or independently recalculate arbitrary upstream damage/coverage statistics.'

def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())

def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')

def member(name):
 p=PurePosixPath(name)
 if not name or p.is_absolute() or '\\' in name or ':' in name or any(ord(c)<32 or ord(c)==127 for c in name) or any(x in ['', '.', '..'] for x in name.split('/')):raise ValueError('Unsafe portable evidence member')
 return name

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_ORIGINAL_MALTEXTRACT17_WORKFLOW_REPLAY',original_fitting_repeated=False,arbitrary_original_statistics_recomputed=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;runtime=a.runtime.resolve()
  if not (runtime/'MaltExtract1.7.jar').is_file() or pin(runtime/'MaltExtract1.7.jar')!=READER:raise OSError('Exact permitted original1.7 reader unavailable; skip dependent replay')
  if shutil.disk_usage(out).free<8*1024**3:raise OSError('Reserve at least8GiB free for complete evidence restoration and replay')
  validation=json.loads((DATA/'VALIDATION.json').read_bytes());archive=DATA/'controls.zip.xz'
  if pin(archive)!=validation['archive']:raise ValueError('Complete original-workflow archive differs')
  for name,value in validation['sources'].items():
   if pin(ROOT/member(name))!=value:raise ValueError('Current replay source differs: '+name)
  container=out/'controls.zip';expanded=0
  with lzma.open(archive,'rb') as source,container.open('xb') as target:
   for block in iter(lambda:source.read(1048576),b''):
    expanded+=len(block)
    if expanded>2*1024**3:raise ValueError('Complete evidence container exceeds2GiB capacity')
    target.write(block)
  evidence=out/'evidence';evidence.mkdir();restored=0
  with zipfile.ZipFile(container) as z:
   names=set()
   for info in z.infolist():
    name=member(info.filename)
    if name in names or info.is_dir() or info.file_size>268435456 or (info.external_attr>>16)&0o170000 not in [0,0o100000]:raise ValueError('Duplicate, nonregular or oversized evidence object')
    names.add(name)
   if len(names)>20000 or 'MANIFEST.json' not in names or z.getinfo('MANIFEST.json').file_size>67108864:raise ValueError('Invalid complete manifest capacity')
   manifest=json.loads(z.read('MANIFEST.json'))
   if manifest.get('schema')!='MALTEXTRACT17_ORIGINAL_WORKFLOW_EVIDENCE_V1':raise ValueError('Unsupported original-workflow evidence schema')
   files={member(name):value for name,value in manifest['files'].items()};objects={}
   if len(files)>30000:raise ValueError('Complete logical evidence member capacity exceeded')
   for value in files.values():
    if set(value)!=set(['bytes','sha256']) or type(value['bytes']) is not int or not 0<=value['bytes']<=268435456 or not re.fullmatch('[0-9a-f]{64}',value['sha256']):raise ValueError('Invalid evidence fingerprint')
    if value['sha256'] in objects and objects[value['sha256']]!=value:raise ValueError('Conflicting shared evidence object')
    objects[value['sha256']]=value
   if names!=set(['MANIFEST.json'])|{'objects/'+digest for digest in objects} or sum(v['bytes'] for v in objects.values())>2*1024**3:raise ValueError('Complete object population differs or exceeds capacity')
   for digest,value in objects.items():
    h=hashlib.sha256();n=0
    with z.open('objects/'+digest) as f:
     for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
    if dict(bytes=n,sha256=h.hexdigest())!=value:raise ValueError('Complete evidence object differs')
   for name,value in files.items():
    restored+=value['bytes']
    if restored>8*1024**3:raise ValueError('Complete restored evidence exceeds8GiB capacity')
    target=evidence/name;target.parent.mkdir(parents=True,exist_ok=True)
    with z.open('objects/'+value['sha256']) as source,target.open('xb') as dest:shutil.copyfileobj(source,dest,1048576)
    if pin(target)!=value:raise ValueError('Restored evidence member differs: '+name)
  calls=[]
  def run(label,command,expected=0):
   command=list(map(str,command));r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=600);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);receipt=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);save(out/(label+'.process.json'),receipt);calls.append(receipt)
   if r.returncode!=expected:raise ValueError('Complete replay failed at '+label+'; retain diagnostics')
   if expected==2 and (b'NO RESULT ADMITTED' not in r.stderr or b'Required action:' not in r.stderr):raise ValueError('Refusal lacks prominent recovery: '+label)
  build=out/'build';run('build',[sys.executable,ROOT/'tools/literature/maltextract17_workflow/build.py','--java-bin',a.java_bin,'--out',build]);suffix='.exe' if os.name=='nt' else '';java=a.java_bin/('java'+suffix);javac=a.java_bin/('javac'+suffix);artifact=build/'maltextract17-workflow-development.jar'
  classes=out/'probes';classes.mkdir();module=ROOT/'tools/literature/maltextract17_workflow/java';probes=['MaltExtractNamesNativeProbe','MaltExtractSelectionNativeProbe']
  run('probe-javac',[javac,'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',build/'classes','-d',classes,*[module/(n+'.java') for n in probes]])
  for label,probe in [('names','MaltExtractNamesNativeProbe'),('selection','MaltExtractSelectionNativeProbe')]:
   target=out/(label+'.tsv');run(label,[java,'-Xmx3g','-cp',str(build/'classes')+os.pathsep+str(classes),probe,evidence/'oracles'/label/'fixture/cases.tsv',target])
   if target.read_bytes()!=(evidence/'oracles'/label/'source.tsv').read_bytes():raise ValueError('Complete original name/descendant oracle differs: '+label)
  comparisons=[]
  for case in CASES:
   bundle=evidence/'controls'/case/'bundle';run('verify-'+case,[java,'-Xmx3g','-jar',artifact,'--verify',bundle,'--runtime',runtime])
   gold={'source-default-1':'default-upstream','complete-default-1':'default-complete','complete-defanc-2':'defanc-complete','complete-defanc-3':'defanc-complete'}.get(case)
   if gold:
    original=evidence/'original-oracles'/gold;index=json.loads((bundle/'original-files.json').read_bytes());expected={p.relative_to(original).as_posix() for p in original.rglob('*') if p.is_file() and p.relative_to(original).parts[0] in ['default','ancient']};actual={n for n in index if n.split('/')[0] in ['default','ancient']}
    if actual!=expected:raise ValueError('Complete scientific output population differs: '+case)
    for name in sorted(expected):
     if (bundle/index[name]['member']).read_bytes()!=(original/name).read_bytes():raise ValueError('Independent original statistical/FASTA/BLAST bytes differ: '+case+'/'+name)
    comparisons.append(dict(case=case,complete_original_scientific_files=len(expected)))
  for case in REFUSALS:run('refuse-'+case,[java,'-Xmx3g','-jar',artifact,'--verify',evidence/'damaged'/case/'bundle','--runtime',runtime],2)
  for name,value in files.items():
   if pin(evidence/name)!=value:raise ValueError('Read-only replay changed complete evidence: '+name)
  if pin(runtime/'MaltExtract1.7.jar')!=READER:raise ValueError('Exact reader changed during replay')
  for name,value in validation['sources'].items():
   if pin(ROOT/name)!=value:raise ValueError('Current replay sources changed')
  state.update(status='PASS_COMPLETE_ORIGINAL_MALTEXTRACT17_WORKFLOW_SAVED_REPLAY',restored_members=len(files),restored_bytes=restored,unique_objects=len(objects),original_name_cases=16,original_descendant_cases=76027,complete_workflow_bundles=len(CASES),metadata_refusals=len(REFUSALS),original_scientific_comparisons=comparisons,commands=calls,new_tool_credit=0);save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Saved software-control replay only\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;state.update(status='SKIP3_REPLAY_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_MALTEXTRACT17_WORKFLOW_REPLAY',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,dependent_analysis_skipped=True)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete replay checks')
 raise SystemExit(main())
