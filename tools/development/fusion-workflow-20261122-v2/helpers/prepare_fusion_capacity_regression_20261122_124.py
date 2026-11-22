from pathlib import Path
import json,shutil,subprocess
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';B=F/'workflow-extra-weight-fixture-1';B.mkdir();(B/'weights').mkdir();source=T/'examples/fusion-original/data'
r=B/'generate.R';r.write_text('''args<-commandArgs(TRUE)
for(i in 1:3){
 load(paste0(args[1],"/weights/g",i,".wgt.RDat"))
 ids<-sprintf("unmatched%03d",1:200)
 extra<-data.frame(V1=rep(1L,200),V2=ids,V3=rep(0,200),V4=200000+seq_len(200),V5=rep("A",200),V6=rep("C",200),stringsAsFactors=FALSE)
 snps<-rbind(snps,extra)
 wgt.matrix<-rbind(wgt.matrix,matrix(.1,nrow=200,ncol=ncol(wgt.matrix),dimnames=list(ids,colnames(wgt.matrix))))
 save(wgt.matrix,snps,cv.performance,hsq,hsq.pv,N.tot,file=paste0(args[2],"/weights/g",i,".wgt.RDat"))
}
''',encoding='utf-8')
p=subprocess.run(['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(r),str(source),str(B)],cwd=T,capture_output=True);(B/'stdout').write_bytes(p.stdout);(B/'stderr').write_bytes(p.stderr);assert p.returncode==0
shutil.copyfile(source/'weights.pos',B/'weights.pos');(B/'SYNTHETIC.json').write_text(json.dumps(dict(synthetic=True,each_weight_variants=212,reference_matched_variants=12,unmatched_ids='unmatched001..unmatched200',no_original_inputs_modified=True),indent=2)+'\n')
p=Path('D:/projects/JLinAlg/build/qualify_fusion_packaged_20261122_124_v5.py');s=p.read_text(encoding='utf-8');mark='# Record actual JVM fit/source export scope.';assert s.count(mark)==1
s=s.replace(mark,"""# LD capacity is based on matched variants, retaining unmatched source rows as inputs.
r=json.loads(json.dumps(base));r['out']=str(B/'matched-LD-cap-valid');r['inputs'].update(weights=str(F/'workflow-extra-weight-fixture-1/weights.pos'),weights_dir=str(F/'workflow-extra-weight-fixture-1/weights'));r['limits']={'maximum_matrix_cells':144};run('matched-LD-cap-valid',r,expected=0)
"""+mark);p.write_text(s,encoding='utf-8')
for source_name,target_name,replacements in [
 ('check_fusion_refusals_20261122_124_v2.py','check_fusion_refusals_20261122_124_v3.py',{'workflow-refusal-controls-2':'workflow-refusal-controls-3','workflow-packaged-4/assoc-w21':'workflow-packaged-5/assoc-w21','workflow-normal-build-6/project-build':'workflow-normal-build-7/project-build'}),
 ('check_fusion_runtime_controls_20261122_124_v2.py','check_fusion_runtime_controls_20261122_124_v3.py',{'workflow-runtime-controls-2':'workflow-runtime-controls-3','workflow-normal-build-6':'workflow-normal-build-7'}),
 ('check_fusion_training_replays_20261122_124.py','check_fusion_training_replays_20261122_124_v2.py',{'workflow-training-replays-1':'workflow-training-replays-2','workflow-normal-build-6':'workflow-normal-build-7','workflow-packaged-4':'workflow-packaged-5'}),
 ('check_fusion_compatible_plots_20261122_124.py','check_fusion_compatible_plots_20261122_124_v2.py',{'workflow-compatible-plots-1':'workflow-compatible-plots-2','workflow-normal-build-6':'workflow-normal-build-7','workflow-packaged-4':'workflow-packaged-5'}),
 ('prove_fusion_workflow_20261122_124.py','prove_fusion_workflow_20261122_124_v2.py',{'workflow-independent-checks-1':'workflow-independent-checks-2','workflow-normal-build-6':'workflow-normal-build-7','workflow-packaged-4':'workflow-packaged-5'})]:
 raw=(Path('D:/projects/JLinAlg/build')/source_name).read_text(encoding='utf-8')
 for a,b in replacements.items():raw=raw.replace(a,b)
 (Path('D:/projects/JLinAlg/build')/target_name).write_text(raw,encoding='utf-8')
print('matched-variant fixture and fresh-producer controls prepared')