#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged original TPMCalculator and a transparent full-state reader on synthetic controls."""
from pathlib import Path
import argparse,hashlib,json,os,random,re,shutil,subprocess,sys,zipfile

ROOT=Path(__file__).resolve().parents[3]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--archive',type=Path,required=True)
args=parser.parse_args();BUILD=args.build.resolve();OUT=args.out.resolve();archive=args.archive.resolve()
PREFIX=BUILD/'runtime/.pixi/envs/default'
ENV=dict(os.environ,LD_LIBRARY_PATH=str(PREFIX/'lib'),PATH=str(PREFIX/'bin')+':/usr/bin:/bin')

def pin(p):
 b=p.read_bytes();return dict(bytes=len(b),sha256=hashlib.sha256(b).hexdigest())
def save(p,x):p.write_text(json.dumps(x,indent=2)+'\n')
def run(folder,name,command):
 p=subprocess.run(list(map(str,command)),cwd=folder,env=ENV,stdin=subprocess.DEVNULL,capture_output=True)
 (folder/(name+'.stdout')).write_bytes(p.stdout);(folder/(name+'.stderr')).write_bytes(p.stderr)
 save(folder/(name+'.json'),dict(command=list(map(str,command)),exit_code=p.returncode))
 if p.returncode:raise RuntimeError((name,p.returncode,p.stderr[-3000:]))
 return p
def gtf(genes):
 lines=[]
 for gene,tx,exons,strand,chr in genes:
  for start,end in exons:lines.append(f'{chr}\tcontrol\texon\t{start}\t{end}\t.\t{strand}\t.\tgene_id "{gene}"; transcript_id "{tx}";\n')
 return ''.join(lines)
def sam(rows):
 lines=['@HD\tVN:1.6\tSO:unsorted\n']+[f'@SQ\tSN:{c}\tLN:100000\n' for c in ['chr1','chr2','chrZ']]
 for n,(pos,cigar,flags,mapq,chr) in enumerate(rows):
  size=sum(int(k) for k,op in re.findall(r'(\d+)([A-Z=])',cigar) if op in 'MIS=X')
  lines.append(f'r{n}\t{flags}\t{chr}\t{pos}\t{mapq}\t{cigar}\t*\t0\t0\t'+('A'*size if size else '*')+'\t'+('I'*size if size else '*')+'\n')
 return ''.join(lines)
def gene(g,t,exons,strand='+',chr='chr1'):return (g,t,exons,strand,chr)
def read(pos,cigar='20M',flags=0,mapq=60,chr='chr1'):return (pos,cigar,flags,mapq,chr)

OUT.mkdir(exist_ok=False)
assert json.loads((BUILD/'COMPLETED.json').read_bytes())['source_algorithms_patched'] is False
source=ROOT/'tools/literature/tpmcalculator_validation/OriginalProbe.cpp'
shutil.copyfile(source,OUT/'OriginalProbe.cpp');shutil.copyfile(Path(__file__),OUT/'make_controls.py')
objects=sorted(p for p in (BUILD/'build-original/TPMCalculator/build/Release/GNU-MacOSX/src').glob('*.o') if p.name!='main.o')
compiler=PREFIX/'bin/x86_64-conda_cos6-linux-gnu-g++'
run(OUT,'compile-original-state-reader',[compiler,'-std=c++14','-O2','-I'+str(BUILD/'source-original/TPMCalculator/includes'),'-I'+str(PREFIX/'include/bamtools'),source,*objects,'-L'+str(PREFIX/'lib'),'-Wl,-rpath,'+str(PREFIX/'lib'),'-lbamtools','-lm','-lz','-o',OUT/'OriginalProbe'])
save(OUT/'SOURCE.json',dict(original_build=str(BUILD),original_completion=pin(BUILD/'COMPLETED.json'),probe_source=pin(source),probe_binary=pin(OUT/'OriginalProbe'),objects={p.name:pin(p) for p in objects},original_algorithms_modified=False))
cases=[]
def case(name,genes,samples,cutoff=16,mapq=0,overlap=8,proper=False):
 folder=OUT/name;folder.mkdir();(folder/'bams').mkdir();(folder/'original-main').mkdir();(folder/'original-state').mkdir()
 (folder/'annotation.gtf').write_text(gtf(genes),encoding='utf-8')
 for sample,rows in samples.items():
  (folder/(sample+'.sam')).write_text(sam(rows))
  run(folder,'samtools-'+sample,[PREFIX/'bin/samtools','view','--no-PG','-b','-o',folder/'bams'/(sample+'.bam'),folder/(sample+'.sam')])
 args=['-g',folder/'annotation.gtf','-d',folder/'bams','-c',str(cutoff),'-q',str(mapq),'-o',str(overlap),'-e','-a']+(['-p'] if proper else [])
 main=run(folder/'original-main','process',[BUILD/'build-original/TPMCalculator/bin/TPMCalculator',*args])
 order=re.findall(r'Processing sample: ([^\s]+)',main.stderr.decode())
 assert sorted(order)==sorted(samples),(name,order)
 probe=run(folder/'original-state','process',[OUT/'OriginalProbe',folder/'annotation.gtf',cutoff,mapq,overlap,int(proper),*[n+'='+str(folder/'bams'/(n+'.bam')) for n in order]])
 products=sorted(p.name for p in (folder/'original-main').iterdir() if p.suffix in ['.out','.ent','.uni'])
 for n in products:assert (folder/'original-main'/n).read_bytes()==(folder/'original-state'/n).read_bytes(),(name,n)
 metadata=dict(name=name,cutoff=cutoff,mapq=mapq,overlap=overlap,proper=proper,sample_order=order,full_state='original-state/process.stdout',original_main_and_probe_tables_byte_equal=True,table_files=products)
 save(folder/'CASE.json',metadata);cases.append(metadata)
 print('ORIGINAL_CASE_COMPLETE',name,flush=True)

simple=[gene('g','t',[(100,140),(180,220)])]
case('flags',simple,{'one':[read(105,flags=f) for f in [0,16,99,147,256,1024,2048,512,4]]+[read(105,mapq=0),read(130,'8M'),read(50,'100M')]})
case('proper-only',simple,{'one':[read(105,flags=f) for f in [0,2,16,99,147,256,1024,2048,512]]},mapq=20,proper=True)
case('spliced-partial',simple,{'one':[read(105,'20M55N20M'),read(105,'20M300N20M'),read(50,'20M50N20M'),read(200,'20M')]})
case('cigar',simple,{'one':[read(110,c) for c in ['5S10M','5H10M','10M2P10M','5M10I5M','5M10D5M','10=2X8=','10M20N10M','2S3N10M','5M']]})
case('zero-overlap',simple,{'one':[read(100,c) for c in ['2S3N10M','10M20N10M','5S10M']]},overlap=0)
alternative=[gene('a','a1',[(100,150),(200,250),(350,400)]),gene('a','a2',[(100,160),(180,260),(350,400)]),gene('b','b1',[(140,210),(300,360)],'-')]
case('alternative-isoforms',alternative,{'one':[read(p,'30M') for p in range(85,430,13)]})
for cutoff in [0,15,16,17,50]:case('cutoff-'+str(cutoff),[gene('a','t1',[(100,150),(167,200)]),gene('b','t2',[(300,350),(366,400)])],{'one':[read(p) for p in range(100,390,17)]},cutoff=cutoff)
case('antisense-copies',[gene('keep','t1',[(100,160)]),gene('drop-AS9','x',[(110,170)]),gene('keep','t2',[(1000,1050)]),gene('lower-as','y',[(2100,2150)])],{'one':[read(120),read(1020),read(2120)]})
case('nested-long-gene',[gene('long','long',[(100,10000)])]+[gene('short'+str(i),'s'+str(i),[(200+i*100,230+i*100)]) for i in range(12)],{'one':[read(1500),read(215),read(105)]})
case('ties',[gene('A','a',[(100,200)]),gene('B','b',[(100,200)]),gene('C','c',[(100,200)])],{'one':[read(100),read(105),read(180)]})
case('first-base-tiny',[gene('tiny','tiny',[(1,3)]),gene('far','far',[(100,150)])],{'one':[read(5,'10M'),read(1,'10M'),read(95,'20M')]})
case('sample-state',simple+[gene('b','bt',[(800,850)])],{'one':[read(105)],'two':[read(810)],'three':[]})
case('empty',simple,{'one':[]})
case('mapq-all-filtered',simple,{'one':[read(105)]},mapq=65535)
case('chromosomes',[*simple,gene('g2','t2',[(200,300)],chr='chr2')],{'one':[read(105),read(210,chr='chr2'),read(400,chr='chrZ')]})
case('reused-transcript-id',[gene('A','t',[(100,150)]),gene('B','t',[(500,550)]),gene('A','t',[(200,250)])],{'one':[read(105),read(210),read(510)]})
rng=random.Random(8310527)
for k in range(30):
 genes=[]
 for j in range(12):
  base=rng.randrange(100,3000)
  for t in range(rng.randrange(1,4)):
   exons=[];pos=base+rng.randrange(0,30)
   for e in range(rng.randrange(1,5)):
    end=pos+rng.randrange(8,100);exons.append((pos,end));pos=end+rng.randrange(1,90)
   genes.append(gene('g'+str(j),'g'+str(j)+'t'+str(t),exons,'+' if j%2 else '-'))
 rows=[read(rng.randrange(50,3500),rng.choice(['20M','40M','10M30N20M','5M4I12M','10M4D15M','3S25M']),rng.choice([0,0,0,16,99,147,256,1024,2048,512]),rng.choice([0,10,30,60])) for _ in range(150)]
 case('random-'+str(k).zfill(2),genes,{'one':rows},cutoff=rng.choice([0,8,16,17,40]),mapq=rng.choice([0,20]),overlap=rng.choice([0,1,8,20]),proper=k%5==0)

save(OUT/'CASES.json',cases)
archive.parent.mkdir(parents=True,exist_ok=True)
assert not archive.exists()
with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for p in sorted(OUT.rglob('*')):
  if p.is_file() and p.name!='OriginalProbe':z.write(p,p.relative_to(OUT).as_posix())
save(OUT/'COMPLETED.json',dict(status='PASS_ORIGINAL_MAIN_AND_COMPLETE_STATE_SYNTHETIC_CONTROLS_NOT_NATIVE_QUALIFICATION',cases=len(cases),archive=pin(archive),original_build=str(BUILD),source_commit='b275fb3d749dc46e7e0efeca9bdd1c230702339d',native_implementation_credit=0))
print('ARCHIVED_ORIGINAL_CONTROLS',len(cases),pin(archive),flush=True)
