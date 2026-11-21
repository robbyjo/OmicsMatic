"""Admit one limma family, its voom alias and 23 source-bound reviews."""
from collections import Counter
import copy,html,json,re
from limma_catalog import primary as p
from gcta_catalog.model import META,DATA,NOTICE as PREVIOUS_NOTICE

GUIDE='docs/literature/LIMMA_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/limma-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/LimmaWorkflowCli.java'
SCOPE=('Global limma-tools and standalone LimmaWorkflowCli expose prepared-data LM, VOOM, '
 'EDGER_VOOM_LMFIT, BATCH, NORMALIZE and CAMERAPR through the exact original '
 'limma 3.68.5 / edgeR 4.10.7 / statmod 1.5.2 / R 4.6.1 profile. RUN retains literal '
 'inputs, model/null/partial objects, warnings and original failures; VERIFY checks saved '
 'integrity and declared structural/selected arithmetic invariants without refitting or '
 'authenticating; REPLAY preserves parent lineage. Native Java variance components cover '
 'qualified fitFDist, legacy nonrobust squeezeVar, supplied-prior posterior, inverse-trigamma '
 'and logmdigamma operations. They add no second family credit.')
NOTE=('Limma is one credited upstream family. Voom is a validated-family-alias of limma '
 'with zero additional integration credit; COJO remains the separate GCTA alias. The 23 '
 'reviewed labels across 18 papers describe source roles, not whole author workflows. '
 'Older editions, dreamlet mixed models, ProStaR preparation, standalone voom-only '
 'transformation/custom residualization and paper-executed linked commits remain unqualified. '
 'P086 retains both methylation editions; P195 retains the reported 3.44 label without an '
 'invented patch. P228 retains the author-reported voomLmFit/lmfit spelling and unresolved '
 'provider attribution. P203 quantile normalization belongs to preprocessCore, not limma. '
 'P059 historical stock limma is excluded from positive use; retain scrattch. P237 remains '
 'edgeR-only. Generic native differential/VoomTrend APIs are unchanged and are not stock '
 'limma qualifications. Build current source; released 0.4.0 predates limma-tools.')
CURRENT=('250 papers, 446 named tool labels, 2,717 observations, 1,598 individually reviewed '
 'positive uses and 1,119 pending. There are 142 scoped integrations (26 native Java and '
 '116 upstream), two validated family aliases with zero extra credit, 259 missing and '
 '31 method-overlap labels; 145 integrations include three existing adapters. All 1,575 '
 'earlier reviews remain exact. Full implementation and paper-wide semantic completeness '
 'remain unfinished.')
ACTION=('SKIP3: DEPENDENT ANALYSIS SKIPPED; supply the named exact permitted source, input, '
 'runtime or package and retry NEW output, or retain the skip if it cannot be supplied. '
 'Downloads and private installation remain authorized where the environment permits; '
 'absence is not an installation prohibition. FAIL2: NO COMPLETE RESULT; preserve raw '
 'inputs, original model/null/partial objects, warnings and failed attempts, resolve the '
 'named source/data/design, numerical/convergence, integrity, capacity or process cause '
 'and retry NEW output. Never silently repair identifiers, populations, thresholds, '
 'editions, estimators or nonfinite values. Retain valid infinite prior df and negative '
 'unconstrained variance components. All three exact R packages are required by this '
 'profile; no generic fallback. Original-R Linux execution is not claimed. Cell Ranger '
 'stays excluded. Both literature reports stay withheld from Pages. Ignored exact runtimes, '
 'JARs, full text and complete private evidence need a separate checksummed transfer.')
NOTICE=('<aside id="limma-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
 'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>Limma/voom: one implementation family and 23 reviewed labels</h2><p><strong>Warnings '
 'and required actions:</strong> '+html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)+'</p><p>'
 +html.escape(SCOPE+' '+NOTE)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'
 +GUIDE+'">Exact source roles, scope and recovery</a>.</p></aside>')
encoded=p.encoded
require=p.require

def registry():
    from gcta_catalog.model import registry as previous
    rows=previous();require(len(rows)==485,'Different registry population')
    for tool in ['limma','voom']:
        row=next(r for r in rows if r['id']==tool)
        require(row['current_coverage']=='method-overlap','Different previous limma/voom state')
        row.update(current_coverage='upstream-adapter-validated' if tool=='limma' else 'validated-family-alias',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE)
    return rows

def validate(before,primary,qualification):
    require(set(before)==set(META),'Exactly eight preceding metadata files required');p.validate(primary)
    require(primary==p.frozen(),'Different qualified primary/code source review')
    require(qualification['status']=='PASS_SAVED_GLOBAL_LIMMA_AND23_SOURCE_ROLES_NOT_CATALOG_ADMISSION'
        and qualification['saved_tests_per_JVM']==78 and qualification['JVMs']==3
        and qualification['global_original_RUN_examples']==6 and qualification['global_original_REPLAY']==8
        and qualification['global_successful_capture_verifications']==18 and qualification['reviewed_source_labels']==23
        and qualification['source_semantic_controls_per_host']==15 and qualification['source_control_hosts']==2
        and qualification['legacy_CLI_regression_tests']==22,'Exact global qualification required')
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);reviews=json.loads(before[META[5]])
    require(len(papers)==250 and len(inventory)==446 and sum(len(r['tools']) for r in papers)==2715
        and len(reviews['reviews'])==1575,'Different preceding catalog population')
    require(not any(r['tool_id'] in ['limma','voom'] for r in reviews['reviews']),'Existing limma/voom reviews must not be overwritten')
    for tool,count in [('limma',16),('voom',5)]:
        row=next(r for r in inventory if r['id']==tool)
        require(row['implementation_status']==row['current_coverage']=='method-overlap' and row['paper_count']==count,'Different/already admitted limma/voom state')
    identities={r['paper_id']:r for r in primary['source_identities']}
    existing={(r['paper_id'],r['tool_id']) for r in primary['roles'] if r['existing_observation']}|{('P059','limma')}
    for paper in papers:
        pid=paper['paper_id'];require(all(paper[k]==v for k,v in identities[pid].items()),'Frozen paper identity changed')
        for tool in ['limma','voom']:
            require(sum(r['tool_id']==tool for r in paper['tools'])==int((pid,tool) in existing),'Different existing observation membership: '+pid+' '+tool)
    return primary['roles']

def transition(before,primary,qualification):
    roles=validate(before,primary,qualification);papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before[META[5]])
    byid={r['paper_id']:r for r in papers};byid['P059']['tools']=[r for r in byid['P059']['tools'] if r['tool_id']!='limma']
    for role in roles:
        paper=byid[role['paper_id']];tool=role['tool_id'];pointer=role['primary_evidence'][0]
        if role['proposed_observation']:paper['tools'].append(dict(tool_id=tool,tool='limma',matched_text=role['source_name_witness']['literal'],extraction_score=0))
        use=next(t for t in paper['tools'] if t['tool_id']==tool)
        # Distinct editions stay in structured source roles; no single-version invention.
        versions=list(dict.fromkeys(r['literal'] for r in role['reported_editions']))
        version=versions[0] if len(versions)==1 else None
        text=role['usage_summary']
        if versions:text+=' Reported limma edition'+('s' if len(versions)>1 else '')+': '+', '.join(versions)+'.'
        caution=role['required_caution']+' This source review is not a complete author workflow or biological reproduction.'
        use.update(analysis_role='Limma/voom: '+'; '.join(role['operations']),use_summary=text,implementation_scope_note=caution,
            matched_text=role['source_name_witness']['literal'],evidence_section=pointer['section'],evidence_paragraph_index=pointer['paragraph_index'],
            evidence_paragraph_sha256=pointer['paragraph_sha256'],evidence_locator=paper['fulltext_url']+' (section: '+pointer['section']+')',
            evidence_selection='individually-reviewed-source-role',usage_context='analysis-method-description',
            review_status='usage-summary-reviewed; paper-wide-completeness-review-open',version_reported_near_name=version,source_role_qualification=copy.deepcopy(role))
        support=copy.deepcopy(role['primary_evidence'][1:])
        if support:use['supporting_evidence']=support
        else:use.pop('supporting_evidence',None)
        record=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=tool,evidence_paragraph_sha256=pointer['paragraph_sha256'],use_summary=text,implementation_scope_note=caution)
        if version:record['version_reported_near_name']=version
        if support:record['supporting_evidence']=support
        reviews['reviews'].append(record)
    for tool,status,credit in [('limma','upstream-adapter-validated',1),('voom','validated-family-alias',0)]:
        row=next(r for r in inventory if r['id']==tool)
        row.update(previous_method_overlap_source=row['existing_source'],current_coverage=status,implementation_status=status,
            existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE+' '+NOTE,implementation_scope=SCOPE+' '+NOTE,
            validation_note='Exact original-R prepared-data scope qualified on Windows R 4.6.1; packaged tests and saved-capture checks on Windows21/25/Linux21. VERIFY does not refit. Read the exact source crosswalk and warnings.',
            implementation_family_id='limma',new_scoped_integration_credit=credit,upstream_family_credit_added=credit,native_family_credit_added=0,
            source_role_coverage=COVERAGE.rsplit('/',1)[-1],source_roles_reviewed=sum(r['tool_id']==tool for r in roles),current_documentation=GUIDE,
            workflow_qualification='../limma-global-development/PROFILE.json',public_CLI_available=True,global_CLI_dispatch_available=True,
            qualified_component_entrypoints=['limma-tools --request RUN/VERIFY/REPLAY','org.jlinalg.cli.LimmaWorkflowCli'],
            source_build_required=True,released_binary_contains_workflow_adapter=False,
            paper_role_qualification={r['paper_id']:copy.deepcopy(r) for r in roles if r['tool_id']==tool},whole_tool_qualified=False,whole_paper_reproduced=False)
        if tool=='voom':row['family_alias_of']='limma'
        row['papers']=[p['paper_id'] for p in papers if any(t['tool_id']==tool for t in p['tools'])];row['paper_count']=len(row['papers'])
    counts=dict(Counter(t['implementation_status'] for t in inventory))
    require(counts['upstream-adapter-validated']==116 and counts['native-subset-validated']==26 and counts['validated-family-alias']==2
        and counts['method-overlap']==31 and counts['missing']==259,'Incorrect family/alias credit')
    summary.update(implementation_status_counts=counts,paper_tool_observations=2717,integration_family_counts=dict(scoped=142,native=26,upstream=116,including_existing_adapters=145,validated_alias_labels_excluded_from_extra_credit=2))
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[5],reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before[META[6]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Different preceding embedded report')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    for name,text in [(META[6],page),(META[7],before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE)==1 and 'id="limma-catalog-notice"' not in text,'Different/already admitted report notice')
        text=text.replace(PREVIOUS_NOTICE,'<details><summary>Previous GCTA checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1)
        body=re.search(r'<body[^>]*>',text,re.I);require(body is not None,'Report body missing');after[name]=(text[:body.end()]+NOTICE+text[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
        after[name]=('# '+title+'\n\n## Limma/voom: one credited family and 23 reviewed labels\n\n**Warnings and required actions:** '+ACTION+'\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\nRead [the source crosswalk](../LIMMA_CATALOG_INTEGRATION.md).\n\n<details><summary>Before limma admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after

def check(before,after,primary,qualification):
    require(after==transition(before,primary,qualification),'Different deterministic source-bound transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity/metadata')
        require([t for t in a['tools'] if t['tool_id'] not in ['limma','voom']]==[t for t in b['tools'] if t['tool_id'] not in ['limma','voom']],'Changed unrelated observation')
        expected=[t['tool_id'] for t in a['tools'] if not (a['paper_id']=='P059' and t['tool_id']=='limma')]+(['limma'] if a['paper_id'] in ['P088','P183','P203'] else [])
        require([t['tool_id'] for t in b['tools']]==expected,'Unreviewed observation addition/removal/order change')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]])
    require([r for r in a if r['id'] not in ['limma','voom']]==[r for r in b if r['id'] not in ['limma','voom']],'Changed unrelated 444 inventory rows')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]])
    require(b['reviews'][:1575]==a['reviews'] and len(b['reviews'])==1598 and all(r['tool_id'] in ['limma','voom'] for r in b['reviews'][1575:]),'Changed previous reviews or append scope')
    require({k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'}
        and len({(r['pmcid'],r['tool_id']) for r in b['reviews']})==1598,'Changed review metadata or duplicate identity')
    counts=Counter(t['tool_id'] for paper in new for t in paper['tools']);require(len(counts)==446 and sum(counts.values())==2717,'Different named/observation population')
    for row in json.loads(after[META[1]]):
        require(row['paper_count']==counts[row['id']] and row['papers']==[p['paper_id'] for p in new if any(t['tool_id']==row['id'] for t in p['tools'])],'Inventory membership/count differs')
    return dict(papers=250,named_tools=446,registry_definitions=485,observations=2717,reviewed_uses=1598,pending_uses=1119,
        existing_reviews_retained=1575,new_review_records=23,positive_limma_labels=18,positive_voom_labels=5,positive_papers=18,
        observations_added=3,observations_removed=1,integrations=142,native_integrations=26,upstream_integrations=116,
        including_existing_adapters=145,validated_family_alias_labels=2,alias_extra_integration_credit=0,method_overlap=31,missing=259,
        new_scoped_integration_credit=1,upstream_family_credit_added=1,native_family_credit_added=0,
        whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
