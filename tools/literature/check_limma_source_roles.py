"""Fresh primary/code-cache audit and semantic refusal controls; no canonical mutation."""
from pathlib import Path
import argparse,copy,json
import limma_primary as p


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--primary-cache',type=Path,required=True);parser.add_argument('--author-code-cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False)
    result=p.audit(args.primary_cache,args.author_code_cache);frozen=json.loads((p.ROOT/p.FOLDER/'SOURCE-CENSUS.json').read_bytes());p.require(result==frozen,'Fresh primary/code audit differs from frozen reviewed crosswalk')
    controls=[]
    def refuse(name,edit):
        changed=copy.deepcopy(result);edit(changed)
        try:p.validate(changed)
        except (ValueError,KeyError) as error:controls.append(dict(name=name,status='EXPECTED_REFUSAL',cause=str(error)))
        else:raise AssertionError('Admitted '+name)
    refuse('wrong-primary-identity',lambda v:v['roles'][0].update(doi='wrong'))
    refuse('wrong-method-description',lambda v:v['roles'][0].update(usage_summary='Generic GSEA'))
    refuse('dropped-second-CpG-edition',lambda v:next(r for r in v['roles'] if r['paper_id']=='P086')['reported_editions'].pop())
    refuse('invented-patch-edition',lambda v:next(r for r in v['roles'] if r['paper_id']=='P195')['reported_editions'][0].update(literal='3.44.0'))
    refuse('repaired-author-attribution',lambda v:next(r for r in v['roles'] if r['paper_id']=='P228').update(provider_attribution_unresolved=False))
    refuse('voom-credited-with-batch-removal',lambda v:next(r for r in v['roles'] if r['paper_id']=='P182' and r['tool_id']=='voom').update(qualified_related_current_operations=['BATCH']))
    refuse('claimed-author-execution',lambda v:v['roles'][0].update(author_runtime_executed=True))
    refuse('claimed-complete-preparation',lambda v:v['roles'][0].update(preparation_and_author_settings_complete=True))
    refuse('claimed-whole-paper',lambda v:v['roles'][0].update(whole_paper_reproduced=True))
    refuse('lost-exclusion',lambda v:v['exclusions'].pop())
    refuse('alias-double-integration-credit',lambda v:v.update(alias_additional_integration_credit=1))
    refuse('wrong-source-name-offset',lambda v:v['roles'][0]['source_name_witness'].update(character_offset=0))
    refuse('different-author-code-commit',lambda v:next(r for r in v['roles'] if r['paper_id']=='P183')['linked_code_source'].update(commit='0'*40))
    refuse('dropped-alias-role',lambda v:v['roles'].pop())
    refuse('wrong-catalog-family',lambda v:v['roles'][0].update(catalog_family_id='gcta'))
    record=dict(status='PASS_FRESH_250_PRIMARY23_LABEL_ROLES_AND15_SEMANTIC_REFUSALS',primary_papers=250,positive_labels=23,positive_papers=18,candidates=20,code_sources=2,tests=15,controls=controls,canonical_metadata_modified=False,new_catalog_credit=0,whole_goal_complete=False)
    (args.out/'RESULT.json').write_bytes(p.encoded(record));print('PASS all250 sources,23 roles,15 semantic refusals; no canonical mutation')


if __name__=='__main__':main()
