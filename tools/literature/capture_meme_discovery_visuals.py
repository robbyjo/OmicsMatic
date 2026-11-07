# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture unobstructed warning-element images; keep the tested page and original UI screenshots unchanged."""
import argparse
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_htseq_catalog_ui import NODE,EDGE


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/meme-runtime') and not out.exists();out.mkdir()
    script=out/'capture.cjs'
    script.write_text('''const fs=require('fs'),path=require('path'),assert=require('assert/strict'),crypto=require('crypto');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=process.argv[4],out=process.argv[5];
(async()=>{const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
try {const page=await browser.newPage({viewport:{width:1440,height:1100}});
const response=await page.goto('http://127.0.0.1:8769/literature-review.html');const raw=await response.body();
assert(raw.equals(fs.readFileSync(path.join(root,'site/literature-review.html'))));
await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
const action=page.locator('#meme-discovery-required-actions');const text=await action.innerText();
assert.match(text,/SKIP3/);assert.match(text,/FAIL2/);assert.match(text,/Required action/);
const style='header, .skip-link { visibility:hidden !important; }';
await action.screenshot({path:path.join(out,'meme-actions-desktop-clear.png'),style});
await page.setViewportSize({width:390,height:950});await page.evaluate(()=>document.documentElement.dataset.theme='dark');
const layout=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth}));assert.deepEqual(layout,{viewport:390,document:390});
await action.screenshot({path:path.join(out,'meme-actions-mobile-dark-clear.png'),style});assert.equal(await action.innerText(),text);
fs.writeFileSync(path.join(out,'VISUAL.json'),JSON.stringify({status:'passed-element-capture-without-sticky-overlay',page_sha256:crypto.createHash('sha256').update(raw).digest('hex'),mobile_layout:layout,
note:'Sticky header and skip link are hidden only during element screenshots. Source page bytes, section text, actual browser checks and earlier screenshots are unchanged.'},null,2)+'\\n');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
''',encoding='utf8',newline='\n')
    server=None
    with (out/'server.log').open('xb') as log:
        try:
            server=subprocess.Popen([sys.executable,'-m','http.server','8769','--bind','127.0.0.1','--directory',str(ROOT/'site')],cwd=ROOT,stdin=subprocess.DEVNULL,stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW)
            for attempt in range(100):
                try:
                    with urllib.request.urlopen('http://127.0.0.1:8769/literature-review.html',timeout=1) as response:raw=response.read()
                    break
                except urllib.error.URLError:
                    if server.poll() is not None:raise RuntimeError('Owned visual server failed to start')
            else:raise RuntimeError('Owned visual server unavailable')
            assert raw==(ROOT/'site/literature-review.html').read_bytes()
            command=[str(NODE/'bin/node.exe'),str(script),str(NODE/'node_modules'),str(EDGE),str(ROOT),str(out)]
            r=subprocess.run(command,cwd=ROOT,capture_output=True,stdin=subprocess.DEVNULL,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW)
            (out/'stdout.log').write_bytes(r.stdout);(out/'stderr.log').write_bytes(r.stderr);save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
            save(out/'COMPLETED.json',dict(status='captured-unobstructed-MEME-warning-elements',page=pin(ROOT/'site/literature-review.html'),script=pin(script),capture_source=pin(Path(__file__)),source_page_modified=False))
        finally:
            if server is not None:server.terminate();server.wait(timeout=15)
    print('Captured clear desktop/mobile warning elements; original page and UI evidence preserved')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source-page checks')
    main()
