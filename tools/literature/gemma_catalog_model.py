"""Deterministic, narrowly scoped GEMMA catalog transition; raw evidence stays intact."""
from collections import Counter
from pathlib import Path
import copy
import html
import json
import re
from gemma_primary import encoded, pin, require
from verify_gemma_workflows import require_qualification

ROOT = Path(__file__).resolve().parents[2]
DATA = 'docs/literature/omics-2023-2026'
META = [DATA + '/' + n for n in ('papers.json', 'tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md')]
META += ['tools/literature/reviewed-uses.json', 'site/literature-review.html', 'site/literature-warnings.html']
PROGRAM = 'src/main/java/org/jlinalg/cli/GemmaToolsCli.java'
NATIVE = 'src/main/java/org/jlinalg/genetics/GemmaKinship.java'
GUIDE = 'docs/literature/GEMMA_WORKFLOWS.md'
REVIEWED = 'usage-summary-reviewed; paper-wide-completeness-review-open'
COVERAGE = DATA + '/gemma-source-role-coverage.json'
PROOF = DATA + '/gemma-catalog-validation.json'
SCOPE = (
    'Java PLINK hard-call centered/standardized kinship and SNP quality filtering, plus original '
    'unmodified GEMMA 0.98.1/0.98.3/0.98.5 Linux x86_64 univariate LMM1 Wald, LMM2 likelihood-ratio, '
    'LMM3 score and LMM4 all-tests fitting through gemma-tools. Explicit phenotype, covariates, '
    'sample-order sidecars, external kinship, QC, genome declaration and capacities are required. '
    'Complete inputs and all sample/SNP/output populations remain captured; exact-producer portable '
    'verification works on Windows and Linux. This is one scoped upstream family with a native '
    'component receiving no additional family credit. No source-version inference, PanGEMMA fallback, '
    'LOCO, multivariate/BSLMM model, phenotype BLUP derivation, imputation, pruning, optimizer '
    'convergence certificate, complete package equivalence or full paper reproduction is claimed.'
)
NOTE = (
    'P102 reports 0.98.1, Wald trait GWAS and SV-eQTL, with first-three-PC adjustment in GWAS; '
    'its author IBS wording does not identify a -gk flag. P171 reports 0.98.3 and explicitly -gk 2; '
    'association modes are unreported. P176 literally reports 0.983 in both method paragraphs: '
    'the exact edition remains unresolved and is not silently mapped to 0.98.3. P248 reports '
    '0.98.5 and precomputed BLUP phenotypes with first-three-PC adjustment; this does not establish '
    'GEMMA BSLMM/BLUP training. Author input formats and unreported settings remain unqualified.'
)
ACTION = (
    'SKIP3: DEPENDENT ANALYSIS SKIPPED when the exact permitted platform, runtime, installation, '
    'download, access or monitoring prerequisite is unavailable. Supply the named exact prerequisite '
    'through separate authorized setup, or retain the skip; analysis never installs software. '
    'FAIL2: NO COMPLETE RESULT for invalid/changing input/runtime, process/resource, cancellation, '
    'nonfinite/degenerate or inconsistent output, or finalization failure. Read WARNINGS.md, '
    'FAILURE.json, every QC.jsonl disposition, original logs and process stderr. Preserve all '
    'attempts and correct the cause using NEW paths. Original exit0 can accompany nan p-values; '
    'constant/covariate-explained phenotypes are refused. Original 0.98.1/0.98.3 score-mode logl_H1 '
    'zeros are uncomputed placeholders, not fitted likelihoods; 0.98.5 omits that column. '
    'Review lambda boundaries and retained near-zero-eigenvalue warnings. Keep exact producers '
    'and private evidence; never refresh historical seals. Earlier 0.4.1 release downloads predate '
    'this source-built command. Cell Ranger remains excluded. Unfinished literature HTMLs remain '
    'withheld from Pages. Hashes establish integrity, not authenticity or biological accuracy.'
)
CURRENT = (
    '250 papers, 446 labels, 485 provider definitions and 2,724 observations; '
    '1,776 reviewed/948 pending uses. 163 scoped families (30 native + 133 upstream), '
    '166 including three existing adapters; 253 missing and 15 method-overlap. '
    'This admission adds one GEMMA upstream family and four source-role reviews. '
    'Its Java kinship component receives no duplicate native-family credit. The full goal remains unfinished.'
)
NOTICE = (
    '<aside id="gemma-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
    'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
    '<h2>GEMMA: Java kinship and original univariate mixed models</h2><p>' + html.escape(CURRENT) + '</p>'
    '<p><strong>Warnings and required actions:</strong> ' + html.escape(ACTION) + '</p><p>' + html.escape(NOTE)
    + '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/' + GUIDE
    + '">Commands, exact versions, paper roles and remaining scope</a></p></aside>'
)

def registry(root=ROOT):
    raw = (Path(root) / 'docs/literature/crossmap-catalog-admission/REGISTRY.json').read_bytes()
    require(pin(raw) == dict(bytes=241292, sha256='f1d88f01ca312913869d3746da3077145018349d9316d279c4d66dab9bea0995'),
            'Changed exact preceding provider registry; preserve its original bytes')
    rows = json.loads(raw)
    require(len(rows) == 485, 'Changed predecessor provider population')
    target = next(r for r in rows if r['id'] == 'gemma')
    require(target['current_coverage'] == 'method-overlap', 'GEMMA predecessor registry was already promoted')
    old_method = target['existing_source']
    target.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAM,
                  related_native_method_source=old_method, coverage_note=SCOPE + ' ' + NOTE)
    return rows

def applied(role):
    result = copy.deepcopy(role)
    result.update(source_role_review_applied=True, catalog_admitted=True,
                  reported_version=role['literal_reported_version'],
                  selected_qualified_runtime_edition=role['reported_edition'],
                  paper_edition_qualified=False,
                  historical_analysis_status='Prepared software controls qualified; complete author commands, formats, environment and study analyses were not reproduced.')
    return result

def validate(before, roles, qualification, root=ROOT):
    require(roles == json.loads((Path(root) / 'docs/literature/gemma-source-audit/ROLES.json').read_bytes()),
            'Changed frozen source-role interpretations')
    require_qualification(qualification)
    require(set(before) == set(META), 'Changed preceding metadata population')
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in (0, 1, 2, 5)]
    require(len(papers) == 250 and len(inventory) == 446 and sum(len(p['tools']) for p in papers) == 2724,
            'Changed frozen paper/label/observation population')
    require(len(ledger['reviews']) == 1772 and not any(r['tool_id'] == 'gemma' for r in ledger['reviews']),
            'Changed prior review population or duplicate GEMMA reviews')
    require(summary['integration_family_counts'] == dict(scoped=162, native=30, upstream=132,
            including_existing_adapters=165, validated_alias_labels_excluded_from_extra_credit=3), 'Changed preceding family counts')
    target = next(r for r in inventory if r['id'] == 'gemma')
    require(target['implementation_status'] == 'method-overlap'
            and target['papers'] == ['P102', 'P171', 'P176', 'P248'], 'Changed GEMMA predecessor coverage')
    require([r['paper_id'] for r in roles['roles']] == target['papers'], 'Changed source-role population')
    for role in roles['roles']:
        use = next(u for p in papers if p['paper_id'] == role['paper_id'] for u in p['tools'] if u['tool_id'] == 'gemma')
        require({k: use.get(k) for k in role['raw_source_fields']} == role['raw_source_fields'], 'Raw source/version fields changed')
        require(role['paper_edition_reproduced'] is False and role['whole_paper_reproduced'] is False, 'Expanded paper reproduction claim')
    unresolved = next(r for r in roles['roles'] if r['paper_id'] == 'P176')
    require(unresolved['literal_reported_version'] == '0.983' and unresolved['reported_edition'] is None,
            'P176 literal version was silently repaired')

def transition(before, roles, qualification, root=ROOT):
    validate(before, roles, qualification, root)
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in (0, 1, 2, 5)]
    for role in roles['roles']:
        paper = next(p for p in papers if p['paper_id'] == role['paper_id'])
        use = next(u for u in paper['tools'] if u['tool_id'] == 'gemma')
        evidence = copy.deepcopy(use.get('supporting_evidence', []))
        for anchor in role['primary_anchors']:
            if anchor['paragraph_index'] != use['evidence_paragraph_index'] and not any(
                    e.get('paragraph_index') == anchor['paragraph_index'] and e.get('paragraph_sha256') == anchor['paragraph_sha256'] for e in evidence):
                evidence.append(copy.deepcopy(anchor))
        scope = role['remaining_scope'] + ' Available bounded implementation: ' + SCOPE
        use.update(use_summary=role['use_summary'], implementation_scope_note=scope, review_status=REVIEWED,
                   evidence_selection='individually-reviewed-source-role', supporting_evidence=evidence,
                   source_role_qualification=applied(role))
        ledger['reviews'].append(dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id='gemma',
            evidence_paragraph_sha256=use['evidence_paragraph_sha256'], use_summary=role['use_summary'],
            implementation_scope_note=scope, usage_context=use['usage_context'],
            version_reported_near_name=use['version_reported_near_name'], supporting_evidence=copy.deepcopy(evidence),
            reported_settings=copy.deepcopy(role['reported_settings']), source_role_qualification=applied(role)))
    target = next(r for r in inventory if r['id'] == 'gemma')
    old_method = target['implementation_source']
    target.update(current_coverage='upstream-adapter-validated', implementation_status='upstream-adapter-validated',
        existing_source=PROGRAM, implementation_source=PROGRAM, related_native_method_source=old_method,
        coverage_note=SCOPE + ' ' + NOTE, implementation_scope=SCOPE + ' ' + NOTE,
        current_documentation=GUIDE, current_qualification='../gemma-workflow-checkpoint/STATUS.json',
        upstream_runtime_adapter_required=True, native_component_available=True, native_component_family_credit=0,
        native_component_source=NATIVE, public_CLI_available=True, global_CLI_dispatch_available=True,
        source_build_required=True, released_binary_contains_workflow=False, whole_tool_qualified=False,
        whole_paper_reproduced=False, whole_goal_complete=False, new_native_family_credit=0,
        new_upstream_family_credit=1, source_roles_reviewed=4, new_source_reviews=4, refined_existing_reviews=0,
        source_role_coverage=COVERAGE.rsplit('/', 1)[-1],
        paper_role_qualification={r['paper_id']: applied(r) for r in roles['roles']}, current_paper_role_limits=NOTE,
        validation_note='60 complete captures verified on Windows/Linux; 32 Java tests per platform; 588 association rows independently checked; 22 workflow refusals and 17 saved-result refusals per platform. P176 remains version-unresolved; no original study rerun or optimizer convergence certificate.')
    summary['implementation_status_counts'] = dict(Counter(r['implementation_status'] for r in inventory))
    summary['integration_family_counts'] = dict(scoped=163, native=30, upstream=133, including_existing_adapters=166,
        validated_alias_labels_excluded_from_extra_credit=3)
    after = dict(before)
    for name, value in ((META[0], papers), (META[1], inventory), (META[2], summary), (META[5], ledger)):
        raw = encoded(value)
        after[name] = raw.replace(b'\n', b'\r\n') if before[name].count(b'\n') == before[name].count(b'\r\n') else raw
    for name in META[6:]:
        page = before[name].decode()
        require('id="gemma-catalog-notice"' not in page and 'id="crossmap-catalog-notice"' in page, 'Changed preceding report notice')
        if name == META[6]:
            match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
            require(match and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]), tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]])), 'Changed preceding embedded catalog')
            payload = json.dumps(dict(papers=papers, tools=inventory, summary=summary), ensure_ascii=False).replace('<', '\\u003c')
            page = page[:match.start(2)] + payload + page[match.end(2):]
            require(page.count('${crossmapVersion(t)}') == page.count('function renderContent(){') == 1, 'Changed source-role renderer')
            helper = "function gemmaVersion(t){if(t.tool_id!=='gemma'||!t.source_role_qualification)return crossmapVersion(t);const q=t.source_role_qualification;return '<br><span class=\"gemma-version\" style=\"overflow-wrap:anywhere\">Role: '+esc(q.role_kind)+'; Reported version: '+esc(q.literal_reported_version)+'; '+(q.reported_edition?'Prepared runtime available: '+esc(q.reported_edition):'Exact release unresolved; no substitution')+'; '+esc(q.remaining_scope)+'</span>';}\n"
            page = page.replace('${crossmapVersion(t)}', '${gemmaVersion(t)}', 1).replace('function renderContent(){', helper + 'function renderContent(){', 1)
        body = re.search(r'<body[^>]*>', page, re.I)
        require(body is not None, 'Missing report body')
        marker = page.find('<site-header ', body.end())
        require(marker >= body.end() and page.count('<site-header ') == 1, 'Changed historical notice boundary')
        history = '<details id="gemma-prior-catalog"><summary>Before GEMMA admission (historical)</summary>' + page[body.end():marker] + '</details>'
        after[name] = (page[:body.end()] + NOTICE + history + page[marker:]).encode()
    prefix = ('# Current GEMMA catalog checkpoint\n\n' + CURRENT + '\n\n' + SCOPE + '\n\n' + NOTE
        + '\n\n**Warnings and required actions:** ' + ACTION + '\n\nSee [workflow and source-role guide](../GEMMA_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n').encode()
    for name in META[3:5]:
        after[name] = prefix + before[name]
    return after

def check(before, after, roles, qualification, root=ROOT):
    require(after == transition(before, roles, qualification, root), 'Changed deterministic source-bound transition')
    old, new = json.loads(before[META[0]]), json.loads(after[META[0]])
    for a, b in zip(old, new, strict=True):
        require({k: v for k, v in a.items() if k != 'tools'} == {k: v for k, v in b.items() if k != 'tools'}, 'Paper identity changed')
        require([u for u in a['tools'] if u['tool_id'] != 'gemma'] == [u for u in b['tools'] if u['tool_id'] != 'gemma'], 'Unrelated observation changed')
        x = next((u for u in a['tools'] if u['tool_id'] == 'gemma'), None)
        if x:
            y = next(u for u in b['tools'] if u['tool_id'] == 'gemma')
            for key in next(r for r in roles['roles'] if r['paper_id'] == a['paper_id'])['raw_source_fields']:
                require(x.get(key) == y.get(key), 'Raw source/version field changed: ' + key)
    a, b = json.loads(before[META[1]]), json.loads(after[META[1]])
    require([r for r in a if r['id'] != 'gemma'] == [r for r in b if r['id'] != 'gemma'], 'Unrelated inventory row changed')
    oldledger, newledger = json.loads(before[META[5]]), json.loads(after[META[5]])
    require([r for r in newledger['reviews'] if r['tool_id'] != 'gemma'] == oldledger['reviews'], 'Existing review changed')
    require(len(newledger['reviews']) == len({(r['pmcid'], r['tool_id']) for r in newledger['reviews']}) == 1776, 'Wrong unique review count')
    markers = {REVIEWED, 'individually-reviewed-primary-source-roles; whole-paper-method-review-open'}
    require({(p['pmcid'], u['tool_id']) for p in new for u in p['tools'] if u['review_status'] in markers}
            == {(r['pmcid'], r['tool_id']) for r in newledger['reviews']}, 'Review-ledger/observation mismatch')
    counts = Counter(r['implementation_status'] for r in b)
    require((counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['method-overlap'], counts['missing']) == (30, 133, 15, 253), 'Wrong family counts')
    return dict(papers=250, labels=446, provider_definitions=485, observations=2724, reviewed_uses=1776,
        pending_uses=948, source_roles_reviewed=4, new_reviews=4, refined_existing_reviews=0,
        unchanged_prior_reviews=1772, unchanged_unrelated_observations=2720, unchanged_unrelated_inventory_rows=445,
        scoped_families=163, native=30, upstream=133, including_existing_adapters=166, method_overlap=15, missing=253,
        new_native_family_credit=0, new_upstream_family_credit=1, added_positive_observations=0,
        removed_positive_observations=0, P176_literal_0_983_resolved=False,
        author_unreported_editions_or_modes_inferred=False, original_paper_analyses_reexecuted=False,
        optimizer_convergence_certified=False, whole_paper_reproduced=False, whole_goal_complete=False)
