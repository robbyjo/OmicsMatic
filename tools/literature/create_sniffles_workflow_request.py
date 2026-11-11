# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create the typed fourteen-case adapter request from the retained original controls."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:'
    return '/mnt/d/'+path.as_posix()[3:]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True);parser.add_argument('--workflow-out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();target=args.workflow_out.resolve()
    base=ROOT/'build/literature-review/sniffles-runtime'
    assert not out.exists() and out.is_relative_to(base) and not target.exists() and target.is_relative_to(base)
    binding=args.runtime.resolve();proof=json.loads(binding.read_bytes())
    assert proof['status']=='PASS_BOUND_ORIGINAL_SNIFFLES_RUNTIME_ONLY' and proof['binding_files']
    fixtures=base/'fixtures-1';reference=base/'original-controls-2'
    original=json.loads((reference/'COMPLETED.json').read_bytes())
    assert original['status']=='PASS_FOURTEEN_COMPLETE_ORIGINAL_SNIFFLES_REFERENCE_CONTROLS_ONLY'
    out.mkdir(parents=True)
    inputs=[dict(id=sample,kind='bam',path=linux(fixtures/(sample+'.bam')),pin=pin(fixtures/(sample+'.bam'))) for sample in ['sampleA','sampleB']]
    inputs.append(dict(id='tandem',kind='bed',path=linux(fixtures/'tandem-repeats.bed'),pin=pin(fixtures/'tandem-repeats.bed')))
    for id,case in [('knownV107','v107-sampleA'),('knownV207','v207-sampleA')]:
        source=reference/case/'original.vcf';inputs.append(dict(id=id,kind='vcf',path=linux(source),pin=pin(source)))
    analyses=[]
    for case in original['cases']:
        row=dict(id=case['name'],edition=case['edition'][1:],input=case['sample'],threads=1)
        if case['name']=='v202-tandem':row['tandem_repeats']='tandem'
        if case['name']=='v24-phased':row['phase']=True
        if case['name']=='v107-force':row['genotype_vcf']='knownV107'
        if case['name']=='v207-force':row['genotype_vcf']='knownV207'
        analyses.append(row)
    request=dict(schema_version=1,out=linux(target),runtime=dict(path=linux(binding),pin=pin(binding)),inputs=inputs,analyses=analyses,
        limits=dict(maximum_input_bytes=16777216,maximum_alignment_records=10000,maximum_read_bases=100000,
            maximum_reference_bases=1000000,maximum_vcf_bytes=16777216,maximum_vcf_records=1000,
            wall_millis=120000,address_space_bytes=8589934592,cpu_seconds=120,file_bytes=33554432,
            workspace_bytes=536870912,file_count=20000,processes=32,threads=256,sampled_rss_bytes=2147483648,monitor_millis=100))
    save(out/'request.json',request)
    save(out/'COMPLETED.json',dict(status='PASS_FOURTEEN_CASE_TYPED_SNIFFLES_WORKFLOW_REQUEST_CREATION_ONLY',
        request=pin(out/'request.json'),binding=pin(binding),original_reference=pin(reference/'COMPLETED.json'),
        workflow_out=linux(target),analysis_admitted=False,implementation_credit=False,
        source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))}))
    print('Prepared fourteen typed original-caller cases; analysis has not run')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original request/data binding checks')
    main()
