"""Deterministic six-use Squidpy transition, gated by actual scoped qualification."""
from collections import Counter
import copy,html,json,re
from fusion_catalog import model as previous
from squidpy_catalog import primary as p

ROOT,META,DATA=p.ROOT,previous.META,previous.DATA
TARGET='squidpy';PROGRAM='src/main/java/org/jlinalg/cli/SquidpyWorkflowCli.java'
GUIDE='docs/literature/SQUIDPY_WORKFLOWS.md';COVERAGE=DATA+'/squidpy-source-role-coverage.json';PROOF=DATA+'/squidpy-catalog-validation.json'
REVIEWED=previous.REVIEWED
SCOPE=('Original Squidpy1.1.2/1.2.2/1.8.4 Linux CPU spatial-neighbor, weighted supplied-graph autocorrelation, '
       'local-pair ligand-receptor and source-available Nanostring workflows through squidpy-tools RUN/VERIFY/REPLAY. '
       'Typed original exports, H5AD transport where applicable, source/dependency/input fingerprints, explicit '
       'undefined/tainted-probability admission and safe observed-process ownership are retained. Windows21/25 '
       'support packaged component tests and offline verification; original Python fits are Linux tests. '
       'Supplementary native Java weighted-CSR Moran/Geary remains separately qualified. No silent native fallback, '
       'whole upstream package/paper reproduction, author full environment reconstruction, biological calibration, '
       'opaque-H5AD interpretation by saved readers, host authentication or OS RAM/job-containment claim.')
NOTE=('Six source uses are reviewed with explicit unknown caller/edition boundaries. P008 does not identify an '
      'exact function. P009 broader provenance reports1.1.2/Python3.8.13 while its near-name extraction remains '
      'unknown; compatible3.8.20 tests do not establish the reported full environment. P010 needs the intended '
      'captured interaction snapshot. P024 requires per-sample30-neighbor inputs. P094 uses1.2.2 Moran statistics; '
      'its custom ranking is separate. P161 radius units and FOV/sample boundaries must be supplied; '
      'read.nanostring is a validated available interface, not an established historical caller. Unreported '
      'settings remain unreported; custom GNN/image/temporal/ranking methods are separate.')
ACTION=('SKIP3: DEPENDENT ANALYSIS SKIPPED. Provide/install the exact named permitted local runtime/input, or '
        'retain the skip when unavailable or prohibited. FAIL2: NO COMPLETE RESULT. Keep original/raw/partial/'
        'failed/undefined/superseded evidence, correct the stated cause and retry NEW output. Read WARNINGS.md, '
        'analysis-status.json and per-row admission; require completion and successful VERIFY. Preserve IDs, '
        'source editions/options, NA/NaN/Inf and zero probabilities. Historical development float32 MatrixMarket '
        'exports containing NUL bytes are invalidated for transport qualification and retained unchanged; '
        'never strip padding or use them as qualified results. Use the corrected producer. Cell Ranger remains '
        'excluded. Transfer private runtimes/JARs/full evidence separately; historical sensitive exports stay '
        'withheld. Use current birth-checked process ownership; never stop unrelated processes.')
CURRENT=('250 papers,446 labels,485 provider definitions and2,722 positive observations;1,685 reviewed/1,037 '
         'pending uses. There are152 scoped families (27 native+125 upstream),155 including three existing '
         'adapters;258 missing,21 method-overlap and three aliases without extra family credit. The complete '
         '250-paper implementation/semantic-review objective remains unfinished.')
NOTICE=('<aside id="squidpy-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;'
        'padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
        '<h2>Squidpy: original spatial workflows and source roles</h2><p><strong>Warnings and required actions:</strong> '
        +html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE+' '+NOTE)+'</p><p><a href="'
        +'https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Commands, source scope and recovery</a>.</p></aside>')
require=p.require


def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()


def registry():
    rows=previous.registry();require(len(rows)==485,'Changed registry population');target=next(r for r in rows if r['id']==TARGET)
    require(target['current_coverage']=='method-overlap','Already admitted/changed Squidpy provider')
    target.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE+' '+NOTE);return rows


def frozen_primary():
    result=json.loads((ROOT/'docs/literature/squidpy-catalog-sources/ROLES.json').read_bytes())
    require(result['status']=='PASS_FULL250_SQUIDPY_SOURCE_ROLES_WITH_EXPLICIT_UNREPORTED_CALLERS' and result['papers_checked']==250 and
            [r['paper_id'] for r in result['roles']]==p.POSITIVE,'Incomplete/changed primary roles')
    return result


def validate(before,primary,qualified):
    require(set(before)==set(META) and primary==frozen_primary(),'Changed predecessor/primary source population')
    require(qualified==json.loads((ROOT/'docs/literature/squidpy-workflow-qualification/STATUS.json').read_bytes()) and
            qualified['status']=='PASS_SQUIDPY_ORIGINAL_WORKFLOW_SCOPED_CHECKPOINT' and qualified['normal_tests']==763 and
            qualified['packaged_cases']==73 and qualified['independent_original_comparisons']==66 and qualified['offline_controls']==22 and
            not any(qualified[k] for k in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete')),'Incomplete actual workflow qualification')
    papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    require(len(papers)==250 and len(inventory)==446 and sum(len(p['tools']) for p in papers)==2722 and len(ledger['reviews'])==1679,'Changed predecessor population')
    require(summary['integration_family_counts']==dict(scoped=151,native=27,upstream=124,including_existing_adapters=154,validated_alias_labels_excluded_from_extra_credit=3),'Changed family counts')
    target=next(r for r in inventory if r['id']==TARGET)
    require(target['current_coverage']==target['implementation_status']=='method-overlap' and target['papers']==p.POSITIVE,'Changed target membership/status')
    require(not any(r['tool_id']==TARGET for r in ledger['reviews']),'Existing Squidpy reviews require a separate transition')
    for role in primary['roles']:
        paper=next(r for r in papers if r['paper_id']==role['paper_id'])
        require([u for u in paper['tools'] if u['tool_id']==TARGET]==[role['original_observation']] and paper['xml_sha256']==role['xml_sha256'],'Changed original extraction')


def transition(before,primary,qualified):
    validate(before,primary,qualified);papers,inventory,summary,ledger=[json.loads(before[META[i]]) for i in (0,1,2,5)]
    for role in primary['roles']:
        paper=next(r for r in papers if r['paper_id']==role['paper_id']);use=next(u for u in paper['tools'] if u['tool_id']==TARGET)
        scope=role['scope_limit']+' Current availability: '+SCOPE
        pointers=[copy.deepcopy(a) for a in role['anchors'] if a['paragraph_index']!=use['evidence_paragraph_index']]
        qualification=copy.deepcopy({k:v for k,v in role.items() if k!='original_observation'})
        use.update(use_summary=role['summary'],implementation_scope_note=scope,review_status=REVIEWED,
                   usage_context='analysis-method-description',evidence_selection='individually-reviewed-source-role',
                   supporting_evidence=pointers,source_role_qualification=qualification)
        ledger['reviews'].append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=TARGET,
            evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['summary'],implementation_scope_note=scope,
            usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],supporting_evidence=copy.deepcopy(pointers)))
    target=next(r for r in inventory if r['id']==TARGET)
    target.update(previous_method_overlap_source=target['implementation_source'],current_coverage='upstream-adapter-validated',
        implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,
        coverage_note=SCOPE,implementation_scope=SCOPE,current_documentation=GUIDE,current_qualification='../squidpy-workflow-qualification/STATUS.json',
        canonical_adapter_available=True,public_CLI_available=True,global_CLI_dispatch_available=True,source_build_required=True,
        released_binary_contains_workflow_adapter=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,
        new_upstream_family_credit=1,source_roles_reviewed=6,source_role_coverage=COVERAGE.rsplit('/',1)[-1],
        paper_role_qualification={r['paper_id']:copy.deepcopy({k:v for k,v in r.items() if k!='original_observation'}) for r in primary['roles']},
        validation_note='763 focused normal/package tests;73 original-workflow cases across3 editions,66 independent original-API comparisons,22 offline controls and explicit runtime/transport/H5AD regressions. Linux original fits; Windows/Linux saved checks. Historical malformed float32 exports remain invalidated and retained.',current_paper_role_limits=NOTE)
    summary['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inventory))
    summary['integration_family_counts']=dict(scoped=152,native=27,upstream=125,including_existing_adapters=155,validated_alias_labels_excluded_from_extra_credit=3)
    after=dict(before)
    for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    for name in (META[6],META[7]):
        page=before[name].decode();require(page.count(previous.NOTICE)==1 and 'id="squidpy-catalog-notice"' not in page,'Changed predecessor notice')
        if name==META[6]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
            require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Changed embedded predecessor data')
            payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
        page=page.replace(previous.NOTICE,'<details><summary>Before Squidpy admission (historical)</summary>'+previous.NOTICE+'</details>',1)
        body=re.search(r'<body[^>]*>',page,re.I);require(body is not None,'Missing report body');after[name]=(page[:body.end()]+NOTICE+page[body.end():]).encode()
    for name in (META[3],META[4]):after[name]=('# Current Squidpy catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [workflow guide](../SQUIDPY_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()+before[name]
    return after


def check(before,after,primary,qualified):
    require(after==transition(before,primary,qualified),'Different deterministic transition')
    old,new=json.loads(before[META[0]]),json.loads(after[META[0]]);unchanged=0
    for a,b in zip(old,new,strict=True):
        require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Unrelated paper metadata changed')
        require([u for u in a['tools'] if u['tool_id']!=TARGET]==[u for u in b['tools'] if u['tool_id']!=TARGET],'Unrelated observation changed');unchanged+=sum(u['tool_id']!=TARGET for u in a['tools'])
        if b['paper_id'] in p.POSITIVE:
            x=next(u for u in a['tools'] if u['tool_id']==TARGET);y=next(u for u in b['tools'] if u['tool_id']==TARGET)
            for key in ('tool_id','tool','matched_text','extraction_score','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator','version_reported_near_name'):require(x[key]==y[key],'Original extraction changed')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']!=TARGET]==[r for r in b if r['id']!=TARGET],'Unrelated inventory changed')
    a,b=json.loads(before[META[5]]),json.loads(after[META[5]]);require(b['reviews'][:1679]==a['reviews'] and {k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'},'Prior ledger/scope changed')
    require(len(b['reviews'])==len({(r['pmcid'],r['tool_id']) for r in b['reviews']})==1685,'Wrong review credit')
    markers={REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open'}
    require({(p0['pmcid'],u['tool_id']) for p0 in new for u in p0['tools'] if u['review_status'] in markers}=={(r['pmcid'],r['tool_id']) for r in b['reviews']},'Ledger/observation markers differ')
    counts=Counter(r['implementation_status'] for r in json.loads(after[META[1]]))
    require(counts['native-subset-validated']==27 and counts['upstream-adapter-validated']==125 and counts['method-overlap']==21 and counts['missing']==258 and unchanged==2716,'Wrong family/observation counts')
    require(sum(len(p0['tools']) for p0 in new)==2722,'Changed positive population')
    return dict(papers=250,labels=446,provider_definitions=485,observations=2722,reviewed_uses=1685,pending_uses=1037,
        unchanged_prior_reviews=1679,new_reviews=6,unchanged_unrelated_observations=2716,unchanged_unrelated_inventory_rows=445,
        scoped_families=152,native=27,upstream=125,including_existing_adapters=155,method_overlap=21,missing=258,
        new_upstream_family_credit=1,new_native_family_credit=0,added_positive_observations=0,removed_positive_observations=0,
        whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
