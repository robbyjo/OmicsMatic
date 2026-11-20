"""Deterministic SuSiE admission with exact preservation of prior source reviews."""
from collections import Counter
import copy
import hashlib
import json
import re
from susie_catalog.coverage import build,encoded,require
from scanpy_catalog.model import META,DATA,NOTICE as PREVIOUS_NOTICE

GUIDE='docs/literature/SUSIE_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/susie-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/SusieWorkflowCli.java'
SCOPE=('Standalone Java file workflow calling original susieR 0.11.92, 0.12.35 and 0.14.2 with exact private '
       'R 4.6.1 profiles. Individual design/phenotype, version-specific RSS and sufficient-statistic interfaces; '
       'raw input/original RDS capture, RUN, R-free structural VERIFY and original-runtime REPLAY. '
       'Invoke org.jlinalg.cli.SusieWorkflowCli from a current source-built JAR. '
       'The native global susie command is separate, with no automatic fallback; global susie-tools is not yet added.')
NOTE=('Twenty source roles distinguish fitting/hosted analysis from three external-result reuse roles. '
      'Two missed observations are added and the unreviewed P235 orangutan-name false positive is removed with exact-before preservation. '
      'No author standalone function is inferred from a method name. Exact 0.11.43 is unavailable in this adapter (SKIP3); '
      'host wrappers, original author inputs/settings/dependencies, allele/locus/LD preparation, sequential selection, '
      'FINEMAP consensus, CLPP/coloc and whole author workflows remain explicit gaps. Linux R scientific deployment '
      'is not qualified. Build current source; the published 0.4.0 binary predates this entry point.')
CURRENT=('250 papers, 446 named families, 2,704 observations, 1,503 individually reviewed positive uses and '
         '1,201 awaiting individual review. There are 138 new scoped integrations (25 native Java and 113 upstream), '
         '259 missing and 37 method-overlap definitions; 141 integrations include three existing adapters. '
         'All 1,483 earlier reviews remain exact. The full implementation and semantic-review goal remains open.')
ACTION=('SKIP3: skip dependent analysis/audit when an exact permitted source, runtime, package or input is missing '
        'or installation is forbidden; name what must be supplied and retain the skip. FAIL2: no complete result; '
        'preserve raw inputs, original model objects, partials, warnings and diagnostics, correct invalid schema/data, '
        'integrity, model arithmetic, convergence, capacity or process causes and retry NEW output. '
        'Never silently repair IDs, alleles, populations, source editions, thresholds, nonfinite values or algorithms. '
        'Inspect WARNINGS.md and analysis-status.json. Structural VERIFY does not independently refit R models '
        'or authenticate the host. Two historical marker-verifier JAR prerequisites remain SKIP3. '
        'Cell Ranger stays excluded. Both literature reports stay withheld from Pages; no release is created.')
NOTICE=('<aside id="susie-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
        'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
        '<h2>SuSiE: three original editions and 20 reviewed source roles</h2><p>'+CURRENT+'</p><p>'+SCOPE+' '+NOTE+
        '</p><p><strong>Warnings and required actions:</strong> '+ACTION+
        '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Source roles, qualified scope and recovery</a>.</p></aside>')


def registry():
    from scanpy_catalog.model import registry as previous
    rows=previous();require(len(rows)==485,'Different effective registry population')
    row=next(t for t in rows if t['id']=='susie');require(row['current_coverage']=='method-overlap','Different registry predecessor')
    row.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE)
    return rows


def validate(before,census,q):
    require(set(before)==set(META),'Exactly eight predecessor documents required')
    require(q['status']=='PASS_SUSIE_ORIGINAL_THREE_EDITION_WORKFLOW_SAVED_QUALIFICATION'
        and q['normal_tests_per_JVM']==73 and q['original_model_cases']==41 and q['converged_accepted']==38
        and q['nonconverged_refused']==3 and q['maximum_model_numeric_absolute_error']==0
        and q['actual_replay_fields_exact_except_call'] is True and q['example_input_interfaces']==5
        and q['global_dispatch_added'] is False and q['linux_R_scientific_runtime_qualified'] is False
        and q['author_runtime_executed'] is False and q['whole_tool_qualified'] is False
        and q['new_family_credit']==0,'Exact scoped original workflow qualification required')
    coverage=build(census)
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);reviewed=json.loads(before[META[5]])
    require(len(papers)==250 and len(inventory)==446 and sum(len(p['tools']) for p in papers)==2703
        and len(reviewed['reviews'])==1483,'Different cumulative predecessor population')
    require(not any(r['tool_id']=='susie' for r in reviewed['reviews']),'Prior SuSiE review cannot be removed or overwritten')
    row=next(t for t in inventory if t['id']=='susie')
    require(row['implementation_status']=='method-overlap' and row['paper_count']==19,'Different/already admitted SuSiE family')
    require({p['paper_id'] for p in papers if any(t['tool_id']=='susie' for t in p['tools'])}==
        ({r['paper_id'] for r in coverage['roles']}-{'P073','P203'})|{'P235'},'Different original SuSiE observation set')
    for role in coverage['roles']:
        p=next(p for p in papers if p['paper_id']==role['paper_id'])
        require(p['pmcid']==role['pmcid'] and p['doi']==role['doi'] and p['xml_sha256']==role['source_xml_sha256'],'Fixed identity differs')
        require(sum(t['tool_id']=='susie' for t in p['tools'])==(0 if role['newly_found_observation'] else 1),'Missing/duplicate observation')
    exclusion=coverage['proper_name_exclusions'][0]
    p=next(p for p in papers if p['paper_id']=='P235');use=next(t for t in p['tools'] if t['tool_id']=='susie')
    require(use==exclusion['existing_observation_object'] and hashlib.sha256(encoded(use)).hexdigest()==exclusion['existing_observation_object_sha256'],
            'Exact unreviewed proper-name observation differs')
    return coverage


def transition(before,census,q):
    coverage=validate(before,census,q)
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]])
    summary=json.loads(before[META[2]]);reviewed=json.loads(before[META[5]])
    for role in coverage['roles']:
        p=next(p for p in papers if p['paper_id']==role['paper_id']);anchor=role['anchors'][0]
        if role['newly_found_observation']:
            use=dict(tool_id='susie',tool='SuSiE',analysis_role='Fine-mapping',matched_text='SuSiE',extraction_score=0)
            p['tools'].append(use)
        else:use=next(t for t in p['tools'] if t['tool_id']=='susie')
        versions=list(dict.fromkeys(e['literal'] for e in role['reported_editions']))
        use.update(evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],
            evidence_paragraph_sha256=anchor['paragraph_sha256'],evidence_locator=p['fulltext_url']+' (section: '+anchor['section']+')',
            review_status='usage-summary-reviewed; paper-wide-completeness-review-open',evidence_selection='individually-reviewed-source-role',
            usage_context=('external-fine-mapped-result-reuse' if role['declared_host']=='result-reuse' else
                'analysis-result-description' if role['role'].endswith('result-description') else 'analysis-method-description'),
            use_summary=role['use_summary'],implementation_scope_note=role['required_caution'],
            version_reported_near_name=versions[0] if len(versions)==1 else None)
        pointers=copy.deepcopy(role['anchors'][1:])
        if pointers:use['supporting_evidence']=pointers
        else:use.pop('supporting_evidence',None)
        record={k:p[k] for k in ['pmcid','doi']}
        record.update(tool_id='susie',evidence_paragraph_sha256=anchor['paragraph_sha256'],
            use_summary=role['use_summary'],implementation_scope_note=role['required_caution'])
        if len(versions)==1:record['version_reported_near_name']=versions[0]
        if pointers:record['supporting_evidence']=pointers
        reviewed['reviews'].append(record)
        use['source_role_qualification']={k:copy.deepcopy(role[k]) for k in [
            'role','declared_host','reported_editions','standalone_susieR_function_named','use_summary','required_caution',
            'supplied_input_fit_counterparts','counterparts_are_not_author_function_attribution',
            'reported_package_edition_source_profiles','unestablished_reported_editions','author_R_and_dependency_profile_recovered',
            'supplied_results_resource_or_selection_qualified','remaining_author_roles','full_reported_role_qualified',
            'complete_author_workflow_qualified','historical_package_parity','exact_author_settings_established',
            'author_runtime_executed','whole_paper_reproduced']}
    p=next(p for p in papers if p['paper_id']=='P235');p['tools']=[t for t in p['tools'] if t['tool_id']!='susie']
    row=next(t for t in inventory if t['id']=='susie')
    row.update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',
        previous_method_overlap_source=row['existing_source'],existing_source=PROGRAM,implementation_source=PROGRAM,
        coverage_note=SCOPE+' '+NOTE,implementation_scope=SCOPE+' '+NOTE,
        validation_note='41 independent original model-object comparisons with numeric difference zero; 38 converged accepted, three nonconverged refused. 73 capture/contract checks per JVM on Windows21/25/Linux21; actual Windows original-runtime REPLAY and five input-interface examples. No Linux R fitting qualification.',
        paper_count=20,papers=[p['paper_id'] for p in papers if any(t['tool_id']=='susie' for t in p['tools'])],
        source_roles_reviewed=20,source_role_coverage=COVERAGE.rsplit('/',1)[-1],current_documentation=GUIDE,
        workflow_qualification='../susie-workflow-checks/QUALIFICATION.json',source_build_required=True,
        released_binary_contains_workflow_adapter=False,public_CLI_available=True,global_CLI_dispatch_available=False,
        qualified_component_entrypoints=['org.jlinalg.cli.SusieWorkflowCli --request RUN/VERIFY/REPLAY',
            'org.jlinalg.cli.SusieWorkflowCli --verify'],
        paper_role_qualification={p['paper_id']:copy.deepcopy(next(t for t in p['tools'] if t['tool_id']=='susie')['source_role_qualification'])
            for p in papers if any(t['tool_id']=='susie' for t in p['tools'])},
        native_family_credit_added=0,upstream_family_credit_added=1,new_scoped_integration_credit=1,
        statistical_computation='upstream-original-versioned-susieR',whole_tool_qualified=False,whole_paper_reproduced=False)
    counts=dict(Counter(t['implementation_status'] for t in inventory))
    require(counts['native-subset-validated']==25 and counts['upstream-adapter-validated']==113
        and counts['method-overlap']==37 and counts['missing']==259,'False integration credit')
    summary['implementation_status_counts']=counts;summary['paper_tool_observations']=2704
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[5],reviewed)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before[META[6]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Embedded data differs')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c')
    page=page[:match.start(2)]+payload+page[match.end(2):]
    for name,text in [(META[6],page),(META[7],before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE)==1 and 'id="susie-catalog-notice"' not in text,'Different/already admitted notice')
        text=text.replace(PREVIOUS_NOTICE,'<details><summary>Previous Scanpy catalog checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1)
        body=re.search(r'<body[^>]*>',text,re.I);require(body is not None,'HTML body missing')
        after[name]=(text[:body.end()]+NOTICE+text[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
        after[name]=('# '+title+'\n\n## SuSiE: three original editions and 20 reviewed source roles\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n'+ACTION+
            '\n\nRead [the source crosswalk and exact scope](../SUSIE_CATALOG_INTEGRATION.md).\n\n<details><summary>Catalog before SuSiE admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after


def check(before,after,census,q):
    require(after==transition(before,census,q),'Different deterministic source-bound transition')
    a,b=json.loads(before[META[0]]),json.loads(after[META[0]])
    for old,new in zip(a,b,strict=True):
        require({k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'},'Fixed paper identity changed')
        require([t for t in old['tools'] if t['tool_id']!='susie']==[t for t in new['tools'] if t['tool_id']!='susie'],'Unrelated observation/order changed')
        old_ids=[t['tool_id'] for t in old['tools']];new_ids=[t['tool_id'] for t in new['tools']]
        expected=old_ids+['susie'] if old['paper_id'] in ['P073','P203'] else [t for t in old_ids if t!='susie'] if old['paper_id']=='P235' else old_ids
        require(new_ids==expected,'Unreviewed observation addition/removal/reordering')
    old,new=json.loads(before[META[1]]),json.loads(after[META[1]])
    require([t for t in old if t['id']!='susie']==[t for t in new if t['id']!='susie'],'Unrelated inventory changed')
    ar,br=json.loads(before[META[5]]),json.loads(after[META[5]])
    require(br['reviews'][:1483]==ar['reviews'] and len(br['reviews'])==1503 and all(r['tool_id']=='susie' for r in br['reviews'][1483:]),'Prior reviews or append credit differ')
    require({k:v for k,v in ar.items() if k!='reviews'}=={k:v for k,v in br.items() if k!='reviews'},'Other review metadata changed')
    require(len({(r['pmcid'],r['tool_id']) for r in br['reviews']})==1503,'Duplicate review identity')
    counts=Counter(t['tool_id'] for p in b for t in p['tools']);require(len(counts)==446 and sum(counts.values())==2704,'Observation population differs')
    for row in new:require(row['paper_count']==counts[row['id']] and row['papers']==[p['paper_id'] for p in b if any(t['tool_id']==row['id'] for t in p['tools'])],'Inventory membership differs')
    return dict(papers=250,named_tools=446,registry_definitions=485,observations=2704,reviewed_uses=1503,
        unreviewed_uses=1201,existing_reviews_retained=1483,new_review_records=20,positive_susie_roles=20,
        observations_added=2,observations_removed=1,new_scoped_integrations=138,native_integrations=25,
        upstream_integrations=113,including_existing_adapters=141,missing=259,method_overlap=37,
        new_scoped_integration_credit=1,native_family_credit_added=0,upstream_family_credit_added=1,
        whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
