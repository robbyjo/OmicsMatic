# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze exact native runtime admission, including the original failed report attempt."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import stat
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/stringtie-runtime'

def read(path): return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream: return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())
def rawpin(raw): return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
def portable(value): return {n: {k: v for k, v in row.items() if k != 'mode'} for n, row in value.items()}

def main():
    target = ROOT / 'docs/literature/stringtie-runtime-development'; assert not target.exists()
    matrix = BASE / 'native-runtime-matrix-2'; proof = read(matrix / 'COMPLETED.json'); parent = ROOT / 'docs/literature/stringtie-intake-setup-development'
    assert proof['status'] == 'passed-native-exact-StringTie-runtime-admission' and not (matrix / 'INCOMPLETE.json').exists() and not (matrix / 'FAILURE.json').exists()
    assert len(proof['new_classes']) == 9 and proof['unchanged_parent_entries'] == 19598
    assert all(p['passed'] == 1335 and p['skipped'] == 0 for p in proof['platform_validation']) and len(proof['platform_validation']) == 3
    assert proof['native']['fresh_original_health_fits'] == 8 and proof['native']['all_seven_editions'] and proof['native']['actual_permission_skip_uid'] == 65534
    assert not any(proof[k] for k in ['public_fitting_workflow_available', 'user_analysis_admitted', 'implementation_credit', 'released', 'pushed', 'deployed', 'whole_goal_complete'])
    assert all(pin(ROOT / n) == p for n, p in proof['source_pins'].items()) and all(pin(ROOT / n) == p for n, p in proof['dependency_pins'].items())
    assert pin(ROOT / proof['artifact']['path']) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    with zipfile.ZipFile(ROOT / proof['parent_artifact']['path']) as old, zipfile.ZipFile(ROOT / proof['artifact']['path']) as new:
        assert new.testzip() is None and len(new.namelist()) == len(set(new.namelist())) and len(old.namelist()) == 19598
        assert set(new.namelist()) - set(old.namelist()) == set(proof['new_classes']) and all(new.read(n) == old.read(n) for n in old.namelist())
        assert all(rawpin(new.read(n)) == p for n, p in proof['new_classes'].items())
    native = proof['native']['records']; assert len(native) == 13 and all(p['exit_code'] == 0 for p in native)
    for case in native:
        root = matrix / 'native-evidence' / case['label']
        if case['mode'] == 'admit':
            complete = read(root / 'COMPLETED.json'); manifest = read(root / 'manifest.json')
            assert complete['manifest'] == pin(root / 'manifest.json') and not complete['user_analysis_admitted'] and not manifest['user_analysis_admitted']
            assert not (root / 'INCOMPLETE.json').exists() and not (root / 'FAILURE.json').exists()
            actual = {'.': dict(kind='directory')}
            for file in root.rglob('*'):
                n = file.relative_to(root).as_posix()
                actual[n] = dict(kind='directory') if file.is_dir() else dict(kind='file', **pin(file))
            actual.pop('manifest.json'); actual.pop('COMPLETED.json'); assert actual == portable(manifest['files'])
            health = read(root / 'metadata/health.json'); assert health['transcripts'] == 4 and health['exons'] == 7 and health['genes'] == 3 and health['BAM']['records'] == 840
            assert not health['biological_accuracy_certified'] and health['BAM']['spliced_records'] == 120
            for process in root.rglob('process.json'):
                row = read(process); assert row['status'] == 'completed-process-only' and not row['owned_live_after_cleanup']
        elif case['mode'] == 'tamper':
            assert (matrix / (case['label'] + '.stdout')).read_text().strip() == 'EXPECTED_CHANGED_ADMISSION_REJECTED'
            declared = read(root / 'manifest.json')['files']['metadata/health.json']; assert pin(root / 'metadata/health.json') != {k: declared[k] for k in ['bytes', 'sha256']}
        else:
            assert (root / 'INCOMPLETE.json').exists() and not (root / 'COMPLETED.json').exists()
            state = read(root / 'FAILURE.json')['status']; assert state.startswith('SKIP3' if case['mode'] in ['missing-home', 'forbidden-home'] else 'FAIL2')
    # Keep the actual first original fit and its failed reporting boundary, without
    # modifying any files already present in that attempt or any runtime home.
    failed = BASE / 'native-runtime-matrix-1'; assert read(failed / 'FAILURE.json')['status'] == 'FAIL2_NO_RUNTIME_VALIDATION_ADMITTED'
    copy = failed / 'retained-native-failure'; assert not copy.exists()
    argv = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/python3', '-I', '-B', '-c',
        "import shutil;from pathlib import Path;source=Path('/var/tmp/omicsmatic-stringtie-native-20261103-1/work-health-1.3.0');target=Path('/mnt/d/projects/JLinAlg/build/literature-review/stringtie-runtime/native-runtime-matrix-1/retained-native-failure');assert source.is_dir() and not target.exists();shutil.copytree(source,target)"]
    process = subprocess.run(argv, cwd=ROOT, capture_output=True, timeout=60, check=False); assert process.returncode == 0, process.stderr
    assert (copy / 'health/estimation/transcripts.gtf').is_file() and (copy / 'INCOMPLETE.json').exists() and not (copy / 'COMPLETED.json').exists()
    assert 'Unsupported RSEM audit value' in read(copy / 'FAILURE.json')['error']
    data = ROOT / 'docs/literature/omics-2023-2026'; sums = dict(line.split('  ', 1)[::-1] for line in (data / 'SHA256SUMS').read_text().splitlines())
    assert len(sums) == 396 and all(pin(data / n)['sha256'] == p for n, p in sums.items())
    assert next(p for p in read(data / 'tool-inventory.json') if p['id'] == 'stringtie')['implementation_status'] == 'missing'
    sources = dict(proof['source_pins'])
    for n in ['docs/literature/STRINGTIE_RUNTIME_DEVELOPMENT.md', 'tools/literature/freeze_stringtie_runtime_native.py']: sources[n] = pin(ROOT / n)
    entries = {}
    def add(path, name):
        p = PurePosixPath(name); assert path.is_file() and not path.is_symlink() and name not in entries and not p.is_absolute() and '..' not in p.parts and '\\' not in name
        entries[name] = path
    def tree(folder, prefix):
        for path in sorted(folder.rglob('*')):
            assert not path.is_symlink()
            if path.is_file() and path.suffix not in ['.jar', '.part'] and 'javadoc' not in path.relative_to(folder).parts: add(path, prefix + '/' + path.relative_to(folder).as_posix())
    for n in sources: add(ROOT / n, 'source/' + n)
    for name in ['native-runtime-compile-1', 'native-runtime-matrix-1', 'native-runtime-matrix-2']: tree(BASE / name, 'evidence/' + name)
    for n in ['VALIDATION.json', 'SHA256SUMS']: add(parent / n, 'prior-intake-setup-checkpoint/' + n)
    for n in ['summary.json', 'tool-inventory.json', 'SHA256SUMS']: add(data / n, 'unchanged-catalog/' + n)
    target.mkdir(); archive = target / 'controls.zip'; members = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zipped:
        for name, path in sorted(entries.items()):
            raw = path.read_bytes(); info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = (stat.S_IFREG | 0o644) << 16
            zipped.writestr(info, raw); members[name] = rawpin(raw)
    with zipfile.ZipFile(archive) as zipped:
        assert zipped.testzip() is None and len(zipped.namelist()) == len(set(zipped.namelist())) == len(members)
        assert all(rawpin(zipped.read(n)) == p for n, p in members.items())
    assert archive.stat().st_size < 95000000 and all(pin(ROOT / n) == p for n, p in sources.items())
    result = dict(proof, schema_version=1, source_pins=sources, archive=dict(path=archive.name, **pin(archive), entries=len(members)), archive_files=members,
        parent_validation=pin(parent / 'VALIDATION.json'), parent_archive=pin(parent / 'controls.zip'), failed_first_native_fit_retained=True,
        scientific_sources_and_setup_resources_unchanged=True, third_party_executables_bundled=False,
        unchanged_catalog=dict(new_scoped_integrations=67, including_existing=70, missing=260, reviewed_uses=819, checksummed_files=396, stringtie_status='missing'),
        required_action='Read STRINGTIE_RUNTIME_DEVELOPMENT.md. Exact native runtime health admission is available; public typed fitting and portable complete-fit verification remain unfinished. Do not treat setup or synthetic health completion as a user-analysis result. SKIP3 means skip dependent analysis until a named requirement is available in a permitted environment; FAIL2 means no result and retained diagnostics. Preserve attempts and retry corrected requests into NEW paths. Cell Ranger remains excluded.')
    (target / 'VALIDATION.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    (target / 'SHA256SUMS').write_text(''.join(pin(target / n)['sha256'] + '  ' + n + '\n' for n in ['VALIDATION.json', 'controls.zip']), encoding='ascii', newline='\n')
    (target / '.gitattributes').write_text('controls.zip -text\nVALIDATION.json -text\nSHA256SUMS -text\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(status=result['status'], archive=result['archive'], source_pins=len(sources), fresh_original_fits=8, public_user_fitting=False)))

if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete runtime evidence checks')
    main()
