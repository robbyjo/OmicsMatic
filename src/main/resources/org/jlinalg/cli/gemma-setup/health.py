#!/usr/bin/env python3
"""Synthetic original GEMMA health only; never a user association analysis."""
import argparse,csv,hashlib,json,math,os,signal,subprocess,time
from pathlib import Path
def pin(p):
 with p.open('rb')as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def put(p,value):
 with p.open('x',encoding='utf-8',newline='\n')as f:json.dump(value,f,indent=2,allow_nan=False);f.write('\n')
def run(home,report):
 report.mkdir();support=home/'support';lock=json.loads((support/'source-lock.json').read_bytes());decl=json.loads((support/'health-fixture.json').read_bytes());n=decl['samples'];G=decl['variant_major_genotypes'];m=len(G);f=report/'inputs';f.mkdir()
 (f/'data.fam').write_text(''.join(f'F{i//4:02d} S{i:03d} 0 0 {1+i%2} {y:.17g}\n'for i,y in enumerate(decl['phenotype'])),encoding='ascii')
 (f/'data.bim').write_text(''.join(f'{1+j//12}\tmarker{j:03d}\t0\t{1000+100*j}\tA\tG\n'for j in range(m)),encoding='ascii')
 raw=bytearray(b'\x6c\x1b\x01');codes={0:3,1:2,2:0}
 for g in G:
  for i in range(0,n,4):raw.append(sum(codes[g[i+k]]<<(2*k)for k in range(min(4,n-i))))
 (f/'data.bed').write_bytes(raw);(f/'covariates.txt').write_text(''.join(f'1 {x:.17g}\n'for x in decl['covariate']),encoding='ascii');inputs={p.name:pin(p)for p in f.iterdir()};records=[]
 for edition,definition in lock['editions'].items():
  binary=home/'bin'/('gemma-'+edition)
  if pin(binary)!=definition['program']:raise ValueError('Changed original GEMMA binary: '+edition)
  for mode in('gk1','gk2','lmm4'):
   work=report/(edition+'-'+mode);work.mkdir();(work/'out').mkdir();args=['-gk',mode[-1]]if mode.startswith('gk')else['-lmm','4','-k',str(report/(edition+'-gk2')/'out/control.sXX.txt')]
   argv=['/usr/bin/prlimit','--as=2147483648','--cpu=30','--fsize=16777216','--nofile=256','--',str(binary),'-bfile',str(f/'data'),'-c',str(f/'covariates.txt'),'-maf','.01','-miss','.05','-hwe','0','-r2','.9999',*args,'-outdir',str(work/'out'),'-o','control'];put(work/'command.json',dict(argv=argv,cwd=str(work)));env=dict(PATH='/usr/bin:/bin',HOME=str(work),TMPDIR=str(work),LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',GOTO_NUM_THREADS='1',MKL_NUM_THREADS='1');start=time.monotonic()
   with(work/'stdout').open('xb')as out,(work/'stderr').open('xb')as err:
    child=subprocess.Popen(argv,cwd=work,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err,start_new_session=True);put(work/'process.json',dict(owned_pid=child.pid,owned_session=child.pid))
    try:code=child.wait(timeout=45)
    except BaseException:
     if child.poll()is None:os.killpg(child.pid,signal.SIGKILL)
     child.wait();raise
   put(work/'result.json',dict(exit_code=code,elapsed_seconds=time.monotonic()-start))
   if code!=0:raise ValueError('Original artificial health failed: '+edition+'/'+mode)
   if mode.startswith('gk'):
    k=1 if mode=='gk1'else 2;path=work/'out'/('control.'+('cXX'if k==1 else'sXX')+'.txt');actual=[[float(v)for v in row.split()]for row in path.read_text().splitlines()]
    if len(actual)!=n or any(len(row)!=n or any(not math.isfinite(v)for v in row)for row in actual):raise ValueError('Incomplete/nonfinite original health matrix')
    expected=[[0.]*n for _ in range(n)]
    for g in G:
     mean=sum(g)/n;variance=sum(v*v for v in g)/n-mean*mean;v=[(x-mean)/(math.sqrt(variance)if k==2 else 1)for x in g]
     for i in range(n):
      for j in range(n):expected[i][j]+=v[i]*v[j]/m
    if max(abs(actual[i][j]-expected[i][j])for i in range(n)for j in range(n))>=6e-6:raise ValueError('Original health matrix differs from independent calculation')
   else:
    rows=list(csv.DictReader((work/'out/control.assoc.txt').open(),delimiter='\t'))
    if len(rows)!=m:raise ValueError('Incomplete original health association population')
    for actual,expected in zip(rows,decl['expected_original_association']):
     if actual.keys()!=expected.keys():raise ValueError('Original health association schema differs')
     for key,value in expected.items():
      if key in('chr','rs','ps','n_miss','allele1','allele0'):
       if actual[key]!=value:raise ValueError('Original health identifier differs: '+key)
      else:
       v=float(actual[key]);e=float(value)
       if not math.isfinite(v)or abs(v-e)>1e-5*max(1,abs(e)):raise ValueError('Original health statistic differs: '+key)
   if pin(binary)!=definition['program']:raise ValueError('Original executable changed during health')
   records.append(dict(edition=edition,mode=mode,status='PASS_ORIGINAL_ARTIFICIAL_HEALTH'))
 if inputs!={p.name:pin(p)for p in f.iterdir()}:raise ValueError('Artificial health inputs changed')
 put(report/'COMPLETED.json',dict(schema_version=1,status='PASS_THREE_ORIGINAL_GEMMA_EDITIONS_NINE_ARTIFICIAL_CONTROLS',cases=records,user_analysis_admitted=False,whole_paper_reproduced=False));return records
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--home',type=Path,required=True);parser.add_argument('--report',type=Path,required=True);a=parser.parse_args();run(a.home.resolve(),a.report.absolute())
