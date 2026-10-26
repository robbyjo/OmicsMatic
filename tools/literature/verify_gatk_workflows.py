"""Retained executable checks with independently generated SNP/indel and gVCF truth."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import random
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--gatk',type=Path,default=ROOT/'build/omics-gatk/gatk.jar');parser.add_argument('--picard',type=Path,default=ROOT/'build/omics-picard/picard.jar');args=parser.parse_args()
    out=ROOT/'build/literature-review'/('gatk-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));out.mkdir(parents=True);attempts=[]

    def run(name,method,*extra,expected=0):
        dest=out/name;command=['java','-jar',str(args.jar.resolve()),'gatk-tools','--method',method,'--out',str(dest),'--no-config']
        if method!='reference':command+=['--gatk',str(args.gatk.resolve()),'--memory-mb','512']
        command+=extra;p=subprocess.run(command,capture_output=True,text=True,timeout=180,cwd=ROOT)
        (out/(name+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
        attempts.append({'name':name,'command':command,'exit_code':p.returncode,'expected_exit':expected,'output':str(dest)})
        if p.returncode!=expected:
            detail=(dest/'WARNINGS.md').read_text() if (dest/'WARNINGS.md').exists() else ''
            raise AssertionError(name+': '+p.stdout+p.stderr+detail)
        if expected==0:assert(dest/'results.tsv').is_file()
        else:assert not(dest/'results.tsv').exists() and 'Required action' in(dest/'WARNINGS.md').read_text()
        return dest

    def vcf(path):
        lines=path.read_text().splitlines();header=next(x for x in lines if x.startswith('#CHROM')).split('\t')
        return header[9:],[x.split('\t') for x in lines if not x.startswith('#')]

    def genotype(row,sample_index):return row[9+sample_index].split(':')[row[8].split(':').index('GT')]

    rng=random.Random(118729);sequence=list(''.join(rng.choice('ACGT') for _ in range(1200)))
    sequence[649:653]=list('ACTG');sequence=''.join(sequence);alternate='C' if sequence[349]=='A' else 'A'
    fasta=out/'reference with spaces.fa';fasta.write_text('>chr1\r\n'+'\r\n'.join(sequence[i:i+60] for i in range(0,len(sequence),60))+'\r\n',newline='')

    def bam(sample,all_alt):
        sam=out/(sample+'.sam');text='@HD\tVN:1.6\tSO:coordinate\n@SQ\tSN:chr1\tLN:1200\n@RG\tID:rg\tSM:'+sample+'\tLB:lib\tPL:ILLUMINA\n'
        for site in [350,650]:
            for start in range(site-150,site-120):
                for copy in range(4):
                    alt=all_alt or copy%2==0;cigar='200M';read=sequence[start:start+200]
                    if alt:
                        if site==350:read=read[:349-start]+alternate+read[350-start:]
                        else:
                            left=650-start;cigar=str(left)+'M3D'+str(200-left)+'M';read=sequence[start:650]+sequence[653:start+203]
                    text+=sample+'_'+str(site)+'_'+str(start)+'_'+str(copy)+'\t0\tchr1\t'+str(start+1)+'\t60\t'+cigar+'\t*\t0\t0\t'+read+'\t'+'I'*200+'\tRG:Z:rg\tNM:i:'+str((1 if site==350 else 3) if alt else 0)+'\n'
        sam.write_text(text,newline='\n');dest=out/(sample+'-bam')
        command=['java','-jar',str(args.jar.resolve()),'alignment-tools','--method','sort','--input',str(sam),'--picard',str(args.picard.resolve()),'--create-index','true','--memory-mb','512','--out',str(dest),'--no-config']
        p=subprocess.run(command,capture_output=True,text=True,timeout=120);(out/(sample+'-bam.log')).write_text(p.stdout+p.stderr)
        assert p.returncode==0,(command,p.stdout,p.stderr)
        return dest/'alignment.bam'

    status,error='failed',None
    try:
        bundle=run('reference','reference','--reference',str(fasta))
        shutil.copy2(bundle/(fasta.name+'.fai'),Path(str(fasta)+'.fai'));shutil.copy2(bundle/'reference with spaces.dict',out/'reference with spaces.dict')
        bams={sample:bam(sample,sample=='S2') for sample in ['S1','S2']}
        for sample,bam_path in bams.items():
            dest=run('gvcf-'+sample,'haplotype-call','--reference',str(fasta),'--input',str(bam_path),'--ploidy','2','--emit','gvcf')
            assert 'INTERMEDIATE GVCF' in(dest/'WARNINGS.md').read_text()
            assert any('<NON_REF>' in row[4] for row in vcf(dest/'variants.g.vcf')[1])
        inputs=out/'gvcfs.txt';inputs.write_text('gvcf-S2/variants.g.vcf\ngvcf-S1/variants.g.vcf\n',newline='\n')
        combined=run('combined','combine-gvcfs','--reference',str(fasta),'--inputs-list',str(inputs))
        assert 'INTERMEDIATE COMBINED GVCF' in(combined/'WARNINGS.md').read_text()
        called=run('joint','genotype-gvcfs','--reference',str(fasta),'--input',str(combined/'combined.g.vcf'),'--ploidy','2')
        samples,rows=vcf(called/'variants.vcf');assert samples==['S1','S2'] and len(rows)==2
        bypos={int(row[1]):row for row in rows};assert bypos[350][3:5]==[sequence[349],alternate];assert bypos[650][3:5]==['ACTG','A']
        for row in rows:assert genotype(row,0)=='0/1' and genotype(row,1)=='1/1'
        haploid=run('haploid-interval','haplotype-call','--reference',str(fasta),'--input',str(bams['S2']),'--ploidy','1','--region','chr1:150-500')
        _,rows=vcf(haploid/'variants.vcf');assert len(rows)==1 and int(rows[0][1])==350 and genotype(rows[0],0)=='1'
        tetraploid=run('tetraploid','haplotype-call','--reference',str(fasta),'--input',str(bams['S1']),'--ploidy','4')
        _,rows=vcf(tetraploid/'variants.vcf');assert len(rows)==2
        assert all(sorted(genotype(row,0).replace('|','/').split('/'))==['0','0','1','1'] for row in rows)
        annotated=out/'annotated.vcf';annotated.write_text('##fileformat=VCFv4.2\n##contig=<ID=chr1,length=1200>\n##FILTER=<ID=OLD,Description=Existing_filter>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=Genotype>\n'+''.join('##INFO=<ID='+key+',Number=1,Type=Float,Description=Metric>\n' for key in ['QD','FS','MQ','MQRankSum','ReadPosRankSum'])+'#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS1\n'
            +'chr1\t10\tgood\tA\tC\t60\tPASS\tQD=2;FS=60;MQ=40;MQRankSum=-12.5;ReadPosRankSum=-8\tGT\t0/1\n'
            +'chr1\t20\tsnp\tA\tG\t60\tOLD\tQD=1;FS=0;MQ=60;MQRankSum=0;ReadPosRankSum=0\tGT\t1/1\n'
            +'chr1\t30\tindel\tAC\tA\t60\tPASS\tQD=3;FS=201;ReadPosRankSum=0\tGT\t0/1\n'
            +'chr1\t40\tmissing\tC\tG\t60\tPASS\tFS=0;MQ=60;MQRankSum=0;ReadPosRankSum=0\tGT\t0/0\n',newline='\n')
        for policy in ['pass','fail']:
            dest=run('filters-'+policy,'hard-filter','--input',str(annotated),'--missing-policy',policy)
            _,rows=vcf(dest/'variants.vcf');assert len(rows)==4 and rows[0][6]=='PASS'
            assert set(rows[1][6].split(';'))=={'OLD','JL_SNP_HARD_FILTER'} and rows[2][6]=='JL_INDEL_HARD_FILTER'
            assert rows[3][6]==('PASS' if policy=='pass' else 'JL_SNP_HARD_FILTER')
            assert 'lack one or more requested annotations' in(dest/'WARNINGS.md').read_text()
        run('reject-ordinary-vcf','genotype-gvcfs','--reference',str(fasta),'--input',str(called/'variants.vcf'),'--ploidy','2',expected=2)
        shutil.copy2(out/'gvcf-S1/variants.g.vcf',out/'same-sample.g.vcf');inputs.write_text('gvcf-S1/variants.g.vcf\nsame-sample.g.vcf\n',newline='\n')
        run('reject-duplicate-sample','combine-gvcfs','--reference',str(fasta),'--inputs-list',str(inputs),expected=2)
        Path(str(fasta)+'.fai').write_text('chr1\t1200\t7\t60\t61\n',newline='\n')
        run('reject-stale-index','haplotype-call','--reference',str(fasta),'--input',str(bams['S1']),'--ploidy','2',expected=2)
        status='passed'
    except Exception as failure:error=str(failure)
    record={'status':status,'error':error,'jar':str(args.jar.resolve()),'jar_sha256':digest(args.jar),'gatk_jar_sha256':digest(args.gatk),
        'attempts':attempts,'passed':len(attempts) if status=='passed' else None,'fixture_directory':str(out),
        'scope':'Independent known heterozygous/homozygous SNP and 3-bp deletion truth, explicit haploid interval and tetraploid dosage, real gVCF combination/genotyping, annotation boundaries/missing policies, preserved old filters and rejected invalid contracts. No legacy/somatic/complete-paper or whole-genome parity claim.'}
    path=DATA/'gatk-packaged-validation.json'
    if path.exists():
        previous=json.loads(path.read_text());record['history']=previous.get('history',[])+[{k:v for k,v in previous.items() if k!='history'}]
    path.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps({k:v for k,v in record.items() if k not in ['attempts','history']}));return 0 if status=='passed' else 1


if __name__=='__main__':raise SystemExit(main())
