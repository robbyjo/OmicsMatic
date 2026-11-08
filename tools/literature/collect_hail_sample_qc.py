# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate complete source controls and execute the actual original Hail0.2.139 sample_qc in the prepared private runtime."""
import argparse,copy,json,random,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux

def fixtures():
    def entry(a=None,dp=None,gq=None,filtered=False,phased=False):return dict(call=None if a is None else dict(alleles=a,phased=phased),depth=dp,genotype_quality=gq,filtered=filtered)
    pairs=[(a,b) for a in 'ACGTNM' for b in 'ACGTNM']+[('AC','AT'),('ACG','ATG'),('AC','AC'),('AC','GT'),('AC','ATC'),('ATC','AC'),('AC','AGGT'),('A','*'),('N','*'),('a','*'),('A','<DEL>'),('A','.A'),('A','A.'),('A','A[1:2['),('A','T]1:2]'),('A',''),('','A'),('a','g'),('A','R'),('A','-')]
    samples=['S2','S1','S2','alpha-\u03b1','with space','last'];rows=[]
    allele_sets=[['A','G','C'],['C','T'],['AC','ATC','A'],['ATC','AC'],['A','*','<DEL>'],['AC','AT','GT'],['N','M'],['a','g']]
    for i,alleles in enumerate(allele_sets):
        alt=min(len(alleles)-1,1);last=len(alleles)-1;rows.append(dict(alleles=alleles,entries=[entry([0,alt],i*2+1,10+i,phased=i%2==0),entry([alt,alt],None,20+i),entry([alt],-i,30+i),entry(None,50-i,None),entry([0],-2147483648 if i==0 else i,None,filtered=i%2==0),entry([alt,last],2147483647 if i==0 else 70+i,99,filtered=i%3==0)]))
    cases=[dict(id='multiallelic-haploid-diploid',samples=samples,rows=rows,depth_type='int32',gq_type='int32')];masked=copy.deepcopy(cases[0]);masked['id']='explicit-entry-filtering';
    for i,row in enumerate(masked['rows']):
        for j,e in enumerate(row['entries']):e['filtered']=(i+j)%3==0
    cases.append(masked)
    for id,dp,gq in [('absent-DP','absent','int32'),('non-int32-DP','float64','int32'),('absent-GQ','int32','absent'),('non-int32-GQ','int32','float64'),('no-DP-or-GQ','absent','absent')]:
        case=copy.deepcopy(cases[0]);case.update(id=id,depth_type=dp,gq_type=gq)
        for row in case['rows']:
            for e in row['entries']:
                if dp=='float64' and e['depth'] is not None:e['depth']=e['depth']+.25
                if gq=='float64' and e['genotype_quality'] is not None:e['genotype_quality']=e['genotype_quality']+.25
        cases.append(case)
    cases += [dict(id='zero-rows',samples=['A','B','C'],rows=[],depth_type='int32',gq_type='int32'),dict(id='zero-columns',samples=[],rows=[dict(alleles=['A','G'],entries=[]) for _ in range(3)],depth_type='int32',gq_type='int32'),dict(id='all-missing',samples=['A','B','C'],rows=[dict(alleles=['A','G'],entries=[entry(),entry(),entry()]) for _ in range(4)],depth_type='int32',gq_type='int32'),dict(id='all-filtered',samples=['A','B'],rows=[dict(alleles=['A','G'],entries=[entry([0,1],10,20,True),entry([1,1],30,40,True)]) for _ in range(3)],depth_type='int32',gq_type='int32')]
    source=random.Random(413277);random_rows=[]
    for i in range(50):
        alleles=source.choice(allele_sets);es=[]
        for j in range(17):
            ploidy=source.choice([1,2]);a=None if source.random()<.2 else [source.randrange(len(alleles)) for _ in range(ploidy)];es.append(entry(a,None if source.random()<.25 else source.randint(-20,1000),None if source.random()<.15 else source.randint(-10,300),source.random()<.2,source.choice([True,False])))
        random_rows.append(dict(alleles=alleles,entries=es))
    cases.append(dict(id='seeded-complete-matrix',samples=['S'+str(i) for i in range(17)],rows=random_rows,depth_type='int32',gq_type='int32'));return dict(schema_version=1,cases=cases,allele_pairs=pairs)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='SKIP3_ORIGINAL_HAIL_QC_REFERENCE_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False);sources=[Path(__file__).resolve(),ROOT/'tools/literature/hail_sample_qc_reference.py',ROOT/'tools/literature/supervise_hail_reference_linux.py'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in sources};state['source_pins']=pins
    for f in sources:(out/f.name).write_bytes(f.read_bytes())
    try:
        setup=json.loads((a.setup/'COMPLETED.json').read_bytes());assert setup['status']=='PASS_PRIVATE_ORIGINAL_HAIL139_SPARK353_REFERENCE_ONLY','Original framework preparation/health unavailable; keep dependent reference analysis skipped';save(out/'cases.json',fixtures());state['status']='FAIL2_ORIGINAL_HAIL_QC_REFERENCE_INCOMPLETE';command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(sources[2]),'--setup',linux(a.setup.resolve()),'--out',linux(out),'--worker',linux(out/sources[1].name)]
        with (out/'supervisor.stdout').open('xb') as first,(out/'supervisor.stderr').open('xb') as second:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));report=json.loads((out/'COMPLETED.json').read_bytes());process=json.loads((out/'original.process.json').read_bytes());assert r.returncode==0 and report['status']=='PASS_COMPLETE_ORIGINAL_HAIL_SAMPLE_QC_AND_ALLELE_TYPES_ONLY' and process['owned_live_after_cleanup']==[];assert all(pin(ROOT/n)==v for n,v in pins.items());report.update(source_pins=pins,implementation_credit=False,whole_goal_complete=False);save(out/'COMPLETED.json',report);print(report['status'],len(report['cases']),'complete matrices')
    except Exception as error:
        state['error']=repr(error);state['required_action']='Preserve this attempt and every earlier case. No partial batch is admitted. Inspect original-error.traceback, original.stderr and original.process.json. Correct the named runtime or fixture error and rerun in a NEW output folder. If the runtime cannot be installed or loaded, skip the dependent analysis (SKIP3).';save(out/'SUPERVISOR-ERROR.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state['required_action']+'\n\n'+repr(error)+'\n',encoding='utf8');print(state['status'],repr(error));return 3 if state['status'].startswith('SKIP3') else 2
    return 0

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full original QC control checks')
    raise SystemExit(main())
