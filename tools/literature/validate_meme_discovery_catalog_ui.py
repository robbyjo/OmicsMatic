# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check the exact local catalog in a browser, with classic MEME scope and prominent recovery."""
import argparse
import concurrent.futures
import os
import subprocess
import sys
import urllib.error
import urllib.request
from setup_deseq2_reference import ROOT,pin,save
from validate_htseq_catalog_ui import NODE,EDGE


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=__import__('pathlib').Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/meme-runtime') and not out.exists();out.mkdir()
    server=None;log=(out/'server.log').open('xb')
    try:
        url='http://127.0.0.1:8768/literature-review.html'
        try:
            with urllib.request.urlopen(url,timeout=3) as response:served=response.read()
        except urllib.error.URLError:
            server=subprocess.Popen([sys.executable,'-m','http.server','8768','--bind','127.0.0.1','--directory',str(ROOT/'site')],cwd=ROOT,
                stdin=subprocess.DEVNULL,stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW)
            for attempt in range(100):
                try:
                    with urllib.request.urlopen(url,timeout=1) as response:served=response.read()
                    break
                except urllib.error.URLError:
                    if server.poll() is not None:raise RuntimeError('Owned local server failed')
            else:raise RuntimeError('Owned local server did not become ready')
        assert served==(ROOT/'site/literature-review.html').read_bytes(),'Localhost does not serve the exact workspace page'
        def run(item):
            name,script=item;folder=out/name;folder.mkdir();env=dict(os.environ,JLINALG_REVIEW_UI_OUT=str(folder))
            command=[str(NODE/'bin/node.exe'),str(ROOT/'tools/literature'/script),str(NODE/'node_modules'),str(EDGE)]
            with (out/(name+'.stdout')).open('xb') as a,(out/(name+'.stderr')).open('xb') as b:
                result=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=600,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(name+'.process.json'),dict(command=command,exit_code=result.returncode));assert result.returncode==0,name
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(run,[('general','check_review_ui.cjs'),('meme','check_meme_discovery_review_ui.cjs')]))
        save(out/'COMPLETED.json',dict(status='passed-local-general-and-MEME-discovery-catalog-browser-checks',page=pin(ROOT/'site/literature-review.html'),
            general=pin(out/'general/ui-validation.json'),MEME=pin(out/'meme/meme-discovery-ui-verification.json'),server_started_by_this_capture=server is not None,remote_mutations=False))
        print('Passed general catalog and scoped MEME discovery browser checks')
    finally:
        if server is not None:server.terminate();server.wait(timeout=15)
        log.close()


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact localhost page checks')
    main()
