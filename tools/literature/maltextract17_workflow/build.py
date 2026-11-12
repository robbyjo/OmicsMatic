#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the original MaltExtract1.7 supervised workflow adapter using only a JDK."""
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, subprocess, traceback

ROOT=Path(__file__).resolve().parents[3]
SOURCES=['src/main/java/org/jlinalg/metagenomics/'+n+'.java' for n in ['MaltExtractTaxonomy','MaltExtractRmaReader','MaltExtractReadProjection','MaltExtractSelection','MaltExtractOutputAudit']]+['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['MaltExtractWorkflowCli','NextflowFiles','NextflowJson','NextflowParameters','SimpleJson','NextflowProcess','NfcoreProcess']]+['src/main/java/org/jlinalg/workflow/LinuxProcessSnapshot.java']
ACTION='Required action: use a permitted JDK21 or JDK25 compiler and a NEW output directory. This builds a separate development adapter; canonical/released executables remain unchanged. The pinned original MaltExtract1.7/Linux Java8 provider and complete resources/inputs remain external. If building/install/execution is unavailable or forbidden, skip dependent analysis. Retain diagnostics, read --help, and resolve the named issue before retrying. Whole MALT/HOPS qualification and biological paper reproduction remain incomplete.'

def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())

def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_MALTEXTRACT17_WORKFLOW_BUILD',analysis_qualified=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise OSError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;java=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else ''
  env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def run(label,command):
   r=subprocess.run(list(map(str,command)),cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=list(map(str,command)),exit_code=r.returncode))
   if r.returncode:raise ValueError('Build failed at '+label+'; retain diagnostics')
   return (r.stdout+r.stderr).decode(errors='replace')
  versions={}
  for name in ['java','javac','jar']:
   versions[name]=run(name+'-version',[java/(name+suffix),'-version' if name!='jar' else '--version'])
   if not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',versions[name]):raise OSError('Permitted JDK21 or JDK25 required')
  sources={name:pin(ROOT/name) for name in SOURCES};classes=out/'classes';classes.mkdir()
  run('javac',[java/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-d',classes,*[ROOT/name for name in SOURCES]])
  artifact=out/'maltextract17-workflow-development.jar';run('jar',[java/('jar'+suffix),'--create','--file',artifact,'--main-class','org.jlinalg.cli.MaltExtractWorkflowCli','-C',classes,'.']);run('help',[java/('java'+suffix),'-jar',artifact,'--help'])
  if b'Required action:' not in (out/'help.stdout').read_bytes():raise ValueError('Missing packaged recovery instructions')
  if any(pin(ROOT/name)!=v for name,v in sources.items()):raise ValueError('Build sources changed')
  shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt');state.update(status='READY_ORIGINAL_MALTEXTRACT17_WORKFLOW_DEVELOPMENT_ADAPTER',sources=sources,versions=versions,artifact=pin(artifact),bytecode_target=17,upstream_programs_bundled=False);save(out/'BUILD.json',state);(out/'WARNINGS.md').write_text('# Development build only\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],str(artifact),flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;state.update(status='SKIP3_COMPILER_UNAVAILABLE' if code==3 else 'FAIL2_MALTEXTRACT17_WORKFLOW_BUILD',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,dependent_analysis_skipped=True)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source checks')
 raise SystemExit(main())
