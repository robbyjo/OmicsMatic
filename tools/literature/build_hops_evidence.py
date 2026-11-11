# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the separate native HOPS statistics component; preserve every predecessor member except executable manifest."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25
SOURCES=['src/main/java/org/jlinalg/metagenomics/HopsEvidence.java','src/main/java/org/jlinalg/metagenomics/HopsRScalar.java','src/main/java/org/jlinalg/cli/HopsEvidenceCli.java']

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['21','25'],required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');state=dict(status='FAIL2_HOPS_EVIDENCE_COMPONENT_BUILD',new_tool_credit=0,full_hops_adapter_admitted=False)
 try:
  parent=ROOT/f'docs/literature/nfcore-chipseq200-development/nfcore-chipseq200-javac{a.compiler}.jar';proof=json.loads((parent.parent/'VALIDATION.json').read_bytes());assert pin(parent)==proof['artifacts'][parent.name];guards={name:pin(ROOT/name) for name in [*SOURCES,Path(__file__).resolve().relative_to(ROOT).as_posix()]}
  for name in guards:target=out/'sources'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/name,target)
  classes=out/'classes';classes.mkdir();java=JAVA if a.compiler=='21' else JAVA25;cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(parent),'-d',str(classes),*[str(ROOT/name) for name in SOURCES]];r=subprocess.run(cmd,capture_output=True,timeout=120);(out/'javac.stdout').write_bytes(r.stdout);(out/'javac.stderr').write_bytes(r.stderr);save(out/'javac.process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,r.stderr
  with zipfile.ZipFile(parent) as z:old={name:z.read(name) for name in z.namelist()}
  payload=dict(old)
  for path in classes.rglob('*.class'):name=path.relative_to(classes).as_posix();assert name not in payload;payload[name]=path.read_bytes()
  for name in SOURCES:payload['META-INF/hops-evidence/sources/'+name]=(ROOT/name).read_bytes()
  payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.HopsEvidenceCli\n\n';payload['META-INF/hops-evidence/NOTICE']=b'Own native data/statistics implementation GPL-3.0-or-later. Algorithm attribution: Felix M. Key, keyfm/amps postprocessing.AMPS.r SHA2567912dfcede0084134d1d677368f851a4d55dadeb0b7eaa591fcfe2e9c6cbdf62, commit9d52ae03f4ca474332af3aba8a71d872d886aefb. Upstream README states CC BY without a version: https://github.com/keyfm/amps/tree/9d52ae03f4ca474332af3aba8a71d872d886aefb. Cite Huebler, Key et al. Genome Biology20,280(2019), doi:10.1186/s13059-019-1903-0. Original HOPS0.35 package ships GPLv3. Restricted rounding follows R4.0.5 src/nmath/fround.c, Copyright(C)2000-2020 The R Core Team and Copyright(C)1998 Ross Ihaka, GPL-2.0-or-later, distributed here under GPL-3.0-or-later. Own code implements def_anc numeric statistics and matrices, not original PDFs/RData/default-mode behavior or MALT/LCA/MaltExtract. Original predecessor classes and notices retained unchanged. No tool/biological admission.\n';artifact=out/'hops-evidence.jar'
  with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,raw in sorted(payload.items()):item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
  with zipfile.ZipFile(artifact) as z:assert all(z.read(name)==raw for name,raw in old.items() if name!='META-INF/MANIFEST.MF')
  assert all(pin(ROOT/name)==value for name,value in guards.items());state.update(status='PASS_NATIVE_HOPS_EVIDENCE_COMPONENT_BUILD_ONLY',source_pins=guards,predecessor=pin(parent),artifact=pin(artifact),compiler=a.compiler,all_predecessor_members_except_manifest_unchanged=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source/predecessor checks')
 raise SystemExit(main())
