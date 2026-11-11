# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the complete typed original source profile, annotation controls and retained failures."""
import hashlib,json,shutil,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE
from build_nfcore_chipseq import SOURCES

DEST=ROOT/'docs/literature/nfcore-chipseq121-development'

def main():
 assert not DEST.exists();prior_path=ROOT/'docs/literature/nfcore-complexity-development/VALIDATION.json';prior=json.loads(prior_path.read_bytes());assert all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and all(pin(ROOT/n)==v for n,v in prior['catalog_unchanged'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==prior['canonical_executable_unchanged']
 annotation=json.loads((BASE/'annotation-controls-2/COMPLETED.json').read_bytes());request=json.loads((BASE/'chipseq-request-controls-3/COMPLETED.json').read_bytes());bundle=json.loads((BASE/'chipseq-bundle-controls-1/COMPLETED.json').read_bytes());fault=json.loads((BASE/'chipseq121-runtime-fault-controls-1/COMPLETED.json').read_bytes());executions=json.loads((BASE/'chipseq121-source-results-1/COMPLETED.json').read_bytes())
 assert annotation['status']=='PASS_COMPLETE_ORIGINAL_ANNOTATION_AND_QC_CONTROLS_ONLY' and len(annotation['checks'])==18 and len(annotation['refusals'])==17
 assert request['status']=='PASS_TYPED_REQUEST_ADMISSION_CONTROLS_ONLY' and len(request['checks'])==34
 assert bundle['status']=='PASS_COMPLETE_TYPED_BUNDLE_CONTROLS_ONLY' and len(bundle['checks'])==8 and len(bundle['refusals'])==15
 assert fault['status']=='PASS_TYPED_RUNTIME_FAULT_CONTROLS_ONLY' and len(fault['checks'])==2 and executions['status']=='PASS_TWO_TYPED_SOURCE_PROFILE_EXECUTIONS_AND_RETAINED_FAILURES'
 for group in [annotation['checks'],annotation['refusals'],request['checks'],bundle['checks'],bundle['refusals'],fault['checks']]:assert all(r['passed'] for r in group)
 assert executions['traces']=={'000001':{'COMPLETED':97},'000002':{'CACHED':95,'COMPLETED':2},'000003':{'CACHED':95,'COMPLETED':2}}
 names=[n for n in SOURCES if n!='src/main/java/org/jlinalg/sequence/HomerPeakAnnotations.java']+['docs/literature/NFCORE_CHIPSEQ121.md']
 names+=['tools/literature/'+n for n in ['build_nfcore_chipseq.py','prepare_nfcore_chipseq121.py','validate_nfcore_annotations.py','validate_nfcore_chipseq_request.py','validate_nfcore_chipseq_bundle.py','freeze_nfcore_chipseq_checkpoint.py','verify_nfcore_chipseq_checkpoint.py','java/NfcoreAnnotationControl.java','java/NfcoreChipseqRequestControl.java']]
 names += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT/'src/test/resources/org/jlinalg/cli/nfcore-annotation411').iterdir()) if p.is_file()]
 source_pins={n:pin(ROOT/n) for n in names};files={'sources/'+n:ROOT/n for n in names};builds={};artifacts={};DEST.mkdir()
 for compiler,folder in [('21','chipseq-runner-build21-5'),('25','chipseq-runner-build25-3')]:
  build=json.loads((BASE/folder/'COMPLETED.json').read_bytes());assert build['status']=='PASS_TYPED_CHIPSEQ_RUNNER_BUILD_ONLY' and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());source=BASE/folder/'nfcore-chipseq-development.jar';identity=pin(source);assert identity==build['artifact']==annotation['artifacts'][compiler]==request['artifacts'][compiler]==bundle['artifacts'][compiler];name='nfcore-chipseq-javac'+compiler+'.jar';shutil.copyfile(source,DEST/name);artifacts[name]=pin(DEST/name);builds[compiler]=build
 folders=['annotation-qc-source-intake-1','annotation-gene-lookup-intake-1','annotation-native-1','annotation-native-2','annotation-native-3','annotation-native-4','annotation-native-5','annotation-native-6','annotation-native-7','annotation-sparse-original-1','annotation-sparse-original-2','annotation-controls-1','annotation-controls-2','chipseq-request-controls-1','chipseq-request-controls-2','chipseq-request-controls-3','chipseq121-runner-control-1','chipseq121-runner-control-2','chipseq121-runner-control-3','chipseq121-runner-control-4','chipseq121-runtime-fault-controls-1','chipseq-bundle-controls-1','chipseq121-source-results-1']
 folders += ['chipseq-runner-build21-'+str(i) for i in range(1,6)]+['chipseq-runner-build25-'+str(i) for i in range(1,4)]
 folders += ['chipseq121-freeze-attempt-1']
 for name in folders:
  folder=BASE/name;assert folder.is_dir()
  for p in sorted(folder.rglob('*')):
   relative=p.relative_to(folder)
   if not p.is_file() or p.suffix in ['.jar','.class','.sif'] or any(x.startswith('classes') or x.startswith('helper') for x in relative.parts[:-1]):continue
   if name.startswith('annotation-controls') and relative.parts[0] in ['fixtures','negative']:continue
   if name=='annotation-gene-lookup-intake-1' and p.name=='annotateRelativePosition.pl':continue
   if name=='chipseq-bundle-controls-1' and relative.parts[0] in ['derivatives','latest-state']:continue
   assert not p.is_symlink() and p.stat().st_size<=67108864;files['evidence/'+name+'/'+relative.as_posix()]=p
 for name in ['intake_annotation_qc1.py','stage_annotation_fixture1.py','generate_annotation_sparse1.py','prepare_annotation_sparse2.py','generate_annotation_sparse2.py','package_annotation_references1.py','run_typed_chipseq121_control2.py','resume_typed_chipseq121_control3.py','resume_typed_chipseq121_control4.py','run_typed_chipseq121_fault_controls1.py','copy_typed_chipseq121_result1.py','snapshot_typed_chipseq121_results1.py']:
  files['generation/'+name]=BASE/name
 identities={};archive=DEST/'controls.zip'
 with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,p in sorted(files.items()):
   raw=p.read_bytes();identities[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
 claims=dict(new_tool_credit=0,total_new_integrations=99,native=19,upstream_adapters=80,including_three_existing=102,missing=244,nfcore_catalog_status='missing',declared_chipseq121_profile_validated=True,full_nfcore_family=False,biological_accuracy_certified=False,whole_paper_reproduced=False,whole_goal_complete=False)
 save(DEST/'VALIDATION.json',dict(status='PASS_TYPED_CHIPSEQ121_DEVELOPMENT_PROFILE_ONLY',source_pins=source_pins,artifacts=artifacts,archive=pin(archive),archive_files=identities,builds=builds,annotation_controls=annotation,request_controls=request,bundle_controls=bundle,runtime_fault_controls=fault,source_executions=executions,shared_source_unchanged={'src/main/java/org/jlinalg/sequence/HomerPeakAnnotations.java':pin(ROOT/'src/main/java/org/jlinalg/sequence/HomerPeakAnnotations.java')},large_original_objects_not_redistributed=True,predecessor_proof=pin(prior_path),canonical_executable_unchanged=prior['canonical_executable_unchanged'],catalog_unchanged=prior['catalog_unchanged'],claims=claims))
 (DEST/'.gitattributes').write_bytes(b'* -text\n');(DEST/'README.md').write_text('# Typed original ChIP-seq 1.2.1 development profile\n\nRead [the complete workflow, setup requirements, warnings and recovery steps](../NFCORE_CHIPSEQ121.md). The executable runs the original pinned source workflow and requires complete selected source-consistency audits. The full nf-core family and 250-paper goal remain open; no extra catalog-tool or biological credit is claimed. The released executable is unchanged.\n\nRun `python tools/literature/verify_nfcore_chipseq_checkpoint.py` from the repository root. The evidence retains both compiler executions, original annotation/QC populations, request and portable-bundle controls, missing-prerequisite/capacity failures, the earlier missing packaged entrypoint and the host Java spawn-helper failure. Large original FASTQs/BAMs and private runtimes remain local. Copy complete result directories and require successful `--verify`; read WARNINGS.md even after success.\n',encoding='utf8',newline='\n')
 (DEST/'SHA256SUMS').write_text(''.join(pin(DEST/n)['sha256']+'  '+n+'\n' for n in [*artifacts,'controls.zip','VALIDATION.json']),encoding='utf8',newline='\n');assert all(pin(ROOT/n)==v for n,v in source_pins.items());print('Frozen typed ChIP-seq profile:',len(files),'evidence files; two compiler executions; all 97 source tasks;0 catalog credit.',flush=True)

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source, control and scope checks')
 main()
