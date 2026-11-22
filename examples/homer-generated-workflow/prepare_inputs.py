#!/usr/bin/env python3
"""Create version-specific synthetic generated-background recipes at NEW paths."""
import argparse
import base64
import hashlib
import importlib.util
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
VERSIONS=('5.1','4.11','4.11.1')
OLD_VARIANTS=('given-gc-normalized','numeric-cpg-forward','asymmetric-masked','known-only','denovo-only')
NEW_VARIANTS=('overall-normalized','positional-forward','gc-only-all-candidates','known-only','denovo-only')


def prepare(out,version,mode,admission):
    if version not in VERSIONS or mode not in ('generated-old','generated-new') or mode=='generated-new' and version!='5.1':
        raise ValueError('Choose generated-old for5.1/4.11/4.11.1 or generated-new for5.1')
    out=Path(out).absolute();out.mkdir(parents=True,exist_ok=False)
    spec=importlib.util.spec_from_file_location('generated_genome_examples',ROOT/'examples/homer-genome-workflow/prepare_inputs.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);inputs=module.prepare(out/'fixtures')
    lock=json.loads((ROOT/'tools/literature/homer-generated-lock.json').read_text())
    models=out/'models.motif';models.write_bytes(base64.b64decode(lock['health']['models_base64']))
    base=json.loads((inputs/'given-gc-normalized.json').read_text())
    for key in ('background','background-format'):base.pop(key)
    base.update({'background-mode':mode,'generated-admission':str(Path(admission).absolute()),
        'known-motifs':str(models),'comparison-motifs':str(models),'normalization-order':'1',
        'background-randomness':'original-unseeded','background-count-policy':'retain-original'})
    if mode=='generated-old':
        base.update({'background-total':'512','background-boundary-policy':'retain-original',
            'background-overlap':'remove-original' if version=='5.1' else 'retain'})
        variants=OLD_VARIANTS
    else:
        base.update({'weighting':'none','background-count':'256','background-candidates':'5000',
            'background-bins':'5','background-sub-bins':'5','background-kmer':'2',
            'background-kmer-mode':'overall','background-self-overlap':'forbid'})
        variants=NEW_VARIANTS
    for variant in variants:
        options=dict(base)
        if variant=='numeric-cpg-forward':options.update({'peaks':str(inputs/'targets.homer'),'peak-format':'homer',
            'size':'200','weighting':'cpg','normalization-order':'0','strands':'forward',
            'known-strand-policy':'original-wrapper','statistic':'hypergeometric'})
        if variant=='asymmetric-masked':options.update({'genome':str(inputs/'mask-genome.fa'),'size':'-50,150','mask-lowercase':'yes'})
        if variant=='positional-forward':options.update({'background-kmer-mode':'positional','size':'120',
            'strands':'forward','statistic':'hypergeometric','normalization-order':'0'})
        if variant=='gc-only-all-candidates':options.update({'background-kmer':'0','background-candidates':'0',
            'background-bins':'1','background-sub-bins':'1','background-self-overlap':'allow',
            'background-overlap':'retain','normalization-order':'0'})
        if variant=='known-only':
            options['analysis']='known'
            for key in ('comparison-motifs','motif-length','mismatches','motifs-to-find'):options.pop(key,None)
        if variant=='denovo-only':options['analysis']='denovo';options.pop('known-motifs',None)
        (out/(variant+'.json')).write_text(json.dumps(options,indent=2)+'\n',encoding='utf-8',newline='\n')
    files={str(p.relative_to(out)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.rglob('*')) if p.is_file()}
    (out/'provenance.json').write_text(json.dumps(dict(synthetic=True,version=version,mode=mode,variants=variants,
        files=files,scope='Technical workflows only. Four-base known models avoid the legacy zero-percent reporting defect;de novo width remains8. Generated background draws are original and unseeded. No paper dataset or biological calibration.'),indent=2)+'\n',encoding='utf-8',newline='\n')
    return out


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--version',choices=VERSIONS,required=True);parser.add_argument('--mode',choices=['generated-old','generated-new'],required=True)
    parser.add_argument('--generated-admission',type=Path,required=True);a=parser.parse_args()
    print(prepare(a.out,a.version,a.mode,a.generated_admission))
