#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the standalone complete correction controller and native Java histogram component."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,traceback
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
JAVA=['cli/CorrectBleach210Request','cli/CorrectBleach210WorkflowCli','cli/NextflowFiles','cli/NextflowJson','cli/NextflowParameters','cli/SimpleJson','imaging/CorrectBleachHistogram210']
ACTION='Required action: provide permitted JDK21/25, exact completed private CorrectBleach2.1.0 --provider and NEW output. Missing prerequisites SKIP3 skip dependent build/analysis. Retain FAIL2 diagnostics, resolve exact source/provider contradictions without patching original algorithms and retry NEW paths. This builds bytecode17; original runtime is separately captured from the explicit provider. P026 version/method/settings remain unreported. Histogram matching is inappropriate for intensity quantification. No canonical executable or scientific input is replaced.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--provider',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  for protected in [a.java_bin,a.provider]:
   path=protected.resolve()
   if out.is_relative_to(path) or path.is_relative_to(out):raise ValueError('Output overlaps protected compiler/provider')
  out.mkdir(parents=True,exist_ok=False);owned=True;provider=a.provider.resolve();ready=json.loads((provider/'READY.json').read_bytes())
  if ready['status']!='READY_PRIVATE_COMPLETE_ORIGINAL_CORRECTBLEACH210_NO_ANALYSIS' or ready['original_source_patched'] or ready['source_profile']!=pin(HERE/'source-profile.json'):raise ValueError('Original provider edition/profile differs')
  for n,w in ready['runtime'].items():
   if pin(provider/'lib'/n)!=w:raise ValueError('Original runtime differs: '+n)
  java=[ROOT/'src/main/java/org/jlinalg'/(n+'.java') for n in JAVA]+sorted((HERE/'java').rglob('*.java'));notices=sorted((HERE/'licenses').iterdir());sources={p.relative_to(ROOT).as_posix():pin(p) for p in [*java,*notices,HERE/'source-profile.json',HERE/'build.py']};env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']};jdk=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else ''
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode:raise ValueError('Build failed at '+label+'; retain diagnostics')
   return (r.stdout+r.stderr).decode(errors='replace')
  versions={n:run(n+'-version',[jdk/(n+suffix),'--version']) for n in ['java','javac','jar']}
  if any(not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',v) for v in versions.values()):raise OSError('Matching permitted JDK21/25 required')
  classes=out/'classes';classes.mkdir();cp=os.pathsep.join(str(provider/'lib'/n) for n in ready['runtime']);run('javac',[jdk/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-proc:none','-sourcepath','','-cp',cp,'-d',classes,*java]);resources=classes/'org/jlinalg/omics/correctbleach210';resources.mkdir(parents=True);shutil.copyfile(HERE/'source-profile.json',resources/'source-profile.json');shutil.copytree(HERE/'licenses',resources/'licenses');manifest=out/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.CorrectBleach210WorkflowCli\n\n',encoding='ascii',newline='\n');jar=out/'correctbleach210-workflow-development.jar';run('jar',[jdk/('jar'+suffix),'--create','--file',jar,'--manifest',manifest,'-C',classes,'.']);run('help',[jdk/('java'+suffix),'-jar',jar,'--help'])
  if b'SKIP3' not in (out/'help.stdout').read_bytes() or b'Required action:' not in (out/'help.stdout').read_bytes():raise ValueError('Prominent skip/recovery guidance absent')
  if any(pin(ROOT/n)!=w for n,w in sources.items()):raise ValueError('Build sources changed')
  save(out/'BUILD.json',dict(status='READY_COMPLETE_CORRECTBLEACH210_AND_NATIVE_HISTOGRAM_WORKFLOW',sources=sources,versions=versions,calls=calls,artifact=pin(jar),original_runtime_bundled=False,original_source_patched=False,paper_method_inferred=False,new_tool_credit=0,whole_goal_complete=False));shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt');(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('READY_COMPLETE_CORRECTBLEACH210_AND_NATIVE_HISTOGRAM_WORKFLOW',jar,flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_BUILD_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_CORRECTBLEACH210_BUILD',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',v);(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
