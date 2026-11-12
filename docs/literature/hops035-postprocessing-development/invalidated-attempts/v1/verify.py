#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Rebuild/replay complete HOPS postprocessing controls with fresh original RData decoding."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,lzma,os,shutil,subprocess,sys,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3]
DATA=ROOT/'docs/literature/hops035-postprocessing-development'
POSITIVES=['complete10001-2','multi-sample-1','hyphen-jdk25-1']
REFUSALS=['false-tool-credit','false-native-class','changed-scientific-double','NA-relabeled-NaN','corrupted-rdata','missing-profile-pdf','changed-tsv-cell','changed-original-command','contradictory-launch']
READER=dict(bytes=10770756,sha256='8a5be1b5ae43f329995a4647466a7ca3aa698d180cef94dec974ffe91bb3a2ca')
ACTION='Required action: retain all failed replay outputs and diagnostics. Use complete matching source/evidence, a permitted Linux x86_64 JDK21 or JDK25, the exact external MaltExtract1.7 reader, an intact public R4.0.5 provider from setup.py and a NEW native-Linux output directory with at least8GiB free. Install/download only where permitted; missing or forbidden prerequisites skip dependent replay/analysis. Resolve the named integrity, format or complete-capacity issue without repairing evidence. Replay freshly decodes RData and checks complete native scientific fields; it does not repeat original fitting/plotting, authenticate a producer, establish biological validity or qualify whole HOPS/the250-paper goal.'
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
   if expanded>2*1024**3:raise ValueError('Complete object container exceeds2GiB capacity')
   target.write(b)
 evidence=out/'evidence';evidence.mkdir()
 with zipfile.ZipFile(container) as z:
  names=set()
  for info in z.infolist():
   name=member(info.filename)
   if name in names or info.is_dir() or info.file_size>268435456 or (info.external_attr>>16)&0o170000 not in [0,0o100000]:raise ValueError('Duplicate, nonregular or oversized evidence object')
   names.add(name)
  if len(names)>30000 or 'MANIFEST.json' not in names or z.getinfo('MANIFEST.json').file_size>67108864:raise ValueError('Complete evidence manifest capacity differs')
  manifest=json.loads(z.read('MANIFEST.json'))
  if manifest.get('schema')!='HOPS035_POSTPROCESSING_COMPLETE_EVIDENCE_V1':raise ValueError('Unsupported complete evidence schema')
  files={member(k):v for k,v in manifest['files'].items()};objects={};restored=0
  if len(files)>30000:raise ValueError('Complete logical file capacity exceeded')
  for value in files.values():
   if set(value)!=set(['bytes','sha256','mode']) or type(value['bytes']) is not int or not 0<=value['bytes']<=268435456 or type(value['mode']) is not int or not 0<=value['mode']<=0o777 or len(value['sha256'])!=64 or any(x not in '0123456789abcdef' for x in value['sha256']):raise ValueError('Invalid complete evidence identity/mode')
   obj={k:value[k] for k in ['bytes','sha256']}
   if obj['sha256'] in objects and objects[obj['sha256']]!=obj:raise ValueError('Conflicting evidence object')
   objects[obj['sha256']]=obj
  if names!=set(['MANIFEST.json'])|{'objects/'+n for n in objects} or sum(v['bytes'] for v in objects.values())>2*1024**3:raise ValueError('Complete evidence object population differs')
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
   target.chmod(value['mode'])
   if pin(target)!={k:value[k] for k in ['bytes','sha256']}:raise ValueError('Restored complete evidence member differs')
 return evidence,files,len(objects),restored
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for name in ['java-bin','reader-runtime','post-runtime','out']:p.add_argument('--'+name,required=True,type=Path)
 a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_HOPS035_POSTPROCESSING_REPLAY',original_fitting_repeated=False,original_plotting_repeated=False,new_tool_credit=0,whole_hops_qualified=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if sys.platform!='linux' or os.uname().machine not in ['x86_64','amd64']:raise OSError('Fresh RData decoding requires a permitted native Linux x86_64 runtime')
  if shutil.disk_usage(out).free<8*1024**3:raise OSError('Reserve at least8GiB free for complete evidence restoration and verification')
  reader=a.reader_runtime.resolve();provider=a.post_runtime.resolve()
  if not (reader/'MaltExtract1.7.jar').is_file() or pin(reader/'MaltExtract1.7.jar')!=READER:raise OSError('Exact external MaltExtract1.7 reader unavailable')
  if not (provider/'RUNTIME_READY.json').is_file():raise OSError('Public R4.0.5 provider unavailable; use setup.py only where permitted')
  validation=json.loads((DATA/'VALIDATION.json').read_bytes());archive=DATA/'controls.zip.xz'
  if validation['status']!='PASS_ORIGINAL_HOPS035_POSTPROCESSING_COMPLETE_OUTPUT_CONTROLS' or validation['new_tool_credit']!=0 or validation['whole_hops_qualified'] or validation['whole_goal_complete']:raise ValueError('Unsupported postprocessing qualification scope')
  if pin(archive)!=validation['archive']:raise ValueError('Complete postprocessing evidence archive differs')
  for name,value in validation['sources'].items():
   if pin(ROOT/member(name))!=value:raise ValueError('Current replay source differs: '+name)
  evidence,files,objects,restored=restore(archive,out);calls=[]
  def run(label,command,expected=0):
   command=list(map(str,command));r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=600);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);save(out/(label+'.process.json'),row);calls.append(row)
   if r.returncode==3 and expected!=3:raise OSError('Required replay prerequisite unavailable at '+label+'; inspect retained diagnostics and skip dependent analysis')
   if r.returncode!=expected:raise ValueError('Complete replay failed at '+label+'; inspect retained diagnostics')
   if expected==2 and (b'NO RESULT ADMITTED' not in r.stderr or b'Required action:' not in r.stderr):raise ValueError('Refusal lacks prominent recovery: '+label)
  build=out/'build';run('build',[sys.executable,ROOT/'tools/literature/hops035_workflow/build.py','--java-bin',a.java_bin,'--out',build]);java=a.java_bin/'java';javac=a.java_bin/'javac';artifact=build/'hops035-postprocessing-development.jar'
  # Remaining replay checks follow below; no original fitting/plotting is run.
  return finish_replay(out,evidence,files,objects,restored,validation,reader,provider,java,javac,artifact,build,run,calls,state)
 except Exception as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;state.update(status='SKIP3_REPLAY_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_HOPS035_POSTPROCESSING_REPLAY',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,dependent_analysis_skipped=True)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code

def finish_replay(out,evidence,files,objects,restored,validation,reader,provider,java,javac,artifact,build,run,calls,state):
 native=out/'native-reference';native.mkdir();expanded=0
 with zipfile.ZipFile(ROOT/'src/test/resources/org/jlinalg/metagenomics/hops-evidence/reference.zip') as z:
  seen=set()
  for info in z.infolist():
   name=member(info.filename.rstrip('/'))
   if name in seen:raise ValueError('Duplicate original native-control member')
   seen.add(name)
   if info.is_dir():continue
   expanded+=info.file_size
   if info.file_size>16777216 or expanded>134217728:raise ValueError('Complete native-control capacity exceeded')
   target=native/name;target.parent.mkdir(parents=True,exist_ok=True)
   with z.open(info) as source,target.open('xb') as dest:shutil.copyfileobj(source,dest,1048576)
 helper=ROOT/'tools/literature/java/HopsEvidenceControl.java';classes=out/'native-helper';classes.mkdir()
 run('native-javac',[javac,'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-sourcepath','','-cp',build/'classes','-d',classes,helper])
 config=out/'native-controls.json';save(config,dict(groups=[dict(label=name,data=str(native/name),expected=str(native/(name+'-expected'))) for name in ['source','boundaries','metadata']],classification_cases=str(native/'classification/cases.tsv'),classification_expected=str(native/'classification/expected.tsv')))
 run('native-original-controls',[java,'-Xmx1g','-cp',str(build/'classes')+os.pathsep+str(classes),'org.jlinalg.cli.HopsEvidenceControl',config,out/'native-result'])
 native_result=json.loads((out/'native-result/COMPLETED.json').read_bytes())
 if (native_result['sample_node_pairs'],native_result['classification_boundaries'],native_result['refusals'])!=(2901,420,7):raise ValueError('Complete original native-control population differs')
 (out/'calls').mkdir();pairs=0
 for case in POSITIVES:
  bundle=evidence/'controls'/case/'bundle';target=out/'calls'/case
  run('verify-'+case,[java,'-Xmx3g','-jar',artifact,'--verify',bundle,'--reader-runtime',reader,'--post-runtime',provider,'--out',target])
  result=json.loads((target/'VERIFIED.json').read_bytes())
  if result['status']!='pass-complete-original-hops-postprocessing-saved-verification' or result['rdata_freshly_decoded'] is not True or result['original_fitting_repeated'] or result['original_plotting_repeated']:raise ValueError('Saved RData verification scope differs')
  pairs+=result['pairs'];print('Complete HOPS postprocessing saved control',case,'passed',flush=True)
 if pairs!=22:raise ValueError('Complete captured sample/taxon pair population differs')
 for case in REFUSALS:
  run('refuse-'+case,[java,'-Xmx3g','-jar',artifact,'--verify',evidence/'refusals'/case/'bundle','--reader-runtime',reader,'--post-runtime',provider,'--out',out/'calls'/('refuse-'+case)],2)
 for case in ['initial-provider-link','original-default','empty-candidates']:
  failed=evidence/'failed'/case
  if not (failed/'FAILURE.json').is_file() or (failed/'bundle/COMPONENT_COMPLETED.json').exists():raise ValueError('Original failed attempt was lost or relabeled complete')
 for case,pattern in [('original-default','undefined columns selected'),('empty-candidates',"increasing 'x' and 'y' values expected")]:
  if pattern not in (evidence/'failed'/case/'post-process/process.stderr').read_text():raise ValueError('Original retained R failure evidence differs')
 original=json.loads((evidence/'original/workspace-exports/COMPLETED.json').read_bytes())
 if original['status']!='PASS_THREE_INDEPENDENT_ORIGINAL_RDATA_SCIENTIFIC_FIELDS_IDENTICAL':raise ValueError('Independent original reference comparison is incomplete')
 decoded=[json.loads((evidence/'original/workspace-exports'/case/'scientific.json').read_bytes()) for case in ['reference','public','hops']]
 if not decoded[0]==decoded[1]==decoded[2]:raise ValueError('Complete independent original RData fields differ')
 for name,value in files.items():
  if pin(evidence/name)!={k:value[k] for k in ['bytes','sha256']} or (evidence/name).stat().st_mode&0o777!=value['mode']:raise ValueError('Read-only replay changed retained evidence: '+name)
 for name,value in validation['sources'].items():
  if pin(ROOT/name)!=value:raise ValueError('Current replay sources changed')
 state.update(status='PASS_COMPLETE_HOPS035_POSTPROCESSING_SAVED_REPLAY_WITH_FRESH_RDATA_DECODING',restored_members=len(files),restored_bytes=restored,unique_objects=objects,complete_workflow_bundles=len(POSITIVES),sample_taxon_pairs=pairs,source_statistic_cells=14*pairs,metadata_refusals=len(REFUSALS),native_source_pairs=2901,native_classification_boundaries=420,native_refusals=7,original_failed_attempts_retained=3,independent_original_RData_comparisons=3,commands=calls)
 save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Saved software-control replay only\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],flush=True);return 0

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source/evidence checks')
 raise SystemExit(main())
