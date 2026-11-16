#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Complete native inf/grid workflow controls against saved original 1.1m effects."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,array,hashlib,json,os,shutil,subprocess,sys,time,traceback
ROOT=Path(__file__).resolve().parents[3]

def pin(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):h.update(raw)
    return dict(bytes=path.stat().st_size,sha256=h.hexdigest())

def save(path,obj):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(obj,f,indent=2);f.write('\n')

def doubles(path):
    value=array.array('d')
    with path.open('rb') as f:value.fromfile(f,path.stat().st_size//8)
    if sys.byteorder!='little':value.byteswap()
    return value

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference',required=True,type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--java',required=True,type=Path);a=p.parse_args();out=None
    try:
        target=a.out.absolute();target.mkdir(parents=True,exist_ok=False);out=target
        ref=a.reference.resolve(strict=True);jar=out/'candidate.jar';shutil.copyfile(a.jar,jar);assert pin(jar)==pin(a.jar)
        required=['beta.f64','beta_se.f64','n_eff.f64','ld-pointers.i32','ld-rows.i32','ld-values.f64','original-inf-allele.f64','original-grid-allele.f64','seed-before.txt','seed-after.txt','COMPLETED.txt']
        pins={name:pin(ref/name) for name in required};save(out/'REFERENCE-PINS.json',pins);save(out/'VALIDATION-SOURCE-PINS.json',{str(Path(__file__).resolve().relative_to(ROOT)):pin(Path(__file__).resolve())})
        beta,se,n=[doubles(ref/name) for name in ['beta.f64','beta_se.f64','n_eff.f64']];m=len(beta);assert m==1100000 and len(se)==m and len(n)==m
        data=out/'data';data.mkdir()
        with (data/'summary.tsv').open('x',encoding='utf-8',newline='\n') as s,(data/'variants.tsv').open('x',encoding='utf-8',newline='\n') as v:
            s.write('variant\teffect_allele\tother_allele\tbeta\tbeta_se\tn_eff\n');v.write('variant\teffect_allele\tother_allele\n')
            for i in range(m):
                label=f'v{i}\tA\tG';v.write(label+'\n');s.write(label+f'\t{beta[i]:.17g}\t{se[i]:.17g}\t{n[i]:.17g}\n')
        del beta,se,n
        for source,name in [('ld-pointers.i32','p.bin'),('ld-rows.i32','i.bin'),('ld-values.f64','x.bin')]:shutil.copyfile(ref/source,data/name);assert pin(data/name)==pins[source]
        (data/'models.tsv').write_text('model\th2\tp\tsparse\nmodel1\t0.3\t1\tfalse\n')
        cli=[str(a.java),'-Xmx256m','-cp',str(jar),'org.jlinalg.cli.LdPred2WorkflowCli']
        def run(label,command):
            start=time.monotonic()
            with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
            save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode,seconds=round(time.monotonic()-start,3)));print(label,r.returncode,flush=True)
            if r.returncode:raise RuntimeError(label+' failed; inspect retained consoles')
        common=dict(engine='native',summary=str(data/'summary.tsv').replace('\\','/'),ld_variants=str(data/'variants.tsv').replace('\\','/'),ld_pointers=str(data/'p.bin').replace('\\','/'),ld_rows=str(data/'i.bin').replace('\\','/'),ld_values=str(data/'x.bin').replace('\\','/'),genome_build='synthetic',ld_population='synthetic',java_heap_mb='1024',wall_ms='1200000',max_work_bytes='2147483648',max_files='128')
        def one(method):
            values=common.copy();values['method']=method
            if method=='inf':values.update(heritability='0.3',relative_tolerance='1e-10',solver_iterations='1000')
            else:values.update(models=str(data/'models.tsv').replace('\\','/'),seed='32768',burn_in='1',iterations='3')
            request=out/(method+'.properties');request.write_text(''.join(k+'='+v+'\n' for k,v in values.items()),encoding='utf-8')
            destination=out/method;run(method,cli+['--request',str(request),'--out',str(destination)])
            expected=doubles(ref/f'original-{method}-allele.f64');count=0;worst=0.0;model='inf' if method=='inf' else 'model1'
            with (destination/f'results/{model}/effects.tsv').open() as f:
                assert f.readline().strip()=='variant\teffect_allele\tother_allele\teffect'
                for count,line in enumerate(f,1):
                    fields=line.rstrip('\n').split('\t');assert fields[:3]==[f'v{count-1}','A','G'];error=abs(float(fields[3])-expected[count-1]);assert error<(2e-10 if method=='inf' else 2e-11);worst=max(worst,error)
            assert count==m
            if method=='grid':
                diagnostics=dict(line.rstrip('\n').split('\t') for line in (destination/'results/model1/diagnostics.tsv').read_text().splitlines()[1:])
                expected_state=','.join((ref/'seed-after.txt').read_text().splitlines());assert diagnostics['rng_final']==expected_state
            run(method+'-genuine-replay',cli+['--verify',str(destination),'--out',str(out/(method+'-replay'))])
            return dict(method=method,variants=m,stored_ld_entries=2*m,all_effects_compared=True,max_absolute_error=worst,worker_heap_mb=1024)
        with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(one,['inf','grid']))
        assert pins=={name:pin(ref/name) for name in required},'Saved original data changed'
        save(out/'COMPLETED.json',dict(status='PASS_NATIVE_FULL_WORKFLOWS_AT_1100000_VARIANTS',profile='ldpred2-prepared-ld-workflow-v2',results=results,synthetic_pair_blocks=True,original_auto_scale_qualified=False,new_catalog_credit=0,whole_goal_complete=False,candidate=pin(jar)));return 0
    except Exception as error:
        if out is not None:
            try:save(out/'FAILURE.json',dict(cause=repr(error),traceback=traceback.format_exc(),required_action='Retain failed attempts, restore the exact saved original controls, correct the named cause, and retry NEW output. Do not thin variants or alter LD dependencies to fit.'))
            except Exception:pass
        print('FAIL2_FULL_NATIVE_WORKFLOW_SCALE_NOT_QUALIFIED',repr(error),flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
