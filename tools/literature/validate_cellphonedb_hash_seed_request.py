# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual public request rejection for invalid exact Python hash-seed values."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_CELLPHONEDB_HASH_SEED_REQUEST_CONTROLS',processes=[],implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='PASS_COMPILED_CELLPHONEDB_HASH_SEED_DEVELOPMENT_ONLY';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};source=ROOT/'build/literature-review/cellphonedb-runtime/hash-seed-controls-1/request.json';base=json.loads(source.read_bytes());base['analyses']=base['analyses'][:1];base.update(mode='RUN',runtime='unused-runtime.json');pins={str(p):pin(p) for p in [Path(__file__).resolve(),source,jar]}
        for i,value in enumerate([-1,4294967296,1.5,True,'0','random',None]):
            request=dict(base,python_hash_seed=value,out=str(out/('never-analysis-'+str(i))));file=out/('request-'+str(i)+'.json');save(file,request);command=[str(JAVA21/'java.exe'),'-cp',str(jar),'org.jlinalg.cli.CellPhoneDbWorkflowCliV2','--request',str(file)];run=subprocess.run(command,capture_output=True,timeout=30);(out/(str(i)+'.stdout')).write_bytes(run.stdout);(out/(str(i)+'.stderr')).write_bytes(run.stderr);assert run.returncode==2 and not Path(request['out']).exists() and b'CELLPHONEDB FAIL2' in run.stderr and b'Runner revision2' in run.stderr;state['processes'].append(dict(command=command,exit_code=run.returncode,rejected_value=value,analysis_directory_created=False))
        assert all(pin(Path(n))==want for n,want in pins.items());state.update(status='PASS_SEVEN_PUBLIC_HASH_SEED_REQUEST_REJECTIONS_ONLY',source_pins=pins,artifact=built['artifact'])
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
