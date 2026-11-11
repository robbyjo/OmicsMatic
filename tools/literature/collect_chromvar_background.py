# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare unchanged source samplers with the actual public installed upstream interface and exact RNG states."""
import argparse,copy,json,os,subprocess
from pathlib import Path
from validate_mashr_native import pin
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='original-background-controls-in-progress'))
    try:
        reference=json.loads((BASE/'chromvar-deviations-original-3/cases.json').read_bytes())['datasets']['baseline']
        baseline=dict(counts=reference['counts'],bias=[.15+((i*13)%23)/40 for i in range(20)]);datasets=dict(baseline=baseline);cases=[]
        def add(id,**opts):
            c=dict(id=id,dataset='baseline',seed=17,niterations=7,w=.1,bs=10);c.update(opts);cases.append(c)
        add('baseline');add('seed-zero-one-iteration',seed=0,niterations=1,bs=2);add('different-seed',seed=2);add('wide-bandwidth',w=1,bs=7);add('narrow-bandwidth',w=.001,bs=6);add('upstream-defaults',niterations=50,bs=50)
        datasets['repeated-bias']=copy.deepcopy(baseline);datasets['repeated-bias']['bias']=[.25,.5]*10;add('repeated-bias',dataset='repeated-bias')
        datasets['constant-bias']=copy.deepcopy(baseline);datasets['constant-bias']['bias']=[.5]*20;add('constant-bias-source-failure',dataset='constant-bias')
        datasets['zero-peak']=copy.deepcopy(baseline);datasets['zero-peak']['counts'][0]=[0]*6;add('zero-peak-source-failure',dataset='zero-peak')
        datasets['constant-intensity']=copy.deepcopy(baseline);datasets['constant-intensity']['counts']=[[1]*6 for _ in range(20)];add('constant-intensity-source-failure',dataset='constant-intensity')
        datasets['undefined-GC']=copy.deepcopy(baseline);datasets['undefined-GC']['bias'][0]='NaN';add('undefined-GC-source-failure',dataset='undefined-GC')
        datasets['one-peak']=dict(counts=[baseline['counts'][0]],bias=[.25]);add('one-peak-source-failure',dataset='one-peak')
        for count in [2,3]:
            datasets[f'{count}-peaks']=dict(counts=baseline['counts'][:count],bias=baseline['bias'][:count]);add(f'{count}-peaks',dataset=f'{count}-peaks')
        datasets['fractional-counts']=dict(counts=[[x+.25 for x in r] for r in baseline['counts']],bias=baseline['bias']);add('fractional-counts',dataset='fractional-counts')
        resources=ROOT/'src/main/resources/org/jlinalg/omics/chromvar-background';names=['tools/literature/collect_chromvar_background.py','tools/literature/collect_chromvar_background.R','tools/literature/prepare_chromvar_background_sources.py']+[p.relative_to(ROOT).as_posix() for p in resources.rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            dest=out/'source-snapshot'/n;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((ROOT/n).read_bytes())
        save(out/'cases.json',dict(datasets=datasets,cases=cases));protocol=out/'protocol.txt';protocol.write_text('\n'.join(map(str,[BASE/'motif-reference-setup-1/R-lib',BASE/'reference-setup-1/R-lib',ROOT/'build/omics-sctransform-r',out/'cases.json',resources]))+'\n',encoding='utf8',newline='\n')
        env={k:v for k,v in os.environ.items() if not k.startswith('LC_') and k not in {'LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS'}};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/'tools/literature/collect_chromvar_background.R'),str(protocol)];r=subprocess.run(argv,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/'stdout.log').write_bytes(r.stdout);(out/'stderr.log').write_bytes(r.stderr);save(out/'process.json',dict(command=argv,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')[-1200:]
        original=json.loads((out/'original.json').read_bytes());assert len(original['results'])==3*len(cases);success=0;failed=0
        for case in cases:
            rows=[v for v in original['results'] if v['id']==case['id']];assert len(rows)==3
            filtered=[{k:v for k,v in row.items() if k!='profile'} for row in rows];assert filtered[0]==filtered[1]==filtered[2],case['id']
            row=rows[0];assert len(row['rng_before'])==len(row['rng_after'])==626
            if row['ok']:
                success+=1;assert row['rows']==len(datasets[case['dataset']]['counts']) and row['columns']==case['niterations']
                assert all(1<=int(v)<=row['rows'] for r in row['background'] for v in r)
            else:failed+=1;assert 'error' in row
        assert success>=9 and failed>=5 and all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'COMPLETED.json',dict(status='captured-exact-original-chromVAR-background-and-public-interface-controls',source_pins=sources,case_populations=len(cases),source_and_public_protocols=3*len(cases),successful_populations=success,retained_source_failures=failed,all_source_public_backgrounds_and_RNG_states_identical=True,RNG_state_integers=626,cases=pin(out/'cases.json'),original=pin(out/'original.json'),actual_installed_public_interface_executed=True,historical_whole_packages_executed=False,independent_native_RNG_replay=False,user_analysis_admitted=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='captured-exact-original-chromVAR-background-and-public-interface-controls',cases=len(cases),protocols=3*len(cases),success=success,retained_source_failures=failed)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ORIGINAL_BACKGROUND_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source background controls')
    main()
