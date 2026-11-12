#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the owned original HOPS_Main me_po driver without changing frozen components."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,os,re,subprocess,traceback,shutil
ROOT=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('hops_post_build',ROOT/'tools/literature/hops035_workflow/build.py');prior=importlib.util.module_from_spec(spec);spec.loader.exec_module(prior)
SOURCES=prior.SOURCES+['src/main/java/org/jlinalg/cli/HopsMepoCli.java']
ACTION='Required action: use a permitted JDK21 or JDK25 compiler and a NEW build directory. Retain all diagnostics. Execution/fresh RData verification requires native Linux x86_64 and the separately pinned providers. If installation/runtime/execution is unavailable or forbidden, skip dependent analysis. This development build bundles no upstream program/runtime and does not qualify whole HOPS/MALT or biological reproduction.'
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;java=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else '';env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  frozen=json.loads((ROOT/'docs/literature/hops035-postprocessing-development/VALIDATION.json').read_bytes())
  for name,value in frozen['sources'].items():
   if pin(ROOT/name)!=value:raise ValueError('Frozen dependency differs: '+name)
  def run(label,command):
   command=list(map(str,command));r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
   if r.returncode:raise ValueError('Build failed at '+label+'; inspect retained diagnostics')
   return (r.stdout+r.stderr).decode(errors='replace')
  versions={}
  for name in ['java','javac','jar']:
   versions[name]=run(name+'-version',[java/(name+suffix),'-version' if name!='jar' else '--version'])
   if not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',versions[name]):raise OSError('JDK21 or JDK25 required')
  sources={name:pin(ROOT/name) for name in SOURCES};sources['tools/literature/hops_mepo/build.py']=pin(Path(__file__))
  classes=out/'classes';classes.mkdir();run('javac',[java/('javac'+suffix),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-sourcepath','','-d',classes,*[ROOT/name for name in SOURCES]])
  resource=classes/'org/jlinalg/metagenomics/hops035';resource.mkdir()
  for name in ['export_assessment.R','r-runtime-lock.json']:shutil.copyfile(prior.RESOURCE/name,resource/name);sources['src/main/resources/org/jlinalg/metagenomics/hops035/'+name]=pin(prior.RESOURCE/name)
  artifact=out/'hops-mepo-development.jar';run('jar',[java/('jar'+suffix),'--create','--file',artifact,'--main-class','org.jlinalg.cli.HopsMepoCli','-C',classes,'.']);run('help',[java/('java'+suffix),'-jar',artifact,'--help'])
  if b'Required action:' not in (out/'help.stdout').read_bytes():raise ValueError('Packaged recovery instructions absent')
  if any(pin(ROOT/name)!=value for name,value in sources.items()):raise ValueError('Build source changed')
  shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt');save(out/'BUILD.json',dict(status='READY_OWNED_ORIGINAL_HOPS_MEPO_DEVELOPMENT_DRIVER',sources=sources,frozen_dependencies=frozen['sources'],versions=versions,artifact=pin(artifact),upstream_programs_bundled=False,bytecode_target=17,new_tool_credit=0,whole_goal_complete=False))
  print('READY_OWNED_ORIGINAL_HOPS_MEPO_DEVELOPMENT_DRIVER',artifact,flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;state=dict(status='SKIP3_BUILD_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_HOPS_MEPO_BUILD',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source checks')
 raise SystemExit(main())
