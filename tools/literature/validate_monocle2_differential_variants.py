# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent original-family controls, literal identifiers and actual atomic failure protocols."""
import argparse
import copy
import csv
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    build=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    prefix=base/'monocle2-linux-reference-setup-3/.pixi/envs/default';extra=base/'monocle2-hsmm-reference-setup-1/R-lib';reference=base/'monocle2-legacy-readiness-probe-6';source_pin=pin(Path(__file__));save(out/'STARTED.json',dict(artifact=artifact,source_pin=source_pin));(out/'validator-used.py').write_bytes(Path(__file__).read_bytes())
    def execute(command,label,expected=0,timeout=300):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=timeout)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode,expected=expected));assert r.returncode==expected,label+': inspect retained stdout/stderr'
    common=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','R_HOME='+linux(prefix/'lib/R'),'LD_LIBRARY_PATH='+linux(prefix/'lib'),'LANG=C','LC_ALL=C','LANGUAGE=en','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1',linux(prefix/'bin/Rscript'),'--vanilla']
    original=out/'independent-original.R';original.write_text('''a<-commandArgs(TRUE);out<-a[1];reference<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE)
Sys.setlocale("LC_CTYPE","C.UTF-8");suppressPackageStartupMessages(library(monocle));RhpcBLASctl::blas_set_num_threads(1L)
x<-readRDS(file.path(reference,"input.rds"));set.seed(20261107);ng<-12L;nc<-ncol(x$counts);cm<-x$cell_metadata;cm$progress_copy<-cm$progress
rownames(cm)<-c(' cell"β ',"NA",paste0("cell",3:nc));rownames(x$gene_metadata)<-c(' gene"β ',"NA",paste0("gene",3:nrow(x$counts)))
gm<-x$gene_metadata[seq_len(ng),,drop=FALSE];rownames(x$counts)<-rownames(x$gene_metadata);colnames(x$counts)<-rownames(cm)
sets<-list(counts=x$counts[seq_len(ng),,drop=FALSE],continuous=matrix(rnorm(ng*nc),ng)+outer(seq_len(ng)/10,cm$progress),binary=matrix(rbinom(ng*nc,1,0.35),ng),proportions=matrix(rbeta(ng*nc,2,5),ng))
for(n in names(sets)){dimnames(sets[[n]])<-list(rownames(gm),rownames(cm));encoded<-matrix(sprintf("%.17g",sets[[n]]),ng,dimnames=dimnames(sets[[n]]));write.table(data.frame(gene_id=rownames(gm),encoded,check.names=FALSE),file.path(out,paste0(n,".tsv")),sep="\\t",quote=FALSE,row.names=FALSE)}
encoded<-cm;for(n in names(encoded))if(is.double(encoded[[n]]))encoded[[n]]<-sprintf("%.17g",encoded[[n]])
write.table(data.frame(cell_id=rownames(cm),encoded,check.names=FALSE),file.path(out,"cells.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
write.table(data.frame(gene_id=rownames(gm),gm,check.names=FALSE),file.path(out,"genes.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
warnings<-character();outcomes<-list();result<-withCallingHandlers({
 for(id in c("nb-fixed-relative","nb-fixed-unscaled","nb-estimated","normal","binary","proportions")){
  family<-switch(id,"nb-fixed-relative"=VGAM::negbinomial.size(size=8),"nb-fixed-unscaled"=VGAM::negbinomial.size(size=8),"nb-estimated"=VGAM::negbinomial(),"normal"=VGAM::uninormal(),VGAM::binomialff())
  which<-switch(id,normal="continuous",binary="binary",proportions="proportions","counts");values<-methods::as(sets[[which]],"dgCMatrix")
  cds<-monocle::newCellDataSet(values,phenoData=methods::new("AnnotatedDataFrame",data=cm),featureData=methods::new("AnnotatedDataFrame",data=gm),lowerDetectionLimit=0.1,expressionFamily=family)
  relative<-id=="nb-fixed-relative";if(relative)cds<-BiocGenerics::estimateSizeFactors(cds)
  set.seed(20261107);failure<-NULL;tests<-tryCatch(monocle::differentialGeneTest(cds,fullModelFormulaStr="~condition+progress+sex",reducedModelFormulaStr="~progress+sex",relative_expr=relative,cores=1L,verbose=TRUE),error=function(e){failure<<-conditionMessage(e);NULL})
  outcomes[[id]]<-list(returned=is.null(failure),error=failure);if(!is.null(failure))next
  saveRDS(tests,file.path(out,paste0(id,"-original.rds")));saveRDS(cds,file.path(out,paste0(id,"-original-cds.rds")));stopifnot(nrow(tests)==ng,all(tests$status=="OK"),all(is.finite(tests$pval)),all(is.finite(tests$qval)))
 }
},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")});jsonlite::write_json(as.list(warnings),file.path(out,"original-warnings.json"),pretty=TRUE);jsonlite::write_json(outcomes,file.path(out,"original-outcomes.json"),pretty=TRUE,auto_unbox=TRUE,null="null")
''',encoding='utf8',newline='\n');execute([*common,linux(original),linux(out),linux(reference),linux(extra),linux(prefix/'lib/R/library')],'independent-original')
    model=lambda *names:dict(intercept=True,terms=[dict(column=n) for n in names])
    template=dict(schema_version=1,mode='DIFF',version='2.26.0',matrix_format='dense-tsv',expression_units='raw-integer-counts',expression=linux(out/'counts.tsv'),cell_metadata=linux(out/'cells.tsv'),gene_metadata=linux(out/'genes.tsv'),metadata_types=dict(progress=dict(type='numeric'),progress_copy=dict(type='numeric'),condition=dict(type='factor',levels=['control','case']),sex=dict(type='factor',levels=['female','male']),batch=dict(type='factor',levels=['a','b','c','d'])),rscript=linux(prefix/'bin/Rscript'),r_home=linux(prefix/'lib/R'),r_libraries=[linux(extra),linux(prefix/'lib/R/library')],timeout_seconds=120)
    case=dict(id='test',seed=20261107,family='negbinomial.size',size=8,lower_detection_limit=0.1,estimate_size_factors=False,dispersion=dict(estimate=False),relative_expr=False,full_model=model('condition','progress','sex'),reduced_model=model('progress','sex'))
    reports=[]
    def run(label,r,code):
        r['out']=linux(out/(label+'-result'));file=out/(label+'.json');save(file,r);execute(['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx1g','-cp',linux(jar),'org.jlinalg.cli.Monocle2DifferentialCli','--request',linux(file)],label,code)
        result=out/(label+'-result');status=json.loads((result/'analysis-status.json').read_bytes());assert status['result_admitted'] is (code==0)
        assert (result/'results').exists() is (code==0) and 'REQUIRED ACTION:' in (result/'WARNINGS.md').read_text(encoding='utf8')
        receipt=result/'worker/process-receipt.json'
        if receipt.exists():assert all(not x['alive_after_cleanup'] for x in json.loads(receipt.read_bytes())['owned_process_cleanup'])
        reports.append(dict(id=label,exit_code=code,result_admitted=status['result_admitted'],cause=status.get('cause'),owned_cleanup_checked=receipt.exists()));print(label+': expected exit '+str(code),flush=True)
    for id in ['nb-fixed-relative','nb-fixed-unscaled','nb-estimated','normal','binary','proportions']:
        r=copy.deepcopy(template);c=copy.deepcopy(case);c['id']=id
        if id=='nb-fixed-relative':c['relative_expr']=True;c['estimate_size_factors']=True
        if id=='nb-estimated':c['family']='negbinomial';del c['size']
        if id in ['normal','binary','proportions']:
            c['family']='uninormal' if id=='normal' else 'binomialff';del c['size'];r['expression_units']='continuous' if id=='normal' else id;r['expression']=linux(out/('continuous.tsv' if id=='normal' else id+'.tsv'))
        r['analyses']=[c];outcome=json.loads((out/'original-outcomes.json').read_bytes())[id];run(id,r,0 if outcome['returned'] else 2)
        if not outcome['returned']:
            report=json.loads((out/(id+'-result/worker/worker-report.json')).read_bytes());assert report['cases'][0]['original_error']['message']==outcome['error']
    compare=out/'compare.R';compare.write_text('''a<-commandArgs(TRUE);out<-a[1];.libPaths(c(a[-1],.Library),include.site=FALSE);suppressPackageStartupMessages(library(monocle));reports<-list()
for(id in c("nb-fixed-relative","nb-fixed-unscaled","nb-estimated","normal","binary","proportions")){
 if(!file.exists(file.path(out,paste0(id,"-original.rds"))))next
 path<-file.path(out,paste0(id,"-result"),"results",id);x<-readRDS(file.path(path,"tests.rds"));y<-readRDS(file.path(out,paste0(id,"-original.rds")));stopifnot(identical(rownames(x),rownames(y)),isTRUE(all.equal(x,y,tolerance=1e-13)))
 cds<-readRDS(file.path(path,"cds.rds"));original<-readRDS(file.path(out,paste0(id,"-original-cds.rds")));stopifnot(identical(Biobase::exprs(cds),Biobase::exprs(original)),identical(Biobase::pData(cds),Biobase::pData(original)),identical(BiocGenerics::sizeFactors(cds),BiocGenerics::sizeFactors(original)))
 reports[[id]]<-list(rows=nrow(x),all_original_fields_axes_metadata_and_size_factors_equal=TRUE)
};jsonlite::write_json(reports,file.path(out,"comparisons.json"),pretty=TRUE,auto_unbox=TRUE)
''',encoding='utf8',newline='\n');execute([*common,linux(compare),linux(out),linux(extra),linux(prefix/'lib/R/library')],'compare')
    for id,changes,code in [('unavailable-edition',dict(version='2.40.0'),3),('unavailable-data-dependency',dict(r_libraries=[linux(prefix/'lib/R/library')]),3),('timeout',dict(timeout_seconds=1),2),('output-capacity',dict(maximum_output_bytes=1),2),('input-capacity',dict(maximum_input_bytes=1),2),('json-capacity',dict(maximum_json_bytes=64),2)]:
        r=copy.deepcopy(template);r['analyses']=[copy.deepcopy(case)];r.update(changes);run(id,r,code)
    r=copy.deepcopy(template);good=copy.deepcopy(case);good['id']='valid-first';bad=copy.deepcopy(case);bad['id']='singular-second';bad['full_model']=model('condition','progress','sex','progress_copy');r['analyses']=[good,bad];run('atomic-original-failed-genes',r,2)
    failed=out/'atomic-original-failed-genes-result/worker/cases';assert json.loads((failed/'valid-first/source-status.json').read_bytes())['status']=='original-returned'
    table=json.loads((failed/'singular-second/tests.json').read_bytes());columns={c['name']:c for c in table['columns']};assert 'FAIL' in columns['status']['tokens'];assert set(columns['pval']['tokens'])=={'1'} and set(columns['qval']['tokens'])=={'1'}
    # Every portable atomic export retains the original token strings and kinds;
    # quoted TSV must preserve spaces, quotes, Unicode and literal NA IDs too.
    exports=0
    for result in out.glob('*-result/results'):
        for file in result.rglob('*.json'):
            table=json.loads(file.read_bytes())
            if not isinstance(table,dict) or 'columns' not in table:continue
            with file.with_suffix('.tsv').open(encoding='utf8',newline='') as stream:rows=list(csv.reader(stream,delimiter='\t'))
            assert rows==[[c['name'] for c in table['columns']],*list(map(list,zip(*(c['tokens'] for c in table['columns']))))];exports+=1
    outcomes=json.loads((out/'original-outcomes.json').read_bytes());assert {k:v['returned'] for k,v in outcomes.items()}=={'nb-fixed-relative':True,'nb-fixed-unscaled':True,'nb-estimated':False,'normal':True,'binary':False,'proportions':False};assert all(v['error']=='the condition has length > 1' for v in outcomes.values() if not v['returned']);assert exports==2*sum(c['returned'] for c in outcomes.values()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and pin(Path(__file__))==source_pin and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    save(out/'COMPLETED.json',dict(status='original-VGAM-success-and-failure-outcomes-matched-and-atomic-protocols-passed',artifact=artifact,source_pin=source_pin,comparisons=json.loads((out/'comparisons.json').read_bytes()),original_family_outcomes=outcomes,protocols=reports,atomic_table_exports_checked=exports,implementation_credit=False,whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original results/protocol assertions')
    main()
