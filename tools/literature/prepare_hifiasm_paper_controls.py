# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a NEW positive corpus; retain prior failing original matrices unchanged."""
import argparse,json,shutil
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();source=a.source.resolve();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');before={q.name:pin(q) for q in source.iterdir() if q.is_file()};request=json.loads((source/'request.json').read_bytes());out.mkdir(parents=True)
    for name,record in request['inputs'].items():
        old=source/(name+'.fa');assert pin(old)=={k:record[k] for k in ['bytes','sha256']};new=out/(name+('.fq' if name=='ont' else '.fa'))
        if name=='ont':
            lines=old.read_text(encoding='ascii').splitlines();assert len(lines)%2==0
            with new.open('x',encoding='ascii',newline='\n') as stream:
                for i in range(0,len(lines),2):
                    assert lines[i].startswith('>') and set(lines[i+1])<=set('ACGT');stream.write('@'+lines[i][1:]+'\n'+lines[i+1]+'\n+\n'+'8'*len(lines[i+1])+'\n')
        else:shutil.copyfile(old,new)
        request['inputs'][name]=dict(path=linux(new),**pin(new))
    omitted=[c for c in request['cases'] if c['edition'] in ['0.14.2-r315','0.15.1-r328'] and c['assembly_mode']=='hic'];assert len(omitted)==2
    request['cases']=[c for c in request['cases'] if c not in omitted];assert len(request['cases'])==27;save(out/'request.json',request)
    assert all(pin(source/n)==v for n,v in before.items());save(out/'COMPLETED.json',dict(status='prepared-twenty-seven-explicit-supported-original-hifiasm-controls',request=pin(out/'request.json'),case_count=27,source_corpus=dict(path=source.relative_to(ROOT).as_posix(),files=before),omitted_positive_expectations=omitted,limitation='Hi-C in0.14.2/0.15.1 is unsupported by this integration after retained upstream crashes; no reported role in these editions requires Hi-C. Original failing matrices remain FAIL2, not admitted.',ONT_quality_policy='Explicit synthetic constant Phred23 (ASCII8); sequences unchanged. This supplies required FASTQ, not measured sequencing qualities or paper inputs.',analysis_admitted=False,implementation_credit=False,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())}));print('Prepared NEW27-case supported corpus; prior failed matrices unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable control checks')
    main()
