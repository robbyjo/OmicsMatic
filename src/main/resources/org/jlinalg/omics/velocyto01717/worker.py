# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Counter API orchestration follows velocyto0.17.17 _run.py (BSD-2-Clause).
# Original copyright2017 Peter Kharchenko, Sten Linnarsson and Gioele La Manno.
# Original notice: META-INF/licenses/Velocyto-BSD-2-Clause.txt.
"""Unmodified original velocyto counting and complete original Loom value export.

The uint64 mode selects the original Counter constructor's dtype argument;
it is explicitly different from the original CLI's uint16 internal buffers.
"""
import hashlib,json,logging,math,os,struct,subprocess,sys,traceback
from pathlib import Path
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n')as stream:json.dump(value,stream,sort_keys=True,indent=2,ensure_ascii=False,allow_nan=False);stream.write('\n')
def safe(value):
    import numpy as np
    if isinstance(value,np.ndarray):return safe(value.tolist())
    if isinstance(value,np.generic):return safe(value.item())
    if isinstance(value,bytes):return value.decode('utf-8','strict')
    if isinstance(value,dict):return{str(k):safe(v)for k,v in value.items()}
    if isinstance(value,(tuple,list)):return[safe(v)for v in value]
    if isinstance(value,float)and not math.isfinite(value):return 'NaN'if math.isnan(value)else('Infinity'if value>0 else'-Infinity')
    return value
def scan(path,rows,line_cap):
    count=0
    with path.open('r',encoding='utf-8',errors='strict')as stream:
        while True:
            line=stream.readline(line_cap+1)
            if not line:break
            count+=1;assert count<=rows and len(line)<=line_cap,'Complete annotation row/line capacity exceeded'
            if not line.startswith('#'):assert len(line.rstrip('\r\n').split('\t'))==9,'Original GTF requires nine literal tab-delimited fields, without blank rows'
    return count
def barcode_selection(inputs,effective):
    import gzip
    if effective['barcode_file']is None:return None,None,''
    path=inputs/effective['barcode_file'];raw=gzip.decompress(path.read_bytes())if effective['barcode_gzip']else path.read_bytes();tokens=raw.decode('utf-8','strict').rstrip().split();assert tokens,'Original barcode selection is empty';prefixes={b.split('-')[0]for b in tokens};suffix='-'+tokens[0].split('-')[-1]if len(prefixes)==1 else'x';return tokens,prefixes,suffix
def counter(settings,prefixes):
    import velocyto as vcy
    dtype='uint64'if settings['counter_engine']=='original-counter-api-uint64'else vcy.LOOM_NUMERIC_DTYPE
    return vcy.ExInCounter(sampleid=settings['sample_id'],logic=getattr(vcy,settings['logic']),valid_bcset=prefixes,umi_extension='no',onefilepercell=False,loom_numeric_dtype=dtype)
def inspect(root,request,effective):
    inputs=root/'engine/inputs';settings=request['settings'];capacity=request['capacity'];gtf=inputs/'annotation.gtf';lines=scan(gtf,capacity['gtf_rows'],capacity['line_characters']);_,prefixes,_=barcode_selection(inputs,effective);c=counter(settings,prefixes);annotation=c.read_transcriptmodels(str(gtf));assert 0<len(c.genes)<=capacity['genes'],'Original gene population absent or above capacity'
    genes=[None]*len(c.genes)
    for gene,info in c.genes.items():genes[c.geneid2ix[gene]]=dict(Accession=gene,Gene=info.genename,Chromosome=info.chrom,Strand=info.strand,Start=info.start,End=info.end)
    assert all(g is not None for g in genes),'Original gene index has holes'
    report=dict(status='ORIGINAL_GTF_PARSED',gtf_rows=lines,genes=genes,annotation_chromosome_strands=list(annotation),original_internal_counter_dtype=str(c.loom_numeric_dtype),requested_counter_engine=settings['counter_engine'])
    save(root/'ANNOTATION-AUDIT.json',safe(report))
    import pysam
    native=json.loads((root/'INPUT-AUDIT.json').read_bytes());reference_lengths={};unannotated=[];annotated={key[:-1]for key in annotation};allowed=set(settings['allowed_unannotated_contigs'])
    for row in native['bams']:
        with pysam.AlignmentFile(root/'engine/prepared'/row['prepared'])as bam:raw_lengths=dict(zip(bam.references,bam.lengths))
        for raw,canonical in row['source_chromosome_mapping'].items():
            length=raw_lengths[raw]
            if canonical in reference_lengths:assert reference_lengths[canonical]==length,'Shared original normalized reference names have different sequence lengths across input BAMs'
            reference_lengths[canonical]=length
            if row['eligible_records_by_contig'].get(raw,0)>0 and canonical not in annotated:unannotated.append(dict(contig=raw,source_name=canonical,explicitly_allowed=raw in allowed))
    for gene in genes:
        assert gene['Start']>=1 and gene['End']>=gene['Start'],'Invalid original gene coordinate range'
        if gene['Chromosome']in reference_lengths:assert gene['End']<=reference_lengths[gene['Chromosome']],'Original gene coordinates exceed the actual compatible BAM reference length'
    save(root/'REFERENCE-ANNOTATION-AUDIT.json',dict(original_annotation_chromosomes=sorted(annotated),observed_unannotated_contigs=unannotated,assembly_identity_not_inferred=True))
    assert all(row['explicitly_allowed']for row in unannotated),'Original BAM-side chromosome conversion does not match the actual annotation for observed reads. Inspect the audit and supply/document compatible actual inputs or explicitly intended unannotated contigs; no identifiers are repaired'
    if effective['repeat_mask']is not None:
        path=inputs/'repeat-mask.gtf';scan(path,capacity['gtf_rows'],capacity['line_characters']);actual=c.read_repeats(str(path));applied={key:[[f.start,f.end]for f in value]for key,value in actual.items()};missing=[]
        with path.open('r',encoding='utf-8')as stream:
            for i,line in enumerate(stream,1):
                if line.startswith('#'):continue
                f=line.rstrip('\r\n').split('\t');chrom=f[0][3:]if f[0][:3].lower()=='chr'else f[0];start,end=int(f[3]),int(f[4]);key=chrom+f[6]
                if not any(a<=start and b>=end for a,b in applied.get(key,[])):missing.append(dict(line=i,chromosome=chrom,strand=f[6],start=start,end=end))
        save(root/'MASK-AUDIT.json',dict(actual_original_intervals=applied,requested_intervals_not_applied=missing,all_requested_intervals_applied=not missing,source_modified=False))
        assert not missing,'Original repeat parser omitted requested intervals (including final-group behavior). Raw mask audit retained; no complete repeat-masked analysis. Do not add dummy intervals or silently patch the parser'
def count(root,request,effective):
    settings=request['settings'];inputs=root/'engine/inputs';prepared=root/'engine/prepared';output=root/'engine/original';output.mkdir();bams=tuple(str(prepared/name)for name in effective['prepared_bams']);gtf=inputs/'annotation.gtf';tokens,prefixes,suffix=barcode_selection(inputs,effective)
    if settings['counter_engine']=='original-cli-uint16-buffers':
        from velocyto.commands.velocyto import cli
        arguments=['run','--sampleid',settings['sample_id'],'--outputfolder',str(output),'--logic',settings['logic'],'--samtools-threads',str(settings['samtools_threads']),'--samtools-memory',str(settings['samtools_memory_mb']),'--dtype',settings['output_dtype'],'--dump','0']
        if tokens is not None:arguments+=['--bcfile',str(inputs/effective['barcode_file'])]
        if effective['repeat_mask']is not None:arguments+=['--mask',str(inputs/'repeat-mask.gtf')]
        if settings['multimap']:arguments+=['--multimap']
        if settings['verbosity']>1:arguments+=['-'+'v'*(settings['verbosity']-1)]
        arguments+=[*bams,str(gtf)];save(root/'ORIGINAL-ARGUMENTS.json',dict(engine=settings['counter_engine'],argv=['velocyto',*arguments],scientific_source_modified=False));cli.main(args=arguments,prog_name='velocyto',standalone_mode=False)
    else:
        import velocyto as vcy,numpy as np,loompy
        c=counter(settings,prefixes);c.peek(bams[0]);assert c.cellbarcode_str=='CB'and c.umibarcode_str=='UB','Original peek did not establish the declared CB/UB chemistry';c.read_transcriptmodels(str(gtf))
        if effective['repeat_mask']is not None:c.read_repeats(str(inputs/'repeat-mask.gtf'))
        sorted_bams=[];commands=[]
        for i,bam in enumerate(bams):
            target=prepared/('cellsorted_'+Path(bam).name);assert not target.exists(),'Existing cell-sorted files cannot bypass computation';command=['samtools','sort','-l',str(vcy.BAM_COMPRESSION),'-m',str(settings['samtools_memory_mb'])+'M','-t','CB','-O','BAM','-@',str(settings['samtools_threads']),'-o',str(target),bam];commands.append(command);subprocess.run(command,check=True,stdin=subprocess.DEVNULL);sorted_bams.append(str(target))
        save(root/'ORIGINAL-ARGUMENTS.json',dict(engine=settings['counter_engine'],original_constructor=dict(umi_extension='no',onefilepercell=False,loom_numeric_dtype='uint64',logic=settings['logic']),samtools_commands=commands,scientific_source_modified=False,deliberately_different_from_original_CLI_uint16_buffers=True))
        c.mark_up_introns(bamfile=bams,multimap=settings['multimap']);chunks,cells=c.count(tuple(sorted_bams),multimap=settings['multimap']);layers={name:np.concatenate(value,axis=1)for name,value in chunks.items()};assert all(value.dtype==np.dtype('uint64')for value in layers.values()),'Original explicit uint64 counter buffer was not used'
        row_attrs={name:np.zeros(len(c.genes),dtype=object)for name in ['Gene','Accession','Chromosome','Strand','Start','End']}
        for gene,info in c.genes.items():
            ix=c.geneid2ix[gene]
            for name,value in dict(Gene=info.genename,Accession=info.geneid,Chromosome=info.chrom,Strand=info.strand,Start=info.start,End=info.end).items():row_attrs[name][ix]=value
        row_attrs={name:value.astype(int if name in ['Start','End']else str)for name,value in row_attrs.items()};col_attrs=dict(CellID=np.array([settings['sample_id']+':'+cell+suffix for cell in cells]));total=None
        for name in c.logic.layers:total=layers[name].copy()if total is None else total+layers[name]
        loompy.create(str(output/(settings['sample_id']+'.loom')),layers={'':total.astype('float32'),**layers},row_attrs=row_attrs,col_attrs=col_attrs,file_attrs={'velocyto.__version__':vcy.__version__,'velocyto.logic':settings['logic'],'OmicsMaticCounterEngine':settings['counter_engine'],'OmicsMaticCounterBuffer':'uint64'})
def metadata(h):
    import h5py,numpy as np
    result=dict(file_attributes={str(k):safe(v)for k,v in h.attrs.items()},groups={},datasets={})
    def walk(group,path=''):
        result['groups'][path]=dict(attributes={str(k):safe(v)for k,v in group.attrs.items()})
        for name in sorted(group):
            link=group.get(name,getlink=True);assert isinstance(link,h5py.HardLink),'External or soft HDF5 links are not admitted'
            node=group[name];member=path+'/'+name
            if isinstance(node,h5py.Group):walk(node,member)
            else:
                row=dict(shape=list(node.shape),dtype=node.dtype.str,attributes={str(k):safe(v)for k,v in node.attrs.items()})
                if member.startswith(('/row_attrs/','/col_attrs/','/attrs/')):row['values']=safe(node[...])
                elif member.startswith(('/row_graphs/','/col_graphs/')):assert node.size==0,'Unexpected original count Loom graph data; no values are silently omitted'
                elif member!='/matrix'and not member.startswith('/layers/'):raise AssertionError('Unexpected original Loom data population: '+member)
                result['datasets'][member]=row
    walk(h);return result
def without_clock(value):
    if isinstance(value,list):return[without_clock(v)for v in value]
    if not isinstance(value,dict):return value
    result={}
    for key,item in value.items():
        if key in ['last_modified','CreationDate']or key=='/attrs/CreationDate':continue
        result[key]=without_clock(item)
    return result
def export(root,request,effective):
    import numpy as np,h5py
    settings=request['settings'];capacity=request['capacity'];path=root/'engine/original'/(settings['sample_id']+'.loom');target=root/'engine/export';target.mkdir();hashes={name:hashlib.sha256()for name in ['spliced','unspliced','ambiguous','matrix']};records=0
    with h5py.File(path,'r')as h:
        assert set(h['layers'])=={'spliced','unspliced','ambiguous'},'Complete three original count layers required';shape=h['matrix'].shape;assert len(shape)==2 and 0<shape[0]<=capacity['genes']and 0<shape[1]<=capacity['cells'],'Original output gene/cell population empty or above capacity';size=int(shape[0])*int(shape[1]);assert size<=capacity['matrix_entries']and size*28<=capacity['decoded_matrix_bytes'],'Complete decoded matrix capacity exceeded';assert h['matrix'].dtype==np.dtype('float32'),'Original matrix must retain float32 storage'
        expected_dtype='uint64'if settings['counter_engine']=='original-counter-api-uint64'else settings['output_dtype']
        for name in ['spliced','unspliced','ambiguous']:assert h['layers'][name].shape==shape and h['layers'][name].dtype==np.dtype(expected_dtype),'Original count layer shape/dtype differs'
        raw_metadata=metadata(h);save(target/'LOOM-METADATA.json',raw_metadata)
        for name in ['Gene','Accession','Chromosome','Strand','Start','End']:assert h['row_attrs'][name].shape==(shape[0],),'Original row metadata dimension differs'
        assert h['col_attrs/CellID'].shape==(shape[1],),'Original CellID population differs';genes=safe(h['row_attrs/Accession'][...]);cells=safe(h['col_attrs/CellID'][...]);assert len(set(genes))==len(genes)and len(set(cells))==len(cells),'Original gene/cell identifiers are duplicated';assert all(isinstance(v,str)and v for v in genes+cells),'Original identifiers must remain nonempty literal strings'
        with(target/'counts.bin').open('xb')as output:
            for g in range(shape[0]):
                for start in range(0,shape[1],16384):
                    stop=min(start+16384,shape[1]);arrays={name:np.asarray(h['layers'][name][g,start:stop],dtype='<u8')for name in ['spliced','unspliced','ambiguous']};arrays['matrix']=np.asarray(h['matrix'][g,start:stop],dtype='<f4')
                    for name,array in arrays.items():hashes[name].update(array.tobytes(order='C'))
                    selected=np.flatnonzero((arrays['spliced']!=0)|(arrays['unspliced']!=0)|(arrays['ambiguous']!=0)|(arrays['matrix'].view('<u4')!=0));records+=len(selected);assert records<=capacity['sparse_entries'],'Complete union-of-nonzero record capacity exceeded'
                    for cell in selected:output.write(struct.pack('<QQQQf',g*shape[1]+start+int(cell),int(arrays['spliced'][cell]),int(arrays['unspliced'][cell]),int(arrays['ambiguous'][cell]),float(arrays['matrix'][cell])))
    result=dict(status='COMPLETE_ORIGINAL_LOOM_LOGICAL_VALUE_EXPORT',genes=genes,cells=cells,shape=list(shape),logical_dense_sha256={k:v.hexdigest()for k,v in hashes.items()},sparse_records=int(records),record_bytes=36,record_format='little-endian u64 index,S,U,A; f32 original matrix',counter_engine=settings['counter_engine'],original_counter_buffer_dtype='uint64'if settings['counter_engine']=='original-counter-api-uint64'else'uint16',row_metadata=without_clock({k:v for k,v in raw_metadata['datasets'].items()if k.startswith('/row_attrs/')}),column_metadata=without_clock({k:v for k,v in raw_metadata['datasets'].items()if k.startswith('/col_attrs/')}),canonical_loom_metadata=without_clock(raw_metadata),clock_fields_excluded_from_fresh_comparison_only=['last_modified','CreationDate','/attrs/CreationDate'],raw_metadata_and_original_file_retained=True,author_recipe_qualified=False)
    save(target/'EXPORT.json',safe(result))
def main():
    assert sys.flags.safe_path and sys.flags.no_user_site and not sys.flags.ignore_environment,'Use the controlled environment with -s -P; -I ignores the required original-process hash seed'
    stage=sys.argv[1];root=Path(sys.argv[2]);request=json.loads((root/'request.json').read_bytes());effective=json.loads((root/'engine/effective.json').read_bytes());logging.basicConfig(stream=sys.stdout,level=[logging.ERROR,logging.WARNING,logging.INFO,logging.DEBUG][request['settings']['verbosity']]);{'inspect':inspect,'count':count,'export':export}[stage](root,request,effective)
if __name__=='__main__':
    try:main()
    except BaseException as error:
        save(Path(sys.argv[2])/('WORKER-'+sys.argv[1]+'-FAILURE.json'),dict(error=type(error).__name__+': '+str(error),traceback=traceback.format_exc(),analysis_admitted=False));raise
