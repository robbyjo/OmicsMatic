"""Apply/check the source-reviewed RFMix transition after actual private qualification.

All predecessor metadata and checksum bytes remain retained. No paper text,
private runtime or raw process trace is published by this transition.
"""
import argparse
from collections import Counter
import copy
import hashlib
import html
import json
import os
from pathlib import Path
import re
import sys
import zipfile

import supplement_pdf_catalog as prior
from ascat_catalog import checksums, verify_sums
from catalog_fixed_cohort import verify_fixed_cohort
from rfmix_source_audit import audit as source_audit
from verify_rfmix_workflows import source_record, private_evidence, pin, read, require

ROOT = Path(__file__).resolve().parents[2]
DATA = prior.DATA
META = prior.META
FOLDER = 'docs/literature/rfmix-catalog-admission'
Q = 'docs/literature/rfmix-validation/QUALIFICATION.json'
ROLES = 'docs/literature/rfmix-source-audit/ROLES.json'
COVERAGE = DATA + '/rfmix-source-role-coverage.json'
PROOF = DATA + '/rfmix-catalog-validation.json'
SOURCE = 'src/main/java/org/jlinalg/cli/RfmixToolsCli.java'
REVIEWED = 'individually-reviewed-primary-source-roles; whole-paper-method-review-open'
NOTICE = ('250 papers, 453 named labels, 492 providers and 2,733 observations; '
          '1,825 reviewed / 908 pending uses. 172 scoped families: 34 native + 138 upstream. '
          'RFMix adds one scoped original-v2 upstream family; 251 labels remain missing. The full goal remains active.')
SCOPE = ('Original RFMix v2.03-r0 (09599c19) phased local ancestry, source-pinned private runtime binding, '
         'complete immutable VCF/gzip/BCF+CSI inputs, explicit source model units/options, independent complete '
         'sample/locus/filter/map census, guarded original inference, complete MSP/FB/Q checks and fresh saved-input '
         'binary/numerical replay. SIS bytes remain retained but SIS-dependent analysis is skipped because the '
         'original writer does not certify final-iteration probabilities. No extra native-family credit. '
         'Legacy v1.5.4, exact author revisions/reference panels, masking/tract dating and whole-paper reproduction remain separate.')
ACTION = ('SIS-dependent analysis SKIPPED: inspect original logs and output audits; require exact-producer fresh replay '
          'before using MSP/FB/Q. P041 reports v1.5.4, whose original runtime remains unavailable/skipped; v2 is not a '
          'substitute. Supply a permitted provenance-backed v1.5.4 archive or retain that skip. P142 version remains '
          'unreported. P229 reported 0.2 cM windows differ from inspected current 50/5 SNP defaults: declare actual '
          'settings and never infer author equivalence. Missing/forbidden prerequisites produce SKIP3; install only '
          'where permitted or supply the named input/runtime/helper/index. Invalid/changed data, runtime, producer, '
          'model/QC, cancellation or resource exhaustion produce FAIL2 with no complete result; preserve attempts, '
          'correct the cause and retry NEW paths. Original upstream academic-use terms apply. Cell Ranger remains '
          'excluded; MaxQuant remains pending with runtime analyses skipped. ConstructASCATFiles/PREASCAT code-use '
          'permission remains pending. Literature HTMLs remain withheld from Pages.')


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n').encode()


def identity(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def predecessor(root, before):
    folder = root / prior.FOLDER
    baseline = read(folder / 'BASELINE.json')
    require(pin(folder / 'metadata-before.zip') == baseline['archive'] and pin(folder / 'EVIDENCE.json') == baseline['evidence']
            and pin(root / prior.ADDENDUM) == baseline['addendum'] and pin(root / prior.PRIOR_REGISTRY) == baseline['prior_registry'], 'Predecessor evidence changed')
    with zipfile.ZipFile(folder / 'metadata-before.zip') as stream:
        require(len(stream.namelist()) == len(META) and set(stream.namelist()) == set(META), 'Predecessor archive population differs')
        older = {name: stream.read(name) for name in META}
    require({name: identity(raw) for name, raw in older.items()} == baseline['metadata'], 'Predecessor original metadata changed')
    expected, registry = prior.transition(older, read(root / prior.PRIOR_REGISTRY), read(root / prior.ADDENDUM), read(folder / 'EVIDENCE.json'))
    require(expected == before and read(folder / 'REGISTRY.json') == registry, 'RFMix predecessor is not the exact retained PDF transition')
    return registry


def transition(before, registry, roles):
    require(set(before) == set(META), 'Predecessor metadata population differs')
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in (0, 1, 2, 7)]
    require((len(papers), len(inventory), sum(len(p['tools']) for p in papers), len(ledger['reviews']), len(registry))
            == (250, 453, 2733, 1821, 492), 'Predecessor counts differ')
    require({r['paper_id'] for r in roles} == {'P041', 'P142', 'P184', 'P229'} and not any(r['tool_id'] == 'rfmix' for r in ledger['reviews']), 'RFMix roles already reviewed or missing')
    coverage = []
    for role in roles:
        paper = next(p for p in papers if p['paper_id'] == role['paper_id'])
        uses = [u for u in paper['tools'] if u['tool_id'] == 'rfmix']
        require(len(uses) == 1 and uses[0] == role['raw_catalog_observation_preserved'], 'Raw RFMix source observation changed')
        use = uses[0]
        legacy = role['paper_id'] == 'P041'
        qualification = dict(paper_id=paper['paper_id'], reported_version=role['literal_reported_version'],
                             selected_runtime_version='v2.03-r0', selected_runtime_commit='09599c19c69f81571c7a7aab4cd2d847a9e0633a',
                             source_role_review='complete-main-text-contexts; supplementary-details-open',
                             runtime_scope='SKIP3_LEGACY_1_5_4_UNAVAILABLE' if legacy else 'SCOPED_ORIGINAL_V2_WORKFLOW_AVAILABLE_AUTHOR_EQUIVALENCE_UNESTABLISHED',
                             reported_version_implemented=False if legacy else None,
                             exact_author_recipe_qualified=False, whole_paper_reproduced=False,
                             remaining_scope=role['remaining_scope'], sis_dependent_analysis='SKIPPED_ORIGINAL_WRITER_FINAL_ITERATION_UNQUALIFIED')
        use.update(review_status=REVIEWED, use_summary=role['use_summary'], implementation_scope_note=SCOPE + ' ' + ACTION,
                   evidence_selection='individually-reviewed-all-primary-RFMix-contexts', supporting_evidence=copy.deepcopy(role['primary_anchors']),
                   source_role_qualification=qualification)
        ledger['reviews'].append(dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id='rfmix',
                                     evidence_paragraph_sha256=use['evidence_paragraph_sha256'], use_summary=use['use_summary'],
                                     implementation_scope_note=use['implementation_scope_note'], supporting_evidence=copy.deepcopy(role['primary_anchors']),
                                     source_role_qualification=copy.deepcopy(qualification)))
        coverage.append(qualification)
    item = next(x for x in inventory if x['id'] == 'rfmix')
    item.update(current_coverage='upstream-adapter-validated', existing_source=SOURCE, coverage_note=SCOPE,
                implementation_status='upstream-adapter-validated', implementation_source=SOURCE, implementation_scope=SCOPE,
                validation_note='Actual original five-model, gzip, indexed-BCF and source-selection captures and fresh replays; 36 native tests per OS; explicit refusals and original SIS limitation. See docs/literature/rfmix-validation/QUALIFICATION.json.',
                paper_role_qualification={x['paper_id']: x for x in coverage})
    registry = copy.deepcopy(registry)
    next(x for x in registry if x['id'] == 'rfmix').update(current_coverage='upstream-adapter-validated', existing_source=SOURCE, coverage_note=SCOPE)
    summary.update(implementation_status_counts=dict(Counter(x['implementation_status'] for x in inventory)),
                   reviewed_tool_uses=1825, pending_tool_use_reviews=908, current_effective_registry=FOLDER + '/REGISTRY.json')
    summary['integration_family_counts'].update(scoped=172, native=34, upstream=138, including_existing_adapters=175)
    after = dict(before)
    for name, value in ((META[0], papers), (META[1], inventory), (META[2], summary), (META[7], ledger)):
        after[name] = encoded(value)
    note = ('# Current RFMix workflow checkpoint\n\n' + NOTICE + '\n\n**Warnings and required actions:** ' + ACTION
            + '\n\nSee [RFMix workflow guide](../../rfmix-tools.md) and [qualification](../rfmix-validation/QUALIFICATION.json). Earlier checkpoints below are historical.\n\n').encode()
    table = ('| Paper | Reported version | Reviewed use | Runtime scope |\n| --- | --- | --- | --- |\n' + '\n'.join(
        '| ' + r['paper_id'] + ' | ' + (r['literal_reported_version'] or 'unreported') + ' | ' + r['use_summary'] + ' | '
        + ('Legacy v1.5.4 skipped; v2 is not a replacement.' if r['paper_id'] == 'P041' else 'Scoped v2 adapter; exact author recipe remains unqualified.') + ' |' for r in roles) + '\n\n').encode()
    for name in META[3:7]:
        after[name] = note + (table if name in (META[5], META[6]) else b'') + b'---\n\n' + before[name]
    notice = '<aside id="rfmix-catalog-notice" style="margin:1rem;padding:1rem;border:2px solid #986619;overflow-wrap:anywhere"><h2>RFMix scoped workflow and warnings</h2><p>' + html.escape(NOTICE) + '</p><p><strong>Warnings and required actions:</strong> ' + html.escape(ACTION) + '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/docs/rfmix-tools.md">Input contract, scope, validation and recovery</a></p></aside>'
    for name in META[8:]:
        page = before[name].decode()
        require('id="rfmix-catalog-notice"' not in page, 'Report already transitioned')
        if name == META[8]:
            match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
            require(match and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]), tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]])), 'Predecessor interactive payload differs')
            payload = json.dumps(dict(papers=papers, tools=inventory, summary=summary), ensure_ascii=False).replace('<', '\\u003c')
            page = page[:match.start(2)] + payload + page[match.end(2):]
            token = '${primaryEvidence(t)}'
            require(page.count(token) == 1 and page.count('function primaryEvidence(t){') == 1, 'Source evidence renderer differs')
            page = page.replace(token, token + '${rfmixScope(t)}', 1)
            helper = "function rfmixScope(t){if(t.tool_id!=='rfmix')return '';const q=t.source_role_qualification;return '<br><strong>Reported version: '+esc(q.reported_version||'unreported')+'</strong><br>Selected runtime: '+esc(q.selected_runtime_version)+'<br>'+esc(q.runtime_scope)+'<br>SIS-dependent analysis: SKIPPED. Exact author recipe remains unqualified.<br>'+esc(q.remaining_scope);}\n"
            page = page.replace('function primaryEvidence(t){', helper + 'function primaryEvidence(t){', 1)
        body = re.search(r'<body[^>]*>', page, re.I)
        marker = page.find('<site-header ', body.end())
        require(marker >= body.end(), 'Report body boundary differs')
        after[name] = (page[:body.end()] + notice + '<details id="rfmix-prior-catalog"><summary>Earlier catalog checkpoints (historical)</summary>' + page[body.end():marker] + '</details>' + page[marker:]).encode()
    return after, registry, coverage


def totals():
    return dict(papers=250, named_labels=453, provider_definitions=492, observations=2733, reviewed_uses=1825,
                pending_uses=908, scoped_families=172, native=34, upstream=138, missing_labels=251,
                new_source_reviews=4, new_upstream_family_credit=1, new_native_family_credit=0,
                unchanged_previous_observations=2729, unchanged_previous_reviews=1821,
                legacy_1_5_4_runtime='SKIPPED', sis_dependent_analysis='SKIPPED', whole_goal_complete=False)


def check(root):
    source_record(root)
    folder = root / FOLDER
    baseline = read(folder / 'BASELINE.json')
    require(pin(folder / 'metadata-before.zip') == baseline['archive'] and pin(folder / 'PREVIOUS-SHA256SUMS') == baseline['previous_checksums']
            and pin(root / Q) == baseline['qualification'] and pin(root / ROLES) == baseline['source_roles']
            and pin(root / 'tools/literature/rfmix_catalog.py') == baseline['transformer'], 'Catalog transition evidence changed')
    with zipfile.ZipFile(folder / 'metadata-before.zip') as stream:
        require(len(stream.namelist()) == len(META) and set(stream.namelist()) == set(META), 'RFMix predecessor archive population differs')
        before = {name: stream.read(name) for name in META}
    require({n: identity(raw) for n, raw in before.items()} == baseline['metadata'], 'Archived predecessor bytes changed')
    registry = predecessor(root, before)
    after, registry, coverage = transition(before, registry, read(root / ROLES)['roles'])
    require(all((root / n).read_bytes() == raw for n, raw in after.items()) and read(folder / 'REGISTRY.json') == registry, 'Current canonical metadata differs from exact transition')
    require(read(root / COVERAGE) == dict(schema_version=1, tool_id='rfmix', roles=coverage, new_family_credit=1, whole_paper_reproduced=False), 'Source role scope changed')
    require(read(root / PROOF) == dict(schema_version=1, status='PASS_SCOPED_RFMIX_CATALOG_ADMISSION', totals=totals(), qualification=pin(root / Q), raw_source_text_published=False, released_binary_contains_workflow=False, website_reports_published=False), 'Catalog proof changed')
    old, current = json.loads(before[META[0]]), json.loads(after[META[0]])
    verify_fixed_cohort(root, root / DATA, current)
    require([[u for u in p['tools'] if u['tool_id'] != 'rfmix'] for p in old] == [[u for u in p['tools'] if u['tool_id'] != 'rfmix'] for p in current], 'Unrelated observation changed')
    require([x for x in json.loads(before[META[1]]) if x['id'] != 'rfmix'] == [x for x in json.loads(after[META[1]]) if x['id'] != 'rfmix'], 'Unrelated inventory changed')
    require(json.loads(after[META[7]])['reviews'][:-4] == json.loads(before[META[7]])['reviews'], 'Prior reviews changed')
    reviews = json.loads(after[META[7]])['reviews']
    require(len({(x['pmcid'], x['tool_id']) for x in reviews}) == len(reviews) == 1825, 'Duplicate/wrong review population')
    previous = checksums((folder / 'PREVIOUS-SHA256SUMS').read_bytes())
    current_sums = checksums((root / DATA / 'SHA256SUMS').read_bytes())
    require(set(current_sums) == set(previous) | {Path(COVERAGE).name, Path(PROOF).name}, 'Checksum population differs')
    allowed = {Path(n).name for n in META if n.startswith(DATA + '/')}
    require(all(current_sums[n] == digest or n in allowed for n, digest in previous.items()), 'Unrelated checksum refreshed')
    verify_sums(root, current_sums)
    return totals()


def apply(root, private, cache, source, out):
    require(not (root / FOLDER).exists() and out.is_relative_to(private) and not out.exists() and out.parent.resolve() == out.parent, 'NEW catalog/private output paths required')
    q, evidence = private_evidence(root, private)
    primary = source_audit(root, cache, source)
    before = {n: (root / n).read_bytes() for n in META}
    registry = predecessor(root, before)
    after, registry, coverage = transition(before, registry, read(root / ROLES)['roles'])
    previous = (root / DATA / 'SHA256SUMS').read_bytes()
    rows = checksums(previous)
    require(len(rows) == 896, 'Predecessor checksum population differs')
    verify_sums(root, rows)
    out.mkdir()
    folder = root / FOLDER
    folder.mkdir()
    with zipfile.ZipFile(folder / 'metadata-before.zip', 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as stream:
        for name, raw in before.items():
            stream.writestr(name, raw)
    (folder / 'PREVIOUS-SHA256SUMS').write_bytes(previous)
    (folder / 'BASELINE.json').write_bytes(encoded(dict(schema_version=1, archive=pin(folder / 'metadata-before.zip'), metadata={n: identity(raw) for n, raw in before.items()},
        previous_checksums=pin(folder / 'PREVIOUS-SHA256SUMS'), qualification=pin(root / Q), source_roles=pin(root / ROLES), transformer=pin(root / 'tools/literature/rfmix_catalog.py'))))
    (out / 'PLAN.json').write_bytes(encoded(dict(before={n: identity(raw) for n, raw in before.items()}, after={n: identity(raw) for n, raw in after.items()}, totals=totals(), primary=primary, retained_private_evidence=evidence)))
    for i, (name, raw) in enumerate(after.items()):
        require((root / name).read_bytes() == before[name], 'Concurrent catalog change: ' + name)
        stage = out / ('staged-' + str(i))
        stage.write_bytes(raw)
        os.replace(stage, root / name)
    (folder / 'REGISTRY.json').write_bytes(encoded(registry))
    (root / COVERAGE).write_bytes(encoded(dict(schema_version=1, tool_id='rfmix', roles=coverage, new_family_credit=1, whole_paper_reproduced=False)))
    (root / PROOF).write_bytes(encoded(dict(schema_version=1, status='PASS_SCOPED_RFMIX_CATALOG_ADMISSION', totals=totals(), qualification=pin(root / Q), raw_source_text_published=False, released_binary_contains_workflow=False, website_reports_published=False)))
    for name in [n for n in META if n.startswith(DATA + '/')] + [COVERAGE, PROOF]:
        rows[Path(name).name] = pin(root / name)['sha256']
    newline = '\r\n' if previous.count(b'\n') == previous.count(b'\r\n') else '\n'
    (root / DATA / 'SHA256SUMS').write_bytes((''.join(digest + '  ' + name + newline for name, digest in rows.items())).encode())
    result = check(root)
    (out / 'COMPLETED.json').write_bytes(encoded(dict(status='PASS_EXACT_RFMIX_CATALOG_TRANSITION', totals=result)))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--private', type=Path)
    parser.add_argument('--xml-cache', type=Path)
    parser.add_argument('--upstream-source', type=Path)
    parser.add_argument('--out', type=Path)
    args = parser.parse_args()
    try:
        root = args.root.resolve(strict=True)
        if args.apply:
            require(args.private and args.xml_cache and args.upstream_source and args.out, 'Apply requires completed private qualification, original source inputs and NEW private output')
            result = apply(root, args.private.resolve(strict=True), args.xml_cache.absolute(), args.upstream_source.absolute(), args.out.absolute())
            status = 'PASS_EXACT_RFMIX_CATALOG_TRANSITION'
        else:
            result = check(root)
            status = 'PASS_METADATA_DERIVATION_ONLY_ORIGINAL_SOURCES_NOT_VERIFIED'
            if args.xml_cache or args.upstream_source:
                require(args.xml_cache and args.upstream_source, 'Supply both original XML cache and exact original upstream source')
                result['primary_source_review'] = source_audit(root, args.xml_cache.absolute(), args.upstream_source.absolute())
                status = 'PASS_FULL250_RFMIX_MAIN_TEXT_SOURCE_AND_METADATA_REVIEW'
        print(json.dumps(dict(status=status, **result), sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3 DEPENDENT RFMIX CATALOG CHECK SKIPPED: ' if code == 3 else 'FAIL2 NO COMPLETE RFMIX CATALOG CHECK: ') + str(error)
              + '. Preserve evidence, restore the exact predecessor, original sources and producing context, or correct NEW paths. Do not refresh historical seals. Full goal remains active.', file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
