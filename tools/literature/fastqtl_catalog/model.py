"""One exact prepared upstream family and five source reviews; no historical edition substitution."""
from collections import Counter
import copy,html,json,re
from cytometry_catalog import model as previous
from fastqtl_catalog import primary as p

ROOT,META,DATA=p.ROOT,previous.META,previous.DATA
REVIEWED=previous.REVIEWED;require=p.require;encoded=p.encoded
PROGRAM='src/main/java/org/jlinalg/cli/FastqtlToolsCli.java'
GUIDE='docs/literature/FASTQTL_CATALOG_WORKFLOWS.md'
COVERAGE=DATA+'/fastqtl-source-role-coverage.json';PROOF=DATA+'/fastqtl-catalog-validation.json'
CHECKPOINT='docs/literature/fastqtl-workflow-checkpoint/STATUS.json'
SCOPE=('Prepared nominal and fixed/adaptive permutation passes through the source-built Java fastqtl-tools adapter to pinned Ubuntu 2.184+v7+dfsg-4build3 (v2.184_gtex), including its declared distribution patches. '
 'Linux x86_64/WSL execution; one-chromosome biallelic-SNV BGZF VCF/BED with paired TBI checks, exact sample-ID sets and optional finite numeric covariates. '
 'Original DS-before-GT selection, float arithmetic, mean imputation, normal transformation, covariate correction, rounded-dosage MAF, empirical and beta-adjusted P values remain upstream. '
 'Fresh original VERIFY/REPLAY and read-only derived-audit inspection are separate. No historical 2.165 equivalence, indel/SV/multiallelic support, automatic preprocessing/PEER/TSS/peak-center construction, FDR/q-values, conditional mapping, whole-tool/paper or biological qualification.')
NOTE=('Five roles preserve raw near-name versions (all unreported). P019 selects the yield closest to 95% of maximum, not a first-above-threshold rule; ties and yield unit remain unresolved. '
 'P157 reports 2.165 in Code availability, repeated in Associated Data; Ubuntu 2.184/GTEx is not substituted. P170 mapping Q<0.05 differs from profile prioritization Q<0.10; nominal-Q wording remains literal. '
 'P187 uses distinct peak-center and TSS windows, covariates and BH adjustment of beta P values. P212 reuses public islet results without establishing a new local FastQTL run; APEX conditional analyses concern other tissues.')
ACTION=('SKIP3: dependent analysis skipped when its exact required edition, permitted runtime, original prepared inputs or qualified capability is unavailable. '
 'Install/stage the pinned Linux/WSL runtime only by an allowed method; if installation is prohibited, keep the analysis skipped. Exact 2.165 remains unqualified. '
 'FAIL2: no complete result; preserve raw/partial/nonfinite/failed output, fix the named cause and retry NEW output. Read WARNINGS.md, FAILURE.md, result-audit.tsv and scientific-audit.json. '
 'Do not repair IDs, replace beta P with FDR, infer coordinates or double-correct residualized phenotypes. Printed nonfinite statistics remain unadmitted; no-cis rows mean no result. '
 'Keep exact historical producer JARs and physical source contexts; never refresh old hashes. Current release assets lack this source workflow. Cell Ranger remains excluded.')
CURRENT=('250 papers, 446 labels, 485 provider definitions and 2,724 observations; 1,760 reviewed/964 pending uses. '
 '159 scoped families (30 native + 129 upstream), 162 including three existing adapters; 254 missing and 18 method-overlap. '
 'This admission adds one bounded FastQTL upstream family and five source reviews. Historical 2.165 and complete paper analyses remain unqualified; the full goal is unfinished.')
NOTICE=('<aside id="fastqtl-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>FastQTL: prepared upstream workflows and five paper uses</h2><p>'+html.escape(CURRENT)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p>'+html.escape(NOTE)+'</p>'
 '<p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Commands, source roles and remaining work</a></p></aside>')

def qualification():
    state=json.loads((ROOT/CHECKPOINT).read_bytes());after=json.loads((ROOT/'docs/literature/fastqtl-workflow-checkpoint/PRESERVATION.json').read_bytes());r=after['readback']
    require(state['status']==r['status']=='PASS_PREPARED_FASTQTL_RUNTIME_PENDING_CATALOG' and not state['catalog_admitted'] and not state['historical_2_165_qualified'],'Expanded workflow qualification')
    require((r['synthetic_cases'],r['fresh_refits_recorded'],r['actual_refusal_controls'],r['independent_R_statistics'],r['Java_tests'],r['private_files_checked'],r['fresh_primary_XML_checked'])==(8,2,15,240,30,9272,250),'Incomplete actual workflow evidence')
    require(state['producer']==dict(bytes=199548555,sha256='f9f45eaeaf93d8b4dfddf07e7cb5b96dd9d8b2d47a929aa33541380146f75d1d'),'Changed exact producer')
    return dict(status='EIGHT_PREPARED_FASTQTL_CASES_WITH_HISTORICAL_PAPER_ANALYSES_PENDING',checkpoint=CHECKPOINT,checkpoint_identity=p.pin((ROOT/CHECKPOINT).read_bytes()),producer=state['producer'],synthetic_cases=8,fresh_original_refits=2,actual_refusal_controls=15,independent_R_statistics=240,selected_Java_tests=30,native_Windows_upstream_fits=0,Linux_upstream_execution=True,paired_index_audit=True,derived_saved_audit_recomputed=True,historical_2_165_qualified=False,catalog_admitted=False,whole_goal_complete=False)
def applied(role):
    result=copy.deepcopy({k:v for k,v in role.items() if k!='original_observation'});result.update(source_role_review_applied=True,catalog_admitted=True);return result
def registry():
    rows=previous.registry();target=next(r for r in rows if r['id']=='fastqtl');target.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE);return rows
def validate(before,primary,q):
    require(set(before)==set(META) and primary==p.frozen() and q==qualification(),'Changed source interpretation or workflow qualification');papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    require(len(papers)==250 and len(inventory)==446 and sum(len(r['tools']) for r in papers)==2724 and len(ledger['reviews'])==1755,'Changed predecessor population');require(summary['integration_family_counts']==dict(scoped=158,native=30,upstream=128,including_existing_adapters=161,validated_alias_labels_excluded_from_extra_credit=3),'Changed predecessor families')
    target=next(r for r in inventory if r['id']=='fastqtl');require(target['implementation_status']==target['current_coverage']=='method-overlap' and target['papers']==p.POSITIVE and target['paper_count']==5 and not any(r['tool_id']=='fastqtl' for r in ledger['reviews']),'Changed target baseline')
    for role in primary['roles']:require([u for u in next(r for r in papers if r['paper_id']==role['paper_id'])['tools'] if u['tool_id']=='fastqtl']==[role['original_observation']],'Changed raw FastQTL observation')
def transition(before,primary,q):
    validate(before,primary,q);papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    for role in primary['roles']:
        paper=next(r for r in papers if r['paper_id']==role['paper_id']);use=next(u for u in paper['tools'] if u['tool_id']=='fastqtl');pointers=copy.deepcopy(use.get('supporting_evidence',[]))
        for a in role['primary_anchors']:
            if a['paragraph_index']!=use['evidence_paragraph_index'] and not any((b.get('paragraph_index'),b.get('paragraph_sha256'))==(a['paragraph_index'],a['paragraph_sha256']) for b in pointers):pointers.append(copy.deepcopy(a))
        scope=role['remaining_scope']+' Available bounded implementation: '+SCOPE
        use.update(use_summary=role['use_summary'],implementation_scope_note=scope,review_status=REVIEWED,evidence_selection='individually-reviewed-source-role',supporting_evidence=pointers,source_role_qualification=applied(role))
        ledger['reviews'].append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='fastqtl',evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['use_summary'],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],supporting_evidence=copy.deepcopy(pointers),reported_version_contexts=copy.deepcopy(role['reported_version_contexts']),reported_settings=copy.deepcopy(role['reported_settings']),historical_analysis_status=role['historical_analysis_status'],source_role_qualification=applied(role)))
    target=next(r for r in inventory if r['id']=='fastqtl');old_source=target['existing_source'];target.update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,prior_method_overlap_source=old_source,coverage_note=SCOPE,implementation_scope=SCOPE,current_documentation=GUIDE,current_qualification='../fastqtl-workflow-checkpoint/STATUS.json',upstream_runtime_adapter_required=True,native_component_available=True,native_component_family_credit=0,public_CLI_available=True,global_CLI_dispatch_available=True,source_build_required=True,released_binary_contains_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,new_native_family_credit=0,new_upstream_family_credit=1,source_roles_reviewed=5,source_role_coverage=COVERAGE.rsplit('/',1)[-1],paper_role_qualification={r['paper_id']:applied(r) for r in primary['roles']},current_paper_role_limits=NOTE,validation_note='Eight prepared cases match separate direct-original calls byte for byte; two fresh refits, 240 independent R statistics, 15 actual refusal controls and 30 selected Java tests. No original paper cohort or 2.165 equivalence is established.')
    summary['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inventory));summary['integration_family_counts']=dict(scoped=159,native=30,upstream=129,including_existing_adapters=162,validated_alias_labels_excluded_from_extra_credit=3);after=dict(before)
    for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    for name in META[6:]:
        page=before[name].decode();require(page.count(previous.NOTICE)==1 and 'id="fastqtl-catalog-notice"' not in page,'Changed prior notice')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed embedded predecessor catalog');payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
            require(page.count('${cytometryVersion(t)}')==1 and page.count('function renderContent(){')==1,'Changed inherited renderer')
            helper=("function fastqtlVersion(t){if(t.tool_id!=='fastqtl'||!t.source_role_qualification)return cytometryVersion(t);const q=t.source_role_qualification;return '<br><span class=\"fastqtl-version\" style=\"overflow-wrap:anywhere\">Role: '+esc(q.role_kind)+'; Reported edition: '+esc(q.reported_version)+'; '+esc(q.historical_analysis_status)+'; '+esc(q.remaining_scope)+'</span>';}\n")
            page=page.replace('${cytometryVersion(t)}','${fastqtlVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        body=re.search(r'<body[^>]*>',page,re.I);require(body is not None,'Missing body');marker=page.find('<site-header ',body.end());require(marker>=body.end() and page.count('<site-header ')==1,'Changed historical notice boundary');history='<details id="fastqtl-prior-catalog"><summary>Before FastQTL admission (historical)</summary>'+page[body.end():marker]+'</details>';after[name]=(page[:body.end()]+NOTICE+history+page[marker:]).encode()
    prefix=('# Current FastQTL catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [workflow and source-role guide](../FASTQTL_CATALOG_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in META[3:5]:after[name]=prefix+before[name]
    return after
def check(before,after,primary,q):
    require(after==transition(before,primary,q),'Changed deterministic source-bound transition');old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity');require([u for u in a['tools'] if u['tool_id']!='fastqtl']==[u for u in b['tools'] if u['tool_id']!='fastqtl'],'Changed unrelated observation')
        for x in a['tools']:
            if x['tool_id']=='fastqtl':
                y=next(u for u in b['tools'] if u['tool_id']=='fastqtl')
                for key in ('tool_id','tool','analysis_role','matched_text','extraction_score','usage_context','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator','version_reported_near_name'):require(x[key]==y[key],'Changed raw source field: '+key)
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']!='fastqtl']==[r for r in b if r['id']!='fastqtl'],'Changed unrelated inventory');oldledger,newledger=json.loads(before[META[5]]),json.loads(after[META[5]]);require(newledger['reviews'][:1755]==oldledger['reviews'] and {k:v for k,v in oldledger.items() if k!='reviews'}=={k:v for k,v in newledger.items() if k!='reviews'},'Changed prior cumulative ledger')
    require(len(newledger['reviews'])==len({(r['pmcid'],r['tool_id']) for r in newledger['reviews']})==1760,'Wrong unique review count');markers={REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open'};require({(r['pmcid'],u['tool_id']) for r in new for u in r['tools'] if u['review_status'] in markers}=={(r['pmcid'],r['tool_id']) for r in newledger['reviews']},'Ledger/observation mismatch');counts=Counter(r['implementation_status'] for r in b);require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['method-overlap'],counts['missing'])==(30,129,18,254),'Wrong scoped family counts');require(sum(len(r['tools']) for r in new)==2724,'Changed observation population')
    return dict(papers=250,labels=446,provider_definitions=485,observations=2724,reviewed_uses=1760,pending_uses=964,source_roles_reviewed=5,new_reviews=5,unchanged_prior_reviews=1755,unchanged_unrelated_observations=2719,unchanged_unrelated_inventory_rows=445,scoped_families=159,native=30,upstream=129,including_existing_adapters=162,method_overlap=18,missing=254,new_native_family_credit=0,new_upstream_family_credit=1,added_positive_observations=0,removed_positive_observations=0,historical_2_165_qualified=False,local_P212_FastQTL_run_established=False,whole_paper_reproduced=False,whole_goal_complete=False)
