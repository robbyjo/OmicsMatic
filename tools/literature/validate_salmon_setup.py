# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise explicit offline Salmon installation and original setup health on five profiles."""
import argparse
import concurrent.futures
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import linux,pin
DISTRO='JLinAlg-Omics-SAIGE-20261026'


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    if not __debug__:raise RuntimeError('Never disable setup evidence checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();base=(ROOT/'build/literature-review/salmon-runtime').resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    source=ROOT/'tools/literature/install_salmon.py';kit=ROOT/'src/main/resources/org/jlinalg/omics/salmon-runtime-setup';assert source.read_bytes()==(kit/'install_salmon.py').read_bytes()
    paths=[source,Path(__file__),ROOT/'tools/literature/salmon-runtime-lock.json',*sorted(p for p in kit.rglob('*') if p.is_file())];sources={p.relative_to(ROOT).as_posix():pin(p) for p in paths}
    remote=subprocess.check_output(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/python3','-I','-B','-c','import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-salmon-managed-",dir="/var/tmp"))'],cwd=ROOT).decode().strip();assert re.fullmatch(r'/var/tmp/omicsmatic-salmon-managed-[A-Za-z0-9_-]+',remote)
    write(out/'OWNER.json',dict(owned_linux_root=remote,purpose='Exact runtime setup only; no user analysis or native host admission'))
    versions=['1.0.0','1.1.0','1.5.2','1.8.0','1.9.0']
    def check(version):
        report=out/('report-'+version);home=remote+'/home-'+version
        command=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/python3','-I','-B',linux(source),'--profile','linux-x86_64-salmon-'+version,'--home',home,'--report',linux(report),'--kit',linux(kit),'--archive-dir',linux(base/'upstream-capture-1'/version),'--allow-install']
        with (out/(version+'.stdout')).open('xb') as stdout,(out/(version+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=1200)
        receipt=dict(command=command,exit_code=r.returncode);write(out/(version+'.json'),receipt);assert r.returncode==0,(version,r.returncode)
        completion=json.loads((report/'COMPLETED.json').read_bytes());assert completion['status']=='COMPLETE_SETUP_ONLY' and completion['user_analysis_admitted'] is False and completion['manifest']==pin(report/'manifest.json')
        health=json.loads((report/'health.json').read_bytes());assert health['status']=='passed-two-original-optimizer-bias-and-bootstrap-health-controls' and health['version']==version and len(health['audits'])==2
        assert [p['audit']['bootstrap_values'] for p in health['audits']]==[2000,4000]
        for p in report.glob('*.process.json'):
            ledger=json.loads(p.read_bytes());assert ledger['exit_code']==0 and ledger['owned_live_after_cleanup']==[]
        assert not (report/'FAILURE.json').exists() and 'Required action:' in (report/'WARNINGS.md').read_text(encoding='utf-8')
        print(version+':exact offline home and two original optimizer/bias/bootstrap health controls pass',flush=True)
        return dict(version=version,home=home,report=report.relative_to(ROOT).as_posix(),completion=pin(report/'COMPLETED.json'),command=receipt,original_health_quantifications=2,bootstrap_values=6000,user_analysis_admitted=False)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:runs=list(pool.map(check,versions))
    assert all(pin(ROOT/name)==row for name,row in sources.items())
    write(out/'COMPLETED.json',dict(status='passed-five-exact-offline-Salmon-setups-and-original-synthetic-health',runs=runs,source_pins=sources,owned_linux_root=remote,
        original_health_quantifications=10,complete_health_quant_rows=400,complete_health_bootstrap_values=30000,native_scientific_host_admission_available=False,user_analysis_admitted=False,Salmon_catalog_status='missing'))


if __name__=='__main__':main()
