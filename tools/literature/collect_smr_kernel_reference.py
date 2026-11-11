# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Link a separate thin harness to unchanged original SMR objects and capture complete covariance/probability controls."""
import argparse,json,math,random,shlex,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_smr_cmake import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build-home',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();build=a.build_home.resolve();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);record=json.loads((build/'COMPLETED.json').read_bytes());assert json.loads((build/'compile-process.json').read_bytes())['exit_code']==0;source=build/'owned-source';compiler=json.loads((build/'build/compile_commands.json').read_bytes());entry=next(c for c in compiler if c['file'].endswith('/src/SMR.cpp'));arguments=shlex.split(entry['command']);harness=ROOT/'tools/literature/smr_reference_kernel.cpp';original=build/'build/smr';before=pin(original);pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),harness,build/'COMPLETED.json',source/'src/SMR_data.cpp',source/'src/StatFunc.cpp',source/'src/SMR.cpp']};command=arguments[:];command[command.index('-o')+1]=linux(out/'wrapper.o');command[command.index(entry['file'])]=linux(harness);command+=['-I'+linux(source/'src')];link=shlex.split((build/'build/CMakeFiles/smr.dir/link.txt').read_text());index=next(i for i,x in enumerate(link) if x.endswith('/src/SMR.cpp.o'));link[index]=linux(out/'wrapper.o');link[link.index('-o')+1]=linux(out/'original-kernel-reference');prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/env','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1'];reports=[]
    def run(identifier,args,stdin=None):
        with (out/(identifier+'.stdout')).open('xb') as stdout,(out/(identifier+'.stderr')).open('xb') as stderr:r=subprocess.run(prefix+args,cwd=build/'build',input=stdin,stdout=stdout,stderr=stderr,timeout=180)
        save(out/(identifier+'-process.json'),dict(command=prefix+args,exit_code=r.returncode));assert r.returncode==0,'Inspect retained '+identifier+' logs'
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    run('compile',command);run('link',link);rng=random.Random(190731);cases=[]
    for n in [2,3,4,8,20,40]:
        for rho in [0,.15,.5,.9]:
            for kind in ['ordinary','signed-effects','equal-ratios']:
                bx=[(.35+rng.random()*.2)*(-1 if kind=='signed-effects' and j%2 else 1) for j in range(n)];sx=[.015+rng.random()*.015 for _ in range(n)];by=[b*.3+(0 if kind=='equal-ratios' else rng.uniform(-.06,.06)) for b in bx];sy=[.01+rng.random()*.02 for _ in range(n)];ld=[[rho**abs(i-j) for j in range(n)] for i in range(n)];cases.append(dict(id='n'+str(n)+'-r'+str(int(rho*100))+'-'+kind,exposure=bx,exposure_se=sx,outcome=by,outcome_se=sy,LD=ld))
    text=''.join(c['id']+' '+str(len(c['exposure']))+'\n'+''.join(' '.join(format(c[name][i],'.17g') for name in ['exposure','exposure_se','outcome','outcome_se'])+'\n' for i in range(len(c['exposure'])))+''.join(' '.join(format(v,'.17g') for v in row)+'\n' for row in c['LD']) for c in cases);(out/'inputs.txt').write_text(text,encoding='utf8',newline='\n');save(out/'inputs.json',cases);run('original',[linux(out/'original-kernel-reference')],text.encode());results=[json.loads(line) for line in (out/'original.stdout').read_text(encoding='utf8').splitlines()];assert len(results)==len(cases)==72 and [r['id'] for r in results]==[c['id'] for c in cases];assert all(r['instruments']==len(c['exposure']) and len(r['original_covariance'])==len(c['exposure']) for r,c in zip(results,cases));save(out/'results.json',results);assert pin(original)==before and all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='captured72-unchanged-original-SMR-kernel-calls-not-Java-validation',cases=72,source_pins=pins,original_executable_unchanged=before,reference_program=pin(out/'original-kernel-reference'),inputs=pin(out/'inputs.json'),results=pin(out/'results.json'),actual_original_functions_called=['CommFunc::max_abs_id','SMRDATA::est_cov_bxy','SMRDATA::bxy_hetero3','StatFunc::pchisq'],original_main_symbol_only_renamed=True,original_scientific_function_bodies_patched=False,ratio_diagnostic_arithmetic_is_owned_harness_not_independent_original_function=True,source_header_float_overload_not_used=True,analysis_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Captured72 complete original covariance/HEIDI cases and original chi-square function controls')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable reference/source checks')
    main()
