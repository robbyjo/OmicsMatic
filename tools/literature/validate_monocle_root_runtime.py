# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the actual packaged root RUN workflow against retained direct original-package fits."""
import argparse
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
    probe=base/'current-readiness-probe-3'
    assert json.loads((probe/'RESULT.json').read_bytes())['status']=='COMPLETED_ORIGINAL_MONOCLE3_SYNTHETIC_READINESS_PROBE'
    worker=out/'export-input.R'
    worker.write_text('''a<-commandArgs(TRUE);x<-readRDS(a[2]);out<-a[1]
write.table(data.frame(gene_id=rownames(x$counts),x$counts,check.names=FALSE),file.path(out,"counts.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
cm<-x$cell_metadata;for(n in names(cm))if(is.double(cm[[n]]))cm[[n]]<-sprintf("%.17g",cm[[n]])
write.table(data.frame(cell_id=rownames(cm),cm,check.names=FALSE),file.path(out,"cell-metadata.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
write.table(data.frame(gene_id=rownames(x$gene_metadata),x$gene_metadata,check.names=FALSE),file.path(out,"gene-metadata.tsv"),sep="\\t",quote=FALSE,row.names=FALSE)
''',encoding='utf8',newline='\n')
    rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe'
    env=dict(os.environ,LC_ALL='C',LANG='C',LANGUAGE='en',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1')
    command=[rscript,'--vanilla',str(worker),str(out),str(probe/'input.rds')]
    r=subprocess.run(command,cwd=ROOT,env=env,capture_output=True,timeout=60);(out/'input-export.stdout').write_bytes(r.stdout);(out/'input-export.stderr').write_bytes(r.stderr);assert r.returncode==0
    request=dict(schema_version=1,mode='RUN',version='1.4.27',counts='counts.tsv',cell_metadata='cell-metadata.tsv',gene_metadata='gene-metadata.tsv',
        count_units='raw-integer-counts',metadata_types={'progress':{'type':'numeric'},'batch':{'type':'factor','levels':['a','b']}},
        rscript=rscript,r_libraries=libraries,out='run',timeout_seconds=300,analyses=[dict(id='trajectory',seed=20261107,
            trajectory=dict(preprocess=dict(method='PCA',num_dim=10,norm_method='log',pseudo_count=1,scaling=True),
                umap=dict(n_neighbors=15,min_dist=0.1,metric='cosine',fast_sgd=False),
                cluster=dict(method='leiden',k=15,resolution=0.01,partition_qval=0.05,weight=False),
                graph=dict(use_partition=False,close_loop=False,ncenter=12,minimal_branch_len=3,prune_graph=True,maxiter=10),root_cells=['cell-001']),
            fit_models=dict(family='quasipoisson',intercept=True,terms=[{'column':'progress'},{'column':'batch'}]),
            graph_test=dict(neighbor_graph='principal_graph',k=25,alternative='greater'))])
    save(out/'request.json',request)
    command=[str(JAVA21/'java.exe'),'-Xmx1g','-jar',str(jar),'monocle-tools','RUN','--request',str(out/'request.json')]
    with (out/'java.stdout').open('xb') as aout,(out/'java.stderr').open('xb') as err:
        r=subprocess.run(command,cwd=ROOT,env=env,stdout=aout,stderr=err,stdin=subprocess.DEVNULL,timeout=360)
    save(out/'java-process.json',dict(command=command,exit_code=r.returncode))
    assert r.returncode==0,(out/'java.stderr').read_text(encoding='utf8',errors='replace')
    state=json.loads((out/'run/analysis-status.json').read_bytes());assert state['result_admitted']
    compare=out/'compare-original.R'
    compare.write_text('''a<-commandArgs(TRUE);.libPaths(c(a[-(1:3)],.Library),include.site=FALSE)
suppressPackageStartupMessages(library(BPCells));suppressPackageStartupMessages(library(monocle3))
candidate<-a[1];reference<-a[2];out<-a[3]
x<-monocle3::load_monocle_objects(file.path(candidate,"cds"));y<-monocle3::load_monocle_objects(file.path(reference,"ordered"))
stopifnot(identical(colnames(x),colnames(y)),identical(rownames(x),rownames(y)),identical(monocle3::pseudotime(x),monocle3::pseudotime(y)),
 identical(SingleCellExperiment::reducedDims(x)[["UMAP"]],SingleCellExperiment::reducedDims(y)[["UMAP"]]),
 identical(monocle3::clusters(x),monocle3::clusters(y)),identical(monocle3::partitions(x),monocle3::partitions(y)))
for(name in c("coefficients","graph-test")) {
 x<-readRDS(file.path(candidate,paste0(name,".rds")));y<-readRDS(file.path(reference,paste0(name,".rds")))
 keep<-vapply(y,function(v)is.atomic(v)&&is.null(dim(v)),logical(1))
 stopifnot(identical(names(x),names(y)),isTRUE(all.equal(as.data.frame(x[,keep,drop=FALSE]),as.data.frame(y[,keep,drop=FALSE]),tolerance=1e-13)))
}
jsonlite::write_json(list(status="matched-independent-retained-original-full-fits",cells=160L,genes=80L,coefficient_rows=240L,graph_test_rows=80L,
 trajectory_axes_clusters_embedding_pseudotime_identical=TRUE,all_atomic_coefficient_and_graph_test_columns_equal=TRUE,original_reference="current-readiness-probe-3",whole_paper_reproduced=FALSE),file.path(out,"original-comparison.json"),auto_unbox=TRUE,pretty=TRUE)
''',encoding='utf8',newline='\n')
    command=[rscript,'--vanilla',str(compare),str(out/'run/results/trajectory'),str(probe),str(out),*libraries]
    r=subprocess.run(command,cwd=ROOT,env=env,capture_output=True,timeout=120);(out/'comparison.stdout').write_bytes(r.stdout);(out/'comparison.stderr').write_bytes(r.stderr);save(out/'comparison-process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
    save(out/'COMPLETED.json',dict(status='actual-root-RUN-matched-independent-original-full-fit',artifact=artifact,
        source_pin=pin(Path(__file__)),comparison=json.loads((out/'original-comparison.json').read_bytes()),
        input_pins={n:pin(out/n) for n in ['counts.tsv','cell-metadata.tsv','gene-metadata.tsv','request.json']},
        original_reference_pins={n:pin(probe/n) for n in ['RESULT.json','PROVENANCE.json','source-used.R','input.rds','coefficients.rds','graph-test.rds']},
        implementation_credit=False,whole_goal_complete=False))
    print('Actual root RUN matched direct original full trajectory, 240 coefficients and 80 Moran tests')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original reference checks')
    main()
