#!/usr/bin/env python3
"""Freeze synthetic original Beagle outputs after independent genotype/posterior checks."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference',required=True,type=Path)
    parser.add_argument('--complete',required=True,type=Path)
    parser.add_argument('--math',required=True,type=Path)
    parser.add_argument('--out',required=True,type=Path)
    args = parser.parse_args()
    original = json.loads((args.reference/'verification.json').read_text())
    complete = json.loads((args.complete/'verification.json').read_text())
    math = json.loads((args.math/'verification.json').read_text())
    assert original['status'].startswith('passed-original-Beagle-GT-phase-and-reference-imputation')
    assert complete['status'].startswith('passed-original-complete-prephased-target-and-cross-JVM')
    assert math['status'] == 'passed-independent-original-Beagle-GT-rounded-posterior-and-version-specific-phase-contracts'
    assert len(original['steps']) == 16 and len(complete['steps']) == 8 and len(math['cases']) == 24
    args.out.mkdir(parents=True,exist_ok=False)
    for name,digest in original['input_sha256'].items():
        source = args.reference/'input'/name
        assert sha(source) == digest,name
        shutil.copy2(source,args.out/name)
    assert sha(args.complete/'target-phased-complete.vcf') == complete['completed_input_sha256']
    shutil.copy2(args.complete/'target-phased-complete.vcf',args.out/'target-phased-complete.vcf')
    rows = ['case\tprofile\ttask\tJVM\ttarget\tuse_reference\toutput_variants\tphase_preservation_required\toutput_file']
    profiles = {'5.4':'5.4-29Oct24','5.1':'5.1-18May20','4.1-08Jun17':'4.1-08Jun17','4.1-21Jan17':'4.1-21Jan17'}
    sources = []
    for container,steps in [(args.reference,original['steps']),(args.complete,complete['steps'])]:
        for step in steps:
            is_complete = step['task'] == 'complete-phased-impute'
            case = step['profile']+'-'+(step['JVM'] if is_complete else step['task'])
            folder = container/case
            source = folder/'result.vcf.gz'
            assert sha(source) == step['files_sha256'][source.name]
            target = 'target-phased-complete.vcf' if is_complete else 'target-phased.vcf' if step['task']=='phased-impute' else 'target.vcf'
            output = case+'.vcf.gz'
            shutil.copy2(source,args.out/output)
            diagnostic = next(r for r in math['cases'] if r['label']==case)
            rows.append('\t'.join(map(str,[case,profiles[step['profile']],step['task'],step.get('JVM','java21'),target,
                step['task']!='phase',diagnostic['variants'],diagnostic['phase_preservation_required'],output])))
            sources.append(dict(case=case,original_process=step,independent_diagnostics=diagnostic))
    (args.out/'cases.tsv').write_text('\n'.join(rows)+'\n',encoding='utf-8')
    provenance = dict(scope='Synthetic diploid genotype/phasing/imputation references only. No patient data or biological accuracy calibration. Modern partly missing phased targets can be rephased;complete phased targets and the tested legacy builds preserve known phase. Rounded GP argmax is never substituted for original GT.',
        producer_sha256=sha(__file__),input_and_output_files_sha256={p.name:sha(p) for p in sorted(args.out.iterdir())},
        reference_proof_sha256=sha(args.reference/'verification.json'),complete_proof_sha256=sha(args.complete/'verification.json'),
        math_proof_sha256=sha(args.math/'verification.json'),cases=sources,totals=math['totals'])
    (args.out/'provenance.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(files=len(list(args.out.iterdir())),cases=len(sources),genotype_cells=math['totals']['genotype_cells'])))


if __name__=='__main__':
    main()
