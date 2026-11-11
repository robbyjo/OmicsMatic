# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify GENESIS catalog admission and all unchanged scientific dependencies, with the full goal open."""
import argparse
from collections import Counter
import csv
import gzip
import io
import json
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack, byte_pin
from catalog_source_integrity import verify_source
from verify_genesis_tools_evidence import inspect as component_verify

DATA = ROOT / 'docs/literature/omics-2023-2026'
STATUS = 'PASS_GENESIS_SCOPED_WORKFLOWS_FIVE_PAPER_ROLES_AND_CATALOG_WITH_GOAL_OPEN'
PREFIX = 'build/literature-review/genesis-runtime/'


def verify(check_current=True):
    proof = json.loads((DATA / 'genesis-catalog-validation.json').read_bytes())
    assert proof['status'] == STATUS and proof['new_catalog_credit'] and proof['new_tool_id'] == 'genesis'
    assert not proof['whole_package_implemented'] and not proof['whole_paper_reproduced'] and not proof['whole_goal_complete']
    for name, value in proof['source_pins'].items():
        verify_source(ROOT, DATA, name, value['sha256'])
    for name, value in proof['component_files'].items():
        assert pin(ROOT / name) == value, ('Changed current GENESIS scientific evidence', name)
    component_verify()
    assert pin(DATA / 'genesis-catalog-support.zip') == proof['archive']; files = unpack(DATA / 'genesis-catalog-support.zip', proof)
    for name, value in proof['source_pins'].items():
        assert byte_pin(files['current-source/' + name]) == value
    before = {r['id']: r for r in json.loads(files['catalog-before/tool-inventory.json'])}
    after = {r['id']: r for r in json.loads(files['catalog-snapshot/tool-inventory.json'])}
    assert set(before) == set(after) and len(after) == 402
    assert before['genesis']['implementation_status'] == 'missing' and all(after[key] == value for key, value in before.items() if key != 'genesis')
    gene = after['genesis']; assert gene['implementation_status'] == 'upstream-adapter-validated'
    assert gene['papers'] == ['P128', 'P130', 'P142', 'P191', 'P219'] and gene['source_roles_reviewed'] == 5
    assert gene['root_command'] == 'genesis-tools' and gene['supported_source_versions'] == ['2.42.0'] and not gene['canonical_adapter_available'] and not gene['analysis_installs_software']
    assert after['cellranger']['implementation_status'] == 'excluded-by-user'
    summary = json.loads(files['catalog-snapshot/summary.json']); counts = dict(Counter(r['implementation_status'] for r in after.values()))
    assert counts == summary['implementation_status_counts']
    assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations'], counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['existing-adapter'], counts['missing']) == (250, 402, 2475, 19, 78, 3, 246)
    old = json.loads(gzip.decompress(files['catalog-snapshot/genesis-prior-reviews.json.gz']))['reviews']
    reviews = json.loads(files['current-source/tools/literature/reviewed-uses.json'])['reviews']; assert len(old) == len(reviews) == 1038
    assert len({(r['pmcid'], r['tool_id']) for r in reviews}) == 1038 and sum(len(r.get('supporting_evidence', [])) for r in reviews) == 369
    changed = []
    for a, b in zip(old, reviews, strict=True):
        if a != b:
            assert b['tool_id'] == 'genesis' and {k: v for k, v in a.items() if k != 'implementation_scope_note'} == {k: v for k, v in b.items() if k != 'implementation_scope_note'}
            changed.append(b['pmcid'])
    assert len(changed) == 5
    papers = json.loads(files['catalog-snapshot/papers.json']); oldpapers = json.loads(gzip.decompress(files['catalog-snapshot/genesis-prior-papers.json.gz']))
    assert len(papers) == len(oldpapers) == 250 and sum(len(p['tools']) for p in papers) == 2475
    for a, b in zip(oldpapers, papers, strict=True):
        assert {k: v for k, v in a.items() if k != 'tools'} == {k: v for k, v in b.items() if k != 'tools'}
        for x, y in zip(a['tools'], b['tools'], strict=True):
            assert x == y if y['tool_id'] != 'genesis' else {k: v for k, v in x.items() if k != 'implementation_scope_note'} == {k: v for k, v in y.items() if k != 'implementation_scope_note'}
    coverage = json.loads(files['catalog-snapshot/genesis-source-role-coverage.json']); assert coverage['status'] == 'PASS_FIVE_GENESIS_SCOPES_AND_SEPARATE_PRIMARY_SUPPLEMENT_POINTER'
    supplement = coverage['separate_primary_supplement']; assert supplement['explicit_function'] == 'pcairPartition' and supplement['reported_kinship_cutoff'] == .044 and supplement['reported_version'] == '2.16.1'
    assert supplement['downstream_unreported_functions_not_inferred'] and not supplement['pdf_or_article_prose_redistributed']
    def obj(path):
        return json.loads(files[PREFIX + path])
    assert obj('catalog-source-audit-1/COMPLETED.json')['reviewed_roles'] == 1038
    assert len(obj('catalog-shared-gates-1/COMPLETED.json')['checks']) == 51
    ui = obj('catalog-ui-1/COMPLETED.json'); assert (ui['general_checks'], ui['focused_checks']) == (299, 18)
    assert obj('catalog-render-2/COMPLETED.json')['unchanged_other_tool_objects'] == 401
    for name in ['tools-replay-windows21-1', 'tools-replay-windows25-1', 'tools-replay-linux21-1']:
        replay = obj(name + '/COMPLETED.json'); assert replay['status'] == 'PASS11_GENESIS_TOOLS_SAVED_SCIENTIFIC_REPLAYS_WITHOUT_R' and len(replay['records']) == 11 and not replay['original_R_analysis_executed']
    cross = obj('cross-option-controls-1/COMPLETED.json'); assert len(cross['records']) == 12
    assert all(r['original_exit'] == 0 and not r['project_analysis_admitted'] for r in cross['records'])
    assert sum(r['native_component_exit'] == 0 for r in cross['records']) == 10
    finding = json.loads(files['catalog-snapshot/genesis-cross-option-findings.json'])
    assert finding['actual_root_refusals'] == 2 and not finding['original_missing_values_replaced'] and not finding['partial_results_admitted']
    for case in finding['findings']:
        prefix = PREFIX + 'cross-option-controls-1/' + case['case'] + '/worker/'
        schema = {r['column']: bytes.fromhex(r['name_utf8_hex']).decode('utf8') for r in csv.DictReader(io.StringIO(files[prefix + 'original-association.schema.tsv'].decode('utf8')), delimiter='\t')}
        cells = list(csv.DictReader(io.StringIO(files[prefix + 'original-association.cells.tsv'].decode('utf8')), delimiter='\t'))
        ids = {r['row']: int(float.fromhex(r['value_hex'])) for r in cells if schema[r['column']] == 'variant.id'}
        missing = [dict(output_row=int(c['row']), input_variant_row=ids[c['row']], field=schema[c['column']], value_hex=c['value_hex'], is_na=c['is_na'], is_nan=c['is_nan']) for c in cells if c['is_na'] == 'TRUE']
        assert missing == case['complete_missing_cells'] and len(missing) == case['missing_cells']
        assert all(c['value_hex'] == 'NA' and c['is_nan'] == 'FALSE' for c in missing)
        status = obj('cross-option-refusal-cli-1/' + case['case'] + '/COMPLETED.json')
        assert not status['result_admitted'] and status['original_analysis_executed'] and 'SE.G:PRS' in status['cause']
        assert b'REQUIRED ACTION' in files[PREFIX + 'cross-option-refusal-cli-1/' + case['case'] + '/WARNINGS.md']
    readme = files['catalog-snapshot/README.md']; assert b'97 new scoped integrations' in readme and b'100 including three existing adapters' in readme and b'246 named tools still missing' in readme
    warning = files['catalog-snapshot/WARNINGS.md']; assert warning.startswith(b'## GENESIS: warnings and required actions') and b'GENESIS: original missing interaction results' in warning and b'SE.G:PRS' in warning
    assert proof['canonical_executable_unchanged']['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    if check_current:
        assert all(pin(DATA / name) == value for name, value in proof['catalog_snapshot'].items())
        assert all(pin(ROOT / name) == value for name, value in proof['page_pins'].items())
        expected = {p.name: pin(p)['sha256'] for p in DATA.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
        rows = (DATA / 'SHA256SUMS').read_text(encoding='utf8').splitlines(); actual = {r.split('  ', 1)[1]: r.split('  ', 1)[0] for r in rows}
        assert len(rows) == len(actual) and actual == expected
    return proof, files


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact source, prior science or complete catalog checks')
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--archived-catalog-only', action='store_true'); args = p.parse_args()
    verify(not args.archived_catalog_only); print(STATUS, '250 papers;97 new/100 including existing;246 missing;goal open')
