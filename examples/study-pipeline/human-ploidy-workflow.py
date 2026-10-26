"""Packaged human-ploidy acceptance; synthetic inputs and independent NumPy/SciPy OLS.

python human-ploidy-workflow.py --jar /path/jlinalg.jar --root NEW_DIRECTORY
The root is never overwritten. No network/LLM request is made.
"""
from pathlib import Path
import argparse,csv,hashlib,json,math,shutil,subprocess,sys
import numpy as np
from scipy import stats
import scipy
import yaml

REGIONS=['autosomes','par','x_nonpar_xx','x_nonpar_xy','y_nonpar_xy']
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,obj):path.write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def table(path,header,rows):
    with path.open('w',encoding='utf-8',newline='') as f:w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(header);w.writerows(rows)
def rows(path):
    with path.open(encoding='utf-8') as f:return list(csv.DictReader(f,delimiter='\t'))
def run(jar,folder,label,args,expected=0):
    command=['java','-jar',str(jar),*map(str,args)]
    result=subprocess.run(command,cwd=folder,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8')
    (folder/(label+'.log')).write_text(result.stdout,encoding='utf-8');write(folder/(label+'-execution.json'),{'command':command,'exit_code':result.returncode})
    assert (result.returncode==expected if expected==0 else result.returncode!=0),(label,result.returncode,result.stdout[-2500:])
    return result
def fixture(folder,scaled=False,lost=False,unknown=False,invalid=False,binary=False,build='GRCh38'):
    folder.mkdir();ids=['private-synthetic-'+str(i) for i in range(160)]
    g=np.array([int(i%80<20) for i in range(160)]);age=np.array([i%7 for i in range(160)])
    y=np.array([int(i%3==0) for i in range(160)]) if binary else 2+.8*g+.12*age+np.sin(np.arange(160)*1.4)
    table(folder/'phenotype.tsv',['id','y','age'],([ids[i],int(y[i]) if binary else y[i],'NA' if lost and i==0 else int(age[i])] for i in reversed(range(160))))
    table(folder/'ploidy.tsv',['id','chromosome_complement'],([ids[i],'XXY' if unknown and i==0 else 'XY' if i<80 else 'XX'] for i in range(160)))
    par=60001 if build=='GRCh37' else 10001
    variants=[('auto','1',100,g),('x20','X',3000000,g),('x19','X',3000001,np.array([int(i%80<19) for i in range(160)])),('xflip20','X',3000002,1-g),('y20','Y',3000000,g),('par','X',par,np.arange(160)%3),('y-par-duplicate','Y',10001,np.arange(160)%3)]
    lines=['##fileformat=VCFv4.2','##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">','#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t'+'\t'.join(ids)]
    for name,chrom,pos,values in variants:
        calls=[]
        for i,value in enumerate(values):
            hap=chrom in ['X','Y'] and pos>=3000000 and name!='par' and i<80
            call=str(value) if hap and not scaled else ('0/0','0/1','1/1')[int(value)*(2 if hap and scaled else 1)]
            if invalid and name=='x20' and i==21:call='0/1'
            calls.append(call)
        lines.append('\t'.join([chrom,str(pos),name,'A','G','.','PASS','.','GT',*calls]))
    (folder/'genotype.vcf').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    return dict(ids=ids,g=g,age=age,y=y,variants=variants,build=build)
def close(actual,expected,label):assert math.isclose(float(actual),float(expected),rel_tol=2e-7,abs_tol=2e-9),(label,actual,expected)
def ols(truth,ids,values):
    x=np.column_stack([np.ones(len(ids)),truth['age'][ids],values[ids]]);y=truth['y'][ids]
    beta=np.linalg.lstsq(x,y,rcond=None)[0];df=len(ids)-x.shape[1];res=y-x@beta;se=np.sqrt(np.linalg.inv(x.T@x)[-1,-1]*(res@res)/df);t=beta[-1]/se
    return dict(beta=beta[-1],standard_error=se,statistic=t,p_value=2*stats.t.sf(abs(t),df))
def check_row(row,truth,region,marker,eligible=None):
    ids=list(range(160)) if eligible is None else list(eligible)
    if region.endswith('_xy'):ids=[i for i in ids if i<80]
    elif region.endswith('_xx'):ids=[i for i in ids if i>=80]
    values=next(v[3] for v in truth['variants'] if v[0]==marker);ploidy=1 if region.endswith('_xy') else 2
    close(row['n_samples'],len(ids),'N');close(row['allele_number'],ploidy*len(ids),'physical AN');ac=sum(values[ids]);close(row['minor_allele_count'],min(ac,ploidy*len(ids)-ac),'MAC')
    if min(ac,ploidy*len(ids)-ac)<20:assert row['status']=='filtered' and 'BELOW_MINIMUM_MAC' in row['filter_reason']
    if row['status']=='ok':
        for key,value in ols(truth,ids,values).items():close(row[key],value,'NumPy/SciPy '+key)
def cli_case(root,jar,name,region='x_nonpar_xy',extra=None,expected=0,**kwargs):
    folder=root/name;truth=fixture(folder,**kwargs);before={p.name:sha(p) for p in folder.iterdir() if p.suffix in ['.vcf','.tsv']}
    args=['--omics','genotype.vcf','--pheno','phenotype.tsv','--id','id','--formula','y~age+<omics>','--model','ols','--omics-type','gwas','--human-ploidy-region',region,'--human-ploidy-build',truth['build'],'--ploidy-samples','ploidy.tsv','--out','result.tsv','--threads','1','--no-log','--no-config',*(extra or [])]
    run(jar,folder,'association',args,expected);assert before=={name:sha(folder/name) for name in before}
    if expected:
        if kwargs.get('invalid') or kwargs.get('scaled'):
            assert not yaml.safe_load((folder/'result.tsv.human-ploidy.yaml').read_text(encoding='utf-8'))['scan_finished']
        return {'case':name,'state':'rejected','exit_record':'association-execution.json'}
    raw={r['id']:r for r in rows(folder/'result.tsv')};summary=yaml.safe_load((folder/'result.tsv.human-ploidy.yaml').read_text(encoding='utf-8'));assert summary['scanned_source_variants']==7 and summary['scan_finished']
    eligible=[i for i in range(160) if not (i==0 and (kwargs.get('lost') or kwargs.get('unknown')))]
    for marker,row in raw.items():
        if not kwargs.get('binary') and not kwargs.get('invalid'):check_row(row,truth,region,marker,eligible)
    assert ('y-par-duplicate' not in raw and 'y20' in raw) if region=='y_nonpar_xy' else 'y-par-duplicate' not in raw
    if kwargs.get('lost') or kwargs.get('unknown'):assert raw['x20']['status']=='filtered' and 'BELOW_MINIMUM_MAC' in raw['x20']['filter_reason']
    if kwargs.get('invalid'):
        close(raw['x20']['allele_number'],79,'invalid-call AN');assert summary['invalid_calls']==1;assert len(rows(folder/'result.tsv.human-ploidy-invalid-calls.tsv'))==1
    return {'case':name,'state':'complete','variants':len(raw),'independent_ols':not kwargs.get('binary') and not kwargs.get('invalid')}
def study_case(root,jar):
    folder=root/'study';truth=fixture(folder)
    hypotheses=[]
    for region in REGIONS:
        association={'model':'ols','human_ploidy':{'region':region,'genome_build':'GRCh38'},'threads':1}
        if region in ['par','x_nonpar_xx']:association['minimum_hwe_p']=1e-8
        hypotheses.append({'id':'H_'+region,'description':'Synthetic separately registered chromosome/complement estimand.','outcome':'y','covariates':['age'],'inputs':{'discovery':['a']},'analysis':{'mode':'manual','goals':['association'],'association':association,'harmonization':{'effect_outliers':{'enabled':False}}}})
    config={'schema_version':1,'project':{'id':'human-ploidy','title':'Synthetic human-ploidy acceptance','description':'Offline numerical verification only. Declared complements are not inferred sex or karyotype certification.'},'resources':{'directory':'fixture-resources','network':'offline','downloads':'deny'},'datasets':[{'id':'a','cohort':'synthetic','type':'gwas','stage':'processed_matrix','species':'human','genome_build':'GRCh38','files':{'matrix':'genotype.vcf','phenotype':'phenotype.tsv','ploidy_samples':'ploidy.tsv'}}],'hypotheses':hypotheses}
    write(folder/'study.yaml',config);before={p.name:sha(p) for p in folder.iterdir() if p.suffix in ['.vcf','.tsv']}
    run(jar,folder,'study-run',['study','auto',folder/'study.yaml','--offline'])
    manifests=[(p,yaml.safe_load(p.read_text(encoding='utf-8'))) for p in (folder/'runs').rglob('manifest.yaml')]
    associations=[(p,m) for p,m in manifests if m.get('operation_type')=='association'];assert len(associations)==5
    checks=[]
    for path,m in associations:
        assert m['state']=='complete',m;region=m['hypothesis'][2:];raw=rows(path.parent/'association.tsv');assert raw
        for r in raw:check_row(r,truth,region,r['id'])
        assert len([f for f in m['output_sha256'] if '.human-ploidy' in f])==4
        qc=next((p,d) for p,d in manifests if d.get('operation_type')=='participant_qc' and d.get('hypothesis')==m['hypothesis']);assert qc[1]['state']=='complete'
        summary=yaml.safe_load((qc[0].parent/'prefit-qc.yaml').read_text(encoding='utf-8'));assert summary['analysis_samples']==(80 if region.endswith(('_xx','_xy')) else 160)
        checks.append({'region':region,'variants':len(raw),'state':m['state']})
    assert before=={name:sha(folder/name) for name in before};assert not list(folder.rglob('request.json'))
    run(jar,folder,'study-verify',['study','verify','--project',folder])
    return {'case':'five-contrast-study','state':'complete','contrasts':checks,'llm_requests':0,'network_requests':0}
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--root',type=Path,required=True);a=ap.parse_args();root=a.root.resolve();root.mkdir();software=root/'software';software.mkdir();jar=software/a.jar.name;shutil.copyfile(a.jar,jar);shutil.copyfile(__file__,root/'workflow-verifier-source.py')
    write(root/'execution-software.json',{'jar_sha256':sha(jar),'verifier_sha256':sha(Path(__file__)),'numpy':np.__version__,'scipy':scipy.__version__,'python':sys.version})
    cases=[]
    for region in REGIONS:cases.append(cli_case(root,jar,region,region))
    cases.append(cli_case(root,jar,'diploid-coded',scaled=True,extra=['--haploid-source-scale','diploid']))
    cases.append(cli_case(root,jar,'wrong-source-scale',scaled=True,expected=1))
    cases.append(cli_case(root,jar,'complete-case-mac',lost=True))
    cases.append(cli_case(root,jar,'unknown-error',unknown=True,expected=1))
    cases.append(cli_case(root,jar,'unknown-exclude',unknown=True,extra=['--unknown-ploidy-samples','exclude']))
    cases.append(cli_case(root,jar,'invalid-error',invalid=True,expected=1))
    cases.append(cli_case(root,jar,'invalid-missing',invalid=True,extra=['--invalid-ploidy-calls','missing']))
    cases.append(cli_case(root,jar,'haploid-hwe',extra=['--min-hwe-p','.001'],expected=1))
    cases.append(cli_case(root,jar,'grch37-par',region='par',build='GRCh37'))
    for mode in ['efficient-score','firth-lrt','exact-conditional']:
        extra=['--model','glm','--family','binomial','--binary-test',mode]
        if mode=='exact-conditional':extra+=['--formula','y~<omics>','--exact-effect-max-parameters','0']
        cases.append(cli_case(root,jar,'binary-'+mode,binary=True,extra=extra))
        cases.append(cli_case(root,jar,'binary-scaled-'+mode,binary=True,scaled=True,extra=[*extra,'--haploid-source-scale','diploid']))
        direct={r['id']:r for r in rows(root/('binary-'+mode)/'result.tsv')};scaled={r['id']:r for r in rows(root/('binary-scaled-'+mode)/'result.tsv')}
        for marker in ['x20','xflip20']:
            close(direct[marker]['p_value'],scaled[marker]['p_value'],mode+' converted P');close(direct[marker]['allele_number'],80,'binary physical AN')
        if mode=='exact-conditional':
            successes=sum(i%3==0 for i in range(20));cases_n=sum(i%3==0 for i in range(80));rv=stats.hypergeom(80,cases_n,20);support=np.arange(max(0,20+cases_n-80),min(20,cases_n)+1);pr=rv.pmf(support);p=pr[pr<=rv.pmf(successes)*(1+1e-7)].sum();close(direct['x20']['p_value'],p,'SciPy hypergeometric exact P')
    cases.append(study_case(root,jar));write(root/'verification.json',{'status':'passed','cases':cases,'network_requests':0,'llm_requests':0});print(json.dumps({'status':'passed','cases':len(cases),'root':str(root)}))
if __name__=='__main__':main()
