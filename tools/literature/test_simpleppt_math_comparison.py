# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject corrupted whole primitive reference families through the real JVM comparator."""
import argparse,hashlib,json,os,shutil,struct,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def replace_json(path,value):
    # Never write into a hard-linked original fixture inode.
    temp=path.with_name(path.name+'.mutation');assert not temp.exists()
    with temp.open('xb') as stream:stream.write((json.dumps(value,ensure_ascii=True,allow_nan=False)+'\n').encode())
    os.replace(temp,path)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['build','reference','out']:parser.add_argument('--'+name,type=Path,required=True)
    a=parser.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SIMPLEPPT_COMPARISON_CORRUPTIONS',checks=[],implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};original=a.reference.resolve()/'original';before={p.relative_to(original).as_posix():pin(p) for p in original.rglob('*') if p.is_file()};assert len(before)>600;save(out/'original-fixture-inventory-before.json',before)
        mutations=['assignment-value','signed-zero','nan-to-infinity','conditional-system','conditional-rhs','observed-correlation-score','observed-convergence','incomplete-family','duplicate-case']
        for mutation in mutations:
            home=out/mutation;home.mkdir();family=home/'family';shutil.copytree(original,family,copy_function=os.link);overlay=[]
            def load(name):return json.loads((family/name).read_bytes())
            def numeric(name,key,at,new):
                metadata=load(name);value=metadata[key];assert value['dtype']=='<f8';path=(family/name).parent;raw=bytearray((path/value['path']).read_bytes());old=struct.unpack_from('<d',raw,at*8)[0];struct.pack_into('<d',raw,at*8,new(old));digest=hashlib.sha256(raw).hexdigest();newpath='objects/'+digest+'.bin';assert not (path/newpath).exists()
                with (path/newpath).open('xb') as stream:stream.write(raw)
                value.update(path=newpath,bytes=len(raw),sha256=digest);replace_json(family/name,metadata);overlay.extend([name,(Path(name).parent/newpath).as_posix()])
            expected=''
            if mutation=='assignment-value':numeric('weights-k2-sigma0.5/CASE.json','assignments',0,lambda _:0.25);expected='Original numeric value differs'
            elif mutation=='signed-zero':numeric('weights-k1-sigma0.0005/CASE.json','kernel',1,lambda old:-0.0 if old==0 else (_ for _ in ()).throw(AssertionError('Expected zero kernel')));expected='Original signed zero state differs'
            elif mutation=='nan-to-infinity':name='zero-norm-zero-change/CASE.json';v=load(name);assert v['error']==dict(kind='nonfinite-scalar',value='NaN');v['error']['value']='+Inf';replace_json(family/name,v);expected='Original nonfinite state differs'
            elif mutation=='conditional-system':numeric('conditional-small/CASE.json','system',0,lambda old:old+1);expected='Original numeric value differs'
            elif mutation=='conditional-rhs':numeric('conditional-small/CASE.json','right_hand_side',0,lambda old:old+1);expected='Original numeric value differs'
            elif mutation in ['observed-correlation-score','observed-convergence']:
                name='tree-root-project-likelihood/observed-final-ppt-step.json';v=load(name)
                if mutation=='observed-correlation-score':v['locals']['score'][0]+=1e-9;expected='Original correlation score exceeds declared cancellation comparison'
                else:v['locals']['err']+=1;expected='Original numeric value differs'
                replace_json(family/name,v);overlay.append(name)
            else:
                name='COMPLETED.json';v=load(name)
                if mutation=='incomplete-family':v['arithmetic_cases'].pop();expected='Incomplete original family'
                else:v['arithmetic_cases'][1]=v['arithmetic_cases'][0];expected='Unsafe/duplicate case identity'
                replace_json(family/name,v);overlay.append(name)
            if mutation=='nan-to-infinity':overlay.append(name)
            command=[str(JAVA21/'java.exe'),'-Xmx3g','-cp',str(jar),'org.jlinalg.cli.SimplePptMathMatrix',str(family),str(home/'must-not-exist.json')];run=subprocess.run(command,capture_output=True,timeout=120);(home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr);assert run.returncode!=0 and expected.encode() in run.stderr and not(home/'must-not-exist.json').exists(),(mutation,run.stderr[-2000:]);state['checks'].append(dict(mutation=mutation,command=command,exit_code=run.returncode,expected_rejection=expected,complete_comparison_rejected=True,fixture_overlay={n:pin(family/n) for n in overlay}))
        after={p.relative_to(original).as_posix():pin(p) for p in original.rglob('*') if p.is_file()};save(out/'original-fixture-inventory-after.json',after);assert before==after;state.update(status='PASS_NINE_SIMPLEPPT_COMPARISON_CORRUPTIONS_REJECTED_ONLY',artifact=built['artifact'],original_fixtures_unchanged=True,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
