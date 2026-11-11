# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare the actual packaged Java Monocle2 adapter with retained independent full original fits."""
import argparse
import copy
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    build=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    prefix=base/'monocle2-linux-reference-setup-3/.pixi/envs/default';extra=base/'monocle2-hsmm-reference-setup-1/R-lib';reference=base/'monocle2-legacy-readiness-probe-6';source_pin=pin(Path(__file__))
    save(out/'STARTED.json',dict(source_pin=source_pin,artifact=artifact));(out/'validator-used.py').write_bytes(Path(__file__).read_bytes())
    def execute(command,label,timeout=300):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=timeout)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,label+': inspect retained stdout/stderr'
    common=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','R_HOME='+linux(prefix/'lib/R'),'LD_LIBRARY_PATH='+linux(prefix/'lib'),'LANG=C','LC_ALL=C','LANGUAGE=en','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1',linux(prefix/'bin/Rscript'),'--vanilla']
    export=out/'export.R';export.write_text('''a<-commandArgs(TRUE);out<-a[1];reference<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE)
suppressPackageStartupMessages(library(monocle));x<-readRDS(file.path(reference,"input.rds"))
write.table(data.frame(gene_id=rownames(x$counts),x$counts,check.names=FALSE),file.path(out,"expression.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
Matrix::writeMM(methods::as(x$counts,"dgCMatrix"),file.path(out,"expression.mtx"))
cm<-x$cell_metadata;for(n in names(cm))if(is.double(cm[[n]]))cm[[n]]<-sprintf("%.17g",cm[[n]])
write.table(data.frame(cell_id=rownames(cm),cm,check.names=FALSE),file.path(out,"cells.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
write.table(data.frame(gene_id=rownames(x$gene_metadata),x$gene_metadata),file.path(out,"genes.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
''',encoding='utf8',newline='\n');execute([*common,linux(export),linux(out),linux(reference),linux(extra),linux(prefix/'lib/R/library')],'export')
    linear=lambda *names:dict(intercept=True,terms=[dict(column=n) for n in names])
    analyses=[]
    for id,relative in [('condition-adjusted-relative',True),('condition-adjusted-unscaled',False),('progress-spline',True)]:
        full=linear('condition','progress','sex');reduced=linear('progress','sex')
        if id=='progress-spline':full=dict(intercept=True,terms=[dict(column='progress',spline_df=3),dict(column='batch')]);reduced=linear('batch')
        analyses.append(dict(id=id,seed=20261107,family='negbinomial.size',lower_detection_limit=0.1,estimate_size_factors=True,dispersion=dict(estimate=True,model=linear(),relative_expr=True,min_cells_detected=1,remove_outliers=True),relative_expr=relative,full_model=full,reduced_model=reduced))
    request=dict(schema_version=1,mode='DIFF',version='2.26.0',matrix_format='dense-tsv',expression_units='raw-integer-counts',expression=linux(out/'expression.tsv'),cell_metadata=linux(out/'cells.tsv'),gene_metadata=linux(out/'genes.tsv'),
        metadata_types=dict(progress=dict(type='numeric'),condition=dict(type='factor',levels=['control','case']),sex=dict(type='factor',levels=['female','male']),batch=dict(type='factor',levels=['a','b','c','d'])),
        rscript=linux(prefix/'bin/Rscript'),r_home=linux(prefix/'lib/R'),r_libraries=[linux(extra),linux(prefix/'lib/R/library')],analyses=analyses,out=linux(out/'dense-result'),timeout_seconds=240)
    for format in ['dense-tsv','matrix-market']:
        r=copy.deepcopy(request);r['matrix_format']=format;r['expression']=linux(out/('expression.tsv' if format=='dense-tsv' else 'expression.mtx'));r['out']=linux(out/('dense-result' if format=='dense-tsv' else 'sparse-result'));file=out/(format+'.json');save(file,r)
        execute(['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx1g','-cp',linux(jar),'org.jlinalg.cli.Monocle2DifferentialCli','--request',linux(file)],format+'-adapter')
    compare=out/'compare.R';compare.write_text('''a<-commandArgs(TRUE);out<-a[1];reference<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE)
suppressPackageStartupMessages(library(monocle));reports<-list()
for(format in c("dense","sparse"))for(id in c("condition-adjusted-relative","condition-adjusted-unscaled","progress-spline")) {
 candidate<-file.path(out,paste0(format,"-result"),"results",id);x<-readRDS(file.path(candidate,"tests.rds"));y<-readRDS(file.path(reference,id,"original-tests.rds"))
 stopifnot(identical(rownames(x),rownames(y)),isTRUE(all.equal(x,y,tolerance=1e-13)))
 cds<-readRDS(file.path(candidate,"cds.rds"));original<-readRDS(file.path(reference,"dispersions-cds.rds"))
 stopifnot(identical(rownames(cds),rownames(original)),identical(colnames(cds),colnames(original)),identical(Biobase::exprs(cds),Biobase::exprs(original)),identical(BiocGenerics::sizeFactors(cds),BiocGenerics::sizeFactors(original)),isTRUE(all.equal(monocle::dispersionTable(cds),monocle::dispersionTable(original),tolerance=1e-13)))
 reports[[paste(format,id,sep="/")]]<-list(rows=nrow(x),all_original_columns_equal=TRUE,counts_axes_size_factors_and_dispersions_equal=TRUE)
}
jsonlite::write_json(reports,file.path(out,"comparisons.json"),pretty=TRUE,auto_unbox=TRUE)
''',encoding='utf8',newline='\n');execute([*common,linux(compare),linux(out),linux(reference),linux(extra),linux(prefix/'lib/R/library')],'compare')
    assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and pin(Path(__file__))==source_pin
    save(out/'COMPLETED.json',dict(status='actual-Java-dense-and-sparse-Monocle2-adapter-matched-independent-original',artifact=artifact,source_pin=source_pin,comparisons=json.loads((out/'comparisons.json').read_bytes()),original_reference=pin(reference/'RESULT.json'),implementation_credit=False,whole_goal_complete=False))
    print('Actual packaged Java adapter matched all original fields, 720 differential rows and dense/sparse saved state')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual adapter/original comparisons')
    main()
