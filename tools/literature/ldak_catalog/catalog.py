#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit original LDAK workflows while correcting positive versus contextual roles."""
import argparse,copy,hashlib,json,subprocess,sys,traceback,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
sys.dont_write_bytecode=True
sys.path.insert(0,str(ROOT/'tools/literature'))
if not __debug__:
 print('FAIL2: source assertions cannot be disabled. Rerun without -O; no admission.',file=sys.stderr);raise SystemExit(2)
from collect_omics_review import validate_supporting_evidence
from ldak_workflows.primary import audit as parent_primary
from ldak_workflows.payloads import classes
from common_statistics.prism_catalog import manifest_audit,refresh_current_manifest
from ldak_catalog.model import META,DATA,GUIDE,COVERAGE,NOTE,ACTION,POSITIVE,registry,transition,check,require
from ldak_catalog.evidence import audit as deep_audit
BASE='586f758f0835bb2f40fb6792bc1bb2da6353808f'
FOLDER='docs/literature/ldak-catalog-admission'
PROOF=DATA+'/ldak-catalog-validation.json'
COMPAT=DATA+'/ldak-catalog-shared-source-compatibility.json'
OWN=['tools/literature/ldak_catalog/catalog.py','tools/literature/ldak_catalog/model.py','tools/literature/ldak_catalog/evidence.py','tools/literature/ldak_catalog/test_catalog.py']
FOUNDATION=['tools/literature/ldak_workflows/primary.py','tools/literature/ldak_workflows/model.py','tools/literature/ldak_workflows/payloads.py','tools/literature/ldak_original/audit_roles.py','tools/literature/ldsc_hsq/catalog.py','tools/literature/common_statistics/prism_catalog.py','tools/literature/collect_omics_review.py','tools/literature/catalog_fixed_cohort.py','tools/literature/catalog_source_integrity.py','tools/literature/wire_signac_catalog.py']
CHANGED_CONTEXT='P245'

def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):
 path=Path(path)
 with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=ROOT)
def baseline():
 before={name:git('show',BASE+':'+name) for name in META};parent=json.loads(git('show',BASE+':'+DATA+'/ldsc-catalog-validation.json'));require(parent['after']=={n:rawpin(v) for n,v in before.items()},'Current predecessor is not the actual cumulative LDSC admission');return before

def primary(cache,before):
 census=parent_primary(cache,papers=json.loads(before[META[0]]));require(census['papers_scanned']==250 and len(census['roles'])==6,'Complete primary recheck required')
 census=copy.deepcopy(census);census.update(status='PASS250_PRIMARY_IDENTITIES_FIVE_POSITIVE_LDAK_ROLES_ONE_CONTEXT',positive_roles=sorted(POSITIVE),excluded_context_roles=['P245'],observations_added=['P238'],observations_removed=['P245'],historical_development_curation_unchanged=True)
 for role in census['roles']:
  role['positive_record_retained']=role['paper_id'] in POSITIVE
  role['selected_project_profile']='5.2' if role['paper_id'] in POSITIVE else None
 return census

def qualification(private_root,checks_root):
 private_root=Path(private_root);checks_root=Path(checks_root);closure_path='docs/literature/ldak-workflow-development/EVIDENCE-CLOSURE.json';workflow_path='docs/literature/ldak-workflow-development/WORKFLOW-VALIDATION.json';closure=json.loads((ROOT/closure_path).read_bytes());workflow=json.loads((ROOT/workflow_path).read_bytes())
 require((ROOT/closure_path).read_bytes()==git('show',BASE+':'+closure_path) and (ROOT/workflow_path).read_bytes()==git('show',BASE+':'+workflow_path),'Preserved original qualification changed')
 require(closure['payload_files']==11474 and closure['current_complete_bundles']==23 and closure['class_fingerprints']==48,'Original closure incomplete');require(workflow['java_tests']==dict(tests=58,failures=0,errors=0,skipped=0) and workflow['total_process_controls']==92,'Original execution population differs')
 require(pin(private_root/'INDEX.json')==closure['index'],'Closed physical index differs');index=json.loads((private_root/'INDEX.json').read_bytes())
 intake=json.loads((checks_root/'COMPLETED.json').read_bytes());physical=json.loads((checks_root/'CLOSED-EVIDENCE-RECHECK.json').read_bytes());reader=json.loads((checks_root/'current-reader-1/COMPLETED.json').read_bytes());method=json.loads((checks_root/'current-reader-1/METHOD-COMPATIBILITY.json').read_bytes())
 require(intake['status']=='PASS_CLOSED_LDAK_INTAKE_AND_CURRENT_PRIMARY_SOURCES' and physical['index']==closure['index'] and physical['payload_files']==11474 and physical['unique_objects']==2874,'Missing current complete physical evidence audit')
 require(reader['status']=='PASS_CURRENT_SOURCE_JAR_23_CLOSED_BUNDLE_VERIFICATIONS_AND_TWO_GENUINE_REPLAYS' and reader['verified_bundles']==23 and reader['genuine_original_replays']==2 and len(reader['processes'])==25 and all(p['exit_code']==0 for p in reader['processes']),'Current source-build reader was not qualified')
 jar=ROOT/'build/ldsc-reference-cli-1/build-2/jar/omicsmatic-0.4.0.jar';require(pin(jar)==reader['current_jar']==method['current_jar'],'Current candidate JAR changed');require(classes(jar)==method['method_classes'] and len(method['method_classes'])==48 and method['closed_reader']==closure['reader'],'Current method bytecode does not match its closed original reader')
 packet={};source_guards={}
 for name,row in index['files'].items():
  if name.startswith('source/'):
   relative=name[len('source/'):];packet['historical-source/'+relative]=(private_root/name).read_bytes()
   if relative.startswith('src/') and 'Ldak' in Path(relative).name:
    require(pin(ROOT/relative)==row['pin'],'Qualified LDAK source changed: '+relative);source_guards[relative]=row['pin']
  if name.startswith('records/') and Path(name).name in ['summary.tsv','DIAGNOSTICS.tsv','COMPLETED.properties','scientific.properties','implementation.properties','PROCESS-STARTED.properties','PROCESS-FINISHED.properties']:
   packet['retained-reports/'+name]=(private_root/name).read_bytes()
 packet['closed-INDEX.json']=(private_root/'INDEX.json').read_bytes()
 for key,entry in workflow['records'].items():
  p=private_root/entry['path'].replace('build/ldak/','records/',1)
  if key=='java_build':p=private_root/'build-records/workflow-build-7/COMPLETED.json'
  require(pin(p)==entry['pin'],'Original qualification receipt changed: '+key);packet['qualification-receipts/'+key+'.json']=p.read_bytes()
 for name in ['COMPLETED.json','CLOSED-EVIDENCE-RECHECK.json','current-reader-1/COMPLETED.json','current-reader-1/METHOD-COMPATIBILITY.json']:packet['current-checks/'+name]=(checks_root/name).read_bytes()
 for name in ['build.gradle','src/main/java/org/jlinalg/compute/BackendContext.java','src/main/java/org/jlinalg/compute/BackendPolicy.java']:
  raw=(ROOT/name).read_bytes();require(raw==git('show',BASE+':'+name),'Current build/shared arithmetic source changed since the checked JAR: '+name);source_guards[name]=rawpin(raw);packet['current-qualified-source/'+name]=raw
 for p in (checks_root/'current-reader-1').glob('*'):
  if p.is_file():packet['current-reader-controls/'+p.name]=p.read_bytes()
 for folder in ['replay-sumher','replay-kinship']:
  for p in (checks_root/'current-reader-1'/folder).rglob('*'):
   if p.is_file() and 'runtime' not in p.relative_to(checks_root/'current-reader-1'/folder).parts:packet['selected-replay-payloads/'+folder+'/'+p.relative_to(checks_root/'current-reader-1'/folder).as_posix()]=p.read_bytes()
 for name in [closure_path,workflow_path,'docs/literature/ldak-workflow-development/CODE-IDENTITY-CORRECTION.json','docs/literature/ldak-workflow-development/PAPER-ROLES.json']:packet['preserved-records/'+name]=(ROOT/name).read_bytes()
 proof=dict(status='PASS_CURRENT_METHOD_IDENTITY_CLOSED_PHYSICAL_EVIDENCE_AND_25_READER_PROCESSES',closed_index=closure['index'],closed_reader=closure['reader'],current_reader=reader['current_jar'],method_classes=method['method_classes'],current_verified_bundles=23,current_genuine_replays=2,retained_java_tests=58,retained_original_process_controls=92,physical_payload_files=11474,physical_unique_objects=2874,physical_unique_object_bytes=2975360402,source_files=99,source_guards=source_guards,whole_tool_qualified=False,whole_paper_reproduced=False,historical_fingerprints_refreshed=False,original_runtime_in_public_packet=False,complete_private_payload_in_public_packet=False)
 return proof,packet

def packet_write(folder,payload):
 entries={name:rawpin(raw) for name,raw in payload.items()};path=folder/'qualification-evidence.zip'
 with zipfile.ZipFile(path,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,raw in sorted(payload.items()):z.writestr(name,raw)
  z.writestr('ENTRY-MANIFEST.json',encoded(dict(payloads=entries)))
 with zipfile.ZipFile(path) as z:
  require(set(z.namelist())==set(entries)|{'ENTRY-MANIFEST.json'},'Qualification packet inventory incomplete')
  for name,value in entries.items():require(rawpin(z.read(name))==value,'Qualification packet readback differs: '+name)
 return dict(archive=pin(path),indexed_payloads=len(entries),all_archive_payloads_read_back=True)

def verify(cache,out,closed_root=None):
 proof=json.loads((ROOT/PROOF).read_bytes());require(proof['status']=='PASS_LDAK_CURRENT_CATALOG_ADMISSION_AND_SOURCE_CORRECTION','Missing LDAK admission');require(proof['source_code']=={n:pin(ROOT/n) for n in OWN},'Catalog audit source changed; preserve its matching version')
 with zipfile.ZipFile(ROOT/FOLDER/'catalog-before.zip') as z:require(set(z.namelist())==set(META) and len(z.namelist())==8,'Incomplete predecessor archive');before={name:z.read(name) for name in META}
 require(before==baseline(),'Archived predecessor differs from actual Git')
 for n,v in proof['evidence'].items():require(pin(ROOT/n)==v,'Catalog evidence changed: '+n)
 with zipfile.ZipFile(ROOT/FOLDER/'qualification-evidence.zip') as z:
  require(pin(ROOT/FOLDER/'qualification-evidence.zip')==proof['qualification_packet']['archive'],'Qualification archive summary differs')
  entries=json.loads(z.read('ENTRY-MANIFEST.json'))['payloads'];require(len(entries)==proof['qualification_packet']['indexed_payloads'] and len(set(z.namelist()))==len(z.namelist()) and set(z.namelist())==set(entries)|{'ENTRY-MANIFEST.json'},'Scientific packet membership differs')
  for n,v in entries.items():require(rawpin(z.read(n))==v,'Scientific packet payload differs: '+n)
  q=proof['workflow_qualification'];method=json.loads(z.read('current-checks/current-reader-1/METHOD-COMPATIBILITY.json'));require(q['method_classes']==method['method_classes'] and len(method['method_classes'])==48,'Current/closed reader method identity differs')
  reader=json.loads(z.read('current-checks/current-reader-1/COMPLETED.json'));require(reader['verified_bundles']==23 and reader['genuine_original_replays']==2 and all(p['exit_code']==0 for p in reader['processes']),'Current reader controls incomplete')
  require(q['current_reader']==reader['current_jar']==method['current_jar'] and q['closed_reader']==method['closed_reader'],'Reader identities differ');require(rawpin(z.read('closed-INDEX.json'))==q['closed_index'],'Closed physical index identity differs')
 for name,value in proof['foundation_sources'].items():require(pin(ROOT/name)==rawpin(git('show',BASE+':'+name))==value,'Preserved catalog/source-audit helper changed: '+name)
 for name,value in proof['workflow_qualification']['source_guards'].items():require(pin(ROOT/name)==value,'Qualified scientific source changed: '+name)
 census=primary(cache,before);require(census==json.loads((ROOT/COVERAGE).read_bytes()),'Current primary-source recheck differs');after={name:(ROOT/name).read_bytes() for name in META};result=check(before,after,census);require(proof['before']=={n:rawpin(v) for n,v in before.items()} and proof['after']=={n:rawpin(v) for n,v in after.items()},'Recorded metadata fingerprints differ')
 for name,value in result.items():require(proof.get(name)==value,'Recorded admission summary differs: '+name)
 audit=manifest_audit();require(not audit['mismatches'] and not audit['missing_entries'] and not audit['extra_entries'],'Current data manifest differs');deep=deep_audit(closed_root,proof['workflow_qualification']['closed_index']) if closed_root else None
 result=dict(status=proof['status'],**result,source_xml_hashes_checked=250,current_manifest=audit,qualification_packet_payloads=len(entries),closed_physical_payloads_read_now=deep is not None,closed_physical_audit=deep,scientific_runtime_reexecuted=False)
 (out/'COMPLETED.json').write_bytes(encoded(result));return result

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);mode=p.add_mutually_exclusive_group(required=True);mode.add_argument('--plan',action='store_true');mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true');p.add_argument('--primary-cache',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--closed-evidence',type=Path);p.add_argument('--current-checks',type=Path);a=p.parse_args();out=None;before=after=None;started=False
 try:
  if sys.version_info<(3,11):raise FileNotFoundError('Python 3.11+ and Git are required for this developer catalog audit')
  target=a.out.absolute();require(target.resolve().is_relative_to(ROOT/'build'),'Use a NEW workspace build evidence output');target.mkdir(parents=True,exist_ok=False);out=target
  if a.verify:result=verify(a.primary_cache,out,a.closed_evidence)
  else:
   require(a.closed_evidence is not None and a.current_checks is not None,'Supply the complete retained closed evidence and current reader-check roots');before=baseline();require(all((ROOT/n).read_bytes()==raw for n,raw in before.items()),'Canonical metadata differs from the exact cumulative predecessor; preserve/review changes');old_manifest=manifest_audit();require(not old_manifest['mismatches'] and not old_manifest['missing_entries'] and not old_manifest['extra_entries'],'Predecessor manifest differs')
   foundation={n:pin(ROOT/n) for n in FOUNDATION}
   for name,value in foundation.items():require(rawpin(git('show',BASE+':'+name))==value,'Preserved helper changed: '+name)
   census=primary(a.primary_cache,before);q,payload=qualification(a.closed_evidence,a.current_checks);after=transition(before,census);result=check(before,after,census);(out/'PLAN.json').write_bytes(encoded(result));(out/'SOURCE_CENSUS.json').write_bytes(encoded(census));(out/'QUALIFICATION.json').write_bytes(encoded(q))
   for name,raw in after.items():dest=out/'preview'/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
   if a.apply:
    folder=ROOT/FOLDER;require(not folder.exists() and all(not (ROOT/n).exists() for n in [PROOF,COVERAGE,COMPAT]),'Existing admission evidence must be retained; choose a separate transition');folder.mkdir();(folder/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(folder/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
     for name,raw in before.items():z.writestr(name,raw)
    (folder/'REGISTRY.json').write_bytes(encoded(registry()));(folder/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/DATA/'SHA256SUMS').read_bytes());(ROOT/COVERAGE).write_bytes(encoded(census));packet=packet_write(folder,payload)
    from wire_signac_catalog import delta
    from catalog_source_integrity import reverse_delta
    name='tools/literature/reviewed-uses.json';change=delta(name,before[name],after[name]);require(reverse_delta(after[name],change)==before[name],'Review inverse differs');(ROOT/COMPAT).write_bytes(encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='ldak',scope='Five positive role appends; context exclusion is retained in the primary crosswalk. No scientific source/result overlay or historical fingerprint refresh.',paths=[change])))
    (folder/'WARNINGS.md').write_bytes(('# Scoped LDAK admission: warnings and required actions\n\n'+NOTE+'\n\n'+ACTION+'\n\nThe portable qualification packet contains selected receipts/reports/source snapshots, not the complete private 3 GB physical evidence store or redistributed runtime. Selected replay payloads are evidence views, not standalone admitted execution bundles. Use the complete retained store for the optional deep audit.\n').encode())
    with zipfile.ZipFile(folder/'verifier-source.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
     for name in OWN:z.writestr(name,(ROOT/name).read_bytes())
    require(all((ROOT/n).read_bytes()==raw for n,raw in before.items()),'Canonical metadata changed before admission');started=True
    for name,raw in after.items():(ROOT/name).write_bytes(raw)
    proof=dict(status='PASS_LDAK_CURRENT_CATALOG_ADMISSION_AND_SOURCE_CORRECTION',**result,base_commit=BASE,source_code={n:pin(ROOT/n) for n in OWN},foundation_sources=foundation,before={n:rawpin(v) for n,v in before.items()},after={n:rawpin(v) for n,v in after.items()},workflow_qualification=q,qualification_packet=packet,predecessor_current_manifest=old_manifest,evidence={p.relative_to(ROOT).as_posix():pin(p) for p in folder.rglob('*') if p.is_file()},scientific_engines_changed=False,historical_fingerprints_refreshed=False,earlier_sources_or_verifiers_overlaid=False,website_deployed=False)
    for name in [COVERAGE,COMPAT]:proof['evidence'][name]=pin(ROOT/name)
    (ROOT/PROOF).write_bytes(encoded(proof));refresh_current_manifest();audit=manifest_audit();require(not audit['mismatches'] and not audit['missing_entries'] and not audit['extra_entries'],'Current manifest recheck failed');(out/'COMPLETED.json').write_bytes(encoded(dict(status=proof['status'],**result,current_manifest=audit)))
   else:(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_REVIEWABLE_LDAK_CURRENT_CATALOG_PLAN',**result)))
  print(json.dumps(result),flush=True);return 0
 except Exception as e:
  restored=[]
  if started:
   for name,raw in before.items():
    if (ROOT/name).read_bytes()==after[name]:(ROOT/name).write_bytes(raw);restored.append(name)
  code=3 if isinstance(e,(FileNotFoundError,PermissionError)) else 2;record=dict(status='SKIP3_DEPENDENT_ADMISSION_SKIPPED' if code==3 else 'FAIL2_NO_CATALOG_ADMISSION',error=repr(e),detail=traceback.format_exc(),restored_metadata=restored,required_action=ACTION)
  if out is not None:(out/'FAILURE.json').write_bytes(encoded(record))
  print(record['status']+': '+record['detail']+'\n'+ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
