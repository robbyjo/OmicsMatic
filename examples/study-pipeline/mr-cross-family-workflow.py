"""Synthetic cross-phenotype primary MR acceptance; complete/missing/native/supplied sources.

python mr-cross-family-workflow.py --jar JAR --root NEW_DIRECTORY
Independent SciPy BH/BY calculations include declared non-estimable hypotheses.
No network/LLM requests; synthetic source approvals are not scientific attestations.
"""
from pathlib import Path
import argparse,copy,csv,hashlib,importlib.util,json,shutil,subprocess,sys
import numpy as np
import scipy
from scipy import stats
import yaml

MODES=['native-BY','native-BH','native-bonferroni','native-NONE','missing-native-source','supplied-complete','supplied-partial','supplied-denominator','inventory-context','zero-eligible','all-supplied-no-raw']
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,d):p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8') as f:return list(csv.DictReader(f,delimiter='\t'))
def table(p,data):
    with p.open('w',encoding='utf-8',newline='') as f:w=csv.DictWriter(f,fieldnames=list(data[0]),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(data)
def run(jar,p,label,args,allowed=(0,3)):
    result=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],cwd=p,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8');(p/(label+'.log')).write_text(result.stdout,encoding='utf-8');write(p/(label+'-execution.json'),{'arguments':list(map(str,args)),'exit_code':result.returncode});assert result.returncode in allowed,(label,result.stdout[-2500:]);return result
def generate(root,mode,base):
    if mode=='all-supplied-no-raw':
        p=root/mode;p.mkdir();hs=[]
        for h,outcome,feature,pv in [('H1','BMI','g1',.03),('H2','waist','g2',.9)]:
            file=h+'-completed.tsv';table(p/file,[{'feature_id':feature,'beta':.5,'se':.5/stats.norm.isf(pv/2),'p_value':pv,'adjusted_p':pv,'status':'complete'}]);provided={'id':'completed-'+h,'kind':'mendelian_randomization','files':{'results':file},'scope':{'outcome':outcome,'exposure':'splicing','species':'human','modality':'transcriptomics','genome_build':'GRCh38'},'method':{'name':'Synthetic previously completed MR','parameters':{'primary_estimator':'ivw-fixed'}},'family':{'id':h+'-source-family','coverage':'complete','tested_count':1,'adjustment':'BH','adjusted_p_from_full_family':True},'provenance':{'results_sha256':sha(p/file)}}
            hs.append({'id':h,'description':'Synthetic completed MR without original raw data','outcome':outcome,'inputs':{},'analysis':{'mode':'manual','goals':['mendelian_randomization','impact_assessment'],'supplied_results':[provided]}})
        d={'schema_version':1,'project':{'id':mode,'title':'Synthetic source reuse','description':'No individual or original association data','mr_testing_families':[{'id':'all-traits','hypotheses':['H1','H2'],'rationale':'Synthetic registered primary MR family'}]},'resources':{'network':'offline','downloads':'deny'},'datasets':[],'hypotheses':hs};write(p/'study.yaml',d);write(p/'stable-inputs.json',{f.name:sha(f) for f in p.iterdir() if f.is_file()});return p
    p=root/mode;base.generate(p,False);d=json.loads((p/'study.yaml').read_text());h2=copy.deepcopy(d['hypotheses'][0]);h2.update(id='H2',outcome='waist');h2['inputs']={'discovery':['b']};dataset=copy.deepcopy(d['datasets'][0]);dataset.update(id='b',cohort='synthetic-waist');dataset['files']['results']='results2.tsv';shutil.copyfile(p/'results.tsv',p/'results2.tsv')
    for i in range(1,5):
        y=rows(p/f'y{i}.tsv');
        for r in y:r['beta']=str(float(r['beta'])*(.2 if i<3 else 2))
        table(p/f'waist-y{i}.tsv',y);dataset['files'][f'outcome{i}']=f'waist-y{i}.tsv'
    for pair in h2['analysis']['bioinformatics']['genetic_pairs']:
        pair['id']='waist-'+pair['id']
        for role in ['exposure','outcome','ld']:pair[role]['dataset_id']='b';pair[role]['source_id']='waist-'+pair[role]['source_id']
        pair['outcome']['trait']='waist'
    d['datasets'].append(dataset);d['hypotheses'].append(h2);method=mode.removeprefix('native-') if mode.startswith('native-') else 'BY'
    d['project']['mr_testing_families']=[{'id':'all-traits','hypotheses':['H1','H2'],'adjustment':method,'rationale':'Synthetic prespecified primary MR tests across two phenotypes; no independence or causal-validity claim.'}]
    if mode=='missing-native-source':h2['analysis']['bioinformatics']['genetic_pairs']=[x for x in h2['analysis']['bioinformatics']['genetic_pairs'] if x['feature_id']!='g4']
    if mode=='zero-eligible':
        data=rows(p/'results2.tsv')
        for row in data:row['p_value']='.9'
        table(p/'results2.tsv',data);h2['analysis']['bioinformatics']['genetic_pairs']=[]
    if mode.startswith('supplied-'):
        h2['analysis']['bioinformatics']['genetic_pairs']=[]
        external=[{'feature_id':'g1','beta':.4,'se':.4/stats.norm.isf(.03/2),'p_value':.03,'adjusted_p':.06,'status':'complete'},{'feature_id':'g2','beta':.01,'se':.01/stats.norm.isf(.9/2),'p_value':.9,'adjusted_p':.9,'status':'complete'}];table(p/'supplied-mr.tsv',external)
        h2['analysis']['supplied_results']=[{'id':'completed-waist-mr','kind':'mendelian_randomization','files':{'results':'supplied-mr.tsv'},'scope':{'outcome':'waist','exposure':'splicing','ancestry':'European','genome_build':'GRCh38','species':'human','modality':'transcriptomics'},'method':{'name':'Synthetic supplied primary MR','parameters':{'primary_estimator':'ivw-fixed'}},'family':{'id':'waist-source-family','coverage':'selected_only' if mode=='supplied-partial' else 'complete','tested_count':20 if mode=='supplied-denominator' else 2,'adjustment':'BH','adjusted_p_from_full_family':True},'provenance':{'results_sha256':sha(p/'supplied-mr.tsv')},'limitations':['Synthetic software test; no scientific appraisal.']}]
    if mode=='inventory-context':
        for h in d['hypotheses']:h['analysis']['goals'].append('impact_assessment')
    write(p/'study.yaml',d);write(p/'stable-inputs.json',{f.name:sha(f) for f in p.iterdir() if f.is_file() and f.name not in ['stable-inputs.json','fixture-input-sha256.json']});return p
def artifacts(p):
    state=yaml.safe_load((p/'state/project-status.yaml').read_text(encoding='utf-8'));return [(f,yaml.safe_load(f.read_text(encoding='utf-8'))) for f in (p/'runs'/state['run_id']).rglob('manifest.yaml')]
def verify(p,mode):
    current=artifacts(p);fam=[(f,m) for f,m in current if m['operation_type']=='mr_family_adjustment'];assert len(fam)==1;path,m=fam[0];out=rows(path.parent/'mr-family-results.tsv');pending=mode in ['missing-native-source','supplied-partial','supplied-denominator'];assert m['state']==('waiting_for_input' if pending else 'complete'),m
    if pending:
        assert all(r['joint_adjusted_p']=='NA' and r['joint_significant_primary']=='false' for r in out)
    else:
        size=int(m['details']['family_size']);assert size==len(out);values=np.array([float(r['causal_p_value']) if r['disposition']!='not_estimable' else 1. for r in out]);method=m['details']['policy']['adjustment'];expected=stats.false_discovery_control(values,method=method.lower()) if method in ['BH','BY'] else np.minimum(1,values*size) if method=='bonferroni' else values
        for r,q in zip(out,expected):
            if r['disposition']=='not_estimable':assert r['joint_adjusted_p']=='NA';continue
            assert np.isclose(float(r['joint_adjusted_p']),q,rtol=1e-10,atol=1e-14),(mode,r,q)
        assert len({r['hypothesis'] for r in out})==(1 if mode=='zero-eligible' else 2)
    native_checks=0
    for f,source in current:
        if source['operation_type']!='mendelian_randomization' or (f.parent/'supplied-analysis.yaml').exists():continue
        for r in rows(f.parent/'mr-primary-results.tsv'):
            if r['disposition']!='primary_tested':continue
            feature=int(r['feature_id'][1:]);ys={r['variant_id']:r for r in rows(p/(f'waist-y{feature}.tsv' if source['hypothesis']=='H2' else f'y{feature}.tsv'))};aligned=[]
            for x in rows(p/f'x{feature}.tsv'):
                bx=float(x['beta']);y=ys[x['variant_id']]
                if float(x['p_value'])>5e-8 or (bx/float(x['se']))**2<10:continue
                sign=1 if x['effect_allele']==y['effect_allele'] else -1;aligned.append((bx,float(y['beta'])*sign,float(y['se'])))
            precision=sum(bx*bx/sy**2 for bx,by,sy in aligned);beta=sum(bx*by/sy**2 for bx,by,sy in aligned)/precision;se=1/np.sqrt(precision);pv=2*stats.norm.sf(abs(beta/se))
            for key,value in [('estimate',beta),('standard_error',se),('causal_p_value',pv)]:assert np.isclose(float(r[key]),value,rtol=1e-10,atol=1e-14),(mode,key,r,value);native_checks+=1
    for name,digest in json.loads((p/'stable-inputs.json').read_text()).items():assert sha(p/name)==digest
    if mode in ['inventory-context','all-supplied-no-raw']:
        ip,im=next((f,m) for f,m in current if m['operation_type']=='finding_inventory');assert im['state']=='complete';dossiers=[json.loads(line) for line in (ip.parent/'finding-inventory.jsonl').read_text().splitlines()];assert any(any(o.get('kind')=='cross_hypothesis_mr_testing' for o in d['observations']) for d in dossiers);assert 'cross_hypothesis_mr_families' in im['details']
        if mode=='all-supplied-no-raw':assert all(not d['candidate'] for d in dossiers) and any(d.get('source_primary_significant') for d in dossiers)
    for f,manifest in current:
        for name,digest in manifest['output_sha256'].items():assert sha(f.parent/name)==digest
    return {'case':mode,'state':m['state'],'rows':len(out),'joint_family_size':m['details']['family_size'],'independent_scipy_checked':not pending,'native_MR_value_checks':native_checks}
def main():
    a=argparse.ArgumentParser();a.add_argument('--jar',type=Path,required=True);a.add_argument('--root',type=Path,required=True);a.add_argument('--cases',nargs='+',choices=MODES);args=a.parse_args();root=args.root.resolve();root.mkdir();software=root/'software';software.mkdir();jar=software/args.jar.name;shutil.copyfile(args.jar,jar);shutil.copyfile(__file__,software/Path(__file__).name);base_path=Path(__file__).with_name('mr-testing-workflow.py');shutil.copyfile(base_path,software/base_path.name);spec=importlib.util.spec_from_file_location('base_mr',base_path);base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
    write(root/'execution-software.json',{'jar_sha256':sha(jar),'verifier_sha256':sha(Path(__file__)),'base_fixture_sha256':sha(base_path),'python':sys.version,'numpy':np.__version__,'scipy':scipy.__version__});results=[]
    for mode in args.cases or MODES:
        p=generate(root,mode,base);run(jar,p,'run',['auto',p/'study.yaml','--offline']);case=verify(p,mode);results.append(case);run(jar,p,'verify',['verify','--project',p],allowed=(0,));run(jar,p,'replay',['auto',p/'study.yaml','--offline'])
        for f,m in artifacts(p):
            # An incomplete MR producer resumes completed pairs and rewrites its
            # provenance. Its diagnostic consumers may correctly rerun.
            if m['state']!='complete' or m['operation_type'] in ['report','impact_assessment']:continue
            if case['state']=='waiting_for_input' and m['operation_type'] not in ['validate_harmonize','mendelian_randomization']:continue
            assert m.get('reused_from_cache'),(mode,m['operation_id'])
    write(root/'verification.json',{'status':'passed','cases':results,'llm_requests':0,'network_requests':0});print(json.dumps({'status':'passed','cases':len(results),'root':str(root)}))
if __name__=='__main__':main()
