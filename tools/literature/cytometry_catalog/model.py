"""One bounded native family; acquisition remains missing; 41 new and 3 enriched reviews."""
from collections import Counter
import copy,html,json,re
from impute_catalog import model as previous
from cytometry_catalog import primary as p

ROOT,META,DATA=p.ROOT,previous.META,previous.DATA
REVIEWED=previous.REVIEWED;require=p.require;encoded=p.encoded
GUIDE='docs/literature/CYTOMETRY_CATALOG_WORKFLOWS.md'
COVERAGE=DATA+'/cytometry-source-role-coverage.json';PROOF=DATA+'/cytometry-catalog-validation.json'
CHECKPOINT='docs/literature/cytometry-intensity-validated-checkpoint/STATUS.json'
SCOPE=('Native Java flowjo-tools GATE for FlowJo 10.x workspace format 20.0 with the explicit FlowKit 1.3.2 interpretation: complete sample-local gate memberships/counts, captured FCS/WSP and explicit biex tables; '
 'SUMMARIZE adds complete unweighted unbinned amplifier or saved-WSP-compensated measurement summaries with explicit R type-7 quantiles. Fresh memberships and saved-count metadata are checked. '
 'Exact producer replay and integrity-only verification are separate. No vendor runtime is installed or required. Saved vendor-count differences remain visible. '
 'No vendor-binned statistics/off-scale Robust SD equivalence, FlowJo 9, acquisition/sorting, cell-cycle fits, clustering plugins, image-event import, vendor figures, inferred MFI definition, whole-tool/paper or biological qualification.')
FACSDIVA_SCOPE=('FACSDiva source roles are reviewed; its instrument acquisition/sorting and unresolved jointly attributed analysis are not implemented. '
 'Offline native FCS/workspace computation is a separate capability and does not replace the instrument software. No native/upstream integration credit is assigned to FACSDiva.')
NOTE=('Forty-four roles cover 27 FlowJo and 17 FACSDiva paper/tool pairs. P032/P084 intensity statistics remain distinct from unbinned summaries; P084 vendor binning/off-scale rules are unresolved. '
 'P051 FlowSOM, UMAP and ClusterExplorer plugins/image-event import remain gaps. P121 reports FlowJo 9; P122 Dean-Jett-Fox and P228 Watson Pragmatic fits are not gate counts. '
 'P031/P052 jointly attribute analysis without a package-specific split. P119 retains FlowJo 10 OR FCS Express 7. P082 FACSDiva is a software-only declaration; P064 literal FACSDiva 8.02 remains unchanged. '
 'Reported versions are source summaries; raw near-name captures remain unmodified. No paper cohort or original vendor analysis is reproduced.')
ACTION=('SKIP3: skip the dependent paper analysis when original permitted FCS/WSP, explicit calibration/measurement settings or a required supported method are unavailable. '
 'Supply exact inputs/settings or implement/qualify the missing model/plugin; do not substitute native unbinned statistics for vendor-binned results or FlowJo 10 for version 9. '
 'FACSDiva acquisition/sorting remains unavailable here. FAIL2: no complete result; retain original/partial/failed evidence, fix the named integrity, numerical, capacity or saved-count cause and retry NEW output. '
 'Define MFI and measurement domain explicitly. Keep null/undefined statuses, all source events and numerical discrepancies; no clipping, zero snapping or nonpositive-event removal. '
 'Read WARNINGS.md and analysis.json. Preserve exact producer JARs and historical evidence. Cell Ranger remains excluded.')
CURRENT=('250 papers, 446 labels, 485 provider definitions and 2,724 observations; 1,755 reviewed/969 pending uses. '
 '158 scoped families (30 native + 128 upstream), 161 including three existing adapters; 254 missing and 19 method-overlap. '
 'One bounded native FlowJo family and 41 new reviews are admitted; three prior cytometry reviews are enriched. FACSDiva remains missing. The full goal is unfinished.')
NOTICE=('<aside id="cytometry-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>Cytometry: native workspace analysis and source-use review</h2><p>'+html.escape(CURRENT)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p>'+html.escape(NOTE)+'</p>'
 '<p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Supported commands, source roles and remaining work</a></p></aside>')

def qualification():
    state=json.loads((ROOT/CHECKPOINT).read_bytes());r=json.loads((ROOT/'docs/literature/cytometry-intensity-validated-checkpoint/READBACK-AFTER-CLEANUP.json').read_bytes())
    require(state['status']==r['status']=='PASS_NATIVE_UNBINNED_CYTOMETRY_INTENSITY_V1' and not state['catalog_admitted'] and not state['vendor_statistics_qualified'],'Changed native component qualification')
    require((r['packaged_samples_per_host'],r['event_gate_pairs_per_pass'],r['raw_R_control_rows'],r['raw_R_underflow_differences_preserved'],r['source_role_pairs_prepared'],r['private_files_checked'])==(23,53019143,910,2,44,8045),'Incomplete source workflow evidence')
    require(state['producer']==dict(bytes=199498076,sha256='afa06f981e84a0cfc9ec20d14304f770a9f716c65f4be13b00ac7bd53cb13405'),'Changed exact producer')
    return dict(status='NATIVE_GATE_AND_UNBINNED_STATISTICS_COMPONENT_PRE_ADMISSION',checkpoint=CHECKPOINT,checkpoint_identity=p.pin((ROOT/CHECKPOINT).read_bytes()),producer=state['producer'],normal_tests_final=76,packaged_samples_per_host=23,event_gate_pairs_per_pass=53019143,selected_values_per_pass=16312934,native_values_R_statistics=855,raw_R_control_rows=910,raw_R_underflow_differences=2,compensation_zero_boundary_comparisons=513,Windows_boundary_controls=25,Linux_boundary_controls=5,vendor_statistics_qualified=False,whole_tool_qualified=False,whole_goal_complete=False)
def applied(role):
    q=copy.deepcopy({k:v for k,v in role.items() if k!='original_observation'});q.update(source_role_review_applied=True,catalog_admitted=True);return q
def registry():
    from common_statistics.prism_catalog import registry as complete
    rows=complete();require(len(rows)==len({r['id'] for r in rows})==485,'Changed complete provider population');base={r['id']:r for r in previous.registry()};inventory={r['id']:r for r in json.loads((ROOT/META[1]).read_bytes())}
    for i,row in enumerate(rows):
        if row['id'] in base:rows[i]=copy.deepcopy(base[row['id']]);row=rows[i]
        if row['id'] in inventory:
            current=inventory[row['id']]
            for key in ('current_coverage','existing_source','coverage_note'):row[key]=current[key]
        if row['id']=='flowjo':row.update(current_coverage='native-subset-validated',existing_source='src/main/java/org/jlinalg/cli/FlowJoToolsCli.java',coverage_note=SCOPE)
        if row['id']=='facsdiva':row.update(current_coverage='missing',existing_source='',coverage_note=FACSDIVA_SCOPE)
    return rows
def validate(before,primary,q):
    require(set(before)==set(META) and primary==p.frozen() and q==qualification(),'Changed source interpretation or native qualification');papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    require(len(papers)==250 and len(inventory)==446 and sum(len(x['tools']) for x in papers)==2724 and len(ledger['reviews'])==1714,'Changed predecessor population');require(summary['integration_family_counts']==dict(scoped=157,native=29,upstream=128,including_existing_adapters=160,validated_alias_labels_excluded_from_extra_credit=3),'Changed predecessor families')
    for tool,n,reviewed in [('flowjo',27,1),('facsdiva',17,2)]:
        row=next(x for x in inventory if x['id']==tool);require(row['implementation_status']==row['current_coverage']=='missing' and row['paper_count']==n and sum(r['tool_id']==tool for r in ledger['reviews'])==reviewed,'Changed target state')
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id']);require([u for u in paper['tools'] if u['tool_id']==role['tool_id']]==[role['original_observation']],'Changed raw target observation')
def transition(before,primary,q):
    validate(before,primary,q);papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)];ledger_index={(r['pmcid'],r['tool_id']):r for r in ledger['reviews']}
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id']);use=next(u for u in paper['tools'] if u['tool_id']==role['tool_id']);pointers=copy.deepcopy(use.get('supporting_evidence',[]))
        for anchor in role['primary_anchors']:
            if anchor['paragraph_sha256']!=use['evidence_paragraph_sha256'] and not any(a.get('paragraph_sha256')==anchor['paragraph_sha256'] for a in pointers):pointers.append(copy.deepcopy(anchor))
        scope=role['remaining_scope']+' '+(SCOPE if role['tool_id']=='flowjo' else FACSDIVA_SCOPE)
        use.update(use_summary=role['use_summary'],implementation_scope_note=scope,review_status=REVIEWED,evidence_selection='individually-reviewed-source-role',supporting_evidence=pointers,source_role_qualification=applied(role))
        key=(paper['pmcid'],role['tool_id']);review=ledger_index.get(key)
        if review is None:review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=role['tool_id'],evidence_paragraph_sha256=use['evidence_paragraph_sha256']);ledger['reviews'].append(review);ledger_index[key]=review
        review.update(use_summary=role['use_summary'],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],supporting_evidence=copy.deepcopy(pointers),reported_version_summary=role['reported_version'],historical_analysis_status=role['historical_analysis_status'],source_role_qualification=applied(role))
    for tool in ('flowjo','facsdiva'):
        row=next(x for x in inventory if x['id']==tool);native=tool=='flowjo';scope=SCOPE if native else FACSDIVA_SCOPE;source='src/main/java/org/jlinalg/cli/FlowJoToolsCli.java' if native else '';status='native-subset-validated' if native else 'missing';roles=[r for r in primary['roles'] if r['tool_id']==tool]
        row.update(current_coverage=status,implementation_status=status,existing_source=source,implementation_source=source,coverage_note=scope,implementation_scope=scope,current_documentation=GUIDE,current_qualification='../cytometry-intensity-validated-checkpoint/STATUS.json' if native else None,upstream_runtime_adapter_required=False,native_component_available=native,public_CLI_available=native,global_CLI_dispatch_available=native,source_build_required=native,released_binary_contains_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,new_native_family_credit=int(native),new_scoped_integration_credit=int(native),new_upstream_family_credit=0,source_roles_reviewed=len(roles),source_role_coverage=COVERAGE.rsplit('/',1)[-1],paper_role_qualification={r['paper_id']:applied(r) for r in roles},current_paper_role_limits=NOTE,validation_note='Source-bound offline native profile only; 23 packaged cases per host, 855 R summary rows and 25/5 actual boundary controls. Original raw R underflow and compensation sign-boundary differences remain visible. No vendor installation or original paper analysis is claimed.' if native else 'Seventeen roles reviewed; acquisition/sorting, original instrument software and unresolved joint analyses remain unimplemented.')
    summary['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inventory));summary['integration_family_counts']=dict(scoped=158,native=30,upstream=128,including_existing_adapters=161,validated_alias_labels_excluded_from_extra_credit=3);after=dict(before)
    for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    for name in META[6:]:
        page=before[name].decode();require(page.count(previous.NOTICE)==1 and 'id="cytometry-catalog-notice"' not in page,'Changed prior notice')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed embedded catalog');payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
            require(page.count('${imputeVersion(t)}')==1 and page.count('function renderContent(){')==1,'Changed inherited renderer')
            helper=("function cytometryVersion(t){if(!['flowjo','facsdiva'].includes(t.tool_id)||!t.source_role_qualification)return imputeVersion(t);const q=t.source_role_qualification;return '<br><span class=\"cytometry-version\" style=\"overflow-wrap:anywhere\">Reported version(s): '+esc(q.reported_version)+'; '+esc(q.historical_analysis_status)+'; '+esc(q.remaining_scope)+'</span>';}\n")
            page=page.replace('${imputeVersion(t)}','${cytometryVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        body=re.search(r'<body[^>]*>',page,re.I);require(body is not None,'No page body');marker=page.find('<site-header ',body.end());require(marker>=body.end() and page.count('<site-header ')==1,'Changed historical boundary');history='<details id="cytometry-prior-catalog"><summary>Before cytometry admission (historical)</summary>'+page[body.end():marker]+'</details>';after[name]=(page[:body.end()]+NOTICE+history+page[marker:]).encode()
    prefix=('# Current cytometry catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+FACSDIVA_SCOPE+'\n\n'+NOTE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [workflow and source-role guide](../CYTOMETRY_CATALOG_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in META[3:5]:after[name]=prefix+before[name]
    return after
def check(before,after,primary,q):
    require(after==transition(before,primary,q),'Changed deterministic source-bound transition');old,new=json.loads(before[META[0]]),json.loads(after[META[0]]);unrelated=0
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity');require([u for u in a['tools'] if u['tool_id'] not in p.TARGETS]==[u for u in b['tools'] if u['tool_id'] not in p.TARGETS],'Changed unrelated observation');unrelated+=sum(u['tool_id'] not in p.TARGETS for u in a['tools'])
        for x in a['tools']:
            if x['tool_id'] in p.TARGETS:
                y=next(u for u in b['tools'] if u['tool_id']==x['tool_id'])
                for key in ('tool_id','tool','matched_text','extraction_score','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator','usage_context','version_reported_near_name'):require(x[key]==y[key],'Changed raw source field')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id'] not in p.TARGETS]==[r for r in b if r['id'] not in p.TARGETS],'Changed unrelated inventory')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]]);require([r for r in a['reviews'] if r['tool_id'] not in p.TARGETS]==[r for r in b['reviews'] if r['tool_id'] not in p.TARGETS],'Changed prior unrelated review');require({k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'},'Changed ledger metadata')
    for oldrow in a['reviews']:
        if oldrow['tool_id'] in p.TARGETS:
            row=next(r for r in b['reviews'] if r['pmcid']==oldrow['pmcid'] and r['tool_id']==oldrow['tool_id']);require(all(oldrow[k]==row[k] for k in ('pmcid','doi','tool_id','evidence_paragraph_sha256')),'Changed prior target identity')
    require(len(b['reviews'])==len({(r['pmcid'],r['tool_id']) for r in b['reviews']})==1755,'Changed unique review count');markers={REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open'};require({(p['pmcid'],u['tool_id']) for p in new for u in p['tools'] if u['review_status'] in markers}=={(r['pmcid'],r['tool_id']) for r in b['reviews']},'Ledger/observation review mismatch');counts=Counter(r['implementation_status'] for r in json.loads(after[META[1]]));require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['method-overlap'],counts['missing'],unrelated)==(30,128,19,254,2680),'Changed family/unrelated counts');require(sum(len(p['tools']) for p in new)==2724,'Changed positive observation population')
    return dict(papers=250,labels=446,provider_definitions=485,observations=2724,reviewed_uses=1755,pending_uses=969,source_roles_reviewed=44,new_reviews=41,existing_reviews_enriched=3,unchanged_prior_reviews=1711,unchanged_unrelated_observations=2680,unchanged_unrelated_inventory_rows=444,scoped_families=158,native=30,upstream=128,including_existing_adapters=161,method_overlap=19,missing=254,new_native_family_credit=1,new_upstream_family_credit=0,added_positive_observations=0,removed_positive_observations=0,FACSDiva_implemented=False,vendor_statistics_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
