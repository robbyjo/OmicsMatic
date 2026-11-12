#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the complete owned MALT0.5.2 index-build/BlastN workflow with frozen components."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,os,re,shutil,subprocess,traceback,urllib.request,urllib.error
ROOT=Path(__file__).resolve().parents[3]
def module(name,path):
 spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
align=module('malt052_alignment_build',ROOT/'tools/literature/malt052/build.py')
index=module('malt052_index_build',ROOT/'tools/literature/malt052/build_index.py')
SOURCES=list(dict.fromkeys(index.SOURCES+align.SOURCES+['src/main/java/org/jlinalg/cli/Malt052WorkflowCli.java']))
ACTION='Required action: supply a permitted JDK21/25 compiler, the exact HTSJDK5.0.0 dependency with --htsjdk-jar or permitted --download-htsjdk, and a NEW build directory. Keep the exact adjacent HTSJDK jar and project license with this workflow jar. Original fitting requires the separate pinned Linux MALT0.5.2/Java14 provider; saved replay uses its exact reader jars under modern Java. If installation/compiler/runtime is unavailable or forbidden, skip dependent analysis. Retain diagnostics. No original MALT, JRE, taxonomy database or paper data is bundled.'
def pin(p):return align.pin(p)
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--out',required=True,type=Path);g=p.add_mutually_exclusive_group(required=True);g.add_argument('--htsjdk-jar',type=Path);g.add_argument('--download-htsjdk',action='store_true');a=p.parse_args();out=a.out.absolute();owned=False
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True
  frozen=json.loads((ROOT/'docs/literature/malt052-build-development/VALIDATION.json').read_bytes())
  for n,v in frozen['sources'].items():
   if pin(ROOT/n)!=v:raise ValueError('Frozen complete index/alignment component differs: '+n)
  java=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else '';env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def run(label,command):
   command=list(map(str,command));r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
   if r.returncode:raise ValueError('Build failed at '+label+'; retain diagnostics')
   return (r.stdout+r.stderr).decode(errors='replace')
  versions={}
  for n in ['java','javac','jar']:
   versions[n]=run(n+'-version',[java/(n+suffix),'--version'])
   if not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',versions[n]):raise OSError('Matching permitted JDK21/25 compiler/runtime required')
  dependency=out/'htsjdk-5.0.0.jar'
  if a.htsjdk_jar:shutil.copyfile(a.htsjdk_jar.resolve(),dependency)
  else:
   with urllib.request.urlopen(align.URL,timeout=60) as r,dependency.open('xb') as f:
    size=0
    for chunk in iter(lambda:r.read(1048576),b''):
     size+=len(chunk)
     if size>16777216:raise ValueError('Complete HTSJDK dependency exceeds byte capacity')
     f.write(chunk)
  if pin(dependency)!=align.HTSJDK:raise ValueError('Complete HTSJDK5.0.0 dependency differs')
  sources={n:pin(ROOT/n) for n in SOURCES};sources['tools/literature/malt052_workflow/build.py']=pin(Path(__file__));classes=out/'classes';classes.mkdir()
  run('javac',[java/('javac'+suffix),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-sourcepath','','-cp',dependency,'-d',classes,*[ROOT/n for n in SOURCES]])
  manifest=out/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.Malt052WorkflowCli\nClass-Path: htsjdk-5.0.0.jar\n\n',encoding='ascii',newline='\n')
  artifact=out/'malt052-workflow-development.jar';run('jar',[java/('jar'+suffix),'--create','--file',artifact,'--manifest',manifest,'-C',classes,'.']);run('help',[java/('java'+suffix),'-jar',artifact,'--help'])
  if b'Required action:' not in (out/'help.stdout').read_bytes() or b'SKIP3' not in (out/'help.stdout').read_bytes():raise ValueError('Packaged recovery instructions missing')
  if any(pin(ROOT/n)!=v for n,v in sources.items()) or pin(dependency)!=align.HTSJDK:raise ValueError('Build source/dependency changed')
  save(out/'BUILD.json',dict(status='READY_COMPLETE_OWNED_MALT052_BUILD_BLASTN_DEVELOPMENT_WORKFLOW',sources=sources,frozen_dependencies=frozen['sources'],artifact=pin(artifact),htsjdk=align.HTSJDK,versions=versions,bytecode_target=17,classpath_dependency_must_remain_adjacent='htsjdk-5.0.0.jar',original_programs_bundled=False,new_tool_credit=0,whole_goal_complete=False));shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt');(out/'WARNINGS.md').write_text('# Development workflow only\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print('READY_COMPLETE_OWNED_MALT052_BUILD_BLASTN_DEVELOPMENT_WORKFLOW',artifact,flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,(OSError,urllib.error.URLError)) else 2;state=dict(status='SKIP3_BUILD_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_MALT052_WORKFLOW_BUILD',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source/dependency checks')
 raise SystemExit(main())
