# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Test-only process-local namespace mutation. No installed library file is edited.
args<-commandArgs(TRUE);stopifnot(length(args)==1L,args[[1]]%in%c('body','formals'))
paths<-readLines('runtime-paths.txt',encoding='UTF-8');.libPaths(c(paths,.Library),include.site=FALSE)
ns<-asNamespace('Seurat');target<-'FindMarkers.Assay';original<-get(target,envir=ns,inherits=FALSE);mutated<-original
if(args[[1]]=='body') {
 body(mutated)<-substitute({stop('TEST_ONLY_CHANGED_FUNCTION_BODY');OLD},list(OLD=body(original)))
} else {
 f<-formals(mutated);f<-c(f,alist(TEST_ONLY_UNDECLARED_FORMAL=));formals(mutated)<-f
}
stopifnot(!identical(body(original),body(mutated))||!identical(formals(original),formals(mutated)))
unlockBinding(target,ns);assign(target,mutated,envir=ns);lockBinding(target,ns)
source('../source/seurat_differential.R',local=.GlobalEnv)
stop('SOURCE GUARD FAILED TO REFUSE MUTATED NAMESPACE')
