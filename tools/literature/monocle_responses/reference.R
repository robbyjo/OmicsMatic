# Independent official Monocle response-expression and spdep regression-Moran controls.
# No author fork or Java implementation is loaded or sourced.
args<-commandArgs(TRUE);stopifnot(length(args)==5L)
out<-args[1];.libPaths(args[2:3],include.site=FALSE);official_graph<-args[4];official_moran<-args[5]
stopifnot(as.character(getRversion())=="4.3.3",as.character(packageVersion("monocle3"))=="1.0.0",as.character(packageVersion("spdep"))=="1.4.1")
defs<-list();for(expr in parse(official_graph,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))defs[[as.character(expr[[2]])]]<-expr[[3]]
original<-eval(defs$graph_test,asNamespace("monocle3"));loaded<-get("graph_test",asNamespace("monocle3"));stopifnot(identical(body(original),body(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE),identical(formals(original),formals(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE))
sp<-new.env(parent=asNamespace("spdep"));sys.source(official_moran,envir=sp,keep.source=FALSE);stopifnot(identical(body(sp$lm.morantest),body(spdep::lm.morantest),ignore.srcref=FALSE,ignore.bytecode=FALSE),identical(formals(sp$lm.morantest),formals(spdep::lm.morantest),ignore.srcref=FALSE,ignore.bytecode=FALSE))
# R4.3.3 matchArgs_NR records ARGUSED in the substitute() argument pairlist.
# Serialization preserves that execution state (LEVELS << 12); strict parsed
# body/formal equality is the admission guard. Preserve raw hashes as diagnostics.
source_matches<-function(candidate,reference,namespace) {
 is.function(candidate)&&identical(environment(candidate),namespace)&&
 identical(body(candidate),body(reference),ignore.srcref=FALSE,ignore.bytecode=FALSE)&&
 identical(formals(candidate),formals(reference),ignore.srcref=FALSE,ignore.bytecode=FALSE)
}
assert_sources<-function()stopifnot(
 source_matches(get("graph_test",asNamespace("monocle3")),original,asNamespace("monocle3")),
 source_matches(get("lm.morantest",asNamespace("spdep")),sp$lm.morantest,asNamespace("spdep")))
assert_sources();guard_controls<-list()
for(name in c("graph_test","lm.morantest")) {
 namespace<-asNamespace(if(name=="graph_test")"monocle3"else"spdep")
 candidate<-get(name,namespace,inherits=FALSE);reference<-if(name=="graph_test")original else sp$lm.morantest
 for(change in c("body","formals","environment")) {
  changed<-candidate
  if(change=="body")body(changed)<-quote(stop("changed owned control"))
  if(change=="formals")formals(changed)<-c(formals(changed),alist(owned_changed_argument=TRUE))
  if(change=="environment")environment(changed)<-new.env(parent=baseenv())
  refused<-!source_matches(changed,reference,namespace);stopifnot(refused)
  guard_controls[[paste(name,change,sep="/")]]<-list(refused=refused,installed_namespace_modified=FALSE)
 }
}
assert_sources()
before<-list(graph_body=digest::digest(serialize(body(loaded),NULL,version=2),algo="sha256",serialize=FALSE),graph_formals=digest::digest(serialize(formals(loaded),NULL,version=2),algo="sha256",serialize=FALSE),moran_body=digest::digest(serialize(body(spdep::lm.morantest),NULL,version=2),algo="sha256",serialize=FALSE),moran_formals=digest::digest(serialize(formals(spdep::lm.morantest),NULL,version=2),algo="sha256",serialize=FALSE))
branches<-list();find_branch<-function(node){if(!is.call(node))return(invisible(NULL));if(identical(node[[1]],as.name("if"))&&identical(node[[2]],quote(expression_family %in% c("uninormal","binomialff"))))branches[[length(branches)+1L]]<<-node;children<-as.list(node);for(index in seq_along(children)){if(identical(unname(children[index]),unname(alist(missing=))))next;find_branch(children[[index]])};invisible(NULL)}
find_branch(body(original));stopifnot(length(branches)==1L);branch<-branches[[1]]
jsonlite::write_json(list(status="EXACT_OFFICIAL_MONOCLE100_RESPONSE_BRANCH",statement=as.list(deparse(branch)),source_sha256=digest::digest(official_graph,file=TRUE,algo="sha256"),branch_serialization_sha256=digest::digest(serialize(branch,NULL,version=2),algo="sha256",serialize=FALSE),full_graph_test_called=FALSE,original_namespace_modified=FALSE),file.path(out,"SOURCE-EXPRESSION.json"),auto_unbox=TRUE,pretty=TRUE)
write_table<-function(path,header,axis,values){con<-file(path,open="wt",encoding="UTF-8");on.exit(close(con));writeLines(paste(header,collapse="\t"),con);for(i in seq_along(axis))writeLines(paste(c(axis[i],vapply(as.numeric(values[i,]),function(x)sprintf("%.17g",x),character(1))),collapse="\t"),con)}
tokens<-function(x)lapply(as.numeric(x),function(v)if(is.nan(v))"NaN"else if(is.na(v))"NA"else if(is.infinite(v))if(v>0)"Inf"else"-Inf"else sprintf("%.17g",v))
results<-list();warnings<-list()
for(case in c("quasi-design","uninormal-design","binomial-design","quasi-metadata","asis-plain","extreme-log")){
 directory<-file.path(out,case);dir.create(directory);n<-if(case=="extreme-log")12L else 48L;i<-seq_len(n);cells<-paste0("cell-",sprintf("%03d",i));cells[2]<-"NA";cells[3]<-" cell β ";genes<-c('gene "one"',"NA","gene-3","gene-4")
 expression<-rbind(2+(i%%13),5+floor(4*sin(i*.11)+4),i%%9,1+(i%%5)^2);sf<-.5+(i%%7)/4;detected<-as.double(150+(i*7)%%53);family<-if(case=="uninormal-design"||case=="asis-plain")"uninormal"else if(case=="binomial-design")"binomialff"else"quasipoisson"
 if(family!="quasipoisson")expression<-rbind(sin(i*.13)-2,cos(i*.3)+i*.01,(-1)^i,i/7-4)
 if(case=="extreme-log"){expression<-rbind(c(0,5e-324,1e-300,1e-200,1e-10,.1,1,10,1e100,1e200,1e300,1e308),c(1e200,0,.5,1e-10,1e-100,1e308,10,4,3,2,1,0));sf<-rep(c(1,.5,2),length.out=n);sf[6]<-1e20;genes<-genes[1:2];detected<-c(1,2,3,4,5,10,100,1000,10000,100000,1000000,9007199254740992)}
 with_metadata<-case!="asis-plain";with_design<-case%in%c("quasi-design","uninormal-design","binomial-design")
 prepared<-expression
 for(g in seq_len(nrow(expression))){environment<-new.env(parent=baseenv());environment$expression_family<-family;environment$exprs_val<-expression[g,];environment$sz<-sf;eval(branch,envir=environment);prepared[g,]<-environment$exprs_val}
 dimnames(expression)<-dimnames(prepared)<-list(genes,cells);logged<-if(with_metadata)log(detected)else NULL;base<-if(with_design)cbind(intercept=1,BatchB=as.numeric(i%%3==1),BatchC=as.numeric(i%%3==2),SexM=as.numeric(i%%4>=2),PMI=2+i%%17)else NULL;design<-if(with_design)cbind(base,log_n_genes=logged)else cbind(intercept=1,trend=i/n)
 write_table(file.path(directory,"expression.tsv"),c("gene_id",cells),genes,expression)
 if(family=="quasipoisson")write_table(file.path(directory,"size_factors.tsv"),c("cell_id","size_factor"),cells,matrix(sf,ncol=1))
 if(with_metadata)write_table(file.path(directory,"detected_genes.tsv"),c("cell_id","nFeature_RNA"),cells,matrix(detected,ncol=1))
 if(with_design)write_table(file.path(directory,"base_design.tsv"),c("cell_id",colnames(base)),cells,base)
 write_table(file.path(directory,"original-responses.tsv"),c("gene_id",cells),genes,prepared);write_table(file.path(directory,"original-design.tsv"),c("cell_id",colnames(design)),cells,design)
 nb<-lapply(i,function(j)as.integer(sort(c(if(j==1L)n else j-1L,if(j==n)1L else j+1L))));class(nb)<-"nb";attr(nb,"region.id")<-cells;attr(nb,"sym")<-TRUE;glist<-lapply(nb,function(x)rep(.5,length(x)));lw<-spdep::nb2listw(nb,glist=glist,style="B",zero.policy=TRUE)
 con<-file(file.path(directory,"weights.tsv"),"wt",encoding="UTF-8");writeLines("from_cell\tto_cell\tweight",con);for(j in i)for(k in nb[[j]])writeLines(paste(c(cells[j],cells[k],"0.5"),collapse="\t"),con);close(con)
 tests<-list();raw_returns<-list()
 for(alternative in c("greater","less","two.sided")){
  rows<-list();pvalues<-numeric(nrow(prepared))
  for(g in seq_len(nrow(prepared))){y<-prepared[g,];X<-unname(design);fit<-stats::lm(y~0+X,na.action=stats::na.fail,tol=1e-7);stopifnot(fit$rank==ncol(X));answer<-withCallingHandlers(spdep::lm.morantest(fit,lw,zero.policy=TRUE,alternative=alternative),warning=function(w){warnings[[length(warnings)+1L]]<<-list(case=case,gene=genes[g],message=conditionMessage(w));invokeRestart("muffleWarning")});stopifnot(all(is.finite(c(answer$estimate,answer$statistic,answer$p.value))),answer$estimate[3]>0);z<-unname(answer$statistic);logp<-if(alternative=="two.sided")log(2)+pnorm(abs(z),lower.tail=FALSE,log.p=TRUE)else pnorm(z,lower.tail=alternative=="less",log.p=TRUE);pvalues[g]<-answer$p.value;rows[[g]]<-list(gene_id=genes[g],morans_I=unname(answer$estimate[1]),expectation=unname(answer$estimate[2]),variance=unname(answer$estimate[3]),z=z,p_value=answer$p.value,log_p_value=logp);raw_returns[[paste(alternative,g)]]<-list(model=fit,result=answer)}
  q<-p.adjust(pvalues,method="BH");for(g in seq_along(rows))rows[[g]]$q_value<-q[g];tests[[alternative]]<-rows
 }
 record<-list(id=case,source_expression_family_label=family,transform=if(family=="quasipoisson")"size-factor-log10-plus-0.1"else"as-is",cell_ids=as.list(cells),gene_ids=as.list(genes),expression=unname(expression),size_factors=if(family=="quasipoisson")as.list(sf)else NULL,detected_genes=if(with_metadata)as.list(detected)else NULL,expected_responses=unname(prepared),expected_log_n_genes=if(with_metadata)as.list(logged)else NULL,base_design=if(with_design)unname(base)else NULL,base_design_columns=if(with_design)as.list(colnames(base))else NULL,expected_design=unname(design),tests=tests)
 jsonlite::write_json(record,file.path(directory,"RESULT.json"),auto_unbox=TRUE,digits=17,matrix="rowmajor",dataframe="rows",na="string",null="null");saveRDS(list(input=expression,size_factors=sf,detected_genes=detected,responses=prepared,design=design,models_and_results=raw_returns),file.path(directory,"original-complete-objects.rds"));results[[case]]<-record;cat(case,"original preparation and",3*nrow(prepared),"gene tests\n");flush.console()
}
assert_sources()
after<-list(graph_body=digest::digest(serialize(body(get("graph_test",asNamespace("monocle3"))),NULL,version=2),algo="sha256",serialize=FALSE),graph_formals=digest::digest(serialize(formals(get("graph_test",asNamespace("monocle3"))),NULL,version=2),algo="sha256",serialize=FALSE),moran_body=digest::digest(serialize(body(spdep::lm.morantest),NULL,version=2),algo="sha256",serialize=FALSE),moran_formals=digest::digest(serialize(formals(spdep::lm.morantest),NULL,version=2),algo="sha256",serialize=FALSE))
jsonlite::write_json(list(status="PASS_STRICT_PARSED_OFFICIAL_FUNCTION_GUARDS",body_formals_environment_match_before_and_after=TRUE,owned_changed_function_controls=guard_controls,namespace_modified=FALSE,serialized_hashes_are_diagnostics=TRUE,before=before,after=after),file.path(out,"SOURCE-GUARDS.json"),pretty=TRUE,auto_unbox=TRUE)
result<-list(status="PASS_ORIGINAL_MONOCLE_RESPONSE_BRANCH_AND_SPDEP_CONTROLS",R=as.character(getRversion()),monocle3=as.character(packageVersion("monocle3")),spdep=as.character(packageVersion("spdep")),official_source_body_formals_identical=TRUE,official_namespace_unchanged=TRUE,selected_namespace_functions_guarded=c("monocle3::graph_test","spdep::lm.morantest"),author_code_executed=FALSE,cases=results,source_signatures_before=before,source_signatures_after=after,source_guard_controls=guard_controls,warnings=warnings,whole_paper_reproduced=FALSE)
jsonlite::write_json(result,file.path(out,"RESULT.json"),auto_unbox=TRUE,digits=17,matrix="rowmajor",dataframe="rows",na="string",null="null");capture.output(sessionInfo(),file=file.path(out,"SESSION.txt"))
