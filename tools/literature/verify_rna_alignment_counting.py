"""Executable-JAR fixtures with independent sequence/coordinate/count truth; no pipeline parity."""
import argparse
import csv
import datetime as dt
import hashlib
import json
from pathlib import Path
import random
import subprocess

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def table(p):
    with p.open(encoding='utf-8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))
def reverse(s):return s.translate(str.maketrans('ACGTN','TGCAN'))[::-1]


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--jar',type=Path,required=True);args=ap.parse_args();jar=args.jar.resolve()
    out=ROOT/'build/literature-review'/('rna-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));out.mkdir(parents=True)
    steps=[];checks=[];original={}
    def write(name,text):
        p=out/name;p.write_text(text,encoding='utf-8',newline='\n');original[p]=sha(p);return p
    def fastq(name,seqs,mate=0):return write(name,''.join('@q'+str(i)+('/'+str(mate) if mate else '')+'\n'+s+'\n+\n'+'I'*len(s)+'\n' for i,s in enumerate(seqs)))
    def run(name,route,opts,expected=0):
        dest=out/name;command=['java','-jar',str(jar),route,*map(str,opts),'--out',str(dest),'--no-config']
        child=subprocess.run(command,cwd=ROOT,capture_output=True,text=True,timeout=300);log=out/(name+'.console.log');log.write_text(child.stdout+child.stderr,encoding='utf-8')
        steps.append(dict(name=name,command=command,exit_code=child.returncode,expected_exit=expected,output=str(dest),console_sha256=sha(log)))
        assert child.returncode==expected,(name,child.stdout,child.stderr,(dest/'WARNINGS.md').read_text(encoding='utf-8') if (dest/'WARNINGS.md').exists() else '')
        status=json.loads((dest/'analysis-status.json').read_text())['status'];assert status.startswith('completed') if expected==0 else status==('skipped-dependency-unavailable' if expected==3 else 'failed')
        assert (dest/'results.tsv').is_file() if expected==0 else not(dest/'results.tsv').exists()
        if expected:assert 'Required action:' in (dest/'WARNINGS.md').read_text(encoding='utf-8')
        return dest
    def sam(p):return [line.split('\t') for line in p.read_text(encoding='utf-8').splitlines() if line and not line.startswith('@')]
    rng=random.Random(2976);dna=''.join(rng.choice('ACGT') for _ in range(10000));dna=dna[:400]+'GT'+dna[402:598]+'AG'+dna[600:]
    ref=write('reference with spaces.fa','>chr1\n'+dna+'\n');gtf=write('genes.gtf','chr1\ttest\texon\t201\t400\t.\t+\t.\tgene_id "G1"; transcript_id "T1";\nchr1\ttest\texon\t601\t900\t.\t+\t.\tgene_id "G1"; transcript_id "T1";\nchr1\ttest\texon\t1001\t1200\t.\t-\t.\tgene_id "G2"; transcript_id "T2";\n')
    status,error='failed',None
    try:
        a=run('annotation','feature-counts',['--method','annotation','--reference',ref,'--annotation',gtf]);assert {r['feature_id']:r['union_length'] for r in table(a/'results.tsv')}=={'G1':'500','G2':'200'};checks.append('independent genomic exon union lengths')
        a=run('splice-sites','feature-counts',['--method','splice-sites','--reference',ref,'--annotation',gtf]);assert(a/'splice-sites.tsv').read_text()=='chr1\t399\t600\t+\n';checks.append('zero-based exonic splice flanks')
        c=write('normalization.tsv','feature_id\tlength\tS\ng1\t1000\t10\ng2\t2000\t20\n')
        a=run('tpm','feature-counts',['--method','normalize','--counts',c,'--normalization','tpm']);assert [float(r['S']) for r in table(a/'results.tsv')]==[500000,500000];checks.append('hand-calculated gene TPM')
        a=run('fpkm','feature-counts',['--method','normalize','--counts',c,'--normalization','fpkm','--library-units','1000000']);assert [float(r['S']) for r in table(a/'results.tsv')]==[10,10];checks.append('explicit fragment-denominator FPKM')
        # Independent hand-written alignments: one split plus one reverse read.
        records='@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:10000\n'
        records+='split\t0\tchr1\t351\t60\t50M200N50M\t*\t0\t0\t'+dna[350:400]+dna[600:650]+'\t'+'I'*100+'\tNH:i:1\n'
        records+='reverse\t16\tchr1\t1051\t60\t100M\t*\t0\t0\t'+dna[1050:1150]+'\t'+'I'*100+'\tNH:i:1\n'
        align=write('raw.sam',records);inputs=write('samples.tsv','sample\talignment\tstrand\nS1\t'+align.name+'\t0\n');home=ROOT/'build/omics-featurecounts'
        common=['--method','count','--home',home,'--inputs',inputs,'--annotation',gtf,'--library','single','--unit','reads']
        a=run('raw-gene-junction-counts','feature-counts',common+['--junctions','true']);assert {r['feature_id']:float(r['S1']) for r in table(a/'counts.tsv')}=={'G1':1,'G2':1};j=table(a/'junction-counts.tsv');assert len(j)==1 and j[0]['Location_SP1']=='400' and j[0]['Location_SP2']=='601' and j[0]['S1']=='1';checks.append('independent raw gene and exact junction counts')
        peak=write('peaks.saf','GeneID\tChr\tStart\tEnd\tStrand\np1\tchr1\t350\t400\t.\np2\tchr1\t1050\t1200\t.\n')
        a=run('peak-counts','feature-counts',['--method','count','--home',home,'--inputs',inputs,'--annotation',peak,'--format','SAF','--library','single','--unit','reads']);assert [float(r['S1']) for r in table(a/'counts.tsv')]==[1,1];checks.append('SAF ATAC-style peak count contract')
        pairs='@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:10000\n'
        pairs+='pair\t99\tchr1\t211\t60\t50M\t=\t311\t150\t'+dna[210:260]+'\t'+'I'*50+'\tNH:i:1\n'
        pairs+='pair\t147\tchr1\t311\t60\t50M\t=\t211\t-150\t'+dna[310:360]+'\t'+'I'*50+'\tNH:i:1\n'
        pair=write('pairs.sam',pairs);pairinput=write('paired.tsv','sample\talignment\tstrand\nPAIR\t'+pair.name+'\t0\n')
        for unit,expected in [('reads',2),('fragments',1)]:
            a=run('paired-'+unit,'feature-counts',['--method','count','--home',home,'--inputs',pairinput,'--annotation',gtf,'--library','paired','--unit',unit]);assert float(next(r for r in table(a/'counts.tsv') if r['feature_id']=='G1')['PAIR'])==expected
        checks.append('independent two mates versus one fragment')
        mm=ROOT/'build/omics-minimap2';hifi=fastq('hifi.fq',[dna[2000:4000],reverse(dna[5000:7000]),'N'*1000]);mmcommon=['--home',mm,'--reference',ref,'--query-format','fastq','--format','sam']
        a=run('hifi','minimap-tools',mmcommon+['--query',hifi,'--preset','map-hifi','--eqx','true','--cs','short']);r=sam(a/'alignment.sam');assert [x[3] for x in r]==['2001','5001','0'] and r[0][5]=='2000=' and int(r[1][1])&16 and int(r[2][1])&4;assert 'MD instead of cs' in(a/'WARNINGS.md').read_text();checks.append('HiFi exact/reverse/unmapped content and tag precedence')
        mutant=list(dna[2000:4000]);mutant[500]='A' if mutant[500]!='A' else 'C';mutant.insert(1000,'G');del mutant[1500];ont=fastq('ont.fq',[''.join(mutant)])
        a=run('ont-mutated','minimap-tools',mmcommon+['--query',ont,'--preset','map-ont','--md','false','--cs','long']);r=sam(a/'alignment.sam');assert len(r)==1 and r[0][2]=='chr1' and r[0][3]=='2001' and 'I' in r[0][5] and 'D' in r[0][5] and any(x.startswith('cs:Z:') for x in r[0][11:]);checks.append('ONT preset with independent substituted/inserted/deleted query')
        rna=fastq('rna.fq',[dna[350:400]+dna[600:650]]);a=run('minimap-splice','minimap-tools',mmcommon+['--query',rna,'--preset','splice','--noncanonical-penalty','0']);r=sam(a/'alignment.sam');assert r[0][3]=='351' and r[0][5]=='50M200N50M';checks.append('minimap splice geometry')
        assembly=write('assemblies.fa','>plus\n'+dna[2000:5000]+'\n>minus\n'+reverse(dna[6000:9000])+'\n>unmapped\n'+'N'*3000+'\n')
        a=run('assembly-paf','minimap-tools',['--home',mm,'--reference',ref,'--query',assembly,'--query-format','fasta','--format','paf','--preset','asm20','--secondary','false','--bed','true']);bed=(a/'mappings.bed').read_text();assert 'chr1\t2000\t5000\tplus\t60\t+' in bed and 'chr1\t6000\t9000\tminus\t60\t-' in bed;assert 'unmapped\t3000\tunmapped' in(a/'query-status.tsv').read_text();checks.append('assembly PAF forward/reverse target geometry and unmapped status')
        hh=ROOT/'build/omics-hisat2';a=run('hisat-index','hisat-tools',['--method','index','--home',hh,'--reference',ref,'--annotation',gtf]);index=a
        a=run('hisat-rna','hisat-tools',['--method','align','--home',hh,'--index',index,'--reads',rna,'--sample','SAMPLE','--strand','F','--dta','true','--max-intron','40000']);r=sam(a/'alignment.sam');assert r[0][3]=='351' and r[0][5]=='50M200N50M';assert '213 compiler warnings' in(a/'WARNINGS.md').read_text();checks.append('annotated HISAT2 graph and DTA splice alignment with build warnings')
        r1=fastq('R1.fq',[dna[2000:2100]],1);r2=fastq('R2.fq',[reverse(dna[2300:2400])],2)
        a=run('hisat-pairs','hisat-tools',['--method','align','--home',hh,'--index',index,'--reads',r1,'--reads2',r2,'--sample','PAIR','--strand','RF']);r=sam(a/'alignment.sam');assert len(r)==2 and all(int(x[1])&1 for x in r) and {x[3] for x in r}=={'2001','2301'};checks.append('paired HISAT2 strand/RG/coordinate contract')
        for route,opts in [('feature-counts',['--method','count','--inputs',inputs,'--annotation',gtf,'--library','single','--unit','reads']),('minimap-tools',['--reference',ref,'--query',hifi,'--query-format','fastq','--format','sam','--preset','map-hifi']),('hisat-tools',['--method','index','--reference',ref])]:run('unavailable-'+route,route,['--home',out/'absent',*opts],3)
        checks.append('all three missing runtime skips preserve actionable recovery')
        assert all(sha(p)==before for p,before in original.items());status='passed'
    except Exception as failure:error=repr(failure)
    record=dict(status=status,error=error,jar=str(jar),jar_sha256=sha(jar),steps=steps,checks=checks,check_count=len(checks),fixture_directory=str(out),verifier_sha256=sha(Path(__file__)),recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),scope='Independent hand-written count/SAM fixtures, independently generated sequences and known coordinate/strand/splice/mutation truth through the executable JAR; typed adapter/native helper subset, not paper/old-version parity')
    report=DATA/'rna-alignment-counting-packaged-validation.json'
    if report.exists():
        old=json.loads(report.read_text(encoding='utf-8'));record['validation_history']=old.get('validation_history',[])+[{k:v for k,v in old.items() if k!='validation_history'}]
    report.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');(out/'validation.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps(dict(status=status,steps=len(steps),checks=len(checks),error=error)));return 0 if status=='passed' else 1


if __name__=='__main__':raise SystemExit(main())
