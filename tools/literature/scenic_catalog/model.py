"""Admit one upstream SCENIC workflow and five roles without altering other records."""
from collections import Counter
import copy,html,json,re
from smartpca_catalog import model as previous
from scenic_catalog import primary as p

ROOT,META,DATA=p.ROOT,previous.META,previous.DATA
TARGET='scenic';PROGRAM='src/main/java/org/jlinalg/cli/ScenicToolsCli.java'
GUIDE='docs/literature/SCENIC_WORKFLOWS.md';COVERAGE=DATA+'/scenic-source-role-coverage.json';PROOF=DATA+'/scenic-catalog-validation.json'
REVIEWED=previous.REVIEWED;require=p.require;encoded=p.encoded
SCOPE=('Pinned pySCENIC0.12.1/Arboreto0.1.6/ctxcore0.2.0 Linux x86_64 Python3.10.22 upstream adapter plus native Java AUCell. '
 'Global scenic-tools RUN, full original GRN/CTX and native activity recomputation VERIFY, actual REPLAY and metadata-only inspection. '
 'Prepared literal matrices, explicit TFs/motif rankings/annotations, separate fitting/activity populations, original GRNBoost2 or GENIE3, '
 'complete target/module task ledgers and bounded batched cell scoring. No automatic sampling, normalization, filtering, ID repair or reference selection. '
 'Only activity fields flagged eligible are numerically admitted; explicit empty/unreportable policies do not repair undefined fields. '
 'No earlier-edition equivalence, author environment, whole-tool/paper/biological reproduction, authenticity or OS containment is established.')
NOTE=('Five RNA-only roles are individually reviewed. P058 uses raw Seurat counts, mouse resources and NES2.5/min_genes10; its edition is unreported. '
 'P070 reports0.10.3: exact historical analysis is SKIP3 under the available0.12.1 profile; do not substitute editions. '
 'P100 reports0.12.1,5percent per-broad-cell-type fitting downsampling, dropout masking and v9/mc9nr promoter resources. '
 'P145 reports0.12.1 with20,000 normalized/log-transformed fitting nuclei, full-dataset activity and distinct v10 resources. '
 'P225 is an orthogonal comparator with unreported edition/options. Preserve unknown raw near-name editions, unreported seeds and missing exact resource bytes. '
 'Downstream scaling, RSS, Wilcoxon, motif-assignment harmonization and visualization are separate. SCENIC+ and missed P153 remain pending.')
ACTION=('SKIP3: dependent analysis skipped. Supply/install the named permitted exact Linux runtime, matching inputs and exact producer JAR; otherwise retain the skip. '
 'P070 historical0.10.3 is unsupported by this profile. FAIL2: no complete result. Preserve raw/partial/failed/undefined evidence, correct the stated '
 'input, source, numerical, resource, deadline or integrity cause and retry NEW output. Read WARNINGS.md, analysis-status.json, every task ledger and '
 'per-field eligibility. Require full VERIFY for fresh scientific recomputation; inspect checks metadata only. Never replace NA/NaN/Inf, repair IDs '
 'or refresh historical hashes. Cell Ranger remains excluded. Privately transfer required runtimes, exact JARs and original evidence.')
CURRENT=('250 papers,446 labels,485 provider definitions and2,722 positive observations;1,702 reviewed/1,020 pending uses. '
 'There are155 scoped families (29 native +126 upstream),158 including three existing adapters;257 missing and19 method-overlap. '
 'One upstream SCENIC family is added; its native AUCell component receives no separate family credit. The full goal remains unfinished.')
NOTICE=('<aside id="scenic-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;padding:1rem;'
 'border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere"><h2>SCENIC workflow and paper uses</h2>'
 '<p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)+'</p>'
 '<p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Prepare inputs, run SCENIC and resolve skips or failures</a>.</p>'
 '<details><summary>Source scope and reported versions</summary><p>'+html.escape(SCOPE+' '+NOTE)+'</p></details></aside>')

def registry():
    rows=previous.registry();target=next(r for r in rows if r['id']==TARGET)
    require(len(rows)==485 and target['current_coverage']=='missing','Changed predecessor registry')
    target.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE);return rows

def applied(role):
    value=copy.deepcopy({k:v for k,v in role.items() if k!='original_observation'});value['source_role_review_applied']=True;return value

def validate(before,primary,qualified):
    require(set(before)==set(META) and primary==p.frozen(),'Changed predecessor or primary interpretation')
    require(qualified==json.loads((ROOT/'docs/literature/scenic-workflow-checkpoint-20261123/STATUS.json').read_bytes()) and
        qualified['bounded_workflow_qualification'] and not any(qualified[k] for k in ('whole_tool_qualified','catalog_admitted','whole_goal_complete')) and
        qualified['normal_tests']==dict(tests=2069,failures=0,errors=0,skipped=0) and qualified['producer']['sha256']=='ae5f81ed6a520a1a9f4be32f6558ecb2120b20ffbaa2248b701f8aafcecf3a2a' and
        qualified['live_checks']==dict(original_comparisons=4,maximum_activity_error=0.0,workflow_scenarios=14,integrity_cases=11,integrity_commands=13,unit_methods_per_host=10,unit_hosts=['windows21','windows25','linux21'],example_modes=3,actual_after_fork_timeout=True),'Incomplete or changed workflow qualification')
    papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    require(len(papers)==250 and len(inventory)==446 and sum(len(x['tools']) for x in papers)==2722 and len(ledger['reviews'])==1697,'Changed predecessor population')
    require(summary['integration_family_counts']==dict(scoped=154,native=29,upstream=125,including_existing_adapters=157,validated_alias_labels_excluded_from_extra_credit=3),'Changed predecessor counts')
    target=next(x for x in inventory if x['id']==TARGET)
    require(target['current_coverage']==target['implementation_status']=='missing' and target['papers']==p.POSITIVE,'Changed target status/population')
    require(not any(r['tool_id']==TARGET for r in ledger['reviews']),'Duplicate source-review credit')
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id'])
        require([u for u in paper['tools'] if u['tool_id']==TARGET]==[role['original_observation']] and paper['xml_sha256']==role['xml_sha256'],'Changed original extraction')

def transition(before,primary,qualified):
    validate(before,primary,qualified);papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id']);use=next(u for u in paper['tools'] if u['tool_id']==TARGET)
        pointers=[copy.deepcopy(r) for r in role['anchors'] if r['paragraph_index']!=use['evidence_paragraph_index']];scope=role['boundary']+' Current availability: '+SCOPE
        use.update(use_summary=role['summary'],implementation_scope_note=scope,review_status=REVIEWED,usage_context='analysis-method-description',evidence_selection='individually-reviewed-source-role',supporting_evidence=pointers,source_role_qualification=applied(role))
        ledger['reviews'].append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=TARGET,evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['summary'],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],supporting_evidence=copy.deepcopy(pointers),source_table_evidence=[],reported_version_contexts=copy.deepcopy(role['reported_version_contexts']),reported_settings=copy.deepcopy(role['reported_settings']),historical_edition_analysis_status=role['historical_edition_analysis_status']))
    target=next(x for x in inventory if x['id']==TARGET)
    target.update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE,implementation_scope=SCOPE,current_documentation=GUIDE,current_qualification='../scenic-workflow-checkpoint-20261123/STATUS.json',upstream_runtime_adapter_required=True,native_component_available=True,public_CLI_available=True,global_CLI_dispatch_available=True,source_build_required=True,released_binary_contains_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,new_native_family_credit=0,new_upstream_family_credit=1,source_roles_reviewed=5,source_role_coverage=COVERAGE.rsplit('/',1)[-1],paper_role_qualification={r['paper_id']:applied(r) for r in primary['roles']},validation_note='2,069 normal selected tests;10 focused methods per Windows21/25 and Linux21;four full independent original scientific comparisons, maximum observed activity error0;14 workflow scenarios;11 integrity cases/13 commands;actual RUN/VERIFY/REPLAY and all three runnable example modes. Complete raw failures remain distinct.',current_paper_role_limits=NOTE)
    summary['implementation_status_counts']=dict(Counter(x['implementation_status'] for x in inventory));summary['integration_family_counts']=dict(scoped=155,native=29,upstream=126,including_existing_adapters=158,validated_alias_labels_excluded_from_extra_credit=3)
    after=dict(before)
    for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    for name in (META[6],META[7]):
        page=before[name].decode();require(page.count(previous.NOTICE)==1 and 'id="scenic-catalog-notice"' not in page,'Changed previous notice')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
            require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed embedded catalog')
            payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
            require(page.count('${smartpcaVersion(t)}')==1 and page.count('function renderContent(){')==1,'Changed inherited version renderer')
            helper=("function scenicVersion(t){if(t.tool_id!=='scenic'||!t.source_role_qualification)return smartpcaVersion(t);const q=t.source_role_qualification;"
                "return '<br><span class=\"scenic-version\" style=\"overflow-wrap:anywhere\">'+(q.reported_package_edition===null?'package edition not reported':'reported package edition '+esc(q.reported_package_edition))+'; '+esc(q.historical_edition_analysis_status)+'</span>';}\n")
            page=page.replace('${smartpcaVersion(t)}','${scenicVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        page=page.replace(previous.NOTICE,'<details><summary>Before SCENIC admission (historical)</summary>'+previous.NOTICE+'</details>',1)
        body=re.search(r'<body[^>]*>',page,re.I);require(body is not None,'Missing body');after[name]=(page[:body.end()]+NOTICE+page[body.end():]).encode()
    prefix=('# Current SCENIC catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [workflow guide](../SCENIC_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in (META[3],META[4]):after[name]=prefix+before[name]
    return after

def check(before,after,primary,qualified):
    require(after==transition(before,primary,qualified),'Changed deterministic transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]]);unchanged=0
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity')
        require([u for u in a['tools'] if u['tool_id']!=TARGET]==[u for u in b['tools'] if u['tool_id']!=TARGET],'Changed unrelated use')
        unchanged+=sum(u['tool_id']!=TARGET for u in a['tools'])
        if b['paper_id'] in p.POSITIVE:
            x=next(u for u in a['tools'] if u['tool_id']==TARGET);y=next(u for u in b['tools'] if u['tool_id']==TARGET)
            for key in ('tool_id','tool','matched_text','extraction_score','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator','version_reported_near_name'):require(x[key]==y[key],'Changed literal extraction')
            require(y['source_role_qualification']['source_role_review_applied'],'Role not applied')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']!=TARGET]==[r for r in b if r['id']!=TARGET],'Changed unrelated inventory')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]])
    require(b['reviews'][:1697]==a['reviews'] and {k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'},'Changed prior ledger')
    require(len(b['reviews'])==len({(r['pmcid'],r['tool_id']) for r in b['reviews']})==1702,'Wrong unique review count')
    markers={REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open'}
    require({(paper['pmcid'],use['tool_id']) for paper in new for use in paper['tools'] if use['review_status'] in markers}=={(r['pmcid'],r['tool_id']) for r in b['reviews']},'Ledger/observations disagree')
    counts=Counter(r['implementation_status'] for r in json.loads(after[META[1]]));require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['method-overlap'],counts['missing'],unchanged)==(29,126,19,257,2717),'Wrong family counts')
    require(sum(len(x['tools']) for x in new)==2722,'Changed positive observation population')
    return dict(papers=250,labels=446,provider_definitions=485,observations=2722,reviewed_uses=1702,pending_uses=1020,unchanged_prior_reviews=1697,new_reviews=5,unchanged_unrelated_observations=2717,unchanged_unrelated_inventory_rows=445,scoped_families=155,native=29,upstream=126,including_existing_adapters=158,method_overlap=19,missing=257,new_native_family_credit=0,new_upstream_family_credit=1,added_positive_observations=0,removed_positive_observations=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
