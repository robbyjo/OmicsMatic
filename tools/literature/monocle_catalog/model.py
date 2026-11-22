"""Admit one bounded Monocle family and revise nine existing scopes without duplicate reviews."""
from collections import Counter
import copy,html,json,re
from monocle_catalog import primary as p
from twosamplemr_catalog.model import DATA,META,NOTICE as PREVIOUS_NOTICE

GUIDE='docs/literature/MONOCLE_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/monocle-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/MonocleToolsCli.java'
PY_SCOPE=('The paper calls py-monocle a Python wrapper; upstream describes a separate Python implementation with graph-handling changes. Exact paper commit and settings are unstated. The inspected Community-Use License permits noncommercial use only. '
 'The recorded intended use category is educational/research/nonprofit; the earlier waiting-for-use-category note is stale. No py-monocle runtime or analysis is qualified here, and its missing implementation status is unchanged. R/native Monocle controls do not establish py-monocle equivalence. '
 'Preserve the eligible microglia root population, 200 trials and Braak-correlation selection; that selected correlation is not independent predictive validation.')
SCOPE=('One scoped Monocle upstream family exposes exact original 1.0.0 graph/neighbors, 1.3.1 models, 1.3.3 graphs, 1.3.7/Batchelor 1.20 alignment, selected Monocle2 2.26.0 differential tests and selected 1.4.27 workflows through monocle-tools. '
 'Native supplied-graph ordering, 1.0.0 weights, explicit response/metadata/design preparation and residual-Moran helpers add no second family credit. Each operation requires its own complete declared input, edition, settings, roots and admission flags. No automatic provider, edition, root, assay or covariate substitution is made.')
NOTE=('Nine existing Monocle source-role scopes and one related P024 py-monocle policy note are updated; no observations or review records are added. Four reported package editions have qualified component controls; unreported paper patches/settings and executed commits remain unknown. '
 'P021 reports UMAT and a modified test; the author fork was not executed. Native whole-family numerical/self-arc refusal differs from author per-gene FAIL/OK-only BH handling. P024 uses separate unqualified py-monocle; the recorded research/nonprofit use category is already answered. '
 'P043 is Cicero platform context, not a trajectory run. Cicero and Batchelor already have separate scoped rows and gain no extra credit here. P051 exact 1.3.7/Batchelor 1.20 and P085 exact 1.3.1 runtime controls now exist; older skip/source-candidate notes are historical. '
 'P085 original t/grouped-Holm behavior and paper Wald/FDR/documentation BH wording remain distinct. P099 patch/formulas/units are unresolved; source-family failures are unadmitted. P117 settings/root choices, P134 protein activity and P233 separate FD/WT objects remain explicit. '
 'Source-build availability and synthetic original controls do not establish whole-tool, whole-paper or biological reproduction.')
ACTION=('SKIP3: DEPENDENT ANALYSIS SKIPPED; supply the named exact permitted runtime, profile, input or path, or retain the skip. Downloads/private installation are authorized where the operating environment permits them; absence alone is not a prohibition. '
 'If installation is disallowed, name the affected analysis, reason and exact item the user must supply. FAIL2: NO COMPLETE RESULT; preserve raw/original/null/failed/partial evidence and logs, correct the named data/design, rank, numerical, source/integrity, capacity or process cause, and retry NEW output. '
 'Do not silently repair IDs, drop cells/genes, replace nonfinite values, infer roots or substitute editions/providers. Read the selected stage WARNINGS.md, completion and admission flags; preparation/graph/weights do not admit a gene test. '
 'Retain finite original R inference from unresolved rounding residuals as source evidence; native refuses the affected complete testing family. Keep numeric-zero probabilities with log-p. Cell Ranger remains excluded. Both unfinished reports remain off Pages. '
 'Ignored runtimes, JARs, full text, private author source and complete private evidence require a separate checksummed transfer before machine retirement.')
CURRENT=('250 papers, 446 named labels and 2,722 observations remain unchanged, with 1,630 reviewed uses and 1,092 pending. There are 145 scoped integrations (26 native Java + 119 upstream), two zero-credit aliases, 258 missing and 29 method-overlap labels; 148 include three existing adapters. '
 'This adds one Monocle family, updates nine existing Monocle scopes and corrects one related py-monocle policy note, with zero duplicate review credit. Full implementation and paper-wide semantic completeness remain unfinished.')
NOTICE=('<aside id="monocle-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>Monocle: scoped components and nine existing source-role updates</h2><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE+' '+NOTE)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Exact roles,qualified scope and recovery</a>.</p></aside>')
encoded=p.encoded
require=p.require

def registry():
    from twosamplemr_catalog.model import registry as previous
    rows=previous();require(len(rows)==485,'Different preceding registry population');row=next(row for row in rows if row['id']=='monocle')
    require(row['current_coverage']=='missing' and row['existing_source'] is None,'Different/already admitted Monocle provider')
    row.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE);return rows

def validate(before,primary,qualification):
    require(set(before)==set(META),'Exactly eight preceding metadata files required');p.validate(primary)
    require(qualification['status']=='PASS_CURRENT_MONOCLE_COMPONENTS_FOR_CATALOG_ADMISSION' and qualification['packaged_tests_per_JVM']==889 and qualification['packaged_JVMs']==3
        and qualification['current_exact_edition_original_runs']==4 and qualification['current_scientific_files_byte_identical']==29
        and qualification['native_response_cases']==6 and qualification['native_response_values']==984 and qualification['admitted_original_gene_tests']==54 and qualification['retained_degenerate_family_refusals']==3
        and qualification['source_papers_per_host']==250 and qualification['source_roles']==9 and qualification['source_hosts']==2
        and qualification['global_command_registered'] and qualification['new_catalog_credit']==0 and not qualification['whole_goal_complete'],'Exact current original/native qualification required')
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);reviews=json.loads(before[META[5]])
    require(len(papers)==250 and len(inventory)==446 and sum(len(row['tools']) for row in papers)==2722 and len(reviews['reviews'])==1630,'Different preceding catalog population')
    require(len({(row['pmcid'],row['tool_id']) for row in reviews['reviews']})==1630 and sum(row['tool_id']=='monocle' for row in reviews['reviews'])==9,'Different existing review identities')
    row=next(row for row in inventory if row['id']=='monocle');require(row['implementation_status']==row['current_coverage']=='missing' and row['paper_count']==9 and row['papers']==list(p.ROLES),'Different/already admitted Monocle inventory')
    require(next(row for row in inventory if row['id']=='batchelor')['implementation_status']=='upstream-adapter-validated' and next(row for row in inventory if row['id']=='cicero')['implementation_status']=='upstream-adapter-validated' and next(row for row in inventory if row['id']=='py-monocle')['implementation_status']=='missing','Changed separate provider boundaries')
    identity={row['paper_id']:row for row in primary['source_identities']};roles={row['paper_id']:row for row in primary['roles']}
    for paper in papers:
        require(all(paper[key]==value for key,value in identity[paper['paper_id']].items()),'Changed frozen paper identity')
        uses=[row for row in paper['tools'] if row['tool_id']=='monocle'];require(len(uses)==int(paper['paper_id'] in roles),'Changed Monocle membership')
        if not uses:continue
        use=uses[0];role=roles[paper['paper_id']];anchor=role['primary_evidence'][0]
        require(use['use_summary']==role['usage_summary'] and use['evidence_paragraph_index']==anchor['paragraph_index'] and use['evidence_paragraph_sha256']==anchor['paragraph_sha256'] and use['evidence_section']==anchor['section'],'Changed existing reviewed source anchor or usage summary')
        expected=role['reported_edition']['literal'] if role['reported_edition'] else None;require(use.get('version_reported_near_name')==expected,'Changed existing reported edition')
        record=next(row for row in reviews['reviews'] if row['pmcid']==paper['pmcid'] and row['tool_id']=='monocle');require(record['use_summary']==use['use_summary'] and record['implementation_scope_note']==use['implementation_scope_note'] and record['evidence_paragraph_sha256']==anchor['paragraph_sha256'],'Existing review and paper scope differ')
    return primary['roles']

def transition(before,primary,qualification):
    roles=validate(before,primary,qualification);papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before[META[5]]);byid={row['paper_id']:row for row in papers}
    for role in roles:
        paper=byid[role['paper_id']];use=next(row for row in paper['tools'] if row['tool_id']=='monocle');use.update(analysis_role=role['analysis_role'],implementation_scope_note=role['implementation_scope_note'],source_role_qualification=copy.deepcopy(role))
        record=next(row for row in reviews['reviews'] if row['pmcid']==paper['pmcid'] and row['tool_id']=='monocle');record['implementation_scope_note']=role['implementation_scope_note']
    related=byid['P024'];py_use=next(row for row in related['tools'] if row['tool_id']=='py-monocle');require(py_use['evidence_paragraph_index']==107 and py_use['evidence_paragraph_sha256']==next(row for row in related['tools'] if row['tool_id']=='monocle')['evidence_paragraph_sha256'],'Changed related py-monocle source anchor');py_use['implementation_scope_note']=PY_SCOPE
    next(row for row in reviews['reviews'] if row['pmcid']==related['pmcid'] and row['tool_id']=='py-monocle')['implementation_scope_note']=PY_SCOPE
    row=next(row for row in inventory if row['id']=='monocle')
    row.update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE+' '+NOTE,implementation_scope=SCOPE+' '+NOTE,
        validation_note='Current normal JAR:889 focused tests per packaged Windows21/25/Linux21 JVM;six complete native response controls with984 values,54 admitted original inference tests and three retained degenerate-family refusals. Four exact-edition current-JAR original R executions match29 complete scientific output files. See the source crosswalk and immutable qualification receipts.',
        source_roles_reviewed=9,existing_role_scopes_updated=9,new_review_records=0,source_role_coverage=COVERAGE.rsplit('/',1)[-1],current_documentation=GUIDE,workflow_qualification='../monocle-response-checkpoint/PROFILE.json',
        canonical_adapter_available=True,public_CLI_available=True,global_CLI_dispatch_available=True,source_build_required=True,release_asset_qualification='Not established by this catalog update;inspect versioned release metadata.',
        qualified_component_entrypoints=['monocle-tools --help',PROGRAM],native_helpers_additional_family_credit=0,separate_cicero_batchelor_credit_added=0,paper_role_qualification={role['paper_id']:copy.deepcopy(role) for role in roles},whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
    counts=dict(Counter(row['implementation_status'] for row in inventory));require(counts['upstream-adapter-validated']==119 and counts['native-subset-validated']==26 and counts['missing']==258 and counts['method-overlap']==29 and counts['validated-family-alias']==2,'Incorrect Monocle/helper/provider family credit')
    summary.update(implementation_status_counts=counts,integration_family_counts=dict(scoped=145,native=26,upstream=119,including_existing_adapters=148,validated_alias_labels_excluded_from_extra_credit=2))
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[5],reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before[META[6]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Different preceding embedded report')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    for name,text in [(META[6],page),(META[7],before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE)==1 and 'id="monocle-catalog-notice"' not in text,'Different/already admitted report notice');text=text.replace(PREVIOUS_NOTICE,'<details><summary>Previous TwoSampleMR checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1);body=re.search(r'<body[^>]*>',text,re.I);require(body is not None,'Missing report body');after[name]=(text[:body.end()]+NOTICE+text[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
        after[name]=('# '+title+'\n\n## Monocle: one scoped family and nine existing role updates\n\n**Warnings and required actions:** '+ACTION+'\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\nRead [the source crosswalk](../MONOCLE_CATALOG_INTEGRATION.md).\n\n<details><summary>Before Monocle admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after

def check(before,after,primary,qualification):
    require(after==transition(before,primary,qualification),'Different deterministic source-bound Monocle transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]]);updated=0
    for a,b in zip(old,new,strict=True):
        require({key:value for key,value in a.items() if key!='tools'}=={key:value for key,value in b.items() if key!='tools'},'Changed paper identity/metadata')
        affected={'monocle','py-monocle'} if a['paper_id']=='P024' else {'monocle'}
        require([row for row in a['tools'] if row['tool_id'] not in affected]==[row for row in b['tools'] if row['tool_id'] not in affected] and [row['tool_id'] for row in a['tools']]==[row['tool_id'] for row in b['tools']],'Changed unrelated observation or membership/order')
        for previous,current in zip(a['tools'],b['tools'],strict=True):
            if a['paper_id']=='P024' and previous['tool_id']=='py-monocle':
                require({key:value for key,value in previous.items() if key!='implementation_scope_note'}=={key:value for key,value in current.items() if key!='implementation_scope_note'} and current['implementation_scope_note']==PY_SCOPE,'Changed related py-monocle fields beyond its policy note');continue
            if previous['tool_id']!='monocle':continue
            allowed={'analysis_role','implementation_scope_note','source_role_qualification'};require({key:value for key,value in previous.items() if key not in allowed}=={key:value for key,value in current.items() if key not in allowed},'Changed literal Monocle source evidence or usage summary');updated+=1
    require(updated==9,'Different revised role count');a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([row for row in a if row['id']!='monocle']==[row for row in b if row['id']!='monocle'],'Changed unrelated445 inventory rows')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]]);require(len(a['reviews'])==len(b['reviews'])==1630 and {key:value for key,value in a.items() if key!='reviews'}=={key:value for key,value in b.items() if key!='reviews'},'Changed review metadata or count')
    updates=0
    for previous,current in zip(a['reviews'],b['reviews'],strict=True):
        if previous['tool_id']=='monocle':require({key:value for key,value in previous.items() if key!='implementation_scope_note'}=={key:value for key,value in current.items() if key!='implementation_scope_note'},'Changed existing review identity/source/summary');updates+=1
        elif previous['tool_id']=='py-monocle' and previous['pmcid']=='PMC13600946':require({key:value for key,value in previous.items() if key!='implementation_scope_note'}=={key:value for key,value in current.items() if key!='implementation_scope_note'} and current['implementation_scope_note']==PY_SCOPE,'Changed related py-monocle review beyond policy note')
        else:require(previous==current,'Changed unrelated existing review')
    require(updates==9 and len({(row['pmcid'],row['tool_id']) for row in b['reviews']})==1630,'Duplicate or missing review')
    counts=Counter(tool['tool_id'] for paper in new for tool in paper['tools']);require(len(counts)==446 and sum(counts.values())==2722,'Different named/observation population')
    for row in json.loads(after[META[1]]):require(row['paper_count']==counts[row['id']] and row['papers']==[paper['paper_id'] for paper in new if any(tool['tool_id']==row['id'] for tool in paper['tools'])],'Inventory membership/count differs')
    return dict(papers=250,named_tools=446,registry_definitions=485,observations=2722,reviewed_uses=1630,pending_uses=1092,unchanged_unrelated_reviews=1620,existing_scope_records_updated=9,related_policy_scope_records_updated=1,total_existing_review_records_updated=10,new_review_records=0,observations_added=0,observations_removed=0,integrations=145,native_integrations=26,upstream_integrations=119,including_existing_adapters=148,validated_family_alias_labels=2,method_overlap=29,missing=258,new_scoped_integration_credit=1,upstream_family_credit_added=1,native_family_credit_added=0,separate_provider_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
