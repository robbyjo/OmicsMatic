#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the dependency-free native Java palette library without replacing the canonical executable."""
from pathlib import Path
import argparse,hashlib,json,os,re,subprocess,traceback
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
ACTION='Required action: provide permitted JDK21/25 and NEW output. Missing/forbidden compiler prerequisites SKIP3 skip the dependent build. FAIL2 retains diagnostics and admits no partial library; resolve the source/compiler cause and retry NEW paths. Java color generation needs no R runtime or package installation. This library does not implement original R plotting devices or infer paper palette/category assignments.'
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  jdk=a.java_bin.resolve()
  if out.is_relative_to(jdk) or jdk.is_relative_to(out):raise ValueError('Output overlaps protected compiler')
  out.mkdir(parents=True,exist_ok=False);owned=True;source=ROOT/'src/main/java/org/jlinalg/plot/WesAnderson037.java';cli=ROOT/'src/main/java/org/jlinalg/cli/WesAnderson037PaletteCli.java';notices=sorted((HERE/'licenses').iterdir());paths=[source,cli,HERE/'source-profile.json',HERE/'build.py',*notices];before={p.relative_to(ROOT).as_posix():pin(p) for p in paths};suffix='.exe' if os.name=='nt' else '';env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']}
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=180);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);calls.append(dict(label=label,command=command,exit_code=r.returncode));save(out/(label+'.process.json'),calls[-1])
   if r.returncode:raise ValueError('Native library build failed: '+label)
   return (r.stdout+r.stderr).decode('utf8',errors='replace')
  java=run('java-version',[jdk/('java'+suffix),'-version']);compiler=run('javac-version',[jdk/('javac'+suffix),'-version']);major=re.search(r'(?:openjdk|java) version "(\d+)',java);compileMajor=re.search(r'javac (\d+)',compiler)
  if not major or not compileMajor or int(major.group(1)) not in [21,25] or major.group(1)!=compileMajor.group(1):raise OSError('Supply a qualified matching JDK21/25 compiler and runtime')
  profile=json.loads((HERE/'source-profile.json').read_bytes())
  for row in profile['license_sources']:
   if pin(HERE/'licenses'/row['path'])!={k:row[k] for k in ['bytes','sha256']}:raise ValueError('Official license differs: '+row['path'])
  classes=out/'classes';classes.mkdir();run('compile',[jdk/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-proc:none','-sourcepath','','-d',classes,source,cli]);resources=classes/'org/jlinalg/plot/wesanderson037';resources.mkdir(parents=True);(resources/'source-profile.json').write_bytes((HERE/'source-profile.json').read_bytes());licenseDir=resources/'licenses';licenseDir.mkdir()
  for path in notices:(licenseDir/path.name).write_bytes(path.read_bytes())
  (resources/'implementation-SHA256SUMS').write_text(''.join(row['sha256']+'  '+name+'\n' for name,row in sorted(before.items())),encoding='utf8',newline='\n')
  jar=out/'wesanderson037-native-development.jar';run('jar',[jdk/('jar'+suffix),'--create','--file',jar,'--main-class','org.jlinalg.cli.WesAnderson037PaletteCli','-C',classes,'.'])
  if any(pin(ROOT/n)!=wanted for n,wanted in before.items()):raise ValueError('Build source changed')
  for name in before:
   target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
  save(out/'BUILD.json',dict(status='READY_NATIVE_WESANDERSON037_JAVA_LIBRARY',source_pins=before,artifact=pin(jar),calls=calls,compiled_target=17,qualified_JDK_major=int(major.group(1)),java_version=java,compiler_version=compiler,original_R_runtime_required_for_native_generation=False,original_R_graphics_ported=False,whole_goal_complete=False,new_tool_credit=0));print('READY_NATIVE_WESANDERSON037_JAVA_LIBRARY',flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;result=dict(status='SKIP3_NATIVE_BUILD_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_WESANDERSON037_BUILD',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
