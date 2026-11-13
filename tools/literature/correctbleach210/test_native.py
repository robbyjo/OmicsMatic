#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every original histogram lookup, CDF raw bit and exception with the native Java port."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,time,traceback
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
ACTION='Required action: provide permitted JDK21/25, the exact published original2.1.0 --reference-plugin and NEW output. These controls execute all1048 complete original lookup/CDF cases and the native Java implementation. Missing/forbidden prerequisites SKIP3 skip dependent analysis. Keep all original binary values, logs and FAIL2 diagnostics; never truncate bins, replace NaN/Inf or patch original behavior. Histogram matching is inappropriate for intensity quantification. No paper reproduction or whole-tool qualification is claimed.'
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--reference-plugin',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;jdk=a.java_bin.resolve();plugin=a.reference_plugin.resolve();suffix='.exe' if os.name=='nt' else '';env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']}
  if pin(plugin)!=dict(bytes=19065,sha256='65b861ebae641b2aaa235e296f42afdb948d356bbddec92d2808f0c40da1174a'):raise ValueError('Published original release identity differs')
  before={str(n):pin(n) for n in [plugin,HERE/'OriginalHistogramControl.java',HERE/'NativeHistogramControl.java',HERE/'test_native.py',ROOT/'src/main/java/org/jlinalg/imaging/CorrectBleachHistogram210.java']}
  def run(label,args):
   command=list(map(str,args));start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/(label+'.live.json'),dict(command=command,pid=child.pid,started_unix=start))
    try:code=child.wait(timeout=600)
    except subprocess.TimeoutExpired:child.kill();code=child.wait(timeout=30);save(out/(label+'.process.json'),dict(label=label,command=command,pid=child.pid,exit_code=code,timed_out=True));raise ValueError('Original/native control time limit exceeded: '+label)
   row=dict(label=label,command=command,pid=child.pid,exit_code=code,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if code:raise ValueError('Complete original/native histogram control failed: '+label)
  original=out/'original-classes';original.mkdir();native=out/'native-classes';native.mkdir();flags=['--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-proc:none','-sourcepath','']
  run('compile-original',[jdk/('javac'+suffix),*flags,'-cp',plugin,'-d',original,HERE/'OriginalHistogramControl.java']);run('compile-native',[jdk/('javac'+suffix),*flags,'-d',native,ROOT/'src/main/java/org/jlinalg/imaging/CorrectBleachHistogram210.java',HERE/'NativeHistogramControl.java']);run('complete-original',[jdk/('java'+suffix),'-Xmx1g','-cp',str(original)+os.pathsep+str(plugin),'OriginalHistogramControl',out/'complete-original.bin']);run('complete-native',[jdk/('java'+suffix),'-Xmx1g','-cp',native,'NativeHistogramControl',out/'complete-original.bin'])
  expected=b'PASS_COMPLETE_ORIGINAL_HISTOGRAM_NATIVE_PARITY cases=1048 lookup_rows=212763 cdf_values=426993'
  if expected not in (out/'complete-native.stdout').read_bytes() or any(pin(Path(n))!=wanted for n,wanted in before.items()):raise ValueError('Complete original/native population or source changed')
  result=dict(status='PASS_COMPLETE_ORIGINAL_CORRECTBLEACH210_HISTOGRAM_REFERENCE_AND_NATIVE',cases=1048,complete_lookup_rows=212763,complete_cdf_raw_values=426993,calls=calls,sources=before,reference=pin(out/'complete-original.bin'),original_published_plugin_unmodified=True,original_exceptions_preserved=True,comparison_budget_tested=True,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;result=dict(status='SKIP3_HISTOGRAM_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_CORRECTBLEACH210_NATIVE_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
