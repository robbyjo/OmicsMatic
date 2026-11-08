# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check real setup permission skips and connected dependent-analysis skips."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_PERMISSION_SKIP_CONTROLS',processes=[],implementation_credit=False,whole_goal_complete=False);pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/prepare_scfates_reference_linux.py',ROOT/'tools/literature/run_scfates_reference_linux.py']}
    def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()
    def execute(name,arguments):
        command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',*arguments];run=subprocess.run(command,capture_output=True,timeout=60);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);assert run.returncode==3,(name,run.stderr[-2000:]);state['processes'].append(dict(name=name,command=command,exit_code=run.returncode))
    try:
        for name,flags in [('install-forbidden',[]),('download-forbidden',['--allow-install'])]:
            home=out/name;execute(name,[linux(ROOT/'tools/literature/prepare_scfates_reference_linux.py'),'--out',linux(home),*flags]);report=json.loads((home/'COMPLETED.json').read_bytes());assert report['status']=='SKIP3_SCFATES_RUNTIME_UNAVAILABLE' and not report['processes'] and not(home/'dependencies').exists() and not(home/'wheels').exists() and (home/'WARNINGS.md').is_file()
        for name,setup in [('connected-skipped',out/'install-forbidden'),('missing-skipped',out/'absent-setup')]:
            home=out/name;execute(name,[linux(ROOT/'tools/literature/run_scfates_reference_linux.py'),'--setup',linux(setup),'--out',linux(home)]);report=json.loads((home/'COMPLETED.json').read_bytes());assert report['status']=='SKIP3_SCFATES_ORIGINAL_RUNTIME_UNAVAILABLE' and not report['processes'] and not(home/'original').exists() and (home/'WARNINGS.md').is_file()
        assert all(pin(ROOT/n)==v for n,v in pins.items());state.update(status='PASS_FOUR_SCFATES_PERMISSION_AND_CONNECTED_SKIP_CONTROLS_ONLY',source_pins=pins,software_installed=False,original_analysis_executed=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
