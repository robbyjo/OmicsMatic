"""One native smartpca family and six source reviews; all unrelated records preserved."""
from collections import Counter
import copy,html,json,re
from mrpresso_catalog import model as previous
from smartpca_catalog import primary as p

ROOT,META,DATA=p.ROOT,previous.META,previous.DATA
TARGET='smartpca';PROGRAM='src/main/java/org/jlinalg/cli/SmartpcaCommandCli.java'
GUIDE='docs/literature/SMARTPCA_TOOLS.md'
COVERAGE=DATA+'/smartpca-source-role-coverage.json';PROOF=DATA+'/smartpca-catalog-validation.json'
REVIEWED=previous.REVIEWED;require=p.require
SCOPE=('Native Java source-specific EIG7.2.1 smartpca program16000 PCA/outlier/perturbation-shrinkage and population FST/inbreeding profiles, '
 'commit4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1. Global smartpca-tools RUN, complete scientific recomputation VERIFY and actual child REPLAY. '
 'Prepared strict EIGENSTRAT text, explicit population selection, bounded resources and exact producer/source binding. '
 'Source marker/pair contributions, global block weights, position-unit inference and weighted jackknife are retained. '
 'Negative finite FST is not clamped. Undefined/one-block ordinary diagnostics retain false field eligibility; original inbreeding with at most one block is refused. '
 'No original EIGENSOFT installation is needed for native analysis. No whole upstream package, author environment, historical-version equivalence, '
 'whole-paper/biological reproduction, authenticity or OS-containment claim.')
NOTE=('Six individual paper roles are reviewed. P019 MVP EIGENSOFTv6 is separate from ROSMAP reference projection with unreported edition. '
 'P041 reports7.2.1; P045/P071 editions remain unreported. P045 r2thresh0.7 does not establish killr2. '
 'P071 Eurasian/East Asian reference panels stay distinct. P180 PCA7.2.0, its100,000-SNP cutoff and LSQ/outlier settings are separate from its '
 'fstonly call whose edition/defaults/uncertainty are not restated. P229 program13050 is not a package edition or qualified program16000. '
 'Available native7.2.1 does not replace paper-reported or unreported versions. LD pruning, imputation, ancestry masking and other packages remain separate.')
ACTION=('SKIP3: DEPENDENT ANALYSIS SKIPPED. Supply/install the named permitted input, Java17+ or exact complete producer JAR; otherwise retain the skip. '
 'FAIL2: NO COMPLETE RESULT. Keep raw/partial/failed/undefined/superseded evidence, fix the named input/numerical/resource/deadline/integrity cause and retry NEW output. '
 'Read WARNINGS.md, analysis-status.json and per-field flags; require completion and passing VERIFY. Never repair IDs, replace NA/NaN/Inf, '
 'clamp negative FST or refresh historical hashes. Explicit diagnostic retention does not grant scientific eligibility. '
 'Retain the original one-block inbreeding abort/checker failure and the corrected final qualification separately. '
 'Native analysis requires no R/Python/EIGENSOFT runtime. Cell Ranger remains excluded. Private runtimes/JARs/full evidence require private transfer; restricted histories remain local.')
CURRENT=('250 papers,446 labels,485 provider definitions and2,722 positive observations;1,697 reviewed/1,025 pending uses. '
 'There are154 scoped families (29 native +125 upstream),157 including three existing adapters;258 missing,19 method-overlap and three aliases without extra family credit. '
 'The full250-paper implementation and semantic-review goal remains unfinished.')
VISIBLE_ACTION=('SKIP3: dependent analysis skipped. Supply the named permitted input, Java17+ or exact producer JAR; otherwise keep the skip. '
 'FAIL2: no complete result. Preserve evidence, fix the stated cause and retry NEW output. Read WARNINGS.md and per-field eligibility; require passing VERIFY. '
 'Negative FST stays unclamped; undefined diagnostics are not eligible inference. Native analysis needs no EIGENSOFT installation.')
VISIBLE_CURRENT=('250 papers;154 scoped families (29 native +125 upstream);1,697 reviewed/1,025 pending uses. The full250-paper implementation and review goal remains unfinished.')
NOTICE=('<aside id="smartpca-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;'
 'padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>smartpca native workflows and paper uses</h2><p><strong>Warnings and required actions:</strong> '
 +html.escape(VISIBLE_ACTION)+'</p><p>'+html.escape(VISIBLE_CURRENT)+'</p><p><a href="'
 +'https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Run PCA/FST, inspect eligibility and resolve failures</a>.</p>'
 '<details id="smartpca-source-details"><summary>Source scope, reported versions and remaining work</summary><p>'
 +html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE+' '+NOTE)+'</p><p>'+html.escape(ACTION)+'</p></details></aside>')

def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def registry():
    rows=previous.registry();require(len(rows)==485,'Changed provider population');target=next(x for x in rows if x['id']==TARGET)
    require(target['current_coverage']=='method-overlap','Already admitted/changed smartpca provider')
    target.update(current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE);return rows

def frozen_primary():
    result=json.loads((ROOT/'docs/literature/smartpca-catalog-sources/ROLES.json').read_bytes())
    require(result['status']=='PASS_FULL250_SMARTPCA_SIX_SOURCE_ROLES_CONTEXT_VERSIONS_NOT_ADMISSION' and result['papers_checked']==250 and
        [r['paper_id'] for r in result['roles']]==p.POSITIVE and result['new_reviews']==result['new_family_credit']==0 and
        not any(r['source_role_review_applied'] for r in result['roles']),'Changed/incomplete primary roles')
    return result

def applied_role(role):
    q=copy.deepcopy({key:value for key,value in role.items() if key!='original_observation'});q['source_role_review_applied']=True;return q

def validate(before,primary,qualified):
    require(set(before)==set(META) and primary==frozen_primary(),'Changed predecessor/primary roles')
    require(qualified==json.loads((ROOT/'docs/literature/smartpca-fst-qualification/STATUS.json').read_bytes()) and
        qualified['status']=='PASS_NATIVE_SMARTPCA_PCA_FST_GLOBAL_WORKFLOWS' and qualified['native_fst_workflow_qualified'] is True and
        qualified['global_CLI_dispatch_available'] is True and qualified['normal_tests']==1906 and qualified['packaged_protocol_commands']==362 and
        qualified['source_program_calls']==24 and qualified['direct_source_jackknife_calls']==23 and qualified['source_finite_comparisons_across_profiles']==69 and
        qualified['methods_per_JVM']==14 and qualified['example_commands']==18 and qualified['source_audit_profiles']==2 and
        qualified['producer']['sha256']=='534da0fe4f10959a622c4d5f74fee35dc959c6d48e2730f9861dc83790973522' and
        not any(qualified[k] for k in ('catalog_admitted','whole_tool_qualified','whole_paper_reproduced','whole_goal_complete')),
        'Incomplete/changed actual native qualification')
    papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    require(len(papers)==250 and len(inventory)==446 and sum(len(x['tools']) for x in papers)==2722 and len(ledger['reviews'])==1691,'Changed predecessor population')
    require(summary['integration_family_counts']==dict(scoped=153,native=28,upstream=125,including_existing_adapters=156,validated_alias_labels_excluded_from_extra_credit=3),'Changed predecessor family counts')
    target=next(x for x in inventory if x['id']==TARGET)
    require(target['current_coverage']==target['implementation_status']=='method-overlap' and target['papers']==p.POSITIVE,'Changed smartpca source population/status')
    require(not any(x['tool_id']==TARGET for x in ledger['reviews']),'Target has existing review credit')
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id']);uses=[x for x in paper['tools'] if x['tool_id']==TARGET]
        require(uses==[role['original_observation']] and paper['xml_sha256']==role['xml_sha256'],'Changed original extraction')
        require(role['original_extraction_version_reported_near_name']==uses[0]['version_reported_near_name'],'Original near-name version differs')

def transition(before,primary,qualified):
    validate(before,primary,qualified);papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id']);use=next(x for x in paper['tools'] if x['tool_id']==TARGET)
        pointers=[copy.deepcopy(x) for x in role['anchors'] if x['paragraph_index']!=use['evidence_paragraph_index']]
        scope=role['scope_limit']+' Current native availability: '+SCOPE
        use.update(use_summary=role['summary'],implementation_scope_note=scope,review_status=REVIEWED,usage_context='analysis-method-description',
            evidence_selection='individually-reviewed-source-role',supporting_evidence=pointers,source_role_qualification=applied_role(role))
        ledger['reviews'].append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=TARGET,evidence_paragraph_sha256=use['evidence_paragraph_sha256'],
            use_summary=role['summary'],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],
            supporting_evidence=copy.deepcopy(pointers),source_table_evidence=[],reported_version_contexts=copy.deepcopy(role['roles']),reported_settings=copy.deepcopy(role['reported_settings'])))
    target=next(x for x in inventory if x['id']==TARGET)
    target.update(previous_method_overlap_source=target['implementation_source'],current_coverage='native-subset-validated',implementation_status='native-subset-validated',
        existing_source=PROGRAM,implementation_source=PROGRAM,native_helper_sources=['src/main/java/org/jlinalg/genetics/'+n+'.java' for n in ('SmartpcaPrepared','SmartpcaOutliers','SmartpcaShrinkage','SmartpcaAnalysis','SmartpcaFst')],
        coverage_note=SCOPE,implementation_scope=SCOPE,current_documentation=GUIDE,current_qualification='../smartpca-fst-qualification/STATUS.json',
        canonical_native_workflow_available=True,upstream_runtime_adapter_required=False,public_CLI_available=True,global_CLI_dispatch_available=True,
        source_build_required=True,released_binary_contains_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,
        new_native_family_credit=1,new_upstream_family_credit=0,source_roles_reviewed=6,source_role_coverage=COVERAGE.rsplit('/',1)[-1],
        paper_role_qualification={r['paper_id']:applied_role(r) for r in primary['roles']},
        validation_note='1,906 normal-build tests;362 packaged protocol commands on Windows21/25 and Linux21;24 original program cases including one refusal;23 direct original jackknife cases;69 finite comparisons reuse23 original cases. Fourteen focused methods and six example commands per JVM. Older qualified PCA261-command evidence and all failures remain distinct.',current_paper_role_limits=NOTE)
    summary['implementation_status_counts']=dict(Counter(x['implementation_status'] for x in inventory))
    summary['integration_family_counts']=dict(scoped=154,native=29,upstream=125,including_existing_adapters=157,validated_alias_labels_excluded_from_extra_credit=3)
    after=dict(before)
    for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    for name in (META[6],META[7]):
        page=before[name].decode();require(page.count(previous.NOTICE)==1 and 'id="smartpca-catalog-notice"' not in page,'Changed predecessor notice')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
            require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed predecessor embedded data')
            payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
            require(page.count('${mrpressoVersion(t)}')==1 and page.count('function renderContent(){')==1 and 'function smartpcaVersion' not in page,'Changed inherited edition renderer')
            helper=("function smartpcaVersion(t){if(t.tool_id!=='smartpca'||!t.source_role_qualification)return mrpressoVersion(t);"
                "return '<br><span class=\"smartpca-version\" style=\"overflow-wrap:anywhere\">'+t.source_role_qualification.roles.map(r=>"
                "'<span class=\"smartpca-context-version\">'+esc(r.context)+': '+(r.reported_package_edition===null?'package edition not reported':'reported package edition '+esc(r.reported_package_edition))+"
                "(r.reported_program_version===null?'':'; reported program identifier '+esc(r.reported_program_version))+'</span>').join('<br>')+'</span>';}\n")
            page=page.replace('${mrpressoVersion(t)}','${smartpcaVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        page=page.replace(previous.NOTICE,'<details><summary>Before smartpca admission (historical)</summary>'+previous.NOTICE+'</details>',1)
        body=re.search(r'<body[^>]*>',page,re.I);require(body is not None,'Missing report body');after[name]=(page[:body.end()]+NOTICE+page[body.end():]).encode()
    prefix=('# Current smartpca catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n**Warnings and required actions:** '+ACTION+
        '\n\nSee [current workflow guide](../SMARTPCA_TOOLS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in (META[3],META[4]):after[name]=prefix+before[name]
    return after

def check(before,after,primary,qualified):
    require(after==transition(before,primary,qualified),'Different deterministic native transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]]);unchanged=0
    for a,b in zip(old,new,strict=True):
        require({key:value for key,value in a.items() if key!='tools'}=={key:value for key,value in b.items() if key!='tools'},'Unrelated paper metadata changed')
        require([x for x in a['tools'] if x['tool_id']!=TARGET]==[x for x in b['tools'] if x['tool_id']!=TARGET],'Unrelated observation changed')
        unchanged+=sum(x['tool_id']!=TARGET for x in a['tools'])
        if b['paper_id'] in p.POSITIVE:
            x=next(u for u in a['tools'] if u['tool_id']==TARGET);y=next(u for u in b['tools'] if u['tool_id']==TARGET)
            for key in ('tool_id','tool','matched_text','extraction_score','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator','version_reported_near_name'):require(x[key]==y[key],'Original extraction changed')
            require(y['source_role_qualification']['source_role_review_applied'] is True,'Draft role was not explicitly applied')
    old_inventory,new_inventory=json.loads(before[META[1]]),json.loads(after[META[1]])
    require([x for x in old_inventory if x['id']!=TARGET]==[x for x in new_inventory if x['id']!=TARGET],'Unrelated inventory changed')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]])
    require(b['reviews'][:1691]==a['reviews'] and {key:value for key,value in a.items() if key!='reviews'}=={key:value for key,value in b.items() if key!='reviews'},'Prior ledger/scope changed')
    require(len(b['reviews'])==len({(x['pmcid'],x['tool_id']) for x in b['reviews']})==1697,'Wrong review credit')
    markers={REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open'}
    require({(paper['pmcid'],use['tool_id']) for paper in new for use in paper['tools'] if use['review_status'] in markers}=={(x['pmcid'],x['tool_id']) for x in b['reviews']},'Ledger/observation review markers differ')
    counts=Counter(x['implementation_status'] for x in new_inventory)
    require(counts['native-subset-validated']==29 and counts['upstream-adapter-validated']==125 and counts['method-overlap']==19 and counts['missing']==258 and unchanged==2716,'Wrong family/observation counts')
    require(sum(len(x['tools']) for x in new)==2722,'Changed positive observation population')
    return dict(papers=250,labels=446,provider_definitions=485,observations=2722,reviewed_uses=1697,pending_uses=1025,
        unchanged_prior_reviews=1691,new_reviews=6,unchanged_unrelated_observations=2716,unchanged_unrelated_inventory_rows=445,
        scoped_families=154,native=29,upstream=125,including_existing_adapters=157,method_overlap=19,missing=258,
        new_native_family_credit=1,new_upstream_family_credit=0,added_positive_observations=0,removed_positive_observations=0,
        whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
