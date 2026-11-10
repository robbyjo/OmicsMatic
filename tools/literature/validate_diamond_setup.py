# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test packaged setup export, explicit original download and dependent alignment admission."""
import argparse, collections, copy, json, shutil, subprocess, traceback
from pathlib import Path
from validate_diamond_execution import ROOT, BASE, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    p.add_argument('--jar', required=True, type=Path)
    a = p.parse_args()
    out, jar = a.out.resolve(), a.jar.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    shutil.copy2(Path(__file__).resolve(), out/'validator-source.py')
    state = dict(status='FAIL2_DIAMOND_PACKAGED_SETUP', analysis_admitted=False, implementation_credit=False, whole_goal_complete=False)
    records = []

    def run(name, command, allowed):
        folder = out/name
        folder.mkdir()
        with (folder/'stdout').open('xb') as so, (folder/'stderr').open('xb') as se:
            r = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=180)
        save(folder/'process.json', dict(command=command, exit_code=r.returncode))
        assert r.returncode in allowed, (name, r.returncode)
        records.append(dict(case=name, exit_code=r.returncode))
        print(name, r.returncode, flush=True)
        return r.returncode

    try:
        exported = out/'exported'
        assert run('export', ['/usr/bin/java', '-jar', str(jar), '--export-runtime-setup', str(exported)], [0]) == 0
        for name in ['SOURCE-LOCK.json', 'setup_diamond_runtime.py', 'NOTICE.txt', 'LICENSE.txt']:
            assert pin(exported/('diamond-source-lock.json' if name == 'SOURCE-LOCK.json' else name)) == pin(ROOT/'src/main/resources/org/jlinalg/omics/diamond'/name)
        helper = ['/usr/bin/python3', str(exported/'setup_diamond_runtime.py')]
        assert run('no-download-authorization', helper+['--version', '2.0.0', '--out', str(out/'no-authorization-profile')], [3]) == 3
        bad = out/'bad-binary'
        bad.write_bytes(b'not an original DIAMOND binary\n')
        assert run('changed-existing-binary', helper+['--version', '2.0.0', '--out', str(out/'bad-profile'), '--binary', str(bad)], [3]) == 3
        assert run('existing-original-binary', helper+['--version', '2.1.8', '--out', str(out/'existing-profile'),
            '--binary', str(BASE/'source-intake-1/2.1.8/bin/diamond')], [0]) == 0
        download_code = run('explicit-original-download', helper+['--version', '2.0.0', '--out', str(out/'download-profile'), '--allow-download'], [0, 3])
        if download_code == 0:
            request = json.loads((BASE/'adapter-pilot-1/2.0.0/protein-sensitive-top50/request.json').read_bytes())
            request.update(out=str(out/'download-dependent-analysis'), runtime_profile=str(out/'download-profile/runtime-profile.json'))
            save(out/'download-request.json', request)
            assert run('download-dependent-alignment', ['/usr/bin/java', '-jar', str(jar), '--request', str(out/'download-request.json')], [0]) == 0
            assert run('download-dependent-verify', ['/usr/bin/java', '-jar', str(jar), '--verify', str(out/'download-dependent-analysis')], [0]) == 0
            actual = (out/'download-dependent-analysis/results/hits.tsv').read_text().splitlines()
            expected = (BASE/'reference-controls-2/2.0.0/protein-sensitive-top50/hits.tsv').read_text().splitlines()
            assert collections.Counter(actual) == collections.Counter(expected)
        for folder in ['no-authorization-profile', 'bad-profile']+(['download-profile'] if download_code == 3 else []):
            assert 'SKIP3' in (out/folder/'WARNINGS.md').read_text() and 'REQUIRED ACTION' in (out/folder/'WARNINGS.md').read_text()
            assert not (out/folder/'runtime-profile.json').exists()
        state.update(status='PASS_DIAMOND_PACKAGED_SETUP_AND_EXPLICIT_RUNTIME_DISPOSITIONS', records=records,
            explicit_download=download_code == 0, downloaded_runtime_alignment_validated=download_code == 0,
            download_analysis_skipped=download_code == 3, artifact=pin(jar),
            scope='Original runtime setup only, plus complete original-output comparison of a dependent synthetic alignment when the explicit download succeeds.')
    except Exception as error:
        state.update(error=repr(error), records=records)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:500], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original-runtime/download checks')
    raise SystemExit(main())
