"""One native-family/six-use transition; all predecessor and unrelated data retained."""
from collections import Counter
import copy,html,json,re
from squidpy_catalog import model as previous
from mrpresso_catalog import primary as p

ROOT,META,DATA=p.ROOT,previous.META,previous.DATA
TARGET='mrpresso';PROGRAM='src/main/java/org/jlinalg/cli/MrPressoToolsCli.java'
GUIDE='docs/literature/MRPRESSO_WORKFLOWS.md'
COVERAGE=DATA+'/mrpresso-source-role-coverage.json';PROOF=DATA+'/mrpresso-catalog-validation.json'
REVIEWED=previous.REVIEWED;require=p.require
SCOPE=('Native Java MRPRESSO1.0 original-profile global/outlier/distortion and conditional corrected estimates through '
 'mrpresso-tools RUN, full native-recomputation VERIFY and actual REPLAY. Pinned source commit '
 '3e3c92d7eda6dce0d1d66077373ec0f7ff4f7e87; explicit R-compatible MT/Inversion and Rounding/Rejection streams. '
 'Prepared one-outcome/one-or-more-exposure associations, finite full-rank profile and bounded resources. '
 'Original probability bounds, multivariable recycling, exact IDs/complete-case mappings and typed undefined fields remain. '
 'Invalid/perfect-fit inference and original distortion values above1 are retained without clamping and not admitted. '
 'The older analytic/multiple-outcome PRESSO-style methods remain separate. R is not a native runtime prerequisite. '
 'No whole upstream package/paper, author environment, biological calibration, data authentication or OS containment claim.')
NOTE=('Six individual source roles are reviewed; editions, seeds and simulation counts remain unreported. '
 'P105 outlier correction is separate from weak-instrument filtering; P125 sensitivity is separate from SIMEX/Egger. '
 'P135 uses multiple height/BMI exposures with one fracture outcome, not the older multiple-outcome diagnostic. '
 'P150 conditional method reporting, its325-outcome Bonferroni correction and decision tree remain separate. '
 'P189 nonsignificant global tests do not prove absence of pleiotropy or causality; corrected NA is conditional. '
 'P224 is a comparator to primary MR-RAPS analyses. P177 only recommends the method; execution is not established.')
ACTION=('SKIP3: DEPENDENT ANALYSIS SKIPPED. Provide/install the named permitted input, Java17+ and exact complete '
 'producer JAR; otherwise retain the skip. FAIL2: NO COMPLETE RESULT. Keep original/raw/partial/failed/undefined/'
 'superseded evidence, fix the named input/numerical/resource/deadline/integrity cause and retry NEW output. '
 'Require completion and passing VERIFY; read WARNINGS.md, analysis-status.json and per-field admission. '
 'Do not repair IDs, replace NA/NaN/Inf, clamp original probabilities above1, add Monte Carlo pseudocounts or '
 'replace source upper bounds by zero. Explicit diagnostic retention does not make invalid fields valid inference. '
 'Use the qualified af72b8cc... producer; the7b9bedea... tiny-unit verifier,0e44e460... perfect-fit guard and '
 '86bcb46c... missing-marker classification failures remain retained. Cell Ranger remains excluded. '
 'Private runtimes/JARs/full evidence need separate transfer; previously rejected historical exports remain local.')
CURRENT=('250 papers, 446 labels, 485 provider definitions and 2,722 positive observations; 1,691 reviewed/1,031 pending uses. '
 'There are 153 scoped families (28 native +125 upstream), 156 including three existing adapters; 258 missing, 20 '
 'method-overlap and three aliases without extra family credit. The full250-paper implementation and semantic-review goal remains unfinished.')
VISIBLE_ACTION=('SKIP3: dependent analysis skipped. Supply the named permitted input, Java17+ or exact producer JAR; otherwise keep the skip. '
 'FAIL2: no complete result. Preserve the evidence, fix the stated cause and retry NEW output. Read WARNINGS.md and per-field admission; '
 'require passing VERIFY. Original distortion values above one and undefined/perfect-fit inference must not be reported as valid results. R is not required.')
VISIBLE_CURRENT=('250 papers;153 scoped families (28 native +125 upstream);1,691 reviewed/1,031 pending uses. '
 'The full250-paper implementation and review goal remains unfinished.')
NOTICE=('<aside id="mrpresso-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;'
 'padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>MR-PRESSO workflows and paper uses</h2><p><strong>Warnings and required actions:</strong> '
 +html.escape(VISIBLE_ACTION)+'</p><p>'+html.escape(VISIBLE_CURRENT)+'</p><p><a href="'
 +'https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Run MR-PRESSO, inspect results and resolve failures</a>.</p>'
 '<details id="mrpresso-source-details"><summary>Source scope, paper roles and retained failures</summary><p>'
 +html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE+' '+NOTE)+'</p><p>'+html.escape(ACTION)+'</p></details></aside>')

def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def registry():
    rows=previous.registry();require(len(rows)==485,'Changed provider population');target=next(x for x in rows if x['id']==TARGET)
    require(target['current_coverage']=='method-overlap','Already admitted/changed MR-PRESSO provider')
    target.update(current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE);return rows

def frozen_primary():
    result=json.loads((ROOT/'docs/literature/mrpresso-catalog-sources/ROLES.json').read_bytes())
    require(result['status']=='PASS_FULL250_MRPRESSO_SOURCE_ROLES_WITH_EXPLICIT_UNREPORTED_SETTINGS' and result['papers_checked']==250 and
        [r['paper_id'] for r in result['roles']]==p.POSITIVE and result['discussion_only'][0]['paper_id']=='P177','Changed/incomplete primary roles')
    return result

def validate(before,primary,qualified):
    require(set(before)==set(META) and primary==frozen_primary(),'Changed predecessor/primary roles')
    require(qualified==json.loads((ROOT/'docs/literature/mrpresso-workflow-qualification/STATUS.json').read_bytes()) and
        qualified['status']=='PASS_NATIVE_MRPRESSO_ORIGINAL_PROFILE_SCOPED_WORKFLOW' and qualified['native_workflow_qualified'] is True and
        qualified['normal_build_tests']==dict(tests=797,failures=0,errors=0,skipped=0) and qualified['original_public_api_calls']==45 and
        qualified['packaged_commands']==309 and qualified['independent_original_comparisons']==153 and qualified['saved_control_host_checks']==51 and
        qualified['protocol_commands']==30 and not any(qualified[k] for k in ('catalog_admitted','whole_tool_qualified','whole_paper_reproduced','whole_goal_complete')),
        'Incomplete/changed actual native qualification')
    papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    require(len(papers)==250 and len(inventory)==446 and sum(len(x['tools']) for x in papers)==2722 and len(ledger['reviews'])==1685,'Changed predecessor population')
    require(summary['integration_family_counts']==dict(scoped=152,native=27,upstream=125,including_existing_adapters=155,validated_alias_labels_excluded_from_extra_credit=3),'Changed predecessor family counts')
    target=next(x for x in inventory if x['id']==TARGET)
    require(target['current_coverage']==target['implementation_status']=='method-overlap' and target['papers']==p.POSITIVE,'Changed MR-PRESSO source population/status')
    require(not any(x['tool_id']==TARGET for x in ledger['reviews']),'Target has existing review credit')
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id']);uses=[x for x in paper['tools'] if x['tool_id']==TARGET]
        require(uses==[role['original_observation']] and paper['xml_sha256']==role['xml_sha256'],'Changed original extraction')
        require(role['reported_near_name_version'] is None and role['reported_seed'] is None and role['reported_simulations'] is None,'Unreported settings cannot be invented')
    require(not any(x['tool_id']==TARGET for paper in papers if paper['paper_id']=='P177' for x in paper['tools']),'Discussion recommendation became execution')

def transition(before,primary,qualified):
    validate(before,primary,qualified);papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    for role in primary['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id']);use=next(x for x in paper['tools'] if x['tool_id']==TARGET)
        pointers=[copy.deepcopy(x) for x in role['anchors'] if x['paragraph_index']!=use['evidence_paragraph_index']]
        scope=role['scope_limit']+' Current native availability: '+SCOPE
        use.update(use_summary=role['summary'],implementation_scope_note=scope,review_status=REVIEWED,usage_context='analysis-method-description',
            evidence_selection='individually-reviewed-source-role',supporting_evidence=pointers,
            source_role_qualification=copy.deepcopy({key:value for key,value in role.items() if key!='original_observation'}))
        ledger['reviews'].append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=TARGET,evidence_paragraph_sha256=use['evidence_paragraph_sha256'],
            use_summary=role['summary'],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],
            supporting_evidence=copy.deepcopy(pointers),source_table_evidence=copy.deepcopy(role['table_anchors'])))
    target=next(x for x in inventory if x['id']==TARGET)
    target.update(previous_method_overlap_source=target['implementation_source'],current_coverage='native-subset-validated',implementation_status='native-subset-validated',
        existing_source=PROGRAM,implementation_source=PROGRAM,native_helper_source='src/main/java/org/jlinalg/mrpresso/MrPressoOriginal.java',
        coverage_note=SCOPE,implementation_scope=SCOPE,current_documentation=GUIDE,current_qualification='../mrpresso-workflow-qualification/STATUS.json',
        canonical_native_workflow_available=True,upstream_runtime_adapter_required=False,public_CLI_available=True,global_CLI_dispatch_available=True,
        source_build_required=True,released_binary_contains_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,
        new_native_family_credit=1,new_upstream_family_credit=0,source_roles_reviewed=6,source_role_coverage=COVERAGE.rsplit('/',1)[-1],
        paper_role_qualification={r['paper_id']:copy.deepcopy({key:value for key,value in r.items() if key!='original_observation'}) for r in primary['roles']},
        validation_note='797 focused normal-build tests;45 unchanged original-R witnesses;309 packaged commands/153 original comparisons on Windows21/25 and Linux21;51 saved-integrity checks and30 protocol commands. Degenerate inference limits, source probabilities above1 and all earlier failures remain explicit.',
        current_paper_role_limits=NOTE)
    summary['implementation_status_counts']=dict(Counter(x['implementation_status'] for x in inventory))
    summary['integration_family_counts']=dict(scoped=153,native=28,upstream=125,including_existing_adapters=156,validated_alias_labels_excluded_from_extra_credit=3)
    after=dict(before)
    for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    for name in (META[6],META[7]):
        page=before[name].decode();require(page.count(previous.NOTICE)==1 and 'id="mrpresso-catalog-notice"' not in page,'Changed predecessor notice')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
            require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed predecessor embedded data')
            payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
            require(page.count('${squidpyVersion(t)}')==1 and page.count('function renderContent(){')==1 and 'function mrpressoVersion' not in page,'Changed inherited edition renderer')
            helper=("function mrpressoVersion(t){if(t.tool_id!=='mrpresso'||!t.source_role_qualification)return squidpyVersion(t);"
                "return '<br><span class=\"mrpresso-version\" style=\"overflow-wrap:anywhere\">edition, seed and simulation count not reported</span>';}\n")
            page=page.replace('${squidpyVersion(t)}','${mrpressoVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        page=page.replace(previous.NOTICE,'<details><summary>Before MR-PRESSO admission (historical)</summary>'+previous.NOTICE+'</details>',1)
        body=re.search(r'<body[^>]*>',page,re.I);require(body is not None,'Missing report body');after[name]=(page[:body.end()]+NOTICE+page[body.end():]).encode()
    prefix=('# Current MR-PRESSO catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n**Warnings and required actions:** '+ACTION+
        '\n\nSee [current workflow guide](../MRPRESSO_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
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
    old_inventory,new_inventory=json.loads(before[META[1]]),json.loads(after[META[1]])
    require([x for x in old_inventory if x['id']!=TARGET]==[x for x in new_inventory if x['id']!=TARGET],'Unrelated inventory changed')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]])
    require(b['reviews'][:1685]==a['reviews'] and {key:value for key,value in a.items() if key!='reviews'}=={key:value for key,value in b.items() if key!='reviews'},'Prior ledger/scope changed')
    require(len(b['reviews'])==len({(x['pmcid'],x['tool_id']) for x in b['reviews']})==1691,'Wrong review credit')
    markers={REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open'}
    require({(paper['pmcid'],use['tool_id']) for paper in new for use in paper['tools'] if use['review_status'] in markers}=={(x['pmcid'],x['tool_id']) for x in b['reviews']},'Ledger/observation review markers differ')
    counts=Counter(x['implementation_status'] for x in new_inventory)
    require(counts['native-subset-validated']==28 and counts['upstream-adapter-validated']==125 and counts['method-overlap']==20 and counts['missing']==258 and unchanged==2716,'Wrong family/observation counts')
    require(sum(len(x['tools']) for x in new)==2722,'Changed positive observation population')
    return dict(papers=250,labels=446,provider_definitions=485,observations=2722,reviewed_uses=1691,pending_uses=1031,
        unchanged_prior_reviews=1685,new_reviews=6,unchanged_unrelated_observations=2716,unchanged_unrelated_inventory_rows=445,
        scoped_families=153,native=28,upstream=125,including_existing_adapters=156,method_overlap=20,missing=258,
        new_native_family_credit=1,new_upstream_family_credit=0,added_positive_observations=0,removed_positive_observations=0,
        whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
