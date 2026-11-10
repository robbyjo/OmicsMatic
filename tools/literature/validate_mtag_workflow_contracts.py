# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual packaged MTAG typed requests and full runtime admission; no MTAG fit or tool credit."""
import argparse
import copy
import json
import os
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE,linux


def baseline():
    return dict(schema_version=1,runtime_profile='../../workflow-runtime-profile-1/runtime.json',output_dir='NEW-unused-analysis-output',
        traits=[dict(name='trait'+str(t+1),input='../../original-workflow-probe-1/inputs/trait'+str(t+1)+'.tsv') for t in range(3)],
        columns=dict(snp='snpid',chr='chr',bp='bpos',a1='a1',a2='a2',z='z',n='n',freq='freq',p='p',info=None),
        filters=dict(maf_min=[.01]*3,n_min=[1]*3,n_max=None,info_min=None,homogeneous_kind='none',homogeneous_values=None,include=[],exclude=[],chromosomes=[],include_ambiguous=False),
        covariance=dict(sigma='ldsc',sigma_file=None,omega='gmm',omega_file=None,constraint='none',no_overlap=False,tolerance=1e-6,ld_reference='bundled_1kg_eur',ld_reference_dir=None),
        fdr=dict(mode='grid',intervals=2,grid_file=None,p_significant=5e-8,cores=1),standardized_effects=False,force_low_signal=False,median_z_cutoff=.1,
        limits=dict(rows_per_trait=50000,input_bytes=100000000,output_bytes=200000000,working_bytes=1000000000,wall_seconds=300,maximum_files=1000,maximum_fdr_grid_points=10000),
        no_allele_flipping=False,meta_format=False)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',type=Path,required=True);p.add_argument('--runtime-profile',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    state=dict(status='FAIL2_MTAG_WORKFLOW_CONTRACTS',analysis_executed=False,new_catalog_credit=False,full_adapter_available=False)
    (out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        build=a.build.resolve();receipt=json.loads((build/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_MTAG_WORKFLOW_DEVELOPMENT_BUILD_ONLY'
        jar=build/'mtag-workflow-development.jar';assert pin(jar)==receipt['artifact']
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';protected={str(canonical):pin(canonical),str(jar):pin(jar),str(a.runtime_profile.resolve()):pin(a.runtime_profile)}
        inputs=out/'inputs';inputs.mkdir();cases=[]
        def add(name,change=None,expected=0):
            recipe=baseline()
            if change:change(recipe)
            path=inputs/(name+'.json');save(path,recipe);cases.append(dict(case=name,input=path.name,expected=expected,pin=pin(path)))
        add('valid-three-trait')
        add('unknown-field',lambda r:r.update(shell='echo forbidden'),2)
        add('missing-explicit-choice',lambda r:r.pop('force_low_signal'),2)
        add('duplicate-trait-name',lambda r:r['traits'][1].update(name='trait1'),2)
        add('duplicate-trait-input',lambda r:r['traits'][1].update(input=r['traits'][0]['input']),2)
        add('one-trait',lambda r:r.update(traits=r['traits'][:1]),2)
        add('wrong-filter-length',lambda r:r['filters'].update(maf_min=[.01]),2)
        add('invalid-maf',lambda r:r['filters'].update(maf_min=[.01,-.1,.01]),2)
        add('ambiguous-provided-sigma',lambda r:r['covariance'].update(sigma='provided'),2)
        add('ignored-no-overlap-with-provided-sigma',lambda r:r['covariance'].update(sigma='provided',sigma_file='provided.txt',no_overlap=True),2)
        add('ignored-constraint-with-provided-omega',lambda r:r['covariance'].update(omega='provided',omega_file='provided.txt',constraint='perfect'),2)
        add('ambiguous-custom-grid',lambda r:r['fdr'].update(mode='custom'),2)
        add('invalid-significance',lambda r:r['fdr'].update(p_significant=0),2)
        add('fractional-capacity',lambda r:r['limits'].update(rows_per_trait=10.5),2)
        add('nonboolean-force',lambda r:r.update(force_low_signal='false'),2)
        add('unknown-info-column',lambda r:r['columns'].update(info='ImputationInfo'),2)
        add('missing-provided-ld-reference',lambda r:r['covariance'].update(ld_reference='provided'),2)
        add('duplicate-chromosome',lambda r:r['filters'].update(chromosomes=[1,1]),2)
        add('valid-provided-covariance',lambda r:r['covariance'].update(sigma='provided',sigma_file='sigma.txt',omega='provided',omega_file='omega.txt'))
        add('valid-original-perfect-constraint',lambda r:r['covariance'].update(constraint='perfect'))
        add('valid-original-equal-h2',lambda r:r['covariance'].update(constraint='equal_h2'))
        add('valid-numerical-omega',lambda r:r['covariance'].update(omega='numerical'))
        add('valid-original-fit-ss',lambda r:r['fdr'].update(mode='fit_ss'))
        add('valid-custom-grid',lambda r:r['fdr'].update(mode='custom',grid_file='grid.txt'))
        save(out/'declared-cases.json',cases)
        records=[]
        for platform in ['windows21','windows25','linux21']:
            prefix=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java'] if platform=='linux21' else [str(Path('C:/Program Files')/('Common Files/Oracle/Java/javapath' if platform=='windows25' else 'Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')/'java.exe')]
            cp=':'.join(map(linux,[jar,canonical])) if platform=='linux21' else ';'.join(map(str,[jar,canonical]))
            directory=out/platform;directory.mkdir()
            def run(name,mode,source,expected):
                target=directory/(name+'.json')
                command=prefix+['-cp',cp,'org.jlinalg.cli.MtagWorkflowReferenceProbe',mode,linux(source) if platform=='linux21' else str(source),linux(target) if platform=='linux21' else str(target)]
                r=subprocess.run(command,cwd=ROOT,env=dict(os.environ),stdin=subprocess.DEVNULL,capture_output=True,timeout=300)
                label=platform+'-'+name;(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
                assert r.returncode==expected,(label,r.returncode,r.stderr[-3000:])
                assert target.exists()==(expected==0)
                if expected:assert ('SKIP3_' if expected==3 else 'FAIL2_').encode() in r.stderr
                records.append(dict(platform=platform,case=name,expected=expected))
            for c in cases:run(c['case'],'request',inputs/c['input'],c['expected'])
            run('actual-runtime','runtime',a.runtime_profile.resolve(),0 if platform=='linux21' else 3)
            run('missing-runtime','runtime',out/'absent-runtime.json',3)
        for path,wanted in protected.items():assert pin(Path(path))==wanted,path
        state.update(status='PASS_TYPED_MTAG_REQUEST_AND_PRIVATE_RUNTIME_CONTRACTS_ONLY',request_cases=len(cases),platforms=3,
                     checked_protocols=len(records),records=records,protected_files=protected,canonical_executable_unchanged=True)
        print(json.dumps({k:state[k] for k in ['status','request_cases','platforms','checked_protocols']}))
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');print(state['status']+': '+repr(error))
    save(out/'COMPLETED.json',state)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual-artifact request/runtime protocol checks')
    raise SystemExit(main())
