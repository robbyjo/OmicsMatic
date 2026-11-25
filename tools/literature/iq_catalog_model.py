"""Native iq admission plus source-bound missing-provider catalog corrections."""
from pathlib import Path
from collections import Counter
import copy,html,json,re
from gemma_primary import encoded,require

ROOT=Path(__file__).resolve().parents[2]
DATA='docs/literature/omics-2023-2026'
META=[DATA+'/'+n for n in('papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md')]+['tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']
ADMISSION='docs/literature/iq-proteomics-catalog-admission'
PROGRAM='src/main/java/org/jlinalg/cli/IqToolsCli.java'
GUIDE='docs/literature/IQ_MAXLFQ_WORKFLOWS.md'
REVIEWED='usage-summary-reviewed; paper-wide-completeness-review-open'
EXISTING=frozenset(('diann','maxquant'))
NEW=frozenset(('iq','andromeda','maxdia','macs_iq_view'))
TARGETS=EXISTING|NEW
SCOPE=('Native Java iq2.3.0 fast_MaxLFQ aggregation of complete prepared normalized-log2 ion tables; '
       'source duplicate-cell linear sums, sample-pair medians, connected-component scaling/annotations, '
       'convergence and independent residual checks, complete rows/identities and exact-producer Windows/Linux '
       'capture/replay. One scoped native family. No raw MS search, FDR filtering, normalization, imputation, '
       'streaming iq format, complete iq package or historical paper reproduction is claimed.')
ACTION=('SKIP3: dependent analysis skipped when the named permitted prerequisite is unavailable; supply it '
        'or retain the skip. MaxQuant registration requires identity, CAPTCHA and licence acceptance; no '
        'licensed archive was supplied; the user requested skipping runtime analyses while keeping MaxQuant pending. Andromeda/MaxDIA are bundled '
        'components of that pending workflow, without additional family credit. DIA-NN raw-search integration '
        'remains unqualified despite private executable/library-generation pilots. MACS iQ View is a distinct '
        'pending imaging application. FAIL2: no complete result for invalid/changed data, capacity, cancellation, '
        'arithmetic, nonconvergence or finalization failures. Read capture warnings/audits and retain every '
        'attempt; correct the cause using NEW paths. Native iq requires already-normalized log2 quantities '
        'and a source-built JAR. Old releases predate it. Cell Ranger remains excluded, raw evidence/runtimes '
        'remain private, literature HTMLs remain withheld from Pages, and the full 250-paper goal stays open.')
CURRENT=('250 papers, 451 labels, 490 provider definitions and 2,731 observations; 1,802 reviewed/929 pending uses. '
         '165 scoped families (32 native + 133 upstream), 168 including three existing adapters. 256 labels remain '
         'missing, 13 method-overlap and five validated aliases. This checkpoint adds one native iq family, '
         'four newly named providers, five positive observations and 16 source-role reviews. Andromeda, MaxDIA '
         'and MACS iQ View remain missing; DIA-NN/MaxQuant remain missing. No duplicate family credit.')


def qualified(q):
    require(q['status']=='PASS_NATIVE_IQ_PREPARED_AGGREGATION_COMPLETE_CAPTURE'and q['Windows_focused_JUnit']==q['Linux_focused_JUnit']==24
            and q['original_fast_MaxLFQ_cases']==33 and q['native_captures_each_OS']==4
            and q['capture_and_refusal_calls_each_OS']==22 and q['foreign_host_captures_recomputed_each_OS']==4
            and q['DIA_NN_raw_search_qualified']is False and q['MaxQuant_runtime_qualified']is False
            and q['whole_package_qualified']is False and q['whole_paper_reproduced']is False
            and q['whole_goal_complete']is False,'Incomplete or expanded iq qualification')


def new_providers():
    return [dict(id='iq',name='iq',pattern=r'(?<![A-Za-z0-9_])(?:fast[\W_]*MaxLFQ|iq(?=\s*(?:::|package)))(?![A-Za-z0-9_])',role='Prepared precursor-to-protein MaxLFQ aggregation',current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=SCOPE,integration_family='iq',family_credit=1),
        dict(id='andromeda',name='Andromeda',pattern=r'(?<![A-Za-z0-9_])Andromeda(?![A-Za-z0-9_])',role='Bundled MaxQuant spectral search engine',current_coverage='missing',existing_source=None,coverage_note='Named search-engine component of pending MaxQuant workflow; no standalone installation, engine edition or extra family credit inferred.',integration_family='maxquant',component_of='maxquant',family_credit=0),
        dict(id='maxdia',name='MaxDIA',pattern=r'(?<![A-Za-z0-9_])MaxDIA(?![A-Za-z0-9_])',role='Embedded MaxQuant DIA search module',current_coverage='missing',existing_source=None,coverage_note='Embedded module of pending MaxQuant workflow; parent version is not an independently reported module edition; no extra family credit.',integration_family='maxquant',component_of='maxquant',family_credit=0),
        dict(id='macs_iq_view',name='MACS iQ View',pattern=r'MACS[\s-]*iQ[\s-]*View',role='Cyclic-image preprocessing and segmentation',current_coverage='missing',existing_source=None,coverage_note='Distinct imaging software; not the R iq protein-aggregation package. Exact licensed runtime and workflow remain pending.',family_credit=0)]


def registry(root=ROOT):
    rows=json.loads((Path(root)/ADMISSION/'PREVIOUS-REGISTRY.json').read_bytes());require(len(rows)==486 and not any(r['id']in NEW for r in rows),'Changed preceding provider registry')
    return rows+new_providers()


def applied(role):
    out=copy.deepcopy(role);out.update(source_role_review_applied=True,catalog_admitted=True,paper_edition_reproduced=False,
        raw_search_qualified=False,whole_package_qualified=False,new_family_credit=1 if role['tool_id']=='iq'else 0)
    return out


def transition(before,roles,q):
    qualified(q);papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in(0,1,2,5)]
    require((len(papers),len(inventory),sum(len(p['tools'])for p in papers),len(ledger['reviews']))==(250,447,2726,1786),'Changed prior catalog population')
    require(not any(r['tool_id']in TARGETS for r in ledger['reviews'])and len(roles['roles'])==16,'Duplicate/changed source-role population')
    require(all(r['implementation_available']==(r['tool_id']=='iq')and r['whole_paper_reproduced']is False for r in roles['roles']),'Pending runtime/provider promoted or paper claim expanded')
    require(all(r['literal_reported_version']is None for r in roles['roles']if r['tool_id']in('iq','andromeda','maxdia')),'Unreported package/engine edition inferred')
    require(next(r for r in roles['roles']if(r['paper_id'],r['tool_id'])==('P032','maxquant'))['literal_reported_version']=='2.4.7','Literal MaxQuant2.4.7 silently normalized')
    require(next(r for r in roles['roles']if(r['paper_id'],r['tool_id'])==('P029','diann'))['reported_settings']['reported_intensity_name_literal']=='maxLGQ','Source intensity spelling silently repaired')
    providers={r['id']:r for r in new_providers()}
    for role in roles['roles']:
        p=next(p for p in papers if p['paper_id']==role['paper_id']);tool=role['tool_id'];use=next((u for u in p['tools']if u['tool_id']==tool),None)
        if role['raw_source_fields']is None:
            require(use is None and tool in NEW,'Unexpected existing new-provider observation');a=role['primary_anchors'][0]
            use=dict(tool_id=tool,tool=providers[tool]['name'],analysis_role=providers[tool]['role'],matched_text=providers[tool]['name'],extraction_score=15,usage_context='analysis-method-description',evidence_section=a['section'],evidence_paragraph_index=a['paragraph_index'],evidence_paragraph_sha256=a['paragraph_sha256'],evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/'+p['pmcid']+'/ (section: '+a['section']+')',version_reported_near_name=role['literal_reported_version'],discovery='full250-manual-source-role-completeness-review');p['tools'].append(use)
        else:require(use is not None and {k:use.get(k)for k in role['raw_source_fields']}==role['raw_source_fields'],'Raw source/version changed')
        supporting=copy.deepcopy(use.get('supporting_evidence',[]))
        for a in role['primary_anchors']:
            if a['paragraph_index']!=use['evidence_paragraph_index']and not any(x.get('paragraph_index')==a['paragraph_index']for x in supporting):supporting.append(copy.deepcopy(a))
        scope=role['remaining_scope']+(' Available implementation: '+SCOPE if tool=='iq'else' Runtime search/imaging implementation remains pending; native iq does not substitute for it.')
        use.update(use_summary=role['use_summary'],review_status=REVIEWED,implementation_scope_note=scope,evidence_selection='individually-reviewed-source-role',supporting_evidence=supporting,source_role_qualification=applied(role))
        ledger['reviews'].append(dict(pmcid=p['pmcid'],doi=p['doi'],tool_id=tool,evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['use_summary'],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],supporting_evidence=copy.deepcopy(supporting),reported_settings=copy.deepcopy(role['reported_settings']),source_role_qualification=applied(role)))
    for row in inventory:
        if row['id']in EXISTING:
            require(row['implementation_status']=='missing','Pending vendor/raw-search provider was unexpectedly promoted')
            related=[r for r in roles['roles']if r['tool_id']==row['id']];row.update(paper_role_qualification={r['paper_id']:applied(r)for r in related},current_documentation=GUIDE,implementation_scope=' '.join(r['remaining_scope']for r in related),validation_note='Primary-source role review only. Native iq aggregation and DIA-NN startup/library pilots do not qualify the raw-search/vendor workflow.',whole_tool_qualified=False,whole_paper_reproduced=False)
            if row['id']=='maxquant':row.update(runtime_analysis_status='SKIP3_USER_REQUESTED_KEEP_TOOL_PENDING',runtime_analysis_required_action='Keep MaxQuant pending; provide an appropriate licensed archive and request resumed qualification before running its dependent analyses. No identity or CAPTCHA was fabricated.')
    for provider in new_providers():
        target=copy.deepcopy(provider);related=[r for r in roles['roles']if r['tool_id']==provider['id']];paper_ids=sorted({r['paper_id']for r in related});target.update(paper_count=len(paper_ids),papers=paper_ids,implementation_status=provider['current_coverage'],implementation_source=provider['existing_source'],implementation_scope=provider['coverage_note'],current_documentation=GUIDE,paper_role_qualification={r['paper_id']:applied(r)for r in related},whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
        if provider['id']=='iq':target.update(source_build_required=True,released_binary_contains_workflow=False,current_qualification='docs/literature/iq-maxlfq-validation/QUALIFICATION.json',validation_note='33 unmodified-original iq2.3.0 synthetic goldens;24 focused Java tests and22 capture/refusal calls per OS;four complete captures and foreign-host replay per OS; exact producer and full numeric readback. No author iq version is inferred.')
        else:target.update(validation_note='Source-role review only; implementation/runtime remains pending. No native-family credit.')
        inventory.append(target)
    counts=Counter(r['implementation_status']for r in inventory);summary.update(paper_tool_observations=sum(len(p['tools'])for p in papers),distinct_named_tools=len(inventory),implementation_status_counts=dict(counts),integration_family_counts=dict(scoped=counts['native-subset-validated']+counts['upstream-adapter-validated'],native=counts['native-subset-validated'],upstream=counts['upstream-adapter-validated'],including_existing_adapters=counts['native-subset-validated']+counts['upstream-adapter-validated']+counts['existing-adapter'],validated_alias_labels_excluded_from_extra_credit=counts['validated-family-alias']))
    after=dict(before)
    for name,value in((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value);after[name]=raw.replace(b'\n',b'\r\n')if before[name].count(b'\n')==before[name].count(b'\r\n')else raw
    notice=('<aside id="proteomics-catalog-notice" style="margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere"><h2>Native iq aggregation; raw proteomics searches pending</h2><p>'+html.escape(CURRENT)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Commands and source-role limits</a></p></aside>')
    for name in META[6:]:
        page=before[name].decode();require('id="proteomics-catalog-notice"'not in page and'id="metaxcan-catalog-notice"'in page,'Changed preceding report notice')
        if name==META[6]:
            m=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(m and json.loads(m[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed preceding embedded catalog')
            payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:m.start(2)]+payload+page[m.end(2):]
            require(page.count('${metaxcanVersion(t)}')==page.count('function renderContent(){')==1,'Changed source-role renderer')
            helper="function proteomicsVersion(t){if(!['iq','diann','maxquant','andromeda','maxdia','macs_iq_view'].includes(t.tool_id)||!t.source_role_qualification)return metaxcanVersion(t);const q=t.source_role_qualification;return '<br><span class=\"proteomics-version\" style=\"overflow-wrap:anywhere\">Role: '+esc(q.role_kind)+'; Reported version: '+esc(q.literal_reported_version===null?'unreported':q.literal_reported_version)+'; '+(q.implementation_available?'Native prepared aggregation available':'Implementation/runtime pending')+'; '+esc(q.remaining_scope)+'</span>';}\n"
            page=page.replace('${metaxcanVersion(t)}','${proteomicsVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Changed report boundary')
        history='<details id="proteomics-prior-catalog"><summary>Before iq/proteomics admission (historical)</summary>'+page[body.end():marker]+'</details>';after[name]=(page[:body.end()]+notice+history+page[marker:]).encode()
    prefix=('# Current iq and proteomics catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [native iq workflow guide](../IQ_MAXLFQ_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in META[3:5]:after[name]=prefix+before[name]
    return after


def check(before,after,roles,q):
    require(after==transition(before,roles,q),'Different deterministic source-bound transition');old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items()if k!='tools'}=={k:v for k,v in b.items()if k!='tools'},'Paper identity changed')
        require([u for u in a['tools']if u['tool_id']not in EXISTING]==[u for u in b['tools']if u['tool_id']not in TARGETS],'Unrelated observation changed')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']not in EXISTING]==[r for r in b if r['id']not in TARGETS],'Unrelated inventory classification changed')
    prior=json.loads(before[META[5]])['reviews'];ledger=json.loads(after[META[5]])['reviews'];require(ledger[:len(prior)]==prior and len(ledger)==len({(r['pmcid'],r['tool_id'])for r in ledger})==1802,'Changed/duplicate source reviews')
    marked={(p['pmcid'],u['tool_id'])for p in new for u in p['tools']if u['review_status']in(REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open')};require(marked=={(r['pmcid'],r['tool_id'])for r in ledger},'Review-ledger mismatch')
    counts=Counter(r['implementation_status']for r in b);require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'],counts['method-overlap'],counts['validated-family-alias'])==(32,133,256,13,5),'Wrong implementation populations')
    require(all(r['implementation_status']=='missing'for r in b if r['id']in TARGETS-{'iq'}),'Pending search/component/imaging provider promoted to native iq')
    return dict(papers=250,labels=len(b),provider_definitions=490,observations=sum(len(p['tools'])for p in new),reviewed_uses=len(ledger),pending_uses=sum(len(p['tools'])for p in new)-len(ledger),scoped_families=165,native=32,upstream=133,including_existing_adapters=168,missing=256,method_overlap=13,validated_alias_labels=5,new_native_family_credit=1,new_upstream_family_credit=0,new_source_role_reviews=16,new_named_providers=4,added_positive_observations=5,unchanged_prior_reviews=1786,unchanged_unrelated_observations=2715,unchanged_unrelated_inventory_rows=445,DIA_NN_raw_search_qualified=False,MaxQuant_runtime_qualified=False,whole_package_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
