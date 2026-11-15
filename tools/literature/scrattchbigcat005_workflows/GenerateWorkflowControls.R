# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Synthetic method controls. Dimensions/settings do not recover author data or options.
args <- commandArgs(TRUE);stopifnot(length(args)==1L)
out <- args[[1L]];stopifnot(!file.exists(out));dir.create(out,recursive=TRUE)
library(scrattch.bigcat);library(Matrix);library(matrixStats);library(arrow);library(data.table);library(dplyr)
dir.create(file.path(out,"assets"));dir.create(file.path(out,"requests"))
path <- function(relative)structure(list(relative=relative),class="omicsmatic_bigcat_path_v1")
ref <- function(id,selection=character())structure(list(id=id,path=selection),class="omicsmatic_bigcat_reference_v1")
parquet <- function(mat,id="expression")list(constructor="convert_big.dat_parquet",
  arguments=list(mat=mat,dir=path(id),col.bin.size=50000L,row.bin.size=500L,logNormal=TRUE,mc.cores=1L))
index <- list()
write.case <- function(id,operation,a,preparation=list(),profile="prepublication-2023",globals=list(),post=NULL,role) {
  set.seed(1729L)
  request <- list(schema_version=1L,profile=profile,operation=operation,arguments=a,
    rng=list(kind=RNGkind(),seed=.Random.seed),globals=globals,preparation=preparation,
    post_selection=post,arrow_cpu_count=1L)
  saveRDS(request,file.path(out,"requests",paste0(id,".rds")),version=3)
  index[[id]] <<- list(profile=profile,operation=operation,role=role,request=paste0("requests/",id,".rds"))
}
set.seed(18001L)
genes <- sprintf("g%05d",seq_len(2400L));cells <- sprintf("cell-%03d",seq_len(180L))
macro <- rep(seq_len(3L),each=60L);subgroup <- rep(seq_len(6L),each=30L)
lambda <- exp(runif(length(genes),log(.05),log(4)))
counts <- matrix(rpois(length(genes)*length(cells),lambda),nrow=length(genes),dimnames=list(genes,cells))
for(g in seq_len(3L)) {
  rows <- (g-1L)*80L+seq_len(80L);cols <- which(macro==g)
  counts[rows,cols] <- counts[rows,cols]+matrix(rpois(length(rows)*length(cols),40),nrow=length(rows))
}
for(g in seq_len(6L)) {
  rows <- 240L+(g-1L)*35L+seq_len(35L);cols <- which(subgroup==g)
  counts[rows,cols] <- counts[rows,cols]+matrix(rpois(length(rows)*length(cols),20),nrow=length(rows))
}
counts <- as(counts,"dgCMatrix");norm.dat <- logCPM(counts);truth <- setNames(subgroup,cells)
saveRDS(list(counts=counts,norm.dat=norm.dat,truth=truth),file.path(out,"synthetic-expression.rds"),version=3)
cl.dat <- get_cl_means(norm.dat,truth)
for(boot in c(0L,100L))write.case(paste0("dend-",boot),"build_dend",
  list(cl.dat=as.matrix(cl.dat[seq_len(1871L),]),nboot=boot,ncores=1L),
  role="P058 type means for 1871 genes; bootstrap count is an explicit project choice")
means <- matrix(c(5,0,5,0,5,5,5,5,0,1,1,1),nrow=3L,
  dimnames=list(c("g1","g2","g3"),c("single-A","single-B","mixed","negative")))
write.case("marker-doublets","find_doublet_by_marker",list(cl.means=means,markers=list(A="g1",B="g2")),role="P059 marker doublet detection")
write.case("marker-missing-gene","find_doublet_by_marker",list(cl.means=means,markers=list(A="absent",B="g2")),role="Original missing-gene error; no repaired identifiers")
de.genes <- list("1_2"=list(up.genes=setNames(30:21,genes[1:10]),down.genes=setNames(30:21,genes[81:90])),
  "1_3"=list(up.genes=setNames(30:21,genes[1:10]),down.genes=setNames(30:21,genes[161:170])),
  "2_3"=list(up.genes=setNames(30:21,genes[81:90]),down.genes=setNames(30:21,genes[161:170])))
select.means <- as.matrix(get_cl_means(norm.dat,setNames(macro,cells)))
score <- get_gene_score(de.genes,cl.means=select.means,mc.cores=1L)
write.case("markers-ranked-DE","select_N_markers",list(de.genes=de.genes,cl.means=select.means,pair.num=2L,mc.cores=1L),role="P059 marker selection from declared ranked DE summaries")
for(dense in c(FALSE,TRUE))write.case(if(dense)"markers-default-dense"else"markers-default-sparse","select_N_markers",
  list(de.genes=de.genes,cl.means=select.means,
    up.gene.score=if(dense)as.matrix(score$up.gene.score)else score$up.gene.score,
    down.gene.score=if(dense)as.matrix(score$down.gene.score)else score$down.gene.score,
    default.markers=c(genes[[1L]],genes[[81L]]),rm.genes=genes[[2L]],pair.num=3L,mc.cores=1L),
  role="Explicit caller score type/default markers/exclusion; original sparse failure retained")
map.args <- list(big.dat=ref("expression"),cl.dat=as.matrix(cl.dat[seq_len(500L),]),
  select.cells=cells[c(1:7,31:37,61:67,91:97,121:127,151:157)],block.size=11L,mc.cores=1L)
for(method in c("source-default","Annoy.Cosine","cor"))write.case(paste0("mapping-",method),"map_cells_knn_big",
  if(method=="source-default")map.args else c(map.args,list(method=method)),
  preparation=list(expression=parquet(norm.dat)),role="P059 original chunked centroid mapping; scalar method is a project caller choice")
# Independent prepared edge data include both reciprocal and one-way edges.
centers <- data.frame(cl=as.character(1:4),cluster_id=1:4,cluster_label=paste0("synthetic-cluster-",1:4),
  cluster_size=c(90L,60L,5L,25L),cluster_color=c("#2166AC","#B2182B","#4D9221","#762A83"),
  x=c(0,4,4,0),y=c(0,0,4,4))
edges <- data.frame(cl.from=c("1","1","1","2","2","3","3","4","4"),
  cl.to=c("1","2","3","2","1","3","4","4","3"),
  per.cell=c(12L,2L,1L,12L,3L,14L,1L,13L,2L))
edges$Freq <- edges$per.cell*centers$cluster_size[match(edges$cl.from,centers$cl)]
edges$from.total <- 15L*centers$cluster_size[match(edges$cl.from,centers$cl)]
edges$to.total <- unname(tapply(edges$Freq,edges$cl.to,sum)[edges$cl.to]);edges$frac <- edges$Freq/edges$from.total
for(trans in c("sqrt","log10"))write.case(paste0("constellation-prepared-",trans),"plot_constellation",
  list(knn.cl.df=edges,cl.center.df=centers,out.dir=path("plots"),prefix=paste0(trans,"-"),
    node_trans=trans,plot.parts=TRUE,return.list=TRUE),
  role="P058/P059 original plotting from explicit 15-neighbour counts; logarithmic node transform is a project spelling choice")
set.seed(7404L);embedding <- matrix(rnorm(180L*4L),ncol=4L,
  dimnames=list(sprintf("graph-cell-%03d",1:180),paste0("dimension-",1:4)))
graph.cl <- setNames(rep(as.character(1:4),centers$cluster_size),rownames(embedding))
centers$x <- vapply(centers$cl,function(id)mean(embedding[graph.cl==id,1]),numeric(1))
centers$y <- vapply(centers$cl,function(id)mean(embedding[graph.cl==id,2]),numeric(1))
write.case("constellation-original-neighbours","constellation_from_embedding",
  list(graph_arguments=list(rd.dat=embedding,cl=graph.cl,k=16L,method="Annoy.Euclidean",
      outlier.frac.th=1,mc.cores=1L),
    plot_arguments=list(cl.center.df=centers,out.dir=path("plots"),prefix="embedding-",
      node_trans="log10",plot.parts=TRUE,return.list=TRUE),outside_fraction_threshold=.05),
  role="P058/P059 original graph and plot: k16 requests 15 after source rank-one exclusion; method/outlier setting/coordinates are project choices")
for(version in c("v2","v3")) {
  use <- seq_along(cells)%%2L==if(version=="v2")1L else 0L
  param <- if(version=="v2")de_param(q1.th=.4,q.diff.th=.7,de.score.th=150,min.cells=10L,min.genes=8L)
           else de_param(q1.th=.5,q.diff.th=.7,de.score.th=150,min.cells=4L,min.genes=8L)
  write.case(paste0("clustering-",version),"iter_clust_big",
    list(big.dat=ref("expression"),select.cells=cells[use],counts=counts[,use],
      prefix=path(version),split.size=10L,method="louvain",de.param=param,mc.cores=1L,verbose=TRUE),
    preparation=list(expression=parquet(norm.dat[,use])),role=paste("P059 separate",version,"clustering with reported DE settings; explicit project split size and method"))
}
# A real 50,000-cell bin boundary with sparse data keeps this I/O control modest.
for(spec in list(c(501L,50001L),c(501L,50002L),c(502L,50002L))) {
  ngenes <- spec[[1L]];ncells <- spec[[2L]]
  bg <- sprintf("boundary-g%03d",seq_len(ngenes));bc <- sprintf("boundary-c%05d",seq_len(ncells))
  cols <- unique(c(1L,25000L,50000L,50001L,ncells))
  mat <- sparseMatrix(i=rep(1:3,length(cols)),j=rep(cols,each=3L),
    x=rep(c(2,4,8),length(cols)),dims=c(ngenes,ncells),dimnames=list(bg,bc))
  template <- matrix(0,nrow=ngenes,ncol=3L,dimnames=list(bg,paste0("type",1:3)))
  template[1:3,] <- diag(c(2,4,8))
  write.case(if(ngenes==501L)paste0("mapping-boundary-",ncells)else paste0("mapping-boundary-",ngenes,"-by-",ncells),"map_cells_knn_big",
    list(big.dat=ref("expression"),cl.dat=template,select.cells=bc[cols],method="Annoy.Cosine",block.size=2L,mc.cores=1L),
    preparation=list(expression=parquet(mat)),role="Actual 50000-cell/500-gene source bins; final one-column and two-column bin probes")
}
sets <- c("WB10x","WBMERFISH","SPN");platform <- setNames(rep(sets,length.out=length(cells)),cells)
rare <- names(truth)[truth==1L&platform[names(truth)]=="SPN"];platform[head(rare,6L)] <- "WB10x"
recipes <- setNames(lapply(sets,function(set) {
  mat <- norm.dat[,platform==set,drop=FALSE]
  if(set=="WBMERFISH")mat <- mat[seq_len(500L),,drop=FALSE]
  parquet(mat,set)
}),sets)
comb <- list(type="big",dat.list=setNames(lapply(sets,ref),sets),all.cells=cells,
  meta.df=data.frame(platform=unname(platform),row.names=cells),
  de.param.list=setNames(lapply(sets,function(x)de_param()),sets),
  cl.list=setNames(lapply(sets,function(set)truth[cells[platform==set]]),sets))
hargs <- list(comb.dat=comb,select.cells=cells,prefix="WB-SPN",dir=path("harmonization"),
  mc.cores=1L,split.size=100L,sample.size=50000L,k=5L)
# Original harmonize writes into an existing caller directory. Use workspace itself.
hargs$dir <- path(".")
for(profile in c("pre-regression-2022","prepublication-2023"))write.case(paste0("harmonization-",profile),"i_harmonize",hargs,
  preparation=recipes,profile=profile,globals=list(sets=sets),
  post=list(resolution_prefix="WB-SPN",platform_column="platform",platform_value="SPN",minimum=5L),
  role="P058 three-dataset integration followed by SPN>=5 selection; source profile/k/resolution are project choices")
ig <- paste0("impute-gene-",seq_len(20L));ic <- paste0("impute-cell-",seq_len(12L))
initial <- matrix(seq_len(240L)/7,nrow=20L,dimnames=list(ig,ic))
knn <- matrix(rep(c(1L,2L,7L,8L),each=12L),nrow=12L,dimnames=list(ic,NULL))
split.results <- list(coarse=list(impute.genes=ig,knn.genes=ig,cl=setNames(rep(1L,12L),ic),knn=knn),
  fine=list(impute.genes=ig[1:10],knn.genes=ig[1:10],cl=setNames(rep(1:2,each=6L),ic),knn=knn[c(1:3,7:9),,drop=FALSE]))
impute.recipe <- list(state=list(constructor="create_big.dat_fbm",arguments=list(row.id=ig,col.id=ic,
  backingfile=path("imputed-state")),initial_values=initial))
ia <- list(comb.dat=list(all.cells=ic,meta.df=data.frame(platform=rep(c("A","B"),each=6L),row.names=ic)),
  impute.dat.big=ref("state"),split.results=split.results,select.cells=ic)
for(explicit in c(FALSE,TRUE))write.case(if(explicit)"imputation-partitioned"else"imputation-source-default","impute_cross_big",
  if(explicit)c(ia,list(ref.sets=c("A","B"),select.genes=ig))else ia,preparation=impute.recipe,
  role="P059 sequential coarse/fine partition imputation with original FBM mutation and float warnings")
# More than 200 training cells per cluster detects accidental centroid subsampling.
large.cells <- sprintf("large-cv-cell-%03d",1:600);large.genes <- sprintf("large-cv-gene-%02d",1:20)
large.dat <- matrix(rep(seq_len(600L)/600,each=20L)+rep(seq_len(20L)/20,600L),nrow=20L,
  dimnames=list(large.genes,large.cells))
large.dat <- as(Matrix(large.dat,sparse=TRUE),"dgCMatrix")
large.truth <- setNames(rep(c("class-A","class-B"),each=300L),large.cells)
large.folds <- setNames(rep(rep(1:5,60L),2L),large.cells)
write.case("centroid-fivefold-over-200-training","centroid_cross_validation",
  list(big.dat=ref("expression"),cl=large.truth,fold_assignments=as.integer(large.folds)|>setNames(names(large.folds)),
    marker_sets=list(all=large.genes),method="Annoy.Cosine",block.size=31L,mc.cores=1L),
  preparation=list(expression=parquet(large.dat)),role="Independent all-training-centroid control: 240 training cells per class in each fold")
saveRDS(list(norm.dat=large.dat,truth=large.truth,folds=large.folds),file.path(out,"large-centroid-inputs.rds"),version=3)
# Full reported CV shape; these are synthetic genes/panels, not the author lists.
set.seed(7403L);extra <- sprintf("g%05d",2401:8460)
more <- Matrix(matrix(rpois(length(extra)*length(cells),.3),nrow=length(extra),dimnames=list(extra,cells)),sparse=TRUE)
cv.dat <- rbind(norm.dat,more);all.markers <- rownames(cv.dat)
panels <- c(list(all_8460=all.markers,synthetic_TF_size_534=all.markers[1:534]),
  setNames(lapply(seq_len(20L),function(i)sample(all.markers,534L,replace=FALSE)),sprintf("random-%02d",1:20)))
folds <- setNames(rep(rep(1:5,length.out=30L),6L),cells)
write.case("centroid-fivefold-22-panels","centroid_cross_validation",
  list(big.dat=ref("expression"),cl=truth,fold_assignments=as.integer(folds)|>setNames(names(folds)),
    marker_sets=panels,method="Annoy.Cosine",block.size=11L,mc.cores=1L),
  preparation=list(expression=parquet(cv.dat)),role="P059 five folds,8460 markers,534 TF-sized synthetic panel,20 random panels of534; assignments/seed/identities are project choices")
saveRDS(list(cases=index,synthetic_expression=list(norm.dat=norm.dat,truth=truth),
  CV=list(norm.dat=cv.dat,truth=truth,folds=folds,panels=panels),author_data=FALSE),file.path(out,"complete-control-inputs.rds"),version=3)
jsonlite::write_json(list(status="SYNTHETIC_BIGCAT_WORKFLOW_REQUESTS_CREATED_NOT_QUALIFICATION",cases=index,
  author_data=FALSE,author_unreported_options_inferred=FALSE,new_family_credit=0),
  file.path(out,"CONTROLS.json"),pretty=TRUE,auto_unbox=TRUE)
cat("CREATED_ORIGINAL_BIGCAT_WORKFLOW_REQUESTS",length(index),"\n")
