"""Fresh all-250 PEER source audit and semantic refusal controls, without catalog changes."""
from pathlib import Path
import argparse,copy,sys,traceback
import peer_primary as p

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--primary-cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();owned=False
    try:
        p.require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');args.out.mkdir(parents=True,exist_ok=False);owned=True
        result=p.audit(args.primary_cache);p.require(result==p.frozen(),'Fresh source review differs from frozen crosswalk');controls=[]
        def refuse(name,edit):
            changed=copy.deepcopy(result);edit(changed)
            try:p.validate(changed)
            except (ValueError,KeyError) as error:controls.append(dict(name=name,status='EXPECTED_REFUSAL',cause=str(error)))
            else:raise AssertionError('Accepted '+name)
        role=lambda value,pid:next(r for r in value['roles'] if r['paper_id']==pid)
        refuse('wrong-primary-identity',lambda v:v['roles'][0].update(doi='wrong'))
        refuse('rewritten-source-usage',lambda v:role(v,'P019').update(usage_summary='Generic PCA'))
        refuse('sensitivity-promoted-to-primary',lambda v:role(v,'P193').update(kind='inference-and-main-QTL'))
        refuse('supplied-GTEx-factors-claimed-as-inference',lambda v:role(v,'P108').update(kind='new-inference'))
        refuse('QTLtools-edition-assigned-to-PEER',lambda v:role(v,'P219').update(reported_edition=dict(literal='1.2',paragraph_index=29,character_offset=0)))
        refuse('wrong-PEER-version-offset',lambda v:role(v,'P212')['reported_edition'].update(character_offset=0))
        refuse('changed-four-versus-six-PC-caution',lambda v:role(v,'P213').update(required_caution='Use six PCs everywhere.'))
        refuse('merged-BMI-residual-definitions',lambda v:role(v,'P219').update(usage_summary='One residual matrix for both searches'))
        refuse('repaired-logistic-wording',lambda v:role(v,'P249').update(required_caution='A linear model is proven by the paper.'))
        refuse('wrong-factor-count',lambda v:role(v,'P201').update(reported_factor_counts=['30']))
        refuse('reviewer-person-promoted-to-tool',lambda v:next(c for c in v['candidates'] if c['paper_id']=='P052').update(positive_software_use=True))
        refuse('wrong-tool-name-offset',lambda v:v['roles'][0]['source_name_witness'].update(character_offset=-1))
        refuse('fake-author-execution',lambda v:v['roles'][0].update(author_runtime_executed=True))
        refuse('invented-paper-executed-commit',lambda v:v['roles'][0].update(paper_executed_commit_confirmed=True))
        refuse('missed-expanded-name-addition',lambda v:v['proposed_additions'].pop())
        refuse('source-review-awards-family-credit',lambda v:v.update(new_family_credit=1))
        record=dict(status='PASS_FRESH250_PEER18_ROLES_AND16_SEMANTIC_REFUSALS',papers=250,positive_papers=18,case_insensitive_candidates=181,generic_or_person_excluded_papers=163,semantic_refusals=16,controls=controls,canonical_metadata_modified=False,new_family_credit=0,new_canonical_reviews=0,whole_goal_complete=False)
        (args.out/'RESULT.json').write_bytes(p.encoded(record));print('PASS all250 primary identities;18 reviewed PEER roles;16 semantic refusals');return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2;record=dict(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED' if code==3 else 'FAIL2_NO_COMPLETE_RESULT',error=repr(error),detail=traceback.format_exc(),complete_result=False,required_action='Supply the exact permitted XML/source cache for a missing prerequisite; preserve invalid-source attempts, correct the named integrity/role/schema cause and retry NEW output. No catalog credit is awarded.')
        if owned:(args.out/'FAILURE.json').write_bytes(p.encoded(record))
        print(record['status']+': '+str(error)+'\n'+record['required_action'],file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
