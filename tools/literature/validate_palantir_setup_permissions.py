# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual installation/download permission skips and connected original-analysis skips."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_PALANTIR_SETUP_SKIP_CONTROLS',processes=[],implementation_credit=False,whole_goal_complete=False);pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/prepare_palantir_reference_linux.py',ROOT/'tools/literature/run_palantir_multiscale_reference_linux.py']}
    def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()
    def execute(name,arguments):
        command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',*arguments];run=subprocess.run(command,capture_output=True,timeout=60);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);assert run.returncode==3,name+' must return actual process3';state['processes'].append(dict(name=name,command=command,exit_code=run.returncode))
    try:
        for name,flags in [('install-forbidden',[]),('download-forbidden',['--allow-install'])]:
            home=out/name;execute(name,[linux(ROOT/'tools/literature/prepare_palantir_reference_linux.py'),'--out',linux(home),*flags]);report=json.loads((home/'COMPLETED.json').read_bytes());assert report['status']=='SKIP3_PALANTIR_RUNTIME_UNAVAILABLE' and not report['processes'] and not(home/'dependencies').exists() and not(home/'editions').exists() and (home/'WARNINGS.md').is_file()
        execute('connected-reference-skipped',[linux(ROOT/'tools/literature/run_palantir_multiscale_reference_linux.py'),'--setup',linux(out/'install-forbidden'),'--out',linux(out/'connected-skipped')]);connected=json.loads((out/'connected-skipped/COMPLETED.json').read_bytes());assert connected['status'].startswith('SKIP3') and not connected['processes'];execute('missing-reference-skipped',[linux(ROOT/'tools/literature/run_palantir_multiscale_reference_linux.py'),'--setup',linux(out/'absent-setup'),'--out',linux(out/'missing-skipped')]);assert not json.loads((out/'missing-skipped/COMPLETED.json').read_bytes())['processes'];assert all(pin(ROOT/n)==v for n,v in pins.items());state.update(status='PASS_FOUR_PALANTIR_PERMISSION_AND_CONNECTED_SKIP_CONTROLS_ONLY',source_pins=pins,software_installed=False,original_analysis_executed=False)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
