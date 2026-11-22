#!/usr/bin/env python3
"""Compare native genomic operations with three admitted original HOMER releases.

Requires a development JAR containing HomerGenomicSequences/HomerGenomeRuntime
and a Java17+ JDK. Uses exact supplied homes; never downloads, installs or
changes a runtime. Retains original commands, outputs and failed comparisons.
This verifies primitives and runtime bindings; genomic CLI workflow checks are separate.
"""
import argparse
import json
from pathlib import Path
import random
import shutil
import sys
import install_homer_genome as genome

HERE=Path(__file__).resolve().parent
VERSIONS=['5.1','4.11','4.11.1']
sha=genome.core.sha
RECOVERY=('Required action: inspect verification.json, original-* diagnostics and native-* tables. Preserve failed outputs; correct the named runtime, path or comparison cause and rerun to a NEW verification directory. A failed or skipped verification is not a pass. '+genome.RECOVERY)

def require(condition,context):
 if not condition:raise ValueError("Original/native genomic comparison differs: "+repr(context))

def verify(args,O,homes,jar):
 classes=O/'classes';classes.mkdir();calls=[];jar_hash=sha(jar);probe=HERE/'fixtures/HomerGenomeNativeProbe.java';probe_hash=sha(probe);shutil.copy2(probe,O/probe.name)
 def run(cmd,work,label,env=None):
  call=genome.execute(list(map(str,cmd)),homes['5.1'],work,label,seconds=120,build_env=env);calls.append(call);return (work/(label+'.stdout')).read_bytes()
 run([args.javac,'--release','17','-Xlint:all','-Werror','-cp',jar,'-d',classes,O/'HomerGenomeNativeProbe.java'],O,'compile')
 rng=random.Random(2026102802);seq=''.join(rng.choice('AACCGGTTNacgtnRYUDHV') for _ in range(20000));(O/'genome.fa').write_text('>chr1 exact mixed-case input\n'+''.join(seq[i:i+79]+'\n' for i in range(0,len(seq),79)))
 positions=[]
 for i in range(64):
  start=200+i*270;end=start+[1,2,5,120,121,200][i%6]-1;positions.append(f'P{i+1:04d}\tchr1\t{start}\t{end}\t{"+" if i%2==0 else "-"}\t7.5\n')
 (O/'positions.pos').write_text(''.join(positions));freq=['A'*10,'C'+'A'*3,'C'*3+'A'*7,'C'*5+'A'*5,'ACGNCGNT','ANCNGNT','NNNNNN','CG','A','C','ACGT','N'*30,'CG'+'A'*127,'C'+'A'*127]
 for i in range(150):freq.append(''.join(rng.choice('ACGTNN') for _ in range(5+i%137)))
 (O/'frequency-input.tsv').write_text(''.join(f'F{i:04d}\t{s}\n' for i,s in enumerate(freq)));filters=freq+['A'*100000,'A'*100001,'A'*3+'N'*7,'A'*2+'N'*8];(O/'filter-input.tsv').write_text(''.join(f'F{i:04d}\t{s}\n' for i,s in enumerate(filters)))
 groups=[];bins=[]
 for i in range(160):
  # Last two bins intentionally contain only one role; fractional weights exercise unit-weight support.
  b=i%12;t=(i%5==0) if b<10 else b==10;groups.append(f'W{i:04d}\t{1 if t else 0}\t{[.5,1,2.75,3][i%4]}\n');bins.append(f'W{i:04d}\t{b}\n')
 (O/'rounding-group.tsv').write_text('t\t1\t2.000005\nb\t0\t1\n');(O/'rounding-bins.tsv').write_text('t\t0\nb\t0\n')
 (O/'weight-group.tsv').write_text(''.join(groups));(O/'weight-bins.tsv').write_text(''.join(bins))
 run([args.java,'-Xmx768m','-cp',str(classes)+':'+str(jar),'org.jlinalg.cli.HomerGenomeNativeProbe',O,*[homes[v] for v in VERSIONS]],O,'native')
 def rows(raw):
  result={}
  for line in raw.decode().splitlines():
   f=line.split('\t');require(f[0] not in result,'duplicate original identity');result[f[0]]=f[1:]
  return result
 observed=[]
 for v in VERSIONS:
  home=homes[v];work=O/('original-'+v);work.mkdir();env=dict(PATH=str(home/'bin')+':/usr/bin:/bin',HOME=str(work),TMPDIR=str(work),LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',PERL_HASH_SEED='0',PERL_PERTURB_KEYS='0');total=0
  for mask in [False,True]:
   raw=run([home/'bin/homerTools','extract',O/'positions.pos',O/'genome.fa',*(['-mask'] if mask else [])],work,'extract-'+str(mask),env);p=work/('raw-'+str(mask)+'.tsv');p.write_bytes(raw);clean=run(['/usr/bin/perl',home/'bin/cleanUpSequences.pl',p],work,'cleanup-'+str(mask),env);native=rows((O/('native-extract-'+str(mask).lower()+'.tsv')).read_bytes());original=rows(clean);require(native==original,('extract',v,mask));total+=len(native)
  for size,move in [(3,0),(4,0),(199,0),(200,0),(200,-11),(199,11)]:
   raw=run(['/usr/bin/perl',home/'bin/resizePosFile.pl',O/'positions.pos',str(size),str(move)],work,f'resize-{size}-{move}',env);require(rows(raw)==rows((O/f'native-resize-{size}-{move}.pos').read_bytes()),('resize',v,size,move));total+=64
  run([home/'bin/homerTools','freq','-gc',work/'frequency.tsv',O/'frequency-input.tsv'],work,'frequency',env);require(rows((work/'frequency.tsv').read_bytes())==rows((O/'native-frequency.tsv').read_bytes()),('freq',v));total+=len(rows((work/'frequency.tsv').read_bytes()))
  for col in [1,2]:
   raw=run(['/usr/bin/perl',home/'bin/freq2group.pl',work/'frequency.tsv',str(col)],work,'bins-'+str(col),env)
   require(rows(raw)==rows((O/('native-bins-'+str(col)+'.tsv')).read_bytes()),('bins',v,col));total+=len(rows(raw))
  for limit in [0.0,.5,.7,1.0]:
   raw=run(['/usr/bin/perl',home/'bin/removePoorSeq.pl',O/'filter-input.tsv',str(limit)],work,'filter-'+str(limit),env);require(rows(raw)==rows((O/('native-filter-'+str(limit)+'.tsv')).read_bytes()),('filter',v,limit));total+=len(filters)
  raw=run(['/usr/bin/perl',home/'bin/assignGeneWeights.pl',O/'weight-group.tsv',O/'weight-bins.tsv'],work,'weights',env);require(rows(raw)==rows((O/'native-weights.tsv').read_bytes()),('weights',v));total+=160
  raw=run(['/usr/bin/perl',home/'bin/assignGeneWeights.pl',O/'rounding-group.tsv',O/'rounding-bins.tsv'],work,'rounding-weights',env);require(rows(raw)==rows((O/'native-rounding-weights.tsv').read_bytes()),('rounding-weights',v));total+=2
  observed.append(dict(version=v,extract_records=128,resized_records=384,frequency_records=len(rows((work/'frequency.tsv').read_bytes())),bin_records=2*len(rows((work/'frequency.tsv').read_bytes())),filter_dispositions=4*len(filters),weighted_population_records=162,compared_records=total));(O/'progress.json').write_text(json.dumps(observed,indent=2)+'\n')
 require(sha(jar)==jar_hash and sha(probe)==probe_hash and sha(O/probe.name)==probe_hash,'immutable JAR/probe source')
 record=dict(status='passed-original-genomic-primitive-comparisons',candidate_sha256=sha(jar),probe_source_sha256=sha(HERE/'fixtures/HomerGenomeNativeProbe.java'),runtime_marker_sha256={v:sha(homes[v]/genome.MARKER) for v in VERSIONS},runtime_bindings=3,versions=observed,calls=calls,scope='Native indexed extraction,masking/strand,resize,frequency,poor-sequence disposition,GC/CpG bin-weight primitive comparisons against all three source-configured original releases. Original full workflow admission health is separate. Genomic project CLI workflow checks are separate; no full-paper parity.');return record

def main(argv=None):
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
 for v in VERSIONS:parser.add_argument('--home-'+v,type=Path,required=True,dest='home_'+v)
 parser.add_argument('--java',default=shutil.which('java'));parser.add_argument('--javac',default=shutil.which('javac'))
 args=parser.parse_args(argv);O=None;owns=False;code=0;record={}
 try:
  O=genome.core.private(args.out)
  if O.exists():raise ValueError('Verification output exists; choose a NEW directory')
  homes={v:genome.core.private(getattr(args,'home_'+v)) for v in VERSIONS}
  if any(O==p or O in p.parents or p in O.parents for p in homes.values()):raise ValueError('Verification output and runtime homes must be nonnested')
  O.mkdir(parents=True);owns=True;host=genome.core.host()
  for v in VERSIONS:genome.verify_home(homes[v],'linux-x86_64-genome-'+v,host)
  marker_hashes={v:sha(homes[v]/genome.MARKER) for v in VERSIONS}
  for tool in [args.java,args.javac]:
   if not tool or not Path(tool).is_file():raise genome.core.Unavailable('Java17+ Java/Javac unavailable; supply --java and --javac')
  jar=genome.core.private(args.jar)
  if not jar.is_file():raise genome.core.Unavailable('Development JAR missing: '+str(jar))
  record=verify(args,O,homes,jar)
  for v in VERSIONS:genome.verify_home(homes[v],'linux-x86_64-genome-'+v,host)
  require(marker_hashes=={v:sha(homes[v]/genome.MARKER) for v in VERSIONS},'immutable runtime markers')
 except (genome.core.Unavailable,FileNotFoundError,PermissionError) as failure:code=3;record=dict(status='skipped-dependency-unavailable',cause=str(failure))
 except Exception as failure:code=2;record=dict(status='failed-retained',cause=repr(failure))
 if owns:
  record['exit_code']=code;record['required_action']=RECOVERY;genome.core.write(O/'verification.json',record)
  (O/'WARNINGS.md').write_text('# HOMER GENOMIC '+('VERIFICATION SKIPPED' if code==3 else 'VERIFICATION FAILED' if code else 'VERIFICATION SCOPE')+'\n\n'+record.get('cause',record.get('scope',''))+'\n\n'+RECOVERY+'\n')
 if code:record['required_action']=RECOVERY;print(json.dumps(record),file=sys.stderr)
 else:print(json.dumps({k:record[k] for k in ['status','runtime_bindings','versions']}),flush=True)
 return code

if __name__=='__main__':raise SystemExit(main())
