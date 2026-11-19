#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit native Seurat preprocessing source and qualification evidence; never run R."""
import argparse,hashlib,json,sys,traceback,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
EVIDENCE=ROOT/'docs/literature/seurat-preprocessing-development-v2'
RESOURCE=ROOT/'src/test/resources/org/jlinalg/sequence/seurat-preprocessing'
STATUS='PASS_NATIVE_SEURAT_5_6_0_PREPROCESSING_QUALIFICATION'
ACTION=('SKIP3: dependent evidence audit was skipped. Supply the named permitted source/evidence/Python3.11+ path and retry NEW output. '
 'FAIL2: no complete qualification admitted. Preserve evidence and diagnostics, correct the stated cause and retry NEW output. '
 'This checks integrity/source relationships, not producer authenticity, an independent statistical refit or biological validity. '
 'Native matrix methods need Java17+ and no R; original reference recapture has separate explicit runtime requirements.')

def require(ok,why):
 if not ok:raise ValueError(why)

def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def pin(path):
 path=Path(path)
 with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def entries(z):
 names=z.namelist();rows=json.loads(z.read('ENTRY-MANIFEST.json'))['payloads']
 require(len(names)==len(set(names)) and set(names)==set(rows)|{'ENTRY-MANIFEST.json'},'Archive file membership differs')
 for name,value in rows.items():require(rawpin(z.read(name))==value,'Archived bytes differ: '+name)
 return rows

def verify(runtime_manifest=None,parent_jar=None,candidate_jar=None):
 v=json.loads((EVIDENCE/'VALIDATION.json').read_bytes());require(v['status']==STATUS and v['new_family_credit']==0 and v['whole_goal_complete'] is False,'Qualification scope differs')
 require(pin(Path(__file__))==v['audit_source_guard'],'Auditor source changed; qualify a new version rather than refreshing old hashes')
 for name,value in v['source_guards'].items():require(pin(ROOT/name)==value,'Qualified native/test/reference/example source changed: '+name)
 for name,value in v['fixture_guards'].items():require(pin(ROOT/name)==value,'Original reference fixture changed: '+name)
 path=EVIDENCE/'qualification-evidence.zip';require(pin(path)==v['archive'],'Qualification archive changed')
 with zipfile.ZipFile(path) as z:
  members=entries(z);require(len(members)==v['indexed_payloads'],'Qualification payload population differs')
  for name,value in v['source_guards'].items():require(rawpin(z.read('current-source/'+name))==value,'Archived current source differs: '+name)
  require(rawpin(z.read('auditor-source.py'))==v['audit_source_guard'],'Archived auditor differs')
  capture=json.loads(z.read('reference/CAPTURE.json'));reference=json.loads(z.read('reference/reference.json'))
  require(capture['native_comparison_pending'] is False and capture['reference']==rawpin(z.read('reference/reference.json')) and capture['original_results']==rawpin(z.read('reference/original-results.rds')),'Reference capture/source object binding differs')
  require(reference['source_commit']=='75dfa8603befe31505e6287d32dc86c607732bd6' and len(reference['datasets'])==13 and len(reference['normalizations'])==90 and len(reference['scalings'])==208,'Original source profile/population differs')
  require(sum('error' in row for row in reference['normalizations'])==3 and not any('error' in row for row in reference['scalings']),'Original errors were lost or relabelled')
  require(len(reference['guards'])==6 and all(row['body_formals_identical'] for row in reference['guards']),'Installed original R source guards differ')
  fields=sum(len(row['values']) if isinstance(row['values'],list) else 1 for kind in ['normalizations','scalings'] for row in reference[kind] if 'error' not in row);require(fields==15081,'Complete original cell population differs')
  q=json.loads(z.read('qualification/COMPLETED.json'));require(q['source_datasets']==13 and q['original_requests']==298 and q['source_matrix_views']==295 and q['primary_dense_reference_fields']==15081 and q['pipeline_dense_reference_fields']==920,'Native source comparison scope differs')
  for platform in ['windows21','linux21']:
   raw=z.read('qualification/'+platform+'/TEST-junit-jupiter.xml');test=ET.fromstring(raw);require({k:int(test.get(k,'0')) for k in ['tests','errors','failures','skipped']}==dict(tests=13,errors=0,failures=0,skipped=0),'Packaged native test result differs: '+platform)
   stdout=z.read('qualification/'+platform+'.stdout').decode();require('models=208 usable=176 diagnostic_only=32 dense_fields=10544' in stdout and 'models=90 source_errors=3 usable=71 diagnostic_only=16 dense_fields=4537' in stdout,'Native admission/diagnostic populations differ: '+platform)
  for name,value in v['method_classes'].items():require(rawpin(z.read('method-classes/'+name))==value,'Qualified method bytecode differs: '+name)
  ex=json.loads(z.read('examples/COMPLETED.json'));require(ex['platform_outputs_equal_after_line_ending_normalization'] is True and ex['expected_failed_demo_admits_no_matrix'] is True and len(ex['processes'])==4,'Example qualification incomplete')
  for name,row in ex['processes'].items():require(row['exit_code']==row['expected_exit']==(2 if name.endswith('zero-total') else 0),'Failed example admitted: '+name)
  for platform in ['windows21','linux21']:
   require(z.read('examples/'+platform+'-zero-total.stdout')==b'' and z.read('examples/'+platform+'-zero-total.stderr').startswith(b'FAIL2:'),'Expected failure emitted an admitted matrix')
  collector=json.loads(z.read('collector-controls/COMPLETED.json'));require(collector['recaptured_reference_byte_identical'] and collector['recaptured_original_RDS_byte_identical'] and len(collector['processes'])==5,'Collector recapture scope differs')
  for name,row in collector['processes'].items():require(row['exit_code']==row['expected']==(0 if name=='valid' else 3),'Collector skip failed: '+name)
  for name in ['forbid','missing-r','missing-library','missing-package']:require(collector['processes'][name]['reference_process_launched'] is False,'A skipped reference was executed')
  preserved=json.loads(z.read('preservation/COMPLETED.json'));require(preserved['private_R_files']==24841 and preserved['other_worktrees_unchanged']==20 and preserved['previous_frozen_artifacts_unchanged']==33 and preserved['canonical_metadata_files_unchanged']==9,'Preservation scope differs')
  old=json.loads(z.read('historical-build-1/COMPLETED.json'));require(old['old_members_unchanged']==v['parent_members'] and old['jar']==v['candidate_jar'] and old['parent']==v['parent_jar'],'Parent JAR relationship differs')
  require(set(old['added_classes'])==set(v['method_classes']),'Added bytecode population differs')
 source_archive=RESOURCE/'original-source.zip';require(pin(source_archive)==capture['source_archive'],'Pinned upstream source archive differs')
 with zipfile.ZipFile(source_archive) as z:
  source_members=entries(z);official=json.loads(z.read('SOURCE.json'));require(official['commit']==reference['source_commit'] and len(official['files'])==5,'Original source edition differs')
  for name,row in official['files'].items():require(rawpin(z.read(name))=={k:row[k] for k in ['bytes','sha256']},'Official source receipt differs: '+name)
 physical_members=0
 require((parent_jar is None)==(candidate_jar is None),'Supply both --parent-jar and --candidate-jar for physical JAR auditing')
 if parent_jar is not None:
  require(pin(parent_jar)==v['parent_jar'] and pin(candidate_jar)==v['candidate_jar'],'Wrong frozen parent/candidate JAR')
  with zipfile.ZipFile(parent_jar) as old,zipfile.ZipFile(candidate_jar) as new:
   require(len(old.namelist())==v['parent_members'] and len(set(new.namelist()))==len(new.namelist()),'Physical JAR membership differs')
   for name in old.namelist():require(old.read(name)==new.read(name),'Pre-existing JAR payload changed: '+name);physical_members+=1
   require(set(new.namelist())-set(old.namelist())==set(v['method_classes']),'Unexpected additional JAR payloads')
   for name,value in v['method_classes'].items():require(rawpin(new.read(name))==value,'Physical method class differs: '+name)
 runtime_files=0
 if runtime_manifest is not None:
  require(pin(runtime_manifest)==v['runtime_manifest'],'Wrong immutable private R-library manifest')
  ledger=json.loads(Path(runtime_manifest).read_bytes());require(len(ledger)==24841,'Private R library population differs')
  roots=[Path(p).resolve() for p in v['runtime_roots']];actual={str(p) for root in roots for p in root.rglob('*') if p.is_file()};require(actual==set(ledger),'Private library file membership differs')
  for name,value in ledger.items():require(pin(name)==value,'Private R library changed: '+name);runtime_files+=1
 return dict(status=STATUS,indexed_public_payloads=len(members),upstream_source_payloads=len(source_members),source_datasets=13,original_requests=298,original_shape_errors=3,original_source_views=295,primary_dense_reference_fields=15081,pipeline_dense_reference_fields=920,retained_usable_native_matrices=247,retained_diagnostic_only_matrices=48,retained_packaged_tests_per_platform=13,physical_parent_members_rechecked=physical_members,physical_private_R_files_rechecked=runtime_files,original_R_executed_now=False,Java_tests_executed_now=False,new_family_credit=0,whole_goal_complete=False)

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--runtime-manifest',type=Path);parser.add_argument('--parent-jar',type=Path);parser.add_argument('--candidate-jar',type=Path);a=parser.parse_args();out=None
 try:
  target=a.out.absolute();target.resolve().relative_to(ROOT/'build');target.mkdir(parents=True,exist_ok=False);out=target;result=verify(a.runtime_manifest,a.parent_jar,a.candidate_jar);(out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result),flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2;record=dict(status='SKIP3_DEPENDENT_AUDIT_SKIPPED' if code==3 else 'FAIL2_NO_QUALIFICATION_ADMITTED',error=str(error),detail=traceback.format_exc(),required_action=ACTION)
  if out is not None:(out/'FAILURE.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
  print(record['status']+': '+str(error)+'\nRequired action: '+ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
