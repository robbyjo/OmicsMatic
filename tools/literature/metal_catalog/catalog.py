#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Plan, admit and verify source-specific METAL workflows on the cumulative catalog."""
import argparse,json,sys,traceback,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];sys.dont_write_bytecode=True;sys.path.insert(0,str(ROOT/'tools/literature'))
if not __debug__:
 print('FAIL2: rerun without -O; source assertions must not be disabled.',file=sys.stderr);raise SystemExit(2)
from common_statistics.prism_catalog import manifest_audit,refresh_current_manifest
from metal_catalog.model import META,DATA,GUIDE,COVERAGE,NOTE,ACTION,encoded,registry,transition,check,require
from metal_catalog.primary import audit as primary_audit
from metal_catalog.evidence import BASE,FOUNDATION,baseline,predecessor,qualification,public_qualification,packet_write,packet_read,pin,rawpin,git
from metal_workflows import verify as workflow

FOLDER='docs/literature/metal-catalog-admission'
PROOF=DATA+'/metal-catalog-validation.json'
COMPAT=DATA+'/metal-catalog-shared-source-compatibility.json'
OWN=['tools/literature/metal_catalog/'+n for n in ['primary.py','model.py','evidence.py','catalog.py','test_catalog.py']]
STATUS='PASS_METAL_CUMULATIVE_CATALOG_ADMISSION_AND24_SOURCE_ROLES'


def primary(cache,before):
 definition=next(t for t in registry() if t['id']=='metal')
 return primary_audit(cache,json.loads(before[META[0]]),definition)


def clean_manifest():
 result=manifest_audit();require(not result['mismatches'] and not result['missing_entries'] and not result['extra_entries'],'Current data manifest differs');return result


def verify(cache,out,private_root=None,verify_bundles=False,java='java'):
 proof=json.loads((ROOT/PROOF).read_bytes());require(proof['status']==STATUS and proof['base_commit']==BASE,'Missing or different METAL admission')
 require(proof['source_code']=={n:pin(ROOT/n) for n in OWN},'Admission verifier source changed; retain its qualified version')
 with zipfile.ZipFile(ROOT/FOLDER/'catalog-before.zip') as z:
  require(len(z.namelist())==8 and set(z.namelist())==set(META),'Incomplete predecessor archive');before={n:z.read(n) for n in META}
 require(before==baseline(),'Archived predecessor differs from actual cumulative Git state')
 require((ROOT/FOLDER/'PREVIOUS-SHA256SUMS').read_bytes()==git('show',BASE+':'+DATA+'/SHA256SUMS'),'Predecessor data manifest changed')
 for name,value in proof['evidence'].items():require(pin(ROOT/name)==value,'Admission evidence differs: '+name)
 for name,value in proof['foundation_sources'].items():require(pin(ROOT/name)==rawpin(git('show',BASE+':'+name))==value,'Preserved audit/source foundation changed: '+name)
 require(json.loads((ROOT/FOLDER/'REGISTRY.json').read_bytes())==registry(),'Admitted registry differs')
 prior=predecessor(cache,before);require(prior==proof['predecessor_audit'],'Retained predecessor verification differs')
 q=proof['workflow_qualification'];v,public=public_qualification();require(q['public_audit']==public and q['source_guards']==v['source_guards'] and q['audit_source_guard']==v['audit_source_guard'] and q['closed_index']==v['private_closed_index'] and q['reader']==v['reader'],'Qualified METAL identity/scope differs')
 require(q['original_qualification']==pin(workflow.EVIDENCE/'VALIDATION.json') and q['original_public_archive']==v['archive'] and q['reproduction_scripts_archive']==v['reproduction_scripts_archive'],'Original qualification artifact identity differs')
 count=packet_read(ROOT/FOLDER/'qualification-evidence.zip',proof['qualification_packet'])
 with zipfile.ZipFile(ROOT/FOLDER/'qualification-evidence.zip') as z:
  require(rawpin(z.read('closed-INDEX.json'))==q['closed_index'],'Closed INDEX differs');index=json.loads(z.read('closed-INDEX.json'));require(index['reader']['pin']==q['reader'] and len(index['payloads'])==813 and len(index['admitted_bundles'])==28,'Closed population/reader differs')
  report_raw=z.read('current-reader-checks/COMPLETED.json');report=json.loads(report_raw);require(rawpin(report_raw)==q['retained_reader_check'],'Saved reader check differs')
  expected=dict(public);expected.pop('reproduction_script_payloads');expected.update(private_payloads_rehashed=813,private_bundles_reverified_now=28,status=v['status'],original_runtime_reexecuted=False,whole_goal_complete=False,new_family_credit=0,public_views_are_standalone_bundles=False);require(report==expected,'Retained current-reader qualification differs')
  for name,value in v['source_guards'].items():require(rawpin(z.read('qualified-project-source/'+name))==value,'Archived qualified project source differs: '+name)
  require(rawpin(z.read('qualified-auditor.py'))==v['audit_source_guard'],'Archived qualified auditor differs')
  for name in index['admitted_bundles']:
   for suffix in ['.stdout','.stderr']:require('current-reader-checks/'+name+suffix in z.namelist(),'Reader console missing')
 census=primary(cache,before);require(census==json.loads((ROOT/COVERAGE).read_bytes()),'Current primary census differs');after={n:(ROOT/n).read_bytes() for n in META};result=check(before,after,census)
 require(proof['before']=={n:rawpin(v) for n,v in before.items()} and proof['after']=={n:rawpin(v) for n,v in after.items()},'Recorded metadata fingerprints differ');require(all(proof.get(k)==v for k,v in result.items()),'Recorded admission counts differ')
 from catalog_source_integrity import reverse_delta,verify_source
 compat=json.loads((ROOT/COMPAT).read_bytes());require(compat['schema_version']==1 and compat['status']=='passed-dispatch-catalog-only-source-deltas' and len(compat['paths'])==1 and compat['paths'][0]['path']=='tools/literature/reviewed-uses.json','Review compatibility scope differs')
 name='tools/literature/reviewed-uses.json';require(reverse_delta(after[name],compat['paths'][0])==before[name],'Review inverse differs');verify_source(ROOT,ROOT/DATA,name,rawpin(before[name])['sha256'])
 require(not verify_bundles or private_root is not None,'Actual reader checks need --closed-evidence');deep=workflow.private(v,Path(private_root),out,java,None,verify_bundles) if private_root is not None else dict(private_payloads_rehashed=0,private_bundles_reverified_now=0)
 report=dict(status=STATUS,**result,current_manifest=clean_manifest(),source_xml_hashes_checked=250,qualification_packet_payloads=count,original_public_payloads_read=public['indexed_public_payloads'],original_script_payloads_read=public['reproduction_script_payloads'],predecessor_qualification_payloads_read=prior['qualification_payloads_read'],**deep,original_runtime_reexecuted=False,scientific_engines_changed=False,earlier_sources_or_verifiers_overlaid=False)
 (out/'COMPLETED.json').write_bytes(encoded(report));return report


def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);mode=p.add_mutually_exclusive_group(required=True);mode.add_argument('--plan',action='store_true');mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true');p.add_argument('--primary-cache',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--closed-evidence',type=Path);p.add_argument('--current-checks',type=Path);p.add_argument('--verify-bundles',action='store_true');p.add_argument('--java',default='java');a=p.parse_args();out=None;journal={};written={}
 try:
  if sys.version_info<(3,11):raise FileNotFoundError('Python3.11+ and Git are required for the developer admission audit')
  target=a.out.absolute();require(target.resolve().is_relative_to(ROOT/'build'),'Choose NEW workspace build evidence output');target.mkdir(parents=True,exist_ok=False);out=target
  if a.verify:result=verify(a.primary_cache,out,a.closed_evidence,a.verify_bundles,a.java)
  else:
   require(a.closed_evidence is not None and a.current_checks is not None,'Supply complete private evidence and the successful 28-bundle reader-check directory')
   require(not a.verify_bundles,'Use --verify-bundles with --verify; plan/apply bind retained reader checks and rehash private bytes')
   before=baseline();require(all((ROOT/n).read_bytes()==raw for n,raw in before.items()),'Canonical metadata differs from exact predecessor; preserve and review current changes');old_manifest=clean_manifest()
   foundation={n:pin(ROOT/n) for n in FOUNDATION}
   for name,value in foundation.items():require(value==rawpin(git('show',BASE+':'+name)),'Preserved foundation changed: '+name)
   prior=predecessor(a.primary_cache,before);census=primary(a.primary_cache,before);q,payload=qualification(a.closed_evidence,a.current_checks,out);after=transition(before,census);result=check(before,after,census)
   for name,obj in [('PLAN.json',result),('SOURCE-CENSUS.json',census),('QUALIFICATION.json',q),('PREDECESSOR-CHECK.json',prior)]:(out/name).write_bytes(encoded(obj))
   for name,raw in after.items():dest=out/'preview'/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
   if a.apply:
    folder=ROOT/FOLDER;require(not folder.exists() and all(not (ROOT/n).exists() for n in [PROOF,COVERAGE,COMPAT]),'Admission paths already exist; preserve evidence and use a separate transition');folder.mkdir();(folder/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(folder/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
     for name,raw in before.items():z.writestr(name,raw)
    (folder/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/DATA/'SHA256SUMS').read_bytes());(folder/'REGISTRY.json').write_bytes(encoded(registry()));(folder/'PREDECESSOR-CHECK.json').write_bytes(encoded(prior));packet=packet_write(folder,payload)
    (folder/'WARNINGS.md').write_bytes(('# METAL admission: warnings and required actions\n\n'+NOTE+'\n\n'+ACTION+'\n\nThe portable packet contains selected receipts, consoles, our project source and a private INDEX, not the complete execution store or original METAL runtime/source. No original runtime was re-executed for catalog admission. The full goal remains unfinished.\n').encode())
    with zipfile.ZipFile(folder/'verifier-source.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
     for name in OWN:z.writestr(name,(ROOT/name).read_bytes())
    from wire_signac_catalog import delta
    from catalog_source_integrity import reverse_delta
    name='tools/literature/reviewed-uses.json';change=delta(name,before[name],after[name]);require(reverse_delta(after[name],change)==before[name],'Review inverse differs')
    extras={COVERAGE:encoded(census),COMPAT:encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='metal',scope='24 individually reviewed METAL appends. Exact review-JSON inverse only; no scientific source/result overlay, observation membership change or historical fingerprint refresh.',paths=[change]))}
    proof=dict(status=STATUS,**result,base_commit=BASE,source_code={n:pin(ROOT/n) for n in OWN},foundation_sources=foundation,before={n:rawpin(raw) for n,raw in before.items()},after={n:rawpin(raw) for n,raw in after.items()},predecessor_audit=prior,workflow_qualification=q,qualification_packet=packet,predecessor_current_manifest=old_manifest,evidence={p.relative_to(ROOT).as_posix():pin(p) for p in folder.rglob('*') if p.is_file()},scientific_engines_changed=False,historical_fingerprints_refreshed=False,earlier_sources_or_verifiers_overlaid=False,website_deployed=False)
    proof['evidence'].update({n:rawpin(raw) for n,raw in extras.items()});extras[PROOF]=encoded(proof)
    require(all((ROOT/n).read_bytes()==raw for n,raw in before.items()),'Canonical metadata changed before admission');require(clean_manifest()==old_manifest,'Manifest changed before admission')
    journal={**before,**{n:None for n in extras},DATA+'/SHA256SUMS':(ROOT/DATA/'SHA256SUMS').read_bytes()}
    for name,raw in {**after,**extras}.items():(ROOT/name).write_bytes(raw);written[name]=raw
    refresh_current_manifest();written[DATA+'/SHA256SUMS']=(ROOT/DATA/'SHA256SUMS').read_bytes();audit=clean_manifest()
    (out/'COMPLETED.json').write_bytes(encoded(dict(status=STATUS,**result,current_manifest=audit)));journal={}
   else:(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_REVIEWABLE_METAL_CUMULATIVE_CATALOG_PLAN',**result)))
  print(json.dumps(result),flush=True);return 0
 except Exception as error:
  restored=[];retained=[]
  for name,old in journal.items():
   path=ROOT/name
   if name not in written or not path.exists() or path.read_bytes()!=written[name]:continue
   if old is not None:path.write_bytes(old);restored.append(name)
   else:
    dest=out/'retained-failed-metadata'/name;dest.parent.mkdir(parents=True,exist_ok=True);path.rename(dest);retained.append(name)
  code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2;failure=dict(status='SKIP3_DEPENDENT_ADMISSION_SKIPPED' if code==3 else 'FAIL2_NO_CATALOG_ADMISSION',error=repr(error),detail=traceback.format_exc(),restored_metadata=restored,failed_new_metadata_retained=retained,required_action=ACTION)
  if out is not None:(out/'FAILURE.json').write_bytes(encoded(failure))
  print(failure['status']+': '+failure['detail']+'\n'+ACTION,file=sys.stderr);return code


if __name__=='__main__':raise SystemExit(main())
