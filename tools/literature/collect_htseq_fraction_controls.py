# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add genuine three-feature fractional source controls without repeating the completed matrix."""
import argparse
import json
import subprocess
from pathlib import Path
from setup_htseq_reference import ROOT,BASE,DISTRO,linux,pin
from collect_htseq_count_controls import save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    parent=BASE/'count-original-1';report=json.loads((parent/'COMPLETED.json').read_bytes())
    assert report['successful_original_calls']==40 and report['retained_original_failures']==2
    original=(parent/'data/features.gtf').read_bytes()
    (out/'three.gtf').write_bytes(original+b'chr1\tsynthetic\texon\t81\t95\t.\t+\t.\tgene_id "third";\n')
    runtime=json.loads((BASE/'reference-inventory-recovery-2/COMPLETED.json').read_bytes())
    results=[]
    for policy in ['all','fraction']:
        folder=out/policy;folder.mkdir()
        spec=dict(id='three-feature-'+policy,layout='SINGLE',stranded='no',overlap='union',nonunique=policy,
            minimum_mapq=10,secondary='ignore',supplementary='ignore',order='name',
            alignment=linux(parent/'data/single.sam'),annotation=linux(out/'three.gtf'))
        save(folder/'request.json',spec)
        arguments=['-s','no','-a','10','-m','union','--nonunique',policy,'--secondary-alignments','ignore',
            '--supplementary-alignments','ignore','-r','name','-t','exon','-i','gene_id',spec['alignment'],spec['annotation']]
        save(folder/'arguments.json',arguments)
        for label,tail in [('original-count',[linux(parent/'bootstrap.py'),linux(folder/'arguments.json')]),
            ('prepared-units',[linux(parent/'prepared-units.py'),linux(folder/'request.json'),linux(folder/'prepared-units.json')])]:
            argv=['wsl.exe','-d',DISTRO,'--','/usr/bin/env','PYTHONHASHSEED=0','PYTHONDONTWRITEBYTECODE=1',
                'OPENBLAS_NUM_THREADS=1','OMP_NUM_THREADS=1',runtime['python'],'-s','-B',*tail]
            with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:
                result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=120)
            save(folder/(label+'.process.json'),dict(command=argv,exit_code=result.returncode));assert result.returncode==0
        fields=[line.split('\t') for line in (folder/'original-count.stdout').read_text().splitlines()]
        assert len(fields)==11 and all(len(r)==2 for r in fields)
        table=dict(genes=fields[:-5],summary=fields[-5:]);save(folder/'source-count-table.json',table)
        results.append(dict(id=spec['id'],config=spec,counts=table,source_folder=policy))
    save(out/'COMPLETED.json',dict(schema_version=1,status='collected-original-three-feature-HTSeq-count-controls',
        actual_original_calls=2,successful_original_calls=2,prepared_cases=2,cases=results,script_pin=pin(Path(__file__).read_bytes()),
        parent_original_matrix=pin((parent/'COMPLETED.json').read_bytes()),native_replay_pending=True))
    print('Collected2 additional actual original counts, including non-dyadic1/3 fractions; completed42-case matrix unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original fractional controls')
    main()
