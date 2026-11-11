# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check current installer extraction, five no-install readmissions and real policy failures."""
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
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--previous',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();previous=a.previous.resolve();out=a.out.resolve();base=(ROOT/'build/literature-review/salmon-runtime').resolve();assert previous.is_relative_to(base) and out.is_relative_to(base) and not out.exists();out.mkdir()
    prior=json.loads((previous/'COMPLETED.json').read_bytes());assert len(prior['runs'])==5;homes={r['version']:r['home'] for r in prior['runs']}
    source=ROOT/'tools/literature/install_salmon.py';kit=ROOT/'src/main/resources/org/jlinalg/omics/salmon-runtime-setup';assert source.read_bytes()==(kit/'install_salmon.py').read_bytes()
    source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [source,Path(__file__),*sorted(p for p in kit.rglob('*') if p.is_file())]}
    remote=subprocess.check_output(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/python3','-I','-B','-c','import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-salmon-managed-current-",dir="/var/tmp"))']).decode().strip();assert re.fullmatch(r'/var/tmp/omicsmatic-salmon-managed-current-[A-Za-z0-9_-]+',remote);write(out/'OWNER.json',dict(owned_linux_root=remote))
    common=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/python3','-I','-B',linux(source),'--kit',linux(kit)]
    def call(label,version,home,extras,expected=0,report=None):
        report=out/label if report is None else report;command=[*common,'--profile','linux-x86_64-salmon-'+version,'--home',home,'--report',linux(report),*map(str,extras)]
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=1200)
        record=dict(label=label,version=version,home=home,report=report.relative_to(ROOT).as_posix(),command=command,expected_exit=expected,actual_exit=run.returncode);write(out/(label+'.json'),record);assert run.returncode==expected,(label,run.returncode)
        if expected==0:
            completed=json.loads((report/'COMPLETED.json').read_bytes());assert completed['status']=='COMPLETE_SETUP_ONLY' and completed['manifest']==pin(report/'manifest.json')
            for path in report.glob('*.process.json'):
                value=json.loads(path.read_bytes());assert value['exit_code']==0 and value['owned_live_before_cleanup']==[] and value['owned_live_after_cleanup']==[] and value['forced_cleanup'] is False
            health=json.loads((report/'health.json').read_bytes());assert len(health['audits'])==2 and sum(row['audit']['bootstrap_values'] for row in health['audits'])==6000
        elif label!='existing-report':
            failure=json.loads((report/'FAILURE.json').read_bytes());assert failure['exit_code']==expected and 'Required action:' in failure['required_action'] and ('SKIP3' if expected==3 else 'FAIL2') in (report/'WARNINGS.md').read_text(encoding='utf-8').splitlines()[0]
        print(label+':expected exit'+str(expected)+' passed',flush=True);return record
    fresh_home=remote+'/fresh-1.9.0';fresh=call('fresh-1.9.0','1.9.0',fresh_home,['--archive-dir',linux(base/'upstream-capture-1/1.9.0'),'--allow-install'])
    homes['1.9.0']=fresh_home
    def recheck(item):return call('recheck-'+item[0],item[0],item[1],[])
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:rechecks=list(pool.map(recheck,sorted(homes.items())))
    negative=[];negative.append(call('install-forbidden','1.8.0',remote+'/absent-install',[],3));absent=out/'absent-archives';absent.mkdir()
    negative.append(call('download-forbidden','1.8.0',remote+'/absent-download',['--archive-dir',linux(absent),'--allow-install'],3))
    corrupted=out/'corrupt-archives';corrupted.mkdir();(corrupted/'salmon-1.8.0_linux_x86_64.tar.gz').write_bytes(b'corrupt retained archive')
    negative.append(call('corrupt-archive','1.8.0',remote+'/absent-corrupt',['--archive-dir',linux(corrupted),'--allow-install'],2))
    existing=out/'recheck-1.8.0';before={p.relative_to(existing).as_posix():pin(p) for p in existing.rglob('*') if p.is_file()};negative.append(call('existing-report','1.8.0',homes['1.8.0'],[],2,existing));after={p.relative_to(existing).as_posix():pin(p) for p in existing.rglob('*') if p.is_file()};assert before==after
    assert all(pin(ROOT/name)==value for name,value in source_pins.items())
    write(out/'COMPLETED.json',dict(status='passed-current-Salmon-extraction-five-rechecks-and-real-authorization-integrity-controls',fresh_install=fresh,rechecks=rechecks,negative_controls=negative,source_pins=source_pins,
        original_health_quantifications=12,complete_health_quant_rows=480,complete_health_bootstrap_values=36000,existing_report_unchanged=True,scientific_runtime_admitted=False,user_analysis_admitted=False,Salmon_catalog_status='missing'))


if __name__=='__main__':main()
