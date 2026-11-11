# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every complete source value/axis/hash/set order/distance against the packaged native implementation on three actual JVMs."""
import argparse,json,struct,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def compare(actual,expected,at,counters):
    if isinstance(expected,float):
        assert isinstance(actual,(int,float)) and not isinstance(actual,bool) and struct.pack('!d',actual)==struct.pack('!d',expected),(at,actual,expected);counters['numeric']+=1
    elif isinstance(expected,int):assert type(actual) is int and actual==expected,(at,actual,expected);counters['exact']+=1
    elif isinstance(expected,list):
        assert isinstance(actual,list) and len(actual)==len(expected),(at,'complete shape',len(actual),len(expected))
        for i,(a,b) in enumerate(zip(actual,expected)):compare(a,b,at+'/'+str(i),counters)
    else:assert actual==expected,(at,actual,expected);counters['exact']+=1


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/spyrrow-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_SPYRROW_NATIVE_COMMUNICATION_VALIDATION',implementation_credit=False,whole_goal_complete=False,runs=[])
    try:
        built=json.loads((args.build/'COMPLETED.json').read_bytes());assert built['status']=='PASS_COMPILED_SPYRROW_COMMUNICATION_COMPONENT_ONLY';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};reference=ROOT/'build/literature-review/spyrrow-runtime/communication-original-1';original=json.loads((reference/'COMPLETED.json').read_bytes());assert original['status']=='PASS_EXACT_SPYRROW_ORIGINAL_COMPONENT_REFERENCES_ONLY' and original['shared_runtime_unchanged'] and original['overlay_unchanged'] and original['source_inputs_unchanged'];cases=reference/'original/cases.json';truth=reference/'original/original-results.json';expected=json.loads(truth.read_bytes());inputs={str(p.relative_to(ROOT)):pin(p) for p in [cases,truth,*[ROOT/n for n in built['source_pins']]]};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
        def path(p,linux):return '/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix() if linux else str(p)
        for label,java,major,linux in [('windows21',str(JAVA21/'java.exe'),21,False),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',25,False),('linux21','/usr/bin/java',21,True)]:
            home=out/label;home.mkdir();version=[java,'-version'];command=[java,'-Xmx4g','-cp',path(jar,linux),'org.jlinalg.cli.SpyrrowCommunicationMatrix',path(cases,linux),path(home/'native',linux)]
            if linux:version=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',*version];command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',*command]
            v=subprocess.run(version,capture_output=True,timeout=30);(home/'java-version.stdout').write_bytes(v.stdout);(home/'java-version.stderr').write_bytes(v.stderr);import re;assert v.returncode==0 and re.search('version "'+str(major)+r'(?:\.|\")',(v.stdout+v.stderr).decode('utf8'));run=subprocess.run(command,capture_output=True,timeout=300);(home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr);save(home/'process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode('utf8',errors='replace');actual=json.loads((home/'native/results.json').read_bytes());checks=dict(numeric=0,exact=0);assert len(actual['cases'])==len(expected['cases'])==21
            for a,b in zip(actual['cases'],expected['cases']):
                assert not set(b['dtypes'])-{'float64'}
                for key in ['id','index','columns','values','pairs']:compare(a[key],b[key],b['id']+'/'+key,checks)
            for key in ['hashes','sets','distances']:compare(actual[key],expected[key],key,checks)
            report=json.loads((home/'native/COMPLETED.json').read_bytes());assert report['status']=='PASS_NATIVE_SPYRROW_COMMUNICATION_COMPONENT_ONLY' and len(report['expected_rejections'])==14 and report['defensive_copy_controls'] and not report['implementation_credit'];record=dict(label=label,command=command,exit_code=run.returncode,cases=21,comparisons=checks,expected_rejections=report['expected_rejections'],operations=report['operations']);state['runs'].append(record);print(label,checks,flush=True)
        assert all(pin(ROOT/n)==want for n,want in inputs.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_THREE_JVM_EXACT_SPYRROW_COMMUNICATION_COMPONENT_ONLY',artifact=built['artifact'],reference=pin(reference/'COMPLETED.json'),input_pins=inputs,canonical_executable_unchanged=canonical,full_spyrrow_tool_admitted=False)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete numerical/source comparisons')
    main()
