#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Strict standalone Java build for the unchanged original QuPath0.5.1 script adapter."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,traceback
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
JAVA=['QuPath051WorkflowCli','QuPath051Request','NextflowFiles','NextflowJson','NextflowParameters','SimpleJson']
ACTION='Required action: supply a permitted JDK21/25 and NEW build output. Missing compiler/runtime SKIP3 skips dependent build/analysis. FAIL2 admits no build; retain source snapshots and complete compiler/process logs, correct the named cause and retry NEW paths. This builds the development adapter and pinned source profile; it does not embed a redistributed QuPath binary or modify the canonical executable. Run provider.py separately for the complete native-platform original runtime. No scientific analysis, paper reproduction or catalog admission follows from compilation alone.'
def pin(path):
 raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--java-bin',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  for protected in [HERE,ROOT/'src',ROOT/'docs',args.java_bin.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Build output overlaps protected source/compiler')
  out.mkdir(parents=True,exist_ok=False);owned=True;jdk=args.java_bin.resolve();suffix='.exe' if os.name=='nt' else '';java=[ROOT/'src/main/java/org/jlinalg/cli'/(name+'.java') for name in JAVA]
  sources=[*java,HERE/'source-profile.json',HERE/'provider.py',HERE/'build.py'];pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
  for source in sources:
   dest=out/'source-snapshot'/source.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
  save(out/'BEFORE.json',dict(source_pins=pins));env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']}
  def run(label,command):
   command=list(map(str,command));result=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(result.stdout);(out/(label+'.stderr')).write_bytes(result.stderr);row=dict(label=label,command=command,exit_code=result.returncode);calls.append(row);save(out/(label+'.process.json'),row)
   if result.returncode:raise ValueError('Actual standalone build failed at '+label+'; retain compiler/source diagnostics')
   return (result.stdout+result.stderr).decode(errors='replace')
  versions={name:run(name+'-version',[jdk/(name+suffix),'--version']) for name in ['java','javac','jar']}
  if any(not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',v) for v in versions.values()):raise OSError('Matching permitted JDK21/25 required')
  classes=out/'classes';classes.mkdir();run('compile',[jdk/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp','','-d',classes,*java])
  resource=classes/'org/jlinalg/omics/qupath051';resource.mkdir(parents=True);shutil.copyfile(HERE/'source-profile.json',resource/'source-profile.json');save(resource/'build-profile.json',dict(version='0.5.1',provider_source=pin(HERE/'provider.py'),original_scientific_source_modified=False))
  manifest=out/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.QuPath051WorkflowCli\n\n',encoding='ascii',newline='\n');artifact=out/'qupath051-workflow-development.jar'
  run('package',[jdk/('jar'+suffix),'--create','--file',artifact,'--manifest',manifest,'-C',classes,'.']);run('help',[jdk/('java'+suffix),'-jar',artifact,'--help'])
  help=(out/'help.stdout').read_bytes()
  if b'SKIP3' not in help or b'FAIL2' not in help or b'Required action:' not in help:raise ValueError('Prominent skip/error recovery guidance missing')
  if any(pin(ROOT/name)!=wanted for name,wanted in pins.items()):raise ValueError('Build source/profile changed')
  save(out/'BUILD.json',dict(status='READY_ORIGINAL_QUPATH051_SCRIPT_ADAPTER_NOT_ANALYSIS_QUALIFICATION',artifact=pin(artifact),sources=pins,versions=versions,calls=calls,source_profile=pin(HERE/'source-profile.json'),original_scientific_source_modified=False,canonical_changed=False,new_tool_credit=0,whole_goal_complete=False));shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt');(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('READY_ORIGINAL_QUPATH051_SCRIPT_ADAPTER_NOT_ANALYSIS_QUALIFICATION',artifact,flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_QUPATH_BUILD_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_QUPATH051_BUILD',cause=repr(error),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
