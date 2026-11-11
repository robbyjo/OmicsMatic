# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare deterministic complete PPI families and run unchanged original projectData."""
import argparse,json,random,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save
from setup_chipseeker_reference import inventory
BASE=ROOT/'build/literature-review/cellchat-runtime'

def table(path,rows,columns,values):
    path.write_text('gene_id\t'+'\t'.join(columns)+'\n'+''.join(name+'\t'+'\t'.join(format(v,'.17g') for v in row)+'\n' for name,row in zip(rows,values)),encoding='utf8',newline='\n')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_CELLCHAT_PROJECTION_REFERENCE',implementation_credit=False,whole_goal_complete=False);libraries=[];before={};bindings={};source=None;members={}
    try:
        setup=BASE/'edition-1.1.3-setup-1';assert json.loads((setup/'COMPLETED.json').read_bytes())['status']=='INSTALLED_ORIGINAL_CELLCHAT_EDITION_ONLY'
        libraries=[setup/'R-lib',*[Path(p) for p in json.loads((setup/'STARTED.json').read_bytes())['libraries']]];before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
        source=BASE/'source-intake-1/1.1.3/source';members=json.loads((source.parent/'original-members.json').read_bytes());assert all(pin(source/n)==v for n,v in members.items())
        scripts=[Path(__file__).resolve(),ROOT/'tools/literature/reference_cellchat_projection.R'];bindings={p.relative_to(ROOT).as_posix():pin(p) for p in scripts}
        for script in scripts:
            target=out/'source-snapshot'/script.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(script.read_bytes())
        rng=random.Random(819177);cases=[]
        for i in range(48):
            n=[1,2,3,8,17,31,64,9][i%8];cells=[1,2,5,9][(i//2)%4];mode=['partial','all','none'][i%3]
            network=[f'G{j:03d}' for j in range(n)];genes=network[:] if mode=='all' else network[::2]+['outside gene Ω'] if mode=='partial' else ['outside gene Ω','unmapped']
            rng.shuffle(genes);names=[f'cell {j} Ω' for j in range(cells)]
            values=[[0 if rng.random()<.35 else rng.random()*9 for c in names] for g in genes]
            adjacency=[[rng.random()*5 if rng.random()<.35 else 0 for j in network] for g in network]
            # Include the COMPLETE network, including genes with no measured row.
            for j in range(n):adjacency[j][(j+1)%n]+=1
            alpha=[1e-8,.1,.5,.9,.999,.999999][i//8];normalization=['rows','columns'][i%2]
            home=out/f'case-{i+1:03d}';home.mkdir();table(home/'expression.tsv',genes,names,values);table(home/'ppi.tsv',network,network,adjacency)
            cases.append(dict(id=home.name,alpha=alpha,normalization=normalization,network=n,cells=cells,overlap=mode))
        # Missing intermediate gene must participate: A -> absent B -> C.
        for mode in ['rows','columns']:
            home=out/f'bridge-{mode}';home.mkdir();table(home/'expression.tsv',['C','A','outside'],['cell'],[[0],[8],[3]]);table(home/'ppi.tsv',['A','B','C'],['A','B','C'],[[1,2,0],[0,1,4],[3,0,1]])
            cases.append(dict(id=home.name,alpha=.5,normalization=mode,network=3,cells=1,overlap='bridge'))
        save(out/'cases.json',cases);save(out/'STARTED.json',dict(libraries=list(map(str,libraries)),source_pins=bindings,source_members=members,cases=len(cases)))
        command=[str(RSCRIPT),'--vanilla',str(out/'source-snapshot/tools/literature/reference_cellchat_projection.R'),str(out),str(source),*map(str,libraries)]
        with (out/'reference.stdout').open('xb') as stdout,(out/'reference.stderr').open('xb') as stderr:run=subprocess.run(command,cwd=ROOT,env=environment(),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=1800)
        save(out/'process.json',dict(command=command,exit_code=run.returncode));result=json.loads((out/'R-RESULT.json').read_bytes());assert run.returncode==0 and result['status']=='PASS_ORIGINAL_CELLCHAT_1_1_3_PROJECTDATA_CONTROLS_ONLY',result
        state.update(status='PASS_ORIGINAL_CELLCHAT_PROJECTION_REFERENCE_ONLY',case_count=len(cases),original_result=result,source_members=members)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);state.update(preexisting_libraries_unchanged=before==after,source_pins=bindings)
        if before!=after or any(pin(ROOT/n)!=v for n,v in bindings.items()) or source is not None and any(pin(source/n)!=v for n,v in members.items()):state.update(status='FAIL2_REFERENCE_SOURCE_OR_LIBRARY_CHANGED')
        save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-source/reference checks')
    raise SystemExit(main())
