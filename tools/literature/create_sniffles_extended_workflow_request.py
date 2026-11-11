# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare typed extended-format or real zero-exit-fatal workflow requests; no analysis."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--kind',choices=['extended','source-fatal'],required=True)
    parser.add_argument('--binding',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--workflow-out',type=Path,required=True);a=parser.parse_args()
    base=ROOT/'build/literature-review/sniffles-runtime';out=a.out.resolve();target=a.workflow_out.resolve()
    assert not out.exists() and out.is_relative_to(base) and not target.exists() and target.is_relative_to(base)
    binding=a.binding.resolve();bound=json.loads(binding.read_bytes());assert bound['status']=='PASS_BOUND_ORIGINAL_SNIFFLES_RUNTIME_ONLY'
    fixtures=base/'extended-fixtures-1';original=base/'fixtures-1';creation=json.loads((fixtures/'COMPLETED.json').read_bytes())
    assert creation['status']=='PASS_EXTENDED_SYNTHETIC_SNIFFLES_FORMAT_CONTROL_CREATION_ONLY'
    assert all(pin(fixtures/name)==value for name,value in creation['files'].items())
    inputs=[];analyses=[]
    def input(id,kind,path,reference=None):
        row=dict(id=id,kind=kind,path=linux(path),pin=pin(path))
        if reference is not None:row['reference']=reference
        inputs.append(row)
    def case(id,edition,source,**settings):analyses.append(dict(id=id,edition=edition,input=source,threads=1,**settings))
    if a.kind=='source-fatal':
        input('sampleA','bam',original/'sampleA.bam');input('wrongBed','bed',fixtures/'wrong-contig.bed')
        case('earlier-provisional','2.4','sampleA');case('real-zero-exit-fatal','2.0.2','sampleA',tandem_repeats='wrongBed')
    else:
        input('referencePlain','fasta',fixtures/'reference.fa');input('referenceBgzf','fasta',fixtures/'reference-bgzip.fa.bgz')
        input('cramPlain','cram',fixtures/'sampleA.cram','referencePlain');input('cramBgzf','cram',fixtures/'sampleA.cram','referenceBgzf')
        input('csi','bam',fixtures/'long-contig.bam');input('empty','bam',fixtures/'no-indels.bam')
        input('bamReference','bam',original/'sampleA.bam','referencePlain');input('bamLegacy','bam',original/'sampleA.bam')
        input('knownDup','vcf',fixtures/'known-dup.vcf')
        for version in ['2.0.2','2.0.7','2.3.2','2.4']:case('v'+version.replace('.','')+'-cram-plain',version,'cramPlain')
        for version in ['2.0.2','2.4']:case('v'+version.replace('.','')+'-cram-bgzf',version,'cramBgzf')
        case('v24-csi','2.4','csi');case('v24-empty','2.4','empty');case('v24-bam-reference','2.4','bamReference')
        case('v107-known-dup','1.0.7','bamLegacy',genotype_vcf='knownDup')
    request=dict(schema_version=1,out=linux(target),runtime=dict(path=linux(binding),pin=pin(binding)),inputs=inputs,analyses=analyses,
        limits=dict(maximum_input_bytes=16777216,maximum_alignment_records=10000,maximum_read_bases=100000,
            maximum_reference_bases=1000000,maximum_vcf_bytes=16777216,maximum_vcf_records=1000,
            wall_millis=120000,address_space_bytes=8589934592,cpu_seconds=120,file_bytes=33554432,
            workspace_bytes=536870912,file_count=20000,processes=32,threads=256,sampled_rss_bytes=2147483648,monitor_millis=100))
    out.mkdir();save(out/'request.json',request);save(out/'COMPLETED.json',dict(status='PASS_TYPED_SNIFFLES_EXTENDED_REQUEST_CREATION_ONLY',kind=a.kind,
        analyses=len(analyses),analysis_admitted=False,implementation_credit=False,request=pin(out/'request.json'),binding=pin(binding),
        source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))}))
    print('Prepared '+str(len(analyses))+' typed '+a.kind+' cases; no analysis admitted')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable extended request/input binding checks')
    main()
