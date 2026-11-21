# Complete synthetic prepared inputs; no graph is learned while preparing these cases.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L);out<-args[1];.libPaths(args[2:3],include.site=FALSE)
base<-readRDS(args[4]);stopifnot(as.character(packageVersion("monocle3"))=="1.0.0")
original_coordinates<-SingleCellExperiment::reducedDims(base)[["UMAP"]];counts<-SingleCellExperiment::counts(base)
make<-function(name,coordinates,partition,clusters=NULL){
 n<-nrow(coordinates);ids<-sprintf("cell-%03d",seq_len(n));rownames(coordinates)<-ids
 matrix<-counts[,rep(seq_len(ncol(counts)),length.out=n),drop=FALSE];colnames(matrix)<-ids
 gm<-as.data.frame(SummarizedExperiment::rowData(base));cm<-data.frame(declared_synthetic_partition=as.character(partition),row.names=ids,stringsAsFactors=FALSE)
 cds<-monocle3::new_cell_data_set(matrix,cell_metadata=cm,gene_metadata=gm);SingleCellExperiment::reducedDims(cds)[["UMAP"]]<-coordinates
 if(is.null(clusters))clusters<-rep(c("C1","C2","C3"),length.out=n)
 cl<-factor(clusters,levels=unique(clusters));names(cl)<-ids;pa<-factor(partition,levels=unique(partition));names(pa)<-ids
 cds@clusters[["UMAP"]]<-list(cluster_result=NULL,clusters=cl,partitions=pa)
 saveRDS(cds,file.path(out,paste0(name,".rds")));list(cells=n,genes=nrow(cds),cluster_levels=as.list(levels(cl)),partition_levels=as.list(levels(pa)),source="Complete explicitly prepared synthetic original1.0.0 CDS; all counts/axes retained")
}
results<-list();results$base<-make("base",original_coordinates,rep("1",27))
angle<-seq(0,2*pi,length.out=61)[-61];circle<-cbind(cos(angle),sin(angle));results$circle<-make("circle",circle,rep("1",60))
multi<-original_coordinates[rep(seq_len(27),each=2),,drop=FALSE];part<-rep(c("1","2"),27);multi[part=="2",1]<-multi[part=="2",1]+20
results$interleaved_partitions<-make("interleaved_partitions",multi,part)
results$bad_partition_labels<-make("bad_partition_labels",multi,ifelse(part=="1","A","B"))
results$uniform<-make("uniform",matrix(0,nrow=27,ncol=2),rep("1",27))
scaled<-original_coordinates;scaled[,1]<-scaled[,1]*100;results$scaled_input<-make("scaled_input",scaled,rep("1",27))
jsonlite::write_json(list(status="PASS6_COMPLETE_PREPARED_GRAPH_INPUTS",inputs=results),file.path(out,"RESULT.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
