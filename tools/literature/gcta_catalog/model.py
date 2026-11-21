"""One credited GCTA implementation, one COJO alias,27 source reviews and exact unrelated state."""
from collections import Counter
import copy,html,json,re
from gcta_catalog import primary as p
from gsea_catalog.model import META,DATA,NOTICE as PREVIOUS_NOTICE

GUIDE='docs/literature/GCTA_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/gcta-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/GctaWorkflowCli.java'
SCOPE=('Global gcta-tools and standalone GctaWorkflowCli expose exact GCTA 1.94.1 original Windows/Linux '
 'GRM, PCA, COJO selection/joint/conditioning, constrained/unconstrained/multi-GRM REML, LD scores, sparse GRM, '
 'binary fastGWA and GSMR/supplied-effect mtCOJO on explicitly prepared complete inputs. Native Java '
 'components cover source-qualified hard-call GRM,sparse conversion and prepared-parameter conditional '
 'equations. RUN captures inputs and raw model/null/partial/error objects; VERIFY checks saved integrity '
 'and declared invariants, and REPLAY preserves parent lineage. Native components add no second family credit.')
NOTE=('GCTA is the single credited upstream family; COJO is its validated-family-alias with zero additional '
 'integration credit. The 27 reviewed labels across 19 executed papers distinguish operations, reported '
 'editions and unresolved author preparation. Add P112/P169 and remove only P127 comparison-only COJO '
 'with exact-before retention. Older 1.92.4/1.93.2/1.93.0 and P140 1.91.7beta/fastGWA, panel correspondence '
 'in P169, wheat ploidy in P171, P135 uppercase --MAF and P248 formula/cutoff remain unresolved. '
 'The qualified 1.94.1 source does not substitute for those editions or complete author workflows. '
 'Build current source; the released 0.4.0 binary predates gcta-tools.')
CURRENT=('250 papers, 446 named tool labels, 2,715 observations, 1,575 individually reviewed positive uses '
 'and 1,140 pending. There are 141 scoped integrations (26 native Java and 115 upstream), one validated '
 'family alias with zero extra credit, 259 missing and 33 method-overlap labels; 144 integrations include '
 'three existing adapters. All 1,548 earlier reviews remain exact. Full implementation and paper-wide '
 'semantic completeness remain unfinished.')
ACTION=('SKIP3: dependent analysis skipped; supply the named exact permitted source, input, runtime or '
 'package, or retain the skip if installation is forbidden. FAIL2: NO COMPLETE RESULT; preserve raw '
 'inputs, original model/null objects, partials and logs, correct the named source/data/design, numerical, '
 'convergence, integrity, capacity or process cause and retry NEW output. Never silently repair IDs, '
 'alleles, populations, thresholds, nonfinite values, editions or algorithms. Retain valid unconstrained '
 'variance/h2 outside 0..1. GCTA 1.94.1 mtCOJO has no liability conversion; original GSMR can exit 0 '
 'and write NaN, and those results are refused. Read analysis-status.json, WARNINGS.md, manifest.json '
 'and every original worker log. Original-model VERIFY does not refit or authenticate. Cell Ranger '
 'stays excluded. Both literature reports remain withheld from Pages. Ignored exact runtimes, JARs, '
 'full text and complete private evidence need a separate checksummed transfer.')
NOTICE=('<aside id="gcta-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;'
 'padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>GCTA/COJO: one implementation family and 27 reviewed labels</h2><p><strong>Warnings and required actions:</strong> '
 +html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE+' '+NOTE)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Exact source roles,scope and recovery</a>.</p></aside>')
encoded=p.encoded
require=p.require
def registry():
    from gsea_catalog.model import registry as previous
    rows=previous();require(len(rows)==485,'Different registry population')
    for tool in ['gcta','cojo']:
        row=next(r for r in rows if r['id']==tool);require(row['current_coverage']=='method-overlap','Different previous family/alias state')
        row.update(current_coverage='upstream-adapter-validated' if tool=='gcta' else 'validated-family-alias',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE)
    return rows
def validate(before,primary,qualification):
    require(set(before)==set(META),'Exactly eight preceding metadata files required');p.validate(primary)
    require(qualification['status']=='PASS_SAVED_GCTA_GLOBAL_AND_27_REVIEWED_LABEL_ROLES_NOT_CATALOG_ADMISSION' and qualification['saved_tests_per_JVM']==27 and qualification['JVMs']==3 and qualification['saved_global_capture_verifications']==246 and qualification['saved_original_table_comparisons']==12,'Exact global qualification required')
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);reviews=json.loads(before[META[5]])
    require(len(papers)==250 and len(inventory)==446 and sum(len(r['tools']) for r in papers)==2714 and len(reviews['reviews'])==1548,'Different preceding catalog population')
    require(not any(r['tool_id'] in ['gcta','cojo'] for r in reviews['reviews']),'Existing GCTA/COJO reviews must not be overwritten')
    for tool,count in [('gcta',17),('cojo',9)]:
        row=next(r for r in inventory if r['id']==tool);require(row['implementation_status']==row['current_coverage']=='method-overlap' and row['paper_count']==count,'Different/already admitted family state')
    candidates={r['paper_id']:r for r in primary['candidates']};identities={r['paper_id']:r for r in primary['source_identities']}
    for paper in papers:
        pid=paper['paper_id'];require(all(paper[k]==v for k,v in identities[pid].items()),'Frozen paper identity changed')
        current=[t['tool_id'] for t in paper['tools'] if t['tool_id'] in ['gcta','cojo']];require(current==candidates.get(pid,{}).get('existing_tool_ids',[]),'Different existing observation membership/order: '+pid)
    return primary['roles']
def transition(before,primary,qualification):
    roles=validate(before,primary,qualification);papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before[META[5]])
    byid={r['paper_id']:r for r in papers};removed=byid['P127'];removed['tools']=[r for r in removed['tools'] if r['tool_id']!='cojo']
    for role in roles:
        paper=byid[role['paper_id']];tool=role['tool_id'];pointer=role['primary_evidence'][0]
        if role['proposed_observation']:paper['tools'].append(dict(tool_id=tool,tool='GCTA',matched_text=role['source_name_witness']['literal'],extraction_score=0))
        use=next(t for t in paper['tools'] if t['tool_id']==tool)
        version=role['reported_edition'] if role['paper_id']!='P248' else None
        summary_text=role['usage_summary']
        if role['paper_id']=='P248':summary_text+=' Reported PCA edition: 1.94.1; REML edition label: 1.94.'
        caution=role['required_caution']+' This source review is not a complete author workflow or biological reproduction.'
        use.update(analysis_role='GCTA/COJO: '+'; '.join(role['operations']),use_summary=summary_text,implementation_scope_note=caution,matched_text=role['source_name_witness']['literal'],
            evidence_section=pointer['section'],evidence_paragraph_index=pointer['paragraph_index'],evidence_paragraph_sha256=pointer['paragraph_sha256'],
            evidence_locator=paper['fulltext_url']+' (section: '+pointer['section']+')',evidence_selection='individually-reviewed-source-role',
            usage_context='analysis-method-description',review_status='usage-summary-reviewed; paper-wide-completeness-review-open',version_reported_near_name=version,source_role_qualification=copy.deepcopy(role))
        support=copy.deepcopy(role['primary_evidence'][1:])
        if support:use['supporting_evidence']=support
        else:use.pop('supporting_evidence',None)
        record=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=tool,evidence_paragraph_sha256=pointer['paragraph_sha256'],use_summary=summary_text,implementation_scope_note=caution)
        if version:record['version_reported_near_name']=version
        if support:record['supporting_evidence']=support
        reviews['reviews'].append(record)
    for tool,status,credit in [('gcta','upstream-adapter-validated',1),('cojo','validated-family-alias',0)]:
        row=next(r for r in inventory if r['id']==tool);row.update(previous_method_overlap_source=row['existing_source'],current_coverage=status,implementation_status=status,existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE+' '+NOTE,implementation_scope=SCOPE+' '+NOTE,
            validation_note='Exact source-profiled prepared-data components and global RUN/VERIFY/REPLAY qualified on Windows21/25/Linux21. Original VERIFY checks invariants without refitting. Read the source crosswalk and exact warnings.',
            implementation_family_id='gcta',new_scoped_integration_credit=credit,upstream_family_credit_added=credit,native_family_credit_added=0,
            source_role_coverage=COVERAGE.rsplit('/',1)[-1],source_roles_reviewed=sum(r['tool_id']==tool for r in roles),current_documentation=GUIDE,workflow_qualification='../gcta-global-development/PROFILE.json',
            public_CLI_available=True,global_CLI_dispatch_available=True,qualified_component_entrypoints=['gcta-tools --request RUN/VERIFY/REPLAY','org.jlinalg.cli.GctaWorkflowCli'],source_build_required=True,released_binary_contains_workflow_adapter=False,
            paper_role_qualification={r['paper_id']:copy.deepcopy(r) for r in roles if r['tool_id']==tool},whole_tool_qualified=False,whole_paper_reproduced=False)
        if tool=='cojo':row['family_alias_of']='gcta'
        row['papers']=[p['paper_id'] for p in papers if any(t['tool_id']==tool for t in p['tools'])];row['paper_count']=len(row['papers'])
    counts=dict(Counter(t['implementation_status'] for t in inventory));require(counts['upstream-adapter-validated']==115 and counts['native-subset-validated']==26 and counts['validated-family-alias']==1 and counts['method-overlap']==33 and counts['missing']==259,'Incorrect family/alias credit')
    summary.update(implementation_status_counts=counts,paper_tool_observations=2715,integration_family_counts=dict(scoped=141,native=26,upstream=115,including_existing_adapters=144,validated_alias_labels_excluded_from_extra_credit=1))
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[5],reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before[META[6]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Different preceding embedded report')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    for name,text in [(META[6],page),(META[7],before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE)==1 and 'id="gcta-catalog-notice"' not in text,'Different/already admitted report notice')
        text=text.replace(PREVIOUS_NOTICE,'<details><summary>Previous GSEA checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1);body=re.search(r'<body[^>]*>',text,re.I);require(body is not None,'Report body missing');after[name]=(text[:body.end()]+NOTICE+text[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:after[name]=('# '+title+'\n\n## GCTA/COJO: one credited family and 27 reviewed labels\n\n**Warnings and required actions:** '+ACTION+'\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\nRead [the source crosswalk](../GCTA_CATALOG_INTEGRATION.md).\n\n<details><summary>Before GCTA admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after
def check(before,after,primary,qualification):
    require(after==transition(before,primary,qualification),'Different deterministic source-bound transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity/metadata')
        require([t for t in a['tools'] if t['tool_id'] not in ['gcta','cojo']]==[t for t in b['tools'] if t['tool_id'] not in ['gcta','cojo']],'Changed unrelated observation')
        expected=[t['tool_id'] for t in a['tools'] if not (a['paper_id']=='P127' and t['tool_id']=='cojo')]+(['gcta'] if a['paper_id'] in ['P112','P169'] else [])
        require([t['tool_id'] for t in b['tools']]==expected,'Unreviewed observation addition/removal/order change')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id'] not in ['gcta','cojo']]==[r for r in b if r['id'] not in ['gcta','cojo']],'Changed unrelated444 inventory rows')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]]);require(b['reviews'][:1548]==a['reviews'] and len(b['reviews'])==1575 and all(r['tool_id'] in ['gcta','cojo'] for r in b['reviews'][1548:]),'Changed previous reviews or append scope')
    require({k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'} and len({(r['pmcid'],r['tool_id']) for r in b['reviews']})==1575,'Changed review metadata or duplicate identity')
    counts=Counter(t['tool_id'] for paper in new for t in paper['tools']);require(len(counts)==446 and sum(counts.values())==2715,'Different named/observation population')
    for row in json.loads(after[META[1]]):require(row['paper_count']==counts[row['id']] and row['papers']==[p['paper_id'] for p in new if any(t['tool_id']==row['id'] for t in p['tools'])],'Inventory membership/count differs')
    return dict(papers=250,named_tools=446,registry_definitions=485,observations=2715,reviewed_uses=1575,pending_uses=1140,existing_reviews_retained=1548,new_review_records=27,positive_gcta_labels=19,positive_cojo_labels=8,executed_papers=19,observations_added=2,observations_removed=1,integrations=141,native_integrations=26,upstream_integrations=115,including_existing_adapters=144,validated_family_alias_labels=1,alias_extra_integration_credit=0,method_overlap=33,missing=259,new_scoped_integration_credit=1,upstream_family_credit_added=1,native_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
