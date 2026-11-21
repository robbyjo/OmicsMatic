from pathlib import Path
import concurrent.futures,hashlib,json,math,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/gc-5';B=T/'build/gcta-mtcojo-1';F=B/'synthetic-inputs-1';O=B/'original-behavior-controls-1';O.mkdir(exist_ok=False)
UP=R/'build/gc-1/build/gcta-high-impact-1/upstream-1.94.1-intake-1'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8',newline='\n')
def linux(p):return '/mnt/d/'+p.relative_to(Path('D:/')).as_posix()
def tokens(p):return [line.split() for line in p.read_text().splitlines()]
cases=[('single-prevalence','single','supplied','normal',[]),('multi-prevalence','multi','supplied','normal',[]),('multi-prevalence-swapped','multi','supplied','swapped',[]),('single-GSMR-low-IV','single','GSMR',None,['--gwas-thresh','1e-100']),('single-supplied-low-IV','single','supplied',None,['--gwas-thresh','1e-100']),('multi-GSMR-low-IV','multi','GSMR',None,['--gwas-thresh','1e-100']),('single-supplied-100','single','sentinel',None,[]),('single-GSMR-decimal-LD-FDR','single','GSMR',None,['--gsmr-ld-fdr','0.05']),('single-supplied-zero','single','zero',None,[])]
def task(host):
    base=O/host;base.mkdir();results=[];exe=UP/('linux/download' if host=='linux' else 'windows/files/gcta-1.94.1-win-x86_64/gcta-win-1.94.1.exe')
    for name,count,mode,prevalence,extra in cases:
        folder=base/name;folder.mkdir();traits=['Y','X1']+(['X2'] if count=='multi' else []);lines=[]
        for i,trait in enumerate(traits):
            path=F/(trait+'.ma');line=trait+'\t'+(linux(path) if host=='linux' else str(path))
            if prevalence:
                sample,population=[(.25,.01),(.4,.05),(.3,.08)][i]
                if prevalence=='swapped':sample,population=population,sample
                line+=f'\t{sample}\t{population}'
            lines.append(line+'\n')
        traitfile=folder/'traits.list';traitfile.write_text(''.join(lines),encoding='utf-8',newline='\n');out=folder/'result'
        args=['--bfile','reference','--mtcojo-file',linux(traitfile) if host=='linux' else str(traitfile),'--ref-ld-chr','ref-ld','--w-ld-chr','weights-ld']
        if mode!='GSMR':
            bxy=folder/'bxy.tsv';bxy.write_text('X1\t'+('100' if mode=='sentinel' else '0' if mode=='zero' else '0.35')+'\n'+('X2\t-0.2\n' if count=='multi' else ''),encoding='utf-8',newline='\n');args+=['--mtcojo-bxy',linux(bxy) if host=='linux' else str(bxy)]
        args+=extra+['--thread-num','1','--out',linux(out) if host=='linux' else str(out)]
        command=[str(exe),*args] if host=='windows' else ['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--cd',linux(F),'--exec',linux(exe),*args]
        save(folder/'COMMAND.json',dict(command=command,cwd=str(F),source_tag='v1.94.1',scientific_result_admitted=False))
        with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=F,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,env=dict(os.environ,OMP_NUM_THREADS='1',MKL_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',LANG='C',LC_ALL='C'),timeout=300)
        stdout=(folder/'stdout').read_text(errors='replace');table=folder/'result.mtcojo.cma';row=dict(name=name,exit_code=p.returncode,table_present=table.is_file(),rg_fallback_announced='bxy is estimated from rg.' in stdout,GSMR_invoked='GSMR analysis for covariate #' in stdout,declared_causal_mode=mode,prevalence_declaration=prevalence,accepted_decimal_FDR_as_zero='--gsmr-ld-fdr 0' in stdout)
        if table.is_file():
            data=tokens(table);row['rows']=len(data)-1;row['all_fields_finite']=all(math.isfinite(float(x)) for line in data[1:] for x in line[3:]);reference=B/'original-1.94.1-1'/host/(count+'-'+('GSMR' if mode=='GSMR' or mode=='sentinel' else 'supplied'))/'result.mtcojo.cma';row['all_baseline_table_tokens_equal']=tokens(reference)==data
        save(folder/'RESULT.json',row);results.append(row);save(base/'PROGRESS.json',results);print('Original behavior',host,name,'exit',p.returncode,'table',row['table_present'],'finite',row.get('all_fields_finite',''),'equal',row.get('all_baseline_table_tokens_equal',''),'fallback',row['rg_fallback_announced'],flush=True)
    return host,results
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=dict(pool.map(task,['windows','linux']))
save(O/'COMPLETED.json',dict(status='ACTUAL_ORIGINAL_SOURCE_EDGE_OBSERVATIONS_REVIEW_REQUIRED',results=results,original_inputs_modified=False,scientific_results_admitted=False))
