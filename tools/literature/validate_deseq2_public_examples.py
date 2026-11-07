# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check literal DESeq2 examples with the clean private runtime and setup refusal policy."""
import argparse
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save
from validate_deseq2_inference import JAVA21,JAVA25,linux,compare_tables


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve()
    base=ROOT/'build/literature-review/deseq2-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir()
    example=ROOT/'examples/deseq2-inference';jar=base/'inference-development/omicsmatic-0.3.7-deseq2-inference-development.jar'
    setup=ROOT/'build/deseq2-user-runtime';assert json.loads((setup/'RESULT.json').read_bytes())['status']=='INSTALLED_DESEQ2_REFERENCE_RUNTIME_NOT_USER_ANALYSIS'
    sources=[p for p in example.iterdir() if p.is_file()]+[Path(__file__),ROOT/'tools/literature/install_deseq2_runtime.R',ROOT/'docs/literature/DESEQ2_INFERENCE_DEVELOPMENT.md',ROOT/'tools/literature/prepare_deseq2_inference_examples.py']
    bound={p.relative_to(ROOT).as_posix():pin(p) for p in sources};artifact=pin(jar)
    def command(name,argv,expected=0,env=None):
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:p=subprocess.run(list(map(str,argv)),cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
        save(out/(name+'.process.json'),dict(command=list(map(str,argv)),exit_code=p.returncode,expected_exit_code=expected));assert p.returncode==expected,name+' failed; inspect retained logs'
    try:
        runs=[]
        for name in ['paired','shrinkage','lrt']:
            request=example/(name+'.json');payload=json.loads(request.read_bytes());target=(example/payload['out']).resolve();assert not target.exists()
            argv=[JAVA21,'-Xmx512m','-cp',jar,'org.jlinalg.cli.Deseq2InferenceCli','--request',request];command(name,argv)
            status=json.loads((target/'analysis-status.json').read_bytes());assert status['results_admitted']
            identities=json.loads((target/'prepared-inputs.json').read_bytes());mapping={**{'g'+str(i+1):v for i,v in enumerate(identities['genes'])},**{'s'+str(i+1):v for i,v in enumerate(identities['samples'])}}
            reference=base/'inference-reference-3/cases'/('lrt' if name=='lrt' else 'paired');comparisons={}
            for source,dest in [('original-prepared.tsv','counts-prepared.tsv'),('original-normalized.tsv','normalized-counts.tsv'),('original-factors.tsv','size-factors.tsv')]:comparisons[source]=compare_tables(reference/source,target/dest,mapping)
            for r in payload['results']:
                for kind in ['base','shrunken'] if 'shrink' in r else ['base']:
                    source='original-'+r['id']+'-'+kind+'.tsv';dest='results/'+r['id']+('-shrunken' if kind=='shrunken' else '')+'.tsv';comparisons[source]=compare_tables(reference/source,target/dest,mapping)
            for t in payload.get('transforms',[]):
                source='original-transform-'+t['id']+'.tsv';comparisons[source]=compare_tables(reference/source,target/'transforms'/(t['id']+'.tsv'),mapping)
            for platform,java in [('windows-java21',JAVA21),('windows-java25',JAVA25)]:command(name+'-verify-'+platform,[java,'-Xmx512m','-cp',jar,'org.jlinalg.cli.Deseq2InferenceCli','--request',example/('verify-'+name+'.json')])
            command(name+'-verify-linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx512m','-cp',linux(jar),'org.jlinalg.cli.Deseq2InferenceCli','--request',linux(example/('verify-'+name+'.json'))])
            before={p.relative_to(target).as_posix():pin(p) for p in target.rglob('*') if p.is_file()};command(name+'-existing-output',argv,2);assert before=={p.relative_to(target).as_posix():pin(p) for p in target.rglob('*') if p.is_file()}
            runs.append(dict(example=name,analysis=target.relative_to(ROOT).as_posix(),files=before,comparisons=comparisons))
        fresh=json.loads((example/'paired.json').read_bytes())
        for key in ['counts','samples','keep_genes']:fresh[key]=str((example/fresh[key]).resolve())
        fresh['r_libraries']=[str(setup/'R-lib')];fresh['out']=str(out/'fresh-win25-analysis');save(out/'fresh-win25.json',fresh)
        command('fresh-win25',[JAVA25,'-Xmx512m','-cp',jar,'org.jlinalg.cli.Deseq2InferenceCli','--request',out/'fresh-win25.json'])
        assert (out/'fresh-win25-analysis/results/paired.tsv').read_bytes()==(ROOT/'build/deseq2-example-paired/results/paired.tsv').read_bytes()
        command('forbidden-install',[RSCRIPT,'--vanilla',ROOT/'tools/literature/install_deseq2_runtime.R',out/'forbidden-library',out/'forbidden-report','forbid'],3,environment())
        assert not (out/'forbidden-library').exists() and '# SKIP3' in (out/'forbidden-report/WARNINGS.md').read_text()
        before={p.relative_to(setup/'R-lib').as_posix():pin(p) for p in (setup/'R-lib').rglob('*') if p.is_file()}
        command('existing-runtime-refused',[RSCRIPT,'--vanilla',ROOT/'tools/literature/install_deseq2_runtime.R',setup/'R-lib',setup/'report','allow'],1,environment())
        assert before=={p.relative_to(setup/'R-lib').as_posix():pin(p) for p in (setup/'R-lib').rglob('*') if p.is_file()}
        assert pin(jar)==artifact and all(pin(ROOT/n)==v for n,v in bound.items())
        save(out/'COMPLETED.json',dict(status='passed-literal-DESeq2-examples-with-clean-runtime',artifact=artifact,source_pins=bound,literal_examples=3,original_runtime_requests=4,manual_verifies=9,existing_output_refusals=3,forbidden_install_skipped_without_library_creation=True,existing_runtime_refused_and_preserved=True,runs=runs,maximum_relative_error=max(c['maximum_relative_error'] for r in runs for c in r['comparisons'].values()),finite_numeric_values=sum(c['numeric_values'] for r in runs for c in r['comparisons'].values()),nonfinite_kind_values=sum(c['nonfinite_kind_values'] for r in runs for c in r['comparisons'].values()),clean_runtime_setup=pin(setup/'RESULT.json')))
        print('3 literal examples and a fresh Java25 fit passed with the clean runtime; setup/refusal controls passed')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_LITERAL_DESEQ2_INFERENCE_EXAMPLES',error=str(e)));raise


if __name__=='__main__':main()
