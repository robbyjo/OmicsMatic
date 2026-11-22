# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed original SKAT calls. No namespace replacement, tracing or statistical repair.
options(warn=0)
state<-new.env(parent=emptyenv());state$phase<-"initialization";state$set<-0L;state$conditions<-list();state$fits<-0L;state$setcalls<-0L;state$diagnostics<-0L;state$code<-2L;state$error<-NULL
fail<-function(message)stop(message,call.=FALSE)
need<-function(ok,message)if(!isTRUE(ok))fail(message)
unavailable<-function(message)stop(structure(list(message=message,call=NULL),class=c("skat_unavailable","error","condition")))
hex<-function(value)paste(sprintf("%02x",as.integer(charToRaw(enc2utf8(value)))),collapse="")
number<-function(x)vapply(x,function(v)if(is.nan(v))"NaN" else if(is.na(v))"NA" else if(is.infinite(v))if(v>0)"Inf" else "-Inf" else sprintf("%.17g",v),character(1))
newpath<-function(path){need(!file.exists(path)&&!dir.exists(path),paste("Preserve existing evidence and choose NEW output:",path));path}
save<-function(value,path)saveRDS(value,newpath(path),version=3)
tableout<-function(value,path){
 columns<-lapply(value,function(x){if(is.numeric(x))return(number(x));if(is.logical(x))return(ifelse(is.na(x),"NA",ifelse(x,"TRUE","FALSE")));need(is.character(x),"Unsupported owned table column type");x[is.na(x)]<-"NA";enc2utf8(x)})
 lines<-c(paste(names(value),collapse="\t"),vapply(seq_len(nrow(value)),function(i)paste(vapply(columns,function(x)x[[i]],character(1)),collapse="\t"),character(1)))
 connection<-file(newpath(path),open="wb");on.exit(close(connection));writeLines(enc2utf8(lines),connection,useBytes=TRUE)
}
readtable<-function(path){x<-read.delim(path,header=TRUE,sep="\t",quote="",comment.char="",colClasses="character",check.names=FALSE,na.strings=character(),encoding="UTF-8");need(length(names(x))==length(unique(names(x))),"Duplicate literal table column names");x}
label<-function(x)all(!is.na(x)&nzchar(x)&!grepl("[[:cntrl:]]",x))
numeric_cells<-function(x,allow_na=FALSE){
 ok<-grepl("^[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?$",x,perl=TRUE)
 need(all(ok|(allow_na&x=="NA")),"Expected finite decimal numeric cells or explicitly allowed NA tokens")
 y<-rep(NA_real_,length(x));y[ok]<-as.numeric(x[ok]);need(all(is.finite(y[ok])),"Nonfinite input cell;no repair performed");y
}
matrix_input<-function(path,expected_ids=NULL,allow_na=FALSE){
 x<-readtable(path);need(ncol(x)>=2L&&identical(names(x)[[1]],"sample_id"),"Require sample_id followed by numeric columns")
 ids<-x[[1]];need(label(ids)&&!anyDuplicated(ids),"Sample IDs must be unique literal text")
 if(!is.null(expected_ids))need(identical(ids,expected_ids),"Sample membership/order differs;no automatic matching or sorting")
 values<-matrix(numeric_cells(as.vector(as.matrix(x[,-1,drop=FALSE])),allow_na),nrow=nrow(x),ncol=ncol(x)-1L,dimnames=list(ids,names(x)[-1]));list(ids=ids,values=values)
}
record_condition<-function(c,kind){state$conditions[[length(state$conditions)+1L]]<-list(sequence=length(state$conditions)+1L,phase=state$phase,set_index=state$set,kind=kind,message=conditionMessage(c),condition=c)}
handlers<-function(expression)withCallingHandlers(expression,warning=function(w){record_condition(w,"warning");invokeRestart("muffleWarning")},message=function(m){record_condition(m,"message");invokeRestart("muffleMessage")})
guard_namespace<-function(ns,stage){
 reference<-readtable("../source/reference/functions.tsv");actual<-Filter(function(n)is.function(get(n,envir=ns,inherits=FALSE)),ls(ns,all.names=TRUE))
 need(!anyDuplicated(reference[["function"]])&&length(actual)==nrow(reference)&&setequal(actual,reference[["function"]]),"Changed SKAT namespace function population")
 dir.create(stage)
 for(i in seq_len(nrow(reference))){
  f<-get(reference[["function"]][[i]],envir=ns,inherits=FALSE)
  text<-paste0("FORMALS\n",paste(deparse(formals(f),width.cutoff=500L),collapse="\n"),"\nBODY\n",paste(deparse(body(f),width.cutoff=500L),collapse="\n"),"\n")
  bytes<-charToRaw(enc2utf8(text));expected<-readBin(file.path("../source",reference$file[[i]]),"raw",n=16777216L)
  writeBin(bytes,newpath(file.path(stage,paste0(reference[["function"]][[i]],".txt"))))
  need(identical(bytes,expected),paste("Changed SKAT function formals/body:",reference[["function"]][[i]]))
 }
 invisible(TRUE)
}
fields<-function(value,path){
 connection<-file(newpath(path),open="wt",encoding="UTF-8");on.exit(close(connection));writeLines("path\ttype\tindex\telement_name_hex\tvalue\tencoding\tdimensions",connection,useBytes=TRUE)
 add<-function(p,type,index,name,value,encoding,dimensions)writeLines(paste(p,type,index,hex(name),value,encoding,dimensions,sep="\t"),connection,useBytes=TRUE)
 walk<-function(x,p){
  dims<-if(is.null(dim(x)))"" else paste(dim(x),collapse=",")
  if(is.null(x)){add(p,"NULL",0L,"","NULL","literal",dims);return(invisible(NULL))}
  if(is.list(x)){
   if(!length(x))add(p,"list",0L,"","","literal",dims)
   for(i in seq_along(x)){n<-if(is.null(names(x))||!nzchar(names(x)[[i]]))as.character(i) else names(x)[[i]];walk(x[[i]],paste0(p,"/",gsub("/","~1",gsub("~","~0",n,fixed=TRUE),fixed=TRUE)))}
  }else if(is.atomic(x)){
   if(!length(x))add(p,typeof(x),0L,"","","literal",dims)
   for(i in seq_along(x)){
    n<-if(is.null(names(x)))"" else names(x)[[i]];v<-x[[i]]
    if(is.numeric(x))add(p,typeof(x),i,n,number(v),"number",dims)
    else if(is.logical(x))add(p,"logical",i,n,if(is.na(v))"NA" else if(v)"TRUE" else "FALSE","logical",dims)
    else add(p,typeof(x),i,n,if(is.na(v))"NA" else hex(as.character(v)),if(is.na(v))"missing" else "utf8-hex",dims)
   }
  }else add(p,typeof(x),0L,"",hex(paste(deparse(x),collapse="\n")),"deparse-utf8-hex",dims)
 }
 walk(value,"")
}
numeric_matrix_export<-function(z,path,ids){
 need(is.matrix(z)&&nrow(z)==length(ids),"Source matrix row count differs from declared null sample mask")
 values<-matrix(number(as.vector(z)),nrow=nrow(z),ncol=ncol(z));colnames(values)<-paste0("value_",seq_len(ncol(z)))
 frame<-data.frame(sample_index=seq_along(ids),sample_id=ids,source_row_name_hex=if(is.null(rownames(z)))rep("",nrow(z)) else vapply(rownames(z),hex,character(1)),values,check.names=FALSE,stringsAsFactors=FALSE);tableout(frame,path)
}
run<-function(){
 state$phase<-"configuration"
 raw<-readtable("parameters.tsv");need(identical(names(raw),c("key","value"))&&!anyDuplicated(raw$key),"Invalid generated parameter table")
 getp<-function(key){i<-match(key,raw$key);need(!is.na(i),paste("Missing generated parameter:",key));raw$value[[i]]}
 bool<-function(key){v<-getp(key);need(v %in% c("true","false"),paste("Invalid boolean",key));identical(v,"true")}
 num<-function(key)numeric_cells(getp(key))
 int<-function(key){v<-num(key);need(v==floor(v)&&abs(v)<=.Machine$integer.max,paste("Invalid integer",key));as.integer(v)}
 vectorp<-function(key)numeric_cells(strsplit(getp(key),",",fixed=TRUE)[[1]])
 need(identical(getp("profile"),"2.2.5-r4.6.1"),"Unknown generated SKAT profile")
 if(getRversion()!="4.6.1")unavailable(paste("Require declared R4.6.1;observed",getRversion()))
 libs<-readLines("runtime-paths.txt",warn=FALSE,encoding="UTF-8");if(!length(libs)||!all(dir.exists(libs)))unavailable("Supply every exact permitted declared R library directory")
 .libPaths(c(libs,file.path(R.home(),"library")),include.site=FALSE)
 state$phase<-"runtime-load"
 packages<-readtable("../source/reference/packages.tsv");metadata<-list();critical<-character()
 for(i in seq_len(nrow(packages))){
  n<-packages$package[[i]];version<-tryCatch(as.character(packageVersion(n)),error=function(e)unavailable(paste("Install exact permitted package",n,packages$version[[i]],"in a declared private library")))
  d<-packageDescription(n)
  if(!identical(d$Version,packages$version[[i]])||!identical(version,as.character(package_version(packages$version[[i]]))))unavailable(paste("Require exact DESCRIPTION version",n,packages$version[[i]],"observed",d$Version,"with canonical version",version))
  base<-system.file(package=n);metadata[[i]]<-data.frame(package=n,version=d$Version,canonical_version=version,built_hex=hex(if(is.null(d$Built))"" else d$Built),path=base,stringsAsFactors=FALSE)
  candidates<-list.files(base,recursive=TRUE,full.names=TRUE,all.files=TRUE);critical<-c(critical,candidates[grepl("(?:/R/[^/]+\\.(?:rdb|rdx)|/libs/.*\\.(?:dll|so)|/DESCRIPTION|/NAMESPACE|/Meta/[^/]+\\.rds)$",gsub("\\\\","/",candidates),perl=TRUE)])
 }
 ns<-loadNamespace("SKAT");guard_namespace(ns,"namespace-before");tableout(do.call(rbind,metadata),"runtime-packages.tsv")
 dlls<-vapply(getLoadedDLLs(),function(d)d[["path"]],character(1));critical<-sort(unique(c(critical,dlls[nzchar(dlls)],list.files(R.home("bin"),pattern="\\.(dll|exe)$",recursive=TRUE,full.names=TRUE))))
 critical<-sort(unique(c(critical,list.files(R.home("lib"),pattern="\\.so(?:\\.[0-9.]+)?$",recursive=TRUE,full.names=TRUE))));critical<-critical[file.exists(critical)&!dir.exists(critical)];need(length(critical)>0L&&all(!grepl("[[:cntrl:]]",critical)),"Invalid runtime fingerprint file population")
 tableout(data.frame(index=seq_along(critical),path=critical,stringsAsFactors=FALSE),"runtime-files.tsv")
 tableout(data.frame(key=c("R_version","R_home","R_platform","namespace_functions"),value=c(as.character(getRversion()),R.home(),R.version$platform,"239"),stringsAsFactors=FALSE),"runtime-info.tsv")
 nonce<-readLines("launch-nonce.txt",warn=FALSE);need(length(nonce)==1L&&grepl("^[a-f0-9-]{36}$",nonce),"Invalid owned runtime handshake nonce")
writeLines(nonce,newpath("runtime-ready.pending"),useBytes=TRUE);need(file.rename("runtime-ready.pending",newpath("runtime-ready.txt")),"Could not publish complete runtime readiness record")
 deadline<-Sys.time()+int("timeout_seconds")
 while(!file.exists("runtime-ack.txt")){if(Sys.time()>deadline)fail("Runtime fingerprint acknowledgement timed out before any model fit");Sys.sleep(.02)}
 need(identical(readLines("runtime-ack.txt",warn=FALSE),nonce),"Changed runtime fingerprint acknowledgement")
 state$phase<-"input"
 y<-matrix_input("../inputs/phenotype.tsv",allow_na=TRUE);need(ncol(y$values)==1L,"Exactly one declared phenotype column required");ids<-y$ids;n<-length(ids)
 G<-matrix_input("../inputs/genotypes.tsv",ids,TRUE)$values;need(label(colnames(G)),"Invalid literal variant IDs")
 if(file.exists("../inputs/covariates.tsv"))X<-matrix_input("../inputs/covariates.tsv",ids,TRUE)$values else X<-matrix(numeric(),nrow=n,ncol=0L,dimnames=list(ids,NULL))
 membership<-readtable("../inputs/sets.tsv");need(identical(names(membership),c("set_id","variant_id"))&&nrow(membership)>0L&&label(membership$set_id)&&label(membership$variant_id),"Invalid explicit set membership")
 need(all(membership$variant_id %in% colnames(G))&&!anyDuplicated(paste0(nchar(membership$set_id),":",membership$set_id,membership$variant_id)),"Missing/duplicate set variant membership")
 setids<-unique(membership$set_id);state$setids<-setids
 weights<-NULL
 if(file.exists("../inputs/weights.tsv")){
  w<-readtable("../inputs/weights.tsv");need(identical(names(w),c("variant_id","weight"))&&identical(w$variant_id,colnames(G)),"Supplied weights must match every original genotype column in literal order");weights<-numeric_cells(w$weight)
 }
 Y<-as.numeric(y$values[,1]);names(Y)<-ids;mask<-complete.cases(Y,X);need(sum(mask)>=3L,"Fewer than three complete phenotype/covariate samples")
 if(identical(getp("missing_model"),"reject"))need(all(mask),"Missing phenotype/covariate values require explicit complete-cases policy")
 outtype<-getp("out_type");need(outtype %in% c("C","D"),"Invalid outcome type")
 if(outtype=="D")need(all(Y[mask] %in% c(0,1))&&length(unique(Y[mask]))==2L,"Binary null requires both literal 0/1 outcomes")
 if(bool("check_genotype")&&!bool("dosage"))need(all(is.na(G)|G %in% c(0,1,2,9)),"Genotype input requires 0,1,2,9 or NA;no rounding performed")
 if(bool("dosage"))need(all(is.na(G)|(G>=0&G<=2)|G==9),"Dosage requires [0,2],9 or NA;no clipping performed")
 formula<-if(ncol(X)>0L)if(bool("intercept"))Y~X else Y~0+X else Y~1
 if(!ncol(X))need(bool("intercept"),"A no-intercept model needs declared covariate columns")
 frame<-data.frame(Y=Y,row.names=ids);if(ncol(X)>0L)frame$X<-I(X)
 design<-model.matrix(formula,data=model.frame(formula,data=frame,na.action=na.omit));rank<-qr(design)$rank
 if(identical(getp("rank_policy"),"require-full-rank"))need(rank==ncol(design),"Rank-deficient null design;declare source-svd to retain the upstream SVD behavior")
 need(sum(mask)>ncol(design),"No positive residual degrees of freedom")
 need((as.double(length(G))+length(X))<=num("maximum_cells"),"Input cell capacity exceeded")
 need(sum(vapply(setids,function(s)sum(membership$set_id==s),integer(1)))*sum(mask)<=num("maximum_set_cells"),"Aggregate processed-set cell capacity exceeded")
 if(!getp("kernel") %in% c("linear","linear.weighted"))need(as.double(sum(mask))^2<=num("maximum_kernel_cells"),"Dense nonlinear-kernel cell capacity exceeded")
 nr<-int("n_resampling");nk<-if(identical(getp("null_function"),"SKAT_Null_Model_MomentAdjust"))if(bool("kurtosis_adjustment"))int("kurtosis_resamples") else 0L else if(outtype=="D"&&bool("adjustment")&&sum(mask)<2000L)10000L else 0L
 need(as.double(sum(mask))*(nr+nk)<=num("maximum_resampling_cells"),"Declared/implicit null resampling cell capacity exceeded")
 RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(int("seed"));save(.Random.seed,"rng-before-null.rds")
 state$phase<-"null-fit"
 if(identical(getp("null_function"),"SKAT_Null_Model"))obj<-SKAT::SKAT_Null_Model(formula,data=frame,out_type=outtype,n.Resampling=nr,type.Resampling=getp("resampling"),Adjustment=bool("adjustment"))
 else{need(outtype=="D","MomentAdjust null is binary only");obj<-SKAT::SKAT_Null_Model_MomentAdjust(formula,data=frame,n.Resampling=nr,type.Resampling=getp("resampling"),is_kurtosis_adj=bool("kurtosis_adjustment"),n.Resampling.kurtosis=int("kurtosis_resamples"))}
 state$fits<-1L;save(obj,"null-model.rds");save(.Random.seed,"rng-after-null.rds")
 base<-if(inherits(obj,"SKAT_NULL_Model_ADJ"))obj$re1 else obj
 fields(list(null_class=class(obj),null_type=obj$type,n.all=base$n.all,out_type=base$out_type,n.Resampling=base$n.Resampling,type.Resampling=base$type.Resampling,id_include=base$id_include,s2=base$s2,design_dimensions=dim(base$X1),resampling_dimensions=dim(base$res.out),kurtosis_dimensions=if(is.null(obj$re2))NULL else dim(obj$re2$res.out)),"null-fields.tsv")
 need(identical(as.integer(base$id_include),which(mask)),"Original null sample mask differs from declared complete cases")
 if(nr>0L&&!identical(base$type.Resampling,getp("resampling")))need(bool("allow_source_fallback"),"Original null changed the requested resampling method;explicit original fallback acceptance is required")
 need(length(base$res)==sum(mask)&&all(is.finite(base$res))&&all(is.finite(base$X1)),"Nonfinite or inconsistent original null state")
 if(outtype=="C")need(length(base$s2)==1L&&is.finite(base$s2)&&base$s2>0,"Nonpositive/nonfinite original null residual variance")
 else need(all(is.finite(base$mu)&base$mu>0&base$mu<1)&&all(is.finite(base$pi_1)&base$pi_1>0),"Invalid or degenerate original binary null probabilities")
 nullconditions<-Filter(function(r)identical(r$phase,"null-fit"),state$conditions)
 need(!any(vapply(nullconditions,function(r)grepl("did not converge|failed to converge|numerically 0 or 1",r$message,ignore.case=TRUE),logical(1))),"Original null reported convergence/separation failure;raw model and conditions retained")
 used<-ids[base$id_include];numeric_matrix_export(base$X1,"null-design.tsv",used)
 tableout(data.frame(sample_index=seq_len(n),sample_id=ids,included=mask,stringsAsFactors=FALSE),"sample-mask.tsv")
 tableout(data.frame(sample_id=used,residual=number(base$res),mu=if(outtype=="D")number(base$mu) else rep("NA",length(used)),variance=if(outtype=="D")number(base$pi_1) else rep(number(base$s2),length(used)),stringsAsFactors=FALSE),"null-residuals.tsv")
 summary<-list();pvalues<-list();bad<-FALSE;tested<-0L
 for(si in seq_along(setids)){
  state$set<-si;state$phase<-"set-fit";sd<-file.path("sets",sprintf("%06d",si));dir.create(sd,recursive=TRUE);positions<-match(membership$variant_id[membership$set_id==setids[[si]]],colnames(G));Z<-G[,positions,drop=FALSE];w<-if(is.null(weights))NULL else weights[positions];before_seed<-.Random.seed;attempted<-FALSE
  save(before_seed,file.path(sd,"rng-before-fit.rds"));value<-NULL;diagnostic<-NULL;identity<-NULL;issues<-character();notes<-character();failure<-NULL;kind<-"unadmitted";exportp<-"NA";rawp<-"NA";nt<-0L;type<-"";bound<-""
  tryCatch({
   callargs<-list(Z=Z,obj=obj,kernel=getp("kernel"),method=getp("method"),weights.beta=vectorp("weights_beta"),weights=w,impute.method=getp("impute"),r.corr=vectorp("r_corr"),is_check_genotype=bool("check_genotype"),is_dosage=bool("dosage"),missing_cutoff=num("missing_cutoff"),max_maf=num("max_maf"),estimate_MAF=int("estimate_maf"))
   attempted<-TRUE;state$setcalls<-state$setcalls+1L;value<-do.call(SKAT::SKAT,callargs);save(value,file.path(sd,"original-result.rds"));after_seed<-.Random.seed;save(after_seed,file.path(sd,"rng-after-fit.rds"));fields(value,file.path(sd,"original-fields.tsv"))
   state$phase<-"set-result-validation";nt<-as.integer(value$param$n.marker.test);need(length(nt)==1L&&!is.na(nt)&&nt>=0L,"Missing original tested-marker count")
   need(length(value$p.value)==1L,"Original p-value has invalid length");rawp<-number(value$p.value);need(is.finite(value$p.value)&&value$p.value>=0&&value$p.value<=1,"Original p-value is invalid;no repair performed")
   type<-if(is.null(value$Test.Type))"" else paste(value$Test.Type,collapse=",");bound<-if(is.null(value$pval.zero.msg))"" else paste(value$pval.zero.msg,collapse="\n")
   diagnostic_call<-function(override){
    old<-.Random.seed;assign(".Random.seed",before_seed,envir=.GlobalEnv);on.exit(assign(".Random.seed",old,envir=.GlobalEnv))
    SKAT:::SKAT_MAIN_Check_Z(Z,base$n.all,base$id_include,SetID=NULL,weights=override,weights.beta=vectorp("weights_beta"),impute.method=getp("impute"),is_check_genotype=bool("check_genotype"),is_dosage=bool("dosage"),missing_cutoff=num("missing_cutoff"),max_maf=num("max_maf"),estimate_MAF=int("estimate_maf"))
   }
   state$phase<-"diagnostic-genotype-replay";diagnostic<-diagnostic_call(w);state$diagnostics<-state$diagnostics+1L;save(diagnostic,file.path(sd,"original-genotype-diagnostic.rds"))
   if(identical(as.integer(diagnostic$return),1L)){
    need(nt==0L,"Source no-test diagnostic contradicts original marker count");kind<-"not-tested-no-variants";notes<-c(notes,"Original source returned a no-test p-value of1;it is retained raw and not exported as a tested null")
    if(!bool("allow_empty_sets"))issues<-c(issues,"No variants tested;allow_empty_sets is false")
    tableout(data.frame(input_index=positions,variant_id=colnames(Z),tested=FALSE,source_column=0L,source_name_hex="",source_weight="NA",source_mac="NA",stringsAsFactors=FALSE),file.path(sd,"variants.tsv"))
   }else{
    need(is.matrix(diagnostic$Z.test)&&ncol(diagnostic$Z.test)==nt&&nrow(diagnostic$Z.test)==length(used)&&all(is.finite(diagnostic$Z.test)),"Invalid original processed genotype matrix")
    mac<-SKAT:::SingleSNP_INFO(diagnostic$Z.test);need(identical(mac,value$test.snp.mac),"Original result MAC/names disagree with preserved-RNG genotype replay")
    state$phase<-"diagnostic-identity-replay";identity<-diagnostic_call(seq_len(ncol(Z)));state$diagnostics<-state$diagnostics+1L;save(identity,file.path(sd,"identity-only-diagnostic.rds"))
    need(identical(identity$Z.test,diagnostic$Z.test),"Identity-only diagnostic changed processed genotypes")
    map<-as.integer(identity$weights);need(length(map)==nt&&all(is.finite(identity$weights))&&identical(as.numeric(map),as.numeric(identity$weights))&&all(map>=1L&map<=ncol(Z))&&!anyDuplicated(map),"Original identity diagnostic cannot map source columns unambiguously")
    sw<-diagnostic$weights;if(is.null(sw))sw<-rep(NA_real_,nt);need(length(sw)==nt,"Original source weight count differs")
    testedflag<-seq_len(ncol(Z)) %in% map;rankmap<-match(seq_len(ncol(Z)),map);source_names<-if(is.null(colnames(diagnostic$Z.test)))rep("",nt) else vapply(colnames(diagnostic$Z.test),hex,character(1))
    tableout(data.frame(input_index=positions,variant_id=colnames(Z),tested=testedflag,source_column=ifelse(testedflag,rankmap,0L),source_name_hex=ifelse(testedflag,source_names[rankmap],""),source_weight=ifelse(testedflag,number(sw)[rankmap],"NA"),source_mac=ifelse(testedflag,number(mac)[rankmap],"NA"),stringsAsFactors=FALSE),file.path(sd,"variants.tsv"))
    numeric_matrix_export(diagnostic$Z.test,file.path(sd,"processed-genotypes.tsv"),used)
    resolved<-if(inherits(obj,"SKAT_NULL_Model_ADJ"))SKAT:::SKAT_Check_Method(getp("method"),vectorp("r_corr")) else SKAT:::SKAT_Check_Method(getp("method"),vectorp("r_corr"),n=nrow(Z),m=ncol(Z));save(resolved,file.path(sd,"source-method-resolution.rds"));fields(resolved,file.path(sd,"method-resolution.tsv"))
    substituted<-length(resolved$r.corr)>1L&&(nt<=1L||sum(abs(diagnostic$Z.test-diagnostic$Z.test[,1]))==0)
    if(substituted){notes<-c(notes,"Source replaced the requested adaptive test with SKAT");if(!bool("allow_test_substitution"))issues<-c(issues,"Original test substitution was not accepted")}
    omitted<-inherits(obj,"SKAT_NULL_Model_ADJ")&&!getp("kernel") %in% c("linear","linear.weighted")
    if(omitted){notes<-c(notes,"Source omitted small-sample adjustment for the requested kernel");if(!bool("allow_adjustment_omission"))issues<-c(issues,"Original small-sample adjustment omission was not accepted")}
    convergence<-unlist(value$param[grepl("^Is_Converged(?:\\.|$)",names(value$param),perl=TRUE)],use.names=FALSE)
    if(length(convergence)&&any(!is.finite(convergence)|convergence!=1)){notes<-c(notes,"Source returned a primary or resampling nonconvergence/fallback indicator");if(!bool("allow_source_fallback"))issues<-c(issues,"Reported original fallback/nonconvergence was not accepted")}
    fitwarnings<-Filter(function(r)identical(r$set_index,si)&&identical(r$phase,"set-fit")&&identical(r$kind,"warning"),state$conditions)
    numericalwarning<-any(vapply(fitwarnings,function(r)grepl("integration fail|failed to converge|non.?converg|NaNs produced|roundoff|maximum number of subdivisions|unable to calculate",r$message,ignore.case=TRUE),logical(1)))
    if(numericalwarning&&!bool("allow_source_fallback"))issues<-c(issues,"Original numerical warning was not accepted;raw conditions retained")
    if(!is.null(value$p.value.resampling))need(length(value$p.value.resampling)==nr&&all(is.finite(value$p.value.resampling)&value$p.value.resampling>0&value$p.value.resampling<=1),"Invalid,incomplete or zero-valued original resampling p-values;individual source bounds are unavailable")
    if(nr>0L){resampled<-SKAT::Get_Resampling_Pvalue(value);save(resampled,file.path(sd,"original-resampling-summary.rds"));fields(resampled,file.path(sd,"resampling-summary.tsv"))}
    if(value$p.value==0){
     notes<-c(notes,"Original numeric zero is retained;it is not an exact probability or infinite biological significance")
     if(identical(getp("zero_policy"),"retain-source-bound")&&nzchar(bound)){kind<-"source-reported-bound";exportp<-"NA"}else issues<-c(issues,"Zero p-value requires an explicit retained-source-bound policy and original bound text")
    }else{kind<-"numeric-pvalue";exportp<-rawp}
    tested<-tested+1L
   }
   need(identical(.Random.seed,after_seed),"Diagnostic replay consumed or changed original RNG state")
  },error=function(error){failure<<-error;record_condition(error,"error");issues<<-c(issues,conditionMessage(error));if(!file.exists(file.path(sd,"rng-after-fit.rds")))save(.Random.seed,file.path(sd,"rng-after-error.rds"))})
  if(!is.null(failure))save(failure,file.path(sd,"original-or-adapter-error.rds"))
  admitted<-!length(issues)&&!identical(kind,"not-tested-no-variants");if(length(issues))bad<-TRUE
  summary[[si]]<-data.frame(set_index=si,set_id=setids[[si]],original_call_attempted=attempted,source_returned=!is.null(value),raw_p=rawp,export_p=if(admitted)exportp else "NA",p_kind=kind,source_test_type_hex=hex(type),input_variants=ncol(Z),tested_variants=nt,result_admitted=admitted,issues_hex=hex(paste(issues,collapse="\n")),notes_hex=hex(paste(notes,collapse="\n")),bound_hex=hex(bound),stringsAsFactors=FALSE)
 }
 state$phase<-"final-source-guard";state$set<-0L;save(.Random.seed,"rng-after-all.rds");guard_namespace(ns,"namespace-after")
 state$summary<-do.call(rbind,summary);tableout(state$summary,"set-results.tsv")
 state$admitted<-!bad&&tested>0L;state$tested<-tested
 state$metadata<-list(input_samples=n,used_samples=length(used),null_class=paste(class(obj),collapse=","),input_design_rank=rank,input_design_columns=ncol(design),original_null_design_columns=ncol(base$X1),null_type=if(is.null(obj$type))"" else obj$type,source_genotype_diagnostic_calls=state$diagnostics)
 if(state$admitted){state$code<-0L;tableout(state$summary[,c("set_index","set_id","export_p","p_kind","result_admitted","bound_hex")],"../results/p-values.tsv")}
 else{state$code<-2L;state$error<-"At least one set is unadmitted or no set was tested;complete batch export withheld. Read raw per-set objects and conditions."}
}

handlers(tryCatch(run(),error=function(error){state$code<-if(inherits(error,"skat_unavailable"))3L else 2L;state$error<-conditionMessage(error);record_condition(error,"error");try(save(error,"fatal-error.rds"),silent=TRUE)}))
if(length(state$conditions)){
 rows<-lapply(state$conditions,function(r)data.frame(sequence=r$sequence,phase=r$phase,set_index=r$set_index,kind=r$kind,message_hex=hex(r$message),stringsAsFactors=FALSE));tableout(do.call(rbind,rows),"conditions.tsv");save(state$conditions,"conditions.rds")
}else tableout(data.frame(sequence=integer(),phase=character(),set_index=integer(),kind=character(),message_hex=character()),"conditions.tsv")
save(as.list(state),"worker-report.rds")
status<-if(state$code==0L)"COMPLETED_WITH_WARNINGS" else if(state$code==3L)"SKIP3_DEPENDENT_ANALYSIS_SKIPPED" else "FAIL2_NO_COMPLETE_RESULT"
tableout(data.frame(status=status,detail_hex=hex(if(is.null(state$error))"Original SKAT calls completed;read conditions and per-set admission" else state$error),original_null_fits=state$fits,original_set_calls=state$setcalls,genotype_diagnostic_calls=state$diagnostics,stringsAsFactors=FALSE),"worker-status.tsv")
capture.output(sessionInfo(),file=newpath("SESSION.txt"))
quit(save="no",status=state$code)
