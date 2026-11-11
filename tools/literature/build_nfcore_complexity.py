# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build separate native Picard numerical library and complete captured-workflow complexity auditor."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25

SOURCES=['src/main/java/org/jlinalg/sequence/PicardLibraryComplexity.java','src/main/java/org/jlinalg/cli/NfcoreChipseq121Complexity.java','src/main/java/org/jlinalg/cli/NfcoreComplexityAuditCli.java']

def jar(path,payload):
 with zipfile.ZipFile(path,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,raw in sorted(payload.items()):
   item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['21','25'],required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');state=dict(status='FAIL2_NATIVE_COMPLEXITY_BUILD',new_tool_credit=0)
 try:
  parent=ROOT/f'docs/literature/nfcore-deseq126-development/nfcore-deseq126-javac{a.compiler}.jar';proof=json.loads((parent.parent/'VALIDATION.json').read_bytes());assert pin(parent)==proof['artifacts'][parent.name]
  resources=sorted((ROOT/'src/main/resources/org/jlinalg/omics/picard2231').iterdir());sources=[ROOT/n for n in SOURCES]
  names=[*sources,*resources,Path(__file__).resolve()];source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in names if p.is_file()}
  for name in source_pins:
   target=out/'sources'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/name,target)
  classes=out/'classes';classes.mkdir();java=JAVA if a.compiler=='21' else JAVA25;cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(parent),'-d',str(classes),*map(str,sources)]
  r=subprocess.run(cmd,capture_output=True,timeout=120);(out/'javac.stdout').write_bytes(r.stdout);(out/'javac.stderr').write_bytes(r.stderr);save(out/'javac.process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,r.stderr
  meta={}
  for p in resources:
   if p.is_file():meta['org/jlinalg/omics/picard2231/'+p.name]=p.read_bytes()
  for p in sources:meta['META-INF/picard2231/sources/'+p.relative_to(ROOT).as_posix()]=p.read_bytes()
  meta['META-INF/picard2231/GPL-3']=(ROOT/'src/main/resources/org/jlinalg/source-numerics/GPL-3').read_bytes()
  own={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
  small={n:v for n,v in own.items() if n.startswith('org/jlinalg/sequence/PicardLibraryComplexity')};small.update(meta);small['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\n\n';jar(out/'picard-complexity-library.jar',small)
  with zipfile.ZipFile(parent) as z:old={n:z.read(n) for n in z.namelist()}
  assert not (set(own)|set(meta))&set(old);payload=dict(old);payload.update(own);payload.update(meta);payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.NfcoreComplexityAuditCli\n\n';jar(out/'nfcore-complexity-development.jar',payload)
  with zipfile.ZipFile(out/'nfcore-complexity-development.jar') as z:assert all(z.read(n)==v for n,v in old.items() if n!='META-INF/MANIFEST.MF')
  assert all(pin(ROOT/n)==v for n,v in source_pins.items());state.update(status='PASS_NATIVE_COMPLEXITY_BUILD_ONLY',compiler=a.compiler,source_pins=source_pins,predecessor=pin(parent),all_predecessor_members_except_manifest_unchanged=True,artifacts={name:pin(out/name) for name in ['picard-complexity-library.jar','nfcore-complexity-development.jar']},native_classes={n:dict(bytes=len(v),sha256=__import__('hashlib').sha256(v).hexdigest()) for n,v in own.items()})
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable inherited-source or complete byte checks')
 raise SystemExit(main())
