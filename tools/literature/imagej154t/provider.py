#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the complete unmodified ImageJ1.54t core from fixed original source into NEW paths."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,signal,subprocess,sys,tarfile,time,traceback
HERE=Path(__file__).resolve().parent
ACTION='Required action: provide permitted JDK21/25, explicit --allow-build and NEW output. Supply the complete pristine --source-root or explicit --allow-download for the fixed upstream Git commit. No system installation is performed. Missing/forbidden compiler/source/build/download prerequisites SKIP3 skip dependent analysis. Retain FAIL2 diagnostics and correct source/build contradictions without patching original scientific code. This builds original ImageJ1.54t core and its original bundled resources, not a recovered Fiji distribution or external plugins. It performs no image analysis. Original Ant bytecode1.6 is unsupported by these modern compilers; the unmodified source is compiled with --release8 and debug information instead, and the difference is recorded.'
class Unavailable(OSError):pass
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);n+=len(raw)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def copy_source(source,dest,expected):
 if source.is_symlink() or not source.is_dir():raise Unavailable('Complete pristine source root is unavailable')
 files={}
 for p in source.rglob('*'):
  if p.is_symlink():raise ValueError('Source links are not pristine original files')
  if p.is_file():
   if not p.resolve().is_relative_to(source):raise ValueError('Source file escapes its root')
   files[p.relative_to(source).as_posix()]=p
 if set(files)!=set(expected):raise ValueError('Complete source population differs; use pristine exported source, not a Git/compiled working tree')
 dest.mkdir()
 for name,wanted in expected.items():
  p=files[name]
  if pin(p)!=wanted:raise ValueError('Original source fingerprint differs: '+name)
  target=dest/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--java-bin',required=True,type=Path);parser.add_argument('--source-root',type=Path);parser.add_argument('--allow-download',action='store_true');parser.add_argument('--allow-build',action='store_true');parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  for protected in [a.java_bin,a.source_root]:
   if protected is not None:
    p=protected.resolve()
    if out.is_relative_to(p) or p.is_relative_to(out):raise ValueError('Output overlaps a protected compiler/source path; no output created')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_build:raise Unavailable('Build permission absent; no original compiler or analysis launched')
  if not a.source_root and not a.allow_download:raise Unavailable('Supply complete local --source-root or explicit --allow-download')
  profile=json.loads((HERE/'source-profile.json').read_bytes());expected=profile['source_members'];suffix='.exe' if os.name=='nt' else '';jdk=a.java_bin.resolve();env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def run(label,args,seconds=240,prerequisite=False):
   command=list(map(str,args));start=time.time();timed_out=False
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    p=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=os.name!='nt');save(out/(label+'.live.json'),dict(command=command,pid=p.pid,started_unix=start))
    try:code=p.wait(timeout=seconds)
    except subprocess.TimeoutExpired:
     timed_out=True
     if p.poll() is None:
      if os.name=='nt':
       stopped=subprocess.run(['taskkill','/PID',str(p.pid),'/T','/F'],stdin=subprocess.DEVNULL,capture_output=True,timeout=30);save(out/(label+'.cleanup.json'),dict(owned_root_pid=p.pid,exit_code=stopped.returncode,stdout=stopped.stdout.decode(errors='replace'),stderr=stopped.stderr.decode(errors='replace')))
      else:os.killpg(p.pid,signal.SIGKILL)
     if p.poll() is None:p.kill()
     code=p.wait(timeout=30)
   row=dict(label=label,command=command,exit_code=code,timed_out=timed_out,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if timed_out:raise Unavailable('Required source download timed out: '+label) if prerequisite else ValueError('Original build/readiness timed out at '+label+'; owned process stopped, diagnostics retained')
   if code:raise Unavailable('Required download failed: '+label) if prerequisite else ValueError('Original source build/readiness failed at '+label+'; inspect retained diagnostics')
   return ((out/(label+'.stdout')).read_bytes()+(out/(label+'.stderr')).read_bytes()).decode(errors='replace')
  versions={n:run(n+'-version',[jdk/(n+suffix),'--version']) for n in ['java','javac','jar']}
  if any(not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',text) for text in versions.values()):raise Unavailable('Matching permitted JDK21 or JDK25 required')
  if a.source_root:copy_source(a.source_root.resolve(),out/'source',expected)
  else:
   git=shutil.which('git')
   if not git:raise Unavailable('Permitted Git is unavailable')
   run('source-clone',[git,'clone','--no-checkout','--filter=blob:none','--single-branch',profile['repository'],out/'git-source'],prerequisite=True)
   run('source-archive',[git,'-C',out/'git-source','archive','--format=tar','--output',out/'source.tar',profile['source_commit']])
   if pin(out/'source.tar')!=profile['source_archive']:raise ValueError('Fixed original Git archive differs')
   source=out/'source-download';source.mkdir()
   with tarfile.open(out/'source.tar') as tar:
    members=[m for m in tar.getmembers() if not m.isdir()]
    if len(members)!=len(expected) or {m.name for m in members}!=set(expected) or len({m.name for m in members})!=len(members):raise ValueError('Complete source archive population differs')
    for m in members:
     if not m.isfile() or m.size!=expected[m.name]['bytes'] or m.size>16777216:raise ValueError('Unsafe original source member')
     p=source/m.name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(tar.extractfile(m).read(16777217))
   copy_source(source,out/'source',expected)
  classes=out/'classes';classes.mkdir();java=sorted((out/'source/ij').rglob('*.java'))
  if not java:raise ValueError('Original ij source tree is empty')
  argfile=out/'original-java-sources.txt';argfile.write_text(''.join('"'+str(p).replace('\\','\\\\').replace('"','\\"')+'"\n' for p in java),encoding='utf8',newline='\n')
  run('original-javac',[jdk/('javac'+suffix),'--release','8','-g','-encoding','UTF-8','-proc:none','-d',classes,'@'+str(argfile)])
  for src,dest in [('IJ_Props.txt','IJ_Props.txt'),('images/microscope.gif','microscope.gif'),('images/about.jpg','about.jpg'),('plugins/MacAdapter.class','ij/plugin/MacAdapter.class'),('plugins/MacAdapter9.class','ij/plugin/MacAdapter9.class')]:
   target=classes/dest;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(out/'source'/src,target)
  shutil.copytree(out/'source/macros',classes/'macros');artifact=out/'ij-1.54t.jar';run('original-jar',[jdk/('jar'+suffix),'--create','--file',artifact,'--manifest',out/'source/MANIFEST.MF','-C',classes,'.'])
  readiness=out/'readiness-classes';readiness.mkdir();shutil.copyfile(HERE/'ImageJReadiness.java',out/'ImageJReadiness.java');run('readiness-javac',[jdk/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',artifact,'-d',readiness,out/'ImageJReadiness.java'])
  cp=str(readiness)+os.pathsep+str(artifact);text=run('readiness',[jdk/('java'+suffix),'-Djava.awt.headless=true','-cp',cp,'ImageJReadiness'])
  if 'READY_ORIGINAL_IMAGEJ154T_NOT_ANALYSIS version=1.54t' not in text:raise ValueError('Original runtime version readiness differs')
  for n,wanted in expected.items():
   if pin(out/'source'/n)!=wanted or (a.source_root and pin(a.source_root.resolve()/n)!=wanted):raise ValueError('Pristine source changed during build: '+n)
  shutil.copyfile(HERE/'source-profile.json',out/'source-profile.json');result=dict(status='READY_PRIVATE_ORIGINAL_IMAGEJ154T_NOT_ANALYSIS',version='1.54t',source_commit=profile['source_commit'],source_members=len(expected),compiled_original_java_files=len(java),original_jar=str(artifact),artifact=pin(artifact),source_profile=pin(out/'source-profile.json'),versions=versions,original_ant_bytecode_target='1.6',compiled_source_target='8',original_source_patched=False,original_mac_adapter_classes_copied=True,source_root=str(out/'source'),calls=calls,Fiji_distribution_or_plugins_recovered=False,image_analysis_executed=False,analysis_admitted=False,new_tool_credit=0,whole_goal_complete=False)
  save(out/'RESULT.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;value=dict(status='SKIP3_IMAGEJ_PROVIDER_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_IMAGEJ154T_PROVIDER',cause=repr(e),traceback=traceback.format_exc(),calls=calls,image_analysis_executed=False,analysis_admitted=False,required_action=ACTION)
  if owned:save(out/'FAILURE.json',value);(out/'WARNINGS_FAILURE.md').write_text(value['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(value['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
