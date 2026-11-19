"""Paper-role mapping for the qualified ABF scope and explicit native BF variant."""
from collections import Counter
import copy
import hashlib
import json
from coloc_source_roles import DECISIONS

def require(value,message):
    if not value:raise ValueError(message)

def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def build(census):
    require(census['status']=='PASS250_IDENTITIES_27_CURATED_COLOC_ROLES_SOURCE_REVIEW_ONLY'
            and len(census['primary_files'])==250 and census['positive_roles']==27
            and census['direct_package_roles']==24 and census['hosted_roles']==2
            and census['host_unspecified_roles']==1 and not census['canonical_metadata_written'],
            'Different frozen source population')
    roles=copy.deepcopy(census['roles'])
    require(len(roles)==27 and {r['paper_id'] for r in roles}==set(DECISIONS),'Different paper-role population')
    for role in roles:
        indices,method,host,editions,summary,caution=DECISIONS[role['paper_id']]
        require(role['method_family']==method and role['host']==host and role['use_summary']==summary
                and role['required_caution']==caution and [a['paragraph_index'] for a in role['anchors']]==indices
                and [(e['paragraph_index'],e['literal']) for e in role['reported_editions']]==editions,
                'Changed manual source decision: '+role['paper_id'])
        require(role['existing_review_preserved']==(role['paper_id']=='P190')
                and role['direct_package_execution_established']==(host=='direct')
                and role['positive_use'] is True,'Different preserved review or source-use declaration')
        for key in ['author_runtime_executed','exact_author_settings_established','whole_paper_reproduced']:
            require(role[key] is False,'Source roles cannot claim author execution/settings/reproduction')
        abf=method in {'abf','single-and-susie','abf-and-susie-benchmark'}
        bf=method in {'susie','single-and-susie','abf-and-susie-benchmark'}
        role.update(tool_id='coloc',qualified_component_overlap=(['source-compatible-current-ABF-method'] if abf else []),
                    native_alternative_overlap=(['native-stable-supplied-credible-BF-method'] if bf else []),
                    abf_method_branch_explicit=abf,source_entrypoint_unreported=method in {'unspecified','hosted-unspecified','multi-causal-unspecified'},
                    hosted_execution_scope_unvalidated=host!='direct',native_BF_literal_source_parity=False,
                    complete_author_workflow_qualified=False)
        remaining=['Exact author inputs, options, dataset/model preparation and complete workflow are not reproduced.',
                   'Historical editions and original multi-causal fit/LD/credible-set parity are not established.']
        if not abf and not bf:remaining.append('The unreported entry point prevents assigning an exact author invocation to either available component.')
        if bf:remaining.append('The native BF method is an explicitly different stable statistical variant; source-mode null/per-SNP priors and literal source outputs remain separate gaps.')
        if host!='direct':remaining.append('Hosted/unspecified-host execution is not validated by a standalone native coloc workflow.')
        remaining.append(caution);role['remaining_author_roles']=remaining
    counts=dict(Counter(r['method_family'] for r in roles))
    require(counts=={'abf':6,'unspecified':13,'multi-causal-unspecified':1,'single-and-susie':1,
                     'hosted-unspecified':2,'abf-and-susie-benchmark':1,'susie':3},'Different source method classes')
    return dict(schema_version=1,status='PASS_COLOC_27_SOURCE_ROLES_WITH_BOUNDED_COMPONENT_MAPPING',
                primary_source_census_sha256=hashlib.sha256(encoded(census)).hexdigest(),
                primary_files=copy.deepcopy(census['primary_files']),named_body_hits=copy.deepcopy(census['named_body_hits']),
                roles=roles,positive_roles=27,direct_package_roles=24,hosted_roles=2,host_unspecified_roles=1,
                source_method_counts=counts,explicit_ABF_method_branches=8,explicit_SuSiE_method_branches=5,
                whole_paper_reproduced=False,whole_tool_qualified=False,new_family_credit=0,whole_goal_complete=False)
