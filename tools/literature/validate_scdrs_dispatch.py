# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise staged scDRS root routes, preserved legacy routes and an original-source analysis."""
import argparse
import copy
import gzip
import json
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE, linux


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--root21', type=Path, required=True)
    p.add_argument('--root25', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    before = pin(canonical)
    live = ROOT / 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    root_before = pin(live)
    state = dict(status='FAIL2_STAGED_SCDRS_ROOT_VALIDATION', live_root_modified=False, new_catalog_credit=False)

    def run(label, command, expected=0):
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            result = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=900)
        save(out / (label + '.process.json'), dict(command=command, exit_code=result.returncode))
        assert result.returncode == expected, (label, result.returncode, (out / (label + '.stderr')).read_bytes()[-2000:])

    try:
        jars = {}
        for name, directory in [('21', a.root21), ('25', a.root25)]:
            record = json.loads((directory / 'COMPLETED.json').read_bytes())
            assert record['status'] == 'PASS_STAGED_SCDRS_ROOT_ALL_PREDECESSOR_SCIENTIFIC_BYTES_RETAINED'
            assert record['every_predecessor_entry_except_root_dispatch_byte_identical'] and record['source_before'] == root_before
            jars[name] = directory.resolve() / 'scdrs-root-supplement.jar'
            assert pin(jars[name]) == record['artifact']
        platforms = [
            ('windows21', ['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'], ';', str, '21'),
            ('windows25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'], ';', str, '25'),
            ('linux21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java'], ':', linux, '21')]
        preserved = []
        for name, java, sep, convert, tag in platforms:
            prefix = java + ['-cp', sep.join(convert(p) for p in [jars[tag], canonical]), 'org.jlinalg.cli.JLinAlgCli']
            run(name + '-root-help', prefix + ['--help'])
            assert b'scdrs-tools --help' in (out / (name + '-root-help.stdout')).read_bytes()
            for route in ['scdrs-tools', 'mtag-tools', 'genesis-tools']:
                run(name + '-' + route + '-help', prefix + [route, '--help'])
                assert route.encode() in (out / (name + '-' + route + '-help.stdout')).read_bytes()
                preserved.append((name, route))
        request = json.loads((BASE / 'typed-workflow-smoke-1/request.json').read_bytes())
        request['output_dir'] = linux(out / 'bundle')
        save(out / 'request.json', request)
        linux_prefix = platforms[2][1] + ['-cp', ':'.join(linux(p) for p in [jars['21'], canonical]), 'org.jlinalg.cli.JLinAlgCli', 'scdrs-tools']
        run('root-original-analysis', linux_prefix + ['--request', linux(out / 'request.json')])
        gold = BASE / 'original-cli-foundation-2/version-1.0.2/original-output'
        assert sorted(p.name for p in gold.iterdir()) == sorted(p.name for p in (out / 'bundle/original').iterdir())
        for expected in gold.iterdir():
            actual = out / 'bundle/original' / expected.name
            a_bytes, b_bytes = actual.read_bytes(), expected.read_bytes()
            if expected.name.endswith('.gz'): a_bytes, b_bytes = gzip.decompress(a_bytes), gzip.decompress(b_bytes)
            assert a_bytes == b_bytes, expected.name
        for name, java, sep, convert, tag in platforms:
            prefix = java + ['-cp', sep.join(convert(p) for p in [jars[tag], canonical]), 'org.jlinalg.cli.JLinAlgCli', 'scdrs-tools']
            run(name + '-saved-verify', prefix + ['--verify', convert(out / 'bundle')])
        run('root-export', ['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe', '-cp',
            ';'.join(map(str, [jars['21'], canonical])), 'org.jlinalg.cli.JLinAlgCli', 'scdrs-tools', '--export-runtime-helper', str(out / 'exported')])
        assert (out / 'exported/scdrs-workflow-lock.json').read_bytes() == (ROOT / 'src/main/resources/org/jlinalg/omics/scdrs/scdrs-workflow-lock.json').read_bytes()
        state.update(status='PASS_STAGED_SCDRS_ROOT_ORIGINAL_WORKFLOW_THREE_SAVED_JVMS_AND_LEGACY_ROUTES',
            root_help_checks=3, tool_help_checks=preserved, original_analyses=1,
            original_data_bytes_identical=True, saved_replays=3, export_passed=True,
            predecessor_scientific_bytes_changed=False, canonical_executable_unchanged=before)
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
    assert pin(canonical) == before and pin(live) == root_before
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable root/predecessor/original-output preservation checks')
    raise SystemExit(main())
