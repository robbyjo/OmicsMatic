# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run general and eight-role browser checks against the exact workspace page on localhost."""
import argparse
import concurrent.futures
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/htseq-runtime'
NODE=Path('C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/node')
EDGE=Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    server=None;log=(out/'server.log').open('xb')
    try:
        url='http://127.0.0.1:8768/literature-review.html'
        try:
            with urllib.request.urlopen(url,timeout=3) as response:served=response.read()
        except urllib.error.URLError:
            server=subprocess.Popen([sys.executable,'-m','http.server','8768','--bind','127.0.0.1','--directory',str(ROOT/'site')],
                cwd=ROOT,stdin=subprocess.DEVNULL,stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW)
            for attempt in range(100):
                try:
                    with urllib.request.urlopen(url,timeout=1) as response:served=response.read()
                    break
                except urllib.error.URLError:
                    if server.poll() is not None:raise RuntimeError('Owned local server failed')
            else:raise RuntimeError('Owned local server did not become ready')
        assert served==(ROOT/'site/literature-review.html').read_bytes(),'Localhost is not serving the exact workspace page'
        def run(item):
            name,script=item;folder=out/name;folder.mkdir();env=dict(os.environ,JLINALG_REVIEW_UI_OUT=str(folder))
            argv=[str(NODE/'bin/node.exe'),str(ROOT/'tools/literature'/script),str(NODE/'node_modules'),str(EDGE)]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                result=subprocess.run(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(name+'.process.json'),dict(command=argv,exit_code=result.returncode));assert result.returncode==0,name
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(run,[('general','check_review_ui.cjs'),('roles','check_htseq_roles_ui.cjs')]))
        save(out/'COMPLETED.json',dict(status='passed-local-general-and-eight-HTSeq-role-browser-checks',
            page=pin(ROOT/'site/literature-review.html'),general=pin(out/'general/ui-validation.json'),roles=pin(out/'roles/htseq-ui-verification.json'),
            server_started_by_this_capture=server is not None,remote_mutations=False,implementation_credit=False))
        print('Passed general catalog and8 primary HTSeq role browser checks')
    finally:
        if server is not None:
            server.terminate();server.wait(timeout=15)
        log.close()


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable local-page identity checks')
    main()
