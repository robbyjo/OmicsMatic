# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check the exact local rendered catalog with unchanged general and new CellChat browser tests."""
import argparse, concurrent.futures, json, os, subprocess, sys, urllib.error, urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_htseq_catalog_ui import NODE, EDGE


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cellchat-runtime')
    out.mkdir()
    scripts = [('general', 'check_cistarget_general_review_ui.cjs'), ('cellchat', 'check_cellchat_catalog_ui.cjs')]
    names = [Path(__file__).resolve(), *[ROOT/'tools/literature'/n for _, n in scripts],
        ROOT/'site/literature-review.html', ROOT/'site/literature-warnings.html']
    pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in names}
    inputs = {p.relative_to(ROOT).as_posix(): pin(p) for p in [ROOT/'docs/literature/omics-2023-2026'/n
        for n in ['papers.json', 'tool-inventory.json', 'summary.json']]}
    for name in pins:
        dest = out/'source-snapshot'/name
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes((ROOT/name).read_bytes())
    server = None
    log = (out/'server.log').open('xb')
    try:
        url = 'http://127.0.0.1:8768/literature-review.html'
        try:
            with urllib.request.urlopen(url, timeout=3) as r:
                served = r.read()
        except urllib.error.URLError:
            server = subprocess.Popen([sys.executable, '-m', 'http.server', '8768', '--bind', '127.0.0.1',
                '--directory', str(ROOT/'site')], stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                creationflags=subprocess.CREATE_NO_WINDOW)
            for _ in range(100):
                try:
                    with urllib.request.urlopen(url, timeout=1) as r:
                        served = r.read()
                    break
                except urllib.error.URLError:
                    assert server.poll() is None, 'Owned localhost server failed'
            else:
                raise RuntimeError('Owned localhost server did not become ready')
        assert served == (ROOT/'site/literature-review.html').read_bytes()

        def run(item):
            name, script = item
            home = out/name
            home.mkdir()
            env = dict(os.environ, JLINALG_REVIEW_UI_OUT=str(home))
            cmd = [str(NODE/'bin/node.exe'), str(ROOT/'tools/literature'/script), str(NODE/'node_modules'), str(EDGE)]
            with (out/(name+'.stdout')).open('xb') as so, (out/(name+'.stderr')).open('xb') as se:
                r = subprocess.run(cmd, cwd=ROOT, env=env, stdin=subprocess.DEVNULL, stdout=so, stderr=se,
                    timeout=600, creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(name+'.process.json'), dict(command=cmd, exit_code=r.returncode))
            assert r.returncode == 0, name+' failed; inspect retained stdout/stderr'

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(run, scripts))
        assert all(pin(ROOT/n) == v for n, v in {**pins, **inputs}.items())
        general = json.loads((out/'general/ui-validation.json').read_bytes())
        focused = json.loads((out/'cellchat/COMPLETED.json').read_bytes())
        assert general['status'] == 'passed' and len(general['checks']) == 299
        assert focused['status'] == 'PASS_CELLCHAT_BROWSER_CATALOG_AND_REQUIRED_ACTIONS' and len(focused['checks']) == 12
        save(out/'COMPLETED.json', dict(status='PASS_UNCHANGED299_GENERAL_AND12_CELLCHAT_BROWSER_CHECKS',
            general=pin(out/'general/ui-validation.json'), cellchat=pin(out/'cellchat/COMPLETED.json'),
            source_pins=pins, input_pins=inputs, server_started_by_this_capture=server is not None,
            remote_mutations=False, whole_goal_complete=False))
        print('Passed299 unchanged general and12 focused CellChat browser checks')
    finally:
        if server is not None:
            server.terminate()
            server.wait(timeout=15)
        log.close()


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact local browser checks')
    main()
