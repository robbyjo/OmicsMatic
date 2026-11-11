# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Probe original motif formats/defaults/row preprocessing without admitting project analyses."""
import argparse
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_meme_original_controls import pin,census,write,limits


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build-proof',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();assert out.is_relative_to((ROOT/'build/literature-review/meme-runtime').resolve()) and not out.exists()
    proof=json.loads(args.build_proof.read_bytes());home=Path(proof['prefix']);before=census(home)
    for name,value in proof['installed_files'].items():assert pin(home/name)==value
    area=Path(tempfile.mkdtemp(prefix='omicsmatic-tomtom-inputs-',dir='/var/tmp'));area.chmod(0o700);out.mkdir(parents=True)
    write(out/'OWNER.json',dict(linux_area=str(area),runtime_home=str(home),build_proof=pin(args.build_proof)))
    inputs=area/'inputs';inputs.mkdir()
    header='MEME version 5\n\nALPHABET= ACGT\n\nstrands: + -\n\nBackground letter frequencies\nA 0.4 C 0.1 G 0.1 T 0.4\n\n'
    def motif(identity='first',sites=' nsites= 20',evalue=' E= 0',mass=1.0,url=''):
        row=' '.join(format(v*mass,'.17g') for v in [.7,.1,.1,.1])
        return 'MOTIF '+identity+' alt-'+identity+'\nletter-probability matrix: alength= 4 w= 6'+sites+evalue+'\n'+(row+'\n')*6+('\nURL '+url+'\n' if url else '')+'\n'
    (inputs/'targets.meme').write_text(header+motif('target-a')+motif('target-b'),encoding='utf-8')
    cases=[]
    def add(name,text):
        path=inputs/(name+'.meme');path.write_text(text,encoding='utf-8');cases.append((name,path))
    for mass in [1.0,1.000001,.999999,1.000009,.999991,1.000011,.999989,1.01,.99,.5,0.0]:
        add('mass-'+str(mass),header+motif(mass=mass)+motif('second'))
    add('missing-sites',header+motif(sites='')+motif('second'))
    add('zero-sites',header+motif(sites=' nsites= 0')+motif('second'))
    add('missing-evalue',header+motif(evalue='')+motif('second'))
    add('metadata-url',header+motif(sites=' nsites= 20.5',evalue=' E= 1.234e-200',url='https://example.test/motif?x=1&y=2')+motif('second'))
    add('missing-background',header[:header.index('Background letter frequencies')]+motif()+motif('second'))
    add('equals-id',header+motif(identity='TF=first')+motif('second'))
    add('unicode-id',header+motif(identity='TFα')+motif('second'))
    original=ROOT/'build/literature-review/meme-runtime/original-controls-2/captured'
    for label,folder,stem in [('MEME',original/'meme-oops-forward/result','meme'),('STREME',original/'streme-supplied-control/result','streme')]:
        for extension in ['txt','xml','html']:
            name=label+'-'+extension;target=inputs/(name+'.'+extension);shutil.copyfile(folder/(stem+'.'+extension),target);cases.append((name,target))
    input_pins=census(inputs);results=[]
    for name,query in cases:
        folder=area/name;folder.mkdir();command=[str(home/'bin/tomtom'),'-dist','ed','-motif-pseudo','0.1','-thresh','1','-o',str(folder/'result'),str(query),str(inputs/'targets.meme')]
        env=dict(PATH=str(home/'bin')+':'+str(home/'libexec/meme-5.5.9')+':/usr/bin:/bin',HOME=str(area),LC_ALL='C',TMPDIR=str(area),PYTHONDONTWRITEBYTECODE='1')
        with (folder/'stdout.log').open('wb') as stdout,(folder/'stderr.log').open('wb') as stderr:
            process=subprocess.Popen(command,cwd=folder,env=env,stdout=stdout,stderr=stderr,start_new_session=True,preexec_fn=limits);timeout=False
            try:code=process.wait(timeout=240)
            except subprocess.TimeoutExpired:
                timeout=True;os.killpg(process.pid,signal.SIGTERM)
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait(timeout=10)
                code=process.returncode
        row=dict(name=name,query_input=query.name,command=command,environment=env,exit_code=code,timed_out=timeout);write(folder/'PROCESS.json',row)
        row['native_inventory']=census(folder);results.append(row);write(out/(name+'-receipt.json'),row);print(name+': exit '+str(code),flush=True)
    assert census(home)==before and census(inputs)==input_pins
    inventory=census(area);write(out/'NATIVE-INVENTORY.json',inventory);shutil.copytree(area,out/'captured');copied=census(out/'captured');assert set(copied)==set(inventory)
    for name,value in inventory.items():assert {k:v for k,v in value.items() if k!='mode'}=={k:v for k,v in copied[name].items() if k!='mode'}
    assert census(home)==before and census(inputs)==input_pins
    result=dict(status='collected-original-Tomtom-input-format-and-preprocessing-controls-native-admission-pending',version='5.5.9',jobs=results,families=len(results),successful_processes=sum(r['exit_code']==0 and not r['timed_out'] for r in results),
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/collect_meme_original_controls.py']},runtime_and_inputs_unchanged=True,implementation_credit=False,
        scope='Raw original minimal/learned text/XML/HTML formats and explicit defaults/metadata/row-mass probes. No normalization or format substitution is delivered to the original program. Exit0 is not native input/output, runtime or project scientific admission.')
    write(out/'COMPLETED.json',result);print(json.dumps(dict(status=result['status'],families=result['families'],successful_processes=result['successful_processes'])),flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/input/runtime checks')
    main()
