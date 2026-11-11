# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run all typed SVD profiles, independent original R controls, copied verification and failure gates."""
import argparse
import concurrent.futures
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,linux,pin
BASE=ROOT/'build/literature-review/signac-runtime'
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def seq(x):return x if isinstance(x,list) else [x]
def table(path):
    with path.open(encoding='utf8',newline='') as stream:return list(csv.reader(stream,delimiter='\t'))
def array(path):return [[float(x) for x in row[1:]] for row in table(path)[1:]]
def same(a,b,tol=3e-12):assert math.isfinite(a) and math.isfinite(b) and abs(a-b)<=tol*max(1,abs(a),abs(b)),(a,b)
def reseal(root):
    paths=sorted(p for p in root.rglob('*') if p.is_file() and p.name!='artifact-checksums.tsv')
    (root/'artifact-checksums.tsv').write_text('sha256\tpath\n'+''.join(pin(p)['sha256']+'\t'+p.relative_to(root).as_posix()+'\n' for p in paths),encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--reuse-fits-from',type=Path);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='typed-workflow-validation-in-progress'))
    try:
        build=json.loads((BASE/'lsi-build-5/BUILD.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        sources={n:pin(ROOT/n) for n in ['tools/literature/validate_signac_lsi_workflow.py','tools/literature/collect_signac_lsi_workflow.R']};save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        inputs=out/'data';inputs.mkdir()
        for name in ['counts.mtx','features.tsv','cells.tsv']:shutil.copyfile(BASE/'lsi-probe-1'/name,inputs/name)
        java=[str(JAVA21/'java.exe')];mainclass='org.jlinalg.cli.SignacLsiToolsCli';prefix=[*java,'-Xmx512m','-cp',str(jar),mainclass]
        common=['--counts',str(inputs/'counts.mtx'),'--features',str(inputs/'features.tsv'),'--cells',str(inputs/'cells.tsv'),'--rscript','C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--r-library',str(BASE/'reference-setup-1/R-lib'),'--r-base-library',str(ROOT/'build/omics-sctransform-r')]
        def command(name,argv,expected=0,timeout=120):
            argv=list(map(str,argv));result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout,check=False)
            (out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=result.returncode,expected=expected));assert result.returncode==expected,(name,result.returncode);return result
        profiles=['1.17.1-package-rspectra','1.6.0-source-irlba','1.7.0-source-irlba','1.9.0-source-irlba','1.14.0-source-irlba'];requests=[]
        for profile in profiles:
            for mode in ['scaled','clipped','unscaled-clip']:
                requests.append(dict(id=f'{profile}-{mode}',profile=profile,components=5,scale_embeddings=mode!='unscaled-clip',clip=1.2 if mode!='scaled' else None,
                                     cutoff_kind='count' if mode=='clipped' else 'percentile',cutoff=100 if mode=='clipped' else 5,tfidf_method=1,tfidf_scale=10000,tolerance=1e-5,seed=4901,java25=False))
        for method in [2,3,4]:requests.append(dict(requests[0],id=f'current-tfidf-{method}',tfidf_method=method))
        requests.append(dict(requests[0],id='current-clamped-rank',components=50))
        requests.append(dict(requests[12],id='historical-clamped-rank',components=50))
        requests.append(dict(requests[0],id='java25-current',java25=True));requests.append(dict(requests[12],id='java25-historical',java25=True))
        for request in requests:request.update(counts=str(inputs/'counts.mtx'),features=str(inputs/'features.tsv'),cells=str(inputs/'cells.tsv'))
        save(out/'requests.json',requests)
        prior=args.reuse_fits_from.resolve() if args.reuse_fits_from else None
        if prior:
            assert prior.is_relative_to(BASE.resolve()) and ((prior/'FAILURE.json').exists() or (prior/'COMPLETED.json').exists())
            previous_requests=json.loads((prior/'requests.json').read_bytes());assert len(previous_requests)==len(requests)
            for old,new in zip(previous_requests,requests,strict=True):assert all(old[k]==v for k,v in new.items() if k not in ['counts','features','cells'])
        def fit(request):
            runprefix=prefix if not request['java25'] else ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',*prefix[1:]]
            argv=[*runprefix,*common,'--out',str(out/request['id']),'--profile',request['profile'],'--components',str(request['components']),'--scale-embeddings',str(request['scale_embeddings']).lower(),
                  '--tolerance',str(request['tolerance']),'--seed',str(request['seed']),'--tfidf-method',str(request['tfidf_method']),'--tfidf-scale',str(request['tfidf_scale']),
                  '--cutoff-'+request['cutoff_kind'],str(request['cutoff'])]
            if request['clip'] is not None:argv+=['--clip',str(request['clip'])]
            if prior:
                earlier=json.loads((prior/(request['id']+'-fit.process.json')).read_bytes());assert earlier['exit_code']==0 and str(jar) in earlier['command']
                for name in ['counts.mtx','features.tsv','cells.tsv']:assert pin(prior/request['id']/'input'/name)==pin(inputs/name)
                shutil.copytree(prior/request['id'],out/request['id'])
                command(request['id']+'-reverify-retained-fit', [*prefix,'--verify',str(out/request['id'])])
                save(out/(request['id']+'-reuse.json'),dict(status='reused-byte-identical-candidate-successful-original-fit-not-fresh-execution',original_process=earlier,source_directory=str(prior/request['id'])))
            else:command(request['id']+'-fit',argv)
            manifest=json.loads((out/request['id']/'manifest.json').read_bytes());assert manifest['analysis_status'] in ['completed','completed-with-warnings'] and manifest['process_exit_code']==0
            print(json.dumps(dict(completed_fit=request['id'])),flush=True);return dict(id=request['id'],diagnostics=manifest['diagnostics'])
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:fits=list(pool.map(fit,requests))
        env=dict(os.environ)
        for key in list(env):
            if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS']:env.pop(key,None)
        env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        rcmd=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/'tools/literature/collect_signac_lsi_workflow.R'),str(out/'requests.json'),str(out/'original.json'),str(BASE/'source-intake-1')]
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:r=subprocess.run(rcmd,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300,check=False)
        save(out/'original.process.json',dict(command=rcmd,exit_code=r.returncode));assert r.returncode==0
        original=json.loads((out/'original.json').read_bytes());assert len(original['results'])==len(requests)==22
        comparisons=[]
        for reference in original['results']:
            run=out/reference['id'];features=(run/'exchange/features.tsv').read_text().splitlines();cells=(run/'exchange/cells.tsv').read_text().splitlines();assert features==seq(reference['selected']) and cells==seq(reference['cells'])
            mm=(run/'exchange/normalized.mtx').read_text().splitlines();triplets=[line.split() for line in mm if line and not line.startswith('%')][1:];p=seq(reference['p']);i=seq(reference['i']);x=seq(reference['x']);expected=[]
            for cell in range(len(cells)):
                for k in range(p[cell],p[cell+1]):expected.append((i[k]+1,cell+1,x[k]))
            assert len(expected)==len(triplets)
            for row,(feature,cell,value) in zip(triplets,expected,strict=True):assert [int(row[0]),int(row[1])]==[feature,cell];same(float(row[2]),value,3e-13)
            u=array(run/'worker/raw-u.tsv');v=array(run/'worker/loadings.tsv');embedding=array(run/'worker/embeddings.tsv');singular=table(run/'worker/singular-values.tsv')[1:]
            for k,d in enumerate(seq(reference['singular_values'])):
                same(float(singular[k][1]),d,3e-7);same(float(singular[k][2]),seq(reference['stdev'])[k],3e-7)
                dot=sum(row[k]*ref[k] for row,ref in zip(u,reference['u'],strict=True));sign=1 if dot>=0 else -1;assert abs(abs(dot)-1)<1e-7
                for actual,expected_matrix in [(u,reference['u']),(v,reference['v']),(embedding,reference['embeddings'])]:
                    for row,ref in zip(actual,expected_matrix,strict=True):same(row[k],sign*ref[k],3e-6)
            comparisons.append(dict(id=reference['id'],original_normalization_and_feature_selection=True,original_solver_sign_aligned=True))
        platforms=[('windows-java21',[str(JAVA21/'java.exe')],str),('windows-java25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        copies=out/'copied';copies.mkdir();copy= copies/'relocated-analysis';shutil.copytree(out/requests[0]['id'],copy)
        verifications=[]
        def check(spec):
            platform,java,convert,request=spec;target=out/request['id'];name=platform+'-verify-'+request['id'];command(name,[*java,'-cp',convert(jar),mainclass,'--verify',convert(target)]);return dict(platform=platform,id=request['id'])
        specs=[(*platform,request) for platform in platforms for request in requests]
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:verifications=list(pool.map(check,specs))
        for platform,java,convert in platforms:command(platform+'-verify-relocated', [*java,'-cp',convert(jar),mainclass,'--verify',convert(copy)])
        failures=[]
        for name,replace in [('missing-rscript',{'--rscript':str(out/'no-rscript.exe')}),('missing-library',{'--r-library':str(out/'no-library')})]:
            argv=[*prefix,*common,'--out',str(out/name),'--profile',profiles[0],'--components','5']
            for key,value in replace.items():argv[argv.index(key)+1]=value
            command(name,argv,expected=3);assert json.loads((out/name/'manifest.json').read_bytes())['analysis_status']=='skipped-dependency-unavailable'
            assert not list((out/name).glob('result-*')) and 'Required action:' in (out/name/'WARNINGS.md').read_text(encoding='utf8');failures.append(dict(name=name,code=3))
        command('owned-timeout', [*prefix,*common,'--out',str(out/'owned-timeout'),'--profile',profiles[0],'--components','5','--timeout','1'],expected=2)
        assert not list((out/'owned-timeout').glob('result-*'));failures.append(dict(name='owned-timeout',code=2))
        before={p.relative_to(copy).as_posix():pin(p) for p in copy.rglob('*') if p.is_file()};command('existing-destination',[*prefix,*common,'--out',str(copy),'--profile',profiles[0],'--components','5'],expected=2);assert before=={p.relative_to(copy).as_posix():pin(p) for p in copy.rglob('*') if p.is_file()};failures.append(dict(name='existing-destination-preserved',code=2))
        corruptions=[]
        for kind in ['raw-u','loadings','singular-value','metadata','normalized','solver','diagnostics','status','source']:
            altered=copies/kind;shutil.copytree(copy,altered)
            if kind in ['raw-u','loadings']:
                name='raw-u.tsv' if kind=='raw-u' else 'loadings.tsv';path=altered/'worker'/name;rows=table(path);rows[1][1]=str(float(rows[1][1])+0.05);path.write_text('\n'.join('\t'.join(r) for r in rows)+'\n',encoding='utf8',newline='\n')
                if kind=='loadings':shutil.copyfile(path,altered/'result-loadings.tsv')
            elif kind=='singular-value':
                path=altered/'worker/singular-values.tsv';rows=table(path);rows[1][1]=str(float(rows[1][1])*1.1);rows[1][2]=str(float(rows[1][2])*1.1);path.write_text('\n'.join('\t'.join(r) for r in rows)+'\n',encoding='utf8',newline='\n');shutil.copyfile(path,altered/'result-singular-values.tsv')
            elif kind=='metadata':
                path=altered/'feature-metadata.tsv';rows=table(path);rows[1][2]='-1';path.write_text('\n'.join('\t'.join(r) for r in rows)+'\n',encoding='utf8',newline='\n')
            elif kind=='normalized':
                path=altered/'exchange/normalized.mtx';lines=path.read_text().splitlines();fields=lines[3].split();fields[2]=str(float(fields[2])+1);lines[3]=' '.join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            elif kind=='solver':
                path=altered/'worker/effective-settings.tsv';path.write_text(path.read_text().replace('RSpectra::svds','not-the-solver'),encoding='utf8',newline='\n')
            elif kind=='diagnostics':
                path=altered/'manifest.json';data=json.loads(path.read_bytes());data['diagnostics']['maximum_relative_left_residual']=0.9;save(path,data)
            elif kind=='status':save(altered/'analysis-status.json',dict(status='failed'))
            elif kind=='source':
                path=altered/'worker/source/signac-lsi/RunSVD-1.17.1.R';path.write_bytes(path.read_bytes()+b'\n# changed source\n')
            reseal(altered)
            for platform,java,convert in platforms:command(platform+'-reject-rehashed-'+kind,[*java,'-cp',convert(jar),mainclass,'--verify',convert(altered)],expected=2)
            corruptions.append(dict(kind=kind,rehashed=True,rejected_platforms=3))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        save(out/'COMPLETED.json',dict(status='passed-typed-Signac-LSI-upstream-workflows-and-independent-original-controls',source_pins=sources,artifact=build['artifact'],actual_fits=fits,independent_original_comparisons=comparisons,
                                     copied_verifications=len(verifications)+3,failure_gates=failures,rehashed_scientific_rejections=corruptions,historical_packages_installed=False,root_dispatch_available=False,catalog_implementation_credit=False,
                                     runtime_admission_scope='exact package/source checks and numeric controls; complete DLL/host closure is not certified',
                                     fresh_actual_fits=0 if prior else len(fits),retained_actual_fits=len(fits) if prior else 0,reused_fits_from=str(prior) if prior else None))
        (out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-typed-Signac-LSI-upstream-workflows-and-independent-original-controls',fits=22,copied_verifications=len(verifications)+3,rehashed_rejections=len(corruptions)*3)))
    except BaseException as e:save(out/'FAILURE.json',dict(status='FAILED_TYPED_LSI_WORKFLOW_VALIDATION_NO_CREDIT',error=str(e)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable upstream workflow checks')
    main()
