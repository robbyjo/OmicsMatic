# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture unchanged chromVAR expectation/deviation cores with explicit prepared backgrounds."""
import argparse,copy,json,os,random,subprocess
from pathlib import Path
from prepare_signac_scan_request import ROOT,BASE,save
from validate_mashr_native import pin
RESOURCE=ROOT/'src/main/resources/org/jlinalg/omics/chromvar-deviations'
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='original-chromVAR-deviation-controls-in-progress'))
    try:
        names=['tools/literature/prepare_chromvar_deviation_sources.py','tools/literature/collect_chromvar_deviations.py','tools/literature/collect_chromvar_deviations.R']+[p.relative_to(ROOT).as_posix() for p in RESOURCE.rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        rng=random.Random(31197);counts=[[rng.randrange(9) for _ in range(6)] for _ in range(20)];background=[[rng.randrange(1,21) for _ in range(7)] for _ in range(20)];datasets={'baseline':dict(counts=counts,background=background)};cases=[]
        def add(id,**options):
            c=dict(id=id,dataset='baseline',peak_sets=[[1,3,5,7,11],[2],[]],norm=False,group=None,group_levels=None,expectation=None,threshold=1);c.update(options);cases.append(c);return c
        add('baseline');add('single-peak',peak_sets=[[2]]);add('empty-annotation',peak_sets=[[]]);add('duplicate-annotations',peak_sets=[[1,1,3,5,5]]);add('all-peaks',peak_sets=[list(range(1,21))]);add('norm-true',norm=True)
        add('grouped',group=['a','a','b','b','b','c'],group_levels=['a','b','c']);add('grouped-norm',group=['a','a','b','b','b','c'],group_levels=['a','b','c'],norm=True);add('unused-group-level',group=['a','a','b','b','b','c'],group_levels=['a','b','c','unused'])
        add('nonunit-expectation',expectation=[.1]*20);add('zero-expectation',expectation=[0]*20);add('zero-threshold',threshold=0,expectation=[0]*20);add('large-threshold',threshold=1e6)
        for id in ['identity-background','duplicate-background','one-background','zero-background','zero-background-index','large-background-index','one-cell-multiple-annotations','one-cell-single-annotation','one-cell-single-peak','one-peak','zero-cell','zero-peak','fractional-counts']:
            d=copy.deepcopy(datasets['baseline']);c=add(id,dataset=id)
            if id=='identity-background':d['background']=[[i+1]*7 for i in range(20)]
            elif id=='duplicate-background':d['background']=[[1]*7 for _ in range(20)]
            elif id=='one-background':d['background']=[[r[0]] for r in background]
            elif id=='zero-background':d['background']=[[] for _ in range(20)]
            elif id=='zero-background-index':d['background'][0][0]=0
            elif id=='large-background-index':d['background'][0][0]=21
            elif id.startswith('one-cell'):
                d['counts']=[[sum(r)] for r in counts]
                if id=='one-cell-single-annotation':c['peak_sets']=[[1,3,5]]
                elif id=='one-cell-single-peak':c['peak_sets']=[[2]]
            elif id=='one-peak':d['counts']=[counts[0]];d['background']=[[1]*7];c['peak_sets']=[[1]]
            elif id=='zero-cell':
                for row in d['counts']:row[0]=0
            elif id=='zero-peak':d['counts'][0]=[0]*6
            elif id=='fractional-counts':d['counts']=[[v+.25 for v in row] for row in counts]
            datasets[id]=d
        save(out/'cases.json',dict(datasets=datasets,cases=cases));protocol=out/'request.txt';protocol.write_text('\n'.join([str(BASE/'motif-reference-setup-1/R-lib'),str(BASE/'reference-setup-1/R-lib'),str(ROOT/'build/omics-sctransform-r'),str(out/'cases.json'),str(RESOURCE)])+'\n',encoding='utf8',newline='\n')
        env={k:v for k,v in os.environ.items() if not k.startswith('LC_') and k not in {'LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS'}};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/'tools/literature/collect_chromvar_deviations.R'),str(protocol)];r=subprocess.run(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/'stdout.log').write_bytes(r.stdout);(out/'stderr.log').write_bytes(r.stderr);save(out/'process.json',dict(command=argv,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')[-1800:]
        original=json.loads((out/'original.json').read_bytes());assert len(original['results'])==2*len(cases) and all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'COMPLETED.json',dict(status='captured-original-chromVAR-expectation-deviation-controls',source_pins=sources,case_populations=len(cases),profile_case_protocols=len(original['results']),source_profiles=['1.16.0-source','1.34.1-package'],cases=pin(out/'cases.json'),original=pin(out/'original.json'),historical_whole_packages_executed=False,background_sampling_performed=False,user_analysis_admitted=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='captured-original-chromVAR-expectation-deviation-controls',profiles=2,cases=len(cases))),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_CHROMVAR_DEVIATION_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original control checks')
    main()
