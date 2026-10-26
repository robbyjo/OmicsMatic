"""Freeze actual local MACS bdgpeakcall outputs; no download, installation or Python reimplementation."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import random
import subprocess

ROOT=Path(__file__).resolve().parents[2]
DEST=ROOT/'src/test/resources/track-peaks'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--python',type=Path,required=True);ap.add_argument('--macs-script',type=Path,required=True);ap.add_argument('--dll-directory',type=Path,required=True);args=ap.parse_args()
    cache=ROOT/'build/literature-review'/('track-peak-reference-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));cache.mkdir(parents=True)
    env=os.environ.copy();env['PATH']=str(args.dll_directory.resolve())+os.pathsep+env['PATH']
    for key in ['OPENBLAS_NUM_THREADS','OMP_NUM_THREADS','MKL_NUM_THREADS']:env[key]='1'
    prefix=[str(args.python.resolve()),'-I',str(args.macs_script.resolve())]
    probe=subprocess.run(prefix+['--version'],env=env,capture_output=True,text=True,timeout=60)
    assert probe.returncode==0 and '3.1.0' in probe.stdout,probe.stdout+probe.stderr
    cases=[]
    def add(name,runs,cutoff,minlen,gap,precision='float32'):
        cases.append({'id':name,'cutoff':cutoff,'min_length':minlen,'max_gap':gap,'precision':precision,'segments':runs})
    add('inclusive-gap-exact',[['chr1',0,10,0],['chr1',10,30,3],['chr1',30,40,0],['chr1',40,70,3],['chr1',70,100,0]],3,60,10)
    add('gap-too-wide',[['chr1',0,10,0],['chr1',10,30,3],['chr1',30,40,0],['chr1',40,70,3],['chr1',70,100,0]],3,1,9)
    add('adjacent-equal-is-one-plateau',[['chr1',0,5,0],['chr1',5,10,5],['chr1',10,40,5],['chr1',40,50,0]],5,1,0)
    add('lower-median-centers-unweighted',[['chr1',0,100,3],['chr1',100,101,0],['chr1',101,102,3],['chr1',102,103,0],['chr1',103,110,3],['chr1',110,111,0],['chr1',111,113,3]],3,1,1)
    add('maxima-not-first-or-longest',[['chr1',0,100,4],['chr1',100,101,0],['chr1',101,104,6],['chr1',104,105,0],['chr1',105,150,5]],3,1,1)
    add('empty-result',[['chr1',0,100,1]],2,1,0)
    add('short-result-excluded',[['chr1',0,10,4]],3,11,100)
    add('float32-boundary',[['chr1',0,10,1.00000001],['chr1',10,20,0],['chr1',20,30,1.0000001]],1.00000002,1,0)
    add('lexical-chromosome-order',[['chr2',0,11,4],['chr10',0,7,3]],2,1,0)
    rng=random.Random(754109)
    for i in range(120):
        runs=[]
        for chromosome in ['chr1','chr2']:
            position=0
            for _ in range(rng.randrange(4,30)):
                length=rng.randrange(1,31);value=rng.choice([-2,-1,0,.25,.5,1,2,3,4,5,7.25])
                runs.append([chromosome,position,position+length,value]);position+=length
        add('random-'+str(i),runs,rng.choice([.25,.5,1,2,3,4,5]),rng.choice([1,10,30,70,200]),rng.choice([0,1,5,10,30]))
    def execute(case):
        here=cache/case['id'];here.mkdir();track=here/'signal.bedGraph';track.write_text(''.join('\t'.join(map(str,r))+'\n' for r in case['segments']),newline='\n')
        command=prefix+['bdgpeakcall','-i',str(track),'-c',str(case['cutoff']),'-l',str(case['min_length']),'-g',str(case['max_gap']),'-o','peaks.narrowPeak','--outdir',str(here),'--no-trackline','--verbose','0']
        result=subprocess.run(command,env=env,capture_output=True,text=True,timeout=90);(here/'run.log').write_text(result.stdout+result.stderr,encoding='utf-8')
        assert result.returncode==0,(case['id'],result.returncode,result.stderr)
        rows=[]
        for line in(here/'peaks.narrowPeak').read_text().splitlines():
            if line.startswith('track'):continue
            f=line.split('\t');assert len(f)==10
            rows.append([f[0],int(f[1]),int(f[2]),int(f[9])])
        sizes={}
        for chrom,_,end,_ in case['segments']:sizes[chrom]=max(sizes.get(chrom,0),end)
        return {**case,'reference':sizes,'expected':rows,'upstream_output_sha256':sha(here/'peaks.narrowPeak'),'command':command,'log_sha256':sha(here/'run.log')}
    with ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(execute,cases))
    assert len(results)==129
    DEST.mkdir(parents=True,exist_ok=True)
    target=DEST/'macs3-3.1.0-bdgpeakcall.json'
    fixture={'schema_version':1,'version':probe.stdout.strip(),'source_sha256':'aae548b86ab652717dca281fa2e33645fc1dc8091264ccbf7ce09cf8a13532e0','scope':'Actual bdgpeakcall positive-cutoff geometry/summit references, complete continuous tracks, float32 semantics. No callpeak, HMMRATAC, negative/zero cutoff summit, old-version or whole-paper parity claim. Windows portability patches do not modify BedGraph/PeakIO modules.','cases':[{k:v for k,v in r.items() if k not in ['command','log_sha256']} for r in results]}
    assert not target.exists(),'Preserve an existing frozen reference; choose a new version/path for regeneration.'
    target.write_text(json.dumps(fixture,indent=2)+'\n',encoding='utf-8')
    evidence={'status':'passed','cases':len(results),'version':probe.stdout.strip(),'runtime_python_sha256':sha(args.python),'macs_script_sha256':sha(args.macs_script),'fixture_sha256':sha(target),'cache':str(cache),'commands':[{k:r[k] for k in ['id','command','log_sha256','upstream_output_sha256']} for r in results]}
    (cache/'verification.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in evidence.items() if k!='commands'}))

if __name__=='__main__':main()
