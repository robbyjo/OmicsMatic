from pathlib import Path
import csv,hashlib,json,shutil,subprocess,time,zipfile
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';N=F/'workflow-normal-build-5';assert json.loads((N/'RESULT.json').read_bytes())['exit_code']==0
B=F/'workflow-packaged-3';B.mkdir();jar=N/'project-build/cli/omicsmatic-0.4.0.jar';assert jar.is_file();records=[]
def linux(p):s=str(p).replace('\\','/');return '/mnt/'+s[0].lower()+s[2:]
def run(name,request,platform='windows21',expected=None):
 file=B/(name+'.json');file.write_text(json.dumps(request,indent=2)+'\n',encoding='utf-8');java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if platform=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe'
 args=([java,'-Xmx2g','-jar',str(jar),'fusion-tools','--request',str(file)] if platform!='linux21' else ['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','java','-Xmx2g','-jar',linux(jar),'fusion-tools','--request',linux(file)])
 started=time.time()
 with (B/(name+'.stdout')).open('xb') as out,(B/(name+'.stderr')).open('xb') as err:
  p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/(name+'.PROCESS.json')).write_text(json.dumps(dict(pid=p.pid,arguments=args,started_unix=started),indent=2)+'\n');code=p.wait()
 row=dict(case=name,platform=platform,exit_code=code,expected=expected,seconds=time.time()-started);records.append(row);(B/'PROGRESS.json').write_text(json.dumps(records,indent=2)+'\n');print(json.dumps(row),flush=True)
 if expected is not None and code!=expected:
  print((B/(name+'.stderr')).read_text(errors='replace')[-1400:],flush=True)
  root=B/name
  for log in ['stderr.log','original.stdout.log','original.stderr.log']:
   f=root/'worker'/log
   if f.is_file():print(log,f.read_text(errors='replace')[-1000:],flush=True)
 return code
base=json.loads((F/'workflow-development-2/request.json').read_bytes());data=F/'original-association-1/data'
for name,options,stats in [('assoc-w21',{},'complete'),('missing-w21',{},'missing'),('top1-w21',{'force_model':'top1'},'complete'),('coloc-w21',{'coloc_P':1,'GWASN':10000},'complete'),('beta-se-w21',{'coloc_P':1},'beta-se'),('permutation-w21',{'perm':31,'perm_minp':1},'complete'),('susie-w21',{'coloc_susie_P':1,'GWASN':10000},'complete')]:
 r=json.loads(json.dumps(base));r['out']=str(B/name);r['options']=options;r['inputs']['sumstats']=str(data/(stats+'.sumstats'))
 if name=='susie-w21':
  versions={}
  for lib in map(Path,r['r_libraries']):
   with (lib.parent/'PACKAGES.tsv').open(encoding='utf-8-sig',newline='') as stream:
    for row in csv.DictReader(stream,delimiter='\t'):versions.setdefault(row['Package'],row['Version'])
  r['expected_packages'].update({k:versions[k] for k in ['coloc','susieR']});r['admission']={'allow_no_result_rows':True}
 elif 'coloc_P' in options:r['expected_packages']['coloc']='5.2.3'
 run(name,r,expected=0)
r=json.loads(json.dumps(base));r['out']=str(B/'assoc-w25');run('assoc-w25',r,'windows25',0)
linuxbase=json.loads(json.dumps(base));linuxbase['rscript']='/opt/jlinalg-saige-runtime-v1/source/.pixi/envs/default/bin/Rscript';linuxbase['r_libraries']=[linux(F/'workflow-linux-install-1/library')];linuxbase['expected_R']='4.5.2';linuxbase['inputs']={k:linux(Path(v)) if k!='chromosome' else v for k,v in base['inputs'].items()};linuxbase['out']=linux(B/'assoc-linux');run('assoc-linux',linuxbase,'linux21',0)
for name,old in [('train-all-models','all-models'),('train-h2','estimated-h2')]:
 r=json.loads((F/'workflow-linux-training-3'/(old+'.json')).read_bytes());r['out']=linux(B/name);run(name,r,'linux21',0)
r=json.loads((F/'workflow-linux-training-3/estimated-h2.json').read_bytes());r['out']=linux(B/'train-top1');r.pop('runtime_compatibility',None);r['binaries'].pop('gcta',None);r['options']['hsq_set']=.4;run('train-top1',r,'linux21',0)
# Explicitly synthetic multi-panel fixture: same gene in two distinct reference panels.
assert (B/'assoc-w21/COMPLETED.json').is_file(), 'Dependent fixture construction skipped: packaged association was not admitted'
fixture=B/'data';fixture.mkdir();source=B/'assoc-w21/worker/out/result';rows=list(csv.DictReader(source.open(encoding='utf-8'),delimiter='\t'));fields=list(rows[0])
for i,row in enumerate(rows[:2]):
 panel='panel-'+('A' if i==0 else 'B');folder=fixture/panel;folder.mkdir();shutil.copyfile(data/'weights'/('g'+str(i+1)+'.wgt.RDat'),folder/('g'+str(i+1)+'.wgt.RDat'));row['ID']='SYNTH_SHARED_GENE';row['FILE']=panel+'/g'+str(i+1)+'.wgt.RDat'
with (fixture/'omnibus.tsv').open('w',encoding='utf-8',newline='') as stream:
 writer=csv.DictWriter(stream,fieldnames=fields,delimiter='\t',lineterminator='\n');writer.writeheader();writer.writerows(rows[:2])
(fixture/'gene-annotation').write_text('1 99000 111000 SYNTH_ANNOTATION\n',encoding='ascii',newline='\n')
post=json.loads(json.dumps(base));post['operation']='POSTPROCESS';post['inputs']={'association':str(source),'sumstats':str(data/'complete.sumstats'),'ref_ld_prefix':str(data/'ref.'),'chromosome':'1','weights_base':str(B/'assoc-w21/worker')}
for name,options in [('post-joint',{'minp_input':1}),('post-ldsc',{'minp_input':1,'ldsc':True}),('post-omnibus',{'minp_input':1,'omnibus':True,'max_r2':.99}),('post-correlation',{'minp_input':1,'omnibus':True,'omnibus_corr':'best'}),('post-report',{'minp_input':1,'report':True,'save_loci':True}),('post-plot',{'minp_input':1,'plot':True,'plot_corr':True,'plot_legend':'all','plot_individual':True}),('post-correlation-plot',{'minp_input':1,'plot_corr':True}),('post-eqtl-plot',{'minp_input':1,'plot':True,'plot_eqtl':True}),('post-scatter-plot',{'minp_input':1,'plot':True,'plot_scatter':True})]:
 r=json.loads(json.dumps(post));r['out']=str(B/name);r['options']=options
 if 'omnibus' in name or 'correlation' in name:r['inputs'].update(association=str(fixture/'omnibus.tsv'),weights_base=str(fixture))
 if options.get('plot'):r['inputs']['gene_annotation']=str(fixture/'gene-annotation')
 if options.get('plot_corr'):
  with (F/'workflow-windows-library-1/PACKAGES.tsv').open(encoding='utf-8-sig',newline='') as stream:
   versions={row['Package']:row['Version'] for row in csv.DictReader(stream,delimiter='\t')}
  r['expected_packages']['corrplot']=versions['corrplot']
 run(name,r,expected=0)
# Actual re-execution, never copied scientific output.
for name,parent,platform in [('replay-assoc','assoc-w21','windows21'),('replay-top1','train-top1','linux21')]:
 translate=linux if platform=='linux21' else str;r=dict(schema_version=1,mode='REPLAY',analysis=translate(B/parent),out=translate(B/name));run(name,r,platform,0)
# Kernel/matrix refusal must precede the original analysis.
r=json.loads((B/'train-top1.json').read_bytes());r['out']=linux(B/'matrix-refusal');r['limits']={'maximum_matrix_cells':100};run('matrix-refusal',r,'linux21',2)
# Record actual JVM fit/source export scope. Full-family admission remains a separate task.
result=dict(status='PACKAGED_FUSION_DEVELOPMENT_CASES_EXECUTED',jar=dict(bytes=jar.stat().st_size,sha256=hashlib.sha256(jar.read_bytes()).hexdigest()),cases=records,all_expected=all(x['expected'] is None or x['exit_code']==x['expected'] for x in records),new_family_credit=0,whole_tool_qualified=False,synthetic_inputs_only=True)
(B/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(dict(all_expected=result['all_expected'],cases=len(records))),flush=True)