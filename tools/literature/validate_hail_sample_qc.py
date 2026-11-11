# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every original Hail sample-QC output/state against an actual native candidate on three JVMs."""
import argparse,json,math,os,re,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux

RELATIVE_TOLERANCE=2e-12
ABSOLUTE_TOLERANCE=2e-12

def compare(expected,actual,path,errors,counts):
    if isinstance(expected,dict):
        if not isinstance(actual,dict) or set(expected)!=set(actual):errors.append(dict(path=path,expected=expected,actual=actual,reason='complete field set/type differs'));return
        if 'state' in expected:
            counts['numeric_states']+=1
            if expected['state']!=actual['state']:errors.append(dict(path=path,expected=expected,actual=actual,reason='missing/nonfinite/finite state differs'));return
            if expected['state']=='value':
                e,a=float(expected['text']),float(actual['text'])
                if not math.isfinite(e) or not math.isfinite(a) or not math.isclose(e,a,rel_tol=RELATIVE_TOLERANCE,abs_tol=ABSOLUTE_TOLERANCE):errors.append(dict(path=path,expected=expected,actual=actual,reason='complete finite value differs'))
            elif expected!=actual:errors.append(dict(path=path,expected=expected,actual=actual,reason='complete source nonfinite/missing representation differs'))
        else:
            for key in expected:compare(expected[key],actual[key],path+'/'+key,errors,counts)
    elif isinstance(expected,list):
        if not isinstance(actual,list) or len(expected)!=len(actual):errors.append(dict(path=path,expected_count=len(expected),actual=actual,reason='complete row/column cardinality differs'));return
        for i,(e,a) in enumerate(zip(expected,actual)):compare(e,a,path+'/'+str(i),errors,counts)
    else:
        counts['exact_scalar_values']+=1
        if type(expected)!=type(actual) or expected!=actual:errors.append(dict(path=path,expected=expected,actual=actual,reason='exact source scalar differs'))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_NATIVE_HAIL_QC_VALIDATION_INCOMPLETE',implementation_credit=False,full_Hail_workflow_admitted=False,whole_goal_complete=False);sources={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))};state['source_pins']=dict(sources);(out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes());all_errors=[];canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());reference=json.loads((a.reference/'COMPLETED.json').read_bytes());assert build['status']=='compiled-native-Hail-sample-QC-not-original-runtime-validation' and reference['status']=='PASS_COMPLETE_ORIGINAL_HAIL_SAMPLE_QC_AND_ALLELE_TYPES_ONLY';jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};fixtures=json.loads((a.reference/'cases.json').read_bytes());assert len(fixtures['cases'])==12 and len(fixtures['allele_pairs'])==56;assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());assert all(pin(ROOT/n)==v for n,v in reference['source_pins'].items());sources.update(build['source_pins']);sources.update(reference['source_pins']);reference_pins={f.relative_to(a.reference).as_posix():pin(f) for f in a.reference.rglob('*') if f.is_file() and (f.name in ['cases.json','COMPLETED.json','original-allele-types.json','original.process.json','original-result.json','original-schema.txt'])};save(out/'reference-file-pins.json',reference_pins)
        runtimes={'windows21':[str(JAVA21/'java.exe')],'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','-u','JAVA_TOOL_OPTIONS','-u','_JAVA_OPTIONS','-u','JDK_JAVA_OPTIONS','/usr/bin/java']};environment=dict(os.environ)
        for key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:environment.pop(key,None)
        totals={};versions={}
        for name,runtime in runtimes.items():
            version=subprocess.run([*runtime,'-version'],cwd=ROOT,env=environment,capture_output=True,timeout=60,creationflags=subprocess.CREATE_NO_WINDOW);(out/(name+'.version.stdout')).write_bytes(version.stdout);(out/(name+'.version.stderr')).write_bytes(version.stderr);version_text=(version.stdout+version.stderr).decode('utf8');assert version.returncode==0 and re.search(r'version "'+('25' if name=='windows25' else '21')+r'(?:\.|\")',version_text),'Unexpected JVM release spelling: '+version_text;versions[name]=version_text
            paths=[linux(jar),linux(a.reference.resolve()/'cases.json'),linux(out/name)] if name=='linux21' else [str(jar),str(a.reference.resolve()/'cases.json'),str(out/name)];command=[*runtime,'-Xmx1g','-cp',paths[0],'org.jlinalg.cli.HailSampleQcMatrix',paths[1],paths[2],paths[0]]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=environment,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=180,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Actual native candidate failed: '+name;report=json.loads((out/name/'COMPLETED.json').read_bytes());assert report['status']=='PASS_COMPLETE_NATIVE_SAMPLE_QC_CANDIDATE_ONLY' and report['cases']==12 and report['allele_pairs']==56 and len(report['expected_native_rejections'])==9;counts=dict(numeric_states=0,exact_scalar_values=0,cases=12,sample_outputs=0,expected_native_rejections=9)
            for case in fixtures['cases']:
                original=json.loads((a.reference/case['id']/'original-result.json').read_bytes());native=json.loads((out/name/(case['id']+'.json')).read_bytes());counts['sample_outputs']+=len(original);compare(original,native,name+'/'+case['id'],all_errors,counts)
            compare(json.loads((a.reference/'original-allele-types.json').read_bytes()),json.loads((out/name/'allele-types.json').read_bytes()),name+'/allele-types',all_errors,counts);totals[name]=counts
        save(out/'complete-mismatches.json',all_errors);assert not all_errors,str(len(all_errors))+' complete original/native output mismatches; inspect complete-mismatches.json';assert all(pin(ROOT/n)==v for n,v in sources.items());assert all(pin(a.reference/n)==v for n,v in reference_pins.items());assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_COMPLETE_ORIGINAL_NATIVE_HAIL_SAMPLE_QC_THREE_JVM_COMPONENT_ONLY',artifact=build['artifact'],source_pins=sources,reference_path=a.reference.resolve().relative_to(ROOT).as_posix(),reference_file_pins=reference_pins,checks=totals,jvm_versions=versions,relative_tolerance=RELATIVE_TOLERANCE,absolute_tolerance=ABSOLUTE_TOLERANCE,canonical_executable_unchanged=canonical);print(state['status'],totals)
    except Exception as error:
        state.update(error=repr(error),required_action='No partial batch or Hail tool integration is admitted. Preserve all inputs, original/native outputs and failures; inspect complete-mismatches.json and JVM stderr. Correct the named discrepancy against the unchanged original source and repeat into NEW build/validation paths.');(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');save(out/'complete-mismatches.json',all_errors);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state['required_action']+'\n\n'+repr(error)+'\n',encoding='utf8');print(state['status'],repr(error))
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/native comparisons')
    raise SystemExit(main())
