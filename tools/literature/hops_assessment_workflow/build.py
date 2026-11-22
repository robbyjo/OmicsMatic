#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the complete declared RMA-to-HOPS-assessment coordinator with a JDK."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,os,re,shutil,subprocess,traceback
ROOT=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('hops_post_build',ROOT/'tools/literature/hops035_workflow/build.py');prior=importlib.util.module_from_spec(spec);spec.loader.exec_module(prior)
SOURCES=prior.SOURCES+['src/main/java/org/jlinalg/cli/HopsAssessmentWorkflowCli.java']
ACTION='Required action: use a permitted JDK21 or JDK25 compiler and a NEW output directory. Original workflow execution requires native Linux x86_64 application JDK21, the separately pinned full MaltExtract1.7/Java8 and public R4.0.5 providers, complete resources/RMAs and explicit method choices. If a required install/runtime is unavailable or forbidden, skip dependent assessment. Retain diagnostics and read --help. This build bundles no upstream program/runtime and does not qualify whole HOPS/MALT or biological reproduction.'
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
  out.mkdir(parents=True,exist_ok=False);owned=True;java=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else ''
  frozen=json.loads((ROOT/'docs/literature/hops035-postprocessing-development/VALIDATION.json').read_bytes())
  for name,value in frozen['sources'].items():
   if pin(ROOT/name)!=value:raise ValueError('Frozen original postprocessing/extraction dependency differs: '+name)
  env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def run(label,command):
   command=list(map(str,command));r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
   if r.returncode:raise ValueError('Build failed at '+label+'; retain diagnostics')
   return (r.stdout+r.stderr).decode(errors='replace')
  versions={}
  for name in ['java','javac','jar']:
   versions[name]=run(name+'-version',[java/(name+suffix),'-version' if name!='jar' else '--version'])
   if not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',versions[name]):raise OSError('Permitted JDK21/JDK25 compiler required')
  sources={n:pin(ROOT/n) for n in SOURCES};sources['tools/literature/hops_assessment_workflow/build.py']=pin(Path(__file__))
  classes=out/'classes';classes.mkdir();run('javac',[java/('javac'+suffix),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-sourcepath','','-d',classes,*[ROOT/n for n in SOURCES]])
  resource=classes/'org/jlinalg/metagenomics/hops035';resource.mkdir()
  for name in ['export_assessment.R','r-runtime-lock.json']:shutil.copyfile(prior.RESOURCE/name,resource/name);sources['src/main/resources/org/jlinalg/metagenomics/hops035/'+name]=pin(prior.RESOURCE/name)
  artifact=out/'hops-assessment-workflow-development.jar';run('jar',[java/('jar'+suffix),'--create','--file',artifact,'--main-class','org.jlinalg.cli.HopsAssessmentWorkflowCli','-C',classes,'.']);run('help',[java/('java'+suffix),'-jar',artifact,'--help'])
  if b'Required action:' not in (out/'help.stdout').read_bytes():raise ValueError('Packaged recovery instructions missing')
  if any(pin(ROOT/n)!=v for n,v in sources.items()):raise ValueError('Current build source changed')
  save(out/'BUILD.json',dict(status='READY_ORIGINAL_HOPS_RMA_ASSESSMENT_DEVELOPMENT_WORKFLOW',sources=sources,frozen_dependencies=frozen['sources'],artifact=pin(artifact),versions=versions,bytecode_target=17,upstream_programs_bundled=False,new_tool_credit=0));shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt');print('READY_ORIGINAL_HOPS_RMA_ASSESSMENT_DEVELOPMENT_WORKFLOW',artifact,flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;state=dict(status='SKIP3_BUILD_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_HOPS_ASSESSMENT_BUILD',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source checks')
 raise SystemExit(main())
