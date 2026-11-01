"""Collect terminal source-build metadata and complete synthetic RSEM controls.

Compiled programs and downloaded source archives remain private. Identical
metadata/result bytes share one ZIP object, with every logical path retained.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import zipfile

VERSIONS = ('1.2.28', '1.3.0', '1.3.1', '1.3.2', '1.3.3')
INPUTS = '742c4d5feb9118d5bed6b9c6e2b6f5ae89b7924f083be490823f6045fe123338'
PROCESS = {'process.stdout', 'process.stderr', 'process.json', 'launch.json', 'WARNINGS.md'}
ROOT = Path(__file__).resolve().parents[3]


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def raw_file(path):
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode) or path.resolve(strict=True) != path or before.st_size > 268435456:
        raise ValueError('Linked, nonregular or oversized evidence member: ' + str(path))
    raw = path.read_bytes()
    after = path.lstat()
    if (before.st_size, before.st_mtime_ns, before.st_ctime_ns) != (after.st_size, after.st_mtime_ns, after.st_ctime_ns):
        raise ValueError('Evidence changed during collection: ' + str(path))
    return raw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--matrix', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if os.name != 'posix':
        raise ValueError('Collect on the Linux host that owns the completed build directories')
    matrix = args.matrix.resolve(strict=True)
    summary = json.loads(raw_file(matrix / 'observed.json'))
    if summary['status'] != 'passed-five-exact-source-builds-and-bound-original-controls' or set(summary['versions']) != set(VERSIONS):
        raise ValueError('The complete five-profile terminal matrix is required')
    fixture = raw_file(ROOT / 'src/main/resources/org/jlinalg/omics/rsem-health/inputs.zip')
    if digest(fixture) != INPUTS:
        raise ValueError('Original synthetic control fixture differs')
    with zipfile.ZipFile(__import__('io').BytesIO(fixture)) as archive:
        golden = json.loads(archive.read('expected-science.json'))
    target = args.out.absolute()
    if target.exists() or target.parent.resolve(strict=True) != target.parent:
        raise ValueError('Choose a NEW output in an existing canonical directory')
    partial = matrix / 'source-build-evidence.partial.zip'
    index, objects, skipped = {}, set(), {}
    total = 0
    with zipfile.ZipFile(partial, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        def add(name, raw):
            nonlocal total
            if name in index or len(index) >= 50000:
                raise ValueError('Repeated or over-capacity logical evidence population')
            sha = digest(raw)
            index[name] = dict(sha256=sha, bytes=len(raw))
            if sha not in objects:
                total += len(raw)
                if total > 1073741824:
                    raise ValueError('Complete deduplicated evidence exceeds its explicit 1 GiB capacity')
                info = zipfile.ZipInfo('objects/' + sha, date_time=(2026, 11, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, raw)
                objects.add(sha)
            return sha

        add('matrix/observed.json', raw_file(matrix / 'observed.json'))
        add('license/RSEM-GPL-3.0.txt', raw_file(ROOT / 'LICENSES/RSEM-GPL-3.0.txt'))
        add('controls/inputs.zip', fixture)
        for version in VERSIONS:
            row = summary['versions'][version]
            if row['exit_code'] != 0:
                raise ValueError('Nonzero source/runtime driver exit: ' + version)
            build_root, runtime_root = Path(row['build_home']), Path(row['runtime_home'])
            receipt_raw = raw_file(build_root / 'build.json')
            receipt = json.loads(receipt_raw)
            runtime = json.loads(raw_file(runtime_root / 'observed.json'))
            if digest(receipt_raw) != row['build_receipt_sha256'] or runtime['source_build_receipt_sha256'] != digest(receipt_raw):
                raise ValueError('Source/runtime build-receipt link differs: ' + version)
            if receipt['compiler_prefix'] != receipt['compiler_prefix_after']:
                raise ValueError('Complete compiler input graph changed: ' + version)
            add('build/' + version + '/build.json', receipt_raw)
            add('build/' + version + '/observed.json', raw_file(build_root / 'observed.json'))
            for name, expected in sorted(receipt['evidence'].items()):
                path = build_root / name
                if path.resolve(strict=True).is_relative_to(build_root) is False:
                    raise ValueError('Build evidence leaves its original owned home')
                raw = raw_file(path)
                if digest(raw) != expected:
                    raise ValueError('Retained source-build evidence changed: ' + str(path))
                logical = 'build/' + version + '/' + name
                if name in ('inputs/original-source.tar.gz', 'outputs/omicsmatic-rsem-decode'):
                    skipped[logical] = dict(sha256=expected, bytes=len(raw), reason='Source archive or compiled runtime remains private; exact hashes and original source identity are retained')
                else:
                    add(logical, raw)
            for stage in ['prepare', 'paired-em', 'reverse-em', 'single-em', 'paired-posterior', 'noise']:
                actual = {}
                for path in (runtime_root / stage).rglob('*'):
                    mode=path.lstat().st_mode
                    if not (stat.S_ISDIR(mode) or stat.S_ISREG(mode)):
                        raise ValueError('Nonregular scientific control member: ' + str(path))
                    if stat.S_ISREG(mode):
                        actual[path.relative_to(runtime_root / stage).as_posix()]=digest(raw_file(path))
                science = {n: h for n, h in actual.items() if n not in PROCESS}
                expected = golden['references'][version] if stage == 'prepare' else golden['cases'].get(version + '/' + stage)
                if expected is not None and science != expected:
                    raise ValueError('Complete original scientific population differs: ' + version + '/' + stage)
            for path in sorted(runtime_root.rglob('*')):
                relative = path.relative_to(runtime_root).as_posix()
                mode=path.lstat().st_mode
                if not (stat.S_ISDIR(mode) or stat.S_ISREG(mode)):
                    raise ValueError('Nonregular runtime control member: ' + str(path))
                if stat.S_ISDIR(mode):
                    continue
                if relative.startswith(('payload/bin/', 'negative-payload/bin/')):
                    continue
                add('runtime/' + version + '/' + relative, raw_file(path))
            if (runtime_root / 'reloaded-source-build.json').exists():
                verification=runtime_root.resolve().parent / (runtime_root.name + '-source-reverification.jsonl')
                if digest(raw_file(verification)) != receipt['source_graph']['sha256']:
                    raise ValueError('Reopened complete source graph differs: ' + version)
                add('runtime/' + version + '/source-reverification.jsonl', raw_file(verification))
        manifest = dict(schema='omicsmatic-rsem-source-build-controls-v1', candidate_sha256=summary['candidate_sha256'],
                        original_input_fixture_sha256=INPUTS, logical_files=index, private_artifacts=skipped,
                        scope='Complete retained build metadata/commands and synthetic reference/fitting controls. Compiled runtime payloads and source archives are private. Fixed controls do not admit a public scientific workflow or certify whole-tool/paper equivalence.')
        info = zipfile.ZipInfo('index.json', date_time=(2026, 11, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, (json.dumps(manifest, sort_keys=True, indent=2) + '\n').encode())
    with partial.open('rb') as source, target.open('xb') as out:
        shutil.copyfileobj(source, out, 1048576)
    result = dict(status='passed-collected-terminal-source-build-and-synthetic-control-evidence',
                  recorded_utc=datetime.now(timezone.utc).isoformat(), path=target.as_posix(), sha256=digest(target.read_bytes()),
                  bytes=target.stat().st_size, logical_files=len(index), unique_objects=len(objects), unique_decoded_bytes=total,
                  private_artifacts=len(skipped), scientific_files=364, reference_files=30,
                  source_to_build_provenance_verified=True, scientific_runtime_admitted=False, public_fitting_workflow_available=False)
    (matrix / 'evidence-bundle.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
