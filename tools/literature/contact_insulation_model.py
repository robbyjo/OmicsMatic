"""Pure source-bound contact-insulation admission; eight roles, seven new reviews, one native family."""
from collections import Counter
import copy,html,json,re
from gemma_primary import encoded,require

DATA='docs/literature/omics-2023-2026'
META=[DATA+'/'+n for n in ('papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md')]+['tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']
ADMISSION='docs/literature/contact-insulation-catalog-admission'
GUIDE='docs/literature/CONTACT_INSULATION.md'
PROGRAM='src/main/java/org/jlinalg/cli/ContactInsulationCli.java'
Q='docs/literature/contact-insulation-validation/QUALIFICATION.json'
TARGETS={'allcools','cooltools'}
PAIRS={('P047','allcools'),('P053','allcools'),('P060','allcools'),('P061','allcools'),('P003','cooltools'),('P053','cooltools'),('P169','cooltools'),('P228','cooltools')}
PRIOR_REVIEW=('PMC10719113','allcools')
REVIEWED='usage-summary-reviewed; paper-wide-completeness-review-open'
SCOPE=('Native Java prepared weighted cis-map insulation and fixed numeric boundary strengths using the shared selected cooltools 0.5.1/0.5.4 numerical behavior. '
       'Complete typed bins/pixels, supplied weights, explicit settings, per-region median normalization, global per-window eligibility, source masks/prominence/order, '
       'full input/producer capture and portable numerical replay. One scoped native family. Raw Cooler IO/balancing, automatic thresholds, eigenvectors, dots, '
       'ALLCools and full author pipelines remain separate. A selected comparison tag does not authenticate an author commit.')
ACTION=('SKIP3: the dependent analysis was skipped because a named permitted source, executable or installation prerequisite is unavailable. '
        'Supply it where permitted, or retain the skip. FAIL2: no complete result was admitted; preserve the attempt, correct the named input, resource, '
        'cancellation, arithmetic or consistency problem and retry NEW paths. Read WARNINGS.md and OUTPUT-AUDIT.json and require COMPLETED.json plus '
        'saved verification with the exact producing JAR. Native insulation uses >=; P053 reports >0.1 and that strict author rule is not silently substituted. '
        'Missing source score/strength markers are counted explicitly. ALLCools remains pending. MaxQuant runtime stays skipped at the user request; '
        'MaxQuant/Andromeda/MaxDIA remain pending. Cell Ranger remains excluded. Raw evidence stays private and literature reports remain withheld from Pages.')
CURRENT=('250 papers, 451 labels, 490 provider definitions and 2,731 observations; 1,809 reviewed / 922 pending uses. '
         '167 scoped families (33 native + 134 upstream), 170 including three existing adapters. 254 labels remain missing, '
         '13 method overlaps and five validated aliases. This checkpoint adds one native insulation family, seven source-role reviews '
         'and an expanded existing ALLCools review, with zero new papers, observations or providers. The full goal remains open.')
UI_CURRENT=('250 papers; 167 scoped families (33 native Java, 134 upstream adapters). Of 2,731 uses, 1,809 reviewed / 922 pending. '
            'Another 254 tool labels remain pending.')
SUMMARIES={
 ('P047','allcools'):'Counts methylated and total calls into 100 kb and gene-body features, calculates posterior fractions, transfers labels on integrated coordinates and calls cluster pseudobulk DMRs.',
 ('P053','allcools'):'Builds methylome feature tensors and posterior mCH/mCG values, selects cluster-enriched features and merges ALLC profiles. The described downstream DMR caller is methylpy.',
 ('P060','allcools'):'Uses SeuratIntegration CCA and anchors for two rounds of RNA/MERFISH coembedding, label transfer and expression imputation across large datasets.',
 ('P061','allcools'):'Builds methylome feature tensors, performs iterative clustering, merges ALLC profiles, constructs BaseDS and calls permutation RMS DMS/DMRs, with additional marker selection and integration.',
 ('P003','cooltools'):'Calculates compartment eigenvectors with gene-density selection/orientation, diamond insulation on 25 kb bins with a 100 kb window and two ignored diagonals, and separately versioned dots loop calls.',
 ('P053','cooltools'):'Calculates domain-boundary insulation on 10 kb matrices with 500 kb windows and reports retaining boundary strength >0.1.',
 ('P169','cooltools'):'Uses cooltools 0.5.4 for Micro-C domain/compartment analyses, reporting 10 kb bins, 30/100 kb windows and GC-phased first-eigenvector compartment assignments.',
 ('P228','cooltools'):'Uses eigs-cis for compartments on balanced Hi-C matrices. Surrounding TAD/differential-TAD operations in the same paragraph belong to other named providers.'}
GAPS={
 ('P047','allcools'):'Implementation pending. The package edition is unreported. Exact weighted-neighbor integration/transfer and DMR routines/settings require author-source tracing; generic kNN is not this workflow.',
 ('P053','allcools'):'Implementation pending. Keep YAP/aligners, openTSNE/UMAP and methylpy DMR testing separate. The methylpy 500 bp / difference >=0.3 recipe is distinct from P061 ALLCools BaseDS.',
 ('P060','allcools'):'Implementation pending. Package edition and complete settings are unreported. Generic CCA/kNN does not implement the two-round integration or its multi-million-cell scale.',
 ('P061','allcools'):'Implementation pending. The selected 1.0.8 tag exposes generate_base_ds but no generate-base-ds CLI registration; resolve the author wrapper/updated source. Preserve paired CpGs, coverage caps 50 per sample / 3,000 total, and 250 bp / PCC >0.3 DMR aggregation.',
 ('P003','cooltools'):'Native prepared insulation is available with explicit settings. The insulation edition is unreported; only dots has explicit 0.5.1 attribution. Resolve the literal diamond-insulation command/wrapper. Eigenvectors, dots, Peakachu and downstream protocol classification remain separate.',
 ('P053','cooltools'):'Native prepared insulation and fixed >= thresholds are available. The paper reports strict >0.1; do not silently equate it with >= or infer the author entry point. Original author inputs and broader pipeline remain separate.',
 ('P169','cooltools'):'Native explicit prepared insulation is available. Resolve the reported window-to-function/flag mapping; eigs-cis has no generic sliding-window option. Compartments/GC phasing, nf-distiller preprocessing, balancing and FitHiC2 remain separate.',
 ('P228','cooltools'):'This eigs-cis role remains pending; native insulation does not implement it. Edition, phasing and eigenvector selection are unreported. HiCExplorer/nf-core/HiC-Pro/diffHic/edgeR operations remain with their own providers.'}

def qualified(q):
    require(q['status']=='PASS_SCOPED_NATIVE_PREPARED_CONTACT_INSULATION_CAPTURE'and q['direct_public_source_build']
        and q['Windows_focused_JUnit']==q['Linux_focused_JUnit']==84 and q['JUnit_skips']==0
        and q['source_component_cases_each_OS']==459 and q['original_source_editions']==['0.5.1','0.5.4']
        and q['original_selected_functions_executed']is True and q['selected_functions_identical_between_editions']is True
        and (q['full_map_bins'],q['full_map_stored_pixels'],q['full_map_chromosomes'])==(2666,3307826,22)
        and q['typed_captures_each_OS']==q['full_foreign_saved_replays_each_OS']==1
        and q['corrupted_capture_refusals_each_OS']==7 and q['resource_refusals_each_OS']==q['unavailable_source_skips_each_OS']==1
        and q['unchanged_prior_packaged_members']==23529 and q['only_prior_packaged_change']=='org/jlinalg/cli/JLinAlgCli.class'
        and q['private_probes_packaged']is False and q['MIT_notice_packaged']is True
        and q['eligible_new_native_family_credit']==1 and q['new_upstream_family_credit']==0
        and q['source_role_reviews']==8 and q['new_source_reviews_planned']==7 and q['existing_partial_source_review_expanded']==1
        and q['new_observations_or_providers']==0 and q['catalog_credit_already_applied']is False
        and all(q[k]is False for k in ('whole_package_qualified','full_author_roles_implemented','whole_paper_reproduced','whole_goal_complete',
            'complete_original_cooltools_package_installed_or_executed','original_source_orchestrator_executed','raw_Cooler_IO_or_balancing_qualified',
            'automatic_thresholds_qualified','eigenvectors_or_dots_qualified','ALLCools_implementation_qualified')),'Incomplete or expanded native insulation qualification')

def applied(role):
    pair=role['paper_id'],role['tool_id'];available=pair[1]=='cooltools'and pair[0]!='P228';result=copy.deepcopy(role)
    result.update(source_role_review_applied=True,catalog_admitted=True,use_summary=SUMMARIES[pair],remaining_scope=GAPS[pair],
        predecessor_remaining_scope=role['remaining_scope'],implementation_available=available,native_prepared_insulation_available=available,
        literal_reported_version=role['literal_package_version_reported_elsewhere'],role_kind='contact-map analysis'if pair[1]=='cooltools'else'methylome analysis',
        runtime_analysis_status='SCOPED_NATIVE_PREPARED_INSULATION_AVAILABLE_AUTHOR_ROLE_PENDING'if available else'ROLE_IMPLEMENTATION_PENDING',
        full_source_role_implemented=False,whole_package_qualified=False,whole_paper_reproduced=False,paper_edition_reproduced=False,new_family_credit=0)
    return result

def registry(previous):
    rows=copy.deepcopy(previous);require(len(rows)==490,'Changed provider population');selected=[r for r in rows if r['id']=='cooltools'];require(len(selected)==1 and selected[0]['current_coverage']=='missing','Provider already promoted or duplicated')
    selected[0].update(current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=SCOPE,integration_family='cooltools',family_credit=1)
    return rows

def transition(before,roles,q):
    qualified(q);papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in (0,1,2,5)]
    require((len(papers),len(inventory),sum(len(p['tools'])for p in papers),len(ledger['reviews']))==(250,451,2731,1802),'Changed predecessor populations')
    selected=roles['roles'];require(len(selected)==8 and {(r['paper_id'],r['tool_id'])for r in selected}==PAIRS,'Different source roles')
    by_pair={(r['paper_id'],r['tool_id']):r for r in selected}
    require(by_pair['P003','cooltools']['literal_package_version_reported_elsewhere']is None
        and by_pair['P003','cooltools']['component_version_exceptions']==dict(dots='0.5.1',other_cooltools_roles=None),'Dots edition substituted for an unreported insulation edition')
    require('strict reported>0.1selection'in by_pair['P053','cooltools']['distinct_uses']
        and by_pair['P228','cooltools']['distinct_uses']==['eigs-cis compartment analysis'],'Boundary strictness or provider-specific role changed')
    require('methylpy'in by_pair['P053','allcools']['use_summary']and'250bp/PCC>0.3'in by_pair['P061','allcools']['remaining_scope'],'Distinct methylation callers or recipes were conflated')
    prior_targets=[r for r in ledger['reviews']if r['tool_id']in TARGETS];require(len(prior_targets)==1 and (prior_targets[0]['pmcid'],prior_targets[0]['tool_id'])==PRIOR_REVIEW,'Existing review expansion would duplicate a review')
    for role in selected:
        pair=role['paper_id'],role['tool_id'];paper=next(p for p in papers if p['paper_id']==pair[0]);uses=[u for u in paper['tools']if u['tool_id']==pair[1]];require(len(uses)==1,'Existing source observation missing/duplicated')
        use=uses[0];require(use==role['raw_catalog_observation_preserved'],'Raw predecessor observation changed')
        supporting=copy.deepcopy(use.get('supporting_evidence',[]))
        for anchor in role['primary_anchors']:
            if anchor['paragraph_index']!=use['evidence_paragraph_index']and not any(a.get('paragraph_index')==anchor['paragraph_index']for a in supporting):supporting.append(copy.deepcopy(anchor))
        scope=GAPS[pair]+(' Available profile: '+SCOPE if pair[1]=='cooltools'else' Native contact insulation supplies no ALLCools implementation.')
        use.update(use_summary=SUMMARIES[pair],review_status=REVIEWED,evidence_selection='individually-reviewed-source-role',supporting_evidence=supporting,implementation_scope_note=scope,source_role_qualification=applied(role))
        review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=pair[1],evidence_paragraph_sha256=use['evidence_paragraph_sha256'],
            use_summary=SUMMARIES[pair],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],
            supporting_evidence=copy.deepcopy(supporting),source_role_qualification=applied(role))
        existing=[r for r in ledger['reviews']if(r['pmcid'],r['tool_id'])==(paper['pmcid'],pair[1])]
        if existing:require(len(existing)==1 and (paper['pmcid'],pair[1])==PRIOR_REVIEW,'Unexpected existing review');existing[0].update(review)
        else:ledger['reviews'].append(review)
    for row in inventory:
        if row['id']not in TARGETS:continue
        require(row['implementation_status']==row['current_coverage']=='missing','A target provider was already classified as implemented')
        related=[r for r in selected if r['tool_id']==row['id']]
        row.update(paper_role_qualification={r['paper_id']:applied(r)for r in related},current_documentation=GUIDE,whole_tool_qualified=False,whole_paper_reproduced=False)
        if row['id']=='cooltools':row.update(current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=SCOPE,implementation_status='native-subset-validated',
            implementation_source=PROGRAM,implementation_scope=SCOPE,current_qualification=Q,integration_family='cooltools',family_credit=1,source_build_required=True,released_binary_contains_workflow=False,
            validation_note='459 selected-function cases and a complete 2,666-bin / 3,307,826-pixel reference map; 84 focused tests, full typed capture, portable replay and explicit corruption/resource/skip controls on each OS. Only the prepared insulation/fixed-threshold subset is qualified.')
        else:row.update(implementation_scope=' '.join(GAPS[r['paper_id'],r['tool_id']]for r in related),validation_note='Eight-role full-context primary review includes four ALLCools papers; no ALLCools implementation or runtime is admitted.',runtime_analysis_status='PENDING_IMPLEMENTATION_NO_NATIVE_INSULATION_SUBSTITUTION')
    counts=Counter(r['implementation_status']for r in inventory)
    summary.update(implementation_status_counts=dict(counts),integration_family_counts=dict(scoped=counts['native-subset-validated']+counts['upstream-adapter-validated'],native=counts['native-subset-validated'],upstream=counts['upstream-adapter-validated'],including_existing_adapters=counts['native-subset-validated']+counts['upstream-adapter-validated']+counts['existing-adapter'],validated_alias_labels_excluded_from_extra_credit=counts['validated-family-alias']))
    after=dict(before)
    for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value);after[name]=raw.replace(b'\n',b'\r\n')if before[name].count(b'\n')==before[name].count(b'\r\n')else raw
    notice=('<aside id="contact-insulation-catalog-notice" style="margin:1rem;padding:1rem;border:2px solid #286d5a;background:#edf8f3;color:#163b30;overflow-wrap:anywhere"><h2>Native prepared contact-map insulation available</h2><p>'+html.escape(UI_CURRENT)+'</p><p>Explicit weighted insulation and fixed numeric boundary strengths are available. Raw Cooler preparation, eigenvectors, dots, ALLCools and full author workflows remain separate.</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Contact-insulation commands, inputs and limits</a></p></aside>')
    for name in META[6:]:
        page=before[name].decode();require('id="contact-insulation-catalog-notice"'not in page and'id="diann-catalog-notice"'in page,'Different predecessor report')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
            require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Predecessor embedded data differ')
            payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
            require(page.count('${diannVersion(t)}')==page.count('function renderContent(){')==1,'Changed report role renderer')
            helper="function contactVersion(t){if(!['cooltools','allcools'].includes(t.tool_id)||!t.source_role_qualification)return diannVersion(t);const q=t.source_role_qualification;const c=q.component_version_exceptions;return '<br><span class=\"contact-version\" style=\"overflow-wrap:anywhere\">Role: '+esc(q.role_kind)+'; Reported package version: '+esc(q.literal_reported_version===null?'unreported':q.literal_reported_version)+(c?'; Component versions: '+esc(JSON.stringify(c)):'')+'; '+(q.implementation_available?'Native prepared insulation subset available; full author role pending':'Implementation pending for this role')+'; '+esc(q.remaining_scope)+'</span>';}\n"
            page=page.replace('${diannVersion(t)}','${contactVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Changed report body boundary')
        history='<details id="contact-insulation-prior-catalog"><summary>Before native contact-insulation admission (historical)</summary>'+page[body.end():marker]+'</details>'
        after[name]=(page[:body.end()]+notice+history+page[marker:]).encode()
    prefix=('# Current native contact-insulation catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [contact-insulation workflow guide](../CONTACT_INSULATION.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in META[3:5]:after[name]=prefix+before[name]
    return after

def check(before,after,roles,q):
    require(after==transition(before,roles,q),'Different deterministic native contact-insulation transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
    for left,right in zip(old,new,strict=True):
        require({k:v for k,v in left.items()if k!='tools'}=={k:v for k,v in right.items()if k!='tools'},'Paper identity changed')
        require([u for u in left['tools']if u['tool_id']not in TARGETS]==[u for u in right['tools']if u['tool_id']not in TARGETS],'Unrelated observation changed')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]])
    require([r for r in a if r['id']not in TARGETS]==[r for r in b if r['id']not in TARGETS],'Unrelated inventory changed')
    prior=json.loads(before[META[5]])['reviews'];reviews=json.loads(after[META[5]])['reviews']
    require(len(reviews)==len({(r['pmcid'],r['tool_id'])for r in reviews})==1809,'Review duplicate or wrong expansion count')
    require([r for r in prior if(r['pmcid'],r['tool_id'])!=PRIOR_REVIEW]==[r for r in reviews[:1802]if(r['pmcid'],r['tool_id'])!=PRIOR_REVIEW],'Unrelated existing review changed')
    marked={(p['pmcid'],u['tool_id'])for p in new for u in p['tools']if u['review_status']in(REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open')}
    require(marked=={(r['pmcid'],r['tool_id'])for r in reviews},'Review markers and ledger differ')
    counts=Counter(r['implementation_status']for r in b)
    require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'],counts['method-overlap'],counts['validated-family-alias'])==(33,134,254,13,5),'Wrong family/label credit')
    require(next(r for r in b if r['id']=='allcools')['implementation_status']=='missing','ALLCools was substituted by native insulation')
    return dict(papers=250,labels=451,provider_definitions=490,observations=2731,reviewed_uses=1809,pending_uses=922,scoped_families=167,native=33,upstream=134,including_existing_adapters=170,missing=254,method_overlap=13,validated_alias_labels=5,new_native_family_credit=1,new_upstream_family_credit=0,new_source_role_reviews=7,existing_source_review_expanded=1,new_named_providers=0,added_positive_observations=0,unchanged_unrelated_inventory_rows=449,unchanged_unrelated_observations=2723,unchanged_unrelated_reviews=1801,ALLCools_implementation_qualified=False,eigenvectors_or_dots_qualified=False,whole_package_qualified=False,full_author_roles_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)
