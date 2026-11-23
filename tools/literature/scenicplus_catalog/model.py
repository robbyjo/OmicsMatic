"""One bounded upstream family, six reviewed roles and one source-bound addition."""
from collections import Counter
import copy,html,json,re
from scenic_catalog import model as previous
from scenicplus_catalog import primary as p

ROOT,META,DATA=p.ROOT,previous.META,previous.DATA
TARGET='scenicplus';PROGRAM='src/main/java/org/jlinalg/cli/ScenicPlusCommandCli.java'
GUIDE='docs/literature/SCENICPLUS_CATALOG_WORKFLOWS.md'
COVERAGE=DATA+'/scenicplus-source-role-coverage.json';PROOF=DATA+'/scenicplus-catalog-validation.json'
QUALIFICATION='docs/literature/scenicplus-global-checkpoint-20261123/STATUS.json'
REVIEWED=previous.REVIEWED;require=p.require;encoded=p.encoded
SCOPE=('Pinned original SCENIC+ v1.0a2 private, unmodified internal-academic Linux Python runtime; source-built Java scenicplus-tools adapter. '
 'Four explicit profiles support unpaired same-label random metacells, prepared cisTarget/DEM and cistromes, region-to-gene GBM/correlation, '
 'and prepared TF2G/R2G/eGRN/activity inference. Actual main-command RUN, fresh VERIFY, REPLAY and metadata-only inspection are tested. '
 'Inputs, references, normalization and search-space candidates are explicit. Original nonfinite values, warnings, omitted statistics and '
 'source failures remain visible. Paired preparation, annotation/search-space generation and genome scanning are outside these profiles. '
 'No whole-tool, historical/unknown-edition, author-environment, whole-paper, biological, authenticity or OS-containment qualification.')
NOTE=('Six roles are reviewed individually. P008 uses random same-label unpaired metacells and a reported 150 kb window. '
 'P021 compares published eRegulons, with no local SCENIC+ fit established. P047 uses TF annotations and motif-cluster resources; '
 'P047/P061 ClusterBuster scanning remains SKIP3. P061 also uses cisTarget NES > 3 and leading-edge assignment. '
 'P094 uses externally prepared SEACells metacells (target 75 cells, 15 initial eigenvalues), not random metacells, with a 2 Mbp upstream/downstream search space. '
 'P153 is a benchmark with pinned author R2G calls; author analysis v1.2 is not a SCENIC+ edition. Both 1 Mbp/150 kb author branches remain distinct. '
 'SCENIC+ editions and author seeds remain unreported. Source-role review is distinct from successful reproduction of each paper analysis.')
ACTION=('SKIP3: dependent analysis skipped. Supply/install the named permitted exact runtime, input/reference and producer JAR, or retain the skip. '
 'ClusterBuster scanning needs confirmed permission for its exact runtime; prepared motif enrichment is a separate supported operation. '
 'Exact author-edition reproduction remains skipped where edition/settings/inputs are unavailable. '
 'FAIL2: no complete result. Preserve raw, partial, failed and undefined evidence, fix the named cause and retry NEW output. '
 'Read analysis-status.json, WARNINGS.md, source-warnings.json and field ledgers; require full VERIFY for fresh scientific comparison. '
 'Keep NaN/Inf and eligibility flags. Original masked-array/empty-cistrome failures remain failures; unbalanced DEM may ignore a cap, so inspect actual counts. '
 'Keep the accepted unmodified academic runtime, trusted objects and source-bearing traces private; check permission before transfers or WSL exports. '
 'Use exact producer JARs for old captures; do not refresh historical hashes. Cell Ranger remains excluded.')
CURRENT=('250 papers, 446 labels, 485 provider definitions and 2,723 positive observations; 1,707 reviewed/1,016 pending uses. '
 'There are 156 scoped families (29 native + 127 upstream), 159 including three existing adapters; 256 missing and 19 method-overlap. '
 'This admission adds one bounded upstream family, five new source reviews and the missed P153 observation, and expands the existing P061 review. Six roles are covered. The full goal remains unfinished.')
VISIBLE_ACTION=('SKIP3: dependent analysis skipped. Supply the named permitted prerequisite or retain the skip. ClusterBuster scanning needs confirmed permission. '
 'FAIL2: no complete result. Keep failed evidence, fix the stated cause and retry NEW output. Preserve NaN/Inf and per-field eligibility; require full VERIFY. '
 'Keep the academic runtime, trusted objects and source-bearing traces private. Exact author reproduction stays skipped where required editions, settings or inputs are unavailable.')
NOTICE=('<aside id="scenicplus-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;padding:1rem;'
 'border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere"><h2>SCENIC+ workflows and paper uses</h2>'
 '<p><strong>Warnings and required actions:</strong> '+html.escape(VISIBLE_ACTION)+'</p><p>'+html.escape(CURRENT)+'</p>'
 '<p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">SCENIC+ inputs, source roles and remedies</a>.</p>'
 '<details><summary>Full warnings, source scope and reported settings</summary><p>'+html.escape(ACTION+' '+SCOPE+' '+NOTE)+'</p></details></aside>')

def registry():
    rows=previous.registry();target=next(x for x in rows if x['id']==TARGET)
    require(len(rows)==485 and target['current_coverage']=='missing','Changed predecessor registry')
    target.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE);return rows

def applied(role):
    result=copy.deepcopy({k:v for k,v in role.items() if k!='original_observation'})
    result.update(source_role_review_applied=True,observation_addition_pending=False)
    return result

def validate(before,primary,q):
    require(set(before)==set(META) and primary==p.frozen(),'Changed source interpretation')
    require(q==json.loads((ROOT/QUALIFICATION).read_bytes()) and q['status']=='FOUR_PREPARED_SCENICPLUS_PROFILES_TESTED_THROUGH_MAIN_COMMAND_CATALOG_PENDING','Changed qualification')
    require(q['global_dispatch_integrated'] and q['actual_profile_routes']==4 and q['actual_modes']==['RUN','VERIFY','REPLAY'] and
        q['main_route_checks']==25 and q['boundary_controls']==20 and q['scientific_export_comparisons']==54 and q['historical_readers_on_archived_context']==6 and
        q['scientific_workers_and_profiles_unchanged'] and not q['historical_hashes_refreshed'] and not q['whole_SCENICplus_tool_qualified'] and not q['catalog_admitted'] and not q['whole_goal_complete'] and
        q['producer']['sha256']=='50e126903c8787e24742c5e710220600581de13cb6eeb98a6fd08a4fab84c222' and q['normal_tests']==dict(tests=2069,failures=0,errors=0,skipped=0),'Incomplete actual qualification')
    papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    require(len(papers)==250 and len(inventory)==446 and sum(len(x['tools']) for x in papers)==2722 and len(ledger['reviews'])==1702,'Changed predecessor population')
    require(summary['integration_family_counts']==dict(scoped=155,native=29,upstream=126,including_existing_adapters=158,validated_alias_labels_excluded_from_extra_credit=3),'Changed predecessor family counts')
    target=next(x for x in inventory if x['id']==TARGET)
    require(target['current_coverage']==target['implementation_status']=='missing' and target['papers']==p.POSITIVE[:-1] and target['paper_count']==5,'Changed target population')
    existing=[r for r in ledger['reviews'] if r['tool_id']==TARGET]
    role=next(r for r in primary['roles'] if r['paper_id']=='P061')
    require(len(existing)==1 and existing[0]['pmcid']==role['pmcid'] and existing[0]['doi']==role['doi'] and existing[0]['evidence_paragraph_sha256']==role['original_observation']['evidence_paragraph_sha256'],'Changed existing P061 review or duplicate reviews')
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id']);uses=[u for u in paper['tools'] if u['tool_id']==TARGET]
        require(uses==([] if role['original_observation'] is None else [role['original_observation']]) and paper['xml_sha256']==role['xml_sha256'],'Changed original observation')

def added_observation(role):
    anchor=next(a for a in role['anchors'] if a['paragraph_index']==11)
    return dict(tool_id=TARGET,tool='SCENIC+',analysis_role='Multiomic regulatory-network tools and motif reference resources',
        evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],matched_text='SCENIC+',extraction_score=None,
        usage_context='benchmark-analysis-with-pinned-author-implementation',review_status=REVIEWED,version_reported_near_name=None,
        evidence_selection='manual-source-role-addition-from-results-and-pinned-author-code',evidence_paragraph_sha256=anchor['paragraph_sha256'],
        use_summary=role['summary'],evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/'+role['pmcid']+'/ (section: '+anchor['section']+')',
        observation_addition_provenance='Missed positive corrected by individual source review; extraction_score is null because no automated Methods-extractor score is claimed.')

def transition(before,primary,q):
    validate(before,primary,q);papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id'])
        if role['original_observation'] is None:paper['tools'].append(added_observation(role))
        use=next(u for u in paper['tools'] if u['tool_id']==TARGET)
        pointers=[copy.deepcopy(a) for a in role['anchors']+role['additional_supporting_paragraphs'] if a['paragraph_index']!=use['evidence_paragraph_index']]
        scope=role['boundary']+' Available bounded implementation: '+SCOPE
        use.update(use_summary=role['summary'],implementation_scope_note=scope,review_status=REVIEWED,supporting_evidence=pointers,source_role_qualification=applied(role))
        if role['original_observation'] is not None:use['evidence_selection']='individually-reviewed-source-role'
        review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=TARGET,evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['summary'],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],supporting_evidence=copy.deepcopy(pointers),source_table_evidence=[],reported_version_contexts=copy.deepcopy(role['reported_version_contexts']),reported_settings=copy.deepcopy(role['reported_settings']),historical_edition_analysis_status=role['historical_edition_analysis_status'])
        old=next((r for r in ledger['reviews'] if (r['pmcid'],r['tool_id'])==(paper['pmcid'],TARGET)),None)
        if old is None:ledger['reviews'].append(review)
        else:old.update(review,review_enrichment_provenance='Expanded existing P061 resource review; its exact original record remains in metadata-before.zip. No new review credit.')
    target=next(x for x in inventory if x['id']==TARGET)
    target.update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE,implementation_scope=SCOPE,current_documentation=GUIDE,current_qualification='../scenicplus-global-checkpoint-20261123/STATUS.json',upstream_runtime_adapter_required=True,native_component_available=False,public_CLI_available=True,global_CLI_dispatch_available=True,source_build_required=True,released_binary_contains_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,new_native_family_credit=0,new_upstream_family_credit=1,source_roles_reviewed=6,paper_count=6,papers=p.POSITIVE,source_role_coverage=COVERAGE.rsplit('/',1)[-1],paper_role_qualification={r['paper_id']:applied(r) for r in primary['roles']},validation_note='2,069 selected tests; 25 actual packaged-main checks across four profiles, RUN/VERIFY/REPLAY; 54 exact exports against previously independently original-qualified component results; 20 boundary controls. No author-paper outcomes are reproduced by this source-role admission.',current_paper_role_limits=NOTE)
    summary['paper_tool_observations']=2723;summary['implementation_status_counts']=dict(Counter(x['implementation_status'] for x in inventory));summary['integration_family_counts']=dict(scoped=156,native=29,upstream=127,including_existing_adapters=159,validated_alias_labels_excluded_from_extra_credit=3)
    after=dict(before)
    for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    for name in (META[6],META[7]):
        page=before[name].decode();require(page.count(previous.NOTICE)==1 and 'id="scenicplus-catalog-notice"' not in page,'Changed previous notice')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
            require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed embedded metadata')
            payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
            require(page.count('${scenicVersion(t)}')==1 and page.count('function renderContent(){')==1,'Changed inherited renderer')
            helper=("function scenicplusVersion(t){if(t.tool_id!=='scenicplus'||!t.source_role_qualification)return scenicVersion(t);const q=t.source_role_qualification;"
                "return '<br><span class=\"scenicplus-version\" style=\"overflow-wrap:anywhere\">'+(q.reported_SCENICplus_edition===null?'SCENIC+ edition not reported':esc(q.reported_SCENICplus_edition))+'; '+esc(q.available_profile_role)+'; '+esc(q.historical_edition_analysis_status)+'</span>';}\n")
            page=page.replace('${scenicVersion(t)}','${scenicplusVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        body=re.search(r'<body[^>]*>',page,re.I);require(body is not None,'Missing body')
        marker=page.find('<site-header ',body.end());require(marker>=body.end() and page.count('<site-header ')==1,'Changed historical prefix boundary')
        inherited=page[body.end():marker]
        history='<details id="scenicplus-prior-catalog"><summary>Before SCENIC+ admission (historical)</summary>'+inherited+'</details>'
        after[name]=(page[:body.end()]+NOTICE+history+page[marker:]).encode()
    prefix=('# Current SCENIC+ catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [workflow guide](../SCENICPLUS_CATALOG_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in (META[3],META[4]):after[name]=prefix+before[name]
    return after

def check(before,after,primary,q):
    require(after==transition(before,primary,q),'Changed deterministic transition');old,new=json.loads(before[META[0]]),json.loads(after[META[0]]);unchanged=0
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity')
        require([u for u in a['tools'] if u['tool_id']!=TARGET]==[u for u in b['tools'] if u['tool_id']!=TARGET],'Changed unrelated use');unchanged+=sum(u['tool_id']!=TARGET for u in a['tools'])
        if b['paper_id'] in p.POSITIVE[:-1]:
            x=next(u for u in a['tools'] if u['tool_id']==TARGET);y=next(u for u in b['tools'] if u['tool_id']==TARGET)
            for key in ('tool_id','tool','matched_text','extraction_score','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator','version_reported_near_name'):require(x[key]==y[key],'Changed literal extraction')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']!=TARGET]==[r for r in b if r['id']!=TARGET],'Changed unrelated inventory')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]])
    require([r for r in b['reviews'][:1702] if r['tool_id']!=TARGET]==[r for r in a['reviews'] if r['tool_id']!=TARGET] and {k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'},'Changed unrelated prior ledger')
    prior=next(r for r in a['reviews'] if r['tool_id']==TARGET);expanded=next(r for r in b['reviews'] if r['pmcid']==prior['pmcid'] and r['tool_id']==TARGET)
    for key in ('pmcid','doi','tool_id','evidence_paragraph_sha256'):require(prior[key]==expanded[key],'Changed existing review identity')
    require(len(b['reviews'])==len({(r['pmcid'],r['tool_id']) for r in b['reviews']})==1707,'Wrong unique review count')
    markers={REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open'}
    require({(paper['pmcid'],use['tool_id']) for paper in new for use in paper['tools'] if use['review_status'] in markers}=={(r['pmcid'],r['tool_id']) for r in b['reviews']},'Ledger/observations disagree')
    counts=Counter(r['implementation_status'] for r in json.loads(after[META[1]]));require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['method-overlap'],counts['missing'],unchanged)==(29,127,19,256,2717),'Wrong family counts')
    require(sum(len(x['tools']) for x in new)==2723,'Wrong positive population')
    return dict(papers=250,labels=446,provider_definitions=485,observations=2723,reviewed_uses=1707,pending_uses=1016,unchanged_prior_reviews=1701,existing_reviews_enriched=1,source_roles_reviewed=6,new_reviews=5,unchanged_unrelated_observations=2717,unchanged_unrelated_inventory_rows=445,scoped_families=156,native=29,upstream=127,including_existing_adapters=159,method_overlap=19,missing=256,new_native_family_credit=0,new_upstream_family_credit=1,added_positive_observations=1,removed_positive_observations=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
