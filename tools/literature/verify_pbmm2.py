"""Verify scoped original pbmm2 qualification records; optional retained private evidence checks."""
import argparse,json
from pathlib import Path
from nfcore_review_source_audit import read,pin,require
ROOT=Path(__file__).resolve().parents[2];FOLDER='docs/literature/pbmm2-validation';Q=FOLDER+'/QUALIFICATION.json'
STATUS='PASS_ORIGINAL_PBMM2_PREPARED_INPUT_OPERATIONS_AND_NATIVE_CIGAR_CONTRACTS'

def verify(root,private=None):
 q=read(root/Q);completion=read(root/FOLDER/'COMPLETED.json')
 require(q['status']==STATUS and q['new_native_family_credit']==0 and q['new_upstream_family_credit']==1 and not q['whole_goal_complete']and not q['whole_paper_reproduced']and not q['complete_binary_source_equivalence_certified'],'Qualification scope differs')
 require(completion['qualification']==pin(root/Q)and completion['verifier']==pin(Path(__file__)),'Qualification/verifier binding differs')
 for name,expected in q['public_files'].items():require(pin(root/name)==expected,'Qualified implementation/profile/source-role bytes differ: '+name)
 route=(root/'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_text(encoding='utf-8');require('arguments[0].equals("pbmm2-tools")'in route and 'return Pbmm2ToolsCli.run('in route,'Packaged CLI route missing')
 profile=read(root/'src/main/resources/org/jlinalg/omics/pbmm2/PROFILES.json');require(set(profile['versions'])=={'1.13.1','1.17.0'},'Edition census differs')
 for version,failures in [('1.13.1',0),('1.17.0',3)]:
  row=profile['versions'][version];tests=row['original_public_cram_controls'];require(tests['tests']==109 and tests['passed']==109-failures and tests['failed']==failures and tests['skipped']==0 and not row['source_code_complete_for_this_binary'],'Original source/binary discrepancy was hidden')
 require(q['native_cigar']['files']==500 and q['native_cigar']['target_provider_files']==472 and q['native_cigar']['mapped_records']==28608 and q['native_cigar']['compared_source_float_tags']==28992,'Complete native BAM population differs')
 require(q['namespace_parity']['files']==500 and q['namespace_parity']['all_records_including_nonprovider_inputs']==29808 and q['namespace_parity']['record_order_differences']==0,'Full namespace parity population differs')
 roles=read(root/'docs/literature/pbmm2-source-audit/ROLES.json');require([r['paper_id']for r in roles['roles']]==['P171','P214','P232','P248']and roles['new_source_reviews']==4 and roles['recovered_observations']==0,'Primary role population differs')
 census=read(root/'docs/literature/pbmm2-source-audit/CENSUS.json');require(len(census['sources'])==250 and len(census['contexts'])==5,'Frozen primary-source census differs')
 require(q['packaged_tests']==dict(tests=14,failures=0,errors=0,skipped=0),'Packaged tests are incomplete')
 if private is not None:
  private=private.resolve()
  for name,expected in q['private_receipts'].items():require(pin(private/name)==expected,'Retained private evidence differs: '+name)
  for build in [1,3,4]:
   record=read(private/f'project-build-Windows-{build}/COMPLETED.json');require(pin(private/f'project-build-Windows-{build}/project-build/cli/omicsmatic-0.4.1.jar')==record['producer'],'Actual producing JAR differs')
  require(read(private/'project-build-Windows-4/COMPLETED.json')['producer']==q['producer'],'Final actual producer differs')
  for version,expected in [('1.13.1',[]),('1.17.0',['fastxinput_22','fastxinput_23','fastxinput_24'])]:
   for label in ['upstream-functional-'+version+'-3','namespaced-functional-'+version+'-2']:
    terminal=read(private/label/'TERMINAL.json');require(terminal['tests']==109 and terminal['skipped']==0 and [r['case']for r in terminal['cases']if r['failures']]==expected,'Original cases/failures were omitted or repaired')
  for name,code in q['typed_controls'].items():
   terminal=read(private/name/'TERMINAL.json');require(terminal['exit_code']==code and terminal['completed']==(code==0)and terminal['failure']==(code!=0),'Typed original operation/refusal differs: '+name)
  for host in ['Windows','Linux']:
   proof=read(private/f'portable-verify-{host}-1/COMPLETED.json');require(proof['producer']==q['producer']and proof['mapped_records']==9,'Portable native verification differs')
  corruption=read(private/'cigar-corruption-proof-1/COMPLETED.json');require(corruption['exit_code']==2 and corruption['negative_bundle_is_invalid_despite_refrozen_completion_marker'],'Refrozen semantic corruption refusal missing')
  objects=private/'complete-control-bams-1/objects';seen=set()
  for row in read(private/'complete-control-bams-1/FILES.json'):
   if row['object']not in seen:require(pin(objects/row['object'])==row['pin'],'Complete original captured BAM changed');seen.add(row['object'])
  require(len(seen)==476,'Original full BAM object population differs')
 return dict(status=STATUS,mode='retained-private-evidence-and-public-records'if private is not None else'public-qualification-records-only',qualified_upstream_families=1,new_source_reviews_eligible=4,whole_paper_reproduced=False,whole_goal_complete=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private),indent=2))
 except Exception as e:raise SystemExit('FAIL2: no complete pbmm2 qualification; retain evidence and correct the named cause: '+str(e))
