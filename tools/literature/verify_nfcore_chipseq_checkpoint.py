# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only integrity and scope verification of the typed ChIP-seq1.2.1 development checkpoint."""
import collections,hashlib,json,stat,sys,zipfile
from pathlib import PurePosixPath
from setup_deseq2_reference import ROOT,pin

def main():
 data=ROOT/'docs/literature/nfcore-chipseq121-development';proof=json.loads((data/'VALIDATION.json').read_bytes());assert proof['status']=='PASS_TYPED_CHIPSEQ121_DEVELOPMENT_PROFILE_ONLY'
 assert proof['claims']==dict(new_tool_credit=0,total_new_integrations=99,native=19,upstream_adapters=80,including_three_existing=102,missing=244,nfcore_catalog_status='missing',declared_chipseq121_profile_validated=True,full_nfcore_family=False,biological_accuracy_certified=False,whole_paper_reproduced=False,whole_goal_complete=False)
 expected=set(proof['artifacts'])|{'controls.zip','VALIDATION.json'};seen=set()
 for line in (data/'SHA256SUMS').read_text().splitlines():
  sha,name=line.split('  ',1);assert name in expected and name not in seen and pin(data/name)['sha256']==sha;seen.add(name)
 assert seen==expected and pin(data/'controls.zip')==proof['archive']
 for field in ['source_pins','catalog_unchanged','shared_source_unchanged']:
  for n,v in proof[field].items():assert pin(ROOT/n)==v,('Current checkpoint binding differs',n)
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged'] and pin(ROOT/'docs/literature/nfcore-complexity-development/VALIDATION.json')==proof['predecessor_proof']
 annotation=proof['annotation_controls'];request=proof['request_controls'];bundle=proof['bundle_controls'];fault=proof['runtime_fault_controls'];executions=proof['source_executions'];assert len(annotation['checks'])==18 and len(annotation['refusals'])==17 and len(request['checks'])==34 and len(bundle['checks'])==8 and len(bundle['refusals'])==15 and len(fault['checks'])==2
 for group in [annotation['checks'],annotation['refusals'],request['checks'],bundle['checks'],bundle['refusals'],fault['checks']]:assert all(r['passed'] for r in group)
 assert annotation['source_last_transcript_row_per_contig_gene_preserved'] and annotation['source_absent_category_NA_preserved'] and annotation['source_gene_version_and_Unicode_sort_preserved'] and bundle['original_result_unchanged'] and not bundle['hashes_establish_authenticity'] and proof['large_original_objects_not_redistributed']
 assert executions['traces']=={'000001':{'COMPLETED':97},'000002':{'CACHED':95,'COMPLETED':2},'000003':{'CACHED':95,'COMPLETED':2}} and not executions['biological_accuracy_certified']
 with zipfile.ZipFile(data/'controls.zip') as z:
  assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(proof['archive_files'])
  for item in z.infolist():
   name=item.filename;p=PurePosixPath(name);assert not p.is_absolute() and '..' not in p.parts and '\\' not in name and str(p)==name and not item.is_dir() and not stat.S_ISLNK(item.external_attr>>16);raw=z.read(item);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==proof['archive_files'][name]
  for n,v in proof['source_pins'].items():assert proof['archive_files']['sources/'+n]==v
  for compiler,id in [('21','000002'),('25','000003')]:
   prefix='evidence/chipseq121-source-results-1/compiler'+compiler+'/';report=json.loads(z.read(prefix+'workflow.json'));assert report['status']=='complete-declared-original-source-workflow' and not report['biological_accuracy_certified'] and not report['whole_paper_reproduced'] and len(report['source_components'])==5
   for component in ['reads-and-alignments','consensus','complexity','annotation-QC','differential']:
    audit=json.loads(z.read(prefix+'audits/'+component+'/audit.json'));completed=json.loads(z.read(prefix+'audits/'+component+'/COMPONENT_AUDIT_COMPLETED.json'));assert not completed['scientific_result_admitted'] and audit['original_engine_manifest']==report['engine_manifest']
   assert json.loads(z.read('evidence/chipseq121-runner-control-'+('3' if compiler=='21' else '4')+'/process.json'))['exit_code']==0
  for id,counts in executions['traces'].items():
   lines=z.read('evidence/chipseq121-source-results-1/engine/'+id+'/trace.tsv').decode().splitlines();i=lines[0].split('\t').index('status');assert dict(collections.Counter(row.split('\t')[i] for row in lines[1:]))==counts
  assert b'ClassNotFoundException' in z.read('evidence/chipseq121-source-results-1/earlier-missing-entrypoint/attempts/000001/engine-process/process.stderr') and b'Failed to exec spawn helper' in z.read('evidence/chipseq121-runner-control-2/stderr')
  assert json.loads(z.read('evidence/annotation-controls-2/COMPLETED.json'))==annotation and json.loads(z.read('evidence/chipseq-request-controls-3/COMPLETED.json'))==request and json.loads(z.read('evidence/chipseq-bundle-controls-1/COMPLETED.json'))==bundle and json.loads(z.read('evidence/chipseq121-runtime-fault-controls-1/COMPLETED.json'))==fault
  for compiler in ['21','25']:
   build=proof['builds'][compiler];assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());artifact=data/('nfcore-chipseq-javac'+compiler+'.jar');assert pin(artifact)==proof['artifacts'][artifact.name]==build['artifact']==annotation['artifacts'][compiler]==request['artifacts'][compiler]==bundle['artifacts'][compiler]
   assert pin(ROOT/f'docs/literature/nfcore-complexity-development/nfcore-complexity-javac{compiler}.jar')==build['predecessor'] and pin(ROOT/f'docs/literature/nfcore-captured-development/nfcore-captured-javac{compiler}.jar')==build['captured_predecessor']
   with zipfile.ZipFile(artifact) as current,zipfile.ZipFile(ROOT/f'docs/literature/nfcore-complexity-development/nfcore-complexity-javac{compiler}.jar') as old,zipfile.ZipFile(ROOT/f'docs/literature/nfcore-captured-development/nfcore-captured-javac{compiler}.jar') as captured:
    assert b'Main-Class: org.jlinalg.cli.NfcoreChipseqToolsCli' in current.read('META-INF/MANIFEST.MF') and all(current.read(n)==old.read(n) for n in old.namelist() if n!='META-INF/MANIFEST.MF')
    for n,v in build['captured_classes'].items():
     raw=current.read(n);assert raw==captured.read(n) and dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==v
    for n in build['source_pins']:
     if n.endswith('.java'):assert current.read('META-INF/nfcore-chipseq121/sources/'+n)==(ROOT/n).read_bytes()
 print('PASS typed ChIP-seq1.2.1 profile:97 original tasks; two compiler executions;18 annotation replays/17 discrepancies;34 request controls;8 portable replays/15 refusals;2 runtime fault controls;0 catalog credit.',flush=True);return 0

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable full source, archive or scope checks')
 try:raise SystemExit(main())
 except Exception as error:
  print('FAIL2 NO CHECKPOINT ADMISSION: '+repr(error)+'\nRequired action: preserve complete originals and failed attempts; inspect the named discrepancy and validate a new checkpoint. Do not rewrite historical hashes or omit source populations to conceal failures.',file=sys.stderr);raise SystemExit(2)
