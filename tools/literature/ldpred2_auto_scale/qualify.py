#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual original LDpred2-auto default-iteration scale controls, with real replay."""
from pathlib import Path
import argparse,csv,hashlib,json,os,platform,shutil,subprocess,time,traceback
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
def pin(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):h.update(raw)
    return dict(bytes=p.stat().st_size,sha256=h.hexdigest())
def save(p,obj):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(obj,f,indent=2);f.write('\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--ld-reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=None
    try:
        if platform.system()!='Linux' or platform.machine() not in ['amd64','x86_64']:raise FileNotFoundError('Use a permitted Linux x86-64 JVM and exact adopted original runtime, otherwise skip analysis')
        target=a.out.absolute();target.mkdir(parents=True,exist_ok=False);out=target
        runtime=a.runtime.resolve(strict=True);config=dict(s.split('=',1) for s in (runtime/'runtime.properties').read_text().splitlines() if s and not s.startswith('#'))
        if config['edition']!='1.12.4':raise ValueError('This scale profile requires the explicitly selected 1.12.4 original; no edition substitution')
        jar=out/'candidate.jar';shutil.copyfile(a.jar,jar);assert pin(jar)==pin(a.jar)
        comparison=ROOT/'tools/literature/ldpred2_workflow_validation/compare.R'
        sources={p.relative_to(ROOT).as_posix():pin(p) for p in [*sorted(HERE.glob('*')),comparison] if p.is_file()};save(out/'SOURCE-PINS.json',sources)
        data=out/'data';data.mkdir();record=json.loads((ROOT/'docs/literature/ldpred2-workflow-v2-development/PRIVATE-RECORDS.json').read_bytes())['files']
        for name,target in [('ld-pointers.i32','p.bin'),('ld-rows.i32','i.bin'),('ld-values.f64','x.bin')]:
            original=a.ld_reference/name;assert pin(original)==record['large-original-1/'+name],name
            shutil.copyfile(original,data/target);assert pin(data/target)==pin(original)
        env=os.environ.copy();env.update(OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1',R_LIBS_USER=str(out/'unused-library'))
        def run(label,cmd,expected=0):
            cmd=list(map(str,cmd));start=time.monotonic()
            with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(cmd,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
            result=dict(command=cmd,exit_code=r.returncode,expected_exit_code=expected,seconds=round(time.monotonic()-start,3));save(out/(label+'.process.json'),result);print(label,r.returncode,flush=True)
            if r.returncode!=expected:raise RuntimeError(label+' failed; retain all raw outputs and consoles, correct the named cause and retry NEW paths')
        rscript=config['rscript'];bounded=['/usr/bin/timeout','--signal=TERM','--kill-after=10','21600','/usr/bin/prlimit','--as=8589934592','--',rscript,'--vanilla']
        run('generation',bounded+[HERE/'generate.R',data])
        reference=out/'reference';reference.mkdir();run('original-reference',bounded+[HERE/'reference.R',data,reference,config['r_library']])
        rows=list(csv.DictReader((reference/'REFERENCE.tsv').open(),delimiter='\t'));accepted=sum(row['accepted']=='TRUE' for row in rows);assert len(rows)==3
        (data/'models.tsv').write_text('model\tp_init\nlow\t0.01\nhigh\t0.9\nmiddle\t0.1\n')
        values=dict(engine='original',method='auto',summary=str(data/'summary.tsv'),ld_variants=str(data/'variants.tsv'),ld_pointers=str(data/'p.bin'),ld_rows=str(data/'i.bin'),ld_values=str(data/'x.bin'),genome_build='synthetic-population-pair-LD',ld_population='synthetic-Gaussian-summary-simulation',runtime=str(runtime),models=str(data/'models.tsv'),heritability='0.3',seed='4096',java_heap_mb='256',r_address_space_mb='8192')
        request=out/'request.properties';request.write_text(''.join(k+'='+v+'\n' for k,v in values.items()))
        cli=['/usr/bin/timeout','--signal=TERM','--kill-after=10','21600','/usr/bin/java','-Xmx256m','-cp',jar,'org.jlinalg.cli.LdPred2WorkflowCli']
        run('analysis',cli+['--request',request,'--out',out/'analysis'],0 if accepted else 2)
        run('independent-original-comparison',bounded+[comparison,reference,out/'analysis','auto_defaults',config['r_library']])
        if accepted==0:raise RuntimeError('All original chains were rejected; matched failures are retained, but this is not successful auto scale qualification')
        run('genuine-recomputation',cli+['--verify',out/'analysis','--out',out/'replay'])
        assert sources=={p.relative_to(ROOT).as_posix():pin(p) for p in [*sorted(HERE.glob('*')),comparison] if p.is_file()},'Scale-control source changed during execution'
        save(out/'COMPLETED.json',dict(status='PASS_ACTUAL_ORIGINAL_AUTO_SCALE_WITH_DEFAULT_700_ITERATIONS',edition='1.12.4',variants=1100000,stored_ld_entries=2200000,chains=3,accepted_chains=accepted,rejected_chains=3-accepted,burn_in=500,retained_iterations=200,use_MLE=True,report_step=201,sample_beta_columns=0,complete_original_objects_identical=True,all_exported_results_and_rng_compared=True,genuine_recomputation=True,r_address_space_mb=8192,synthetic_population_pair_LD_Gaussian_summary=True,sampled_GWAS_cohort=False,whole_paper_reproduced=False,candidate=pin(jar),new_catalog_credit=0,whole_goal_complete=False));return 0
    except Exception as error:
        code=3 if isinstance(error,FileNotFoundError) else 2
        if out is not None:
            try:save(out/'FAILURE.json',dict(cause=repr(error),traceback=traceback.format_exc(),required_action='Preserve failures, raw inputs and original outputs. Missing permitted prerequisites: skip dependent analysis and supply named setup only where allowed. Correct other causes and retry NEW paths. Never tune away a saved source failure or refresh fingerprints.'))
            except Exception:pass
        print('SKIP3_AUTO_SCALE_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_AUTO_SCALE_UNQUALIFIED',repr(error),flush=True);return code
if __name__=='__main__':raise SystemExit(main())
