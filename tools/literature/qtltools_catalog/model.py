"""Admit one prepared QTLtools family and nine new source-bound reviews, preserving unrelated bytes."""
from collections import Counter
import copy,html,json,re
from monocle_catalog.model import META,DATA,NOTICE as PREVIOUS_NOTICE
from qtltools_catalog import primary as p

GUIDE='docs/literature/QTLTOOLS_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/qtltools-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/QtltoolsWorkflowCli.java'
SCOPE=('Global qtltools-tools supplies typed RUN, offline VERIFY and actual REPLAY for ten prepared-input operations: nominal/permutation/conditional cis, nominal/permutation/sample/adjust trans, MBV, quan and fdensity. Exact measured original1.1/1.2/1.3.1 Linux builds are qualified; native Java supplies selected1.3.1 fdensity. One upstream family is credited; the native helper adds no duplicate family. Inputs,edition,seed,settings and capacities remain explicit. Not every declared input-format combination is qualified.')
NOTE=('Nine newly reviewed QTLtools observations retain their distinct roles and operation-specific edition boundaries. P073 bare--grp is unresolved; P074 mapping mode is unknown; P104 neighborhood wording and annotation/QTL count semantics are not silently equated; P108 HCP selection is separate. P190 publisher supplement explicitly establishes grouped QTLtools execution,without an edition; its adaptive wording remains distinct from fixed-count inspected code. P201 also leaves edition/adaptive behavior unresolved. P203 literal match is retained without an invented historical MBV command. P219 quan edition remains unknown while mapping is explicitly1.2,with distinct residual construction and allele-orientation requirements. P227 reports1.2 followed by citation27,not1.2.27. No author binary/commit,whole-tool or whole-paper reproduction is claimed.')
ACTION=('SKIP3: DEPENDENT ANALYSIS SKIPPED. Supply the exact permitted missing executable,libraries,input,index,Linux/WSL/Python utility or named source-audit dependency. Download/private installation is authorized where allowed; if installation is prohibited,retain the skip and identify the affected analysis and exact required item. FAIL2: NO COMPLETE RESULT. Preserve raw/null/partial/failed evidence,logs,signal/exit and ownership/cleanup receipts;correct the named data,numerical,capacity,source/integrity or process cause and retry NEW output. Completed null results carry warnings and are not positive findings. Read analysis-status.json and WARNINGS.md. Preserve original1.1 MBV integer fractions,edition-specific trans-null columns and fdensity input-order guards. Never infer unknown paper settings,repair nonfinite values,overwrite failed evidence or substitute an edition. Cell Ranger remains excluded. Both unfinished reports remain off Pages. Private runtimes,JARs,full text and complete failed evidence need a separate checksummed transfer before machine retirement.')
CURRENT=('250 papers,446 named labels,485 definitions and2722 observations are preserved. There are1639 reviewed uses and1083 pending,146 scoped families(26 native Java+120 upstream),149 including three existing adapters,258 missing and28 method-overlap labels. Two aliases receive no duplicate family credit. This transition adds nine reviews and one upstream family from the former method-overlap row. The full250-paper implementation objective remains unfinished.')
NOTICE=('<aside id="qtltools-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere"><h2>QTLtools: prepared workflows and nine source reviews</h2><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE+' '+NOTE)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Exact paper roles,qualified scope and recovery</a>.</p></aside>')
require=p.require
encoded=p.encoded

def registry():
    from monocle_catalog.model import registry as previous
    rows=previous();require(len(rows)==485,'Different preceding registry population');row=next(r for r in rows if r['id']=='qtltools')
    require(row['current_coverage']=='method-overlap' and row['existing_source']=='src/main/java/org/jlinalg/pipeline/StreamingOmicsAssociationPipeline.java','Different/already admitted QTLtools provider')
    row.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE);return rows

def validate(before,primary,qualification):
    require(set(before)==set(META),'Exactly eight retained metadata files required');p.validate(primary);require(primary==p.frozen(),'Use the exact frozen independently audited source census')
    require(qualification['status']=='PASS_CURRENT_QTLTOOLS_COMPONENTS_AND_SOURCE_FOR_CATALOG_ADMISSION' and qualification['producer']==dict(bytes=197333355,sha256='27bc9571b2f15d98bddb9654d345fffed242767e377b68d37fe4645a163c7740') and qualification['tests_each_JVM']==680 and qualification['JVMs']==3 and qualification['actual_operations']==243 and qualification['complete_direct_original_files']==324 and qualification['actual_replays']==138 and qualification['source_hosts']==2 and qualification['new_catalog_credit']==0 and not qualification['whole_goal_complete'],'Actual immutable workflow and fresh source qualification required')
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);reviews=json.loads(before[META[5]]);summary=json.loads(before[META[2]])
    require(len(papers)==250 and len(inventory)==446 and sum(len(r['tools']) for r in papers)==2722 and len(reviews['reviews'])==1630,'Different preceding catalog population')
    require(len({(r['pmcid'],r['tool_id']) for r in reviews['reviews']})==1630 and not any(r['tool_id']=='qtltools' for r in reviews['reviews']),'QTLtools reviews already applied or duplicate preceding identities')
    row=next(r for r in inventory if r['id']=='qtltools');require(row['implementation_status']==row['current_coverage']=='method-overlap' and row['paper_count']==9 and row['papers']==list(p.ROLES),'Different/already admitted QTLtools inventory')
    require(summary['integration_family_counts']==dict(scoped=145,native=26,upstream=119,including_existing_adapters=148,validated_alias_labels_excluded_from_extra_credit=2),'Different preceding family counts')
    require(len(papers)==len(primary['source_identities']) and [r['paper_id'] for r in papers]==[r['paper_id'] for r in primary['source_identities']],'Changed ordered source cohort')
    roles={r['paper_id']:r for r in primary['roles']}
    for paper,identity in zip(papers,primary['source_identities'],strict=True):
        require(all(paper[k]==v for k,v in identity.items()),'Changed frozen paper identity')
        uses=[r for r in paper['tools'] if r['tool_id']=='qtltools'];require(len(uses)==int(paper['paper_id'] in roles),'Changed QTLtools membership')
        if not uses:continue
        use=uses[0];anchor=roles[paper['paper_id']]['primary_evidence'];require(use['evidence_paragraph_index']==anchor['paragraph_index'] and use['evidence_paragraph_sha256']==anchor['paragraph_sha256'] and use['evidence_section']==anchor['section'],'Changed existing source anchor')
        require(use['review_status']=='source-evidence-extracted; semantic-completeness-review-open','Different/already reviewed QTLtools observation')
    return primary['roles']

def transition(before,primary,qualification):
    roles=validate(before,primary,qualification);papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before[META[5]]);byid={r['paper_id']:r for r in papers}
    for role in roles:
        paper=byid[role['paper_id']];use=next(r for r in paper['tools'] if r['tool_id']=='qtltools');version=role['reported_edition']['literal'] if role['reported_edition'] else None
        use.update(analysis_role=role['analysis_role'],use_summary=role['usage_summary'],implementation_scope_note=role['implementation_scope_note'],evidence_selection='individually-reviewed-source-role',usage_context=role['usage_context'],review_status='usage-summary-reviewed; paper-wide-completeness-review-open',version_reported_near_name=version,source_role_qualification=copy.deepcopy(role),supporting_evidence=copy.deepcopy(role['supporting_evidence']))
        record=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='qtltools',evidence_paragraph_sha256=role['primary_evidence']['paragraph_sha256'],use_summary=role['usage_summary'],implementation_scope_note=role['implementation_scope_note'],usage_context=role['usage_context'],version_reported_near_name=version,supporting_evidence=copy.deepcopy(role['supporting_evidence']))
        if role['auxiliary_evidence']:record['auxiliary_evidence']=copy.deepcopy(role['auxiliary_evidence'])
        reviews['reviews'].append(record)
    row=next(r for r in inventory if r['id']=='qtltools');row.update(previous_method_overlap_source=row['implementation_source'],current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE+' '+NOTE,implementation_scope=SCOPE+' '+NOTE,validation_note='680 focused tests per packaged Windows21/25/Linux21 JVM;243 actual global requests(237 complete,six retained original failures);138 replays;324 complete direct-original files. Both fresh250 XML/PDF audits agree. See immutable workflow qualification and source-role crosswalk.',source_roles_reviewed=9,new_review_records=9,source_role_coverage=COVERAGE.rsplit('/',1)[-1],current_documentation=GUIDE,workflow_qualification='../qtltools-workflow-development/PROFILE.json',workflow_qualification_owner='Actual unchanged build/qt-3 or its exact3ae29f5d commit;current catalog metadata intentionally differs.',canonical_adapter_available=True,public_CLI_available=True,global_CLI_dispatch_available=True,source_build_required=True,release_asset_qualification='No release asset is established by catalog admission;build source or inspect versioned release metadata.',qualified_component_entrypoints=['qtltools-tools --help',PROGRAM],native_helpers_additional_family_credit=0,paper_role_qualification={r['paper_id']:copy.deepcopy(r) for r in roles},whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
    counts=dict(Counter(r['implementation_status'] for r in inventory));require(counts['upstream-adapter-validated']==120 and counts['native-subset-validated']==26 and counts['missing']==258 and counts['method-overlap']==28 and counts['validated-family-alias']==2,'Incorrect upstream/native/overlap family credit')
    summary.update(implementation_status_counts=counts,integration_family_counts=dict(scoped=146,native=26,upstream=120,including_existing_adapters=149,validated_alias_labels_excluded_from_extra_credit=2))
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[5],reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before[META[6]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Different preceding embedded catalog')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    for name,text in [(META[6],page),(META[7],before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE)==1 and 'id="qtltools-catalog-notice"' not in text,'Different/already admitted report notice');text=text.replace(PREVIOUS_NOTICE,'<details><summary>Before QTLtools admission (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1);body=re.search(r'<body[^>]*>',text,re.I);require(body is not None,'Missing report body');after[name]=(text[:body.end()]+NOTICE+text[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
        after[name]=('# '+title+'\n\n## QTLtools: one scoped family and nine new source reviews\n\n**Warnings and required actions:** '+ACTION+'\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\nRead [the source crosswalk](../QTLTOOLS_CATALOG_INTEGRATION.md).\n\n<details><summary>Before QTLtools admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after

def check(before,after,primary,qualification):
    require(after==transition(before,primary,qualification),'Different deterministic source-bound QTLtools transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]]);changed=0
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity or metadata')
        require([r['tool_id'] for r in a['tools']]==[r['tool_id'] for r in b['tools']] and [r for r in a['tools'] if r['tool_id']!='qtltools']==[r for r in b['tools'] if r['tool_id']!='qtltools'],'Changed unrelated observation or membership/order')
        for previous,current in zip(a['tools'],b['tools'],strict=True):
            if previous['tool_id']!='qtltools':continue
            fixed=['tool_id','tool','matched_text','extraction_score','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator'];require(all(previous[k]==current[k] for k in fixed),'Changed literal source anchor/name');changed+=1
    require(changed==9,'Different reviewed observation count')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']!='qtltools']==[r for r in b if r['id']!='qtltools'],'Changed unrelated445 inventory rows')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]]);require({k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'} and b['reviews'][:1630]==a['reviews'],'Changed existing1630 reviews or metadata')
    require(len(b['reviews'])==len({(r['pmcid'],r['tool_id']) for r in b['reviews']})==1639 and all(r['tool_id']=='qtltools' for r in b['reviews'][1630:]),'Incorrect/duplicate nine review append')
    reviewed_markers={'usage-summary-reviewed; paper-wide-completeness-review-open','individually-reviewed-primary-source-roles; whole-paper-method-review-open'}
    paper_keys={(paper['pmcid'],r['tool_id']) for paper in new for r in paper['tools'] if r['review_status'] in reviewed_markers}
    require(paper_keys=={(r['pmcid'],r['tool_id']) for r in b['reviews']} and len(paper_keys)==1639,'Paper and ledger reviewed identity sets differ')
    counts=Counter(r['tool_id'] for paper in new for r in paper['tools']);require(len(counts)==446 and sum(counts.values())==2722,'Changed named/observation population')
    for row in json.loads(after[META[1]]):require(row['paper_count']==counts[row['id']] and row['papers']==[paper['paper_id'] for paper in new if any(r['tool_id']==row['id'] for r in paper['tools'])],'Inventory population differs')
    return dict(papers=250,named_tools=446,registry_definitions=485,observations=2722,reviewed_uses=1639,pending_uses=1083,unchanged_prior_reviews=1630,new_review_records=9,unchanged_unrelated_observations=2713,observations_added=0,observations_removed=0,integrations=146,native_integrations=26,upstream_integrations=120,including_existing_adapters=149,validated_family_alias_labels=2,method_overlap=28,missing=258,new_scoped_integration_credit=1,upstream_family_credit_added=1,native_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
