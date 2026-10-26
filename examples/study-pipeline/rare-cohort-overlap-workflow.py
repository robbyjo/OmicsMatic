"""Offline synthetic rare-set overlap acceptance: run NEW_DIR --jar JAR; verify DIR --jar PINNED_JAR.

Independently reconstructs full source BY families, primary mask/test memberships,
frozen all-test validation families and exact exclusive intersections. Requires PyYAML.
No real scientific calibration, literature appraisal or AI requests are performed.
"""
import argparse,csv,hashlib,importlib.util,json,math,pathlib,shutil,subprocess
import yaml
MODES=('four-way','three-way','two-way','union-coverage','intersection-coverage','reduced','multi-discovery','odd-cohort','signed','mixed','unsigned-direction','work-limit','provided','partial-membership','absent-cohort','native')
WAIT={'unsigned-direction','work-limit','partial-membership','absent-cohort'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,d):p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def table(p,data):
    with p.open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,list(data[0]),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(data)
def cli(jar,*args):
    p=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True,encoding='utf-8');assert p.returncode in (0,3),p.stdout+p.stderr
def operations(p):
    s=yaml.safe_load((p/'state/project-status.yaml').read_text(encoding='utf-8'));return [(f.parent,yaml.safe_load(f.read_text(encoding='utf-8'))) for f in sorted((p/'runs'/s['run_id']/'operations').glob('*/manifest.yaml'))]
def one(p,kind):
    result=[(f,m) for f,m in operations(p) if m['operation_type']==kind];assert len(result)==1,(p,kind,len(result));return result[0]
def adjust(values,method='BY'):
    if method=='NONE':return dict(values)
    order=sorted(values,key=lambda k:(values[k],k));h=sum(1/i for i in range(1,len(order)+1)) if method=='BY' else 1;q=1;out={}
    for rank in range(len(order),0,-1):
        key=order[rank-1];q=min(q,values[key]*len(order)*h/rank);out[key]=q
    return out
def identity(r):return (r['feature_id'],r['mask_id'],r['test'])
def finite(v):
    try:return math.isfinite(float(v))
    except ValueError:return False
def acat(values):
    statistic=math.fsum(1/math.tan(p*math.pi) for p in values)/len(values)
    return math.atan2(1,statistic)/math.pi if statistic>=0 else 1-math.atan2(1,-statistic)/math.pi
def generate(root,mode):
    p=root/mode
    if mode=='native':
        spec=importlib.util.spec_from_file_location('binary_fixture',root/'software/binary-rare-variant-workflow.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);module.generate(root,'native');study=json.loads((p/'study.json').read_text());a=study['hypotheses'][0]['analysis'];a['rare_variants']['followup']={'enabled':False}
    elif mode in ('provided','partial-membership','absent-cohort'):
        p.mkdir();folder,_=one(root/'four-way','rare_cohort_overlap');data=rows(folder/'cohort-overlap-membership.tsv')
        if mode=='partial-membership':data=data[:-1]
        if mode=='absent-cohort':data=[r for r in data if r['set_id']!='D']
        table(p/'provided.tsv',data);a={'mode':'manual','goals':['rare_cohort_overlap'],'rare_variants':{'enabled':False},'supplied_results':[{'id':'provided','kind':'rare_cohort_overlap','files':{'results':'provided.tsv'},'scope':{'datasets':list('ABCD')},'method':{'name':'Externally assessed synthetic rare-set membership'},'provenance':{'source_membership_sha256':sha(p/'provided.tsv')}}]};study={'schema_version':1,'project':{'id':'rare-overlap-'+mode,'title':'Synthetic supplied rare-set overlap'},'datasets':[],'hypotheses':[{'id':'H','description':'Reuse selection without refitting','analysis':a}]}
    else:
        p.mkdir();k=2 if mode=='two-way' else 3 if mode in ('three-way','signed','unsigned-direction') else 4;ids=list('ABCD'[:k]);primary='burden' if mode=='signed' else 'acat-o';datasets=[]
        for j,id_value in enumerate(ids):
            data=[]
            for n in range(16):
                for mask in ('lof','missense'):
                    for test in (primary,'skat'):
                        selected=bool(n&(1<<(k-j-1)));selected=not selected if mask=='missense' else selected
                        if mode=='odd-cohort':selected=(n==0 and mask=='lof') if id_value==ids[-1] else (selected and not(n==0 and mask=='lof'))
                        probability=(1e-6 if selected else .9) if test==primary else (1e-8 if n==0 and mask=='lof' else .9)
                        if mode in ('union-coverage','intersection-coverage') and id_value==ids[-1] and n==15 and mask=='lof' and test==primary:probability='NA'
                        record={'feature_id':f'f{n:02}','mask_id':mask,'test':test,'p_value':str(probability)}
                        if mode=='signed' and test==primary:record.update(beta='-1' if id_value==ids[-1] else '1',se='.1',result_type='signed_burden')
                        elif mode=='signed':record.update(beta='NA',se='NA',result_type='unsigned_set')
                        data.append(record)
            table(p/(id_value+'.tsv'),data);datasets.append({'id':id_value,'cohort':'cohort-'+id_value,'type':'gwas','stage':'set_association_results','genome_build':'GRCh38','files':{'results':id_value+'.tsv'},'metadata':{'complete_testing_family':True,'set_definition_id':'synthetic-masks-v1','trait_id':'synthetic-trait'}})
        a={'mode':'manual','goals':['rare_variant_analysis','impact_assessment'],'multiple_testing':{'method':'BY','alpha':.05},'rare_variants':{'tests':[primary,'skat'],'primary_test':primary,'unsigned_pooling':'acat','followup':{'enabled':False}},'cohort_overlap':{}}
        if mode=='union-coverage':a['cohort_overlap']['universe']='union'
        if mode=='reduced':a['cohort_overlap']['validation_family']='discovery_subset'
        if mode=='unsigned-direction':a['cohort_overlap']['direction']='require_discovery'
        if mode=='work-limit':a['cohort_overlap']['maximum_features']=1
        discovery=ids[:2] if mode=='multi-discovery' else ids[:1];study={'schema_version':1,'project':{'id':'rare-overlap-'+mode,'title':'Synthetic rare-set cohort diagnostics'},'datasets':datasets,'hypotheses':[{'id':'H','description':'Software and provenance verification only','inputs':{'discovery':discovery,'replication':[i for i in ids if i not in discovery]},'analysis':a}]}
        if mode=='mixed':
            for label,role in [('R1','discovery'),('R2','replication')]:
                table(p/(label+'.tsv'),[{'feature_id':'f01','beta':'.1','se':'.1','p_value':'.001'},{'feature_id':'f02','beta':'.2','se':'.1','p_value':'.4'}]);datasets.append({'id':label,'cohort':'cohort-'+label,'type':'generic','stage':'association_results','files':{'results':label+'.tsv'}});study['hypotheses'][0]['inputs'][role].append(label)
    study['project'].setdefault('description','Synthetic software and provenance acceptance only; no biological conclusions.');study['hypotheses'][0].setdefault('outcome','synthetic-trait');study['resources']={'network':'offline','downloads':'deny'};study['assistance']={'enabled':False};write(p/'study.json',study);write(p/'input-sha256.json',{str(f.relative_to(p)):sha(f) for f in p.rglob('*') if f.is_file() and f.name!='input-sha256.json'})
def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text());assert sha(jar)==software['jar_sha256'];cases=[];seals={};cells=0
    for mode in MODES:
        p=root/mode;study=json.loads((p/'study.json').read_text());a=study['hypotheses'][0]['analysis'];folder,m=one(p,'rare_cohort_overlap');assert m['state']==('waiting_for_input' if mode in WAIT else 'complete'),(mode,m['summary'])
        if mode not in WAIT:assert m.get('reused_from_cache') is True
        for name,digest in json.loads((p/'input-sha256.json').read_text()).items():assert sha(p/name)==digest
        for op,meta in operations(p):
            for name,digest in meta.get('output_sha256',{}).items():assert sha(op/name)==digest;seals[str((op/name).relative_to(root))]=digest
        if mode in WAIT:assert not (folder/'cohort-overlap-upset.png').exists()
        elif mode=='provided':
            assert m['details']['analysis_executed_by_jlinalg'] is False;actual=rows(folder/'cohort-overlap-membership.tsv');assert actual==rows(p/'provided.tsv');assert not any(meta['operation_type']=='rare_variant_analysis' for _,meta in operations(p));assert len(rows(folder/'cohort-overlap-features.tsv'))==32
        else:
            producer,_=one(p,'rare_variant_analysis');raw=rows(producer/'rare-results.tsv');scoped={}
            for r in raw:scoped.setdefault(r['scope'],{})[identity(r)]=r
            ids=m['details']['set_order'];primary=m['details']['prespecified_test'];allkeys=set().union(*(set(scoped['cohort:'+i]) for i in ids));common=set.intersection(*({key for key,r in scoped['cohort:'+i].items() if finite(r['p_value'])} for i in ids));universe={key for key in (allkeys if a.get('cohort_overlap',{}).get('universe')=='union' else common) if key[2]==primary}
            source_probabilities={}
            if mode!='native':
                inputs={i:{identity(r):float(r['p_value']) for r in rows(p/(i+'.tsv')) if finite(r['p_value'])} for i in ids};d=[i for i in study['hypotheses'][0]['inputs']['discovery'] if i in ids]
                for scope,data in scoped.items():
                    subset=[scope.split(':',1)[1]] if scope.startswith('cohort:') else d if scope=='discovery' else ids
                    expected={key:(inputs[subset[0]][key] if len(subset)==1 else acat([inputs[i][key] for i in subset if key in inputs[i]])) for key in set().union(*(set(inputs[i]) for i in subset))}
                    actual={key:float(r['p_value']) for key,r in data.items() if finite(r['p_value'])};assert set(actual)==set(expected)
                    for key,value in expected.items():assert math.isclose(actual[key],value,rel_tol=5e-9,abs_tol=1e-14),(mode,scope,key,actual[key],value)
                    source_probabilities[scope]=expected
            source_q={}
            for scope,data in scoped.items():
                q=adjust(source_probabilities.get(scope,{key:float(r['p_value']) for key,r in data.items() if finite(r['p_value'])}));source_q[scope]=q
                for key,value in q.items():assert math.isclose(float(data[key]['adjusted_p']),value,rel_tol=1e-10,abs_tol=1e-300)
            discovery=[i for i in study['hypotheses'][0]['inputs']['discovery'] if i in ids];validation=[i for i in study['hypotheses'][0]['inputs']['replication'] if i in ids];ref='discovery' if len(discovery)>1 else 'cohort:'+discovery[0];frozen={key for key in common if source_q[ref].get(key,1)<.05};chosen_q={i:source_q['cohort:'+i] for i in ids}
            if mode=='reduced':
                for i in validation:chosen_q[i]=adjust({key:float(scoped['cohort:'+i][key]['p_value']) for key in frozen})
                assert any(key[2]!=primary for key in frozen)
            membership=rows(folder/'cohort-overlap-membership.tsv');assert len(membership)==len([key for key in allkeys if key[2]==primary])*len(ids);patterns={};selected={i:set() for i in ids}
            for r in membership:
                key=identity(r);id_value=r['set_id'];expected=key in universe and chosen_q[id_value].get(key,1)<.05;assert (r['selected']=='true')==expected;assert (r['in_universe']=='true')==(key in universe);q=chosen_q[id_value].get(key,float('nan'))
                if math.isfinite(q):assert math.isclose(float(r['adjusted_p']),q,rel_tol=1e-10,abs_tol=1e-300)
                else:assert not finite(r['adjusted_p'])
                original=source_q['cohort:'+id_value].get(key,float('nan'))
                if math.isfinite(original):assert math.isclose(float(r['source_adjusted_p']),original,rel_tol=1e-10,abs_tol=1e-300)
                assert int(r['source_testing_denominator'])==len(source_q['cohort:'+id_value]);cells+=1
                if expected:selected[id_value].add(key)
                if mode!='signed':assert r['beta']=='NA' and r['same_sign_reference']=='unavailable'
            for key in universe:
                signature=''.join('1' if key in selected[i] else '0' for i in ids);patterns[signature]=patterns.get(signature,0)+1
            assert {r['selected_pattern']:int(r['features']) for r in rows(folder/'cohort-overlap-patterns.tsv')}==patterns
            if mode=='odd-cohort':assert any(r['dataset']==ids[-1] and r['alert'] for r in m['details']['zero_overlap_alerts'])
            if any(selected.values()):assert (folder/'cohort-overlap-upset.pdf').stat().st_size>1000
            if len(ids)<=3 and any(selected.values()):assert (folder/'cohort-overlap-venn.png').stat().st_size>1000
            inv,_=one(p,'finding_inventory');records=[json.loads(l) for l in (inv/'finding-inventory.jsonl').read_text().splitlines()];contexts=[o for f in records for o in f['observations'] if o.get('kind')=='rare_cohort_overlap_context'];assert len(contexts)==len(membership);assert all('mask_id' in o and 'test' in o and 'source_testing_denominator' in o and o['primary'] is False for o in contexts)
            if mode=='mixed':assert set(one(p,'cohort_overlap')[1]['details']['set_order'])=={'R1','R2'} and not {'R1','R2'}&set(ids)
        cli(jar,'verify','--project',p);cases.append({'case':mode,'state':m['state']})
    report={'passed':True,'cases':cases,'jar_sha256':sha(jar),'verifier_sha256':software['verifier_sha256'],'independently_checked_membership_cells':cells,'sealed_outputs':len(seals),'source_joint_families_preserved':True,'missing_cohort_and_cells_not_negative':True,'primary_analysis_unchanged':True,'scientific_calibration':False,'external_requests_attempted':False,'full_pipeline_conformance':False}
    for name,data in [('verification.json',report),('sealed-output-sha256.json',seals)]:
        if (root/name).exists():assert json.loads((root/name).read_text())==data
        else:write(root/name,data)
    print(json.dumps(report))
if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('action',choices=['run','verify']);ap.add_argument('directory',type=pathlib.Path);ap.add_argument('--jar',type=pathlib.Path,required=True);a=ap.parse_args();root=a.directory.resolve();jar=a.jar.resolve()
    if a.action=='run':
        root.mkdir(parents=True,exist_ok=False);software=root/'software';software.mkdir();shutil.copyfile(jar,software/jar.name);jar=software/jar.name;shutil.copyfile(pathlib.Path(__file__),software/'rare-cohort-overlap-workflow.py');shutil.copyfile(pathlib.Path(__file__).with_name('binary-rare-variant-workflow.py'),software/'binary-rare-variant-workflow.py');write(root/'execution-software.json',{'jar':str(jar.relative_to(root)),'jar_sha256':sha(jar),'verifier_sha256':sha(software/'rare-cohort-overlap-workflow.py'),'native_generator_sha256':sha(software/'binary-rare-variant-workflow.py'),'pinned_before_execution':True})
        for mode in MODES:
            generate(root,mode)
            for _ in range(2):cli(jar,'auto',root/mode/'study.json','--offline','--actor-type','external','--actor-id','synthetic-rare-overlap')
    verify(root,jar)
