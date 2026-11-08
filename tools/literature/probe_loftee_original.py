# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged VEP105/LOFTEE on explicit synthetic FASTA/GTF/GERP resources and audit complete returned VCFs in Java."""
import argparse,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_loftee_reference import BASE,linux
from validate_mashr_native import JAVA21


def controls(out):
    home=out/'inputs';home.mkdir();sequence=list('A'*4000);rows=[]
    def transcript(name,exons,strand,coding=True):
        ordered=sorted(exons,reverse=strand=='-');total=sum(b-a+1 for a,b in ordered);dna='ATG'+'CAA'*((total-6)//3)+'TAA' if coding else 'A'*total;assert len(dna)==total
        transcript_positions=[pos for a,b in ordered for pos in (range(a,b+1) if strand=='+' else range(b,a-1,-1))]
        complement=str.maketrans('ACGT','TGCA')
        for base,pos in zip(dna,transcript_positions):sequence[pos-1]=base if strand=='+' else base.translate(complement)
        for (a,b),(c,d) in zip(sorted(exons),sorted(exons)[1:]):
            if strand=='+':sequence[b:b+2]=list('GT');sequence[c-3:c-1]=list('AG')
            else:sequence[b:b+2]=list('CT');sequence[c-3:c-1]=list('AC')
        start=min(a for a,b in exons);end=max(b for a,b in exons);biotype='protein_coding' if coding else 'lncRNA';attrs='gene_id "SYNGENE'+name+'"; transcript_id "SYNTX'+name+'"; gene_name "SYN'+name+'"; gene_biotype "'+biotype+'"; transcript_biotype "'+biotype+'";'
        def feature(kind,a,b,phase='.',extra=''):rows.append(('SYN1','synthetic',kind,a,b,'.',strand,phase,attrs+extra))
        feature('gene',start,end);feature('transcript',start,end)
        for index,(a,b) in enumerate(ordered,1):feature('exon',a,b,extra=' exon_number "'+str(index)+'"; exon_id "SYNEXON'+name+'_'+str(index)+'";')
        if coding:
            coding_positions=set(transcript_positions[:-3]);stop=transcript_positions[-3:];start_codon=transcript_positions[:3]
            for a,b in exons:
                positions=[pos for pos in range(a,b+1) if pos in coding_positions]
                if positions:feature('CDS',min(positions),max(positions),'0')
            feature('start_codon',min(start_codon),max(start_codon),'0');feature('stop_codon',min(stop),max(stop),'0')
    transcript('PLUS',[(101,400),(601,900)],'+');transcript('MINUS',[(1601,1900),(2101,2400)],'-');transcript('SHORT',[(1001,1150),(1161,1310)],'+');sequence[1151]='C';transcript('NONCODING',[(3001,3300)],'+',False);transcript('SINGLE',[(3501,3800)],'+')
    text=''.join(sequence);(home/'reference.fa').write_text('>SYN1 synthetic_reference_no_human_sequence_claim\n'+'\n'.join(text[i:i+80] for i in range(0,len(text),80))+'\n',encoding='ascii',newline='\n')
    priority={'gene':0,'transcript':1,'exon':2,'CDS':3,'start_codon':4,'stop_codon':5};rows.sort(key=lambda r:(r[3],priority[r[2]],r[4]));(home/'genes.gtf').write_text(''.join('\t'.join(map(str,row))+'\n' for row in rows),encoding='ascii',newline='\n');(home/'chrom.sizes').write_text('SYN1\t4000\n',encoding='ascii');(home/'gerp.bedGraph').write_text('SYN1\t0\t3400\t-2\nSYN1\t3400\t4000\t2\n',encoding='ascii')
    variants=[(104,'C','T','early-plus-stop'),(105,'A','C','missense'),(106,'A','G','synonymous'),(401,'G','A','donor'),(599,'A','C','acceptor'),(850,'C','T','late-plus-stop'),(1152,'C','T','short-intron-GC-to-GT'),(1550,'A','C','intergenic'),(1651,'G','A','late-minus-stop'),(2397,'G','A','early-minus-stop'),(3101,'A','G','noncoding'),(3504,'C','T','single-exon-stop'),(104,'C','T,A','multiallelic'),(104,'C','CA','frameshift-insertion'),(104,'CA','C','frameshift-deletion')]
    for pos,ref,alt,label in variants:assert text[pos-1:pos-1+len(ref)]==ref,(label,pos,text[pos-1:pos+2])
    (home/'input.vcf').write_text('##fileformat=VCFv4.2\n##contig=<ID=SYN1,length=4000>\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n'+''.join('SYN1\t'+str(pos)+'\t'+label+'\t'+ref+'\t'+alt+'\t.\tPASS\t.\n' for pos,ref,alt,label in sorted(variants)),encoding='ascii',newline='\n');save(home/'CONTROL-DESIGN.json',dict(kind='entirely-synthetic-4000-base-five-transcript-reference',variants=len(variants),transcripts=5,source_GERP_values={'first3400bases':-2,'last600bases':2},human_reference_or_paper_reproduction=False,variant_designs=[dict(position=p,ref=r,alt=a,purpose=n) for p,r,a,n in variants]));return home


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['environment','plugin','bioperl','build','out']:p.add_argument('--'+name,required=True,type=Path)
    a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);environment=a.environment.resolve();plugin=a.plugin.resolve();bioperl=a.bioperl.resolve();assert all(p.is_relative_to(BASE) for p in [environment,plugin,bioperl]);source=Path(__file__).resolve();(out/'probe-at-attempt.py').write_bytes(source.read_bytes());build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};pins={source.relative_to(ROOT).as_posix():pin(source),**build['source_pins']};plugin_pins={p.relative_to(plugin).as_posix():pin(p) for p in plugin.rglob('*') if p.is_file()};state=dict(status='FAIL2_ORIGINAL_LOFTEE_PROBE_INCOMPLETE',artifact=build['artifact'],source_pins=pins,plugin_source_pins=plugin_pins,analysis_design='synthetic genome only; no human/paper validation',LOFTEE_annotation_admitted=False,implementation_credit=False,whole_goal_complete=False);checks=[]
    share=list((environment/'share').glob('ensembl-vep-105.0-*'));assert len(share)==1;share=share[0];prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','-i','HOME='+linux(out),'PATH='+linux(environment/'bin')+':/usr/bin:/bin','LC_ALL=C','PERL5LIB='+':'.join(linux(p) for p in [plugin,share,bioperl]),'LD_LIBRARY_PATH='+linux(environment/'lib')]
    def run(label,command,stdout=None,timeout=300):
        target=out/(label+'.stdout') if stdout is None else stdout
        with target.open('xb') as first,(out/(label+'.stderr')).open('xb') as second:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=timeout)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,(label,r.returncode)
    try:
        home=controls(out);run('fasta-index',prefix+[linux(environment/'bin/samtools'),'faidx',linux(home/'reference.fa')]);run('gtf-compress',prefix+[linux(environment/'bin/bgzip'),'-c',linux(home/'genes.gtf')],stdout=home/'genes.gtf.gz');run('gtf-index',prefix+[linux(environment/'bin/tabix'),'-p','gff',linux(home/'genes.gtf.gz')]);converter=ROOT/'build/literature-review/homer-runtime/ucsc-bigwig-source-intake-1/bedGraphToBigWig';assert pin(converter)==dict(bytes=12989000,sha256='fe9ed8ed858731d22d9a3cefa99d192f51bff1496693599b2f8054fa350adb48');run('GERP-bigwig',['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',linux(converter),linux(home/'gerp.bedGraph'),linux(home/'chrom.sizes'),linux(home/'gerp.bw')])
        runner=out/'RunOriginalLofteeAudit.java';runner.write_text('''import java.nio.file.*;import java.io.*;import java.util.*;import org.jlinalg.genetics.LofteeVcfAudit;
public final class RunOriginalLofteeAudit{public static void main(String[]a)throws Exception{try(var v=new PrintWriter(Files.newBufferedWriter(Path.of(a[2])));var t=new PrintWriter(Files.newBufferedWriter(Path.of(a[3])))){v.println("row\\tchromosome\\tposition\\tid\\tref\\talts\\tannotations\\ttargets\\tHC\\tLC\\tOS");t.println("row\\tannotation\\tallele_number\\tchromosome\\tposition\\tid\\tref\\talt\\tgene\\tfeature\\tconsequence\\tconfidence\\tfilters\\tflags\\tinfo\\traw_CSQ");List<Path> logs=new ArrayList<>();for(int i=4;i<a.length;i++)logs.add(Path.of(a[i]));var s=LofteeVcfAudit.audit(Path.of(a[0]),Path.of(a[1]),logs,false,new LofteeVcfAudit.Limits(10000000,1000,10000,1000000),x->v.println(x.row()+"\\t"+x.chromosome()+"\\t"+x.position()+"\\t"+x.id()+"\\t"+x.reference()+"\\t"+x.alternates()+"\\t"+x.annotations()+"\\t"+x.targetTranscripts()+"\\t"+x.highConfidence()+"\\t"+x.lowConfidence()+"\\t"+x.otherSplice()),x->t.println(x.row()+"\\t"+x.annotation()+"\\t"+x.alleleNumber()+"\\t"+x.chromosome()+"\\t"+x.position()+"\\t"+x.id()+"\\t"+x.reference()+"\\t"+x.alternate()+"\\t"+x.gene()+"\\t"+x.feature()+"\\t"+x.consequence()+"\\t"+x.confidence()+"\\t"+x.filters()+"\\t"+x.flags()+"\\t"+x.info()+"\\t"+x.rawCsq()));System.out.println("{\\"variants\\":"+s.variants()+",\\"annotations\\":"+s.annotations()+",\\"HC\\":"+s.highConfidence()+",\\"LC\\":"+s.lowConfidence()+"}");}}}
''',encoding='utf8',newline='\n');classes=out/'runner-classes';classes.mkdir();run('compile-audit-runner',[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-cp',str(jar),'-d',str(classes),str(runner)])
        for name,settings in [('original-default',[]),('literal-percentile',['use_gerp_end_trunc:0'])]:
            case=out/name;case.mkdir();options=['loftee_path:'+linux(plugin),'human_ancestor_fa:false','gerp_bigwig:'+linux(home/'gerp.bw'),*settings];command=prefix+[linux(environment/'bin/vep'),'--input_file',linux(home/'input.vcf'),'--output_file',linux(case/'original.vcf'),'--format','vcf','--vcf','--gtf',linux(home/'genes.gtf.gz'),'--fasta',linux(home/'reference.fa'),'--species','homo_sapiens','--assembly','SYNTHETIC_LOFTEE_CONTROLS','--no_stats','--allele_number','--biotype','--numbers','--symbol','--canonical','--dir_plugins',linux(plugin),'--plugin','LoF,'+','.join(options),'--warning_file',linux(case/'original.warnings.txt')];run(name,command);logs=[out/(name+'.stdout'),out/(name+'.stderr')]
            if (case/'original.warnings.txt').exists():logs.append(case/'original.warnings.txt')
            run(name+'-native-audit',['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-cp',linux(classes)+':'+linux(jar),'RunOriginalLofteeAudit',linux(home/'input.vcf'),linux(case/'original.vcf'),linux(case/'native-variants.tsv'),linux(case/'native-annotations.tsv'),*[linux(p) for p in logs]])
            summary=json.loads((out/(name+'-native-audit.stdout')).read_bytes());expected=dict(variants=15,annotations=80,HC=8 if name=='original-default' else 10,LC=2 if name=='original-default' else 0);assert summary==expected,(name,summary,expected);checks.append(dict(case=name,summary=summary,complete_original_vcf=pin(case/'original.vcf'),native_annotations=pin(case/'native-annotations.tsv'),native_variants=pin(case/'native-variants.tsv')))
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(plugin/n)==v for n,v in plugin_pins.items());state.update(status='passed-original-VEP105-LOFTEE-synthetic-resource-and-full-output-audit-probes',cases=checks)
    except Exception as error:state.update(error=repr(error),cases=checks)
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/source/family checks')
    raise SystemExit(main())
