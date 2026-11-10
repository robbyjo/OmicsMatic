# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise typed original MTAG options, full audits and deliberate scientific refusals on declared synthetic fixtures."""
import argparse
import bz2
import concurrent.futures
import copy
import csv
import gzip
import io
import json
import math
import os
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE,linux
from validate_mtag_workflow_contracts import baseline


def tsv_bytes(rows,info=False):
    frequency='frequency' if 'frequency' in rows[0] else 'freq'
    stream=io.StringIO(newline='');w=csv.DictWriter(stream,fieldnames=['snpid','chr','bpos','a1','a2','z','n',frequency,'p']+(['info'] if info else []),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(rows);return stream.getvalue().encode()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',type=Path,required=True);p.add_argument('--runtime-profile',type=Path,required=True);p.add_argument('--workers',type=int,default=3);a=p.parse_args()
    out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists() and 1<=a.workers<=3;out.mkdir(parents=True)
    state=dict(status='FAIL2_TYPED_MTAG_WORKFLOW_MATRIX',new_catalog_credit=False,whole_paper_reproduced=False,synthetic_only=True)
    (out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        build=a.build.resolve();receipt=json.loads((build/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_MTAG_WORKFLOW_DEVELOPMENT_BUILD_ONLY'
        jar=build/'mtag-workflow-development.jar';assert pin(jar)==receipt['artifact']
        for name,wanted in receipt['source_pins'].items():assert pin(ROOT/name)==wanted,name
        profile=a.runtime_profile.resolve();runtime=json.loads(profile.read_bytes());canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
        protected={str(jar):pin(jar),str(profile):pin(profile),str(canonical):pin(canonical)}
        fixtures=out/'fixtures';fixtures.mkdir();base=[]
        for t in range(3):
            source=BASE/'original-workflow-probe-1/inputs'/('trait%d.tsv'%(t+1))
            with source.open(encoding='utf8',newline='') as stream:raw=list(csv.DictReader(stream,delimiter='\t'))[:1000]
            for r in raw:
                z=1.8*float(r['z']);r['z']=repr(z);r['p']=repr(math.erfc(abs(z)/math.sqrt(2)))
            base.append(raw)
        matrices=BASE/'original-workflow-probe-1/original'
        for source,name in [('mtag_sigma_hat.txt','sigma.txt'),('mtag_omega_hat.txt','omega.txt'),('mtag_prob_grid.txt','grid.txt')]:
            (fixtures/name).write_bytes((matrices/source).read_bytes())
        save(fixtures/'GENERATION.json',dict(status='PREDECLARED_SYNTHETIC_OPTION_CONTROLS_NOT_BIOLOGICAL_DATA',
            base='First1000 rows of the earlier synthetic20k fixture, Z multiplied by1.8 and input P recomputed with standard-library erfc before any test execution.',
            force_low_signal=True,reason='Explicit controlled small-fixture source option, never an automatic response to a failed source run.',
            scalar_covariances='Exact saved original20k matrices; provided modes intentionally test declared external matrices.',independent_biological_datasets=0))
        cases=[]
        def add(name,change=None,rows=None,compression=None,expected=0,retained=1000):
            home=out/name;home.mkdir();data=copy.deepcopy(base if rows is None else rows);recipe=baseline()
            recipe['runtime_profile']=linux(profile);recipe['output_dir']='analysis';recipe['force_low_signal']=True
            recipe['fdr']['mode']='none';recipe['limits']['wall_seconds']=1800
            recipe['covariance'].update(sigma='provided',sigma_file=linux(fixtures/'sigma.txt'),omega='provided',omega_file=linux(fixtures/'omega.txt'))
            if change:change(recipe,data)
            trait_files=[]
            for t,values in enumerate(data):
                info='info' in values[0];raw=tsv_bytes(values,info);suffix='.tsv'+('.gz' if compression=='gzip' else '.bz2' if compression=='bzip2' else '')
                path=home/('source-trait-%d'%(t+1)+suffix)
                path.write_bytes(gzip.compress(raw,mtime=0) if compression=='gzip' else bz2.compress(raw) if compression=='bzip2' else raw)
                trait_files.append(dict(name='trait'+str(t+1),input=linux(path)))
            recipe['traits']=trait_files
            save(home/'request.json',recipe)
            cases.append(dict(case=name,request=home/'request.json',expected=expected,retained=retained,input_pins={p.name:pin(p) for p in home.iterdir() if p.is_file()},positive_scientific_control=expected==0))
        add('provided-covariances')
        add('standardized-effects',lambda r,d:r.update(standardized_effects=True))
        add('gmm-omega',lambda r,d:r['covariance'].update(omega='gmm',omega_file=None))
        add('numerical-omega',lambda r,d:r['covariance'].update(omega='numerical',omega_file=None))
        add('perfect-genetic-correlation',lambda r,d:r['covariance'].update(omega='gmm',omega_file=None,constraint='perfect'))
        add('equal-h2-meta',lambda r,d:r['covariance'].update(omega='gmm',omega_file=None,constraint='equal_h2'))
        add('gzip-summaries',compression='gzip')
        add('bzip2-summaries',compression='bzip2')
        def quality(r,data,keep=False):
            r['columns']['info']='info';r['filters'].update(info_min=[.8]*3,n_min=[1000]*3,include_ambiguous=keep)
            for group in data:
                for row in group:row['info']='.99'
                group[6]['a2']='T'
            data[0][0]['freq']='.01';data[1][1]['info']='.2';data[2][2]['p']='0';data[0][3]['n']='50';data[1][4]['z']='NA'
            duplicate=copy.deepcopy(data[2][5]);duplicate['z']='9';duplicate['p']=repr(math.erfc(9/math.sqrt(2)));data[2].append(duplicate)
            row=data[1][7];row['a1'],row['a2']=row['a2'],row['a1'];row['z']=repr(-float(row['z']));row['freq']=repr(1-float(row['freq']))
            data[1][8]['a1'],data[1][8]['a2']='T','G'
        # Attempt1 established that upstream ignores the default freq header
        # during MAF munging. The unchanged boundary fixtures must be refused;
        # separate explicitly renamed fixtures test active source filtering.
        add('quality-and-source-allele-operations',quality,expected=2,retained=None)
        add('explicit-ambiguous-inclusion',lambda r,d:quality(r,d,True),expected=2,retained=None)
        def active_maf(r,data,keep=False):
            quality(r,data,keep);r['columns']['freq']='frequency'
            for group in data:
                for row in group:row['frequency']=row.pop('freq')
        add('explicit-frequency-header-quality',active_maf,retained=993)
        add('explicit-frequency-header-ambiguous',lambda r,d:active_maf(r,d,True),retained=994)
        def homogeneous(r,data,kind):
            r['filters'].update(homogeneous_kind=kind,homogeneous_values=[.05]*3 if kind=='fraction' else [1000]*3)
            for i in range(0,1000,50):data[0][i]['n']='10000'
        add('homogeneous-n-fraction',lambda r,d:homogeneous(r,d,'fraction'),retained=980)
        add('homogeneous-n-distance',lambda r,d:homogeneous(r,d,'distance'),retained=980)
        def selection(r,data):
            for name,indices in [('include1',range(900)),('include2',range(850,950)),('exclude',[10,20])]:
                path=fixtures/(name+'.csv');path.write_text('SNP\n'+'\n'.join(base[0][i]['snpid'] for i in indices)+'\n',encoding='utf8')
            r['filters'].update(include=[linux(fixtures/'include1.csv'),linux(fixtures/'include2.csv')],exclude=[linux(fixtures/'exclude.csv')],chromosomes=[1])
            for values in data:
                for i in range(900,920):values[i]['chr']='2'
        add('selection-and-chromosomes',selection,retained=928)
        add('custom-fdr-grid',lambda r,d:r['fdr'].update(mode='custom',grid_file=linux(fixtures/'grid.txt')),expected=3,retained=None)
        add('fdr-spike-slab',lambda r,d:r['fdr'].update(mode='fit_ss'))
        def full_default_grid(r,d):
            r['fdr'].update(mode='grid',intervals=10);r['limits']['maximum_fdr_grid_points']=20000
        add('default-ten-interval-fdr',full_default_grid)
        add('fdr-two-cores',lambda r,d:r['fdr'].update(mode='grid',cores=2))
        # Reference-directory and no-overlap paths use the complete already
        # declared20k fixture, avoiding a new univariate-LDSC small-sample claim.
        for name,no_overlap in [('explicit-bundled-reference-copy',False),('ldsc-no-overlap',True)]:
            home=out/name;home.mkdir();recipe=baseline();recipe['runtime_profile']=linux(profile);recipe['output_dir']='analysis';recipe['fdr']['mode']='none';recipe['limits']['wall_seconds']=1800
            recipe['covariance']['no_overlap']=no_overlap
            if not no_overlap:recipe['covariance'].update(ld_reference='provided',ld_reference_dir=runtime['source']+'/ld_ref_panel/eur_w_ld_chr')
            for t,trait in enumerate(recipe['traits']):trait['input']=linux(BASE/'original-workflow-probe-1/inputs'/('trait%d.tsv'%(t+1)))
            save(home/'request.json',recipe);cases.append(dict(case=name,request=home/'request.json',expected=0,retained=20000,input_pins={'request.json':pin(home/'request.json')},positive_scientific_control=True))
        def covariance_adjustment(r,data):
            path=fixtures/'indefinite-sigma.txt';path.write_text('1 .9 .9\n.9 1 -.9\n.9 -.9 1\n',encoding='ascii');r['covariance']['sigma_file']=linux(path)
        add('original-covariance-adjustment',covariance_adjustment)
        def underflow(r,data):
            del data[2];r['filters']['maf_min']=[.01]*2;r['filters']['n_min']=[1]*2
            for values in data:
                for i,row in enumerate(values):row['z']=repr(26.9 if i%2 else -26.9);row['p']=repr(math.erfc(26.9/math.sqrt(2)))
            for name,value in [('sigma2.txt','1 0\n0 1\n'),('omega2.txt','1e-5 1e-5\n1e-5 1e-5\n')]:
                (fixtures/name).write_text(value,encoding='ascii')
            r['covariance'].update(sigma_file=linux(fixtures/'sigma2.txt'),omega_file=linux(fixtures/'omega2.txt'))
        add('source-zero-tail-underflow',underflow)
        add('native-coordinate-refusal',lambda r,d:d[1][0].update(bpos=str(int(d[1][0]['bpos'])+1)),expected=2,retained=None)
        def invalid_omega(r,data):
            path=fixtures/'invalid-omega.txt';path.write_text('1e-10 2e-10 2e-10\n2e-10 1e-10 2e-10\n2e-10 2e-10 1e-10\n',encoding='ascii');r['covariance']['omega_file']=linux(path)
        add('native-invalid-provided-covariance-refusal',invalid_omega,expected=2,retained=None)
        save(out/'declared-cases.json',[dict(c,request=c['request'].relative_to(out).as_posix()) for c in cases])
        fixture_pins={str(p):pin(p) for p in fixtures.iterdir() if p.is_file()}
        def execute(c):
            home=out/c['case'];command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx2g','-cp',linux(jar)+':'+linux(canonical),'org.jlinalg.cli.MtagToolsCli','--request',linux(c['request'])]
            save(home/'command.json',dict(command=command))
            with (home/'stdout').open('xb') as stdout,(home/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=dict(os.environ),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=2400)
            save(home/'process.json',dict(exit_code=r.returncode))
            result=dict(case=c['case'],exit_code=r.returncode,expected=c['expected'],retained=c['retained'],passed=False)
            bundle=home/'analysis'
            if r.returncode!=c['expected']:result['cause']='Actual typed outcome differs; inspect retained source and native diagnostics';return result
            session=json.loads((bundle/'session.json').read_bytes());result['source_executed']=session['original_analysis_executed']
            if c['expected']==0:
                assert (bundle/'COMPLETED.json').is_file() and session['result_admitted']
                audit=json.loads((bundle/'scientific-audit.json').read_bytes());assert audit['variants']==c['retained']
                if c['case']=='source-zero-tail-underflow':assert audit['original_p_underflow_rows']==2000
                if c['case']=='explicit-frequency-header-quality':assert audit['original_trait_allele_flips']>=1
                verify=command[:-2]+['--verify',linux(bundle)]
                with (home/'verify.stdout').open('xb') as stdout,(home/'verify.stderr').open('xb') as stderr:v=subprocess.run(verify,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
                save(home/'verify.process.json',dict(command=verify,exit_code=v.returncode));assert v.returncode==0
                result['audit']=audit;result['saved_verify_passed']=True
            elif c['expected']==3:
                assert not session['result_admitted'] and not session['original_analysis_executed'] and not (bundle/'COMPLETED.json').exists()
                assert 'custom maxFDR grids' in session['cause']
            else:
                assert not session['result_admitted'] and not (bundle/'COMPLETED.json').exists()
                source=json.loads((bundle/'original-process.json').read_bytes());assert source['exit_code']==0
                assert (bundle/'unadmitted-original/mtag_trait_1.txt').is_file()
                required='coordinates disagree' if c['case']=='native-coordinate-refusal' else 'declared strict MAF cutoff' if c['case'] in ('quality-and-source-allele-operations','explicit-ambiguous-inclusion') else 'not positive semidefinite'
                assert required in session['cause'],session
            result['passed']=True;return result
        results=[]
        with concurrent.futures.ThreadPoolExecutor(max_workers=a.workers) as executor:
            futures={executor.submit(execute,c):c['case'] for c in cases}
            for f in concurrent.futures.as_completed(futures):
                try:r=f.result()
                except Exception as error:r=dict(case=futures[f],passed=False,cause=repr(error),traceback=traceback.format_exc())
                results.append(r);save(out/'progress.json',dict(completed=len(results),declared=len(cases),results=results));print(r['case'],r.get('exit_code'),r['passed'],flush=True)
        assert all(r['passed'] for r in results),[(r['case'],r.get('cause')) for r in results if not r['passed']]
        for name,wanted in {**protected,**fixture_pins}.items():assert pin(Path(name))==wanted,name
        for c in cases:
            for name,wanted in c['input_pins'].items():assert pin(out/c['case']/name)==wanted
        state.update(status='PASS_TYPED_ORIGINAL_MTAG_OPTION_MATRIX_AND_NATIVE_REFUSALS',cases=len(cases),results=results,source_rows_only_synthetic=True,
                     original_covariance_refits_for_verification=False,canonical_executable_unchanged=True)
        print(state['status'],len(cases))
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');print(state['status']+': '+repr(error),flush=True)
    save(out/'COMPLETED.json',state)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original-source/native option/refusal checks')
    raise SystemExit(main())
