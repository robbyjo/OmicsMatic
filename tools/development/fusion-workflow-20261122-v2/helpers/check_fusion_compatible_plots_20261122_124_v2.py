from pathlib import Path
import csv,json,shutil,subprocess,time
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';P=F/'workflow-packaged-5';B=F/'workflow-compatible-plots-2';B.mkdir();data=B/'data';data.mkdir();jar=F/'workflow-normal-build-7/project-build/cli/omicsmatic-0.4.0.jar';rows=list(csv.DictReader((P/'data/omnibus.tsv').open(encoding='utf-8'),delimiter='\t'))
for i,row in enumerate(rows):
 panel='panel-'+('A' if i==0 else 'B');(data/panel).mkdir();name='g'+str(i+1)+'.wgt.RDat';shutil.copyfile(P/'data'/panel/name,data/panel/name);row['ID']='G'+str(i+1);row['PANEL']='A' if i==0 else 'B'
with (data/'plot-input.tsv').open('w',encoding='utf-8',newline='') as stream:
 w=csv.DictWriter(stream,fieldnames=list(rows[0]),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(rows)
(data/'gene-annotation').write_text('1 105000 106000 AUX\n',encoding='ascii')
base=json.loads((P/'post-joint.json').read_bytes());base['r_libraries'].insert(0,str(F/'workflow-windows-corrplot084-2/library'));base['inputs'].update(association=str(data/'plot-input.tsv'),weights_base=str(data),gene_annotation=str(data/'gene-annotation'));records=[]
for name,options in [('correlation',{'minp_input':1,'plot_corr':True}),('eqtl-individual',{'minp_input':1,'plot':True,'plot_eqtl':True,'plot_individual':True}),('scatter-individual',{'minp_input':1,'plot':True,'plot_scatter':True,'plot_individual':True})]:
 r=json.loads(json.dumps(base));r['out']=str(B/name);r['options']=options
 if options.get('plot_corr'):r['expected_packages']['corrplot']='0.84'
 file=B/(name+'.json');file.write_text(json.dumps(r,indent=2)+'\n');args=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx2g','-jar',str(jar),'fusion-tools','--request',str(file)];started=time.time()
 with (B/(name+'.stdout')).open('xb') as out,(B/(name+'.stderr')).open('xb') as err:p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);code=p.wait()
 records.append(dict(case=name,exit_code=code,seconds=time.time()-started));(B/'PROGRESS.json').write_text(json.dumps(records,indent=2)+'\n');print(name,code,flush=True);assert code==0
 messages=(B/name/'worker/original.stderr.log').read_text(errors='replace');assert 'not a graphical parameter' not in messages
(B/'RESULT.json').write_text(json.dumps(dict(status='PASS_EXPLICIT_COMPATIBLE_FIGURE_RUNTIME',cases=records,corrplot='0.84',scientific_settings_or_original_source_patched=False,existing_095_runtime_retained=True,synthetic_fixture_with_explicit_PANEL_labels=True),indent=2)+'\n')