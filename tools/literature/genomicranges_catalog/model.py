"""One upstream provider, five new reviews and two existing scope-note updates."""
from collections import Counter
import copy
import html
import json
import re
from skat225_catalog import model as previous
from genomicranges_catalog import primary as p

ROOT = p.ROOT
META, DATA = previous.META, previous.DATA
GUIDE = 'docs/literature/GENOMICRANGES_WORKFLOWS.md'
COVERAGE = DATA + '/genomicranges-source-role-coverage.json'
PROOF = DATA + '/genomicranges-catalog-validation.json'
PROGRAM = 'src/main/java/org/jlinalg/cli/GenomicRangesToolsCli.java'
TARGET = 'genomicranges'
REVIEWED = 'usage-summary-reviewed; paper-wide-completeness-review-open'
SCOPE = ('Original GenomicRanges prepared GRanges RDS operations through genomicranges-tools RUN/VERIFY/REPLAY: find/subset overlaps, nearest, '
         'distance-to-nearest, promoters, internal boundary indices, reduce and disjoin. Explicit '
         'Windows x86_64 Java21 reference profiles cover GenomicRanges1.44.0/R4.1.0 and '
         '1.58.0 or1.60.0/R4.6.1 with their pinned private dependencies. RUN retains full original '
         'RDS, metadata, seqinfo, warnings and failures; VERIFY checks saved integrity/core fields; '
         'REPLAY requires a new physical output. One upstream family, not eight operations or an '
         'additional native geometry family. No complete package, paper pipeline or Linux R qualification.')
NOTE = ('Five new source reviews; existing P094/P242 review identities, summaries and evidence anchors '
        'are preserved while their scope notes are updated. P051 names no function; reduce/disjoin '
        'are not attributed to it. Nearest-within-distance is not all-pairs-within-distance. Unknown '
        'editions, ties, strand choices and prepared-range construction remain explicit. Linked P094 '
        'Part3 output records Linux R4.4.2/GenomicRanges1.58.0/IRanges2.40.0; the tested Windows '
        'profiles differ and do not establish the executed paper commit. Preserve both single-base '
        'and POS-through-POS+1 source range constructions. P039 has an unattributed function mention '
        'and receives no provider observation or credit.')
ACTION = ('SKIP3: DEPENDENT ANALYSIS SKIPPED. Supply the exact named permitted Rscript, private package '
          'profile and prepared input. Download/install privately where allowed; if prohibited, keep '
          'the skip and state the unavailable analysis and exact prerequisite. FAIL2: NO COMPLETE '
          'RESULT. Preserve raw, failed, null, partial, superseded and invalidated attempts. Fix the '
          'named input, source/integrity, capacity, numerical or process cause and retry NEW output. '
          'Only admitted/result.rds from a complete admitted capture with successful VERIFY is a '
          'result. Read WARNINGS.md. Never silently repair IDs, NA/NaN/Inf, endpoints or unreported '
          'settings. Use the corrected source build: pre-fix external-worker JARs may terminate '
          'unrelated processes. Compile with JDK21. Offline verification is not RDS interpretation, '
          'refitting or authenticated provenance. Private evidence/runtimes need separate transfer. '
          'Cell Ranger stays excluded; unfinished reports stay off Pages.')
CURRENT = ('250 papers, 446 labels, 485 provider definitions and 2723 observations; '
           '1666 reviewed uses and 1057 pending. There are 149 scoped families '
           '(27 native + 122 upstream), 152 including three existing adapters; '
           '258 missing, 24 method-overlap and three aliases without extra family credit. '
           'The full 250-paper implementation goal remains unfinished.')
NOTICE = ('<aside id="genomicranges-catalog-notice" style="box-sizing:border-box;'
          'max-width:calc(100% - 2rem);margin:1rem;padding:1rem;border:2px solid #a56613;'
          'background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
          '<h2>GenomicRanges: one prepared-object upstream family</h2>'
          '<p><strong>Warnings and required actions:</strong> ' + html.escape(ACTION) + '</p>'
          '<p>' + html.escape(CURRENT) + '</p><p>' + html.escape(SCOPE + ' ' + NOTE) + '</p>'
          '<p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/' + GUIDE + '">'
          'Qualified profiles, source roles and recovery</a>.</p></aside>')
require = p.require


def registry():
    rows = previous.registry()
    require(len(rows) == 485, 'Changed preceding registry population')
    for row in rows:
        if row['id'] == TARGET:
            require(row['current_coverage'] == 'method-overlap', 'Already admitted/changed provider')
            row.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAM,
                       coverage_note=SCOPE + ' ' + NOTE)
    return rows


def validate(before, primary, qualified):
    require(set(before) == set(META), 'Exactly eight predecessor metadata files required')
    p.validate(primary)
    require(primary == p.frozen(), 'Changed independently frozen source roles')
    require(qualified['status'] == 'PASS_ORIGINAL_GENOMICRANGES_PREPARED_WORKFLOWS_FOR_ADMISSION'
            and qualified['producer'] == dict(bytes=197926716, sha256='75394cca138d9dd5f9cfccd211f00aaf28924584d153370b5c6de66fba8de90c'),
            'Supply the actual corrected measured producer')
    expected = dict(normal_tests=783, matrix_cases=168, full_RDS_comparisons=159,
                    original_error_comparisons=9, replays=27, public_resealed_controls=21,
                    runtime_controls=9, intake_replay_controls=7, fresh_primary_sources=250,
                    named_source_roles=7, proposed_new_reviews=5, prior_reviews_preserved=2,
                    source_functions_per_profile=160, proposed_new_upstream_family_credit=1)
    require(all(qualified[k] == value for k, value in expected.items()), 'Incomplete measured gates')
    require(all(qualified[k] is False for k in ['whole_tool_qualified', 'whole_paper_reproduced', 'whole_goal_complete']),
            'False broad qualification')
    require(qualified['profiles'] == ['windows-x86_64-r4.1.0-genomicranges1.44.0',
            'windows-x86_64-r4.6.1-genomicranges1.58.0', 'windows-x86_64-r4.6.1-genomicranges1.60.0']
            and qualified['runtime_capture_count'] == 195, 'Changed qualified platform/profile scope')
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in [0, 1, 2, 5]]
    require(len(papers) == 250 and len(inventory) == 446 and sum(len(r['tools']) for r in papers) == 2723
            and len(ledger['reviews']) == 1661, 'Changed preceding population')
    require(summary['integration_family_counts'] == dict(scoped=148, native=27, upstream=121,
            including_existing_adapters=151, validated_alias_labels_excluded_from_extra_credit=3), 'Changed preceding counts')
    row = next(r for r in inventory if r['id'] == TARGET)
    require(row['implementation_status'] == row['current_coverage'] == 'method-overlap'
            and row['papers'] == [r['paper_id'] for r in primary['roles']], 'Changed target membership/status')
    reviewed = {(r['pmcid'], r['tool_id']): r for r in ledger['reviews']}
    require(len(reviewed) == 1661, 'Duplicate prior review')
    byid = {r['paper_id']: r for r in papers}
    for role in primary['roles']:
        paper = byid[role['paper_id']]
        uses = [u for u in paper['tools'] if u['tool_id'] == TARGET]
        require(len(uses) == 1, 'Missing/duplicate target use')
        use = uses[0]
        anchor = role['source_pointers'][0]
        require(use['evidence_paragraph_sha256'] == anchor['paragraph_sha256']
                and use['evidence_paragraph_index'] == anchor['paragraph_index']
                and use['evidence_section'] == anchor['section'], 'Changed existing full-paragraph anchor')
        old = reviewed.get((paper['pmcid'], TARGET))
        if role.get('preserve_existing_review'):
            require(old is not None and use['use_summary'] == old['use_summary'] == role['usage_summary'],
                    'Existing reviewed summary changed')
        else:
            require(old is None and use['review_status'] == 'source-evidence-extracted; semantic-completeness-review-open',
                    'Already reviewed pending role')


def transition(before, primary, qualified):
    validate(before, primary, qualified)
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in [0, 1, 2, 5]]
    byid = {r['paper_id']: r for r in papers}
    for role in primary['roles']:
        paper = byid[role['paper_id']]
        use = next(r for r in paper['tools'] if r['tool_id'] == TARGET)
        use.update(implementation_scope_note=role['implementation_scope_note'], source_role_qualification=copy.deepcopy(role))
        if role.get('preserve_existing_review'):
            old = next(r for r in ledger['reviews'] if r['pmcid'] == paper['pmcid'] and r['tool_id'] == TARGET)
            old['implementation_scope_note'] = role['implementation_scope_note']
        else:
            use.update(use_summary=role['usage_summary'], review_status=REVIEWED,
                       evidence_selection='individually-reviewed-source-role')
            ledger['reviews'].append(dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id=TARGET,
                evidence_paragraph_sha256=use['evidence_paragraph_sha256'], use_summary=role['usage_summary'],
                implementation_scope_note=role['implementation_scope_note'], usage_context=use['usage_context'],
                version_reported_near_name=use['version_reported_near_name']))
    target = next(r for r in inventory if r['id'] == TARGET)
    target.update(previous_method_overlap_source=target['implementation_source'],
        current_coverage='upstream-adapter-validated', implementation_status='upstream-adapter-validated',
        existing_source=PROGRAM, implementation_source=PROGRAM, coverage_note=SCOPE, implementation_scope=SCOPE,
        current_documentation=GUIDE, current_qualification='../genomicranges-qualification/QUALIFICATION.json',
        canonical_adapter_available=True, public_CLI_available=True, global_CLI_dispatch_available=True,
        source_build_required=True, whole_tool_qualified=False, whole_paper_reproduced=False, whole_goal_complete=False,
        new_upstream_family_credit=1, source_roles_reviewed=7, source_role_coverage=COVERAGE.rsplit('/', 1)[-1],
        paper_role_qualification={r['paper_id']: copy.deepcopy(r) for r in primary['roles']},
        native_geometry_component_source='src/main/java/org/jlinalg/genetics/GenomicRangeGeometry.java',
        additional_native_family_credit=0, validation_note='783 focused normal-build tests;168 actual original packaged cases,27 replays,21 resealed refusals,9 runtime controls and7 intake/replay controls. Exact declared Windows reference profiles only.',
        current_paper_role_limits=NOTE)
    summary['implementation_status_counts'] = dict(Counter(r['implementation_status'] for r in inventory))
    summary['integration_family_counts'] = dict(scoped=149, native=27, upstream=122,
        including_existing_adapters=152, validated_alias_labels_excluded_from_extra_credit=3)
    after = dict(before)
    for name, value in [(META[0], papers), (META[1], inventory), (META[2], summary), (META[5], ledger)]:
        raw = p.encoded(value)
        if before[name].count(b'\n') == before[name].count(b'\r\n'):
            raw = raw.replace(b'\n', b'\r\n')
        after[name] = raw
    for name in [META[6], META[7]]:
        page = before[name].decode('utf-8')
        require(page.count(previous.NOTICE) == 1 and 'id="genomicranges-catalog-notice"' not in page, 'Changed report predecessor')
        if name == META[6]:
            match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
            require(match is not None and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]),
                tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]])), 'Changed embedded predecessor')
            payload = json.dumps(dict(papers=papers, tools=inventory, summary=summary), ensure_ascii=False).replace('<', '\\u003c')
            page = page[:match.start(2)] + payload + page[match.end(2):]
        page = page.replace(previous.NOTICE, '<details><summary>Before GenomicRanges admission (historical)</summary>' + previous.NOTICE + '</details>', 1)
        body = re.search(r'<body[^>]*>', page, re.I)
        require(body is not None, 'Missing report body')
        after[name] = (page[:body.end()] + NOTICE + page[body.end():]).encode('utf-8')
    for name in [META[3], META[4]]:
        after[name] = ('# Current GenomicRanges catalog checkpoint\n\n' + CURRENT + '\n\n' + SCOPE + '\n\n'
                       + NOTE + '\n\n**Warnings and required actions:** ' + ACTION + '\n\n'
                       + 'See [qualified workflow guide](../GENOMICRANGES_WORKFLOWS.md).\n\n'
                       + '---\n\nEarlier checkpoint text is historical.\n\n').encode('utf-8') + before[name]
    return after


def check(before, after, primary, qualified):
    require(after == transition(before, primary, qualified), 'Different deterministic admission')
    old, new = json.loads(before[META[0]]), json.loads(after[META[0]])
    unchanged = 0
    for a, b in zip(old, new, strict=True):
        require({k:v for k,v in a.items() if k != 'tools'} == {k:v for k,v in b.items() if k != 'tools'}, 'Changed paper metadata')
        require(len(a['tools']) == len(b['tools']), 'Added/removed observation')
        for x, y in zip(a['tools'], b['tools'], strict=True):
            if x['tool_id'] != TARGET:
                require(x == y, 'Changed unrelated observation')
                unchanged += 1
            else:
                for key in ['tool_id', 'tool', 'matched_text', 'extraction_score', 'evidence_section', 'evidence_paragraph_index',
                            'evidence_paragraph_sha256', 'evidence_locator', 'version_reported_near_name']:
                    require(x[key] == y[key], 'Changed extraction/source anchor')
    a, b = json.loads(before[META[1]]), json.loads(after[META[1]])
    require([r for r in a if r['id'] != TARGET] == [r for r in b if r['id'] != TARGET], 'Changed other445 inventory rows')
    a, b = json.loads(before[META[5]]), json.loads(after[META[5]])
    require({k:v for k,v in a.items() if k != 'reviews'} == {k:v for k,v in b.items() if k != 'reviews'}, 'Changed ledger scope')
    preserved, notes = 0, 0
    for x, y in zip(a['reviews'], b['reviews'][:1661], strict=True):
        if x == y:
            preserved += 1
        else:
            require(x['tool_id'] == TARGET and x['pmcid'] in ['PMC13649600', 'PMC12461027']
                    and {k:v for k,v in x.items() if k != 'implementation_scope_note'} == {k:v for k,v in y.items() if k != 'implementation_scope_note'},
                    'Changed prior source review outside its availability note')
            notes += 1
    keys = {(r['pmcid'], r['tool_id']) for r in b['reviews']}
    require(len(keys) == len(b['reviews']) == 1666 and preserved == 1659 and notes == 2, 'Wrong review preservation/credit')
    markers = {REVIEWED, 'individually-reviewed-primary-source-roles; whole-paper-method-review-open'}
    require(keys == {(paper['pmcid'], use['tool_id']) for paper in new for use in paper['tools'] if use['review_status'] in markers}, 'Paper/ledger reviewed identities differ')
    counts = Counter(r['implementation_status'] for r in json.loads(after[META[1]]))
    require(counts['native-subset-validated'] == 27 and counts['upstream-adapter-validated'] == 122
            and counts['method-overlap'] == 24 and counts['missing'] == 258 and counts['validated-family-alias'] == 3
            and unchanged == 2716 and len(registry()) == 485, 'Wrong family or observation counts')
    return dict(papers=250, labels=446, provider_definitions=485, observations=2723, reviewed_uses=1666,
        pending_uses=1057, prior_review_identities_and_summaries_preserved=1661, unchanged_prior_review_records=1659,
        existing_scope_notes_updated_without_review_credit=2, new_reviews=5, unchanged_unrelated_observations=2716,
        unchanged_unrelated_inventory_rows=445, scoped_families=149, native=27, upstream=122,
        including_existing_adapters=152, validated_family_alias_labels=3, method_overlap=24, missing=258,
        new_upstream_family_credit=1, new_native_family_credit=0, new_observations=0,
        whole_tool_qualified=False, whole_paper_reproduced=False, whole_goal_complete=False)
