# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Probe unchanged privately retained KING programs and run the official example; no project adapter credit."""
import argparse, json, re, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT/'build/literature-review/plink-relatedness-runtime'
DISTRO = 'JLinAlg-Omics-SAIGE-20261026'


def linux(path):
    value = str(path.resolve()).replace('\\', '/')
    assert value[1:3] == ':/'
    return '/mnt/'+value[0].lower()+value[2:]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    intake = BASE/'king-source-intake-1'
    exe = {'windows': intake/'Windows-king-original/king.exe', 'linux': intake/'Linux-king-original/king'}
    inputs = [intake/'ex-original'/('ex.'+extension) for extension in ['bed', 'bim', 'fam']]
    before = {str(path): pin(path) for path in [*exe.values(), *inputs]}
    state = dict(status='FAIL2_ORIGINAL_KING_PLATFORM_PILOT', runtime_admitted=False,
        project_analysis_admitted=False, new_catalog_credit=False, whole_goal_complete=False)
    records = []
    try:
        def run(platform, mode, arguments):
            folder = out/(platform+'-'+mode)
            folder.mkdir()
            if platform == 'windows':
                command = [str(exe[platform]), *arguments]
            else:
                command = ['wsl.exe', '-d', DISTRO, '--cd', linux(folder), '--exec', '/usr/bin/env', '-i',
                    'PATH=/usr/bin:/bin', 'LANG=C', 'LC_ALL=C', linux(exe[platform]), *arguments]
            with (folder/'stdout').open('xb') as so, (folder/'stderr').open('xb') as se:
                result = subprocess.run(command, cwd=folder, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=240)
            record = dict(platform=platform, mode=mode, command=command, exit_code=result.returncode)
            save(folder/'process.json', record)
            text = (folder/'stdout').read_bytes().decode('utf8', errors='replace')+(folder/'stderr').read_bytes().decode('utf8', errors='replace')
            assert re.search(r'(?m)^KING 2\.3\.2\b', text), ('Original banner unavailable', platform, mode)
            records.append(record)
            print(platform, mode, result.returncode, flush=True)
            return folder, result.returncode

        for platform in ['windows', 'linux']:
            run(platform, 'no-input-banner', [])
            convert = linux if platform == 'linux' else lambda path: str(path.resolve())
            folder = out/(platform+'-official-kinship')
            _, code = run(platform, 'official-kinship', ['-b', convert(inputs[0]), '--kinship', '--cpus', '1', '--prefix', convert(folder/'original')])
            assert code == 0, ('Original kinship failed', platform)
            assert (folder/'original.kin').exists() and (folder/'original.kin0').exists()
        assert all(pin(Path(name)) == value for name, value in before.items())
        state.update(status='PASS_ORIGINAL_KING232_WINDOWS_LINUX_BANNERS_AND_OFFICIAL_KINSHIP_PILOT', records=records,
            original_analysis_executed=True, complete_input_binary_pins=before,
            producer=pin(Path(__file__)), scope='Private original-only research execution. Version/banner and output existence do not validate numerical fields, complete population, biological relationships, historical papers or a project adapter.')
    except Exception as error:
        state.update(error=repr(error), records=records)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:800], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original-input/runtime preservation checks')
    raise SystemExit(main())
