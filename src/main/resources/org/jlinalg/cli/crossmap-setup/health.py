# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Original CrossMap source probes and small explicitly artificial runtime controls."""
from pathlib import Path
import argparse,json,os,runpy,subprocess,sys
sys.dont_write_bytecode=True
RUNNER="import sys,runpy;root=sys.argv.pop(1);entry=sys.argv.pop(1);sys.path.insert(0,root);sys.argv[0]=entry;runpy.run_path(entry,run_name='__main__')"
def put(p,v):p.write_bytes((json.dumps(v,indent=2)+'\n').encode())
def environment(home):return dict(PATH='/usr/bin:/bin',HOME=str(home),LANG='C.UTF-8',LC_ALL='C.UTF-8',PYTHONDONTWRITEBYTECODE='1',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1')
def entrypoint(home,version):
 source=home/'sources'/version;entries=list(source.glob('*.data/scripts/CrossMap.py'))
 if len(entries)!=1:raise ValueError('Missing unique original CrossMap entrypoint')
 return source,entries[0]
def original(home,version,arguments,folder):
 folder.mkdir();source,entry=entrypoint(home,version);command=[str(home/'bin/python'),'-I','-B','-c',RUNNER,str(source),str(entry),*map(str,arguments)];put(folder/'COMMAND.json',command)
 with(folder/'stdout').open('xb')as stdout,(folder/'stderr').open('xb')as stderr:child=subprocess.run(command,cwd=folder,env=environment(folder),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
 put(folder/'RESULT.json',dict(exit_code=child.returncode));return child.returncode
def probe(home,version):
 source,entry=entrypoint(home,version);sys.path.insert(0,str(source));namespace=runpy.run_path(str(entry),run_name='original_source_probe')
 if namespace['__version__']!=version:raise ValueError('Original declared source version differs')
 import importlib.metadata as metadata
 import numpy,pysam,bx.intervals.intersection,pyBigWig,Cython
 return dict(status='ORIGINAL_SOURCE_AND_DEPENDENCIES_LOADED',source_version=namespace['__version__'],python=sys.version,dependencies={n:metadata.version(n)for n in('numpy','pysam','bx-python','pyBigWig','Cython')},source_program_modified=False)
def health(home,folder):
 import pysam,pyBigWig
 folder.mkdir();d=folder/'inputs';d.mkdir();chain=d/'input.chain';chain.write_bytes(b'chain 1000 chrS 100 + 0 100 chrT 200 + 100 200 1\n100\n')
 bed=d/'input.bed';bed.write_bytes(b'chrS\t10\t20\tcontrol\t0\t+\nchrMissing\t0\t1\tunmapped\t0\t+\n')
 fasta=d/'target.fa';fasta.write_bytes(b'>chrT\n'+b'A'*200+b'\n');pysam.faidx(str(fasta))
 vcf=d/'input.vcf';vcf.write_bytes(b'##fileformat=VCFv4.2\n##contig=<ID=chrS,length=100>\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\nchrS\t11\tcontrol\tA\tC\t.\tPASS\t.\n')
 signal=d/'input.bw';b=pyBigWig.open(str(signal),'w');b.addHeader([('chrS',100)]);b.addEntries(['chrS'],[10],ends=[20],values=[2.]);b.close();rows=[]
 for version in('0.5.2','0.6.3'):
  root=folder/version;root.mkdir()
  for mode,inputfile in(('bed',bed),('vcf',vcf),('bigwig',signal)):
   work=root/mode;output=work/('mapped'if mode=='bigwig'else'mapped.'+mode);args=[mode,chain,inputfile]
   if mode=='vcf':args.append(fasta)
   args.append(output);code=original(home,version,args,work)
   if code:raise ValueError('Original '+version+' '+mode+' health failed; retain source logs and incomplete runtime')
   if mode=='bed':
    if output.read_text().strip()!='chrT\t110\t120\tcontrol\t0\t+'or'chrMissing'not in Path(str(output)+'.unmap').read_text():raise ValueError('Complete original BED health differs')
   elif mode=='vcf':
    values=[line.split('\t')for line in output.read_text().splitlines()if not line.startswith('#')]
    if len(values)!=1 or values[0][:5]!=['chrT','111','control','A','C']:raise ValueError('Complete original VCF health differs')
   else:
    if Path(str(output)+'.sorted.bgr').read_text().strip()!='chrT\t110\t120\t2.0':raise ValueError('Complete original signal health differs')
    b=pyBigWig.open(str(output)+'.bw');actual=dict(chroms=b.chroms(),intervals=b.intervals('chrT'));b.close()
    if actual!=dict(chroms={'chrT':200},intervals=((110,120,2.),)):raise ValueError('Complete original binary signal health differs')
   rows.append(dict(edition=version,format=mode,status='PASS_ORIGINAL_ARTIFICIAL_HEALTH'))
 result=dict(status='PASS_BOTH_ORIGINAL_EDITIONS_THREE_FORMATS',cases=rows,artificial_controls=True,user_analysis_admitted=False,whole_paper_reproduced=False);put(folder/'COMPLETED.json',result);return result
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,required=True);p.add_argument('--probe',choices=('0.5.2','0.6.3'));p.add_argument('--health',type=Path);a=p.parse_args()
 try:
  if bool(a.probe)==bool(a.health):raise ValueError('Select one source probe or health operation')
  print(json.dumps(probe(a.home,a.probe)if a.probe else health(a.home,a.health),indent=2));sys.exit(0)
 except Exception as e:print('FAIL2: CrossMap runtime qualification failed: '+repr(e)+'. Preserve all original sources and logs; supply a new qualified runtime before dependent analysis.',file=sys.stderr);sys.exit(2)
