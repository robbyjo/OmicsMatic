# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual Windows export/platform SKIP3 and Linux permission/filesystem setup skips, including native process return codes."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_CELLPHONEDB_WINDOWS_AND_SETUP_PROTOCOLS',implementation_credit=False,whole_goal_complete=False,processes=[])
    def execute(name,command,expected):
        run=subprocess.run(list(map(str,command)),capture_output=True,timeout=90);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);record=dict(name=name,command=list(map(str,command)),expected_exit=expected,exit_code=run.returncode);save(out/(name+'.process.json'),record);state['processes'].append(record);assert run.returncode==expected,name+' unexpected actual native return code'
    try:
        built=json.loads((args.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};java=JAVA21/'java.exe';execute('export-kit',[java,'-cp',jar,'org.jlinalg.cli.CellPhoneDbSetupCliV2','--export',out/'exported-kit'],0);kit=out/'exported-kit';pins={p.relative_to(kit).as_posix():pin(p) for p in kit.rglob('*') if p.is_file()};assert (kit/'SETUP.md').is_file() and (kit/'original/cellphonedb-5.0.1.dist-info/LICENSE').is_file();execute('protected-existing-export',[java,'-cp',jar,'org.jlinalg.cli.CellPhoneDbSetupCliV2','--export',kit],2);assert all(pin(kit/n)==want for n,want in pins.items())
        base=json.loads((ROOT/'build/literature-review/cellphonedb-runtime/hash-seed-controls-1/request.json').read_bytes());base['analyses']=base['analyses'][:1];base.update(mode='RUN',runtime='unused-runtime.json')
        for label,exe in [('windows21',java),('windows25',Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'))]:
            request=json.loads(json.dumps(base));request['out']=str(out/(label+'-skipped'));save(out/(label+'-request.json'),request);execute(label+'-platform-skip3',[exe,'-cp',jar,'org.jlinalg.cli.CellPhoneDbWorkflowCliV2','--request',out/(label+'-request.json')],3);report=json.loads((out/(label+'-skipped/analysis-status.json')).read_bytes());assert not report['result_admitted'] and report['status']=='SKIP3_CELLPHONEDB_DEPENDENCIES_UNAVAILABLE' and not(out/(label+'-skipped/worker')).exists()
        def linux(p):return '/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix()
        for label,flags in [('install-permission-denied',[]),('case-insensitive-filesystem',['--allow-install'])]:
            destination=out/(label+'-setup');prefix=out/(label+'-never-prefix');command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(kit/'setup/prepare.py'),'--out',linux(destination),'--prefix',linux(prefix),'--micromamba',linux(ROOT/'build/literature-review/leafcutter-runtime/r-python-setup-1/micromamba'),*flags];execute(label,command,3);report=json.loads((destination/'COMPLETED.json').read_bytes());assert report['status'].startswith('SKIP3') and not prefix.exists() and (destination/'WARNINGS.md').is_file()
        assert all(pin(kit/n)==want for n,want in pins.items());state.update(status='PASS_SIX_CELLPHONEDB_EXPORT_PLATFORM_SETUP_PROTOCOLS_ONLY',artifact=built['artifact'],exported_source_members=len(pins))
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':raise SystemExit(main())
