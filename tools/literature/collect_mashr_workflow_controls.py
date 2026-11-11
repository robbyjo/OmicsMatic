#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the complete original mashr driver on declared synthetic populations.

No download/install; uses a completed private reference profile. Successful
upstream exit is explicitly unaudited until the separate native audit passes.
"""
import argparse
import concurrent.futures
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import random
import shutil
import stat
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/mashr-runtime'
RHOME=Path('C:/Program Files/R/R-4.6.1')
RSCRIPT=RHOME/'bin/Rscript.exe'
DRIVER=ROOT/'src/main/resources/org/jlinalg/omics/mashr-workflow/analysis.R'


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as target:json.dump(value,target,indent=2);target.write('\n')


def census(root):
    files={};size=0
    for path in sorted(root.rglob('*')):
        info=path.lstat()
        if path.is_symlink() or getattr(info,'st_file_attributes',0)&0x400:raise ValueError('Reparse point in exact runtime closure: '+str(path))
        if path.is_file():
            size+=info.st_size
            if len(files)>=100000 or size>4_294_967_296:raise ValueError('Complete runtime closure exceeds explicit census capacity')
            files[path.relative_to(root).as_posix()]=pin(path)
    return files


def settings(**overrides):
    result=dict(schema_version='1',alpha='0',covariance_mode='canonical',npc='2',ed_tolerance='1e-6',ed_max_iterations='2000',
                residual_mode='fixed',residual_initial='identity',null_z_threshold='2',residual_max_iterations='40',
                residual_tolerance='1e-4',prior='nullbiased',null_weight='10',optimizer='mixSQP',optimizer_tolerance='1e-10',
                optimizer_max_iterations='10000',pi_threshold='1e-10',normalize_u='1',use_point_mass='1',grid='.25,.5,1,2,4',
                algorithm_version='Rcpp',seed='123',fit_dual_gap_tolerance='1e-6')
    result.update(overrides);return result


def population():
    rng=random.Random(702792);n=3;rows=[]
    for feature in range(120):
        se=[math.exp(rng.uniform(-.3,.3)) for _ in range(n)]
        independent=[rng.gauss(0,1) for _ in range(n)];noise=[independent[0]]
        for condition in range(1,n):noise.append(.3*noise[-1]+math.sqrt(1-.3**2)*independent[condition])
        mode=rng.random();shared=rng.gauss(0,2)
        beta=[0.]*n if mode<.6 else [shared]*n if mode<.85 else [shared,-shared*.6,shared*.3]
        effects=[beta[c]+se[c]*noise[c] for c in range(n)]
        rows.append(dict(feature='gene-'+str(feature+1),effects=effects,se=se,train=int(feature<100),strong=0,target=int(feature>=100)))
    strongest=sorted(range(100),key=lambda i:max(abs(x/s) for x,s in zip(rows[i]['effects'],rows[i]['se'])),reverse=True)[:24]
    for i in strongest:rows[i]['strong']=1
    return rows


def inputs(folder,config,rows,conditions=None):
    conditions=conditions or ['cell-A','cell-B','cell-C']
    folder.mkdir()
    with (folder/'settings.tsv').open('x',encoding='utf-8',newline='\n') as out:
        out.write('key\tvalue\n');out.writelines(k+'\t'+v+'\n' for k,v in config.items())
    for filename,key in [('effects.tsv','effects'),('standard-errors.tsv','se')]:
        with (folder/filename).open('x',encoding='utf-8',newline='\n') as out:
            out.write('feature\t'+'\t'.join(conditions)+'\n')
            out.writelines(row['feature']+'\t'+'\t'.join(format(x,'.17g') for x in row[key])+'\n' for row in rows)
    with (folder/'populations.tsv').open('x',encoding='utf-8',newline='\n') as out:
        out.write('feature\ttrain\tstrong\ttarget\n')
        out.writelines(row['feature']+'\t'+'\t'.join(str(row[k]) for k in ('train','strong','target'))+'\n' for row in rows)
    if config['residual_initial']=='supplied':
        with (folder/'residual.tsv').open('x',encoding='utf-8',newline='\n') as out:
            out.write('condition\t'+'\t'.join(conditions)+'\n')
            for i,name in enumerate(conditions):out.write(name+'\t'+'\t'.join(format(.2**abs(i-j),'.17g') for j in range(len(conditions)))+'\n')


def main():
    if not __debug__:raise RuntimeError('Run with assertions enabled, never Python -O')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--runtime',required=True,type=Path)
    args=parser.parse_args();out,runtime=args.out.resolve(),args.runtime.resolve()
    if not out.is_relative_to(BASE.resolve()) or out==BASE.resolve() or out.exists():raise ValueError('Use a NEW private output below mashr-runtime')
    profile=json.loads((runtime/'COMPLETED.json').read_text());assert profile['status']=='COMPLETED_PRIVATE_REFERENCE_SETUP_ONLY'
    assert pin(RSCRIPT)=={key:profile['rscript'][key] for key in ('bytes','sha256')}
    out.mkdir(parents=True)
    try:
        shutil.copyfile(Path(__file__),out/'collector.py');shutil.copyfile(DRIVER,out/'analysis.R')
        before=dict(rhome=census(RHOME),library=census(runtime/'library'))
        write(out/'runtime-before.json',dict(rhome=str(RHOME),library=str(runtime/'library'),rscript=pin(RSCRIPT),files=before))
        rows=population();protocols=[]
        for mode in ('canonical','canonical_pca_ed'):
            for residual in ('fixed','em'):
                for algorithm in ('Rcpp','R'):
                    name=mode+'-'+residual+'-'+algorithm
                    config=settings(covariance_mode=mode,residual_mode=residual,algorithm_version=algorithm,
                                    residual_initial='simple_null' if residual=='em' else 'identity')
                    protocols.append(dict(name=name,settings=config,rows=rows,expected=0))
        changed=[dict(row,effects=[v+(.5 if row['target'] else 0.) for v in row['effects']]) for row in rows]
        protocols.append(dict(name='target-values-cannot-train-prior',settings=settings(covariance_mode='canonical_pca_ed',residual_mode='em',residual_initial='simple_null'),rows=changed,expected=0))
        large=[dict(row,se=list(row['se']),effects=list(row['effects'])) for row in rows]
        large[-1]['effects'][1]=0.;large[-1]['se'][1]=1e10
        protocols.append(dict(name='explicit-large-SE-target',settings=settings(covariance_mode='canonical_pca_ed'),rows=large,expected=0))
        numeric=[dict(row,feature=str(i+1).zfill(4)) for i,row in enumerate(rows)]
        protocols.append(dict(name='numeric-feature-identities',settings=settings(),rows=numeric,expected=0))
        for algorithm in ('Rcpp','R'):
            protocols.append(dict(name='alpha-one-data-driven-em-'+algorithm,
                settings=settings(alpha='1',covariance_mode='canonical_pca_ed',residual_mode='em',residual_initial='simple_null',algorithm_version=algorithm),rows=rows,expected=0))
        protocols.append(dict(name='simple-null-residual',settings=settings(residual_mode='simple_null'),rows=rows,expected=0))
        protocols.append(dict(name='uniform-no-point-mass-mixEM-supplied',
            settings=settings(prior='uniform',use_point_mass='0',normalize_u='0',optimizer='mixEM',optimizer_tolerance='1e-12',optimizer_max_iterations='100000',residual_initial='supplied'),rows=rows,expected=0))
        single=[r for r in rows if r['train'] or r['feature']==rows[-1]['feature']]
        protocols.append(dict(name='single-target',settings=settings(algorithm_version='R'),rows=single,expected=2,expected_source_error='logical subscript too long'))
        protocols.append(dict(name='single-target-Rcpp-explicit-failure',settings=settings(),rows=single,expected=2,expected_source_error='SOURCE_RCPP_SINGLE_FEATURE_UNAVAILABLE'))
        singleTrain=[dict(r,train=int(i==0)) for i,r in enumerate(rows) if i==0 or r['target']]
        protocols.append(dict(name='single-training',settings=settings(algorithm_version='R'),rows=singleTrain,expected=2,expected_source_error='dims [product 3] do not match the length of object [41]'))
        protocols.append(dict(name='single-training-Rcpp-explicit-failure',settings=settings(),rows=singleTrain,expected=2,expected_source_error='SOURCE_RCPP_SINGLE_FEATURE_UNAVAILABLE'))
        overlapping=[dict(r,target=1) for r in rows]
        protocols.append(dict(name='overlapping-training-targets',settings=settings(covariance_mode='canonical_pca_ed'),rows=overlapping,expected=0))
        protocols.append(dict(name='colliding-canonical-component-labels',settings=settings(),rows=rows,conditions=['identity','cell-B','cell-C'],expected=0))
        for name,changes in [('ed-iteration-limit',dict(covariance_mode='canonical_pca_ed',ed_max_iterations='2')),
                             ('residual-iteration-limit',dict(residual_mode='em',residual_tolerance='0',residual_max_iterations='2')),
                             ('unknown-setting',dict(unreported_option='1'))]:
            protocols.append(dict(name=name,settings=settings(**changes),rows=rows,expected=2))
        bad=[dict(row,se=list(row['se'])) for row in rows];bad[0]['se'][0]=0.
        protocols.append(dict(name='invalid-zero-SE',settings=settings(),rows=bad,expected=2))
        protocols.append(dict(name='missing-upstream-library',settings=settings(),rows=rows,expected=3,missing_dependency=True))
        for protocol in protocols:
            folder=out/protocol['name'];folder.mkdir();inputs(folder/'inputs',protocol['settings'],protocol['rows'],protocol.get('conditions'))
            protocol['expected_training_rows']=sum(r['train'] for r in protocol['rows']);protocol['expected_target_rows']=sum(r['target'] for r in protocol['rows'])
            basis=8+(int(protocol['settings']['npc'])+1 if protocol['settings']['covariance_mode']=='canonical_pca_ed' else 0)
            protocol['expected_components']=len(protocol['settings']['grid'].split(','))*basis+int(protocol['settings']['use_point_mass'])
            (folder/'scratch').mkdir()
        (out/'empty-library').mkdir()
        def execute(protocol):
            name=protocol['name'];folder=out/name;lib=out/'empty-library' if protocol.get('missing_dependency') else runtime/'library'
            bound={p.relative_to(folder/'inputs').as_posix():pin(p) for p in (folder/'inputs').iterdir() if p.is_file()}
            command=[str(RSCRIPT),'--vanilla',str(out/'analysis.R'),str(lib),str(folder/'inputs'),str(folder/'outputs')]
            overrides=dict(LANG='C',LC_ALL='C',R_LIBS_USER=str(lib),R_LIBS_SITE='',R_DEFAULT_PACKAGES='datasets,utils,grDevices,graphics,stats,methods',
                           OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(folder/'scratch'),TMP=str(folder/'scratch'),TEMP=str(folder/'scratch'))
            write(folder/'command.json',dict(argv=command,cwd=str(folder),environment_overrides=overrides,timeout_seconds=600))
            with (folder/'process.stdout').open('xb') as stdout,(folder/'process.stderr').open('xb') as stderr:
                process=subprocess.run(command,cwd=folder,env=dict(os.environ,**overrides),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=600,check=False)
            result=dict(name=name,exit_code=process.returncode,expected_exit_code=protocol['expected'],scientific_result_admitted=False,
                        expected_training_rows=protocol['expected_training_rows'],expected_target_rows=protocol['expected_target_rows'],expected_components=protocol['expected_components'])
            write(folder/'process.json',result)
            assert bound=={p.relative_to(folder/'inputs').as_posix():pin(p) for p in (folder/'inputs').iterdir() if p.is_file()},'Original request changed during execution'
            if protocol.get('expected_source_error'):
                error=(folder/'outputs/error.txt').read_text();assert protocol['expected_source_error'] in error,(name,error)
                assert 'Required action' in (folder/'outputs/WARNINGS.md').read_text()
            print(name+': '+str(process.returncode)+' expected '+str(protocol['expected']),flush=True)
            return result
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(execute,protocols))
        after=dict(rhome=census(RHOME),library=census(runtime/'library'));write(out/'runtime-after.json',dict(files=after));assert before==after,'Original runtime closure changed during controls'
        write(out/'outcomes.json',results)
        wrong=[r for r in results if r['exit_code']!=r['expected_exit_code']]
        if wrong:raise RuntimeError('Original workflow outcomes differ from expected: '+json.dumps(wrong))
        baseline=out/'canonical_pca_ed-em-Rcpp/outputs';changed=out/'target-values-cannot-train-prior/outputs'
        names={p.relative_to(baseline/'model').as_posix() for p in (baseline/'model').rglob('*') if p.is_file()}
        assert names=={p.relative_to(changed/'model').as_posix() for p in (changed/'model').rglob('*') if p.is_file()}
        assert all((baseline/'model'/name).read_bytes()==(changed/'model'/name).read_bytes() for name in names),'Targets leaked into prior/residual learning'
        assert (baseline/'posterior/mean.tsv').read_bytes()!=(changed/'posterior/mean.tsv').read_bytes(),'Controlled target alteration failed to affect posterior'
        write(out/'ORIGINAL-CONTROLS.json',dict(schema_version=1,status='original-workflow-controls-returned-awaiting-native-audit',protocols=results,
             target_population_leakage_control='passed',runtime_files=sum(map(len,before.values())),source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [DRIVER,Path(__file__)]},
             scientific_result_admitted=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}))
        return 0
    except Exception as error:
        write(out/'FAILURE.json',dict(status='FAIL2_WORKFLOW_CONTROLS_INCOMPLETE',message=str(error)))
        (out/'WARNINGS.md').write_text('# FAIL2: original workflow validation incomplete\n\n'+str(error)+'\n\nRequired action: preserve all attempts and original outputs. Inspect each process and source-status file. Correct the failure and repeat into a NEW directory. No study analysis is admitted.\n',encoding='utf-8',newline='\n')
        raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('MASHR WORKFLOW CONTROLS FAIL2: '+str(failure),file=sys.stderr);sys.exit(2)
