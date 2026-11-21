"""Admit one original PEER upstream family and eighteen literal paper roles."""
from collections import Counter
import copy,html,json,re
from peer_catalog import primary as p
from limma_catalog.model import META,DATA,NOTICE as PREVIOUS_NOTICE

GUIDE='docs/literature/PEER_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/peer-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/PeerWorkflowCli.java'
# Generic lowercase peer review and the person name Peer are deliberately excluded.
PATTERN=r'(?-i:(?<![A-Za-z0-9_])PEER(?![A-Za-z0-9_]))|(?i:probabilistic[\s-]+estimations?[\s-]+of[\s-]+(?:gene[\s-]+)?expression[\s-]+residuals?)'
SCOPE=('Global peer-tools and standalone PeerWorkflowCli expose RUN, offline VERIFY and REPLAY '
 'through the unchanged original dense PMBio PEER 1.3 source at '
 '40bc4b2cd92459ce42f44dfe279717436395f3f6, with FP32 matrices and an owned observer. '
 'Explicit prepared matrices, known covariates, measurement variance, priors, initialization, '
 'seed and stopping options retain original model states, warnings, histories and failures. '
 'VERIFY checks inventory, source/input bindings, structural and selected arithmetic invariants '
 'and exports without refitting or authenticating. REPLAY preserves parent bytes and lineage.')
NOTE=('PEER receives one upstream family credit. The separate legacy native Java Peer API '
 'remains unchanged and has no stock-output qualification: FP64, Java random initialization '
 'and the initial second moment differ from the original core. No native fallback or second '
 'family credit. Eighteen paper roles include supplied GTEx factors and sensitivity-only use; '
 'they are not eighteen newly executed fitting pipelines. Only P212 explicitly reports 1.3; '
 'no paper-executed commit is established. Preserve P213 four-versus-six PCs, P219 distinct '
 'BMI/LMM residual definitions, P203 higher-BIC wording and P249 unresolved logistically '
 'corrected wording. Author normalization, factor-count/QTL search, downstream models, '
 'sparse prior-guided PEER, older editions and whole-paper reproduction remain unqualified. '
 'Build current source; released 0.4.0 predates peer-tools.')
CURRENT=('250 papers, 446 named tool labels, 2,720 observations, 1,616 individually reviewed '
 'positive uses and 1,104 pending. There are 143 scoped integrations (26 native Java and '
 '117 upstream), two validated family aliases with zero extra credit, 259 missing and '
 '30 method-overlap labels; 146 integrations include three existing adapters. All 1,598 '
 'earlier reviews remain exact. Full implementation and paper-wide semantic completeness '
 'remain unfinished.')
ACTION=('SKIP3: DEPENDENT ANALYSIS SKIPPED; supply the named exact permitted source, input, '
 'runtime/package or environment and retry NEW output, or retain the skip if unavailable. '
 'Downloads and private installation are authorized where the environment permits; absence '
 'is not an installation prohibition. If installation is prohibited, name the affected '
 'analysis and exact prerequisite and skip it. FAIL2: NO COMPLETE RESULT; preserve raw '
 'inputs, initial/fitted/null/partial objects, histories, warnings and failed attempts, '
 'resolve the named data/design, numerical/convergence, source/integrity, capacity or process '
 'cause and retry NEW output. Never silently repair identifiers, populations, thresholds, '
 'editions, estimators or nonfinite values. Read analysis-status.json, WARNINGS.md and '
 'original logs. Inspect only the recorded owned process group if cleanup fails. Unknown '
 'editions are not substituted; interrupted runs without final status are incomplete. '
 'CMake and g++ are needed to build the private runtime; Windows WSL transport additionally '
 'needs Python3 in the explicit distribution. Cell Ranger stays excluded. Both literature '
 'reports stay withheld from Pages. Ignored exact runtimes, JARs, full text and complete '
 'private evidence need a separate checksummed transfer.')
NOTICE=('<aside id="peer-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
 'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>PEER: original dense runtime and 18 reviewed source roles</h2><p><strong>Warnings '
 'and required actions:</strong> '+html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)+'</p><p>'
 +html.escape(SCOPE+' '+NOTE)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'
 +GUIDE+'">Exact source roles, scope and recovery</a>.</p></aside>')
encoded=p.encoded
require=p.require

def registry():
    from limma_catalog.model import registry as preceding
    rows=preceding();require(len(rows)==485,'Different preceding registry population')
    row=next(r for r in rows if r['id']=='peer')
    require(row['current_coverage']=='method-overlap','Different/already admitted PEER state')
    row.update(pattern=PATTERN,current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE)
    return rows

def validate(before,primary,qualification):
    require(set(before)==set(META),'Exactly eight preceding metadata files required')
    p.validate(primary);require(primary==p.frozen(),'Different qualified PEER primary source review')
    require(qualification['status']=='PASS_SAVED_PEER_GLOBAL_ORIGINAL_DENSE_AND18_ROLE_PROPOSALS'
        and qualification['packaged_tests_per_JVM']==26 and qualification['JVMs']==3
        and qualification['original_control_cases']==24 and qualification['original_completed_controls']==6
        and qualification['explicit_fixed_iteration_partials']==18 and qualification['observer_unchanged_original_files']==120
        and qualification['global_examples']==6 and qualification['global_example_verifications']==6
        and qualification['actual_global_replays']==3 and qualification['source_reviewed_labels']==18
        and qualification['source_reviewed_papers']==18 and qualification['source_semantic_controls_per_host']==16
        and qualification['source_control_hosts']==2 and qualification['source_inverse_refusals']==7
        and qualification['global_command_registered'] and not qualification['legacy_native_Peer_stock_parity_qualified']
        and qualification['new_catalog_credit']==0 and not qualification['whole_goal_complete'],'Exact original/global PEER qualification required')
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);reviews=json.loads(before[META[5]])
    require(len(papers)==250 and len(inventory)==446 and sum(len(r['tools']) for r in papers)==2717
        and len(reviews['reviews'])==1598,'Different preceding catalog population')
    require(not any(r['tool_id']=='peer' for r in reviews['reviews']),'Existing PEER reviews must not be overwritten')
    row=next(r for r in inventory if r['id']=='peer')
    require(row['implementation_status']==row['current_coverage']=='method-overlap' and row['paper_count']==15
        and row['papers']==primary['original_observations'],'Different/already admitted PEER inventory')
    identities={r['paper_id']:r for r in primary['source_identities']}
    for paper in papers:
        pid=paper['paper_id'];require(all(paper[k]==v for k,v in identities[pid].items()),'Frozen paper identity changed')
        require(sum(r['tool_id']=='peer' for r in paper['tools'])==int(pid in primary['original_observations']),'Different existing PEER membership: '+pid)
    return primary['roles']

def transition(before,primary,qualification):
    roles=validate(before,primary,qualification)
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before[META[5]])
    byid={r['paper_id']:r for r in papers}
    for role in roles:
        paper=byid[role['paper_id']];pointer=role['primary_evidence'][0]
        if role['proposed_observation']:paper['tools'].append(dict(tool_id='peer',tool='PEER',matched_text=role['source_name_witness']['literal'],extraction_score=0))
        use=next(r for r in paper['tools'] if r['tool_id']=='peer')
        edition=role['reported_edition'];version=edition['literal'] if edition else None
        text=role['usage_summary']+(' Reported PEER edition: '+version+'.' if version else '')
        caution=role['required_caution']+' This source review is not a complete author workflow or biological reproduction.'
        use.update(analysis_role='PEER: '+role['kind'],use_summary=text,implementation_scope_note=caution,
            matched_text=role['source_name_witness']['literal'],evidence_section=pointer['section'],evidence_paragraph_index=pointer['paragraph_index'],
            evidence_paragraph_sha256=pointer['paragraph_sha256'],evidence_locator=paper['fulltext_url']+' (section: '+pointer['section']+')',
            evidence_selection='individually-reviewed-source-role',usage_context='analysis-method-description',
            review_status='usage-summary-reviewed; paper-wide-completeness-review-open',version_reported_near_name=version,source_role_qualification=copy.deepcopy(role))
        support=copy.deepcopy(role['primary_evidence'][1:])
        if support:use['supporting_evidence']=support
        else:use.pop('supporting_evidence',None)
        record=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='peer',evidence_paragraph_sha256=pointer['paragraph_sha256'],use_summary=text,implementation_scope_note=caution)
        if version:record['version_reported_near_name']=version
        if support:record['supporting_evidence']=support
        reviews['reviews'].append(record)
    row=next(r for r in inventory if r['id']=='peer')
    row.update(previous_method_overlap_source=row['existing_source'],pattern=PATTERN,current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',
        existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE+' '+NOTE,implementation_scope=SCOPE+' '+NOTE,
        validation_note='Unchanged pinned original dense C++ core, actual original controls and global examples/replays; 26 packaged configured tests per Windows21/25/Linux21 JVM. VERIFY does not refit. Legacy native Peer has no stock-output qualification.',
        implementation_family_id='peer',new_scoped_integration_credit=1,upstream_family_credit_added=1,native_family_credit_added=0,
        source_role_coverage=COVERAGE.rsplit('/',1)[-1],source_roles_reviewed=18,current_documentation=GUIDE,
        workflow_qualification='../peer-workflow-checkpoint/PROFILE.json',public_CLI_available=True,global_CLI_dispatch_available=True,
        qualified_component_entrypoints=['peer-tools --request RUN/VERIFY/REPLAY','org.jlinalg.cli.PeerWorkflowCli'],
        source_build_required=True,released_binary_contains_workflow_adapter=False,legacy_native_Peer_stock_parity_qualified=False,
        paper_role_qualification={r['paper_id']:copy.deepcopy(r) for r in roles},whole_tool_qualified=False,whole_paper_reproduced=False)
    row['papers']=[r['paper_id'] for r in papers if any(t['tool_id']=='peer' for t in r['tools'])];row['paper_count']=len(row['papers'])
    counts=dict(Counter(r['implementation_status'] for r in inventory))
    require(counts['upstream-adapter-validated']==117 and counts['native-subset-validated']==26 and counts['validated-family-alias']==2
        and counts['method-overlap']==30 and counts['missing']==259,'Incorrect PEER family credit')
    summary.update(implementation_status_counts=counts,paper_tool_observations=sum(len(r['tools']) for r in papers),integration_family_counts=dict(scoped=143,native=26,upstream=117,including_existing_adapters=146,validated_alias_labels_excluded_from_extra_credit=2))
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[5],reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before[META[6]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Different preceding embedded report')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    for name,text in [(META[6],page),(META[7],before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE)==1 and 'id="peer-catalog-notice"' not in text,'Different/already admitted report notice')
        text=text.replace(PREVIOUS_NOTICE,'<details><summary>Previous limma checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1)
        body=re.search(r'<body[^>]*>',text,re.I);require(body is not None,'Report body missing');after[name]=(text[:body.end()]+NOTICE+text[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
        after[name]=('# '+title+'\n\n## PEER: one original-runtime family and 18 reviewed roles\n\n**Warnings and required actions:** '+ACTION+'\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\nRead [the source crosswalk](../PEER_CATALOG_INTEGRATION.md).\n\n<details><summary>Before PEER admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after

def check(before,after,primary,qualification):
    require(after==transition(before,primary,qualification),'Different deterministic PEER source-bound transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity/metadata')
        require([r for r in a['tools'] if r['tool_id']!='peer']==[r for r in b['tools'] if r['tool_id']!='peer'],'Changed unrelated observation')
        expected=[r['tool_id'] for r in a['tools']]+(['peer'] if a['paper_id'] in primary['proposed_additions'] else [])
        require([r['tool_id'] for r in b['tools']]==expected,'Unreviewed observation addition/removal/order change')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]])
    require([r for r in a if r['id']!='peer']==[r for r in b if r['id']!='peer'],'Changed unrelated 445 inventory rows')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]])
    require(b['reviews'][:1598]==a['reviews'] and len(b['reviews'])==1616 and all(r['tool_id']=='peer' for r in b['reviews'][1598:]),'Changed previous reviews or append scope')
    require({k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'}
        and len({(r['pmcid'],r['tool_id']) for r in b['reviews']})==1616,'Changed review metadata or duplicate identity')
    counts=Counter(t['tool_id'] for paper in new for t in paper['tools']);require(len(counts)==446 and sum(counts.values())==2720,'Different named/observation population')
    for row in json.loads(after[META[1]]):
        require(row['paper_count']==counts[row['id']] and row['papers']==[r['paper_id'] for r in new if any(t['tool_id']==row['id'] for t in r['tools'])],'Inventory membership/count differs')
    return dict(papers=250,named_tools=446,registry_definitions=485,observations=2720,reviewed_uses=1616,pending_uses=1104,
        existing_reviews_retained=1598,new_review_records=18,positive_peer_labels=18,positive_papers=18,observations_added=3,observations_removed=0,
        integrations=143,native_integrations=26,upstream_integrations=117,including_existing_adapters=146,validated_family_alias_labels=2,
        alias_extra_integration_credit=0,method_overlap=30,missing=259,new_scoped_integration_credit=1,upstream_family_credit_added=1,native_family_credit_added=0,
        legacy_native_Peer_stock_parity_qualified=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
