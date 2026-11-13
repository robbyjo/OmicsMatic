#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the standalone original Rphenograph0.99.1 complete clustering adapter; no R installation."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,traceback
ROOT=Path(__file__).resolve().parents[3]
JAVA=['cli/Rphenograph099WorkflowCli', 'cli/Rphenograph099Request', 'cli/Rphenograph099Inputs', 'cli/Rphenograph099Audit', 'singlecell/RphenographJaccard099', 'cli/Monocle131ModelRequest', 'cli/NextflowFiles', 'cli/NextflowJson', 'cli/NextflowParameters', 'cli/SimpleJson', 'cli/NextflowProcess', 'cli/NfcoreProcess', 'workflow/LinuxProcessSnapshot']
RESOURCES='src/main/resources/org/jlinalg/omics/rphenograph099'
ACTION='Required action: provide a permitted JDK21/25 and NEW output. This builds Java bytecode17 without installing R. Complete original clustering additionally requires the exact documented private Linux R4.3.3/RANN2.6.2/igraph2.0.3/Rphenograph0.99.1 provider and caller-prepared cells-by-features TSV. Install/download only where permitted. Missing/forbidden prerequisites SKIP3 skip dependent work. Retain diagnostics; resolve FAIL2 source/input/resource contradictions and retry NEW paths. The paper reports Rphenograph0.1.1, whose package identity remains unresolved; no edition substitution or paper reproduction is claimed.'

def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;suffix='.exe' if os.name=='nt' else '';java=a.java_bin.resolve();env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
   if r.returncode:raise ValueError('Build failed at '+label+'; read retained diagnostics')
   return (r.stdout+r.stderr).decode(errors='replace')
  versions={n:run(n+'-version',[java/(n+suffix),'--version']) for n in ['java','javac','jar']}
  if any(not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',v) for v in versions.values()):raise OSError('Matching permitted JDK21/25 required')
  names=['src/main/java/org/jlinalg/'+n+'.java' for n in JAVA]+[RESOURCES+'/'+n for n in ['worker.R','source-profile.json']]+['tools/literature/rphenograph099/'+n for n in ['build.py','LICENSE-upstream-Artistic-2.0.txt','UPSTREAM_NOTICE.md']];sources={n:pin(ROOT/n) for n in names};classes=out/'classes';classes.mkdir()
  run('javac',[java/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-d',classes,*[ROOT/'src/main/java/org/jlinalg'/(n+'.java') for n in JAVA]])
  target=classes/'org/jlinalg/omics/rphenograph099';target.mkdir(parents=True)
  for n in ['worker.R','source-profile.json']:shutil.copyfile(ROOT/RESOURCES/n,target/n)
  manifest=out/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.Rphenograph099WorkflowCli\n\n',encoding='ascii',newline='\n')
  artifact=out/'rphenograph099-workflow-development.jar';run('jar',[java/('jar'+suffix),'--create','--file',artifact,'--manifest',manifest,'-C',classes,'.']);run('help',[java/('java'+suffix),'-jar',artifact,'--help'])
  if b'SKIP3' not in (out/'help.stdout').read_bytes() or b'Required action:' not in (out/'help.stdout').read_bytes():raise ValueError('Packaged skip/recovery guidance missing')
  if any(pin(ROOT/n)!=v for n,v in sources.items()):raise ValueError('Build source changed')
  save(out/'BUILD.json',dict(status='READY_ORIGINAL_RPHENOGRAPH099_DEVELOPMENT_ADAPTER',sources=sources,artifact=pin(artifact),versions=versions,bytecode_target=17,original_runtime_bundled=False,new_tool_credit=0,whole_goal_complete=False));shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt');shutil.copyfile(ROOT/'tools/literature/rphenograph099/LICENSE-upstream-Artistic-2.0.txt',out/'LICENSE-upstream-Artistic-2.0.txt');shutil.copyfile(ROOT/'tools/literature/rphenograph099/UPSTREAM_NOTICE.md',out/'UPSTREAM_NOTICE.md');(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('READY_ORIGINAL_RPHENOGRAPH099_DEVELOPMENT_ADAPTER',artifact,flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;state=dict(status='SKIP3_BUILD_UNAVAILABLE' if code==3 else 'FAIL2_RPHENOGRAPH099_BUILD',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text(state['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
