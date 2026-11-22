"""Deterministic ACAT component admission and 22 individual source-role reviews."""
from collections import Counter
import copy,html,json,re
from qtltools_catalog.model import DATA,META,NOTICE as PREVIOUS_NOTICE
from acat_skat_catalog import primary as p
from acat_skat_catalog.decisions import PATTERNS
require=p.require
GUIDE='docs/literature/ACAT_SKAT_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/acat-skat-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/AcatToolsCli.java'
SCOPE=('One native ACAT family covers checked ACAT 0.91 prepared matrix-column p-value combination and acat-tools RUN/VERIFY/REPLAY. It does not qualify ACAT-V/null fitting, ACAT-O, REGENIE, REMETA, isoTWAS, SKAT/SKAT-O or paper pipelines. Existing native ACAT methods remain separate numerical/model profiles, without additional family credit.')
NOTE=('22 individual reviews comprise 21 previously pending observations and one recovered P127 SKATO observation. Comparator-only P090, provider-specific methods, reused summaries and explicit versus unknown editions stay distinct. P127 rho-grid ACAT differs from minimum-p SKAT-O. P131 explicitly reports ACAT 0.91; P246 explicitly reports SKAT R 2.2.5 but omits SKAT-O method arguments. P220 SAIGE 1.1.9 is separate from bibliography 45 and from downloaded AoU/Genebass results. Three Cauchy-only papers do not establish a named ACAT package. P210 CDF wording is retained as a tail discrepancy. No full-paper completeness or executed author build is claimed.')
ACTION=('SKIP3: DEPENDENT ANALYSIS SKIPPED. Supply the exact named permitted executable, Java/R library, index, source XML/PDF or audit dependency. Download/private installation is authorized where permitted; if installation is prohibited, retain the skip and identify the affected analysis and exact prerequisite. FAIL2: NO COMPLETE RESULT. Preserve raw, null, partial, failed and superseded evidence and logs; resolve the named input, numerical, capacity, source/integrity or process cause and retry NEW output. Never repair NaN/Inf, endpoints, weights or identifiers silently, guess unknown paper options, refresh historical hashes or overwrite evidence. Read analysis-status.json and WARNINGS.md; one unadmitted ACAT column withholds the batch export. VERIFY may confirm failed-capture consistency without admitting its result. Private runtimes, JARs, primary text and complete failed evidence require a separate checksummed transfer before machine retirement. Cell Ranger remains excluded; both unfinished reports stay off Pages.')
CURRENT=('250 papers, 446 labels and 485 provider definitions; 2723 observations, 1661 reviewed uses and 1062 pending. There are 147 scoped families (27 native + 120 upstream), 150 including three existing adapters, 258 missing and 27 method-overlap labels. Two aliases receive no duplicate credit. Only the qualified native ACAT component receives one new family; a standalone SKAT adapter remains pending. The full 250-paper objective is unfinished.')
NOTICE=('<aside id="acat-skat-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere"><h2>ACAT: one native component; 22 reviewed ACAT/SKAT roles</h2><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE+' '+NOTE)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Source crosswalk and remaining implementation work</a>.</p></aside>')

def registry():
    from qtltools_catalog.model import registry as previous
    rows=previous();require(len(rows)==485,'Changed previous provider population')
    for row in rows:
        if row['id'] not in PATTERNS:continue
        require(row['current_coverage']=='method-overlap','Already admitted/changed source family');row['pattern']=PATTERNS[row['id']]
        if row['id']=='acat':row.update(current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=SCOPE)
    return rows

def validate(before,primary,qualified):
    require(set(before)==set(META),'Exactly eight before metadata files required');p.validate(primary);require(primary==p.frozen(),'Use exact independently frozen source review')
    require(qualified['status']=='PASS_CURRENT_NATIVE_ACAT091_AND_SOURCE_ROLES_FOR_ADMISSION' and qualified['tests_each_JVM']==500 and qualified['JVMs']==3 and qualified['source_hosts']==2 and qualified['producer']==dict(bytes=197362611,sha256='9965571a5f24f6d701b4da693d20b8c44e58c4359da54dd932476dc1ee19fd7b') and qualified['source_roles']==22 and not qualified['whole_goal_complete'],'Actual native/source qualification required')
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);reviews=json.loads(before[META[5]])['reviews'];summary=json.loads(before[META[2]])
    require(len(papers)==250 and len(inventory)==446 and sum(len(r['tools']) for r in papers)==2722 and len(reviews)==1639,'Changed preceding population')
    require(not any(r['tool_id'] in PATTERNS for r in reviews) and len({(r['pmcid'],r['tool_id']) for r in reviews})==1639,'Already reviewed or duplicate preceding uses')
    require(summary['integration_family_counts']==dict(scoped=146,native=26,upstream=120,including_existing_adapters=149,validated_alias_labels_excluded_from_extra_credit=2),'Changed preceding family counts')
    for paper,identity in zip(papers,primary['source_identities'],strict=True):require(all(paper[k]==v for k,v in identity.items()),'Changed frozen paper identity')
    byid={r['paper_id']:r for r in papers};target=[]
    for role in primary['roles']:
        uses=[r for r in byid[role['paper_id']]['tools'] if r['tool_id']==role['tool_id']]
        if role['recovered_observation']:require(not uses,'Recovered observation already exists');continue
        require(len(uses)==1,'Missing/duplicate pre-existing target use');use=uses[0];anchor=role['primary_evidence']
        require(use['review_status']=='source-evidence-extracted; semantic-completeness-review-open','Already reviewed observation')
        require(use['evidence_paragraph_index']==anchor['paragraph_index'] and use['evidence_paragraph_sha256']==anchor['paragraph_sha256'] and use['evidence_section']==anchor['section'],'Changed original primary anchor');target.append((role['paper_id'],role['tool_id']))
    require(len(target)==21 and sum(row['paper_count'] for row in inventory if row['id'] in PATTERNS)==21,'Changed target population')
    require(all(row['implementation_status']==row['current_coverage']=='method-overlap' for row in inventory if row['id'] in PATTERNS),'Already admitted implementation')

def transition(before,primary,qualified):
    validate(before,primary,qualified);papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);ledger=json.loads(before[META[5]]);byid={r['paper_id']:r for r in papers}
    for role in primary['roles']:
        paper=byid[role['paper_id']];anchor=role['primary_evidence'];tool=role['tool_id']
        if role['recovered_observation']:
            use=dict(tool_id=tool,tool='SKAT-O',analysis_role='REMETA SKATO-type variance-component test',evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],matched_text=role['source_name_witness']['literal'],extraction_score=None,evidence_paragraph_sha256=anchor['paragraph_sha256'],evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/'+paper['pmcid']+'/ (section: '+anchor['section']+')',recovery_note='Individually reviewed omitted SKATO spelling;not an automatically scored extractor candidate.')
            paper['tools'].append(use)
        else:use=next(r for r in paper['tools'] if r['tool_id']==tool)
        use.update(use_summary=role['usage_summary'],implementation_scope_note=role['implementation_scope_note'],usage_context=role['usage_context'],review_status='usage-summary-reviewed; paper-wide-completeness-review-open',evidence_selection='individually-reviewed-source-role',version_reported_near_name=role['reported_tool_edition'],source_role_qualification=copy.deepcopy(role),supporting_evidence=copy.deepcopy(role['supporting_evidence']))
        record=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=tool,evidence_paragraph_sha256=anchor['paragraph_sha256'],use_summary=role['usage_summary'],implementation_scope_note=role['implementation_scope_note'],usage_context=role['usage_context'],version_reported_near_name=role['reported_tool_edition'],supporting_evidence=copy.deepcopy(role['supporting_evidence']))
        if role['auxiliary_evidence']:record['auxiliary_evidence']=copy.deepcopy(role['auxiliary_evidence'])
        ledger['reviews'].append(record)
    for row in inventory:
        tool=row['id']
        if tool not in PATTERNS:continue
        row.update(pattern=PATTERNS[tool],paper_count=sum(any(u['tool_id']==tool for u in r['tools']) for r in papers),papers=[r['paper_id'] for r in papers if any(u['tool_id']==tool for u in r['tools'])],source_roles_reviewed=sum(r['tool_id']==tool for r in primary['roles']),source_role_coverage=COVERAGE.rsplit('/',1)[-1],paper_role_qualification={r['paper_id']:copy.deepcopy(r) for r in primary['roles'] if r['tool_id']==tool},current_documentation=GUIDE,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
        if tool=='acat':row.update(previous_method_overlap_source=row['implementation_source'],current_coverage='native-subset-validated',implementation_status='native-subset-validated',existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE,implementation_scope=SCOPE,validation_note='500 focused tests per normal build and packaged Windows21/25/Linux21 JVM;274 actual original matrix cases and192 R sum/mean pairs. Source-built native ACAT0.91 combination only;no genotype/null fit or original R executed by the native command.',canonical_adapter_available=False,native_component_available=True,public_CLI_available=True,global_CLI_dispatch_available=True,source_build_required=True,current_qualification='../acat091-development/QUALIFICATION.json',qualification_owner='Actual unchanged build/sa-1 or exact f66c8e5e checkpoint;current catalog metadata intentionally differs.',new_native_family_credit=1,additional_ACATV_ACATO_family_credit=0)
    counts=dict(Counter(r['implementation_status'] for r in inventory));require(counts['native-subset-validated']==27 and counts['upstream-adapter-validated']==120 and counts['method-overlap']==27 and counts['missing']==258,'Wrong family credit')
    summary.update(paper_tool_observations=2723,implementation_status_counts=counts,integration_family_counts=dict(scoped=147,native=27,upstream=120,including_existing_adapters=150,validated_alias_labels_excluded_from_extra_credit=2))
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)]:
        raw=p.encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before[META[6]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed preceding embedded report')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    for name,text in [(META[6],page),(META[7],before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE)==1 and 'id="acat-skat-catalog-notice"' not in text,'Changed/already applied report notice');text=text.replace(PREVIOUS_NOTICE,'<details><summary>Before ACAT admission (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1);body=re.search(r'<body[^>]*>',text,re.I);require(body is not None,'Missing report body');after[name]=(text[:body.end()]+NOTICE+text[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:after[name]=('# '+title+'\n\n## ACAT component and22 reviewed ACAT/SKAT roles\n\n**Warnings and required actions:** '+ACTION+'\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\nRead [the source crosswalk](../ACAT_SKAT_CATALOG_INTEGRATION.md).\n\n<details><summary>Before ACAT admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after

def check(before,after,primary,qualified):
    require(after==transition(before,primary,qualified),'Different deterministic admission')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]]);unchanged=0;added=[]
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Changed paper identity/metadata')
        aa=[r for r in a['tools'] if r['tool_id'] not in PATTERNS];bb=[r for r in b['tools'] if r['tool_id'] not in PATTERNS];require(aa==bb,'Changed unrelated use');unchanged+=len(aa)
        existing={r['tool_id']:r for r in a['tools']}
        for row in b['tools']:
            if row['tool_id'] not in existing:added.append((a['paper_id'],row['tool_id']));continue
            previous=existing[row['tool_id']]
            if row['tool_id'] in PATTERNS:require(all(previous[k]==row[k] for k in ['tool_id','tool','matched_text','extraction_score','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator']),'Changed existing named evidence anchor')
    require(added==[('P127','skato')] and unchanged==2701,'Wrong recovered/unrelated observations')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id'] not in PATTERNS]==[r for r in b if r['id'] not in PATTERNS],'Changed other443 inventory rows')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]]);require({k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'} and b['reviews'][:1639]==a['reviews'],'Changed previous1639 reviews')
    keys={(r['pmcid'],r['tool_id']) for r in b['reviews']};require(len(keys)==len(b['reviews'])==1661,'Duplicate/missing appended review')
    markers={'usage-summary-reviewed; paper-wide-completeness-review-open','individually-reviewed-primary-source-roles; whole-paper-method-review-open'}
    require(keys=={(paper['pmcid'],r['tool_id']) for paper in new for r in paper['tools'] if r['review_status'] in markers},'Paper/ledger reviewed identity sets differ')
    counts=Counter(r['tool_id'] for paper in new for r in paper['tools']);require(len(counts)==446 and sum(counts.values())==2723,'Changed labels/observation population')
    for row in json.loads(after[META[1]]):require(row['paper_count']==counts[row['id']] and row['papers']==[paper['paper_id'] for paper in new if any(r['tool_id']==row['id'] for r in paper['tools'])],'Inventory membership differs')
    return dict(papers=250,named_tools=446,registry_definitions=485,observations=2723,reviewed_uses=1661,pending_uses=1062,unchanged_prior_reviews=1639,new_review_records=22,unchanged_unrelated_observations=2701,observations_added=1,observations_removed=0,integrations=147,native_integrations=27,upstream_integrations=120,including_existing_adapters=150,validated_family_alias_labels=2,method_overlap=27,missing=258,new_scoped_integration_credit=1,native_family_credit_added=1,upstream_family_credit_added=0,SKAT_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
