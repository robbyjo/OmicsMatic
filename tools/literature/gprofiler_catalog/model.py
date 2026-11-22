"""Deterministic source-bound g:Profiler admission; imports never mutate canonical data."""
from collections import Counter
import copy
import html
import json
import re
from genomicranges_catalog import model as previous
from gprofiler_catalog import primary as p

ROOT, META, DATA = p.ROOT, previous.META, previous.DATA
TARGET = 'gprofiler'
GUIDE = 'docs/literature/GPROFILER_WORKFLOWS.md'
PROGRAM = 'src/main/java/org/jlinalg/cli/GprofilerWorkflowCli.java'
COVERAGE = DATA + '/gprofiler-source-role-coverage.json'
PROOF = DATA + '/gprofiler-catalog-validation.json'
REVIEWED = previous.REVIEWED
SCOPE = ('Original g:GOSt service through gprofiler-tools RUN, offline VERIFY and offline REPLAY: explicit '
         'ranked/unranked, named/combined, over/under-representation, custom backgrounds, correction and GO '
         'driver settings. Exact full-build/organism metadata preflight precedes gene submission. Original '
         'requests, responses, mapping/normalization, absence sentinels and errors are retained; complete '
         'closed-inventory admission and response-derived reports are checked. Statistics/mapping/highlighting '
         'execute upstream. Windows Java21 original-service controls; Linux Java21 saved verification/replay. '
         'Reference service e114_eg62_p19_9dc92ac8 is not substituted for paper editions. No original R-client '
         'plotting, complete package/paper reproduction, independent refit or biological calibration.')
NOTE = ('Seven new source reviews, no changed prior review or observation identities. P052 reports '
        'e111_eg58_p18_30541362, but the inspected archive returns e111_eg58_p18_28c0ce60: retain SKIP3 '
        'for that exact historical build. Preserve its unusual protein-filter wording. P088 uses Ensembl110 '
        'background, separately from FUMA. P090/P227 report R client0.2.3/0.2.1, not full server builds. '
        'P096/P088 calendar dates do not identify complete archives. Preserve ranked/custom-background P208 '
        'and HPO under-representation P227; its separate regression correction does not establish the '
        'g:Profiler correction. Unreported options remain unknown. Public evidence copies redact cookie '
        'values explicitly; originals and all scientific payloads are retained unchanged.')
ACTION = ('SKIP3: DEPENDENT ANALYSIS SKIPPED. Restore permitted HTTPS access to biit.cs.ut.ee and the exact '
          'full build named in request.json/wire trace, or retain the skipped dependent analysis. Never '
          'substitute another archive or weaken TLS. FAIL2: NO COMPLETE RESULT. Preserve raw, failed, '
          'partial, null, superseded and invalidated attempts; fix the named input, protocol, capacity or '
          'timeout cause and retry NEW output. Read WARNINGS.md and require a valid COMPLETED.json and '
          'successful VERIFY. Original headers can contain Set-Cookie: keep raw traces private and publish '
          'only declared redacted copies while retaining originals. No IDs, probabilities or settings are '
          'silently repaired. Use current source builds for worker execution; pre-fix worker JARs remain '
          'unsafe. Private evidence/runtimes require separate transfer. Cell Ranger remains excluded; '
          'unfinished reports remain off Pages. Integrity checks do not authenticate coordinated replacements.')
CURRENT = ('250 papers, 446 labels, 485 provider definitions and 2,723 observations; 1,673 reviewed uses '
           'and 1,050 pending. There are 150 scoped families (27 native + 123 upstream), 153 including three '
           'existing adapters; 258 missing, 23 method-overlap and three aliases without extra credit. '
           'The full 250-paper implementation objective remains unfinished.')
NOTICE = ('<aside id="gprofiler-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
          'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
          '<h2>g:Profiler: original service workflow and exact-build limits</h2><p><strong>Warnings and required actions:</strong> '
          + html.escape(ACTION) + '</p><p>' + html.escape(CURRENT) + '</p><p>' + html.escape(SCOPE + ' ' + NOTE)
          + '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/' + GUIDE
          + '">Workflow, source roles, public-copy provenance and recovery</a>.</p></aside>')
require = p.require


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def frozen_primary():
    folder = ROOT / 'docs/literature/gprofiler-api-development'
    raw = (folder / 'CENSUS.json').read_bytes()
    require(p.pin(raw) == json.loads((folder / 'CENSUS-PIN.json').read_bytes()), 'Changed frozen source census')
    primary = json.loads(raw)
    require(primary['status'] == 'PASS_FULL250_PRIMARY_GPROFILER_SOURCE_AUDIT_ONLY'
            and primary['papers_checked'] == len(primary['identities']) == 250
            and primary['unbound_broader_candidates'] == primary['table_candidates'] == [], 'Changed source census scope')
    require([r['paper_id'] for r in primary['roles']] == ['P052','P088','P090','P096','P208','P227','P228'], 'Changed individually inspected roles')
    return primary


def registry():
    rows = previous.registry()
    require(len(rows) == 485, 'Changed registry population')
    row = next(r for r in rows if r['id'] == TARGET)
    require(row['current_coverage'] == 'method-overlap', 'Already admitted/changed provider')
    row.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAM, coverage_note=SCOPE + ' ' + NOTE)
    return rows


def validate(before, primary, qualified):
    require(set(before) == set(META) and primary == frozen_primary(), 'Changed predecessor/source roles')
    require(qualified == json.loads((ROOT / 'docs/literature/gprofiler-workflow-qualification/QUALIFICATION.json').read_bytes()),
            'Changed exact qualified workflow receipt')
    require(qualified['status'] == 'PASS_GPROFILER_ORIGINAL_PROTOCOL_WORKFLOWS_FOR_ADMISSION'
            and qualified['normal_tests'] == 28 and qualified['enrichment_RUN_cases'] == 8
            and qualified['metadata_RUN_cases'] == 1 and qualified['Windows_offline_REPLAY_cases'] == 9
            and qualified['Linux_offline_REPLAY_cases'] == 3 and qualified['saved_protocol_refusal_controls'] == 21
            and qualified['runtime_resource_replay_refusal_controls'] == 11
            and qualified['exact_build_preflight_SKIP3_cases'] == 1, 'Incomplete actual workflow gates')
    require(all(qualified[k] is False for k in ['whole_tool_qualified','whole_paper_reproduced','whole_goal_complete'])
            and qualified['public_cookie_values_redacted'] is True, 'False scope/publication claim')
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in [0,1,2,5]]
    require(len(papers) == 250 and len(inventory) == 446 and sum(len(r['tools']) for r in papers) == 2723
            and len(ledger['reviews']) == 1666, 'Changed predecessor population')
    require(summary['integration_family_counts'] == dict(scoped=149,native=27,upstream=122,
            including_existing_adapters=152,validated_alias_labels_excluded_from_extra_credit=3), 'Changed predecessor counts')
    target = next(r for r in inventory if r['id'] == TARGET)
    require(target['current_coverage'] == target['implementation_status'] == 'method-overlap'
            and target['papers'] == [r['paper_id'] for r in primary['roles']], 'Changed target membership/status')
    seen = {(r['pmcid'],r['tool_id']) for r in ledger['reviews']}
    require(len(seen) == 1666, 'Duplicate preceding review')
    for role in primary['roles']:
        paper = next(r for r in papers if r['paper_id'] == role['paper_id'])
        uses = [u for u in paper['tools'] if u['tool_id'] == TARGET]
        require(len(uses) == 1 and (paper['pmcid'],TARGET) not in seen, 'Missing/duplicate/already-reviewed target')
        use, anchor = uses[0], role['source_pointers'][0]
        require(use['evidence_paragraph_index'] == anchor['paragraph_index']
                and use['evidence_paragraph_sha256'] == anchor['paragraph_sha256']
                and use['evidence_section'] == anchor['section']
                and use['review_status'] == 'source-evidence-extracted; semantic-completeness-review-open', 'Changed full-paragraph anchor/pending status')


def transition(before, primary, qualified):
    validate(before, primary, qualified)
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in [0,1,2,5]]
    for role in primary['roles']:
        paper = next(r for r in papers if r['paper_id'] == role['paper_id'])
        use = next(r for r in paper['tools'] if r['tool_id'] == TARGET)
        note = role['scope'] + ' Current availability: ' + SCOPE
        use.update(use_summary=role['summary'], implementation_scope_note=note, review_status=REVIEWED,
                   evidence_selection='individually-reviewed-source-role', source_role_qualification=copy.deepcopy(role))
        ledger['reviews'].append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=TARGET,
            evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['summary'],
            implementation_scope_note=note,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name']))
    target = next(r for r in inventory if r['id'] == TARGET)
    target.update(previous_method_overlap_source=target['implementation_source'], current_coverage='upstream-adapter-validated',
        implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,
        coverage_note=SCOPE,implementation_scope=SCOPE,current_documentation=GUIDE,
        current_qualification='../gprofiler-workflow-qualification/QUALIFICATION.json',canonical_adapter_available=True,
        public_CLI_available=True,global_CLI_dispatch_available=True,source_build_required=True,
        whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,new_upstream_family_credit=1,
        source_roles_reviewed=7,source_role_coverage=COVERAGE.rsplit('/',1)[-1],
        paper_role_qualification={r['paper_id']:copy.deepcopy(r) for r in primary['roles']},
        validation_note='28 focused normal-build tests; eight original enrichment and one metadata case; nine Windows and three Linux offline replays;21 saved-protocol and11 runtime/resource/replay refusals. Exact historical P052 build remains skipped before gene submission. Public cookie-value redactions are explicit; scientific payloads unchanged.',
        current_paper_role_limits=NOTE)
    summary['implementation_status_counts'] = dict(Counter(r['implementation_status'] for r in inventory))
    summary['integration_family_counts'] = dict(scoped=150,native=27,upstream=123,including_existing_adapters=153,validated_alias_labels_excluded_from_extra_credit=3)
    after = dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)]:
        raw = encoded(value)
        if before[name].count(b'\n') == before[name].count(b'\r\n'): raw = raw.replace(b'\n',b'\r\n')
        after[name] = raw
    for name in [META[6],META[7]]:
        page = before[name].decode('utf-8')
        require(page.count(previous.NOTICE) == 1 and 'id="gprofiler-catalog-notice"' not in page, 'Changed predecessor report notice')
        if name == META[6]:
            match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
            require(match is not None and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])), 'Changed embedded predecessor')
            payload = json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c')
            page = page[:match.start(2)] + payload + page[match.end(2):]
            old="${t.version_reported_near_name?'<br>reported '+esc(t.version_reported_near_name):''}"
            require(page.count(old) == 1 and 'function gprofilerVersion' not in page, 'Changed version renderer')
            helper="function gprofilerVersion(t){if(t.tool_id!=='gprofiler'||!t.source_role_qualification)return t.version_reported_near_name?'<br>reported '+esc(t.version_reported_near_name):'';const r=t.source_role_qualification,v=[];if(r.reported_client)v.push('R client '+r.reported_client);for(const b of r.reported_builds||[])v.push((/^e[0-9]+_eg/.test(b)?'database ':'service ')+b);return v.length?'<br><span class=\"gprofiler-version\" style=\"overflow-wrap:anywhere\">reported '+esc(v.join('; '))+'</span>':'';}\n"
            page = page.replace(old,'${gprofilerVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
        page = page.replace(previous.NOTICE,'<details><summary>Before g:Profiler admission (historical)</summary>'+previous.NOTICE+'</details>',1)
        body = re.search(r'<body[^>]*>',page,re.I);require(body is not None,'Missing body')
        after[name] = (page[:body.end()]+NOTICE+page[body.end():]).encode()
    for name in [META[3],META[4]]:
        after[name] = ('# Current g:Profiler catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [workflow guide](../GPROFILER_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()+before[name]
    return after


def check(before, after, primary, qualified):
    require(after == transition(before,primary,qualified), 'Different deterministic transition')
    a,b = json.loads(before[META[0]]),json.loads(after[META[0]])
    unchanged = 0
    for old,new in zip(a,b,strict=True):
        require({k:v for k,v in old.items() if k!='tools'} == {k:v for k,v in new.items() if k!='tools'},'Changed paper metadata')
        require(len(old['tools']) == len(new['tools']),'Added/removed observation')
        for x,y in zip(old['tools'],new['tools'],strict=True):
            if x['tool_id'] != TARGET: require(x==y,'Changed unrelated observation');unchanged+=1
            else:
                for key in ['tool_id','tool','matched_text','extraction_score','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator','version_reported_near_name']:
                    require(x[key]==y[key],'Changed extraction/source anchor')
    old,new = json.loads(before[META[1]]),json.loads(after[META[1]])
    require([r for r in old if r['id']!=TARGET] == [r for r in new if r['id']!=TARGET],'Changed other inventory rows')
    old,new = json.loads(before[META[5]]),json.loads(after[META[5]])
    require(new['reviews'][:1666] == old['reviews'] and {k:v for k,v in old.items() if k!='reviews'} == {k:v for k,v in new.items() if k!='reviews'},'Changed prior ledger records/scope')
    require(len(new['reviews']) == len({(r['pmcid'],r['tool_id']) for r in new['reviews']}) == 1673,'Wrong review credit')
    markers = {REVIEWED, 'individually-reviewed-primary-source-roles; whole-paper-method-review-open'}
    marked = {(paper['pmcid'], use['tool_id']) for paper in b for use in paper['tools'] if use['review_status'] in markers}
    require(marked == {(r['pmcid'],r['tool_id']) for r in new['reviews']}, 'Reviewed markers and ledger identities differ')
    counts = Counter(r['implementation_status'] for r in json.loads(after[META[1]]))
    require(counts['native-subset-validated']==27 and counts['upstream-adapter-validated']==123 and counts['method-overlap']==23 and counts['missing']==258 and unchanged==2716,'Wrong family/observation counts')
    return dict(papers=250,labels=446,provider_definitions=485,observations=2723,reviewed_uses=1673,pending_uses=1050,
                unchanged_prior_reviews=1666,new_reviews=7,unchanged_unrelated_observations=2716,unchanged_unrelated_inventory_rows=445,
                scoped_families=150,native=27,upstream=123,including_existing_adapters=153,method_overlap=23,missing=258,
                new_upstream_family_credit=1,new_native_family_credit=0,new_observations=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
