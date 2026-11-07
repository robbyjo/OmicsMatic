# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare explicit >=1-read binary presence against actual original HTSeq interval sets."""
import argparse
import copy
import json
import random
import subprocess
from pathlib import Path
from setup_htseq_reference import ROOT,BASE,DISTRO,linux,pin


def iv(chrom,start,end,strand='+'):return dict(chromosome=chrom,start=start,end=end,strand=strand)
def feature(name,chrom,start,end,strand='+'):return dict(id=name,interval=iv(chrom,start,end,strand))
def read(cell,*blocks):return dict(cell=cell,blocks=list(blocks))


def specifications():
    peaks=[feature('A','chr1',10,30),feature('B','chr1',20,40,'-'),feature('zero','chr2',0,10)]
    basic=dict(id='unstranded-literal-axes',stranded=False,features=peaks,peaks=['B','zero','A'],cells=['cell Z','cell α','empty'],
        reads=[read('cell α',iv('chr1',12,18)),read('cell α',iv('chr1',12,18)),read('cell α',iv('chr1',22,25)),
            read('cell Z',iv('chr1',35,38),iv('unknown',1,3)),read('cell Z'),read('cell Z',iv('chr1',40,41))])
    cases=[basic,
        dict(id='stranded-blocks',stranded=True,features=peaks,peaks=['A','B','zero'],cells=['A','B'],
            reads=[read('A',iv('chr1',22,25)),read('A',iv('chr1',22,25,'-')),read('B',iv('chr1',22,25),iv('chr1',22,25,'-'))]),
        dict(id='adjacent-and-duplicate-feature-records',stranded=False,
            features=[feature('left','chr1',0,10),feature('right','chr1',10,20),feature('dup','chr1',8,12),feature('dup','chr1',8,12)],
            peaks=['right','dup','left'],cells=['second','first'],reads=[read('first',iv('chr1',9,11)),read('second',iv('chr1',10,11)),read('second',iv('chr1',9,10))]),
        dict(id='empty-reads',stranded=False,features=peaks,peaks=['zero','A','B'],cells=['empty B','empty A'],reads=[]),
        dict(id='empty-features',stranded=False,features=[],peaks=[],cells=['cell'],reads=[read('cell',iv('chr1',0,10)),read('cell')]),
        dict(id='empty-cells-and-reads',stranded=False,features=[],peaks=[],cells=[],reads=[]),
        dict(id='exact-long-coordinates-and-unicode',stranded=False,
            features=[feature('\ue000','huge',9007199254740993,9007199254741003),feature('\U00010000','huge',9007199254741003,9007199254741013)],
            peaks=['\U00010000','\ue000'],cells=['cell β'],reads=[read('cell β',iv('huge',9007199254741002,9007199254741004)),read('cell β',iv('huge',9007199254741013,9007199254741014))]),
        dict(id='repeated-read-presence-not-depth',stranded=False,features=[feature('peak','chr1',0,10)],peaks=['peak'],cells=['cell','zero'],reads=[read('cell',iv('chr1',1,3)) for _ in range(150)])]
    bad=copy.deepcopy(basic);bad['id']='zero-length-feature-original-refusal';bad['features'].append(feature('bad','chr1',5,5));bad['peaks'].append('bad');cases.append(bad)
    bad=copy.deepcopy(basic);bad['id']='zero-length-query-original-refusal';bad['reads'].append(read('cell Z',iv('chr1',5,5)));cases.append(bad)
    rng=random.Random(92451)
    for n in range(24):
        fs=[]
        for i in range(8):
            start=rng.randrange(200);fs.append(feature('p'+str(i),rng.choice(['chr1','chr2']),start,start+rng.randrange(1,41),rng.choice('+-')))
        fs.append(copy.deepcopy(fs[0]));axis=['p'+str(i) for i in range(8)];rng.shuffle(axis);cells=['cell2','cell0','cell1','zero'];reads=[]
        for i in range(24):
            blocks=[]
            for j in range(rng.randrange(4)):
                start=rng.randrange(220);blocks.append(iv(rng.choice(['chr1','chr2','unknown']),start,start+rng.randrange(1,31),rng.choice('+-')))
            reads.append(read(rng.choice(cells[:3]),*blocks))
        cases.append(dict(id='seeded-'+str(n),stranded=n%2==0,features=fs,peaks=axis,cells=cells,reads=reads))
    return cases


COLLECTOR='''import json,sys,HTSeq
assert HTSeq.__version__=='2.1.2'
def interval(row):return HTSeq.GenomicInterval(row['chromosome'],row['start'],row['end'],row['strand'])
output=[]
for case in json.load(open(sys.argv[1])):
 record=dict(case=case,trace=[],matrix=None)
 try:
  g=HTSeq.GenomicArrayOfSets('auto',stranded=case['stranded'])
  for f in case['features']:g[interval(f['interval'])]+=f['id']
  pairs=set();overlap=0
  for r in case['reads']:
   labels=set();steps=[]
   for block in r['blocks']:
    original=[]
    for span,features in g[interval(block)].steps():
     original.append(dict(interval=dict(chromosome=span.chrom,start=span.start,end=span.end,strand=span.strand),features=sorted(features)))
     labels.update(features)
    steps.append(original)
   if labels:overlap+=1
   for peak in labels:pairs.add((case['peaks'].index(peak),case['cells'].index(r['cell'])))
   record['trace'].append(dict(cell=r['cell'],features=sorted(labels),original_steps=steps))
  record['matrix']=dict(peaks=case['peaks'],cells=case['cells'],entries=sorted(pairs),processed_reads=len(case['reads']),reads_with_overlap=overlap,reads_without_overlap=len(case['reads'])-overlap)
 except Exception as error:
  record['source_error']=dict(type=type(error).__name__,message=str(error))
 output.append(record)
json.dump(dict(schema_version=1,HTSeq=HTSeq.__version__,cases=output,scope='New explicit binary-presence reduction over actual original interval steps; not recovered paper-exact author code or original htseq-count.'),open(sys.argv[2],'w'),indent=2,ensure_ascii=False)
'''


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
    save(out/'cases.json',specifications());(out/'original.py').write_text(COLLECTOR,encoding='utf8',newline='\n')
    runtime=json.loads((BASE/'reference-inventory-recovery-2/COMPLETED.json').read_bytes())
    argv=['wsl.exe','-d',DISTRO,'--','/usr/bin/env','PYTHONHASHSEED=0','PYTHONDONTWRITEBYTECODE=1','PYTHONNOUSERSITE=1','OPENBLAS_NUM_THREADS=1','OMP_NUM_THREADS=1',runtime['python'],'-s','-B',linux(out/'original.py'),linux(out/'cases.json'),linux(out/'RESULT.json')]
    result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180)
    (out/'original.stdout').write_bytes(result.stdout);(out/'original.stderr').write_bytes(result.stderr);save(out/'original.process.json',dict(command=argv,exit_code=result.returncode));assert result.returncode==0
    records=json.loads((out/'RESULT.json').read_bytes());assert len(records['cases'])==34
    errors=[r for r in records['cases'] if 'source_error' in r];assert len(errors)==2 and all(r['source_error']['type']=='IndexError' and r['matrix'] is None for r in errors)
    save(out/'COMPLETED.json',dict(status='collected-original-HTSeq-interval-set-binary-presence-controls',case_populations=34,successful_matrices=32,retained_original_refusals=2,
        result=pin((out/'RESULT.json').read_bytes()),collector=pin(Path(__file__).read_bytes()),original_custom_author_script_recovered=False,original_htseq_count_run=False,catalog_credit=False))
    print('Collected32 original-library binary-presence matrices and2 genuine original interval refusals')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original library checks')
    main()
