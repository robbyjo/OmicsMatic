"""Offline readback of the development handover; no JVM or service execution."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
import zipfile

from enrichr_workflow_transition import annotations, dispatcher, previous_abf, previous_enrichr_api

ROOT = Path(__file__).resolve().parents[2]
FOLDER = ROOT / 'docs/literature/enrichr-workflow-checks'
STATUS = 'PASS_ENRICHR_WORKFLOW_DEVELOPMENT_HANDOVER'


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def member(name):
    require(isinstance(name, str) and '\\' not in name and ':' not in name,
            'Unsafe archive/source member')
    p = PurePosixPath(name)
    require(not p.is_absolute() and p.parts and all(x not in ['', '.', '..'] for x in p.parts)
            and p.as_posix() == name, 'Unsafe archive/source member')
    return name


def verify(folder):
    readback = json.loads((folder / 'READBACK.json').read_bytes())
    for name, wanted in readback['artifacts'].items():
        require('/' not in name and '\\' not in name and name not in ['.', '..'], 'Unsafe packet path')
        require(pin((folder / name).read_bytes()) == wanted, 'Changed packet: ' + name)
    proof = json.loads((folder / 'QUALIFICATION.json').read_bytes())
    require(proof['status'] == STATUS and proof['catalog_admitted'] is False
            and proof['new_family_credit'] == 0 and proof['whole_goal_complete'] is False,
            'Wrong handover disposition')
    for name, wanted in proof['sources'].items():
        require(pin((ROOT / member(name)).read_bytes()) == wanted, 'Changed current source: ' + name)
    with zipfile.ZipFile(folder / 'evidence.zip') as z:
        names = z.namelist()
        require(len(names) == len(set(names)) and set(names) == set(proof['evidence_payloads']),
                'Archive population differs')
        require(z.testzip() is None, 'Evidence CRC failed')
        for name, wanted in proof['evidence_payloads'].items():
            require(pin(z.read(member(name))) == wanted, 'Changed evidence payload: ' + name)
        normal = json.loads(z.read('normal/COMPLETED.json'))
        require(normal['status'] == 'PASS_NORMAL_STRICT_GRADLE_ENRICHR_HANDOVER_TESTS'
                and normal['candidate'] == proof['normal_candidate'], 'Wrong normal artifact')
        counts = {}
        for host in ['windows21', 'windows25', 'linux21']:
            total = {k: 0 for k in ['tests', 'failures', 'errors', 'skipped']}
            prefix = 'normal/reports-' + host + '/'
            reports = [n for n in names if n.startswith(prefix) and n.endswith('.xml')]
            require(bool(reports), 'Missing test XML: ' + host)
            for name in reports:
                node = ET.fromstring(z.read(name))
                for key in total:
                    total[key] += int(node.get(key, '0'))
            require(total == dict(tests=42, failures=0, errors=0, skipped=0), 'Wrong test disposition')
            counts[host] = total
        require(counts == normal['tests'], 'Saved test summary differs')
        ref = json.loads(z.read('reference/COMPLETED.json'))
        require(ref['candidate'] == proof['composed_reference_candidate'], 'Wrong reference artifact')
        require(sum(x['rows'] for x in ref['comparisons']) == 600, 'Wrong reference result population')
        require(all(x['all_nine_result_fields_compared'] and x['exact_numeric_tokens']
                    for x in ref['comparisons']), 'Reference comparison scope differs')
        serial = json.loads(z.read('serial/COMPLETED.json'))
        require(serial['scientific_bytecode_changed'] is False
                and serial['old_fingerprints_refreshed'] is False
                and len(serial['class_payloads']) == 25, 'Wrong annotation proof')
        for name, wanted in serial['class_payloads'].items():
            require(pin(z.read('serial/annotated/' + member(name))) == wanted,
                    'Changed annotated class payload: ' + name)
            require(pin(z.read('serial/original/' + name)) == wanted,
                    'Changed original class payload: ' + name)
        before = z.read('dispatch/JLinAlgCli.java')
        compatibility = dict(dispatch=dispatcher(before, (ROOT / 'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_bytes()),
                             annotations=annotations(), previous_abf=previous_abf(before),
                             previous_api=previous_enrichr_api())
        require(compatibility == json.loads(z.read('COMPATIBILITY.json')), 'Compatibility record differs')
    return dict(status=STATUS, source_files_checked=len(proof['sources']),
                archive_payloads_checked=len(proof['evidence_payloads']), tests=counts,
                saved_reference_live_rows=600, normal_candidate=proof['normal_candidate'],
                compatibility=compatibility, fresh_JVM_execution=False,
                fresh_service_execution=False, normal_jar_live_qualified=False,
                catalog_admitted=False, new_family_credit=0, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True, help='NEW readback output directory')
    parser.add_argument('--packet', type=Path, default=FOLDER, help='Saved packet directory')
    args = parser.parse_args()
    if args.out.exists():
        print('FAIL2: output exists; select a NEW directory', file=sys.stderr)
        return 2
    args.out.mkdir(parents=True)
    try:
        result = verify(args.packet)
        (args.out / 'COMPLETED.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print(STATUS + ': saved evidence only; normal-JAR live/example qualification and catalog admission remain open')
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        action = ('Restore the named exact source/packet dependency and retry into a NEW output.' if code == 3 else
                  'Preserve the failure, inspect changed/invalid evidence, restore exact reviewed bytes and retry into a NEW output. Do not refresh old hashes.')
        result = dict(status='SKIP3' if code == 3 else 'FAIL2', error=str(error), action=action,
                      catalog_admitted=False, whole_goal_complete=False)
        (args.out / 'FAILURE.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print(result['status'] + ': ' + str(error) + '\n' + action, file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
