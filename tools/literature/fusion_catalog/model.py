"""Deterministic FUSION admission: six uses; cohort exclusion; method baseline."""
from collections import Counter
import copy
import html
import json
import re
from gprofiler_catalog import model as previous
from fusion_catalog import primary as p
from fusion_catalog.roles import POSITIVE

ROOT, META, DATA = p.ROOT, previous.META, previous.DATA
TARGET = 'fusion'
GUIDE = 'docs/literature/FUSION_WORKFLOWS.md'
PROGRAM = 'src/main/java/org/jlinalg/cli/FusionWorkflowCli.java'
COVERAGE = DATA + '/fusion-source-role-coverage.json'
PROOF = DATA + '/fusion-catalog-validation.json'
REVIEWED = previous.REVIEWED
SCOPE = ('Original FUSION TRAIN/ASSOCIATE/POSTPROCESS through fusion-tools RUN, saved offline VERIFY and real REPLAY. '
         'Pinned revision9346c1222bffbb34499fa7a8e23c1b701b55cb05 with exact original scripts/license, declared R/package/tool '
         'versions, original inputs/exports, raw source TWAS.P and exact formatted main/MHC reconstruction. '
         'Training, prepared-weight TWAS, fixed-ridge missing-Z imputation, COLOC/SuSiE colocalization, permutation, '
         'conditional/omnibus/correlation/LDSC postprocessing, reports and original plots are scoped by recorded cases. '
         'Windows21/25 association, Linux21 association/training, Windows21 postprocessing/plots. Native prepared-model '
         'Java association is also available. Current runtime is not an established historical paper edition; no '
         'independent training refit, complete upstream-tool or paper reproduction, biological calibration, opaque-RDS '
         'interpretation by the offline reader, host attestation or OS memory-containment claim.')
NOTE = ('Six positive software-analysis/comparator reviews; P212 is removed from positive uses and retained in the '
        'exclusion audit as a FUSION study-cohort homonym. P108 is retained as a FUSION-labelled method-baseline '
        'description, not added as an original-software use: its archived v1.0.0 manuscript scripts import isoTWAS '
        'and call univariate elastic-net/BLUP/SuSiE functions. Original FUSION command execution is unestablished. '
        'P183 includes prediction training and association for lncRNAs/PCGs, explicitly not circRNAs. P131 includes '
        'FUSION colocalization; Genomic SEM0.0.5 is a separate package. P181 coloc follow-up is separately named; '
        'P238 distinguishes shared algorithm from its LeafCutter/FUSION comparator. Unreported editions/options '
        'stay unknown; no paper runtime is inferred from current availability.')
ACTION = ('SKIP3: DEPENDENT ANALYSIS SKIPPED. Supply the exact named permitted input, R/package or tool edition, '
          'installing privately where allowed; otherwise retain the skip and explain the missing prerequisite and remedy. '
          'FAIL2: NO COMPLETE RESULT. Preserve original/raw/partial/failed/null/superseded evidence, correct the stated '
          'input/protocol/numeric/resource/time/process cause and retry NEW output. Read WARNINGS.md and require a '
          'valid COMPLETED.json and successful VERIFY. Preserve IDs, NA/NaN/Inf, zero p-values, source thresholds and '
          'stochastic differences. GEMMA output routing, modern-GCTA startup and differing replay admission require '
          'explicit recorded opt-in. Original compact plots can have overlapping labels. Use current birth-checked '
          'worker ownership; pre-fix worker producers remain unsafe. Transfer private runtimes/JARs/full evidence '
          'separately; historical sensitive branch exports remain withheld. Cell Ranger remains excluded. Hashes '
          'give integrity, not authenticated provenance.')
CURRENT = ('250 papers, 446 labels, 485 provider definitions and 2,722 positive paper-tool observations; '
           '1,679 reviewed uses and 1,043 pending. There are 151 scoped families (27 native +124 upstream), '
           '154 including three existing adapters; 258 missing, 22 method-overlap and three aliases without extra '
           'family credit. One false-positive cohort observation is retained separately. The full 250-paper '
           'implementation and semantic-completeness objective remains unfinished.')
NOTICE = ('<aside id="fusion-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
          'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
          '<h2>FUSION: original workflows and corrected paper roles</h2><p><strong>Warnings and required actions:</strong> '
          + html.escape(ACTION) + '</p><p>' + html.escape(CURRENT) + '</p><p>' + html.escape(SCOPE + ' ' + NOTE)
          + '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/' + GUIDE
          + '">Commands, source roles, measured qualification and recovery</a>.</p></aside>')
require = p.require


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def frozen_primary():
    result = json.loads((ROOT / 'docs/literature/fusion-catalog-sources/ROLES.json').read_bytes())
    require(result['status'] == 'PASS_FULL250_FUSION_ROLES_WITH_AUTHOR_BASELINE_AND_COHORT_EXCLUSION' and
            result['primary_XML_freshly_checked'] == 250 and [r['paper_id'] for r in result['roles']] == POSITIVE,
            'Changed reviewed positive source population')
    require([r['paper_id'] for r in result['excluded_observations']] == ['P212'] and
            [r['paper_id'] for r in result['additional_method_baselines']] == ['P108'], 'Changed role exclusions/baseline')
    return result


def registry():
    rows = previous.registry()
    require(len(rows) == 485, 'Changed registry population')
    row = next(r for r in rows if r['id'] == TARGET)
    require(row['current_coverage'] == 'method-overlap', 'Already admitted/changed FUSION provider')
    row.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAM, coverage_note=SCOPE + ' ' + NOTE)
    return rows


def validate(before, primary, qualified):
    require(set(before) == set(META) and primary == frozen_primary(), 'Changed predecessor/reviewed roles')
    require(qualified == json.loads((ROOT / 'docs/literature/fusion-workflow-qualification/STATUS.json').read_bytes()) and
            qualified['status'] == 'PASS_FUSION_ORIGINAL_WORKFLOW_SCOPED_CHECKPOINT' and
            qualified['normal_tests'] == 75 and qualified['packaged_cases'] == 30 and
            qualified['resealed_offline_controls'] == 17 and qualified['runtime_controls'] == 6 and
            not qualified['whole_tool_qualified'] and not qualified['whole_paper_reproduced'] and
            not qualified['whole_goal_complete'], 'Incomplete actual workflow scope')
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in [0, 1, 2, 5]]
    require(len(papers) == 250 and len(inventory) == 446 and sum(len(p['tools']) for p in papers) == 2723 and
            len(ledger['reviews']) == 1673, 'Changed predecessor population')
    require(summary['integration_family_counts'] == dict(scoped=150, native=27, upstream=123,
            including_existing_adapters=153, validated_alias_labels_excluded_from_extra_credit=3), 'Changed family counts')
    target = next(r for r in inventory if r['id'] == TARGET)
    require(target['current_coverage'] == target['implementation_status'] == 'method-overlap' and
            target['papers'] == ['P112', 'P120', 'P131', 'P181', 'P183', 'P212', 'P238'], 'Changed target membership/status')
    require(not any(r['tool_id'] == TARGET for r in ledger['reviews']), 'Preserve existing FUSION reviews; new transition needed')
    for role in primary['roles']:
        paper = next(r for r in papers if r['paper_id'] == role['paper_id'])
        require([u for u in paper['tools'] if u['tool_id'] == TARGET] == [role['original_observation']] and
                paper['xml_sha256'] == role['xml_sha256'], 'Changed original positive extraction')
    excluded = primary['excluded_observations'][0]
    paper = next(r for r in papers if r['paper_id'] == 'P212')
    require([u for u in paper['tools'] if u['tool_id'] == TARGET] == [excluded['original_observation']],
            'Changed excluded observation; preserve and reassess')
    require(not any(u['tool_id'] == TARGET for p0 in papers if p0['paper_id'] == 'P108' for u in p0['tools']),
            'Do not infer an original-software use from method-baseline wording')


def transition(before, primary, qualified):
    validate(before, primary, qualified)
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in [0, 1, 2, 5]]
    excluded = next(r for r in papers if r['paper_id'] == 'P212')
    excluded['tools'] = [u for u in excluded['tools'] if u['tool_id'] != TARGET]
    for role in primary['roles']:
        paper = next(r for r in papers if r['paper_id'] == role['paper_id'])
        use = next(u for u in paper['tools'] if u['tool_id'] == TARGET)
        scope = role['scope'] + ' Current availability: ' + SCOPE
        pointers = [{k: a[k] for k in ['paragraph_index', 'paragraph_sha256', 'section']}
                    for a in role['source_pointers'] if a['paragraph_index'] != use['evidence_paragraph_index']]
        use.update(use_summary=role['summary'], implementation_scope_note=scope, review_status=REVIEWED,
                   usage_context=role['usage_context'], evidence_selection='individually-reviewed-source-role',
                   supporting_evidence=pointers, source_role_qualification=copy.deepcopy(role))
        ledger['reviews'].append(dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id=TARGET,
            evidence_paragraph_sha256=use['evidence_paragraph_sha256'], use_summary=role['summary'],
            implementation_scope_note=scope, usage_context=role['usage_context'],
            version_reported_near_name=use['version_reported_near_name'], supporting_evidence=copy.deepcopy(pointers)))
    target = next(r for r in inventory if r['id'] == TARGET)
    target.update(previous_method_overlap_source=target['implementation_source'], current_coverage='upstream-adapter-validated',
        implementation_status='upstream-adapter-validated', existing_source=PROGRAM, implementation_source=PROGRAM,
        paper_count=6, papers=POSITIVE[:], coverage_note=SCOPE, implementation_scope=SCOPE,
        current_documentation=GUIDE, current_qualification='../fusion-workflow-qualification/STATUS.json',
        canonical_adapter_available=True, public_CLI_available=True, global_CLI_dispatch_available=True,
        source_build_required=True, whole_tool_qualified=False, whole_paper_reproduced=False, whole_goal_complete=False,
        new_upstream_family_credit=1, source_roles_reviewed=6, excluded_cohort_observations=1,
        source_role_coverage=COVERAGE.rsplit('/', 1)[-1],
        paper_role_qualification={r['paper_id']: copy.deepcopy(r) for r in primary['roles']},
        additional_method_baselines=copy.deepcopy(primary['additional_method_baselines']),
        validation_note='75 focused normal-build tests,30 packaged cases,17 offline controls,6 runtime/resource controls; independent saved reader on Windows/Linux,4 resealed scientific contradictions refused,15 unchanged-original association rows and3 complete panel exports match; complete producer payload map checked. Explicit operation/platform and source-object limits apply.',
        current_paper_role_limits=NOTE)
    summary['paper_tool_observations'] = sum(len(r['tools']) for r in papers)
    summary['implementation_status_counts'] = dict(Counter(r['implementation_status'] for r in inventory))
    summary['integration_family_counts'] = dict(scoped=151, native=27, upstream=124,
            including_existing_adapters=154, validated_alias_labels_excluded_from_extra_credit=3)
    after = dict(before)
    for name, value in [(META[0], papers), (META[1], inventory), (META[2], summary), (META[5], ledger)]:
        raw = encoded(value)
        if before[name].count(b'\n') == before[name].count(b'\r\n'):
            raw = raw.replace(b'\n', b'\r\n')
        after[name] = raw
    for name in [META[6], META[7]]:
        page = before[name].decode()
        require(page.count(previous.NOTICE) == 1 and 'id="fusion-catalog-notice"' not in page, 'Changed predecessor notice')
        if name == META[6]:
            match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
            require(match is not None and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]),
                    tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]])), 'Changed embedded predecessor data')
            payload = json.dumps(dict(papers=papers, tools=inventory, summary=summary), ensure_ascii=False).replace('<', '\\u003c')
            page = page[:match.start(2)] + payload + page[match.end(2):]
        page = page.replace(previous.NOTICE, '<details><summary>Before FUSION admission (historical)</summary>' + previous.NOTICE + '</details>', 1)
        body = re.search(r'<body[^>]*>', page, re.I)
        require(body is not None, 'Missing page body')
        after[name] = (page[:body.end()] + NOTICE + page[body.end():]).encode()
    for name in [META[3], META[4]]:
        after[name] = ('# Current FUSION catalog checkpoint\n\n' + CURRENT + '\n\n' + SCOPE + '\n\n' + NOTE +
                       '\n\n**Warnings and required actions:** ' + ACTION + '\n\nSee [workflow guide](../FUSION_WORKFLOWS.md).'
                       '\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode() + before[name]
    return after


def check(before, after, primary, qualified):
    require(after == transition(before, primary, qualified), 'Different deterministic transition')
    old, new = json.loads(before[META[0]]), json.loads(after[META[0]])
    unchanged = 0
    for a, b in zip(old, new, strict=True):
        require({k: v for k, v in a.items() if k != 'tools'} == {k: v for k, v in b.items() if k != 'tools'},
                'Unrelated paper metadata changed')
        require([u for u in a['tools'] if u['tool_id'] != TARGET] == [u for u in b['tools'] if u['tool_id'] != TARGET],
                'Unrelated observation changed')
        unchanged += len([u for u in a['tools'] if u['tool_id'] != TARGET])
        if b['paper_id'] in POSITIVE:
            x = next(u for u in a['tools'] if u['tool_id'] == TARGET)
            y = next(u for u in b['tools'] if u['tool_id'] == TARGET)
            for key in ['tool_id', 'tool', 'matched_text', 'extraction_score', 'evidence_section',
                        'evidence_paragraph_index', 'evidence_paragraph_sha256', 'evidence_locator', 'version_reported_near_name']:
                require(x[key] == y[key], 'Original positive extraction/provenance changed')
    a, b = json.loads(before[META[1]]), json.loads(after[META[1]])
    require([r for r in a if r['id'] != TARGET] == [r for r in b if r['id'] != TARGET], 'Other inventory rows changed')
    a, b = json.loads(before[META[5]]), json.loads(after[META[5]])
    require(b['reviews'][:1673] == a['reviews'] and {k: v for k, v in a.items() if k != 'reviews'} ==
            {k: v for k, v in b.items() if k != 'reviews'}, 'Prior review records or ledger scope changed')
    require(len(b['reviews']) == len({(r['pmcid'], r['tool_id']) for r in b['reviews']}) == 1679, 'Wrong review credit')
    markers = {REVIEWED, 'individually-reviewed-primary-source-roles; whole-paper-method-review-open'}
    marked = {(paper['pmcid'], use['tool_id']) for paper in new for use in paper['tools'] if use['review_status'] in markers}
    require(marked == {(r['pmcid'], r['tool_id']) for r in b['reviews']}, 'Ledger and positive reviewed observations differ')
    counts = Counter(r['implementation_status'] for r in json.loads(after[META[1]]))
    require(counts['native-subset-validated'] == 27 and counts['upstream-adapter-validated'] == 124 and
            counts['method-overlap'] == 22 and counts['missing'] == 258 and unchanged == 2716, 'Wrong family/observation counts')
    require(sum(len(paper['tools']) for paper in new) == 2722, 'Excluded cohort was credited as positive use')
    return dict(papers=250, labels=446, provider_definitions=485, observations=2722, reviewed_uses=1679,
                pending_uses=1043, unchanged_prior_reviews=1673, new_reviews=6,
                unchanged_unrelated_observations=2716, unchanged_unrelated_inventory_rows=445,
                scoped_families=151, native=27, upstream=124, including_existing_adapters=154,
                method_overlap=22, missing=258, new_upstream_family_credit=1, new_native_family_credit=0,
                added_positive_observations=0, removed_cohort_observations=1, excluded_method_baselines=1,
                whole_tool_qualified=False, whole_paper_reproduced=False, whole_goal_complete=False)
