"""Run prepared synthetic FastQTL comparisons and real packaged-CLI refusal/refit checks on Linux."""
from pathlib import Path
import argparse,copy,hashlib,json,os,shutil,subprocess,time

PROFILE='fastqtl-2.184-v7-ubuntu-noble-prepared-v1'
CASES=[('gt-nominal','GT','NOMINAL',False,None,True,1),('ds-nominal','DS','NOMINAL',False,None,True,1),('ds-normal','DS','NOMINAL',True,None,True,1),('gt-no-covariates','GT','NOMINAL',False,None,False,1),('nominal-empty','GT','NOMINAL',False,None,True,1e-300),('gt-fixed','GT','PERMUTATION',False,[1000],True,None),('ds-adaptive-2','DS','PERMUTATION',False,[10,1000],True,None),('ds-adaptive-3','DS','PERMUTATION',True,[100,10,1000],True,None)]

def pin(path):
    h=hashlib.sha256();size=0
    with path.open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
    return dict(bytes=size,sha256=h.hexdigest())

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')

def run_checks(jar,home,fixtures,out,java='java'):
    for p in (jar,home,fixtures):assert p.exists(),str(p)
    out.mkdir(exist_ok=False);cases=[];controls=[];refits=[]
    env={'LC_ALL':'C','LANG':'C','TZ':'UTC','PATH':'/usr/bin:/bin'}
    def run(cmd,stem):
        cmd=[str(v) for v in cmd];start=time.time()
        with (out/(stem+'.stdout')).open('xb') as stdout,(out/(stem+'.stderr')).open('xb') as stderr:
            child=subprocess.Popen(cmd,env=env,stdout=stdout,stderr=stderr)
            # The Java CLI manages its own scientific worker. This harness never
            # terminates OS processes by name or scans unrelated descendants.
            code=child.wait(timeout=180)
        return dict(command=cmd,exit_code=code,seconds=time.time()-start,stdout=pin(out/(stem+'.stdout')),stderr=pin(out/(stem+'.stderr')))
    def request(label,kind='GT'):
        f=fixtures/kind
        return dict(schema_version=1,mode='RUN',profile=PROFILE,operation='NOMINAL',runtime_home=str(home),out=str(out/(label+'-capture')),assembly='synthetic-single-chromosome',region='22:9000-100000',inputs=dict(vcf=str(f/'genotypes.vcf.gz'),vcf_index=str(f/'genotypes.vcf.gz.tbi'),phenotypes=str(f/'phenotypes.bed.gz'),phenotype_index=str(f/'phenotypes.bed.gz.tbi'),covariates=str(f/'covariates.txt')),options=dict(seed=701,window=1000,normal=False,maf_threshold=.1,ma_sample_threshold=2,anchor_policy='bed-start-plus-one',input_reviewed=True,threshold=1))
    def invoke(label,r):
        p=out/(label+'.json');save(p,r);return run([java,'-jar',jar,'fastqtl-tools','--request',p],label+'-java')
    status='FAIL_VALIDATION_RETAINED'
    try:
        for label,kind,operation,normal,permutations,covariates,threshold in CASES:
            r=request(label,kind);r['operation']=operation;r['options']['normal']=normal
            if operation=='PERMUTATION':del r['options']['threshold'];r['options']['permutations']=permutations
            else:r['options']['threshold']=threshold
            if not covariates:del r['inputs']['covariates']
            row=dict(case=label,actual=invoke(label,r));cases.append(row);print(label,row['actual']['exit_code'],flush=True);assert row['actual']['exit_code']==0
            f=fixtures/kind;direct=out/(label+'-direct');direct.mkdir()
            cmd=[home/'lib/ld-linux-x86-64.so.2','--inhibit-cache','--library-path',home/'lib',home/'bin/fastQTL','--vcf',f/'genotypes.vcf.gz','--bed',f/'phenotypes.bed.gz','--region','22:9000-100000','--out',direct/'result.txt','--log',direct/'original.log','--seed','701','--window','1000','--maf-threshold','0.1','--ma-sample-threshold','2','--global-af-threshold','0']
            if covariates:cmd+=['--cov',f/'covariates.txt']
            if normal:cmd+=['--normal']
            cmd+=['--permute']+[str(v) for v in permutations] if operation=='PERMUTATION' else ['--threshold',str(threshold)]
            row['direct']=run(cmd,label+'-direct');assert row['direct']['exit_code']==0
            row['byte_identical_direct_original_output']=pin(direct/'result.txt')==pin(out/(label+'-capture')/'worker/result.txt');assert row['byte_identical_direct_original_output']
            row['scientific_summary']=json.loads((out/(label+'-capture')/'scientific-audit.json').read_bytes())
            row['inspect']=run([java,'-jar',jar,'fastqtl-tools','--inspect',out/(label+'-capture')],label+'-inspect');assert row['inspect']['exit_code']==0
        assert cases[4]['scientific_summary']['rows']==0 and cases[5]['scientific_summary']['no_cis_variant_rows']==1
        for mode in ('VERIFY','REPLAY'):
            parent=out/'gt-fixed-capture';before=pin(parent/'manifest.json');label='fresh-'+mode.lower()
            r=dict(schema_version=1,mode=mode,analysis=str(parent),out=str(out/(label+'-capture')));actual=invoke(label,r);assert actual['exit_code']==0 and pin(parent/'manifest.json')==before
            row=dict(mode=mode,actual=actual,comparison=json.loads((out/(label+'-capture')/'fresh-comparison.json').read_bytes()),parent_unchanged=True);refits.append(row);print(label,actual['exit_code'],flush=True)
        def control(label,r,expected):
            row=dict(case=label,expected=expected,actual=invoke(label,r),complete_result=(out/(label+'-capture')/'COMPLETED.json').exists());controls.append(row);assert row['actual']['exit_code']==expected and not row['complete_result'];print(label,expected,flush=True)
        r=request('missing-input');r['inputs']['covariates']=str(out/'absent-covariates');control('missing-input',r,3)
        r=request('missing-runtime');r['runtime_home']=str(out/'absent-runtime');control('missing-runtime',r,3)
        r=request('historical-edition');r['profile']='fastqtl-2.165';control('historical-edition',r,3)
        wrong=out/'wrong-runtime';(wrong/'bin').mkdir(parents=True);(wrong/'bin/fastQTL').write_bytes(b'wrong-edition');shutil.copyfile(home/'COPYRIGHT',wrong/'COPYRIGHT');r=request('wrong-runtime');r['runtime_home']=str(wrong);control('wrong-runtime',r,3)
        missing=out/'incomplete-runtime';(missing/'bin').mkdir(parents=True);shutil.copyfile(home/'bin/fastQTL',missing/'bin/fastQTL');shutil.copyfile(home/'COPYRIGHT',missing/'COPYRIGHT');r=request('missing-library');r['runtime_home']=str(missing);control('missing-library',r,3)
        for label in ('mismatched-samples','duplicate-samples','invalid-covariate','CRLF-covariate'):
            r=request(label);raw=(fixtures/'GT/covariates.txt').read_bytes()
            if label=='mismatched-samples':raw=raw.replace(b'S00',b'UNKNOWN')
            if label=='duplicate-samples':raw=raw.replace(b'S00',b'S01')
            if label=='invalid-covariate':raw=raw.replace(b'age\t',b'age\tNaN\t',1)
            if label=='CRLF-covariate':raw=raw.replace(b'\n',b'\r\n')
            f=out/(label+'.txt');f.write_bytes(raw);r['inputs']['covariates']=str(f);control(label,r,2)
        r=request('foreign-index');r['inputs']['phenotype_index']=str(fixtures/'controls/other-chromosome.bed.gz.tbi');control('foreign-index',r,2)
        r=request('nonfinite-source');r['inputs']['phenotypes']=str(fixtures/'controls/constant.bed.gz');r['inputs']['phenotype_index']=str(fixtures/'controls/constant.bed.gz.tbi');control('nonfinite-source',r,2)
        bad=out/'nonfinite-source-capture';assert (bad/'scientific-audit.json').is_file() and json.loads((bad/'scientific-audit.json').read_bytes())['nonfinite_rows']>0
        r=request('owned-timeout');r['operation']='PERMUTATION';del r['options']['threshold'];r['options']['permutations']=[1000000];r['limits']=dict(timeout_seconds=1);control('owned-timeout',r,2)
        process=json.loads((out/'owned-timeout-capture/worker-process.json').read_bytes());assert process['owned_processes'] and not any(p['same_identity_alive'] for p in process['owned_processes']);controls[-1]['owned_processes_closed']=True
        r=request('existing-output');Path(r['out']).mkdir();(Path(r['out'])/'keep.txt').write_text('original');control('existing-output',r,2);assert (Path(r['out'])/'keep.txt').read_text()=='original'
        # Recompute forged metadata hashes: inspection must derive the source
        # numerical status rather than trust an edited count in JSON.
        forged=out/'forged-nonfinite';shutil.copytree(bad,forged);(forged/'analysis-status.json').unlink();(forged/'FAILURE.md').unlink()
        canonical=json.loads((forged/'canonical-request.json').read_bytes());canonical['out']=str(forged);(forged/'canonical-request.json').write_text(json.dumps(canonical)+'\n')
        scientific=json.loads((forged/'scientific-audit.json').read_bytes());scientific['nonfinite_rows']=0;(forged/'scientific-audit.json').write_text(json.dumps(scientific)+'\n')
        inventory=dict(files={p.relative_to(forged).as_posix():pin(p) for p in sorted(forged.rglob('*')) if p.is_file()},directories=sorted(p.relative_to(forged).as_posix() for p in forged.rglob('*') if p.is_dir()))
        save(forged/'manifest.json',dict(status='COMPLETE_SCOPED_FASTQTL_OPERATION',profile=PROFILE,scientific_summary=scientific,inventory=inventory));save(forged/'COMPLETED.json',dict(status='COMPLETE_SCOPED_FASTQTL_OPERATION',manifest=pin(forged/'manifest.json')))
        actual=run([java,'-jar',jar,'fastqtl-tools','--inspect',forged],'forged-nonfinite-inspect');assert actual['exit_code']==2;controls.append(dict(case='forged-nonfinite-summary-with-refreshed-hashes',expected=2,actual=actual,scientific_claim_refused=True))
        extra_request=request('extra-file-control');precondition=invoke('extra-file-control',extra_request);assert precondition['exit_code']==0
        parent=Path(extra_request['out']);before_output=pin(parent/'worker/result.txt');(parent/'unexpected.txt').write_text('unadmitted extra file')
        actual=run([java,'-jar',jar,'fastqtl-tools','--inspect',parent],'extra-file-inspect');assert actual['exit_code']==2 and pin(parent/'worker/result.txt')==before_output;controls.append(dict(case='extra-saved-file',expected=2,actual=actual,precondition=precondition,scientific_output_unchanged=True))
        # Keep the altered control capture as failed evidence. All eight primary
        # completed captures and both fresh refits retain their original bytes.
        status='PASS_EIGHT_ORIGINAL_COMPARISONS_TWO_FRESH_REFITS_FIFTEEN_REFUSALS'
    finally:
        result=dict(status=status,jar=pin(jar),cases=cases,refits=refits,controls=controls,catalog_credit=0,whole_goal_complete=False)
        save(out/'RESULT.json',result)
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('jar','runtime-home','out'):parser.add_argument('--'+name,required=True,type=Path)
    parser.add_argument('--fixtures',type=Path,default=Path(__file__).resolve().parent/'fixtures');parser.add_argument('--java',default='java');args=parser.parse_args()
    result=run_checks(args.jar.resolve(),args.runtime_home.resolve(),args.fixtures.resolve(),args.out.absolute(),args.java);print(result['status'])
