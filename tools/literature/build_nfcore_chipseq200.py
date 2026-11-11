# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the source-specific multi-image execution control without modifying predecessors/releases."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25

SOURCES=['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['NfcoreContainerSet','NfcoreChipseq200SourceCli']]
RESOURCE='src/main/resources/org/jlinalg/omics/nfcore-chipseq200/images.json'
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['21','25'],required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');state=dict(status='FAIL2_CHIPSEQ200_SOURCE_CONTROL_BUILD',new_tool_credit=0)
 try:
  parent=ROOT/f'docs/literature/nfcore-chipseq121-development/nfcore-chipseq-javac{a.compiler}.jar';proof=json.loads((parent.parent/'VALIDATION.json').read_bytes());assert pin(parent)==proof['artifacts'][parent.name];guards={n:pin(ROOT/n) for n in [*SOURCES,RESOURCE,Path(__file__).resolve().relative_to(ROOT).as_posix()]}
  for n in guards:dest=out/'sources'/n;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/n,dest)
  classes=out/'classes';classes.mkdir();java=JAVA if a.compiler=='21' else JAVA25;cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(parent),'-d',str(classes),*[str(ROOT/n) for n in SOURCES]];r=subprocess.run(cmd,capture_output=True,timeout=120);(out/'javac.stdout').write_bytes(r.stdout);(out/'javac.stderr').write_bytes(r.stderr);save(out/'javac.process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,r.stderr
  with zipfile.ZipFile(parent) as z:old={n:z.read(n) for n in z.namelist()}
  payload=dict(old)
  for file in classes.rglob('*.class'):name=file.relative_to(classes).as_posix();assert name not in payload;payload[name]=file.read_bytes()
  payload['org/jlinalg/omics/nfcore-chipseq200/images.json']=(ROOT/RESOURCE).read_bytes()
  for n in SOURCES:payload['META-INF/nfcore-chipseq200/sources/'+n]=(ROOT/n).read_bytes()
  payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.NfcoreChipseq200SourceCli\n\n';payload['META-INF/nfcore-chipseq200/NOTICE']=b'Original locked ChIP-seq2.0.0 source execution/namespace control only. Pinned observed bytes for all 33 original source image URIs and complete module/alias runtime binding. Original source and scientific defaults remain upstream. Native 2.0 output contracts and complete nf-core family admission remain required. Zero catalog-tool or biological admission. Original predecessor members except executable manifest remain exact. Own code GPL-3.0-or-later. Original nf-core MIT and predecessor notices retained.\n';artifact=out/'nfcore-chipseq200-source-control.jar'
  with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for n,raw in sorted(payload.items()):item=zipfile.ZipInfo(n,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
  with zipfile.ZipFile(artifact) as z:assert all(z.read(n)==raw for n,raw in old.items() if n!='META-INF/MANIFEST.MF')
  assert all(pin(ROOT/n)==v for n,v in guards.items());state.update(status='PASS_CHIPSEQ200_SOURCE_CONTROL_BUILD_ONLY',source_pins=guards,predecessor=pin(parent),artifact=pin(artifact),compiler=a.compiler,all_predecessor_members_except_manifest_unchanged=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source/runtime/predecessor identity checks')
 raise SystemExit(main())
