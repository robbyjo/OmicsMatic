#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check twelve setup-policy, prerequisite, source, dependency and protected-path refusals."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,traceback
HERE=Path(__file__).resolve().parent
ACTION='Required action: provide permitted JDK21/25, complete pristine --source-root and --dependency-root, and NEW output. Setup failures must skip dependent analysis with prominent recovery guidance. These controls do not install software or launch image analysis; they preserve original sources, compiler and prior output. Retain FAIL2/SKIP3 diagnostics and resolve the stated cause before retrying NEW paths.'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--source-root',required=True,type=Path);p.add_argument('--dependency-root',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;source=a.source_root.resolve();deps=a.dependency_root.resolve();jdk=a.java_bin.resolve();profile=json.loads((HERE/'source-profile.json').read_bytes());before={str(source/n):pin(source/n) for n in profile['source_members']};before.update({str(deps/r['path']):pin(deps/r['path']) for r in profile['dependency_artifacts']});before[str(HERE/'provider.py')]=pin(HERE/'provider.py');env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']}
  def run(label,flags,expected,target=None):
   command=list(map(str,[sys.executable,HERE/'provider.py',*flags]));r=subprocess.run(command,env=env,capture_output=True,stdin=subprocess.DEVNULL,timeout=120);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode!=expected or b'Required action:' not in r.stdout+r.stderr:raise ValueError('Setup refusal or prominent recovery guidance differs: '+label)
   if target is not None and (target/'FAILURE.json').is_file():
    failed=json.loads((target/'FAILURE.json').read_bytes())
    if any(c['label'] in ['original-javac','original-jar','readiness'] for c in failed['calls']):raise ValueError('Refused setup launched dependent original compiler/readiness')
  base=['--java-bin',jdk];local=['--source-root',source,'--dependency-root',deps]
  def own(label,flags,expected):target=out/label;run(label,[*flags,'--out',target],expected,target)
  own('build-forbidden',[*base,*local],3);own('download-forbidden',[*base,'--allow-build'],3);own('missing-jdk',['--java-bin',out/'absent-jdk',*local,'--allow-build'],3);own('missing-source',[*base,'--source-root',out/'absent-source','--dependency-root',deps,'--allow-build'],3);own('missing-dependency',[*base,'--source-root',source,'--dependency-root',out/'absent-dependency','--allow-build'],3)
  incomplete=out/'incomplete-source-fixture';incomplete.mkdir();(incomplete/'README.txt').write_text('Incomplete source refusal fixture\n',encoding='utf8',newline='\n');own('incomplete-source',[*base,'--source-root',incomplete,'--dependency-root',deps,'--allow-build'],2)
  corrupt=out/'corrupt-source-fixture';shutil.copytree(source,corrupt);first=corrupt/next(iter(profile['source_members']));first.write_bytes(first.read_bytes()+b'\n');own('corrupt-source',[*base,'--source-root',corrupt,'--dependency-root',deps,'--allow-build'],2)
  corruptDeps=out/'corrupt-dependency-fixture';corruptDeps.mkdir()
  for row in profile['dependency_artifacts']:shutil.copyfile(deps/row['path'],corruptDeps/row['path'])
  first=corruptDeps/profile['dependency_artifacts'][0]['path'];first.write_bytes(first.read_bytes()+b'\0');own('corrupt-dependency',[*base,'--source-root',source,'--dependency-root',corruptDeps,'--allow-build'],2)
  for label,path in [('source-overlap',source),('compiler-overlap',jdk),('dependency-overlap',deps)]:
   protected=path/'not-created-by-correctbleach-refusal';assert not protected.exists();run(label,[*base,*local,'--allow-build','--out',protected],2);assert not protected.exists()
  existing=out/'existing-output';existing.mkdir();sentinel=existing/'retained.txt';sentinel.write_bytes(b'Prior output must remain unchanged\n');wanted=pin(sentinel);run('existing-output',[*base,*local,'--allow-build','--out',existing],3);assert list(existing.iterdir())==[sentinel] and pin(sentinel)==wanted
  if len(calls)!=12 or any(pin(Path(n))!=wanted for n,wanted in before.items()):raise ValueError('Setup control population or original inputs changed')
  result=dict(status='PASS_CORRECTBLEACH210_SETUP_POLICY_SOURCE_DEPENDENCY_AND_PATH_REFUSALS',calls=calls,setup_refusals=12,image_analysis_executed=False,dependent_original_compiler_launched=False,sources_preserved=before);save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  result=dict(status='FAIL2_CORRECTBLEACH210_SETUP_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(e),'\n'+ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
