"""Deterministic bounded original DIA-NN admission; existing source reviews are updated once."""
from collections import Counter
from pathlib import Path
import copy, html, json, re
from gemma_primary import encoded, require

ROOT=Path(__file__).resolve().parents[2]
DATA='docs/literature/omics-2023-2026'
META=[DATA+'/'+name for name in ('papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md')]+['tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']
ADMISSION='docs/literature/diann-catalog-admission'
GUIDE='docs/literature/DIA_NN.md'
PROGRAM='src/main/java/org/jlinalg/cli/DiannToolsCli.java'
EDITIONS={'P005':'1.8.1','P020':'1.8','P029':'2.0.2','P097':'1.8.1','P168':'1.8.1'}
SCOPE=('Original separately permitted Linux x86_64 DIA-NN1.8/1.8.1/2.0.2 local library-free protein quantification, '
       'with explicit UniProt FASTA, tryptic C/UniMod4 and M/UniMod35 candidates, complete requested raw-run capture, '
       'fresh original prediction and raw fitting, original MBR/normalization and separately tested1.8RobustLC, '
       'supported full report/matrix/statistics consistency and exact-producer Windows/Linux saved readback. '
       'One scoped upstream family. Full execution controls use two complete distinct Bruker TDF runs and a limited1704-record reference; '
       'they do not reproduce a historical paper, whole-human database, every format or every DIA-NN mode.')
GAPS={
 'P005':'Exact historical mouse UniProtApril2023 inputs and unreported author options remain unresolved. Other reference/header/modification profiles, vendor conversion and downstream MetaboAnalyst6.0 comparisons require separate qualification.',
 'P020':'Original1.8RobustLC high-accuracy and MBR are available as a local subset. Historical SwissProt2021_04/20395entries, original raw data and author options were not reproduced. Perseus transformations, filtering, imputation and tests remain separate.',
 'P029':'Keep the reported literalmaxLGQ unchanged. Exact S288C-2023.06.26.fasta/header compatibility, author per-run mass/scan optimization and median batch recipe, exclusions and SAINTq/downstream comparisons remain separate. No non-UniProt reference or unreported modification profile is silently substituted.',
 'P097':'Baseline local1.8.1library-free MBR is available. PAV-exclusive/inclusive reference/library construction, nanoparticle selection, missingness/log transformations, age/sex/10PC residualization and inverse-normal scaling remain separate. PG.Normalised is retained and is not replaced with PG.MaxLFQ or iq output.',
 'P168':'The local1.8.1subset does not implement private Proteograph cloud orchestration or cohort-variant/MAC5 library construction. Exact author inputs/settings, PepCal extraction/five-run smoothing, iq aggregation and limma plate correction remain separate.'}
ACTION=('SKIP3 skips a dependent analysis when its named permitted input/platform/runtime/install/access prerequisite is unavailable; '
        'supply it separately where allowed or retain the skip. FAIL2 admits no complete result for invalid/changed data, process, '
        'numerical, resource, cancellation or consistency failure. Keep all attempts and use NEW paths after correction. '
        'Read WARNINGS.md, OUTPUT-AUDIT.json and original logs; require COMPLETED.json and successful saved readback with the EXACT producing JAR. '
        'Original fragmentless-candidate omissions can include targets and decoys and remain visible. QuantUMS with fewer than six runs '
        'retains its optimization limitation; do not duplicate or silently change the cohort. MaxQuant runtime analyses stay skipped at the user request, '
        'with MaxQuant/Andromeda/MaxDIA pending. Cell Ranger remains excluded. Raw scientific/process/runtime evidence stays private; '
        'literature HTML reports remain withheld from Pages. The full 250-paper goal remains active.')
CURRENT=('250 papers, 451 labels, 490 provider definitions and 2,731 observations; 1,802 reviewed / 929 pending uses. '
         '166 scoped families (32 native + 134 upstream), 169 including three existing adapters. '
         '255 labels remain missing, 13 method overlaps and five validated aliases. '
         'This checkpoint adds one bounded original DIA-NN upstream family and zero papers, observations, provider definitions or source reviews.')
UI_CURRENT=('250 papers; 166 scoped families (32 native Java, 134 upstream adapters). '
            'Of 2,731 uses, 1,802 reviewed / 929 pending. Another 255 tool labels remain pending.')
UI_SCOPE=('Original DIA-NN 1.8, 1.8.1 and 2.0.2 run locally on Linux. Controls use two full Bruker TDF runs '
          'and a limited 1,704-record UniProt reference. Windows supports saved readback. '
          'Full author workflows and other modes remain separate.')
UI_ACTION=('SKIP3: the dependent analysis was skipped because a named input, runtime, platform, permission or installation '
           'is unavailable. Supply the stated prerequisite where permitted. FAIL2: no complete result was admitted; '
           'preserve the attempt and correct the stated problem using a new output path. Read WARNINGS.md and verify '
           'the capture with its producing JAR before downstream use. QuantUMS with fewer than six runs retains an '
           'optimisation limitation. MaxQuant runtime stays skipped; MaxQuant, Andromeda and MaxDIA remain pending. '
           'Cell Ranger is excluded.')


def qualified(q):
    require(q['status']=='PASS_SCOPED_DIA_NN_ORIGINAL_LIBRARY_FREE_CAPTURE'
        and q['Windows_focused_JUnit']==q['Linux_focused_JUnit']>=74 and q['JUnit_skips']==0
        and q['edition_profiles']==['1.8','1.8.1','2.0.2'] and q['typed_full_Linux_captures']==3
        and q['complete_distinct_raw_runs_each_capture']==2 and q['original_fresh_MBR_controls']==3
        and q['portable_positive_controls_each_OS']==3 and q['portable_refusal_controls_each_OS']==18
        and q['private_probes_packaged']is False and q['only_prior_packaged_change']=='org/jlinalg/cli/JLinAlgCli.class'
        and q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0
        and all(q[k]is False for k in('whole_package_qualified','whole_paper_reproduced','full_author_roles_implemented','PTM_localisation_qualified','whole_goal_complete')),
        'Incomplete or expanded DIA-NN workflow qualification')


def applied(role):
    require(role['tool_id']=='diann' and role['paper_id']in EDITIONS
        and role['literal_reported_version']==EDITIONS[role['paper_id']], 'Different primary DIA-NN edition or role')
    result=copy.deepcopy(role)
    result.update(source_role_review_applied=True,catalog_admitted=True,implementation_available=True,
        qualified_behavior_edition=role['literal_reported_version'],runtime_analysis_status='SCOPED_ORIGINAL_LOCAL_LIBRARY_FREE_WORKFLOW_AVAILABLE',
        raw_search_qualified=True,raw_search_qualified_scope='local-UniProt-library-free-protein-quantification',
        full_source_role_implemented=False,whole_package_qualified=False,whole_paper_reproduced=False,
        paper_edition_reproduced=False,new_family_credit=0,
        predecessor_remaining_scope=role['remaining_scope'],remaining_scope=GAPS[role['paper_id']])
    return result


def registry(previous):
    rows=copy.deepcopy(previous);require(len(rows)==490 and sum(row['id']=='diann'for row in rows)==1,'Different provider population')
    target=next(row for row in rows if row['id']=='diann');require(target['current_coverage']=='missing','Preceding DIA-NN provider already promoted')
    target.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE,integration_family='diann',family_credit=1)
    return rows


def transition(before,roles,q):
    qualified(q)
    papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in(0,1,2,5)]
    require((len(papers),len(inventory),sum(len(p['tools'])for p in papers),len(ledger['reviews']))==(250,451,2731,1802),'Changed predecessor populations')
    selected=[role for role in roles['roles']if role['tool_id']=='diann']
    require(len(selected)==5 and {role['paper_id']for role in selected}==set(EDITIONS),'Changed DIA-NN source-role set')
    require(next(role for role in selected if role['paper_id']=='P029')['reported_settings']['reported_intensity_name_literal']=='maxLGQ','Literal source intensity name repaired')
    require(next(role for role in selected if role['paper_id']=='P097')['reported_settings']['readout']=='PG.Normalised','Distinct source readout replaced')
    for role in selected:
        paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools']if u['tool_id']=='diann'];require(len(uses)==1,'Missing/duplicate DIA-NN use')
        use=uses[0];require({k:use.get(k)for k in role['raw_source_fields']}==role['raw_source_fields'],'Changed raw source/version evidence')
        scope=SCOPE+' Remaining author-role scope: '+GAPS[role['paper_id']]
        use.update(implementation_scope_note=scope,source_role_qualification=applied(role))
        reviews=[r for r in ledger['reviews']if(r['pmcid'],r['tool_id'])==(paper['pmcid'],'diann')];require(len(reviews)==1,'DIA-NN review must already exist exactly once')
        reviews[0].update(implementation_scope_note=scope,source_role_qualification=applied(role))
    target=next(row for row in inventory if row['id']=='diann');require(target['implementation_status']==target['current_coverage']=='missing','Predecessor DIA-NN status changed')
    target.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE,
        implementation_status='upstream-adapter-validated',implementation_source=PROGRAM,implementation_scope=SCOPE,
        validation_note='Three complete fresh original Linux workflows;74selected tests eachOS;three complete portable readbacks and18corruption/refusal controls eachOS. Exact producer, every input/namespace and supported report/matrix/statistics readback. Original warnings retained. No author dataset or whole-tool claim.',
        paper_role_qualification={role['paper_id']:applied(role)for role in selected},current_documentation=GUIDE,
        current_qualification='docs/literature/diann-validation/QUALIFICATION.json',integration_family='diann',family_credit=1,
        whole_tool_qualified=False,whole_paper_reproduced=False,source_build_required=True,released_binary_contains_workflow=False)
    counts=Counter(row['implementation_status']for row in inventory)
    summary.update(implementation_status_counts=dict(counts),integration_family_counts=dict(
        scoped=counts['native-subset-validated']+counts['upstream-adapter-validated'],native=counts['native-subset-validated'],
        upstream=counts['upstream-adapter-validated'],including_existing_adapters=counts['native-subset-validated']+counts['upstream-adapter-validated']+counts['existing-adapter'],
        validated_alias_labels_excluded_from_extra_credit=counts['validated-family-alias']))
    after=dict(before)
    for name,value in((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value);after[name]=raw.replace(b'\n',b'\r\n')if before[name].count(b'\n')==before[name].count(b'\r\n')else raw
    notice=('<aside id="diann-catalog-notice" style="margin:1rem;padding:1rem;border:2px solid #286d5a;background:#edf8f3;color:#163b30;overflow-wrap:anywhere"><h2>Original local DIA-NN subset available</h2><p>'+html.escape(UI_CURRENT)+'</p><p>'+html.escape(UI_SCOPE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(UI_ACTION)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">DIA-NN commands and limits</a></p></aside>')
    for name in META[6:]:
        page=before[name].decode();require('id="diann-catalog-notice"'not in page and'id="proteomics-catalog-notice"'in page,'Different predecessor report notice')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
            require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed predecessor embedded catalog')
            payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
            require(json.loads(payload)==dict(papers=papers,tools=inventory,summary=summary),'Embedded DIA-NN catalog does not roundtrip')
            require(page.count('${proteomicsVersion(t)}')==page.count('function renderContent(){')==1,'Changed role renderer')
            helper="function diannVersion(t){if(t.tool_id!=='diann'||!t.source_role_qualification)return proteomicsVersion(t);const q=t.source_role_qualification;return '<br><span class=\"diann-version\" style=\"overflow-wrap:anywhere\">Role: '+esc(q.role_kind)+'; Reported version: '+esc(q.literal_reported_version===null?'unreported':q.literal_reported_version)+'; Original local library-free subset available; full author role pending; '+esc(q.remaining_scope)+'</span>';}\n"
            page=page.replace('${proteomicsVersion(t)}','${diannVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Changed report boundary')
        history='<details id="diann-prior-catalog"><summary>Before DIA-NN admission (historical)</summary>'+page[body.end():marker]+'</details>'
        after[name]=(page[:body.end()]+notice+history+page[marker:]).encode()
    prefix=('# Current original DIA-NN catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [DIA-NN workflow guide](../DIA_NN.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in META[3:5]:after[name]=prefix+before[name]
    return after


def check(before,after,roles,q):
    require(after==transition(before,roles,q),'Different deterministic DIA-NN transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
    for left,right in zip(old,new,strict=True):
        require({k:v for k,v in left.items()if k!='tools'}=={k:v for k,v in right.items()if k!='tools'},'Paper identity changed')
        require([u for u in left['tools']if u['tool_id']!='diann']==[u for u in right['tools']if u['tool_id']!='diann'],'Unrelated paper/tool observation changed')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]])
    require([r for r in a if r['id']!='diann']==[r for r in b if r['id']!='diann'],'Unrelated provider classification changed')
    prior,ledger=json.loads(before[META[5]])['reviews'],json.loads(after[META[5]])['reviews']
    require(len(prior)==len(ledger)==len({(r['pmcid'],r['tool_id'])for r in ledger})==1802,'Existing reviews were duplicated or lost')
    for left,right in zip(prior,ledger,strict=True):
        require((left['pmcid'],left['tool_id'])==(right['pmcid'],right['tool_id']),'Review ordering/identity changed')
        ignored={'implementation_scope_note','source_role_qualification'}if left['tool_id']=='diann'else set()
        require({k:v for k,v in left.items()if k not in ignored}=={k:v for k,v in right.items()if k not in ignored},'Unrelated/source review fields changed')
    counts=Counter(r['implementation_status']for r in b)
    require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'],counts['method-overlap'],counts['validated-family-alias'])==(32,134,255,13,5),'Wrong family/label populations')
    require(next(r for r in b if r['id']=='maxquant')['runtime_analysis_status']=='SKIP3_USER_REQUESTED_KEEP_TOOL_PENDING','User MaxQuant skip was changed')
    return dict(papers=250,labels=451,provider_definitions=490,observations=2731,reviewed_uses=1802,pending_uses=929,
        scoped_families=166,native=32,upstream=134,including_existing_adapters=169,missing=255,method_overlap=13,validated_alias_labels=5,
        new_upstream_family_credit=1,new_native_family_credit=0,new_source_role_reviews=0,new_named_providers=0,added_positive_observations=0,
        unchanged_unrelated_inventory_rows=450,unchanged_unrelated_observations=2726,unchanged_unrelated_reviews=1797,
        DIA_NN_local_library_free_subset_qualified=True,MaxQuant_runtime_qualified=False,full_author_roles_implemented=False,
        whole_package_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
