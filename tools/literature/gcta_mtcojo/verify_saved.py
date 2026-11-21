#!/usr/bin/env python3
"""Verify saved mtCOJO development bytes/receipts; never execute a tool or model."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import math
import re
import struct
import zipfile


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True, help='NEW receipt directory')
    parser.add_argument('--restore', action='store_true', help='Restore exact logical evidence files')
    parser.add_argument('--jar', type=Path, help='Optional exact saved normal producer; not a rebuilt substitute')
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    root = Path(__file__).resolve().parents[3]
    q = root / 'docs/literature/gcta-mtcojo-development'
    try:
        profile = json.loads((q / 'PROFILE.json').read_bytes())
        for name, expected in profile['source_inputs'].items():
            path = PurePosixPath(name)
            assert not path.is_absolute() and '..' not in path.parts
            assert pin(root / name) == expected, 'Source differs: use the exact handover owner: ' + name
        assert pin(q / 'evidence.zip') == profile['archive']
        assert pin(q / 'INVENTORY.json') == profile['inventory']
        inventory = json.loads((q / 'INVENTORY.json').read_bytes())
        unique = {v['sha256'] for v in inventory.values()}
        with zipfile.ZipFile(q / 'evidence.zip') as archive:
            names = archive.namelist()
            assert len(names) == len(set(names)) == len(unique)
            assert set(names) == {'blobs/' + sha for sha in unique}
            for sha in unique:
                assert re.fullmatch('[0-9a-f]{64}', sha)
                assert hashlib.sha256(archive.read('blobs/' + sha)).hexdigest() == sha
            for name, expected in inventory.items():
                path = PurePosixPath(name)
                assert not path.is_absolute() and '..' not in path.parts
                raw = archive.read('blobs/' + expected['sha256'])
                assert len(raw) == expected['bytes']
                if args.restore:
                    destination = out / 'evidence' / name
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    with destination.open('xb') as stream:
                        stream.write(raw)

            def raw(name):
                return archive.read('blobs/' + inventory[name]['sha256'])

            def read(name):
                return json.loads(raw(name))

            def table(name):
                return [line.split() for line in raw(name).decode('utf-8').splitlines()]

            build = read('current/normal-gradle-2/COMPLETED.json')
            assert build['exit_code'] == 0
            assert sum(r['tests'] for r in build['reports'].values()) == 24
            assert all(r['failures'] == r['errors'] == r['skipped'] == 0 for r in build['reports'].values())
            assert build['JARs']['cli/omicsmatic-0.4.0.jar'] == profile['normal_producer']
            packaged = read('current/packaged-qualification-1/COMPLETED.json')
            assert packaged['jar'] == profile['normal_producer']
            assert packaged['payloads']['old_payloads_unchanged'] == 23533
            assert len(packaged['payloads']['changed_classes']) == 8
            assert len(packaged['payloads']['added']) == 7 and not packaged['payloads']['removed']
            assert set(packaged['results']) == {'windows21', 'windows25', 'linux21'}
            for host in packaged['results'].values():
                assert host['packaged_tests'] == 24 and host['exit_code'] == 0
                assert len(host['existing_capture_checks']) == 56
                assert all(r['exit_code'] == 0 for r in host['existing_capture_checks'])
            normal = read('current/normal-workflow-runs-1/COMPLETED.json')['results']
            assert set(normal) == {'windows', 'linux'}
            for host, rows in normal.items():
                assert len(rows) == 4
                for row in rows:
                    assert row['exit_code'] == row['verify_exit'] == 0 and row['all_original_table_tokens_equal']
                    current = table('current/normal-workflow-runs-1/' + host + '/' + row['name'] + '/worker/result.mtcojo.cma')
                    original = table('current/original-1.94.1-1/' + host + '/' + row['name'] + '/result.mtcojo.cma')
                    assert len(current) == 10001 and current == original
            controls = read('current/original-behavior-controls-1/COMPLETED.json')['results']
            assert set(controls) == {'windows', 'linux'}
            for host, rows in controls.items():
                assert len(rows) == 9
                by_name = {row['name']: row for row in rows}
                assert len(by_name) == 9 and all(row['exit_code'] == 0 for row in rows)
                for name in ['single-GSMR-low-IV', 'multi-GSMR-low-IV']:
                    row = by_name[name]
                    assert row['rg_fallback_announced'] and not row['all_fields_finite']
                    data = table('current/original-behavior-controls-1/' + host + '/' + name + '/result.mtcojo.cma')
                    assert any(not math.isfinite(float(value)) for r in data[1:] for value in r[8:11])
                for name in ['single-prevalence', 'multi-prevalence', 'multi-prevalence-swapped', 'single-supplied-low-IV']:
                    assert by_name[name]['all_baseline_table_tokens_equal']
                assert by_name['single-supplied-100']['GSMR_invoked'] and by_name['single-supplied-100']['all_baseline_table_tokens_equal']
                assert by_name['single-GSMR-decimal-LD-FDR']['accepted_decimal_FDR_as_zero']
                assert by_name['single-supplied-zero']['all_fields_finite']
            corpus_receipt = read('current/native-reference-corpus-1/CORPUS.json')
            assert corpus_receipt['original_fields'] == 300000 and corpus_receipt['cases'] == 10
        corpus_path = root / 'src/test/resources/org/jlinalg/genetics/mtcojo1941-conditional-originals.zip'
        assert pin(corpus_path) == corpus_receipt['resource']
        fields = 0
        with zipfile.ZipFile(corpus_path) as corpus:
            metadata = json.loads(corpus.read('CORPUS.json'))
            assert len(metadata['cases']) == 10 and metadata['variants'] == 10000
            for case in metadata['cases']:
                data = corpus.read(case['reference'])
                assert data[:8] == b'JMTREF01' and struct.unpack_from('<i', data, 8)[0] == 10000
                assert len(data) == 12 + 10000 * 104
                for values in struct.iter_unpack('<13d', data[12:]):
                    assert all(math.isfinite(v) for v in values)
                    for i in range(3):
                        assert values[7 + i] <= values[4 + i] <= values[10 + i]
                        fields += 1
        assert fields == 300000
        if args.jar:
            assert pin(args.jar) == profile['normal_producer'], 'Exact producer mismatch; do not substitute'
        result = dict(status='PASS_SAVED_MTCOJO_DEVELOPMENT_BYTES_AND_RECEIPTS_ONLY',
                      logical_files=len(inventory), unique_blobs=len(unique), saved_tests_per_JVM=24,
                      saved_JVMs=3, saved_prior_capture_checks=168, normal_original_table_comparisons=8,
                      original_behavior_runs=18, saved_original_conditional_fields=fields,
                      original_field_export_bounds_checked=fields, model_executed=False,
                      normal_producer_bytes_checked=bool(args.jar), new_catalog_credit=0,
                      full_mtcojo_qualification_complete=False, whole_goal_complete=False)
        (out / 'RESULT.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
        print(json.dumps(result))
    except BaseException as error:
        (out / 'FAILURE.txt').write_text(repr(error) + '\n', encoding='utf-8', newline='\n')
        raise


if __name__ == '__main__':
    main()
