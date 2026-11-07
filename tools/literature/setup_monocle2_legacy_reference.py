# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Provision an isolated Linux R4.2/Monocle2 reference candidate; no user analysis admission."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');p.add_argument('--use-recorded-lock',action='store_true');a=p.parse_args()
    base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    action='Preserve this candidate and its exact lock/conda package identities. Inspect install stdout/stderr and exit status. Provision missing dependencies only where permitted into NEW private paths; otherwise skip dependent Monocle2 analyses. Installation/namespace health is not scientific workflow admission or a paper-version reconstruction. Never change existing R libraries or patch original source to bypass incompatibility.'
    if not a.allow_install:
        save(out/'RESULT.json',dict(status='SKIP3_MONOCLE2_LEGACY_RUNTIME_UNAVAILABLE',analysis_admitted=False,required_action=action));return 3
    manifest=out/'pixi.toml';manifest.write_text('''[workspace]
name = "private-monocle2-legacy-reference"
version = "0.1.0"
channels = ["conda-forge", "bioconda"]
platforms = ["linux-64"]

[dependencies]
r-base = ">=4.2,<4.3"
r-dplyr = "==1.1.4"
bioconductor-monocle = "==2.26.0"
r-leidenbase = {version = ">=0.1.9", channel = "bioconda"}
r-jsonlite = "*"
r-rhpcblasctl = "*"
''',encoding='utf8',newline='\n')
    if a.use_recorded_lock:
        recorded=ROOT/'tools/literature/monocle2-legacy-reference';profile=json.loads((recorded/'PROFILE.json').read_bytes())
        for name,wanted in profile['files'].items():assert name in ['pixi.toml','pixi.lock'] and pin(recorded/name)==wanted
        manifest.write_bytes((recorded/'pixi.toml').read_bytes());(out/'pixi.lock').write_bytes((recorded/'pixi.lock').read_bytes())
    bootstrap=ROOT/'build/literature-review/saige-runtime/pixi-bootstrap-1/pixi';assert bootstrap.is_file();wanted=pin(bootstrap)
    (out/'source-used.py').write_bytes(Path(__file__).read_bytes())
    save(out/'STARTED.json',dict(source_pin=pin(Path(__file__)),bootstrap_pin=wanted,manifest=pin(manifest),requested=dict(R='>=4.2,<4.3; exact candidate is bound by the solved lock',dplyr='1.1.4',monocle='2.26.0'),
        candidate_only=True,reason='R4.6/current dplyr fails original Monocle2 group_by_; original dplyr1.1.4 cannot compile against removed R4.6 C APIs. A new older-runtime candidate preserves original software and existing libraries.',analysis_admitted=False))
    for name in ['home','pixi-home','pixi-cache']:(out/name).mkdir()
    command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--user','root','--exec','/usr/bin/timeout','--signal=TERM','--kill-after=15','900',
        '/usr/bin/env','PIXI_NO_PATH_UPDATE=1','HOME='+linux(out/'home'),'PIXI_HOME='+linux(out/'pixi-home'),'PIXI_CACHE_DIR='+linux(out/'pixi-cache'),
        linux(bootstrap),'install','--manifest-path',linux(manifest),*(['--locked'] if a.use_recorded_lock else [])]
    with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:
        process=subprocess.Popen(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,env=dict(os.environ));save(out/'PROCESS-STARTED.json',dict(pid=process.pid,command=command));code=process.wait(timeout=960)
    save(out/'process.json',dict(command=command,pid=process.pid,exit_code=code));assert pin(bootstrap)==wanted
    record=dict(status='INSTALLED_MONOCLE2_LEGACY_CANDIDATE_NOT_VALIDATED' if code==0 else 'SKIP3_MONOCLE2_LEGACY_RUNTIME_UNAVAILABLE',analysis_admitted=False,required_action=action,installer_exit_code=code)
    lock=out/'pixi.lock'
    if lock.exists():record['lock']=pin(lock)
    save(out/'RESULT.json',record);(out/'WARNINGS.md').write_text('# '+record['status']+'\n\nREQUIRED ACTION: '+action+'\n',encoding='utf8',newline='\n');print(json.dumps(record),flush=True)
    return 0 if code==0 else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable private bootstrap/manifest checks')
    raise SystemExit(main())
