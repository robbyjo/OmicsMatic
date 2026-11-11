# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare typed DESeq2 requests to independent original-package calls and verify captures without R."""
import argparse
import concurrent.futures
import csv
import json
import math
from pathlib import Path
import shutil
import subprocess
import zipfile
from setup_deseq2_reference import ROOT, BASES, pin, save

BASE=ROOT/'build/literature-review/deseq2-runtime'
JAVA21='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
JAVA25='C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'


def linux(path):return '/mnt/d/'+Path(path).resolve().relative_to(Path('D:/')).as_posix()


def table(path):
    with path.open(encoding='utf-8',newline='') as stream:return list(csv.reader(stream,delimiter='\t',quoting=csv.QUOTE_NONE))


def write_table(path,rows):
    path.write_text('\n'.join('\t'.join(row) for row in rows)+'\n',encoding='utf-8',newline='\n')


def compare_tables(reference,actual,mapping):
    source=table(reference);observed=table(actual);assert len(source)==len(observed),(reference,actual,'rows')
    header=[mapping.get(s,s) for s in source[0]];assert header==observed[0],(reference,actual,'header',header,observed[0])
    numeric=0;missing=0;max_relative=0.0
    for expected,row in zip(source[1:],observed[1:],strict=True):
        assert len(expected)==len(row) and mapping.get(expected[0],expected[0])==row[0],(reference,'identity/order')
        for a,b in zip(expected[1:],row[1:],strict=True):
            a=a.strip();b=b.strip()
            if a in ['NA','NaN','Inf','-Inf']:
                assert a==b,(reference,a,b);missing+=1
            else:
                x,y=float(a),float(b);assert math.isfinite(y)
                assert math.isclose(x,y,rel_tol=2e-10,abs_tol=1e-300),(reference,x,y)
                if x!=0:max_relative=max(max_relative,abs(x-y)/abs(x))
                numeric+=1
    return dict(numeric_values=numeric,nonfinite_kind_values=missing,maximum_relative_error=max_relative)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    reference=BASE/'inference-reference-3';assert json.loads((reference/'process.json').read_bytes())['exit_code']==0
    original=json.loads((reference/'cases/ORIGINAL-SUMMARY.json').read_bytes());assert len(original)==13 and all(v['status']=='success' for v in original.values())
    jar=BASE/'inference-development/omicsmatic-0.3.7-deseq2-inference-development.jar';artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    sources=[*ROOT.glob('src/main/java/org/jlinalg/cli/Deseq2*.java'),ROOT/'src/test/java/org/jlinalg/cli/Deseq2InferenceContractsTest.java',Path(__file__),ROOT/'tools/literature/collect_deseq2_inference_reference.R',ROOT/'tools/literature/deseq2-inference-development.init.gradle',ROOT/'src/main/resources/org/jlinalg/omics/deseq2_inference.R']
    sources += [p for p in (ROOT/'src/main/resources/org/jlinalg/omics/deseq2-reference').rglob('*') if p.is_file()]
    bound={p.relative_to(ROOT).as_posix():pin(p) for p in sources};save(out/'SOURCE-PINS.json',bound)
    for p in sources:
        target=out/'source-snapshot'/p.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
    with zipfile.ZipFile(BASE/'normalization-build-1/omicsmatic-0.3.7-deseq2-normalization-development.jar') as old,zipfile.ZipFile(jar) as new:
        names={n for n in old.namelist() if not n.endswith('/')};new_names={n for n in new.namelist() if not n.endswith('/')}
        assert names<=new_names and all(old.read(n)==new.read(n) for n in names),'A preexisting packaged file changed'
        save(out/'parent-artifact-audit.json',dict(preexisting_file_entries_unchanged=len(names),added_entries=sorted(new_names-names)))

    def command(name,argv,expected=0):
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
            p=subprocess.run(list(map(str,argv)),cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
        save(out/(name+'.process.json'),dict(command=list(map(str,argv)),exit_code=p.returncode,expected_exit_code=expected))
        assert p.returncode==expected,name+' failed; inspect retained logs and analysis status'

    libraries=[BASE/'inference-extra-setup-1/R-lib',BASE/'reference-setup-1/R-lib',*BASES[:-1]]
    platforms=[('windows-java21',JAVA21),('windows-java25',JAVA25)]

    def prepare_case(directory,name):
        directory.mkdir();src=reference/'cases'/name;request=json.loads((src/'request-template.json').read_bytes());mapping={}
        count_rows=table(src/request['counts'])
        for sample in count_rows[0][1:]:mapping[sample]='样本_β_'+sample[1:]
        for row in count_rows[1:]:mapping[row[0]]='基因_α_'+row[0][1:]
        for key in ['counts','samples','keep_genes','size_factors','normalization_factors','geometric_means','control_genes']:
            if key not in request:continue
            rows=table(src/request[key])
            for row in rows:
                for i,value in enumerate(row):row[i]=mapping.get(value,value)
            if key=='samples':
                header=rows[0]
                if 'condition' in header:
                    c=header.index('condition');levels={'control':'对照','treated':'处理','third':'第三'}
                    for row in rows[1:]:row[c]=levels[row[c]]
                if 'biological_sample' in header:
                    c=header.index('biological_sample')
                    for row in rows[1:]:mapping[row[c]]='供体_γ_'+row[c][3:];row[c]=mapping[row[c]]
            write_table(directory/request[key],rows)
        for variable in request['variables']:
            if variable['name']=='condition':variable['levels']=[{'control':'对照','treated':'处理','third':'第三'}[x] for x in variable['levels']]
        for result in request.get('results',[]):
            selector=result['selector']
            if selector['kind']=='factor':
                for key in ['numerator','denominator']:selector[key]={'control':'对照','treated':'处理','third':'第三'}[selector[key]]
        request.update(rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_libraries=list(map(str,libraries)),out='结果/analysis',maximum_output_bytes=100_000_000,timeout_seconds=180)
        save(directory/'request.json',request);return request,mapping

    def execute(item):
        platform,java,name=item;label=platform+'-'+name;directory=out/label;request,mapping=prepare_case(directory,name)
        argv=[java,'-Xmx512m','-cp',jar,'org.jlinalg.cli.Deseq2InferenceCli','--request',directory/'request.json']
        command(label,argv);analysis=directory/'结果/analysis';status=json.loads((analysis/'analysis-status.json').read_bytes());assert status['results_admitted'] and status['status']=='completed-with-warnings'
        src=reference/'cases'/name;comparisons={}
        for source,target in [('original-prepared.tsv','counts-prepared.tsv'),('original-normalized.tsv','normalized-counts.tsv'),('original-factors.tsv','size-factors.tsv')]:
            if (src/source).exists():comparisons[source]=compare_tables(src/source,analysis/target,mapping)
        for source,spec in original[name]['outputs'].items():
            if spec['type']=='transform':target='transforms/'+spec['id']+'.tsv'
            else:target='results/'+spec['id']+('-shrunken' if spec['kind']=='shrunken' else '')+'.tsv'
            comparisons[source]=compare_tables(src/source,analysis/target,mapping)
        verifies=[]
        if platform=='windows-java21':
            verify=directory/'verify.json';save(verify,dict(schema_version=1,mode='VERIFY',analysis='结果/analysis'))
            for check_platform,check_java in platforms:
                command(label+'-verify-'+check_platform,[check_java,'-Xmx512m','-cp',jar,'org.jlinalg.cli.Deseq2InferenceCli','--request',verify]);verifies.append(check_platform)
            command(label+'-verify-linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx512m','-cp',linux(jar),'org.jlinalg.cli.Deseq2InferenceCli','--request',linux(verify)]);verifies.append('linux-java21')
        if name=='paired':
            before={p.relative_to(analysis).as_posix():pin(p) for p in analysis.rglob('*') if p.is_file()};command(label+'-existing-output',argv,2)
            assert before=={p.relative_to(analysis).as_posix():pin(p) for p in analysis.rglob('*') if p.is_file()}
        save(directory/'COMPARISON.json',dict(status='matched-independent-original-DESeq2',comparisons=comparisons,verifies=verifies,identities_preserved=True))
        print(label+': original numeric outputs matched; exact Unicode identities retained',flush=True)
        return dict(platform=platform,case=name,analysis=analysis.relative_to(ROOT).as_posix(),comparisons=comparisons,manual_verifies=verifies)

    try:
        jobs=[(platform,java,name) for platform,java in platforms for name in original]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:runs=list(pool.map(execute,jobs))
        controls=[]
        for name,template,modify,expected in [
            ('missing-runtime','paired',lambda r:r.update(rscript='missing-Rscript.exe'),3),
            ('missing-optional','paired',lambda r:r.update(r_libraries=list(map(str,libraries[1:]))),3),
            ('timeout','paired',lambda r:r.update(timeout_seconds=1),2),
            ('source-normal-interaction-failure','interaction',lambda r:r.update(results=[dict(id='unsupported_normal',selector=dict(kind='coefficient',index=4),shrink=dict(type='normal'))]),2),
            ('source-glm-factor-failure','glm_lrt',lambda r:r.update(results=[dict(id='source_factor',selector=dict(kind='factor',factor='condition',numerator='第三',denominator='对照'))]),2)
        ]:
            directory=out/name;request,_=prepare_case(directory,template);modify(request);save(directory/'request.json',request)
            command(name,[JAVA21,'-Xmx512m','-cp',jar,'org.jlinalg.cli.Deseq2InferenceCli','--request',directory/'request.json'],expected)
            analysis=directory/'结果/analysis';state=json.loads((analysis/'analysis-status.json').read_bytes());assert not state['results_admitted']
            warnings=(analysis/'WARNINGS.md').read_text(encoding='utf-8');assert warnings.startswith('# SKIP3' if expected==3 else '# FAIL2') and 'Required action:' in warnings
            save(directory/'verify.json',dict(schema_version=1,mode='VERIFY',analysis='结果/analysis'))
            command(name+'-verify-refused',[JAVA21,'-cp',jar,'org.jlinalg.cli.Deseq2InferenceCli','--request',directory/'verify.json'],2)
            if name=='timeout':
                manifest=json.loads((analysis/'manifest.json').read_bytes());assert manifest.get('owned_process_cleanup') and all(not p['alive_after_cleanup'] for p in manifest['owned_process_cleanup'])
            controls.append(dict(name=name,status=state['status'],expected_exit_code=expected,failed_capture_refused=True))
        assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        assert all(pin(ROOT/n)==v for n,v in bound.items())
        counts=dict(numeric_values=sum(c['numeric_values'] for r in runs for c in r['comparisons'].values()),nonfinite_kind_values=sum(c['nonfinite_kind_values'] for r in runs for c in r['comparisons'].values()),maximum_relative_error=max(c['maximum_relative_error'] for r in runs for c in r['comparisons'].values()))
        save(out/'COMPLETED.json',dict(status='passed-typed-original-DESeq2-inference-and-portable-captures',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=bound,reference_source=pin(reference/'SOURCE.R'),original_reference_cases=13,actual_original_runtime_requests=len(runs),manual_Java_only_verifies=sum(len(r['manual_verifies']) for r in runs),runs=runs,comparisons=counts,controls=controls,existing_output_refusals=2,canonical_artifact_unchanged=canonical,full_historical_paper_workflows_reproduced=False,catalog_implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False))
        print(json.dumps(counts),flush=True)
    except Exception as error:
        save(out/'FAILURE.json',dict(status='FAIL2_DESEQ2_INFERENCE_VALIDATION',error=str(error)));raise


if __name__=='__main__':main()
