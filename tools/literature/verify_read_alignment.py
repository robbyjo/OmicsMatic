"""Retained executable checks against independent synthetic DNA/read origins."""
import argparse
import datetime as dt
import gzip
import hashlib
import json
from pathlib import Path
import random
import subprocess

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--bowtie-home',type=Path,default=ROOT/'build/omics-align-bowtie2/bowtie2-2.5.5-mingw-x86_64')
    parser.add_argument('--bwa',type=Path,default=ROOT/'build/omics-align-bwa/bin/bwa.exe');args=parser.parse_args()
    out=ROOT/'build/literature-review'/('alignment-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));out.mkdir(parents=True,exist_ok=False)
    attempts=[]
    def run(name,method,*extra,expected=0):
        destination=out/name
        command=['java','-jar',str(args.jar.resolve()),'read-align','--method',method,
            '--bowtie-home' if method.startswith('bowtie2') else '--bwa',str(args.bowtie_home.resolve() if method.startswith('bowtie2') else args.bwa.resolve()),
            '--out',str(destination),'--no-config',*extra]
        p=subprocess.run(command,capture_output=True,text=True,timeout=240,cwd=ROOT)
        (out/(name+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
        attempts.append({'name':name,'command':command,'exit_code':p.returncode,'expected_exit':expected,
            'status':'passed' if p.returncode==expected else 'skipped-dependency' if p.returncode==3 else 'failed','output':str(destination)})
        if p.returncode!=expected:raise AssertionError(name+': '+p.stdout+p.stderr)
        if expected==0:assert (destination/'results.tsv').is_file()
        else:assert not (destination/'results.tsv').exists() and 'Required action' in (destination/'WARNINGS.md').read_text()
        return destination
    def sam(directory):
        return [line.split('\t') for line in (directory/'alignment.sam').read_text(encoding='utf-8').splitlines() if not line.startswith('@')]
    def reverse(s):return s.translate(str.maketrans('ACGTN','TGCAN'))[::-1]
    def fq(name,rows):
        path=out/(name+'.fastq.gz')
        with gzip.open(path,'wt',newline='\n') as stream:
            for name,seq in rows:stream.write('@'+name+' comment\n'+seq+'\n+\n'+''.join(chr(63+i%15) for i in range(len(seq)))+'\n')
        return path
    rng=random.Random(193);chromosomes=[''.join(rng.choice('ACGT') for _ in range(6500)) for _ in range(2)]
    ref=out/'reference with spaces.fa';ref.write_text('>chr1 description\n'+chromosomes[0]+'\n>chr2\n'+chromosomes[1]+'\n',newline='\n')
    mismatch=list(chromosomes[0][1500:1600]);mismatch[50]='C' if mismatch[50]=='A' else 'A'
    single=fq('single',[('plus/1',chromosomes[0][100:200]),('minus/2',reverse(chromosomes[1][900:1000])),('mismatch',''.join(mismatch)),('unmapped','N'*100)])
    pairs1=fq('pairs1',[('pair'+str(i)+'/1',chromosomes[0][700+i*25:800+i*25]) for i in range(24)])
    pairs2=fq('pairs2',[('pair'+str(i)+'/2',reverse(chromosomes[0][1000+i*25:1100+i*25])) for i in range(24)])
    def check_single(directory):
        rows=[r for r in sam(directory) if int(r[1])&2304==0]
        assert len(rows)==4
        assert rows[0][2:4]==['chr1','101'] and rows[1][2:4]==['chr2','901'] and int(rows[1][1])&16
        assert rows[2][3]=='1501' and 'NM:i:1' in rows[2][11:]
        assert int(rows[3][1])&4
        assert rows[1][10]==''.join(chr(63+i%15) for i in range(100))[::-1]
    status,error='failed',None
    try:
        indexes={}
        for mode in ['small','large']:
            indexes[mode]=run('bowtie-index-'+mode,'bowtie2-index','--reference',str(ref),'--index-mode',mode)
            aligned=run('bowtie-single-'+mode,'bowtie2','--index',str(indexes[mode]),'--reads',str(single),'--sample','S1','--threads','2','--preset','very-sensitive')
            check_single(aligned)
        clipped=fq('clipped',[('clip','TTTTTTTT'+chromosomes[0][2000:2080]+'GGGGGGGGGGGG')])
        local=run('bowtie-local','bowtie2','--index',str(indexes['small']),'--reads',str(clipped),'--sample','S1','--mode','local','--preset','very-sensitive')
        assert 'S' in sam(local)[0][5] and not int(sam(local)[0][1])&4
        paired=run('bowtie-paired','bowtie2','--index',str(indexes['small']),'--reads',str(pairs1),'--reads2',str(pairs2),'--sample','S1','--threads','2','--preset','very-sensitive','--max-insert','700','--dovetail','true')
        assert len(sam(paired))==48 and all(int(r[1])&2 for r in sam(paired))
        d1=fq('dovetail1',[('dovetail/1',chromosomes[0][3000:3100])]);d2=fq('dovetail2',[('dovetail/2',reverse(chromosomes[0][2980:3080]))])
        for enabled in [False,True]:
            result=run('dovetail-'+str(enabled).lower(),'bowtie2','--index',str(indexes['small']),'--reads',str(d1),'--reads2',str(d2),'--sample','S1','--preset','very-sensitive','--dovetail',str(enabled).lower(),'--mixed','false','--discordant','false')
            rows=sam(result);assert len(rows)==2 and all(bool(int(r[1])&2)==enabled for r in rows)
        bwa_indexes={}
        for algorithm in ['auto','bwtsw']:
            bwa_indexes[algorithm]=run('bwa-index-'+algorithm,'bwa-index','--reference',str(ref),'--algorithm',algorithm)
            for method in ['bwa-mem','bwa-backtrack']:
                aligned=run(method+'-'+algorithm,method,'--index',str(bwa_indexes[algorithm]),'--reads',str(single),'--sample','S1','--threads','2')
                check_single(aligned)
        for method in ['bwa-mem','bwa-backtrack']:
            paired=run(method+'-paired',method,'--index',str(bwa_indexes['auto']),'--reads',str(pairs1),'--reads2',str(pairs2),'--sample','S1','--threads','2')
            rows=sam(paired);assert len(rows)==48 and all(int(r[1])&2 for r in rows)
            assert rows[0][3]=='701' and rows[1][3]=='1001'
        split=fq('split',[('chimera',chromosomes[0][2500:2600]+chromosomes[1][3500:3600])])
        for compatible in [False,True]:
            result=run('split-'+str(compatible).lower(),'bwa-mem','--index',str(bwa_indexes['auto']),'--reads',str(split),'--sample','S1','--mark-shorter-split-secondary',str(compatible).lower())
            rows=sam(result);assert len(rows)==2 and sum(int(r[1])&2304==0 for r in rows)==1
            assert any(int(r[1])&(256 if compatible else 2048) for r in rows)
        wrong=fq('wrong',[('other',chromosomes[0][300:400])])
        run('wrong-pair','bowtie2','--index',str(indexes['small']),'--reads',str(single),'--reads2',str(wrong),'--sample','S1',expected=2)
        damaged=indexes['small']/'reference.1.bt2'
        with damaged.open('ab') as f:f.write(b'corrupt')
        run('corrupt-index','bowtie2','--index',str(indexes['small']),'--reads',str(single),'--sample','S1',expected=2)
        status='passed'
    except Exception as failure:
        error=repr(failure)
        if attempts and attempts[-1]['exit_code']==3:status='skipped-dependency'
        (out/'WARNINGS.md').write_text('# ALIGNER VERIFICATION '+status.upper()+'\n\n'+error+'\n\nRequired action: inspect the named attempt, native logs and WARNINGS.md; correct the exact runtime/input error and rerun to a new output directory.\n',encoding='utf-8')
    result={'status':status,'error':error,'jar':str(args.jar.resolve()),'jar_sha256':hashlib.sha256(args.jar.read_bytes()).hexdigest(),
        'attempts':attempts,'passed':sum(a['status']=='passed' for a in attempts),'fixture_directory':str(out),
        'scope':'Actual pinned upstream aligners on independent known DNA origins, both index families/algorithms, input/strand qualities, pairs, local/dovetail and split reporting. No whole-genome performance or old-version/MEM2 equivalence claim.'}
    report=DATA/'alignment-packaged-validation.json'
    if report.exists():
        previous=json.loads(report.read_text(encoding='utf-8'));result['history']=previous.get('history',[])+[{k:v for k,v in previous.items() if k!='history'}]
    report.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps({k:v for k,v in result.items() if k not in {'attempts','history'}}))
    return 0 if status=='passed' else 3 if status.startswith('skipped') else 2


if __name__=='__main__':raise SystemExit(main())
