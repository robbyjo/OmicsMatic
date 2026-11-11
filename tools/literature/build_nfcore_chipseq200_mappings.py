# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a dependency-free native source-specific samplesheet inspection library."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25

SOURCE='src/main/java/org/jlinalg/workflow/NfcoreChipseq200Mappings.java'
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['21','25'],required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_NATIVE_CHIPSEQ200_MAPPING_BUILD',new_tool_credit=0)
 try:
  license_file=ROOT/'src/main/resources/org/jlinalg/omics/nfcore-chipseq200/MIT-nf-core.txt';guards={n:pin(ROOT/n) for n in [SOURCE,license_file.relative_to(ROOT).as_posix(),Path(__file__).resolve().relative_to(ROOT).as_posix()]};classes=out/'classes';classes.mkdir();java=JAVA if a.compiler=='21' else JAVA25;cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-d',str(classes),str(ROOT/SOURCE)];r=subprocess.run(cmd,capture_output=True,timeout=90);(out/'javac.stdout').write_bytes(r.stdout);(out/'javac.stderr').write_bytes(r.stderr);save(out/'javac.process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,r.stderr
  payload={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\n\n';payload['META-INF/nfcore-chipseq200/sources/'+SOURCE]=(ROOT/SOURCE).read_bytes();payload['META-INF/nfcore-chipseq200/MIT-nf-core.txt']=license_file.read_bytes()
  with zipfile.ZipFile(ROOT/'docs/literature/nfcore-chipseq121-development/nfcore-chipseq-javac21.jar') as parent:payload['META-INF/nfcore-chipseq200/GPL-3']=parent.read('META-INF/picard2231/GPL-3')
  payload['META-INF/nfcore-chipseq200/NOTICE']=b'Own native samplesheet inspection/API GPL-3.0-or-later. Source contract derived from the locked nf-core/chipseq2.0.0 MIT check_samplesheet.py. Upstream MIT and GPL-3 text retained. Inspection preserves complete raw cells, source normalization ledger, scalar sorting and technical-run IDs; it does not rewrite original input, infer physical FASTQ identity, or admit a biological/library graph. Consumers must select the literal-ID and consistent-metadata policies explicitly before analysis.\n';artifact=out/'nfcore-chipseq200-mappings.jar'
  with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,raw in sorted(payload.items()):item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
  assert all(pin(ROOT/n)==v for n,v in guards.items());state.update(status='PASS_NATIVE_CHIPSEQ200_MAPPING_BUILD_ONLY',source_pins=guards,artifact=pin(artifact),compiler=a.compiler,external_runtime_required=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source identity checks')
 raise SystemExit(main())
