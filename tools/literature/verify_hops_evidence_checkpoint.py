# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify saved native HOPS evidence and replay both native artifacts without original R/HOPS runtimes."""
import argparse,hashlib,io,json,lzma,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA
from verify_nfcore_eager_screening_checkpoint import extract

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['out','jar21','jar25']:p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_SAVED_HOPS_EVIDENCE_CHECKPOINT',new_tool_credit=0,whole_goal_complete=False)
 try:
  public=ROOT/'docs/literature/hops-evidence-development';proof=json.loads((public/'VALIDATION.json').read_bytes());assert proof['new_tool_credit']==0 and proof['whole_goal_complete'] is False
  for name,value in {**proof['source_pins'],**proof['dependency_pins']}.items():assert pin(ROOT/name)==value,name
  archive=public/'controls.zip.xz';assert pin(archive)==proof['evidence_archive'];raw=lzma.decompress(archive.read_bytes());assert len(raw)<=268435456
  with zipfile.ZipFile(io.BytesIO(raw)) as z:manifest=json.loads(z.read('MANIFEST.json'));expected=dict(manifest['files']);body=z.read('MANIFEST.json');expected['MANIFEST.json']=dict(bytes=len(body),sha256=hashlib.sha256(body).hexdigest())
  extract(raw,out/'evidence',expected);assert not any(p.suffix.lower() in ['.jar','.sif','.class'] for p in (out/'evidence').rglob('*') if p.is_file());fixture=ROOT/'src/test/resources/org/jlinalg/metagenomics/hops-evidence';metadata=json.loads((fixture/'SOURCE.json').read_bytes());assert pin(fixture/'reference.zip')==metadata['reference_zip'];extract((fixture/'reference.zip').read_bytes(),out/'fixture',metadata['reference_files']);helper=ROOT/'tools/literature/java/HopsEvidenceControl.java';results=[]
  for compiler in ['21','25']:
   artifact=Path(getattr(a,'jar'+compiler)).resolve();assert pin(artifact)==proof['development_artifacts'][compiler];classes=out/('helper'+compiler);classes.mkdir();cmd=[str(JAVA/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(artifact),'-d',str(classes),str(helper)];r=subprocess.run(cmd,capture_output=True,timeout=90);(out/('javac'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr
   config=out/('replay'+compiler+'.json');save(config,dict(groups=[dict(label=group,data=str(out/'fixture'/group),expected=str(out/'fixture'/(group+'-expected'))) for group in ['source','boundaries','metadata']],classification_cases=str(out/'fixture/classification/cases.tsv'),classification_expected=str(out/'fixture/classification/expected.tsv')));target=out/('replay'+compiler);cmd=[str(JAVA/'java.exe'),'-Xmx512m','-cp',str(classes)+';'+str(artifact),'org.jlinalg.cli.HopsEvidenceControl',str(config),str(target)];r=subprocess.run(cmd,capture_output=True,timeout=180);(out/('replay'+compiler+'.stdout')).write_bytes(r.stdout);(out/('replay'+compiler+'.stderr')).write_bytes(r.stderr);save(out/('replay'+compiler+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,r.stderr;report=json.loads((target/'COMPLETED.json').read_bytes());assert report['sample_node_pairs']==2901 and report['classification_boundaries']==420 and report['refusals']==7;results.append(dict(compiler=compiler,report=pin(target/'COMPLETED.json')))
  assert pin(archive)==proof['evidence_archive'] and all(pin(ROOT/name)==value for name,value in {**proof['source_pins'],**proof['dependency_pins']}.items());state.update(status='PASS_SAVED_HOPS_EVIDENCE_AND_BOTH_NATIVE_BUILD_FIXTURE_REPLAYS_ONLY',evidence_files=len(manifest['files']),sample_node_pairs=5802,classification_boundaries=840,refusals=14,results=results,full_hops_or_eager_adapter_admitted=False,original_R_HOPS_reexecuted=False,biological_result_admitted=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source/evidence/fixture checks')
 raise SystemExit(main())
