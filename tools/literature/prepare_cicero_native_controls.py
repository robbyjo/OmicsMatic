# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture actual original Cicero reconciliation/penalty/window and R4.6 cov2cor outcomes for Java."""
import argparse
import gzip
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();setup=base/'cicero-reference-setup-1';libs=[setup/'R-lib',*[Path(p) for p in json.loads((setup/'STARTED.json').read_bytes())['libraries']]];profile=ROOT/'src/main/resources/org/jlinalg/omics/cicero-workflow/SOURCE-PROFILE.json'
    script=out/'controls.R';script.write_text('''a<-commandArgs(TRUE);out<-a[1];profile_path<-a[2];source_root<-a[3];.libPaths(c(a[-(1:3)],.Library),include.site=FALSE);suppressPackageStartupMessages(library(cicero));profile<-jsonlite::read_json(profile_path,simplifyVector=FALSE);stopifnot(as.character(getRversion())==profile$r_version,R.version$platform==profile$platform)
for(n in names(profile$required_packages))stopifnot(as.character(packageVersion(n))==profile$required_packages[[n]])
definitions<-list();ns<-asNamespace("cicero");for(file in list.files(file.path(source_root,"R"),pattern="[.]R$",full.names=TRUE))for(e in parse(file,keep.source=FALSE))if(is.call(e)&&length(e)==3L&&is.symbol(e[[1]])&&as.character(e[[1]]) %in% c("<-","=")&&is.symbol(e[[2]])&&is.call(e[[3]])&&identical(e[[3]][[1]],as.name("function")))definitions[[as.character(e[[2]])]]<-e[[3]]
for(n in c("reconcile","get_rho_mat","get_genomic_range")){f<-eval(definitions[[n]],ns);g<-get(n,ns,inherits=FALSE);stopifnot(identical(body(f),body(g)),identical(formals(f),formals(g)))}
stopifnot(paste(deparse(body(stats::cov2cor)),collapse="\\n")==profile$cov2cor_body)
token<-function(x)if(is.nan(x))"NaN" else if(is.na(x))"NA" else if(is.infinite(x)){if(x>0)"Inf" else "-Inf"}else sprintf("%.17g",x)
controls<-list();add<-function(kind,input,result){controls[[length(controls)+1L]]<<-list(id=length(controls)+1L,kind=kind,input=input,result=if(is.logical(result))result else token(result))}
reconcile<-get("reconcile",ns);penalty<-get("get_rho_mat",ns);add("reconcile",list(values=list()),reconcile(numeric()))
grid<-c(-1,-0.5,-1e-10,-0,0,1e-10,0.5,1)
for(x in c(grid,1e-320,-1e-320))add("reconcile",list(values=as.list(vapply(x,token,character(1)))),reconcile(x))
for(x in grid)for(y in grid)add("reconcile",list(values=as.list(vapply(c(x,y),token,character(1)))),reconcile(c(x,y)))
set.seed(20261107);for(i in seq_len(160)){x<-runif(i%%17+1,-1,1);if(i%%3==0)x<-abs(x);if(i%%3==1)x<--abs(x);add("reconcile",list(values=as.list(vapply(x,token,character(1)))),reconcile(x))}
for(d in c(0,1,999,1000,1001,10000,250000,500000,1e150))for(v in c(0,0.5,2,1e12))for(s in c(0.1,0.5,0.75))add("penalty",list(distance=token(d),parameter=token(v),power=token(s)),penalty(matrix(d,1),v,s)[1,1])
for(x in c(1e-100,0.001,1,1000,1e100))for(y in c(1e-100,0.01,1,100,1e100))for(r in c(-1,-0.3,0,0.3,1)){
covariance<-r*sqrt(x)*sqrt(y);m<-matrix(c(x,covariance,covariance,y),2);add("correlation",list(covariance=token(covariance),row_diagonal=token(x),column_diagonal=token(y)),stats::cov2cor(m)[1,2])}
peaks<-data.frame(chr="1",bp1=c(0,1,1,2,2,0,25,0,100,75),bp2=c(100,101,99,100,99,101,75,1,101,200),row.names=paste0("peak",1:10))
counts<-matrix(1,nrow(peaks),3,dimnames=list(rownames(peaks),paste0("cell",1:3)));cds<-monocle3::new_cell_data_set(counts,cell_metadata=data.frame(row.names=colnames(counts)),gene_metadata=peaks)
for(bounds in list(c(1,100),c(25,75),c(100,200))){gr<-GenomicRanges::GRanges("1",IRanges::IRanges(bounds[1],bounds[2]));selected<-rownames(get("get_genomic_range",ns)(gr,cds,1));for(i in seq_len(nrow(peaks)))add("window",list(start=peaks$bp1[i],end=peaks$bp2[i],window_start=bounds[1],window_end=bounds[2]),rownames(peaks)[i] %in% selected)}
jsonlite::write_json(list(original_package_loaded=TRUE,Cicero_version=as.character(packageVersion("cicero")),R_version=as.character(getRversion()),source_body_guards=list("reconcile","get_rho_mat","get_genomic_range"),controls=controls),file.path(out,"controls.json"),auto_unbox=TRUE,pretty=TRUE,null="null",digits=NA)
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
''',encoding='utf8',newline='\n')
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(profile),str(base/'related-source-intake-1/cicero-m3/source'),*map(str,libs)]
    with (out/'stdout.log').open('xb') as stdout,(out/'stderr.log').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en'),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=120)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Read original source-control transcripts';raw=(out/'controls.json').read_bytes();document=json.loads(raw);assert document['original_package_loaded'] is True;resource=ROOT/'src/test/resources/org/jlinalg/singlecell/cicero-coaccessibility/original-controls-v1.json.gz';assert not resource.exists();resource.parent.mkdir(parents=True);resource.write_bytes(gzip.compress(raw,mtime=0));save(out/'COMPLETED.json',dict(status='captured-actual-original-Cicero-native-controls',source_pin=pin(Path(__file__)),fixture=pin(resource),uncompressed=pin(out/'controls.json'),controls=len(document['controls']),source_profile=pin(profile),analysis_admitted=False));print(json.dumps(dict(controls=len(document['controls']),fixture=pin(resource),raw=pin(out/'controls.json'))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original native-control bindings')
    main()
