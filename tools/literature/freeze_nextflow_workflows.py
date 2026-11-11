# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete typed Nextflow controls without replacing earlier evidence."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_nextflow_setup import check_report


def read(path): return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())
def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__: raise RuntimeError('Never disable evidence checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    target = args.out.resolve()
    assert os.uname().sysname == 'Linux'
    assert target.is_relative_to((ROOT / 'docs/literature').resolve()) and not target.exists()
    base = ROOT / 'build/literature-review/nextflow-runtime'
    dev = base / 'typed-workflow-development-1'
    native = read(dev / 'native-matrix-2/COMPLETED.json')
    users = read(dev / 'user-matrix-2/COMPLETED.json')
    recovery = read(dev / 'recovery-matrix-2/COMPLETED.json')
    example = read(dev / 'example-1/COMPLETED.json')
    roles = read(dev / 'role-application-1/verification.json')
    assert native['status'] == 'passed-packaged-Nextflow-capture-session-and-admission-checks'
    assert len(native['runs']) == 3 and all(r['tests'] == 166 and r['passed'] + r['aborted'] == 166 for r in native['runs'])
    assert sorted(r['aborted'] for r in native['runs']) == [0, 4, 4]
    assert users['status'] == 'passed-36-original-engine-typed-user-workflow-controls' and users['cases'] == len(users['runs']) == 36
    assert all(c['actual_exit'] == c['expected_exit'] for r in users['runs'] for c in r['calls'])
    assert recovery['status'] == 'passed-three-engine-preflight-recovery-cache-tamper-profiles-and-late-diagnostics'
    assert len(recovery['runs']) == 3 and all(r['passed'] and r['cached_after_preflight_skip'] == 4 and r['repeated_tamper_rejections'] == 2 and r['named_profile_proven'] and r['late_failure_warning_prominent'] for r in recovery['runs'])
    assert example['status'] == 'passed-documented-example-export-run-and-verify' and example['counts_exact']
    assert roles['applied'] and roles['new_reviews_appended'] == 10 and roles['prior_reviews'] == roles['unrelated_prior_reviews_preserved'] == 775 and roles['total_reviews'] == 785
    artifact = native['artifact']
    assert artifact == users['artifact'] == recovery['artifact']
    assert pin(ROOT / artifact['path']) == {k: artifact[k] for k in ('bytes', 'sha256')} == example['artifact']
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    assert canonical == native['canonical_executable_unchanged'] == users['canonical_executable_unchanged']
    sources = {}
    for proof in (native, users, recovery):
        for name, value in proof['source_pins'].items():
            assert pin(ROOT / name) == value, name
            assert name not in sources or sources[name] == value
            sources[name] = value
    for path in [Path(__file__), ROOT / 'tools/literature/nextflow-development.init.gradle',
                 ROOT / 'examples/nextflow-workflow/create_example.py', ROOT / 'examples/nextflow-workflow/README.md',
                 ROOT / 'docs/literature/NEXTFLOW_DEVELOPMENT.md',
                 ROOT / 'tools/literature/curate_nextflow_uses.py', ROOT / 'tools/literature/nextflow-use-reviews.json',
                 ROOT / 'tools/literature/install_nextflow.py', ROOT / 'tools/literature/nextflow-managed-runtime-lock.json']:
        sources[path.relative_to(ROOT).as_posix()] = pin(path)
    setup = read(base / 'managed-setup-development-1/setup-validation-1/COMPLETED.json')
    locked = read(ROOT / 'tools/literature/nextflow-managed-runtime-lock.json')
    post = [check_report(Path(row['report']), Path(row['home']), row['profile'], locked,
                         pin(ROOT / 'tools/literature/install_nextflow.py')) for row in setup['rechecks']]
    assert len(post) == 3 and all(row['permission_departures'] == 130 for row in post)
    print('All three complete runtime/setup/helper populations remain unchanged.', flush=True)

    entries = {}
    hardlink_groups = {}
    def add(path, name):
        assert name not in entries and name and not name.startswith('/') and '..' not in Path(name).parts
        info = path.lstat()
        if stat.S_ISDIR(info.st_mode):
            entries[name + '/'] = (path, 'directory')
        elif stat.S_ISLNK(info.st_mode): entries[name] = (path, 'symlink')
        else:
            assert stat.S_ISREG(info.st_mode), ('Special streams are not read', path)
            entries[name] = (path, 'file')
            if info.st_nlink > 1: hardlink_groups.setdefault((info.st_dev, info.st_ino), []).append(name)
    def tree(folder, label):
        for path in sorted(folder.rglob('*')): add(path, label + '/' + path.relative_to(folder).as_posix())
    for name in sources: add(ROOT / name, 'source/' + name)
    for folder in ['native-matrix-2', 'user-matrix-2', 'recovery-matrix-2', 'example-1', 'role-application-1',
                   'standalone-matrix-1', 'standalone-matrix-2', 'standalone-matrix-3',
                   'recovery-matrix-1', 'package-3-sources']:
        tree(dev / folder, 'evidence/' + folder)
    for path in sorted(dev.glob('*.log')): add(path, 'retained-build-logs/' + path.name)
    data = ROOT / 'docs/literature/omics-2023-2026'
    for name in ['nextflow-dispatch-shared-source-compatibility.json', 'nextflow-use-shared-source-compatibility.json']:
        add(data / name, 'catalog-source-relationships/' + name)
    # Preserve all regular files, directory entries and link bytes; do not follow symlinks.
    target.mkdir()
    (target / '.gitattributes').write_text('* -text\n', encoding='ascii', newline='\n')
    archive = target / 'controls.zip'
    census = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for name, (path, kind) in sorted(entries.items()):
            raw = b'' if kind == 'directory' else os.readlink(path).encode('utf-8') if kind == 'symlink' else path.read_bytes()
            mode = {'file': 0o100644, 'directory': 0o40755, 'symlink': 0o120777}[kind]
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.create_system = 3; entry.external_attr = mode << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(entry, raw)
            census[name] = dict(kind=kind, bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as check:
        assert len(check.infolist()) == len(census) == len(set(check.namelist())) and check.testzip() is None
        for item in check.infolist():
            value = census[item.filename]; raw = check.read(item)
            assert len(raw) == value['bytes'] and hashlib.sha256(raw).hexdigest() == value['sha256']
            expected = {'file': stat.S_IFREG, 'directory': stat.S_IFDIR, 'symlink': stat.S_IFLNK}[value['kind']]
            assert stat.S_IFMT(item.external_attr >> 16) == expected
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    result = dict(schema_version=1, status='passed-typed-Nextflow-workflows-portable-bundles-and-recovery',
        recorded_utc=datetime.now(timezone.utc).isoformat(), exact_editions=['21.04.1', '22.10.6', '24.04.4'],
        artifact=artifact, canonical_executable_unchanged=canonical,
        source_pins=sources, archive=dict(path=archive.name, **pin(archive), entries=len(census)), archive_files=census,
        archived_hardlink_groups=[sorted(names) for names in hardlink_groups.values() if len(names) > 1],
        native_validation=native, user_workflow_validation=users, recovery_validation=recovery,
        example_validation=example, source_role_validation=roles, post_matrix_exact_runtime_verification=post,
        scope='Current packaged central nextflow-tools command, complete declared original inputs/workflow/data, lossless parameters, explicit configuration/profiles, pinned executables, original-engine task/trace completion, whole working-state resume, complete declared output family and standalone immutable blobs. Portable verification on Windows does not access the producer runtime or rerun biology.',
        control_population_note='The 36 controls reuse a tiny synthetic three-sequence count population across three exact editions. They are not 36 independent biological datasets. Additional recovery/profile/capacity controls reuse that population. Setup and earlier component controls are distinct evidence, not paper reconstructions.',
        retained_findings=['The first standalone matrix did not compile its generated test code; no scientific engine was run there.',
            'The second standalone matrix exposed a real original-request binding gap (21/22 controls passed). The corrected verifier rejects jointly changed request/parameters; all 22 pass on all three JVMs in standalone-matrix-3 and the current 166-case matrix.',
            'Recovery-matrix-1 chose an overly generous workspace limit. Its original run legitimately succeeded; the controller expected failure and stopped. That complete original result is retained. Recovery-matrix-2 uses an explicit late metadata-capacity failure and proves prominent FAIL2 warnings.'],
        claims=dict(typed_public_analysis_command_available=True, stable_session_resume_available=True,
                    portable_analysis_bundle_verification_available=True, complete_declared_population_verified=True,
                    whole_paper_reproduced=False, biological_accuracy_certified=False, whole_goal_complete=False,
                    released_or_deployed=False, catalog_promotion_pending=True),
        required_action='Use current-source nextflow-tools and NEXTFLOW_DEVELOPMENT.md. Linux Java plus separate exact admitted runtime/setup is required for execution; Windows supports portable verification. Install/download only where permitted, using explicit setup flags. Missing/forbidden runtime, dependency or capability: SKIP3 and skip dependent analysis. Invalid input, integrity, process, capacity, cancellation or finalization failure: FAIL2, no result admitted. Read latest WARNINGS.md and original logs, preserve all attempts and use NEW sessions for changed requests. Default verification rejects a failed latest attempt; inspect prior successes by explicit attempt path. Review the130 reported filesystem mode departures. Local process ownership does not supervise remote services/schedulers; undeclared external resources and unreported paper settings remain outside the declared contract. Hashes establish integrity, not authenticity. Cell Ranger remains excluded.')
    write(target / 'VALIDATION.json', result)
    (target / 'SHA256SUMS').write_text(''.join(pin(p)['sha256'] + '  ' + p.name + '\n' for p in [archive, target / 'VALIDATION.json']), encoding='ascii', newline='\n')
    print(json.dumps(dict(status=result['status'], archive_entries=len(census), archive_bytes=archive.stat().st_size,
                          focused_cases_per_JVM=166, typed_workflow_controls=36, reviewed_roles=10)), flush=True)


if __name__ == '__main__': main()
