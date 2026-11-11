# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Explicit development-only XML::Simple preparation for original legacy MEME-ChIP."""
import argparse
import json
import os
from pathlib import Path
import re
import signal
import subprocess
from collect_meme_discovery_references import BASE, pin, write


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(BASE.resolve()) and not out.exists()
    out.mkdir(parents=True)
    record = dict(status='preparing-original-legacy-report-dependency', source=pin(Path(__file__)), steps=[],
                  analysis_run=False, implementation_credit=False)
    (out / 'PREPARATION-SOURCE.py').write_bytes(Path(__file__).read_bytes())

    def run(name, command, allow_failure=False):
        with (out / (name + '.log')).open('xb') as log:
            p = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                                 env=dict(PATH='/usr/bin:/bin', LC_ALL='C', DEBIAN_FRONTEND='noninteractive'), start_new_session=True)
            try:
                code = p.wait(timeout=600)
            except subprocess.TimeoutExpired:
                os.killpg(p.pid, signal.SIGTERM)
                try:
                    p.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(p.pid, signal.SIGKILL)
                    p.wait(timeout=10)
                raise RuntimeError('Preparation timeout: ' + name)
        record['steps'].append(dict(name=name, command=command, exit_code=code, log=pin(out / (name + '.log'))))
        write(out / 'PROGRESS.json', record)
        print(name + ': exit ' + str(code), flush=True)
        if code and not allow_failure:
            raise RuntimeError('Preparation failed; inspect ' + str(out / (name + '.log')))
        return code, (out / (name + '.log')).read_text()

    try:
        code, _ = run('before-import', ['/usr/bin/perl', '-MXML::Simple', '-e', 'print "$XML::Simple::VERSION\n"'], True)
        if code:
            if not (args.allow_install and args.allow_download):
                raise PermissionError('SKIP3: XML::Simple is unavailable and explicit permitted preparation is absent')
            _, plan = run('plan', ['/usr/bin/apt-get', '--simulate', '--no-install-recommends', 'install', 'libxml-simple-perl'])
            if not re.search(r'\b0 upgraded, [0-5] newly installed, 0 to remove\b', plan):
                raise PermissionError('SKIP3: preparation exceeds five new dependencies/no upgrades/no removals; use another permitted host')
            run('install', ['/usr/bin/apt-get', '-y', '--no-install-recommends', 'install', 'libxml-simple-perl'])
        run('after-import', ['/usr/bin/perl', '-MXML::Simple', '-e', 'print "$XML::Simple::VERSION\n"'])
        run('packages', ['/usr/bin/dpkg-query', '-W', '-f=${Package}\t${Version}\t${Status}\n',
                         'libxml-simple-perl', 'libxml-libxml-perl', 'libxml-namespacesupport-perl', 'libxml-sax-base-perl', 'libxml-sax-perl'])
        record.update(status='prepared-original-legacy-XML-Simple-not-analysis-admission')
        write(out / 'COMPLETED.json', record)
    except Exception as failure:
        record.update(status='legacy-report-dependency-unavailable-analysis-skipped', error=str(failure))
        write(out / 'FAILURE.json', record)
        (out / 'WARNINGS.md').write_text('SKIP3 — DEPENDENT ANALYSIS SKIPPED\n\n' + str(failure) +
            '\n\nRequired action: inspect retained package/import logs. Prepare XML::Simple only on a permitted host, or skip MEME-ChIP. Preserve this attempt and retry in a NEW area.\n')
        raise


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable dependency preparation bounds')
    main()
