#!/usr/bin/env python3
"""Freeze the reviewed synthetic original-pruning masks; no executable is redistributed."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference',type=Path,required=True)
    parser.add_argument('--standalone',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args()
    original=json.loads((args.reference/'verification.json').read_text(encoding='utf-8'))
    prototype=json.loads((args.standalone/'verification.json').read_text(encoding='utf-8'))
    assert original['status']=='passed-original-partition-and-order-contracts' and len(original['steps'])==6
    assert prototype['status']=='passed-standalone-draft-original-partition-comparisons' and len(prototype['workflows'])==12 and prototype['negative_controls']==8
    args.out.mkdir(parents=True,exist_ok=False)
    for name,digest in prototype['input_sha256'].items():
        source=args.standalone/'input'/name;assert sha(source)==digest;shutil.copy2(source,args.out/name)
    rows=['case\tengine\tunit\twindow\tstep\tpair_order\tretained\tremoved\tin_file\tout_file']
    for case in original['steps']:
        label=case['engine']+'-'+case['label'];folder=args.reference/label
        for suffix in ['in','out']:
            source=folder/('result.prune.'+suffix);assert sha(source)==case['files_sha256'][source.name];shutil.copy2(source,args.out/(label+'.'+suffix))
        kb=case['label'].startswith('kb-')
        rows.append('\t'.join(map(str,[label,case['engine'],'kb' if kb else 'variants',50 if kb else 20,1 if kb else 5,'backward' if case['label'].endswith('backward') else 'legacy',case['retained'],case['removed'],label+'.in',label+'.out'])))
    (args.out/'cases.tsv').write_text('\n'.join(rows)+'\n',encoding='utf-8')
    provenance=dict(scope='Synthetic original --indep-pairwise partitions only;128input samples,125included,123recorded founders,91variants. Near/distant copied/reversed genotype rows and one missing-call contrast are deliberate. No patient data,biological linkage realism,native pruning algorithm or packaged public adapter claim.',runtime_profile=original['runtime_profile'],source_proof_sha256=sha(args.reference/'verification.json'),standalone_proof_sha256=sha(args.standalone/'verification.json'),producer_sha256=sha(__file__),reference_mutations=original['derived_genotype_mutations'],actual_original_cases=original['steps'],input_and_mask_sha256={f.name:sha(f) for f in args.out.iterdir() if f.is_file()})
    (args.out/'provenance.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(files=len(list(args.out.iterdir())),original_cases=6,scope=provenance['scope'])))

if __name__=='__main__':main()
