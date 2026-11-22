#!/usr/bin/env python3
"""Read-only source/receipt checks for a development component, without network or Java/R execution."""
from pathlib import Path
import argparse
import hashlib
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
FOLDER = ROOT / 'docs/literature/gprofiler-api-development'


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, help='Optionally compare the unchanged actual development JAR bytes')
    args = parser.parse_args()
    files = json.loads((FOLDER / 'FILES.json').read_bytes())
    require(set(p.name for p in FOLDER.iterdir()) == set(files) | {'FILES.json'}, 'Changed receipt file set')
    for name, wanted in files.items():
        require(Path(name).name == name and pin(FOLDER / name) == wanted, 'Changed receipt: ' + name)
    status = json.loads((FOLDER / 'STATUS.json').read_bytes())
    require(status['status'] == 'DEVELOPMENT_API_COMPONENT_TESTED_NOT_CATALOG_ADMITTED', 'Changed checkpoint claim')
    require(status['new_family_credit'] == status['canonical_review_records_added'] == 0
            and status['whole_goal_complete'] is False and status['whole_tool_qualified'] is False,
            'Component receipts cannot establish provider admission or goal completion')
    for field in ['component_sources', 'source_audit_program']:
        for name, wanted in status[field].items():
            path = Path(name)
            require(not path.is_absolute() and '..' not in path.parts and pin(ROOT / path) == wanted,
                    'Changed declared source context: ' + name)
    tests = ET.parse(FOLDER / 'junit.xml').getroot()
    require(tests.get('tests') == '13' and all(tests.get(k) == '0' for k in ['failures', 'errors', 'skipped']),
            'Different normal Java test receipt')
    census = json.loads((FOLDER / 'CENSUS.json').read_bytes())
    require(pin(FOLDER / 'CENSUS.json') == json.loads((FOLDER / 'CENSUS-PIN.json').read_bytes()), 'Changed census pin')
    require(len(census['identities']) == census['papers_checked'] == 250 and len(census['roles']) == 7
            and census['unbound_broader_candidates'] == census['table_candidates'] == [], 'Changed source audit scope')
    require(census['new_family_credit'] == 0 and not census['canonical_catalog_modified'], 'False audit admission')
    live = json.loads((FOLDER / 'live-service-summary.json').read_bytes())
    require(live['producer'] == status['normal_build']['development_jar'] and len(live['cases']) == 5,
            'Changed actual producer or live-control scope')
    require(sum(c.get('result', {}).get('terms', 0) for c in live['cases']) == 3028
            and live['cases'][-1]['failure']['exit_code'] == 3
            and not live['cases'][-1]['failure']['admitted_result'], 'Changed result/skip receipts')
    reference = json.loads((FOLDER / 'original-client-reference-summary.json').read_bytes())
    require(sum(c['returned_rows'] for c in reference['cases']) == 3028
            and reference['new_network_execution'] is False
            and reference['full_client_runtime_qualification'] is False, 'False original-client runtime claim')
    if args.jar:
        require(pin(args.jar) == live['producer'], 'Restore the exact original component test producer')
    print(json.dumps(dict(status='PASS_DEVELOPMENT_SOURCE_AND_SAVED_RECEIPT_CONSISTENCY',
                          receipt_files=len(files), component_sources=len(status['component_sources']),
                          source_papers=250, source_roles=7, saved_normal_tests=13,
                          actual_jar_bytes_checked=args.jar is not None, network_executed=False,
                          original_reply_bundle_reverified=False, whole_tool_qualified=False,
                          new_family_credit=0, whole_goal_complete=False)))


if __name__ == '__main__':
    main()
