# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise committed-source export, new private source install and disabled-install SKIP3."""
import argparse,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();base=ROOT/'build/literature-review/dndscv-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();python=__import__('sys').executable;setup=base/'reference-setup-3';libs=[setup/'R-lib',*map(Path,json.loads((setup/'STARTED.json').read_bytes())['libraries'])];names=['export_dndscv_source.py','setup_dndscv_runtime.py','setup_dndscv_runtime.R',Path(__file__).name];pins={'tools/literature/'+n:pin(ROOT/'tools/literature'/n) for n in names};reports=[]
    def run(identifier,command,expected):
        r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=700);(out/(identifier+'.stdout')).write_bytes(r.stdout);(out/(identifier+'.stderr')).write_bytes(r.stderr);save(out/(identifier+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==expected,(identifier,r.stderr.decode('utf8',errors='replace')[-2000:]);reports.append(dict(id=identifier,exit_code=expected))
    exported=out/'export';run('source-export',[python,str(ROOT/'tools/literature/export_dndscv_source.py'),'--out',str(exported)],0)
    for identifier,allow in [('private-install',True),('installation-disabled',False)]:
        command=[python,str(ROOT/'tools/literature/setup_dndscv_runtime.py'),'--source-home',str(exported),'--rscript','C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--out',str(out/identifier)]
        for lib in libs:command.extend(['--dependency-library',str(lib)])
        if allow:command.append('--allow-install')
        run(identifier,command,0 if allow else 3);state=json.loads((out/identifier/'COMPLETED.json').read_bytes());assert state['preexisting_libraries_unchanged'] and not state['analysis_admitted'];assert state['status']==('installed-exact-original-dndscv-runtime-only' if allow else 'SKIP3_DNDSCV_REFERENCE_UNAVAILABLE')
        if not allow:assert not (out/identifier/'process.json').exists() and 'REQUIRED ACTION:' in (out/identifier/'WARNINGS.md').read_text(encoding='utf8')
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='passed-dndscv-source-export-private-install-and-disabled-install-skip',source_pins=pins,protocols=reports,analysis_admitted=False,whole_goal_complete=False));print('Passed exact source export, new private installation and installation-disabled SKIP3')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/setup checks')
    main()
