# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual original MTAG4-through8-trait workflows, including the full default four-trait maxFDR grid."""
import argparse
import csv
import io
import json
import math
import os
import random
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE,linux
from validate_mtag_workflow_contracts import baseline
from validate_mtag_tools_safety import census


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--runtime-profile',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    state=dict(status='FAIL2_MTAG_ORIGINAL_DIMENSION_CONTROLS',new_catalog_credit=False,whole_paper_reproduced=False,synthetic_only=True)
    (out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';home=ROOT/'docs/literature/mtag-workflow-development';proof=json.loads((home/'VALIDATION.json').read_bytes());profile=a.runtime_profile.resolve()
        jars={v:home/('mtag-root-javac'+v+'.jar') for v in ['21','25']}
        assert all(pin(jar)==proof['artifacts'][jar.name] for jar in jars.values()) and pin(canonical)==proof['canonical_executable_unchanged']
        protected={str(path):pin(path) for path in [canonical,profile,*jars.values()]}
        source=BASE/'original-workflow-probe-1/inputs/trait1.tsv'
        with source.open(encoding='utf8',newline='') as stream:reference=list(csv.DictReader(stream,delimiter='\t'))[:1000]
        assert len(reference)==1000
        rng=random.Random(20261111);fields=['snpid','chr','bpos','a1','a2','z','n','frequency','p'];raw=[[] for _ in range(8)]
        # One explicit eight-trait synthetic population; lower-dimensional
        # tests use its declared prefixes. Pair signs to keep every median0.
        for i in range(500):
            shared=rng.gauss(0,1);specific=[rng.gauss(0,1) for _ in range(8)];noise=[rng.gauss(0,1) for _ in range(8)]
            for sign,offset in [(1,0),(-1,500)]:
                row=reference[i+offset]
                for t in range(8):
                    n=90000+10000*t;beta=math.sqrt(1.5e-5)*shared+math.sqrt(.5e-5)*specific[t];z=sign*(math.sqrt(n)*beta+noise[t])
                    raw[t].append(dict(snpid=row['snpid'],chr=row['chr'],bpos=row['bpos'],a1='A',a2='C',z=repr(z),n=str(n),frequency=repr(.2+.2*rng.random()),p=repr(math.erfc(abs(z)/math.sqrt(2)))))
        inputs=out/'declared-inputs';inputs.mkdir()
        for t,rows in enumerate(raw):
            rows.sort(key=lambda r:int(r['bpos']));stream=io.StringIO(newline='');writer=csv.DictWriter(stream,fieldnames=fields,delimiter='\t',lineterminator='\n');writer.writeheader();writer.writerows(rows);(inputs/('trait'+str(t+1)+'.tsv')).write_text(stream.getvalue(),encoding='utf8',newline='\n')
        generation=dict(status='PREDECLARED_SYNTHETIC_ONLY_DIMENSION_CONTROLS',seed=20261111,traits=8,rows_per_trait=1000,shared_population_prefixes=True,
            z_generation='sqrt(N)*(sqrt(1.5e-5)*shared_standard_normal+sqrt(0.5e-5)*trait_standard_normal)+trait_standard_normal_noise; each signed pair retained',
            supplied_sigma='identity',supplied_omega_diagonal=2e-5,supplied_omega_offdiagonal=1.5e-5,force_low_signal=False,frequency_header='frequency',
            fdr_choices='Four traits: original default10 intervals. Five through eight: explicit2 intervals, not paper defaults and never a fallback after failure.',
            working_bytes=64000000000,maximum_grid_points=4000000,wall_seconds=3600,independent_biological_datasets=0)
        save(out/'GENERATION.json',generation);recipes=[]
        for traits in range(4,9):
            home=out/('traits-'+str(traits));home.mkdir();r=baseline();r['runtime_profile']=linux(profile);r['output_dir']='analysis'
            r['traits']=[dict(name='trait'+str(t+1),input=linux(inputs/('trait'+str(t+1)+'.tsv'))) for t in range(traits)]
            r['columns']['freq']='frequency';r['filters'].update(maf_min=[.01]*traits,n_min=[1]*traits);r['fdr'].update(intervals=10 if traits==4 else 2,cores=1)
            for name in ['sigma','omega']:
                matrix=[[float(i==j) if name=='sigma' else 2e-5 if i==j else 1.5e-5 for j in range(traits)] for i in range(traits)]
                path=home/(name+'.txt');path.write_text(''.join(' '.join(format(x,'.17g') for x in row)+'\n' for row in matrix),encoding='ascii')
                r['covariance'].update({name:'provided',name+'_file':linux(path)})
            r['limits'].update(working_bytes=64000000000,maximum_fdr_grid_points=4000000,wall_seconds=3600,output_bytes=2000000000)
            save(home/'request.json',r);recipes.append((traits,home));save(home/'PREDECLARED.json',dict(traits=traits,request=pin(home/'request.json'),input_pins={p.name:pin(p) for p in inputs.iterdir()}))
        commands={
            'linux21':['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx2g','-cp',linux(jars['21'])+':'+linux(canonical),'org.jlinalg.cli.JLinAlgCli','mtag-tools'],
            'windows21':[r'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx2g','-cp',str(jars['21'])+os.pathsep+str(canonical),'org.jlinalg.cli.JLinAlgCli','mtag-tools'],
            'windows25':[r'C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx2g','-cp',str(jars['25'])+os.pathsep+str(canonical),'org.jlinalg.cli.JLinAlgCli','mtag-tools']}
        records=[]
        for traits,home in recipes:
            cmd=commands['linux21']+['--request',linux(home/'request.json')];save(home/'command.json',dict(command=cmd))
            with (home/'stdout').open('xb') as so,(home/'stderr').open('xb') as se:r=subprocess.run(cmd,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=5000)
            save(home/'process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,(traits,(home/'stderr').read_text(errors='replace'))
            bundle=home/'analysis';audit=json.loads((bundle/'scientific-audit.json').read_bytes());assert audit['traits']==traits and audit['variants']==1000 and audit['fdr']['calculated']
            before=census(bundle);replays=[]
            for jvm,prefix in commands.items():
                cmd=prefix+['--verify',linux(bundle) if jvm=='linux21' else str(bundle)]
                with (home/(jvm+'-saved.stdout')).open('xb') as so,(home/(jvm+'-saved.stderr')).open('xb') as se:v=subprocess.run(cmd,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=600)
                save(home/(jvm+'-saved.process.json'),dict(command=cmd,exit_code=v.returncode));assert v.returncode==0 and census(bundle)==before
                replays.append(dict(jvm=jvm,passed=True))
            records.append(dict(traits=traits,audit=audit,replays=replays,passed=True));save(out/'progress.json',records);print('PASS original MTAG',traits,'traits;grid rows',audit['fdr']['grid_rows'],flush=True)
        for name,wanted in protected.items():assert pin(Path(name))==wanted
        state.update(status='PASS_MTAG4_THROUGH8_ORIGINAL_WORKFLOWS_DEFAULT4_TRAIT_GRID_AND15_SAVED_REPLAYS',records=records,original_source_unchanged=True,
            source_parameters_repaired_or_retried_with_smaller_grid=False,canonical_executable_unchanged=True,guide_note='Synthetic prefix populations; no paper reconstruction or biological-accuracy certification.')
        print(state['status'])
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');print(state['status']+': '+repr(error),flush=True)
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original dimensions/default-grid and saved-bundle checks')
    raise SystemExit(main())
