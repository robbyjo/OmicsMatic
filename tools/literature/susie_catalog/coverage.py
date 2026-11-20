"""Literal SuSiE source roles and qualified supplied-input counterparts.

An edition match is not a recovered author runtime, function attribution or
hosted workflow. Reused results never acquire model-execution credit.
"""
from pathlib import Path
import copy
import hashlib
import json
from susie_source_roles import encoded, require

ROOT=Path(__file__).resolve().parents[3]
CENSUS='docs/literature/susie-workflow-checks/CENSUS.json'
CENSUS_SHA256='36d10daae4895e014a595e9a07c7d789e0ff5d8a660204aeddf03c6089ab0f64'
PROFILES={v:'SuSiE-'+v+'-R4.6.1' for v in ['0.11.92','0.12.35','0.14.2']}
REUSE={'P095','P127','P153'}
INDIVIDUAL={'P108','P181','P234'}
SUMMARY={'P090','P092','P105','P151','P157','P168','P169','P186','P200','P219','P221'}
HOSTED={'P090','P108','P151','P169','P181','P219'}


def build(census):
    raw=(ROOT/CENSUS).read_bytes()
    require(hashlib.sha256(raw).hexdigest()==CENSUS_SHA256,'Changed frozen SuSiE census')
    require(census==json.loads(raw),'Fresh SuSiE census differs from frozen source decisions')
    identities={r['paper_id']:r for r in census['primary_files']}
    require(len(identities)==250 and census['positive_roles']==20 and census['existing_review_records']==0,
            'Different source population')
    roles=[]
    for original in census['roles']:
        r=copy.deepcopy(original);pid=r['paper_id'];editions=[e['literal'] for e in r['reported_editions']]
        require(all(r[k] is False for k in ['standalone_susieR_function_named','author_runtime_executed',
                    'historical_package_parity','exact_author_settings_established','whole_paper_reproduced']),
                'Invented author runtime/function/parity')
        counterparts=[]
        if pid in INDIVIDUAL:
            counterparts.append(dict(model='INDIVIDUAL',function='susie',
                relation='Supplied design/phenotype regression counterpart; no host, phenotype preparation, cross-validation or null-shuffle orchestration.'))
        if pid in SUMMARY:
            counterparts.append(dict(model='RSS_Z',function='susie_rss',
                relation='Supplied aligned z/LD counterpart; the paper does not name this standalone function. Author statistics/LD preparation and hosted calls are unqualified.'))
        missing=[]
        if pid in REUSE:
            missing.append('Reuse of external fine-mapped results: no new SuSiE fit is established; the fit adapter does not retrieve or qualify those resources or downstream selection.')
        elif pid not in INDIVIDUAL|SUMMARY:
            missing.append('Input/function attribution is unspecified; no individual/RSS/sufficient interface is assigned to the author.')
        if pid in HOSTED:missing.append('The named hosting stack is not executed by this standalone adapter.')
        if not editions:missing.append('No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author.')
        if '0.11.43' in editions:missing.append('Exact original 0.11.43 source/runtime is unestablished; explicit profile requests are SKIP3. No adjacent-version substitution.')
        missing.extend([r['required_caution'],
            'Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.'])
        r.update(source_xml_sha256=identities[pid]['xml_sha256'],
            paper_reports_fitting_or_hosted_analysis=(pid not in REUSE),
            supplied_input_fit_counterparts=counterparts,
            counterparts_are_not_author_function_attribution=True,
            reported_package_edition_source_profiles=[PROFILES[v] for v in editions if v in PROFILES],
            unestablished_reported_editions=[v for v in editions if v not in PROFILES],
            author_R_and_dependency_profile_recovered=False,
            supplied_results_resource_or_selection_qualified=False,
            full_reported_role_qualified=False,complete_author_workflow_qualified=False,
            remaining_author_roles=missing,catalog_review_appended=True)
        roles.append(r)
    require({r['paper_id'] for r in roles if r['declared_host']=='result-reuse'}==REUSE,'Different reuse population')
    return dict(schema_version=1,status='PASS_SUSIE_20_LITERAL_SOURCE_ROLES_AND_SCOPED_THREE_EDITION_COUNTERPARTS',
        roles=roles,proper_name_exclusions=copy.deepcopy(census['proper_name_exclusions']),
        nonexecution_contexts=copy.deepcopy(census['nonexecution_contexts']),
        source_roles=20,review_records_to_append=20,existing_review_records_to_preserve=1483,
        observations_to_add=['P073','P203'],unreviewed_observations_to_remove=['P235'],
        qualified_profiles=copy.deepcopy(PROFILES),unsupported_historical_profiles=['SuSiE-0.11.43-R4.6.1'],
        source_function_attribution_invented=False,native_family_credit_added=0,upstream_family_credit_added=1,
        new_family_credit=1,whole_tool_qualified=False,whole_paper_reproduced=False,
        full_author_workflows_reproduced=0)
