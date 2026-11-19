"""Curated Enrichr paper roles mapped to explicitly bounded current-service scopes."""
from collections import Counter
import copy
import hashlib
import json
from enrichr_source_roles import DECISIONS


def require(value, message):
    if not value:
        raise ValueError(message)


def encoded(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode()


def build(census):
    require(census['status'] == 'PASS250_IDENTITIES_25_ENRICHR_ROLES_SOURCE_CENSUS_ONLY'
            and len(census['primary_files']) == 250 and census['positive_roles'] == 25
            and census['enrichment_analysis_roles'] == 23 and census['library_resource_roles'] == 1
            and census['unresolved_function_host_roles'] == 1
            and not census['canonical_metadata_written'], 'Different source census')
    roles = copy.deepcopy(census['roles'])
    require(len(roles) == 25 and {r['paper_id'] for r in roles} == set(DECISIONS), 'Different role population')
    for r in roles:
        indices, role, host, editions, libraries, summary, caution = DECISIONS[r['paper_id']]
        require(r['role'] == role and r['declared_host'] == host and r['reported_library_labels'] == libraries
                and r['use_summary'] == summary and r['required_caution'] == caution
                and [a['paragraph_index'] for a in r['anchors']] == indices
                and [(e['paragraph_index'], e['literal']) for e in r['reported_editions']] == editions,
                'Changed manual source decision: ' + r['paper_id'])
        require(r['existing_review_preserved'] == (r['paper_id'] in ['P061', 'P163'])
                and r['newly_found_observation'] == (r['paper_id'] in ['P048', 'P051', 'P134'])
                and r['positive_tool_related_use'] is True, 'Wrong review/observation preservation')
        for key in ['enrichment_service_execution_verified', 'author_runtime_executed',
                    'exact_author_settings_established', 'whole_paper_reproduced']:
            require(r[key] is False, 'Source roles do not establish author execution or full settings')
        scopes = ([] if r['paper_id'] == 'P182' else ['current-human-mouse-library-download']
                  if role == 'library-resource' else ['current-human-mouse-remote-enrichment'])
        if r['paper_id'] == 'P121':
            scopes.append('current-human-mouse-library-download')
        remaining = ['Exact historical service, library bytes, submitted genes, preparation and complete author workflow are not reproduced.',
                     'Current service replies and query echoes do not independently establish its final scoring population.', caution]
        if host in ['GSEApy', 'enrichR-R', 'Enrichr.jl']:
            remaining.append('The declared client/runtime is not executed by this current-service Java workflow.')
        if r['paper_id'] == 'P055':
            remaining.append('Resource retrieval does not implement the multi-library target filtering/intersections or FGSEA stage.')
        if r['paper_id'] == 'P121':
            remaining.append('Remote service statistics do not implement the separate Julia Fisher/odds-ratio/BH recalculation.')
        if r['paper_id'] == 'P182':
            remaining.append('No Enrichr execution credit is assigned to an unresolved ClusterProfiler function attribution.')
        identity = next(p for p in census['primary_files'] if p['paper_id'] == r['paper_id'])
        require(identity['pmcid'] == r['pmcid'] and identity['doi'] == r['doi'], 'Role identity differs')
        r.update(tool_id='enrichr', source_xml_sha256=identity['xml_sha256'], qualified_component_overlap=scopes,
                 historical_client_parity=False, historical_library_parity=False,
                 complete_author_workflow_qualified=False, statistical_computation='upstream-service',
                 remaining_author_roles=remaining)
    return dict(schema_version=1, status='PASS_ENRICHR_25_SOURCE_ROLES_WITH_CURRENT_SERVICE_SCOPE_MAPPING',
                primary_source_census_sha256=hashlib.sha256(encoded(census)).hexdigest(),
                primary_files=copy.deepcopy(census['primary_files']),
                named_body_hits=copy.deepcopy(census['named_body_hits']), roles=roles,
                positive_roles=25, enrichment_analysis_roles=23, library_resource_roles=1,
                unresolved_function_host_roles=1, source_role_counts=dict(Counter(r['role'] for r in roles)),
                existing_reviews_retained=2, potential_new_review_records=23,
                observations_added=['P048', 'P051', 'P134'], whole_tool_qualified=False,
                whole_paper_reproduced=False, new_family_credit=0, whole_goal_complete=False)
