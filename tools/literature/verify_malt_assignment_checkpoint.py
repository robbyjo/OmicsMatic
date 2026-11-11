# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify an export/source-audit checkpoint with explicitly failed assignment validation; no analysis rerun."""
import argparse,hashlib,io,json,lzma,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA
from verify_nfcore_eager_screening_checkpoint import extract

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--dependency-jar',type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_SAVED_MALT_AUDIT_CHECKPOINT_VERIFICATION',assignment_validation_passed=False,new_tool_credit=0,whole_goal_complete=False)
 try:
  public=ROOT/'docs/literature/malt-assignment-development';proof=json.loads((public/'VALIDATION.json').read_bytes());assert proof['status']=='VERIFIED_EXPORT_WITH_ASSIGNMENT_AUDIT_FAILED_IN_PROGRESS' and proof['assignment_validation_passed'] is False and proof['new_tool_credit']==0
  for name,value in {**proof['source_pins'],**proof['dependency_pins']}.items():assert pin(ROOT/name)==value,name
  archive=public/'controls.zip.xz';assert pin(archive)==proof['evidence_archive'];raw=lzma.decompress(archive.read_bytes());assert len(raw)<=268435456
  with zipfile.ZipFile(io.BytesIO(raw)) as z:manifest=json.loads(z.read('MANIFEST.json'));wanted=dict(manifest['files']);body=z.read('MANIFEST.json');wanted['MANIFEST.json']=dict(bytes=len(body),sha256=hashlib.sha256(body).hexdigest())
  extract(raw,out/'evidence',wanted);assert not any(p.suffix.lower() in ['.jar','.sif','.class'] for p in (out/'evidence').rglob('*') if p.is_file());evidence=out/'evidence/evidence';export=json.loads((evidence/'malt-taxonomy-export-control-1/process.json').read_bytes());assert export['exit_code']==0;source=json.loads((evidence/'malt-taxonomy-complete-data-1/SOURCE.json').read_bytes());assert len(source['members'])==14 and source['hops_taxonomy_substituted'] is False
  for number in [1,2]:
   audit=evidence/('malt-rma-original-audit-'+str(number));assert json.loads((audit/'COMPLETED.json').read_bytes())['status']=='FAIL2_ORIGINAL_MALT_RMA_ASSIGNMENT_AUDIT';assert json.loads((audit/'original-api.process.json').read_bytes())['exit_code']!=0
  assert 'Unrecognized VM option' in (evidence/'malt-rma-original-audit-1/original-api.stderr').read_text();assert '1,601,131' in (evidence/'malt-rma-original-audit-2/original-api.stderr').read_text();assert '1,703,609' in (evidence/'malt-taxonomy-complete-data-1/malt.log').read_text()
  parent=a.dependency_jar.resolve() if a.dependency_jar else ROOT/proof['compile_dependency']['path'];assert pin(parent)==proof['compile_dependency']['pin'];classes=out/'classes';classes.mkdir();cmd=[str(JAVA/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(parent),'-d',str(classes),str(ROOT/'tools/literature/java/NfcoreMaltTaxonomyExport.java')];r=subprocess.run(cmd,capture_output=True,timeout=90);(out/'javac.stdout').write_bytes(r.stdout);(out/'javac.stderr').write_bytes(r.stderr);save(out/'javac.process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,r.stderr
  assert pin(archive)==proof['evidence_archive'] and all(pin(ROOT/name)==value for name,value in {**proof['source_pins'],**proof['dependency_pins']}.items());state.update(status='PASS_SAVED_EXPORT_EVIDENCE_AND_HELPER_COMPILATION_ONLY_ASSIGNMENT_REMAINS_FAILED',evidence_files=len(manifest['files']),complete_export_members=14,retained_failed_audits=2,source_execution_or_assignments_rerun=False,biological_result_admitted=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable failed-audit/source/evidence checks')
 raise SystemExit(main())
