#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit current METAL adapter source, public qualification views and optional private closure."""
import argparse,csv,hashlib,io,json,os,subprocess,sys,traceback,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
EVIDENCE=ROOT/'docs/literature/metal-workflow-development'
ACTION=('SKIP3: dependent audit/analysis skipped; supply the named permitted source, '
 'evidence, Python 3.9+ or Java17+ prerequisite, or leave that analysis skipped. '
 'FAIL2: no complete result or qualification admitted. Preserve partials, original '
 'inputs and all diagnostics, correct the stated cause, and retry NEW output. '
 'Never refresh old method hashes, drop failed cohorts or repair NaN/Inf/P=0. '
 'Public bundle views omit restricted runtimes and are not standalone admitted '
 'execution bundles. Original-runtime replay is a separate explicit Java command.')

def require(ok,why):
 if not ok:raise ValueError(why)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def filepin(path):
 digest=hashlib.sha256()
 with path.open('rb') as stream:
  for block in iter(lambda:stream.read(1<<20),b''):digest.update(block)
 return dict(bytes=path.stat().st_size,sha256=digest.hexdigest())

def kv(raw):
 rows=list(csv.reader(io.StringIO(raw.decode('utf-8')),delimiter='\t'));require(rows and rows[0]==['key','value'],'Unexpected captured key/value header');result={}
 for row in rows[1:]:require(len(row)==2 and row[0] not in result,'Malformed/duplicate captured key');result[row[0]]=row[1]
 return result

def archive(v):
 path=EVIDENCE/'qualification-evidence.zip';require(filepin(path)==v['archive'],'Qualification archive bytes differ')
 with zipfile.ZipFile(path) as z:
  names=z.namelist();entries=json.loads(z.read('ENTRY-MANIFEST.json'))['payloads'];require(len(names)==len(set(names)) and set(names)==set(entries)|{'ENTRY-MANIFEST.json'},'Archive membership differs');require(len(entries)==v['indexed_public_payloads'],'Archive payload population differs')
  for name,value in entries.items():require(pin(z.read(name))==value,'Archived payload differs: '+name)
  require(not any('/runtime/metal' in name or '/source/metal/' in name or name.endswith('NOTICE.txt') for name in names),'Restricted original runtime/source/notice unexpectedly redistributed')
  closure=json.loads(z.read('PRIVATE-CLOSURE.json'));require(closure['index']==v['private_closed_index'] and closure['reader']==v['reader'] and closure['admitted_bundles']==28 and closure['public_views_are_standalone_bundles'] is False,'Private/public closure boundary differs')
  matrix=json.loads(z.read('matrix-controls/COMPLETED.json'));advanced=json.loads(z.read('admission-controls/COMPLETED.json'));require(matrix['actual_java_main_launches']==55 and matrix['success_profiles']==11 and matrix['actual_replays']==11,'Main qualification matrix differs');require(advanced['actual_java_launches']==22,'Admission/automatic-GC population differs')
  for report in [matrix,advanced]:
   for row in report['runs']:require(row['exit_code']==row['expected'],'Retained failed control was reclassified as a pass')
  tests=json.loads(z.read('packaged-tests/COMPLETED.json'));require(tests['junit']==dict(windows=dict(tests=17,errors=0,failures=0,skipped=0),linux=dict(tests=17,errors=0,failures=0,skipped=0)),'Packaged test population differs')
  setup=json.loads(z.read('private-runtime-setup/CONTROLS.json'));require(setup['prepared']['original_source_files_unchanged']==136 and setup['prepared']['scientific_analysis_executed'] is False and setup['prepared']['system_installation'] is False,'Private original build/source policy differs')
  review=json.loads(z.read('role-reviews/ROLE-REVIEW-PREVIEW.json'));require(review['positive_roles_reviewed']==24 and review['named_paragraphs']==47 and review['primary_xml_hashes_checked']==250 and review['canonical_catalog_modified'] is False,'Primary role review scope differs')
  completions=[n for n in names if n.startswith('bundle-views/') and n.endswith('/COMPLETED.tsv')];require(len(completions)==28,'Admitted bundle-view population differs');stages=0
  for name in completions:
   base=name.rsplit('/',1)[0]+'/';completion=kv(z.read(name));require(completion['profile']=='original-metal-20200505-captured-v1' and completion['result_admitted']=='true','Unexpected profile/admission');require(int(completion['markers']) in [3,2000],'Unexpected complete marker population')
   request=kv(z.read(base+'REQUEST.tsv'));original_request=kv(z.read(base+'REQUEST-ORIGINAL.tsv'));require(set(request)==set(original_request) and all(request[k]==original_request[k] for k in request if k not in ['executable','notice','studies']),'Original/canonical request declarations differ')
   original_studies=list(csv.DictReader(io.StringIO(z.read(base+'STUDIES-ORIGINAL.tsv').decode('utf-8')),delimiter='\t'));canonical_studies=list(csv.DictReader(io.StringIO(z.read(base+'inputs/STUDIES.tsv').decode('utf-8')),delimiter='\t'));require(len(original_studies)==len(canonical_studies) and all(x['study']==y['study'] and x['genomic_control']==y['genomic_control'] for x,y in zip(original_studies,canonical_studies)),'Original/canonical study-list declarations differ')
   summary=list(csv.DictReader(io.StringIO(z.read(base+'native/summary.tsv').decode('utf-8')),delimiter='\t'));require(len(summary)==int(completion['markers']) and len({r['marker'] for r in summary})==len(summary),'Complete native summary population differs')
   for stage in ['primary','post-gc']:
    path=base+'stages/'+stage+'/'
    if path+'PROCESS-FINISHED.tsv' not in names:continue
    end=kv(z.read(path+'PROCESS-FINISHED.tsv'));require(end['exit_code']=='0' and all(end[k]=='false' for k in ['timed_out','budget_exceeded','interrupted']),'A failed original stage was admitted');stages+=1
   if 'overlap' in base:
    require(all(r['declared_total_n']=='20000' and r['primary_weight']=='23317.00' and r['primary_overlap_adjusted_n']=='18341.50' for r in summary),'Overlap weight/N lineage collapsed')
    for row in summary:
     if row['primary_freq']!='-':require(row['primary_freq']=='0.2573' and row['primary_freq_min']==row['primary_freq_max']=='0.3000','Original overlap/frequency convention was silently repaired')
   if 'large-fractional-n' in base:require(next(r for r in summary if r['marker']=='M1')['declared_total_n']=='1.69136e+06','Large fractional-N source formatting changed')
   if any(r['post_gc']=='true' for r in summary):require(any(r['primary_het_df']=='1' for r in summary),'Post-GC lost primary heterogeneity lineage')
  require(stages==37,'Admitted original source-stage population differs')
  for name,value in v['source_guards'].items():require(pin(z.read('current-source/'+name))==value,'Archived qualified source differs: '+name)
 return dict(indexed_public_payloads=len(entries),admitted_bundle_views=28,admitted_original_stages=stages,individual_primary_reviews=24,canonical_catalog_modified=False)

def private(v,root,out,java,reader,verify_bundles):
 require(root.is_dir() and not root.is_symlink(),'Require the complete private closure directory');require(filepin(root/'INDEX.json')==v['private_closed_index'],'Private INDEX differs');index=json.loads((root/'INDEX.json').read_bytes());require(len(index['admitted_bundles'])==28 and index['reader']['pin']==v['reader'],'Private reader/bundle population differs')
 expected=index['payloads'];actual={p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file() and p.name!='INDEX.json'};require(actual==set(expected),'Private closure file membership differs')
 for name,value in expected.items():
  path=root/name;require(not path.is_symlink() and path.resolve().is_relative_to(root.resolve()),'Unsafe/linked private payload');require(filepin(path)==value,'Private payload differs: '+name)
 checked=0
 if verify_bundles:
  jar=reader or root/index['reader']['path'];require(filepin(jar)==v['reader'],'Wrong frozen reader JAR; keep the matching historical reader')
  for name,row in sorted(index['admitted_bundles'].items()):
   bundle=root/row['path'];require(bundle.resolve().is_relative_to(root.resolve()),'Unsafe private bundle path');command=[java,'-cp',str(jar),'org.jlinalg.cli.MetalWorkflowCli','verify','--bundle',str(bundle)]
   with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:p=subprocess.run(command,cwd=ROOT,env=dict(os.environ,PATH=''),stdout=so,stderr=se,stdin=subprocess.DEVNULL,timeout=90)
   require(p.returncode==0,'Current frozen-reader audit failed: '+name+'; read retained consoles');checked+=1
 return dict(private_payloads_rehashed=len(expected),private_bundles_reverified_now=checked)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--closed-evidence',type=Path);p.add_argument('--verify-bundles',action='store_true');p.add_argument('--java',default='java');p.add_argument('--reader',type=Path);a=p.parse_args();out=None
 try:
  if sys.version_info<(3,9):raise FileNotFoundError('Developer audit needs Python3.9+; runtime analysis needs Java17+')
  target=a.out.absolute();require(target.resolve().is_relative_to(ROOT/'build'),'Choose NEW workspace build audit output');target.mkdir(parents=True,exist_ok=False);out=target
  v=json.loads((EVIDENCE/'VALIDATION.json').read_bytes());require(v['status']=='PASS_ORIGINAL_METAL_TYPED_WORKFLOW_QUALIFICATION' and v['new_family_credit']==0 and v['catalog_modified'] is False and v['whole_goal_complete'] is False,'Qualification scope/status differs')
  require(filepin(Path(__file__))==v['audit_source_guard'],'Qualification audit source changed; qualify changed audit code separately')
  for name,value in v['source_guards'].items():require(filepin(ROOT/name)==value,'Qualified method/setup/review/example source changed: '+name)
  scripts=EVIDENCE/'reproduction-scripts.zip';require(filepin(scripts)==v['reproduction_scripts_archive'],'Reproduction script archive changed')
  with zipfile.ZipFile(scripts) as z:
   names=z.namelist();entries=json.loads(z.read('ENTRY-MANIFEST.json'))['payloads'];require(len(names)==len(set(names)) and set(names)==set(entries)|{'ENTRY-MANIFEST.json'} and len(entries)==v['reproduction_script_payloads'],'Reproduction script inventory differs')
   for name,value in entries.items():require(pin(z.read(name))==value,'Reproduction script changed: '+name)
  result=archive(v);require(not a.verify_bundles or a.closed_evidence is not None,'Actual frozen-reader verification requires --closed-evidence')
  if a.closed_evidence:result.update(private(v,a.closed_evidence,out,a.java,a.reader,a.verify_bundles))
  else:result.update(private_payloads_rehashed=0,private_bundles_reverified_now=0)
  for name,value in v['source_guards'].items():require(filepin(ROOT/name)==value,'Qualified source changed during audit: '+name)
  result.update(status=v['status'],original_runtime_reexecuted=False,whole_goal_complete=False,new_family_credit=0,public_views_are_standalone_bundles=False)
  (out/'COMPLETED.json').write_bytes((json.dumps(result,indent=2)+'\n').encode());print(json.dumps(result),flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,(FileNotFoundError,PermissionError)) else 2;failure=dict(status='SKIP3_DEPENDENT_AUDIT_SKIPPED' if code==3 else 'FAIL2_NO_QUALIFICATION_ADMITTED',error=repr(e),detail=traceback.format_exc(),required_action=ACTION)
  if out is not None:(out/'FAILURE.json').write_bytes((json.dumps(failure,indent=2)+'\n').encode())
  print(failure['status']+': '+str(e)+'\n'+ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
