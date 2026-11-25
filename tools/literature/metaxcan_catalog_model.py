"""Scoped MetaXcan catalog transition; preserve all unrelated catalog bytes/data."""
import copy
import html
import json
import re
from collections import Counter
from pathlib import Path
from gemma_primary import encoded, pin, require

ROOT = Path(__file__).resolve().parents[2]
DATA = 'docs/literature/omics-2023-2026'
META = [DATA + '/' + n for n in ('papers.json', 'tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md')]
META += ['tools/literature/reviewed-uses.json', 'site/literature-review.html', 'site/literature-warnings.html']
ADMISSION = 'docs/literature/metaxcan-catalog-admission'
PROGRAM = 'src/main/java/org/jlinalg/cli/MetaxcanToolsCli.java'
GUIDE = 'docs/literature/METAXCAN_WORKFLOWS.md'
REVIEWED = 'usage-summary-reviewed; paper-wide-completeness-review-open'
TARGETS = frozenset(('metaxcan', 'predixcan', 'multixcan'))
SCOPE = ('Native Java S-PrediXcan and S-MulTiXcan summary association from complete PredictDB SQLite, GWAS, '
         'covariance and explicit joint model-product inputs. Source-compatible float32 GWAS Z, exact/swap alleles, '
         'used-SNP variance, explicit key/first-row b38 policy, optional additional statistics and phi calibration, '
         'all four explicit joint cutoffs, complete exclusion ledgers and exact-producer numerical replay on '
         'Windows/Linux. One scoped native family; PrediXcan and MultiXcan are aliases without extra family credit. '
         'No model training, individual prediction/association adapter, unreported author setting inference, '
         'complete package equivalence or historical study reproduction is claimed.')
TRAINING = ('PredictDB transcriptome model training remains a separate missing integration. A SQLite reader and '
            'generic elastic-net kernel do not implement nested CV, training QC, export or the author workflow. '
            'Obtain exact licensed training sources and corrected author inputs before training; selected tutorial '
            'license template establishes no grant. Keep P074 literal v7 and unreported editions unresolved.')
ACTION = ('SKIP3: dependent analysis skipped when the named permitted prerequisite is unavailable; supply it '
          'where allowed or retain the skip. FAIL2: no complete result for invalid/changed input, numerical, '
          'capacity or finalization failure. Read WARNINGS.md, OUTPUT-AUDIT.json and every disposition; preserve '
          'attempts and use NEW paths after correcting the cause. The deposited MESA wrapper selects absent '
          'SNP_ID and its R trainer fails parsing; neither was patched. Source-build required; old release '
          'downloads predate the command. Exact producer JARs and raw evidence stay private. Cell Ranger remains '
          'excluded; unfinished literature HTMLs remain withheld from Pages. Full250-paper goal remains open.')
CURRENT = ('250 papers, 447 labels, 486 provider definitions and 2,726 observations; 1,786 reviewed/940 pending '
           'uses. 164 scoped families (31 native + 133 upstream), 167 including three existing adapters; '
           '253 missing and13 method-overlap, with five validated alias labels excluded from extra credit. '
           'This checkpoint adds one native summary family, two aliases and two missing PredictDB training observations.')


def qualification(q):
    require(q['status'] == 'PASS_NATIVE_METAXCAN_KEYS_FULL_CAPTURE_AND_PRIMARY_ROLES'
            and q['Windows_JUnit'] == q['Linux_JUnit'] == 52
            and q['original_key_command_controls'] == 15 and q['original_author_model_positive_controls'] == 3
            and q['original_absent_author_key_failures'] == 3 and q['native_author_model_comparisons_each_OS'] == 24
            and q['final_control_calls_each_OS'] == 14 and q['primary_XML_checked'] == 250
            and q['training_implemented'] is False and q['whole_paper_reproduced'] is False
            and q['whole_goal_complete'] is False, 'Incomplete/expanded MetaXcan qualification')


def provider():
    return dict(id='predictdb', name='PredictDB', pattern=r'(?<![A-Za-z0-9_])PredictDB(?![A-Za-z0-9_])',
                role='Transcriptome prediction model training', current_coverage='missing', existing_source=None,
                coverage_note=TRAINING)


def registry(root=ROOT):
    old = json.loads((Path(root) / ADMISSION / 'PREVIOUS-REGISTRY.json').read_bytes())
    require(len(old) == 485 and not any(r['id'] == 'predictdb' for r in old), 'Changed predecessor providers')
    for row in old:
        if row['id'] in TARGETS:
            row.update(current_coverage='native-subset-validated' if row['id'] == 'metaxcan' else 'validated-family-alias',
                       existing_source=PROGRAM, coverage_note=SCOPE, integration_family='metaxcan')
    return old + [provider()]


def applied(role, training=False):
    value = copy.deepcopy(role)
    value.update(source_role_review_applied=True, catalog_admitted=True, integration_family=None if training else 'metaxcan',
                 implementation_available=not training, whole_tool_qualified=False,
                 historical_analysis_status='Not rerun; prepared summary controls only. Training remains missing.')
    return value


def transition(before, roles, q):
    qualification(q)
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in (0, 1, 2, 5)]
    require(len(papers) == 250 and len(inventory) == 446 and sum(len(p['tools']) for p in papers) == 2724
            and len(ledger['reviews']) == 1776, 'Changed predecessor catalog population')
    require(not any(r['tool_id'] in TARGETS | {'predictdb'} for r in ledger['reviews']), 'Source reviews already applied')
    require(len(roles['roles']) == 8 and len(roles['additional_training_roles']) == 2, 'Changed role population')
    for role in roles['roles'] + roles['additional_training_roles']:
        training = role['tool_id'] == 'predictdb'
        paper = next(p for p in papers if p['paper_id'] == role['paper_id'])
        if training:
            require(not any(u['tool_id'] == 'predictdb' for u in paper['tools']), 'Duplicate training observation')
            a = role['primary_anchors'][0]
            use = dict(tool_id='predictdb', tool='PredictDB', analysis_role=provider()['role'], matched_text='PredictDB',
                       extraction_score=15, usage_context='analysis-method-description', evidence_section=a['section'],
                       evidence_paragraph_index=a['paragraph_index'], evidence_paragraph_sha256=a['paragraph_sha256'],
                       evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/' + paper['pmcid'] + '/ (section: ' + a['section'] + ')',
                       version_reported_near_name=role['literal_reported_version'], discovery='full250-manual-source-role-completeness-review')
            paper['tools'].append(use)
        else:
            use = next(u for u in paper['tools'] if u['tool_id'] == role['tool_id'])
            require({k: use.get(k) for k in role['raw_source_fields']} == role['raw_source_fields'], 'Raw source/version changed')
        evidence = copy.deepcopy(use.get('supporting_evidence', []))
        for a in role['primary_anchors']:
            if a['paragraph_index'] != use['evidence_paragraph_index'] and not any(e.get('paragraph_index') == a['paragraph_index'] for e in evidence):
                evidence.append(copy.deepcopy(a))
        scope = role['remaining_scope'] + ' ' + (TRAINING if training else SCOPE)
        use.update(use_summary=role['use_summary'], review_status=REVIEWED, implementation_scope_note=scope,
                   evidence_selection='individually-reviewed-source-role', supporting_evidence=evidence,
                   source_role_qualification=applied(role, training))
        ledger['reviews'].append(dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id=role['tool_id'],
            evidence_paragraph_sha256=use['evidence_paragraph_sha256'], use_summary=role['use_summary'],
            implementation_scope_note=scope, usage_context=use['usage_context'],
            version_reported_near_name=use['version_reported_near_name'], supporting_evidence=copy.deepcopy(evidence),
            reported_settings=copy.deepcopy(role.get('reported_settings', {})), source_role_qualification=applied(role, training)))
    for row in inventory:
        if row['id'] in TARGETS:
            row.update(current_coverage='native-subset-validated' if row['id'] == 'metaxcan' else 'validated-family-alias',
                implementation_status='native-subset-validated' if row['id'] == 'metaxcan' else 'validated-family-alias',
                existing_source=PROGRAM, implementation_source=PROGRAM, coverage_note=SCOPE, implementation_scope=SCOPE,
                integration_family='metaxcan', family_credit=1 if row['id'] == 'metaxcan' else 0,
                current_documentation=GUIDE, current_paper_roles='docs/literature/METAXCAN_PAPER_ROLES.md',
                current_qualification='docs/literature/metaxcan-key-validation/QUALIFICATION.json',
                paper_role_qualification={r['paper_id']: applied(r) for r in roles['roles'] if r['tool_id'] == row['id']},
                source_build_required=True, released_binary_contains_workflow=False, whole_tool_qualified=False,
                whole_paper_reproduced=False, training_implemented=False, whole_goal_complete=False,
                validation_note='52 focused Java tests on each OS;15 original key controls; three public-author-model synthetic-GWAS controls plus three absent-key failures; complete native captures and foreign-host replay, with legacy bytes and exact old producers preserved.')
    inventory.append(dict(**provider(), paper_count=2, papers=['P019', 'P074'], implementation_status='missing',
                          implementation_source=None, implementation_scope=TRAINING,
                          validation_note='Source-role review only. Training runtime/analysis not qualified.',
                          paper_role_qualification={r['paper_id']: applied(r, True) for r in roles['additional_training_roles']}))
    counts = Counter(r['implementation_status'] for r in inventory)
    summary.update(paper_tool_observations=sum(len(p['tools']) for p in papers), distinct_named_tools=len(inventory),
                   implementation_status_counts=dict(counts), integration_family_counts=dict(
                       scoped=counts['native-subset-validated'] + counts['upstream-adapter-validated'],
                       native=counts['native-subset-validated'], upstream=counts['upstream-adapter-validated'],
                       including_existing_adapters=counts['native-subset-validated'] + counts['upstream-adapter-validated'] + counts['existing-adapter'],
                       validated_alias_labels_excluded_from_extra_credit=counts['validated-family-alias']))
    after = dict(before)
    for name, value in ((META[0], papers), (META[1], inventory), (META[2], summary), (META[5], ledger)):
        raw = encoded(value)
        after[name] = raw.replace(b'\n', b'\r\n') if before[name].count(b'\n') == before[name].count(b'\r\n') else raw
    notice = ('<aside id="metaxcan-catalog-notice" style="margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
              '<h2>MetaXcan summary association; PredictDB training pending</h2><p>' + html.escape(CURRENT) + '</p><p>'
              + html.escape(TRAINING) + '</p><p><strong>Warnings and required actions:</strong> ' + html.escape(ACTION)
              + '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/' + GUIDE + '">Commands and paper roles</a></p></aside>')
    for name in META[6:]:
        page = before[name].decode()
        require('id="metaxcan-catalog-notice"' not in page and 'id="gemma-catalog-notice"' in page, 'Changed previous report notice')
        if name == META[6]:
            m = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
            require(m and json.loads(m[2]) == dict(papers=json.loads(before[META[0]]), tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]])), 'Changed embedded baseline')
            payload = json.dumps(dict(papers=papers, tools=inventory, summary=summary), ensure_ascii=False).replace('<', '\\u003c')
            page = page[:m.start(2)] + payload + page[m.end(2):]
            require(page.count('${gemmaVersion(t)}') == page.count('function renderContent(){') == 1, 'Changed role renderer')
            helper = "function metaxcanVersion(t){if(!['metaxcan','predixcan','multixcan','predictdb'].includes(t.tool_id)||!t.source_role_qualification)return gemmaVersion(t);const q=t.source_role_qualification;return '<br><span class=\"metaxcan-version\" style=\"overflow-wrap:anywhere\">Role: '+esc(q.role_kind)+'; Reported version: '+esc(q.literal_reported_version===null?'unreported':q.literal_reported_version)+'; '+esc(q.remaining_scope)+'</span>';}\n"
            page = page.replace('${gemmaVersion(t)}', '${metaxcanVersion(t)}', 1).replace('function renderContent(){', helper + 'function renderContent(){', 1)
        body = re.search(r'<body[^>]*>', page, re.I)
        marker = page.find('<site-header ', body.end())
        require(marker >= body.end(), 'Changed report boundary')
        history = '<details id="metaxcan-prior-catalog"><summary>Before MetaXcan admission (historical)</summary>' + page[body.end():marker] + '</details>'
        after[name] = (page[:body.end()] + notice + history + page[marker:]).encode()
    prefix = ('# Current MetaXcan catalog checkpoint\n\n' + CURRENT + '\n\n' + SCOPE + '\n\n' + TRAINING
              + '\n\n**Warnings and required actions:** ' + ACTION + '\n\nSee [workflow guide](../METAXCAN_WORKFLOWS.md) and [paper roles](../METAXCAN_PAPER_ROLES.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in META[3:5]:
        after[name] = prefix + before[name]
    return after


def check(before, after, roles, q):
    require(after == transition(before, roles, q), 'Different deterministic catalog transition')
    old, new = json.loads(before[META[0]]), json.loads(after[META[0]])
    for a, b in zip(old, new, strict=True):
        require({k: v for k, v in a.items() if k != 'tools'} == {k: v for k, v in b.items() if k != 'tools'}, 'Paper identity changed')
        require([u for u in a['tools'] if u['tool_id'] not in TARGETS] == [u for u in b['tools'] if u['tool_id'] not in TARGETS | {'predictdb'}], 'Unrelated observation changed')
    a, b = json.loads(before[META[1]]), json.loads(after[META[1]])
    require([r for r in a if r['id'] not in TARGETS] == [r for r in b if r['id'] not in TARGETS | {'predictdb'}], 'Unrelated inventory changed')
    ledger = json.loads(after[META[5]])['reviews'];prior = json.loads(before[META[5]])['reviews']
    require(ledger[:len(prior)] == prior and len(ledger) == len({(r['pmcid'], r['tool_id']) for r in ledger}) == 1786, 'Changed/duplicate reviews')
    reviewed = {(p['pmcid'], u['tool_id']) for p in new for u in p['tools'] if u['review_status'] in
                (REVIEWED, 'individually-reviewed-primary-source-roles; whole-paper-method-review-open')}
    require(reviewed == {(r['pmcid'], r['tool_id']) for r in ledger}, 'Review-ledger mismatch')
    counts = Counter(r['implementation_status'] for r in b)
    require((counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['missing'],
             counts['method-overlap'], counts['validated-family-alias']) == (31, 133, 253, 13, 5), 'Wrong family counts')
    return dict(papers=len(new), labels=len(b), provider_definitions=486, observations=sum(len(p['tools']) for p in new),
        reviewed_uses=len(ledger), pending_uses=sum(len(p['tools']) for p in new)-len(ledger), scoped_families=164,
        native=31, upstream=133, including_existing_adapters=167, missing=253, method_overlap=13, validated_alias_labels=5,
        new_native_family_credit=1, new_upstream_family_credit=0, added_positive_observations=2,
        new_source_role_reviews=10, unchanged_prior_reviews=1776, unchanged_unrelated_observations=2716,
        unchanged_unrelated_inventory_rows=443, training_implemented=False,
        whole_tool_qualified=False, original_paper_analyses_reexecuted=False, whole_goal_complete=False)
