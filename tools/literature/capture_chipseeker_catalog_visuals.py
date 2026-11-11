# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture warning text without fixed navigation overlays; preserve tested page bytes."""
import argparse
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.request
from setup_deseq2_reference import ROOT, pin, save
from validate_htseq_catalog_ui import NODE, EDGE


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ui', type=Path, required=True)
    ui = parser.parse_args().ui.resolve()
    assert ui.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and ui.exists()
    out = ui/'unobstructed-capture'
    out.mkdir(exist_ok=False)
    script = out/'capture.cjs'
    script.write_text('''const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
(async()=>{const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
try{const page=await browser.newPage({viewport:{width:1280,height:1100}});
const r=await page.goto('http://127.0.0.1:8769/literature-review.html');
assert((await r.body()).equals(fs.readFileSync(path.join(process.argv[4],'site/literature-review.html'))));
await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
const action=page.locator('#chipseeker-required-actions'),text=await action.innerText();
assert.match(text,/SKIP3/);assert.match(text,/FAIL2/);assert.match(text,/Required action/);
const style='header, .skip-link { visibility:hidden !important; }';
await action.screenshot({path:path.join(process.argv[5],'chipseeker-actions-desktop-clear.png'),style});
await page.setViewportSize({width:390,height:950});await page.evaluate(()=>document.documentElement.dataset.theme='dark');
assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),390);
await action.screenshot({path:path.join(process.argv[5],'chipseeker-actions-mobile-dark-clear.png'),style});
assert.equal(await action.innerText(),text);
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
''', encoding='utf8', newline='\n')
    server = None
    with (out/'server.log').open('xb') as log:
        try:
            server = subprocess.Popen([sys.executable, '-m', 'http.server', '8769', '--bind', '127.0.0.1',
                '--directory', str(ROOT/'site')], cwd=ROOT, stdin=subprocess.DEVNULL, stdout=log,
                stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            for attempt in range(100):
                try:
                    with urllib.request.urlopen('http://127.0.0.1:8769/literature-review.html', timeout=1) as response:
                        raw = response.read()
                    break
                except urllib.error.URLError:
                    if server.poll() is not None:
                        raise RuntimeError('Owned capture server failed')
            else:
                raise RuntimeError('Owned capture server unavailable')
            assert raw == (ROOT/'site/literature-review.html').read_bytes()
            command = [str(NODE/'bin/node.exe'), str(script), str(NODE/'node_modules'), str(EDGE), str(ROOT), str(ui/'chipseeker')]
            result = subprocess.run(command, cwd=ROOT, capture_output=True, stdin=subprocess.DEVNULL, timeout=120,
                creationflags=subprocess.CREATE_NO_WINDOW)
            (out/'stdout.log').write_bytes(result.stdout)
            (out/'stderr.log').write_bytes(result.stderr)
            save(out/'process.json', dict(command=command, exit_code=result.returncode))
            assert result.returncode == 0, result.stderr.decode('utf8', errors='replace')
            save(out/'COMPLETED.json', dict(status='captured-unobstructed-ChIPseeker-warning-elements',
                page=pin(ROOT/'site/literature-review.html'), script=pin(script), capture_source=pin(Path(__file__)),
                source_page_modified=False, note='Fixed header and skip link hidden only during captures; page bytes and section text unchanged.'))
        finally:
            if server is not None:
                server.terminate()
                server.wait(timeout=15)
    print('Captured clear desktop/mobile warning text; page bytes and original browser evidence preserved')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact page checks')
    main()
