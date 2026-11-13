#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the standalone original Monocle3 1.3.1 model adapter with pinned native Java probability support."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,traceback,urllib.request
ROOT=Path(__file__).resolve().parents[3]
JAVA=['cli/Monocle131ModelCli','cli/Monocle131ModelRequest','cli/Monocle131ModelInputs','stats/Monocle131Statistics','cli/NextflowFiles','cli/NextflowJson','cli/NextflowParameters','cli/SimpleJson','cli/NextflowProcess','cli/NfcoreProcess','workflow/LinuxProcessSnapshot']
RES='src/main/resources/org/jlinalg/omics/monocle131-models';SHA='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81';URL='https://github.com/robbyjo/JDistlib/releases/download/v0.10.2/jdistlib-all-0.10.2.jar'
ACTION='Required action: supply permitted JDK21/25 and exact JDistlib0.10.2 with --jdistlib-jar or permitted --download-jdistlib, and NEW output. Keep the adjacent jdistlib-all-0.10.2.jar. Original fitting separately requires the qualified Linux R4.3.3/Monocle3 1.3.1 profile and complete prepared expression/metadata. If a needed compiler/runtime/download/install/execution is unavailable or forbidden, SKIP3 skips dependent analysis. Resolve FAIL2 input, source, integrity or scientific causes before retrying NEW paths. Preserve diagnostics; no original R/Monocle runtime is bundled.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--out',required=True,type=Path);g=p.add_mutually_exclusive_group(required=True);g.add_argument('--jdistlib-jar',type=Path);g.add_argument('--download-jdistlib',action='store_true');a=p.parse_args();out=a.out.absolute();owned=False
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;suffix='.exe' if os.name=='nt' else '';java=a.java_bin.resolve();env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
   if r.returncode:raise ValueError('Build failed at '+label+'; read retained diagnostics')
   return (r.stdout+r.stderr).decode(errors='replace')
  versions={n:run(n+'-version',[java/(n+suffix),'--version']) for n in ['java','javac','jar']}
  if any(not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',v) for v in versions.values()):raise OSError('Matching permitted JDK21/25 required')
  dependency=out/'jdistlib-all-0.10.2.jar'
  if a.jdistlib_jar:shutil.copyfile(a.jdistlib_jar.resolve(),dependency)
  else:
   with urllib.request.urlopen(URL,timeout=60) as r,dependency.open('xb') as f:
    size=0
    for b in iter(lambda:r.read(1048576),b''):
     size+=len(b)
     if size>67108864:raise ValueError('Complete JDistlib exceeds dependency byte capacity')
     f.write(b)
  if pin(dependency)['sha256']!=SHA:raise ValueError('JDistlib0.10.2 dependency integrity differs')
  names=['src/main/java/org/jlinalg/'+n+'.java' for n in JAVA]+[RES+'/'+n for n in ['worker.R','source-profile.json']]+['tools/literature/monocle131_models/build.py'];sources={n:pin(ROOT/n) for n in names};classes=out/'classes';classes.mkdir()
  run('javac',[java/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',dependency,'-d',classes,*[ROOT/'src/main/java/org/jlinalg'/(n+'.java') for n in JAVA]])
  target=classes/'org/jlinalg/omics/monocle131-models';target.mkdir(parents=True)
  for n in ['worker.R','source-profile.json']:shutil.copyfile(ROOT/RES/n,target/n)
  manifest=out/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.Monocle131ModelCli\nClass-Path: jdistlib-all-0.10.2.jar\n\n',encoding='ascii',newline='\n');artifact=out/'monocle131-models-development.jar';run('jar',[java/('jar'+suffix),'--create','--file',artifact,'--manifest',manifest,'-C',classes,'.']);run('help',[java/('java'+suffix),'-jar',artifact,'--help'])
  if b'SKIP3' not in (out/'help.stdout').read_bytes() or b'Required action:' not in (out/'help.stdout').read_bytes():raise ValueError('Packaged recovery instructions absent')
  if any(pin(ROOT/n)!=v for n,v in sources.items()) or pin(dependency)['sha256']!=SHA:raise ValueError('Build source/dependency changed')
  save(out/'BUILD.json',dict(status='READY_ORIGINAL_MONOCLE131_MODEL_DEVELOPMENT_ADAPTER',sources=sources,artifact=pin(artifact),native_math_dependency=pin(dependency),native_math_url=URL,versions=versions,bytecode_target=17,original_runtime_bundled=False,new_tool_credit=0,whole_goal_complete=False));shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt');(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('READY_ORIGINAL_MONOCLE131_MODEL_DEVELOPMENT_ADAPTER',artifact,flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;state=dict(status='SKIP3_BUILD_UNAVAILABLE' if code==3 else 'FAIL2_MONOCLE131_MODEL_BUILD',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text(state['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
