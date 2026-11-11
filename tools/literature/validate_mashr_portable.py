#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Cross-producer public-bundle verification and a complete original learned model."""
import argparse
import concurrent.futures
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
from validate_mashr_native import ROOT,BASE,JAVA21,pin,linux


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def census(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}


def main():
    if not __debug__:raise RuntimeError('Never disable source/cross-platform assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    matrix,out=args.matrix.resolve(),args.out.resolve();assert matrix.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir(parents=True)
    try:
        parent=matrix/'COMPLETED.json';record=json.loads(parent.read_text());assert record['status']=='passed-actual-JAR-public-native-mash-workflow-matrix' and record['tests_per_JVM']==325
        assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items()) and all(pin(matrix/n)==v for n,v in record['files'].items())
        jar=ROOT/record['artifact']['path'];assert pin(jar)=={k:record['artifact'][k] for k in ['bytes','sha256']}
        fixture=ROOT/'src/test/resources/org/jlinalg/cli/mashr-workflow/original-controls.zip';assert pin(fixture)['sha256']=='28187ad99751081f608473ffe6a24f2a7e61081e9eb5bdddfddc6de361239d42'
        bound={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/validate_mashr_native.py',fixture]}
        before_parent=pin(parent);before_jar=pin(jar)
        original='canonical_pca_ed-em-Rcpp';prefix=original+'/outputs/'
        with zipfile.ZipFile(fixture) as archive:raw={n[len(prefix):]:archive.read(n) for n in archive.namelist() if n.startswith(prefix)}
        def lines(name):return raw[name].decode('utf-8').splitlines()
        conditions=lines('condition-ids.tsv');ids=lines('target-ids.tsv');names=lines('model/component-names.tsv');source_settings=dict(line.split('\t') for line in lines('settings.tsv')[1:]);assert len(conditions)==3 and len(ids)==20
        platforms=[('windows-java21',[str(JAVA21/'java.exe')],str),('windows-java25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java'],linux)]
        def command(argv,prefix):
            with prefix.with_suffix('.stdout').open('xb') as stdout,prefix.with_suffix('.stderr').open('xb') as stderr:result=subprocess.run(argv,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=600,check=False)
            item=dict(argv=argv,exit_code=result.returncode);write(prefix.with_suffix('.json'),item);assert result.returncode==0,str(prefix)+' failed; inspect complete logs';return item
        def stage(platform):
            host,java,path=platform;folder=out/host;folder.mkdir();request=folder/'original-learned-model-input';model=request/'model';model.mkdir(parents=True)
            def text(name,value):p=request/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value,encoding='utf-8',newline='\n')
            for source,destination in [('target-bhat.tsv','effects.tsv'),('target-shat.tsv','standard-errors.tsv')]:text(destination,'feature\t'+'\t'.join(conditions)+'\n'+'\n'.join(identity+'\t'+values for identity,values in zip(ids,lines(source)))+'\n')
            text('settings.tsv','key\tvalue\nschema_version\t2\noperation\tpredict\n');text('populations.tsv','feature\ttrain\tstrong\ttarget\n'+'\n'.join(identity+'\t0\t0\t1' for identity in ids)+'\n')
            text('model/representation.tsv','key\tvalue\nschema_version\t1\nrepresentation\tfully_expanded\n');text('model/conditions.tsv','\n'.join(conditions)+'\n');text('model/component-names.tsv','\n'.join(names)+'\n')
            text('model/settings.tsv','key\tvalue\nalpha\t'+source_settings['alpha']+'\nposterior_threshold\t'+source_settings['pi_threshold']+'\ncovariance_tolerance\t1e-12\nminimum_se\t2.220446049250313e-16\nnumeric_sensitivity\t1e-7\n')
            text('model/proportions.tsv','\n'.join(lines('model/proportions.tsv')[0].split('\t'))+'\n');(model/'residual.tsv').write_bytes(raw['model/residual.tsv'])
            for i in range(len(names)):(model/f'component-{i+1}.tsv').write_bytes(raw[f'model/component-{i}.tsv'])
            text('model/SHA256SUMS',''.join(pin(p)['sha256']+'  '+p.name+'\n' for p in sorted(model.iterdir())))
            base=[*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-jar',path(jar),'mashr-tools'];result=folder/'learned-prediction';produced=command([*base,'--input',path(request),'--out',path(result)],folder/'learned-command');verified=command([*base,'--verify',path(result)],folder/'learned-verify')
            checked=0;maximum=0
            for field in ['mean','sd','negative','zero','lfsr','covariance-column-major','weights','component-loglik','loglik']:
                expected=[[float(x) for x in line.split('\t')] for line in lines('posterior/'+field+'.tsv')];observed=[[float(x) for x in line.split('\t')] for line in (result/'execution/posterior'/f'{field}.tsv').read_text().splitlines()]
                if field=='loglik':expected=[[x] for x in expected[0]];observed=[[row[0]] for row in observed]
                assert len(expected)==len(observed)
                for a,b in zip(expected,observed):
                    assert len(a)==len(b)
                    for x,y in zip(a,b):assert math.isfinite(x) and math.isfinite(y) and abs(x-y)<=1e-9+1e-8*abs(x),(host,field,x,y);checked+=1;maximum=max(maximum,abs(x-y))
            assert checked==len(ids)*(25+2*len(names));print(host+': learned original model, '+str(checked)+' original target values passed',flush=True)
            return dict(name=host,commands=[produced,verified],original_target_values=checked,maximum_absolute_error=maximum)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:learned=list(pool.map(stage,platforms))
        tasks=[(producer,verifier,kind) for producer,_,_ in platforms for verifier in platforms if verifier[0]!=producer for kind in ['fit','prediction','learned-prediction']]
        cross_root=out/'cross';cross_root.mkdir()
        def cross(task):
            producer,(consumer,java,path),kind=task;folder=cross_root/(producer+'-to-'+consumer+'-'+kind);folder.mkdir();source=(out/producer if kind=='learned-prediction' else matrix/producer)/kind;copied=folder/'copied-bundle';shutil.copytree(source,copied);before=census(copied)
            checked=command([*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-jar',path(jar),'mashr-tools','--verify',path(copied)],folder/'verify');assert before==census(copied)
            print(producer+' -> '+consumer+' '+kind+': copied complete bundle verified unchanged',flush=True)
            return dict(producer=producer,verifier=consumer,kind=kind,command=checked,source_unchanged=True)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:crossed=list(pool.map(cross,tasks))
        assert len(crossed)==18 and all(pin(ROOT/n)==v for n,v in bound.items()) and pin(parent)==before_parent and pin(jar)==before_jar
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-cross-JVM-native-mash-public-bundle-verification',matrix=dict(path=parent.relative_to(ROOT).as_posix(),**before_parent),source_pins=bound,original_source_case=original,learned=learned,cross_producer_checks=crossed,original_R_rerun=False,upstream_preparation_integrated=False,whole_paper_reproduced=False,
                files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:
        write(out/'FAILURE.json',dict(status='FAIL2_MASH_PORTABLE',message=str(failure)));(out/'WARNINGS.md').write_text('# FAIL2: portable mash verification incomplete\n\n'+str(failure)+'\n\nRequired action: preserve this attempt, inspect every named command log, and resolve the source/platform discrepancy. Retry into a NEW directory. No partial portability claim is admitted.\n',encoding='utf-8');raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as error:print('FAIL2_MASH_PORTABLE: '+str(error),file=sys.stderr);sys.exit(2)
