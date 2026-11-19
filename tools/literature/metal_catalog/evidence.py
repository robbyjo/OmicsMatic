# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind current METAL qualification and the exact retained cumulative predecessor."""
import json,subprocess,zipfile
from pathlib import Path
from metal_workflows import verify as workflow
from metal_catalog.model import META,DATA,ROOT,encoded,require

BASE='b25f8e15bb9af3c3da7dc5344dc7a4b1427c2a9c'
FOUNDATION=['tools/literature/ldak_catalog/catalog.py','tools/literature/ldak_catalog/model.py','tools/literature/ldak_catalog/evidence.py','tools/literature/ldak_workflows/primary.py','tools/literature/ldak_workflows/model.py','tools/literature/ldak_workflows/payloads.py','tools/literature/ldak_original/audit_roles.py','tools/literature/ldsc_hsq/catalog.py','tools/literature/common_statistics/prism_catalog.py','tools/literature/collect_omics_review.py','tools/literature/catalog_fixed_cohort.py','tools/literature/catalog_source_integrity.py','tools/literature/wire_signac_catalog.py','tools/literature/metal_workflows/verify.py','tools/literature/metal_workflows/reviews.py']
pin=workflow.filepin
rawpin=workflow.pin


def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=ROOT)


def baseline():
 before={name:git('show',BASE+':'+name) for name in META}
 proof=json.loads(git('show',BASE+':'+DATA+'/ldak-catalog-validation.json'))
 require(proof['after']=={n:rawpin(raw) for n,raw in before.items()},'Actual Git predecessor differs from cumulative LDAK admission')
 return before


def packet_read(path,expected):
 require(pin(path)==expected['archive'],'Evidence archive bytes differ: '+str(path))
 with zipfile.ZipFile(path) as z:
  names=z.namelist();entries=json.loads(z.read('ENTRY-MANIFEST.json'))['payloads']
  require(len(names)==len(set(names)) and set(names)==set(entries)|{'ENTRY-MANIFEST.json'} and len(entries)==expected['indexed_payloads'],'Evidence archive membership differs')
  for name,value in entries.items():require(rawpin(z.read(name))==value,'Evidence payload differs: '+name)
 return len(entries)


def predecessor(cache,before):
 """Run unchanged LDAK source logic against exact retained bytes, without an overlay."""
 from ldak_catalog import catalog as parent
 from ldak_catalog.model import check as parent_check
 proof_path=DATA+'/ldak-catalog-validation.json';proof_raw=(ROOT/proof_path).read_bytes();require(proof_raw==git('show',BASE+':'+proof_path),'Predecessor proof changed');proof=json.loads(proof_raw)
 require(proof['source_code']=={n:pin(ROOT/n) for n in parent.OWN},'Retained LDAK verifier source changed')
 for name,value in proof['evidence'].items():require(pin(ROOT/name)==value,'Retained LDAK evidence differs: '+name)
 for name,value in proof['foundation_sources'].items():require(pin(ROOT/name)==rawpin(parent.git('show',parent.BASE+':'+name))==value,'LDAK foundation changed: '+name)
 for name,value in proof['workflow_qualification']['source_guards'].items():require(pin(ROOT/name)==value,'Qualified LDAK scientific source changed: '+name)
 with zipfile.ZipFile(ROOT/parent.FOLDER/'catalog-before.zip') as z:
  require(len(z.namelist())==8 and set(z.namelist())==set(META),'LDAK predecessor archive differs');old={n:z.read(n) for n in META}
 require(old==parent.baseline(),'LDAK predecessor archive differs from actual earlier Git state')
 count=packet_read(ROOT/parent.FOLDER/'qualification-evidence.zip',proof['qualification_packet'])
 with zipfile.ZipFile(ROOT/parent.FOLDER/'qualification-evidence.zip') as z:
  q=proof['workflow_qualification'];method=json.loads(z.read('current-checks/current-reader-1/METHOD-COMPATIBILITY.json'));reader=json.loads(z.read('current-checks/current-reader-1/COMPLETED.json'))
  require(q['method_classes']==method['method_classes'] and len(method['method_classes'])==48,'LDAK method identity differs')
  require(reader['verified_bundles']==23 and reader['genuine_original_replays']==2 and all(r['exit_code']==0 for r in reader['processes']),'Retained LDAK reader checks differ')
  require(q['current_reader']==reader['current_jar']==method['current_jar'] and q['closed_reader']==method['closed_reader'] and rawpin(z.read('closed-INDEX.json'))==q['closed_index'],'LDAK reader/index lineage differs')
 census=parent.primary(cache,old);require(census==json.loads((ROOT/parent.COVERAGE).read_bytes()),'Retained LDAK source census differs')
 result=parent_check(old,before,census);require(proof['before']=={n:rawpin(v) for n,v in old.items()} and proof['after']=={n:rawpin(v) for n,v in before.items()},'LDAK before/after bindings differ')
 require(all(proof.get(k)==v for k,v in result.items()),'LDAK historical admission result differs')
 return dict(status='PASS_UNCHANGED_LDAK_LOGIC_ON_ACTUAL_GIT_BOUND_PREDECESSOR',result=result,primary_xml_hashes_checked=250,qualification_payloads_read=count,proof=rawpin(proof_raw),source_overlay_used=False,physical_private_store_rehashed=False,original_runtime_reexecuted=False)


def public_qualification():
 vpath=ROOT/'docs/literature/metal-workflow-development/VALIDATION.json';raw=vpath.read_bytes();require(raw==git('show',BASE+':'+vpath.relative_to(ROOT).as_posix()),'Frozen original METAL qualification changed');v=json.loads(raw)
 require(v['status']=='PASS_ORIGINAL_METAL_TYPED_WORKFLOW_QUALIFICATION' and v['new_family_credit']==0 and v['catalog_modified'] is False and v['whole_goal_complete'] is False,'Original qualification scope differs')
 require(pin(Path(workflow.__file__))==v['audit_source_guard'],'Frozen METAL workflow auditor changed')
 for name,value in v['source_guards'].items():require(pin(ROOT/name)==rawpin(git('show',BASE+':'+name))==value,'Qualified METAL source changed: '+name)
 scripts=workflow.EVIDENCE/'reproduction-scripts.zip';require(pin(scripts)==v['reproduction_scripts_archive'],'METAL reproduction scripts differ')
 with zipfile.ZipFile(scripts) as z:
  names=z.namelist();entries=json.loads(z.read('ENTRY-MANIFEST.json'))['payloads'];require(len(names)==len(set(names)) and set(names)==set(entries)|{'ENTRY-MANIFEST.json'} and len(entries)==v['reproduction_script_payloads'],'METAL reproduction script population differs')
  for name,value in entries.items():require(rawpin(z.read(name))==value,'METAL reproduction script differs: '+name)
 result=workflow.archive(v);return v,dict(**result,reproduction_script_payloads=len(entries))


def qualification(private_root,checks_root,out):
 v,public=public_qualification();deep=workflow.private(v,Path(private_root),out,None,None,False)
 checks_root=Path(checks_root);report_raw=(checks_root/'COMPLETED.json').read_bytes();report=json.loads(report_raw)
 expected=dict(**public);expected.pop('reproduction_script_payloads')
 expected.update(private_payloads_rehashed=813,private_bundles_reverified_now=28,status=v['status'],original_runtime_reexecuted=False,whole_goal_complete=False,new_family_credit=0,public_views_are_standalone_bundles=False)
 require(report==expected,'Require the successful complete 28-bundle frozen-reader check, not a public-only audit')
 index_raw=(Path(private_root)/'INDEX.json').read_bytes();index=json.loads(index_raw);payload={'closed-INDEX.json':index_raw,'current-reader-checks/COMPLETED.json':report_raw,'preserved-METAL-VALIDATION.json':(workflow.EVIDENCE/'VALIDATION.json').read_bytes()}
 for name in index['admitted_bundles']:
  for suffix in ['.stdout','.stderr']:
   p=checks_root/(name+suffix);require(p.is_file(),'Missing retained Java reader console: '+p.name);payload['current-reader-checks/'+p.name]=p.read_bytes()
 for name in v['source_guards']:payload['qualified-project-source/'+name]=(ROOT/name).read_bytes()
 payload['qualified-auditor.py']=Path(workflow.__file__).read_bytes()
 q=dict(status='PASS_METAL_CURRENT_SOURCE_PUBLIC_PAYLOADS_AND_CLOSED_PHYSICAL_BYTES',public_audit=public,private_payloads_rehashed_now=deep['private_payloads_rehashed'],retained_frozen_reader_bundle_checks=28,retained_reader_check=rawpin(report_raw),closed_index=v['private_closed_index'],reader=v['reader'],source_guards=v['source_guards'],audit_source_guard=v['audit_source_guard'],original_qualification=pin(workflow.EVIDENCE/'VALIDATION.json'),original_public_archive=v['archive'],reproduction_scripts_archive=v['reproduction_scripts_archive'],current_scientific_engines_changed=False,original_metal_reexecuted_now=False,whole_tool_qualified=False,whole_paper_reproduced=False,complete_private_store_redistributed=False)
 return q,payload


def packet_write(folder,payload):
 path=folder/'qualification-evidence.zip';entries={n:rawpin(raw) for n,raw in payload.items()}
 with zipfile.ZipFile(path,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,raw in sorted(payload.items()):z.writestr(name,raw)
  z.writestr('ENTRY-MANIFEST.json',encoded(dict(payloads=entries)))
 result=dict(archive=pin(path),indexed_payloads=len(entries));packet_read(path,result);return result
