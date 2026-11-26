"""Replay every synthetic original-namespace ASCAT component reference via a built JAR.

No original ASCAT software, article text, or tumor data is downloaded or executed.
Use --controls to retain controlled failures alongside successful captures.
"""
import argparse
import copy
import csv
import gzip
import hashlib
import io
import json
import math
from pathlib import Path
import shutil
import struct
import subprocess
import sys


def pin(path):
    digest = hashlib.sha256()
    size = 0
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            digest.update(block)
            size += len(block)
    return dict(bytes=size, sha256=digest.hexdigest())


def save(path, value):
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2, ensure_ascii=False, allow_nan=False)
        stream.write('\n')


def number(bits):
    return struct.unpack('>d', bytes.fromhex(bits))[0]


def bits(value):
    return struct.pack('>d', value).hex()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', required=True, type=Path)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--controls', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    fixtures = root / 'src/test/resources/org/jlinalg/genetics/ascat'
    java, jar, out = args.java.resolve(strict=True), args.jar.resolve(strict=True), args.out.absolute()
    out.mkdir()  # Never overwrite prior successes or failures.
    producer = pin(jar)
    try:
        profile = json.loads((fixtures / 'SOURCE.json').read_bytes())
        tables = {}
        for name in ('input.tsv', 'segments.tsv', 'distance.tsv'):
            path = fixtures / (name + '.gz')
            require(pin(path) == profile['files'][path.name], 'Compressed fixture changed: ' + name)
            raw = gzip.decompress(path.read_bytes())
            identity = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
            require(all(p['fixtures'][name] == identity for p in profile['profiles']), 'Original reference identity differs')
            tables[name] = list(csv.DictReader(io.StringIO(raw.decode('ascii')), delimiter='\t'))
        save(out / 'CONTEXT.json', dict(producer=producer, java=str(java), jar=str(jar), fixture_profile=pin(fixtures / 'SOURCE.json'), controller=pin(Path(__file__).resolve())))
        calls = []

        def run(label, arguments, expected=0):
            command = [str(java), '-Xmx1g', '-jar', str(jar), 'ascat-objective', *map(str, arguments)]
            save(out / (label + '-COMMAND.json'), command)
            with (out / (label + '-stdout.log')).open('xb') as stdout, (out / (label + '-stderr.log')).open('xb') as stderr:
                child = subprocess.Popen(command, cwd=out, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
                save(out / (label + '-PROCESS.json'), dict(pid=child.pid))
                try:
                    code = child.wait(timeout=180)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait()
                    raise ValueError('FAIL2 owned native Java process exceeded 180 seconds: ' + label)
            record = dict(label=label, exit_code=code, expected_exit=expected)
            save(out / (label + '-RESULT.json'), record)
            calls.append(record)
            require(code == expected, 'Unexpected process result: ' + label)
            if expected:
                message = (out / (label + '-stderr.log')).read_text('utf-8')
                require(('SKIP3' if expected == 3 else 'FAIL2') in message and 'REQUIRED ACTION' in message, 'Missing prominent refusal/recovery message')
            print(label, code, flush=True)

        checked_cells = checked_segments = 0
        largest_absolute = largest_relative = 0.0
        requests, captures = {}, {}
        for name in profile['synthetic_cases']:
            original = [r for r in tables['input.tsv'] if r['case'] == name]
            cells = [r for r in tables['distance.tsv'] if r['case'] == name]
            expected_segments = [r for r in tables['segments.tsv'] if r['case'] == name]
            locus = []
            for row in original:
                pair = [number(row['logR_bits']), number(row['BAF_bits'])]
                locus.append([None if math.isnan(v) else v for v in pair])
            psi = list(dict.fromkeys(number(r['psi_bits']) for r in cells))
            rho = list(dict.fromkeys(number(r['rho_bits']) for r in cells))
            request = dict(schema_version=1, profile='native-ascat-objective-v1', input_domain='aligned-segmented-autosomal-logR-BAF', preparation='Complete synthetic original-namespace case ' + name + '; see the retained SOURCE.json. This is a numerical component, not a fitted tumor model.', loci=locus, gamma=number(cells[0]['gamma_bits']), psi=psi, rho=rho, capacity=dict(rows=1000000, cells=1000000, segment_cell_operations=100000000, output_bytes=134217728, wall_millis=1800000))
            path, capture = out / (name + '-request.json'), out / (name + '-capture')
            save(path, request)
            requests[name], captures[name] = request, capture
            run(name + '-capture', ['--request', path, '--out', capture])
            run(name + '-replay', ['--verify', capture])
            segments = list(csv.DictReader((capture / 'segments.tsv').open(encoding='ascii'), delimiter='\t'))
            require(len(segments) == len(expected_segments), 'Complete segment population differs')
            for want, got in zip(expected_segments, segments, strict=True):
                require(bits(float(got['logR'])) == want['logR_bits'] and bits(float(got['BAF'])) == want['BAF_bits'] and int(got['length']) == int(want['length']), 'Segment bits/run length differ: ' + name)
                checked_segments += 1
            with (capture / 'distance.tsv').open(encoding='ascii') as stream:
                actual = csv.DictReader(stream, delimiter='\t')
                for want, got in zip(cells, actual, strict=True):
                    expected, value = number(want['distance_bits']), float(got['distance'])
                    require(bits(float(got['psi'])) == want['psi_bits'] and bits(float(got['rho'])) == want['rho_bits'], 'Grid coordinate differs')
                    absolute = abs(expected - value)
                    require(math.isfinite(value) and absolute <= 2e-11 + 2e-12 * abs(expected), 'Full original numerical cell differs: ' + name)
                    largest_absolute, largest_relative = max(largest_absolute, absolute), max(largest_relative, absolute / max(1, abs(expected)))
                    checked_cells += 1
            audit = json.loads((capture / 'OUTPUT-AUDIT.json').read_bytes())
            require(audit['objective_admitted'] is True and audit['tumor_model_admitted'] is False and audit['original_runtime_executed'] is False and audit['native_family_credit'] == audit['upstream_family_credit'] == 0, 'Expanded scientific/family admission')

        control_count = 0
        if args.controls:
            run('missing-input', ['--request', out / 'missing-input.json', '--out', out / 'missing-input-capture'], 3)
            require(not (out / 'missing-input-capture').exists(), 'Missing input created an admitted attempt')
            control_count += 1
            for label, edit in [
                    ('invalid-BAF', lambda r: r.update(loci=[[0, 1.1]])),
                    ('all-missing', lambda r: r.update(loci=[[None, .5], [0, None]])),
                    ('operation-limit', lambda r: r['capacity'].update(segment_cell_operations=1)),
                    ('output-limit', lambda r: r['capacity'].update(output_bytes=1)),
                    ('wall-limit', lambda r: r['capacity'].update(wall_millis=1))]:
                request = copy.deepcopy(requests['basic'])
                edit(request)
                path, capture = out / (label + '.json'), out / (label + '-capture')
                save(path, request)
                run(label, ['--request', path, '--out', capture], 2)
                require(not (capture / 'COMPLETED.json').exists(), 'Controlled calculation admitted a complete result')
                require(not capture.exists() or (capture / 'FAILURE.json').is_file(), 'Created failed attempt lacks its retained failure record')
                control_count += 1
            for label in ('tampered-output', 'resealed-wrong-output', 'extra-directory'):
                capture = out / label
                shutil.copytree(captures['basic'], capture)
                if label == 'extra-directory':
                    (capture / 'unexpected').mkdir()
                else:
                    path = capture / 'distance.tsv'
                    lines = path.read_text('ascii').splitlines()
                    fields = lines[1].split('\t')
                    fields[2] = str(float(fields[2]) + 1)
                    lines[1] = '\t'.join(fields)
                    path.write_bytes(('\n'.join(lines) + '\n').encode('ascii'))
                    if label == 'resealed-wrong-output':
                        manifest_path = capture / 'manifest.json'
                        manifest = json.loads(manifest_path.read_bytes())
                        manifest['files']['distance.tsv'] = pin(path)
                        manifest_path.write_bytes((json.dumps(manifest, sort_keys=True, separators=(',', ':')) + '\n').encode('ascii'))
                        completed_path = capture / 'COMPLETED.json'
                        completed = json.loads(completed_path.read_bytes())
                        completed['manifest'] = pin(manifest_path)
                        completed_path.write_bytes((json.dumps(completed, sort_keys=True, separators=(',', ':')) + '\n').encode('ascii'))
                run(label, ['--verify', capture], 2)
                control_count += 1
        require(checked_cells == 58176 and checked_segments == 22 and pin(jar) == producer, 'Incomplete reference population or changed producing JAR')
        save(out / 'COMPLETED.json', dict(status='PASS_COMPLETE_NATIVE_ASCAT_OBJECTIVE_CAPTURES_REPLAY_AND_ORIGINAL_NUMERICAL_REFERENCES', producer=producer, captures=6, complete_saved_replays=6, original_distance_cells=checked_cells, original_segments=checked_segments, original_versions=['2.5.2', '3.2.0'], controlled_refusals=control_count, largest_absolute_error=largest_absolute, largest_scaled_relative_error=largest_relative, process_results=calls, full_model_qualified=False, new_family_credit=0, whole_goal_complete=False))
    except BaseException as error:
        if not (out / 'FAILURE.json').exists():
            save(out / 'FAILURE.json', dict(status='FAIL2_NO_COMPLETE_ASCAT_COMPONENT_VERIFICATION', error=repr(error), required_action='Retain all attempts and logs; correct the named source, process, numerical or capacity problem. Rebuild only when appropriate and retry NEW output paths. Do not use partial outputs or infer full ASCAT model admission.'))
        raise


if __name__ == '__main__':
    main()
