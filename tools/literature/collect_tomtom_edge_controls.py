# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect original Tomtom parameter/population boundaries; no Java result admission."""
import argparse
import json
import os
from pathlib import Path
import random
import shutil
import signal
import subprocess
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_meme_original_controls import pin,census,write,limits


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-proof',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--cases',nargs='+',help='Explicit named independent controls; omit to run all')
    args=parser.parse_args();out=args.out.resolve()
    assert out.is_relative_to((ROOT/'build/literature-review/meme-runtime').resolve()) and not out.exists()
    proof=json.loads(args.build_proof.read_bytes());assert proof['unchanged_original_C_and_H']==615
    home=Path(proof['prefix']);before=census(home)
    for name,value in proof['installed_files'].items():assert pin(home/name)==value
    area=Path(tempfile.mkdtemp(prefix='omicsmatic-tomtom-edges-',dir='/var/tmp'));area.chmod(0o700)
    out.mkdir(parents=True);write(out/'OWNER.json',dict(linux_area=str(area),runtime_home=str(home),build_proof=pin(args.build_proof)))
    inputs=area/'inputs';inputs.mkdir();rng=random.Random(139)
    def document(path,motifs,background=(.4,.1,.1,.4)):
        text=['MEME version 5','','ALPHABET= ACGT','','strands: + -','','Background letter frequencies',' '.join(b+' '+str(p) for b,p in zip('ACGT',background)),'']
        for name,sites,matrix in motifs:
            text.extend(['MOTIF '+name,'letter-probability matrix: alength= 4 w= '+str(len(matrix))+' nsites= '+str(sites)+' E= 0'])
            text.extend(' '.join(format(p,'.17g') for p in row) for row in matrix);text.append('')
        path.write_text('\n'.join(text),encoding='ascii')
    def matrix(consensus):return [[.7 if b==letter else .1 for b in 'ACGT'] for letter in consensus]
    query=[('short',80,matrix('GAC')),('fractional',7.9,matrix('ACGTAC')),
           ('ties',80,[[.01,.01,.01,.97],[.35,.35,.15,.15],[.1,.1,.4,.4],[.3,.3,.1,.3],[.25,.25,.25,.25]])]
    targets=[]
    for i in range(120):
        rows=[]
        for j in range(3+i%6):
            weights=[rng.randint(1,100) for _ in range(4)];total=sum(weights);rows.append([x/total for x in weights])
        targets.append(('target-'+str(i).zfill(3),2.9 if i%2 else 80,rows))
    targets.extend([('reverse-hit',80,matrix('GTACGT')),('same-hit',80,matrix('ACGTAC'))])
    document(inputs/'queries.meme',query);document(inputs/'targets.meme',targets)
    document(inputs/'subunit-query.meme',[('subunit',.5,matrix('ATGC'))])
    document(inputs/'single-query.meme',[query[1]])
    document(inputs/'targets-skewed.meme',targets,(.1,.2,.3,.4))
    document(inputs/'single-target.meme',[targets[0]],(.1,.2,.3,.4))
    document(inputs/'mixed-zero.meme',targets[:4]+[('zero',80,[[1,0,0,0]]*6)])
    document(inputs/'targets-a.meme',targets[:61]);document(inputs/'targets-b.meme',targets[61:])
    document(inputs/'targets-duplicate.meme',[targets[0]])
    document(inputs/'zero-target.meme',[('zero',80,[[1,0,0,0]]*6)])
    (inputs/'external-bg.txt').write_text('A 0.1\nC 0.4\nG 0.3\nT 0.2\n',encoding='ascii')
    jobs=[]
    for distance in ['ed','kullback','pearson','allr','sandelin','blic1','blic5','llr1','llr5']:
        jobs.append((distance+'-all', ['-dist',distance,'-motif-pseudo','0.1','-thresh','1'],['targets.meme'],False))
    jobs.extend([
      ('threshold-zero',['-dist','ed','-thresh','0'],['targets.meme'],False),
      ('threshold-small',['-dist','ed','-thresh','0.0001'],['targets.meme'],False),
      ('threshold-evalue',['-dist','ed','-evalue','-thresh','1'],['targets.meme'],False),
      ('internal',['-dist','ed','-internal','-min-overlap','8','-thresh','1'],['targets.meme'],False),
      ('overlap',['-dist','ed','-min-overlap','5','-norc','-thresh','1'],['targets.meme'],False),
      ('incomplete',['-dist','kullback','-incomplete-scores','-thresh','1'],['targets.meme'],False),
      ('pseudo-zero',['-dist','ed','-motif-pseudo','0','-thresh','1'],['targets.meme'],False),
      ('pseudo-large',['-dist','ed','-motif-pseudo','50','-thresh','1'],['targets.meme'],False),
      ('multiple-databases',['-dist','ed','-thresh','1'],['targets-a.meme','targets-b.meme'],False),
      ('duplicate-target-databases',['-dist','ed','-thresh','1'],['targets-a.meme','targets-duplicate.meme'],False),
      ('external-background',['-dist','allr','-bfile',str(inputs/'external-bg.txt'),'-thresh','1'],['targets.meme'],False),
      ('zero-excluded-allr',['-dist','allr','-motif-pseudo','0','-thresh','1'],['targets.meme','zero-target.meme'],False),
      ('paper-text',['-dist','kullback','-motif-pseudo','0.1','-min-overlap','1','-thresh','1'],['targets.meme'],True),
      ('subunit-site-count',['-dist','ed','-thresh','1'],['targets.meme'],False),
      ('single-query',['-dist','ed','-thresh','1'],['targets-skewed.meme'],False),
      ('single-target',['-dist','ed','-thresh','1'],['single-target.meme'],False),
      ('single-query-single-target',['-dist','ed','-thresh','1'],['single-target.meme'],False),
      ('single-query-external-bg',['-dist','allr','-bfile',str(inputs/'external-bg.txt'),'-thresh','1'],['targets-skewed.meme'],False),
      ('mixed-zero-allr',['-dist','allr','-motif-pseudo','0','-thresh','1'],['mixed-zero.meme'],False),
    ])
    if args.cases:
        assert len(args.cases)==len(set(args.cases)) and set(args.cases)<={j[0] for j in jobs}
        jobs=[j for j in jobs if j[0] in args.cases]
    input_pins=census(inputs);results=[]
    for name,options,targets_for_job,text in jobs:
        folder=area/name;folder.mkdir()
        command=[str(home/'bin/tomtom'),*options]
        if text:command.append('-text')
        else:command+=['-o',str(folder/'result')]
        query_input='subunit-query.meme' if name=='subunit-site-count' else 'single-query.meme' if name in {'single-query','single-query-single-target','single-query-external-bg'} else 'queries.meme'
        command += [str(inputs/query_input),*[str(inputs/p) for p in targets_for_job]]
        env=dict(PATH=str(home/'bin')+':'+str(home/'libexec/meme-5.5.9')+':/usr/bin:/bin',HOME=str(area),LC_ALL='C',TMPDIR=str(area),PYTHONDONTWRITEBYTECODE='1')
        with (folder/'stdout.log').open('wb') as stdout,(folder/'stderr.log').open('wb') as stderr:
            process=subprocess.Popen(command,cwd=folder,env=env,stdout=stdout,stderr=stderr,start_new_session=True,preexec_fn=limits)
            timeout=False
            try:code=process.wait(timeout=240)
            except subprocess.TimeoutExpired:
                timeout=True;os.killpg(process.pid,signal.SIGTERM)
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait(timeout=10)
                code=process.returncode
        row=dict(name=name,command=command,options=options,query_input=query_input,target_inputs=targets_for_job,text_mode=text,environment=env,exit_code=code,timed_out=timeout)
        write(folder/'PROCESS.json',row);row['native_inventory']=census(folder);write(out/(name+'-receipt.json'),row);results.append(row)
        print(name+': exit '+str(code),flush=True)
    assert census(home)==before and census(inputs)==input_pins
    inventory=census(area);write(out/'NATIVE-INVENTORY.json',inventory);shutil.copytree(area,out/'captured');copied=census(out/'captured')
    assert set(copied)==set(inventory)
    for name,row in inventory.items():assert {k:v for k,v in row.items() if k!='mode'}=={k:v for k,v in copied[name].items() if k!='mode'}
    assert census(home)==before and census(inputs)==input_pins
    result=dict(status='collected-original-Tomtom-complete-parameter-and-population-controls-native-audit-pending',version='5.5.9',jobs=results,families=len(results),
        successful_processes=sum(row['exit_code']==0 and not row['timed_out'] for row in results),runtime_and_inputs_unchanged=True,
        source_pins={path.relative_to(ROOT).as_posix():pin(path) for path in [Path(__file__),ROOT/'tools/literature/collect_meme_original_controls.py']},
        scientific_runtime_admitted=False,implementation_credit=False,
        required_action='Audit complete query/target and reported/unreported populations, raw matrices/sites/backgrounds, orientation/offset/consensus, original probabilities and separate precision. Preserve zero-excluded/duplicate database behaviors and every warning. Exit0 and version probes do not admit Java analyses or biological claims.')
    write(out/'COMPLETED.json',result);print(json.dumps(dict(status=result['status'],families=result['families'],successful_processes=result['successful_processes'])),flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original runtime/input identity checks')
    main()
