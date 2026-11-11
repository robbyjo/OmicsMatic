# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build original v2 library-complexity checks while preserving all published scientific predecessors."""
import argparse,json,subprocess,traceback,zipfile,shutil
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25

SOURCES=['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['NfcoreChipseq200Complexity','NfcoreChipseq200ComplexityAuditCli']]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['21','25'],required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_CHIPSEQ200_COMPLEXITY_AUDIT_BUILD',new_tool_credit=0)
 try:
  parent=BASE/f'chipseq200-peaks-build{a.compiler}-1/nfcore-chipseq200-peaks.jar';build=json.loads((parent.parent/'COMPLETED.json').read_bytes());assert pin(parent)==build['artifact'] and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());guards={n:pin(ROOT/n) for n in [*SOURCES,Path(__file__).resolve().relative_to(ROOT).as_posix()]};classes=out/'classes';classes.mkdir()
  for n in guards:dest=out/'sources'/n;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/n,dest)
  java=JAVA if a.compiler=='21' else JAVA25;cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(parent),'-d',str(classes),*[str(ROOT/n) for n in SOURCES]];r=subprocess.run(cmd,capture_output=True,timeout=90);(out/'javac.stderr').write_bytes(r.stderr);(out/'javac.stdout').write_bytes(r.stdout);save(out/'javac.process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,r.stderr
  with zipfile.ZipFile(parent) as z:old={n:z.read(n) for n in z.namelist()}
  payload=dict(old)
  for f in classes.rglob('*.class'):n=f.relative_to(classes).as_posix();assert n not in payload;payload[n]=f.read_bytes()
  for n in SOURCES:payload['META-INF/nfcore-chipseq200-complexity/sources/'+n]=(ROOT/n).read_bytes()
  payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.NfcoreChipseq200ComplexityAuditCli\n\n';artifact=out/'nfcore-chipseq200-complexity.jar'
  with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for n,raw in sorted(payload.items()):item=zipfile.ZipInfo(n,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
  with zipfile.ZipFile(artifact) as z:assert all(z.read(n)==v for n,v in old.items() if n!='META-INF/MANIFEST.MF')
  assert all(pin(ROOT/n)==v for n,v in guards.items());state.update(status='PASS_CHIPSEQ200_COMPLEXITY_AUDIT_BUILD_ONLY',artifact=pin(artifact),predecessor=pin(parent),source_pins=guards,compiler=a.compiler,all_predecessor_members_except_manifest_unchanged=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source/predecessor identity checks')
 raise SystemExit(main())
