#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build all unchanged CorrectBleach2.1.0 source with its declared original dependency profile privately."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,signal,subprocess,tarfile,time,traceback,urllib.request
HERE=Path(__file__).resolve().parent
ACTION='Required action: supply permitted JDK21/25, explicit --allow-build and NEW output. Supply complete pristine --source-root and complete --dependency-root, or explicitly permit fixed-source/artifact downloads with --allow-download. This performs a private build, not a system installation or image analysis. Missing/forbidden runtime/build/download/source prerequisites SKIP3 skip dependent analysis. FAIL2 retains diagnostics; correct the named source/dependency cause and retry NEW paths without patching original code. P026 reports neither the plugin version nor correction method: select2.1.0 explicitly and never infer a paper setting. Original GPL3+ release notices, older GPL2 prose and all dependency notices remain retained. Histogram matching is for segmentation/display and is not appropriate for quantitative intensity analysis.'
class Unavailable(OSError):pass
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',type=Path,required=True);p.add_argument('--source-root',type=Path);p.add_argument('--dependency-root',type=Path);p.add_argument('--allow-build',action='store_true');p.add_argument('--allow-download',action='store_true');p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  for path in [a.java_bin,a.source_root,a.dependency_root]:
   if path:
    protected=path.resolve()
    if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected runtime/source/dependencies')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_build:raise Unavailable('Build permission absent; no dependent compiler/analysis launched')
  if (not a.source_root or not a.dependency_root) and not a.allow_download:raise Unavailable('Complete local source/dependencies or explicit download permission required')
  profile=json.loads((HERE/'source-profile.json').read_bytes());env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']};jdk=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else ''
  def run(label,args,prerequisite=False):
   command=list(map(str,args));start=time.time();timed_out=False
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    process=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=os.name!='nt');save(out/(label+'.live.json'),dict(command=command,pid=process.pid,started_unix=start))
    try:code=process.wait(timeout=240)
    except subprocess.TimeoutExpired:
     timed_out=True
     if process.poll() is None:
      if os.name=='nt':subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],capture_output=True,timeout=30)
      else:os.killpg(process.pid,signal.SIGKILL)
     if process.poll() is None:process.kill()
     code=process.wait(timeout=30)
   row=dict(label=label,command=command,exit_code=code,timed_out=timed_out,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if code or timed_out:raise Unavailable('Required permitted source download failed: '+label) if prerequisite else ValueError('Original provider failed at '+label+'; retain diagnostics')
   return ((out/(label+'.stdout')).read_bytes()+(out/(label+'.stderr')).read_bytes()).decode(errors='replace')
  versions={n:run(n+'-version',[jdk/(n+suffix),'--version']) for n in ['java','javac','jar']}
  if any(not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',v) for v in versions.values()):raise Unavailable('Matching permitted JDK21/25 required')
  def source_files(source):
   if source.is_symlink() or not source.is_dir():raise Unavailable('Complete pristine original source root unavailable')
   members={p.relative_to(source).as_posix():p for p in source.rglob('*') if p.is_file()}
   if set(members)!=set(profile['source_members']) or any(p.is_symlink() for p in source.rglob('*')):raise ValueError('Original complete source population/link differs')
   for n,wanted in profile['source_members'].items():
    if pin(members[n])!=wanted:raise ValueError('Original source differs: '+n)
   return members
  if a.source_root:source=a.source_root.resolve()
  else:
   git=shutil.which('git')
   if not git:raise Unavailable('Permitted Git unavailable')
   run('source-clone',[git,'clone','--no-checkout','--filter=blob:none',profile['repository'],out/'git-source'],True);run('source-archive',[git,'-C',out/'git-source','archive','--format=tar','--output',out/'source.tar',profile['source_commit']])
   if pin(out/'source.tar')!=profile['source_archive']:raise ValueError('Fixed complete original Git archive differs')
   source=out/'source-download';source.mkdir()
   with tarfile.open(out/'source.tar') as tar:
    members=[m for m in tar.getmembers() if not m.isdir()]
    if len(members)!=len(profile['source_members']) or {m.name for m in members}!=set(profile['source_members']):raise ValueError('Complete original archive population differs')
    for m in members:
     if not m.isfile() or m.name.startswith('/') or '\\' in m.name or any(x in ['','.','..'] for x in m.name.split('/')):raise ValueError('Unsafe original archive member')
     target=source/m.name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(tar.extractfile(m).read())
  members=source_files(source);captured=out/'source';captured.mkdir()
  for n,path in members.items():target=captured/n;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target)
  dependencies=out/'dependencies';dependencies.mkdir()
  for artifact in profile['dependency_artifacts']:
   target=dependencies/artifact['path'];wanted={k:artifact[k] for k in ['bytes','sha256']}
   if a.dependency_root:
    original=a.dependency_root.resolve()/artifact['path']
    if not original.is_file() or original.is_symlink():raise Unavailable('Missing complete declared dependency artifact: '+artifact['path'])
    if pin(original)!=wanted:raise ValueError('Original dependency artifact differs: '+artifact['path'])
    shutil.copyfile(original,target)
   else:
    with urllib.request.urlopen(urllib.request.Request(artifact['url'],headers={'User-Agent':'OmicsMatic-private-original-CorrectBleach/1'}),timeout=60) as response:raw=response.read(artifact['bytes']+1)
    target.write_bytes(raw)
   if pin(target)!=wanted:raise ValueError('Complete original artifact differs: '+artifact['path'])
  lib=out/'lib';lib.mkdir();runtime={}
  for name in ['ij-1.53t.jar','scijava-common-2.89.0.jar','parsington-3.0.0.jar','eventbus-1.4.jar']:
   shutil.copyfile(dependencies/name,lib/name);runtime[name]=pin(lib/name)
  classes=out/'classes';classes.mkdir();classpath=os.pathsep.join(str(lib/n) for n in runtime);java=sorted((captured/'src/main/java').rglob('*.java'));argfile=out/'original-java-sources.txt';argfile.write_text('\n'.join('"'+str(p).replace('\\','\\\\').replace('"','\\"')+'"' for p in java)+'\n',encoding='utf8',newline='\n');run('original-javac',[jdk/('javac'+suffix),'--release','8','-g','-encoding','UTF-8','-proc:none','-cp',classpath,'-d',classes,'@'+str(argfile)])
  for p in (captured/'src/main/resources').rglob('*'):
   if p.is_file():target=classes/p.relative_to(captured/'src/main/resources');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
  meta=classes/'META-INF/maven/sc.fiji/CorrectBleach_';meta.mkdir(parents=True);shutil.copyfile(captured/'pom.xml',meta/'pom.xml');(meta/'pom.properties').write_text('groupId=sc.fiji\nartifactId=CorrectBleach_\nversion=2.1.0\n',encoding='ascii',newline='\n');manifest=out/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nImplementation-Version: 2.1.0\n\n',encoding='ascii',newline='\n');plugin=lib/'CorrectBleach_-2.1.0.jar';run('original-jar',[jdk/('jar'+suffix),'--create','--file',plugin,'--manifest',manifest,'-C',classes,'.']);runtime[plugin.name]=pin(plugin)
  readiness=out/'readiness-classes';readiness.mkdir();cp=os.pathsep.join(str(lib/n) for n in runtime);run('readiness-javac',[jdk/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-proc:none','-cp',cp,'-d',readiness,HERE/'Readiness.java']);run('readiness',[jdk/('java'+suffix),'-Djava.awt.headless=true','-cp',str(readiness)+os.pathsep+cp,'Readiness'])
  source_files(source);source_files(captured)
  for artifact in profile['dependency_artifacts']:
   if pin(dependencies/artifact['path'])!={k:artifact[k] for k in ['bytes','sha256']}:raise ValueError('Complete artifact changed during provider build')
  shutil.copyfile(captured/'LICENSE.txt',out/'LICENSE-upstream-release-GPL3.txt');shutil.copyfile(captured/'README.md',out/'README-upstream-original.md');save(out/'READY.json',dict(status='READY_PRIVATE_COMPLETE_ORIGINAL_CORRECTBLEACH210_NO_ANALYSIS',version='2.1.0',source_commit=profile['source_commit'],source_profile=pin(HERE/'source-profile.json'),source_members=profile['source_members'],compiled_original_java_files=len(java),original_source_patched=False,compiled_original_target=8,dependency_versions=profile['dependency_versions'],runtime=runtime,versions=versions,calls=calls,image_analysis_executed=False,new_tool_credit=0));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('READY_PRIVATE_COMPLETE_ORIGINAL_CORRECTBLEACH210_NO_ANALYSIS',flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_ORIGINAL_PROVIDER_UNAVAILABLE' if code==3 else 'FAIL2_CORRECTBLEACH210_PROVIDER',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',v);(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
