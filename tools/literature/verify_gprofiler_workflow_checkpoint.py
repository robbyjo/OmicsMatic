#!/usr/bin/env python3
"""Read-only saved checkpoint audit; no Java, network, extraction or catalog admission."""
from pathlib import Path, PurePosixPath
from collections import Counter
import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pin(raw):
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def verify(root, jar=None):
    folder = root / 'docs/literature/gprofiler-workflow-qualification'
    q = json.loads((folder / 'QUALIFICATION.json').read_bytes())
    raw = (folder / 'EVIDENCE.json').read_bytes()
    require(pin(raw) == q['evidence'], 'Evidence index differs')
    evidence = json.loads(raw)
    raw = (folder / 'evidence.zip').read_bytes()
    require(pin(raw) == evidence['zip'], 'Evidence ZIP differs')
    require(not evidence['original_cookie_values_exported'] and
            not evidence['private_primary_XML_or_runtime_exported'], 'Private export declaration differs')
    logical = evidence['files']
    with zipfile.ZipFile(folder / 'evidence.zip') as archive:
        names = archive.namelist()
        expected = {'objects/' + item['sha256'] for item in logical.values()}
        require(len(names) == len(set(names)) == evidence['objects'] == len(expected) and
                set(names) == expected, 'ZIP is not a closed unique object inventory')
        objects = {name: archive.read(name) for name in names}
    for name, identity in logical.items():
        require(not PurePosixPath(name).is_absolute() and '..' not in PurePosixPath(name).parts and
                '\\' not in name, 'Unsafe logical path')
        require(pin(objects['objects/' + identity['sha256']]) == identity, 'Logical object differs: ' + name)

    def data(name):
        return objects['objects/' + logical[name]['sha256']]

    def load(name):
        return json.loads(data(name))

    require(data('NOTICE.md') == (folder / 'NOTICE.md').read_bytes(), 'Acknowledgements differ')
    for name, identity in q['production_sources'].items():
        require(pin((root / name).read_bytes()) == identity ==
                logical['normal/source-context/' + name], 'Production source differs: ' + name)
    require(len(q['production_sources']) == 8, 'Wrong scoped source set')
    normal = load('normal/RESULT.json')
    require(normal['exit_code'] == 0 and normal['source_inputs_unchanged'] and
            normal['development_jar'] == q['producer'], 'Normal build receipt differs')
    reports = [ET.fromstring(data(name)) for name in logical if name.startswith('normal/junit/')]
    require(len(reports) == 2 and sum(int(r.attrib['tests']) for r in reports) == q['normal_tests'] == 28,
            'Wrong normal test coverage')
    require(all(int(r.attrib.get(k, 0)) == 0 for r in reports for k in ['failures', 'errors', 'skipped']),
            'Normal test suite did not pass')
    if jar is not None:
        require(pin(jar.read_bytes()) == q['producer'], 'Actual producer JAR differs')

    publication = load('publication-audit/PUBLICATION.json')
    checks = load('publication-audit/RESULT.json')
    require(publication['originals_unchanged_and_retained_privately'] and
            not publication['scientific_request_and_response_payloads_changed'] and
            publication['cookie_secret_scan_passed'] and not publication['authentication'],
            'Publication lineage declarations differ')
    transforms = {row['path']: row for row in publication['transforms']}
    require(len(transforms) == publication['header_files_changed'] == 85, 'Wrong cookie transform census')
    for name, record in publication['files'].items():
        require(logical['publication/' + name] == record['publication'], 'Publication file differs: ' + name)
        if record['original'] != record['publication']:
            require(name.endswith(('-exchange.json', '/manifest.json', '/COMPLETED.json')),
                    'Unexpected scientific payload transformation: ' + name)
        if name in transforms:
            require(record == {k: transforms[name][k] for k in ['original', 'publication']},
                    'Transform identities differ')
    cookie_headers = 0
    for name in logical:
        if name.startswith('publication/') and name.endswith('-exchange.json'):
            for key, values in load(name).get('headers', {}).items():
                if key.lower() == 'set-cookie':
                    require(all(re.fullmatch(r'\[cookie value redacted; original SHA-256 [0-9a-f]{64}\]', v)
                                for v in values), 'Original cookie value would be exported')
                    cookie_headers += 1
    require(cookie_headers == 85, 'Wrong redacted header census')
    require(len(checks['checks']) == 42 and
            Counter(row['exit_code'] for row in checks['checks']) == {0: 21, 2: 21},
            'Wrong native publication-copy checks')
    roots = {row['path']: row for row in publication['roots']}
    require(len(roots) == 42, 'Wrong publication root census')
    for row in checks['checks']:
        require(row['exit_code'] == row['expected'], 'Publication check failed')
        prefix = 'publication-audit/checks/' + row['log_prefix']
        require(prefix + '.stdout' in logical and prefix + '.stderr' in logical, 'Missing native check logs')
        name = row['root']
        info = roots[name]
        require(logical['publication/' + info['original_manifest_copy']] == info['original_manifest'] and
                logical['publication/' + name + '/manifest.json'] == info['publication_manifest'],
                'Original/publication manifest identity differs')
        if row['exit_code'] == 0:
            base = 'publication/' + name + '/'
            manifest = load(base + 'manifest.json')
            require(load(base + 'COMPLETED.json')['manifest_sha256'] == logical[base + 'manifest.json']['sha256'],
                    'Valid completion does not bind manifest')
            actual = {n[len(base):]: identity for n, identity in logical.items()
                      if n.startswith(base) and n[len(base):] not in ['manifest.json', 'COMPLETED.json']}
            require(manifest['files'] == actual, 'Valid bundle is not closed')

    live = load('publication/live-packaged-3/QUALIFICATION.json')
    require(live['producer'] == q['producer'] and len(live['processes']) == 37 and
            all(row['exit_code'] == row['expected'] for row in live['processes']), 'Packaged execution receipt differs')
    cases = [row for row in live['cases'] if row['case'] != 'metadata-discovery' and not row['status'].startswith('SKIP3')]
    require(len(cases) == 8, 'Wrong enrichment case census')
    total = nulls = 0
    for row in cases:
        base = 'publication/live-packaged-3/' + row['case'] + '/run/'
        response = load(base + 'wire/002-response.body')
        require(data(base + 'wire/002-response.body') == data(base + 'result.json'), 'Raw response differs')
        require(response['result'] == [json.loads(line) for line in data(base + 'results.jsonl').splitlines()],
                'Full source result values differ')
        total += len(response['result'])
        payload = load(base + 'request.json')['payload']
        if not payload['no_evidences'] and not payload['combined']:
            nulls += sum(value is None for r in response['result'] for value in r['intersections'])
    require(total == q['original_reported_terms'] == 4089 and
            nulls == q['original_null_evidence_cells_preserved'] == 1647, 'Result/null census differs')
    base = 'publication/live-packaged-3/exact-paper-build-unavailable/'
    require(load(base + 'run/FAILURE.json')['exit_code'] == 3 and
            load(base + 'run/wire/001-request.body') == {'organism': 'hsapiens'} and
            not any(base + 'run/' + tail in logical for tail in
                    ['wire/002-request.body', 'result.json', 'COMPLETED.json']), 'Exact-build skip uploaded genes/admitted result')
    for path, count in [('public-saved-controls-3', 21), ('public-runtime-controls-2', 11)]:
        receipt = load('publication/' + path + '/RESULT.json')
        require(receipt['producer'] == q['producer'] and len(receipt['controls']) == count and
                all(r['exit_code'] == r.get('expected', 2) for r in receipt['controls']), 'Refusal receipt differs')
    linux = load('publication/linux-offline-2/RESULT.json')
    require(linux['producer'] == q['producer'] and len(linux['controls']) == 15 and
            all(r['exit_code'] == 0 for r in linux['controls']) and not linux['Linux_live_service_executed'],
            'Linux saved-protocol receipt differs')
    reference = load('original-python/RESULT.json')
    require(len(reference['cases']) == 8 and reference['rows'] == total and
            not reference['new_network_execution'] and not reference['R_client_parity_claimed'], 'Reference scope differs')
    for name, identity in reference['sources'].items():
        require(logical['original-python/original/' + name] == identity, 'Original Python source differs')
    require(not q['whole_goal_complete'] and not q['whole_tool_qualified'] and not q['whole_paper_reproduced'],
            'Overbroad completion claim')
    return dict(status='PASS_SAVED_CHECKPOINT_INTEGRITY_NOT_CATALOG_ADMISSION', logical_files=len(logical),
                objects=len(objects), normal_tests=28, original_rows=total, original_nulls=nulls,
                public_valid_checks=21, public_invalid_checks=21, actual_producer_checked=jar is not None,
                new_family_credit=0, canonical_reviews_added=0, network_executed=False,
                independent_statistical_refit=False, catalog_admission_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--jar', type=Path, help='Optional exact saved development producer; never executed')
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.root, args.jar), indent=2))
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile, ET.ParseError) as error:
        print('FAIL2: ' + str(error), file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
