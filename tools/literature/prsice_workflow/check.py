#!/usr/bin/env python3
"""Run original PRSice through the complete JAR; retain private qualification evidence."""
from pathlib import Path
import argparse,copy,hashlib,json,subprocess,sys
from fixture import CASES,generate,request

def pin(path):
    digest=hashlib.sha256()
    with path.open('rb')as f:
        for block in iter(lambda:f.read(1024*1024),b''):digest.update(block)
    return dict(bytes=path.stat().st_size,sha256=digest.hexdigest())
def inventory(root):return {p.relative_to(root).as_posix():pin(p)for p in root.rglob('*')if p.is_file()}
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java',default='java');p.add_argument('--jar',type=Path,required=True);p.add_argument('--runtime-home',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',nargs='+',choices=CASES);p.add_argument('--scoring-matrix',action='store_true',help='Run all model/missing combinations at average scale plus SCORE control standardization');a=p.parse_args();root=a.out.absolute();root.mkdir();jar=a.jar.resolve();home=a.runtime_home.resolve();inputs=root/'inputs';generate(inputs);before=inventory(inputs);producer=pin(jar);records=[]
    def run(name,body=None,inspect=None,expected=0):
        if inspect is None:
            f=root/(name+'.json');save(f,body);args=['--request',str(f)]
        else:args=['--inspect',str(inspect)]
        command=[a.java,'-jar',str(jar),'prsice-tools',*args];save(root/(name+'-command.json'),command)
        with(root/(name+'.stdout')).open('xb')as out,(root/(name+'.stderr')).open('xb')as err:code=subprocess.run(command,stdout=out,stderr=err,timeout=600).returncode
        records.append(dict(case=name,expected=expected,observed=code,passed=code==expected));print(name,code,'PASS'if code==expected else'FAIL',flush=True);return code
    names=()if a.scoring_matrix else a.cases or CASES
    for name in names:
        body=request(name,home,inputs,root/name)
        if run(name,body)==0:
            snapshot=inventory(root/name);run(name+'-inspect',inspect=root/name);assert snapshot==inventory(root/name),'Saved inspection mutated capture'
    if a.scoring_matrix:
        for model in ('add','dom','rec','het'):
            for missing in ('MEAN_IMPUTE','SET_ZERO','CENTER'):
                name=model+'-'+missing;body=request('unclumped-score',home,inputs,root/name);body['parameters']['scoring'].update(model=model,missing=missing,scale='avg');run(name,body)
        name='score-control-standardization';body=request('binary-control-standardized',home,inputs,root/name);body['operation']='SCORE';body['parameters']['regression']=None;run(name,body)
    elif not a.cases:
        for mode in ('VERIFY','REPLAY'):run(mode.lower(),dict(schema_version=1,mode=mode,analysis=str(root/'target-clump-score'),out=str(root/mode.lower()),runtime_home=str(home)))
        run('reference-cache-skip',request('reference-cache-skip',home,inputs,root/'reference-cache-skip'),expected=3)
        body=request('unclumped-score',root/'missing-runtime',inputs,root/'missing-runtime-result');run('missing-runtime',body,expected=3)
        body=request('unclumped-score',home,inputs,root/'target-clump-score');before_existing=inventory(root/'target-clump-score');run('existing-output',body,expected=2);assert before_existing==inventory(root/'target-clump-score')
        body=request('unclumped-score',home,inputs,root/'unknown-choice');body['parameters']['silent_repair']=True;run('unknown-choice',body,expected=2)
        body=request('unclumped-score',home,inputs,root/'score-capacity');body['limits']['maximum_native_cells']=1;run('score-capacity',body,expected=2)
        body=request('linear',home,inputs,root/'convergence-required');body['policies']['solver_convergence']='REQUIRE_REPORTED_CONVERGENCE';run('convergence-required',body,expected=3)
        body=request('control-std-keep-gaps',home,inputs,root/'incomplete-standardization');body['policies']['standardization']='FAIL_ON_INCOMPLETE_FIT_ROWS';run('incomplete-standardization',body,expected=2)
        body=request('complement',home,inputs,root/'strand-exact');body['parameters']['alleles']['strand']='EXACT';run('strand-exact',body,expected=2)
        if (root/'target-clump-score'/'COMPLETED.json').is_file():
            def selected(name):return {line.split()[1]for line in(root/name/'worker/original.snp').read_text(encoding='utf-8').splitlines()[1:]}
            target=selected('target-clump-score');reference=selected('reference-clump-score');assert 'rs1'in target and'rs2'not in target and'rs3'not in target and'rs4'in target,'Known target perfect LD/window test failed';assert {'rs1','rs2','rs3','rs4'}<=reference,'Independent reference panel test failed'
    assert before==inventory(inputs),'Original inputs changed';assert producer==pin(jar),'Producer JAR changed';save(root/'PRIVATE-RESULT.json',dict(records=records,producer=producer,inputs_unchanged=True,producer_unchanged=True,raw_captures_private=True,whole_goal_complete=False));passed=all(r['passed']for r in records);print(json.dumps(dict(cases=len(records),passed=sum(r['passed']for r in records),complete=passed)));return 0 if passed else 1
if __name__=='__main__':sys.exit(main())
