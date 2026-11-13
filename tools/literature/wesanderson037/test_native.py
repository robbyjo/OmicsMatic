#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every original color, class/name attribute, palette entry and failure with native Java."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,time,traceback
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
ACTION='Required action: supply permitted JDK21/25, complete original 6108-case --reference and NEW output. Missing/forbidden prerequisites SKIP3 skip dependent comparison; FAIL2 retains every mismatch and admits no implementation parity. Never round, repair colors, truncate vectors or infer the paper palette/category assignments. This compares source-qualified scalar color generation, not R language coercions, original R plotting devices or biological validity.'
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;jdk=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else '';reference=a.reference.resolve();source=ROOT/'src/main/java/org/jlinalg/plot/WesAnderson037.java';files=[source,HERE/'NativeControl.java',HERE/'test_native.py',reference/'complete-original-cases.tsv',reference/'complete-original-palettes.tsv',reference/'COMPLETED.txt'];before={str(n):pin(n) for n in files};save(out/'BEFORE.json',before)
  if (reference/'case-count.txt').read_text().strip()!='6108' or len((reference/'complete-original-cases.tsv').read_text(encoding='utf8').splitlines())!=6108:raise ValueError('Incomplete original case population')
  classes=out/'classes';classes.mkdir();env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']}
  def run(label,args):
   command=list(map(str,args));start=time.time();r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=180);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode:raise ValueError('Complete original/native control failed at '+label)
  run('compile',[jdk/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-proc:none','-sourcepath','','-d',classes,source,HERE/'NativeControl.java']);run('complete-native',[jdk/('java'+suffix),'-Xmx512m','-cp',classes,'NativeControl',reference/'complete-original-cases.tsv',reference/'complete-original-palettes.tsv'])
  if any(pin(Path(n))!=wanted for n,wanted in before.items()):raise ValueError('Source/reference changed during complete comparison')
  result=dict(status='PASS_COMPLETE_ORIGINAL_WESANDERSON037_NATIVE',source_pins=before,calls=calls,original_case_population=6108,all_returned_colors_compared=True,all_returned_name_class_attributes_compared=True,all_original_failures_compared=True,all24_original_palettes_compared=True,original_R_analysis_repeated=False,paper_palette_inferred=False,whole_goal_complete=False,new_tool_credit=0);save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;result=dict(status='SKIP3_NATIVE_COMPARISON_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_WESANDERSON037_NATIVE_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
