"""Retained executable STAR checks with independent reference, splice, mate and count truth."""
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


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--star',type=Path,default=ROOT/'build/omics-star/bin/STAR.exe');args=parser.parse_args()
    out=ROOT/'build/literature-review'/('star-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));out.mkdir(parents=True);attempts=[]
    def run(name,method,*extra,expected=0,runtime=None):
        dest=out/name;command=['java','-jar',str(args.jar.resolve()),'star-tools','--method',method,'--out',str(dest),'--no-config']
        if method!='inspect-gtf':command+=['--star',str((runtime or args.star).resolve())]
        command+=extra;p=subprocess.run(command,capture_output=True,text=True,timeout=180,cwd=ROOT)
        (out/(name+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
        attempts.append({'name':name,'command':command,'exit_code':p.returncode,'expected_exit':expected,'output':str(dest)})
        if p.returncode!=expected:
            detail=(dest/'WARNINGS.md').read_text(encoding='utf-8') if (dest/'WARNINGS.md').exists() else ''
            raise AssertionError(name+': '+p.stdout+p.stderr+detail)
        if expected==0:assert(dest/'results.tsv').is_file()
        else:assert not(dest/'results.tsv').exists() and 'Required action' in(dest/'WARNINGS.md').read_text(encoding='utf-8')
        return dest
    def fastq(path,reads,mate=1):
        text=''.join('@r'+str(i)+'/'+str(mate)+' fixture\n'+seq+'\n+\n'+quality+'\n' for i,(seq,quality) in enumerate(reads))
        if path.suffix=='.gz':path.write_bytes(gzip.compress(text.encode('ascii'),mtime=0))
        else:path.write_text(text,encoding='ascii',newline='\n')
        return path
    def sam(dest):return [line.split('\t') for line in(dest/'rna.Aligned.out.sam').read_text().splitlines() if not line.startswith('@')]
    def counts(dest):return {row[0]:[int(x) for x in row[1:]] for row in(line.split('\t') for line in(dest/'rna.ReadsPerGene.out.tab').read_text().splitlines())}
    def reverse(s):return s.translate(str.maketrans('ACGTN','TGCAN'))[::-1]
    r=random.Random(772391);dna=''.join(r.choice('ACGT') for _ in range(5000));dna=dna[:400]+'GT'+dna[402:698]+'AG'+dna[700:]
    fasta=out/'reference with spaces.fa';fasta.write_text('>chrRNA\r\n'+'\r\n'.join(dna[i:i+60] for i in range(0,len(dna),60))+'\r\n',encoding='ascii',newline='')
    gtf=out/'genes.gtf';gtf.write_text('chrRNA\ttruth\texon\t201\t400\t.\t+\t.\tgene_id "GENE_A"; transcript_id "TRANSCRIPT_A";\nchrRNA\ttruth\texon\t701\t1000\t.\t+\t.\tgene_id "GENE_A"; transcript_id "TRANSCRIPT_A";\n',encoding='ascii',newline='\n')
    quality=''.join(chr(34+i%40) for i in range(100));junction=dna[350:400]+dna[700:750]
    reads=[(junction.lower(),quality),(reverse(dna[780:880]),quality),('N'*100,quality)]
    fq=fastq(out/'single.fastq.gz',reads)
    status,error='failed',None
    try:
        native=run('native-annotation','inspect-gtf','--reference',str(fasta),'--gtf',str(gtf));assert 'exons\t2' in(native/'results.tsv').read_text()
        idx=run('index with spaces','index','--reference',str(fasta),'--gtf',str(gtf),'--sjdb-overhang','99','--memory-mb','512')
        result=run('single','align','--index',str(idx),'--reads',str(fq),'--sample','RNA1','--gene-counts','true','--unmapped-fastq','true')
        rows=sam(result);assert len(rows)==3 and rows[0][3:6]==['351','255','50M300N50M']
        assert int(rows[1][1])&16 and int(rows[2][1])&4
        for row,(seq,q) in zip(rows,reads):
            assert row[9]==(reverse(seq.upper()) if int(row[1])&16 else seq.upper())
            assert row[10]==(q[::-1] if int(row[1])&16 else q)
        assert counts(result)['GENE_A']==[2,1,1] and counts(result)['N_unmapped']==[1,1,1]
        assert 'read-1 strand' in(result/'WARNINGS.md').read_text() and not(result/'reads1.fastq').exists()
        assert (result/'rna.Unmapped.out.mate1').is_file()
        basic=run('basic-two-pass','align','--index',str(idx),'--reads',str(fq),'--sample','RNA1','--gene-counts','true','--two-pass','basic')
        assert sam(basic)[0][5]=='50M300N50M' and counts(basic)==counts(result)
        filtered=run('by-sj-filtered','align','--index',str(idx),'--reads',str(fq),'--sample','RNA1','--gene-counts','true','--filter-type','by-sj-out','--intron-motifs','canonical-only','--multimap-max','1','--match-min','100','--score-min','100','--match-fraction','0','--score-fraction','0')
        assert next(row for row in sam(filtered) if row[0]=='r0')[5]=='50M300N50M'
        assert 'Disk-backed sorted SHA-256' in(filtered/'session.yaml').read_text()
        second=run('explicit-second-pass','align','--index',str(idx),'--reads',str(fq),'--sample','RNA1','--gene-counts','true','--sj-junctions',str(result/'rna.SJ.out.tab'))
        assert sam(second)[0][5]=='50M300N50M' and counts(second)==counts(result)
        assert 'EXPLICIT SECOND PASS' in(second/'WARNINGS.md').read_text()
        a=fastq(out/'R1.fq.gz',[(junction,quality),(dna[730:830],quality),('N'*100,quality)])
        b=fastq(out/'R2.fq.gz',[(reverse(dna[850:950]),quality),('N'*100,quality),('N'*100,quality)],2)
        paired=run('paired','align','--index',str(idx),'--reads',str(a),'--reads2',str(b),'--sample','PAIRS','--gene-counts','true','--filter-type','by-sj-out','--score-fraction','0.4','--match-fraction','0.4','--threads','2')
        records=sam(paired);assert len(records)==6 and sum(bool(int(row[1])&4) for row in records)==3
        assert counts(paired)['GENE_A']==[2,2,0] and counts(paired)['N_unmapped']==[1,1,1]
        for row in records:
            mate=2 if int(row[1])&128 else 1;i=int(row[0][1:]);original=[(junction,quality),(dna[730:830],quality),('N'*100,quality)][i] if mate==1 else [(reverse(dna[850:950]),quality),('N'*100,quality),('N'*100,quality)][i]
            assert row[9]==(reverse(original[0]) if int(row[1])&16 else original[0]) and row[10]==(quality[::-1] if int(row[1])&16 else quality)
        noann=run('unannotated-index','index','--reference',str(fasta),'--memory-mb','512')
        one=fastq(out/'junction.fq',[(junction,quality)])
        novel=run('novel-basic','align','--index',str(noann),'--reads',str(one),'--sample','NOVEL','--two-pass','basic','--sjdb-overhang','99')
        assert sam(novel)[0][5]=='50M300N50M'
        run('no-overhang','align','--index',str(noann),'--reads',str(one),'--sample','NOVEL','--two-pass','basic',expected=2)
        run('no-annotation','align','--index',str(noann),'--reads',str(one),'--sample','NOVEL','--gene-counts','true',expected=2)
        corrupt=idx/'index/chrLength.txt';corrupt.write_text('4999\n')
        run('corrupt-index','align','--index',str(idx),'--reads',str(fq),'--sample','RNA1',expected=2)
        run('missing-runtime','index','--reference',str(fasta),'--memory-mb','512',runtime=out/'absent-STAR.exe',expected=3)
        bad=out/'wrong-contig.gtf';bad.write_text('chrOther\ttruth\texon\t1\t20\t.\t+\t.\tgene_id "g"; transcript_id "t";\n')
        run('wrong-contig','inspect-gtf','--reference',str(fasta),'--gtf',str(bad),expected=2)
        status='passed'
    except Exception as failure:error=str(failure)
    report={'status':status,'jar':str(args.jar.resolve()),'jar_sha256':digest(args.jar),'star_executable_sha256':digest(args.star),
        'source_patch':json.loads((ROOT/'build/literature-review/star-dependency/dependency-status.json').read_text(encoding='utf-8'))['source_patch'],
        'fixture_directory':str(out),'checks':len(attempts),'attempts':attempts,'error':error,
        'scope':'Independent CRLF reference, known 300-base intron, gzip/lowercase/variable qualities, reverse strand, stranded gene counts, multithreaded paired/orphan reads, normal/BySJout and Basic/manual/novel junction workflows. Missing runtime, corrupt index, absent overhang/annotation and wrong contig reject with prominent recovery. Local fixtures do not establish legacy version, STARsolo/WASP/whole-paper equivalence.'}
    target=DATA/'star-packaged-validation.json'
    if target.exists():
        old=json.loads(target.read_text(encoding='utf-8'));report['validation_history']=old.get('validation_history',[])+[{k:v for k,v in old.items() if k!='validation_history'}]
    target.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');(out/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':status,'checks':len(attempts),'error':error,'fixture_directory':str(out)}));return 0 if status=='passed' else 1


if __name__=='__main__':raise SystemExit(main())
