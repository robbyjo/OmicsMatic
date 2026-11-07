# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run actual original HTSeq SAM/GTF counts, retaining parsed-unit inputs for independent native auditing."""
import argparse
import concurrent.futures
import copy
import hashlib
import json
import subprocess
from pathlib import Path
from setup_htseq_reference import ROOT,BASE,DISTRO,linux,pin


def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')


def sam(name,flag,chrom,pos,cigar='20M',mapq=60,nh=None,mate='*',mate_pos=0,length=20):
    fields=[name,str(flag),chrom,str(pos),str(mapq),cigar,mate,str(mate_pos),'0','A'*length,'I'*length]
    if nh is not None:fields.append('NH:i:'+str(nh))
    return '\t'.join(fields)+'\n'


def inputs(out):
    target=out/'data';target.mkdir()
    features=[('chr1',1,100,'+','A'),('chr1',151,200,'+','A'),('chr1',81,140,'+','B'),
        ('chr1',201,250,'-','minus'),('chr2',1,100,'+','C'),('chr3',5001,5050,'+','zero')]
    (target/'features.gtf').write_text(''.join('\t'.join([chrom,'synthetic','exon',str(start),str(end),'.',strand,'.','gene_id "'+gene+'"; gene_name "name-'+gene+'";'])+'\n' for chrom,start,end,strand,gene in features),encoding='utf8',newline='\n')
    header='@HD\tVN:1.6\tSO:queryname\n'+''.join('@SQ\tSN:'+chrom+'\tLN:10000\n' for chrom in ['chr1','chr2','chr3','absent'])
    single=[sam('s01',0,'chr1',11),sam('s02',0,'chr1',86),sam('s03',0,'chr1',105),sam('s04',16,'chr1',206),
        sam('s05',0,'chr1',291),sam('s06',4,'*',0,'*',0),sam('s07',0,'chr1',11,mapq=9),sam('s08',0,'chr1',11,mapq=10),
        sam('s09',0,'chr1',11,nh=2),sam('s10',0,'chr1',11,mapq=0,nh=2),sam('s11',256,'chr1',11),
        sam('s12',2048,'chr1',11),sam('s13',1024,'chr1',11),sam('s14',512,'chr1',11),
        sam('s15',0,'chr1',71,'10M100N10M'),sam('s16',0,'chr1',61,'10M10D10M'),sam('s17',0,'absent',1),
        sam('s18',0,'chr1',11,mapq=255)]
    (target/'single.sam').write_text(header+''.join(single),encoding='ascii',newline='\n')
    pair=[]
    def two(name,p1=11,p2=41,chr1='chr1',chr2='chr1',q1=60,q2=60,nh1=1,nh2=1,flags=(99,147)):
        pair.extend([sam(name,flags[0],chr1,p1,mapq=q1,nh=nh1,mate='=' if chr1==chr2 else chr2,mate_pos=p2),
            sam(name,flags[1],chr2,p2,mapq=q2,nh=nh2,mate='=' if chr1==chr2 else chr1,mate_pos=p1)])
    two('p01');two('p02',206,226,flags=(83,163));two('p03',chr2='chr2',p2=11)
    two('p04',nh1=None,nh2=2);two('p05',nh1=1,nh2=2);two('p06',nh1=2,nh2=None)
    two('p07',q1=9);two('p08',q1=20,q2=21);two('p09',p1=86,p2=90)
    pair.extend([sam('p10',73,'chr1',11,mate='*'),sam('p10',133,'*',0,'*',0,mate='chr1',mate_pos=11)])
    pair.extend([sam('p11',77,'*',0,'*',0),sam('p11',141,'*',0,'*',0)])
    pair.append(sam('p12',73,'chr1',11))
    pair.append(sam('p13',153,'chr1',11))
    two('p14',chr2='absent',p2=1)
    two('p15',flags=(99|256,147|256))
    two('p16',flags=(99|2048,147|2048))
    (target/'paired.sam').write_text(header+''.join(pair),encoding='ascii',newline='\n')
    (target/'header-only.sam').write_text(header,encoding='ascii',newline='\n')
    (target/'eqx.sam').write_text(header+sam('eqx',0,'chr1',11,'5=5X',length=10),encoding='ascii',newline='\n')
    (target/'missing-id.gtf').write_text('chr1\tsynthetic\texon\t1\t100\t.\t+\t.\tother_id "missing";\n',encoding='ascii',newline='\n')
    (target/'unstranded.gtf').write_text('chr1\tsynthetic\texon\t1\t100\t.\t.\t.\tgene_id "unstranded";\n',encoding='ascii',newline='\n')
    return target


PREPARE='''import HTSeq,json,pysam,sys
from pathlib import Path
root=Path(sys.argv[1])
for name in ['single','paired','eqx','header-only']:
 source=pysam.AlignmentFile(str(root/(name+'.sam')),'r');target=pysam.AlignmentFile(str(root/(name+'.bam')),'wb',template=source)
 for read in source:target.write(read)
 source.close();target.close()
pysam.sort('-o',str(root/'paired-position.bam'),str(root/'paired.bam'))
'''

PREPARED_UNITS='''import HTSeq,json,sys
from pathlib import Path
request=json.loads(Path(sys.argv[1]).read_text());root=Path(sys.argv[1]).parent
features=[]
for feature in HTSeq.GFF_Reader(request['annotation']):
 if feature.type=='exon':features.append(dict(chromosome=feature.iv.chrom,start=str(feature.iv.start),end=str(feature.iv.end),strand=feature.iv.strand,feature=feature.attr['gene_id']))
reader=HTSeq.BAM_Reader(request['alignment']);reads=iter(reader)
paired=request['layout']=='PAIRED'
if paired:
 primary=request['secondary']=='ignore' and request['supplementary']=='ignore'
 reads=HTSeq.pair_SAM_alignments(reads,primary_only=primary) if request['order']=='name' else HTSeq.pair_SAM_alignments_with_buffer(reads,max_buffer_size=100000,primary_only=primary)
def alignment(read):
 if read is None:return None
 try:nh=read.optional_field('NH')
 except KeyError:nh=None
 blocks=[]
 if read.aligned:
  for co in read.cigar:
   if co.type in ('M','=','X') and co.size>0:blocks.append(dict(chromosome=co.ref_iv.chrom,start=str(co.ref_iv.start),end=str(co.ref_iv.end),strand=co.ref_iv.strand))
 return dict(aligned=bool(read.aligned),secondary=bool(read.not_primary_alignment),supplementary=bool(read.supplementary),mapq=int(read.aQual),nh=nh,blocks=blocks)
units=[]
for i,read in enumerate(reads):
 units.append(dict(id=str(i),first=alignment(read[0] if paired else read),second=alignment(read[1]) if paired else None))
Path(sys.argv[2]).write_text(json.dumps(dict(features=features,units=units,config=request,HTSeq=HTSeq.__version__,
 preparation='Original HTSeq parser and pairing supplied the prepared inputs. Native audit does not independently parse SAM/GTF or replay pairing.'),indent=2)+'\\n')
'''


def specifications():
    result=[]
    for layout,filename in [('SINGLE','single.sam'),('PAIRED','paired.sam')]:
        for strand in ['yes','no','reverse']:
            for overlap in ['union','intersection-strict','intersection-nonempty']:
                result.append(dict(id=layout.lower()+'-'+strand+'-'+overlap,layout=layout,filename=filename,stranded=strand,
                    overlap=overlap,nonunique='none',minimum_mapq=10,secondary='ignore',supplementary='ignore',order='name'))
    template=result[0]
    for minimum in [0,20,21,255]:result.append(dict(template,id='mapq-'+str(minimum),minimum_mapq=minimum))
    for layout,index in [('SINGLE',0),('PAIRED',9)]:
        for policy in ['all','fraction','random']:
            result.append(dict(result[index],id=layout.lower()+'-nonunique-'+policy,nonunique=policy,stranded='no'))
        result.append(dict(result[index],id=layout.lower()+'-score-secondary',secondary='score'))
        result.append(dict(result[index],id=layout.lower()+'-score-supplementary',supplementary='score'))
        result.append(dict(result[index],id=layout.lower()+'-score-both',secondary='score',supplementary='score'))
    for name,layout in [('single.bam','SINGLE'),('paired.bam','PAIRED'),('paired-position.bam','PAIRED'),('header-only.sam','SINGLE'),('eqx.sam','SINGLE')]:
        result.append(dict(template,id=name.replace('.','-'),filename=name,layout=layout,order='pos' if 'position' in name else 'name'))
    result.append(dict(template,id='missing-id-error',annotation_filename='missing-id.gtf',native_audit=False))
    result.append(dict(template,id='dot-strand-error',annotation_filename='unstranded.gtf',native_audit=False))
    result.append(dict(template,id='dot-strand-unstranded',annotation_filename='unstranded.gtf',stranded='no'))
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    runtime=json.loads((BASE/'reference-inventory-recovery-2/COMPLETED.json').read_bytes())
    python=runtime['python'];data=inputs(out)
    (out/'prepare.py').write_text(PREPARE,encoding='utf8',newline='\n')
    (out/'prepared-units.py').write_text(PREPARED_UNITS,encoding='utf8',newline='\n')
    bootstrap='import json,random,runpy,sys;random.seed(4381);sys.argv=["htseq-count"]+json.loads(open(sys.argv[1]).read());runpy.run_module("HTSeq.scripts.count",run_name="__main__")\n'
    (out/'bootstrap.py').write_text(bootstrap,encoding='utf8',newline='\n')
    def command(folder,label,arguments):
        argv=['wsl.exe','-d',DISTRO,'--','/usr/bin/env','PYTHONHASHSEED=0','PYTHONDONTWRITEBYTECODE=1','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1',python,'-E','-s','-B',*arguments]
        # -E ignores PYTHONHASHSEED. Set the seed at process startup using env
        # without -E; isolated user-site handling remains explicit.
        argv.remove('-E')
        with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:
            process=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        save(folder/(label+'.process.json'),dict(command=argv,exit_code=process.returncode))
        return process.returncode
    assert command(out,'prepare',[linux(out/'prepare.py'),linux(data)])==0
    cases=specifications()
    def run(spec):
        folder=out/spec['id'];folder.mkdir()
        request=dict(spec,annotation=linux(data/spec.get('annotation_filename','features.gtf')),alignment=linux(data/spec['filename']))
        save(folder/'request.json',request)
        arguments=['-s',spec['stranded'],'-a',str(spec['minimum_mapq']),'-m',spec['overlap'],'--nonunique',spec['nonunique'],
            '--secondary-alignments',spec['secondary'],'--supplementary-alignments',spec['supplementary'],'-r',spec['order'],
            '-t','exon','-i','gene_id',request['alignment'],request['annotation']]
        save(folder/'arguments.json',arguments)
        code=command(folder,'original-count',[linux(out/'bootstrap.py'),linux(folder/'arguments.json')])
        parsed=None
        if code==0:
            lines=(folder/'original-count.stdout').read_text(encoding='utf8').splitlines()
            fields=[line.split('\t') for line in lines]
            assert len(fields)>=5 and all(len(row)==2 for row in fields),(spec['id'],lines)
            assert [r[0] for r in fields[-5:]]==['__no_feature','__ambiguous','__too_low_aQual','__not_aligned','__alignment_not_unique']
            parsed=dict(genes=fields[:-5],summary=fields[-5:])
            save(folder/'source-count-table.json',parsed)
        native=code==0 and spec['nonunique']!='random' and spec.get('native_audit',True)
        if native:
            code_prepare=command(folder,'prepared-units',[linux(out/'prepared-units.py'),linux(folder/'request.json'),linux(folder/'prepared-units.json')])
            assert code_prepare==0,spec['id']+' original input preparation failed'
        return dict(id=spec['id'],exit_code=code,counts=parsed,native_deterministic_audit=native,
            config=spec,upstream_random_seed=4381 if spec['nonunique']=='random' else None,python_hash_seed=0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(run,cases))
    save(out/'COMPLETED.json',dict(schema_version=1,status='collected-actual-original-HTSeq2.1.2-count-controls',
        cases=results,total_original_calls=len(results),successful_original_calls=sum(r['exit_code']==0 for r in results),
        retained_original_failures=sum(r['exit_code']!=0 for r in results),native_prepared_cases=sum(r['native_deterministic_audit'] for r in results),
        script_pin=pin(Path(__file__).read_bytes()),source_runtime=pin((BASE/'reference-inventory-recovery-2/COMPLETED.json').read_bytes()),
        original_SAM_GTF_count_executed=True,independent_native_SAM_GTF_parser_or_pairing=False,implementation_credit=False))
    print(json.dumps(dict(status='collected-actual-original-HTSeq-count-controls',original_calls=len(results),
        successful=sum(r['exit_code']==0 for r in results),failures=sum(r['exit_code']!=0 for r in results),native_audit_cases=sum(r['native_deterministic_audit'] for r in results))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original count controls')
    main()
