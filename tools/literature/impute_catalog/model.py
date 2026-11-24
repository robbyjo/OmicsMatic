"""One bounded legacy IMPUTE2 profile, seven source roles, and one missed declaration."""
from collections import Counter
import copy,html,json,re
from scenicplus_catalog import model as previous
from impute_catalog import primary as p

ROOT,META,DATA=p.ROOT,previous.META,previous.DATA
TARGET='impute';PROGRAM='src/main/java/org/jlinalg/cli/ImputeToolsCli.java'
GUIDE='docs/literature/IMPUTE_CATALOG_WORKFLOWS.md'
COVERAGE=DATA+'/impute-source-role-coverage.json';PROOF=DATA+'/impute-catalog-validation.json'
REVIEWED=previous.REVIEWED;require=p.require;encoded=p.encoded
CHECKPOINT='docs/literature/impute2-checkpoint/STATUS.json'
SCOPE=('Pinned original IMPUTE2 v2.3.2 private unmodified academic Linux x86_64 runtime through the source-built Java impute-tools adapter. '
 'Four prepared autosomal-diploid Oxford-format operations: PREPHASE, IMPUTE_GENOTYPES, IMPUTE_PREPHASED and MERGE_REFERENCE; '
 'the Java merge output is also tested as a subsequent imputation input. Actual RUN, fresh original VERIFY/REPLAY and saved-closure inspection are qualified. '
 'Native Java captures literal inputs, audits source fields and provides explicitly conditional unit-mass/zero-censor probability bounds. '
 'Source GEN values, missing codes, INFO, warnings and undefined fields remain unchanged. No native imputation estimator, BGEN conversion, '
 'automatic reference/QC/identity/strand selection, chromosome X/PAR, other ploidy, IMPUTE2 v2.3.1, IMPUTE4/5, exact historical flags, whole-tool/paper, biological or authentication qualification.')
NOTE=('Seven roles are reviewed without assigning unspecified commands. P076/P084 report IMPUTE2 with unknown minor editions. '
 'P084 source MAC>100 removal and the Minimac r2 label remain literal, not repaired or relabeled. P116 explicitly declares IMPUTE2 v2.3.1 software use in Code Availability, '
 'but no local operation is established. P136 reports v2.3.2 for autosomal reference merging and separate ToMMo imputation; X merging uses BEAGLE4.1 and BGEN conversion is separate. '
 'P140 explicitly reports IMPUTE4.1.2_r300.3 in primary context, while its raw near-name extraction stays null. '
 'P149 contains an HMM analogy and a separate v2.3.1 software declaration, not an identified IMPUTE2 call. P180 uses IMPUTE5 in a masked Armenian-genome comparison, not v2.3.2. '
 'Unknown or unavailable exact editions and unresolved local operations remain skipped/pending, without version substitution.')
ACTION=('SKIP3: skip the dependent paper analysis if its permitted exact edition, prepared inputs, references, identity/ploidy/strand review or complete producer are unavailable. '
 'The available v2.3.2 profile does not execute v2.3.1, IMPUTE4 or IMPUTE5. Source declaration review does not establish local execution. '
 'FAIL2: no complete result; preserve raw/partial/failed/superseded evidence, correct the named cause and retry NEW output. '
 'Read WARNINGS.md, OUTCOME-WARNINGS.md, scientific-audit.json and unreportable-genotypes.tsv. Conditional bounds require independently justified unit mass and quantization; '
 'GEN tokens do not establish those premises. No wrapper normalization or repair, INFO/Rsq substitution, allele1-to-genome-ALT assumption or inferred participant IDs. '
 'Keep accepted original runtime and raw vendor traces private. On Windows use Linux Java in WSL. Keep exact historical producer JARs; do not refresh saved hashes. Cell Ranger remains excluded.')
CURRENT=('250 papers, 446 labels, 485 provider definitions and 2,724 positive observations; 1,714 reviewed/1,010 pending uses. '
 'There are 157 scoped families (29 native + 128 upstream), 160 including three existing adapters; 255 missing and 19 method-overlap. '
 'This admission adds one bounded IMPUTE2 upstream profile, seven source reviews and a missed P116 software-use declaration. '
 'IMPUTE4/5 and unresolved historical-paper analyses remain pending; the full goal is unfinished.')
VISIBLE_ACTION=('SKIP3: dependent analysis skipped until its permitted exact edition and prepared, reviewed inputs are available. IMPUTE2 v2.3.2 does not substitute for v2.3.1 or IMPUTE4/5. '
 'A software list or algorithm analogy does not identify a local command. FAIL2: no complete result; preserve failed evidence, fix the stated cause and retry NEW output. '
 'Keep original values and raw traces private. Probability bounds are conditional on unit mass and a declared quantizer; do not normalize or repair GEN values.')
NOTICE=('<aside id="impute-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;padding:1rem;'
 'border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere"><h2>IMPUTE2 workflows and paper-use review</h2>'
 '<p><strong>Warnings and required actions:</strong> '+html.escape(VISIBLE_ACTION)+'</p><p>'+html.escape(CURRENT)+'</p>'
 '<p>'+html.escape(NOTE)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Workflow scope and source-role details</a></p></aside>')

def registry():
    rows=json.loads((ROOT/'tools/literature/tool-registry.json').read_bytes());target=next(x for x in rows if x['id']==TARGET)
    target.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE);return rows

def qualification():
    state=json.loads((ROOT/CHECKPOINT).read_bytes());reader=json.loads((ROOT/'docs/literature/impute2-checkpoint/READBACK.json').read_bytes())
    require(state['status']==reader['status']=='PASS_SCOPED_IMPUTE2_FOUR_OPERATIONS_CONDITIONAL_BOUNDS' and not state['catalog_admitted'],'Changed source workflow checkpoint')
    require((reader['normal_tests'],reader['actual_RUN_VERIFY_REPLAY_fits'],reader['scientific_files_compared_to_original_controls'],reader['boundary_refusals'],reader['private_evidence_checked'])==(1856,15,16,12,1269),'Incomplete actual workflow evidence')
    require(state['producer']==dict(bytes=199408465,sha256='b7b15b83449bb2da767f5890ec46c8f1d8a6651fe11be1fa48209d12a1737d82'),'Changed exact producer')
    return dict(status='FOUR_IMPUTE2_PREPARED_OPERATIONS_AND_MERGE_CHAIN_CATALOG_PENDING',checkpoint=CHECKPOINT,checkpoint_identity=p.pin((ROOT/CHECKPOINT).read_bytes()),producer=state['producer'],normal_tests=1856,actual_original_fits=15,original_control_file_matches=16,boundary_refusals=12,original_operations=['PREPHASE','IMPUTE_GENOTYPES','IMPUTE_PREPHASED','MERGE_REFERENCE'],conditional_bounds_only=True,nearest_only_bounds_superseded=True,quantizer_empirical_not_formal_global_proof=True,only_qualified_tool_edition='2.3.2',catalog_admitted=False,whole_goal_complete=False)

def applied(role):
    result=copy.deepcopy({k:v for k,v in role.items() if k!='original_observation'});result.update(source_role_review_applied=True);return result

def validate(before,primary,q):
    require(set(before)==set(META) and primary==p.frozen() and q==qualification(),'Changed source interpretation or qualification')
    papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    require(len(papers)==250 and len(inventory)==446 and sum(len(x['tools']) for x in papers)==2723 and len(ledger['reviews'])==1707,'Changed predecessor population')
    require(summary['integration_family_counts']==dict(scoped=156,native=29,upstream=127,including_existing_adapters=159,validated_alias_labels_excluded_from_extra_credit=3),'Changed predecessor families')
    target=next(x for x in inventory if x['id']==TARGET);require(target['implementation_status']==target['current_coverage']=='missing' and target['paper_count']==6 and target['papers']==[x for x in p.POSITIVE if x!='P116'],'Changed target observations')
    require(not any(r['tool_id']==TARGET for r in ledger['reviews']),'Unexpected prior IMPUTE review')
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id']);uses=[u for u in paper['tools'] if u['tool_id']==TARGET]
        require(uses==([] if role['original_observation'] is None else [role['original_observation']]),'Changed raw observation')
        require(not role['original_paper_analysis_reexecuted'] and not role['whole_paper_reproduced'] and not role['available_operation_types_are_exact_historical_flags'],'Expanded paper reproduction claim')

def added_observation(role):
    anchor=next(a for a in role['anchors'] if a['paragraph_index']==45)
    return dict(tool_id=TARGET,tool='IMPUTE',analysis_role='Software-use declaration; local operation unresolved',evidence_section=anchor['section'],evidence_paragraph_index=45,matched_text='IMPUTE2',extraction_score=None,usage_context='software-use-declaration-local-operation-unresolved',review_status=REVIEWED,version_reported_near_name='2.3.1',evidence_selection='manual-source-role-addition-from-code-availability',evidence_paragraph_sha256=anchor['paragraph_sha256'],use_summary=role['summary'],evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/'+role['pmcid']+'/ (section: '+anchor['section']+')',observation_addition_provenance='Explicit used-software declaration selected manually; null automatic Methods score. Version is directly reported in Code Availability. No local imputation operation is inferred.')

def transition(before,primary,q):
    validate(before,primary,q);papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id'])
        if role['original_observation'] is None:paper['tools'].append(added_observation(role))
        use=next(u for u in paper['tools'] if u['tool_id']==TARGET);pointers=[copy.deepcopy(a) for a in role['anchors'] if a['paragraph_index']!=use['evidence_paragraph_index']]
        scope=role['boundary']+' Available bounded implementation: '+SCOPE
        use.update(use_summary=role['summary'],implementation_scope_note=scope,review_status=REVIEWED,supporting_evidence=pointers,source_role_qualification=applied(role))
        if role['original_observation'] is not None:use['evidence_selection']='individually-reviewed-source-role'
        ledger['reviews'].append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=TARGET,evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['summary'],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],supporting_evidence=copy.deepcopy(pointers),source_table_evidence=[],reported_version_contexts=copy.deepcopy(role['reported_version_contexts']),reported_settings=copy.deepcopy(role['reported_settings']),historical_edition_analysis_status=role['historical_edition_analysis_status']))
    target=next(x for x in inventory if x['id']==TARGET)
    target.update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE,implementation_scope=SCOPE,current_documentation=GUIDE,current_qualification='../impute2-checkpoint/STATUS.json',upstream_runtime_adapter_required=True,native_component_available=True,native_component_family_credit=0,public_CLI_available=True,global_CLI_dispatch_available=True,source_build_required=True,released_binary_contains_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,new_native_family_credit=0,new_upstream_family_credit=1,source_roles_reviewed=7,paper_count=7,papers=p.POSITIVE,source_role_coverage=COVERAGE.rsplit('/',1)[-1],paper_role_qualification={r['paper_id']:applied(r) for r in primary['roles']},validation_note='1,856 selected tests; 15 actual original-runtime fits across four operations and the Java merge-to-imputation chain; 16 full scientific comparisons; 12 actual refusals. Conditional GP bounds are not posterior reconstruction. Source review does not reproduce paper cohorts or unavailable editions.',current_paper_role_limits=NOTE)
    summary['paper_tool_observations']=2724;summary['implementation_status_counts']=dict(Counter(x['implementation_status'] for x in inventory));summary['integration_family_counts']=dict(scoped=157,native=29,upstream=128,including_existing_adapters=160,validated_alias_labels_excluded_from_extra_credit=3)
    after=dict(before)
    for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    for name in (META[6],META[7]):
        page=before[name].decode();require(page.count(previous.NOTICE)==1 and 'id="impute-catalog-notice"' not in page,'Changed prior notice')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
            require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed embedded source data')
            payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
            require(page.count('${scenicplusVersion(t)}')==1 and page.count('function renderContent(){')==1,'Changed inherited version renderer')
            helper=("function imputeVersion(t){if(t.tool_id!=='impute'||!t.source_role_qualification)return scenicplusVersion(t);const q=t.source_role_qualification;const versions=[...new Set(q.reported_version_contexts.map(v=>/^\\d$/.test(v.tool_edition)?v.tool_edition+' (minor edition unreported)':v.tool_edition))].join(', ');const unresolved=q.reported_settings.local_operation_established===false;const status=q.historical_edition_analysis_status.startsWith('EXACT_')?'Matching v2.3.2 profile available; prepared paper inputs still required':'Exact paper analysis remains skipped or pending; no edition substitution';return '<br><span class=\"impute-version\" style=\"overflow-wrap:anywhere\">Reported edition: '+esc(versions)+'; '+(unresolved?'Software declaration; local operation unreported; ':'')+esc(status)+'</span>';}\n")
            page=page.replace('${scenicplusVersion(t)}','${imputeVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        body=re.search(r'<body[^>]*>',page,re.I);require(body is not None,'Missing body');marker=page.find('<site-header ',body.end());require(marker>=body.end() and page.count('<site-header ')==1,'Changed history boundary')
        inherited=page[body.end():marker];history='<details id="impute-prior-catalog"><summary>Before IMPUTE admission (historical)</summary>'+inherited+'</details>';after[name]=(page[:body.end()]+NOTICE+history+page[marker:]).encode()
    prefix=('# Current IMPUTE catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [workflow guide](../IMPUTE_CATALOG_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in (META[3],META[4]):after[name]=prefix+before[name]
    return after

def check(before,after,primary,q):
    require(after==transition(before,primary,q),'Changed deterministic transition');old,new=json.loads(before[META[0]]),json.loads(after[META[0]]);unchanged=0
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity')
        require([u for u in a['tools'] if u['tool_id']!=TARGET]==[u for u in b['tools'] if u['tool_id']!=TARGET],'Changed unrelated observation');unchanged+=sum(u['tool_id']!=TARGET for u in a['tools'])
        original=[u for u in a['tools'] if u['tool_id']==TARGET]
        if original:
            x=original[0];y=next(u for u in b['tools'] if u['tool_id']==TARGET)
            for key in ('tool_id','tool','matched_text','extraction_score','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator','version_reported_near_name'):require(x[key]==y[key],'Changed raw extraction')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']!=TARGET]==[r for r in b if r['id']!=TARGET],'Changed unrelated inventory')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]]);require(b['reviews'][:1707]==a['reviews'] and {k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'},'Changed prior review ledger')
    require(len(b['reviews'])==len({(r['pmcid'],r['tool_id']) for r in b['reviews']})==1714,'Wrong unique review population')
    markers={REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open'};require({(paper['pmcid'],use['tool_id']) for paper in new for use in paper['tools'] if use['review_status'] in markers}=={(r['pmcid'],r['tool_id']) for r in b['reviews']},'Ledger and reviewed observations disagree')
    counts=Counter(r['implementation_status'] for r in json.loads(after[META[1]]));require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['method-overlap'],counts['missing'],unchanged)==(29,128,19,255,2717),'Wrong family or unrelated-use counts')
    require(sum(len(x['tools']) for x in new)==2724,'Wrong positive observation count')
    return dict(papers=250,labels=446,provider_definitions=485,observations=2724,reviewed_uses=1714,pending_uses=1010,unchanged_prior_reviews=1707,existing_reviews_enriched=0,source_roles_reviewed=7,new_reviews=7,unchanged_unrelated_observations=2717,unchanged_unrelated_inventory_rows=445,scoped_families=157,native=29,upstream=128,including_existing_adapters=160,method_overlap=19,missing=255,new_native_family_credit=0,new_upstream_family_credit=1,added_positive_observations=1,removed_positive_observations=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
