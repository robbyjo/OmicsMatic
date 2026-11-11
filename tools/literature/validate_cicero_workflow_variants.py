# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Original fixed/raw/binary/Unicode/odd-window controls, chunk equivalence and real failure protocols."""
import argparse
import copy
import csv
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--baseline',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']};baseline=a.baseline.resolve();assert json.loads((baseline/'COMPLETED.json').read_bytes())['artifact']==artifact;source_pin=pin(Path(__file__));save(out/'STARTED.json',dict(artifact=artifact,source_pin=source_pin));(out/'validator-used.py').write_bytes(Path(__file__).read_bytes())
    setup=base/'cicero-reference-setup-1';libs=[setup/'R-lib',*[Path(p) for p in json.loads((setup/'STARTED.json').read_bytes())['libraries']]];common=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla'];request=json.loads((baseline/'dense-tsv.json').read_bytes());reports=[]
    def execute(command,label,expected=0,timeout=300):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en'),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=timeout)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode,expected=expected));assert r.returncode==expected,label+': inspect retained stdout/stderr'
    original=out/'original.R';original.write_text('''a<-commandArgs(TRUE);out<-a[1];reference<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE);Sys.setlocale("LC_CTYPE","English_United States.utf8");suppressPackageStartupMessages(library(cicero));RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);input<-readRDS(file.path(reference,"input.rds"));warnings<-character();reports<-list()
withCallingHandlers(for(id in c("fixed-relative","raw-aggregation","zero-peak","binary-unicode-odd")){
 folder<-file.path(out,id);dir.create(folder);counts<-input$counts;coords<-input$prepared_coordinates;genome<-input$genomic_coords;binary<-id=="binary-unicode-odd";normalize<-id!="raw-aggregation";k<-if(binary)10L else 20L;window<-if(binary)500001 else 500000;power<-if(binary)0.9 else 0.75
 if(id=="zero-peak")counts[1,]<-0
 if(binary){rownames(counts)<-sub("^chr1_","chrβ_alt_",rownames(counts));colnames(counts)[1:2]<-c(' cell"β ',"NA");rownames(coords)<-colnames(counts);coords[2,]<-coords[1,];genome<-data.frame(chromosome=c("β_alt","2"),length=c(2000000L,500000L))}
 cells<-data.frame(cell_id=colnames(counts),check.names=FALSE);if(binary)cells$progress<-seq(-2,2,length.out=ncol(counts))
 at<-which(counts!=0,arr.ind=TRUE);triplets<-data.frame(peak=rownames(counts)[at[,1]],cell=colnames(counts)[at[,2]],count=as.numeric(counts[at]),stringsAsFactors=FALSE);if(id=="zero-peak")triplets<-rbind(triplets,data.frame(peak=rownames(counts)[1],cell=colnames(counts)[1],count=0));cds<-cicero::make_atac_cds(triplets,binarize=binary)
 if(binary)SummarizedExperiment::colData(cds)$progress<-cells$progress[match(colnames(cds),cells$cell_id)]
 set.seed(20261108);aggregated<-cicero::make_cicero_cds(cds,coords,k=k,size_factor_normalize=normalize,summary_stats=if(binary)"progress" else NULL,silent=FALSE,return_agg_info=TRUE);ccds<-aggregated[[1]];models<-cicero::generate_cicero_models(ccds,2,s=power,window=window,max_elements=200,genomic_coords=genome);connections<-cicero::assemble_connections(models,silent=FALSE)
 saveRDS(connections,file.path(folder,"original-connections.rds"));saveRDS(models,file.path(folder,"original-models.rds"));saveRDS(ccds,file.path(folder,"original-cds.rds"));saveRDS(aggregated[[2]],file.path(folder,"original-aggregation-map.rds"));saveRDS(list(counts=counts,coordinates=coords,cells=cells,genome=genome),file.path(folder,"input.rds"))
 encoded<-matrix(sprintf("%.17g",counts),nrow(counts),dimnames=dimnames(counts));write.table(data.frame(peak_id=rownames(counts),encoded,check.names=FALSE),file.path(folder,"counts.tsv"),sep="\\t",quote=FALSE,row.names=FALSE,fileEncoding="UTF-8");fields<-cicero:::split_peak_names(rownames(counts));write.table(data.frame(peak_id=rownames(counts),chromosome=fields[,1],start=fields[,2],end=fields[,3]),file.path(folder,"peaks.tsv"),sep="\\t",quote=FALSE,row.names=FALSE,fileEncoding="UTF-8")
 encoded<-matrix(sprintf("%.17g",coords),nrow(coords),dimnames=dimnames(coords));write.table(data.frame(cell_id=rownames(coords),encoded,check.names=FALSE),file.path(folder,"embedding.tsv"),sep="\\t",quote=FALSE,row.names=FALSE,fileEncoding="UTF-8");if(binary)cells$progress<-sprintf("%.17g",cells$progress);write.table(cells,file.path(folder,"cells.tsv"),sep="\\t",quote=FALSE,row.names=FALSE,fileEncoding="UTF-8");write.table(genome,file.path(folder,"genome.tsv"),sep="\\t",quote=FALSE,row.names=FALSE,col.names=c("chromosome","length"),fileEncoding="UTF-8")
 reports[[id]]<-list(rows=nrow(connections),unresolved_NA=sum(is.na(connections$coaccess)),aggregates=ncol(ccds),windows=length(models),successful_models=sum(vapply(models,is.list,logical(1))))
},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")});jsonlite::write_json(list(reports=reports,warnings=as.list(warnings)),file.path(out,"original-results.json"),auto_unbox=TRUE,pretty=TRUE,digits=NA)
''',encoding='utf8',newline='\n');execute([*common,str(original),str(out),str(base/'cicero-readiness-probe-2'),*map(str,libs)],'independent-original')
    def run(label,r,code=0,java=None):
        r['out']=str(out/(label+'-result'));file=out/(label+'.json');save(file,r);execute([java or str(JAVA21/'java.exe'),'-Xmx2g','-cp',str(jar),'org.jlinalg.cli.CiceroWorkflowCli','--request',str(file)],label,code);result=out/(label+'-result');state=json.loads((result/'analysis-status.json').read_bytes());assert state['result_admitted'] is (code==0) and (result/'results').exists() is (code==0);assert 'REQUIRED ACTION:' in (result/'WARNINGS.md').read_text(encoding='utf8');receipt=result/'worker/process-receipt.json'
        if receipt.exists():assert all(not p['alive_after_cleanup'] for p in json.loads(receipt.read_bytes())['owned_process_cleanup'])
        reports.append(dict(id=label,exit_code=code,result_admitted=state['result_admitted'],cause=state.get('cause'),owned_cleanup_checked=receipt.exists()));print(label+': expected exit '+str(code),flush=True)
    for id in ['fixed-relative','raw-aggregation','zero-peak','binary-unicode-odd']:
        r=copy.deepcopy(request);r['matrix_format']='dense-tsv';folder=out/id
        for key,name in [('counts','counts.tsv'),('peak_metadata','peaks.tsv'),('cell_metadata','cells.tsv'),('embedding','embedding.tsv'),('genome','genome.tsv')]:r[key]=str(folder/name)
        c=r['analyses'][0];c['id']=id;c['distance_parameter']=dict(mode='fixed',value=2)
        if id=='raw-aggregation':c['size_factor_normalize']=False
        if id=='binary-unicode-odd':r['binarize']=True;r['numeric_metadata']=['progress'];c['k']=10;c['summary_stats']=['progress'];c['model']['window']=500001;c['model']['power']=0.9;c['skipped_windows_policy']='retain-source-dispositions'
        run(id,r)
        if id=='binary-unicode-odd':run(id+'-java25',copy.deepcopy(r),java='C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')
    r=copy.deepcopy(request);r['transport_chunk_entries']=1000;run('chunked',r)
    compare=out/'compare.R';compare.write_text('''a<-commandArgs(TRUE);out<-a[1];reference<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE);suppressPackageStartupMessages(library(cicero));reports<-list()
for(id in c("fixed-relative","raw-aggregation","zero-peak","binary-unicode-odd","binary-unicode-odd-java25","chunked")){
 expected<-if(id=="chunked")reference else file.path(out,sub("-java25$","",id));case<-if(id=="chunked")"coaccess" else sub("-java25$","",id);target<-file.path(out,paste0(id,"-result"),"results",case)
 pref<-if(id=="chunked")"" else "original-";cdsfile<-if(id=="chunked")"cicero-cds.rds" else "original-cds.rds"
 x<-readRDS(file.path(target,"connections.rds"));y<-readRDS(file.path(expected,paste0(pref,"connections.rds")));stopifnot(identical(x,y),identical(readRDS(file.path(target,"models.rds")),readRDS(file.path(expected,paste0(pref,"models.rds")))),identical(readRDS(file.path(target,"aggregation-map.rds")),readRDS(file.path(expected,paste0(pref,"aggregation-map.rds")))))
 cds<-readRDS(file.path(target,"cicero-cds.rds"));original<-readRDS(file.path(expected,cdsfile));stopifnot(identical(SummarizedExperiment::assay(cds,"counts"),SummarizedExperiment::assay(original,"counts")),identical(SummarizedExperiment::colData(cds),SummarizedExperiment::colData(original)))
 reports[[id]]<-list(rows=nrow(x),unresolved_NA=sum(is.na(x$coaccess)),all_original_connections_models_aggregation_counts_and_metadata_identical=TRUE)
};jsonlite::write_json(reports,file.path(out,"comparisons.json"),auto_unbox=TRUE,pretty=TRUE,digits=NA)
''',encoding='utf8',newline='\n');execute([*common,str(compare),str(out),str(base/'cicero-readiness-probe-2'),*map(str,libs)],'compare')
    chunks=out/'chunked-result/worker';assert 'entry_parts' in json.loads((chunks/'constructed-counts.json').read_bytes()) and 'parts' in json.loads((chunks/'cases/coaccess/connections.json').read_bytes())
    empty=out/'empty-library';empty.mkdir()
    for id,changes,code in [('unavailable-edition',dict(version='1.3.8'),3),('unavailable-dependencies',dict(r_libraries=[str(empty)]),3),('timeout',dict(timeout_seconds=1),2),('worker-output-capacity',dict(maximum_output_bytes=1),2),('input-capacity',dict(maximum_input_bytes=1),2),('json-capacity',dict(maximum_json_bytes=64),2),('json-node-capacity',dict(maximum_json_nodes=1),2),('native-audit-capacity',dict(maximum_audit_operations=1),2),('model-entry-capacity',dict(maximum_model_entries=1),2),('connection-row-capacity',dict(maximum_connection_rows=1),2)]:
        r=copy.deepcopy(request);r.update(changes);run(id,r,code)
    r=copy.deepcopy(request);good=copy.deepcopy(r['analyses'][0]);good['id']='valid-first';bad=copy.deepcopy(good);bad['id']='reject-NA-second';bad['unresolved_connections_policy']='fail-on-NA';r['analyses']=[good,bad];run('atomic-original-unresolved',r,2)
    root=out/'atomic-original-unresolved-result/worker/cases';assert json.loads((root/'valid-first/source-status.json').read_bytes())['status']=='original-returned';assert json.loads((root/'reject-NA-second/source-status.json').read_bytes())['status']=='FAIL2_ORIGINAL_ERROR';table=json.loads((root/'reject-NA-second/connections.json').read_bytes());score=next(c for c in table['columns'] if c['name']=='coaccess');assert score['tokens'].count('NA')==4 and score['kinds'].count('NA')==4
    r=json.loads((out/'binary-unicode-odd.json').read_bytes());r['analyses'][0]['skipped_windows_policy']='require-all-modeled';run('reject-source-skipped-windows',r,2)
    assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and pin(Path(__file__))==source_pin and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']};save(out/'COMPLETED.json',dict(status='passed-original-Cicero-variants-chunks-two-runtime-JVMs-and-failure-protocols',artifact=artifact,source_pin=source_pin,comparisons=json.loads((out/'comparisons.json').read_bytes()),protocols=reports,implementation_credit=False,whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/scientific/failure comparisons')
    main()
