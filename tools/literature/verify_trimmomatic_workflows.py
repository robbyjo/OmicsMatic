"""Real executable-JAR trimming, independent insert truth and recovery checks."""
import argparse,datetime as dt,gzip,hashlib,json,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];DATA=ROOT/'docs/literature/omics-2023-2026'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def reads(path):
    with gzip.open(path,'rt',encoding='utf-8') as f:lines=f.read().splitlines()
    assert len(lines)%4==0
    return [(lines[i],lines[i+1],lines[i+3]) for i in range(0,len(lines),4)]
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--java21',type=Path,default=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'));a=ap.parse_args();jar=a.jar.resolve();initial=sha(jar);out=ROOT/'build/literature-review'/('trimmomatic-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'));out.mkdir();steps=[];checks=[];sources={};status='failed';error=None
    def file(name,body):
        p=out/name;p.write_text(body,encoding='utf-8');sources[p]=sha(p);return p
    def pipeline(name,body):return file(name+'.tsv','step\targuments\n'+body)
    def fastq(name,rows):return file(name+'.fq',''.join('@'+n+'\n'+s+'\n+\n'+q+'\n' for n,s,q in rows))
    def run(name,engine,first,pipe,second=None,version='0.41',expected=0,extra=()):
        dest=out/name;cmd=['java','-jar',str(jar),'trimmomatic-tools','--engine',engine,'--reads',str(first),'--steps',str(pipe),'--phred','33','--out',str(dest),'--no-config']
        if second:cmd+=['--reads2',str(second)]
        if engine=='upstream':cmd+=['--home',str(ROOT/'build/omics-trimmomatic'/version)]
        cmd+=list(map(str,extra));p=subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,timeout=150);log=out/(name+'.console.log');log.write_text(p.stdout+p.stderr,encoding='utf-8');steps.append(dict(name=name,command=cmd,exit_code=p.returncode,expected_exit=expected,output=str(dest),console_sha256=sha(log)));assert p.returncode==expected,(name,p.returncode,p.stdout,p.stderr,(dest/'WARNINGS.md').read_text() if (dest/'WARNINGS.md').exists() else '')
        state=json.loads((dest/'analysis-status.json').read_text())['status'];assert state.startswith('completed') if expected==0 else state==('failed' if expected==2 else 'skipped-dependency-unavailable');assert (dest/'results.tsv').exists() if expected==0 else not(dest/'results.tsv').exists()
        if expected:assert 'Required action:' in (dest/'WARNINGS.md').read_text()
        return dest
    try:
        with zipfile.ZipFile(jar) as z:
            assert 'org/jlinalg/sequence/ReadQualityTrimmer.class' in z.namelist();assert 'META-INF/NOTICE-Trimmomatic.txt' in z.namelist();assert 'META-INF/LICENSE-Trimmomatic.txt' in z.namelist();assert not any(n.endswith('.fa') and 'adapters/' in n for n in z.namelist());assert not any(n.endswith(('trimmomatic-0.39.jar','trimmomatic-0.41.jar','java.exe')) for n in z.namelist())
        checks.append('native class/GPL attribution packaged; no stock Illumina sequences or external program/JVM redistributed')
        names=['both','first','second','drop'];first=fastq('r1',[(n+'/1 comment '+str(i),'ACGTACGT','I'*8 if i in [0,1] else '!'*8) for i,n in enumerate(names)]);second=fastq('r2',[(n+'/2 comment '+str(i),'TGCATGCA','I'*8 if i in [0,2] else '!'*8) for i,n in enumerate(names)]);pipe=pipeline('paired','LEADING\t20\nMINLEN\t2\n');native=run('paired-native','native',first,pipe,second);outputs=['read1.paired.fastq.gz','read1.unpaired.fastq.gz','read2.paired.fastq.gz','read2.unpaired.fastq.gz'];assert all(len(reads(native/f))==1 for f in outputs);assert 'ORPHANS RETAINED' in (native/'WARNINGS.md').read_text()
        for v in ['0.39','0.41']:
            d=run('paired-'+v,'upstream',first,pipe,second,v);assert all(reads(d/f)==reads(native/f) for f in outputs);assert (d/'read-status.tsv').read_text()==(native/'read-status.tsv').read_text();assert (d/'trim.log').read_text()==(native/'trim.log').read_text()
        checks.append('actual0.39/0.41 and native agree on exact headers/bases/qualities/spans and all four paired fates')
        adapter='GTCAGCTACGATTCGAGTCCATGCTACGATCAGATCGTAC';insert='ACGTCGATCGTAGCTAGGCTACGATGCATCGTACGTAGCA';fa=file('synthetic adapters.fa','>SyntheticAdapter\n'+adapter+'\n');source=fastq('adapter',[('insert',insert+adapter,'I'*(len(insert)+len(adapter)))]);clip=pipeline('clip','ILLUMINACLIP\t'+fa.name+'\t2\t30\t10\nMINLEN\t10\n')
        for v in ['0.39','0.41']:
            d=run('clip-'+v,'upstream',source,clip,version=v);assert reads(d/'trimmed.fastq.gz')==[('@insert',insert,'I'*len(insert))];assert (d/'adapters-step-0.fa').read_text()==fa.read_text()
        checks.append('both original programs recover exact independently constructed insert; space-containing adapter path safely staged')
        reverse=lambda s:s.translate(str.maketrans('ACGT','TGCA'))[::-1]
        p1='ACGTCAGCTAGTCGATCGTAGCTA';p2='TCGATGCATGCTAGCTACGATGCA';ins='GATCGTACAGCTAGCATGCTAGTAC';pal=file('palindrome.fa','>PrefixSynthetic/1\n'+p1+'\n>PrefixSynthetic/2\n'+p2+'\n');r1=fastq('pal1',[('pal/1',ins+reverse(p2),'I'*(len(ins)+len(p2)))]);r2=fastq('pal2',[('pal/2',reverse(ins)+reverse(p1),'I'*(len(ins)+len(p1)))])
        for v in ['0.39','0.41']:
            for keep in [False,True]:
                pipe=pipeline('pal-'+v+str(keep),'ILLUMINACLIP\t'+pal.name+'\t2\t30\t10\t2\t'+str(keep).lower()+'\nMINLEN\t5\n');d=run('pal-'+v+str(keep),'upstream',r1,pipe,r2,v)
                if keep:assert reads(d/outputs[0])[0][1]==ins and reads(d/outputs[2])[0][1]==reverse(ins)
                else:assert reads(d/outputs[1])[0][1]==ins and all(not reads(d/f) for f in [outputs[0],outputs[2],outputs[3]]);assert 'NO COMPLETE PAIRS' in (d/'WARNINGS.md').read_text()
        checks.append('original palindrome keep-both choices recover independent two-mate inserts or deliberate forward-only orphan')
        r=fastq('crop',[('crop comment','ACGTACGTACGTACGT','I'*16)]);p=pipeline('forward','HEADCROP\t10\n');d=run('forward','native',r,p);assert reads(d/'trimmed.fastq.gz')==[('@crop comment','GTACGT','I'*6)];checks.append('forward-only HEADCROP10 exact bases and header; reverse read is not silently modified')
        # Literal paper order allows reads below MINLEN after later window trimming.
        r=fastq('order',[('order','ACGT'*9+'AC','I'*20+'!'*18)]);p=pipeline('order','ILLUMINACLIP\t'+fa.name+'\t2\t30\t10\nMINLEN\t36\nSLIDINGWINDOW\t4\t20\n')
        for v in ['0.39','0.41']:
            d=run('literal-order-'+v,'upstream',r,p,version=v);assert 0<len(reads(d/'trimmed.fastq.gz')[0][1])<36
        checks.append('actual paper MINLEN36-before-window order retained; no hidden final length filter')
        r=fastq('max',[('max','ACGT'*30,'I'*70+'!'*50)]);p=pipeline('max','MAXINFO\t40\t0.5\nMINLEN\t20\n');d=run('max-native','native',r,p)
        for v in ['0.39','0.41']:assert reads(run('max-'+v,'upstream',r,p,version=v)/'trimmed.fastq.gz')==reads(d/'trimmed.fastq.gz')
        checks.append('native MAXINFO retained span/qualities match both real programs')
        p=pipeline('none','MINLEN\t1000\n');d=run('all-filtered','native',r,p);assert reads(d/'trimmed.fastq.gz')==[];assert 'NO READS RETAINED' in (d/'WARNINGS.md').read_text();checks.append('all-filtered completed output has prominent no-reads warning')
        r=fastq('one',[('one','A','I')]);p=pipeline('trailing','TRAILING\t20\n');d=run('one-base','native',r,p);assert not reads(d/'trimmed.fastq.gz');assert 'TRAILING' in (d/'WARNINGS.md').read_text();checks.append('upstream one-base TRAILING rejection preserved and warned')
        r=fastq('long',[('long','A'*1001,'I'*1001)]);p=pipeline('long','MAXINFO\t40\t0.5\n');d=run('long-fails','native',r,p,expected=2);assert 'at most1000bases' in (d/'WARNINGS.md').read_text();checks.append('MAXINFO oversized input fails with retained partials and no silent crop')
        d=run('missing','upstream',out/'missing',p,version='absent',expected=3);assert 'install_trimmomatic.py' in (d/'WARNINGS.md').read_text()
        d=run('old-java','upstream',out/'missing',p,expected=3,extra=['--java',a.java21]);assert 'requires Java25' in (d/'WARNINGS.md').read_text();checks.append('absent runtime and actual Java21 skip before inputs with exact recovery, no successful result')
        assert all(sha(p)==h for p,h in sources.items());assert sha(jar)==initial;status='passed'
    except Exception as failure:error=repr(failure)
    record=dict(status=status,error=error,jar=str(jar),jar_sha256=sha(jar),steps=steps,checks=checks,check_count=len(checks),fixture_directory=str(out),verifier_sha256=sha(Path(__file__)),recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),scope='Actual executable-JAR and both original versions on independent synthetic data; native step parity has separate4236reference cases. No patient data or full historical/paper parity.')
    report=DATA/'trimmomatic-packaged-validation.json'
    if report.exists():
        old=json.loads(report.read_text());record['validation_history']=old.get('validation_history',[])+[{k:v for k,v in old.items() if k!='validation_history'}]
    report.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');(out/'validation.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps(dict(status=status,steps=len(steps),checks=len(checks),error=error)));return 0 if status=='passed' else 1
if __name__=='__main__':raise SystemExit(main())
