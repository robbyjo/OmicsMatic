# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only source, scope, reference and execution-evidence verification for the original v2 development profile."""
import collections,hashlib,io,json,lzma,stat,sys,zipfile
from pathlib import PurePosixPath
from setup_deseq2_reference import ROOT,pin
def main():
 data=ROOT/'docs/literature/nfcore-chipseq200-development';proof=json.loads((data/'VALIDATION.json').read_bytes());assert proof['status']=='PASS_TYPED_CHIPSEQ200_DEVELOPMENT_PROFILE_ONLY'
 assert proof['claims']==dict(new_tool_credit=0,total_new_integrations=99,native=19,upstream_adapters=80,including_three_existing=102,missing=244,nfcore_catalog_status='missing',declared_chipseq200_profile_validated=True,full_nfcore_family=False,biological_accuracy_certified=False,whole_paper_reproduced=False,whole_goal_complete=False)
 expected=set(proof['artifacts'])|{'controls.zip.xz','VALIDATION.json'};seen=set()
 for line in (data/'SHA256SUMS').read_text().splitlines():
  digest,name=line.split('  ',1);assert name in expected and name not in seen and pin(data/name)['sha256']==digest;seen.add(name)
 assert seen==expected and pin(data/'controls.zip.xz')==proof['archive_envelope'] and proof['large_original_objects_not_redistributed']
 assert proof['archive']['bytes']<=134217728
 with lzma.open(data/'controls.zip.xz','rb') as stream:archive=stream.read(proof['archive']['bytes']+1)
 assert dict(bytes=len(archive),sha256=hashlib.sha256(archive).hexdigest())==proof['archive']
 for field in ['source_pins','catalog_unchanged','shared_source_unchanged']:
  for name,value in proof[field].items():assert pin(ROOT/name)==value,('Current checkpoint binding differs',name)
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged'] and pin(ROOT/'docs/literature/nfcore-chipseq121-development/VALIDATION.json')==proof['predecessor_proof']
 controls=proof['controls'];assert len(controls['mapping']['checks'])==162 and len(controls['BAM_and_shell']['checks'])==162 and len(controls['complete_inputs']['checks'])==6 and len(controls['peaks_complexity']['checks'])==12
 assert controls['peaks_complexity']['complete_marked_BAM_records']==1389840 and controls['peaks_complexity']['complete_Preseq_rows']==60000 and controls['peaks_complexity']['complete_ROI_bins']==600
 assert controls['QC']['complete_native_replays']==6 and controls['QC']['small_checks']==120 and len(controls['request']['checks'])==52
 assert controls['portable']['successful_verifications']==9 and controls['portable']['deliberate_refusals']==15 and controls['Picard_actual_provider']['literal_provider_version']=='2.27.4-SNAPSHOT' and controls['Picard_actual_provider']['original_cases']==20
 for name,value in controls.items():assert value['status'].startswith('PASS_'),name
 assert proof['source_executions']['traces']['000001']=={'COMPLETED':215}
 for number in ['000002','000003']:
  counts=proof['source_executions']['traces'][number];assert sum(counts.values())==215 and counts.get('CACHED',0)>0 and set(counts)<=set(['COMPLETED','CACHED'])
 with zipfile.ZipFile(io.BytesIO(archive)) as z:
  assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(proof['archive_files'])
  for item in z.infolist():
   name=item.filename;path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name and str(path)==name and not item.is_dir() and not stat.S_ISLNK(item.external_attr>>16);raw=z.read(item);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==proof['archive_files'][name]
  for name,value in proof['source_pins'].items():assert proof['archive_files']['sources/'+name]==value
  for name,reference in proof['control_receipts'].items():assert json.loads(z.read(reference))==controls[name]
  for number,counts in proof['source_executions']['traces'].items():
   prefix='execution/'+number+'/';report=json.loads(z.read(prefix+'workflow.json'));assert report['status']=='complete-declared-original-source-workflow' and not report['biological_accuracy_certified'] and not report['whole_paper_reproduced'] and len(report['source_components'])==4
   rows=z.read(prefix+'engine/trace.tsv').decode().splitlines();index=rows[0].split('\t').index('status');assert len(rows)==216 and dict(collections.Counter(row.split('\t')[index] for row in rows[1:]))==counts
   for component in ['reads-and-alignments','peaks-and-annotation','complexity','saved-QC']:
    audit=json.loads(z.read(prefix+'audits/'+component+'/audit.json'));assert audit['original_engine_manifest']==report['engine_manifest'] and not audit['scientific_result_admitted'] and audit['nfcore_tool_credit']==0
   qc=json.loads(z.read(prefix+'audits/saved-QC/audit.json'));assert qc['actual_saved_transform']=='vst' and len(qc['antibodies'])==1 and qc['antibodies'][0]['native_complete_QC']['normalized_cells_checked']==5888
  assert b'Original marked-BAM literal provider version differs' in z.read('evidence/chipseq200-complexity-audit-1/stderr')
  assert b'outside the complete declared future capture' in z.read('evidence/chipseq200-request-controls-2/21-external_samplesheet_read/stderr')
  for compiler in ['21','25']:
   artifact=data/('nfcore-chipseq200-javac'+compiler+'.jar');assert pin(artifact)==proof['artifacts'][artifact.name]==proof['builds'][compiler]['artifact']
   previous=ROOT/f'docs/literature/nfcore-chipseq121-development/nfcore-chipseq-javac{compiler}.jar';assert pin(previous)==proof['predecessor_artifacts'][compiler]
   with zipfile.ZipFile(artifact) as current,zipfile.ZipFile(previous) as old:
    assert b'Main-Class: org.jlinalg.cli.NfcoreChipseq200ToolsCli' in current.read('META-INF/MANIFEST.MF') and all(current.read(n)==old.read(n) for n in old.namelist() if n!='META-INF/MANIFEST.MF')
    for name in proof['source_pins']:
     if name.startswith('src/main/java/') and (name.endswith('/NfcoreContainerSet.java') or '/NfcoreChipseq200' in name):
      matches=[n for n in current.namelist() if n.startswith('META-INF/') and n.endswith('/'+name)];assert matches and all(current.read(n)==(ROOT/name).read_bytes() for n in matches),name
    for member in ['images.json','MIT-nf-core.txt']:
     name='src/main/resources/org/jlinalg/omics/nfcore-chipseq200/'+member
     if member=='images.json':assert current.read(name.removeprefix('src/main/resources/'))==(ROOT/name).read_bytes()
    name='src/main/resources/org/jlinalg/omics/nfcore-chipseq200-qc/inspect.R';assert current.read(name.removeprefix('src/main/resources/'))==(ROOT/name).read_bytes()
 print('PASS original typed ChIP-seq2.0.0 profile:215 cold tasks; two final-build cache resumes; complete native output populations; source/R/portable refusal controls;0 catalog credit.',flush=True);return 0
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source, archive or scope checks')
 try:raise SystemExit(main())
 except Exception as error:
  print('FAIL2 NO CHECKPOINT ADMISSION: '+repr(error)+'\nRequired action: retain complete originals and failed attempts; inspect the named mismatch and validate a new checkpoint. Do not rewrite historical evidence hashes or omit scientific populations to hide failure.',file=sys.stderr);raise SystemExit(2)
