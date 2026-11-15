#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Additional real source executions for origin coordinates and overlapping exons."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,zipfile
p=argparse.ArgumentParser(description=__doc__)
for key in ['build','probe','out','archive']:p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();out=a.out.resolve();out.mkdir(exist_ok=False);prefix=a.build.resolve()/'runtime/.pixi/envs/default'
env=dict(os.environ,LD_LIBRARY_PATH=str(prefix/'lib'),PATH=str(prefix/'bin')+':/usr/bin:/bin')
def pin(path):
 raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n')
def run(folder,name,command):
 r=subprocess.run(list(map(str,command)),cwd=folder,env=env,capture_output=True)
 (folder/(name+'.stdout')).write_bytes(r.stdout);(folder/(name+'.stderr')).write_bytes(r.stderr)
 write(folder/(name+'.json'),dict(command=list(map(str,command)),exit_code=r.returncode))
 if r.returncode:raise RuntimeError((name,r.returncode,r.stderr))
shutil.copyfile(Path(__file__),out/Path(__file__).name)
write(out/'SOURCE.json',dict(original_completion=pin(a.build/'COMPLETED.json'),probe=pin(a.probe),original_algorithms_modified=False))
cases=[]
definitions=[
 ('overlapping-exons-origin',[('g','t',[(1,50),(1,80)]),('h','h',[(100,150)])],[(2,'20M'),(120,'20M')],16),
 ('overlapping-exons-away',[('g','t',[(100,150),(100,180)]),('h','h',[(200,250)])],[(105,'20M'),(220,'20M')],0),
 ('adjacent-exons',[('g','t',[(100,150),(151,190)])],[(140,'20M'),(155,'10M')],0),
 ('single-base-exons',[('g','t',[(1,1),(10,10)]),('h','h',[(20,20)])],[(1,'20M'),(4,'10M')],0),
 ('nested-exons',[('g','t',[(100,200),(120,150),(180,250)])],[(110,'100M'),(125,'10M')],0),
 ('tied-transcript-models',[('g','a',[(100,150),(250,300)]),('g','b',[(100,200),(230,300)])],[(110,'20M'),(170,'20M'),(240,'20M')],16),
 ('overlap-one-base',[('g','t',[(100,150),(150,190)])],[(140,'20M'),(150,'10M')],0),
]
for name,genes,reads,cutoff in definitions:
 folder=out/name;folder.mkdir();(folder/'bams').mkdir();(folder/'original-main').mkdir();(folder/'original-state').mkdir()
 text=''
 for gene,tx,exons in genes:
  for start,end in exons:text+=f'chr1\tcontrol\texon\t{start}\t{end}\t.\t+\t.\tgene_id "{gene}"; transcript_id "{tx}";\n'
 (folder/'annotation.gtf').write_text(text)
 text='@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:100000\n'
 for n,(start,cigar) in enumerate(reads):
  length=sum(int(v) for v,op in re.findall(r'(\d+)([A-Z=])',cigar) if op in 'MIS=X')
  text+=f'r{n}\t0\tchr1\t{start}\t60\t{cigar}\t*\t0\t0\t'+('A'*length)+'\t'+('I'*length)+'\n'
 (folder/'one.sam').write_text(text)
 run(folder,'samtools',[prefix/'bin/samtools','view','--no-PG','-b','-o',folder/'bams/one.bam',folder/'one.sam'])
 run(folder/'original-main','process',[a.build/'build-original/TPMCalculator/bin/TPMCalculator','-g',folder/'annotation.gtf','-b',folder/'bams/one.bam','-c',cutoff,'-e','-a'])
 run(folder/'original-state','process',[a.probe,folder/'annotation.gtf',cutoff,0,8,0,'one='+str(folder/'bams/one.bam')])
 products=sorted(p.name for p in (folder/'original-main').iterdir() if p.suffix in ['.out','.ent','.uni'])
 for file in products:assert (folder/'original-main'/file).read_bytes()==(folder/'original-state'/file).read_bytes(),(name,file)
 metadata=dict(name=name,cutoff=cutoff,mapq=0,overlap=8,proper=False,sample_order=['one'],full_state='original-state/process.stdout',original_main_and_probe_tables_byte_equal=True,table_files=products)
 write(folder/'CASE.json',metadata);cases.append(metadata);print('ORIGINAL_EDGE_CASE_COMPLETE',name,flush=True)
write(out/'CASES.json',cases)
a.archive.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for f in sorted(out.rglob('*')):
  if f.is_file():z.write(f,f.relative_to(out).as_posix())
print(json.dumps(dict(status='PASS_SEVEN_COMPLETE_ORIGINAL_EDGE_CONTROLS_NOT_NATIVE_QUALIFICATION',archive=pin(a.archive)),indent=2))
