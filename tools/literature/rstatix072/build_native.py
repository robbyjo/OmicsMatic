#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Strict standalone native Levene build, with exact existing project JDistlib classes."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,traceback,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
ACTION='Required action: supply permitted matching JDK21/25, a JAR containing the exact existing project JDistlib dependency classes and NEW output. Missing prerequisites SKIP3 skip dependent build/analysis. FAIL2 retains logs and admits no build; correct the named source/dependency cause and retry NEW paths. Native Levene needs no R installation. This is a prepared-vector mathematical test, not a general R formula/car/rstatix implementation or catalog admission.'
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',type=Path,required=True);p.add_argument('--dependency-jar',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  jdk=a.java_bin.resolve();dep=a.dependency_jar.resolve()
  for protected in [jdk,dep,ROOT/'src',HERE]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected source/compiler/dependency')
  out.mkdir(parents=True,exist_ok=False);owned=True;suffix='.exe' if os.name=='nt' else ''
  java=[ROOT/'src/main/java/org/jlinalg/stats/LeveneTest.java']+[ROOT/'src/main/java/org/jlinalg/cli'/(n+'.java') for n in ['LeveneTestCli','SimpleJson','NextflowJson','NextflowParameters']]
  sources=[*java,HERE/'build_native.py',HERE/'native-dependency-profile.json',ROOT/'LICENSE'];pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources};dep_pin=pin(dep)
  for source in sources:
   dest=out/'source-snapshot'/source.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
  profile=json.loads((HERE/'native-dependency-profile.json').read_bytes());expected=profile['members']
  with zipfile.ZipFile(dep) as archive:
   names=[n for n in archive.namelist() if n.startswith('jdistlib/') and not n.endswith('/')]
   if len(names)!=len(set(names)) or set(names)!=set(expected):raise ValueError('Complete original JDistlib class population differs')
   if any(rawpin(archive.read(n))!=wanted for n,wanted in expected.items()):raise ValueError('Existing project numerical dependency differs')
  env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']}
  def run(label,command):
   command=list(map(str,command));r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode:raise ValueError('Actual strict standalone build failed: '+label)
   return (r.stdout+r.stderr).decode(errors='replace')
  versions={n:run(n+'-version',[jdk/(n+suffix),'--version']) for n in ['java','javac','jar']}
  if any(not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',v) for v in versions.values()):raise OSError('Permitted JDK21/25 required')
  classes=out/'classes';classes.mkdir();run('compile',[jdk/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-proc:none','-sourcepath','','-cp',dep,'-d',classes,*java])
  with zipfile.ZipFile(dep) as archive:
   for name in expected:
    dest=classes/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(archive.read(name))
  resource=classes/'org/jlinalg/stats/levene';resource.mkdir(parents=True);shutil.copyfile(HERE/'native-dependency-profile.json',resource/'native-dependency-profile.json');shutil.copyfile(ROOT/'LICENSE',resource/'LICENSE-project-GPL3.txt')
  jar=out/'levene-native-development.jar';run('package',[jdk/('jar'+suffix),'--create','--file',jar,'--main-class','org.jlinalg.cli.LeveneTestCli','-C',classes,'.']);run('help',[jdk/('java'+suffix),'-jar',jar,'--help'])
  help=(out/'help.stdout').read_bytes()
  if any(x not in help for x in [b'SKIP3',b'FAIL2',b'Required action:']):raise ValueError('Prominent recovery guidance missing')
  if pin(dep)!=dep_pin or any(pin(ROOT/n)!=wanted for n,wanted in pins.items()):raise ValueError('Protected build inputs changed')
  save(out/'BUILD.json',dict(status='READY_NATIVE_JAVA_LEVENE_NOT_SCIENTIFIC_QUALIFICATION',artifact=pin(jar),sources=pins,dependency_jar=dep_pin,complete_numerical_dependency_members=len(expected),versions=versions,calls=calls,canonical_changed=False,original_R_analysis_executed=False,new_tool_credit=0,whole_goal_complete=False));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('READY_NATIVE_JAVA_LEVENE_NOT_SCIENTIFIC_QUALIFICATION',flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_NATIVE_LEVENE_BUILD_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_NATIVE_LEVENE_BUILD',cause=repr(error),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
