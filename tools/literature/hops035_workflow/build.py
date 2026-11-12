#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build original HOPS postprocessing and native audits with only a modern JDK."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,os,re,shutil,subprocess,traceback
ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).resolve().parent
RESOURCE=ROOT/'src/main/resources/org/jlinalg/metagenomics/hops035'
spec=importlib.util.spec_from_file_location('maltextract_workflow_build',HERE.parent/'maltextract17_workflow/build.py')
prior=importlib.util.module_from_spec(spec);spec.loader.exec_module(prior)
SOURCES=prior.SOURCES+['src/main/java/org/jlinalg/metagenomics/'+n+'.java' for n in ['HopsEvidence','HopsRScalar','HopsAssessmentAudit']]+['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['HopsPostprocessingProvider','HopsPostprocessingCli']]
ACTION='Required action: use a permitted JDK21 or JDK25 and a NEW build directory. Supply the complete matching source/resources. This separate development JAR does not change the canonical executable or qualify whole HOPS. Original fitting/postprocessing requires permitted external exact runtimes; unavailable or forbidden installation/execution skips dependent analysis. Retain all diagnostics and read --help before retrying.'
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
  out.mkdir(parents=True,exist_ok=False);owned=True;java=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else '';env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  for name in ['export_assessment.R','r-runtime-lock.json']:
   if (HERE/name).read_bytes()!=(RESOURCE/name).read_bytes():raise ValueError('Public setup and packaged resource bytes differ: '+name)
  def run(label,command):
   command=list(map(str,command));r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
   if r.returncode:raise ValueError('Build failed at '+label+'; inspect retained diagnostics')
   return (r.stdout+r.stderr).decode(errors='replace')
  versions={}
  for name in ['java','javac','jar']:
   versions[name]=run(name+'-version',[java/(name+suffix),'-version' if name!='jar' else '--version'])
   if not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',versions[name]):raise OSError('Permitted JDK21/JDK25 compiler required')
  files=SOURCES+[p.relative_to(ROOT).as_posix() for p in sorted(RESOURCE.iterdir()) if p.is_file()]+['tools/literature/hops035_workflow/'+n for n in ['build.py','setup.py','r-runtime-lock.json','export_assessment.R','verify.py']]
  sources={name:pin(ROOT/name) for name in files};classes=out/'classes';classes.mkdir()
  run('javac',[java/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-d',classes,*[ROOT/name for name in SOURCES]])
  target=classes/'org/jlinalg/metagenomics/hops035';target.mkdir()
  for name in ['export_assessment.R','r-runtime-lock.json']:shutil.copyfile(RESOURCE/name,target/name)
  artifact=out/'hops035-postprocessing-development.jar';run('jar',[java/('jar'+suffix),'--create','--file',artifact,'--main-class','org.jlinalg.cli.HopsPostprocessingCli','-C',classes,'.']);run('help',[java/('java'+suffix),'-jar',artifact,'--help'])
  if b'Required action:' not in (out/'help.stdout').read_bytes():raise ValueError('Packaged help lacks required actions')
  if any(pin(ROOT/name)!=v for name,v in sources.items()):raise ValueError('Build sources/resources changed')
  shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt');save(out/'BUILD.json',dict(status='READY_HOPS035_ORIGINAL_POSTPROCESSING_DEVELOPMENT_ADAPTER',sources=sources,versions=versions,artifact=pin(artifact),bytecode_target=17,upstream_programs_bundled=False,new_tool_credit=0,whole_goal_complete=False));(out/'WARNINGS.md').write_text('# Development build only\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print('READY_HOPS035_ORIGINAL_POSTPROCESSING_DEVELOPMENT_ADAPTER',str(artifact),flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;state=dict(status='SKIP3_BUILD_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_HOPS035_POSTPROCESSING_BUILD',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete build checks')
 raise SystemExit(main())
