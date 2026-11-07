# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture actual FASTA/PFM scanning, sequence metadata and GC-matched enrichment before adapter code."""
import argparse
import copy
import json
import os
from pathlib import Path
import random
import subprocess
from validate_mashr_native import pin
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'

def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def recipes():
    rng=random.Random(790113);sequences={'chr1':''.join(rng.choices('ACGT',k=24000)),'chr2':''.join(rng.choices('ACGTN',weights=[.25,.2,.2,.25,.1],k=18000)),'chrN':'N'*1000};peaks=[]
    for chromosome in ['chr1','chr2']:
        for i in range(40):start=100+210*i;peaks.append(dict(chromosome=chromosome,start=start,end=start+100+10*(i%8)-1))
    pfms=[dict(id='M1',name='ACGT α',background=[.25]*4,rows=[[40,1,1,1],[1,40,1,1],[1,1,40,1],[1,1,1,40]]),dict(id='M2',name='CGCG',background=[.25]*4,rows=[[1,40,1,1],[1,1,40,1],[1,40,1,1],[1,1,40,1]]),dict(id='M3',name='rare',background=[.25]*4,rows=[[40,1,1,1]]*12)]
    base=dict(sequences=sequences,peaks=peaks,cells=['cell-'+str(i) for i in range(12)],counts=[[rng.randrange(0,8) for j in range(12)] for i in range(80)],pfms=pfms,crlf=False);datasets=dict(base=base);cases=[]
    def add(id,**kwargs):case=dict(id=id,dataset='base',query=list(range(12)),background=20,features_match=['GC.percent'],adjustment='BH',seed=7013);case.update(kwargs);cases.append(case)
    add('matched');add('all-background',background=None);add('explicit-background',background=list(range(20,80)));add('match-GC-width',features_match=['GC.percent','sequence.length']);add('empty-query',query=[]);add('repeated-query',query=[0,0,1,1,2,3,4,5,6,7,8,9]);add('zero-background',background=0);add('capped-background',background=10000);add('negative-background',background=-1);add('missing-query',query=[0,1,2,3,4,5,6,7,8,9,'missing']);add('single-query',query=[0]);add('match-count',features_match=['GC.percent','count']);add('none-adjustment',adjustment='none')
    for name in ['one-pfm','duplicate-names','fractional-pfm','edge-peaks','missing-chromosome','two-missing-chromosomes','only-missing-chromosomes','past-end','all-N-peak','ambiguity','crlf']:
        d=copy.deepcopy(base)
        if name=='one-pfm':d['pfms']=d['pfms'][:1]
        elif name=='duplicate-names':d['pfms'][1]['name']=d['pfms'][0]['name']
        elif name=='fractional-pfm':d['pfms'][0]['rows'][0]=[40.5,1.25,1.5,1]
        elif name=='edge-peaks':d['peaks'][0].update(start=1,end=4);d['peaks'][1].update(start=23900,end=24000)
        elif name in ['missing-chromosome','two-missing-chromosomes','only-missing-chromosomes']:
            indices=[79] if name=='missing-chromosome' else [78,79] if name=='two-missing-chromosomes' else range(80)
            for i in indices:d['peaks'][i]['chromosome']='chrAbsent'
        elif name=='past-end':d['peaks'][0].update(start=23900,end=24100)
        elif name=='all-N-peak':d['peaks'][0].update(chromosome='chrN',start=1,end=100)
        elif name=='ambiguity':d['sequences']['chr1']=d['sequences']['chr1'][:99]+'ACGTRYSWKMBDHVNacgtn-+'+d['sequences']['chr1'][120:]
        elif name=='crlf':d['crlf']=True
        datasets[name]=d;add(name,dataset=name)
    return dict(datasets=datasets,cases=cases,profiles=['1.6.0-source','1.9.0-source','1.14.0-source','1.17.1-package'])

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--profile',choices=['1.6.0-source','1.9.0-source','1.14.0-source','1.17.1-package']);p.add_argument('--case',choices=[c['id'] for c in recipes()['cases']]);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='original-FASTA-PFM-scan-and-enrichment-controls-in-progress'));process=None
    try:
        data=recipes();data['profiles']=[args.profile] if args.profile else data['profiles'];data['cases']=[c for c in data['cases'] if c['id']==args.case] if args.case else data['cases'];save(out/'cases.json',data);names=['tools/literature/collect_signac_scan_controls.py','tools/literature/collect_signac_scan_controls.R','tools/literature/prepare_signac_scan_sources.py','src/main/resources/org/jlinalg/omics/signac_motif_legacy_access.R','src/main/resources/org/jlinalg/omics/signac_scan_legacy_fasta.R'];names += [p.relative_to(ROOT).as_posix() for p in (ROOT/'src/main/resources/org/jlinalg/omics/signac-scan').rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in names:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        env=dict(os.environ)
        for key in list(env):
            if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS']:env.pop(key,None)
        env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/'tools/literature/collect_signac_scan_controls.R'),str(out/'cases.json'),str(out/'original.json'),str(out)]
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:process=subprocess.Popen(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);save(out/'RUNNING.json',dict(pid=process.pid,command=argv));code=process.wait(timeout=600)
        save(out/'original.process.json',dict(command=argv,exit_code=code));assert code==0;original=json.loads((out/'original.json').read_bytes());assert len(original['results'])==len(data['cases'])*len(data['profiles']) and all('replay_error' not in r for r in original['results']);assert all(pin(ROOT/n)==v for n,v in sources.items());success=sum('error' not in r['result']['value'] for r in original['results']);assert not any(c['id']=='matched' for c in data['cases']) or all('error' not in next(r['result']['value'] for r in original['results'] if r['profile']==profile and r['id']=='matched') for profile in data['profiles']),'A basic original scan/enrichment baseline failed; inspect scaffold/API errors before classifying source controls';save(out/'COMPLETED.json',dict(status='captured-original-FASTA-PFM-scan-and-matched-enrichment-controls',source_pins=sources,cases=pin(out/'cases.json'),original=pin(out/'original.json'),controls=len(original['results']),successful_controls=success,retained_source_failures=len(original['results'])-success,full_current_package_executed='1.17.1-package' in data['profiles'],historical_whole_packages_executed=False,historical_access_bridge=True,motifmatchr_version='1.34.0',profiles=data['profiles']));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='captured-original-FASTA-PFM-scan-controls',controls=len(original['results']),successful=success)),flush=True)
    except BaseException as error:
        if process is not None and process.poll() is None:subprocess.run(['taskkill.exe','/PID',str(process.pid),'/T','/F'],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=20,check=False);process.wait(timeout=20)
        save(out/'FAILURE.json',dict(status='FAILED_ORIGINAL_SCAN_CONTROLS',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original scanning controls')
    main()
