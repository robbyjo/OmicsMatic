#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual Linux original-R analysis controls; no runtime installation or source overlays."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,hashlib,json,os,platform,shutil,struct,subprocess,time,traceback,zipfile
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]

def save(path,obj):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(obj,f,indent=2);f.write('\n')

def pin(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):h.update(raw)
    return {'bytes':path.stat().st_size,'sha256':h.hexdigest()}

def properties(path):
    return dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#'))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--runtime',required=True,type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--java',type=Path,default=Path('/usr/bin/java'));a=p.parse_args();out=None
    try:
        if platform.system()!='Linux':raise FileNotFoundError('Run this profile in a permitted Linux JVM environment')
        target=a.out.absolute();target.mkdir(parents=True,exist_ok=False);out=target
        runtime=a.runtime.resolve(strict=True);config=properties(runtime/'runtime.properties');edition=config['edition'];assert edition in ['1.12.2','1.12.4']
        assert json.loads((runtime/'READY.json').read_text())['status']=='READY_ADOPTED_ORIGINAL_BIGSNPR_LDPRED2_RUNTIME_NOT_ANALYSIS'
        jar=out/'candidate.jar';shutil.copyfile(a.jar,jar);assert pin(jar)==pin(a.jar)
        own={str(p.relative_to(ROOT)):pin(p) for p in HERE.glob('*') if p.is_file()};save(out/'VALIDATION-SOURCE-PINS.json',own)
        env=os.environ.copy();env.update(OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1',R_LIBS_USER=str(out/'unused-library'))
        def run(label,cmd,expected=0):
            cmd=list(map(str,cmd));start=time.monotonic()
            with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
                result=subprocess.run(cmd,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
            record=dict(command=cmd,exit_code=result.returncode,expected_exit_code=expected,seconds=round(time.monotonic()-start,3));save(out/(label+'.process.json'),record)
            print(label,result.returncode,flush=True)
            if result.returncode!=expected:raise RuntimeError(label+' failed; inspect retained consoles')
            return record
        data=out/'data';data.mkdir()
        with zipfile.ZipFile(ROOT/'src/test/resources/org/jlinalg/genetics/ldpred2-whole-grid-r433.zip') as z:fixture={n:z.read(n).decode() for n in z.namelist()}
        stats=[[float(s) for s in line.split('\t')] for line in fixture['summary.tsv'].splitlines()];ld=[[float(s) for s in line.split('\t')] for line in fixture['ld.tsv'].splitlines()];m=len(stats)
        labels=[('NA' if i==0 else 'v'+str(i))+'\tA\tG' for i in range(m)]
        (data/'summary.tsv').write_text('variant\teffect_allele\tother_allele\tbeta\tbeta_se\tn_eff\n'+''.join(label+'\t'+'\t'.join(map(str,row))+'\n' for label,row in zip(labels,stats)))
        (data/'null.tsv').write_text('variant\teffect_allele\tother_allele\tbeta\tbeta_se\tn_eff\n'+''.join(label+'\t0\t'+'\t'.join(map(str,row[1:]))+'\n' for label,row in zip(labels,stats)))
        (data/'variants.tsv').write_text('variant\teffect_allele\tother_allele\n'+'\n'.join(labels)+'\n')
        pointers=[];rows=[];values=[]
        for col in range(m):
            pointers.append(len(rows))
            for row in range(m):
                if ld[row][col]!=0:rows.append(row);values.append(ld[row][col])
        pointers.append(len(rows))
        for name,fmt,seq in [('p.bin','i',pointers),('i.bin','i',rows),('x.bin','d',values)]: (data/name).write_bytes(struct.pack('<'+fmt*len(seq),*seq))
        grid='model\th2\tp\tsparse\n'
        for k,line in enumerate(fixture['grid.tsv'].splitlines(),1):
            prob,h,sparse=line.split('\t');grid+=f'm{k}\t{h}\t{prob}\t'+('true' if sparse=='1' else 'false')+'\n'
        (data/'models.tsv').write_text(grid);(data/'auto.tsv').write_text('model\tp_init\nlow\t0.01\nhigh\t0.9\nmiddle\t0.1\n');(data/'mixed.tsv').write_text('model\th2\tp\tsparse\ndense\t0.3\t1\tfalse\nsparse\t0.3\t0.5\ttrue\n')
        reference=out/'reference';reference.mkdir()
        run('original-reference',[config['rscript'],'--vanilla',HERE/'reference.R',reference,data,config['r_library'],edition])
        common=dict(engine='original',summary=str(data/'summary.tsv'),ld_variants=str(data/'variants.tsv'),ld_pointers=str(data/'p.bin'),ld_rows=str(data/'i.bin'),ld_values=str(data/'x.bin'),genome_build='synthetic',ld_population='synthetic',runtime=str(runtime),java_heap_mb='256',r_address_space_mb='8192',wall_ms='180000',max_work_bytes='268435456',max_files='1000')
        cli=[a.java,'-Xmx256m','-cp',jar,'org.jlinalg.cli.LdPred2WorkflowCli']
        cases=['auto_mlefalse','auto_mletrue','auto_sparse','auto_options','inf','grid','grid_rejections','auto_rejections']
        def one(name):
            values=common.copy();method='auto' if name.startswith('auto') else ('inf' if name=='inf' else 'grid');values['method']=method
            if method in ['inf','auto']:values['heritability']='0.3'
            if method!='inf':values.update(models=str(data/('auto.tsv' if method=='auto' else 'models.tsv')),seed='4096',burn_in='10',iterations='20')
            if method=='auto':values.update(use_mle='true' if name in ['auto_mletrue','auto_options'] else 'false',sparse='true' if name in ['auto_sparse','auto_options'] else 'false',report_step='5')
            if name=='auto_options':
                values.update(allow_jump_sign='false',shrink_corr='0.95',alpha_min='-1.2',alpha_max='0.2')
                if edition=='1.12.4':values.update(p_min='0.05',p_max='0.8')
            if name.endswith('rejections'):values['summary']=str(data/'null.tsv')
            if name=='grid_rejections':values['models']=str(data/'mixed.tsv')
            request=out/(name+'.properties');request.write_text(''.join(k+'='+v+'\n' for k,v in sorted(values.items())))
            destination=out/name;expected=2 if name=='auto_rejections' else 0
            run(name,cli+['--request',request,'--out',destination],expected)
            assert (destination/'COMPLETED.properties').exists()==(expected==0)
            run(name+'-independent-comparison',[config['rscript'],'--vanilla',HERE/'compare.R',reference,destination,name,config['r_library']])
            if name in ['inf','grid','auto_mlefalse']:run(name+'-genuine-replay',cli+['--verify',destination,'--out',out/(name+'-replay')])
            return name
        with ThreadPoolExecutor(max_workers=2) as pool:completed=list(pool.map(one,cases))
        assert own=={str(p.relative_to(ROOT)):pin(p) for p in HERE.glob('*') if p.is_file()},'Validation sources changed during execution'
        save(out/'COMPLETED.json',dict(status='PASS_ACTUAL_ORIGINAL_LDPRED2_WORKFLOW_CONTROLS',edition=edition,profile='ldpred2-prepared-ld-workflow-v2',cases=completed,candidate=pin(jar),complete_original_R_objects_identical=True,all_exported_effects_paths_posteriors_and_rng_compared=True,genuine_recomputation_methods=['inf','grid','auto'],source_nonfinite_controls_retained=True,new_catalog_credit=0,whole_goal_complete=False))
        print('PASS_ACTUAL_ORIGINAL_LDPRED2_WORKFLOW_CONTROLS',edition,flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,FileNotFoundError) else 2
        if out is not None:
            try:save(out/'FAILURE.json',dict(cause=repr(error),traceback=traceback.format_exc(),required_action='Preserve failures and original data; correct the cause and retry NEW output. Missing permitted prerequisites require skipping dependent analysis.'))
            except Exception:pass
        print('SKIP3_ORIGINAL_WORKFLOW_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_WORKFLOW_NOT_QUALIFIED',repr(error),flush=True);return code
if __name__=='__main__':raise SystemExit(main())
