# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run299 predecessor browser checks with two explicit count updates plus focused new package checks."""
import argparse
import concurrent.futures
import json
import os
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_htseq_catalog_ui import NODE,EDGE
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--reuse-general',type=Path,help='Reuse a passing299-check capture only while pages/data and every general assertion stay byte-identical');a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    scripts=[('general','check_snprelate_general_review_ui.cjs'),('snprelate','check_snprelate_catalog_ui.cjs')]
    names=[Path(__file__),ROOT/'tools/literature/check_cistarget_general_review_ui.cjs',*[ROOT/'tools/literature'/n for _,n in scripts],
        ROOT/'site/literature-review.html',ROOT/'site/literature-warnings.html',*[ROOT/'docs/literature/omics-2023-2026'/n for n in ['papers.json','tool-inventory.json','summary.json']]]
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in names};server=None
    if a.reuse_general:
        previous=a.reuse_general.resolve();assert previous.is_relative_to(BASE)
        general=json.loads((previous/'general/ui-validation.json').read_bytes());assert general['status']=='passed' and len(general['checks'])==299
        assert json.loads((previous/'general.process.json').read_bytes())['exit_code']==0
        render=json.loads((BASE/'catalog-render-2/COMPLETED.json').read_bytes())
        assert all(pin(ROOT/n)==value for n,value in render['rendered_files'].items()),'Page/data changes require a new general browser capture'
        derivation=json.loads((previous/'general/DERIVATION.json').read_bytes())
        assert pin(ROOT/'tools/literature/check_cistarget_general_review_ui.cjs')['sha256']==derivation['before_sha256']
        shutil.copytree(previous/'general',out/'general');scripts=scripts[1:]
    with (out/'server.log').open('xb') as log:
        try:
            url='http://127.0.0.1:8768/literature-review.html'
            try:
                with urllib.request.urlopen(url,timeout=3) as r:served=r.read()
            except urllib.error.URLError:
                server=subprocess.Popen([sys.executable,'-m','http.server','8768','--bind','127.0.0.1','--directory',str(ROOT/'site')],stdin=subprocess.DEVNULL,stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW)
                for _ in range(100):
                    try:
                        with urllib.request.urlopen(url,timeout=1) as r:served=r.read()
                        break
                    except urllib.error.URLError:assert server.poll() is None
                else:raise RuntimeError('Owned local browser server did not become ready')
            assert served==(ROOT/'site/literature-review.html').read_bytes()
            def run(item):
                label,script=item;home=out/label;home.mkdir();env=dict(os.environ,JLINALG_REVIEW_UI_OUT=str(home))
                command=[str(NODE/'bin/node.exe'),str(ROOT/'tools/literature'/script),str(NODE/'node_modules'),str(EDGE)]
                with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
                    r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=900,creationflags=subprocess.CREATE_NO_WINDOW)
                save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,label+' failed;inspect saved logs'
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(run,scripts))
            general=json.loads((out/'general/ui-validation.json').read_bytes());focused=json.loads((out/'snprelate/COMPLETED.json').read_bytes())
            assert general['status']=='passed' and len(general['checks'])==299 and focused['status']=='PASS_SNPRELATE_NINE_ROLES_BROWSER_AND_REQUIRED_ACTIONS'
            assert all(pin(ROOT/n)==v for n,v in pins.items())
            save(out/'COMPLETED.json',dict(status='PASS299_PREDECESSOR_AND_SNPRELATE_BROWSER_CHECKS',general_checks=299,focused_checks=len(focused['checks']),
                count_expectations_explicitly_updated=2,all_other_predecessor_assertions_unchanged=True,source_pins=pins,
                general_reused_from=str(a.reuse_general) if a.reuse_general else None,
                general=pin(out/'general/ui-validation.json'),focused=pin(out/'snprelate/COMPLETED.json'),remote_mutations=False,whole_goal_complete=False))
            print('PASS299_PREDECESSOR_AND_SNPRELATE_BROWSER_CHECKS',len(focused['checks']),flush=True)
        finally:
            if server is not None:server.terminate();server.wait(timeout=15)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full predecessor or focused browser assertions')
    main()
