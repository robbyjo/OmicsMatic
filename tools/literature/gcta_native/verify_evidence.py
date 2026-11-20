"""Verify saved native GRM component evidence; never run Java or scientific models."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile


def pin(data):
    return dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--jar', type=Path,
        help='Optional exact captured normal producer; omission records SKIP3 for producer integrity')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    q = root / 'docs/literature/gcta-native-development'
    profile = json.loads((q / 'PROFILE.json').read_bytes())
    args.out.mkdir(parents=True, exist_ok=False)
    try:
        for name, expected in profile['source_inputs'].items():
            if pin((root / name).read_bytes()) != expected:
                raise ValueError('qualified component input differs: ' + name)
        archive = q / 'evidence.zip'
        if pin(archive.read_bytes()) != profile['evidence_archive']:
            raise ValueError('saved evidence archive integrity mismatch')
        with zipfile.ZipFile(archive) as z:
            entries = z.infolist()
            if len(entries) != len({e.filename for e in entries}) or sum(e.file_size for e in entries) > 30_000_000:
                raise ValueError('duplicate or over-capacity evidence archive')
            def read(name): return json.loads(z.read(name))
            before = read('before-cancellation/COMPLETED.json')
            report = before['reports']['org.jlinalg.genetics.GctaHardcallGrmTest']
            if before['exit_code'] != 1 or report['failures'] != 1 or report['failed_cases'] != ['cancellationAtEntryAndFinalProgressCallbackCannotReturnAResult()']:
                raise ValueError('missing original cancellation regression evidence')
            build = read('qualified-build/COMPLETED.json')
            if build['exit_code'] != 0 or sum(r['tests'] for r in build['reports'].values()) != 9 or any(r['failures'] or r['errors'] or r['skipped'] for r in build['reports'].values()):
                raise ValueError('normal build/focused qualification incomplete')
            packaged = read('packaged/COMPLETED.json')
            if set(packaged['results']) != {'windows21', 'windows25', 'linux21'} or any(r['exit_code'] != 0 for r in packaged['results'].values()):
                raise ValueError('packaged three-JVM qualification incomplete')
            if packaged['jar'] != profile['normal_producer'] or packaged['payloads']['prior_payloads_unchanged'] != 23522 or len(packaged['payloads']['added_native_classes']) != 8 or packaged['payloads']['removed'] or packaged['payloads']['changed']:
                raise ValueError('producer payload preservation differs')
            for host in packaged['results']:
                if b'GCTA_PACKAGED_SOURCE_CHECKS_PASS tests=9 original_cases=35 float32_fields=190428 explicit_source_refusals=6' not in z.read('packaged/' + host + '.stdout'):
                    raise ValueError('packaged execution summary absent: ' + host)
            independent = read('edge-native/INDEPENDENT.json')['cases']
            if len(independent) != 30 or any(not r['cross_host_binary_exact'] for r in independent):
                raise ValueError('original cross-host/rational controls incomplete')
            originals = read('edge-original/COMPLETED.json')['results']
            if set(originals) != {'windows', 'linux'} or any(len(rows) != 30 or any(r['exit_code'] for r in rows) for rows in originals.values()):
                raise ValueError('original runtime edge captures incomplete')
            example = read('example/COMPLETED.json')['results']
            if set(example) != {'windows21', 'windows25', 'linux21'} or any(r['exit_code'] for r in example.values()):
                raise ValueError('actual example execution incomplete')
        producer = 'SKIP3: exact captured producer not supplied; saved evidence only'
        if args.jar:
            if not args.jar.is_file():
                producer = 'SKIP3: supply the exact captured producer at ' + str(args.jar)
            else:
                if pin(args.jar.read_bytes()) != profile['normal_producer']:
                    raise ValueError('supplied producer differs; do not replace its saved hash')
                producer = 'PASS_EXACT_CAPTURED_PRODUCER_INTEGRITY'
        result = dict(status='PASS_SAVED_NATIVE_GCTA_GRM_COMPONENT_EVIDENCE',
            current_source_inputs_checked=len(profile['source_inputs']), producer_integrity=producer,
            Java_executed_now=False, original_models_executed_now=False,
            saved_original_cases=35, saved_exact_float32_fields_per_JVM=190428,
            saved_explicit_no_estimate_refusals_per_JVM=6, saved_focused_tests_per_JVM=9,
            JVMs=3, GCTA_workflow_complete=False, catalog_credit=0, whole_goal_complete=False)
        (args.out / 'RESULT.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(result))
    except BaseException as error:
        (args.out / 'FAILURE.json').write_text(json.dumps(dict(status='FAIL2: NO COMPLETE COMPONENT VERIFICATION',
            error=repr(error), recovery='Retain diagnostics; restore exact qualified source/evidence/producer or use its historical owner. Retry NEW output; never refresh old hashes.'), indent=2) + '\n', encoding='utf-8')
        raise


if __name__ == '__main__':
    main()
