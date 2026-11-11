# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run299 general,18 GENESIS,11 MTAG and12 scDRS browser checks; preserve earlier scope assertions."""
import argparse
import concurrent.futures
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_htseq_catalog_ui import NODE, EDGE


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True); a = p.parse_args(); out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/scdrs-runtime'); out.mkdir(parents=True)
    scripts = [('general', 'check_scdrs_general_review_ui.cjs'), ('genesis', 'check_scdrs_genesis_review_ui.cjs'), ('mtag', 'check_scdrs_mtag_review_ui.cjs'), ('scdrs', 'check_scdrs_catalog_ui.cjs')]
    names = [Path(__file__), ROOT / 'tools/literature/check_genesis_catalog_ui.cjs', ROOT / 'tools/literature/check_cistarget_general_review_ui.cjs', *[ROOT / 'tools/literature' / name for _, name in scripts],
             ROOT / 'site/literature-review.html', ROOT / 'site/literature-warnings.html', *[ROOT / 'docs/literature/omics-2023-2026' / name for name in ['papers.json', 'tool-inventory.json', 'summary.json']]]
    pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in names}; server = None
    with (out / 'server.log').open('xb') as log:
        try:
            url = 'http://127.0.0.1:8768/literature-review.html'
            try:
                with urllib.request.urlopen(url, timeout=3) as response:
                    served = response.read()
            except urllib.error.URLError:
                server = subprocess.Popen([sys.executable, '-m', 'http.server', '8768', '--bind', '127.0.0.1', '--directory', str(ROOT / 'site')], stdin=subprocess.DEVNULL, stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
                deadline = time.monotonic() + 30
                while time.monotonic() < deadline:
                    try:
                        with urllib.request.urlopen(url, timeout=1) as response:
                            served = response.read()
                        break
                    except urllib.error.URLError:
                        assert server.poll() is None; time.sleep(.1)
                else:
                    raise RuntimeError('Owned local browser server did not start')
            assert served == (ROOT / 'site/literature-review.html').read_bytes()

            def run(item):
                label, script = item; home = out / label; home.mkdir(); env = dict(os.environ, JLINALG_REVIEW_UI_OUT=str(home))
                command = [str(NODE / 'bin/node.exe'), str(ROOT / 'tools/literature' / script), str(NODE / 'node_modules'), str(EDGE)]
                with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
                    result = subprocess.run(command, cwd=ROOT, env=env, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=900, creationflags=subprocess.CREATE_NO_WINDOW)
                save(out / (label + '.process.json'), dict(command=command, exit_code=result.returncode)); assert result.returncode == 0, label + ': inspect retained browser logs'

            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
                list(pool.map(run, scripts))
            general = json.loads((out / 'general/ui-validation.json').read_bytes()); focused = json.loads((out / 'genesis/COMPLETED.json').read_bytes())
            assert general['status'] == 'passed' and len(general['checks']) == 299
            assert focused['status'] == 'PASS_GENESIS_FIVE_ROLES_AND_SNPRELATE_BROWSER_REQUIRED_ACTIONS' and len(focused['checks']) == 18
            mtag=json.loads((out / 'mtag/mtag-ui-verification.json').read_bytes());assert mtag['status']=='passed' and len(mtag['checks'])==11
            scdrs=json.loads((out/'scdrs/scdrs-ui-verification.json').read_bytes());assert scdrs['status']=='passed' and len(scdrs['checks'])==12
            assert all(pin(ROOT / name) == value for name, value in pins.items())
            save(out / 'COMPLETED.json', dict(status='PASS299_GENERAL18_GENESIS11_MTAG12_SCDRS_BROWSER_CHECKS', general_checks=299, preserved_genesis_scope_checks=18, focused_mtag_checks=11,focused_scdrs_checks=12,
                 all_predecessor_scientific_scope_assertions_unchanged=True, current_count_expectations_updated=True, source_pins=pins, general=pin(out / 'general/ui-validation.json'), focused=pin(out / 'genesis/COMPLETED.json'), mtag=pin(out / 'mtag/mtag-ui-verification.json'),
                 remote_mutations=False, whole_goal_complete=False)); print('PASS299_GENERAL18_GENESIS11_MTAG12_SCDRS_BROWSER_CHECKS', flush=True)
        finally:
            if server is not None:
                server.terminate(); server.wait(timeout=15)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete predecessor or new scientific-source/warning assertions')
    main()
