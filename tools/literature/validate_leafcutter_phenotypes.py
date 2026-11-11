# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare complete native phenotype/state/order families against unchanged original Python controls on three JVMs; no full-tool credit."""
import argparse,json,math,os,re,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux

RELATIVE_TOLERANCE=2e-14
ABSOLUTE_TOLERANCE=2e-14

def compare(expected,actual,path,errors,counts):
    if type(expected) is not type(actual):errors.append(dict(path=path,cause='type',expected=expected,actual=actual));return
    if isinstance(expected,dict):
        if set(expected)!=set(actual):errors.append(dict(path=path,cause='field-family',expected_keys=sorted(expected),actual_keys=sorted(actual)));return
        for k,v in expected.items():compare(v,actual[k],path+'/'+k,errors,counts)
    elif isinstance(expected,list):
        if len(expected)!=len(actual):errors.append(dict(path=path,cause='complete-length',expected_length=len(expected),actual_length=len(actual)));return
        for i,(left,right) in enumerate(zip(expected,actual)):compare(left,right,path+'/'+str(i),errors,counts)
    elif isinstance(expected,float):
        counts['floating_values']+=1;error=abs(expected-actual);counts['maximum_absolute_error']=max(counts['maximum_absolute_error'],error)
        if not (math.isfinite(expected) and math.isfinite(actual) and math.isclose(expected,actual,rel_tol=RELATIVE_TOLERANCE,abs_tol=ABSOLUTE_TOLERANCE)):errors.append(dict(path=path,cause='numeric',expected=expected,actual=actual,absolute_error=error))
    else:
        counts['exact_values']+=1
        if expected!=actual:errors.append(dict(path=path,cause='exact-value/state/order',expected=expected,actual=actual))

def audit_pca(source,pca):
    if pca is None:return dict(PCA_cases=0,PCA_equations=0,source_undefined_variance_ratios=0,randomized_PCA_cases=0)
    values=[row['inverse_normal_scores'] for row in source['kept']];n=len(values);m=len(source['samples']);k=len(pca['components']);assert pca['observations']==n and pca['features']==m and len(pca['mean'])==m and len(pca['scores'])==n;equations=0
    def close(a,b):assert math.isfinite(a) and math.isfinite(b) and math.isclose(a,b,rel_tol=2e-10,abs_tol=2e-10),(a,b)
    for j in range(m):close(sum(row[j] for row in values)/n,pca['mean'][j]);equations+=1
    for a,row in enumerate(pca['components']):
        assert len(row)==m
        for b,other in enumerate(pca['components']):close(sum(x*y for x,y in zip(row,other)),1.0 if a==b else 0.0);equations+=1
        for i,observation in enumerate(values):close(sum((observation[j]-pca['mean'][j])*row[j] for j in range(m)),pca['scores'][i][a]);equations+=1
        variance=pca['singular_values'][a]**2/(n-1);close(variance,pca['explained_variance'][a]);equations+=1
        if pca['solver']=='full':close(sum(s[a]**2 for s in pca['scores']),pca['singular_values'][a]**2);equations+=1
        else:assert pca['solver']=='randomized' and sum(s[a]**2 for s in pca['scores'])+1e-10>=pca['singular_values'][a]**2
    undefined=sum(isinstance(v,dict) and v==dict(state='NaN') for v in pca['explained_variance_ratio']);return dict(PCA_cases=1,PCA_equations=equations,source_undefined_variance_ratios=undefined,randomized_PCA_cases=int(pca['solver']=='randomized'))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_NATIVE_LEAFCUTTER_PHENOTYPE_COMPARISON_INCOMPLETE',implementation_credit=False,whole_goal_complete=False);(out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes());errors=[]
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};sources=dict(build['source_pins']);sources[Path(__file__).resolve().relative_to(ROOT).as_posix()]=pin(Path(__file__));assert all(pin(ROOT/n)==want for n,want in sources.items());reference=a.reference.resolve();report=json.loads((reference/'COMPLETED.json').read_bytes());assert report['status']=='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_PHENOTYPE_AND_PCA_CONTROLS_ONLY' and report['runtime_trees_unchanged'] is True and report['namespace_patched'] is False and report['analysis_installed_software'] is False and len(report['cases'])==57;cases=json.loads((reference/'cases.json').read_bytes())['cases'];env=dict(os.environ)
        for key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:env.pop(key,None)
        jvms={'windows21':[str(JAVA21/'java.exe')],'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','-u','JAVA_TOOL_OPTIONS','-u','_JAVA_OPTIONS','-u','JDK_JAVA_OPTIONS','/usr/bin/java']};checks={};versions={};pca_checks=dict(PCA_cases=0,PCA_equations=0,source_undefined_variance_ratios=0,randomized_PCA_cases=0)
        for case in cases:
            home=reference/case['id'];process=json.loads((home/'original.process.json').read_bytes());assert process['exit_code']==0 and process['owned_live_after_cleanup']==[];data=json.loads((home/'original-result.json').read_bytes());pca=json.loads((home/'original-PCA.json').read_bytes())
            for key,value in audit_pca(data,pca).items():pca_checks[key]+=value
        for name,java in jvms.items():
            version=subprocess.run([*java,'-version'],cwd=ROOT,env=env,capture_output=True,timeout=60);(out/(name+'.version.stdout')).write_bytes(version.stdout);(out/(name+'.version.stderr')).write_bytes(version.stderr);versions[name]=(version.stdout+version.stderr).decode('utf8');assert version.returncode==0 and re.search(r'version "'+('25' if name=='windows25' else '21')+r'(?:\.|\")',versions[name]);destination=out/name;command=[*java,'-Xmx2g','-cp',linux(jar) if name=='linux21' else str(jar),'org.jlinalg.cli.LeafCutterPhenotypesMatrix',linux(reference/'cases.json') if name=='linux21' else str(reference/'cases.json'),linux(destination) if name=='linux21' else str(destination)]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Actual native phenotype JAR failed '+name;native=json.loads((destination/'COMPLETED.json').read_bytes());assert native['status']=='PASS_NATIVE_LEAFCUTTER_PHENOTYPE_COMPONENT_ONLY' and len(native['cases'])==57 and len(set(native['expected_native_rejections']))==18;count=dict(cases=57,source_rows=0,retained_rows=0,floating_values=0,exact_values=0,maximum_absolute_error=0.0,expected_native_rejections=18)
            for case in cases:
                expected=json.loads((reference/case['id']/'original-result.json').read_bytes());actual=json.loads((destination/(case['id']+'.json')).read_bytes());compare(expected,{k:v for k,v in actual.items() if k!='warnings'},name+'/'+case['id'],errors,count);count['source_rows']+=len(case['rows']);count['retained_rows']+=len(actual['kept']);assert actual['warnings'] and 'PCA' in actual['warnings'][2]
            checks[name]=count;print(name,'compared all57 complete original phenotype families',flush=True)
        save(out/'complete-mismatches.json',errors);assert not errors,'Complete original/native phenotype discrepancy; inspect every retained mismatch';assert all(pin(ROOT/n)==want for n,want in sources.items()) and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged'];state.update(status='PASS_COMPLETE_ORIGINAL_NATIVE_LEAFCUTTER_PHENOTYPES_THREE_JVM_COMPONENT_ONLY',artifact=build['artifact'],source_pins=sources,original_reference=dict(path=reference.relative_to(ROOT).as_posix(),**pin(reference/'COMPLETED.json')),checks=checks,source_PCA_audit=pca_checks,jvm_versions=versions,relative_tolerance=RELATIVE_TOLERANCE,absolute_tolerance=ABSOLUTE_TOLERANCE,canonical_executable_unchanged=build['canonical_executable_unchanged'])
    except Exception as failure:state['error']=repr(failure);save(out/'complete-mismatches.json',errors);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    action='Required action: no full LeafCutter workflow or partial native comparison is admitted. Preserve all source/native matrices, count/ID states, original PCA/warnings and failed receipts. Correct the named numeric/state/order/runtime/process/capacity cause and retry NEW paths; never omit a case/field, change source counts or re-pin a frozen scientific hash. Original zero-variance/undefined PCA diagnostics remain explicit, not repaired. R/Stan differential fitting remains separate.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Complete preprocessing comparison only; full named tool remains unadmitted.')+'\n\n'+action+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact complete comparison checks')
    raise SystemExit(main())
