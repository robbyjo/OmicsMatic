# Copyright (C) 2026 JLinAlg contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Typed local-file interface to original engines. No user R code is evaluated.
args<-commandArgs(trailingOnly=TRUE)
if(length(args)!=3L)stop("Expected private library,request JSON and output directory")
main<-normalizePath(args[[1]],winslash="/",mustWork=TRUE);out<-normalizePath(args[[3]],winslash="/",mustWork=TRUE)
.libPaths(c(main,.Library),include.site=FALSE)
tryCatch({
  if(!identical(R.version$major,"4")||!identical(R.version$minor,"6.1"))stop("The validated profile requires R4.6.1")
  # LC_ALL=C gives deterministic numeric/collation rules but cannot represent
  # Unicode on Windows. Set only character handling; keep other C categories.
  if(!nzchar(Sys.setlocale("LC_CTYPE",".UTF-8"))||!isTRUE(l10n_info()[["UTF-8"]]))stop("A UTF-8 character locale is required to preserve original identifiers and descriptions")
  if(!requireNamespace("jsonlite",quietly=TRUE)||as.character(packageVersion("jsonlite"))!="2.0.0")stop("Pinned jsonlite2.0.0 cannot load")
},error=function(e){writeLines(paste("DEPENDENCY UNAVAILABLE:",conditionMessage(e)),file.path(out,"dependency-error.txt"));quit(status=3L)})
request<-jsonlite::fromJSON(args[[2]],simplifyVector=FALSE)
if(!identical(request$schema,"jlinalg-enrichment-request-v1"))stop("Wrong request schema")
engine<-request$engine;method<-request$method;o<-request$options
value<-function(n,d=NULL)if(is.null(o[[n]]))d else o[[n]]
num<-function(n,d=NULL)as.numeric(value(n,d));intvalue<-function(n,d=NULL)as.integer(value(n,d))
if(engine=="dose") .libPaths(c(normalizePath(request$dose_library,winslash="/",mustWork=TRUE),main,.Library),include.site=FALSE)
pins<-c(jsonlite="2.0.0",fgsea="1.38.0",enrichit="0.2.6")
if(engine=="dose")pins<-c(pins,DOSE="4.5.0")
if(engine=="clusterprofiler")pins<-c(pins,clusterProfiler="4.20.3")
tryCatch({
  for(p in names(pins))if(as.character(packageVersion(p))!=pins[[p]])stop("Pinned version differs: ",p)
  for(p in c("BiocParallel",switch(engine,dose="DOSE",clusterprofiler="clusterProfiler",fgsea="fgsea",stop("Unknown engine"))))if(!requireNamespace(p,quietly=TRUE))stop("Cannot load original ",p)
},error=function(e){writeLines(paste("DEPENDENCY UNAVAILABLE:",conditionMessage(e)),file.path(out,"dependency-error.txt"));quit(status=3L)})
options(digits=17,enrichment_force_universe=TRUE)
BiocParallel::register(BiocParallel::SerialParam(progressbar=FALSE),default=TRUE)
options(mc.cores=1L)
read<-function(n)utils::read.delim(file.path(out,n),sep="\t",quote="",comment.char="",colClasses="character",na.strings=character(),check.names=FALSE,stringsAsFactors=FALSE,fileEncoding="UTF-8")
write<-function(x,n)utils::write.table(x,file.path(out,n),sep="\t",quote=FALSE,row.names=FALSE,na="NA",fileEncoding="UTF-8")
mapping<-read("memberships.tsv");descriptions<-read("term-names.tsv");sets<-split(mapping$gene,mapping$term)
warnings<-character();algorithm<-value("algorithm");seed<-intvalue("seed",0L)
RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(seed)
upstream<-file.path(out,"upstream");dir.create(upstream,showWarnings=FALSE)
result<-withCallingHandlers({
  if(method=="ora") {
    query<-read("selected.tsv")$gene;universe<-read("universe.tsv")$gene
    if(engine=="clusterprofiler")clusterProfiler::enricher(query,pvalueCutoff=1,qvalueCutoff=1,pAdjustMethod=value("p-adjust"),universe=universe,minGSSize=intvalue("min-size"),maxGSSize=intvalue("max-size"),TERM2GENE=mapping,TERM2NAME=descriptions)
    else if(engine=="dose")DOSE:::enricher_internal(query,pvalueCutoff=1,qvalueCutoff=1,pAdjustMethod=value("p-adjust"),universe=universe,minGSSize=intvalue("min-size"),maxGSSize=intvalue("max-size"),USER_DATA=DOSE:::build_Anno(mapping,descriptions))
    else stop("ORA requires clusterprofiler or the explicit legacy DOSE engine")
  } else if(method=="gsea") {
    ranking<-read("ranking.tsv");stats<-setNames(as.numeric(ranking$statistic),ranking$gene)
    if(any(!is.finite(stats))||anyDuplicated(names(stats))||is.unsorted(-stats))stop("Invalid canonical ranked statistics")
    common<-list(exponent=num("exponent"),minGSSize=intvalue("min-size"),maxGSSize=intvalue("max-size"),pvalueCutoff=1,pAdjustMethod=value("p-adjust"),verbose=FALSE)
    if(engine=="clusterprofiler") {
      common<-c(list(geneList=stats,TERM2GENE=mapping,TERM2NAME=descriptions),common,list(method=algorithm,seed=seed,reportNA=TRUE))
      if(algorithm=="multilevel")common<-c(common,list(eps=num("eps"),sampleSize=intvalue("sample-size"),nPermSimple=intvalue("simple-permutations"),scoreType=value("tail")))
      else common<-c(common,list(nPerm=intvalue("permutations"),adaptive=FALSE))
      do.call(clusterProfiler::GSEA,common)
    } else if(engine=="dose") {
      common<-c(list(geneList=stats),common,list(eps=num("eps",0),USER_DATA=DOSE:::build_Anno(mapping,descriptions),seed=FALSE,by=if(algorithm=="dose")"DOSE"else"fgsea"))
      if(algorithm=="dose")common<-c(common,list(nPerm=intvalue("permutations")))
      else if(algorithm=="simple")common<-c(common,list(nPerm=intvalue("permutations"),scoreType=value("tail"),BPPARAM=BiocParallel::SerialParam()))
      else common<-c(common,list(sampleSize=intvalue("sample-size"),nPermSimple=intvalue("simple-permutations"),scoreType=value("tail"),BPPARAM=BiocParallel::SerialParam()))
      do.call(DOSE:::GSEA_internal,common)
    } else if(engine=="fgsea") {
      common<-list(pathways=sets,stats=stats,minSize=intvalue("min-size"),maxSize=intvalue("max-size"),gseaParam=num("exponent"),scoreType=value("tail"),BPPARAM=BiocParallel::SerialParam())
      if(algorithm=="simple")do.call(fgsea::fgseaSimple,c(common,list(nperm=intvalue("permutations"))))
      else do.call(fgsea::fgseaMultilevel,c(common,list(eps=num("eps"),sampleSize=intvalue("sample-size"),nPermSimple=intvalue("simple-permutations"))))
    } else stop("Unknown original GSEA engine")
  } else stop("Unknown original method")
},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")})
saveRDS(result,file.path(upstream,"original-object.rds"),version=3)
raw<-if(is.null(result))data.frame()else if(isS4(result))result@result else as.data.frame(result)
leading<-data.frame(term=character(),gene=character(),rank=integer())
if(method=="gsea"&&!is.null(result)) {
  if(engine=="dose") {
    helper_rows<-list()
    for(id in raw$ID) {
      helper<-DOSE:::gseaScores(geneList=stats,geneSet=sets[[id]],exponent=num("exponent"))
      hits<-helper$runningES[helper$runningES$position==1,,drop=FALSE]
      helper_rows[[length(helper_rows)+1L]]<-data.frame(term=id,gene=hits$gene,rank=hits$x,running_score=hits$runningScore,helper_es=helper$ES)
    }
    helper_table<-if(length(helper_rows))do.call(rbind,helper_rows)else data.frame(term=character(),gene=character(),rank=integer(),running_score=numeric(),helper_es=numeric())
    write(helper_table,"upstream/legacy-leading-hits.tsv")
  }
  if("leadingEdge"%in%colnames(raw)) {
    for(i in seq_len(nrow(raw)))if(length(raw$leadingEdge[[i]]))leading<-rbind(leading,data.frame(term=raw$pathway[[i]],gene=raw$leadingEdge[[i]],rank=match(raw$leadingEdge[[i]],names(stats))))
  } else if("core_enrichment"%in%colnames(raw)) {
    for(i in seq_len(nrow(raw)))if(!is.na(raw$core_enrichment[[i]])&&nzchar(raw$core_enrichment[[i]])) {
      genes<-strsplit(raw$core_enrichment[[i]],"/",fixed=TRUE)[[1]];leading<-rbind(leading,data.frame(term=raw$ID[[i]],gene=genes,rank=match(genes,names(stats))))
    }
  }
  write(data.frame(gene=names(stats),statistic=as.numeric(stats)),"upstream/ranking.tsv")
  weights<-if(engine=="fgsea"||engine=="dose"&&algorithm!="dose")getFromNamespace("prepareStats","fgsea")(stats,scoreType=value("tail"),gseaParam=num("exponent"))else if(algorithm=="multilevel")getFromNamespace("scale_fgsea_ranks","enrichit")(stats,num("exponent"))else abs(stats)^num("exponent")
  write(data.frame(gene=names(stats),weight=as.numeric(weights)),"upstream/weights.tsv")
}
write(leading,"upstream/leading-edges.tsv")
flat<-raw
for(n in colnames(flat))if(is.list(flat[[n]]))flat[[n]]<-vapply(flat[[n]],paste,character(1),collapse="/")
if(ncol(flat)==0)flat<-data.frame(ID=character())
write(flat,"upstream/results.tsv")
metadata<-list(schema="jlinalg-enrichment-upstream-v1",engine=engine,method=method,algorithm=algorithm,seed=seed,r_version=R.version.string,versions=as.list(sapply(names(pins),function(p)as.character(packageVersion(p)))),rng_kind=RNGkind(),reported_terms=nrow(raw),warnings=as.list(unique(warnings)),namespace_packages=as.list(sort(loadedNamespaces())),scope="Original local-file API call and raw RDS/table retained. No online annotation lookup,LLM interface or user R-code evaluation is invoked by this adapter. Serial execution;engines are not numerical substitutes.")
jsonlite::write_json(metadata,file.path(out,"upstream/metadata.json"),auto_unbox=TRUE,pretty=TRUE,null="null",na="null")
cat("Original",engine,method,"returned",nrow(raw),"terms\n")
