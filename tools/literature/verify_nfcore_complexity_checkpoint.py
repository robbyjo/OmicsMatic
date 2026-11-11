# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only native Picard complexity checkpoint integrity/scope checks, without rerunning scientific analysis."""
import hashlib,json,stat,sys,zipfile
from pathlib import PurePosixPath
from setup_deseq2_reference import ROOT,pin

def main():
 data=ROOT/'docs/literature/nfcore-complexity-development';proof=json.loads((data/'VALIDATION.json').read_bytes());assert proof['status']=='PASS_NATIVE_PICARD2231_COMPLEXITY_COMPONENT_ONLY'
 assert proof['claims']==dict(new_tool_credit=0,total_new_integrations=99,native=19,upstream_adapters=80,including_three_existing=102,missing=244,nfcore_status='missing',full_nfcore_adapter=False,fitted_model_admitted=False,whole_pipeline_admitted=False,biological_accuracy_certified=False,whole_paper_reproduction=False,whole_goal_complete=False)
 expected=set(proof['artifacts'])|{'controls.zip','VALIDATION.json'};seen=set()
 for line in (data/'SHA256SUMS').read_text().splitlines():
  sha,name=line.split('  ',1);assert name in expected and name not in seen and pin(data/name)['sha256']==sha;seen.add(name)
 assert seen==expected and pin(data/'controls.zip')==proof['archive']
 for name,v in proof['artifacts'].items():assert pin(data/name)==v
 for name,v in proof['source_pins'].items():assert pin(ROOT/name)==v,('Current complexity source/reference differs',name)
 for name,v in proof['catalog_unchanged'].items():assert pin(ROOT/name)==v
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged'] and pin(ROOT/'docs/literature/nfcore-deseq126-development/VALIDATION.json')==proof['predecessor_proof']
 c=proof['controls'];assert len(c['native_and_optical_replays'])==12 and len(c['complete_captured_replays'])==4 and len(c['negative_controls'])==15 and all(r['passed'] for r in c['native_and_optical_replays']+c['complete_captured_replays']+c['negative_controls'])
 assert c['complete_marked_BAM_records']==1389840 and c['original_ROI_bins']==600 and c['original_Preseq_rows']==60000 and c['full_reports_identical'] and not c['full_workflow_or_biological_admission']
 assert proof['original_reference']['cases']==20 and proof['original_reference_fields_match_across_two_JVMs'] and proof['physical_original_line_endings_retained']
 images=proof['chipseq200_image_preparation'];assert images['status']=='PASS_CHIPSEQ200_SOURCE_DECLARED_SIF_PREPARATION_ONLY' and images['prepared_images']==images['requested_images']==33 and not images['scientific_analysis_executed'] and not images['host_security_configuration_changed']
 intake=proof['chipseq200_source_intake'];assert intake['locked_source_files']==203 and intake['complete_module_declarations']==50 and intake['unique_images']==33
 with zipfile.ZipFile(data/'controls.zip') as z:
  assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(proof['archive_files'])
  for item in z.infolist():
   name=item.filename;p=PurePosixPath(name);assert not p.is_absolute() and '..' not in p.parts and '\\' not in name and str(p)==name and not item.is_dir() and not stat.S_ISLNK(item.external_attr>>16)
   raw=z.read(item);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==proof['archive_files'][name]
  for name,v in proof['source_pins'].items():assert proof['archive_files']['sources/'+name]==v
  assert json.loads(z.read('evidence/complexity-controls-2/COMPLETED.json'))==c
  assert b'INVALID_FLAG_NOT_PRIM_ALIGNMENT' in z.read('evidence/complexity-controls-1/21-windows21-optical.stderr') and b'INVALID_FLAG_NOT_PRIM_ALIGNMENT' in z.read('evidence/complexity-controls-2/invalid-secondary-unmapped.stderr')
  a=z.read('evidence/picard2231-original-reference-2/windows21.tsv');b=z.read('evidence/picard2231-original-reference-2/original_image.tsv');assert a!=b and a.decode().splitlines()==b.decode().splitlines()
  assert json.loads(z.read('evidence/chipseq200-images-1/COMPLETED.json'))==images
  for compiler in ['21','25']:
   build=proof['builds'][compiler];assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
   with zipfile.ZipFile(data/('nfcore-complexity-javac'+compiler+'.jar')) as full,zipfile.ZipFile(data/('picard-complexity-javac'+compiler+'.jar')) as library:
    assert b'Main-Class: org.jlinalg.cli.NfcoreComplexityAuditCli' in full.read('META-INF/MANIFEST.MF') and b'Main-Class:' not in library.read('META-INF/MANIFEST.MF')
    predecessor=ROOT/f'docs/literature/nfcore-deseq126-development/nfcore-deseq126-javac{compiler}.jar';assert pin(predecessor)==build['predecessor']
    with zipfile.ZipFile(predecessor) as old:assert all(old.read(n)==full.read(n) for n in old.namelist() if n!='META-INF/MANIFEST.MF')
    for name,v in build['native_classes'].items():
     raw=full.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==v
     if name.startswith('org/jlinalg/sequence/PicardLibraryComplexity'):assert library.read(name)==raw
    for jar in [full,library]:
     assert jar.read('org/jlinalg/omics/picard2231/MIT-Picard.txt') and jar.read('META-INF/picard2231/GPL-3')
     for name in ['src/main/java/org/jlinalg/sequence/PicardLibraryComplexity.java','src/main/java/org/jlinalg/cli/NfcoreChipseq121Complexity.java','src/main/java/org/jlinalg/cli/NfcoreComplexityAuditCli.java']:assert jar.read('META-INF/picard2231/sources/'+name)==(ROOT/name).read_bytes()
 print('PASS native Picard complexity:20 original cases;12 native/optical replays;4 complete six-library audits;15 refusals;33 images prepared only;0 tool credit.',flush=True);return 0

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete reference, source or scope assertions')
 try:raise SystemExit(main())
 except Exception as error:
  print('FAIL2 NO COMPLEXITY CHECKPOINT ADMISSION: '+repr(error)+'\nRequired action: preserve original inputs and failures; inspect the named discrepancy and validate a new checkpoint. Do not rewrite historical hashes or omit populations to conceal failures.',file=sys.stderr);raise SystemExit(2)
