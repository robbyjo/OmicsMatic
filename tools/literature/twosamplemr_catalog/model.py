"""Admit one scoped upstream TwoSampleMR family and fourteen literal source roles."""
from collections import Counter
import copy,html,json,re
from twosamplemr_catalog import primary as p
from peer_catalog.model import META,DATA,NOTICE as PREVIOUS_NOTICE

GUIDE='docs/literature/TWOSAMPLEMR_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/twosamplemr-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/TwoSampleMrToolsCli.java'
PATTERN=r'(?<![A-Za-z0-9_])TwoSampleMR(?![A-Za-z0-9_])'
SCOPE=('Global twosamplemr-tools provides exact-profile original-package RUN, offline VERIFY and REPLAY. '
 'Prepared MR, harmonization, sensitivity and MV preparation are qualified for 0.7.14 and tagged 0.4.9/0.5.6/0.5.7/0.6.8; '
 'historical FORMAT and original 0.7.14/0.6.8 local clumping are available with explicit runtime/input/seed/capacities. '
 'Complete original returns/errors/RNG and typed exports are retained. VERIFY checks saved integrity, source projections '
 'and selected native statistics, not every provider refit or coordinated replacement authentication. REPLAY preserves parents.')
NOTE=('One upstream TwoSampleMR family is credited. Its native deterministic Java helpers add no second family credit. '
 'Fourteen source roles are reviewed, not fourteen executed author pipelines. Unreported editions remain unknown; '
 'release tags and present-day dependencies do not establish paper execution commits or historical dependency eras. '
 'The 0.4.9 reference build omits only broken man documentation, preserving all scientific/namespace/data bytes. '
 'Older 0.4.9/0.5.6/0.5.7 API clumping, remote reference/authentication, optional/provider scopes and complete author '
 'preparation remain unqualified. P157 archived bidirectional harmonise_data/mr attribution is static; P177 IVW is '
 'explicitly a separate MendelianRandomization provider, median/mode attribution is an inference, and TEMR/Egger gaps '
 'remain explicit. Preserve P112 trait counts, P150 MAF wording, P211 Steiger thresholds and P224 separate MVMR/F. '
 'Original R execution is Windows 4.6.1; Linux qualification verifies saved captures. Build current source; released 0.4.0 predates this command.')
CURRENT=('250 papers, 446 named labels, 2,722 observations, 1,630 individually reviewed uses and 1,092 pending. '
 'There are 144 scoped integrations (26 native Java + 118 upstream), two zero-credit validated aliases, 259 missing '
 'and 29 method-overlap labels; 147 integrations include three existing adapters. All 1,616 earlier reviews remain exact. '
 'Full implementation and paper-wide semantic completeness remain unfinished.')
ACTION=('SKIP3: DEPENDENT ANALYSIS SKIPPED; supply the named exact permitted runtime, package/profile, PLINK or input, '
 'or retain the skip. Downloads/private installation are authorized when allowed; absence is not an installation prohibition. '
 'If actually disallowed, name the affected analysis and what the user must supply. FAIL2: NO COMPLETE RESULT; preserve '
 'raw/original/null/partial/failed evidence, original errors and logs, correct the named data/design, numerical, integrity, '
 'capacity or process cause and retry NEW output. Never silently repair IDs, populations, allele/trait order, thresholds, '
 'versions, estimators/providers or nonfinite values. Read WARNINGS.md,analysis-status.json,worker/report.json and source logs. '
 'Singleton clumping may bypass reference/significance checks; a complete empty selection or MV preparation supplies no MR estimate. '
 'Old source/startup configuration stays private. Cell Ranger remains excluded. Both literature reports remain off Pages. '
 'Ignored exact runtimes/JARs/full text and complete private evidence require a separate checksummed transfer.')
NOTICE=('<aside id="twosamplemr-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
 'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>TwoSampleMR: original workflows and 14 reviewed source roles</h2><p><strong>Warnings and required actions:</strong> '
 +html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE+' '+NOTE)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'
 +GUIDE+'">Exact roles, scope and recovery</a>.</p></aside>')
encoded=p.encoded
require=p.require

def registry():
    from peer_catalog.model import registry as preceding
    rows=preceding();require(len(rows)==485,'Different preceding registry population')
    row=next(r for r in rows if r['id']=='twosamplemr');require(row['current_coverage']=='method-overlap','Different/already admitted TwoSampleMR state')
    row.update(pattern=PATTERN,current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE);return rows

def validate(before,primary,qualification):
    require(set(before)==set(META),'Exactly eight preceding metadata files required');p.validate(primary)
    require(qualification['status']=='PASS_GLOBAL_TWOSAMPLEMR858_CATALOG_UNFINISHED'
        and qualification['packaged_tests_per_JVM']==858 and qualification['JVMs']==3
        and qualification['global_profiles']==qualification['global_examples']==qualification['actual_global_replays']==7
        and qualification['complete_original_returns_classes_RNG_identical']==50
        and qualification['Linux_R_free_verifications']==7 and qualification['global_refusals']==10
        and qualification['neighbor_help_identities']==6 and qualification['fresh_primary_papers_per_host']==250
        and qualification['source_roles']==14 and qualification['source_refusals_per_host']==16 and qualification['source_hosts']==2
        and qualification['global_command_registered'] and qualification['new_catalog_credit']==0
        and not qualification['whole_goal_complete'],'Exact original/global TwoSampleMR qualification required')
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);reviews=json.loads(before[META[5]])
    require(len(papers)==250 and len(inventory)==446 and sum(len(r['tools']) for r in papers)==2720 and len(reviews['reviews'])==1616,'Different preceding catalog population')
    require(not any(r['tool_id']=='twosamplemr' for r in reviews['reviews']),'Existing reviews must not be overwritten')
    row=next(r for r in inventory if r['id']=='twosamplemr')
    require(row['implementation_status']==row['current_coverage']=='method-overlap' and row['paper_count']==12 and row['papers']==primary['original_observations'],'Different/already admitted inventory')
    identities={r['paper_id']:r for r in primary['source_identities']}
    for paper in papers:
        pid=paper['paper_id'];require(all(paper[k]==v for k,v in identities[pid].items()),'Frozen paper identity changed')
        require(sum(r['tool_id']=='twosamplemr' for r in paper['tools'])==int(pid in primary['original_observations']),'Different existing membership: '+pid)
    return primary['roles']

def transition(before,primary,qualification):
    roles=validate(before,primary,qualification);papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before[META[5]]);byid={r['paper_id']:r for r in papers}
    for role in roles:
        paper=byid[role['paper_id']];pointer=role['primary_evidence'][0]
        if role['proposed_observation']:paper['tools'].append(dict(tool_id='twosamplemr',tool='TwoSampleMR',matched_text=role['source_name_witness']['literal'],extraction_score=0))
        use=next(r for r in paper['tools'] if r['tool_id']=='twosamplemr');edition=role['reported_edition'];version=edition['literal'] if edition else None;text,caution=p.role_text(role,primary)
        if version:text+=' Reported TwoSampleMR edition: '+version+'.'
        use.update(analysis_role='TwoSampleMR: '+role['kind'],use_summary=text,implementation_scope_note=caution,
            matched_text=role['source_name_witness']['literal'],evidence_section=pointer['section'],evidence_paragraph_index=pointer['paragraph_index'],evidence_paragraph_sha256=pointer['paragraph_sha256'],
            evidence_locator=paper['fulltext_url']+' (section: '+pointer['section']+')',evidence_selection='individually-reviewed-source-role',usage_context='analysis-method-description',
            review_status='usage-summary-reviewed; paper-wide-completeness-review-open',version_reported_near_name=version,source_role_qualification=copy.deepcopy(role))
        if role['paper_id'] in ['P157','P177']:use['static_author_code_attribution']=copy.deepcopy(primary['author_attribution'][role['paper_id']])
        support=copy.deepcopy(role['primary_evidence'][1:])
        if support:use['supporting_evidence']=support
        else:use.pop('supporting_evidence',None)
        record=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='twosamplemr',evidence_paragraph_sha256=pointer['paragraph_sha256'],use_summary=text,implementation_scope_note=caution)
        if version:record['version_reported_near_name']=version
        if support:record['supporting_evidence']=support
        reviews['reviews'].append(record)
    row=next(r for r in inventory if r['id']=='twosamplemr')
    row.update(previous_method_overlap_source=row['existing_source'],pattern=PATTERN,current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',
        existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE+' '+NOTE,implementation_scope=SCOPE+' '+NOTE,
        validation_note='Seven global original-package profiles;858 packaged tests per Windows21/25/Linux21 JVM, seven actual examples/replays and50 complete original-return identities. Exact scientific payloads preserved; no author pipeline or dependency-era reproduction.',
        implementation_family_id='twosamplemr',new_scoped_integration_credit=1,upstream_family_credit_added=1,native_family_credit_added=0,
        source_role_coverage=COVERAGE.rsplit('/',1)[-1],source_roles_reviewed=14,current_documentation=GUIDE,workflow_qualification='../twosamplemr-global-checkpoint/PROFILE.json',
        public_CLI_available=True,global_CLI_dispatch_available=True,qualified_component_entrypoints=['twosamplemr-tools --request RUN/VERIFY/REPLAY','org.jlinalg.cli.TwoSampleMrToolsCli'],
        source_build_required=True,released_binary_contains_workflow_adapter=False,native_helpers_additional_family_credit=0,
        paper_role_qualification={r['paper_id']:copy.deepcopy(r) for r in roles},whole_tool_qualified=False,whole_paper_reproduced=False)
    row['papers']=[r['paper_id'] for r in papers if any(t['tool_id']=='twosamplemr' for t in r['tools'])];row['paper_count']=len(row['papers']);counts=dict(Counter(r['implementation_status'] for r in inventory))
    require(counts['upstream-adapter-validated']==118 and counts['native-subset-validated']==26 and counts['validated-family-alias']==2 and counts['method-overlap']==29 and counts['missing']==259,'Incorrect family/alias credit')
    summary.update(implementation_status_counts=counts,paper_tool_observations=sum(len(r['tools']) for r in papers),integration_family_counts=dict(scoped=144,native=26,upstream=118,including_existing_adapters=147,validated_alias_labels_excluded_from_extra_credit=2))
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[5],reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before[META[6]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Different preceding embedded report')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    for name,text in [(META[6],page),(META[7],before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE)==1 and 'id="twosamplemr-catalog-notice"' not in text,'Different/already admitted report notice')
        text=text.replace(PREVIOUS_NOTICE,'<details><summary>Previous PEER checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1)
        body=re.search(r'<body[^>]*>',text,re.I);require(body is not None,'Missing report body');after[name]=(text[:body.end()]+NOTICE+text[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
        after[name]=('# '+title+'\n\n## TwoSampleMR: one original-workflow family and14 reviewed roles\n\n**Warnings and required actions:** '+ACTION+'\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\nRead [the source crosswalk](../TWOSAMPLEMR_CATALOG_INTEGRATION.md).\n\n<details><summary>Before TwoSampleMR admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after

def check(before,after,primary,qualification):
    require(after==transition(before,primary,qualification),'Different deterministic source-bound transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity/metadata')
        require([r for r in a['tools'] if r['tool_id']!='twosamplemr']==[r for r in b['tools'] if r['tool_id']!='twosamplemr'],'Changed unrelated observation')
        require([r['tool_id'] for r in b['tools']]==[r['tool_id'] for r in a['tools']]+(['twosamplemr'] if a['paper_id'] in primary['proposed_additions'] else []),'Unreviewed addition/removal/order change')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']!='twosamplemr']==[r for r in b if r['id']!='twosamplemr'],'Changed unrelated445 inventory rows')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]])
    require(b['reviews'][:1616]==a['reviews'] and len(b['reviews'])==1630 and all(r['tool_id']=='twosamplemr' for r in b['reviews'][1616:]),'Changed earlier review or append scope')
    require({k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'} and len({(r['pmcid'],r['tool_id']) for r in b['reviews']})==1630,'Changed review metadata or duplicate identity')
    counts=Counter(t['tool_id'] for paper in new for t in paper['tools']);require(len(counts)==446 and sum(counts.values())==2722,'Different named/observation population')
    for row in json.loads(after[META[1]]):require(row['paper_count']==counts[row['id']] and row['papers']==[r['paper_id'] for r in new if any(t['tool_id']==row['id'] for t in r['tools'])],'Inventory membership/count differs')
    return dict(papers=250,named_tools=446,registry_definitions=485,observations=2722,reviewed_uses=1630,pending_uses=1092,existing_reviews_retained=1616,new_review_records=14,
        positive_twosamplemr_labels=14,positive_papers=14,observations_added=2,observations_removed=0,integrations=144,native_integrations=26,upstream_integrations=118,
        including_existing_adapters=147,validated_family_alias_labels=2,alias_extra_integration_credit=0,method_overlap=29,missing=259,new_scoped_integration_credit=1,
        upstream_family_credit_added=1,native_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
