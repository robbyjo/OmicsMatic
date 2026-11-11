# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reuse the unchanged299 passing browser checks and retry only the wording-corrected DIAMOND check."""
import argparse, json, os, subprocess, sys, urllib.error, urllib.request
from pathlib import Path
from validate_diamond_catalog_ui import NODE, EDGE
from validate_diamond_execution import ROOT, BASE, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    prior = BASE/'catalog-ui-1'
    general = prior/'general/ui-validation.json'
    report = json.loads(general.read_bytes())
    assert report['status'] == 'passed' and len(report['checks']) == 299
    assert json.loads((prior/'general.process.json').read_bytes())['exit_code'] == 0
    names = ['site/literature-review.html', 'site/literature-warnings.html', 'tools/literature/check_cistarget_general_review_ui.cjs']
    assert all((prior/'source-snapshot'/n).read_bytes() == (ROOT/n).read_bytes() for n in names)
    script = ROOT/'tools/literature/check_diamond_catalog_ui.cjs'
    old = (prior/'source-snapshot/tools/literature/check_diamond_catalog_ui.cjs').read_bytes()
    assert old.replace(b"'historical'", b"'whole papers'") == script.read_bytes()
    focused = out/'diamond'
    focused.mkdir()
    env = dict(os.environ, JLINALG_REVIEW_UI_OUT=str(focused))
    server = None
    with (out/'server.log').open('xb') as log:
        try:
            url = 'http://127.0.0.1:8768/literature-review.html'
            try:
                with urllib.request.urlopen(url, timeout=3) as response:
                    served = response.read()
            except urllib.error.URLError:
                server = subprocess.Popen([sys.executable, '-m', 'http.server', '8768', '--bind', '127.0.0.1',
                    '--directory', str(ROOT/'site')], stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                    creationflags=subprocess.CREATE_NO_WINDOW)
                for _ in range(100):
                    try:
                        with urllib.request.urlopen(url, timeout=1) as response:
                            served = response.read()
                        break
                    except urllib.error.URLError:
                        assert server.poll() is None
                else:
                    raise RuntimeError('Owned local browser server did not become ready')
            assert served == (ROOT/'site/literature-review.html').read_bytes()
            command = [str(NODE/'bin/node.exe'), str(script), str(NODE/'node_modules'), str(EDGE)]
            with (out/'diamond.stdout').open('xb') as so, (out/'diamond.stderr').open('xb') as se:
                result = subprocess.run(command, env=env, stdin=subprocess.DEVNULL, stdout=so, stderr=se,
                    timeout=180, creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/'diamond.process.json', dict(command=command, exit_code=result.returncode))
            assert result.returncode == 0, 'Inspect retained focused browser logs'
            completed = json.loads((focused/'COMPLETED.json').read_bytes())
            assert completed['status'] == 'PASS_DIAMOND_BROWSER_CATALOG_AND_REQUIRED_ACTIONS' and len(completed['checks']) == 11
            save(out/'COMPLETED.json', dict(status='PASS_UNCHANGED299_GENERAL_AND11_DIAMOND_BROWSER_CHECKS',
                general=pin(general), general_capture=general.relative_to(ROOT).as_posix(),
                diamond=pin(focused/'COMPLETED.json'), focused_checks=11,
                source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), script, *[ROOT/n for n in names]]},
                prior_focused_assertion_failure_retained=True, recovery='Only assertion wording changed from historical to the displayed whole papers scope; rendered pages/data unchanged.',
                remote_mutations=False, whole_goal_complete=False))
            print('Passed299 unchanged general checks and11 corrected focused checks; initial focused failure retained')
        finally:
            if server is not None:
                server.terminate()
                server.wait(timeout=15)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable unchanged-page/general-capture checks')
    main()
