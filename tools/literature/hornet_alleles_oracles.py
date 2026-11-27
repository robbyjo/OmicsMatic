# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate original Hornet component oracles on synthetic controls; no source is downloaded."""
from pathlib import Path
import argparse,json,hashlib,os,sys,importlib.util,random,collections,traceback
def pin(path):
 raw=Path(path).read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def put(path,value):
 with Path(path).open('x',encoding='utf-8',newline='\n')as stream:json.dump(value,stream,ensure_ascii=False,indent=2,allow_nan=False);stream.write('\n')
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--original-file',type=Path,required=True,help='Unmodified mapping/find_intersecting_snps.py from Hornet e642ac0e')
parser.add_argument('--out',type=Path,required=True,help='NEW result directory')
arguments=parser.parse_args();src=arguments.original_file.resolve()
EXPECTED_SOURCE={'bytes': 19649, 'sha256': '033842fb817e40f564c864f872469297076b548c56367f88544bb7e3a4500842'}
if pin(src)!=EXPECTED_SOURCE:raise SystemExit('FAIL2 original Hornet source identity differs; retain the selected unmodified revision')
D=arguments.out.resolve();D.mkdir()
spec=importlib.util.spec_from_file_location('original_hornet',src);mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
import pysam
header=pysam.AlignmentHeader.from_dict(dict(SQ=[dict(SN='chr1',LN=10000),dict(SN='chr2',LN=10000)]))
def read(sequence='AAAAAA',start=100,cigar=None,chrom='chr1',unmapped=False):
 return dict(chromosome=chrom,start=start,sequence=sequence,cigar=cigar or str(len(sequence))+'M',unmapped=unmapped)
def segment(data):
 r=pysam.AlignedSegment(header);r.query_name='synthetic';r.query_sequence=data['sequence'];r.reference_id=header.get_tid(data['chromosome']);r.reference_start=data['start'];r.cigarstring=data['cigar'];r.flag=4 if data['unmapped']else 0;return r
def variant(position,reference='A',alternate='C',chromosome='chr1'):
 return dict(chromosome=chromosome,position=position,reference=reference,alternate=alternate)
cases=[]
def add(name,reads,variants,phased=False):
 snps=collections.defaultdict(dict)
 for v in variants:snps[v['chromosome']][v['position']]=v['reference']+v['alternate']
 indels=mod.get_indels(snps);counts=collections.Counter();rr=list(map(segment,reads));case=dict(id=name,reads=reads,variants=variants,phased=phased)
 try:
  seqs=[mod.get_read_seqs(rr[0],snps,indels,counts,phased)]if len(rr)==1 else mod.get_dual_read_seqs(rr[0],rr[1],snps,indels,counts,phased)
  case.update(expected=dict(sequences=seqs,dispositions=dict(counts)))
 except BaseException as e:case.update(source_error=dict(type=type(e).__name__,message=str(e),dispositions=dict(counts)))
 cases.append(case)
try:
 add('single-no-snps',[read()],[])
 add('single-two-snps-ordered',[read()],[variant(100),variant(103,'A','G')])
 add('single-other-base',[read('NAAAAA')],[variant(100)])
 add('single-hyphen-indel',[read()],[variant(100,'A','-')])
 add('single-multibase-source-concatenation',[read()],[variant(100,'A','CG')])
 add('single-duplicate-position-last-wins',[read()],[variant(100),variant(100,'A','G')])
 add('single-duplicate-alleles-are-not-deduplicated',[read()],[variant(100,'A','CC')])
 add('single-sixth-snp-last-base-64',[read('AAAAAA')],[variant(100+i)for i in range(6)])
 add('single-sixth-snp-followed-by-base-discards',[read('AAAAAAA')],[variant(100+i)for i in range(6)])
 add('single-five-snps-32',[read('AAAAA')],[variant(100+i)for i in range(5)])
 add('single-phased-unsupported',[read()],[],True)
 add('single-unmapped-with-sequence',[read(unmapped=True)],[])
 for cigar,sequence in [('2S4M','AAAAAA'),('2M2I2M','AAAAAA'),('2M2D4M','AAAAAA'),('2M20N4M','AAAAAA'),('2H6M','AAAAAA'),('2=2X2M','AAAAAA'),('2M1P4M','AAAAAA')]:
  add('single-cigar-'+cigar,[read(sequence,cigar=cigar)],[variant(i)for i in range(100,127)])
 add('pair-no-snps',[read(),read(start=110)],[])
 add('pair-shared-overlap',[read(),read(start=103)],[variant(104)])
 add('pair-discordant-overlap',[read(),read('ACAAAA',start=103)],[variant(104)])
 add('pair-unmapped',[read(),read(unmapped=True)],[variant(100)])
 add('pair-six-snps-discards',[read(),read(start=110)],[variant(100+i)for i in range(6)])
 add('pair-five-snps-32',[read(),read(start=110)],[variant(100+i)for i in range(5)])
 add('pair-phased-six-snps-still-discards',[read(),read(start=110)],[variant(100+i)for i in range(6)],True)
 add('pair-phased-two-haplotypes',[read(),read(start=110)],[variant(100),variant(112)],True)
 add('pair-phased-other-base-no-counter',[read('NAAAAA'),read(start=110)],[variant(100)],True)
 add('pair-unphased-other-base-substitutes',[read('NAAAAA'),read(start=110)],[variant(100)])
 add('pair-first-multibase-discards',[read(),read(start=110)],[variant(100,'A','CC')])
 add('pair-later-multibase-concatenation',[read(),read(start=110)],[variant(100),variant(112,'A','CG')])
 add('pair-phased-later-multibase-indexing',[read(),read('GAAAAA',start=110)],[variant(100),variant(110,'A','CG')],True)
 add('pair-first-read-right-of-second',[read(start=120),read(start=100)],[variant(100),variant(120)],True)
 add('pair-phased-discordant-overlap',[read(),read('ACAAAA',start=103)],[variant(104)],True)
 randomizer=random.Random(896)
 patterns=[('12M',12),('2S10M',12),('4M2I6M',12),('4M2D8M',12),('4M20N8M',12),('2H12M',12),('4=4X4M',12)]
 for i in range(300):
  paired=i%3!=0;phased=paired and i%2==0
  cigar,length=randomizer.choice(patterns);r1=read(''.join(randomizer.choices('ACGTN',k=length)),start=100,cigar=cigar)
  reads=[r1]
  if paired:
   cigar,length=randomizer.choice(patterns);reads.append(read(''.join(randomizer.choices('ACGTN',k=length)),start=randomizer.choice([98,103,115]),cigar=cigar))
  rows=[]
  for position in randomizer.sample(range(98,135),randomizer.randrange(0,9)):
   a,b=randomizer.sample('ACGT',2)
   if i%23==0:b='-'
   if i%29==0:b+='G'
   rows.append(variant(position,a,b))
  add('seed896-'+str(i).zfill(3),reads,rows,phased)
 result=dict(schema_version=1,status='ORIGINAL_HORNET_ALLELE_COMPONENT_ORACLES_ONLY',source=dict(repository='https://github.com/TheFraserLab/Hornet',revision=mod.__dict__.get('REVISION','e642ac0ee9ed6625ce415305b9ced4d686403c79'),file='mapping/find_intersecting_snps.py',identity=pin(src)),python=sys.version,pysam=pysam.__version__,seed=896,cases=cases,reverse_complements=[dict(sequence=s,expected=mod.reverse_complement(s))for s in ['ACTG','ANRY=acgt','ATGCN']])
 put(D/'oracle.json',result);put(D/'COMPLETED.json',dict(status='ORIGINAL_HORNET_COMPONENT_ORACLES_GENERATED',cases=len(cases),source_errors=[dict(id=c['id'],**c['source_error'])for c in cases if 'source_error'in c],oracle=pin(D/'oracle.json'),new_family_credit=0));print(json.dumps(json.loads((D/'COMPLETED.json').read_text())),flush=True)
except BaseException as error:put(D/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),analysis_admitted=False));raise
