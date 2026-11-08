# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Demonstrate that the real JVM comparison rejects corrupt expected values, weights, dimensions and source-error admission."""
import argparse,copy,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();a.build=a.build.resolve();a.reference=a.reference.resolve();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_PALANTIR_COMPARISON_CORRUPTION_TEST',checks=[],implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};source=a.reference/'1.4.6/cases.json';original=json.loads(source.read_bytes());pins={str(p):pin(p) for p in [Path(__file__).resolve(),source,jar]}
        for mutation in ['coordinate','weight','signed-zero','nan-to-infinity','column-count','selected-bound','false-source-error']:
            home=out/mutation;home.mkdir();value=copy.deepcopy(original)
            def case(name):return next(v for v in value['cases'] if v['id']==name)
            if mutation=='coordinate':case('explicit-bound-4')['coordinates_bits'][0][0]='0000000000000000'
            elif mutation=='weight':case('explicit-bound-4')['weights_bits'][0]='0000000000000000'
            elif mutation=='signed-zero':
                c=case('signed-zero-propagation');matches=[(i,j) for i,row in enumerate(c['coordinates_bits']) for j,v in enumerate(row) if v in ['0000000000000000','8000000000000000']];assert matches;i,j=matches[0];c['coordinates_bits'][i][j]='8000000000000000' if c['coordinates_bits'][i][j]=='0000000000000000' else '0000000000000000'
            elif mutation=='nan-to-infinity':
                c=case('nonfinite-source-products');matches=[(i,j) for i,row in enumerate(c['coordinates_bits']) for j,v in enumerate(row) if int(v,16)&0x7fffffffffffffff>0x7ff0000000000000];assert matches;i,j=matches[0];c['coordinates_bits'][i][j]='7ff0000000000000'
            elif mutation=='column-count':case('explicit-bound-4')['output_columns']=1
            elif mutation=='selected-bound':case('explicit-bound-4')['actual_n_eigs']=3
            elif mutation=='false-source-error':case('explicit-bound-4')['source_status']='original-error'
            file=home/'corrupted.json';save(file,value);command=[str(JAVA21/'java.exe'),'-Xmx2g','-cp',str(jar),'org.jlinalg.cli.PalantirMultiscaleMatrix',str(file),str(home/'must-not-exist.json')];run=subprocess.run(command,capture_output=True,timeout=90);(home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr);assert run.returncode!=0 and not(home/'must-not-exist.json').exists();state['checks'].append(dict(mutation=mutation,command=command,exit_code=run.returncode,complete_comparison_rejected=True))
        assert all(pin(Path(n))==v for n,v in pins.items());state.update(status='PASS_SEVEN_PALANTIR_MULTISCALE_COMPARISON_CORRUPTIONS_REJECTED_ONLY',source_pins=pins,artifact=built['artifact'])
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
