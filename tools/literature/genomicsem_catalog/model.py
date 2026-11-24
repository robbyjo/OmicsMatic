"""One original GenomicSEM family and five source reviews; no author-edition substitution."""
from collections import Counter
import copy,html,json,re
from fastqtl_catalog import model as previous
from genomicsem_catalog import primary as p

ROOT,META,DATA=p.ROOT,previous.META,previous.DATA
REVIEWED=previous.REVIEWED;require=p.require;encoded=p.encoded
PROGRAM='src/main/java/org/jlinalg/cli/GenomicsemToolsCli.java'
GUIDE='docs/literature/GENOMICSEM_CATALOG_WORKFLOWS.md'
COVERAGE=DATA+'/genomicsem-source-role-coverage.json';PROOF=DATA+'/genomicsem-catalog-validation.json'
CHECKPOINT='docs/literature/genomicsem-preparation-checkpoint/STATUS.json'
SCOPE=('Original pinned GenomicSEM preparation and prepared fitting through source-built genomicsem-prep and genomicsem-tools Java adapters: munge, full multivariate ldsc, sumstats, read_fusion, s_ldsc, enrich, commonfactor, usermodel, commonfactorGWAS and userGWAS including TWAS mode. '
 'Revision da95d431a4709693d07eae55e172ade55526948b (DESCRIPTION 0.0.5, README 0.0.5c), private Windows x86_64 R4.6.1/Matrix1.7-5/lavaan0.6-19 and independently pinned optional Rmpfr/GMP. '
 'One multivariate call supplies complete joint V; original source transformations, typed fields, warnings and unavailable values remain retained. Fresh VERIFY/REPLAY recompute original calls; saved inspection reads typed exports and never decodes opaque RDS or independently refits the optimizer. '
 'No historical paper-edition equivalence, full package/optimizer/convergence certification, original paper reanalysis or whole-paper/biological qualification.')
NOTE=('P035 reports literal 0.5.0, not current DESCRIPTION 0.0.5; EFA is separately stats::factanal, and manuscript GC wording does not establish exact historical arguments. '
 'P125 describes paired-subtype conditional regressions rather than a generic single-factor fit. P131 preserves 0.0.5 contexts for higher-order models/TSEM without borrowing FUSION or ACAT versions. '
 'P140: 0.0.3f/R4.3.2 Cholesky subtraction is not replaced by current source and is distinct from PCA-based GWAS. P150: 0.0.5 parallel-analysis wording does not establish use of the newer paLDSC API. '
 'Raw near-name versions stay null for the first four observations and0.0.5 for P150. Historical settings and separate methods remain explicit in each role.')
ACTION=('SKIP3: dependent analysis skipped when an exact permitted runtime, input, add-on or qualified edition is unavailable. Install/stage dependencies only by a method allowed in your environment; if installation is forbidden, retain the skip. '
 'FAIL2: no complete admitted result. Preserve partial/original/nonfinite output, read FAILURE.md, WARNINGS.md, scientific-audit.json, row/annotation status tables and original logs; resolve the named cause and retry NEW output. '
 'Never silently replace a historical edition with current software or a native overlap, infer study ancestry/build/prevalences, repair identifiers, clip source endpoints or assemble full joint V from pairwise fits. '
 'Original enrichment P values are one-sided upper tails for enrichment above1. Original import/build warnings remain visible; pinned Rmpfr/GMP BuiltR4.6.2 arithmetic is narrowly checked under unchanged R4.6.1. '
 'Exact producer JARs and original source contexts must be retained; never refresh old hashes. Current release assets lack this source-built workflow. Cell Ranger remains excluded.')
CURRENT=('250 papers, 446 labels, 485 provider definitions and 2,724 observations; 1,765 reviewed/959 pending uses. '
 '160 scoped families (30 native + 130 upstream), 163 including three existing adapters; 254 missing and 17 method-overlap. '
 'This admission adds one GenomicSEM upstream family and five source reviews. Exact historical editions and whole-paper analyses remain unqualified; the full goal is unfinished.')
VALIDATION_NOTE=('Preparation: 24 exact original-object comparisons, 22 admitted and two original returns rejected by explicit policy; 44 focused Java tests on each platform, 20 actual refusals, 3 fresh executions, 6 documented runs and 4 downstream fits. '
 'The prior prepared-fitting milestone separately has 31 direct-original comparisons with its preserved earlier producer. Full saved readers and 250 primary XML checks pass in immutable source contexts. No historical edition or original cohort analysis is established.')
NOTICE=('<aside id="genomicsem-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>GenomicSEM: original workflows and five paper uses</h2><p>'+html.escape(CURRENT)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p>'+html.escape(NOTE)+'</p>'
 '<p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Commands, source roles and remaining work</a></p></aside>')

def qualification():
    state=json.loads((ROOT/CHECKPOINT).read_bytes());done=json.loads((ROOT/'docs/literature/genomicsem-preparation-checkpoint/COMPLETION.json').read_bytes());v=json.loads((ROOT/'docs/literature/genomicsem-preparation-checkpoint/VALIDATION.json').read_bytes())
    require(state['status']=='PASS_SCOPED_ORIGINAL_GENOMICSEM_PREPARATION_PENDING_CATALOG'and not state['catalog_admitted']and not state['historical_paper_editions_qualified'],'Expanded preparation qualification')
    require(state['producer']==dict(bytes=199951327,sha256='38fd28150d960f78294d28a6ce9064f2c5c3aac0db20c151ff0a64f092a2d6c1'),'Changed exact producer')
    require(done['status']=='PASS_PUBLISHED_PREPARATION_AND_POST_CLEANUP_CHECKS'and done['saved_captures_rechecked']==35 and done['private_evidence_files_checked']==5875 and done['primary_XML_rechecked']==250,'Incomplete retained preparation proof')
    require((v['original_object_comparisons'],v['admitted_original_object_comparisons'],v['policy_rejected_original_object_comparisons'],v['Windows_Java_tests'],v['Linux_Java_tests'],v['fresh_original_executions'],len(v['refusals']),v['documented_example_runs'],v['downstream_fitting_operations'])==(24,22,2,44,44,3,20,6,4),'Incomplete actual preparation scope')
    fitting=json.loads((ROOT/'docs/literature/genomicsem-workflow-checkpoint/STATUS.json').read_bytes());fv=json.loads((ROOT/'docs/literature/genomicsem-workflow-checkpoint/VALIDATION.json').read_bytes());require(len(fv['cases'])==31 and all(x['original_R_object_identical_including_attributes']for x in fv['cases']),'Missing separately qualified earlier fits')
    return dict(status='SCOPED_ORIGINAL_PREPARATION_AND_FITTING_WITH_HISTORICAL_ANALYSES_PENDING',checkpoint=CHECKPOINT,checkpoint_identity=p.pin((ROOT/CHECKPOINT).read_bytes()),producer=state['producer'],original_preparation_comparisons=24,admitted_preparation_comparisons=22,policy_rejected_source_returns=2,prior_prepared_fitting_comparisons=31,prior_prepared_fitting_producer=fitting['producer'],fresh_original_preparations=3,actual_refusal_controls=20,Java_tests_per_platform=44,saved_captures_checked=35,source_XML_checked=250,Windows_original_execution=True,Linux_saved_inspection=True,Linux_original_execution_exit=3,full_joint_V_from_one_original_call=True,opaque_RDS_interpreted=False,historical_paper_editions_qualified=False,original_paper_analyses_reexecuted=False,catalog_admitted=False,whole_goal_complete=False)

def applied(role):
    result=copy.deepcopy({k:v for k,v in role.items() if k!='original_observation'});result.update(source_role_review_applied=True,catalog_admitted=True);return result
def registry():
    rows=previous.registry();target=next(r for r in rows if r['id']=='genomicsem');target.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE);return rows
def validate(before,primary,q):
    require(set(before)==set(META) and primary==p.frozen() and q==qualification(),'Changed source interpretation or workflow qualification');papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    require(len(papers)==250 and len(inventory)==446 and sum(len(r['tools']) for r in papers)==2724 and len(ledger['reviews'])==1760,'Changed predecessor population');require(summary['integration_family_counts']==dict(scoped=159,native=30,upstream=129,including_existing_adapters=162,validated_alias_labels_excluded_from_extra_credit=3),'Changed predecessor families')
    target=next(r for r in inventory if r['id']=='genomicsem');require(target['implementation_status']==target['current_coverage']=='method-overlap' and target['papers']==p.POSITIVE and target['paper_count']==5 and not any(r['tool_id']=='genomicsem' for r in ledger['reviews']),'Changed target baseline')
    for role in primary['roles']:require([u for u in next(r for r in papers if r['paper_id']==role['paper_id'])['tools'] if u['tool_id']=='genomicsem']==[role['original_observation']],'Changed raw GenomicSEM observation')
def transition(before,primary,q):
    validate(before,primary,q);papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    for role in primary['roles']:
        paper=next(r for r in papers if r['paper_id']==role['paper_id']);use=next(u for u in paper['tools'] if u['tool_id']=='genomicsem');pointers=copy.deepcopy(use.get('supporting_evidence',[]))
        for a in role['primary_anchors']:
            if a['paragraph_index']!=use['evidence_paragraph_index'] and not any((b.get('paragraph_index'),b.get('paragraph_sha256'))==(a['paragraph_index'],a['paragraph_sha256']) for b in pointers):pointers.append(copy.deepcopy(a))
        scope=role['remaining_scope']+' Available bounded implementation: '+SCOPE
        use.update(use_summary=role['use_summary'],implementation_scope_note=scope,review_status=REVIEWED,evidence_selection='individually-reviewed-source-role',supporting_evidence=pointers,source_role_qualification=applied(role))
        ledger['reviews'].append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='genomicsem',evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['use_summary'],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],supporting_evidence=copy.deepcopy(pointers),reported_version_contexts=copy.deepcopy(role['reported_version_contexts']),reported_settings=copy.deepcopy(role['reported_settings']),historical_analysis_status=role['historical_analysis_status'],source_role_qualification=applied(role)))
    target=next(r for r in inventory if r['id']=='genomicsem');old_source=target['existing_source'];target.update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,preparation_source='src/main/java/org/jlinalg/cli/GenomicsemPrepToolsCli.java',prior_method_overlap_source=old_source,coverage_note=SCOPE+' '+NOTE,implementation_scope=SCOPE+' '+NOTE,current_documentation=GUIDE,current_qualification='../genomicsem-preparation-checkpoint/STATUS.json',upstream_runtime_adapter_required=True,native_component_available=True,native_component_family_credit=0,public_CLI_available=True,global_CLI_dispatch_available=True,source_build_required=True,released_binary_contains_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,new_native_family_credit=0,new_upstream_family_credit=1,source_roles_reviewed=5,source_role_coverage=COVERAGE.rsplit('/',1)[-1],paper_role_qualification={r['paper_id']:applied(r) for r in primary['roles']},current_paper_role_limits=NOTE,validation_note=VALIDATION_NOTE)
    summary['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inventory));summary['integration_family_counts']=dict(scoped=160,native=30,upstream=130,including_existing_adapters=163,validated_alias_labels_excluded_from_extra_credit=3);after=dict(before)
    for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    for name in META[6:]:
        page=before[name].decode();require(page.count(previous.NOTICE)==1 and 'id="genomicsem-catalog-notice"' not in page,'Changed prior notice')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed embedded predecessor catalog');payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
            require(page.count('${fastqtlVersion(t)}')==1 and page.count('function renderContent(){')==1,'Changed inherited renderer')
            helper=("function genomicsemVersion(t){if(t.tool_id!=='genomicsem'||!t.source_role_qualification)return fastqtlVersion(t);const q=t.source_role_qualification;return '<br><span class=\"genomicsem-version\" style=\"overflow-wrap:anywhere\">Role: '+esc(q.role_kind)+'; Reported edition: '+esc(q.reported_version)+'; '+esc(q.historical_analysis_status)+'; '+esc(q.remaining_scope)+'</span>';}\n")
            page=page.replace('${fastqtlVersion(t)}','${genomicsemVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        body=re.search(r'<body[^>]*>',page,re.I);require(body is not None,'Missing body');marker=page.find('<site-header ',body.end());require(marker>=body.end() and page.count('<site-header ')==1,'Changed historical notice boundary');history='<details id="genomicsem-prior-catalog"><summary>Before GenomicSEM admission (historical)</summary>'+page[body.end():marker]+'</details>';after[name]=(page[:body.end()]+NOTICE+history+page[marker:]).encode()
    prefix=('# Current GenomicSEM catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [workflow and source-role guide](../GENOMICSEM_CATALOG_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in META[3:5]:after[name]=prefix+before[name]
    return after
def check(before,after,primary,q):
    require(after==transition(before,primary,q),'Changed deterministic source-bound transition');old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity');require([u for u in a['tools'] if u['tool_id']!='genomicsem']==[u for u in b['tools'] if u['tool_id']!='genomicsem'],'Changed unrelated observation')
        for x in a['tools']:
            if x['tool_id']=='genomicsem':
                y=next(u for u in b['tools'] if u['tool_id']=='genomicsem')
                for key in ('tool_id','tool','analysis_role','matched_text','extraction_score','usage_context','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator','version_reported_near_name'):require(x[key]==y[key],'Changed raw source field: '+key)
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']!='genomicsem']==[r for r in b if r['id']!='genomicsem'],'Changed unrelated inventory');oldledger,newledger=json.loads(before[META[5]]),json.loads(after[META[5]]);require(newledger['reviews'][:1760]==oldledger['reviews'] and {k:v for k,v in oldledger.items() if k!='reviews'}=={k:v for k,v in newledger.items() if k!='reviews'},'Changed prior cumulative ledger')
    require(len(newledger['reviews'])==len({(r['pmcid'],r['tool_id']) for r in newledger['reviews']})==1765,'Wrong unique review count');markers={REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open'};require({(r['pmcid'],u['tool_id']) for r in new for u in r['tools'] if u['review_status'] in markers}=={(r['pmcid'],r['tool_id']) for r in newledger['reviews']},'Ledger/observation mismatch');counts=Counter(r['implementation_status'] for r in b);require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['method-overlap'],counts['missing'])==(30,130,17,254),'Wrong scoped family counts');require(sum(len(r['tools']) for r in new)==2724,'Changed observation population')
    return dict(papers=250,labels=446,provider_definitions=485,observations=2724,reviewed_uses=1765,pending_uses=959,source_roles_reviewed=5,new_reviews=5,unchanged_prior_reviews=1760,unchanged_unrelated_observations=2719,unchanged_unrelated_inventory_rows=445,scoped_families=160,native=30,upstream=130,including_existing_adapters=163,method_overlap=17,missing=254,new_native_family_credit=0,new_upstream_family_credit=1,added_positive_observations=0,removed_positive_observations=0,historical_paper_editions_qualified=False,original_paper_analyses_reexecuted=False,whole_paper_reproduced=False,whole_goal_complete=False)
