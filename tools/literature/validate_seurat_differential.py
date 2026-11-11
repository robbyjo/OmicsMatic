# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare real typed Seurat marker requests with independent original R controls."""
import argparse,csv,json,math,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save,inventory
from validate_deseq2_inference import JAVA21,JAVA25,linux
BASE=ROOT/'build/literature-review/signac-runtime'

def compare(original,observed,mode,only_probabilities=False):
    with original.open(encoding='utf8',newline='') as f:a=list(csv.DictReader(f,delimiter='\t'))
    with observed.open(encoding='utf8',newline='') as f:b=list(csv.DictReader(f,delimiter='\t'))
    assert len(a)==len(b),(original,len(a),len(b));maximum=0;finite=0;nonfinite=0
    for expected,actual in zip(a,b):
        assert actual['feature']==expected['' if mode=='PAIR' else 'gene'];assert actual['contrast']==('pairwise' if mode=='PAIR' else expected['cluster'])
        pairs=[('p_val','p_value'),('p_val_adj','p_adj')]
        if not only_probabilities:pairs +=[(next(k for k in expected if k.startswith('avg_')),'fold_change'),('pct.1','pct_1'),('pct.2','pct_2')]
        for source,target in pairs:
            x,y=expected[source],actual[target]
            if x in ['NA','NaN','Inf','-Inf']:assert x==y,(source,x,y);nonfinite+=1;continue
            p,q=float(x),float(y);assert math.isfinite(q) and (p==0)==(q==0),(source,x,y)
            error=abs(p-q)/max(abs(p),1e-300);assert error<=1e-9,(source,x,y,error);maximum=max(maximum,error);finite+=1
    return dict(rows=len(a),finite_fields=finite,nonfinite_kinds=nonfinite,maximum_relative_error=maximum)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    jar=BASE/'differential-tools-development/omicsmatic-0.3.7-seurat-differential-development.jar';artifact=pin(jar);parent=BASE/'differential-summary-development/omicsmatic-0.3.7-seurat-marker-summary-development.jar'
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as new:
        assert len(new.namelist())==len(set(new.namelist())) and new.testzip() is None;added=sorted(set(new.namelist())-set(old.namelist()));changed=[n for n in old.namelist() if n not in new.namelist() or old.read(n)!=new.read(n)]
        assert not changed and all(n.startswith(('org/jlinalg/cli/SeuratDifferential','org/jlinalg/omics/seurat-differential-reference/','org/jlinalg/omics/seurat_differential.R')) for n in added),(added,changed)
    source=BASE/'differential-requests-2';reference=BASE/'differential-original-reference-3/original';cases=json.loads((reference/'RESULT.json').read_bytes())['cases'];before=inventory();save(out/'BASES-BEFORE.json',before);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');sources={p.relative_to(ROOT).as_posix():pin(p) for p in [*sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('SeuratDifferential*.java')),*sorted((ROOT/'src/main/resources/org/jlinalg/omics/seurat-differential-reference').iterdir()),ROOT/'src/main/resources/org/jlinalg/omics/seurat_differential.R',ROOT/'src/test/java/org/jlinalg/cli/SeuratDifferentialContractsTest.java',Path(__file__),ROOT/'tools/literature/prepare_seurat_differential_requests.py'] if p.is_file()}
    def command(label,argv,expected=0):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:result=subprocess.run(list(map(str,argv)),cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
        save(out/(label+'.process.json'),dict(command=list(map(str,argv)),exit_code=result.returncode,expected_exit_code=expected));assert result.returncode==expected,label+' failed; inspect retained logs'
    def invoke(platform,request):
        if platform=='linux-java21':return ['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx768m','-cp',linux(jar),'org.jlinalg.cli.SeuratDifferentialCli','--request',linux(request)]
        return [JAVA21 if platform=='windows-java21' else JAVA25,'-Xmx768m','-cp',jar,'org.jlinalg.cli.SeuratDifferentialCli','--request',request]
    runs=[]
    try:
        for platform in ['windows-java21','windows-java25']:
            for name in cases:
                label=platform+'-'+name;request=json.loads((source/(name+'.json')).read_bytes())
                for key in ['counts','features','cells','metadata','data']:request[key]=str((source/request[key]).resolve())
                target=out/(label+'-结果');request['out']=str(target);save(out/(label+'.json'),request);command(label,invoke(platform,out/(label+'.json')))
                assert json.loads((target/'analysis-status.json').read_bytes())['results_admitted'];comparison=compare(reference/(name+'.tsv'),target/'markers.tsv',request['mode']);assert comparison['rows']==cases[name]['rows']
                save(out/(label+'-verify.json'),dict(schema_version=1,mode='VERIFY',analysis=target.name))
                for verifier in ['windows-java21','windows-java25','linux-java21']:command(label+'-verify-'+verifier,invoke(verifier,out/(label+'-verify.json')))
                report=json.loads((target/'worker/worker-report.json').read_bytes());assert len(report['warnings'])==len(cases[name]['warnings']),(label,len(report['warnings']),len(cases[name]['warnings']));runs.append(dict(case=name,platform=platform,analysis=target.relative_to(ROOT).as_posix(),comparison=comparison,source_warning_count=len(report['warnings']),files={p.relative_to(target).as_posix():pin(p) for p in target.rglob('*') if p.is_file()}));print(label+' matched independent original fields and three portable verifications',flush=True)
        after=inventory();save(out/'BASES-AFTER.json',after);assert before==after and pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'COMPLETED.json',dict(status='passed-typed-original-Seurat-marker-six-case-three-JVM-matrix',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),parent=pin(parent),new_entries=added,changed_parent_entries=changed,source_pins=sources,canonical_executable_preserved=canonical,actual_original_requests=12,portable_capture_verifications=36,runs=runs,comparisons=dict(finite_fields=sum(r['comparison']['finite_fields'] for r in runs),nonfinite_kinds=sum(r['comparison']['nonfinite_kinds'] for r in runs),maximum_relative_error=max(r['comparison']['maximum_relative_error'] for r in runs)),prior_R_library_files_unchanged=sum(len(v) for v in before.values()),native_statistical_p_value_replay=False,whole_goal_complete=False))
        print('Completed12 real fits and36 portable verifications; exact original comparisons retained',flush=True)
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_TYPED_SEURAT_MARKER_MATRIX',error=str(e)));raise

if __name__=='__main__':main()
