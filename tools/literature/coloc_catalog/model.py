"""Deterministic bounded COLOC admission; mutation/evidence checks live in the driver."""
from collections import Counter
import copy
import hashlib
import json
import re
from coloc_catalog.coverage import build,encoded,require
from plink_catalog.admission_model import META,DATA,NOTICE as PREVIOUS_NOTICE

GUIDE='docs/literature/COLOC_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/coloc-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/ColocAbfWorkflowCli.java'
SCOPE=('Native Java single-causal coloc 6.0.3 ABF from supplied regression/P-value regional statistics, '
       'with exact input capture, warnings, RUN/VERIFY/REPLAY and complete native rederivation. '
       'A separately qualified native stable credible-BF method is available with explicit source differences.')
NOTE=('The 27 reviewed roles distinguish six explicit ABF roles, two mixed ABF/SuSiE roles, three SuSiE roles, '
      '13 unspecified roles, one unspecified multi-causal role and two FUSION-hosted roles. '
      'The native BF method is not literal whole-package source parity: H3 arithmetic, weighted conditional '
      'SNP probabilities, extreme-weight handling and boundary/refusal behavior differ. '
      'Hosted execution, original multi-causal fits, null/per-SNP-prior source mode, historical editions '
      'and complete author workflows remain explicit gaps. Current source-build artifacts are required; '
      'the published 0.4.0 release predates these workflows.')
CURRENT=('250 papers, 446 named families, 2,700 observations, 1,439 individually reviewed positive uses '
         'and 1,261 awaiting individual review. There are 135 new scoped integrations (25 native Java and '
         '110 upstream), 259 missing and 40 method-overlap families; 138 integrations include three existing '
         'adapters. Twenty-six COLOC reviews are appended and all 1,413 earlier reviews, including P190, '
         'are retained. The full implementation and semantic-review goal remains open.')
ACTION=('SKIP3: skip the dependent analysis/audit when an exact permitted source, runtime or validation '
        'dependency is unavailable or forbidden; name what must be restored or leave the skip visible. '
        'FAIL2: admit no complete result; retain raw inputs, partials and errors, correct the cause and '
        'retry NEW outputs. Do not repair IDs, alleles, source inequalities, nonfinite values, statistics '
        'or algorithms. Check native BF warnings and defined-posterior flags; undefined zero markers '
        'are not probabilities. Earlier PLINK fixture failures and historical marker-verifier SKIP3 '
        'gaps remain retained. Both literature reports stay withheld from Pages; no release is created.')
NOTICE=('<aside id="coloc-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
        'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
        '<h2>COLOC: scoped native ABF and 27 reviewed source roles</h2><p>'+CURRENT+'</p><p>'+SCOPE+' '+NOTE+
        '</p><p><strong>Warnings and required actions:</strong> '+ACTION+
        '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">'
        'Individual source roles, qualified scope and recovery</a>.</p></aside>')

def registry():
    from plink_catalog.admission_model import registry as previous
    rows=previous();require(len(rows)==485,'Different effective registry population')
    row=next(t for t in rows if t['id']=='coloc');require(row['current_coverage']=='method-overlap','Different registry predecessor')
    row.update(current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE)
    return rows

def validate(before,census,qualification):
    require(set(before)==set(META),'Exactly eight predecessor documents required')
    require(qualification['status']=='PASS_COLOC_CURRENT_ABF_AND_EXPLICIT_BF_VARIANT_QUALIFICATIONS'
            and qualification['ABF_workflow_tests_per_JVM']==62 and qualification['native_BF_tests_per_JVM']==50
            and qualification['native_BF_literal_source_parity'] is False
            and qualification['new_family_credit']==0,'Qualified current scopes are required')
    coverage=build(census);papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]])
    reviewed=json.loads(before[META[5]])
    require(len(papers)==250 and len(inventory)==446 and sum(len(p['tools']) for p in papers)==2700
            and len(reviewed['reviews'])==1413,'Different cumulative predecessor population')
    existing=[r for r in reviewed['reviews'] if r['tool_id']=='coloc']
    require(len(existing)==1 and existing[0]['pmcid']=='PMC12626415','Exactly the existing P190 review must be retained')
    row=next(t for t in inventory if t['id']=='coloc')
    require(row['implementation_status']=='method-overlap' and row['paper_count']==27,'Family already admitted or different predecessor')
    require(row['papers']==[r['paper_id'] for r in coverage['roles']],'Different source-role order')
    for role in coverage['roles']:
        paper=next(p for p in papers if p['paper_id']==role['paper_id'])
        require(paper['pmcid']==role['pmcid'] and paper['doi']==role['doi'] and paper['xml_sha256']==role['source_xml_sha256'],'Source identity differs')
        require(sum(t['tool_id']=='coloc' for t in paper['tools'])==1,'Missing/duplicate existing observation')
        if role['existing_review_preserved']:
            require(role['paper_id']=='P190' and role['previous_review_object_sha256']==hashlib.sha256(encoded(existing[0])).hexdigest(),'Existing review object differs')
    return coverage

def transition(before,census,qualification):
    coverage=validate(before,census,qualification)
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviewed=json.loads(before[META[5]])
    for role in coverage['roles']:
        paper=next(p for p in papers if p['paper_id']==role['paper_id']);use=next(t for t in paper['tools'] if t['tool_id']=='coloc');anchor=role['anchors'][0]
        if not role['existing_review_preserved']:
            versions=list(dict.fromkeys(e['literal'] for e in role['reported_editions']))
            use.update(evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],
                evidence_paragraph_sha256=anchor['paragraph_sha256'],evidence_locator=paper['fulltext_url']+' (section: '+anchor['section']+')',
                review_status='usage-summary-reviewed; paper-wide-completeness-review-open',evidence_selection='individually-reviewed-source-role',
                usage_context='analysis-method-description',
                use_summary=role['use_summary'],implementation_scope_note=role['required_caution'],
                version_reported_near_name=versions[0] if len(versions)==1 else None)
            pointers=copy.deepcopy(role['anchors'][1:])
            if pointers:use['supporting_evidence']=pointers
            else:use.pop('supporting_evidence',None)
            record={k:paper[k] for k in ['pmcid','doi']}
            record.update(tool_id='coloc',evidence_paragraph_sha256=anchor['paragraph_sha256'],use_summary=role['use_summary'],implementation_scope_note=role['required_caution'])
            if len(versions)==1:record['version_reported_near_name']=versions[0]
            if pointers:record['supporting_evidence']=pointers
            reviewed['reviews'].append(record)
        use['source_role_qualification']={k:copy.deepcopy(role[k]) for k in [
            'method_family','host','reported_editions','use_summary','required_caution','qualified_component_overlap',
            'native_alternative_overlap','remaining_author_roles','direct_package_execution_established','comparative_benchmark',
            'native_BF_literal_source_parity','complete_author_workflow_qualified','exact_author_settings_established','author_runtime_executed','whole_paper_reproduced']}
    row=next(t for t in inventory if t['id']=='coloc')
    row.update(current_coverage='native-subset-validated',implementation_status='native-subset-validated',
        existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE+' '+NOTE,implementation_scope=SCOPE+' '+NOTE,
        validation_note='Qualified ABF API/workflow and separately qualified stable BF variant. Thirty original ABF cases, 62 workflow tests per JVM; 28 BF source profiles explicitly classified and 50 source/boundary/regression tests per JVM. Twelve BF profiles match completely; other outcomes are not relabeled as source parity.',
        source_roles_reviewed=27,direct_package_roles=24,hosted_roles=2,host_unspecified_roles=1,
        source_role_coverage=COVERAGE.rsplit('/',1)[-1],current_documentation=GUIDE,
        component_qualification='../coloc-abf-handover/SNAPSHOT.json',workflow_qualification='../coloc-abf-workflow-checks/QUALIFICATION.json',
        native_BF_qualification='../coloc-susie-audit-checks/QUALIFICATION.json',
        selected_project_editions=['6.0.3'],source_build_required=True,released_binary_contains_workflow_adapter=False,
        public_CLI_available=True,qualified_component_entrypoints=['coloc-abf --request RUN/VERIFY/REPLAY','coloc (native stable supplied-BF variant)'],
        paper_role_qualification={r['paper_id']:copy.deepcopy(next(t for p in papers if p['paper_id']==r['paper_id'] for t in p['tools'] if t['tool_id']=='coloc')['source_role_qualification']) for r in coverage['roles']},
        native_family_credit_added=1,upstream_family_credit_added=0,new_scoped_integration_credit=1,
        whole_tool_qualified=False,whole_paper_reproduced=False,native_BF_literal_source_parity=False)
    counts=dict(Counter(t['implementation_status'] for t in inventory))
    require(counts['native-subset-validated']==25 and counts['upstream-adapter-validated']==110
            and counts['method-overlap']==40 and counts['missing']==259,'False integration credit')
    summary['implementation_status_counts']=counts
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[5],reviewed)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before[META[6]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Embedded predecessor data differs')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c')
    page=page[:match.start(2)]+payload+page[match.end(2):]
    for name,text in [(META[6],page),(META[7],before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE)==1 and 'id="coloc-catalog-notice"' not in text,'Different old notice or already admitted')
        text=text.replace(PREVIOUS_NOTICE,'<details><summary>Previous PLINK-family catalog checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1)
        body=re.search(r'<body[^>]*>',text,re.I);require(body is not None,'HTML body missing')
        after[name]=(text[:body.end()]+NOTICE+text[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
        after[name]=('# '+title+'\n\n## COLOC: scoped native workflows and 27 source roles\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n'+ACTION+
                     '\n\nRead [the source crosswalk and exact scope](../COLOC_CATALOG_INTEGRATION.md).\n\n<details><summary>Catalog before COLOC admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after

def check(before,after,census,qualification):
    require(after==transition(before,census,qualification),'Different deterministic source-bound transition')
    a,b=json.loads(before[META[0]]),json.loads(after[META[0]])
    for old,new in zip(a,b,strict=True):
        require({k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'},'Fixed paper identity changed')
        require([t for t in old['tools'] if t['tool_id']!='coloc']==[t for t in new['tools'] if t['tool_id']!='coloc'],'Unrelated observation/order changed')
        if old['paper_id']=='P190':require(next(t for t in old['tools'] if t['tool_id']=='coloc')=={k:v for k,v in next(t for t in new['tools'] if t['tool_id']=='coloc').items() if k!='source_role_qualification'},'Existing P190 use changed')
    old,new=json.loads(before[META[1]]),json.loads(after[META[1]])
    require([t for t in old if t['id']!='coloc']==[t for t in new if t['id']!='coloc'],'Unrelated inventory changed')
    ar,br=json.loads(before[META[5]]),json.loads(after[META[5]])
    require(br['reviews'][:1413]==ar['reviews'] and len(br['reviews'])==1439
            and all(r['tool_id']=='coloc' for r in br['reviews'][1413:]),'Old reviews or append credit differ')
    require({k:v for k,v in ar.items() if k!='reviews'}=={k:v for k,v in br.items() if k!='reviews'},'Unrelated review metadata changed')
    require(len({(r['pmcid'],r['tool_id']) for r in br['reviews']})==1439,'Duplicate review identity')
    counts=Counter(t['tool_id'] for p in b for t in p['tools']);require(len(counts)==446 and sum(counts.values())==2700,'Observation population changed')
    for row in new:require(row['paper_count']==counts[row['id']] and row['papers']==[p['paper_id'] for p in b if any(t['tool_id']==row['id'] for t in p['tools'])],'Inventory membership differs')
    return dict(papers=250,named_tools=446,registry_definitions=485,observations=2700,reviewed_uses=1439,unreviewed_uses=1261,
                existing_reviews_retained=1413,new_review_records=26,positive_coloc_roles=27,direct_package_roles=24,hosted_roles=2,host_unspecified_roles=1,
                observations_added=0,observations_removed=0,new_scoped_integrations=135,native_integrations=25,upstream_integrations=110,
                including_existing_adapters=138,missing=259,method_overlap=40,new_scoped_integration_credit=1,native_family_credit_added=1,upstream_family_credit_added=0,
                whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
