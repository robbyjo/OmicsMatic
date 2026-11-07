# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual main routes against frozen original expectations on three JVMs."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import zipfile
from setup_deseq2_reference import ROOT,pin,save
from validate_deseq2_inference import JAVA21,JAVA25,linux,compare_tables

BASE=ROOT/'build/literature-review/deseq2-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--prior-attempt',type=Path);a=p.parse_args();out=a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    jar=BASE/'public-tools-development/omicsmatic-0.3.7-deseq2-tools-development.jar'
    parent=BASE/'inference-owned-artifact-1/omicsmatic-0.3.7-deseq2-inference-development.jar'
    artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    with zipfile.ZipFile(jar) as z,zipfile.ZipFile(parent) as q:
        assert set(z.namelist())==set(q.namelist())
        changed=[n for n in z.namelist() if z.read(n)!=q.read(n)]
        assert changed==['org/jlinalg/cli/JLinAlgCli.class']
    for folder in ['deseq2-inference-development','deseq2-normalization-development']:
        proof=json.loads((ROOT/'docs/literature'/folder/'VALIDATION.json').read_bytes())
        assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
    example=ROOT/'examples/deseq2-tools';sources=[p for p in example.iterdir() if p.is_file()]+[Path(__file__),ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java',ROOT/'tools/literature/deseq2-tools-development.init.gradle',ROOT/'tools/literature/prepare_deseq2_main_examples.py']
    bound={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
    def run(label,command,expected=0):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
            result=subprocess.run(list(map(str,command)),cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
        save(out/(label+'.process.json'),dict(command=list(map(str,command)),exit_code=result.returncode,expected_exit_code=expected))
        assert result.returncode==expected,label+' failed; read retained logs'
    def invoke(platform,route,request):
        if platform=='linux-java21':return ['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx512m','-jar',linux(jar),route,'--request',linux(request)]
        return [JAVA21 if platform=='windows-java21' else JAVA25,'-Xmx512m','-jar',jar,route,'--request',request]
    comparisons={};analyses=[]
    try:
        for platform in ['windows-java21','windows-java25','linux-java21']:
            for route in ['deseq2-tools','deseq2-normalize']:
                argv=invoke(platform,route,example/'paired.json')[:-2]+['--help'];run(platform+'-'+route+'-help',argv)
        for name in ['paired','shrinkage','lrt']:
            request=example/(name+'.json');payload=json.loads(request.read_bytes());target=(example/payload['out']).resolve()
            argv=invoke('windows-java21','deseq2-tools',request)
            if target.exists():
                assert a.prior_attempt and a.prior_attempt.resolve().is_relative_to(BASE)
                prior=a.prior_attempt.resolve();process=json.loads((prior/(name+'.process.json')).read_bytes())
                assert process['exit_code']==0 and process['command']==list(map(str,argv)) and (prior/'FAILURE.json').exists()
                save(out/(name+'-retained-success.json'),dict(original_process=process,original_process_pin=pin(prior/(name+'.process.json')),capture_files={p.relative_to(target).as_posix():pin(p) for p in target.rglob('*') if p.is_file()}))
            else:run(name,argv)
            assert json.loads((target/'analysis-status.json').read_bytes())['results_admitted']
            identities=json.loads((target/'prepared-inputs.json').read_bytes());mapping={**{'g'+str(i+1):v for i,v in enumerate(identities['genes'])},**{'s'+str(i+1):v for i,v in enumerate(identities['samples'])}}
            ref=BASE/'inference-reference-3/cases'/('lrt' if name=='lrt' else 'paired')
            pairs=[('original-prepared.tsv','counts-prepared.tsv'),('original-normalized.tsv','normalized-counts.tsv'),('original-factors.tsv','size-factors.tsv')]
            for r in payload['results']:
                for kind in ['base','shrunken'] if 'shrink' in r else ['base']:
                    pairs.append(('original-'+r['id']+'-'+kind+'.tsv','results/'+r['id']+('-shrunken' if kind=='shrunken' else '')+'.tsv'))
            pairs.extend(('original-transform-'+t['id']+'.tsv','transforms/'+t['id']+'.tsv') for t in payload.get('transforms',[]))
            comparisons[name]={s:compare_tables(ref/s,target/d,mapping) for s,d in pairs}
            for platform in ['windows-java21','windows-java25','linux-java21']:run(name+'-verify-'+platform,invoke(platform,'deseq2-tools',example/('verify-'+name+'.json')))
            before={p.relative_to(target).as_posix():pin(p) for p in target.rglob('*') if p.is_file()};run(name+'-existing-output',argv,2);assert before=={p.relative_to(target).as_posix():pin(p) for p in target.rglob('*') if p.is_file()};analyses.append(dict(name=name,path=target.relative_to(ROOT).as_posix(),files=before))
        fresh=json.loads((example/'paired.json').read_bytes())
        for key in ['counts','samples','keep_genes']:fresh[key]=str((example/fresh[key]).resolve())
        fresh['r_libraries']=[str(ROOT/'build/deseq2-user-runtime/R-lib')];fresh['out']=str(out/'fresh-win25-analysis');save(out/'fresh-win25.json',fresh)
        run('fresh-win25',invoke('windows-java25','deseq2-tools',out/'fresh-win25.json'))
        assert (out/'fresh-win25-analysis/results/paired.tsv').read_bytes()==(ROOT/'build/deseq2-tools-example-paired/results/paired.tsv').read_bytes()
        save(out/'verify-fresh.json',dict(schema_version=1,mode='VERIFY',analysis='fresh-win25-analysis'))
        for platform in ['windows-java21','windows-java25','linux-java21']:run('fresh-verify-'+platform,invoke(platform,'deseq2-tools',out/'verify-fresh.json'))
        for name in ['normalize','frozen-means']:
            payload=json.loads((example/(name+'.json')).read_bytes())
            for platform in ['windows-java21','windows-java25','linux-java21']:
                req=dict(payload)
                for key in ['counts','geometric_means','controls']:
                    if key in req:req[key]=os.path.relpath((example/req[key]).resolve(),out).replace('\\','/')
                req['out']=name+'-'+platform;save(out/(name+'-'+platform+'.json'),req)
                run(name+'-'+platform,invoke(platform,'deseq2-normalize',out/(name+'-'+platform+'.json')))
                original=ROOT/('build/deseq2-normalization-example' if name=='normalize' else 'build/deseq2-frozen-means-example')
                # The frozen original-R comparison already establishes native numerics.
                for table in ['size-factors.tsv','normalized-counts.tsv']:assert (out/req['out']/table).read_bytes()==(original/table).read_bytes(),str(original/table)
        missing=dict(fresh,rscript=str(out/'absent-Rscript.exe'),out=str(out/'missing-runtime'));save(out/'missing.json',missing);run('missing-runtime',invoke('windows-java21','deseq2-tools',out/'missing.json'),3)
        assert not json.loads((out/'missing-runtime/analysis-status.json').read_bytes())['results_admitted']
        save(out/'verify-missing.json',dict(schema_version=1,mode='VERIFY',analysis='missing-runtime'));run('verify-missing-refused',invoke('windows-java21','deseq2-tools',out/'verify-missing.json'),2)
        assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/n)==v for n,v in bound.items())
        save(out/'COMPLETED.json',dict(status='passed-DESeq2-actual-main-original-and-native-workflows',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),parent=pin(parent),only_changed_packaged_entry=changed,source_pins=bound,canonical_executable_preserved=canonical,original_R_fits=4,native_main_analyses=6,actual_main_capture_verifications=12,help_protocols=6,existing_output_refusals=3,dependency_skips=1,failed_capture_rejections=1,comparisons=comparisons,finite_numeric_values=sum(c['numeric_values'] for r in comparisons.values() for c in r.values()),nonfinite_kind_values=sum(c['nonfinite_kind_values'] for r in comparisons.values() for c in r.values()),maximum_relative_error=max(c['maximum_relative_error'] for r in comparisons.values() for c in r.values()),analyses=analyses,Linux_R_fits_executed=False,whole_paper_reproduced=False,released=False,pushed=False,deployed=False))
        print('Passed actual main:4 original fits,6 native analyses,12 portable verifies; skips/refusals retained')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_DESEQ2_MAIN_VALIDATION',error=str(e)));raise

if __name__=='__main__':main()
