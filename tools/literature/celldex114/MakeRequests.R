# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Explicit project controls; none is asserted to be a paper's exact reference choice.
local({
 a<-commandArgs(TRUE);stopifnot(length(a)%in%c(1L,2L),!dir.exists(a[[1L]]));dir.create(a[[1L]],recursive=TRUE)
 explicit.local<-length(a)==2L&&identical(a[[2L]],'explicit-local')
 if(length(a)==2L)stopifnot(explicit.local)
 cases<-list(
  immgen.default=list('ImmGenData',list()),mouse.default=list('MouseRNAseqData',list()),
  immgen.lazy=list('fetchReference',list(name='immgen',version='2024-02-26')),
  mouse.realized=list('fetchReference',list(name='mouse_rnaseq',version='2024-02-26',realize.assays=TRUE)),
  immgen.metadata=list('fetchMetadata',list(name='immgen',version='2024-02-26')),
  mouse.metadata=list('fetchMetadata',list(name='mouse_rnaseq',version='2024-02-26')),
  immgen.legacy=list('ImmGenData',list(legacy=TRUE)),mouse.legacy=list('MouseRNAseqData',list(legacy=TRUE)),
  immgen.nonmissing.ontology=list('ImmGenData',list(cell.ont='nonna')),
  mouse.no.ontology=list('MouseRNAseqData',list(cell.ont='none')),
  mouse.ensembl=list('MouseRNAseqData',list(ensembl=TRUE)),
  invalid.ontology=list('ImmGenData',list(cell.ont='not-a-mode')),
  unsupported.argument=list('MouseRNAseqData',list(not.an.original.argument=TRUE)),
  missing.reference.version=list('fetchReference',list(name='immgen')),
  invalid.local.subpath=list('fetchReference',list(name='immgen',version='2024-02-26',path='does-not-exist')),
  pure.text.query=list('defineTextQuery',list(text='immun%',partial=TRUE)),
  remote.listing=list('listReferences',list()),remote.versions=list('listVersions',list(name='immgen')),
  remote.latest=list('fetchLatestVersion',list(name='immgen')),
  missing.cached.reference=list('fetchReference',list(name='omicsmatic-definitely-absent-fixture',version='2000-01-01'))
 )
 for(name in names(cases)){
  set.seed(20261115L)
  request<-list(schema=1L,edition='1.14.0',operation=cases[[name]][[1L]],arguments=cases[[name]][[2L]],
   rng=list(kind=RNGkind(),seed=.Random.seed),options=list(),caller_packages=character(),
   hub_options=if(explicit.local)list(ExperimentHub=list(LOCAL=TRUE),AnnotationHub=list(LOCAL=TRUE))else list())
  saveRDS(request,file.path(a[[1L]],paste0(name,'.rds')),version=3)
 }
 jsonlite::write_json(list(status='EXPLICIT_PROJECT_REFERENCE_CONTROLS_NOT_AUTHOR_SETTINGS',names=names(cases),references_are_public_reference_inputs_not_synthetic=TRUE,
  no_paper_reference_identity_inferred=TRUE,explicit_caller_LOCAL_Hub_options=explicit.local),file.path(a[[1L]],'CASES.json'),pretty=TRUE,auto_unbox=TRUE)
})
