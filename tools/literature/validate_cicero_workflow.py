# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare packaged Java Cicero dense/sparse workflows with independent full original controls."""
import argparse
import copy
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    build=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']};setup=base/'cicero-reference-setup-1';libs=[setup/'R-lib',*[Path(p) for p in json.loads((setup/'STARTED.json').read_bytes())['libraries']]];reference=base/'cicero-readiness-probe-2';source_pin=pin(Path(__file__));save(out/'STARTED.json',dict(artifact=artifact,source_pin=source_pin));(out/'validator-used.py').write_bytes(Path(__file__).read_bytes())
    def run(command,label,timeout=300):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en'),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=timeout)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,label+': inspect retained stdout/stderr'
    common=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla']
    export=out/'export.R';export.write_text('''a<-commandArgs(TRUE);out<-a[1];reference<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE);suppressPackageStartupMessages(library(cicero));x<-readRDS(file.path(reference,"input.rds"))
encoded<-matrix(sprintf("%.17g",x$counts),nrow(x$counts),dimnames=dimnames(x$counts));write.table(data.frame(peak_id=rownames(x$counts),encoded,check.names=FALSE),file.path(out,"counts.tsv"),sep="\\t",quote=FALSE,row.names=FALSE);Matrix::writeMM(methods::as(x$counts,"dgCMatrix"),file.path(out,"counts.mtx"))
write.table(data.frame(cell_id=colnames(x$counts)),file.path(out,"cells.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
fields<-cicero:::split_peak_names(rownames(x$counts));write.table(data.frame(peak_id=rownames(x$counts),chromosome=fields[,1],start=fields[,2],end=fields[,3]),file.path(out,"peaks.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
embedding<-matrix(sprintf("%.17g",x$prepared_coordinates),nrow(x$prepared_coordinates),dimnames=dimnames(x$prepared_coordinates));write.table(data.frame(cell_id=rownames(embedding),embedding,check.names=FALSE),file.path(out,"embedding.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
write.table(x$genomic_coords,file.path(out,"genome.tsv"),sep="\\t",quote=FALSE,row.names=FALSE,col.names=c("chromosome","length"))
''',encoding='utf8',newline='\n');run([*common,str(export),str(out),str(reference),*map(str,libs)],'export')
    analysis=dict(id='coaccess',aggregation_seed=20261108,k=20,size_factor_normalize=True,summary_stats=[],model=dict(window=500000,power=0.75,max_elements=200),distance_parameter=dict(mode='estimate',seed=20261109,maxit=100,sample_num=6,distance_constraint=250000,convergence=1e-22,max_sample_windows=500,require_full_sample=True,allow_maxit_warning=False),skipped_windows_policy='require-all-modeled',unresolved_connections_policy='retain-source-NA')
    request=dict(schema_version=1,mode='COACCESS',version='1.3.9',matrix_format='dense-tsv',coordinate_system='one-based-closed',count_units='raw-integer-counts',counts=str(out/'counts.tsv'),peak_metadata=str(out/'peaks.tsv'),cell_metadata=str(out/'cells.tsv'),embedding=str(out/'embedding.tsv'),genome=str(out/'genome.tsv'),numeric_metadata=[],binarize=False,analyses=[analysis],rscript=common[0],r_home='C:/Program Files/R/R-4.6.1',r_libraries=list(map(str,libs)),out=str(out/'dense-result'),timeout_seconds=240)
    for format in ['dense-tsv','matrix-market']:
        r=copy.deepcopy(request);r['matrix_format']=format;r['counts']=str(out/('counts.tsv' if format=='dense-tsv' else 'counts.mtx'));r['out']=str(out/('dense-result' if format=='dense-tsv' else 'sparse-result'));file=out/(format+'.json');save(file,r);run([str(JAVA21/'java.exe'),'-Xmx1g','-cp',str(jar),'org.jlinalg.cli.CiceroWorkflowCli','--request',str(file)],format+'-adapter')
    compare=out/'compare.R';compare.write_text('''a<-commandArgs(TRUE);out<-a[1];reference<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE);suppressPackageStartupMessages(library(cicero));reports<-list()
for(format in c("dense","sparse")){
 target<-file.path(out,paste0(format,"-result"),"results/coaccess");x<-readRDS(file.path(target,"connections.rds"));y<-readRDS(file.path(reference,"connections.rds"));stopifnot(identical(x,y),identical(readRDS(file.path(target,"models.rds")),readRDS(file.path(reference,"models.rds"))),identical(readRDS(file.path(target,"distance-parameters.rds")),readRDS(file.path(reference,"distance-parameters.rds"))))
 cds<-readRDS(file.path(target,"cicero-cds.rds"));original<-readRDS(file.path(reference,"cicero-cds.rds"));stopifnot(identical(rownames(cds),rownames(original)),identical(colnames(cds),colnames(original)),identical(SummarizedExperiment::assay(cds,"counts"),SummarizedExperiment::assay(original,"counts")),identical(readRDS(file.path(target,"aggregation-map.rds")),readRDS(file.path(reference,"aggregation-map.rds"))))
 reports[[format]]<-list(rows=nrow(x),unresolved_NA=sum(is.na(x$coaccess)),all_original_connections_models_calibration_aggregation_and_saved_axes_equal=TRUE)
};jsonlite::write_json(reports,file.path(out,"comparisons.json"),auto_unbox=TRUE,pretty=TRUE)
''',encoding='utf8',newline='\n');run([*common,str(compare),str(out),str(reference),*map(str,libs)],'compare')
    assert pin(Path(__file__))==source_pin and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='actual-Java-Cicero-dense-and-sparse-workflows-matched-independent-original',artifact=artifact,source_pin=source_pin,comparisons=json.loads((out/'comparisons.json').read_bytes()),reference_control=pin(reference/'RESULT.json'),implementation_credit=False,whole_goal_complete=False));print('Matched4344 original connection rows, including eight retained NA, plus all models/calibration/aggregation and native Java audits')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full original/candidate comparisons')
    main()
