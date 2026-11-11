# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Complete original PCA field comparisons, including source signs, uncomputed NaNs and randomized projections."""
import argparse
import json
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_snprelate_tools import rows,JAVA
from probe_king_runtime import DISTRO,linux
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--partition');p.add_argument('--pilot',action='store_true');a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_ORIGINAL_SNPRELATE_PCA_ADAPTER_VALIDATION',new_catalog_credit=False,whole_goal_complete=False);records=[]
    try:
        build=a.build.resolve();metadata=json.loads((build/'COMPLETED.json').read_bytes());assert metadata['status']=='PASS_ISOLATED_SNPRELATE_ADAPTER_BUILD_ONLY'
        jar=ROOT/metadata['artifact']['path'];assert pin(jar)=={key:metadata['artifact'][key] for key in ['bytes','sha256']}
        assert all(pin(ROOT/name)==value for name,value in metadata['source_pins'].items())
        producer=pin(Path(__file__));canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical);profile=BASE/'workflow-runtime-profile-1/runtime-profile.json';profile_pin=pin(profile)
        cases=[];protected={}
        for cohort in ['mixed','zerohet','wide','rank']:
            reference=BASE/('original-PCA-'+cohort+'-1');done=json.loads((reference/'COMPLETED.json').read_bytes());assert done['status']=='PASS_ORIGINAL_SNPRELATE146_PCA_REFERENCE_COLLECTION_ONLY'
            for name,value in done['complete_original_files'].items():assert pin(reference/name)==value;protected[str(reference/name)]=value
            for ext,value in done['input_pins'].items():assert pin(reference/'inputs'/('cohort.'+ext))==value;protected[str(reference/'inputs'/('cohort.'+ext))]=value
            protected[str(reference/'COMPLETED.json')]=pin(reference/'COMPLETED.json')
            for case in done['cases']:
                parts=case['case'].split('-');algorithm,bayesian,solver,count=parts[:4]
                options=dict(algorithm=algorithm,bayesian=bayesian=='TRUE',eigen_method=solver,eigen_count=int(count),aux_dim=6,
                    iterations=int(parts[4][4:]) if algorithm=='randomized' else 3,seed=20261110,need_genmat=algorithm=='exact')
                cases.append(dict(name=cohort+'-'+case['layout']+'-'+case['case'],case=case,reference=reference,
                    folder=reference/'original-results'/case['layout']/case['case'],options=options))
        assert len(cases)==80
        if a.pilot:
            assert not a.partition;cases=[cases[0],cases[8],cases[60],cases[68]]
        elif a.partition:
            part,total=map(int,a.partition.split('/'));assert 0<=part<total<=8;cases=cases[part::total]
        for item in cases:
            case=item['case'];name=item['name'];bundle=out/name;request=dict(schema_version=1,version='1.46.0',mode='pca',bfile=str(item['reference']/'inputs/cohort'),
                runtime_profile=str(profile),out=str(bundle),layout=case['layout'],autosomes_only=True,remove_monomorphic=True,maf='NaN',missing_rate=0.01,threads=1,options=item['options'],
                limits=dict(input_bytes=10000000,output_bytes=50000000,output_files=150,samples=100,variants=20000,rows=2000000,
                    pair_variant_operations=1000000000,working_bytes=1000000000,wall_seconds=180))
            request_file=out/(name+'.request.json');save(request_file,request)
            command=[str(JAVA),'-Xmx1g','-jar',str(jar),'--request',str(request_file)]
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=240)
            (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
            save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode,r.stderr[-4500:])
            done=json.loads((bundle/'COMPLETED.json').read_bytes());assert done['result_admitted'] and done['original_analysis_executed']
            fields=0
            for filename in ['original-selected-variants.tsv','original-eigenvalues.tsv','original-eigenvectors.tsv','original-PCA-summary.tsv','original-genotype-matrix.tsv']:
                source=item['folder']/filename;actual=bundle/'worker'/filename;assert source.exists()==actual.exists(),filename
                if source.exists():
                    first,second=rows(source),rows(actual);assert first==second,('Complete original PCA fields differ; no sign/NaN/numeric repair allowed',name,filename)
                    fields+=sum(len(r) for r in first)
            for host in ['windows','linux']:
                command=([str(JAVA),'-Xmx1g','-jar',str(jar),'--verify',str(bundle)] if host=='windows' else
                    ['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx1g','-jar',linux(jar),'--verify',linux(bundle)])
                replay=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
                (out/(name+'-'+host+'.stdout')).write_bytes(replay.stdout);(out/(name+'-'+host+'.stderr')).write_bytes(replay.stderr)
                assert replay.returncode==0,(name,host,replay.returncode,replay.stderr[-3500:])
            record=dict(case=name,expected=0,actual=r.returncode,reference=item['folder'].relative_to(ROOT).as_posix(),complete_source_fields=fields,portable_checks=2,
                original_eigenvector_cells=int(case['eigenvector_cells']),source_signs_and_nonfinite_placeholders_unchanged=True)
            records.append(record);print(json.dumps(record),flush=True)
        assert pin(jar)=={key:metadata['artifact'][key] for key in ['bytes','sha256']} and pin(profile)==profile_pin and pin(canonical)==canonical_pin
        assert pin(Path(__file__))==producer and all(pin(Path(name))==value for name,value in protected.items())
        assert all(pin(ROOT/name)==value for name,value in metadata['source_pins'].items())
        state.update(status='PASS_ORIGINAL_SNPRELATE_PCA_PILOT' if a.pilot else 'PASS_ORIGINAL_SNPRELATE_COMPLETE_PCA_REFERENCE_PARTITION',
            pilot=a.pilot,partition=a.partition,records=records,total_declared_cases=80,artifact=metadata['artifact'],source_pins=metadata['source_pins'],producer=producer,
            original_reference_files_unchanged=len(protected),canonical_executable_unchanged=canonical_pin)
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:4500],flush=True)
    return 0 if state['status'].startswith('PASS_ORIGINAL_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original PCA fields, signs or source-preservation checks')
    raise SystemExit(main())
