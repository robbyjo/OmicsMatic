# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Direct original trajectory variants, pseudotime splines, and literal Unicode transport checks."""
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
    fixture=base/'workflow-validation-3'
    for n in ['counts.tsv','cell-metadata.tsv','gene-metadata.tsv']:(out/n).write_bytes((fixture/n).read_bytes())
    request=json.loads((fixture/'request.json').read_bytes());request['r_libraries']=libraries;request['out']='variants-run'
    lsi=copy.deepcopy(request['analyses'][0]);lsi.update(id='lsi-louvain',seed=20261109)
    lsi['trajectory']['preprocess']['method']='LSI';lsi['trajectory']['cluster'].update(method='louvain',weight=True)
    lsi['trajectory'].pop('root_cells');lsi['trajectory']['root_pr_nodes']=['Y_1']
    lsi['fit_models']['terms']=[dict(column='pseudotime',spline_df=3),dict(column='batch')]
    lsi['graph_test'].update(neighbor_graph='knn',alternative='two.sided')
    partition=copy.deepcopy(request['analyses'][0]);partition.update(id='partitions-loop',seed=20261110)
    partition['trajectory']['preprocess'].update(norm_method='none',pseudo_count=0,scaling=False)
    partition['trajectory']['umap'].update(metric='manhattan',min_dist=0.2)
    partition['trajectory']['cluster']['weight']=True;partition['trajectory']['graph'].update(use_partition=True,close_loop=True)
    partition['trajectory']['root_cells']=['cell-001','cell-160'];partition['graph_test']['alternative']='less'
    request['analyses']=[lsi,partition];save(out/'variants.json',request)
    rscript=request['rscript'];env=dict(os.environ,LC_ALL='C',LANG='C',LANGUAGE='en',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1')
    def execute(command,label,timeout=300):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
            r=subprocess.run(command,cwd=ROOT,env=env,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=timeout)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,label+': read retained stderr'
    direct=out/'direct-variants.R'
    direct.write_text('''a<-commandArgs(TRUE);out<-a[1];input<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE)
suppressPackageStartupMessages(library(BPCells));suppressPackageStartupMessages(library(monocle3))
RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);x<-readRDS(input);reports<-list()
for(id in c("lsi-louvain","partitions-loop")) {
 lsi<-id=="lsi-louvain";set.seed(if(lsi)20261109 else 20261110)
 cds<-monocle3::new_cell_data_set(x$counts,cell_metadata=x$cell_metadata,gene_metadata=x$gene_metadata)
 cds<-monocle3::preprocess_cds(cds,method=if(lsi)"LSI" else "PCA",num_dim=10L,norm_method=if(lsi)"log" else "none",pseudo_count=if(lsi)1 else 0,scaling=lsi)
 cds<-monocle3::reduce_dimension(cds,reduction_method="UMAP",preprocess_method=if(lsi)"LSI" else "PCA",umap.metric=if(lsi)"cosine" else "manhattan",umap.n_neighbors=15L,umap.min_dist=if(lsi)0.1 else 0.2,umap.fast_sgd=FALSE,cores=1L)
 cds<-monocle3::cluster_cells(cds,reduction_method="UMAP",cluster_method=if(lsi)"louvain" else "leiden",k=15L,resolution=0.01,random_seed=if(lsi)20261109L else 20261110L,partition_qval=0.05,weight=TRUE)
 cds<-monocle3::learn_graph(cds,use_partition=!lsi,close_loop=!lsi,learn_graph_control=list(ncenter=12L,minimal_branch_len=3L,prune_graph=TRUE,maxiter=10L))
 if(lsi)cds<-monocle3::order_cells(cds,root_pr_nodes="Y_1") else cds<-monocle3::order_cells(cds,root_cells=c("cell-001","cell-160"))
 target<-file.path(out,paste0("original-",id));dir.create(target)
 monocle3::save_monocle_objects(cds,file.path(target,"cds"),hdf5_assays=FALSE,verbose=FALSE,archive_control=list(archive_type="none"))
 fit<-monocle3::fit_models(cds,model_formula_str=if(lsi)"~splines::ns(pseudotime, df=3)+batch" else "~progress+batch",expression_family="quasipoisson",cores=1L,clean_model=FALSE,verbose=TRUE)
 coefs<-monocle3::coefficient_table(fit);tests<-monocle3::graph_test(cds,neighbor_graph=if(lsi)"knn" else "principal_graph",k=25L,alternative=if(lsi)"two.sided" else "less",cores=1L,verbose=TRUE)
 stopifnot(all(fit$status=="OK"),all(tests$status=="OK"));saveRDS(fit,file.path(target,"models.rds"));saveRDS(coefs,file.path(target,"coefficients.rds"));saveRDS(tests,file.path(target,"graph-test.rds"))
 reports[[id]]<-list(genes=nrow(cds),cells=ncol(cds),coefficients=nrow(coefs),graph_tests=nrow(tests),infinite_pseudotime=sum(is.infinite(monocle3::pseudotime(cds))))
}
jsonlite::write_json(reports,file.path(out,"direct-original.json"),auto_unbox=TRUE,pretty=TRUE)
''',encoding='utf8',newline='\n')
    execute([rscript,'--vanilla',str(direct),str(out),str(base/'current-readiness-probe-3/input.rds'),*libraries],'direct-variants')
    execute([str(JAVA21/'java.exe'),'-Xmx1g','-cp',str(jar),'org.jlinalg.cli.Monocle3WorkflowCli','--request',str(out/'variants.json')],'variants-adapter')
    compare=out/'compare-variants.R'
    compare.write_text('''a<-commandArgs(TRUE);out<-a[1];.libPaths(c(a[-1],.Library),include.site=FALSE)
suppressPackageStartupMessages(library(BPCells));suppressPackageStartupMessages(library(monocle3))
for(id in c("lsi-louvain","partitions-loop")) {
 candidate<-file.path(out,"variants-run","results",id);reference<-file.path(out,paste0("original-",id))
 x<-monocle3::load_monocle_objects(file.path(candidate,"cds"));y<-monocle3::load_monocle_objects(file.path(reference,"cds"))
 stopifnot(identical(colnames(x),colnames(y)),identical(rownames(x),rownames(y)),identical(monocle3::pseudotime(x),monocle3::pseudotime(y)),identical(SingleCellExperiment::reducedDims(x)[["UMAP"]],SingleCellExperiment::reducedDims(y)[["UMAP"]]),identical(monocle3::clusters(x),monocle3::clusters(y)),identical(monocle3::partitions(x),monocle3::partitions(y)))
 for(type in c("models","coefficients","graph-test")) {
  x<-readRDS(file.path(candidate,paste0(type,".rds")));y<-readRDS(file.path(reference,paste0(type,".rds")));k<-vapply(y,function(v)is.atomic(v)&&is.null(dim(v)),logical(1))
  stopifnot(isTRUE(all.equal(as.data.frame(x[,k,drop=FALSE]),as.data.frame(y[,k,drop=FALSE]),tolerance=1e-13)))
 }
}
jsonlite::write_json(list(status="matched-direct-original-trajectory-variants",cases=2L),file.path(out,"variant-comparison.json"),auto_unbox=TRUE,pretty=TRUE)
''',encoding='utf8',newline='\n')
    execute([rscript,'--vanilla',str(compare),str(out),*libraries],'compare-variants')
    # Literal quotes, whitespace, Unicode and NA IDs/factor levels through actual R execution.
    unicode=out/'données β';unicode.mkdir();cells=[' cell "β ','NA'];genes=[' gene "β ','NA']
    for name in ['counts.tsv','cell-metadata.tsv','gene-metadata.tsv']:
        rows=[line.split('\t') for line in (fixture/name).read_text(encoding='utf8').splitlines()]
        if name=='counts.tsv':rows[0][1:3]=cells;rows[1][0]=genes[0];rows[2][0]=genes[1]
        if name=='cell-metadata.tsv':
            rows[1][0]=cells[0];rows[2][0]=cells[1]
            for row in rows[1:]:row[2]='NA' if row[2]=='a' else 'β'
        if name=='gene-metadata.tsv':
            rows[1][0]=genes[0];rows[2][0]=genes[1];rows[1][1]=' symbol "β ';rows[2][1]='NA'
        (unicode/name).write_text('\n'.join('\t'.join(r) for r in rows)+'\n',encoding='utf8',newline='\n')
    u=copy.deepcopy(request);u['out']='résultat β';u['metadata_types']['batch']['levels']=['NA','β']
    for key,name in [('counts','counts.tsv'),('cell_metadata','cell-metadata.tsv'),('gene_metadata','gene-metadata.tsv')]:u[key]=str(unicode/name)
    u['analyses']=[copy.deepcopy(json.loads((fixture/'request.json').read_bytes())['analyses'][0])];u['analyses'][0].pop('trajectory');u['analyses'][0].pop('graph_test');save(out/'unicode.json',u)
    execute([str(JAVA21/'java.exe'),'-Xmx1g','-cp',str(jar),'org.jlinalg.cli.Monocle3WorkflowCli','--request',str(out/'unicode.json')],'unicode-adapter')
    ledger=json.loads((out/u['out']/'worker/input-ledger.json').read_bytes());assert ledger['cell_ids'][:2]==cells and ledger['gene_ids'][:2]==genes
    table=json.loads((out/u['out']/'results/trajectory/models.json').read_bytes());gene_column=next(c for c in table['columns'] if c['name']=='gene_id');assert gene_column['tokens'][:2]==genes and gene_column['kinds'][:2]==['value','value']
    assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
    save(out/'COMPLETED.json',dict(status='matched-original-variants-and-literal-Unicode-R-transport',artifact=artifact,source_pin=pin(Path(__file__)),
        original=json.loads((out/'direct-original.json').read_bytes()),comparison=json.loads((out/'variant-comparison.json').read_bytes()),literal_Unicode_axes_and_NA_preserved=True,implementation_credit=False,whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original trajectory and Unicode checks')
    main()
