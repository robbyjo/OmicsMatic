"""Fresh source identities/roles and sixteen semantic refusals; no admission."""
from pathlib import Path
import argparse,copy,sys,traceback
import twosamplemr_primary as p
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--primary-cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();owned=False
    try:
        p.require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');args.out.mkdir(parents=True,exist_ok=False);owned=True
        result=p.audit(args.primary_cache);p.require(result==p.frozen(),'Fresh source witnesses differ');controls=[]
        def refuse(name,edit):
            changed=copy.deepcopy(result);edit(changed)
            try:p.validate(changed)
            except (ValueError,KeyError):controls.append(name)
            else:raise AssertionError('Accepted '+name)
        role=lambda v,pid:next(r for r in v['roles'] if r['paper_id']==pid)
        refuse('wrong-primary-identity',lambda v:v['roles'][0].update(doi='wrong'))
        refuse('rewritten-provider-use',lambda v:role(v,'P088').update(usage_summary='Generic inference'))
        refuse('current-edition-invented-for-unreported-paper',lambda v:role(v,'P105').update(reported_edition={'literal':'0.7.14'}))
        refuse('citation31-merged-into-edition',lambda v:role(v,'P211')['reported_edition'].update(literal='0.5.731'))
        refuse('changed-edition-offset',lambda v:role(v,'P211')['reported_edition'].update(character_offset=0))
        refuse('wrong-name-offset',lambda v:v['roles'][0]['source_name_witness'].update(character_offset=-1))
        refuse('wrong-primary-paragraph-hash',lambda v:v['roles'][0]['primary_evidence'][0].update(paragraph_sha256='0'*64))
        refuse('MVMR-provider-credited-to-TwoSampleMR',lambda v:role(v,'P224').update(kind='robust-MV-IVW-and-conditional-F'))
        refuse('TEMR-credited-to-TwoSampleMR',lambda v:role(v,'P177').update(usage_summary='TwoSampleMR implements TEMR'))
        refuse('MAF-direction-silently-repaired',lambda v:role(v,'P150').update(required_caution='The direction is unambiguous.'))
        refuse('Steiger-threshold-repaired',lambda v:role(v,'P211').update(required_caution='Use the generic direction rule.'))
        refuse('false-author-runtime',lambda v:v['roles'][0].update(author_runtime_executed=True))
        refuse('false-paper-edition-numerical-qualification',lambda v:v['roles'][0].update(paper_edition_numerically_qualified=True))
        refuse('missed-code-availability-addition',lambda v:role(v,'P157').update(proposed_observation=False))
        refuse('source-review-awards-credit',lambda v:v.update(new_family_credit=1))
        refuse('source-review-awards-canonical-reviews',lambda v:v.update(new_canonical_reviews=14))
        (args.out/'RESULT.json').write_bytes(p.encoded(dict(status='PASS_FRESH250_TWOSAMPLEMR14_ROLES_AND16_SOURCE_REFUSALS',papers=250,positive_papers=14,proposed_additions=['P157','P177'],semantic_refusals=16,controls=controls,new_family_credit=0,new_canonical_reviews=0,whole_goal_complete=False)));print('PASS all250 identities;14 roles;16 refusals');return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        record=dict(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED' if code==3 else 'FAIL2_NO_COMPLETE_RESULT',error=repr(error),detail=traceback.format_exc(),complete_result=False,required_action='Supply the exact permitted cache for a missing prerequisite. Preserve failed attempts, correct the named source/role/integrity cause and retry NEW output. Source review awards no implementation or review-ledger credit.')
        if owned:(args.out/'FAILURE.json').write_bytes(p.encoded(record))
        print(record['status']+': '+str(error)+'\n'+record['required_action'],file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
