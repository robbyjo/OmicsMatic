# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""LDAK-only transformation of the frozen catalog; preserve every other family."""
import json
import re
from collections import Counter
from .primary import EXPECTED, encoded

DATA = 'docs/literature/omics-2023-2026'
META = [DATA+'/'+name for name in ['papers.json', 'tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md']]+['tools/literature/reviewed-uses.json', 'site/literature-review.html', 'site/literature-warnings.html']
GUIDE = 'docs/literature/LDAK_WORKFLOWS.md'
PROGRAM = 'src/main/java/org/jlinalg/cli/LdakWorkflowCli.java'
NOTE = ('Original LDAK 5.2 SumHer with complete supplied tagging, explicit inflation/solver/allele/cutoff/liability choices, and optional thinning plus one-kinship REML alpha grids. '
        'Native Java FP64 independently recomputes every kinship entry and checks declared liability conversion; thinning audits check set handoff, and SumHer/REML are fitted by the captured original engine. '
        'Exact runtime/license/input/output capture, 48-class method identity, independent directory verification and genuine replay. '
        'Author binaries, reference snapshots, unreported grids, genotype coding and biological results are not inferred. '
        'Missing/forbidden prerequisites mean SKIP3 and dependent analysis skipped. FAIL2 admits no complete result, including original errors with exit zero; retain diagnostics and retry NEW output.')
CURRENT = ('250 papers, 446 families, 2694 observations, 1224 reviewed uses and 1470 unreviewed. '
           'One scoped LDAK upstream family brings the total to 128 new integrations (23 native/105 upstream), 259 missing; 131 including three existing adapters. '
           'Six LDAK roles were reviewed, including the newly recovered P238 comparator and the P245 background role. Full semantic review and implementation remain open.')
WARNING = ('**SKIP3:** skip dependent analysis when the exact permitted runtime/reference or Linux execution is missing or forbidden; supply the named prerequisite and retry NEW output. '
           '**FAIL2:** admit no complete result. Read `FAILURE.txt`, `WARNINGS.md`, `DIAGNOSTICS.tsv` and both original consoles. Preserve partials and resolve the named cause. '
           '**Observed tutorial substitutions:** original SumHer replaced 18 zero statistics with `1e-6` and floored four tagging values at one; choose `forbid` or explicit `allow-recorded`, and inspect the counts. '
           '**Verifier correction:** rebuild current source and create fresh bundles for expanded code identity; keep earlier bundles with their matching readers and never refresh their fingerprints. '
           '**Study settings:** current full BLD-LDAK controls use synthetic summaries and a public reference snapshot, not the authors\' studies. P238 settings remain under-specified; P245 is background/prior work, not current-study LDAK execution. '
           '**Other retained restrictions:** selected legacy SnpEff software/evidence is private academic noncommercial material with separate redistribution permissions; check its tool guide.')
NOTICE = ('<aside class="ldak-catalog-notice"><h2>LDAK: original heritability workflows and native kinship audits</h2><p>'+CURRENT+'</p>'
          '<p>Build current source; released v0.4.0 lacks these commands. '
          '<a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Commands, model choices and recovery steps</a>.</p>'
          '<p><strong>SKIP3:</strong> skip analysis needing unavailable/forbidden prerequisites. <strong>FAIL2:</strong> accept no complete result, including original errors with exit 0. Preserve diagnostics and retry new output.</p>'
          '<p>Original tutorial SumHer replaced 18 zero statistics and floored four tagging values. Review the recorded changes or explicitly forbid them. Rebuild for the corrected code-identity guard; preserve older readers and never refresh saved fingerprints.</p>'
          '<p>The full BLD-LDAK control uses synthetic summaries; author references and unreported settings remain unresolved. P245 describes background/prior work. Legacy SnpEff software/evidence retains private academic noncommercial and separate redistribution restrictions.</p></aside>')
STYLE = '<style>.ldak-catalog-notice{box-sizing:border-box;max-width:100%;margin:1rem 0;padding:1rem;border:2px solid #a56613;border-radius:.5rem;background:#fff8e8;color:#302718;overflow-wrap:anywhere}.ldak-catalog-notice h2{margin-top:0}.ldak-catalog-notice p:last-child{margin-bottom:0}</style>'


def registry():
    from snpeff.model import registry as previous
    result = previous()
    assert len(result) == 485
    item = next(row for row in result if row['id'] == 'ldak')
    assert item['current_coverage'] == 'missing'
    item.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAM, coverage_note=NOTE)
    return result


def transition(before, census):
    papers = json.loads(before[META[0]])
    inventory = json.loads(before[META[1]])
    summary = json.loads(before[META[2]])
    reviews = json.loads(before['tools/literature/reviewed-uses.json'])
    item = next(row for row in inventory if row['id'] == 'ldak')
    assert item['implementation_status'] == 'missing' and item['paper_count'] == 5
    assert set(item['papers']) == set(EXPECTED)-{'P238'}
    assert len(reviews['reviews']) == 1218 and not any(row['tool_id'] == 'ldak' for row in reviews['reviews'])
    assert len(census['roles']) == 6
    qualifications = {}
    for role in census['roles']:
        paper = next(row for row in papers if row['paper_id'] == role['paper_id'])
        uses = [row for row in paper['tools'] if row['tool_id'] == 'ldak']
        if role['paper_id'] == 'P238':
            assert not uses and role['new_observation'] is not None
            use = dict(role['new_observation'])
            assert use['evidence_paragraph_index'] == 22
            position = next((i for i, row in enumerate(paper['tools']) if row['tool_id'] > 'ldak'), len(paper['tools']))
            paper['tools'].insert(position, use)
        else:
            assert len(uses) == 1
            use = uses[0]
        assert use['evidence_paragraph_sha256'] == role['anchor_sha256'] and paper['xml_sha256'] == role['xml_sha256']
        scope = NOTE+' '+role['unresolved']
        use.update(usage_context=role['usage_context'], review_status='usage-summary-reviewed; paper-wide-completeness-review-open', evidence_selection='individually-reviewed-paragraph', use_summary=role['use_summary'], implementation_scope_note=scope, reviewed_reported_engine_version=role['reported_engine_version'], evidence_locator=role['fulltext_url']+' (section: '+role['anchor_section']+')')
        reviews['reviews'].append(dict(pmcid=role['pmcid'], doi=role['doi'], tool_id='ldak', evidence_paragraph_sha256=role['anchor_sha256'], use_summary=role['use_summary'], implementation_scope_note=scope, usage_context=role['usage_context']))
        background = role['paper_id'] == 'P245'
        qualifications[role['paper_id']] = dict(usage_context=role['usage_context'], reported_engine_version=role['reported_engine_version'], reported_engine_version_known=role['reported_engine_version_known'], selected_project_profile=None if background else '5.2', current_study_execution_attributed=not background, exact_author_settings_established=False, selected_runtime_is_author_binary=False, whole_paper_reproduced=False)
    item.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAM, coverage_note=NOTE, paper_count=6, papers=sorted(EXPECTED), implementation_status='upstream-adapter-validated', implementation_source=PROGRAM, implementation_scope=NOTE, validation_note='58 Java tests; 92 workflow/example/negative process controls; independent inspection of 23 complete bundles and 48 class fingerprints; full 66-category BLD-LDAK synthetic control, genuine original replay and copied-reader verification. Native kinship and declared liability conversion are independently checked; author studies are not reproduced.', selected_project_editions=['5.2'], source_build_required=True, released_binary_contains_workflow_adapter=False, whole_tool_qualified=False, whole_paper_reproduced=False, new_scoped_integration_credit=1, native_family_credit_added=0, upstream_family_credit_added=1, current_documentation=GUIDE, paper_role_qualification=qualifications)
    counts = dict(Counter(row['implementation_status'] for row in inventory))
    assert counts['upstream-adapter-validated'] == 105 and counts['native-subset-validated'] == 23 and counts['missing'] == 259
    summary.update(paper_tool_observations=2694, implementation_status_counts=counts)
    after = dict(before)
    for name, value in [(META[0], papers), (META[1], inventory), (META[2], summary), ('tools/literature/reviewed-uses.json', reviews)]:
        raw = encoded(value)
        if before[name].count(b'\n') == before[name].count(b'\r\n'):
            raw = raw.replace(b'\n', b'\r\n')
        after[name] = raw
    page = before['site/literature-review.html'].decode()
    match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
    assert match and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]), tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]]))
    payload = json.dumps(dict(papers=papers, tools=inventory, summary=summary), ensure_ascii=False).replace('<', '\\u003c')
    page = page[:match.start(2)]+payload+page[match.end(2):]
    from snpeff.model import NOTICE as previous
    for name, text in [('site/literature-review.html', page), ('site/literature-warnings.html', before['site/literature-warnings.html'].decode())]:
        assert text.count(previous) == 1 and text.count('</head>') == 1
        after[name] = text.replace(previous, NOTICE+'<details><summary>Previous SnpEff checkpoint (historical)</summary>'+previous+'</details>', 1).replace('</head>', STYLE+'</head>', 1).encode()
    for name in [META[3], META[4]]:
        title = '# 250-paper omics and GWAS toolbox catalog' if name == META[3] else '# Current literature warnings and required actions'
        after[name] = (title+'\n\n## LDAK: scope, warnings and required actions\n\n**Available from current source:** `ldak-tools run`, `verify` and genuine `replay`, with separate native Java FP64 kinship. Released v0.4.0 lacks these commands. Read [commands and scope](../LDAK_WORKFLOWS.md).\n\n'+WARNING+'\n\n**Current cohort:** '+CURRENT+'\n\n<details><summary>Checkpoint before LDAK admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after


def check(before, after, census):
    assert after == transition(before, census)
    a, b = json.loads(before[META[0]]), json.loads(after[META[0]])
    for old, new in zip(a, b):
        assert {k:v for k, v in old.items() if k != 'tools'} == {k:v for k, v in new.items() if k != 'tools'}
        assert [row for row in old['tools'] if row['tool_id'] != 'ldak'] == [row for row in new['tools'] if row['tool_id'] != 'ldak']
    assert [row for row in json.loads(before[META[1]]) if row['id'] != 'ldak'] == [row for row in json.loads(after[META[1]]) if row['id'] != 'ldak']
    assert [row for row in json.loads(before['tools/literature/reviewed-uses.json'])['reviews'] if row['tool_id'] != 'ldak'] == [row for row in json.loads(after['tools/literature/reviewed-uses.json'])['reviews'] if row['tool_id'] != 'ldak']
    assert len(json.loads(after['tools/literature/reviewed-uses.json'])['reviews']) == 1224
    assert sum(len(paper['tools']) for paper in b) == 2694
    old_summary, new_summary = json.loads(before[META[2]]), json.loads(after[META[2]])
    assert {k:v for k, v in old_summary.items() if k not in ['paper_tool_observations', 'implementation_status_counts']} == {k:v for k, v in new_summary.items() if k not in ['paper_tool_observations', 'implementation_status_counts']}
    return dict(papers=250, named_tools=446, observations=2694, reviewed_uses=1224, unreviewed_uses=1470, new_scoped_integrations=128, native_integrations=23, upstream_integrations=105, including_existing_adapters=131, missing=259, registry_definitions=485, reviewed_ldak_roles=6, observations_added=['P238'], background_or_prior_work_roles=['P245'], new_scoped_integration_credit=1, native_family_credit_added=0, upstream_family_credit_added=1, whole_goal_complete=False)
