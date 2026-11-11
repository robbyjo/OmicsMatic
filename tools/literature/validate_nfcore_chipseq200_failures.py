# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual source setup skips, immutable-result mismatches and a coordinated declaration-change refusal."""
import argparse,json,shutil,subprocess,time,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,DISTRO,linux

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--attempt',type=Path,required=True);p.add_argument('--image-set',type=Path,required=True);a=p.parse_args();out=a.out.resolve();jar=a.jar.resolve();attempt=a.attempt.resolve();images=a.image_set.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_CHIPSEQ200_FAILURE_CONTROLS',new_tool_credit=0,scientific_analysis_executed=False)
 try:
  guard=dict(artifact=pin(jar),manifest=pin(attempt/'manifest.json'),images=pin(images));raw=json.loads(images.read_bytes());checks=[]
  def launch(case,cmd,expected,fragment):
   with (case/'stdout').open('xb') as so,(case/'stderr').open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
   save(case/'process.json',dict(command=cmd,exit_code=r.returncode));error=(case/'stderr').read_text(encoding='utf8');assert r.returncode==expected and fragment in error,(case.name,r.returncode,error[-2500:]);return dict(case=case.name,expected_exit=expected,passed=True)
  def native_cmd(case,declared,selected=attempt):return [str(JAVA/'java.exe'),'-Xmx2048m','-jar',str(jar),'--engine-result',str(selected),'--image-set',str(declared),'--out',str(case/'audit')]
  for name in ['wrong_image_binding','missing_image_URI','wrong_source_version']:
   case=out/name;case.mkdir();declared=case/'images.json';value=json.loads(images.read_bytes());selected=attempt
   if name=='wrong_image_binding':first=next(iter(value['images']));value['images'][first]='/var/tmp/deliberately-not-the-original-image.sif'
   elif name=='missing_image_URI':value['images'].pop(next(iter(value['images'])))
   else:selected=BASE/'scientific-cross-platform-2/original-complete-attempt'
   save(declared,value);checks.append(launch(case,native_cmd(case,declared,selected),2,'FAIL2'));assert not (case/'audit/COMPONENT_AUDIT_COMPLETED.json').exists()
  for name,expected in [('missing_container_home',3),('missing_image_file',3),('corrupt_image_file',2)]:
   case=out/name;case.mkdir();value=json.loads(images.read_bytes());declared=case/'images.json';home='/var/tmp/omicsmatic-nfcore-apptainer-1'
   if name=='missing_container_home':home='/var/tmp/omicsmatic-deliberately-absent-container-home-1'
   elif name=='missing_image_file':value['images'][next(iter(value['images']))]='/var/tmp/omicsmatic-deliberately-absent-source-image.sif'
   else:bad=case/'invalid-image.sif';bad.write_bytes(b'INVALID SOURCE IMAGE CONTROL\n');value['images'][next(iter(value['images']))]=linux(bad)
   save(declared,value);cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx2048m','-cp',linux(jar),'org.jlinalg.cli.NfcoreChipseq200SourceCli','--recipe',linux(BASE/'chipseq200-portable-attempt-1/request.json'),'--image-set',linux(declared),'--container-home',home,'--home',linux(ROOT/'build/literature-review/nextflow-runtime/managed-setup-development-1/home-22'),'--setup-report',linux(BASE/'engine-host-recheck-1/report-22'),'--executor-cpus','4','--executor-memory-bytes','25769803776','--out',linux(case/'source-control')];checks.append(launch(case,cmd,expected,'SKIP3' if expected==3 else 'FAIL2'));assert not (case/'source-control/engine').exists() and not (case/'source-control/SOURCE_EXECUTION_COMPLETED.json').exists()
  case=out/'declaration_changed_during_audit';case.mkdir();declared=case/'images.json';shutil.copyfile(images,declared);cmd=native_cmd(case,declared);started=time.monotonic();changed=False
  with (case/'stdout').open('xb') as so,(case/'stderr').open('xb') as se:
   process=subprocess.Popen(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
   while process.poll() is None and time.monotonic()-started<1800:
    if not changed and (case/'audit/named-inputs').exists():
     assert process.poll() is None;value=json.loads(declared.read_bytes());value['images'][next(iter(value['images']))]+='.deliberate-mid-audit-change';save(declared,value);changed=True
    time.sleep(.02)
   if process.poll() is None:process.kill();process.wait();raise RuntimeError('Declaration-change control exceeded complete timeout')
  save(case/'process.json',dict(command=cmd,exit_code=process.returncode,changed_after_native_image_check=changed));error=(case/'stderr').read_text(encoding='utf8');assert changed and process.returncode==2 and 'image declaration changed' in error and not (case/'audit/COMPONENT_AUDIT_COMPLETED.json').exists();checks.append(dict(case=case.name,expected_exit=2,passed=True))
  assert dict(artifact=pin(jar),manifest=pin(attempt/'manifest.json'),images=pin(images))==guard;state.update(status='PASS_CHIPSEQ200_SOURCE_AND_COMPONENT_FAILURE_CONTROLS_ONLY',checks=checks,original_artifact_engine_and_declaration_unchanged=guard,namespace_prohibition_branch_not_forced=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete failure/refusal expectations')
 raise SystemExit(main())
