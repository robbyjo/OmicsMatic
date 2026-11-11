# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the separate typed original worker; this probe does not admit a Java execution adapter."""
import argparse,csv,gzip,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def fixed(version,mode='constant'):
    e=dict(kind='fixed',rho_mode=mode)
    if mode=='constant':e['rho']=.1
    if version=='1.6.2':e['force_accept']=False
    return e


def manual(version,provided):
    e=dict(kind='manual-global-poisson',mask_mode='provided' if provided else 'estimate')
    if not provided:e.update(clustering_scope='supplied-clusters',maximum_contamination=1,fdr=.05)
    if version=='1.6.2':e['force_accept']=False
    else:e['cell_specific']=False
    return e


def analyses(version):
    adjustment=dict(method='subtraction',clustering_scope='cell-only',round_to_integer=False,tolerance=.001,p_cut=.01)
    cases=[dict(id='fixed-constant',seed=190731,estimation=fixed(version),adjustment=adjustment),dict(id='fixed-per-cell',seed=190731,estimation=fixed(version,'per-cell'),adjustment=adjustment),dict(id='manual-provided',seed=190731,estimation=manual(version,True),adjustment=None),dict(id='manual-estimated',seed=190731,estimation=manual(version,False),adjustment=None)]
    if version=='1.6.2':
        e=dict(kind='automatic-gamma',marker_selection='original-quickMarkers-defaults',tfidf_min=.95,soup_quantile=.6,maximum_markers=100,contamination_range=[.05,.8],rho_max_fdr=.2,prior_rho=.1,prior_rho_sd=.1,force_accept=False);cases.extend([dict(id='auto-estimate',seed=190731,estimation=e,adjustment=None),dict(id='auto-adjust',seed=190731,estimation=e,adjustment=adjustment)])
    else:
        e=dict(kind='automatic-legacy-density',marker_selection='original-quickMarkers-defaults',number_markers=50,tfidf_min=.8,soup_min=.0001,rho_max=1,fdr=.1,minimum_counts=1,density=dict(bandwidth='nrd0',adjust=1,kernel='gaussian',n=512,cut=3,ext=4,old_coordinates=False),apply_density_mode=False);cases.append(dict(id='auto-estimate',seed=190731,estimation=e,adjustment=None));e=dict(e,apply_density_mode=True);cases.append(dict(id='auto-adjust',seed=190731,estimation=e,adjustment=adjustment))
    return cases


def inputs(folder):
    fixture=ROOT/'src/test/resources/org/jlinalg/singlecell/soupx-ambient/inputs-v1.json.gz';data=json.loads(gzip.decompress(fixture.read_bytes()));raw=data['raw'];filtered=data['filtered'];assert raw['genes']==filtered['genes'];folder.mkdir();(folder/'genes.tsv').write_text('gene_id\n'+''.join(g+'\n' for g in raw['genes']),encoding='utf8',newline='\n');(folder/'droplets.tsv').write_text('droplet_id\n'+''.join(c+'\n' for c in raw['cells']),encoding='utf8',newline='\n');(folder/'cells.tsv').write_text('cell_id\tcluster\n'+''.join(c+'\t'+data['clusters'][c]+'\n' for c in filtered['cells']),encoding='utf8',newline='\n')
    for name,m in [('raw-counts',raw),('filtered-counts',filtered)]:
        (folder/name).write_text('%%MatrixMarket matrix coordinate real general\n% Literal original fixture counts; no duplicate aggregation\n'+f"{len(m['genes'])} {len(m['cells'])} {len(m['entries'])}\n"+''.join(f"{e['gene']+1} {e['cell']+1} {e['value']}\n" for e in m['entries']),encoding='utf8',newline='\n')
    (folder/'gene-sets.tsv').write_text('gene_set\tgene_id\nCD7\tCD7\n',encoding='utf8',newline='\n');first=data['clusters'][filtered['cells'][0]];(folder/'nonexpression-mask.tsv').write_text('cell_id\tgene_set\tnon_expressing\n'+''.join(c+'\tCD7\t'+str(data['clusters'][c]!=first).lower()+'\n' for c in filtered['cells']),encoding='utf8',newline='\n');(folder/'rho-by-cell.tsv').write_text('cell_id\trho\n'+''.join(c+'\t0.1\n' for c in filtered['cells']),encoding='utf8',newline='\n');return data


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--version',choices=['1.6.2','1.3.0'],required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/soupx-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();resources=ROOT/'src/main/resources/org/jlinalg/omics/soupx-workflow';profile=json.loads((resources/'SOURCE-PROFILE.json').read_bytes());source=base/('source-intake-1/1.6.2/source' if a.version=='1.6.2' else 'source-recovery-1/1.3.0/source');reference=base/('reference-1.6.2-1' if a.version=='1.6.2' else 'reference-1.3.0-2');libraries=[reference/'R-lib',*map(Path,json.loads((reference/'STARTED.json').read_bytes())['libraries'])];before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before);pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*resources.glob('*'),ROOT/'src/test/resources/org/jlinalg/singlecell/soupx-ambient/inputs-v1.json.gz']};stage=out/'stage';stage.mkdir();inputs(stage/'inputs');captured=stage/'source';captured.mkdir()
    for p in resources.glob('*'):(captured/p.name).write_bytes(p.read_bytes())
    for name,wanted in profile['editions'][a.version]['files'].items():
        assert pin(source/name)==wanted;target=captured/'original'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((source/name).read_bytes())
    worker=stage/'worker';worker.mkdir();(worker/'runtime-paths.txt').write_text(''.join(str(p)+'\n' for p in libraries),encoding='utf8',newline='\n');parameters=dict(version=a.version,matrix_format='matrix-market',clusters='supplied',counts_relation='raw-subset',soup_range=[0,100],analyses=analyses(a.version),gene_sets_provided=True,mask_provided=True,rho_by_cell_provided=True,maximum_intermediate_entries=5000000,transport_chunk_entries=1000);save(worker/'parameters.json',parameters)
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla','../source/worker.R'];save(out/'STARTED.json',dict(command=command,worker=str(worker),libraries=list(map(str,libraries)),source_pins=pins));env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',R_LIBS=';'.join(map(str,libraries)),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(worker),R_USER_CACHE_DIR=str(worker))
    with (worker/'stdout.log').open('xb') as stdout,(worker/'stderr.log').open('xb') as stderr:r=subprocess.run(command,cwd=worker,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);assert before==after and all(pin(ROOT/n)==v for n,v in pins.items());report=json.loads((worker/'worker-report.json').read_bytes());save(out/'COMPLETED.json',dict(status='completed-original-SoupX-worker-probe-only' if r.returncode==0 else 'FAILED_ORIGINAL_SOUPX_WORKER_PROBE',worker_status=report['status'],detail=report['detail'],cases=len(report['cases']),source_pins=pins,preexisting_libraries_unchanged=True,preexisting_files=sum(map(len,before.values())),ambient_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print(json.dumps(dict(exit_code=r.returncode,worker_status=report['status'],detail=report['detail'],cases=len(report['cases']))));return r.returncode


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/worker checks')
    raise SystemExit(main())
