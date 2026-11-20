"""Deterministic GSEA admission; preserve every unrelated role and old review."""
from collections import Counter
import copy,html,json,re
from gsea_catalog import primary as p
from gsea_catalog.decisions import ADDITIONS,ORIGINAL
from edger_catalog.model import META,DATA,NOTICE as PREVIOUS_NOTICE

GUIDE='docs/literature/GSEA_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/gsea-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/BroadGseaWorkflowCli.java'
SCOPE=('Native broad-gsea-tools and BroadGseaWorkflowCli implement explicit prepared-data Broad GSEA '
 '4.3.2/4.3.3/4.4.0 source profiles: preranked, expression/gene-set and expression/phenotype nulls, '
 'source ranking/randomization/weight options, meandiv NES, nominal P and empirical FDR/FWER. '
 'RUN captures literal inputs, complete null families and failures; VERIFY recomputes every scientific '
 'byte in NEW output and REPLAY preserves parent lineage. No external GSEA runtime is needed.')
NOTE=('The GSEA family is a generic ranked-enrichment catalog label. Its 28 reviewed roles explicitly '
 'distinguish Broad, clusterProfiler, fgsea, GSEApy, MAGeCK and unresolved executors. The native Broad '
 'component does not validate those other providers or complete author workflows. Only P077/P220 '
 'name matching qualified Broad source editions; their author inputs/settings remain unrecovered. '
 'P096 source code establishes classic preranked/meandiv settings, 1000 permutations and size 15-200, '
 'but 2023.1 is not a resolved executable edition and timestamp is not a recovered historical seed. '
 'Vendor input parsing, expression transformation, identifier mapping, probe collapse, collection '
 'retrieval, single-sample scoring and whole-paper/biological reproduction are outside this subset. '
 'Build current source; the released 0.4.0 binary predates these workflows.')
CURRENT=('250 papers, 446 named families, 2,714 observations, 1,548 individually reviewed positive uses '
 'and 1,166 pending. There are 140 scoped integrations (26 native Java and 114 upstream), 259 missing '
 'and 35 method-overlap definitions; 143 integrations include three existing adapters. All 1520 earlier '
 'reviews remain exact. Nine missed ranked-enrichment observations are added. The full implementation '
 'and paper-wide semantic-review goal remains unfinished.')
ACTION=('SKIP3: the affected analysis is skipped; supply the named exact permitted input, source profile, '
 'runtime or package, or retain the skip when unavailable or installation is forbidden. Native Broad '
 'execution needs no external GSEA installation; an unknown author executor/edition is not silently '
 'mapped to a supported profile. FAIL2: NO COMPLETE RESULT; preserve raw inputs, null/model objects, '
 'conditions, partials and diagnostics, resolve the named syntax/data/design, numerical/convergence, '
 'integrity, capacity or process cause and retry NEW output. Never silently repair identifiers, '
 'alleles, populations, thresholds, nonfinite values, editions or algorithms. A supervising JVM '
 'terminated without final completion status has no complete result. Cell Ranger stays excluded. '
 'Ignored runtimes, producer JARs and complete private evidence require a separate permitted transfer; '
 'one historical exact producer still requires backup or SKIP3. Both literature HTML reports remain '
 'withheld from Pages. Read each analysis-status.json and WARNINGS.md.')
NOTICE=('<aside id="gsea-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
 'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>Native Broad GSEA and 28 reviewed ranked-enrichment roles</h2>'
 '<p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)
 +'</p><p>'+html.escape(SCOPE+' '+NOTE)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'
 +GUIDE+'">Source roles, exact scope and recovery</a>.</p></aside>')

encoded=p.encoded
require=p.require

def registry():
    from edger_catalog.model import registry as previous
    rows=previous();require(len(rows)==485,'Different effective definition population')
    row=next(r for r in rows if r['id']=='gsea');require(row['current_coverage']=='method-overlap','Different preceding GSEA definition')
    row.update(current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE)
    return rows

def validate(before,primary,qualification):
    require(set(before)==set(META),'Exactly eight preceding metadata documents required');p.validate(primary)
    require(qualification['status']=='PASS_SAVED_NATIVE_GSEA_GLOBAL_HANDOVER' and qualification['JVMs']==3
        and qualification['original_fields_per_JVM']==668739 and qualification['unchanged_predecessor_payloads']==23521
        and qualification['focused_JUnit_tests']==8 and qualification['inverse_refusals']==7
        and qualification['global_RUN_VERIFY_REPLAY'] and qualification['previous_captures_VERIFY_REPLAY']
        and qualification['new_catalog_credit']==0,'Exact native global qualification required')
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);reviews=json.loads(before[META[5]])
    require(len(papers)==250 and len(inventory)==446 and sum(len(x['tools']) for x in papers)==2705 and len(reviews['reviews'])==1520,'Different preceding catalog population')
    require(not any(r['tool_id']=='gsea' for r in reviews['reviews']),'Prior GSEA reviews must not be overwritten')
    row=next(t for t in inventory if t['id']=='gsea');require(row['implementation_status']==row['current_coverage']=='method-overlap' and row['paper_count']==19,'Different/already admitted GSEA family')
    require([x['paper_id'] for x in papers if any(t['tool_id']=='gsea' for t in x['tools'])]==ORIGINAL,'Different original GSEA observation set')
    identities={r['paper_id']:r for r in primary['source_identities']}
    for paper in papers:
        require(all(paper[k]==v for k,v in identities[paper['paper_id']].items()),'Frozen paper identity differs: '+paper['paper_id'])
        require(sum(t['tool_id']=='gsea' for t in paper['tools'])==(1 if paper['paper_id'] in ORIGINAL else 0),'Duplicate/unexpected GSEA observation')
    return primary['roles']

def transition(before,primary,qualification):
    roles=validate(before,primary,qualification);papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before[META[5]])
    for role in roles:
        paper=next(x for x in papers if x['paper_id']==role['paper_id']);anchor=role['primary_evidence'];pid=role['paper_id']
        if pid in ADDITIONS:
            use=dict(tool_id='gsea',tool='GSEA',matched_text=anchor['matched_tokens'][0]['literal'],extraction_score=0);paper['tools'].append(use)
        else:use=next(t for t in paper['tools'] if t['tool_id']=='gsea')
        caution=role['required_caution']+' Source-provider options and whole author workflows remain separately unqualified.'
        versions=role['matching_Broad_source_profiles'];provider_labels=role['reported_provider_versions']
        text=role['usage_summary']
        if provider_labels and role['reported_provider']!='Broad GSEA':
            text+=' Reported provider label: '+', '.join(v['provider']+' '+v['literal'] for v in provider_labels)+'.'
        use.update(analysis_role='Ranked gene-set enrichment; reported provider: '+role['reported_provider'],
            evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],evidence_paragraph_sha256=anchor['paragraph_sha256'],
            evidence_locator=paper['fulltext_url']+' (section: '+anchor['section']+')',evidence_selection='individually-reviewed-source-role',
            review_status='usage-summary-reviewed; paper-wide-completeness-review-open',usage_context=role['evidence_kind'],
            use_summary=text,implementation_scope_note=caution,version_reported_near_name=versions[0] if len(versions)==1 else None,
            source_role_qualification=copy.deepcopy(role))
        pointers=copy.deepcopy(role['supporting_evidence'])
        if pointers:use['supporting_evidence']=pointers
        else:use.pop('supporting_evidence',None)
        record={k:paper[k] for k in ['pmcid','doi']};record.update(tool_id='gsea',evidence_paragraph_sha256=anchor['paragraph_sha256'],use_summary=text,implementation_scope_note=caution)
        if len(versions)==1:record['version_reported_near_name']=versions[0]
        if pointers:record['supporting_evidence']=pointers
        reviews['reviews'].append(record)
    row=next(t for t in inventory if t['id']=='gsea')
    row.update(previous_method_overlap_source=row['existing_source'],current_coverage='native-subset-validated',implementation_status='native-subset-validated',
        existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE+' '+NOTE,implementation_scope=SCOPE+' '+NOTE,
        validation_note='Original-source Broad prepared-data components and global RUN/VERIFY/REPLAY qualified on Windows21/25/Linux21; other providers and complete author workflows remain unqualified. See the source-role crosswalk and exact proof.',
        paper_count=28,papers=[x['paper_id'] for x in papers if any(t['tool_id']=='gsea' for t in x['tools'])],
        source_roles_reviewed=28,source_role_coverage=COVERAGE.rsplit('/',1)[-1],current_documentation=GUIDE,
        workflow_qualification='../gsea-global-development/QUALIFICATION.json',source_build_required=True,released_binary_contains_workflow_adapter=False,
        public_CLI_available=True,global_CLI_dispatch_available=True,qualified_component_entrypoints=['broad-gsea-tools --request RUN/VERIFY/REPLAY','org.jlinalg.cli.BroadGseaWorkflowCli'],
        paper_role_qualification={r['paper_id']:copy.deepcopy(r) for r in roles},native_family_credit_added=1,upstream_family_credit_added=0,
        new_scoped_integration_credit=1,statistical_computation='native-Broad-source-profiled-prepared-data-GSEA',whole_tool_qualified=False,whole_paper_reproduced=False)
    counts=dict(Counter(t['implementation_status'] for t in inventory));require(counts['native-subset-validated']==26 and counts['upstream-adapter-validated']==114 and counts['method-overlap']==35 and counts['missing']==259,'False family credit')
    summary['implementation_status_counts']=counts;summary['paper_tool_observations']=2714
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[5],reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before[META[6]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Embedded preceding report data differs')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    for name,text in [(META[6],page),(META[7],before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE)==1 and 'id="gsea-catalog-notice"' not in text,'Different/already admitted report notice')
        text=text.replace(PREVIOUS_NOTICE,'<details><summary>Previous edgeR checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1);body=re.search(r'<body[^>]*>',text,re.I);require(body is not None,'Report body missing')
        after[name]=(text[:body.end()]+NOTICE+text[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
        after[name]=('# '+title+'\n\n## Native Broad GSEA and 28 reviewed ranked-enrichment roles\n\n**Warnings and required actions:** '+ACTION+'\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\nRead [the source crosswalk and exact scope](../GSEA_CATALOG_INTEGRATION.md).\n\n<details><summary>Before GSEA admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after

def check(before,after,primary,qualification):
    require(after==transition(before,primary,qualification),'Different source-bound deterministic transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Paper identity changed')
        require([t for t in a['tools'] if t['tool_id']!='gsea']==[t for t in b['tools'] if t['tool_id']!='gsea'],'Unrelated observation changed')
        require([t['tool_id'] for t in b['tools']]==[t['tool_id'] for t in a['tools']]+(['gsea'] if a['paper_id'] in ADDITIONS else []),'Unreviewed addition/removal/order change')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']!='gsea']==[r for r in b if r['id']!='gsea'],'Other 445 inventory rows changed')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]]);require(b['reviews'][:1520]==a['reviews'] and len(b['reviews'])==1548 and all(r['tool_id']=='gsea' for r in b['reviews'][1520:]),'Old reviews or append scope changed')
    require({k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'},'Other review metadata changed')
    require(len({(r['pmcid'],r['tool_id']) for r in b['reviews']})==1548,'Duplicate review identity')
    counts=Counter(t['tool_id'] for paper in new for t in paper['tools']);require(len(counts)==446 and sum(counts.values())==2714,'Different named/observation totals')
    for row in json.loads(after[META[1]]):require(row['paper_count']==counts[row['id']] and row['papers']==[x['paper_id'] for x in new if any(t['tool_id']==row['id'] for t in x['tools'])],'Inventory membership differs')
    return dict(papers=250,named_tools=446,registry_definitions=485,observations=2714,reviewed_uses=1548,pending_uses=1166,
        existing_reviews_retained=1520,new_review_records=28,positive_gsea_roles=28,observations_added=9,observations_removed=0,
        integrations=140,native_integrations=26,upstream_integrations=114,including_existing_adapters=143,missing=259,method_overlap=35,
        new_scoped_integration_credit=1,native_family_credit_added=1,upstream_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
