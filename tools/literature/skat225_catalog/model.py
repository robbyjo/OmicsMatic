"""One qualified original SKAT provider, with no duplicate SKAT-O family credit."""
from collections import Counter
import copy,html,json,re
from acat_skat_catalog import model as previous,primary

ROOT=primary.ROOT;META=previous.META;DATA=previous.DATA
GUIDE='docs/literature/skat225-qualification/INTEGRATION.md'
COVERAGE=DATA+'/skat225-workflow-coverage.json'
PROOF=DATA+'/skat225-catalog-validation.json'
PROGRAM='src/main/java/org/jlinalg/cli/SkatWorkflowCli.java'
TARGETS={'skat','skato'}
SCOPE=('Original SKAT R2.2.5 prepared autosomal continuous/binary matrix workflows through skat-tools RUN, offline VERIFY and fresh REPLAY. Explicit null model,method,kernel,seed,ordered variants/sets,weights,imputation,complete cases and admission policies. Qualified Windows x86_64 Java21/R4.6.1 profile with pinned private packages;reported R4.6.2 binary-build warnings remain visible. SKAT-O in this provider is an operation/alias,not another family. No SSD,sex-chromosome,author-binary,whole-tool or whole-paper equivalence is claimed.')
NOTE=('All15 existing reviewed SKAT/SKAT-O observations stay unchanged. P246 explicitly names original R2.2.5,but supplementary PDF/workbook review does not establish method/rho,kernel weights,imputation or small-null choices. Do not invent a matched command. Meta-SAIGE,SAIGE-GENE+,REGENIE,REMETA,SEAK and COAST contexts remain distinct;P090 is comparator-only. The newly available original SKAT provider does not reproduce those workflows. Six extra zero-resampling controls preserve original behavior,including binary perturbation refusal.')
ACTION=('SKIP3: DEPENDENT ANALYSIS SKIPPED. Supply the exact permitted named input,R4.6.1 executable and declared SKAT2.2.5 package profile. Download/install privately where allowed;if prohibited,retain the skip and identify the affected analysis and prerequisite. FAIL2: NO COMPLETE RESULT. Preserve raw,null,failed,partial,superseded and invalidated evidence;fix the named input,numerical,capacity,process or integrity cause and retry NEW output. Only admitted/p-values.tsv with results_admitted=true and successful complete verification is a final export. Original results/p-values.tsv can remain after failure. Untested sets and retained zero bounds export NA. Offline verification is integrity/relationship checking,not RDS interpretation,refitting,authenticated provenance or a convergence certificate. Do not use the retained invalidated matrix4 constant-outcome result. Never silently repair IDs,NaN/Inf,endpoints,weights,unknown options or historical hashes. Read WARNINGS.md first. Private runtimes,JARs and complete historical evidence need separate transfer. Cell Ranger remains excluded;unfinished reports remain off Pages.')
CURRENT=('250 papers,446 labels,485 provider definitions and2723 observations;1661 reviewed uses and1062 pending. There are148 scoped families(27 native+121 upstream),151 including three existing adapters,258 missing labels,25 method-overlap labels and three aliases without extra family credit. This transition adds one upstream SKAT family and one SKAT-O alias,with no new reviews or observations. The full250-paper implementation goal remains unfinished.')
NOTICE=('<aside id="skat225-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere"><h2>SKAT2.2.5: one original upstream family</h2><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE+' '+NOTE)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Qualified operations,source roles and recovery</a>.</p></aside>')
require=primary.require

def registry():
    rows=previous.registry();require(len(rows)==485,'Changed preceding registry population')
    for row in rows:
        if row['id'] not in TARGETS:continue
        require(row['current_coverage']=='method-overlap','Changed/already admitted SKAT provider')
        row.update(current_coverage='upstream-adapter-validated' if row['id']=='skat' else 'validated-family-alias',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE)
        if row['id']=='skato':row['alias_of']='skat'
    return rows

def validate(before,qualification):
    require(set(before)==set(META),'Exactly eight predecessor metadata files required')
    require(qualification['status']=='PASS_ORIGINAL_SKAT225_PREPARED_WORKFLOW_FOR_ADMISSION','Actual complete prepared-workflow qualification required')
    require(qualification['producer']==dict(bytes=197764157,sha256='d87eb1597fcdd48214a4ee6fb72b7b2e4cb4c7b0ce547d1782bcd4303ae902cc'),'Changed actual qualified producer')
    require(qualification['normal_tests']==722 and qualification['matrix_cases']==89 and qualification['additional_zero_resampling_cases']==6 and qualification['public_resealed_controls']==33 and qualification['public_runtime_controls']==8,'Missing original/control evidence')
    require(qualification['fresh_source_audit_papers']==250 and qualification['source_roles_preserved']==15 and qualification['P246_supplementary_review_complete'] is True and qualification['P246_exact_command_reconstructed'] is False,'Different source scope')
    require(not qualification['whole_tool_qualified'] and not qualification['whole_paper_reproduced'] and not qualification['whole_goal_complete'],'False broad qualification claim')
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);ledger=json.loads(before[META[5]])
    require(len(papers)==250 and len(inventory)==446 and sum(len(p['tools']) for p in papers)==2723 and len(ledger['reviews'])==1661,'Changed predecessor population')
    require(summary['integration_family_counts']==dict(scoped=147,native=27,upstream=120,including_existing_adapters=150,validated_alias_labels_excluded_from_extra_credit=2),'Changed preceding family counts')
    require(len({(r['pmcid'],r['tool_id']) for r in ledger['reviews']})==1661,'Duplicate preceding review')
    roles=[r for r in primary.frozen()['roles'] if r['tool_id'] in TARGETS];require(len(roles)==15,'Changed reviewed role population')
    byid={p['paper_id']:p for p in papers}
    for role in roles:
        uses=[u for u in byid[role['paper_id']]['tools'] if u['tool_id']==role['tool_id']]
        require(len(uses)==1 and uses[0]['source_role_qualification']==role,'Changed individually reviewed source role')
    require(all(r['implementation_status']==r['current_coverage']=='method-overlap' for r in inventory if r['id'] in TARGETS),'Already admitted or changed target statuses')
    require({r['id']:r['paper_count'] for r in inventory if r['id'] in TARGETS}==dict(skat=6,skato=9),'Changed label memberships')

def transition(before,qualification):
    validate(before,qualification);after=dict(before);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]])
    for row in inventory:
        if row['id'] not in TARGETS:continue
        alias=row['id']=='skato';status='validated-family-alias' if alias else 'upstream-adapter-validated'
        row.update(previous_method_overlap_source=row['implementation_source'],current_coverage=status,implementation_status=status,existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE,implementation_scope=SCOPE,current_documentation=GUIDE,current_qualification='../skat225-qualification/QUALIFICATION.json',canonical_adapter_available=True,public_CLI_available=True,global_CLI_dispatch_available=True,source_build_required=True,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,new_upstream_family_credit=0 if alias else 1,validation_note='Actual unchanged normal-gradle-8 producer:722 selected tests,89 independent-original matrix controls,6 extra zero-resampling cases,33 public resealed controls and8 real replay/dependency/capacity controls. Windows x86_64 Java21/R4.6.1 only;no Linux R fit claim.',current_paper_role_limits=NOTE)
        if alias:row.update(alias_of='skat',additional_family_credit=0)
    counts=dict(Counter(r['implementation_status'] for r in inventory));require(counts['upstream-adapter-validated']==121 and counts['native-subset-validated']==27 and counts['method-overlap']==25 and counts['validated-family-alias']==3 and counts['missing']==258,'Incorrect family or alias credit')
    summary.update(implementation_status_counts=counts,integration_family_counts=dict(scoped=148,native=27,upstream=121,including_existing_adapters=151,validated_alias_labels_excluded_from_extra_credit=3))
    for name,value in [(META[1],inventory),(META[2],summary)]:
        raw=primary.encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    for name in [META[6],META[7]]:
        page=before[name].decode();require(page.count(previous.NOTICE)==1 and 'id="skat225-catalog-notice"' not in page,'Changed preceding report notice')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
            require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed embedded report')
            payload=json.dumps(dict(papers=json.loads(before[META[0]]),tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
        page=page.replace(previous.NOTICE,'<details><summary>Before standalone SKAT admission (historical)</summary>'+previous.NOTICE+'</details>',1);body=re.search(r'<body[^>]*>',page,re.I);require(body is not None,'Missing report body');after[name]=(page[:body.end()]+NOTICE+page[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
        after[name]=('# '+title+'\n\n## Original SKAT2.2.5 prepared workflows\n\n**Warnings and required actions:** '+ACTION+'\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\nRead [the qualified integration scope](../SKAT225_CATALOG_INTEGRATION.md).\n\n<details><summary>Before standalone SKAT admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after

def check(before,after,qualification):
    require(after==transition(before,qualification),'Different deterministic catalog admission')
    require(after[META[0]]==before[META[0]] and after[META[5]]==before[META[5]],'Paper identities,all2723 observations or1661 prior reviews changed')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]])
    require([r for r in a if r['id'] not in TARGETS]==[r for r in b if r['id'] not in TARGETS],'Changed unrelated444 inventory rows')
    for old,new in zip(a,b,strict=True):
        if old['id'] in TARGETS:
            for field in ['paper_count','papers','paper_role_qualification','source_roles_reviewed','source_role_coverage']:require(old[field]==new[field],'Changed named source roles or memberships')
    require(len(registry())==485,'Changed registry definitions')
    return dict(papers=250,labels=446,provider_definitions=485,observations=2723,reviewed_uses=1661,pending_uses=1062,scoped_families=148,native=27,upstream=121,including_existing_adapters=151,validated_family_alias_labels=3,method_overlap=25,missing=258,new_upstream_family_credit=1,new_native_family_credit=0,new_reviews=0,new_observations=0,unchanged_unrelated_inventory_rows=444,unchanged_SKAT_source_roles=15,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
