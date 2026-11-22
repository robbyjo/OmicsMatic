args<-commandArgs(TRUE);out<-args[[1]];lib<-args[[2]];source<-args[[3]]
dir.create(out,recursive=TRUE,showWarnings=FALSE);.libPaths(c(lib,.Library))
suppressPackageStartupMessages(library(GenomicRanges))
stopifnot(as.character(packageVersion("GenomicRanges"))=="1.64.0")
original<-asNamespace("GenomicRanges");wanted<-c(".compute_promoter_ranges",".GenomicRanges_promoters","get_out_of_bound_index");references<-list()
for(file in list.files(file.path(source,"R"),pattern="[.]R$",full.names=TRUE)){
 for(expr in parse(file,keep.source=FALSE,encoding="UTF-8")){
  if(is.call(expr)&&identical(expr[[1]],as.name("<-"))&&is.symbol(expr[[2]])&&as.character(expr[[2]])%in%wanted&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function"))){
   name<-as.character(expr[[2]]);reference<-eval(expr[[3]],envir=baseenv());actual<-get(name,original,inherits=FALSE)
   stopifnot(identical(formals(reference),formals(actual)),identical(body(reference),body(actual)))
   references[[name]]<-list(formals=formals(actual),body=body(actual),source_file=basename(file))
  }
 }
}
stopifnot(setequal(names(references),wanted));saveRDS(references,file.path(out,"SOURCE-FUNCTIONS.rds"))
hex<-function(s)paste(sprintf("%02x",as.integer(charToRaw(enc2utf8(s)))),collapse="")
write_table<-function(x,path){con<-file(path,"wb");on.exit(close(con));writeLines(paste(names(x),collapse="\t"),con,useBytes=TRUE);if(nrow(x))for(i in seq_len(nrow(x)))writeLines(paste(vapply(x,function(column)as.character(column[[i]]),""),collapse="\t"),con,useBytes=TRUE)}
set.seed(20261122L)
labels<-c("chr1","chr2","chrU","chrC","chrZ"," chr \u03b2 ")
lengths<-c(1000L,2000L,NA_integer_,100L,0L,83L);circular<-c(FALSE,NA,NA,TRUE,NA,FALSE)
seqinfo<-Seqinfo(labels,seqlengths=lengths,isCircular=circular,genome="artificial")
n<-256L;starts<-sample(-1000L:2500L,n,replace=TRUE);widths<-sample(c(0L,1L,2L,10L,501L),n,replace=TRUE)
input<-suppressWarnings(GRanges(sample(labels,n,replace=TRUE),IRanges(start=starts,width=widths),strand=sample(c("+","-","*"),n,replace=TRUE),seqinfo=seqinfo))
metadata(input)<-list(artificial=TRUE);mcols(input)$row_id<-seq_len(n);mcols(input)$unmodified_text<-rep("literal metadata",n)
case_rows<-list();range_rows<-list();conditions<-list();objects<-list();counter<-0L
for(upstream in c(0L,1L,2000L,5000L))for(downstream in c(0L,1L,200L,5000L)){
 counter<-counter+1L;warnings<-character()
 result<-withCallingHandlers(promoters(input,upstream=upstream,downstream=downstream),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")})
 stopifnot(identical(mcols(input),mcols(result)),identical(metadata(input),metadata(result)))
 bounds<-get("get_out_of_bound_index",original)(result)
 case_rows[[counter]]<-data.frame(case=counter,upstream=upstream,downstream=downstream,rows=length(input),source_bound_count=length(bounds))
 range_rows[[counter]]<-data.frame(case=counter,row=seq_len(n),chromosome_hex=vapply(as.character(seqnames(input)),hex,""),input_start=start(input),input_end=end(input),strand=as.character(strand(input)),output_start=start(result),output_end=end(result),out_of_bounds=seq_len(n)%in%bounds)
 conditions[[counter]]<-warnings;objects[[counter]]<-result
}
empty<-input[integer(0)];empty_result<-promoters(empty,upstream=-7L,downstream=-3L);stopifnot(length(empty_result)==0L);counter<-counter+1L;case_rows[[counter]]<-data.frame(case=counter,upstream=-7L,downstream=-3L,rows=0L,source_bound_count=0L)
write_table(do.call(rbind,case_rows),file.path(out,"cases.tsv"));write_table(do.call(rbind,range_rows),file.path(out,"ranges.tsv"));write_table(data.frame(chromosome_hex=vapply(labels,hex,""),length=ifelse(is.na(lengths),"NA",as.character(lengths)),circular=ifelse(is.na(circular),"NA",as.character(circular))),file.path(out,"sequences.tsv"))
saveRDS(list(input=input,results=objects,conditions=conditions,empty=empty_result,session=sessionInfo(),R=R.version),file.path(out,"ORIGINAL-OBJECTS.rds"));writeLines(c(R.version.string,paste("GenomicRanges",as.character(packageVersion("GenomicRanges")))),file.path(out,"RUNTIME.txt"),useBytes=TRUE)
cat("PASS original1.64.0 scalar promoter/bounds references",counter,"cases",n*16L,"rows;three official helper bodies/formals identical;metadata preserved\n")
