# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run general and smr browser checks against the exact local catalog."""
import argparse
import concurrent.futures
import os
import json
import shutil
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.request
from setup_deseq2_reference import ROOT, pin, save
from validate_htseq_catalog_ui import NODE, EDGE


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--general-reference', type=Path)
    args=parser.parse_args();out = args.out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists()
    out.mkdir()
    sources=[Path(__file__).resolve(),ROOT/'tools/literature/check_review_ui.cjs',ROOT/'tools/literature/check_smr_review_ui.cjs',ROOT/'site/literature-review.html',ROOT/'site/literature-warnings.html']
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
    input_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [ROOT/'docs/literature/omics-2023-2026/papers.json',ROOT/'docs/literature/omics-2023-2026/tool-inventory.json',ROOT/'docs/literature/omics-2023-2026/summary.json']}
    for name in pins:
        snapshot=out/'source-snapshot'/name;snapshot.parent.mkdir(parents=True,exist_ok=True);snapshot.write_bytes((ROOT/name).read_bytes())
    server = None
    log = (out/'server.log').open('xb')
    try:
        url = 'http://127.0.0.1:8768/literature-review.html'
        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                served = response.read()
        except urllib.error.URLError:
            server = subprocess.Popen([sys.executable, '-m', 'http.server', '8768', '--bind', '127.0.0.1',
                '--directory', str(ROOT/'site')], cwd=ROOT, stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                creationflags=subprocess.CREATE_NO_WINDOW)
            for attempt in range(100):
                try:
                    with urllib.request.urlopen(url, timeout=1) as response:
                        served = response.read()
                    break
                except urllib.error.URLError:
                    if server.poll() is not None:
                        raise RuntimeError('Owned localhost server failed')
            else:
                raise RuntimeError('Owned localhost server did not become ready')
        assert served == (ROOT/'site/literature-review.html').read_bytes()

        def run(item):
            name, script = item
            folder = out/name
            folder.mkdir()
            env = dict(os.environ, JLINALG_REVIEW_UI_OUT=str(folder))
            command = [str(NODE/'bin/node.exe'), str(ROOT/'tools/literature'/script), str(NODE/'node_modules'), str(EDGE)]
            with (out/(name+'.stdout')).open('xb') as a, (out/(name+'.stderr')).open('xb') as b:
                result = subprocess.run(command, cwd=ROOT, env=env, stdin=subprocess.DEVNULL, stdout=a,
                    stderr=b, timeout=600, creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(name+'.process.json'), dict(command=command, exit_code=result.returncode))
            assert result.returncode == 0, name

        tasks=[('general','check_review_ui.cjs'),('smr','check_smr_review_ui.cjs')]
        reused=None
        if args.general_reference:
            previous=args.general_reference.resolve();completion=previous/'COMPLETED.json'
            if not completion.exists():
                completion=previous/'GENERAL-COMPLETED.json'
            proof=json.loads(completion.read_bytes())
            if completion.name=='GENERAL-COMPLETED.json':
                assert proof['status']=='passed-general-only-from-retained-failed-focused-run'
                assert proof['focused_run_passed'] is False
                for name,value in proof['source_pins'].items():assert pin(ROOT/name)==value
                for name,value in proof['input_pins'].items():assert pin(ROOT/name)==value
            assert proof['page']==pin(ROOT/'site/literature-review.html') and proof['warning_page']==pin(ROOT/'site/literature-warnings.html')
            assert proof['general']==pin(previous/'general/ui-validation.json')
            report=json.loads((previous/'general/ui-validation.json').read_bytes());assert report['status']=='passed' and len(report['checks'])==299
            shutil.copytree(previous/'general',out/'general');reused=dict(source=previous.relative_to(ROOT).as_posix(),completion_path=completion.relative_to(ROOT).as_posix(),completion=pin(completion),general=pin(previous/'general/ui-validation.json'),same_page_bytes=True)
            tasks=[('smr','check_smr_review_ui.cjs')]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(run,tasks))
        assert all(pin(ROOT/name)==value for name,value in pins.items()),'Source/page changed during browser capture; preserve and retry NEW paths'
        assert all(pin(ROOT/name)==value for name,value in input_pins.items()),'Catalog input changed during browser capture'
        save(out/'COMPLETED.json', dict(status='passed-local-general-and-smr-catalog-browser-checks',
            page=pin(ROOT/'site/literature-review.html'), warning_page=pin(ROOT/'site/literature-warnings.html'),
            general=pin(out/'general/ui-validation.json'),
            smr=pin(out/'smr/smr-ui-verification.json'),
            server_started_by_this_capture=server is not None, reused_general=reused, remote_mutations=False,source_pins=pins,input_pins=input_pins))
        print('Passed general and smr browser checks against exact workspace pages')
    finally:
        if server is not None:
            server.terminate()
            server.wait(timeout=15)
        log.close()


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable localhost page checks')
    main()
