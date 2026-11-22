from pathlib import Path
import json,shutil,subprocess
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';B=F/'workflow-mhc-fixture-1';B.mkdir();(B/'weights').mkdir();old=T/'examples/fusion-original/data'
shutil.copyfile(old/'ref.1.bed',B/'ref.6.bed');shutil.copyfile(old/'ref.1.fam',B/'ref.6.fam');(B/'ref.6.bim').write_text(''.join(f'6 synth{i:03d} 0 {28000000+i*1000} A C\n' for i in range(12)),encoding='ascii')
r=B/'generate.R';r.write_text('''args<-commandArgs(TRUE)
for(i in 1:3){
 load(paste0(args[1],"/weights/g",i,".wgt.RDat"))
 snps<-read.table(paste0(args[2],"/ref.6.bim"),stringsAsFactors=FALSE)
 save(wgt.matrix,snps,cv.performance,hsq,hsq.pv,N.tot,file=paste0(args[2],"/weights/g",i,".wgt.RDat"))
}
write.table(data.frame(WGT=paste0("g",1:3,".wgt.RDat"),ID=paste0("SYNTH_MHC_",1:3),CHR=6,P0=28000000,P1=28011000,N=64),paste0(args[2],"/weights.pos"),sep="\\t",quote=FALSE,row.names=FALSE)
''',encoding='utf-8')
p=subprocess.run(['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(r),str(old),str(B)],cwd=T,capture_output=True);(B/'stdout').write_bytes(p.stdout);(B/'stderr').write_bytes(p.stderr);assert p.returncode==0
(B/'SYNTHETIC.json').write_text(json.dumps(dict(synthetic=True,samples=64,variants=12,chr=6,all_features_inside_source_MHC_interval=True,no_real_people_or_MHC_biological_inference=True),indent=2)+'\n')
p=Path('D:/projects/JLinAlg/build/qualify_fusion_packaged_20261122_124_v6.py');s=p.read_text(encoding='utf-8');mark="r=json.loads(json.dumps(base));r['out']=str(B/'assoc-w25')";assert s.count(mark)==1
s=s.replace(mark,"""r=json.loads(json.dumps(base));r['out']=str(B/'mhc-split');mhc=F/'workflow-mhc-fixture-1';r['inputs'].update(weights=str(mhc/'weights.pos'),weights_dir=str(mhc/'weights'),ref_ld_prefix=str(mhc/'ref.'),chromosome='6');run('mhc-split',r,expected=0)
"""+mark);p.write_text(s,encoding='utf-8')
for source_name,target_name,replacements in [
 ('check_fusion_refusals_20261122_124_v3.py','check_fusion_refusals_20261122_124_v4.py',{'workflow-refusal-controls-3':'workflow-refusal-controls-4','workflow-packaged-5/assoc-w21':'workflow-packaged-6/assoc-w21','workflow-normal-build-7/project-build':'workflow-normal-build-8/project-build'}),
 ('check_fusion_runtime_controls_20261122_124_v3.py','check_fusion_runtime_controls_20261122_124_v4.py',{'workflow-runtime-controls-3':'workflow-runtime-controls-4','workflow-normal-build-7':'workflow-normal-build-8'}),
 ('check_fusion_training_replays_20261122_124_v2.py','check_fusion_training_replays_20261122_124_v3.py',{'workflow-training-replays-2':'workflow-training-replays-3','workflow-normal-build-7':'workflow-normal-build-8','workflow-packaged-5':'workflow-packaged-6'}),
 ('check_fusion_compatible_plots_20261122_124_v2.py','check_fusion_compatible_plots_20261122_124_v3.py',{'workflow-compatible-plots-2':'workflow-compatible-plots-3','workflow-normal-build-7':'workflow-normal-build-8','workflow-packaged-5':'workflow-packaged-6'}),
 ('prove_fusion_workflow_20261122_124_v2.py','prove_fusion_workflow_20261122_124_v3.py',{'workflow-independent-checks-2':'workflow-independent-checks-3','workflow-normal-build-7':'workflow-normal-build-8','workflow-packaged-5':'workflow-packaged-6'})]:
 raw=(Path('D:/projects/JLinAlg/build')/source_name).read_text(encoding='utf-8')
 for a,b in replacements.items():raw=raw.replace(a,b)
 (Path('D:/projects/JLinAlg/build')/target_name).write_text(raw,encoding='utf-8')
print('MHC source-state fixture and fresh profile controls prepared',flush=True)