#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check every original C++ coefficient with the native Java implementation; no R execution."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,traceback
ROOT=Path(__file__).resolve().parents[3]
ACTION='Required action: provide permitted JDK21/25 and the complete original coefficient-inputs.tsv/coefficient-original.tsv bank, then retry NEW output. Missing compiler/reference prerequisites SKIP3 skip dependent checks. Retain FAIL2 diagnostics and resolve complete source/numeric differences without changing original controls. This validates the original 0.99.1 integer-neighbor helper only, not RANN, Louvain, paper version0.1.1 or biological results.'
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;classes=out/'classes';classes.mkdir();suffix='.exe' if os.name=='nt' else '';sources=[ROOT/'src/main/java/org/jlinalg/singlecell/RphenographJaccard099.java',ROOT/'tools/literature/rphenograph099/SourceControl.java'];files=sources+[a.reference.resolve()/n for n in ['coefficient-inputs.tsv','coefficient-original.tsv']];before={str(n):pin(n) for n in files}
  for name,args in [('compile',[a.java_bin.resolve()/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-d',classes,*sources]),('original-comparison',[a.java_bin.resolve()/('java'+suffix),'-cp',classes,'SourceControl',a.reference.resolve()])]:
   command=list(map(str,args));r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);calls.append(dict(label=name,command=command,exit_code=r.returncode))
   if r.returncode:raise ValueError('Complete native comparison failed: '+name)
  if b'cases=863 rows=105921 assertions=637388' not in (out/'original-comparison.stdout').read_bytes():raise ValueError('Incomplete original comparison population')
  if any(pin(Path(n))!=v for n,v in before.items()):raise ValueError('Source/reference changed during comparison')
  save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_ORIGINAL_RPHENOGRAPH099_CPP_PARITY',cases=863,complete_rows=105921,assertions=637388,calls=calls,source_and_reference_pins=before,original_R_execution_repeated=False,paper_package_identity_recovered=False,whole_goal_complete=False));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('PASS_COMPLETE_ORIGINAL_RPHENOGRAPH099_CPP_PARITY',flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2
  if owned:save(out/'FAILURE.json',dict(status='SKIP3_COEFFICIENT_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_RPHENOGRAPH099_COEFFICIENT',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION));(out/'WARNINGS_FAILURE.md').write_text(repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print('SKIP3' if code==3 else 'FAIL2',repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
