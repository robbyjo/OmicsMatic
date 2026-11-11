# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent direct original-family controls, packaged adapter comparisons and refusal protocols."""
import argparse
import copy
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    proof=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=proof['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    save(out/'STARTED.json',dict(source_pin=pin(Path(__file__)),artifact=artifact));(out/'validator-used.py').write_bytes(Path(__file__).read_bytes())
    setup=base/'reference-setup-3';libraries=[str(setup/'R-lib'),*json.loads((setup/'STARTED.json').read_bytes())['libraries']]
    rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe';env=dict(os.environ,LC_ALL='C',LANG='C',LANGUAGE='en',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1')
    def rrun(script,arguments,label):
        command=[rscript,'--vanilla',str(script),*map(str,arguments),*libraries]
        r=subprocess.run(command,cwd=ROOT,env=env,capture_output=True,timeout=300)
        (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
        assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    direct=out/'direct-original.R'
    direct.write_text('''a<-commandArgs(TRUE);out<-a[1];.libPaths(c(a[-1],.Library),include.site=FALSE)
suppressPackageStartupMessages(library(BPCells));suppressPackageStartupMessages(library(monocle3))
RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);set.seed(20261108)
progress<-seq(0,1,length.out=240);batch<-factor(rep(c("a","b"),120));cm<-data.frame(progress=progress,batch=batch,row.names=sprintf("cell-%03d",1:240))
lambda<-outer(1:40,progress,function(g,t)exp(1.4+g/50+0.4*t))
counts<-matrix(rnbinom(length(lambda),size=2,mu=as.vector(lambda)),nrow=40);counts[matrix(runif(length(counts))<0.2,nrow=40)]<-0L
b<-matrix(rbinom(2400,1,rep(plogis(-0.4+0.8*progress),each=10)),nrow=10);binary<-rbind(b,1-b)
reports<-list()
for(dataset in c("counts","binary")) {
 x<-if(dataset=="counts")counts else binary;rownames(x)<-sprintf("gene-%03d",seq_len(nrow(x)));colnames(x)<-rownames(cm)
 gm<-data.frame(gene_short_name=rownames(x),row.names=rownames(x));target<-file.path(out,dataset);dir.create(target)
 write.table(data.frame(gene_id=rownames(x),x,check.names=FALSE),file.path(target,"counts.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
 export<-cm;export$progress<-sprintf("%.17g",export$progress)
 write.table(data.frame(cell_id=rownames(export),export),file.path(target,"cell-metadata.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
 write.table(data.frame(gene_id=rownames(gm),gm),file.path(target,"gene-metadata.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
 families<-if(dataset=="binary")"binomial" else c("quasipoisson","poisson","negbinomial","gaussian","zipoisson","zinegbinomial","spline")
 for(family in families) {
  set.seed(20261107);cds<-monocle3::new_cell_data_set(x,cell_metadata=cm,gene_metadata=gm)
  f<-if(family=="spline")"quasipoisson" else family;formula<-if(family=="spline")"~splines::ns(progress, df=3)+batch" else "~progress+batch"
  fit<-monocle3::fit_models(cds,model_formula_str=formula,expression_family=f,cores=1L,clean_model=FALSE,verbose=TRUE)
  coefficients<-monocle3::coefficient_table(fit);saveRDS(fit,file.path(target,paste0(family,"-models.rds")));saveRDS(coefficients,file.path(target,paste0(family,"-coefficients.rds")))
  reports[[paste(dataset,family,sep="/")]]<-list(genes=nrow(x),models=nrow(fit),coefficient_rows=nrow(coefficients),statuses=as.list(table(fit$status)),all_status_OK=!anyNA(fit$status)&&all(fit$status=="OK"))
 }
}
jsonlite::write_json(reports,file.path(out,"direct-original.json"),pretty=TRUE,auto_unbox=TRUE)
''',encoding='utf8',newline='\n')
    rrun(direct,[out],'direct-original');reference=json.loads((out/'direct-original.json').read_bytes());runs=[]
    def java(request,label,expected):
        path=out/(label+'.json');save(path,request)
        command=[str(JAVA21/'java.exe'),'-Xmx1g','-cp',str(jar),'org.jlinalg.cli.Monocle3WorkflowCli','--request',str(path)]
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
            r=subprocess.run(command,cwd=ROOT,env=env,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=300)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==expected,label+': read retained stderr'
        target=out/request['out'];state=json.loads((target/'analysis-status.json').read_bytes());assert state['result_admitted']==(expected==0)
        assert (target/'results').exists()==(expected==0)
        assert 'REQUIRED ACTION:' in (target/'WARNINGS.md').read_text(encoding='utf8')
        assert (target/'manifest.yaml').is_file()
        receipt=target/'worker/process-receipt.json'
        if receipt.exists():assert all(not x['alive_after_cleanup'] for x in json.loads(receipt.read_bytes())['owned_process_cleanup'])
        runs.append(dict(label=label,exit_code=r.returncode,status=state['status']));print(label,state['status'],flush=True)
    requests={}
    for dataset in ['counts','binary']:
        analyses=[]
        for key,value in reference.items():
            d,family=key.split('/')
            if d!=dataset:continue
            if not value['all_status_OK']:
                assert family=='gaussian' and value['statuses']=={'FAIL':40},(key,value)
                continue
            analyses.append(dict(id=family,seed=20261107,fit_models=dict(family='quasipoisson' if family=='spline' else family,intercept=True,
                terms=[dict(column='progress',**({'spline_df':3} if family=='spline' else {})),dict(column='batch')])))
        request=dict(schema_version=1,mode='RUN',version='1.4.27',count_units='raw-integer-counts',counts=dataset+'/counts.tsv',cell_metadata=dataset+'/cell-metadata.tsv',gene_metadata=dataset+'/gene-metadata.tsv',
            metadata_types={'progress':{'type':'numeric'},'batch':{'type':'factor','levels':['a','b']}},rscript=rscript,r_libraries=libraries,analyses=analyses,out=dataset+'-run',timeout_seconds=240)
        java(request,dataset+'-adapter',0);requests[dataset]=request
    gaussian=copy.deepcopy(requests['counts']);gaussian['analyses']=[dict(id='gaussian',seed=20261107,fit_models=dict(family='gaussian',intercept=True,terms=[dict(column='progress'),dict(column='batch')]))];gaussian['out']='gaussian-run'
    java(gaussian,'original-gaussian-status-failure',2)
    atomic=copy.deepcopy(requests['counts']);atomic['analyses']=[atomic['analyses'][0],gaussian['analyses'][0]];atomic['out']='atomic-original-failure-run';java(atomic,'atomic-original-failure',2)
    entries=json.loads((out/'atomic-original-failure-run/worker/worker-report.json').read_bytes())['cases']
    assert [x['status'] for x in entries]==['original-returned','FAIL2_ORIGINAL_ERROR']
    compare=out/'compare-families.R'
    compare.write_text('''a<-commandArgs(TRUE);out<-a[1];.libPaths(c(a[-1],.Library),include.site=FALSE)
suppressPackageStartupMessages(library(BPCells));suppressPackageStartupMessages(library(monocle3))
reports<-list();ref<-jsonlite::fromJSON(file.path(out,"direct-original.json"),simplifyVector=FALSE)
for(key in names(ref)) {
 parts<-strsplit(key,"/",fixed=TRUE)[[1]];dataset<-parts[1];family<-parts[2]
 for(type in c("models","coefficients")) {
  candidate<-if(family=="gaussian")file.path(out,"gaussian-run","worker","cases",family) else file.path(out,paste0(dataset,"-run"),"results",family)
  x<-readRDS(file.path(candidate,paste0(type,".rds")));y<-readRDS(file.path(out,dataset,paste0(family,"-",type,".rds")))
  keep<-vapply(y,function(z)is.atomic(z)&&is.null(dim(z)),logical(1));stopifnot(identical(names(x),names(y)),isTRUE(all.equal(as.data.frame(x[,keep,drop=FALSE]),as.data.frame(y[,keep,drop=FALSE]),tolerance=1e-13)))
 }
 reports[[key]]<-list(all_atomic_model_and_coefficient_columns_equal=TRUE,coefficient_rows=ref[[key]]$coefficient_rows,result_admitted=ref[[key]]$all_status_OK)
}
jsonlite::write_json(reports,file.path(out,"family-comparisons.json"),pretty=TRUE,auto_unbox=TRUE)
''',encoding='utf8',newline='\n')
    rrun(compare,[out],'compare-families')
    # Every negative attempt has a NEW output, retains raw inputs and admits no result.
    for label,changes,expected in [('missing-runtime',{'rscript':str(out/'absent-Rscript')},3),('old-edition',{'version':'1.3.1'},3),
        ('timeout',{'timeout_seconds':1},2),('output-capacity',{'maximum_output_bytes':1},2),('input-capacity',{'maximum_entries':1},2)]:
        request=copy.deepcopy(requests['counts']);request.update(changes);request['out']=label+'-run';java(request,label,expected)
    # Original all-zero-gene failures must remain explicit; no gene may be dropped to force success.
    dataset=out/'counts';text=(dataset/'counts.tsv').read_text(encoding='utf8');rows=text.splitlines();fields=rows[1].split('\t');rows[1]='\t'.join([fields[0],*['0']*(len(fields)-1)])
    (out/'zero-gene.tsv').write_text('\n'.join(rows)+'\n',encoding='utf8',newline='\n')
    request=copy.deepcopy(requests['counts']);request['counts']='zero-gene.tsv';request['analyses']=request['analyses'][:2];request['out']='original-gene-failure-run';java(request,'original-gene-failure',2)
    assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
    save(out/'COMPLETED.json',dict(status='passed-independent-original-family-and-failure-protocol-controls',artifact=artifact,source_pin=pin(Path(__file__)),
        families=json.loads((out/'family-comparisons.json').read_bytes()),runs=runs,implementation_credit=False,whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original family and admission checks')
    main()
