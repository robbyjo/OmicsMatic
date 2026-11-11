# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bound original-format preparation and unmodified Sniffles2 program execution.

No external SNF/pickle input, dependency installation, monkeypatch, automatic
alignment sorting, variant merging, identifier repair or result filtering.
"""
import sys
import json
import hashlib
import gzip
import importlib.util
from pathlib import Path


def save(path,value):
    with path.open('x',encoding='utf8',newline='\n') as stream:
        json.dump(value,stream,indent=2,allow_nan=False);stream.write('\n')


def integer(value,low,high):
    assert type(value) is int and low<=value<=high,'Expected bounded exact integer'
    return value


def prepare(payload,home):
    import pysam
    limits=payload['limits']
    max_records=integer(limits['maximum_alignment_records'],1,1000000000)
    max_read=integer(limits['maximum_read_bases'],1,100000000)
    max_reference=integer(limits['maximum_reference_bases'],1,1000000000000)
    reference=None;fasta=None;reports=[];cache=Path(payload['reference_cache']) if payload['reference_cache'] is not None else None
    if payload['reference'] is not None:
        reference=Path(payload['reference']);assert reference.is_absolute()
        ids=set();raw_bases=0;current=False
        with reference.open('rb') as first:compressed=first.read(2)==b'\x1f\x8b'
        with (gzip.open(reference,'rb') if compressed else reference.open('rb')) as stream:
            while raw:=stream.readline(16777217):
                assert len(raw)<=16777216,'FASTA line capacity exceeded'
                line=raw.rstrip(b'\r\n')
                if line.startswith(b'>'):
                    token=line[1:].split()[0].decode('utf8')
                    assert token and token not in ids,'Duplicate/empty FASTA identifier; no repair'
                    ids.add(token);current=True
                else:
                    assert current or not line,'Sequence before first FASTA header'
                    raw_bases+=len(line);assert raw_bases<=max_reference,'FASTA raw sequence-byte capacity exceeded'
        assert ids
        pysam.faidx(str(reference))
        fasta=pysam.FastaFile(str(reference))
        bases=sum(fasta.lengths)
        assert set(fasta.references)==ids and bases<=max_reference
        reports.append(dict(kind='reference',path=str(reference),contigs=len(ids),bases=bases,index=str(reference)+'.fai'))
        if cache is not None:
            assert cache.is_absolute() and not cache.exists()
            cache.mkdir()
            cached=[]
            for name,length in zip(fasta.references,fasta.lengths):
                digest=hashlib.md5(usedforsecurity=False)
                for start in range(0,length,1048576):digest.update(fasta.fetch(name,start,min(start+1048576,length)).upper().encode('ascii'))
                key=digest.hexdigest();target=cache/key
                if not target.exists():
                    with target.open('xb') as stream:
                        for start in range(0,length,1048576):stream.write(fasta.fetch(name,start,min(start+1048576,length)).upper().encode('ascii'))
                cached.append(dict(contig=name,length=length,md5=key,path=str(target)))
            reports.append(dict(kind='local_reference_cache',path=str(cache),sequences=cached,
                format='HTSlib MD5-named unformatted upper-case sequence; raw FASTA retained unchanged',
                remote_reference_locations_configured=False))
    for row in payload['alignments']:
        path=Path(row['path']);kind=row['kind'];assert kind in ['bam','cram']
        assert kind!='cram' or reference is not None,'CRAM requires explicit matching reference'
        options=dict(require_index=False)
        if reference is not None:options['reference_filename']=str(reference)
        with pysam.AlignmentFile(str(path),'rb' if kind=='bam' else 'rc',**options) as reader:
            header=reader.header.to_dict();assert header.get('HD',{}).get('SO')=='coordinate','Explicit coordinate-sorted input required; no sorting performed'
            dictionary=list(zip(reader.references,reader.lengths))
            assert dictionary and len({n for n,_ in dictionary})==len(dictionary)
            assert all(n and 0<length<=2147483647 for n,length in dictionary)
            verified_m5=unreported_m5=0
            if fasta is not None:
                lengths=dict(zip(fasta.references,fasta.lengths))
                for name,length in dictionary:assert lengths.get(name)==length,'Alignment/reference dictionary mismatch'
                for sq in header['SQ']:
                    if 'M5' in sq:
                        digest=hashlib.md5(usedforsecurity=False)
                        for start in range(0,sq['LN'],1048576):digest.update(fasta.fetch(sq['SN'],start,min(start+1048576,sq['LN'])).upper().encode('ascii'))
                        assert digest.hexdigest().lower()==sq['M5'].lower(),'Alignment/reference M5 mismatch; no remote reference fallback admitted'
                        verified_m5+=1
                    else:
                        assert kind!='cram','CRAM reference requires complete M5 declaration'
                        unreported_m5+=1
            previous=(-1,-1);unplaced=False;count=mapped=hp=ps=0;flags={};name_digest=hashlib.sha256()
            for read in reader.fetch(until_eof=True):
                count+=1;assert count<=max_records,'Alignment record capacity exceeded'
                flags[str(read.flag)]=flags.get(str(read.flag),0)+1
                if read.query_name is None:name_digest.update(b'\x00')
                else:
                    encoded=read.query_name.encode('utf8');name_digest.update(b'\x01');name_digest.update(len(encoded).to_bytes(8,'big'));name_digest.update(encoded)
                if read.reference_id<0:unplaced=True
                else:
                    assert not unplaced and (read.reference_id,read.reference_start)>=previous,'Unsorted complete alignment population'
                    previous=(read.reference_id,read.reference_start)
                    if not read.is_unmapped:
                        assert read.reference_start>=0 and read.reference_end is not None and read.reference_end<=dictionary[read.reference_id][1]
                        mapped+=1
                if read.query_sequence is not None:
                    length=len(read.query_sequence);assert length<=max_read,'Read-length capacity exceeded'
                    if read.cigartuples is not None:assert sum(n for op,n in read.cigartuples if op in [0,1,4,7,8])==length,'CIGAR/query length disagreement'
                hp+=int(read.has_tag('HP'));ps+=int(read.has_tag('PS'))
        # Build a NEW index for the unchanged captured alignment; no supplied
        # index is silently replaced, no sorting or filtering is performed.
        if kind=='bam':
            csi=any(length>=536870912 for _,length in dictionary)
            assert not(csi and row['legacy_requested']),'Legacy 1.0.7 requires BAI-compatible reference coordinates'
            pysam.index(*(['-c'] if csi else []),str(path));index=str(path)+('.csi' if csi else '.bai')
        else:pysam.index(str(path));index=str(path)+'.crai'
        with pysam.AlignmentFile(str(path),'rb' if kind=='bam' else 'rc',require_index=True,
                                 **({'reference_filename':str(reference)} if reference is not None else {})) as indexed:
            assert indexed.has_index()
        reports.append(dict(kind=kind,id=row['id'],path=str(path),index=index,records=count,mapped_records=mapped,
                            flag_counts=flags,read_name_population_sha256=name_digest.hexdigest(),
                            HP_tagged_records=hp,PS_tagged_records=ps,dictionary=dictionary,
                            reference_M5_verified_contigs=verified_m5,reference_M5_unreported_contigs=unreported_m5,
                            original_header=header,source_filtering_or_sorting_performed=False))
    if fasta is not None:fasta.close()
    save(home/'PREPARED.json',dict(status='PASS_COMPLETE_ORIGINAL_PYSAM_PREPARATION_ONLY',inputs=reports,
        source_namespace_patched=False,analysis_admitted=False,analysis_installed_software=False,
        index_policy='New index from unchanged captured alignments; no user index consumed or repaired'))


def call(payload,root,home):
    import runpy
    tag=payload['edition'];assert tag in ['2.0.2','2.0.7','2.3.2','2.4']
    source=root/'sources'/('v'+tag)/'src'
    sys.path.append(str(source))
    arguments=payload['original_arguments']
    assert type(arguments) is list and all(type(a) is str for a in arguments)
    sys.argv=[str(source/'sniffles/sniffles'),*arguments]
    runpy.run_path(str(source/'sniffles/sniffles'),run_name='__main__')
    save(home/'PROGRAM-RETURNED.json',dict(status='ORIGINAL_SNIFFLES2_PROGRAM_RETURNED_ONLY',
        source_namespace_patched=False,analysis_installed_software=False,analysis_admitted=False,
        warning='Exit/return alone is insufficient: Sniffles 2.0.2 can swallow a fatal main error. Native diagnostics and complete VCF admission remain mandatory.'))


def main():
    assert len(sys.argv)==5
    mode=sys.argv[1];root=Path(sys.argv[2]);home=Path(sys.argv[3]);request=Path(sys.argv[4])
    assert mode in ['prepare','call'] and root.is_absolute() and home.is_absolute() and request.is_absolute()
    assert root.resolve()==root and home.resolve()==home and request.resolve()==request
    module_path=Path(__file__).with_name('bootstrap.py')
    spec=importlib.util.spec_from_file_location('_omics_sniffles_bootstrap',module_path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    module.startup(root,home)
    with request.open('rb') as stream:raw=stream.read(16777217)
    assert len(raw)<=16777216,'Worker request capacity exceeded'
    payload=json.loads(raw)
    if mode=='prepare':prepare(payload,home)
    else:call(payload,root,home)
    assert not Path(sys.pycache_prefix).exists(),'Bytecode cache unexpectedly created'


if __name__=='__main__':
    receipt_home=Path(sys.argv[3]) if len(sys.argv)==5 else None
    try:main()
    except SystemExit as error:
        if receipt_home is not None:
            # Preserve an original program's exit, including nonzero status.
            # Adapter FAIL2 admission is a separate native receipt.
            target=receipt_home/'ORIGINAL-EXIT.json'
            if not target.exists():save(target,dict(status='ORIGINAL_SYSTEM_EXIT_ONLY',code=error.code if type(error.code) in [int,str] or error.code is None else repr(error.code)))
        raise
    except BaseException:
        import traceback
        traceback.print_exc();sys.exit(2)
