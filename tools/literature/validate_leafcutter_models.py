# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently audit original retained objectives/effects/LRTs, complete filter states and three actual native JVM runs; no model fitting/full-tool credit."""
import argparse, json, math, os, re, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_mashr_native import JAVA21, linux

DEFAULTS=dict(shape=1.0001,rate=.0001,outlier_a=1.01,outlier_b=100.0)


def source_cases(reference):
    marker=json.loads((reference/'COMPLETED.json').read_bytes());assert marker['status']=='PASS_LOSSLESS_ORIGINAL_LEAFCUTTER_MODEL_CONTROL_BINDING_ONLY' and marker['runtime_unchanged'] and marker['library_unchanged'] and marker['reference_unchanged'] and marker['original_namespace_patched'] is False and marker['fits_refitted'] is False and marker['lossless_numeric_roundtrip'] is True
    request=json.loads((reference/'cases.json').read_bytes());records=[];expected=[];checks=dict(original_grid_points=0,retained_optimizer_fits=0,ANOVA_families=0,effect_introns=0,filtered_cluster_dispositions=0,source_cluster_rows=0,source_missing_table_fields=0)
    def objective(name,counts,design,parameters,priors,robust,value):
        assert math.isfinite(value);case=dict(id=name,kind='objective',counts=counts,design=design,parameters=parameters,priors=priors,robust=robust);records.append(case);expected.append(dict(id=name,kind='objective',objective=value))
    def fitted(name,row,counts,full,null,robust,effects):
        fit=row['fit'];n=len(counts);k=len(counts[0]);assert len(full)==len(null)==n and len(full[0])>len(null[0]) and all(len(r)==k for r in counts)
        for member,design in [('fit_null',null),('fit_full',full)]:
            current=fit[member];assert current['return_code']==0 and len(current['par']['beta_raw'])==len(design[0]);objective(name+'/'+member,counts,design,current['par'],DEFAULTS,robust,current['value']);checks['retained_optimizer_fits']+=1
        case=dict(id=name+'/anova',kind='anova',null_objective=fit['fit_null']['value'],full_objective=fit['fit_full']['value'],null_columns=len(null[0]),full_columns=len(full[0]),introns=k);records.append(case);expected.append(dict(id=case['id'],kind='anova',log_ratio=fit['loglr'],df=fit['df'],p=fit['lrtp']));checks['ANOVA_families']+=1
        assert isinstance(fit['refit_null_flag'],bool) and 0<=fit['lrtp']<=1
        case=dict(id=name+'/effects',kind='effects',parameters=fit['fit_full']['par']);records.append(case);expected.append(dict(id=case['id'],kind='effects',log_effects=[r['logef'] for r in effects],baseline=[r['baseline'] for r in effects],perturbed=[r['perturbed'] for r in effects],delta_psi=[r['deltapsi'] for r in effects]));checks['effect_introns']+=len(effects)
    for case in request['grids']:
        report=json.loads((reference/(case['id']+'.json')).read_bytes());assert report['id']==case['id'];data=report['data'];assert data['y']==case['counts'] and data['x']==case['design'] and len(report['points'])==len(case['points'])
        priors=dict(shape=case['shape'],rate=case['rate'],outlier_a=case['outlier_a'],outlier_b=case['outlier_b'])
        for point, literal in zip(report['points'],case['points']):
            assert point['id']==literal['id'] and point['jacobian_adjustment'] is False and point['parameters']['beta_scale']==literal['beta_scale'] and point['parameters']['conc']==literal['concentration']
            objective(case['id']+'/'+point['id'],case['counts'],case['design'],point['parameters'],priors,case['robust'],point['objective']);checks['original_grid_points']+=1
    for case in request['fits']:
        row=json.loads((reference/(case['id']+'.json')).read_bytes());assert row['counts']==case['counts'] and row['full']==case['full'] and row['null']==case['null'] and row['introns']==case['introns']
        assert [e['intron'] for e in row['effects']]==case['introns'];fitted(case['id'],row,case['counts'],case['full'],case['null'],case['robust'],row['effects'])
    for case in request['differential']:
        row=json.loads((reference/(case['id']+'.json')).read_bytes());assert row['counts']==case['counts'] and row['samples']==case['samples'] and row['introns']==case['introns'] and row['groups']==case['group'];table=row['cluster_table'];assert len(table)==len(row['results'])==len(case['expected_statuses'])
        assert [t['cluster'] for t in table]==list(row['results']); checks['source_cluster_rows']+=len(table)
        for cluster,res in row['results'].items():
            assert res['status']==case['expected_statuses'][cluster];item=next(t for t in table if t['cluster']==cluster);assert item['status']==res['status']
            if res['status']!='Success':
                assert all(item[k]=='NA' for k in ['loglr','df','p','p.adjust']);checks['source_missing_table_fields']+=4;checks['filtered_cluster_dispositions']+=1;continue
            positions=[i for i,name in enumerate(case['introns']) if name.split(':')[0]+':'+name.split(':')[3]==cluster]
            original=list(map(list,zip(*(case['counts'][i] for i in positions))));mask=[sum(r)>0 for r in original];filtered=[r for r,keep in zip(original,mask) if keep];intron_mask=[sum(v>0 for v in col)>=5 for col in zip(*filtered)];filtered=[[v for v,keep in zip(r,intron_mask) if keep] for r in filtered]
            introns=[case['introns'][i] for i,keep in zip(positions,intron_mask) if keep];assert res['counts']==filtered and res['sample_mask']==mask and res['intron_mask']==intron_mask and res['samples']==[s for s,keep in zip(case['samples'],mask) if keep] and res['introns']==introns
            effects=[e for e in row['effects'] if e['intron'] in introns];assert [e['intron'] for e in effects]==introns
            fitted(case['id']+'/'+cluster,res,filtered,res['full'],res['null'],False,effects)
        # Original BH adjustment omits undefined p-values, retaining their NA cells.
        successful=[t for t in table if t['status']=='Success'];ordered=sorted(successful,key=lambda t:t['p'],reverse=True);bound=1.0
        for rank,item in zip(range(len(ordered),0,-1),ordered):
            bound=min(bound,len(ordered)/rank*item['p']);assert math.isclose(item['p.adjust'],bound,rel_tol=2e-12,abs_tol=2e-12)
    assert len(records)==len(expected)==len({c['id'] for c in records});return records,expected,checks


def independent_posterior(case):
    par=case['parameters'];prior=case['priors'];conc=par['conc'];k=len(conc);beta=[[scale*(v-1/k) for v in row] for row,scale in zip(par['beta_raw'],par['beta_scale'])];value=sum((prior['shape']-1)*math.log(c)-prior['rate']*c for c in conc)
    if case['robust']:
        outlier=par['outlier_prob'];value+=(prior['outlier_a']-1)*math.log(outlier)+(prior['outlier_b']-1)*math.log1p(-outlier)
    for counts,design in zip(case['counts'],case['design']):
        eta=[sum(beta[p][j]*design[p] for p in range(len(design))) for j in range(k)];maximum=max(eta);s=[math.exp(v-maximum) for v in eta];total_s=sum(s);a=[c*v/total_s for c,v in zip(conc,s)];total=sum(a);n=sum(counts);ordinary=math.lgamma(total)-math.lgamma(total+n)+sum(math.lgamma(c+y)-math.lgamma(c) for c,y in zip(a,counts))
        if case['robust']:
            uniform=math.lgamma(k)-math.lgamma(k+n)+sum(math.lgamma(1+y) for y in counts);left=math.log1p(-outlier)+ordinary;right=math.log(outlier)+uniform;maximum=max(left,right);ordinary=maximum+math.log(math.exp(left-maximum)+math.exp(right-maximum))
        value+=ordinary
    return value


def compare(expected,actual,path,errors,counts):
    if isinstance(expected,dict):
        if not isinstance(actual,dict) or set(expected)!=set(actual):errors.append(dict(path=path,cause='complete-field-family'));return
        for key,value in expected.items():compare(value,actual[key],path+'/'+key,errors,counts)
    elif isinstance(expected,list):
        if not isinstance(actual,list) or len(expected)!=len(actual):errors.append(dict(path=path,cause='complete-length'));return
        for i,(left,right) in enumerate(zip(expected,actual)):compare(left,right,path+'/'+str(i),errors,counts)
    elif isinstance(expected,(int,float)) and not isinstance(expected,bool):
        counts['numeric_values']+=1
        if not isinstance(actual,(int,float)) or isinstance(actual,bool) or not math.isfinite(expected) or not math.isfinite(actual):errors.append(dict(path=path,cause='nonfinite/type'));return
        delta=abs(expected-actual);counts['maximum_absolute_error']=max(counts['maximum_absolute_error'],delta)
        if path.endswith('/df'):
            if expected!=actual or not isinstance(actual,int):errors.append(dict(path=path,cause='exact-degrees-of-freedom'))
        elif not math.isclose(expected,actual,rel_tol=2e-10,abs_tol=2e-8 if path.endswith('/objective') else 2e-11):errors.append(dict(path=path,cause='numeric',expected=expected,actual=actual,error=delta))
    else:
        counts['exact_values']+=1
        if type(expected) is not type(actual) or expected!=actual:errors.append(dict(path=path,cause='exact-value/type/order'))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_NATIVE_LEAFCUTTER_MODEL_AUDIT_INCOMPLETE',implementation_credit=False,whole_goal_complete=False);errors=[]
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};sources=dict(build['source_pins']);sources[Path(__file__).resolve().relative_to(ROOT).as_posix()]=pin(Path(__file__));assert all(pin(ROOT/name)==want for name,want in sources.items());cases,expected,original_checks=source_cases(a.reference);save(out/'cases.json',dict(cases=cases));save(out/'expected.json',dict(cases=expected));independent=[]
        for case,want in zip(cases,expected):
            if case['kind']=='objective':
                value=independent_posterior(case);assert math.isclose(value,want['objective'],rel_tol=2e-10,abs_tol=2e-8),(case['id'],value,want['objective']);independent.append(dict(id=case['id'],objective=value,absolute_error=abs(value-want['objective'])))
        save(out/'independent-Python-objectives.json',independent);env=dict(os.environ)
        for key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:env.pop(key,None)
        jvms=dict(windows21=[str(JAVA21/'java.exe')],windows25=['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],linux21=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','-u','JAVA_TOOL_OPTIONS','-u','_JAVA_OPTIONS','-u','JDK_JAVA_OPTIONS','/usr/bin/java']);checks={};versions={}
        for name,java in jvms.items():
            version=subprocess.run([*java,'-version'],cwd=ROOT,env=env,capture_output=True,timeout=60);(out/(name+'.version.stdout')).write_bytes(version.stdout);(out/(name+'.version.stderr')).write_bytes(version.stderr);versions[name]=(version.stdout+version.stderr).decode('utf8');assert version.returncode==0 and re.search(r'version "'+('25' if name=='windows25' else '21')+r'(?:\.|\")',versions[name]);destination=out/name
            command=[*java,'-Xmx2g','-cp',linux(jar) if name=='linux21' else str(jar),'org.jlinalg.cli.LeafCutterModelsMatrix',linux(out/'cases.json') if name=='linux21' else str(out/'cases.json'),linux(destination) if name=='linux21' else str(destination)]
            r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Actual native evaluator failed; inspect retained '+name+' stderr'
            report=json.loads((destination/'COMPLETED.json').read_bytes());assert report['status']=='PASS_NATIVE_LEAFCUTTER_MODEL_EVALUATIONS_ONLY' and report['cases']==len(cases) and len(set(report['expected_native_rejections']))==14 and report['negative_source_objective_difference_retained'] is True
            count=dict(cases=len(cases),numeric_values=0,exact_values=0,maximum_absolute_error=0.0,expected_native_rejections=14);compare(dict(cases=expected),json.loads((destination/'results.json').read_bytes()),name,errors,count);checks[name]=count;print(name,count,flush=True)
        save(out/'complete-mismatches.json',errors);assert not errors,'Complete original/native model mismatch';assert all(pin(ROOT/name)==want for name,want in sources.items()) and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged']
        state.update(status='PASS_COMPLETE_ORIGINAL_NATIVE_LEAFCUTTER_MODEL_AUDIT_THREE_JVM_ONLY',artifact=build['artifact'],source_pins=sources,checks=checks,original_checks=original_checks,independent_Python_objectives=len(independent),maximum_Python_objective_error=max(r['absolute_error'] for r in independent),jvm_versions=versions,reference_manifest=pin(a.reference/'COMPLETED.json'),canonical_executable_unchanged=build['canonical_executable_unchanged'])
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n');save(out/'complete-mismatches.json',errors)
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Evaluates retained supplied parameters only; no independent optimization, global-optimum claim, paper calibration or full LeafCutter admission.')+'\n\nRequired action: retain complete original/native objective/effect/state families and failures. Correct the named numerical, axis, type, state, optimizer, runtime or capacity cause and repeat NEW paths. Never admit a partial batch, repair original parameters or refresh frozen scientific hashes. Missing/forbidden software keeps dependent stages skipped.\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete scientific comparisons')
    raise SystemExit(main())
