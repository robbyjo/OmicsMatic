#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package the complete private original Java core and strict Java workflow without changing the canonical JAR."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,traceback,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
JAVA=['ImageJ154tWorkflowCli','ImageJ154tRequest','NextflowFiles','NextflowJson','NextflowParameters','SimpleJson']
ACTION='Required action: supply permitted JDK21/25, the completed private original1.54t --provider directory and NEW output. First run provider.py with explicit permitted build/download choices. Missing prerequisites SKIP3 skip the dependent build/analysis. Retain FAIL2 diagnostics and resolve source/provider contradictions without modifying original source. This packages all original Java core classes/resources, separate project batch/error-diagnostic shims and the complete macro workflow; it does not recover Fiji/plugins, reproduce a paper or replace the canonical executable. The original public native Java ImageJ APIs are also available on this development JAR classpath.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',type=Path,required=True);p.add_argument('--provider',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  for protected in [a.java_bin,a.provider]:
   protected=protected.resolve()
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps a protected compiler/provider')
  out.mkdir(parents=True,exist_ok=False);owned=True;provider=a.provider.resolve();result=json.loads((provider/'RESULT.json').read_bytes());profile=json.loads((HERE/'source-profile.json').read_bytes());original=provider/'ij-1.54t.jar'
  if result['status']!='READY_PRIVATE_ORIGINAL_IMAGEJ154T_NOT_ANALYSIS' or result['version']!='1.54t' or result['source_commit']!=profile['source_commit'] or result['original_source_patched'] or result['source_profile']!=pin(HERE/'source-profile.json') or result['artifact']!=pin(original):raise ValueError('Original complete provider/profile identity differs')
  source=provider/'source'
  if {f.relative_to(source).as_posix() for f in source.rglob('*') if f.is_file()}!=set(profile['source_members']):raise ValueError('Original full source population differs')
  for name,wanted in profile['source_members'].items():
   if pin(source/name)!=wanted:raise ValueError('Original source changed: '+name)
  java=[ROOT/'src/main/java/org/jlinalg/cli'/(n+'.java') for n in JAVA]+sorted((HERE/'java').rglob('*.java'));names=[*java,HERE/'source-profile.json',HERE/'build.py',HERE/'provider.py'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in names};env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']};jdk=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else ''
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);calls.append(dict(label=label,command=command,exit_code=r.returncode));save(out/(label+'.process.json'),calls[-1])
   if r.returncode:raise ValueError('Build failed at '+label+'; read retained diagnostics')
   return (r.stdout+r.stderr).decode(errors='replace')
  versions={n:run(n+'-version',[jdk/(n+suffix),'--version']) for n in ['java','javac','jar']}
  if any(not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',v) for v in versions.values()):raise OSError('Matching permitted JDK21/25 required')
  classes=out/'classes';classes.mkdir();run('javac',[jdk/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',original,'-d',classes,*java]);core_entries={}
  with zipfile.ZipFile(original) as z:
   infos=[i for i in z.infolist() if not i.is_dir() and i.filename!='META-INF/MANIFEST.MF']
   if len({i.filename for i in infos})!=len(infos):raise ValueError('Duplicate original runtime JAR member')
   for info in infos:
    name=info.filename
    if name.startswith('/') or '\\' in name or any(x in ['','.','..'] for x in name.split('/')):raise ValueError('Unsafe original JAR member')
    raw=z.read(info);dest=classes/name
    if dest.exists():raise ValueError('Project class overrides original member: '+name)
    dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw);core_entries[name]=rawpin(raw)
  runtime=dict(version='1.54t',repository=profile['repository'],source_commit=profile['source_commit'],source_archive=profile['source_archive'],original_source_patched=False,original_ant_bytecode_target='1.6',compiled_original_target=8,project_bytecode_target=17,original_precompiled_mac_adapters=True,original_jar_entries=core_entries,original_provider_jar=pin(original),Fiji_distribution_recovered=False,external_plugins_qualified=False,producer_authenticated=False)
  resources=classes/'org/jlinalg/omics/imagej154t';resources.mkdir(parents=True);save(resources/'runtime-profile.json',runtime);shutil.copyfile(HERE/'source-profile.json',resources/'source-profile.json');manifest=out/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.ImageJ154tWorkflowCli\n\n',encoding='ascii',newline='\n');artifact=out/'imagej154t-workflow-development.jar';run('jar',[jdk/('jar'+suffix),'--create','--file',artifact,'--manifest',manifest,'-C',classes,'.']);run('help',[jdk/('java'+suffix),'-jar',artifact,'--help'])
  if b'SKIP3' not in (out/'help.stdout').read_bytes() or b'Required action:' not in (out/'help.stdout').read_bytes():raise ValueError('Prominent warnings and recovery guidance missing')
  if any(pin(ROOT/n)!=v for n,v in pins.items()) or pin(original)!=result['artifact']:raise ValueError('Build sources/provider changed')
  with zipfile.ZipFile(artifact) as z:
   for n,wanted in core_entries.items():
    if rawpin(z.read(n))!=wanted:raise ValueError('Packaged original member differs: '+n)
  save(out/'BUILD.json',dict(status='READY_COMPLETE_ORIGINAL_IMAGEJ154T_JAVA_MACRO_WORKFLOW',artifact=pin(artifact),sources=pins,versions=versions,calls=calls,original_core_members=len(core_entries),runtime_profile=pin(resources/'runtime-profile.json'),original_source_patched=False,original_analysis_executed=False,Fiji_distribution_recovered=False,new_tool_credit=0,whole_goal_complete=False));shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt');(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('READY_COMPLETE_ORIGINAL_IMAGEJ154T_JAVA_MACRO_WORKFLOW',artifact,flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_BUILD_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_IMAGEJ154T_BUILD',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',v);(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
