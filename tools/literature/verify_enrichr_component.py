"""Verify saved Enrichr API-component evidence, optionally restoring its small development JAR."""
from pathlib import Path
import argparse
import hashlib
import io
import json
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PACKET = ROOT / 'docs/literature/enrichr-component-checks'


def encoded(value):
    return (json.dumps(value, indent=2) + '\n').encode()


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def inside(name):
    path = (ROOT / name).resolve()
    require(path.is_relative_to(ROOT) and not path.is_symlink(), 'Unsafe saved source path: ' + name)
    return path


def verify():
    proof = json.loads((PACKET / 'QUALIFICATION.json').read_bytes())
    require(proof['status'] == 'PASS_ENRICHR_SOURCE_CENSUS_AND_API_COMPONENT_ONLY', 'Wrong proof status')
    for name, wanted in proof['sources'].items():
        require(pin(inside(name).read_bytes()) == wanted, 'Saved component source changed: ' + name)
    readback = json.loads((PACKET / 'READBACK.json').read_bytes())
    for name, wanted in readback['artifacts'].items():
        require('/' not in name and '\\' not in name and name not in ['.', '..'], 'Unsafe packet name')
        require(pin((PACKET / name).read_bytes()) == wanted, 'Saved packet changed: ' + name)
    census = json.loads((PACKET / 'CENSUS.json').read_bytes())
    require(census['positive_roles'] == 25 and len(census['roles']) == 25 and
            len(census['primary_files']) == 250 and census['named_paragraphs'] == 44,
            'Saved source census population differs')
    require(census['observations_added'] == ['P048', 'P051', 'P134'] and
            census['existing_reviews_retained'] == 2 and not census['canonical_metadata_written'],
            'Source-only review disposition differs')
    require(all(not role['author_runtime_executed'] and not role['whole_paper_reproduced']
                and not role['enrichment_service_execution_verified'] for role in census['roles']),
            'Saved source roles claim execution')
    archive = (PACKET / 'evidence.zip').read_bytes()
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        names = z.namelist()
        require(len(names) == len(set(names)) and set(names) == set(proof['evidence_payloads']), 'Evidence member set differs')
        require(z.testzip() is None, 'Evidence CRC failure')
        for name, wanted in proof['evidence_payloads'].items():
            require(not name.startswith(('/', '\\')) and '..' not in Path(name).parts, 'Unsafe evidence name')
            require(pin(z.read(name)) == wanted, 'Evidence payload changed: ' + name)
        component = z.read('component/omicsmatic-enrichr-component-development.jar')
        license_text = z.read('component/LICENSE.txt')
    require(pin(component) == proof['component_jar'], 'Component JAR identity differs')
    tests = proof['tests']
    require(set(tests) == {'windows21', 'windows25', 'linux21', 'packaged_windows21'} and
            all(v == dict(tests=16, failures=0, errors=0, skipped=0) for v in tests.values()),
            'Saved component test disposition differs')
    require(not proof['full_cli_workflow_qualified'] and not proof['catalog_changed'] and
            proof['new_family_credit'] == 0, 'Component proof overclaims admission')
    return component, license_text, dict(status='PASS_ENRICHR_SAVED_COMPONENT_READBACK',
        source_files=len(proof['sources']), evidence_payloads=len(proof['evidence_payloads']),
        saved_test_executions=64, fresh_upstream_execution=False, fresh_primary_xml_audit=False,
        full_cli_workflow_qualified=False, catalog_changed=False, new_family_credit=0)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--restore-component', action='store_true')
    args = p.parse_args()
    owned = False
    try:
        args.out.mkdir(parents=True, exist_ok=False)
        owned = True
        component, license_text, result = verify()
        if args.restore_component:
            destination = args.out / 'component'
            destination.mkdir()
            (destination / 'omicsmatic-enrichr-component-development.jar').write_bytes(component)
            (destination / 'LICENSE.txt').write_bytes(license_text)
            result['component_restored'] = True
            result['component_jar'] = pin(component)
        else:
            result['component_restored'] = False
        (args.out / 'COMPLETED.json').write_bytes(encoded(result))
        print(json.dumps(result))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        failure = dict(status='SKIP3_MISSING_COMPONENT_PREREQUISITE' if code == 3 else
            'FAIL2_NO_COMPONENT_READBACK_ADMITTED', error=repr(error),
            required_action='Restore the exact named permitted source/packet for SKIP3 or retain the skip. For FAIL2 preserve changed evidence, correct the cause and retry NEW outputs; do not refresh saved hashes or overwrite old files.')
        if owned:
            (args.out / 'FAILURE.json').write_bytes(encoded(failure))
        print(json.dumps(failure), file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
