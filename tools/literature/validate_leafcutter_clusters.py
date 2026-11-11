# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every native pooled/refined cluster/count/order against complete original scripts on Windows/Linux and audit read conservation. No full-tool credit."""
import argparse,json,os,re,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux

def read(path):return json.loads(path.read_bytes())
def clusters(text):
    out=[]
    for i,line in enumerate(text.splitlines(),1):
        parts=line.split();chrom,strand=parts[0].rsplit(':',1);entries=[]
        for part in parts[1:]:a,b,n=map(int,part.split(':'));entries.append(dict(chromosome=chrom,strand=strand,start=a,end=b,reads=n))
        out.append(dict(ordinal=i,axis=[chrom,strand],introns=entries))
    return out

def source_result(home):
    pooled=clusters((home/'leaf_pooled.decoded').read_text(encoding='utf8'));refined=clusters((home/'leaf_refined.decoded').read_text(encoding='utf8'));lines=(home/'leaf_perind.counts.gz.decoded').read_text(encoding='utf8').splitlines();samples=lines[0].split()[1:];rows=[]
    for line in lines[1:]:
        f=line.split();identifier=f[0];chrom,a,b,cluster=identifier.split(':');match=re.fullmatch(r'clu_(\d+)_(.+)',cluster);assert match;values=[tuple(map(int,v.split('/'))) for v in f[1:]];rows.append(dict(id=identifier,chromosome=chrom,strand=match[2],start=int(a),end=int(b),cluster=int(match[1]),numerators=[n for n,d in values],denominators=[d for n,d in values]));assert len(values)==len(samples)
    numerators=(home/'leaf_perind_numers.counts.gz.decoded').read_text(encoding='utf8').splitlines();assert numerators[0].split()==samples and len(numerators)==len(rows)+1
    for row,line in zip(rows,numerators[1:]):f=line.split();assert f[0]==row['id'] and [int(v) for v in f[1:]]==row['numerators']
    return dict(samples=samples,pooled=pooled,refined=refined,rows=rows)

def compare(expected,actual,where,errors):
    if type(expected) is not type(actual):errors.append(dict(path=where,expected=expected,actual=actual,cause='type'));return
    if isinstance(expected,dict):
        if set(expected)!=set(actual):errors.append(dict(path=where,expected_keys=sorted(expected),actual_keys=sorted(actual)));return
        for k,v in expected.items():compare(v,actual[k],where+'/'+k,errors)
    elif isinstance(expected,list):
        if len(expected)!=len(actual):errors.append(dict(path=where,expected_length=len(expected),actual_length=len(actual)));return
        for i,(left,right) in enumerate(zip(expected,actual)):compare(left,right,where+'/'+str(i),errors)
    elif expected!=actual:errors.append(dict(path=where,expected=expected,actual=actual,cause='exact-value-or-order'))

def audit_counts(case,native):
    by_sample=[];dispositions=[];op=case['options'];known={str(i) for i in range(1,23)}|{'X','Y'};known|={'chr'+x for x in known}
    for si,sample in enumerate(case['samples']):
        counts={}
        for ri,line in enumerate(sample['lines']):
            f=line.split();reason='POOLED'
            if int(f[9])>2:reason='SOURCE_EXCLUDES_EXTRA_BLOCKS'
            else:
                chrom,strand=f[0],f[5];left,right=map(int,f[10].split(','));a,b=int(f[1])+left,int(f[2])-right+1
                if strand=='?':reason='SOURCE_EXCLUDES_AMBIGUOUS_STRAND'
                elif op['check_chromosomes'] and chrom not in known:reason='SOURCE_EXCLUDES_NONCANONICAL_CHROMOSOME'
                elif b-a>op['maximum_intron_length']:reason='SOURCE_EXCLUDES_LONG_INTRON'
                if reason=='POOLED':key=(chrom,strand,a,b);counts[key]=counts.get(key,0)+int(f[4])
            dispositions.append(dict(sample=si,row=ri,original_line=line,disposition=reason))
        by_sample.append(counts)
    assert native['input_dispositions']==dispositions and native['samples']==[s['id'] for s in case['samples']];checks=0
    for cluster in native['refined']:
        for sample,counts in enumerate(by_sample):
            total=sum(counts.get((r['chromosome'],r['strand'],r['start'],r['end']),0) for r in cluster['introns'])
            for row in native['rows']:
                if row['cluster']==cluster['ordinal']:assert row['numerators'][sample]==counts.get((row['chromosome'],row['strand'],row['start'],row['end']),0) and row['denominators'][sample]==total;checks+=2
    return dict(original_source_rows=len(dispositions),independent_count_conservation_values=checks)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--windows-reference',required=True,type=Path);p.add_argument('--linux-reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_NATIVE_LEAFCUTTER_CLUSTER_COMPONENT_VALIDATION_INCOMPLETE',implementation_credit=False,whole_goal_complete=False);(out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        proof=read(a.build/'COMPLETED.json');jar=ROOT/proof['artifact']['path'];assert pin(jar)=={k:proof['artifact'][k] for k in ['bytes','sha256']};sources=dict(proof['source_pins']);sources[Path(__file__).resolve().relative_to(ROOT).as_posix()]=pin(Path(__file__));assert all(pin(ROOT/n)==v for n,v in sources.items());references=[a.windows_reference.resolve(),a.linux_reference.resolve()];reference_proofs=[read(r/'COMPLETED.json') for r in references];assert all(r['status']=='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_CLUSTER_CONTROLS_ONLY' and r['namespace_patched'] is False and r['analysis_installed_software'] is False and len(r['cases'])==88 and all(p['exit_code']==0 and p['owned_live_after_cleanup']==[] for p in r['processes']) for r in reference_proofs);fixture_raw=(references[0]/'cases.json').read_bytes();assert fixture_raw==(references[1]/'cases.json').read_bytes();fixture=read(references[0]/'cases.json');cases=fixture['cases'];errors=[];checks={};versions={};env=dict(os.environ)
        for key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:env.pop(key,None)
        jvms={'windows21':[str(JAVA21/'java.exe')],'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','-u','JAVA_TOOL_OPTIONS','-u','_JAVA_OPTIONS','-u','JDK_JAVA_OPTIONS','/usr/bin/java']}
        for name,java in jvms.items():
            version=subprocess.run([*java,'-version'],cwd=ROOT,env=env,capture_output=True,timeout=60);(out/(name+'.version.stdout')).write_bytes(version.stdout);(out/(name+'.version.stderr')).write_bytes(version.stderr);assert version.returncode==0;versions[name]=(version.stdout+version.stderr).decode('utf8');assert re.search(r'version "'+('25' if name=='windows25' else '21')+r'(?:\.|\")',versions[name]);destination=out/name;command=[*java,'-Xmx2g','-cp',linux(jar) if name=='linux21' else str(jar),'org.jlinalg.cli.LeafCutterClustersMatrix',linux(references[0]/'cases.json') if name=='linux21' else str(references[0]/'cases.json'),linux(destination) if name=='linux21' else str(destination)]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:run=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Actual native JAR failed '+name;report=read(destination/'COMPLETED.json');assert report['status']=='PASS_NATIVE_LEAFCUTTER_CLUSTER_COMPONENT_CASES_ONLY' and len(report['cases'])==88 and len(set(report['expected_native_rejections']))==17;counts=dict(cases=88,original_source_rows=0,independent_count_conservation_values=0,expected_native_rejections=17,retained_output_rows=0)
            for case in cases:
                actual=read(destination/(case['id']+'.json'));selected={k:actual[k] for k in ['samples','pooled','refined','rows']};expected=source_result(references[0]/case['id']);compare(expected,source_result(references[1]/case['id']),'original-platforms/'+case['id'],errors);compare(expected,selected,name+'/'+case['id'],errors);audit=audit_counts(case,actual)
                for key,value in audit.items():counts[key]+=value
                counts['retained_output_rows']+=len(actual['rows']);assert len(actual['warnings'])>=2 and all(isinstance(w,str) and w for w in actual['warnings'])
            checks[name]=counts;print(name,'compared complete88case original cluster/count families',flush=True)
        save(out/'complete-mismatches.json',errors);assert not errors,'Complete original/native field/order/type mismatch; inspect complete-mismatches.json';assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(jar)=={k:proof['artifact'][k] for k in ['bytes','sha256']} and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged'];state.update(status='PASS_COMPLETE_ORIGINAL_NATIVE_LEAFCUTTER_CLUSTER_COMPONENT_THREE_JVM_ONLY',source_pins=sources,artifact=proof['artifact'],checks=checks,jvm_versions=versions,original_reference_proofs={r.relative_to(ROOT).as_posix():pin(r/'COMPLETED.json') for r in references},canonical_executable_unchanged=proof['canonical_executable_unchanged'])
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    action='Required action: no full LeafCutter integration or partial native comparison is admitted. Keep every original/native field, raw input, source and failed receipt. Inspect the complete discrepancy or named JVM/input/capacity error, correct its cause and retry NEW paths. Never drop a failing intron/sample/case, replace counts or re-pin frozen scientific source. Differential splicing, phenotype normalization/PCA and junction extraction remain separate.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Complete clustering/counts component only; full workflow not admitted.')+'\n\n'+action+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete comparison checks')
    raise SystemExit(main())
