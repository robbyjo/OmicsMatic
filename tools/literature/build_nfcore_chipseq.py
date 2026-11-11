# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the typed original ChIP-seq1.2.1 runner without changing the released executable or predecessors."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25

SOURCES=['src/main/java/org/jlinalg/cli/'+name+'.java' for name in ['NfcoreChipseq121Annotations','NfcoreAnnotationAuditCli','NfcoreChipseqRequest','NfcoreChipseqBundle','NfcoreChipseqWorkflow','NfcoreChipseqToolsCli']]+['src/main/java/org/jlinalg/sequence/HomerPeakAnnotations.java']
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['21','25'],required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');state=dict(status='FAIL2_CHIPSEQ_RUNNER_BUILD',new_tool_credit=0)
 try:
  parent=ROOT/f'docs/literature/nfcore-complexity-development/nfcore-complexity-javac{a.compiler}.jar';old_proof=json.loads((parent.parent/'VALIDATION.json').read_bytes());assert pin(parent)==old_proof['artifacts'][parent.name]
  sources=[ROOT/n for n in SOURCES];source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [*sources,Path(__file__).resolve()]}
  for name in source_pins:
   target=out/'sources'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/name,target)
  classes=out/'classes';classes.mkdir();java=JAVA if a.compiler=='21' else JAVA25;cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(parent),'-d',str(classes),*map(str,sources)]
  r=subprocess.run(cmd,capture_output=True,timeout=120);(out/'javac.stdout').write_bytes(r.stdout);(out/'javac.stderr').write_bytes(r.stderr);save(out/'javac.process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,r.stderr
  with zipfile.ZipFile(parent) as z:old={n:z.read(n) for n in z.namelist()}
  payload=dict(old)
  captured=ROOT/f'docs/literature/nfcore-captured-development/nfcore-captured-javac{a.compiler}.jar';captured_proof=json.loads((captured.parent/'VALIDATION.json').read_bytes());assert pin(captured)==captured_proof['artifacts'][captured.name]
  captured_classes={}
  with zipfile.ZipFile(captured) as z:
   for family in ['org/jlinalg/cli/NfcoreCapturedToolsCli','org/jlinalg/cli/NfcoreCapturedWorkflow']:
    names=[n for n in z.namelist() if n==family+'.class' or n.startswith(family+'$') and n.endswith('.class')];assert family+'.class' in names
    for name in names:assert name not in payload;payload[name]=z.read(name);captured_classes[name]=dict(bytes=len(payload[name]),sha256=__import__('hashlib').sha256(payload[name]).hexdigest())
  for p in classes.rglob('*.class'):
   name=p.relative_to(classes).as_posix();assert name not in payload;payload[name]=p.read_bytes()
  for p in sources:payload['META-INF/nfcore-chipseq121/sources/'+p.relative_to(ROOT).as_posix()]=p.read_bytes()
  payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.NfcoreChipseqToolsCli\n\n';payload['META-INF/nfcore-chipseq121/NOTICE']=b'Original nf-core/chipseq 1.2.1 source workflow adapter with pinned managed Nextflow21.04.1 and private original image admission. Typed capture, original task/output completion and selected source consistency contracts are mandatory. Native supplied-GTF gene-ID lookup preserves the original last transcript row per contig/gene, source NA plotting semantics and complete QC statistics. Original programs perform alignment, peak calling, NB fitting and other upstream methods. No biological, independent whole-model, whole-paper or author-setting certification. All predecessor members except executable manifest remain byte-identical. Own code GPL-3.0-or-later; original nf-core MIT notices and predecessor notices retained.\n'
  artifact=out/'nfcore-chipseq-development.jar'
  with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,raw in sorted(payload.items()):
    item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
  with zipfile.ZipFile(artifact) as z:assert all(z.read(n)==v for n,v in old.items() if n!='META-INF/MANIFEST.MF')
  assert all(pin(ROOT/n)==v for n,v in source_pins.items());state.update(status='PASS_TYPED_CHIPSEQ_RUNNER_BUILD_ONLY',compiler=a.compiler,source_pins=source_pins,predecessor=pin(parent),artifact=pin(artifact),all_predecessor_members_except_manifest_unchanged=True,captured_predecessor=pin(captured),captured_classes=captured_classes)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source or inherited-byte checks')
 raise SystemExit(main())
