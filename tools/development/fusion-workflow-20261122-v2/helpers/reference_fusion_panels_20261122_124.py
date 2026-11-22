from pathlib import Path
import csv,hashlib,json,os,shutil,subprocess,time
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';B=F/'post-panel-reference-1';B.mkdir();data=B/'data';data.mkdir();original=F/'original-association-1';raw=original/'complete.out.tsv';rows=list(csv.DictReader(raw.open(encoding='utf-8'),delimiter='\t'));fields=list(rows[0])
for i,row in enumerate(rows[:2]):
 panel='panel-'+('A' if i==0 else 'B');folder=data/panel;folder.mkdir();name='g'+str(i+1)+'.wgt.RDat';shutil.copyfile(original/'data/weights'/name,folder/name);row['ID']='SYNTH_SHARED_GENE';row['FILE']=panel+'/'+name
with (data/'omnibus.tsv').open('w',encoding='utf-8',newline='') as stream:
 w=csv.DictWriter(stream,fieldnames=fields,delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(rows[:2])
source=F/'upstream-intake-1/source/FUSION.post_process.R';assert source.is_file();assert hashlib.sha256(source.read_bytes()).hexdigest()=='bdde74d265034aa82544fdd2ea1d3c6c9a136ec05eac743363900d4feec5af40'
lib=[F/'workflow-windows-library-1/library',F/'runtime-install-1/library'];env=os.environ.copy();env.update(R_LIBS_USER=';'.join(map(str,lib)),R_LIBS_SITE='',LC_ALL='C',LANG='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
records=[]
for name,options in [('correlation',['--omnibus','--omnibus_corr','best']),('omnibus',['--omnibus','--max_r2','0.99'])]:
 prefix=B/name;args=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(source),'--input',str(data/'omnibus.tsv'),'--sumstats',str(original/'data/complete.sumstats'),'--ref_ld_chr',str(original/'data/ref.'),'--chr','1','--out',str(prefix),'--minp_input','1',*options]
 with (B/(name+'.stdout')).open('xb') as out,(B/(name+'.stderr')).open('xb') as err:p=subprocess.Popen(args,cwd=data,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err);code=p.wait()
 record=dict(case=name,exit_code=code,arguments=args,source_sha256=hashlib.sha256(source.read_bytes()).hexdigest());records.append(record);print(name,code,flush=True)
 if code:print((B/(name+'.stderr')).read_text(errors='replace')[-1200:],flush=True)
 assert code==0
 table=list(csv.DictReader(Path(str(prefix)+'.CORR.dat').open(encoding='utf-8'),delimiter='\t'));assert table and {table[0]['REF1'],table[0]['REF2']}=={'panel-A','panel-B'}
(B/'RESULT.json').write_text(json.dumps(dict(status='ORIGINAL_TWO_PANEL_REFERENCE_EXECUTED',cases=records,synthetic_inputs_only=True),indent=2)+'\n')