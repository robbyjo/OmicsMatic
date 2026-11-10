
.libPaths(c(private_library,file.path(R.home(),"library")),include.site=FALSE)
stopifnot(getRversion()=="4.6.1",utils::packageDescription("GENESIS",fields="Version")=="2.42.0",.Machine$longdouble.digits==64L)
Sys.setlocale("LC_CTYPE","English_United States.utf8")
RhpcBLASctl::blas_set_num_threads(1L)
hex_id <- function(s)paste(sprintf("%02x",as.integer(charToRaw(enc2utf8(s)))),collapse="")
write_tsv <- function(x,file)utils::write.table(x,file,sep="\t",quote=FALSE,row.names=FALSE,na="NA")
cases <- readRDS(file.path(previous,"declared-own-cases.rds"))
set.seed(20261110L)
for(k in seq_len(32L)) {
  n <- 5L+k%%20L;ids <- paste0("float-id-",seq_len(n));m <- diag(.5,n);dimnames(m) <- list(ids,ids)
  at <- which(upper.tri(m),arr.ind=TRUE);values <- ifelse(stats::runif(nrow(at)) < (.15 + .1*(k%%6L)), .023 + .22*stats::runif(nrow(at)), 0)
  m[at] <- values;m[t(at)[2:1,,drop=FALSE] |> t()] <- values
  d <- matrix(stats::runif(n*n,-.3,.3),n);d <- (d+t(d))/2;diag(d) <- .5;dimnames(d) <- list(ids,ids)
  cases[[length(cases)+1L]] <- list(name=paste0("nondyadic-graph-",k),kin=m,div=if(k%%2L==0)d else NULL,
    include=if(k%%3L==0)ids[seq_len(n-2L)] else NULL,forced=if(k%%4L==0)ids[c(1,3)] else NULL,kt=2^(-11/2),dt=-2^(-11/2))
}
m <- matrix(0,4L,4L,dimnames=list(letters[1:4],letters[1:4]));m[1,2] <- m[2,1] <- m[3,4] <- m[4,3] <- .25
cases[[length(cases)+1L]] <- list(name="length-one-simplification-related-pairs",kin=m,div=NULL,include=NULL,forced=c("b","d"),kt=.02,dt=-.02)
saveRDS(cases,file.path(out,"complete-own-case-requests.rds"))
write_matrix <- function(m,file) {
  if(is.null(m))return(invisible(NULL));rows <- expand.grid(row=seq_len(nrow(m)),column=seq_len(ncol(m)))
  rows$value_hex <- sprintf("%a",as.vector(m));write_tsv(rows,file)
}
write_ids <- function(ids,file)write_tsv(data.frame(row=seq_along(ids),id_utf8_hex=vapply(ids,hex_id,character(1L))),file)
records <- list()
for(case in cases) {
  home <- file.path(out,case$name);dir.create(home)
  write_matrix(case$kin,file.path(home,"kinship.tsv"));write_ids(colnames(case$kin),file.path(home,"kinship-ids.tsv"))
  if(!is.null(case$div)){write_matrix(case$div,file.path(home,"divergence.tsv"));write_ids(colnames(case$div),file.path(home,"divergence-ids.tsv"))}
  if(!is.null(case$include))write_ids(case$include,file.path(home,"included-ids.tsv"))
  if(!is.null(case$forced))write_ids(case$forced,file.path(home,"forced-ids.tsv"))
  write_tsv(data.frame(key=c("kinship_threshold_hex","divergence_threshold_hex"),value=c(sprintf("%a",case$kt),sprintf("%a",case$dt))),file.path(home,"options.tsv"))
  warnings <- character();failure <- NULL
  answer <- tryCatch(withCallingHandlers(GENESIS::pcairPartition(case$kin,case$div,kin.thresh=case$kt,div.thresh=case$dt,
    unrel.set=case$forced,sample.include=case$include,verbose=FALSE),warning=function(w){warnings <<- c(warnings,conditionMessage(w));invokeRestart("muffleWarning")}),
    error=function(e){failure <<- conditionMessage(e);NULL})
  write_ids(warnings,file.path(home,"original-warnings.tsv"))
  if(is.null(failure)) {
    output <- rbind(data.frame(set=rep("related",length(answer$rels)),result_row=seq_along(answer$rels),id_utf8_hex=unname(vapply(answer$rels,hex_id,character(1L))),id_na=unname(is.na(answer$rels))),
      data.frame(set=rep("unrelated",length(answer$unrels)),result_row=seq_along(answer$unrels),id_utf8_hex=unname(vapply(answer$unrels,hex_id,character(1L))),id_na=unname(is.na(answer$unrels))))
    write_tsv(output,file.path(home,"original-partition.tsv"));saveRDS(answer,file.path(home,"original-result.rds"));dput(answer,file=file.path(home,"original-result.R"))
  }else writeLines(failure,file.path(home,"original-error.txt"),useBytes=TRUE)
  supported <- all(is.finite(case$kin)) && (is.null(case$div)||all(is.finite(case$div)))
  records[[case$name]] <- data.frame(case=case$name,status=if(is.null(failure))"ORIGINAL_SUCCESS" else "ORIGINAL_ERROR",native_finite_profile=supported)
}
write_tsv(do.call(rbind,records),file.path(out,"original-case-dispositions.tsv"))
fixed <- list(numeric(),c(2^53,1,1),c(2^63,1,-2^63),c(2^64,1,1,-2^64),c(2^64,2,-2^64),c(.Machine$double.xmax,2^960),
  c(.Machine$double.xmax,.Machine$double.xmin),c(.Machine$double.xmax,.Machine$double.xmax,-.Machine$double.xmax),c(2^-1074,2^-1074,-2^-1074),c(-0,0,-0))
sum_cases <- fixed
for(k in seq_len(502L)) {
  n <- c(1L,2L,5L,33L,65L,129L)[1L+k%%6L]
  exponents <- sample(-1074:1023,n,replace=TRUE);mantissas <- sample(c(1,1+2^-52,1.5,2-2^-52),n,replace=TRUE)
  x <- mantissas*2^exponents*sample(c(-1,1),n,replace=TRUE);stopifnot(all(is.finite(x)));sum_cases[[length(sum_cases)+1L]] <- x
}
sum_home <- file.path(out,"scalar-sums");dir.create(sum_home);sums <- list()
for(k in seq_along(sum_cases)) {
  x <- as.double(sum_cases[[k]]);writeLines(sprintf("%a",x),file.path(sum_home,paste0(k,".hex")),useBytes=TRUE)
  sums[[k]] <- data.frame(case=k,count=length(x),source_sum_hex=sprintf("%a",sum(x)))
}
write_tsv(do.call(rbind,sums),file.path(out,"original-scalar-sums.tsv"))
dput(.Machine,file=file.path(out,"actual-R-machine.R"));cat("COMPLETE_ORIGINAL_GENESIS_PARTITION_AND_R64_SUM_CONTROLS\n")
