# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the separate coherent-process nf-core development candidate; preserve the frozen foundation."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/nfcore-runtime'

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['21','25'],required=True);a=p.parse_args()
 out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
 canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');state=dict(status='FAIL2_NFCORE_CAPTURED_BUILD',new_catalog_credit=False,full_nfcore_adapter=False)
 try:
  home=ROOT/'docs/literature/nfcore-engine-development';proof=json.loads((home/'VALIDATION.json').read_bytes());predecessor=home/'nfcore-engine-javac21.jar';assert pin(predecessor)==proof['artifacts'][predecessor.name]
  assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
  sources=[*(ROOT/'src/main/java/org/jlinalg/cli'/n for n in ['NfcoreProcess.java','NfcoreCapturedWorkflow.java','NfcoreCapturedToolsCli.java']),ROOT/'src/main/java/org/jlinalg/workflow/LinuxProcessSnapshot.java']
  source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [*sources,Path(__file__)]}
  for name in source_pins:
   target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/name,target)
  tools=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin') if a.compiler=='21' else Path('C:/Program Files/Common Files/Oracle/Java/javapath')
  classes=out/'classes';classes.mkdir();command=[str(tools/'javac.exe'),'--release','17','-sourcepath','','-encoding','UTF-8','-Xlint:all','-Werror','-cp',str(predecessor),'-d',str(classes),*map(str,sources)]
  r=subprocess.run(command,capture_output=True,timeout=120);(out/'javac.stdout').write_bytes(r.stdout);(out/'javac.stderr').write_bytes(r.stderr);save(out/'javac.process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr
  with zipfile.ZipFile(predecessor) as z:old={n:z.read(n) for n in z.namelist()}
  payload=dict(old);payload.update({p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')});payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.NfcoreCapturedToolsCli\n\n'
  artifact=out/'nfcore-captured-development.jar'
  with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,raw in sorted(payload.items()):
    item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
  with zipfile.ZipFile(artifact) as z:assert all(z.read(n)==b for n,b in old.items() if n!='META-INF/MANIFEST.MF')
  assert all(pin(ROOT/n)==v for n,v in source_pins.items())
  state.update(status='PASS_NFCORE_CAPTURED_BUILD_ONLY',compiler=a.compiler,source_pins=source_pins,artifact=dict(path=artifact.relative_to(ROOT).as_posix(),**pin(artifact)),predecessor=pin(predecessor),all_predecessor_scientific_classes_and_resources_byte_identical=True)
 except Exception as e:state.update(cause=repr(e),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable frozen-source, inherited-byte or canonical checks')
 raise SystemExit(main())
