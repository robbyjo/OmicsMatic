# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Additional original controls for factor levels, source value kinds and sparse shape boundaries."""
import argparse,copy,json,os,subprocess
from pathlib import Path
from prepare_signac_scan_request import ROOT,BASE,save
from validate_mashr_native import pin
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='extended-original-chromVAR-controls-in-progress'))
    try:
        base=json.loads((BASE/'chromvar-deviations-original-3/cases.json').read_bytes());baseline=base['datasets']['baseline'];datasets={'baseline':copy.deepcopy(baseline)};cases=[]
        def add(id,**options):
            c=dict(id=id,dataset='baseline',peak_sets=[[1,3,5,7,11],[2],[]],norm=False,group=None,group_levels=None,expectation=None,threshold=1);c.update(options);cases.append(c);return c
        for norm in [False,True]:
            id='group-one-peak'+('-norm' if norm else '');datasets[id]=dict(counts=[baseline['counts'][0]],background=[[1]*7]);add(id,dataset=id,peak_sets=[[1]],group=['a']*6,group_levels=['a'],norm=norm)
        group=['a']*3+['b']*3
        add('balanced-groups',group=group,group_levels=['a','b']);add('balanced-groups-norm',group=group,group_levels=['a','b'],norm=True)
        add('unused-balanced-level',group=group,group_levels=['a','b','unused']);add('unused-balanced-level-norm',group=group,group_levels=['a','b','unused'],norm=True)
        add('partial-missing-groups',group=['a','a','unmatched','b','b','unmatched'],group_levels=['a','b'])
        add('all-missing-groups',group=['unmatched']*6,group_levels=['a','b']);add('all-missing-groups-norm',group=['unmatched']*6,group_levels=['a','b'],norm=True)
        add('zero-factor-levels',group=['unmatched']*6,group_levels=[])
        datasets['one-background']=dict(counts=baseline['counts'],background=[[r[0]] for r in baseline['background']]);add('single-peak-one-background',dataset='one-background',peak_sets=[[2]])
        datasets['zero-background']=dict(counts=baseline['counts'],background=[[] for _ in range(20)]);add('single-peak-zero-background',dataset='zero-background',peak_sets=[[2]])
        datasets['one-cell-one-background']=dict(counts=[[sum(r)] for r in baseline['counts']],background=[[r[0]] for r in baseline['background']]);add('single-peak-one-cell-one-background',dataset='one-cell-one-background',peak_sets=[[2]])
        add('negative-threshold',expectation=[0]*20,threshold=-1)
        for kind in ['NA','NaN','Inf']:add('explicit-'+kind+'-expectation',expectation=[kind]+[.05]*19)
        datasets['identity-background']=dict(counts=baseline['counts'],background=[[i+1]*7 for i in range(20)])
        for kind in ['NA','NaN']:add('unselected-'+kind+'-expectation',dataset='identity-background',expectation=[.05]*19+[kind],peak_sets=[[1,3,5,7,11]])
        for id,scale in [('tiny-counts',1e-8),('large-counts',1e8)]:
            datasets[id]=dict(counts=[[v*scale+(0.25 if scale>1 else 0) for v in row] for row in baseline['counts']],background=baseline['background']);add(id,dataset=id,threshold=0)
        datasets['missing-zero-cell']=copy.deepcopy(baseline)
        for row in datasets['missing-zero-cell']['counts']:row[0]=0
        add('missing-zero-cell',dataset='missing-zero-cell',group=['unmatched','a','a','b','b','b'],group_levels=['a','b'],norm=True)
        datasets['zero-counts']=dict(counts=[[0]*6 for _ in range(20)],background=baseline['background']);add('zero-counts',dataset='zero-counts')
        add('unsorted-duplicate-peaks',peak_sets=[[11,5,1,11,3,5]]);add('empty-and-duplicate-peaks',peak_sets=[[1,1,3],[],[2]])
        resource=ROOT/'src/main/resources/org/jlinalg/omics/chromvar-deviations';names=['tools/literature/collect_chromvar_extended_controls.py','tools/literature/collect_chromvar_deviations.R']+[p.relative_to(ROOT).as_posix() for p in resource.rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        save(out/'cases.json',dict(datasets=datasets,cases=cases));protocol=out/'request.txt';protocol.write_text('\n'.join([str(BASE/'motif-reference-setup-1/R-lib'),str(BASE/'reference-setup-1/R-lib'),str(ROOT/'build/omics-sctransform-r'),str(out/'cases.json'),str(resource)])+'\n',encoding='utf8',newline='\n')
        env={k:v for k,v in os.environ.items() if not k.startswith('LC_') and k not in {'LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS'}};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/'tools/literature/collect_chromvar_deviations.R'),str(protocol)];r=subprocess.run(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/'stdout.log').write_bytes(r.stdout);(out/'stderr.log').write_bytes(r.stderr);save(out/'process.json',dict(command=argv,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')[-1800:]
        original=json.loads((out/'original.json').read_bytes());assert len(original['results'])==2*len(cases) and all(pin(ROOT/n)==v for n,v in sources.items());save(out/'COMPLETED.json',dict(status='captured-extended-original-chromVAR-deviation-controls',source_pins=sources,case_populations=len(cases),profile_case_protocols=len(original['results']),cases=pin(out/'cases.json'),original=pin(out/'original.json'),historical_whole_packages_executed=False,background_sampling_performed=False,user_analysis_admitted=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='captured-extended-original-chromVAR-deviation-controls',cases=len(cases),profiles=2)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_EXTENDED_CHROMVAR_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source controls')
    main()
