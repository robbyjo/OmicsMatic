# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit every original prepared matrix, permutation, bit and derived decision against actual Windows21/25 and Linux21 Java; exact numeric comparison, no named-tool credit."""
import argparse,json,math,os,re,struct,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux


def compare(expected,actual,path,errors,counts):
    if isinstance(expected,dict):
        if not isinstance(actual,dict) or set(actual)!=set(expected):errors.append(dict(path=path,cause='complete-field-family'));return
        for key,value in expected.items():compare(value,actual[key],path+'/'+key,errors,counts)
    elif isinstance(expected,list):
        if not isinstance(actual,list) or len(expected)!=len(actual):errors.append(dict(path=path,cause='complete-axis-or-family-length'));return
        for i,(left,right) in enumerate(zip(expected,actual,strict=True)):compare(left,right,path+'/'+str(i),errors,counts)
    elif isinstance(expected,(int,float)) and not isinstance(expected,bool):
        counts['numeric_values']+=1
        if not isinstance(actual,(int,float)) or isinstance(actual,bool) or not math.isfinite(expected) or not math.isfinite(actual) or expected!=actual:errors.append(dict(path=path,cause='exact-numeric',expected=expected,actual=actual))
    else:
        counts['exact_values']+=1
        if type(expected) is not type(actual) or expected!=actual:errors.append(dict(path=path,cause='exact-value-type-state-or-order',expected=expected,actual=actual))


def independent(request,results,reference):
    counts=dict(cases=0,cluster_cells=0,interaction_cells=0,permutation_cells=0,packed_bytes=0,pvalue_cells=0,rank_rows=0,deg_cells=0,encoded_collisions=0,nonfinite_states=0)
    def value(number):return float('nan') if number=='NaN' else float('inf') if number=='+Inf' else -float('inf') if number=='-Inf' else number
    def same(first,second):return first==second or math.isnan(first) and math.isnan(second)
    def significant(means,scores,cutoff,result):
        for mean,score,actual,rank in zip(means,scores,result['means'],result['ranks'],strict=True):
            expected=[float('nan') if (s>cutoff if cutoff else s==0) else value(m) for m,s in zip(mean,score,strict=True)]
            assert all(same(a,value(b)) for a,b in zip(expected,actual,strict=True))
            # Pandas/NumPy rounds the binary fraction after scaling by1000, ties to even.
            expected_rank=round(sum(not math.isnan(v) for v in expected)/len(expected)*1000)/1000
            assert expected_rank==rank;counts['rank_rows']+=1
    for case,result in zip(request['cases'],results['cases'],strict=True):
        assert case['id']==result['id'];counts['cases']+=1
        def rounded(v):
            if case['precision']=='float64':return v
            try:return struct.unpack('f',struct.pack('f',v))[0]
            except OverflowError:return math.copysign(float('inf'),v)
        features=case['proteins']+[c['id'] for c in case['complexes']];types=sorted(set(case['cell_types']));assert features==result['cluster_features'] and types==result['cell_types']
        row={p:i for i,p in enumerate(features)};col={p:i for i,p in enumerate(types)};original_means=[];original_percents=[]
        for expression in case['expression']:
            means=[];percents=[]
            for group in types:
                selected=[v for v,label in zip(expression,case['cell_types'],strict=True) if label==group];total=0.
                for v in selected:total=rounded(total+v)
                means.append(rounded(total/len(selected)));percents.append(sum(v>0 for v in selected)/len(selected));counts['cluster_cells']+=1
            original_means.append(means);original_percents.append(percents)
        for complex_ in case['complexes']:
            original_means.append([min(original_means[row[p]][i] for p in complex_['proteins']) for i in range(len(types))]);original_percents.append([min(original_percents[row[p]][i] for p in complex_['proteins']) for i in range(len(types))]);counts['cluster_cells']+=len(types)
        assert all(same(a,value(b)) for ra,rb in zip(original_means,result['cluster_means'],strict=True) for a,b in zip(ra,rb,strict=True)) and original_percents==result['cluster_percents'],'Independent raw prepared cluster sums/fractions disagree'
        groups={}
        for environment in case['microenvironments']:groups.setdefault(environment['environment'],[]).append(environment['cell_type'])
        if not groups:groups={'':types}
        pairs=list(dict.fromkeys((a,b) for group in groups.values() for a in group for b in group));assert [list(pair) for pair in pairs]==result['pairs'];columns=[a+case['separator']+b for a,b in pairs];assert columns==result['columns'];counts['encoded_collisions']+=len(columns)-len(set(columns))
        eligible=[]
        for interaction,mean in zip(case['interactions'],result['means'],strict=True):
            gates=[]
            for (a,b),actual in zip(pairs,mean,strict=True):
                x=original_means[row[interaction['first']]][col[a]];y=original_means[row[interaction['second']]][col[b]];expected=rounded((x>0)*(y>0)*rounded(x+y)/2)
                assert same(expected,value(actual));counts['interaction_cells']+=1;counts['nonfinite_states']+=not math.isfinite(expected)
                gates.append(int(original_percents[row[interaction['first']]][col[a]]>case['threshold'] and original_percents[row[interaction['second']]][col[b]]>case['threshold']))
            eligible.append(gates)
        assert eligible==result['eligible'];significant(result['means'],eligible,None,result['non_statistical'])
        hits=[[0]*len(pairs) for _ in case['interactions']]
        for means,packed in zip(result['permuted_means'],result['packed_greater'],strict=True):
            binary=[]
            for i,(left,right) in enumerate(zip(means,result['means'],strict=True)):
                for j,(a,b) in enumerate(zip(left,right,strict=True)):
                    greater=value(a)>value(b);binary.append(int(greater));hits[i][j]+=greater;counts['permutation_cells']+=1
            expected=[]
            for start in range(0,len(binary),8):
                byte=0
                for offset,bit in enumerate(binary[start:start+8]):byte|=bit<<(7-offset)
                expected.append(byte)
            assert expected==packed;counts['packed_bytes']+=len(packed)
        if result['permuted_means']:
            pvalues=[[1 if value(mean)==0 or gate==0 else hit/len(result['permuted_means']) for mean,gate,hit in zip(m,g,h,strict=True)] for m,g,h in zip(result['means'],eligible,hits,strict=True)];assert pvalues==result['pvalues'];counts['pvalue_cells']+=sum(map(len,pvalues));significant(result['means'],pvalues,case['pvalue_cutoff'],result['statistical'])
        else:assert result['pvalues'] is None and result['statistical'] is None
        if case['degs'] is not None:
            deg=result['degs'];deg_features=case['proteins']+sorted(c['id'] for c in case['complexes']);assert deg['features']==deg_features and deg['cell_types']==types;flags=[[int(any(m['protein']==p and m['cell_type']==t for m in case['degs'])) for t in types] for p in case['proteins']]
            for complex_ in sorted(case['complexes'],key=lambda c:c['id']):flags.append([max(flags[row[p]][i] for p in complex_['proteins']) for i in range(len(types))])
            assert flags==deg['flags'];deg_row={p:i for i,p in enumerate(deg_features)};relevant=[[gates[j]&int(flags[deg_row[i['first']]][col[a]]+flags[deg_row[i['second']]][col[b]]>0) for j,(a,b) in enumerate(pairs)] for i,gates in zip(case['interactions'],eligible,strict=True)];assert relevant==result['deg_relevant'];counts['deg_cells']+=sum(map(len,flags));significant(result['means'],relevant,None,result['deg_significant'])
        else:assert result['degs'] is None and result['deg_relevant'] is None and result['deg_significant'] is None
    proof=json.loads((reference/'complex-minima-deg-maxima-and-original-shuffle.rng-proof.json').read_bytes());assert proof['replay_identical'] and proof['patched_or_intercepted_functions'] is False and proof['original_state']==proof['replay_state'] and len(proof['all_original_packed_arrays'])==100
    failures=json.loads((reference/'original-known-failures.json').read_bytes());assert [(r['id'],r['type'],r['scientific_result_admitted']) for r in failures]==[('empty-composition-DEG','TypeError',False),('single-thread-one-iteration-progress-zero','ZeroDivisionError',False)];counts['known_original_failures']=2;counts['full_original_shuffle_iterations']=100;return counts


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_CELLPHONEDB_NATIVE_AUDIT_INCOMPLETE',implementation_credit=False,whole_goal_complete=False);errors=[];(out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes());save(out/'STARTED.json',dict(validator_pin=pin(Path(__file__)),build=str(a.build),reference=str(a.reference)))
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());assert build['status']=='PASS_COMPILED_CELLPHONEDB_COMPONENTS_ONLY_NOT_SCIENTIFIC_VALIDATION';jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};reference=json.loads((a.reference/'COMPLETED.json').read_bytes());assert reference['status']=='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_PREPARED_KERNEL_CONTROLS_ONLY' and reference['runtime_unchanged'] and reference['cases']==13
        sources=dict(build['source_pins']);sources[Path(__file__).resolve().relative_to(ROOT).as_posix()]=pin(Path(__file__));assert all(pin(ROOT/n)==want for n,want in sources.items());request=json.loads((a.reference/'native-input.json').read_bytes());expected=json.loads((a.reference/'original-results.json').read_bytes());assert pin(a.reference/'native-input.json')==reference['native_input'] and pin(a.reference/'original-results.json')==reference['results'];save(out/'cases.json',request);save(out/'expected.json',expected);original_checks=independent(request,expected,a.reference)
        env=dict(os.environ)
        for name in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:env.pop(name,None)
        jvms=dict(windows21=[str(JAVA21/'java.exe')],windows25=['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],linux21=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','-u','JAVA_TOOL_OPTIONS','-u','_JAVA_OPTIONS','-u','JDK_JAVA_OPTIONS','/usr/bin/java']);checks={};versions={}
        for name,java in jvms.items():
            version=subprocess.run([*java,'-version'],cwd=ROOT,env=env,capture_output=True,timeout=60);(out/(name+'.version.stdout')).write_bytes(version.stdout);(out/(name+'.version.stderr')).write_bytes(version.stderr);versions[name]=(version.stdout+version.stderr).decode('utf8');assert version.returncode==0 and re.search(r'version "'+('25' if name=='windows25' else '21')+r'(?:\.|\")',versions[name]);destination=out/name
            command=[*java,'-Xmx2g','-cp',linux(jar) if name=='linux21' else str(jar),'org.jlinalg.cli.CellPhoneDbStatisticsMatrix',linux(out/'cases.json') if name=='linux21' else str(out/'cases.json'),linux(destination) if name=='linux21' else str(destination)];result=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW);(out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=result.returncode));assert result.returncode==0,'Actual native controls failed; inspect complete '+name+' stderr'
            report=json.loads((destination/'COMPLETED.json').read_bytes());assert report['status']=='PASS_NATIVE_CELLPHONEDB_PREPARED_KERNELS_ONLY' and report['cases']==13 and len(set(report['expected_native_rejections']))==20 and report['defensive_copy_controls'];count=dict(cases=13,numeric_values=0,exact_values=0,native_rejections=20,operations=report['operations']);compare(expected,json.loads((destination/'results.json').read_bytes()),name,errors,count);checks[name]=count;print(name,count,flush=True)
        save(out/'complete-mismatches.json',errors);assert not errors,'Exact original/native numerical, axis, state or decision mismatch';assert all(pin(ROOT/n)==want for n,want in sources.items()) and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged'];state.update(status='PASS_EXACT_COMPLETE_ORIGINAL_NATIVE_CELLPHONEDB_PREPARED_KERNELS_THREE_JVMS_ONLY',artifact=build['artifact'],source_pins=sources,checks=checks,original_checks=original_checks,jvm_versions=versions,reference_manifest=pin(a.reference/'COMPLETED.json'),canonical_executable_unchanged=build['canonical_executable_unchanged'],exact_finite_numeric_comparison=True)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n');save(out/'complete-mismatches.json',errors)
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Complete prepared numerical component only; full named-tool workflow, database mapping and biological claims remain unvalidated.')+'\n\nSKIP3: unavailable/forbidden required dependencies skip dependent analysis. FAIL2: any mismatch admits no partial batch. Preserve original inputs and every failed attempt; correct the exact cause and retry NEW paths. No frozen scientific-source hash refresh, input repair, tolerance widening or source patch is permitted.\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original/native comparisons')
    raise SystemExit(main())
